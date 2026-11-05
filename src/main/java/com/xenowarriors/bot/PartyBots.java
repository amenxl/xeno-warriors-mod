package com.xenowarriors.bot;

import com.mojang.authlib.GameProfile;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.party.Parties;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillPool;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Bot teammates: adding them (the pick screen), their skills (used through a stand-in player so every character skill
 * works for them), leveling the owner's saved stats for that character, mining mode, going down and being revived,
 * sparring with your bot, carrying (both ways), saving them while you're offline, and keeping parties at 4.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PartyBots {
    public static final int BLEED_OUT = 30 * 20, REJOIN_COOLDOWN = 5 * 60 * 20, MAX_BOTS = 3;
    public static final long SPAR_XP_COOLDOWN = 30L * 60 * 20;                  // hidden: big spar XP once every 30 minutes
    private static final String RECORDS = "xw_bots", REJOIN = "xw_bot_rejoin_", SPAR_LAST = "xw_spar_xp_last";
    private static final Map<UUID, UUID> FAKE_TO_BOT = new HashMap<>();
    private static final Map<UUID, FakePlayer> FAKES = new HashMap<>();

    private PartyBots() {}

    // ---------------------------------------------------------------- who's on the team

    /** Live bots by owner (kept by the bots themselves as they enter and leave the world). */
    static final Map<UUID, Set<PartyBotEntity>> LIVE = new HashMap<>();

    public static List<PartyBotEntity> bots(ServerPlayer owner) {
        Set<PartyBotEntity> set = LIVE.get(owner.getUUID());
        if (set == null) return List.of();
        List<PartyBotEntity> out = new ArrayList<>();
        for (PartyBotEntity b : set) if (b.isAlive() && !b.isRemoved()) out.add(b);
        return out;
    }

    static void track(PartyBotEntity b, boolean add) {
        if (b.level().isClientSide || b.ownerId() == null) return;
        if (add) LIVE.computeIfAbsent(b.ownerId(), k -> new LinkedHashSet<>()).add(b);
        else { Set<PartyBotEntity> s = LIVE.get(b.ownerId()); if (s != null) { s.remove(b); if (s.isEmpty()) LIVE.remove(b.ownerId()); } }
    }

    /** Party HUD rows for this player's bots. */
    public static List<com.xenowarriors.network.PartySyncPacket.Member> syncMembers(ServerPlayer owner) {
        List<com.xenowarriors.network.PartySyncPacket.Member> out = new ArrayList<>();
        for (PartyBotEntity b : bots(owner))
            out.add(new com.xenowarriors.network.PartySyncPacket.Member(b.getUUID(), b.character().getDisplayName().getString() + " (" + owner.getName().getString() + ")",
                    b.getHealth(), b.getMaxHealth(), b.karma, 100F, b.character().ordinal(), b.downed(), false,
                    b.getX(), b.getY(), b.getZ(), b.level().dimension().location().toString(), true, b.botLevel(), 0F));
        return out;
    }

    private static final Set<UUID> SOLO_SYNCED = new HashSet<>();
    static final List<Map.Entry<UUID, CompoundTag>> LEFT_BEHIND = new ArrayList<>();
    /** Bots whose chunk left the world (their records are in LEFT_BEHIND): discarded next server tick. */
    static final List<PartyBotEntity> STRANDED = new ArrayList<>();

    /** (UI tests) a "XW-TEST bots5 ..." line. */
    public static void testLog(PartyBotEntity b, String s) {
        if (System.getenv("XW_UITEST") != null) XenoWarriors.LOGGER.info("XW-TEST bots5 {} {}", b == null ? "-" : b.character().getId(), s);
    }

    /** (Tests) how many bot records wait for this owner. */
    public static int waitingFor(UUID owner) { int n = 0; for (var e : LEFT_BEHIND) if (e.getKey().equals(owner)) n++; return n; }

    /** Players with bots but no party still get the party cards (them + their bots). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!STRANDED.isEmpty()) {                                                // (their records are on the waiting list: the old copies go)
            for (PartyBotEntity b : new ArrayList<>(STRANDED)) if (!b.isRemoved()) { forgetFake(b.getUUID()); b.discard(); }
            STRANDED.clear();
        }
        if (!LEFT_BEHIND.isEmpty()) {
            List<Map.Entry<UUID, CompoundTag>> list = new ArrayList<>(LEFT_BEHIND);
            LEFT_BEHIND.clear();
            for (var e : list) {
                ServerPlayer o = event.getServer().getPlayerList().getPlayer(e.getKey());
                if (o == null) { LEFT_BEHIND.add(e); continue; }                    // (owner offline: kept for when they're back)
                CharacterType ct = CharacterType.byId(e.getValue().getString("char"));
                if (!canFollowTo(ct, o.level())) { LEFT_BEHIND.add(e); continue; }          // (waits outside the Hekublade)
                if (!o.isAlive() || com.xenowarriors.journey.Journey.inLobbyOrRun(o) || placeNear(o, ct.skylander() || ct == CharacterType.ULTIMA || ct == CharacterType.MIKIKO) == null) { LEFT_BEHIND.add(e); continue; }   // (dead, on a Journey, or high in the air over nothing: once there's ground)
                if (bots(o).stream().noneMatch(b -> b.character() == ct)) spawn(o, ct, e.getValue());
                else homeFromRecord(o, e.getValue());                               // (that character came back another way meanwhile: its things go home, not into thin air)
            }
        }
        if (event.getServer().getTickCount() % 10 != 5) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (Parties.inParty(p)) { SOLO_SYNCED.remove(p.getUUID()); continue; }
            List<com.xenowarriors.network.PartySyncPacket.Member> list = syncMembers(p);
            if (list.isEmpty()) {
                if (SOLO_SYNCED.remove(p.getUUID())) ModNetwork.sendToPlayer(new com.xenowarriors.network.PartySyncPacket(List.of()), p);
                continue;
            }
            list.add(0, new com.xenowarriors.network.PartySyncPacket.Member(p.getUUID(), p.getName().getString(), p.getHealth(), p.getMaxHealth(),
                    com.xenowarriors.karma.Karma.get(p), p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.karmaMax).orElse(com.xenowarriors.karma.Karma.MAX), ModCapabilities.getType(p).ordinal(), false, true,
                    p.getX(), p.getY(), p.getZ(), p.level().dimension().location().toString(), false,
                    com.xenowarriors.stats.Stats.characterLevel(p), com.xenowarriors.stats.Stats.progress(p)));
            ModNetwork.sendToPlayer(new com.xenowarriors.network.PartySyncPacket(list), p);
            SOLO_SYNCED.add(p.getUUID());
        }
    }

    /** Players + bots in this player's party. */
    public static int partySize(ServerPlayer p) {
        int n = 0;
        for (ServerPlayer m : Parties.members(p)) n += 1 + bots(m).size();
        return n;
    }

    public static PartyBotEntity botOf(Entity e) {
        if (e instanceof PartyBotEntity b) return b;
        if (e instanceof FakePlayer f && FAKE_TO_BOT.containsKey(f.getUUID()) && f.level() instanceof ServerLevel s
                && s.getEntity(FAKE_TO_BOT.get(f.getUUID())) instanceof PartyBotEntity b) return b;
        return null;
    }

    /** On the bot's side: its owner, the owner's party, their pets and the other bots. */
    public static boolean friendly(PartyBotEntity bot, Entity e) {
        UUID owner = bot.ownerId();
        if (owner == null || e == null) return false;
        if (e == bot) return true;
        PartyBotEntity other = botOf(e);
        if (other != null) return owner.equals(other.ownerId()) || Parties.sameParty(owner, other.ownerId());
        if (e instanceof Player p) return p.getUUID().equals(owner) || Parties.sameParty(owner, p.getUUID());
        if (e instanceof com.xenowarriors.soul.SoulEntity soul) { UUID so = soul.ownerId(); return so != null && (so.equals(owner) || Parties.sameParty(owner, so)); }   // (a Soul Splitter's soul)
        UUID petOwner = Parties.petOwner(e);
        return petOwner != null && (petOwner.equals(owner) || Parties.sameParty(petOwner, owner));
    }

    // ---------------------------------------------------------------- adding

    /** The pick screen: characters you're not playing and that aren't already with you. */
    public static void openPick(ServerPlayer p) {
        Set<String> taken = new HashSet<>();
        for (ServerPlayer m : Parties.members(p)) { taken.add(ModCapabilities.getType(m).getId()); for (PartyBotEntity b : bots(m)) taken.add(b.character().getId()); }
        List<String> cooling = new ArrayList<>();
        long now = p.level().getGameTime();
        for (CharacterType t : CharacterType.PLAYABLE) if (p.getPersistentData().getLong(REJOIN + t.getId()) > now) cooling.add(t.getId());
        int free = Math.max(0, Math.min(MAX_BOTS, Parties.MAX_SIZE - partySize(p)));   // (up to 3: you plus three makes a full party)
        ModNetwork.sendToPlayer(new com.xenowarriors.network.OpenBotPickPacket(taken.toArray(new String[0]), cooling.toArray(new String[0]), free), p);
    }

    public static void add(ServerPlayer p, String[] ids) {
        if (!p.isAlive() || com.xenowarriors.vr.Vr.is(p.level())) return;   // (not from the death screen or inside VR)
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(p)) { p.sendSystemMessage(Component.literal("No bot teammates on a Journey - it's just you and your partner.").withStyle(ChatFormatting.RED)); return; }
        int free = Parties.MAX_SIZE - partySize(p);
        Set<String> taken = new HashSet<>();
        for (ServerPlayer m : Parties.members(p)) { taken.add(ModCapabilities.getType(m).getId()); for (PartyBotEntity b : bots(m)) taken.add(b.character().getId()); }
        for (var e : LEFT_BEHIND) if (e.getKey().equals(p.getUUID())) taken.add(e.getValue().getString("char"));   // (one waiting outside the Hekublade is still on the team)
        for (String id : ids) {
            CharacterType t = CharacterType.byId(id);
            if (free <= 0) { p.sendSystemMessage(Component.literal("Your party is full (4 people and bots).").withStyle(ChatFormatting.RED)); return; }
            if (t == CharacterType.NONE || !Arrays.asList(CharacterType.PLAYABLE).contains(t) || taken.contains(id)) continue;
            if ((t == CharacterType.AIONI && taken.contains("rhalix")) || (t == CharacterType.RHALIX && taken.contains("aioni"))) {
                p.sendSystemMessage(Component.literal("Ai-oni and Rhalix can never be in the same party.").withStyle(ChatFormatting.RED)); continue; }
            if (com.xenowarriors.character.Seasons.highest(p.server) < t.season()) { p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + " is Season " + t.season() + " content.").withStyle(ChatFormatting.RED)); continue; }
            if (p.getPersistentData().getLong(REJOIN + id) > p.level().getGameTime()) {
                p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + " is still recovering - try again later.").withStyle(ChatFormatting.RED)); continue;
            }
            spawn(p, t, null);
            taken.add(id);
            free--;
        }
    }

    public static PartyBotEntity spawn(ServerPlayer p, CharacterType t, CompoundTag record) {
        PartyBotEntity b = BotEntities.BOT.get().create(p.serverLevel());
        if (b == null) return null;
        b.setCharacter(t);
        b.setOwner(p.getUUID());
        b.applyLevel(levelFor(p, t));
        com.xenowarriors.compat.DmzBotStats.apply(b);                           // (with DragonMineZ: its character's health and strength, before its saved health goes back in)
        com.xenowarriors.party.Bonds.applyHealth(b);                            // (its bond hearts too, like its owner's - also before the saved health)
        if (record != null) b.loadRecord(record);
        else {                                                                   // a fresh teammate: their own inventory, as you left it
            CompoundTag saved = com.xenowarriors.character.CharacterInventories.checkOut(p, t.getId());
            b.inventory.fromTag(com.xenowarriors.character.CharacterInventories.mainItems(saved));
            ItemStack[] arm = com.xenowarriors.character.CharacterInventories.armour(saved);
            for (int i = 0; i < 4; i++) b.setItemSlot(com.xenowarriors.character.CharacterInventories.ARMOUR_SLOTS[i], arm[i]);
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, arm[4]);
            b.getPersistentData().put("xw_inv_kept", saved);
        }
        if (!teleportNear(b, p)) b.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);   // (nowhere to stand: beside them, never at the world's origin)
        b.setYRot(p.getYRot());
        p.serverLevel().addFreshEntity(b);
        p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, b.getX(), b.getY() + 1, b.getZ(), 12, .3, .5, .3, .02);
        if (record == null) p.sendSystemMessage(Component.literal("").append(b.label()).append(" joined your party (level " + b.botLevel() + ").").withStyle(ChatFormatting.AQUA));
        return b;
    }

    /** The level this player has raised a character to (their own saved stats for it). */
    public static int levelFor(ServerPlayer p, CharacterType t) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> {
            int[] lv = d.getType() == t ? d.statLevel : d.charStats.getCompound(t.getId()).getIntArray("lv");
            int sum = 0; for (int v : lv) sum += v;
            return Math.max(1, com.xenowarriors.stats.Stats.fromStats(sum));
        }).orElse(1);
    }

    /** A bot's own level of one stat (Speed / Attack / Defense): its owner's saved stats for that character, as levelFor. */
    public static int statLevel(PartyBotEntity b, Stat stat) {
        ServerPlayer o = b.owner();
        if (o == null) return 0;
        CharacterType t = b.character();
        return o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> {
            int[] lv = d.getType() == t ? d.statLevel : d.charStats.getCompound(t.getId()).getIntArray("lv");
            int i = stat.ordinal();
            return i < lv.length ? Math.max(0, lv[i]) : 0;
        }).orElse(0);
    }

    /** XP the bot earns goes to the owner's saved stats for that character (so switching to it keeps the level). */
    public static void addXp(PartyBotEntity b, Stat stat, float amount) {
        ServerPlayer o = b.owner();
        if (o == null || amount <= 0) return;
        CharacterType t = b.character();
        o.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.getType() == t) return;                                            // you're playing it yourself
            CompoundTag c = d.charStats.getCompound(t.getId());
            int[] lv = c.getIntArray("lv"); if (lv.length != 3) lv = new int[3];
            int i = stat.ordinal();
            float xp = c.getFloat("x" + i) + amount;
            boolean up = false;
            while (lv[i] < Stat.MAX_LEVEL && xp >= stat.needed(lv[i])) { xp -= stat.needed(lv[i]); lv[i]++; up = true; }
            c.putIntArray("lv", lv); c.putFloat("x" + i, xp);
            d.charStats.put(t.getId(), c);
            if (up) {
                int before = b.botLevel();
                b.applyLevel(levelFor(o, t));
                if (b.botLevel() > before) o.displayClientMessage(Component.literal("").append(b.label()).append(" reached level " + b.botLevel() + "!").withStyle(ChatFormatting.GOLD), true);
            }
        });
    }

    /** Whether a bot of this character may come along to a dimension (only Kaeru follows Mikiko into the Hekublade). */
    public static boolean canFollowTo(CharacterType t, net.minecraft.world.level.Level level) {
        return level.dimension() != com.xenowarriors.mikiko.HekubladeRealm.KEY || t == CharacterType.KAERU;
    }

    /** A character's things back into its save when a bot leaves. */
    public static void checkIn(PartyBotEntity b, ServerPlayer o) {
        if (b.journey()) return;
        BotCrafting.forget(b.getUUID());
        if (o == null) {                                                          // owner offline (the void, a /kill while they're away): kept for when they're back
            if (b.ownerId() != null) PENDING_HOME.computeIfAbsent(b.ownerId(), k -> new ArrayList<>()).add(b.record());
        } else {
            ItemStack held = BotGear.stowHand(b);                                // (what's in its hand goes home too)
            CompoundTag kept = b.getPersistentData().getCompound("xw_inv_kept").copy();
            if (!held.isEmpty() && !intoEnder(kept, held) && !o.getInventory().add(held)) o.drop(held, false);   // (bag full: its ender chest, then you)
            com.xenowarriors.character.CharacterInventories.checkIn(o, b.character().getId(), b.inventory.createTag(), b.armour(),
                    b.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND), kept);
        }
        // everything went home: nothing left on it to drop or count twice
        b.inventory.clearContent();
        for (net.minecraft.world.entity.EquipmentSlot es : net.minecraft.world.entity.EquipmentSlot.values()) b.setItemSlot(es, ItemStack.EMPTY);
        b.getPersistentData().remove("xw_inv_kept");
    }

    /** Things of bots whose owner was offline when they went home (handed to the save when the owner logs in). */
    static final Map<UUID, List<CompoundTag>> PENDING_HOME = new HashMap<>();

    /** A saved bot record's things into that character's save (see record()). */
    static void homeFromRecord(ServerPlayer o, CompoundTag rec) {
        ListTag inv = rec.getList("inv", 10).copy();
        ListTag arm = rec.getList("armour", 10);
        ItemStack[] armour = new ItemStack[4];
        for (int i = 0; i < 4; i++) armour[i] = i < arm.size() ? ItemStack.of(arm.getCompound(i)) : ItemStack.EMPTY;
        ItemStack off = arm.size() > 4 ? ItemStack.of(arm.getCompound(4)) : ItemStack.EMPTY;
        ItemStack hand = arm.size() > 5 ? ItemStack.of(arm.getCompound(5)) : ItemStack.EMPTY;
        CompoundTag kept = rec.getCompound("kept").copy();
        if (!hand.isEmpty()) {
            if (inv.size() < 36) inv.add(hand.save(new CompoundTag()));
            else if (!intoEnder(kept, hand) && !o.getInventory().add(hand)) o.drop(hand, false);
        }
        com.xenowarriors.character.CharacterInventories.checkIn(o, rec.getString("char"), inv, armour, off, kept);
    }

    /** Into the ender chest part of a character's save, if there's a free slot. */
    private static boolean intoEnder(CompoundTag kept, ItemStack s) {
        ListTag ender = kept.getList("ender", 10);
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < ender.size(); i++) used.add(ender.getCompound(i).getByte("Slot") & 255);
        for (int slot = 0; slot < 27; slot++) if (!used.contains(slot)) {
            CompoundTag t = s.save(new CompoundTag());
            t.putByte("Slot", (byte) slot);
            ender.add(t);
            kept.put("ender", ender);
            return true;
        }
        return false;
    }

    /** In the air and not on anything: the owner is flying (wings, creative, elytra) or falling a long way. */
    public static boolean airborne(ServerPlayer o) {
        if (o.getAbilities().flying || o.isFallFlying()) return true;
        if (o.onGround() || o.isInWater() || o.isPassenger()) return false;
        return com.xenowarriors.character.RhalixFlight.heightAboveGround(o) > 3.5;
    }

    /** Flying along with the owner (bots that can fly): straight towards them, hovering a few blocks off. */
    static void flyToward(PartyBotEntity b, ServerPlayer o) {
        b.getNavigation().stop();
        b.setNoGravity(true);
        Vec3 goal = o.position().add(-o.getLookAngle().x * 2.5, 0.5, -o.getLookAngle().z * 2.5);
        Vec3 d = goal.subtract(b.position());
        double dist = d.length();
        if (dist > 48) { teleportNear(b, o); b.setNoGravity(true); return; }
        double speed = dist > 10 ? 0.9 : dist > 4 ? 0.55 : 0.25;
        Vec3 v = dist < 1.5 ? Vec3.ZERO : d.normalize().scale(speed);
        b.setDeltaMovement(b.getDeltaMovement().scale(0.5).add(v.scale(0.5)));
        b.fallDistance = 0;
        b.getLookControl().setLookAt(o);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
        if (dist > 1.5) { b.setYRot((float) yaw); b.yBodyRot = (float) yaw; }
    }

    /** The owner's in a vehicle (a mod vehicle or a boat) with a free seat: climb in. */
    static boolean boardWith(PartyBotEntity b, ServerPlayer o) {
        Entity v = o.getVehicle();
        if (v == null || b.isPassenger() || b.distanceToSqr(v) > 5 * 5) return false;
        if (b.downed() || b.reviving != null || com.xenowarriors.gov.HealingPods.inPod(b)) return false;   // (down, in someone's revive circle or resting in a pod: it stays put)
        if (v instanceof com.xenowarriors.vehicle.VehicleEntity || v instanceof net.minecraft.world.entity.vehicle.Boat) return b.startRiding(v, false);
        return false;
    }

    /** Bots get out when their owner does. */
    static void unboard(PartyBotEntity b, ServerPlayer o) {
        if (b.isPassenger() && b.getVehicle() != o && o.getVehicle() == null) b.stopRiding();
    }

    /** A party member who's down (the owner, a party player, or another bot) within 40 blocks, nearest first. */
    static LivingEntity someoneDown(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null) return null;
        LivingEntity best = null; double bd = 40 * 40;
        for (ServerPlayer m : Parties.members(o)) if (m.level() == b.level() && com.xenowarriors.party.Duels.isDowned(m) && m.distanceToSqr(b) < bd) { best = m; bd = m.distanceToSqr(b); }
        if (!Parties.inParty(o) && o.level() == b.level() && com.xenowarriors.party.Duels.isDowned(o) && o.distanceToSqr(b) < bd) { best = o; bd = o.distanceToSqr(b); }
        for (ServerPlayer m : Parties.members(o)) for (PartyBotEntity ob : bots(m)) if (ob != b && ob.downed() && ob.level() == b.level() && ob.distanceToSqr(b) < bd) { best = ob; bd = ob.distanceToSqr(b); }
        return best;
    }

    public static boolean isDown(LivingEntity e) {
        return e instanceof PartyBotEntity b ? b.downed() : e instanceof ServerPlayer p && com.xenowarriors.party.Duels.isDowned(p);
    }

    /** A bot's things: armour, offhand, hand and its bag. */
    public static void openBag(ServerPlayer p, PartyBotEntity b) {
        net.minecraftforge.network.NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new BotMenu(id, inv, b.inventory, new BotMenu.GearContainer(b), b), b.label()),
                buf -> buf.writeVarInt(b.getId()));
    }

    /** Puts on the best armour in its bag (so you can just hand it armour). */
    static void wearBestArmour(PartyBotEntity b) {
        if (b.downed()) return;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!(s.getItem() instanceof net.minecraft.world.item.ArmorItem ai)) continue;
            ItemStack worn = b.getItemBySlot(ai.getEquipmentSlot());
            float have = worn.getItem() instanceof net.minecraft.world.item.ArmorItem wa ? wa.getDefense() * 10 + wa.getToughness() : -1F;
            if (ai.getDefense() * 10 + ai.getToughness() > have) {
                b.setItemSlot(ai.getEquipmentSlot(), s.copyWithCount(1));
                s.shrink(1);
                if (s.isEmpty()) b.inventory.setItem(i, ItemStack.EMPTY);
                if (!worn.isEmpty()) b.inventory.addItem(worn);
                b.level().playSound(null, b.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.NEUTRAL, 0.8F, 1F);
            }
        }
    }

    // ---------------------------------------------------------------- bots on a Journey: put away and brought back

    private static final String STASH = "xw_bots_stash";

    /** A Journey is just you and your partner: bots are put away (with their things) and come back afterwards. */
    public static void stashAll(ServerPlayer p) {
        ListTag list = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getList(STASH, 10);
        for (PartyBotEntity b : bots(p)) {
            if (b.journey()) continue;
            if (!b.downed()) list.add(b.record());
            else checkIn(b, p);
            forgetFake(b.getUUID()); b.discard();
        }
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(STASH, list);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static void unstash(ServerPlayer p) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = persisted.getList(STASH, 10);
        if (list.isEmpty()) return;
        persisted.remove(STASH);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        for (int i = 0; i < list.size(); i++) spawn(p, CharacterType.byId(list.getCompound(i).getString("char")), list.getCompound(i));
        p.displayClientMessage(Component.literal("Your bot teammates are back.").withStyle(ChatFormatting.AQUA), true);
    }

    /** Moves the bot next to its owner (another world: a fresh copy there, things and all). False when there was nowhere to put it. */
    public static boolean teleportNear(PartyBotEntity b, ServerPlayer o) {
        if (b.level() != o.level()) {
            if (b.journey()) { b.discard(); return true; }
            if (!canFollowTo(b.character(), o.level())) return false;                 // (waits outside the Hekublade)
            CompoundTag rec = b.record();
            CharacterType t = b.character();
            forgetFake(b.getUUID());
            b.discard();
            spawn(o, t, rec);
            return true;
        }
        Vec3 at = placeNear(o, b.canFly());
        if (at == null) return false;
        if (b.isPassenger() && b.getVehicle() != o.getVehicle()) b.stopRiding();
        b.moveTo(at.x, at.y, at.z, b.getYRot(), 0);
        b.getNavigation().stop();
        b.fallDistance = 0;
        b.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /**
     * Somewhere a bot can be put by its owner: a free spot to stand 2-3 blocks off (never into a block, lava or thin
     * air); flying, the ground under them (up to 64 blocks down); a flier can also just be put in the air beside them.
     * Null: nowhere yet (high over the void or open ocean, for a walker).
     */
    static Vec3 placeNear(ServerPlayer o, boolean flier) {
        net.minecraft.world.level.Level l = o.level();
        double a = o.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < 8; i++) {
            double ang = a + i * Math.PI / 4, r = 2 + (i % 2);
            BlockPos at = BlockPos.containing(o.getX() + Math.cos(ang) * r, o.getY(), o.getZ() + Math.sin(ang) * r);
            for (int dy = 0; dy >= -3; dy--) { BlockPos q = at.offset(0, dy, 0); if (standable(l, q)) return Vec3.atBottomCenterOf(q); }
        }
        if (o.onGround()) return o.position();
        BlockPos below = o.blockPosition();                                      // (in the air, on elytra or in a flying vehicle: the ground under them)
        for (int dy = 1; dy <= 64 && below.getY() - dy > l.getMinBuildHeight(); dy++) {
            BlockPos q = below.below(dy);
            if (standable(l, q)) return Vec3.atBottomCenterOf(q);
            if (!l.getBlockState(q).isAir() && !l.getFluidState(q).isEmpty()) { if (l.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER) && l.getBlockState(q.above()).isAir()) return Vec3.atBottomCenterOf(q.above()); break; }
            if (!l.getBlockState(q).getCollisionShape(l, q).isEmpty()) break;
        }
        if (flier) {
            Vec3 side = o.position().add(o.getLookAngle().multiply(-2.5, 0, -2.5));
            if (l.noCollision(new net.minecraft.world.phys.AABB(side, side.add(0.6, 1.8, 0.6)).move(-0.3, 0, -0.3))) return side;
        }
        return null;
    }

    private static boolean standable(net.minecraft.world.level.Level l, BlockPos q) {
        BlockState under = l.getBlockState(q.below());
        return l.getBlockState(q).isAir() && l.getBlockState(q.above()).isAir() && under.isSolid()
                && !under.is(net.minecraft.world.level.block.Blocks.LAVA) && !under.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }

    /**
     * Twice a second, from the owner's tick: a bot that's fallen far behind (fast travel, elytra, a vehicle, a respawn,
     * an ender pearl) or whose chunk has stopped ticking - where it would stand frozen until the chunk unloads - is
     * brought over. With nowhere to put it yet it goes on the waiting list (things and all) until there is.
     */
    static void catchUp(ServerPlayer o) {
        for (PartyBotEntity b : bots(o)) {
            if (b.journey() || b.isRemoved() || b.stranded) continue;
            if (b.level() != o.level()) {                                        // (its own tick does this - unless it isn't ticking)
                if (o.isAlive() && canFollowTo(b.character(), o.level())) teleportNear(b, o);
                continue;
            }
            boolean ticking = ((ServerLevel) b.level()).isPositionEntityTicking(b.blockPosition());
            double far = b.canFly() ? 64 : 48, d2 = b.distanceToSqr(o);
            if ((ticking || d2 < 16 * 16) && d2 < far * far) continue;           // (right by them: their own chunks may just not be ticking yet, after a teleport)
            if (b.isPassenger() && b.getVehicle() == o.getVehicle()) continue;   // (riding along with them)
            if (b.downed()) { if (!ticking) { testLog(b, "left behind while down - goes home"); bleedOut(b); } continue; }
            if (!o.isAlive()) continue;
            if (teleportNear(b, o)) { b.catchUps++; testLog(b, "caught up (" + (ticking ? "far" : "chunk not ticking") + ", " + (int) Math.sqrt(d2) + " blocks)"); continue; }
            if (!ticking) {                                                      // nowhere to put it, and it can't wait where it is: it waits on the list
                b.stranded = true;
                LEFT_BEHIND.add(Map.entry(o.getUUID(), b.record()));
                forgetFake(b.getUUID()); b.discard();
                testLog(b, "nowhere to stand by the owner yet - waiting list");
            }
        }
    }

    // ---------------------------------------------------------------- skills (through a stand-in player)

    /** A bot is gone for good: its stand-in FakePlayer goes with it. */
    public static void forgetFake(UUID bot) {
        FakePlayer f = FAKES.remove(bot);
        if (f != null) FAKE_TO_BOT.remove(f.getUUID());
        com.xenowarriors.compat.DmzBotKi.forget(bot);
        BotCrafting.forget(bot);                                                  // (an offer to a bot that's gone: logout, owner away)
        BotSupport.forget(bot);
        ANIM_SENT.remove(bot);
    }

    private static final Map<UUID, Boolean> ANIM_SENT = new HashMap<>();

    /** Its stand-in's skill sent a body animation (relayed to those who see the bot, see ModNetwork). */
    public static void animSent(PartyBotEntity b) { ANIM_SENT.put(b.getUUID(), true); }

    /** The bot's stand-in player (for the tests). */
    public static FakePlayer standIn(PartyBotEntity b) { return fake(b); }

    private static FakePlayer fake(PartyBotEntity b) {
        ServerLevel s = (ServerLevel) b.level();
        FakePlayer f = FAKES.get(b.getUUID());
        if (f == null || f.level() != s) {
            String name = ("xwbot_" + b.character().getId() + "_" + b.getUUID().toString().substring(0, 4));
            f = FakePlayerFactory.get(s, new GameProfile(UUID.nameUUIDFromBytes(("xwbot" + b.ownerId() + b.character().getId()).getBytes()), name.substring(0, Math.min(16, name.length()))));   // (one per owner + character: the factory's cache stays small)
            FAKES.put(b.getUUID(), f);
            FAKE_TO_BOT.put(f.getUUID(), b.getUUID());
        }
        final FakePlayer ff = f;
        ServerPlayer o = b.owner();
        f.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            d.setType(b.character());
            if (o != null) o.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(od -> {
                int[] lv = od.getType() == b.character() ? od.statLevel : od.charStats.getCompound(b.character().getId()).getIntArray("lv");
                for (int i = 0; i < 3; i++) d.statLevel[i] = i < lv.length ? lv[i] : 0;
                d.loadouts = od.loadouts.copy();
                d.youSlot = od.youSlot;                                  // (a You owner's bots can use their skills)
                for (int i = 0; i < d.youSlots.length; i++) d.youSlots[i] = od.youSlots[i].copy();
            });
            d.setKarma(100F);
        });
        return ff;
    }

    /** Worth a skill: bosses, players and anything as tough as the bot (weak mobs just get hit). */
    static boolean strong(PartyBotEntity b, LivingEntity t) {
        if (t instanceof Player || t instanceof PartyBotEntity) return true;
        if (t.getPersistentData().getBoolean("xw_boss") || t.getPersistentData().getBoolean("xw_miniboss") || t.getTags().contains("xw_miniboss")
                || t instanceof com.xenowarriors.corrupted.CSoulBossEntity || t.getType().getCategory() == net.minecraft.world.entity.MobCategory.MISC && t.getMaxHealth() > 100) return true;
        if (t.getMaxHealth() >= b.getMaxHealth() * 0.6F) return true;
        return com.xenowarriors.karma.KarmaLevel.of(t) >= com.xenowarriors.karma.KarmaLevel.of(b) * 0.6;
    }

    static void trySkill(PartyBotEntity b, LivingEntity target) {
        if (b.sparring() && b.getRandom().nextInt(3) != 0) return;
        if (BotTeamUlt.tick(b, target)) return;                                  // a team ultimate with its partner (or asking its player for one)
        if (!b.sparring() && !strong(b, target)) return;                         // save the Karma for enemies that matter
        if (b.journey() && com.xenowarriors.journey.Journey.noSkills(b.owner())) return;   // ("No skills" battle: teammates fight with basic attacks too)
        double dist = b.distanceTo(target);
        if (dist > 20) return;
        if (com.xenowarriors.compat.DmzBotKi.tryFire(b, target, () -> fake(b), false)) return;   // (with DragonMineZ: sometimes one of its equipped ki techniques instead)
        long now = b.level().getGameTime();
        ServerPlayer o = b.owner();
        int[] loadout = o == null ? SkillPool.defaultLoadout() : o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadoutWithUlt(b.character())).orElse(SkillPool.defaultLoadout());   // (with the ultimate picked for that character)
        if (loadout.length > com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT && loadout[com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT] > 0) {    // (the picked one only once the bot itself is strong enough - a pick made in creative doesn't count)
            var ults = SkillPool.ultimates(b.character());
            int c = loadout[com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT];
            if (c >= ults.size() || !SkillPool.available(ults.get(c), b.botLevel(), false, com.xenowarriors.character.Seasons.highest(b.getServer()))) loadout[com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT] = 0;
        }
        int slot = b.getRandom().nextInt(5);
        if (slot == 4 && (b.getHealth() > b.getMaxHealth() * 0.6F || b.karma < 60)) slot = b.getRandom().nextInt(4);    // ultimates when it matters
        if (slot == 4 && BotTeamUlt.saveUltimate(b)) slot = b.getRandom().nextInt(4);   // (saving it for a team ultimate with its partner)
        if (now < b.skillReady[slot]) return;
        Skill skill = SkillPool.inSlot(b.character(), loadout, slot);
        if (skill == null) return;
        float cost = com.xenowarriors.skill.SkillLevels.costFor(b, skill.getId(), skill.getKarmaCost());   // (the owner's ability level for this character)
        if (com.xenowarriors.party.Synergy.has(b, com.xenowarriors.party.Synergy.OLD_FRIENDS))                // Old Friends: cheaper skills, as for a player
            cost = Math.round(cost * (1F - Math.min(0.5F, 0.25F * com.xenowarriors.party.Synergy.strength(b, com.xenowarriors.party.Synergy.OLD_FRIENDS))));
        if (b.karma < cost) return;
        FakePlayer f = fake(b);
        Vec3 eye = b.getEyePosition(), to = target.getBoundingBox().getCenter();
        Vec3 d = to.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        f.moveTo(b.getX(), b.getY(), b.getZ(), yaw, pitch);
        f.setYHeadRot(yaw);
        f.setOnGround(b.onGround());
        ANIM_SENT.remove(b.getUUID());
        boolean used = standInUse(b, f, skill);
        if (used) {
            b.karma -= cost;
            b.skillReady[slot] = now + Math.max(40, com.xenowarriors.skill.SkillLevels.cooldownFor(b, skill.getId(), skill.getCooldownTicks()));
            com.xenowarriors.skill.SkillLevels.used(b, skill);                    // (its uses level the skill up, like a player's: its character's ability levels)
            b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            Integer gen = com.xenowarriors.network.SkillAnimPacket.GENERIC.get(skill.getId());   // the same body animation a player gets for it
            if (gen != null) ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.SkillAnimPacket(f.getId(), gen, !b.onGround()), f);
            if (ANIM_SENT.remove(b.getUUID()) == null) com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.KA_BREAK, 10);   // (a skill without one of its own)
        } else b.skillReady[slot] = now + 60;
    }

    /** A support skill (Kaeru's Fox Heal) used facing someone: true if it went off (its body animation plays, as with trySkill). */
    static boolean castAt(PartyBotEntity b, Skill skill, LivingEntity at) {
        FakePlayer f = fake(b);
        Vec3 d = at.getBoundingBox().getCenter().subtract(b.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        f.moveTo(b.getX(), b.getY(), b.getZ(), yaw, pitch);
        f.setYHeadRot(yaw);
        f.setOnGround(b.onGround());
        ANIM_SENT.remove(b.getUUID());
        boolean used = standInUse(b, f, skill);
        if (used) {
            com.xenowarriors.skill.SkillLevels.used(b, skill);
            b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            Integer gen = com.xenowarriors.network.SkillAnimPacket.GENERIC.get(skill.getId());
            if (gen != null) ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.SkillAnimPacket(f.getId(), gen, !b.onGround()), f);
            if (ANIM_SENT.remove(b.getUUID()) == null) com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.KA_BREAK, 10);
        }
        return used;
    }

    /** The stand-in uses the skill. It mirrors the bot's state first, so what the skill does to its user lands on the bot (see toBot). */
    private static boolean standInUse(PartyBotEntity b, FakePlayer f, Skill skill) {
        f.removeAllEffects();
        f.setDeltaMovement(Vec3.ZERO);
        f.setHealth(Math.max(1F, f.getMaxHealth() * b.getHealth() / b.getMaxHealth()));
        float h0 = f.getHealth();
        Vec3 at0 = f.position();
        boolean used;
        try { used = com.xenowarriors.combat.SkillContext.callSkill(f, skill.getId(), () -> skill.use(f)); } catch (Throwable t) { used = false; }
        toBot(b, f, h0, at0);
        return used;
    }

    /** (tests) the bot uses this skill through its stand-in, standing where it is. */
    public static boolean useForTest(PartyBotEntity b, Skill skill) {
        FakePlayer f = fake(b);
        f.moveTo(b.getX(), b.getY(), b.getZ(), b.getYRot(), 0F);
        return standInUse(b, f, skill);
    }

    /**
     * A skill's effect on its own user (a self-buff or its cost, a heal, a dash or a blink) went to the stand-in, which
     * never ticks or moves: hand it to the bot. Effects move over (the stand-in keeps none - they'd never wear off on it),
     * health changes carry over as a share of max health, a push becomes the bot's, and a teleport moves the bot.
     */
    private static void toBot(PartyBotEntity b, FakePlayer f, float h0, Vec3 at0) {
        try {
            for (net.minecraft.world.effect.MobEffectInstance e : new ArrayList<>(f.getActiveEffects())) b.addEffect(new net.minecraft.world.effect.MobEffectInstance(e));
            f.removeAllEffects();
            float dh = f.getHealth() - h0;
            if (Math.abs(dh) > 0.01F && b.isAlive()) b.setHealth(Math.max(1F, Math.min(b.getMaxHealth(), b.getHealth() + dh / f.getMaxHealth() * b.getMaxHealth())));
            Vec3 v = f.getDeltaMovement();
            if (v.lengthSqr() > 1.0E-4) { b.setDeltaMovement(b.getDeltaMovement().add(v)); b.hurtMarked = true; f.setDeltaMovement(Vec3.ZERO); }
            if (f.level() == b.level() && f.position().distanceToSqr(at0) > 1.0 && f.position().distanceToSqr(at0) < 64 * 64) b.teleportTo(f.getX(), f.getY(), f.getZ());
        } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------------------- mining mode

    public static boolean isOre(BlockState s) {
        return s.is(Tags.Blocks.ORES) || s.is(com.xenowarriors.sky.ModBlocks.SKY_ORE.get()) || s.is(com.xenowarriors.sky.ModBlocks.DATA_ORE.get());
    }

    static BlockPos findOre(PartyBotEntity b, ServerPlayer o) {
        boolean ultima = b.character() == CharacterType.ULTIMA;
        BlockPos c = o.blockPosition(), best = null;
        double bd = 1e9;
        List<Long> seen = new ArrayList<>(); List<Integer> kinds = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-10, -6, -10), c.offset(10, 6, 10))) {
            BlockState s = b.level().getBlockState(p);
            if (s.is(com.xenowarriors.sky.ModBlocks.CORRUPTED_ORE.get())) { if (ultima) return null; continue; }   // Data Sense can't read it
            if (!isOre(s)) continue;
            if (exposed(b, p) && !b.unreachable(p)) {                            // (buried ore it can't get to isn't a target)
                double dd = p.distSqr(b.blockPosition());
                if (dd < bd) { bd = dd; best = p.immutable(); }
            }
            seen.add(p.asLong()); kinds.add(s.is(com.xenowarriors.sky.ModBlocks.DATA_ORE.get()) ? 4 : s.is(Tags.Blocks.ORES_DIAMOND) || s.is(Tags.Blocks.ORES_EMERALD) ? 3 : s.is(Tags.Blocks.ORES_GOLD) ? 1 : 0);
        }
        if (!seen.isEmpty() && b.tickCount % 200 < 20)                       // shows you the ore it senses
            ModNetwork.sendToPlayer(new com.xenowarriors.network.DataSensedPacket(new int[0], seen.stream().mapToLong(Long::longValue).toArray(),
                    kinds.stream().mapToInt(Integer::intValue).toArray(), 200, false), o);
        return best;
    }

    /** (For tests) the ore it would pick now, and whether a block counts as open to the air. */
    public static BlockPos findOreForTest(PartyBotEntity b, ServerPlayer o) { return findOre(b, o); }
    public static boolean exposedForTest(PartyBotEntity b, BlockPos p) { return exposed(b, p); }

    /** Open to the air on some side (in a cave, a cliff, a hillside): somewhere a bot can stand next to it. */
    static boolean exposed(PartyBotEntity b, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
            if (!b.level().getBlockState(p.relative(d)).isSolidRender(b.level(), p.relative(d))) return true;
        return false;
    }

    static void mine(PartyBotEntity b, BlockPos pos) {
        ServerLevel s = (ServerLevel) b.level();
        BlockState st = s.getBlockState(pos);
        if (!isOre(st) || !com.xenowarriors.modes.ModeRules.canGrief(s)) return;
        ItemStack tool = b.getMainHandItem().isCorrectToolForDrops(st) ? b.getMainHandItem() : new ItemStack(Items.DIAMOND_PICKAXE);   // (its own pickaxe - Fortune and all - or its bare Karma)
        List<ItemStack> drops = Block.getDrops(st, s, pos, s.getBlockEntity(pos), b, tool);
        s.destroyBlock(pos, false, b);
        for (ItemStack d : drops) {
            ItemStack left = b.inventory.addItem(d);
            if (!left.isEmpty()) { Block.popResource(s, pos, left); notifyFull(b); }
        }
        addXp(b, Stat.SPEED, 2F);
    }

    static void notifyFull(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null || b.tickCount - b.fullNotice < 400) return;
        b.fullNotice = b.tickCount;
        o.displayClientMessage(Component.literal("").append(b.label()).append("'s inventory is full!").withStyle(ChatFormatting.YELLOW), false);
    }

    /** Pets' bags too. */
    public static void notifyPetFull(Entity pet, ServerPlayer o) {
        long now = o.level().getGameTime();
        if (now - pet.getPersistentData().getLong("xw_full_notice") < 400) return;
        pet.getPersistentData().putLong("xw_full_notice", now);
        o.displayClientMessage(Component.literal(pet.getDisplayName().getString() + "'s bag is full!").withStyle(ChatFormatting.YELLOW), false);
    }

    // ---------------------------------------------------------------- going down

    static void downed(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o != null) o.displayClientMessage(Component.literal("").append(b.label()).append(" is down! Stand in their circle for 2 seconds to revive them (30s)").withStyle(ChatFormatting.RED), false);
        b.level().playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1F, 0.6F);
    }

    /** (Tests) runs out its downed timer now. */
    public static void bleedOutForTest(PartyBotEntity b) { bleedOut(b); }

    /** A downed bot whose chunk left the world (its owner went far): its things go home and it recovers, as from a bleed-out (no world changes: called as it leaves). */
    static void strandedDown(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o != null) {
            o.getPersistentData().putLong(REJOIN + b.character().getId(), o.level().getGameTime() + REJOIN_COOLDOWN);
            o.displayClientMessage(Component.literal("").append(b.label()).append(" was left behind while down and went home to recover (5 min). They keep all their things.").withStyle(ChatFormatting.GRAY), false);
        }
        checkIn(b, o);
    }

    static void bleedOut(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o != null) {
            o.getPersistentData().putLong(REJOIN + b.character().getId(), o.level().getGameTime() + REJOIN_COOLDOWN);
            o.displayClientMessage(Component.literal("").append(b.label()).append(" wasn't revived and left the party to recover (5 min). They keep all their things.").withStyle(ChatFormatting.GRAY), false);
            if (b.journey()) for (int i = 0; i < b.inventory.getContainerSize(); i++) { ItemStack it = b.inventory.removeItemNoUpdate(i); if (!it.isEmpty() && !o.getInventory().add(it)) o.drop(it, false); }   // (a Journey teammate's finds come to you)
            else checkIn(b, o);                                                   // (their things wait in their own save)
        } else checkIn(b, null);                                                  // (owner away: kept for when they're back)
        forgetFake(b.getUUID());
        b.discard();
        if (o != null && !b.journey()) com.xenowarriors.party.PartySwap.botLost(o, b.character());   // (Party Swap on: someone else comes in)
    }

    // ---------------------------------------------------------------- sparring (party PvP XP)

    static void sparOver(PartyBotEntity b, boolean botWon) {
        b.setSparring(false);
        b.setHealth(Math.max(b.getHealth(), b.getMaxHealth() * 0.5F));
        ServerPlayer o = b.owner();
        if (o == null) return;
        o.displayClientMessage(Component.literal(botWon ? "" : "You beat ").append(b.label()).append(botWon ? " wins the spar!" : " in a spar!").withStyle(ChatFormatting.GOLD), false);
        sparReward(o, b.botLevel(), !botWon);
    }

    /** Sparring a party member (bot or player): good XP, more against someone stronger - big XP only once per 30 min (not shown). */
    public static void sparReward(ServerPlayer p, int otherLevel, boolean won) {
        long now = p.level().getGameTime();
        boolean big = now - p.getPersistentData().getLong(SPAR_LAST) >= SPAR_XP_COOLDOWN;
        int my = com.xenowarriors.stats.Stats.characterLevel(p);
        float mult = (big ? 1F : 0.1F) * (won ? 1F : 0.6F) * Math.max(0.5F, Math.min(4F, 1F + (otherLevel - my) / 10F));
        if (big) p.getPersistentData().putLong(SPAR_LAST, now);
        for (Stat s : Stat.values()) {
            int lv = com.xenowarriors.stats.Stats.level(p, s);
            com.xenowarriors.stats.Stats.addXp(p, s, s.needed(lv) * 0.8F * mult);
        }
    }

    // ---------------------------------------------------------------- right-click menu actions

    public static void interact(PartyBotEntity b, ServerPlayer p) {
        if (b.journey()) return;
        if (!p.getUUID().equals(b.ownerId()) && !(b.ownerId() != null && Parties.sameParty(p.getUUID(), b.ownerId()))) return;
        if (b.downed()) {
            p.displayClientMessage(Component.literal("Stand in the circle for 2 seconds to revive them").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!p.getUUID().equals(b.ownerId())) return;
        List<String> names = new ArrayList<>(); List<Integer> ids = new ArrayList<>();
        for (LivingEntity pet : petsFor(p, b.character())) { names.add(pet.getDisplayName().getString()); ids.add(pet.getId()); }
        ModNetwork.sendToPlayer(new com.xenowarriors.network.OpenBotMenuPacket(b.getId(), b.character().getId(), b.mode(), b.botLevel(), b.canFly(),
                names.toArray(new String[0]), ids.stream().mapToInt(Integer::intValue).toArray()), p);
    }

    /** The owner's pets that belong to this character (Mikiko's foxes, Rhalix's winged companions). */
    static List<LivingEntity> petsFor(ServerPlayer p, CharacterType t) {
        List<LivingEntity> out = new ArrayList<>();
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            ListTag tags = t == CharacterType.MIKIKO ? d.pets : t == CharacterType.RHALIX ? d.wingPets : new ListTag();
            for (int i = 0; i < tags.size(); i++) {
                CompoundTag rec = tags.getCompound(i);
                if (!rec.hasUUID("uuid") || rec.getBoolean("dead")) continue;
                if (p.serverLevel().getEntity(rec.getUUID("uuid")) instanceof LivingEntity le && le.isAlive()) out.add(le);
            }
        });
        return out;
    }

    public static final int ACT_INVENTORY = 0, ACT_SUMMON_PET = 1, ACT_FLY = 2, ACT_SPAR = 3, ACT_MODE = 4, ACT_DISMISS = 5, ACT_MENU = 6, ACT_CARRY = 7, ACT_DMZ = 8, ACT_FUSE = 9;

    public static void action(ServerPlayer p, int botId, int action, int arg) {
        if (!(p.serverLevel().getEntity(botId) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId()) || b.distanceToSqr(p) > 32 * 32) return;
        if (b.journey()) { p.displayClientMessage(Component.literal("Journey teammates just fight - no menu in battle").withStyle(ChatFormatting.GRAY), true); return; }
        switch (action) {
            case ACT_INVENTORY -> openBag(p, b);
            case ACT_CARRY -> {                                                   // Rhalix picks them up and flies them
                if (!com.xenowarriors.character.RhalixWings.out(p) || !com.xenowarriors.character.RhalixFlight.winged(p)) { p.displayClientMessage(Component.literal("You need your wings out to carry someone").withStyle(ChatFormatting.GRAY), true); return; }
                b.startRiding(p, true);
                p.displayClientMessage(Component.literal("Carrying ").append(b.label()).append(" - land to set them down").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            case ACT_SUMMON_PET -> {
                if (p.serverLevel().getEntity(arg) instanceof net.minecraft.world.entity.Mob pet && Parties.petOwner(pet) != null && Parties.petOwner(pet).equals(p.getUUID())) {
                    com.xenowarriors.wing.WingPets.teleportNear(pet, b);
                    p.displayClientMessage(Component.literal(pet.getDisplayName().getString() + " comes to ").append(b.label()).withStyle(ChatFormatting.AQUA), true);
                }
            }
            case ACT_FLY -> {
                if (!b.canFly()) { p.displayClientMessage(Component.literal("").append(b.label()).append(" can't fly").withStyle(ChatFormatting.GRAY), true); return; }
                p.startRiding(b, true);
                p.displayClientMessage(Component.literal("Flying with ").append(b.label()).append(" - look to steer, sneak to let go").withStyle(ChatFormatting.AQUA), true);
            }
            case ACT_SPAR -> {
                b.setSparring(true);
                b.setTarget(p);
                p.displayClientMessage(Component.literal("Spar with ").append(b.label()).append("! (nobody dies)").withStyle(ChatFormatting.GOLD), true);
            }
            case ACT_MODE -> {
                b.setMode(arg);
                p.displayClientMessage(Component.literal("").append(b.label()).append(": " + PartyBotEntity.MODES[b.mode()] + " mode").withStyle(ChatFormatting.AQUA), true);
            }
            case ACT_DISMISS -> {
                checkIn(b, p);
                b.discard();
                p.displayClientMessage(Component.literal("").append(b.label()).append(" left the party (their things are kept for when you play them).").withStyle(ChatFormatting.GRAY), true);
            }
            case ACT_MENU -> interact(b, p);
            case ACT_DMZ -> {                                                     // (with DragonMineZ: its DMZ character's menu)
                if (com.xenowarriors.compat.DmzPerCharacter.beginView(p, b))
                    com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.BotDmzPacket(com.xenowarriors.network.BotDmzPacket.OPEN, b.label().getString()), p);
                else p.displayClientMessage(Component.literal("").append(b.label()).append("'s Dragon Ball character can't be opened right now").withStyle(ChatFormatting.GRAY), true);
            }
            case ACT_FUSE -> com.xenowarriors.compat.DmzFusion.fuse(p, b);      // (with DragonMineZ: Potara earrings in its bag, or it knows the Fusion Dance)
            default -> {}
        }
    }

    // ---------------------------------------------------------------- events

    /** Bots go after what you hit, and what hits you. Their stand-in player's hits count as the bot's. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        Entity src = event.getSource().getEntity();
        PartyBotEntity bot = botOf(src);
        if (bot != null && friendly(bot, event.getEntity()) && !(bot.sparring() && event.getEntity() == bot.owner())) { event.setCanceled(true); return; }
        if (bot == null && src instanceof ServerPlayer p && event.getEntity() instanceof PartyBotEntity b && friendly(b, p) && !(b.sparring() && p == b.owner())) { event.setCanceled(true); return; }
        if (src instanceof ServerPlayer owner && !(event.getEntity() instanceof PartyBotEntity) && !(src instanceof FakePlayer)) {
            for (PartyBotEntity b : botsNear(owner)) if (!friendly(b, event.getEntity())) { b.relayTarget = event.getEntity(); b.relayUntil = owner.level().getGameTime() + 200; }
        }
        if (event.getEntity() instanceof ServerPlayer owner && src instanceof LivingEntity att && !(src instanceof FakePlayer)) {
            for (PartyBotEntity b : botsNear(owner)) if (!friendly(b, att)) { b.relayTarget = att; b.relayUntil = owner.level().getGameTime() + 200; }
        }
    }

    private static List<PartyBotEntity> botsNear(ServerPlayer p) {
        return p.level().getEntitiesOfClass(PartyBotEntity.class, p.getBoundingBox().inflate(48), b -> p.getUUID().equals(b.ownerId()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        PartyBotEntity attacker = botOf(event.getSource().getEntity());
        if (attacker != null && attacker != event.getEntity() && !(event.getEntity() instanceof Player)) {
            ServerPlayer o = attacker.owner();
            if (o != null && o.isAlive()) event.getEntity().setLastHurtByPlayer(o);     // owner gets the XP orbs and drops
        }
        // party XP sharing: members near you get 30% of the Attack XP from your hits
        if (event.getSource().getEntity() instanceof ServerPlayer hitter && !(hitter instanceof FakePlayer) && !(event.getEntity() instanceof Player)
                && Parties.inParty(hitter) && !event.isCanceled()) {
            float xp = Math.min(event.getAmount(), event.getEntity().getHealth()) * 0.3F;
            for (ServerPlayer m : Parties.members(hitter)) {
                if (m != hitter && m.level() == hitter.level() && m.distanceToSqr(hitter) < 48 * 48) com.xenowarriors.stats.Stats.addXp(m, Stat.ATTACK, xp);
                for (PartyBotEntity mb : bots(m)) if (mb.level() == hitter.level() && mb.distanceToSqr(hitter) < 48 * 48) addXp(mb, Stat.ATTACK, xp);   // (bots share it like players)
            }
        }
        if (attacker != null && attacker != event.getEntity()) {
            float lvBonus = 1F + Stat.ATTACK.bonus(statLevel(attacker, Stat.ATTACK));   // (its character's own Attack level, the same bonus a player of it gets)
            if (event.getSource().getEntity() instanceof PartyBotEntity && !com.xenowarriors.ka.DmzKi.isKi(event.getSource().getDirectEntity())) event.setAmount(event.getAmount() * lvBonus);   // (its DMZ ki attacks hit as hard as its DMZ character, like skills)
            addXp(attacker, Stat.ATTACK, Math.min(event.getAmount(), event.getEntity().getHealth()));
        }
        if (event.getEntity() instanceof PartyBotEntity b && event.getSource().getEntity() instanceof LivingEntity src && src != b) {
            event.setAmount(event.getAmount() * (1F - Stat.DEFENSE.bonus(statLevel(b, Stat.DEFENSE))));   // its Defense level, like a player's
            addXp(b, Stat.DEFENSE, event.getAmount());
        }
        // sparring: the owner can't go below 1 HP - that's the bot's win
        if (event.getEntity() instanceof ServerPlayer p && attacker != null && attacker.sparring() && p == attacker.owner() && p.getHealth() - event.getAmount() < 2F) {
            event.setCanceled(true);
            p.setHealth(Math.max(p.getHealth(), 2F));
            sparOver(attacker, true);
        }
    }

    /** Kills by a bot count for its owner (Collection, pet-style credit). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof FakePlayer) { event.setCanceled(true); return; }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.tickCount % 10 == 0) for (PartyBotEntity b : bots(p)) unboard(b, p);
        if (p.tickCount % 10 == 7) catchUp(p);
        // standing right by you when you get in (the follow goal only runs when they're further off): they hop in too
        if (p.tickCount % 10 == 5 && p.getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity)
            for (PartyBotEntity b : bots(p)) if (b.getTarget() == null && !b.isPassenger() && b.level() == p.level()) boardWith(b, p);
        // carried bots step down once Rhalix lands
        if (p.tickCount % 20 == 0 && p.onGround()) for (Entity pass : p.getPassengers()) if (pass instanceof PartyBotEntity b) {
            int t = b.getPersistentData().getInt("xw_landed") + 1;
            b.getPersistentData().putInt("xw_landed", t);
            if (t >= 2) { b.stopRiding(); b.getPersistentData().remove("xw_landed"); }
        }
    }

    /** Sneak + right-click a party member as Rhalix (wings out): pick them up and fly them. */
    @SubscribeEvent
    public static void onInteractPlayer(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof ServerPlayer other) || !p.isShiftKeyDown()) return;
        if (ModCapabilities.getType(p) != CharacterType.RHALIX || !com.xenowarriors.character.RhalixWings.out(p) || !Parties.sameParty(p, other)) return;
        if (other.isPassenger()) return;
        other.startRiding(p, true);
        p.displayClientMessage(Component.literal("Carrying " + other.getName().getString()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        other.displayClientMessage(Component.literal(p.getName().getString() + " is carrying you - sneak to let go").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ListTag list = new ListTag();
        for (PartyBotEntity b : bots(p)) {
            if (b.journey()) for (int i = 0; i < b.inventory.getContainerSize(); i++) { ItemStack it = b.inventory.removeItemNoUpdate(i); p.getInventory().add(it); if (!it.isEmpty()) p.drop(it, false); }
            else if (!b.downed()) list.add(b.record());
            else checkIn(b, p);
            forgetFake(b.getUUID()); b.discard();
        }
        for (var it = LEFT_BEHIND.iterator(); it.hasNext(); ) { var e = it.next(); if (e.getKey().equals(p.getUUID())) { list.add(e.getValue()); it.remove(); } }   // (waiting ones too)
        p.getPersistentData().put(RECORDS, list);
    }

    /** What was waiting for offline owners when this world was last saved (BotLimbo) comes back. */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) { BotLimbo.restore(event.getServer()); }

    /** Bots still standing about for an owner who's gone (the 2 s before they'd step out) join the waiting list before the final save. */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (var e : new ArrayList<>(LIVE.entrySet())) {
            if (event.getServer().getPlayerList().getPlayer(e.getKey()) != null) continue;   // (online owners: their logout keeps them)
            for (PartyBotEntity b : new ArrayList<>(e.getValue())) {
                if (b.isRemoved() || b.journey() || b.ownerId() == null) continue;
                LEFT_BEHIND.add(Map.entry(b.ownerId(), b.record()));
                forgetFake(b.getUUID()); b.discard();
            }
        }
    }

    /** Bots waiting for an offline owner belong to this world (saved with it, see BotLimbo): none turn up (doubled) in the next save opened (singleplayer). */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        LEFT_BEHIND.clear(); PENDING_HOME.clear(); ANIM_SENT.clear(); STRANDED.clear(); BotErrands.clear();
    }

    /** The things of bots that went home while this owner was away, into their saves (taken off the list first: handed over once). */
    static int deliverPending(ServerPlayer p) {
        List<CompoundTag> home = PENDING_HOME.remove(p.getUUID());
        if (home == null) return 0;
        for (CompoundTag rec : home) homeFromRecord(p, rec);
        return home.size();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        deliverPending(p);                                                        // (bots that went home while you were away)
        ListTag list = p.getPersistentData().getList(RECORDS, 10);
        p.getPersistentData().remove(RECORDS);
        for (int i = 0; i < list.size(); i++) spawn(p, CharacterType.byId(list.getCompound(i).getString("char")), list.getCompound(i));
        if (!com.xenowarriors.journey.Journey.inLobbyOrRun(p)) unstash(p);      // (a Journey that ended while they were away)
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !com.xenowarriors.journey.Journey.inLobbyOrRun(p))
            com.xenowarriors.skill.ServerScheduler.later(20, () -> { if (p.isAlive() && !com.xenowarriors.journey.Journey.inLobbyOrRun(p)) unstash(p); });
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        for (PartyBotEntity b : bots(p)) { if (b.journey()) b.discard(); else if (canFollowTo(b.character(), p.level())) teleportNear(b, p); }   // (Journey teammates stay; only Kaeru goes into the Hekublade)
        if (p.level().dimension() != com.xenowarriors.mikiko.HekubladeRealm.KEY) {   // back from the blade: the ones who waited come over
            List<Map.Entry<UUID, CompoundTag>> waiting = new ArrayList<>();
            for (var e : LEFT_BEHIND) if (e.getKey().equals(p.getUUID())) waiting.add(e);
            LEFT_BEHIND.removeAll(waiting);
            for (var e : waiting) {
                if (bots(p).stream().noneMatch(b -> b.character().getId().equals(e.getValue().getString("char")))) spawn(p, CharacterType.byId(e.getValue().getString("char")), e.getValue());
                else homeFromRecord(p, e.getValue());                               // (already back another way: its things go home)
            }
        }
    }

    /** Boss drops are split between the party members who were there. */
    @SubscribeEvent
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        boolean boss = dead instanceof com.xenowarriors.corrupted.CSoulBossEntity || dead.getPersistentData().getBoolean("xw_boss");
        if (!boss || dead.level().isClientSide) return;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp && !(sp instanceof FakePlayer) ? sp : null;
        PartyBotEntity bk = botOf(event.getSource().getEntity());
        if (killer == null && bk != null) killer = bk.owner();
        if (killer == null) return;
        List<ServerPlayer> share = new ArrayList<>();
        for (ServerPlayer m : Parties.members(killer)) if (m.level() == dead.level() && m.distanceToSqr(dead) < 64 * 64) share.add(m);
        if (share.size() < 2) return;
        int i = 0;
        for (var it : event.getDrops()) {
            ServerPlayer to = share.get(i++ % share.size());
            ItemStack st = it.getItem().copy();
            if (!to.getInventory().add(st)) to.drop(st, false);
            to.displayClientMessage(Component.literal("Boss loot: ").append(it.getItem().getHoverName()).withStyle(ChatFormatting.GOLD), false);
        }
        event.getDrops().clear();
    }
}

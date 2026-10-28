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
                    b.getX(), b.getY(), b.getZ(), b.level().dimension().location().toString(), true));
        return out;
    }

    private static final Set<UUID> SOLO_SYNCED = new HashSet<>();
    static final List<Map.Entry<UUID, CompoundTag>> LEFT_BEHIND = new ArrayList<>();

    /** Players with bots but no party still get the party cards (them + their bots). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!LEFT_BEHIND.isEmpty()) {
            List<Map.Entry<UUID, CompoundTag>> list = new ArrayList<>(LEFT_BEHIND);
            LEFT_BEHIND.clear();
            for (var e : list) {
                ServerPlayer o = event.getServer().getPlayerList().getPlayer(e.getKey());
                if (o != null && bots(o).stream().noneMatch(b -> b.character().getId().equals(e.getValue().getString("char"))))
                    spawn(o, CharacterType.byId(e.getValue().getString("char")), e.getValue());
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
                    com.xenowarriors.karma.Karma.get(p), com.xenowarriors.karma.Karma.MAX, ModCapabilities.getType(p).ordinal(), false, true,
                    p.getX(), p.getY(), p.getZ(), p.level().dimension().location().toString(), false));
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
        int free = Math.max(0, Math.min(2, Parties.MAX_SIZE - partySize(p)));
        ModNetwork.sendToPlayer(new com.xenowarriors.network.OpenBotPickPacket(taken.toArray(new String[0]), cooling.toArray(new String[0]), free), p);
    }

    public static void add(ServerPlayer p, String[] ids) {
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(p)) { p.sendSystemMessage(Component.literal("No bot teammates on a Journey - it's just you and your partner.").withStyle(ChatFormatting.RED)); return; }
        int free = Parties.MAX_SIZE - partySize(p);
        Set<String> taken = new HashSet<>();
        for (ServerPlayer m : Parties.members(p)) { taken.add(ModCapabilities.getType(m).getId()); for (PartyBotEntity b : bots(m)) taken.add(b.character().getId()); }
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
        if (record != null) b.loadRecord(record);
        teleportNear(b, p);
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

    /** XP the bot earns goes to the owner's saved stats for that character (so switching to it keeps the level). */
    static void addXp(PartyBotEntity b, Stat stat, float amount) {
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

    public static void teleportNear(PartyBotEntity b, ServerPlayer o) {
        double a = b.getRandom().nextDouble() * Math.PI * 2;
        if (b.level() != o.level()) {
            CompoundTag rec = b.record();
            CharacterType t = b.character();
            b.discard();
            spawn(o, t, rec);
            return;
        }
        b.moveTo(o.getX() + Math.cos(a) * 2, o.getY(), o.getZ() + Math.sin(a) * 2, b.getYRot(), 0);
        b.getNavigation().stop();
    }

    // ---------------------------------------------------------------- skills (through a stand-in player)

    private static FakePlayer fake(PartyBotEntity b) {
        ServerLevel s = (ServerLevel) b.level();
        FakePlayer f = FAKES.get(b.getUUID());
        if (f == null || f.level() != s) {
            String name = ("xwbot_" + b.character().getId() + "_" + b.getUUID().toString().substring(0, 4));
            f = FakePlayerFactory.get(s, new GameProfile(UUID.nameUUIDFromBytes(("xwbot" + b.getUUID()).getBytes()), name.substring(0, Math.min(16, name.length()))));
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
            });
            d.setKarma(100F);
        });
        return ff;
    }

    static void trySkill(PartyBotEntity b, LivingEntity target) {
        if (b.sparring() && b.getRandom().nextInt(3) != 0) return;
        double dist = b.distanceTo(target);
        if (dist > 20) return;
        long now = b.level().getGameTime();
        ServerPlayer o = b.owner();
        int[] loadout = o == null ? SkillPool.defaultLoadout() : o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(b.character())).orElse(SkillPool.defaultLoadout());
        int slot = b.getRandom().nextInt(5);
        if (slot == 4 && (b.getHealth() > b.getMaxHealth() * 0.6F || b.karma < 60)) slot = b.getRandom().nextInt(4);    // ultimates when it matters
        if (now < b.skillReady[slot]) return;
        Skill skill = SkillPool.inSlot(b.character(), loadout, slot);
        if (skill == null) return;
        float cost = skill.getKarmaCost();
        if (b.karma < cost) return;
        FakePlayer f = fake(b);
        Vec3 eye = b.getEyePosition(), to = target.getBoundingBox().getCenter();
        Vec3 d = to.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        f.moveTo(b.getX(), b.getY(), b.getZ(), yaw, pitch);
        f.setYHeadRot(yaw);
        f.setOnGround(b.onGround());
        boolean used;
        try { used = skill.use(f); } catch (Throwable t) { used = false; }
        if (used) {
            b.karma -= cost;
            b.skillReady[slot] = now + Math.max(40, skill.getCooldownTicks());
            b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.KA_BREAK, 10);
        } else b.skillReady[slot] = now + 60;
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
            seen.add(p.asLong()); kinds.add(s.is(com.xenowarriors.sky.ModBlocks.DATA_ORE.get()) ? 4 : s.is(Tags.Blocks.ORES_DIAMOND) || s.is(Tags.Blocks.ORES_EMERALD) ? 3 : s.is(Tags.Blocks.ORES_GOLD) ? 1 : 0);
            double dd = p.distSqr(b.blockPosition());
            if (dd < bd) { bd = dd; best = p.immutable(); }
        }
        if (!seen.isEmpty() && b.tickCount % 200 < 20)                       // shows you the ore it senses
            ModNetwork.sendToPlayer(new com.xenowarriors.network.DataSensedPacket(new int[0], seen.stream().mapToLong(Long::longValue).toArray(),
                    kinds.stream().mapToInt(Integer::intValue).toArray(), 200, false), o);
        return best;
    }

    static void mine(PartyBotEntity b, BlockPos pos) {
        ServerLevel s = (ServerLevel) b.level();
        BlockState st = s.getBlockState(pos);
        if (!isOre(st)) return;
        List<ItemStack> drops = Block.getDrops(st, s, pos, s.getBlockEntity(pos), b, new ItemStack(Items.DIAMOND_PICKAXE));
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
        if (o != null) o.displayClientMessage(Component.literal("").append(b.label()).append(" is down! Sneak + right-click to revive (30s)").withStyle(ChatFormatting.RED), false);
        b.level().playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1F, 0.6F);
    }

    static void bleedOut(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o != null) {
            o.getPersistentData().putLong(REJOIN + b.character().getId(), o.level().getGameTime() + REJOIN_COOLDOWN);
            o.displayClientMessage(Component.literal("").append(b.label()).append(" wasn't revived and left the party to recover (5 min).").withStyle(ChatFormatting.GRAY), false);
            for (int i = 0; i < b.inventory.getContainerSize(); i++) Block.popResource(b.level(), b.blockPosition(), b.inventory.removeItemNoUpdate(i));
        }
        b.discard();
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

    static void interact(PartyBotEntity b, ServerPlayer p) {
        if (!p.getUUID().equals(b.ownerId()) && !(b.ownerId() != null && Parties.sameParty(p.getUUID(), b.ownerId()))) return;
        if (b.downed()) {
            if (p.isShiftKeyDown()) { b.revive(); p.displayClientMessage(Component.literal("").append(b.label()).append(" is back up!").withStyle(ChatFormatting.GREEN), true); }
            else p.displayClientMessage(Component.literal("Sneak + right-click to revive").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!p.getUUID().equals(b.ownerId())) return;
        if (p.isShiftKeyDown() && com.xenowarriors.character.RhalixWings.out(p) && ModCapabilities.getType(p) == CharacterType.RHALIX) {   // Rhalix picks them up
            b.startRiding(p, true);
            p.displayClientMessage(Component.literal("Carrying ").append(b.label()).append(" - land to set them down").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
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

    public static final int ACT_INVENTORY = 0, ACT_SUMMON_PET = 1, ACT_FLY = 2, ACT_SPAR = 3, ACT_MODE = 4, ACT_DISMISS = 5, ACT_MENU = 6;

    public static void action(ServerPlayer p, int botId, int action, int arg) {
        if (!(p.serverLevel().getEntity(botId) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId()) || b.distanceToSqr(p) > 32 * 32) return;
        switch (action) {
            case ACT_INVENTORY -> net.minecraftforge.network.NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, b.inventory), b.label()));
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
                for (int i = 0; i < b.inventory.getContainerSize(); i++) { ItemStack it = b.inventory.removeItemNoUpdate(i); if (!it.isEmpty() && !p.getInventory().add(it)) p.drop(it, false); }
                b.discard();
                p.displayClientMessage(Component.literal("").append(b.label()).append(" left the party (their bag went to you).").withStyle(ChatFormatting.GRAY), true);
            }
            case ACT_MENU -> interact(b, p);
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
            for (ServerPlayer m : Parties.members(hitter))
                if (m != hitter && m.level() == hitter.level() && m.distanceToSqr(hitter) < 48 * 48) com.xenowarriors.stats.Stats.addXp(m, Stat.ATTACK, xp);
        }
        if (attacker != null && attacker != event.getEntity()) {
            float lvBonus = 1F + Stat.ATTACK.bonus(Math.min(Stat.MAX_LEVEL, attacker.botLevel() / 3));
            if (event.getSource().getEntity() instanceof PartyBotEntity) event.setAmount(event.getAmount() * lvBonus);
            addXp(attacker, Stat.ATTACK, Math.min(event.getAmount(), event.getEntity().getHealth()));
        }
        if (event.getEntity() instanceof PartyBotEntity b && event.getSource().getEntity() instanceof LivingEntity) addXp(b, Stat.DEFENSE, event.getAmount());
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
        for (PartyBotEntity b : bots(p)) { if (!b.downed()) list.add(b.record()); FAKES.remove(b.getUUID()); b.discard(); }
        p.getPersistentData().put(RECORDS, list);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ListTag list = p.getPersistentData().getList(RECORDS, 10);
        p.getPersistentData().remove(RECORDS);
        for (int i = 0; i < list.size(); i++) spawn(p, CharacterType.byId(list.getCompound(i).getString("char")), list.getCompound(i));
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        for (PartyBotEntity b : bots(p)) teleportNear(b, p);
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

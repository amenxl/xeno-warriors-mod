package com.xenowarriors.tower;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.collection.Catalog;
import com.xenowarriors.collection.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Tower mode: a dimension of arena floors with waves of the enemies you haven't finished in your Collection.
 * Clear every wave to go up a floor. Kills made here only go into your Collection at a checkpoint - reaching floor 10,
 * then every 10 floors after. Leave or get knocked out before that and the unsaved kills are gone.
 * Your party can come too (up to 4 people and bots), and healing is limited on each floor.
 * Every 10 floors the arena gets bigger (radius +4, up to 36), each kill counts for more (x1 on floors 1-9, x2 on
 * 10-19 ... up to x100) and the enemies take a step up on top of the per-floor scaling. Everything that spawns goes
 * for you and your bots, even endermen, piglins, wolves and iron golems. A safety protocol clears a wave nobody has
 * fought for 2 minutes (stragglers stuck in walls) so the floor can go on. The bar at the top of the screen shows the
 * floor, wave, kill multiplier and enemies left.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Tower {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, XenoWarriors.id("tower"));
    public static final int ARENA_R = 16, SPACING = 512, Y = 100, CHECKPOINT = 10;
    /** The arena grows by GROW every 10 floors, up to MAX_R. */
    public static final int GROW = 4, MAX_R = 36, MAX_MULT = 100;
    /** No damage dealt or taken by the party for this long during a wave: what's left of it is cleared (ticks; tests shorten it). */
    public static int SAFETY_TICKS = 2 * 60 * 20;
    private static final UUID DEC_HP = UUID.fromString("5b0e2c11-7a3d-4c55-9a51-0d1f2e3c4b04"), DEC_DMG = UUID.fromString("5b0e2c11-7a3d-4c55-9a51-0d1f2e3c4b05");
    /** Healing allowed per floor (in health points: 20 = 10 hearts). */
    public static final float HEAL_PER_FLOOR = 20F;
    private static final UUID HP = UUID.fromString("5b0e2c11-7a3d-4c55-9a51-0d1f2e3c4b01"), DMG = UUID.fromString("5b0e2c11-7a3d-4c55-9a51-0d1f2e3c4b02");

    private record Back(ResourceKey<Level> dim, Vec3 pos, float yRot) {}

    private static final class Run {
        int slot, floor, wave, waves, state;           // state: 0 = countdown to next wave, 1 = fighting
        long nextAt;
        UUID leader;
        BlockPos center;
        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> alive = new HashSet<>();
        final Map<UUID, Back> back = new HashMap<>();
        final Map<UUID, CompoundTag> pending = new HashMap<>();
        final Map<UUID, Float> healLeft = new HashMap<>();
        /** Joined after the first floor: the leaderboard only counts runs climbed from the bottom. */
        final Set<UUID> lateJoin = new HashSet<>();
        List<String> pool = List.of();
        TowerMod mod = TowerMod.NONE;
        boolean bossFloor, safety;
        int radius = ARENA_R, waveSize;
        long lastCombat;
        net.minecraft.server.level.ServerBossEvent hud;
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Run> BY_PLAYER = new HashMap<>();
    /** The arena radius built at each slot (absent: not built since the server started). */
    private static final Map<Integer, Integer> BUILT = new HashMap<>();

    private Tower() {}

    public static boolean is(Level l) { return l.dimension() == LEVEL; }

    public static int floorOf(ServerPlayer p) { Run r = BY_PLAYER.get(p.getUUID()); return r == null ? 0 : r.floor; }

    /** The arena's radius on this floor: 16, then +4 every 10 floors (capped). */
    public static int radius(int floor) { return Math.min(MAX_R, ARENA_R + GROW * Math.max(0, floor / 10)); }

    /** How many Collection kills one kill counts for on this floor: x1 on 1-9, x2 on 10-19 ... up to x100. */
    public static int mult(int floor) { return Math.max(1, Math.min(MAX_MULT, floor / 10 + 1)); }

    /** Enemy health and damage bonus on this floor (as +x of their own): per floor, plus a step every 10 floors. */
    public static float[] waveScale(int floor) {
        int dec = Math.max(0, floor / 10);
        return new float[]{0.07F * floor + 0.30F * dec, 0.045F * floor + 0.15F * dec};
    }

    /** In a Tower run while a floor is being fought (counting down to a wave, or in one) - not between floors. */
    public static boolean fighting(ServerPlayer p) { Run r = BY_PLAYER.get(p.getUUID()); return r != null && is(p.level()) && r.state != 2; }

    private static Component msg(String s, ChatFormatting c) { return Component.literal(s).withStyle(c); }

    // ------------------------------------------------------------------------------------------------ entering / leaving

    public static void enter(ServerPlayer p) {
        if (!Collection.unlocked(p)) { p.displayClientMessage(msg("Read the Collection Book at the Element School first", ChatFormatting.GRAY), true); return; }
        if (BY_PLAYER.containsKey(p.getUUID())) { p.displayClientMessage(msg("You're already in the Tower", ChatFormatting.GRAY), true); return; }
        if (is(p.level()) || com.xenowarriors.room.RoomTech.is(p.level())) return;
        if (blocked(p)) return;
        // a party member's Tower run already going? join that one
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
            Run r = BY_PLAYER.get(m.getUUID());
            if (r != null) { join(p, r); return; }
        }
        ServerLevel tl = p.server.getLevel(LEVEL);
        if (tl == null) { p.sendSystemMessage(msg("The Tower dimension isn't loaded.", ChatFormatting.RED)); return; }
        int slot = 0;
        while (RUNS.containsKey(slot)) slot++;
        Run r = new Run();
        r.slot = slot; r.leader = p.getUUID();
        r.center = new BlockPos(slot * SPACING + SPACING / 2, Y, SPACING / 2);
        RUNS.put(slot, r);
        r.radius = radius(1);
        ensureArena(tl, r);
        // who comes along: you, and party members close by; the rest get a [Join] link
        join(p, r);
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
            if (m == p) continue;
            if (m.level() == p.level() && m.distanceToSqr(p) < 32 * 32) join(m, r);
            else m.sendSystemMessage(msg(p.getName().getString() + " entered the Tower! ", ChatFormatting.AQUA).copy()
                    .append(Component.literal("[Join]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xeno tower join"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/xeno tower join"))))));
        }
        r.pool = pool(r, p.server);
        nextFloor(p.server, r);
    }

    /** /xeno tower join: into your party's run. */
    public static void joinParty(ServerPlayer p) {
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
            Run r = BY_PLAYER.get(m.getUUID());
            if (r != null && m != p) { join(p, r); return; }
        }
        p.displayClientMessage(msg("No one in your party is in the Tower", ChatFormatting.GRAY), true);
    }

    private static void join(ServerPlayer p, Run r) {
        if (BY_PLAYER.containsKey(p.getUUID()) || is(p.level()) || blocked(p)) return;
        if (r.floor > 0 && r.state != 2) { p.displayClientMessage(msg("Wait for them to clear this floor, then join", ChatFormatting.GRAY), true); return; }
        if (r.floor > 0) r.lateJoin.add(p.getUUID());
        int people = 0;
        for (UUID id : r.players) { ServerPlayer m = p.server.getPlayerList().getPlayer(id); people += 1 + (m == null ? 0 : com.xenowarriors.bot.PartyBots.bots(m).size()); }
        if (people + 1 > com.xenowarriors.party.Parties.MAX_SIZE) { p.displayClientMessage(msg("That Tower run is full (4 people and bots)", ChatFormatting.RED), true); return; }
        ServerLevel tl = p.server.getLevel(LEVEL);
        if (tl == null) return;
        r.players.add(p.getUUID());
        r.back.put(p.getUUID(), new Back(p.level().dimension(), p.position(), p.getYRot()));
        r.healLeft.put(p.getUUID(), r.mod == TowerMod.NO_HEALING ? 0F : HEAL_PER_FLOOR);
        TowerBosses.addPlayer(p, r.center);
        BY_PLAYER.put(p.getUUID(), r);
        hud(r).addPlayer(p);
        double a = r.players.size() * 1.6;
        p.teleportTo(tl, r.center.getX() + .5 + Math.cos(a) * 3, Y + 1, r.center.getZ() + .5 + Math.sin(a) * 3, p.getYRot(), 0);
        p.sendSystemMessage(msg("TOWER - kills here save to your Collection at floor " + CHECKPOINT + ", then every " + CHECKPOINT + " floors. "
                + "You can heal " + (int) (HEAL_PER_FLOOR / 2) + " hearts per floor.", ChatFormatting.AQUA));
    }

    /** Can't go up while downed, inside (or sealed in) a Hekublade, or when the world is on Peaceful (nothing would fight back). */
    private static boolean blocked(ServerPlayer p) {
        String why = !p.isAlive() || p.isSpectator() ? "You can't enter the Tower right now"
                : com.xenowarriors.party.Duels.isKnockedOut(p) ? "You can't enter the Tower while you're down"
                : com.xenowarriors.party.Duels.inDuel(p) ? "Finish your duel first"
                : com.xenowarriors.journey.Journey.inLobbyOrRun(p) ? "You can't enter the Tower on a Journey"
                : com.xenowarriors.vr.Vr.is(p.level()) ? "Take the VR goggles off first"
                : com.xenowarriors.mikiko.HekubladeRealm.inRealm(p) || com.xenowarriors.mikiko.HekubladeRealm.isPrisoner(p) ? "You can't reach the Tower from inside a Hekublade"
                : p.server.getWorldData().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL ? "The Tower doesn't open on Peaceful difficulty"
                : null;
        if (why != null) p.displayClientMessage(msg(why, ChatFormatting.RED), true);
        return why != null;
    }

    public static void leave(ServerPlayer p, String why) {
        Run r = BY_PLAYER.remove(p.getUUID());
        if (r == null) return;
        r.lateJoin.remove(p.getUUID());
        if (p.getUUID().equals(r.leader)) for (UUID id : r.players) if (!id.equals(p.getUUID())) { r.leader = id; break; }   // (someone else leads now)
        TowerBosses.removePlayer(p);
        if (r.hud != null) r.hud.removePlayer(p);
        r.players.remove(p.getUUID());
        CompoundTag lost = r.pending.remove(p.getUUID());
        r.healLeft.remove(p.getUUID());
        Back b = r.back.remove(p.getUUID());
        int unsaved = 0;
        if (lost != null) for (String k : lost.getAllKeys()) unsaved += lost.getInt(k);
        if (is(p.level()) && b != null) {
            ServerLevel to = p.server.getLevel(b.dim());
            if (to == null) to = p.server.overworld();
            // whatever dropped on the floor comes with you
            ServerLevel tl = p.serverLevel();
            if (r.players.isEmpty())
                for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(r.center).inflate(r.radius + 4, 12, r.radius + 4), e -> e instanceof ItemEntity || e instanceof ExperienceOrb))
                    com.xenowarriors.room.RoomTech.move(e, to, b.pos(), 0F);
            p.teleportTo(to, b.pos().x, b.pos().y, b.pos().z, b.yRot(), 0);
        }
        p.sendSystemMessage(msg(why + " (best floor this run: " + Math.max(0, r.floor - (r.state == 1 ? 1 : 0)) + ")"
                + (unsaved > 0 ? " - " + unsaved + " unsaved kills lost" : ""), ChatFormatting.GRAY));
        if (r.players.isEmpty()) close(p.server, r);
    }

    private static void close(MinecraftServer server, Run r) {
        RUNS.remove(r.slot);
        TowerBosses.clear(r.center);
        if (r.hud != null) r.hud.removeAllPlayers();
        ServerLevel tl = server.getLevel(LEVEL);
        if (tl == null) return;
        for (UUID id : r.alive) { Entity e = tl.getEntity(id); if (e != null) e.discard(); }
        for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(r.center).inflate(MAX_R + 8, 30, MAX_R + 8), e -> !(e instanceof net.minecraft.world.entity.player.Player)
                && !(e instanceof com.xenowarriors.bot.PartyBotEntity))) e.discard();
    }

    // ------------------------------------------------------------------------------------------------ floors and waves

    /** Enemies you (or your party) haven't finished in the Collection. */
    private static List<String> pool(Run r, MinecraftServer server) {
        List<String> out = new ArrayList<>();
        ServerPlayer leader = server.getPlayerList().getPlayer(r.leader);
        if (leader == null) for (UUID id : r.players) { leader = server.getPlayerList().getPlayer(id); if (leader != null) { r.leader = id; break; } }
        if (leader == null) return out;
        List<String> all = Catalog.all(leader.serverLevel());
        boolean s3 = com.xenowarriors.character.Seasons.unlocked(server, 3);
        for (String id : all) {
            if (!Catalog.inTower(id) || (!s3 && id.equals("xenowarriors:order_assassin"))) continue;
            boolean unfinished = false;
            for (UUID u : r.players) { ServerPlayer m = server.getPlayerList().getPlayer(u); if (m != null && Collection.count(m, id) < Collection.MAX) unfinished = true; }
            if (unfinished) out.add(id);
        }
        if (out.isEmpty()) for (String id : all) if (Catalog.inTower(id)) out.add(id);   // everything finished: anything goes
        return out;
    }

    private static void nextFloor(MinecraftServer server, Run r) {
        r.floor++;
        r.wave = 0;
        r.bossFloor = r.floor % 10 == 0;
        r.waves = r.bossFloor ? 1 : Math.min(5, 2 + r.floor / 10);
        r.state = 0;
        ServerLevel tl = server.getLevel(LEVEL);
        r.mod = tl == null ? TowerMod.NONE : TowerMod.roll(r.floor, tl.getRandom());
        r.safety = false;
        boolean grew = false;
        if (tl != null && radius(r.floor) != r.radius) { r.radius = radius(r.floor); ensureArena(tl, r); grew = true; }
        r.nextAt = (tl == null ? 0 : tl.getGameTime()) + 80;
        if (r.floor > 1 && (r.floor - 1) % 5 == 0) r.pool = pool(r, server);         // finished ones drop out as you go
        boolean checkpoint = r.floor % CHECKPOINT == 0;
        for (UUID id : r.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            r.healLeft.put(id, r.mod == TowerMod.NO_HEALING ? 0F : HEAL_PER_FLOOR);
            if (!r.lateJoin.contains(id)) { Collection.setBest(p, r.floor); TowerBoard.reached(p, r.floor); }
            String sub = r.bossFloor ? (checkpoint ? "Checkpoint saved - " : "") + "a boss is coming..."
                    : r.mod != TowerMod.NONE ? r.mod.title.toUpperCase() + ": " + r.mod.desc
                    : checkpoint ? "Checkpoint - Collection saved!" : r.waves + " waves  -  " + (int) (HEAL_PER_FLOOR / 2) + " hearts of healing";
            title(p, (r.bossFloor ? "BOSS " : "") + "FLOOR " + r.floor, sub);
            if (r.mod != TowerMod.NONE) p.sendSystemMessage(msg("Floor " + r.floor + ": " + r.mod.title + " - " + r.mod.desc, ChatFormatting.LIGHT_PURPLE));
            if (r.floor % 10 == 0)
                p.sendSystemMessage(msg("Floor " + r.floor + ": " + (grew ? "the arena grew (radius " + r.radius + "), " : "") + "kills now count x" + mult(r.floor) + " and the enemies got stronger!", ChatFormatting.GOLD));
            if (checkpoint) save(p, r);
        }
        if (tl != null) {
            tl.playSound(null, r.center, checkpoint ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 1.2F);
            floorColor(tl, r);
        }
        updateHud(server, r);
    }

    /** Checkpoint: the kills held for this player go into their Collection. */
    private static void save(ServerPlayer p, Run r) {
        CompoundTag t = r.pending.remove(p.getUUID());
        if (t == null || t.isEmpty()) return;
        int saved = 0;
        for (String id : t.getAllKeys()) saved += Collection.add(p, id, t.getInt(id));
        p.sendSystemMessage(msg("Tower checkpoint: " + saved + " kills saved to your Collection", ChatFormatting.GOLD));
    }

    /** Called by the Collection for every kill: true if it's held for the next checkpoint. */
    public static boolean pending(ServerPlayer p, String id) {
        Run r = BY_PLAYER.get(p.getUUID());
        if (r == null || !is(p.level())) return false;
        CompoundTag t = r.pending.computeIfAbsent(p.getUUID(), k -> new CompoundTag());
        t.putInt(id, t.getInt(id) + mult(r.floor));             // (the kill multiplier: one kill counts as more every 10 floors)
        return true;
    }

    /** With DragonMineZ: enemies keep up with part of your power - about half on the first floors, rising to all of it by floor 50. */
    private static float dmzPull(List<ServerPlayer> ps, int floor) {
        float s = com.xenowarriors.compat.DmzScaling.partyScale(ps);
        return 1F + (s - 1F) * Math.min(1F, 0.5F + floor * 0.01F);
    }

    private static void spawnWave(ServerLevel tl, Run r) {
        int people = r.players.size();
        if (r.bossFloor) {
            List<ServerPlayer> ps = new ArrayList<>();
            for (UUID id : r.players) { ServerPlayer p = tl.getServer().getPlayerList().getPlayer(id); if (p != null && p.level() == tl) ps.add(p); }
            List<Mob> spawned = TowerBosses.spawn(tl, r.center, r.floor, ps);
            float dz = dmzPull(ps, r.floor);
            int dec = r.floor / 10;
            for (Mob m : spawned) {
                if (dec >= 2) {                                 // (the step every 10 floors: bosses from floor 20 on hit harder too)
                    var mh = m.getAttribute(Attributes.MAX_HEALTH);
                    if (mh != null) mh.addPermanentModifier(new AttributeModifier(DEC_HP, "Tower decade", 0.15 * (dec - 1), AttributeModifier.Operation.MULTIPLY_TOTAL));
                    var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
                    if (md != null) md.addPermanentModifier(new AttributeModifier(DEC_DMG, "Tower decade", 0.10 * (dec - 1), AttributeModifier.Operation.MULTIPLY_TOTAL));
                    m.setHealth(m.getMaxHealth());
                }
                com.xenowarriors.journey.JourneyWorld.scale(m, dz);
                r.alive.add(m.getUUID());
            }
            if (spawned.isEmpty()) r.bossFloor = false;          // (couldn't make the boss: an ordinary clear, no boss reward)
            r.waveSize = spawned.size();
            r.lastCombat = tl.getGameTime();
            return;
        }
        int count = Math.min(14, 3 + r.floor / 3 + r.wave + (people - 1) * 2);
        if (r.mod == TowerMod.HORDE) count = Math.min(21, count * 3 / 2);
        var rnd = tl.getRandom();
        List<ServerPlayer> targets = new ArrayList<>();
        for (UUID id : r.players) { ServerPlayer p = tl.getServer().getPlayerList().getPlayer(id); if (p != null && p.level() == tl) targets.add(p); }
        int made = 0, tries = 0;
        while (made < count && tries++ < count * 4 && !r.pool.isEmpty()) {
            String id = r.pool.get(rnd.nextInt(r.pool.size()));
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
            if (type == null) continue;
            Entity e;
            try { e = type.create(tl); } catch (Throwable t) { e = null; }
            if (!(e instanceof Mob m)) { if (e != null) e.discard(); continue; }
            double a = rnd.nextDouble() * Math.PI * 2, d = 8 + rnd.nextDouble() * (r.radius - 10);
            m.moveTo(r.center.getX() + .5 + Math.cos(a) * d, Y + 1, r.center.getZ() + .5 + Math.sin(a) * d, rnd.nextFloat() * 360F, 0);
            try { m.finalizeSpawn(tl, tl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
            m.setPersistenceRequired();
            float[] sc = waveScale(r.floor);
            float hp = sc[0], dmg = sc[1];
            var mh = m.getAttribute(Attributes.MAX_HEALTH);
            if (mh != null && hp > 0) mh.addPermanentModifier(new AttributeModifier(HP, "Tower floor", hp, AttributeModifier.Operation.MULTIPLY_TOTAL));
            var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (md != null && dmg > 0) md.addPermanentModifier(new AttributeModifier(DMG, "Tower floor", dmg, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (r.mod == TowerMod.ELITE && mh != null) mh.addPermanentModifier(new AttributeModifier(UUID.fromString("5b0e2c11-7a3d-4c55-9a51-0d1f2e3c4b03"), "Tower elite", 0.6, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (r.mod == TowerMod.ELITE || r.mod == TowerMod.DARKNESS) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, -1, 0, false, false));
            if (r.mod == TowerMod.SWIFT) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, -1, 1, false, false));
            m.setHealth(m.getMaxHealth());
            m.getPersistentData().putBoolean("xw_tower", true);
            com.xenowarriors.journey.JourneyWorld.scale(m, dmzPull(targets, r.floor));
            tl.addFreshEntity(m);
            if (!targets.isEmpty()) aggro(m, targets.get(rnd.nextInt(targets.size())));
            tl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 20, .4, .6, .4, .3);
            r.alive.add(m.getUUID());
            made++;
        }
        r.waveSize = made;
        r.lastCombat = tl.getGameTime();
        for (ServerPlayer p : targets) p.displayClientMessage(msg("Floor " + r.floor + " - wave " + (r.wave + 1) + "/" + r.waves + " (" + made + " enemies)", ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel tl = server.getLevel(LEVEL);
        if (tl == null) return;
        long now = tl.getGameTime();
        if (now % 5 != 0) return;
        for (Mob extra : TowerBosses.tick(tl))                     // helpers the bosses called in count as part of the wave
            for (Run r : RUNS.values()) if (extra.position().distanceTo(Vec3.atCenterOf(r.center)) < r.radius + 8) { r.alive.add(extra.getUUID()); break; }
        for (Run r : new ArrayList<>(RUNS.values())) {
            if (now % 20 == 0) modEffects(server, tl, r);
            for (UUID id : new ArrayList<>(r.players)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) { BY_PLAYER.remove(id); r.players.remove(id); r.pending.remove(id); continue; }
                if (!is(p.level())) leave(p, "You left the Tower");
                else if (p.getY() < Y - 12) p.teleportTo(r.center.getX() + .5, Y + 1, r.center.getZ() + .5);      // fell off
            }
            if (r.players.isEmpty()) { close(server, r); continue; }
            List<LivingEntity> targets = targets(server, tl, r);
            r.alive.removeIf(id -> {
                Entity e = tl.getEntity(id);
                if (!(e instanceof LivingEntity le) || !le.isAlive()) return true;
                if (e.getY() < Y - 12 || e.position().distanceTo(Vec3.atCenterOf(r.center)) > r.radius + 12) {
                    if (TowerBosses.isBoss(e)) { e.teleportTo(r.center.getX() + .5, Y + 1, r.center.getZ() + .5); return false; }   // a boss can't be knocked out of the fight
                    e.discard(); return true;                     // fell out / wandered off
                }
                // everything here fights you and your bots - neutral mobs too - and keeps at it
                if (e instanceof Mob m && !targets.isEmpty()) {
                    LivingEntity cur = m.getTarget();
                    if (cur == null || !cur.isAlive() || !targets.contains(cur)) {
                        LivingEntity best = null; double bd = Double.MAX_VALUE;
                        for (LivingEntity t : targets) { double d = t.distanceToSqr(m); if (d < bd) { bd = d; best = t; } }
                        aggro(m, best);
                    } else if (now % 40 == 0) aggro(m, cur);
                }
                return false;
            });
            // safety protocol: nobody has fought for a while (stragglers stuck in walls, out of reach): clear the wave
            if (r.state == 1 && !r.alive.isEmpty() && now - r.lastCombat > SAFETY_TICKS) {
                int n = r.alive.size();
                for (UUID id : r.alive) { Entity e = tl.getEntity(id); if (e != null) e.discard(); }
                r.alive.clear();
                TowerBosses.clear(r.center);
                r.safety = true;
                XenoWarriors.LOGGER.info("Tower safety protocol: slot {} floor {} wave {} - {} enemies removed after {}s without combat", r.slot, r.floor, r.wave + 1, n, (now - r.lastCombat) / 20);
                String span = SAFETY_TICKS >= 1200 ? SAFETY_TICKS / 1200 + " minutes" : SAFETY_TICKS / 20 + " seconds";
                for (UUID id : r.players) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) p.sendSystemMessage(msg("Safety protocol: no fighting for " + span + " - the " + n + " enemies left were removed so the floor can go on.", ChatFormatting.YELLOW));
                }
            }
            if (r.state == 0 && now >= r.nextAt) { spawnWave(tl, r); r.state = 1; }
            else if (r.state == 1 && r.alive.isEmpty()) {
                r.wave++;
                r.state = 0;
                if (r.wave >= r.waves) {
                    vacuum(tl, r, server);
                    for (UUID id : r.players) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p == null) continue;
                        if (r.bossFloor && !r.safety) TowerRewards.boss(p, r.floor);
                        if (r.mod == TowerMod.HORDE) TowerRewards.horde(p, r.floor);
                    }
                    for (UUID id : r.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) title(p, "FLOOR " + r.floor + " CLEARED", "Next floor..."); }
                    r.nextAt = now + 60;
                    r.state = 2;
                } else r.nextAt = now + 50;
            } else if (r.state == 2 && now >= r.nextAt) nextFloor(server, r);
            if (now % 10 == 0 && RUNS.containsValue(r)) updateHud(server, r);
        }
    }

    /** Who the enemies go for: the run's players (not creative/spectator) and their bots that are up. */
    private static List<LivingEntity> targets(MinecraftServer server, ServerLevel tl, Run r) {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID id : r.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || p.level() != tl) continue;
            if (!p.isCreative() && !p.isSpectator()) out.add(p);
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.level() == tl && !b.downed()) out.add(b);
        }
        return out;
    }

    /** Makes a mob go for this target whatever kind it is: its target, a neutral mob's anger, a brain mob's memories (piglins). */
    public static void aggro(Mob m, LivingEntity t) {
        if (t == null) return;
        m.setTarget(t);
        if (m instanceof NeutralMob nm) { nm.setPersistentAngerTarget(t.getUUID()); nm.startPersistentAngerTimer(); }
        var brain = m.getBrain();
        if (brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED))
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, t);
        if (brain.checkMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED))
            brain.setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT, t.getUUID(), 600L);
    }

    /** Any hit landed or taken by the party in a run's arena counts as combat (for the safety protocol). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        LivingEntity v = event.getEntity();
        if (event.isCanceled() || RUNS.isEmpty() || v.level().isClientSide || !is(v.level())) return;
        Entity src = event.getSource().getEntity();
        boolean party = v instanceof ServerPlayer || v instanceof com.xenowarriors.bot.PartyBotEntity || src instanceof ServerPlayer || com.xenowarriors.bot.PartyBots.botOf(src) != null;
        if (!party) return;
        for (Run r : RUNS.values()) if (Math.abs(v.getX() - r.center.getX()) < SPACING / 2.0 && Math.abs(v.getZ() - r.center.getZ()) < SPACING / 2.0) { r.lastCombat = v.level().getGameTime(); break; }
    }

    private static net.minecraft.server.level.ServerBossEvent hud(Run r) {
        if (r.hud == null) r.hud = new net.minecraft.server.level.ServerBossEvent(Component.literal("Tower"), net.minecraft.world.BossEvent.BossBarColor.BLUE, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        return r.hud;
    }

    /** The Tower bar at the top of the screen: floor, wave, the kill multiplier and how many enemies are left. */
    private static void updateHud(MinecraftServer server, Run r) {
        var bar = hud(r);
        String wave = r.bossFloor ? "BOSS" : r.state == 2 ? "cleared" : "wave " + Math.min(r.waves, r.wave + 1) + "/" + r.waves;
        String left = r.state == 1 ? "  •  " + r.alive.size() + " left" : "";
        int k = mult(r.floor);
        bar.setName(Component.literal("Floor " + r.floor + "  •  " + wave + "  •  Kills x" + k + left).withStyle(k > 1 ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        bar.setColor(r.bossFloor ? net.minecraft.world.BossEvent.BossBarColor.RED : k > 1 ? net.minecraft.world.BossEvent.BossBarColor.YELLOW : net.minecraft.world.BossEvent.BossBarColor.BLUE);
        bar.setProgress(r.state == 1 && r.waveSize > 0 ? Math.max(0F, Math.min(1F, r.alive.size() / (float) r.waveSize)) : r.state == 2 ? 0F : 1F);
        for (UUID id : r.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null && !bar.getPlayers().contains(p)) bar.addPlayer(p); }
    }

    /** (tests) a player's run: floor, radius, multiplier, state, enemies left, ticks since combat. */
    public static String testInfo(ServerPlayer p) {
        Run r = BY_PLAYER.get(p.getUUID());
        if (r == null) return "no run";
        return "floor=" + r.floor + " radius=" + r.radius + " mult=x" + mult(r.floor) + " state=" + r.state + " wave=" + (r.wave + 1) + "/" + r.waves + " alive=" + r.alive.size()
                + " sinceCombat=" + (p.serverLevel().getGameTime() - r.lastCombat) + " safety=" + r.safety;
    }

    /** (tests) this run's arena centre. */
    public static BlockPos testCenter(ServerPlayer p) { Run r = BY_PLAYER.get(p.getUUID()); return r == null ? null : r.center; }

    /** (tests) the kills held for this player's next checkpoint (all enemies together). */
    public static int testPending(ServerPlayer p) {
        Run r = BY_PLAYER.get(p.getUUID());
        CompoundTag t = r == null ? null : r.pending.get(p.getUUID());
        int n = 0;
        if (t != null) for (String k : t.getAllKeys()) n += t.getInt(k);
        return n;
    }

    /** (tests) counts this mob as one of the wave's enemies (spawned by the test). */
    public static void testAdd(ServerPlayer p, Mob m) {
        Run r = BY_PLAYER.get(p.getUUID());
        if (r == null) return;
        m.getPersistentData().putBoolean("xw_tower", true);
        r.alive.add(m.getUUID());
    }

    /** (tests) the wave enemies still up in this player's run. */
    public static List<Mob> testAlive(ServerPlayer p) {
        Run r = BY_PLAYER.get(p.getUUID());
        List<Mob> out = new ArrayList<>();
        if (r != null) for (UUID id : r.alive) if (p.serverLevel().getEntity(id) instanceof Mob m) out.add(m);
        return out;
    }

    /** The floor's twist, kept up every second. */
    private static void modEffects(MinecraftServer server, ServerLevel tl, Run r) {
        if (r.mod == TowerMod.NONE || r.state == 2) return;
        for (UUID id : r.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || p.level() != tl) continue;
            switch (r.mod) {
                case LOW_GRAVITY -> { p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 40, 0, false, false));
                                      p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, 40, 2, false, false)); }
                case DARKNESS -> p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 60, 0, false, false));
                case KARMA_DRAIN -> com.xenowarriors.karma.Karma.lockRegen(p, 30);
                default -> {}
            }
        }
        if (r.mod == TowerMod.LOW_GRAVITY) for (UUID id : r.alive)
            if (tl.getEntity(id) instanceof LivingEntity le) le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 40, 0, false, false));
    }

    /** (op, testing) jump your run to this floor. */
    public static void skipTo(ServerPlayer p, int floor) {
        Run r = BY_PLAYER.get(p.getUUID());
        if (r == null) { p.displayClientMessage(msg("You're not in the Tower", ChatFormatting.GRAY), true); return; }
        ServerLevel tl = p.serverLevel();
        for (UUID id : r.alive) { Entity e = tl.getEntity(id); if (e != null) e.discard(); }
        r.alive.clear();
        TowerBosses.clear(r.center);
        r.floor = floor - 1;
        r.state = 0;
        nextFloor(p.server, r);
    }

    /** Drops and XP from the floor fly to the players. */
    private static void vacuum(ServerLevel tl, Run r, MinecraftServer server) {
        List<ServerPlayer> ps = new ArrayList<>();
        for (UUID id : r.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null && p.level() == tl) ps.add(p); }
        if (ps.isEmpty()) return;
        int i = 0;
        for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(r.center).inflate(r.radius + 4, 12, r.radius + 4), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
            ServerPlayer p = ps.get(i++ % ps.size());
            e.teleportTo(p.getX(), p.getY() + 0.5, p.getZ());
        }
    }

    private static void title(ServerPlayer p, String title, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
    }

    // ------------------------------------------------------------------------------------------------ rules

    /** Healing is limited per floor. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isCanceled()) return;
        Run r = BY_PLAYER.get(p.getUUID());
        if (r == null || !is(p.level())) return;
        float left = r.healLeft.getOrDefault(p.getUUID(), 0F);
        float amount = Math.min(event.getAmount(), p.getMaxHealth() - p.getHealth());
        if (amount <= 0) return;
        if (left <= 0) { event.setCanceled(true); return; }
        float use = Math.min(amount, left);
        event.setAmount(use);
        r.healLeft.put(p.getUUID(), left - use);
        if (left - use <= 0) p.displayClientMessage(msg("No healing left on this floor!", ChatFormatting.RED), true);
        else if ((int) (left / 4) != (int) ((left - use) / 4)) p.displayClientMessage(msg("Healing left this floor: " + String.format("%.1f", (left - use) / 2) + " hearts", ChatFormatting.YELLOW), true);
    }

    /** Knocked out in the Tower: you're out of the run (back where you entered), not dead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !BY_PLAYER.containsKey(p.getUUID()) || !is(p.level())) return;
        event.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.removeAllEffects();
        leave(p, "Knocked out on floor " + floorOf(p));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && BY_PLAYER.containsKey(p.getUUID())) leave(p, "Left the Tower");
    }

    /** The arenas built belong to this world: opening another save (singleplayer) builds its own (else "already built" left it with no floor). */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) { BUILT.clear(); }

    /** Logged in inside the Tower with no run going (e.g. after a restart): back to spawn. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && is(p.level()) && !BY_PLAYER.containsKey(p.getUUID())) {
            ServerLevel ow = p.server.overworld();
            BlockPos s = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : ow.getSharedSpawnPos();
            p.teleportTo(ow, s.getX() + .5, s.getY() + 1, s.getZ() + .5, p.getYRot(), 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ the arena

    /** Builds (or resizes) the run's arena to its radius, when that isn't what's standing there already. */
    private static void ensureArena(ServerLevel tl, Run r) {
        Integer had = BUILT.get(r.slot);
        if (had != null && had == r.radius) return;
        arena(tl, r.center, r.radius, had == null ? MAX_R : Math.max(had, r.radius));
        BUILT.put(r.slot, r.radius);
        XenoWarriors.LOGGER.info("Tower arena slot {} built at radius {} (was {})", r.slot, r.radius, had);
    }

    /**
     * The arena: a round floor of radius R with a glass ring wall and an invisible barrier up to 40 high (so nothing
     * leaves). Anything out to clearTo (an older, bigger or smaller arena, rubble) is cleared first.
     */
    private static void arena(ServerLevel tl, BlockPos c, int R, int clearTo) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int span = Math.max(R, clearTo) + 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -span; dx <= span; dx++) for (int dz = -span; dz <= span; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            int x = c.getX() + dx, z = c.getZ() + dz;
            if (d > R + 1.5) {                                    // outside this arena: clear what an older one left
                if (d > clearTo + 2.5) continue;
                for (int y = -1; y <= 40; y++) if (!tl.getBlockState(m.set(x, Y + y, z)).isAir()) tl.setBlock(m, air, 2);
                continue;
            }
            tl.setBlock(m.set(x, Y - 1, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            BlockState top = d > R - 0.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : (Math.abs(dx) + Math.abs(dz)) % 6 == 0 && d > 3 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
            tl.setBlock(m.set(x, Y, z), top, 2);
            boolean wall = d > R + 0.5;
            for (int y = 1; y <= 40; y++) {
                BlockState want = !wall ? air : y == 5 ? Blocks.SEA_LANTERN.defaultBlockState() : y < 5 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.BARRIER.defaultBlockState();
                if (tl.getBlockState(m.set(x, Y + y, z)) != want) tl.setBlock(m, want, 2);
            }
        }
        tl.setBlock(m.set(c.getX(), Y, c.getZ()), Blocks.BEACON.defaultBlockState(), 2);
    }

    /** The ring around the edge changes color every 10 floors. */
    private static void floorColor(ServerLevel tl, Run r) {
        BlockState[] ring = {Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Blocks.LIME_STAINED_GLASS.defaultBlockState(), Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),
                Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), Blocks.BLACK_STAINED_GLASS.defaultBlockState()};
        BlockState s = ring[Math.min(ring.length - 1, (r.floor - 1) / 10)];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos c = r.center;
        int R = r.radius;
        for (int dx = -R - 2; dx <= R + 2; dx++) for (int dz = -R - 2; dz <= R + 2; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > R + 0.5 && d <= R + 1.5) for (int y = 1; y <= 4; y++) tl.setBlock(m.set(c.getX() + dx, Y + y, c.getZ() + dz), s, 2);
        }
    }

    /**
     * (op) /xeno tower resetmap: rebuilds your run's arena (or every run's, when you're not in one) from scratch - floor,
     * walls and the floor's ring colour - and puts everyone in it back on the floor near the middle. Returns how many.
     */
    public static int resetMap(ServerPlayer by) {
        ServerLevel tl = by.server.getLevel(LEVEL);
        if (tl == null) return 0;
        Run mine = BY_PLAYER.get(by.getUUID());
        List<Run> runs = mine != null ? List.of(mine) : new ArrayList<>(RUNS.values());
        for (Run r : runs) {
            arena(tl, r.center, r.radius, MAX_R);
            BUILT.put(r.slot, r.radius);
            floorColor(tl, r);
            int k = 0;
            for (UUID id : r.players) {
                ServerPlayer p = by.server.getPlayerList().getPlayer(id);
                if (p == null || p.level() != tl) continue;
                double a = k++ * 1.6;
                p.teleportTo(tl, r.center.getX() + .5 + Math.cos(a) * 3, Y + 1, r.center.getZ() + .5 + Math.sin(a) * 3, p.getYRot(), 0);
                for (var b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.level() == tl) b.teleportTo(p.getX() + 1, Y + 1, p.getZ());
                p.displayClientMessage(msg("The arena was reset", ChatFormatting.AQUA), true);
            }
            XenoWarriors.LOGGER.info("Tower resetmap: slot {} rebuilt at radius {}", r.slot, r.radius);
        }
        return runs.size();
    }
}

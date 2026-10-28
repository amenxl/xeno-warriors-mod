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
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Tower {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, XenoWarriors.id("tower"));
    public static final int ARENA_R = 16, SPACING = 512, Y = 100, CHECKPOINT = 10;
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
        List<String> pool = List.of();
        TowerMod mod = TowerMod.NONE;
        boolean bossFloor;
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Run> BY_PLAYER = new HashMap<>();
    private static final Set<Integer> BUILT = new HashSet<>();

    private Tower() {}

    public static boolean is(Level l) { return l.dimension() == LEVEL; }

    public static int floorOf(ServerPlayer p) { Run r = BY_PLAYER.get(p.getUUID()); return r == null ? 0 : r.floor; }

    private static Component msg(String s, ChatFormatting c) { return Component.literal(s).withStyle(c); }

    // ------------------------------------------------------------------------------------------------ entering / leaving

    public static void enter(ServerPlayer p) {
        if (!Collection.unlocked(p)) { p.displayClientMessage(msg("Read the Collection Book at the Element School first", ChatFormatting.GRAY), true); return; }
        if (BY_PLAYER.containsKey(p.getUUID())) { p.displayClientMessage(msg("You're already in the Tower", ChatFormatting.GRAY), true); return; }
        if (is(p.level()) || com.xenowarriors.room.RoomTech.is(p.level())) return;
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
        if (BUILT.add(slot)) arena(tl, r.center);
        RUNS.put(slot, r);
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
        if (BY_PLAYER.containsKey(p.getUUID()) || is(p.level())) return;
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
        double a = r.players.size() * 1.6;
        p.teleportTo(tl, r.center.getX() + .5 + Math.cos(a) * 3, Y + 1, r.center.getZ() + .5 + Math.sin(a) * 3, p.getYRot(), 0);
        p.sendSystemMessage(msg("TOWER - kills here save to your Collection at floor " + CHECKPOINT + ", then every " + CHECKPOINT + " floors. "
                + "You can heal " + (int) (HEAL_PER_FLOOR / 2) + " hearts per floor.", ChatFormatting.AQUA));
    }

    public static void leave(ServerPlayer p, String why) {
        Run r = BY_PLAYER.remove(p.getUUID());
        if (r == null) return;
        TowerBosses.removePlayer(p);
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
                for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(r.center).inflate(ARENA_R + 4, 12, ARENA_R + 4), e -> e instanceof ItemEntity || e instanceof ExperienceOrb))
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
        ServerLevel tl = server.getLevel(LEVEL);
        if (tl == null) return;
        for (UUID id : r.alive) { Entity e = tl.getEntity(id); if (e != null) e.discard(); }
        for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(r.center).inflate(ARENA_R + 8, 30, ARENA_R + 8), e -> !(e instanceof net.minecraft.world.entity.player.Player)
                && !(e instanceof com.xenowarriors.bot.PartyBotEntity))) e.discard();
    }

    // ------------------------------------------------------------------------------------------------ floors and waves

    /** Enemies you (or your party) haven't finished in the Collection. */
    private static List<String> pool(Run r, MinecraftServer server) {
        List<String> out = new ArrayList<>();
        ServerPlayer leader = server.getPlayerList().getPlayer(r.leader);
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
        r.nextAt = (tl == null ? 0 : tl.getGameTime()) + 80;
        if (r.floor > 1 && (r.floor - 1) % 5 == 0) r.pool = pool(r, server);         // finished ones drop out as you go
        boolean checkpoint = r.floor % CHECKPOINT == 0;
        for (UUID id : r.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            r.healLeft.put(id, r.mod == TowerMod.NO_HEALING ? 0F : HEAL_PER_FLOOR);
            Collection.setBest(p, r.floor);
            TowerBoard.reached(p, r.floor);
            String sub = r.bossFloor ? (checkpoint ? "Checkpoint saved - " : "") + "a boss is coming..."
                    : r.mod != TowerMod.NONE ? r.mod.title.toUpperCase() + ": " + r.mod.desc
                    : checkpoint ? "Checkpoint - Collection saved!" : r.waves + " waves  -  " + (int) (HEAL_PER_FLOOR / 2) + " hearts of healing";
            title(p, (r.bossFloor ? "BOSS " : "") + "FLOOR " + r.floor, sub);
            if (r.mod != TowerMod.NONE) p.sendSystemMessage(msg("Floor " + r.floor + ": " + r.mod.title + " - " + r.mod.desc, ChatFormatting.LIGHT_PURPLE));
            if (checkpoint) save(p, r);
        }
        if (tl != null) {
            tl.playSound(null, r.center, checkpoint ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 1.2F);
            floorColor(tl, r);
        }
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
        t.putInt(id, t.getInt(id) + 1);
        return true;
    }

    private static void spawnWave(ServerLevel tl, Run r) {
        int people = r.players.size();
        if (r.bossFloor) {
            List<ServerPlayer> ps = new ArrayList<>();
            for (UUID id : r.players) { ServerPlayer p = tl.getServer().getPlayerList().getPlayer(id); if (p != null && p.level() == tl) ps.add(p); }
            List<Mob> spawned = TowerBosses.spawn(tl, r.center, r.floor, ps);
            for (Mob m : spawned) r.alive.add(m.getUUID());
            if (spawned.isEmpty()) r.bossFloor = false;          // (couldn't make the boss: an ordinary clear, no boss reward)
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
            double a = rnd.nextDouble() * Math.PI * 2, d = 8 + rnd.nextDouble() * (ARENA_R - 10);
            m.moveTo(r.center.getX() + .5 + Math.cos(a) * d, Y + 1, r.center.getZ() + .5 + Math.sin(a) * d, rnd.nextFloat() * 360F, 0);
            try { m.finalizeSpawn(tl, tl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
            m.setPersistenceRequired();
            float hp = 0.07F * r.floor, dmg = 0.045F * r.floor;
            var mh = m.getAttribute(Attributes.MAX_HEALTH);
            if (mh != null && hp > 0) mh.addPermanentModifier(new AttributeModifier(HP, "Tower floor", hp, AttributeModifier.Operation.MULTIPLY_TOTAL));
            var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (md != null && dmg > 0) md.addPermanentModifier(new AttributeModifier(DMG, "Tower floor", dmg, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (r.mod == TowerMod.ELITE && mh != null) mh.addPermanentModifier(new AttributeModifier(UUID.fromString("5b0e2c11-7a3d-4c55-9a51-0d1f2e3c4b03"), "Tower elite", 0.6, AttributeModifier.Operation.MULTIPLY_TOTAL));
            if (r.mod == TowerMod.ELITE || r.mod == TowerMod.DARKNESS) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, -1, 0, false, false));
            if (r.mod == TowerMod.SWIFT) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, -1, 1, false, false));
            m.setHealth(m.getMaxHealth());
            m.getPersistentData().putBoolean("xw_tower", true);
            if (!targets.isEmpty()) m.setTarget(targets.get(rnd.nextInt(targets.size())));
            tl.addFreshEntity(m);
            tl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, m.getX(), m.getY() + 1, m.getZ(), 20, .4, .6, .4, .3);
            r.alive.add(m.getUUID());
            made++;
        }
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
            for (Run r : RUNS.values()) if (extra.position().distanceTo(Vec3.atCenterOf(r.center)) < ARENA_R + 8) { r.alive.add(extra.getUUID()); break; }
        for (Run r : new ArrayList<>(RUNS.values())) {
            if (now % 20 == 0) modEffects(server, tl, r);
            for (UUID id : new ArrayList<>(r.players)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) { BY_PLAYER.remove(id); r.players.remove(id); r.pending.remove(id); continue; }
                if (!is(p.level())) leave(p, "You left the Tower");
                else if (p.getY() < Y - 12) p.teleportTo(r.center.getX() + .5, Y + 1, r.center.getZ() + .5);      // fell off
            }
            if (r.players.isEmpty()) { close(server, r); continue; }
            r.alive.removeIf(id -> {
                Entity e = tl.getEntity(id);
                if (!(e instanceof LivingEntity le) || !le.isAlive()) return true;
                if (e.getY() < Y - 12 || e.position().distanceTo(Vec3.atCenterOf(r.center)) > ARENA_R + 12) {
                    if (TowerBosses.isBoss(e)) { e.teleportTo(r.center.getX() + .5, Y + 1, r.center.getZ() + .5); return false; }   // a boss can't be knocked out of the fight
                    e.discard(); return true;                     // fell out / wandered off
                }
                if (e instanceof Mob m && m.getTarget() == null) {
                    var near = tl.getNearestPlayer(m, 48);
                    if (near != null && !near.isCreative() && !near.isSpectator()) m.setTarget(near);
                }
                return false;
            });
            if (r.state == 0 && now >= r.nextAt) { spawnWave(tl, r); r.state = 1; }
            else if (r.state == 1 && r.alive.isEmpty()) {
                r.wave++;
                r.state = 0;
                if (r.wave >= r.waves) {
                    vacuum(tl, r, server);
                    for (UUID id : r.players) {
                        ServerPlayer p = server.getPlayerList().getPlayer(id);
                        if (p == null) continue;
                        if (r.bossFloor) TowerRewards.boss(p, r.floor);
                        if (r.mod == TowerMod.HORDE) TowerRewards.horde(p, r.floor);
                    }
                    for (UUID id : r.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) title(p, "FLOOR " + r.floor + " CLEARED", "Next floor..."); }
                    r.nextAt = now + 60;
                    r.state = 2;
                } else r.nextAt = now + 50;
            } else if (r.state == 2 && now >= r.nextAt) nextFloor(server, r);
        }
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
        nextFloor(p.server, r);
    }

    /** Drops and XP from the floor fly to the players. */
    private static void vacuum(ServerLevel tl, Run r, MinecraftServer server) {
        List<ServerPlayer> ps = new ArrayList<>();
        for (UUID id : r.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null && p.level() == tl) ps.add(p); }
        if (ps.isEmpty()) return;
        int i = 0;
        for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(r.center).inflate(ARENA_R + 4, 12, ARENA_R + 4), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
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

    private static void arena(ServerLevel tl, BlockPos c) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -ARENA_R - 2; dx <= ARENA_R + 2; dx++) for (int dz = -ARENA_R - 2; dz <= ARENA_R + 2; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > ARENA_R + 1.5) continue;
            int x = c.getX() + dx, z = c.getZ() + dz;
            tl.setBlock(m.set(x, Y - 1, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            BlockState top = d > ARENA_R - 0.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : (Math.abs(dx) + Math.abs(dz)) % 6 == 0 && d > 3 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
            tl.setBlock(m.set(x, Y, z), top, 2);
            if (d > ARENA_R + 0.5) for (int y = 1; y <= 5; y++)
                tl.setBlock(m.set(x, Y + y, z), y == 5 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 2);
        }
        for (int y = 1; y <= 40; y++) for (int dx = -ARENA_R - 2; dx <= ARENA_R + 2; dx++) for (int dz = -ARENA_R - 2; dz <= ARENA_R + 2; dz++)
            if (Math.sqrt(dx * dx + dz * dz) > ARENA_R + 0.5 && Math.sqrt(dx * dx + dz * dz) <= ARENA_R + 1.5 && y > 5)
                tl.setBlock(m.set(c.getX() + dx, Y + y, c.getZ() + dz), Blocks.BARRIER.defaultBlockState(), 2);
        tl.setBlock(m.set(c.getX(), Y, c.getZ()), Blocks.BEACON.defaultBlockState(), 2);
    }

    /** The ring around the edge changes color every 10 floors. */
    private static void floorColor(ServerLevel tl, Run r) {
        BlockState[] ring = {Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Blocks.LIME_STAINED_GLASS.defaultBlockState(), Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),
                Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), Blocks.BLACK_STAINED_GLASS.defaultBlockState()};
        BlockState s = ring[Math.min(ring.length - 1, (r.floor - 1) / 10)];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos c = r.center;
        for (int dx = -ARENA_R - 2; dx <= ARENA_R + 2; dx++) for (int dz = -ARENA_R - 2; dz <= ARENA_R + 2; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > ARENA_R + 0.5 && d <= ARENA_R + 1.5) for (int y = 1; y <= 4; y++) tl.setBlock(m.set(c.getX() + dx, Y + y, c.getZ() + dz), s, 2);
        }
    }
}

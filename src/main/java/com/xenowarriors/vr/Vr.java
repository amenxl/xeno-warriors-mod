package com.xenowarriors.vr;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Yuri's VR room: the room "reshapes itself" into an outdoor place (a pocket dimension per session). Free training
 * against VR Ultima (20%) in any environment, plus challenges with rules - play as a certain character, only so many
 * in the party, no healing, a time limit - with a reward for the first clear. Nothing in VR is real: no drops, no
 * Collection or bounty kills, and getting beaten just ends the session.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Vr {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, XenoWarriors.id("vr"));
    public static final String TAG = "xw_vr", DONE = "xw_vr_done";
    static final int R = 18, SPACING = 512, Y = 100;

    /** Environments: name, floor, accent, decoration. */
    public record Env(String name, Block floor, Block accent, Block deco, Block deco2) {}
    public static final Env[] ENVS = {
            new Env("Grassy Plains", Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.OAK_LOG, Blocks.OAK_LEAVES),
            new Env("Desert", Blocks.SAND, Blocks.SANDSTONE, Blocks.CACTUS, Blocks.DEAD_BUSH),
            new Env("Snowfield", Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES),
            new Env("City Rooftop", Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.IRON_BARS, Blocks.SEA_LANTERN),
            new Env("Sky Island", Blocks.QUARTZ_BLOCK, Blocks.GOLD_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.WHITE_STAINED_GLASS),
            new Env("Volcano", Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.BASALT, Blocks.SHROOMLIGHT),
            new Env("The Grid (default VR)", Blocks.WHITE_CONCRETE, Blocks.SEA_LANTERN, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.WHITE_CONCRETE),
    };

    /** A challenge: rules + what to beat. character = null for anyone; party min/max counts players + bots. */
    public record Challenge(String name, String desc, CharacterType character, int partyMin, int partyMax, boolean noHeal, int timeLimit,
                            int env, int ultimas, String[] waves, int waveCount) {}
    public static final Challenge[] CHALLENGES = {
            new Challenge("Ultimate Sparring", "Beat VR Ultima (20%) alone.", null, 1, 1, false, 0, 6, 1, null, 0),
            new Challenge("Fox Fighter", "Play as Mikiko: 3 waves of C-souls.", CharacterType.MIKIKO, 1, 4, false, 0, 0, 0,
                    new String[]{"xenowarriors:corrupted_soul", "xenowarriors:corrupted_villager"}, 3),
            new Challenge("King of the Sky", "Play as Rhalix: clear the sky island.", CharacterType.RHALIX, 1, 4, false, 0, 4, 0,
                    new String[]{"minecraft:phantom", "minecraft:vex", "minecraft:skeleton"}, 3),
            new Challenge("Data Duel", "Play as Ultima: beat two VR Ultimas at once.", CharacterType.ULTIMA, 1, 1, false, 0, 6, 2, null, 0),
            new Challenge("Duo Trial", "Exactly 2 in the party: 4 waves.", null, 2, 2, false, 0, 3, 0,
                    new String[]{"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:pillager"}, 4),
            new Challenge("Full Squad", "3 or 4 in the party: three VR Ultimas.", null, 3, 4, false, 0, 6, 3, null, 0),
            new Challenge("No Healing", "No healing at all: 3 waves.", null, 1, 4, true, 0, 5, 0,
                    new String[]{"minecraft:husk", "minecraft:blaze", "minecraft:wither_skeleton"}, 3),
            new Challenge("Speed Run", "Clear 3 waves in 90 seconds.", null, 1, 4, false, 90, 1, 0,
                    new String[]{"minecraft:zombie", "minecraft:husk", "minecraft:skeleton"}, 3),
            new Challenge("Solo Survivor", "Alone, no healing: VR Ultima + 2 waves.", null, 1, 1, true, 0, 2, 1,
                    new String[]{"minecraft:stray", "minecraft:zombie"}, 2),
    };

    private record Back(ResourceKey<Level> dim, Vec3 pos, float yRot) {}

    private static final class Session {
        int slot, challenge = -1, env, wave, state; long started, nextAt;
        BlockPos center;
        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> alive = new HashSet<>();
        final Map<UUID, Back> back = new HashMap<>();
    }

    private static final Map<Integer, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();

    private Vr() {}

    public static boolean is(Level l) { return l.dimension() == LEVEL; }

    public static boolean inVr(Entity e) { return e.getPersistentData().getBoolean(TAG) || is(e.level()); }

    private static Component msg(String s, ChatFormatting c) { return Component.literal(s).withStyle(c); }

    // ------------------------------------------------------------------------------------------------ menu

    public static void open(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(DONE);
        boolean[] done = new boolean[CHALLENGES.length];
        for (int i = 0; i < done.length; i++) done[i] = t.getBoolean(CHALLENGES[i].name);
        ModNetwork.sendToPlayer(new com.xenowarriors.network.OpenVrPacket(done, BY_PLAYER.containsKey(p.getUUID())), p);
    }

    /** kind 0 = free training (arg = environment), 1 = challenge (arg = index), 2 = leave. */
    public static void action(ServerPlayer p, int kind, int arg) {
        if (kind == 2) { leave(p, "You took the VR goggles off."); return; }
        if (BY_PLAYER.containsKey(p.getUUID()) || is(p.level())) return;
        List<ServerPlayer> team = new ArrayList<>();
        team.add(p);
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) if (m != p && m.level() == p.level() && m.distanceToSqr(p) < 16 * 16 && !BY_PLAYER.containsKey(m.getUUID())) team.add(m);
        int count = 0;
        for (ServerPlayer m : team) count += 1 + com.xenowarriors.bot.PartyBots.bots(m).size();
        if (kind == 1) {
            if (arg < 0 || arg >= CHALLENGES.length) return;
            Challenge c = CHALLENGES[arg];
            if (count < c.partyMin || count > c.partyMax) {
                p.sendSystemMessage(msg(c.name + " needs " + (c.partyMin == c.partyMax ? c.partyMin : c.partyMin + "-" + c.partyMax)
                        + " in the party (people + bots nearby). You have " + count + ".", ChatFormatting.RED));
                return;
            }
            if (c.character != null) for (ServerPlayer m : team) if (ModCapabilities.getType(m) != c.character) {
                p.sendSystemMessage(msg(c.name + ": everyone has to play as " + c.character.getDisplayName().getString() + " (" + m.getName().getString() + " isn't).", ChatFormatting.RED));
                return;
            }
            if (c.character != null) for (ServerPlayer m : team) for (var b : com.xenowarriors.bot.PartyBots.bots(m)) if (b.character() != c.character) {
                p.sendSystemMessage(msg(c.name + ": bots have to be " + c.character.getDisplayName().getString() + " too.", ChatFormatting.RED));
                return;
            }
        }
        ServerLevel vl = p.server.getLevel(LEVEL);
        if (vl == null) { p.sendSystemMessage(msg("The VR world isn't loaded.", ChatFormatting.RED)); return; }
        int slot = 0;
        while (SESSIONS.containsKey(slot)) slot++;
        Session s = new Session();
        s.slot = slot; s.challenge = kind == 1 ? arg : -1;
        s.env = kind == 1 ? CHALLENGES[arg].env : Math.floorMod(arg, ENVS.length);
        s.center = new BlockPos(slot * SPACING + SPACING / 2, Y, SPACING / 2);
        s.started = vl.getGameTime();
        build(vl, s.center, ENVS[s.env]);
        SESSIONS.put(slot, s);
        int i = 0;
        for (ServerPlayer m : team) {
            s.players.add(m.getUUID());
            s.back.put(m.getUUID(), new Back(m.level().dimension(), m.position(), m.getYRot()));
            BY_PLAYER.put(m.getUUID(), s);
            double a = i++ * 1.3;
            m.teleportTo(vl, s.center.getX() + .5 + Math.cos(a) * 3, Y + 1, s.center.getZ() + .5 + Math.sin(a) * 3, m.getYRot(), 0);
            title(m, "VR " + (kind == 1 ? "CHALLENGE" : "TRAINING"), kind == 1 ? CHALLENGES[arg].name + " - " + CHALLENGES[arg].desc : ENVS[s.env].name + " - VR Ultima (20%)");
        }
        s.nextAt = vl.getGameTime() + 60;
        vl.playSound(null, s.center, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1.8F);
    }

    private static void title(ServerPlayer p, String t, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 10));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(t).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
    }

    public static void leave(ServerPlayer p, String why) {
        Session s = BY_PLAYER.remove(p.getUUID());
        if (s == null) return;
        s.players.remove(p.getUUID());
        Back b = s.back.remove(p.getUUID());
        if (is(p.level()) && b != null) {
            ServerLevel to = p.server.getLevel(b.dim());
            if (to == null) to = p.server.overworld();
            p.teleportTo(to, b.pos().x, b.pos().y, b.pos().z, b.yRot(), 0);
        }
        p.sendSystemMessage(msg(why, ChatFormatting.GREEN));
        if (s.players.isEmpty()) close(p.server, s);
    }

    private static void close(MinecraftServer server, Session s) {
        SESSIONS.remove(s.slot);
        ServerLevel vl = server.getLevel(LEVEL);
        if (vl == null) return;
        for (Entity e : vl.getEntitiesOfClass(Entity.class, new AABB(s.center).inflate(R + 10, 40, R + 10), e -> !(e instanceof Player) && !(e instanceof com.xenowarriors.bot.PartyBotEntity))) e.discard();
    }

    // ------------------------------------------------------------------------------------------------ running it

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel vl = server.getLevel(LEVEL);
        if (vl == null) return;
        long now = vl.getGameTime();
        if (now % 5 != 0) return;
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            for (UUID id : new ArrayList<>(s.players)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) { BY_PLAYER.remove(id); s.players.remove(id); continue; }
                if (!is(p.level())) leave(p, "You left VR.");
                else if (p.getY() < Y - 10) p.teleportTo(s.center.getX() + .5, Y + 1, s.center.getZ() + .5);
            }
            if (s.players.isEmpty()) { close(server, s); continue; }
            s.alive.removeIf(id -> !(vl.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
            Challenge c = s.challenge >= 0 ? CHALLENGES[s.challenge] : null;
            if (c != null && c.timeLimit > 0) {
                long left = c.timeLimit * 20L - (now - s.started);
                if (left <= 0) { finish(server, s, false, "Time's up!"); continue; }
                if (now % 20 == 0) for (UUID id : s.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) p.displayClientMessage(msg("Time left: " + left / 20 + "s", ChatFormatting.YELLOW), true); }
            }
            if (s.state == 0 && now >= s.nextAt) { spawn(vl, s, c); s.state = 1; }
            else if (s.state == 1 && s.alive.isEmpty()) {
                s.wave++;
                int total = c == null ? 1 : Math.max(1, (c.ultimas > 0 ? 1 : 0) + c.waveCount);
                if (s.wave >= total) {
                    if (c == null) {                                            // free training: another round
                        for (UUID id : s.players) { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) title(p, "VR Ultima is down", "You win! Another one coming..."); }
                        s.wave = 0; s.state = 0; s.nextAt = now + 100;
                    } else finish(server, s, true, "VR Ultima is down. You win!");
                } else { s.state = 0; s.nextAt = now + 50; }
            }
        }
    }

    private static void spawn(ServerLevel vl, Session s, Challenge c) {
        var r = vl.getRandom();
        boolean ultimaWave = c == null || (c.ultimas > 0 && s.wave == 0);
        List<Mob> made = new ArrayList<>();
        if (ultimaWave) {
            int n = c == null ? 1 : c.ultimas;
            for (int i = 0; i < n; i++) { VrUltimaEntity u = VrEntities.VR_ULTIMA.get().create(vl); if (u != null) made.add(u); }
        } else {
            int n = 3 + s.wave * 2 + (s.players.size() - 1) * 2;
            for (int i = 0; i < n; i++) {
                EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation(c.waves[r.nextInt(c.waves.length)]));
                if (t != null && t.create(vl) instanceof Mob m) made.add(m);
            }
        }
        List<ServerPlayer> ps = new ArrayList<>();
        for (UUID id : s.players) { ServerPlayer p = vl.getServer().getPlayerList().getPlayer(id); if (p != null) ps.add(p); }
        for (Mob m : made) {
            double a = r.nextDouble() * Math.PI * 2, d = 9 + r.nextDouble() * 6;
            m.moveTo(s.center.getX() + .5 + Math.cos(a) * d, Y + 1, s.center.getZ() + .5 + Math.sin(a) * d, r.nextFloat() * 360F, 0);
            try { m.finalizeSpawn(vl, vl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
            m.getPersistentData().putBoolean(TAG, true);
            m.getPersistentData().putBoolean("xw_mb_rolled", true);
            m.setPersistenceRequired();
            if (m instanceof VrUltimaEntity) m.setCustomName(msg("VR Ultima (20%)", ChatFormatting.GREEN));
            if (!ps.isEmpty()) m.setTarget(ps.get(r.nextInt(ps.size())));
            vl.addFreshEntity(m);
            vl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.25F, 1F, .6F), 1.5F), m.getX(), m.getY() + 1, m.getZ(), 20, .4, .8, .4, 0);
            s.alive.add(m.getUUID());
        }
    }

    private static void finish(MinecraftServer server, Session s, boolean won, String line) {
        Challenge c = s.challenge >= 0 ? CHALLENGES[s.challenge] : null;
        for (UUID id : new ArrayList<>(s.players)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (won && c != null) reward(p, c);
            leave(p, won ? line : "Challenge failed: " + line);
        }
    }

    private static void reward(ServerPlayer p, Challenge c) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag done = root.getCompound(DONE);
        boolean first = !done.getBoolean(c.name);
        done.putBoolean(c.name, true); root.put(DONE, done);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        var r = p.getRandom();
        give(p, new ItemStack(Items.EMERALD, first ? 12 + r.nextInt(8) : 3 + r.nextInt(3)));
        for (var st : com.xenowarriors.stats.Stat.values())
            com.xenowarriors.stats.Stats.addXp(p, st, st.needed(com.xenowarriors.stats.Stats.level(p, st)) * (first ? 0.6F : 0.15F));
        if (first) {
            var shard = ForgeRegistries.ITEMS.getValue(XenoWarriors.id("data_shard"));
            var pot = ForgeRegistries.ITEMS.getValue(XenoWarriors.id("karma_potion"));
            if (shard != null) give(p, new ItemStack(shard, 2));
            if (pot != null) give(p, new ItemStack(pot));
        }
        p.sendSystemMessage(msg((first ? "First clear! " : "Cleared again. ") + c.name + " - rewards added.", ChatFormatting.GOLD));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1.2F);
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    // ------------------------------------------------------------------------------------------------ rules

    /** Nothing in VR is real: no drops, no XP orbs. */
    @SubscribeEvent public static void onDrops(LivingDropsEvent e) { if (inVr(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void onXp(LivingExperienceDropEvent e) { if (inVr(e.getEntity())) e.setCanceled(true); }

    /** Beaten in VR: the session ends for you (you're fine - it's VR). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !BY_PLAYER.containsKey(p.getUUID()) || !is(p.level())) return;
        event.setCanceled(true);
        p.setHealth(p.getMaxHealth()); p.clearFire(); p.removeAllEffects();
        Session s = BY_PLAYER.get(p.getUUID());
        leave(p, "VR: you're down. Session over for you.");
        if (s != null && s.players.isEmpty()) close(p.server, s);
    }

    /** "No Healing" challenges. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Session s = BY_PLAYER.get(p.getUUID());
        if (s != null && s.challenge >= 0 && CHALLENGES[s.challenge].noHeal && is(p.level())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && BY_PLAYER.containsKey(p.getUUID())) leave(p, "Left VR.");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && is(p.level()) && !BY_PLAYER.containsKey(p.getUUID())) {
            ServerLevel ow = p.server.overworld();
            BlockPos sp = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : ow.getSharedSpawnPos();
            p.teleportTo(ow, sp.getX() + .5, sp.getY() + 1, sp.getZ() + .5, p.getYRot(), 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ the environment

    private static void build(ServerLevel l, BlockPos c, Env env) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        var r = l.getRandom();
        for (int dx = -R - 2; dx <= R + 2; dx++) for (int dz = -R - 2; dz <= R + 2; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            int x = c.getX() + dx, z = c.getZ() + dz;
            for (int y = 0; y <= 12; y++) l.setBlock(m.set(x, Y + y, z), Blocks.AIR.defaultBlockState(), 2);
            if (d > R + 1.5) continue;
            boolean grid = env == ENVS[6] && (Math.floorMod(dx, 4) == 0 || Math.floorMod(dz, 4) == 0);
            BlockState top = grid ? env.accent.defaultBlockState() : (r.nextInt(7) == 0 ? env.accent : env.floor).defaultBlockState();
            l.setBlock(m.set(x, Y, z), top, 2);
            l.setBlock(m.set(x, Y - 1, z), Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
            if (d > R + 0.5) for (int y = 1; y <= 30; y++) l.setBlock(m.set(x, Y + y, z), Blocks.BARRIER.defaultBlockState(), 2);
            else if (d > R - 0.5 && (dx + dz) % 3 == 0) l.setBlock(m.set(x, Y + 1, z), Blocks.LIME_STAINED_GLASS.defaultBlockState(), 2);   // the VR boundary
        }
        if (env == ENVS[6]) return;
        for (int i = 0; i < 9; i++) {                                    // scenery: trees / cacti / pillars...
            double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * (R - 8);
            int x = c.getX() + (int) Math.round(Math.cos(a) * d), z = c.getZ() + (int) Math.round(Math.sin(a) * d);
            int h = 2 + r.nextInt(3);
            for (int y = 1; y <= h; y++) l.setBlock(m.set(x, Y + y, z), env.deco.defaultBlockState(), 2);
            BlockState top = env.deco2.defaultBlockState();
            if (env.deco2 == Blocks.DEAD_BUSH) l.setBlock(m.set(x + 1, Y + 1, z), top, 2);
            else for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) l.setBlock(m.set(x + dx, Y + h + 1, z + dz), top, 2);
        }
    }
}

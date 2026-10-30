package com.xenowarriors.npc;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Sky Hater raids: now and then at night, a village with a player in it gets raided. Three waves of Sky Haters glide
 * down out of the sky (the last led by a Sky Hater Captain); beat them all and the village's people shower you with
 * emeralds and Sky Ore.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkyHaterRaids {
    public static final int WAVES = 3;
    private static final double AREA = 96;
    private static final int CHANCE = 260;                    // 1 in this per check (every 10 s) at night in a village
    private static final long PLAYER_COOLDOWN = 2 * 24000L;
    private static final List<Raid> RAIDS = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private SkyHaterRaids() {}

    private static final class Raid {
        final ServerLevel level;
        final BlockPos center;
        final ServerBossEvent bar = new ServerBossEvent(Component.literal("Sky Hater Raid").withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        final Set<UUID> raiders = new HashSet<>();
        final Set<UUID> fought = new HashSet<>();
        int wave, waveSize, age, nextWaveAt = 60, empty;
        Raid(ServerLevel level, BlockPos center) { this.level = level; this.center = center; }
    }

    private static boolean village(ServerLevel level, BlockPos pos) {
        if (level.isVillage(pos)) return true;
        return level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(pos).inflate(40),
                m -> m instanceof Villager || (m instanceof HumanNpcEntity h && !h.raider())).size() >= 5;
    }

    /** Starts a raid on the village around a player (false if there's no village or a raid is already on). */
    public static boolean start(ServerPlayer p, boolean force) {
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        for (Raid r : RAIDS) if (r.level == level && r.center.closerThan(at, 128)) return false;
        if (!force && !village(level, at)) return false;
        Raid raid = new Raid(level, at);
        RAIDS.add(raid);
        LAST.put(p.getUUID(), level.getGameTime());
        for (ServerPlayer q : level.players()) if (q.blockPosition().closerThan(at, AREA)) {
            q.sendSystemMessage(Component.literal("The Sky Haters are raiding the village! Hold them off!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            com.xenowarriors.network.PopupPacket.send(q, com.xenowarriors.network.PopupPacket.DANGER, q.getUUID(), com.xenowarriors.character.CharacterType.NONE.ordinal(), -1,
                    level.isNight() ? "Night raid!" : "Raid!", "The Sky Haters are raiding the village! Hold them off!", "");
            level.playSound(null, q.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64F, 1F);
        }
        return true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld.getGameTime() % 200 == 0 && overworld.isNight() && overworld.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            for (ServerPlayer p : overworld.players()) {
                if (p.isCreative() || p.isSpectator()) continue;
                Long last = LAST.get(p.getUUID());
                if (last != null && overworld.getGameTime() - last < PLAYER_COOLDOWN) continue;
                if (overworld.random.nextInt(CHANCE) != 0) continue;
                start(p, false);
            }
        }
        RAIDS.removeIf(SkyHaterRaids::tick);
    }

    /** One tick of a raid; true when it's over. */
    /** Is this raider part of a raid still going? (Ones left over from a raid that ended, or a restart, leave.) */
    public static boolean listed(UUID id) {
        for (Raid r : RAIDS) if (r.raiders.contains(id)) return true;
        return false;
    }

    private static boolean tick(Raid r) {
        r.age++;
        ServerLevel level = r.level;
        // who's around sees the bar
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (p.blockPosition().closerThan(r.center, AREA) && !p.isSpectator()) near.add(p);
        for (ServerPlayer p : new ArrayList<>(r.bar.getPlayers())) if (!near.contains(p)) r.bar.removePlayer(p);
        for (ServerPlayer p : near) {
            r.bar.addPlayer(p);
            r.fought.add(p.getUUID());
        }
        r.empty = near.isEmpty() ? r.empty + 1 : 0;
        if (r.empty > 1200 || r.age > 15000) {          // everyone left, or it dragged on too long: they move on
            end(r, false);
            return true;
        }
        // the raiders still standing
        List<HumanNpcEntity> alive = new ArrayList<>();
        int unloaded = 0;
        for (Iterator<UUID> it = r.raiders.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Entity e = level.getEntity(id);
            if (e instanceof HumanNpcEntity h && h.isAlive()) alive.add(h);
            else if (e != null) it.remove();
            else unloaded++;                                    // (in an unloaded chunk: still part of the raid, not beaten)
        }
        if (r.waveSize > 0) r.bar.setProgress(Math.max(0F, alive.size() / (float) r.waveSize));
        for (HumanNpcEntity h : alive) {
            if (!h.onGround() && h.tickCount % 4 == 0) level.sendParticles(ParticleTypes.CLOUD, h.getX(), h.getY() + 1.8, h.getZ(), 1, 0.3, 0.1, 0.3, 0);
            // head for the village if they've got nobody to fight yet
            if (h.getTarget() == null && h.tickCount % 40 == 0 && h.onGround() && !h.blockPosition().closerThan(r.center, 10))
                h.getNavigation().moveTo(r.center.getX() + 0.5, r.center.getY(), r.center.getZ() + 0.5, 1.1);
        }
        if (alive.isEmpty() && unloaded == 0) {
            if (r.nextWaveAt < 0) {
                if (r.wave >= WAVES) {
                    end(r, true);
                    return true;
                }
                r.nextWaveAt = r.age + 120;
                for (ServerPlayer p : near) p.displayClientMessage(Component.literal("Wave " + r.wave + " beaten - more are coming!")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            if (r.age >= r.nextWaveAt) {
                spawnWave(r);
                r.nextWaveAt = -1;
            }
        }
        return false;
    }

    private static void spawnWave(Raid r) {
        ServerLevel level = r.level;
        r.wave++;
        int n = 3 + r.wave * 2 + level.getDifficulty().getId() - 1;
        boolean captain = r.wave == WAVES;
        r.waveSize = n + (captain ? 1 : 0);
        r.bar.setName(Component.literal("Sky Hater Raid - Wave " + r.wave + "/" + WAVES).withStyle(ChatFormatting.RED));
        r.bar.setProgress(1F);
        double base = level.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < r.waveSize; i++) {
            HumanNpcEntity h = ModNpcs.HUMAN.get().create(level);
            if (h == null) continue;
            double a = base + (level.random.nextDouble() - 0.5) * 1.6, d = 22 + level.random.nextInt(10);
            int x = r.center.getX() + (int) Math.round(Math.cos(a) * d), z = r.center.getZ() + (int) Math.round(Math.sin(a) * d);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 22 + level.random.nextInt(8);
            h.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360F, 0F);
            h.finalizeSpawn(level, level.getCurrentDifficultyAt(h.blockPosition()), MobSpawnType.EVENT, null, null);
            h.makeRaider(captain && i == 0);
            h.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0, false, false));
            level.addFreshEntity(h);
            r.raiders.add(h.getUUID());
        }
        level.playSound(null, r.center, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 48F, captain ? 0.8F : 1F);
        for (ServerPlayer p : level.players()) if (p.blockPosition().closerThan(r.center, AREA))
            p.displayClientMessage(Component.literal(captain ? "The Sky Hater Captain is coming down!" : "Sky Haters are gliding in!")
                    .withStyle(ChatFormatting.RED), true);
    }

    private static void end(Raid r, boolean won) {
        ServerLevel level = r.level;
        r.bar.removeAllPlayers();
        if (!won) {
            for (UUID id : r.raiders) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            return;
        }
        // the people come out to thank whoever fought for them
        List<net.minecraft.world.entity.Mob> people = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(r.center).inflate(40),
                m -> m.isAlive() && (m instanceof Villager || (m instanceof HumanNpcEntity h && !h.raider() && !h.carriesSoul())));
        for (UUID id : r.fought) {
            if (!(level.getPlayerByUUID(id) instanceof ServerPlayer p) || !p.blockPosition().closerThan(r.center, AREA)) continue;
            p.sendSystemMessage(Component.literal("The village is safe! The people bring you gifts.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            com.xenowarriors.faction.Reputation.add(p, com.xenowarriors.faction.Faction.TOWN, 40);
            level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1F);
            p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, 0, false, false, true));
            List<ItemStack> gifts = List.of(new ItemStack(Items.EMERALD, 6 + level.random.nextInt(9)),
                    new ItemStack(ModItems.SKY_CRYSTAL.get(), 2 + level.random.nextInt(4)),
                    new ItemStack(ModItems.SKY_ORE.get(), 1 + level.random.nextInt(2)));
            for (ItemStack gift : gifts) {
                Vec3 from = people.isEmpty() ? p.position().add(0, 1.5, 0) : people.get(level.random.nextInt(people.size())).getEyePosition();
                ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, gift);
                Vec3 v = p.position().subtract(from);
                double dist = Math.max(1, v.horizontalDistance());
                item.setDeltaMovement(v.x / dist * Math.min(0.6, dist * 0.08), 0.3, v.z / dist * Math.min(0.6, dist * 0.08));
                item.setPickUpDelay(10);
                level.addFreshEntity(item);
            }
        }
        for (var m : people) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + 2.1, m.getZ(), 4, 0.3, 0.2, 0.3, 0);
        HumanReactions.everyone(level, Vec3.atCenterOf(r.center), 48, HumanReactions.CHEER, 120);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        for (Raid r : RAIDS) r.bar.removeAllPlayers();
        RAIDS.clear();
    }

    static boolean levelIs(Level a, Level b) { return a == b; }
}

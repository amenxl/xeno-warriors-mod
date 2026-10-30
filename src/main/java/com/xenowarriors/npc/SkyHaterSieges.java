package com.xenowarriors.npc;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Sky Hater sieges: now and then, when a player is near a sky island with Skylanders on it, a gang of Sky Haters shows
 * up underneath and builds a ladder tower up to it (a cobblestone pillar with ladders up one side and scaffold
 * platforms every few blocks, the lead builder riding the top as it goes up). Once it reaches the island they bridge
 * across, climb up and go for the Skylanders. Kill the builders and the tower stops where it is.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkyHaterSieges {
    private static final int CHECK_EVERY = 1200, CHANCE = 6, BUILD_EVERY = 12, PLATFORM_EVERY = 8;
    private static final List<Siege> SIEGES = new ArrayList<>();
    private static final Map<Long, Long> RECENT = new HashMap<>();      // island -> game time of its last siege

    private SkyHaterSieges() {}

    private static final class Siege {
        final ServerLevel level;
        final BlockPos island, base;
        final Direction facing;                 // from the tower toward the island
        final int topY;
        final List<UUID> builders = new ArrayList<>();
        final java.util.Set<UUID> fought = new java.util.HashSet<>();        // players who hit a builder
        int y, age, climb = -1;
        boolean bridged;
        Siege(ServerLevel level, BlockPos island, BlockPos base, Direction facing, int topY) {
            this.level = level; this.island = island; this.base = base; this.facing = facing; this.topY = topY; this.y = base.getY();
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % CHECK_EVERY == 0 && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || level.random.nextInt(CHANCE) != 0) continue;
                SkylanderEntity sk = level.getEntitiesOfClass(SkylanderEntity.class, p.getBoundingBox().inflate(96, 200, 96),
                        s -> s.islandHome() != null && !s.guardian()).stream().findFirst().orElse(null);
                if (sk != null) start(level, sk, p);
            }
        }
        SIEGES.removeIf(SkyHaterSieges::tick);
    }

    /** Whoever hits a siege builder helped stop it. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (!(com.xenowarriors.collection.Collection.credit(e.getSource().getEntity()) instanceof ServerPlayer p)) return;   // (a bot's hits count for its owner)
        for (Siege s : SIEGES) if (s.builders.contains(e.getEntity().getUUID())) s.fought.add(p.getUUID());
    }

    /** Starts a siege on this Skylander's island (false if it had one recently or there's nowhere to build). */
    public static boolean start(ServerLevel level, SkylanderEntity sk, ServerPlayer near) {
        BlockPos island = sk.islandHome();
        long key = island.asLong(), now = level.getGameTime();
        if (RECENT.containsKey(key) && now - RECENT.get(key) < 24000 * 2) return false;
        for (Siege s : SIEGES) if (s.island.equals(island)) return false;
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
        int off = Math.round(sk.islandRadius() * 1.25F) + 4;
        BlockPos col = island.relative(facing.getOpposite(), off);
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col);
        if (ground.getY() >= island.getY() - 8 || !level.getFluidState(ground.below()).isEmpty()) return false;
        Siege s = new Siege(level, island, ground, facing, island.getY());
        int n = 3 + level.random.nextInt(2);
        for (int i = 0; i < n; i++) {
            HumanNpcEntity h = ModNpcs.HUMAN.get().create(level);
            if (h == null) continue;
            h.moveTo(ground.getX() + 0.5 + level.random.nextInt(5) - 2, ground.getY(), ground.getZ() + 0.5 + level.random.nextInt(5) - 2, level.random.nextFloat() * 360, 0);
            h.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null, null);
            h.makeRaider(false);
            h.setCustomName(Component.literal(i == 0 ? "Sky Hater Foreman" : "Sky Hater Builder").withStyle(ChatFormatting.RED));
            level.addFreshEntity(h);
            s.builders.add(h.getUUID());
        }
        SIEGES.add(s);
        RECENT.put(key, now);
        for (ServerPlayer p : level.players()) if (p.blockPosition().closerThan(ground, 160)) {
            p.sendSystemMessage(Component.literal("Sky Haters are building a tower up to a sky island! Stop them before they reach the Skylanders!")
                    .withStyle(ChatFormatting.RED));
            level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 8F, 1.3F);
        }
        return true;
    }

    private static List<HumanNpcEntity> builders(Siege s) {
        List<HumanNpcEntity> out = new ArrayList<>();
        for (UUID id : s.builders) if (s.level.getEntity(id) instanceof HumanNpcEntity h && h.isAlive()) out.add(h);
        return out;
    }

    /** One tick of a siege; true when it's over. */
    private static boolean tick(Siege s) {
        s.age++;
        List<HumanNpcEntity> alive = builders(s);
        if (alive.isEmpty() || s.age > 20 * 60 * 15) {
            if (alive.isEmpty()) for (ServerPlayer p : s.level.players()) if (p.blockPosition().closerThan(s.base, 160)) {
                p.displayClientMessage(Component.literal("The Sky Hater siege tower was stopped!").withStyle(ChatFormatting.GREEN), true);
                if (s.fought.contains(p.getUUID())) com.xenowarriors.faction.Reputation.add(p, com.xenowarriors.faction.Faction.SKY, 30);     // the Skylanders remember who helped
            }
            return true;
        }
        if (!s.level.isLoaded(s.base)) return false;
        BlockPos colAt = new BlockPos(s.base.getX(), 0, s.base.getZ());
        HumanNpcEntity lead = alive.get(0);
        if (s.y < s.topY) {
            // the foreman stands on top, the others gather at the bottom
            if (s.age % BUILD_EVERY == 0) {
                BlockPos p = colAt.atY(s.y);
                s.level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 3);
                BlockPos ladder = p.relative(s.facing.getOpposite());
                if (s.level.getBlockState(ladder).canBeReplaced())
                    s.level.setBlock(ladder, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, s.facing.getOpposite()), 3);
                if ((s.y - s.base.getY()) % PLATFORM_EVERY == PLATFORM_EVERY - 1) {
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        if (d == s.facing.getOpposite()) continue;
                        BlockPos q = p.relative(d);
                        if (s.level.getBlockState(q).canBeReplaced()) s.level.setBlock(q, Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
                    }
                }
                s.level.playSound(null, p, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1F, 0.9F + s.level.random.nextFloat() * 0.2F);
                s.y++;
                lead.teleportTo(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
                lead.setDeltaMovement(Vec3.ZERO);
                lead.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                lead.getNavigation().stop();
                lead.fallDistance = 0;
            } else {
                BlockPos top = colAt.atY(s.y);
                if (lead.distanceToSqr(Vec3.atBottomCenterOf(top)) > 2.5) lead.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                lead.fallDistance = 0;
            }
            return false;
        }
        if (!s.bridged) {
            // a plank bridge from the top of the tower across to the island
            BlockPos at = colAt.atY(s.topY - 1);
            for (int i = 1; i <= 24; i++) {
                BlockPos q = at.relative(s.facing, i);
                if (!s.level.getBlockState(q).canBeReplaced()) break;
                s.level.setBlock(q, Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
            s.bridged = true;
            s.climb = 0;
            s.level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.HOSTILE, 1.5F, 0.8F);
            for (ServerPlayer p : s.level.players()) if (p.blockPosition().closerThan(s.base, 160))
                p.sendSystemMessage(Component.literal("The Sky Haters' tower reached the island - they're climbing up!").withStyle(ChatFormatting.RED));
        }
        // everyone climbs the ladder (one after another) and storms the island
        s.climb++;
        for (int i = 0; i < alive.size(); i++) {
            HumanNpcEntity h = alive.get(i);
            int start = i * 20;
            if (h.getY() < s.topY - 0.5 && s.climb > start && h.blockPosition().closerThan(colAt.atY(h.getBlockY()), 6)) {
                BlockPos ladder = colAt.relative(s.facing.getOpposite());
                double ny = Math.min(s.topY + 0.1, h.getY() + 0.35);
                h.teleportTo(ladder.getX() + 0.5, ny, ladder.getZ() + 0.5);
                h.setDeltaMovement(Vec3.ZERO);
                h.fallDistance = 0;
                if (ny >= s.topY) h.teleportTo(colAt.getX() + 0.5, s.topY, colAt.getZ() + 0.5);
            } else if (h.getY() >= s.topY - 1 && h.getTarget() == null) {
                // across the bridge, after the Skylanders
                SkylanderEntity sk = s.level.getEntitiesOfClass(SkylanderEntity.class, new AABB(s.island).inflate(24, 10, 24), e -> !e.wingless())
                        .stream().findFirst().orElse(null);
                if (sk != null) {
                    if (sk.flying()) sk.setFlying(false);
                    h.setTarget(sk);
                    sk.defendAgainst(h);
                } else h.getNavigation().moveTo(s.island.getX() + 0.5, s.island.getY(), s.island.getZ() + 0.5, 1.0);
            } else if (h.getY() < s.topY - 1 && h.getTarget() == null && !h.blockPosition().closerThan(colAt.atY(h.getBlockY()), 3)) {
                h.getNavigation().moveTo(s.base.getX() + 0.5, s.base.getY(), s.base.getZ() + 0.5, 1.0);
            }
        }
        return s.age > 20 * 60 * 8 && builders(s).stream().noneMatch(h -> h.getY() < s.topY - 1);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        SIEGES.clear();
    }
}

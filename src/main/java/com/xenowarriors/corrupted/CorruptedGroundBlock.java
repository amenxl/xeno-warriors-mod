package com.xenowarriors.corrupted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Corrupted soil / stone. Keeps creeping outward while an open Corrupted Ore vein is close by (it stops once the ore is
 * mined), saps the strength of anyone standing on it, and can be purified with Karma (crouch + use with an empty hand).
 */
public class CorruptedGroundBlock extends Block {
    public final boolean stone;

    public CorruptedGroundBlock(Properties props, boolean stone) {
        super(props);
        this.stone = stone;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        BlockPos ore = CorruptionData.get(level).nearest(level, pos, Corruption.REACH);
        if (ore == null) return;
        BlockPos next = pos.offset(r.nextInt(3) - 1, r.nextInt(3) - 1, r.nextInt(3) - 1);
        if (next.distSqr(ore) <= Corruption.REACH * Corruption.REACH) Corruption.corrupt(level, next);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity e) {
        if (!level.isClientSide && e instanceof Player p && !p.isCreative() && p.tickCount % 40 == 0
                && !com.xenowarriors.sky.CorruptedArmorItem.wearingAny(p)) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, true));
        }
        super.stepOn(level, pos, state, e);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(10) == 0 && level.getBlockState(pos.above()).isAir())
            level.addParticle(r.nextBoolean() ? ParticleTypes.WITCH : ParticleTypes.SMOKE, pos.getX() + r.nextDouble(), pos.getY() + 1.05,
                    pos.getZ() + r.nextDouble(), 0, 0.02, 0);
    }
}

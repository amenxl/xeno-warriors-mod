package com.xenowarriors.corrupted;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Corrupted Ore. Buried in solid rock it's harmless, but once a cave or a tunnel opens onto it, the corruption
 * starts creeping out into the ground around it. Mining it out stops the spread.
 */
public class CorruptedOreBlock extends DropExperienceBlock {
    public CorruptedOreBlock(Properties props, IntProvider xp) {
        super(props.randomTicks(), xp);
    }

    public static boolean exposed(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.isAir() || !n.getFluidState().isEmpty() || !n.isCollisionShapeFullBlock(level, pos.relative(d))) return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        CorruptionData data = CorruptionData.get(level);
        if (!exposed(level, pos)) {
            data.remove(pos);
            return;
        }
        data.add(pos);
        for (int i = 0; i < 2; i++) Corruption.corrupt(level, pos.offset(r.nextInt(5) - 2, r.nextInt(5) - 2, r.nextInt(5) - 2));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel s) CorruptionData.get(s).remove(pos);
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(6) == 0) level.addParticle(ParticleTypes.WITCH, pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble(),
                pos.getZ() + r.nextDouble(), 0, 0.01, 0);
    }
}

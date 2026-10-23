package com.xenowarriors.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Karma Altar: put the Hekublade and Sky Crystals or Corrupted Shards on it to upgrade the blade's edge. */
public class KarmaAltarBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(3, 4, 3, 13, 12, 13),
            Block.box(1, 12, 1, 15, 16, 15));

    public KarmaAltarBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        player.openMenu(getMenuProvider(state, level, pos));
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inv, p) -> new KarmaAltarMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                Component.translatable("container.xenowarriors.karma_altar"));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.2 + r.nextDouble() * 0.6, pos.getY() + 1.05,
                pos.getZ() + 0.2 + r.nextDouble() * 0.6, 0, 0.02, 0);
        if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0, 0);
    }
}

package com.xenowarriors.gov;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Government healing pod: an upright white tank with a glowing green glass front, about 2.6 blocks tall and a little
 * wider than a block (one placeable block that fills two: the lower half draws the bottom of the pod, the upper half
 * the top). Right-click to get in: you (or a bot / NPC) stand inside with your eyes closed while green light and
 * bubbles heal you; when you're healed the glass doors swing open, you open your eyes and step out.
 * One at a time, and a short cooldown after each use (see {@link HealingPods}). Only a diamond (or better) pickaxe
 * breaks one loose so you can take it home.
 * Older placed pods (before the doors) load as closed and empty, so they keep working.
 */
public class HealingPodBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** The glass doors are swung open (someone is stepping out). */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    /** Someone is inside (the healing fluid glows). */
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");
    private static final VoxelShape BASE = Block.box(0, 0, 0, 16, 3, 16);
    private static final VoxelShape OUTLINE_LOWER = Block.box(-3, 0, -3, 19, 16, 19), OUTLINE_UPPER = Block.box(-3, 0, -3, 19, 26, 19);

    public HealingPodBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false).setValue(OCCUPIED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, HALF, OPEN, OCCUPIED); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        if (pos.getY() >= ctx.getLevel().getMaxBuildHeight() - 1 || !ctx.getLevel().getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            return other.is(this) && other.getValue(HALF) != half ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // breaking the top half breaks the pod (drops come from the bottom half)
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState b = level.getBlockState(below);
            if (b.is(this) && b.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.isCreative()) Block.dropResources(b, level, below, null, player, player.getMainHandItem());
                level.setBlock(below, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(b));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Taken apart with someone inside: they're let out. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!level.isClientSide && !now.is(this) && state.getValue(HALF) == DoubleBlockHalf.LOWER) HealingPods.broken((ServerLevel) level, pos);
        super.onRemove(state, level, pos, now, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }     // (the pod is drawn in two parts, the top one by the upper half)

    /** Doors and fluid on both halves (the upper half draws the top of the pod). */
    static void set(Level level, BlockPos base, boolean open, boolean occupied) {
        for (BlockPos p : new BlockPos[]{base, base.above()}) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof HealingPodBlock && (s.getValue(OPEN) != open || s.getValue(OCCUPIED) != occupied))
                level.setBlock(p, s.setValue(OPEN, open).setValue(OCCUPIED, occupied), 3);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? OUTLINE_LOWER : OUTLINE_UPPER;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? BASE : Shapes.empty();     // you can step in
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos base = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!level.isClientSide) {
            String why = HealingPods.start((ServerLevel) level, base, player, -1, null);
            if (why != null) player.displayClientMessage(net.minecraft.network.chat.Component.literal(why).withStyle(net.minecraft.ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A pod left "occupied" with no one in it (a server restart mid-heal) clears itself. */
    @Override
    public boolean isRandomlyTicking(BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.LOWER && (state.getValue(OCCUPIED) || state.getValue(OPEN)); }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!HealingPods.inUse(level, pos)) set(level, pos, false, false);
    }
}

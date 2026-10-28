package com.xenowarriors.gov;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Government facility vault: a steel door that only a Gov Keycard opens (the card is used up). Opening it pours out
 * the vault's loot - Government tech and riches - and sounds the alarm if staff are around.
 */
public class GovVaultBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private static final ResourceLocation LOOT = com.xenowarriors.XenoWarriors.id("chests/gov_vault");

    public GovVaultBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, OPEN); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide || !(p instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level.isClientSide);
        if (s.getValue(OPEN)) { sp.displayClientMessage(Component.literal("The vault's empty.").withStyle(ChatFormatting.GRAY), true); return InteractionResult.CONSUME; }
        ItemStack held = p.getItemInHand(hand);
        if (!held.is(GovItems.KEYCARD.get()) && !p.isCreative()) {
            sp.displayClientMessage(Component.literal("ACCESS DENIED - Gov Keycard required.").withStyle(ChatFormatting.RED), true);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1F, 0.6F);
            return InteractionResult.CONSUME;
        }
        if (!p.isCreative()) held.shrink(1);
        level.setBlock(pos, s.setValue(OPEN, true), 3);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.6F);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        ServerLevel sl = (ServerLevel) level;
        LootParams params = new LootParams.Builder(sl).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withParameter(LootContextParams.THIS_ENTITY, p).withLuck(p.getLuck()).create(LootContextParamSets.CHEST);
        Direction f = s.getValue(FACING);
        Vec3 out = Vec3.atCenterOf(pos).add(f.getStepX() * 0.8, 0, f.getStepZ() * 0.8);
        for (ItemStack it : sl.getServer().getLootData().getLootTable(LOOT).getRandomItems(params)) {
            ItemEntity e = new ItemEntity(sl, out.x, out.y, out.z, it);
            e.setDeltaMovement(f.getStepX() * 0.15 + (sl.random.nextDouble() - 0.5) * 0.1, 0.25, f.getStepZ() * 0.15 + (sl.random.nextDouble() - 0.5) * 0.1);
            sl.addFreshEntity(e);
        }
        sp.displayClientMessage(Component.literal("Vault opened!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        if (GovFacilities.staffNear(sl, pos, 24)) GovFacilities.alarm(sl, pos, sp, "The vault's been opened!");
        return InteractionResult.CONSUME;
    }
}

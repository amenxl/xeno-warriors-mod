package com.xenowarriors.corrupted;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.karma.Karma;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Turning ground corrupted, and purifying it again with Karma. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Corruption {
    /** How far from an open ore vein the corruption can creep. */
    public static final int REACH = 9;
    public static final float PURIFY_KARMA = 5F;

    private Corruption() {}

    /** Corrupts one block if it's ordinary ground or rock (plants on it wither away). */
    public static void corrupt(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return;
        BlockState s = level.getBlockState(p);
        BlockState to = null;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SOUL_SOIL)) to = CorruptionBlocks.SOIL.get().defaultBlockState();
        else if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.MOSSY_COBBLESTONE)) to = CorruptionBlocks.STONE.get().defaultBlockState();
        if (to == null) return;
        level.setBlock(p, to, 3);
        BlockState above = level.getBlockState(p.above());
        if (above.is(BlockTags.REPLACEABLE) || above.is(BlockTags.FLOWERS) || above.is(BlockTags.SAPLINGS)) {
            if (above.getFluidState().isEmpty()) level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        if (level.random.nextInt(3) == 0) level.sendParticles(ParticleTypes.WITCH, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.01);
    }

    private static boolean corrupted(BlockState s) {
        return s.getBlock() instanceof CorruptedGroundBlock;
    }

    /** Crouch + use with an empty hand on corrupted ground: Karma cleanses it and the ground around it. */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
        ServerLevel level = p.serverLevel();
        BlockPos center = event.getPos();
        if (!corrupted(level.getBlockState(center))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!p.isCreative() && !Karma.trySpend(p, PURIFY_KARMA)) return;
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (q.distSqr(center) > 6.5) continue;
            BlockState s = level.getBlockState(q);
            if (!(s.getBlock() instanceof CorruptedGroundBlock g)) continue;
            BlockState clean = g.stone ? (q.getY() < 0 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState()
                    : level.canSeeSky(q.above()) && level.getBlockState(q.above()).isAir() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            level.setBlock(q, clean, 3);
            level.sendParticles(ParticleTypes.END_ROD, q.getX() + 0.5, q.getY() + 1.0, q.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
            n++;
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5, 12, 1.2, 0.4, 1.2, 0);
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.4F);
        level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
        p.swing(InteractionHand.MAIN_HAND, true);
        if (CorruptionData.get(level).nearest(level, center, REACH) != null)
            p.displayClientMessage(Component.literal("Purified " + n + " blocks - but an open Corrupted Ore vein nearby keeps spreading it")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        else p.displayClientMessage(Component.literal("Purified " + n + " blocks").withStyle(ChatFormatting.AQUA), true);
    }
}

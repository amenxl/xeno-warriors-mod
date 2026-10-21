package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Chunky earth rocks built from real block textures: a main chunk with a smaller lump fused on. */
public final class RockRender {
    private static final BlockState[] MAIN = {
            Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
            Blocks.DIRT.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState()};
    private static final BlockState[] LUMP = {
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
            Blocks.ROOTED_DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState()};

    private RockRender() {}

    /**
     * Draws a rock centered at (x, y, z) relative to the current pose.
     * @param seed picks the materials and lump shape so each rock looks different
     * @param spin tumble angle in degrees
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Level level, double worldX, double worldY, double worldZ,
                            double x, double y, double z, float size, int seed, float spin) {
        if (size <= 0.01F) return;
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(worldX, worldY + 0.5, worldZ));
        var blocks = Minecraft.getInstance().getBlockRenderer();
        int h = mix(seed);
        BlockState main = MAIN[Math.floorMod(h, MAIN.length)];
        BlockState lump = LUMP[Math.floorMod(h >> 8, LUMP.length)];
        float rx = (h & 0xFF) / 255F * 360F, ry = ((h >> 16) & 0xFF) / 255F * 360F;

        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(ry + spin * 0.7F));
        pose.mulPose(Axis.XP.rotationDegrees(rx + spin));
        pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.4F));

        pose.pushPose();
        pose.scale(size, size * 0.82F, size * 0.92F);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(main, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        pose.pushPose();
        pose.translate(size * 0.28F, size * 0.22F, -size * 0.18F);
        pose.mulPose(Axis.ZP.rotationDegrees(35));
        pose.mulPose(Axis.XP.rotationDegrees(25));
        float l = size * 0.62F;
        pose.scale(l, l, l);
        pose.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(lump, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        pose.popPose();
    }

    public static int mix(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return h;
    }
}

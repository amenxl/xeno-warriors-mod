package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.entity.EarthWaveEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Earth Wave as the real ground blocks along its path heaving up into a crest at the front
 * and settling back down behind it. Only drawn - the world's blocks are never touched.
 */
public class EarthWaveRenderer extends EntityRenderer<EarthWaveEntity> {
    private static final int TRAIL = 6;

    public EarthWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(EarthWaveEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(EarthWaveEntity wave, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        float age = wave.tickCount + partialTick;
        if (age < EarthWaveEntity.IMPACT) return;
        Level level = wave.level();
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();

        float traveled = wave.traveled(partialTick);
        float fade = 1F;
        if (wave.stopTick() >= 0) fade = 1F - Mth.clamp((age - wave.stopTick()) / EarthWaveEntity.FADE, 0F, 1F);
        if (fade <= 0F) return;
        float progress = traveled / EarthWaveEntity.RANGE;
        float crest = (1.3F + 0.9F * progress) * fade;
        float half = EarthWaveEntity.halfWidth(traveled);

        double ex = Mth.lerp(partialTick, wave.xOld, wave.getX());
        double ey = Mth.lerp(partialTick, wave.yOld, wave.getY());
        double ez = Mth.lerp(partialTick, wave.zOld, wave.getZ());
        int refY = Mth.floor(ey) - 1;
        float dx = wave.dirX(), dz = wave.dirZ(), px = -dz, pz = dx;

        // work out how high each ground block rises (keep the highest if two samples land on one block)
        Map<Long, Float> heights = new HashMap<>();
        int lanes = Mth.ceil(half);
        for (int k = 0; k <= TRAIL; k++) {
            float s = Mth.floor(traveled) - k;
            if (s < 0.5F) break;
            float d = traveled - s;                                    // how far behind the crest
            float along = d < 0.9F ? d / 0.9F : (float) Math.exp(-(d - 0.9F) * 0.65F);
            for (int l = -lanes; l <= lanes; l++) {
                if (Math.abs(l) > half + 0.3F) continue;
                float lat = 1F - 0.4F * (l / half) * (l / half);
                double wx = wave.startX() + dx * s + px * l;
                double wz = wave.startZ() + dz * s + pz * l;
                int bx = Mth.floor(wx), bz = Mth.floor(wz);
                float jitter = 0.85F + 0.3F * hash(bx, bz);
                float h = crest * along * lat * jitter;
                if (h < 0.08F) continue;
                heights.merge(BlockPos.asLong(bx, 0, bz), h, Math::max);
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, Float> e : heights.entrySet()) {
            int bx = BlockPos.getX(e.getKey()), bz = BlockPos.getZ(e.getKey());
            float h = e.getValue();
            // find the ground block under this spot
            BlockState state = null;
            int by = 0;
            for (int y = refY + 2; y >= refY - 4; y--) {
                pos.set(bx, y, bz);
                if (EarthWaveEntity.isGround(level, pos)) { state = level.getBlockState(pos); by = y; break; }
            }
            if (state == null) continue;
            int light = LevelRenderer.getLightColor(level, pos.set(bx, by + 1 + (int) h, bz));
            float tilt = (hash(bz, bx) - 0.5F) * 24F;

            for (int layer = 0; layer < (h > 1F ? 2 : 1); layer++) {
                poseStack.pushPose();
                poseStack.translate(bx - ex + 0.5, by + h - layer - ey + 0.5, bz - ez + 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(-wave.getYRot()));
                poseStack.mulPose(Axis.XP.rotationDegrees(tilt * Math.min(1F, h)));
                poseStack.scale(0.98F, 0.98F, 0.98F);
                poseStack.translate(-0.5, -0.5, -0.5);
                blocks.renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    private static float hash(int a, int b) {
        int h = a * 73856093 ^ b * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    @Override
    public ResourceLocation getTextureLocation(EarthWaveEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}

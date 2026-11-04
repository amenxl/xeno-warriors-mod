package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.entity.HekubladeRainEntity;
import com.xenowarriors.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hekublade Rain: the ring of glowing Hekublades hanging point-down over the area, each dropping in its wave with a
 * pale streak, a faint ring marking where they'll land, and the giant blade of the finale. The blades are the real
 * Hekublade item model, drawn full-bright.
 */
public class HekubladeRainRenderer extends EntityRenderer<HekubladeRainEntity> {
    private final ItemStack blade = new ItemStack(ModItems.HEKUBLADE.get());

    public HekubladeRainRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(HekubladeRainEntity e, Frustum frustum, double x, double y, double z) { return true; }

    @Override
    public void render(HekubladeRainEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        int bright = LightTexture.FULL_BRIGHT;
        float appear = Mth.clamp(age / HekubladeRainEntity.RING_UP, 0F, 1F);
        float fade = age > HekubladeRainEntity.END - 10 ? Mth.clamp((HekubladeRainEntity.END - age) / 10F, 0F, 1F) : 1F;
        // where they'll land: a faint red-orange ring on the ground, and the ring of light the blades hang in
        if (age < HekubladeRainEntity.GIANT_LAND + 6) {
            pose.pushPose();
            pose.translate(0, 0.08, 0);
            FxDraw.disc(pose, FxDraw.glow(buffers), HekubladeRainEntity.RADIUS - 0.25F, HekubladeRainEntity.RADIUS + 0.15F, 1F, 0.35F, 0.15F, 0.5F * appear * fade, 0F);
            pose.translate(0, HekubladeRainEntity.RING_HEIGHT - 0.08 + 0.9, 0);
            FxDraw.ring(pose, FxDraw.glow(buffers), HekubladeRainEntity.RADIUS, 0.12F, 1F, 0.95F, 0.85F, 0.45F * appear * (age < HekubladeRainEntity.GIANT_START ? 1F : 0.3F));
            pose.popPose();
        }
        for (int i = 0; i < HekubladeRainEntity.BLADES; i++) {
            Vec3 p = e.bladePos(i, age);
            if (p == null) continue;
            boolean falling = age >= HekubladeRainEntity.dropTick(i);
            // each blade flashes in one after another around the ring
            float grow = Mth.clamp((age - i * 0.6F) / 5F, 0F, 1F);
            if (grow <= 0F) continue;
            Vector3f dir = new Vector3f(0, -1, 0);
            if (falling) {
                Vec3 to = e.landPos(i).subtract(e.ringPos(i, HekubladeRainEntity.dropTick(i)));
                dir = new Vector3f((float) to.x, (float) to.y, (float) to.z).normalize();
            }
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            drawBlade(pose, buffers, dir, 1.6F * grow, bright, e.getId() + i);
            float len = falling ? 3.2F : 0.8F;
            FxDraw.beam(pose, FxDraw.glow(buffers), -dir.x() * len, -dir.y() * len, -dir.z() * len, falling ? 0.16F : 0.07F, 1F, 0.92F, 0.85F, falling ? 0.6F : 0.3F);
            pose.popPose();
        }
        float gh = e.giantHeight(age);
        if (!Float.isNaN(gh)) {
            float grow = Mth.clamp((age - (HekubladeRainEntity.GIANT_START - 10)) / 8F, 0F, 1F);
            pose.pushPose();
            pose.translate(0, gh + 1.5, 0);
            drawBlade(pose, buffers, new Vector3f(0, -1, 0), 6F * grow, bright, e.getId() + 99);
            FxDraw.beam(pose, FxDraw.glow(buffers), 0, 5F, 0, 0.6F, 1F, 0.75F, 0.4F, 0.55F * grow);
            pose.popPose();
        }
        // the finale's flash on the ground
        if (age >= HekubladeRainEntity.GIANT_LAND && age < HekubladeRainEntity.GIANT_LAND + 12) {
            float t = (age - HekubladeRainEntity.GIANT_LAND) / 12F;
            pose.pushPose();
            pose.translate(0, 0.1, 0);
            FxDraw.disc(pose, FxDraw.glow(buffers), 0F, HekubladeRainEntity.GIANT_RADIUS * (0.4F + t), 1F, 0.8F, 0.5F, 0.8F * (1F - t), 0F);
            pose.popPose();
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** The Hekublade item model, its point along {@code dir}. */
    private void drawBlade(PoseStack pose, MultiBufferSource buffers, Vector3f dir, float scale, int light, int seed) {
        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        pose.mulPose(Axis.ZP.rotationDegrees(45));           // the model's blade runs diagonally; line it up with +Y
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(blade, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, Minecraft.getInstance().level, seed);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(HekubladeRainEntity e) { return FxDraw.WHITE; }
}

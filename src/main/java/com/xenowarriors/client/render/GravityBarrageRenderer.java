package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.entity.GravityBarrageEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import static com.xenowarriors.entity.GravityBarrageEntity.*;

/**
 * Gravity Barrage visuals: a gravity sphere around each target, earth rocks tearing out of the
 * ground and orbiting them, all firing in at once each volley, rocks buried in the ground that
 * glow and shake right before they burst.
 */
public class GravityBarrageRenderer extends EntityRenderer<GravityBarrageEntity> {
    private static final float ORBIT = 2.3F;

    public GravityBarrageRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(GravityBarrageEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(GravityBarrageEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        Level level = e.level();
        double ex = Mth.lerp(partialTick, e.xOld, e.getX()), ey = Mth.lerp(partialTick, e.yOld, e.getY()), ez = Mth.lerp(partialTick, e.zOld, e.getZ());
        VertexConsumer glow = FxDraw.glow(buffers);

        int[] ids = e.targetIds();
        for (int ti = 0; ti < ids.length; ti++) {
            Entity t = level.getEntity(ids[ti]);
            if (t == null || !t.isAlive()) continue;
            double tx = Mth.lerp(partialTick, t.xOld, t.getX()), ty = Mth.lerp(partialTick, t.yOld, t.getY()), tz = Mth.lerp(partialTick, t.zOld, t.getZ());
            float hgt = t.getBbHeight();
            double cy = ty + hgt / 2;

            // gravity sphere
            if (age >= DOMES && age <= DETONATE) {
                float in = Mth.clamp((age - DOMES) / 4F, 0F, 1F), out = Mth.clamp((DETONATE - age) / 4F, 0F, 1F);
                float pulse = 0F;
                int v = volley(age);
                if (v >= 0) {
                    float c = age - BARRAGE - v * VOLLEY_EVERY - IMPACT_IN_VOLLEY;
                    if (c >= 0) pulse = (float) Math.exp(-c * 0.6F);
                }
                float rad = (Math.max(t.getBbWidth(), hgt) * 0.6F + 0.45F) * (1.6F - 0.6F * in) * (1F + 0.18F * pulse);
                pose.pushPose();
                pose.translate(tx - ex, cy - ey, tz - ez);
                FxDraw.sphere(pose, glow, rad, 0.62F + 0.3F * pulse, 0.3F, 1F, (0.38F + 0.4F * pulse) * in * out, age, -0.9F);
                pose.mulPose(Axis.YP.rotationDegrees(age * 12 + ti * 40));
                pose.mulPose(Axis.XP.rotationDegrees(70));
                FxDraw.ring(pose, glow, rad * 1.1F, 0.08F, 1F, 0.8F, 1F, 0.7F * in * out);
                pose.popPose();
                // pressure ring on the ground
                pose.pushPose();
                pose.translate(tx - ex, ty - ey + 0.03, tz - ez);
                FxDraw.disc(pose, glow, ORBIT * 1.05F, ORBIT * 1.2F, 0.7F, 0.4F, 1F, 0.6F * in * out, 0.6F * in * out);
                FxDraw.disc(pose, glow, 0F, ORBIT * 1.05F, 0.2F, 0.05F, 0.35F, 0.35F * in * out, 0.05F * in * out);
                pose.popPose();
            }

            // orbiting rocks
            if (age >= FORM && age < BARRAGE + VOLLEYS * VOLLEY_EVERY) {
                for (int k = 0; k < ROCKS_PER_TARGET; k++) {
                    float ang = k * ((float) Math.PI) * 2F / ROCKS_PER_TARGET + age * 0.09F + ids[ti];
                    float yOff = ((k % 3) - 1) * 0.45F * hgt / 1.8F + Mth.sin(age * 0.15F + k) * 0.2F;
                    float radius = ORBIT, size = 0.7F + 0.15F * (k % 2);
                    double ry = cy + yOff;
                    boolean streak = false;
                    if (age < BARRAGE) {
                        float em = GravityBarrageEntity.smooth((age - FORM - k * 2F) / 12F);
                        if (em <= 0F) continue;
                        ry = Mth.lerp(em, ty - 0.4, cy + yOff);
                        radius = Mth.lerp(em, 1.5F, ORBIT);
                        size *= 0.35F + 0.65F * em;
                    } else {
                        int v = volley(age);
                        float c = age - BARRAGE - v * VOLLEY_EVERY;
                        if (c < 3) {
                            radius = Mth.lerp(c / 3F, ORBIT, ORBIT * 1.3F);           // wind up
                        } else if (c < IMPACT_IN_VOLLEY) {
                            float s = (c - 3) / (IMPACT_IN_VOLLEY - 3F);
                            radius = Mth.lerp(s * s, ORBIT * 1.3F, 0.15F);            // fire inward
                            ry = Mth.lerp(s * s, ry, cy);
                            streak = true;
                        } else {
                            size *= GravityBarrageEntity.smooth((c - IMPACT_IN_VOLLEY) / 5F);   // a new rock forms
                            if (v == VOLLEYS - 1) continue;
                        }
                    }
                    double rx = tx + Mth.cos(ang) * radius, rz = tz + Mth.sin(ang) * radius;
                    RockRender.draw(pose, buffers, level, rx, ry, rz, rx - ex, ry - ey, rz - ez, size, ids[ti] * 31 + k, age * 20F + k * 50);
                    if (streak) {
                        pose.pushPose();
                        pose.translate(rx - ex, ry - ey, rz - ez);
                        FxDraw.beam(pose, glow, Mth.cos(ang) * 1.8F, 0, Mth.sin(ang) * 1.8F, 0.18F, 0.85F, 0.6F, 1F, 0.6F);
                        pose.popPose();
                    }
                }
            }
        }

        // rocks buried in the ground, glowing and shaking right before they burst
        if (age < DETONATE) {
            float charge = Mth.clamp((age - (DETONATE - 10)) / 10F, 0F, 1F);
            for (double[] r : e.landed()) {
                int seed = (int) r[3];
                float shake = charge * 0.08F;
                double jx = Mth.sin(age * 3.1F + seed) * shake, jz = Mth.cos(age * 2.7F + seed) * shake;
                RockRender.draw(pose, buffers, level, r[0], r[1], r[2], r[0] - ex + jx, r[1] + 0.12 - ey, r[2] - ez + jz,
                        0.55F, seed, (seed % 360));
                pose.pushPose();
                pose.translate(r[0] - ex, r[1] - ey + 0.02, r[2] - ez);
                FxDraw.disc(pose, glow, 0F, 0.6F + 0.4F * charge, 0.75F, 0.35F + 0.4F * charge, 1F, 0.25F + 0.6F * charge, 0F);
                pose.popPose();
            }
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GravityBarrageEntity e) {
        return FxDraw.WHITE;
    }
}

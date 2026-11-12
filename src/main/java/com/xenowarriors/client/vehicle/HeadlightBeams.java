package com.xenowarriors.client.vehicle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.vehicle.Headlights;
import com.xenowarriors.vehicle.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** The headlights' beams: two soft cones of light reaching ahead of a vehicle when its lights are on (Headlights). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HeadlightBeams {
    private HeadlightBeams() {}

    /** (Tests) beams drawn last frame. */
    public static int drawn;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick();
        int n = 0;
        BufferBuilder bb = null;
        Matrix4f m = e.getPoseStack().last().pose();
        for (var ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof VehicleEntity v) || v.isInvisible() || v.position().distanceToSqr(cam) > 96 * 96 || !Headlights.on(v)) continue;
            if (bb == null) {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                RenderSystem.disableCull();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                bb = Tesselator.getInstance().getBuilder();
                bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            }
            float yaw = net.minecraft.util.Mth.rotLerp(pt, v.yRotO, v.getYRot()) * ((float) Math.PI / 180F);
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 front = Headlights.front(v, pt).subtract(cam);
            double spread = v.getBbWidth() * 0.3;
            for (int s = -1; s <= 1; s += 2) {
                if (v.getBbWidth() < 1.2F && s > 0) continue;                    // (a bike: one light)
                Vec3 o = v.getBbWidth() < 1.2F ? front : front.add(side.scale(s * spread));
                cone(bb, m, o, fwd.add(0, -0.12, 0).normalize(), side, 12F, 2.2F);
                n++;
            }
        }
        if (bb != null) {
            BufferUploader.drawWithShader(bb.end());
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        drawn = n;
    }

    /** A flat-ish cone (a fan of triangles), bright at the lamp and fading out. */
    private static void cone(BufferBuilder bb, Matrix4f m, Vec3 o, Vec3 dir, Vec3 side, float len, float width) {
        Vec3 up = side.cross(dir).normalize();
        Vec3 end = o.add(dir.scale(len));
        int seg = 8;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            Vec3 p0 = end.add(side.scale(Math.cos(a0) * width)).add(up.scale(Math.sin(a0) * width * 0.55));
            Vec3 p1 = end.add(side.scale(Math.cos(a1) * width)).add(up.scale(Math.sin(a1) * width * 0.55));
            bb.vertex(m, (float) o.x, (float) o.y, (float) o.z).color(1F, 0.96F, 0.78F, 0.32F).endVertex();
            bb.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(1F, 0.96F, 0.78F, 0F).endVertex();
            bb.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(1F, 0.96F, 0.78F, 0F).endVertex();
        }
    }
}

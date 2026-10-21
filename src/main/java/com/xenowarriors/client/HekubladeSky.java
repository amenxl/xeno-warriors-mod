package com.xenowarriors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The sky inside the Hekublade: a starry night of drifting blue clouds over an endless dark floor, with a glowing
 * violet line where they meet. Drawn as a skybox (mirrored side panels so the corners join up).
 */
public class HekubladeSky extends DimensionSpecialEffects {
    public static final ResourceLocation ID = XenoWarriors.id("hekublade");
    private static final ResourceLocation SIDE = XenoWarriors.id("textures/environment/hekublade_side.png");
    private static final ResourceLocation TOP = XenoWarriors.id("textures/environment/hekublade_top.png");
    private static final ResourceLocation BOTTOM = XenoWarriors.id("textures/environment/hekublade_bottom.png");
    private static final Vec3 FOG = new Vec3(10 / 255.0, 28 / 255.0, 60 / 255.0);

    public HekubladeSky() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return FOG;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack pose, double camX, double camY, double camZ,
                                Matrix4f projection) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, net.minecraft.client.renderer.LightTexture light,
                                     double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack pose, Camera camera, Matrix4f projection,
                             boolean isFoggy, Runnable setupFog) {
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        Tesselator t = Tesselator.getInstance();
        BufferBuilder b = t.getBuilder();
        float s = 100F;
        // slow drift so the clouds feel alive
        float drift = (ticks + partialTick) * 0.002F;

        RenderSystem.setShaderTexture(0, SIDE);
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90F * i + drift));
            Matrix4f m = pose.last().pose();
            float u0 = (i % 2 == 0) ? 0F : 1F, u1 = 1F - u0;
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            b.vertex(m, -s, -s, -s).uv(u0, 1F).endVertex();
            b.vertex(m, -s, s, -s).uv(u0, 0F).endVertex();
            b.vertex(m, s, s, -s).uv(u1, 0F).endVertex();
            b.vertex(m, s, -s, -s).uv(u1, 1F).endVertex();
            t.end();
            pose.popPose();
        }
        Matrix4f m = pose.last().pose();
        RenderSystem.setShaderTexture(0, TOP);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, -s, s, -s).uv(0F, 0F).endVertex();
        b.vertex(m, -s, s, s).uv(0F, 1F).endVertex();
        b.vertex(m, s, s, s).uv(1F, 1F).endVertex();
        b.vertex(m, s, s, -s).uv(1F, 0F).endVertex();
        t.end();
        RenderSystem.setShaderTexture(0, BOTTOM);
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(m, -s, -s, -s).uv(0F, 0F).endVertex();
        b.vertex(m, s, -s, -s).uv(1F, 0F).endVertex();
        b.vertex(m, s, -s, s).uv(1F, 1F).endVertex();
        b.vertex(m, -s, -s, s).uv(0F, 1F).endVertex();
        t.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }
}

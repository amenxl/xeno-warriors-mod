package com.xenowarriors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sky Ore picked up by Karma Sensing: outlined in sky blue, seen through the rock, while the sense lasts. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkyOreGlow {
    private static long[] positions = new long[0];
    private static long until;

    private SkyOreGlow() {}

    public static void set(long[] pos, int duration) {
        positions = pos;
        var level = Minecraft.getInstance().level;
        until = level == null ? 0 : level.getGameTime() + duration;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || positions.length == 0 || mc.level.getGameTime() > until) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderSystem.disableDepthTest();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        float pulse = 0.65F + 0.35F * (float) Math.sin(mc.level.getGameTime() * 0.2);
        for (long l : positions) {
            BlockPos p = BlockPos.of(l);
            LevelRenderer.renderLineBox(pose, vc, new AABB(p).move(-cam.x, -cam.y, -cam.z).inflate(0.002), 0.55F, 0.85F, 1F, pulse);
        }
        buffers.endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }
}

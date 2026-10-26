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
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ultima's Data Sense on screen: life signs outlined green, ore boxed by kind, and the crash screen. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DataSenseClient {
    private static long[] ores = new long[0];
    private static int[] kinds = new int[0];
    private static long until, errorUntil;
    /** Ore box colors: common, gold, redstone/lapis, diamond/emerald, Data Ore. */
    private static final float[][] COL = {{0.75F, 0.75F, 0.78F}, {1F, 0.82F, 0.2F}, {1F, 0.25F, 0.25F}, {0.3F, 1F, 1F}, {0.2F, 1F, 0.5F}};

    private DataSenseClient() {}

    public static void set(int[] ids, long[] o, int[] k, int duration, boolean error) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        if (error) { errorUntil = now + duration; ores = new long[0]; return; }
        for (int id : ids) KarmaSense.data(id, duration);
        ores = o; kinds = k; until = now + duration;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ores.length == 0 || mc.level.getGameTime() > until) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderSystem.disableDepthTest();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        float scan = (mc.level.getGameTime() % 20) / 20F;
        for (int i = 0; i < ores.length; i++) {
            float[] c = COL[Math.max(0, Math.min(4, kinds[i]))];
            float a = 0.55F + 0.45F * (float) Math.abs(Math.sin((scan + i * 0.07F) * Math.PI));
            LevelRenderer.renderLineBox(pose, vc, new AABB(BlockPos.of(ores[i])).move(-cam.x, -cam.y, -cam.z).deflate(0.06), c[0], c[1], c[2], a);
        }
        buffers.endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }

    /** "ERROR - OFFLINE": green static and a warning box for a few seconds after a crash. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() > errorUntil) return;
        var g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        long t = mc.level.getGameTime();
        var r = new java.util.Random(t * 31);
        for (int i = 0; i < 40; i++) {
            int y = r.nextInt(h), x = r.nextInt(w), len = 20 + r.nextInt(120);
            g.fill(x, y, Math.min(w, x + len), y + 1, 0x5533FF88);
        }
        if ((t / 4) % 2 == 0) {
            String s = "ERROR - DATA SENSE OFFLINE";
            int bw = mc.font.width(s) + 16;
            g.fill(w / 2 - bw / 2, h / 3 - 8, w / 2 + bw / 2, h / 3 + 12, 0xC0100000);
            g.renderOutline(w / 2 - bw / 2, h / 3 - 8, bw, 20, 0xFFFF3030);
            g.drawCenteredString(mc.font, s, w / 2, h / 3 - 2, 0xFFFF4040);
        }
    }
}

package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Kaioken you can't miss: a player in DragonMineZ's Kaioken glows red - a pulsing red layer over their whole body and
 * red embers rising off them (any character, or none).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KaiokenTint {
    private KaiokenTint() {}

    /** (Tests) players drawn red this frame. */
    public static int drawn;

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post e) {
        if (!(e.getEntity() instanceof AbstractClientPlayer p) || p.isInvisible() || !com.xenowarriors.client.compat.DmzForms.kaioken(p)) return;
        var renderer = e.getRenderer();
        var model = renderer.getModel();
        float pt = e.getPartialTick();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        pose.scale(-1F, -1F, 1F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0F, -1.501F, 0F);
        pose.translate(0F, 0.75F, 0F);                                    // (puffed out a little around the middle of the body)
        pose.scale(1.06F, 1.03F, 1.06F);
        pose.translate(0F, -0.75F, 0F);
        float pulse = 0.28F + 0.12F * Mth.sin((p.tickCount + pt) * 0.45F);
        var vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucentEmissive(p.getSkinTextureLocation()));
        model.renderToBuffer(pose, vc, 0xF000F0, LivingEntityRenderer.getOverlayCoords(p, 0F), 1F, 0.12F, 0.1F, pulse);
        pose.popPose();
        drawn++;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        var dust = new DustParticleOptions(new Vector3f(1F, 0.08F, 0.08F), 1.1F);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!com.xenowarriors.client.compat.DmzForms.kaioken(p)) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            var r = p.getRandom();
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 0.4 + r.nextDouble() * 0.3;
                mc.level.addParticle(dust, p.getX() + Math.cos(a) * rad, p.getY() + r.nextDouble() * 1.9, p.getZ() + Math.sin(a) * rad, 0, 0.08, 0);
            }
        }
    }
}

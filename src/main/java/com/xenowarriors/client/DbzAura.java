package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Dragon Ball style auras: a glowing flame shell wrapped around the body - flame tongues licking upward into
 * spikes over the head, a bright rim, energy streaks racing up through it - in the same colours as before
 * (each character's Karma colour, Mikiko's orange-red fire, an element's colour on mobs).
 * <ul>
 * <li>Powering up: the full roaring aura, swelling as the charge builds.</li>
 * <li>Karma focus: a calm, smaller, slower aura.</li>
 * <li>Element mobs: a low, steady aura.</li>
 * </ul>
 * Drawn as camera-facing layers (an outer colour layer, a white-hot inner layer behind the body, and a faint
 * layer over the front), additive so it glows.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DbzAura {
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/aura/dbz_aura.png");
    private static final int FRAMES = 8;
    private static final Map<Integer, long[]> MARKS = new HashMap<>();   // mob id -> {rgb, until game time}

    private DbzAura() {}

    public static void mark(int id, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MARKS.put(id, new long[]{rgb, mc.level.getGameTime() + 30});
    }

    /** The aura colour a character powers up with. */
    public static int color(CharacterType t) {
        return t == CharacterType.MIKIKO ? 0xFF5A1A : t.getColor();
    }

    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity e = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || e.isInvisible()) return;
        float partial = event.getPartialTick();
        float age = e.tickCount + partial;
        int rgb; float strength, size, speed;
        if (e instanceof AbstractClientPlayer p) {
            if (FirstPersonHead.ownFirstPerson(p)) return;
            CharacterType t = ClientCharacterData.get(p);
            if (t == CharacterType.NONE) return;
            float charge = ChargePose.weight(p), focus = KarmaFocusClient.weight(p);
            if (charge > 0.01F) {
                rgb = color(t);
                strength = charge;
                size = 0.85F + 0.15F * charge + 0.03F * Mth.sin(age * 0.9F);
                speed = 0.75F;
            } else if (focus > 0.01F) {
                rgb = color(t);
                strength = 0.6F * focus;
                size = 0.8F;
                speed = 0.35F;
            } else return;
        } else {
            long[] m = MARKS.get(e.getId());
            if (m == null) return;
            if (m[1] < mc.level.getGameTime()) { MARKS.remove(e.getId()); return; }
            rgb = (int) m[0];
            strength = 0.55F;
            size = 0.8F;
            speed = 0.4F;
        }
        draw(e, event.getPoseStack(), event.getMultiBufferSource(), age, rgb, strength, size, speed);
    }

    private static void draw(LivingEntity e, PoseStack pose, MultiBufferSource buffers, float age, int rgb, float strength,
                             float size, float speed) {
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        float h = e.getBbHeight() / 1.8F, w = Math.max(e.getBbWidth() / 0.6F, h);
        float height = 2.9F * h * size, width = 1.75F * w * size;
        float camYaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(TEX));   // additive glow
        int frame = (int) (age * speed) % FRAMES;
        pose.pushPose();
        pose.translate(0, -0.25F * h, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180F - camYaw));          // stands up, always facing the camera
        // outer flame, behind the body
        layer(vc, pose, -0.38F * w, width, height, frame, r * strength, g * strength, b * strength);
        // white-hot inner flame, smaller and a few frames ahead so the two layers churn against each other
        float wr = (0.55F + 0.45F * r) * strength * 0.8F, wg = (0.55F + 0.45F * g) * strength * 0.8F, wb = (0.55F + 0.45F * b) * strength * 0.8F;
        layer(vc, pose, -0.3F * w, width * 0.72F, height * 0.82F, (frame + 3) % FRAMES, wr, wg, wb);
        // a faint layer over the front, so they look wrapped in it
        float f = 0.28F * strength;
        layer(vc, pose, 0.42F * w, width * 0.96F, height * 0.97F, (frame + 5) % FRAMES, r * f, g * f, b * f);
        pose.popPose();
    }

    private static void layer(VertexConsumer vc, PoseStack pose, float z, float width, float height, int frame, float r, float g, float b) {
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose(); Matrix3f n = last.normal();
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES, x = width / 2;
        float[][] c = {{-x, 0, u0, 1}, {x, 0, u1, 1}, {x, height, u1, 0}, {-x, height, u0, 0}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[side == 0 ? i : 3 - i];
                vc.vertex(m, q[0], q[1], z).color(r, g, b, 1F).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(0xF000F0).normal(n, 0, 0, 1).endVertex();
            }
        }
    }
}

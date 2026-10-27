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
    private static final ResourceLocation TEX_A = XenoWarriors.id("textures/entity/aura/dbz_aura_a.png");
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
        float height = 3.3F * h * size, width = 2.5F * w * size;
        float camYaw = Minecraft.getInstance().gameRenderer.getMainCamera().getYRot();
        int frame = (int) (age * speed) % FRAMES;
        pose.pushPose();
        pose.translate(0, -0.3F * h, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180F - camYaw));          // stands up, always facing the camera
        // outer flame behind the body: see-through but solid colour, so it keeps its colour even against a bright sky
        VertexConsumer tr = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX_A));
        layer(tr, pose, -0.42F * w, width, height, frame, r, g, b, 0.8F * strength);
        // a faint veil of it over the front, so they look wrapped in it
        layer(tr, pose, 0.45F * w, width * 0.94F, height * 0.95F, (frame + 5) % FRAMES, r, g, b, 0.16F * strength);
        // glow (additive): the outer flame again, and a white-hot inner flame churning a few frames ahead
        VertexConsumer add = buffers.getBuffer(RenderType.eyes(TEX));
        float k = 0.55F * strength;
        layer(add, pose, -0.4F * w, width, height, frame, r * k, g * k, b * k, 1F);
        float wr = (0.5F + 0.5F * r) * strength * 0.75F, wg = (0.5F + 0.5F * g) * strength * 0.75F, wb = (0.5F + 0.5F * b) * strength * 0.75F;
        layer(add, pose, -0.34F * w, width * 0.7F, height * 0.8F, (frame + 3) % FRAMES, wr, wg, wb, 1F);
        pose.popPose();
    }

    private static void layer(VertexConsumer vc, PoseStack pose, float z, float width, float height, int frame, float r, float g, float b, float a) {
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose(); Matrix3f n = last.normal();
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES, x = width / 2;
        float[][] c = {{-x, 0, u0, 1}, {x, 0, u1, 1}, {x, height, u1, 0}, {-x, height, u0, 0}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[side == 0 ? i : 3 - i];
                vc.vertex(m, q[0], q[1], z).color(r, g, b, a).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(0xF000F0).normal(n, 0, 0, 1).endVertex();
            }
        }
    }
}

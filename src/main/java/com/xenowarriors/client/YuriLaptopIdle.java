package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Yuri's idle: when she stands still for a while she puts on her green glasses, calls up her holographic laptop
 * (a green glowing screen and keyboard floating in front of her) and types away. Moving puts it all away again.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class YuriLaptopIdle {
    private static final int AFTER = 100;
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/holo_laptop.png");
    private static final Map<Integer, int[]> STILL = new HashMap<>();

    private YuriLaptopIdle() {}

    private static boolean isYuri(Player p) {
        return p instanceof AbstractClientPlayer a && ClientCharacterData.get(a) == CharacterType.YURI;
    }

    private static boolean calm(AbstractClientPlayer p) {
        return Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && !Busy.busy(p)
                && !p.isCrouching() && !p.isPassenger() && !p.isUsingItem() && !FlightTracker.isFlying(p) && !p.isSleeping()
                && p.getMainHandItem().isEmpty() && p.hurtTime == 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STILL.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!isYuri(p)) { STILL.remove(p.getId()); continue; }
            int[] s = STILL.computeIfAbsent(p.getId(), k -> new int[1]);
            s[0] = calm(p) ? s[0] + 1 : 0;
            float t = s[0] - AFTER;
            if (t >= 18 && t < 24) sparkle(mc, p, 4);               // the laptop blinking into being
            else if (t > 24 && p.getRandom().nextInt(10) == 0) sparkle(mc, p, 1);
        }
    }

    private static void sparkle(Minecraft mc, AbstractClientPlayer p, int n) {
        var r = p.getRandom();
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = p.getX() - Mth.sin(yaw) * 0.6, fz = p.getZ() + Mth.cos(yaw) * 0.6;
        for (int i = 0; i < n; i++)
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.3F, 1F, 0.4F), 0.5F),
                    fx + (r.nextDouble() - 0.5) * 0.6, p.getY() + 0.9 + r.nextDouble() * 0.5, fz + (r.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
    }

    /** Ticks into the idle (smooth), or -1. */
    public static float time(Player p, float partial) {
        int[] s = STILL.get(p.getId());
        if (s == null || s[0] < AFTER) return -1F;
        return s[0] - AFTER + partial;
    }

    /** Her glasses go on a moment into the idle and stay on while she types. */
    public static boolean glasses(Player p) {
        return time(p, 0F) >= 9F;
    }

    private static float sm(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    private static void set(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!isYuri(p)) return;
        float t = time(p, Minecraft.getInstance().getFrameTime());
        if (t < 0F) return;
        // right hand up to her face: slides the glasses on
        float g = sm(t / 6F) * (1F - sm((t - 12F) / 5F));
        set(m.rightArm, -1.9F, -0.55F, 0.2F, g);
        if (g > 0.01F) LimbBend.extraElbows(p, 1.9F * g, 0F);
        // then typing on the hologram: both hands forward, fingers tapping
        float type = sm((t - 16F) / 6F);
        if (type > 0F) {
            float a = Mth.sin(age * 1.3F) * 0.06F, b = Mth.sin(age * 1.3F + 1.7F) * 0.06F;
            set(m.rightArm, -0.95F + a, -0.32F, 0.04F, type);
            set(m.leftArm, -0.95F + b, 0.32F, -0.04F, type);
            LimbBend.extraElbows(p, 0.55F * type, 0.55F * type);
            m.head.xRot = Mth.lerp(type, m.head.xRot, 0.32F + Mth.sin(age * 0.04F) * 0.03F);
            m.head.yRot = Mth.lerp(type, m.head.yRot, Mth.sin(age * 0.03F) * 0.12F);   // reading across the screen
        }
        m.hat.copyFrom(m.head);
    }

    /** Draws the floating green laptop (model space, after the player model). */
    public static void render(Player p, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
        if (!isYuri(p)) return;
        float t = time(p, partial);
        if (t < 18F) return;
        float s = sm((t - 18F) / 6F);
        float flicker = 0.85F + 0.15F * Mth.sin(t * 0.7F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));   // see-through, glowing
        pose.pushPose();
        pose.translate(0F, 8.2F / 16F, -10.0F / 16F);              // in front of her, at hand height
        pose.scale(s, s, s);
        // keyboard: a flat plate under her hands
        quad(vc, pose, -4.5F, 0F, -2.6F, 4.5F, 0F, 2.6F, true, 0F, 40F / 64F, 1F, 1F, flicker);
        // screen: rising from the back edge, tilted toward her
        pose.translate(0F, 0F, -2.8F / 16F);
        pose.mulPose(Axis.XP.rotationDegrees(-12F));
        quad(vc, pose, -5F, -6.6F, 0F, 5F, 0F, 0F, false, 0F, 0F, 1F, 40F / 64F, flicker);
        pose.popPose();
    }

    /** A double-sided quad in pixels. flat = lies in x/z (keyboard), else stands in x/y (screen). */
    private static void quad(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1,
                             boolean flat, float u0, float v0, float u1, float v1, float alpha) {
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose(); Matrix3f n = last.normal();
        float[][] c = flat
                ? new float[][]{{x0, y0, z0, u0, v0}, {x1, y0, z0, u1, v0}, {x1, y0, z1, u1, v1}, {x0, y0, z1, u0, v1}}
                : new float[][]{{x0, y0, z0, u0, v0}, {x1, y0, z0, u1, v0}, {x1, y1, z0, u1, v1}, {x0, y1, z0, u0, v1}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] q = c[side == 0 ? i : 3 - i];
                vc.vertex(m, q[0] / 16F, q[1] / 16F, q[2] / 16F).color(1F, 1F, 1F, alpha).uv(q[3], q[4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0F, flat ? -1F : 0F, flat ? 0F : 1F).endVertex();
            }
        }
    }
}

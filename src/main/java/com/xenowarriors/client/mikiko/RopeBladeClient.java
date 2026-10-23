package com.xenowarriors.client.mikiko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.LockOn;
import com.xenowarriors.client.Surroundings;
import com.xenowarriors.mikiko.Hekublade;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/** The Season 3 Hekublade's rope, client side: right-click throw, the lash, the idle spin and the running saw spin. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RopeBladeClient {
    private static final ResourceLocation ROPE = XenoWarriors.id("textures/entity/hekublade_rope.png");
    private static final int THROW_T = 8, LASH_T = 14;
    private static final Map<Integer, float[]> FX = new HashMap<>();      // id -> {type, start tick}
    private static final Map<Integer, float[]> SAW = new HashMap<>();     // id -> {weight, prev}

    private RopeBladeClient() {}

    public static boolean seasonThree(Player p) {
        int o = ClientCharacterData.mikikoOutfit(p);
        return o == ClientCharacterData.SEASON_3 || o == ClientCharacterData.SEASON_3_TIED;
    }

    public static boolean eligible(Player p) {
        return seasonThree(p) && Hekublade.isBlade(p.getMainHandItem());
    }

    public static void fx(int id, int type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(id) instanceof Player p) FX.put(id, new float[]{type, p.tickCount});
    }

    private static float fxTime(Player p, int type, float partial) {
        float[] f = FX.get(p.getId());
        if (f == null || f[0] != type) return -1F;
        float t = p.tickCount - f[1] + partial;
        return t <= (type == com.xenowarriors.mikiko.RopeBlade.FX_THROW ? THROW_T : LASH_T) ? t : -1F;
    }

    /** Use / right click with it: throw it like a boomerang at the lock-on target. */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || mc.player == null || !eligible(mc.player)) return;
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b && b.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && mc.level.getBlockState(b.getBlockPos()).hasBlockEntity()) return;     // (still open chests and such)
        event.setCanceled(true);
        event.setSwingHand(false);
        LivingEntity target = LockOn.target();
        ModNetwork.sendToServer(new com.xenowarriors.network.RopeThrowPacket(target != null ? target.getId() : -1));
    }

    private static boolean sawWanted(AbstractClientPlayer p) {
        return eligible(p) && p.isSprinting() && !p.isPassenger() && Surroundings.of(p) == Surroundings.ENEMIES;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            boolean on = sawWanted(p);
            float[] w = SAW.get(p.getId());
            if (w == null) { if (!on) continue; SAW.put(p.getId(), w = new float[2]); }
            w[1] = w[0];
            w[0] = Mth.clamp(w[0] + (on ? 0.2F : -0.2F), 0F, 1F);
        }
        SAW.entrySet().removeIf(e -> (e.getValue()[0] <= 0F && e.getValue()[1] <= 0F) || mc.level.getEntity(e.getKey()) == null);
        FX.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static float saw(Player p, float partial) {
        float[] w = SAW.get(p.getId());
        return w == null ? 0F : Mth.lerp(partial, w[1], w[0]);
    }

    /** Ticks into the idle string-spin, or -1. */
    private static float idleSpin(Player p, float partial) {
        float[] bi = MikikoAnimations.bladeIdle(p, partial);
        return bi != null && (int) bi[0] == MikikoAnimations.BI_SPIN ? bi[1] : -1F;
    }

    /** The blade is out on its rope (not in his grip) right now - the hand shouldn't draw it. */
    public static boolean handFree(LivingEntity e) {
        if (!(e instanceof Player p) || !seasonThree(p)) return false;
        float pt = Minecraft.getInstance().getFrameTime();
        return fxTime(p, com.xenowarriors.mikiko.RopeBlade.FX_LASH, pt) >= 0F || saw(p, pt) > 0.3F || idleSpin(p, pt) >= 0F;
    }

    private static float sm(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!seasonThree(p)) return;
        float pt = Minecraft.getInstance().getFrameTime();
        float th = fxTime(p, com.xenowarriors.mikiko.RopeBlade.FX_THROW, pt);
        if (th >= 0F) {                                                    // wound back over the shoulder, then flung
            float t = th / THROW_T, w = Mth.sin(t * (float) Math.PI);
            m.rightArm.xRot = Mth.lerp(Math.min(1F, w * 1.5F), m.rightArm.xRot, Mth.lerp(sm(t * 1.6F), -2.8F, -1.1F));
            m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.3F);
            m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.lerp(t, 0.35F, -0.3F));
            return;
        }
        float lt = fxTime(p, com.xenowarriors.mikiko.RopeBlade.FX_LASH, pt);
        if (lt >= 0F) {                                                    // arm snapped out, then yanked back as it reels in
            float t = lt / LASH_T, w = sm(t * 4F) * (1F - sm((t - 0.85F) / 0.15F));
            m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -1.55F + sm((t - 0.55F) / 0.3F) * 0.45F);
            m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.08F);
            m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0F);
            return;
        }
        float sw = sm(saw(p, pt));
        if (sw > 0F) {                                                     // arm out in front, wrist driving the spin
            float th2 = (p.tickCount + pt) * 1.4F;
            m.rightArm.xRot = Mth.lerp(sw, m.rightArm.xRot, -1.45F + Mth.sin(th2) * 0.07F);
            m.rightArm.yRot = Mth.lerp(sw, m.rightArm.yRot, -0.12F);
            m.rightArm.zRot = Mth.lerp(sw, m.rightArm.zRot, Mth.cos(th2) * 0.06F);
            return;
        }
        float it = idleSpin(p, pt);
        if (it >= 0F) {                                                    // twirling it round on the string for fun
            float in = sm(it / 8F) * (1F - sm((it - 112F) / 8F));
            float th3 = it * 0.55F;
            m.rightArm.xRot = Mth.lerp(in, m.rightArm.xRot, -1.0F + Mth.sin(th3) * 0.08F);
            m.rightArm.yRot = Mth.lerp(in, m.rightArm.yRot, -0.25F);
            m.rightArm.zRot = Mth.lerp(in, m.rightArm.zRot, Mth.cos(th3) * 0.08F);
            m.head.xRot = Mth.lerp(in, m.head.xRot, 0.2F);
            m.hat.copyFrom(m.head);
        }
    }

    // ------------------------------------------------------------------ drawing

    public static void render(PlayerModel<?> model, AbstractClientPlayer p, PoseStack pose, MultiBufferSource buffers, int light, float pt) {
        if (!seasonThree(p) || !Hekublade.isBlade(p.getMainHandItem())) return;
        float lt = fxTime(p, com.xenowarriors.mikiko.RopeBlade.FX_LASH, pt);
        float sw = saw(p, pt);
        float it = idleSpin(p, pt);
        if (lt < 0F && sw <= 0.3F && it < 0F) return;
        pose.pushPose();
        model.translateToHand(HumanoidArm.RIGHT, pose);
        Vector3f hand = new Vector3f(-1F / 16F, 10F / 16F, 0F);
        Vector3f bladeAt, dir;
        float spin = 0F;
        if (lt >= 0F) {
            float ext = Mth.sin(Mth.clamp(lt / LASH_T, 0F, 1F) * (float) Math.PI) * 9F;      // out ~9 blocks and back
            bladeAt = new Vector3f(hand).add(0, ext + 0.15F, 0);
            dir = new Vector3f(0, 1, 0);
            spin = lt * 60F;
        } else if (sw > 0.3F) {
            float th = (p.tickCount + pt) * 1.4F;
            Vector3f c = new Vector3f(hand).add(0, 0.95F, 0);                                  // a spinning saw out in front
            dir = new Vector3f(0, Mth.cos(th), Mth.sin(th));
            bladeAt = new Vector3f(c).add(new Vector3f(dir).mul(0.55F));
        } else {
            float th = it * 0.55F;
            dir = new Vector3f(0, Mth.cos(th), Mth.sin(th));
            bladeAt = new Vector3f(hand).add(new Vector3f(dir).mul(0.75F));
        }
        // the rope from his finger to the ring on the pommel
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ROPE));
        rope(pose, vc, hand, bladeAt, 0.022F, light, lt >= 0F ? 0.12F : 0.03F, p.tickCount + pt);
        // the blade itself, pommel at the rope's end, pointing on along it
        pose.pushPose();
        pose.translate(bladeAt.x, bladeAt.y, bladeAt.z);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir.normalize()));
        if (spin != 0F) pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(0, 0.62F, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(45F));                      // the model's blade runs diagonally
        HekubladeRenderer.current = p;
        Minecraft.getInstance().getItemRenderer().renderStatic(MikikoAnimations.bladeStack(), ItemDisplayContext.NONE, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, p.level(), p.getId());
        HekubladeRenderer.current = null;
        pose.popPose();
        pose.popPose();
    }

    /** A rope (square tube) from a to b, with a little sway in the middle. */
    private static void rope(PoseStack pose, VertexConsumer vc, Vector3f a, Vector3f b, float w, int light, float sway, float time) {
        int segs = 8;
        Vector3f prev = new Vector3f(a);
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f side = new Vector3f(d).cross(Math.abs(d.x) < 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 0, 1)).normalize();
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            Vector3f cur = new Vector3f(a).lerp(b, t).add(new Vector3f(side).mul(Mth.sin(t * (float) Math.PI) * sway * Mth.sin(time * 0.6F + t * 4F)));
            tube(pose, vc, prev, cur, w, light, (i - 1) / (float) segs, t);
            prev = cur;
        }
    }

    private static void tube(PoseStack pose, VertexConsumer vc, Vector3f a, Vector3f b, float w, int light, float v0, float v1) {
        Vector3f d = new Vector3f(b).sub(a);
        if (d.lengthSquared() < 1.0E-8F) return;
        d.normalize();
        Vector3f p1 = new Vector3f(d).cross(Math.abs(d.y) < 0.9F ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize().mul(w);
        Vector3f p2 = new Vector3f(d).cross(p1).normalize().mul(w);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vector3f[] off = {new Vector3f(p1).add(p2), new Vector3f(p1).sub(p2), new Vector3f(p1).negate().sub(p2), new Vector3f(p2).sub(p1)};
        for (int i = 0; i < 4; i++) {
            Vector3f o1 = off[i], o2 = off[(i + 1) % 4];
            Vector3f nrm = new Vector3f(o1).add(o2).normalize();
            quadV(vc, m, n, a, o1, v0, 0F, light, nrm);
            quadV(vc, m, n, b, o1, v1, 0F, light, nrm);
            quadV(vc, m, n, b, o2, v1, 1F, light, nrm);
            quadV(vc, m, n, a, o2, v0, 1F, light, nrm);
        }
    }

    private static void quadV(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f at, Vector3f off, float u, float v, int light, Vector3f nrm) {
        vc.vertex(m, at.x + off.x, at.y + off.y, at.z + off.z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nrm.x, nrm.y, nrm.z).endVertex();
    }
}

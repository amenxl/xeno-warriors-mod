package com.xenowarriors.client.forms;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.forms.FormGunItem;
import com.xenowarriors.forms.FormItems;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.Umbrella;
import com.xenowarriors.forms.UmbrellaItem;
import com.xenowarriors.forms.WiresItem;
import com.xenowarriors.network.WireFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Weapon forms, client side: the wire whip swing and the red binding wires (drawn as ribbons in the world), the
 * Heku-umbrella's open canopy over its holder (which the rain stops at), sparks off held Hekublade / wire forms, and
 * the poses - aiming a gun, swinging the wires, holding a bind, the umbrella held up.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FormsFx {
    public static final ResourceLocation CANOPY = XenoWarriors.id("item/heku_umbrella_canopy");
    public static final int WHIP_T = 8, RECOIL_T = 5;
    private static final Map<Integer, long[]> WHIP = com.xenowarriors.client.ClientReset.track(new HashMap<>());     // id -> {start, side}
    private static final Map<Integer, Long> RECOIL = com.xenowarriors.client.ClientReset.track(new HashMap<>());
    /** Players aiming a summoned gun down the sights (from the server). */
    private static final java.util.Set<Integer> AIMING = com.xenowarriors.client.ClientReset.trackAll(new java.util.HashSet<>());
    /** Rhalix id -> {target id, mash 0-100, start}. */
    private static final Map<Integer, long[]> BINDS = com.xenowarriors.client.ClientReset.track(new HashMap<>());
    /** Test counters. */
    public static int canopiesDrawn, rainColumnsBlocked;

    private FormsFx() {}

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void fx(WireFxPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (m.kind()) {
            case WireFxPacket.WHIP -> WHIP.put(m.entity(), new long[]{now(), m.extra()});
            case WireFxPacket.BIND -> {
                long[] b = BINDS.get(m.entity());
                BINDS.put(m.entity(), new long[]{m.target(), m.extra(), b != null && b[0] == m.target() ? b[2] : now()});
            }
            case WireFxPacket.UNBIND -> BINDS.remove(m.entity());
            case WireFxPacket.RECOIL -> { RECOIL.put(m.entity(), now()); GunClient.remoteShot(m.entity(), m.extra()); }
            case WireFxPacket.AIM -> { if (m.extra() == 1) AIMING.add(m.entity()); else AIMING.remove(m.entity()); }
            case WireFxPacket.HIT -> GunClient.hitMarker(m.extra() == 1);
            case WireFxPacket.SUMMON -> {
                if (mc.level.getEntity(m.entity()) instanceof Player p) {
                    Vec3 h = hand(p, 1F, true);
                    for (int i = 0; i < 20; i++)
                        mc.level.addParticle(ParticleTypes.REVERSE_PORTAL, h.x, h.y, h.z, (Math.random() - 0.5) * 0.2, Math.random() * 0.15, (Math.random() - 0.5) * 0.2);
                }
            }
            default -> {}
        }
    }

    /** The Rhalix holding this entity in a bind, or -1. */
    public static int boundBy(int entityId) {
        for (var e : BINDS.entrySet()) if (e.getValue()[0] == entityId) return e.getKey();
        return -1;
    }

    /** Mash progress (0-100) on the bind holding this entity. */
    public static int mashProgress(int entityId) {
        for (var e : BINDS.values()) if (e[0] == entityId) return (int) e[1];
        return 0;
    }

    public static boolean binding(int rhalixId) { return BINDS.containsKey(rhalixId); }

    public static long bindStart(int rhalixId) { long[] b = BINDS.get(rhalixId); return b == null ? 0 : b[2]; }

    // ------------------------------------------------------------------ geometry helpers

    private static float bodyYaw(LivingEntity e, float pt) { return Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD; }

    /** Roughly where a hand is (arms held out in front for the wire moves). */
    public static Vec3 hand(LivingEntity e, float pt, boolean right) {
        float y = bodyYaw(e, pt);
        Vec3 fwd = new Vec3(-Mth.sin(y), 0, Mth.cos(y)), r = new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
        Vec3 pos = e.getPosition(pt);
        return pos.add(0, e.getBbHeight() * 0.72, 0).add(r.scale(right ? 0.36 : -0.36)).add(fwd.scale(0.5));
    }

    /** Where the umbrella's canopy sits (centre of its rim). */
    public static Vec3 canopy(LivingEntity e, float pt) {
        float y = bodyYaw(e, pt);
        Vec3 fwd = new Vec3(-Mth.sin(y), 0, Mth.cos(y)), r = new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
        return e.getPosition(pt).add(0, Umbrella.HEIGHT * e.getBbHeight() / 1.8, 0).add(r.scale(0.3)).add(fwd.scale(0.22));
    }

    // ------------------------------------------------------------------ rain stops at umbrellas

    /**
     * The rain renderer asks how high the ground is in a column; under an open Heku-umbrella the canopy is the ground,
     * so no rain falls past it (LevelRendererRainMixin).
     */
    public static int rainTop(int x, int z, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return height;
        for (Player p : mc.level.players()) {
            if (!Umbrella.holding(p)) continue;
            Vec3 c = canopy(p, 1F);
            double dx = x + 0.5 - c.x, dz = z + 0.5 - c.z;
            if (dx * dx + dz * dz <= (Umbrella.RADIUS + 0.45) * (Umbrella.RADIUS + 0.45) && height < c.y + 0.4) {
                rainColumnsBlocked++;
                return Mth.ceil(c.y + 0.4);
            }
        }
        return height;
    }

    // ------------------------------------------------------------------ ticking sparks

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        long t = now();
        WHIP.values().removeIf(w -> t - w[0] > WHIP_T + 2);
        RECOIL.values().removeIf(r -> t - r > RECOIL_T + 2);
        BINDS.values().removeIf(b -> mc.level.getEntity((int) b[0]) == null);       // (the binder may be out of sight: the bound one still sees the prompt)
        for (Player p : mc.level.players()) {
            var held = p.getMainHandItem();
            if (!(held.getItem() instanceof FormItems.Summoned s) || (p.tickCount + p.getId()) % 5 != 0) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 h = hand(p, 1F, true).add(0, -0.25, 0);
            if (s.style() == Form.Style.HEKU) {
                mc.level.addParticle(p.getRandom().nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : new DustParticleOptions(new Vector3f(1F, 0.45F, 0.12F), 0.6F),
                        h.x + (Math.random() - 0.5) * 0.3, h.y + Math.random() * 0.3, h.z + (Math.random() - 0.5) * 0.3, 0, 0.01, 0);
            } else {
                mc.level.addParticle(new DustParticleOptions(held.getItem() instanceof WiresItem ? new Vector3f(0.85F, 0.86F, 0.9F) : new Vector3f(0.6F, 0.2F, 1F), 0.6F),
                        h.x + (Math.random() - 0.5) * 0.3, h.y + Math.random() * 0.3, h.z + (Math.random() - 0.5) * 0.3, 0, 0.01, 0);
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long t = now();
        // umbrellas
        BakedModel canopy = mc.getModelManager().getModel(CANOPY);
        for (Player p : mc.level.players()) {
            if (!Umbrella.holding(p) || p.isInvisible()) continue;
            Vec3 c = canopy(p, pt);
            pose.pushPose();
            pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            pose.mulPose(Axis.YP.rotation(-bodyYaw(p, pt)));
            if (Umbrella.gliding(p)) pose.mulPose(Axis.XP.rotationDegrees(-8F));        // tipped back a little into the air
            pose.translate(-0.5, 0, -0.5);
            int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, net.minecraft.core.BlockPos.containing(c));
            VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
            net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(42L);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
                for (var q : canopy.getQuads(null, d, rand)) vc.putBulkData(pose.last(), q, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY);
            for (var q : canopy.getQuads(null, null, rand)) vc.putBulkData(pose.last(), q, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            canopiesDrawn++;
        }
        buffers.endBatch(Sheets.cutoutBlockSheet());
        // wire whips
        for (var e : WHIP.entrySet()) {
            if (!(mc.level.getEntity(e.getKey()) instanceof LivingEntity r)) continue;
            float age = t - e.getValue()[0] + pt;
            if (age < 0 || age > WHIP_T) continue;
            drawWhip(pose, buffers, cam, r, age / WHIP_T, e.getValue()[1] == 1, pt);
        }
        // binds
        for (var e : BINDS.entrySet()) {
            if (!(mc.level.getEntity(e.getKey()) instanceof LivingEntity r) || !(mc.level.getEntity((int) e.getValue()[0]) instanceof LivingEntity tg)) continue;
            drawBind(pose, buffers, cam, r, tg, t + pt - e.getValue()[2], pt);
        }
        buffers.endBatch(RenderType.debugQuads());
        buffers.endBatch(RenderType.lightning());
    }

    private static Vec3 dir(float yawRad, float angle) {
        float a = yawRad + angle;
        return new Vec3(-Mth.sin(a), 0, Mth.cos(a));
    }

    /** The whip's lead arm through a swing (t = 0..1): {xRot, yRot} - raised up and back over the shoulder, then cracked
     *  down and across - and the torso twist that drives it. sgn = -1 mirrors it (a backhand from the other side). */
    private static float[] whipArm(float t, float sgn) {
        float wind = sm(t / 0.25F), lash = sm((t - 0.25F) / 0.33F);
        float rx = Mth.lerp(lash, Mth.lerp(wind, -1.35F, -2.75F), -0.9F);
        float ry = Mth.lerp(lash, Mth.lerp(wind, 0F, 0.6F * sgn), -0.95F * sgn);
        float tw = Mth.lerp(lash, Mth.lerp(wind, 0F, 0.45F * sgn), -0.42F * sgn);
        return new float[]{rx, ry, tw};
    }

    /** Where the lead hand is during a whip swing (follows {@link #whipArm}). */
    private static Vec3 whipHand(LivingEntity e, float pt, float t, boolean right, float sgn) {
        float[] a = whipArm(t, sgn);
        float yaw = bodyYaw(e, pt) - a[2] * 0.6F;                         // (model yaw is the other way round)
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 shoulder = e.getPosition(pt).add(0, e.getBbHeight() * 0.76, 0).add(side.scale(right ? 0.33 : -0.33));
        double up = -Math.cos(a[0]), out = -Math.sin(a[0]);                 // arm down = (0,-1); xRot -pi/2 = straight ahead
        Vec3 dir = fwd.scale(out * Math.cos(-a[1])).add(side.scale(out * Math.sin(-a[1]))).add(0, up, 0);
        return shoulder.add(dir.scale(0.68));
    }

    private static void drawWhip(PoseStack pose, MultiBufferSource buffers, Vec3 cam, LivingEntity r, float t, boolean reverse, float pt) {
        float yaw = bodyYaw(r, pt);
        float sgn = reverse ? -1F : 1F;
        float lash = sm((t - 0.22F) / 0.36F);
        float sweep = Mth.lerp(lash, -1.55F, 1.4F) * sgn;                   // where the wires point, across the swing
        float reach = 0.9F + 3.3F * sm(t / 0.42F) - 1.6F * sm((t - 0.72F) / 0.28F);   // fly out, crack, reel back in
        float fade = 1F - sm((t - 0.8F) / 0.2F);
        float bend = (1F - lash) * 1.3F + 0.35F;                            // the tips trail behind the hand (more early on)
        for (int w = 0; w < 3; w++) {
            boolean right = (w != 1) != reverse;
            Vec3 h = w == 1 ? hand(r, pt, right) : whipHand(r, pt, t, right, sgn);
            float lag = w * 0.16F;                                          // each strand a little behind the last
            float sw = sweep - lag * sgn;
            int n = 22;
            Vec3[] pts = new Vec3[n + 1];
            Vec3 cur = h;
            pts[0] = h;
            float seg = reach * (1F - w * 0.12F) / n;
            for (int i = 1; i <= n; i++) {
                float s = i / (float) n;
                float a = sw - bend * s * s * sgn;                           // curls back towards the tip
                double rise = Math.sin(s * Math.PI * 3 - t * 26F + w) * 0.09 * s // a wave running down the wire
                        + (0.5 - s) * 0.06 - s * s * 0.25 * (1F - lash * 0.6F);  // ...and a little sag
                cur = cur.add(dir(yaw, a).scale(seg)).add(0, rise * seg * 2.5, 0);
                pts[i] = cur;
            }
            ribbon(pose, buffers, cam, pts, 0.07F, 0.82F, 0.83F, 0.86F, 0.95F * fade, 0.2F, 0.75F, 0.78F, 0.85F, 0.28F * fade, 0.35F);   // grayish-white steel, a faint sheen
            if (w == 0 && t > 0.5F && t < 0.56F && r.level().random.nextInt(2) == 0) {   // the crack at the tip
                Vec3 tip = pts[n];
                for (int k = 0; k < 4; k++)
                    r.level().addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z,
                            (Math.random() - 0.5) * 0.3, Math.random() * 0.2, (Math.random() - 0.5) * 0.3);
            }
        }
    }

    private static void drawBind(PoseStack pose, MultiBufferSource buffers, Vec3 cam, LivingEntity r, LivingEntity tg, float age, float pt) {
        float pulse = 0.75F + 0.25F * Mth.sin(age * 0.45F);
        float in = Mth.clamp(age / 5F, 0F, 1F);
        Vec3 c = tg.getPosition(pt);
        float h = tg.getBbHeight(), rad = tg.getBbWidth() * 0.55F + 0.06F;
        // lines from his hands to the target
        for (int w = 0; w < 3; w++) {
            Vec3 a = hand(r, pt, w != 1).add(0, w == 2 ? -0.15 : 0, 0);
            Vec3 b = c.add(0, h * (0.35 + 0.2 * w), 0);
            b = a.lerp(b, in);
            Vec3[] pts = new Vec3[13];
            for (int i = 0; i <= 12; i++) {
                float s = i / 12F;
                double sag = Math.sin(s * Math.PI) * (0.12 + 0.05 * Math.sin(age * 0.3 + w));
                pts[i] = a.lerp(b, s).add(0, -sag, 0);
            }
            ribbon(pose, buffers, cam, pts, 0.035F, 0.85F, 0.05F, 0.05F, 1F, 0.14F, 1F, 0.1F, 0.1F, 0.5F * pulse);
        }
        if (in < 1F) return;
        // coiled round them
        int n = 56;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n, a = s * (float) Math.PI * 2 * 3.2F + age * 0.05F;
            pts[i] = c.add(Math.cos(a) * rad, h * (0.22 + 0.6 * s), Math.sin(a) * rad);
        }
        ribbon(pose, buffers, cam, pts, 0.04F, 0.9F, 0.06F, 0.06F, 1F, 0.16F, 1F, 0.12F, 0.12F, 0.55F * pulse);
    }

    /** A camera-facing strip through the points: a solid core and a wider additive glow. */
    private static void ribbon(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3[] pts, float w, float r, float g, float b, float a,
                               float gw, float gr, float gg, float gb, float ga) {
        ribbon(pose, buffers, cam, pts, w, r, g, b, a, gw, gr, gg, gb, ga, 1F);
    }

    /** As above, narrowing to {@code taper} of its width at the end. */
    private static void ribbon(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3[] pts, float w, float r, float g, float b, float a,
                               float gw, float gr, float gg, float gb, float ga, float taper) {
        if (a <= 0.01F) return;
        Matrix4f m = pose.last().pose();
        VertexConsumer core = buffers.getBuffer(RenderType.debugQuads());
        strip(core, m, cam, pts, w, r, g, b, a, taper);
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        strip(glow, m, cam, pts, gw, gr, gg, gb, ga, taper);
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3[] pts, float w, float r, float g, float b, float a, float taper) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1];
            Vec3 d = p1.subtract(p0);
            Vec3 side = d.cross(p0.subtract(cam));
            if (side.lengthSqr() < 1e-8) continue;
            side = side.normalize().scale(w / 2 * Mth.lerp(i / (float) (pts.length - 1), 1F, taper));
            float ax = (float) (p0.x - cam.x), ay = (float) (p0.y - cam.y), az = (float) (p0.z - cam.z);
            float bx = (float) (p1.x - cam.x), by = (float) (p1.y - cam.y), bz = (float) (p1.z - cam.z);
            float sx = (float) side.x, sy = (float) side.y, sz = (float) side.z;
            vc.vertex(m, ax - sx, ay - sy, az - sz).color(r, g, b, a).endVertex();
            vc.vertex(m, ax + sx, ay + sy, az + sz).color(r, g, b, a).endVertex();
            vc.vertex(m, bx + sx, by + sy, bz + sz).color(r, g, b, a).endVertex();
            vc.vertex(m, bx - sx, by - sy, bz - sz).color(r, g, b, a).endVertex();
        }
    }

    // ------------------------------------------------------------------ poses

    private static float sm(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    public static void pose(HumanoidModel<?> m, Player p, float age) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getFrameTime();
        long t = now();
        var held = p.getMainHandItem().getItem();
        long[] bind = BINDS.get(p.getId());
        if (bind != null) {                                                     // both arms out, straining on the wires
            float tr = Mth.sin(age * 1.7F) * 0.04F;
            m.rightArm.xRot = -1.45F + m.head.xRot * 0.5F + tr; m.rightArm.yRot = -0.15F + m.head.yRot; m.rightArm.zRot = 0F;
            m.leftArm.xRot = -1.45F + m.head.xRot * 0.5F - tr; m.leftArm.yRot = 0.15F + m.head.yRot; m.leftArm.zRot = 0F;
            m.body.xRot = -0.08F;
            return;
        }
        int bound = boundBy(p.getId());
        if (bound >= 0) {                                                       // wrapped up: arms pinned, struggling
            float s = Mth.sin(age * 2.1F) * 0.08F;
            m.rightArm.xRot = s; m.rightArm.yRot = 0; m.rightArm.zRot = 0.05F;
            m.leftArm.xRot = -s; m.leftArm.yRot = 0; m.leftArm.zRot = -0.05F;
            m.body.yRot = s * 0.6F;
            return;
        }
        long[] w = WHIP.get(p.getId());
        if (w != null && held instanceof WiresItem) {
            float tt = Mth.clamp((t - w[0] + pt) / WHIP_T, 0F, 1F);
            float fade = 1F - sm((tt - 0.8F) / 0.2F);
            boolean rev = w[1] == 1;
            float sgn = rev ? -1F : 1F;
            float[] a = whipArm(tt, sgn);
            var lead = rev ? m.leftArm : m.rightArm;
            var off = rev ? m.rightArm : m.leftArm;
            lead.xRot = Mth.lerp(fade, lead.xRot, a[0]);
            lead.yRot = Mth.lerp(fade, lead.yRot, a[1]);
            lead.zRot = Mth.lerp(fade, lead.zRot, 0F);
            off.xRot = Mth.lerp(fade, off.xRot, -0.75F + a[0] * 0.15F);         // the other hand out for balance, feeding wire
            off.zRot = Mth.lerp(fade, off.zRot, 0.45F * (rev ? 1 : -1));
            m.body.yRot = Mth.lerp(fade, m.body.yRot, a[2]);
            // the shoulders go round with the torso
            m.rightArm.z = Mth.sin(m.body.yRot) * 5F; m.rightArm.x = -Mth.cos(m.body.yRot) * 5F;
            m.leftArm.z = -Mth.sin(m.body.yRot) * 5F; m.leftArm.x = Mth.cos(m.body.yRot) * 5F;
            return;
        }
        if (held instanceof UmbrellaItem) {                                     // holding it up over his head
            m.rightArm.xRot = -2.75F + Mth.sin(age * 0.08F) * 0.03F; m.rightArm.yRot = 0F; m.rightArm.zRot = 0.04F;
            return;
        }
        if (held instanceof FormGunItem g) {                                    // hip: gun held low at the chest; aimed: raised to the eye
            Long rc = RECOIL.get(p.getId());
            float kick = rc == null ? 0F : (1F - Mth.clamp((t - rc + pt) / RECOIL_T, 0F, 1F)) * 0.35F;
            float aim = aimPose(p, pt);
            float half = (float) Math.PI / 2F;
            m.rightArm.xRot = Mth.lerp(aim, -half * 0.62F + m.head.xRot * 0.5F, -half + m.head.xRot) - kick;
            m.rightArm.yRot = Mth.lerp(aim, -0.12F + m.head.yRot * 0.6F, m.head.yRot - 0.06F);
            m.rightArm.zRot = 0F;
            if (g.form() != Form.PISTOL) {                                       // long guns: the other hand under the barrel
                m.leftArm.xRot = Mth.lerp(aim, -half * 0.55F + m.head.xRot * 0.5F, -half + m.head.xRot) - kick * 0.6F;
                m.leftArm.yRot = Mth.lerp(aim, 0.6F + m.head.yRot * 0.6F, m.head.yRot + 0.55F);
                m.leftArm.zRot = 0F;
            } else if (aim > 0F) {                                               // pistol aimed: both hands on it
                m.leftArm.xRot = Mth.lerp(aim, m.leftArm.xRot, -half + m.head.xRot);
                m.leftArm.yRot = Mth.lerp(aim, m.leftArm.yRot, m.head.yRot + 0.45F);
                m.leftArm.zRot = 0F;
            }
        }
    }

    /** 0 = hip, 1 = aiming down the sights (the local player blends; others go by what the server says). */
    public static float aimPose(Player p, float pt) {
        if (p == Minecraft.getInstance().player) return GunClient.aimProgress(pt);
        return AIMING.contains(p.getId()) ? 1F : 0F;
    }

    public static boolean remoteAiming(int id) { return AIMING.contains(id); }

    // ------------------------------------------------------------------ the canopy model

    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Models {
        private Models() {}

        @SubscribeEvent
        public static void register(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
            event.register(CANOPY);
        }
    }

    /** For tests: the entity a gun's recoil or a whip is showing on. */
    public static boolean whipping(Entity e) { return WHIP.containsKey(e.getId()); }
}

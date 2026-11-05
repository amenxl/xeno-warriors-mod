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
            case WireFxPacket.RECOIL -> RECOIL.put(m.entity(), now());
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
        BINDS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Player p : mc.level.players()) {
            var held = p.getMainHandItem();
            if (!(held.getItem() instanceof FormItems.Summoned s) || (p.tickCount + p.getId()) % 5 != 0) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 h = hand(p, 1F, true).add(0, -0.25, 0);
            if (s.style() == Form.Style.HEKU) {
                mc.level.addParticle(p.getRandom().nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : new DustParticleOptions(new Vector3f(1F, 0.45F, 0.12F), 0.6F),
                        h.x + (Math.random() - 0.5) * 0.3, h.y + Math.random() * 0.3, h.z + (Math.random() - 0.5) * 0.3, 0, 0.01, 0);
            } else {
                mc.level.addParticle(new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1F), 0.6F),
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

    private static void drawWhip(PoseStack pose, MultiBufferSource buffers, Vec3 cam, LivingEntity r, float t, boolean reverse, float pt) {
        float yaw = bodyYaw(r, pt);
        float e = t < 0.5F ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
        float sweep = Mth.lerp(e, -1.35F, 1.35F) * (reverse ? -1 : 1);
        float out = Mth.sin(Math.min(1F, t * 1.4F) * (float) Math.PI * 0.5F + 0.3F);
        float fade = 1F - Mth.clamp((t - 0.75F) / 0.25F, 0F, 1F);
        for (int w = 0; w < 2; w++) {
            boolean right = (w == 0) != reverse;
            Vec3 h = hand(r, pt, right);
            float sw = sweep - (w == 1 ? 0.35F : 0F) * (reverse ? -1 : 1);
            Vec3 tip = h.add(dir(yaw, sw).scale(4.0 * out)).add(0, -0.25 - w * 0.2, 0);
            Vec3 ctrl = h.add(dir(yaw, sw - 0.6F * (reverse ? -1 : 1)).scale(2.2)).add(0, 0.25, 0);
            Vec3[] pts = new Vec3[17];
            for (int i = 0; i <= 16; i++) {
                float s = i / 16F;
                pts[i] = h.scale((1 - s) * (1 - s)).add(ctrl.scale(2 * (1 - s) * s)).add(tip.scale(s * s));
            }
            ribbon(pose, buffers, cam, pts, 0.05F, 0.18F, 0.05F, 0.3F, 0.95F * fade, 0.18F, 0.55F, 0.2F, 1F, 0.45F * fade);
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
        if (a <= 0.01F) return;
        Matrix4f m = pose.last().pose();
        VertexConsumer core = buffers.getBuffer(RenderType.debugQuads());
        strip(core, m, cam, pts, w, r, g, b, a);
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        strip(glow, m, cam, pts, gw, gr, gg, gb, ga);
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3[] pts, float w, float r, float g, float b, float a) {
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 p0 = pts[i], p1 = pts[i + 1];
            Vec3 d = p1.subtract(p0);
            Vec3 side = d.cross(p0.subtract(cam));
            if (side.lengthSqr() < 1e-8) continue;
            side = side.normalize().scale(w / 2);
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
            float tt = Mth.clamp((t - w[0] + pt) / WHIP_T, 0F, 1F), e = sm(tt * 1.3F);
            float fade = 1F - sm((tt - 0.8F) / 0.2F);
            boolean rev = w[1] == 1;
            float sweep = Mth.lerp(e, 0.95F, -0.95F) * (rev ? -1 : 1);
            m.rightArm.xRot = Mth.lerp(fade, m.rightArm.xRot, -1.35F);
            m.rightArm.yRot = Mth.lerp(fade, m.rightArm.yRot, sweep);
            m.leftArm.xRot = Mth.lerp(fade, m.leftArm.xRot, -1.1F);
            m.leftArm.yRot = Mth.lerp(fade, m.leftArm.yRot, sweep * 0.7F);
            m.body.yRot = Mth.lerp(fade, m.body.yRot, -sweep * 0.35F);
            return;
        }
        if (held instanceof UmbrellaItem) {                                     // holding it up over his head
            m.rightArm.xRot = -2.75F + Mth.sin(age * 0.08F) * 0.03F; m.rightArm.yRot = 0F; m.rightArm.zRot = 0.04F;
            return;
        }
        if (held instanceof FormGunItem g) {                                    // aiming down the sights
            Long rc = RECOIL.get(p.getId());
            float kick = rc == null ? 0F : (1F - Mth.clamp((t - rc + pt) / RECOIL_T, 0F, 1F)) * 0.35F;
            m.rightArm.xRot = -(float) Math.PI / 2F + m.head.xRot - kick;
            m.rightArm.yRot = m.head.yRot - 0.06F;
            m.rightArm.zRot = 0F;
            if (g.form() != Form.PISTOL) {                                       // long guns: the other hand under the barrel
                m.leftArm.xRot = -(float) Math.PI / 2F + m.head.xRot - kick * 0.6F;
                m.leftArm.yRot = m.head.yRot + 0.55F;
                m.leftArm.zRot = 0F;
            }
        }
    }

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

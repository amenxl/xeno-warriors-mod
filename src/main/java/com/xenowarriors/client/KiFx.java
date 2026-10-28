package com.xenowarriors.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.XenoFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Xeno Blast drawn the way DragonMine Z draws ki attacks: solid, glowing 3D shapes in layers - a white-hot core,
 * a coloured body and a soft outer glow - that pulse and shimmer, instead of clouds of particles.
 * Mikiko: white / yellow-orange / red. Rhalix: a black core in a purple shell with a violet glow.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KiFx {
    public static final int CHARGE = 0, BEAM = 1, ORB = 2, BURST = 3, CLASH = 4, SHOT = 5, STOP = 6;

    private static final class Fx {
        final XenoFxPacket m; final long start; float size; int other; boolean otherRhalix; boolean dead;
        Fx(XenoFxPacket m, long start) { this.m = m; this.start = start; size = m.size(); other = m.other(); }
    }
    private static final List<Fx> LIVE = new ArrayList<>();

    private KiFx() {}

    public static void receive(XenoFxPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        switch (m.kind()) {
            case STOP -> LIVE.removeIf(f -> f.m.caster() == m.caster());
            case BURST -> {          // ends the orb it came from
                LIVE.removeIf(f -> f.m.caster() == m.caster() && (f.m.kind() == ORB || f.m.kind() == SHOT));
                LIVE.add(new Fx(m, now));
            }
            case CLASH -> {          // start, or an update of where the blasts meet
                for (Fx f : LIVE) if (f.m.kind() == CLASH && f.m.caster() == m.caster()) {
                    f.size = m.size();
                    if (m.life() <= 0) f.dead = true;
                    return;
                }
                if (m.life() > 0) LIVE.add(new Fx(m, now));
            }
            default -> LIVE.add(new Fx(m, now));
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        long now = mc.level.getGameTime();
        LIVE.removeIf(f -> f.dead || now - f.start > f.m.life() + 1 || now < f.start - 20);
    }

    // ------------------------------------------------------------------ where things are

    private static Vec3 look(Entity e, float pt) { return Vec3.directionFromRotation(e.getViewXRot(pt), e.getViewYRot(pt)); }
    private static Vec3 flatLook(Entity e, float pt) { return Vec3.directionFromRotation(0, e.getViewYRot(pt)); }
    private static Vec3 right(Entity e, float pt) { return Vec3.directionFromRotation(0, e.getViewYRot(pt) + 90F); }

    /** Where the energy gathers while charging (matches the server's spot and the poses). */
    private static Vec3 chargeSpot(Entity e, boolean rhalix, float pt) {
        Vec3 base = e.getPosition(pt);
        if (rhalix) return base.add(0, e.getEyeHeight() - 0.3, 0).add(look(e, pt).scale(1.3)).add(right(e, pt).scale(0.25));
        return base.add(0, 0.95, 0).add(right(e, pt).scale(-0.45)).add(flatLook(e, pt).scale(-0.35));
    }

    /** The hands, thrust out, for the clash. */
    private static Vec3 hands(Entity e, boolean rhalix, float pt) {
        return e.getPosition(pt).add(0, rhalix ? 1.35 : 1.15, 0).add(flatLook(e, pt).scale(1.1));
    }

    // ------------------------------------------------------------------ colours

    /** {core, body, glow} as 0xRRGGBB. */
    private static int[] palette(boolean rhalix) {
        return rhalix ? new int[]{0x0C0014, 0xB04CFF, 0x6A1FD0} : new int[]{0xFFFBEA, 0xFFB22E, 0xFF3A10};
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        float now = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        // pass 1: dark cores (normal blending - light can't make black); pass 2: the glow (additive)
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Fx f : LIVE) {
                float age = now - f.start;
                if (age < 0) continue;
                try { draw(bb, m, mc, f, age, now, pt, pass == 1); } catch (RuntimeException ignored) {}
            }
            BufferUploader.drawWithShader(bb.end());
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void draw(BufferBuilder bb, Matrix4f m, Minecraft mc, Fx f, float age, float now, float pt, boolean glow) {
        XenoFxPacket p = f.m;
        int life = Math.max(1, p.life());
        float fadeOut = Mth.clamp((life - age) / 8F, 0F, 1F);
        int[] col = palette(p.rhalix());
        Entity caster = mc.level.getEntity(p.caster());
        switch (p.kind()) {
            case CHARGE -> {
                if (caster == null) return;
                float k = Mth.clamp(age / life, 0F, 1F);
                float r = (0.12F + k * p.size()) * (1F + 0.08F * Mth.sin(now * 1.3F));
                ball(bb, m, chargeSpot(caster, p.rhalix(), pt), r, col, fadeOut, now, glow, p.rhalix());
                if (glow) {          // energy streaks pulled in towards it
                    Vec3 c = chargeSpot(caster, p.rhalix(), pt);
                    for (int i = 0; i < 6; i++) {
                        double a = i * 1.047 + now * 0.15, b = i * 2.1 + now * 0.11;
                        float ph = ((now * 0.06F + i * 0.17F) % 1F);
                        double dist = (1F - ph) * (1.6 + r * 2);
                        Vec3 dir = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b) * 0.7, Math.sin(a) * Math.cos(b)).normalize();
                        Vec3 s = c.add(dir.scale(dist)), e = c.add(dir.scale(dist * 0.55 + r));
                        tube(bb, m, s, e, 0.03F, 0.05F, col[1], 0.5F * ph * fadeOut, 6, 0, now);
                    }
                }
            }
            case BEAM -> {           // Mikiko: a long solid beam with a big head where it ends
                if (caster == null) return;
                Vec3 d = look(caster, pt);
                Vec3 s = caster.getPosition(pt).add(0, caster.getEyeHeight() - 0.4, 0).add(d.scale(0.9));
                float grow = Mth.clamp(age / 6F, 0F, 1F);
                double len = 40 * grow;
                var hit = mc.level.clip(new ClipContext(s, s.add(d.scale(len)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
                if (hit.getType() == HitResult.Type.BLOCK) len = hit.getLocation().distanceTo(s);
                Vec3 e = s.add(d.scale(len));
                float w = p.size();
                float r0 = 0.45F, r1 = 0.55F + w * 0.42F;
                float shimmer = 1F + 0.06F * Mth.sin(now * 2.1F);
                beamLayers(bb, m, s, e, r0 * shimmer, r1 * shimmer, col, fadeOut, now, glow, false);
                ball(bb, m, s, r0 * 1.5F * shimmer, col, fadeOut, now, glow, false);            // flare at the hands
                ball(bb, m, e, r1 * 1.35F * shimmer, col, fadeOut, now, glow, false);           // the head
                if (glow) for (int i = 0; i < 4; i++) {        // rings sliding down it
                    float ph = (now * 0.08F + i * 0.25F) % 1F;
                    Vec3 q = s.lerp(e, ph);
                    float rr = (r0 + (r1 - r0) * (float) Math.pow(ph, 0.6)) * 1.5F;
                    ring(bb, m, q, d, rr, rr * 0.18F, col[1], 0.35F * fadeOut * (1F - ph));
                }
            }
            case ORB -> {            // Rhalix: the dark orb flies out, slow and heavy
                Vec3 d = new Vec3(p.dx(), p.dy(), p.dz());
                Vec3 c = new Vec3(p.x(), p.y(), p.z()).add(d.scale(0.9 * Math.min(age, life)));
                float r = p.size() * (1F + 0.07F * Mth.sin(now * 1.7F));
                ball(bb, m, c, r, col, 1F, now, glow, true);
                if (glow) tube(bb, m, c.subtract(d.scale(5)), c, 0.05F, r * 0.8F, col[1], 0.3F, 12, 0, now);      // its trail
            }
            case BURST -> {          // a sphere of energy blowing outward and fading
                float k = Mth.clamp(age / life, 0F, 1F);
                float r = p.size() * (0.3F + 0.9F * (1F - (1F - k) * (1F - k)));
                Vec3 c = new Vec3(p.x(), p.y(), p.z());
                float a = (1F - k);
                ball(bb, m, c, r, col, a, now, glow, p.rhalix());
                if (glow) ring(bb, m, c, new Vec3(0, 1, 0), r * 1.6F, r * 0.25F, col[1], 0.5F * a);
            }
            case SHOT -> {           // the winning blast pushing through onto the loser
                Entity to = mc.level.getEntity(p.other());
                Vec3 a = new Vec3(p.x(), p.y(), p.z()), b = to != null ? to.getPosition(pt).add(0, 1, 0) : a;
                float k = Mth.clamp(age / life, 0F, 1F);
                Vec3 head = a.lerp(b, k);
                beamLayers(bb, m, a, head, p.size() * 0.6F, p.size(), col, 1F, now, glow, p.rhalix());
                ball(bb, m, head, p.size() * 1.5F, col, 1F, now, glow, p.rhalix());
            }
            case CLASH -> {          // two blasts pushing against each other, a blazing ball where they meet
                Entity rival = mc.level.getEntity(p.other());
                if (caster == null || rival == null) return;
                boolean rr = p.dx() > 0.5F;            // (rival is Rhalix)
                int[] col2 = palette(rr);
                Vec3 a = hands(caster, p.rhalix(), pt), b = hands(rival, rr, pt);
                Vec3 meet = a.lerp(b, f.size);
                float wob = 1F + 0.1F * Mth.sin(now * 2.7F);
                beamLayers(bb, m, a, meet, 0.4F * wob, 0.75F * wob, col, 1F, now, glow, p.rhalix());
                beamLayers(bb, m, b, meet, 0.4F / wob, 0.75F / wob, col2, 1F, now, glow, rr);
                float r = 1.2F + 0.25F * Mth.sin(now * 3.3F);
                if (glow) {
                    sphere(bb, m, meet, r * 0.6F, 0xFFFFFF, 0.95F, now, 0F);
                    sphere(bb, m, meet, r, col[1], 0.4F, now, 0.12F);
                    sphere(bb, m, meet, r * 1.15F, col2[1], 0.3F, now + 7, 0.15F);
                    sphere(bb, m, meet, r * 1.9F, 0xFFFFFF, 0.12F, now, 0.2F);
                    Vec3 axis = b.subtract(a).normalize();
                    for (int i = 0; i < 3; i++) {       // shockwave rings blowing off the meeting point
                        float ph = (now * 0.1F + i / 3F) % 1F;
                        ring(bb, m, meet, axis, r * (1.2F + ph * 2.5F), 0.2F, i % 2 == 0 ? col[1] : col2[1], 0.5F * (1F - ph));
                    }
                }
            }
            default -> {}
        }
    }

    /** A ki ball: white-hot (or black) core, coloured body, soft glow. */
    private static void ball(BufferBuilder bb, Matrix4f m, Vec3 c, float r, int[] col, float alpha, float now, boolean glow, boolean dark) {
        if (alpha <= 0.01F) return;
        if (!glow) { if (dark) sphere(bb, m, c, r * 0.62F, col[0], 0.97F * alpha, now, 0.05F); return; }
        if (!dark) sphere(bb, m, c, r * 0.55F, col[0], 0.95F * alpha, now, 0F);
        sphere(bb, m, c, r * 0.82F, col[1], 0.55F * alpha, now, 0.08F);
        sphere(bb, m, c, r, col[2], 0.35F * alpha, now + 3, 0.12F);
        sphere(bb, m, c, r * 1.5F, col[2], 0.12F * alpha, now + 5, 0.18F);
    }

    /** A beam: white (or black) core tube inside a coloured tube inside a glow. */
    private static void beamLayers(BufferBuilder bb, Matrix4f m, Vec3 s, Vec3 e, float r0, float r1, int[] col, float alpha, float now, boolean glow, boolean dark) {
        if (alpha <= 0.01F || s.distanceToSqr(e) < 0.01) return;
        if (!glow) { if (dark) tube(bb, m, s, e, r0 * 0.5F, r1 * 0.5F, col[0], 0.95F * alpha, 14, 0.04F, now); return; }
        if (!dark) tube(bb, m, s, e, r0 * 0.45F, r1 * 0.45F, col[0], 0.95F * alpha, 14, 0F, now);
        tube(bb, m, s, e, r0 * 0.8F, r1 * 0.8F, col[1], 0.6F * alpha, 16, 0.06F, now);
        tube(bb, m, s, e, r0, r1, col[2], 0.35F * alpha, 16, 0.1F, now + 4);
        tube(bb, m, s, e, r0 * 1.45F, r1 * 1.45F, col[2], 0.12F * alpha, 16, 0.14F, now + 8);
    }

    private static void sphere(BufferBuilder bb, Matrix4f m, Vec3 c, float r, int rgb, float a, float now, float wobble) {
        int lat = 10, lon = 16;
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                sv(bb, m, c, r, t0, p0, wobble, now, cr, cg, cb, a);
                sv(bb, m, c, r, t0, p1, wobble, now, cr, cg, cb, a);
                sv(bb, m, c, r, t1, p1, wobble, now, cr, cg, cb, a);
                sv(bb, m, c, r, t1, p0, wobble, now, cr, cg, cb, a);
            }
        }
    }

    private static void sv(BufferBuilder bb, Matrix4f m, Vec3 c, float r, double t, double p, float wobble, float now, float cr, float cg, float cb, float a) {
        float rr = r * (1F + wobble * (float) Math.sin(p * 3 + now * 0.9 + t * 4));
        bb.vertex(m, (float) (c.x + Math.cos(t) * Math.cos(p) * rr), (float) (c.y + Math.sin(t) * rr), (float) (c.z + Math.cos(t) * Math.sin(p) * rr))
                .color(cr, cg, cb, a).endVertex();
    }

    /** A tube from s to e, radius r0 -> r1 (widening like a cone), its surface rippling. */
    private static void tube(BufferBuilder bb, Matrix4f m, Vec3 s, Vec3 e, float r0, float r1, int rgb, float a, int sides, float wobble, float now) {
        Vec3 d = e.subtract(s);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 n = d.scale(1 / len);
        Vec3 up = Math.abs(n.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(up).normalize(), v = n.cross(u).normalize();
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        int segs = Math.max(1, Math.min(40, (int) (len / 1.2)));
        for (int k = 0; k < segs; k++) {
            double f0 = k / (double) segs, f1 = (k + 1) / (double) segs;
            float ra = (float) (r0 + (r1 - r0) * Math.pow(f0, 0.6)), rb = (float) (r0 + (r1 - r0) * Math.pow(f1, 0.6));
            Vec3 c0 = s.add(d.scale(f0)), c1 = s.add(d.scale(f1));
            for (int j = 0; j < sides; j++) {
                double p0 = Math.PI * 2 * j / sides, p1 = Math.PI * 2 * (j + 1) / sides;
                tv(bb, m, c0, u, v, ra, p0, f0 * len, wobble, now, cr, cg, cb, a);
                tv(bb, m, c0, u, v, ra, p1, f0 * len, wobble, now, cr, cg, cb, a);
                tv(bb, m, c1, u, v, rb, p1, f1 * len, wobble, now, cr, cg, cb, a);
                tv(bb, m, c1, u, v, rb, p0, f1 * len, wobble, now, cr, cg, cb, a);
            }
        }
    }

    private static void tv(BufferBuilder bb, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float r, double p, double along, float wobble, float now, float cr, float cg, float cb, float a) {
        float rr = r * (1F + wobble * (float) Math.sin(p * 4 + along * 1.3 - now * 1.6));
        Vec3 q = c.add(u.scale(Math.cos(p) * rr)).add(v.scale(Math.sin(p) * rr));
        bb.vertex(m, (float) q.x, (float) q.y, (float) q.z).color(cr, cg, cb, a).endVertex();
    }

    /** A flat ring around an axis. */
    private static void ring(BufferBuilder bb, Matrix4f m, Vec3 c, Vec3 axis, float r, float thick, int rgb, float a) {
        if (a <= 0.01F) return;
        Vec3 n = axis.normalize();
        Vec3 up = Math.abs(n.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(up).normalize(), v = n.cross(u).normalize();
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        int sides = 24;
        for (int j = 0; j < sides; j++) {
            double p0 = Math.PI * 2 * j / sides, p1 = Math.PI * 2 * (j + 1) / sides;
            Vec3 o0 = u.scale(Math.cos(p0)).add(v.scale(Math.sin(p0))), o1 = u.scale(Math.cos(p1)).add(v.scale(Math.sin(p1)));
            Vec3[] q = {c.add(o0.scale(r - thick)), c.add(o1.scale(r - thick)), c.add(o1.scale(r + thick)), c.add(o0.scale(r + thick))};
            for (Vec3 x : q) bb.vertex(m, (float) x.x, (float) x.y, (float) x.z).color(cr, cg, cb, a).endVertex();
        }
    }
}

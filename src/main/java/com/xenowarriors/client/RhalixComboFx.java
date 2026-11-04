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
import com.xenowarriors.client.combo.ComboClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The look of Rhalix's ground combo (after the reference video), in his black / purple palette, drawn as solid glowing
 * shapes like KiFx: dark claw crescents with a hot magenta edge and a thin streak of light across each slash, black
 * crow wings of feathers sweeping up on the Crow Wing and the Raven Launcher, a dark swirl on the Gravity Whirl, an
 * impact flash on the launch, dust off the skid of his rush, and a puddle of dark aura spreading under his feet through
 * the string (still there a moment after it). The finisher's gravity sphere is the server's KiFx burst on the target.
 * Played for anyone doing it (you, other players, his party bots) off the combo swings everyone already sees.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixComboFx {
    private static final int CRESCENT = 0, STREAK = 1, WING = 2, SWIRL = 3, FLASH = 4, BURST = 5, BLAST = 6, TRAIL = 7, PILLARS = 8, POOL = 9;
    private static final int KI_WHITE = 0xF4FBFF, KI_BLUE = 0x7FD0FF;
    // palette
    private static final int INK = 0x07000D, SHADE = 0x24083F, MAGENTA = 0xFF3CD8, VIOLET = 0x7A30FF, LIGHT = 0xE4F0FF, NAVY = 0x0B1052, BLUE = 0x3A5CFF;

    /** One effect: where (a frame: origin, a and b spanning its plane), when, how big. */
    private record Fx(int kind, float start, int life, Vec3 o, Vec3 a, Vec3 b, float size, float p0, float p1, float width) {}

    private static final List<Fx> LIVE = new ArrayList<>();

    private static final class Track {
        int start = Integer.MIN_VALUE, hit = -2; boolean heavy; int done;   // effects of this swing already spawned (bits)
        float puddle; int puddleHold;
    }
    private static final Map<Integer, Track> TRACKS = ClientReset.track(new HashMap<>());
    private static boolean warned;

    private RhalixComboFx() {}

    /** How many effects are live right now (for tests). */
    public static int live() { return LIVE.size(); }

    // ------------------------------------------------------------------ spawning

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { LIVE.clear(); return; }
        if (mc.isPaused()) return;
        float now = level.getGameTime();
        LIVE.removeIf(f -> now - f.start > f.life + 1 || now < f.start - 40);
        for (AbstractClientPlayer p : com.xenowarriors.client.bot.BotAvatars.players(level)) {
            Track tr = TRACKS.get(p.getId());
            if (!RhalixComboAnim.owns(p)) {
                if (tr != null) {                                          // the aura puddle lingering, then soaking away
                    if (tr.puddleHold > 0) tr.puddleHold--;
                    else tr.puddle = Math.max(0F, tr.puddle - 0.04F);
                    if (tr.puddle <= 0F) TRACKS.remove(p.getId());
                }
                continue;
            }
            if (tr == null) TRACKS.put(p.getId(), tr = new Track());
            ComboClient.Swing sw = ComboClient.swing(p);
            if (sw == null) continue;
            if (sw.start() != tr.start || sw.hit() != tr.hit || sw.heavy() != tr.heavy) {
                tr.start = sw.start(); tr.hit = sw.hit(); tr.heavy = sw.heavy(); tr.done = 0;
                tr.puddle = Math.max(tr.puddle, 0.55F + 0.06F * Math.max(0, sw.hit()));
                tr.puddleHold = sw.heavy() || sw.hit() >= 7 ? 45 : 14;
            }
            float t = (p.tickCount - sw.start()) / (float) sw.duration();
            float startTime = now - (p.tickCount - sw.start());               // the swing's start in level time
            int move = sw.heavy() ? 6 : sw.style() == com.xenowarriors.combat.ComboStyle.RHALIX_AIR ? 10 + sw.hit()
                    : sw.hit() == 4 && KishiWarpClient.warpSlash(p, sw.start()) ? 30 : sw.hit();
            spawn(level, p, tr, move, t, startTime, sw.duration());
            if (move == 0 && t < 0.4F) skidDust(level, p);
        }
    }

    private static Vec3 fwd(LivingEntity p) {
        float y = p.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y), 0, Mth.cos(y));
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Does effect bit i of this swing once its progress passes at; returns true the tick it does. */
    private static boolean due(Track tr, int i, float t, float at) {
        if (t < at || (tr.done & (1 << i)) != 0) return false;
        tr.done |= 1 << i;
        return true;
    }

    private static Vec3 dir(Vec3 f, float deg) {
        return f.scale(Mth.cos(deg * Mth.DEG_TO_RAD)).add(UP.scale(Mth.sin(deg * Mth.DEG_TO_RAD))).normalize();
    }

    private static void spawn(ClientLevel level, AbstractClientPlayer p, Track tr, int move, float t, float start, int dur) {
        Vec3 f = fwd(p), r = f.cross(UP).normalize(), o = p.position();
        float at;
        switch (move) {
            case 0 -> {      // Shadow Rush: the rising claw, a long light streak up across it
                if (due(tr, 0, t, at = 0.28F)) {
                    add(CRESCENT, start + at * dur, 9, o.add(UP.scale(1.05)).add(f.scale(0.7)), f, UP.scale(0.9).add(r.scale(-0.42)).normalize(), 1.25F, -70F, 100F, 0.42F);
                    add(STREAK, start + at * dur + 1, 6, o.add(UP.scale(1.2)).add(f.scale(1.4)), dir(f, 25F), null, 6.5F, 0, 0, 0.04F);
                }
            }
            case 1 -> {      // Low Lunge: a dark burst where the fist goes in
                if (due(tr, 0, t, at = 0.34F)) {
                    add(BURST, start + at * dur, 8, o.add(UP.scale(0.82)).add(f.scale(1.05)).add(r.scale(0.12)), f, UP, 0.42F, 0, 0, 0);
                    add(CRESCENT, start + at * dur, 7, o.add(UP.scale(0.85)).add(f.scale(0.6)), f, r, 0.6F, -60F, 60F, 0.22F);
                }
            }
            case 2 -> {      // Crescent Slash: a big flat crescent swept right to left, a streak along it
                if (due(tr, 0, t, at = 0.3F)) {
                    add(CRESCENT, start + at * dur, 9, o.add(UP.scale(1.1)).add(f.scale(0.25)), r, dir(f, 32F), 1.55F, 0F, 180F, 0.45F);
                    add(STREAK, start + at * dur + 1, 6, o.add(UP.scale(1.0)).add(f.scale(1.3)), dir(f, -10F), null, 6.5F, 0, 0, 0.04F);
                }
            }
            case 3 -> {      // Crow Wing: a black wing of feathers sweeping up in front of him
                if (due(tr, 0, t, at = 0.28F)) {
                    add(CRESCENT, start + at * dur, 8, o.add(UP.scale(1.0)).add(f.scale(0.45)), f, UP, 1.0F, -40F, 115F, 0.3F);
                    add(WING, start + at * dur, 13, o.add(f.scale(0.95)).add(UP.scale(0.2)), f, UP, 2.2F, 12F, 98F, 0F);
                    feathers(level, o.add(f.scale(1.2)).add(UP.scale(1.2)), 12);
                }
            }
            case 4 -> {      // Gravity Whirl: a dark swirl round them, a purple ring through it
                if (due(tr, 0, t, at = 0.22F)) {
                    add(SWIRL, start + at * dur, 15, o.add(f.scale(1.25)).add(UP.scale(0.75)), f, UP, 1.0F, 0, 0, 0);
                    add(CRESCENT, start + (at + 0.15F) * dur, 9, o.add(UP.scale(0.95)).add(f.scale(0.2)), r, dir(f, 20F), 1.45F, -20F, 200F, 0.35F);
                }
            }
            case 5 -> {      // Reaping Grab: a low sweep along the floor, a dark flash as he hauls them up
                if (due(tr, 0, t, at = 0.28F)) {
                    add(CRESCENT, start + at * dur, 9, o.add(UP.scale(0.35)).add(f.scale(0.55)), r, dir(f, 18F), 1.35F, 0F, 180F, 0.36F);
                    add(STREAK, start + at * dur + 1, 6, o.add(UP.scale(0.45)).add(f.scale(1.3)), dir(f, -6F), null, 5.5F, 0, 0, 0.035F);
                }
                if (due(tr, 1, t, at = 0.6F)) add(BURST, start + at * dur, 7, o.add(UP.scale(1.85)).add(f.scale(0.85)), f, UP, 0.34F, 0, 0, 0);
            }
            case 6 -> {      // Raven Launcher (and the heavy): a big rising crescent, the impact flash, a wing spread under them
                if (due(tr, 0, t, at = 0.28F)) {
                    add(CRESCENT, start + at * dur, 10, o.add(UP.scale(1.2)).add(f.scale(0.6)), f, UP.scale(0.9).add(r.scale(0.4)).normalize(), 1.7F, -80F, 105F, 0.55F);
                    add(STREAK, start + at * dur + 1, 7, o.add(UP.scale(1.5)).add(f.scale(1.0)), dir(f, 42F), null, 7F, 0, 0, 0.045F);
                }
                if (due(tr, 1, t, at = 0.36F)) add(FLASH, start + at * dur, 6, o.add(UP.scale(1.6)).add(f.scale(0.95)), f, UP, 0.95F, 0, 0, 0);
                if (due(tr, 2, t, at = 0.4F)) {
                    add(WING, start + at * dur, 14, o.add(f.scale(0.8)).add(UP.scale(0.55)), f, UP, 2.0F, -32F, 45F, 0F);
                    feathers(level, o.add(f.scale(1.4)).add(UP.scale(0.9)), 14);
                }
            }
            case 7 -> {      // Gravity Burst: a slash down across them (the sphere itself comes from the server)
                if (due(tr, 0, t, at = 0.27F)) {
                    add(CRESCENT, start + at * dur, 10, o.add(UP.scale(1.35)).add(f.scale(0.5)), f, UP.scale(0.9).add(r.scale(-0.4)).normalize(), 1.55F, 115F, -50F, 0.48F);
                    add(STREAK, start + at * dur + 1, 7, o.add(UP.scale(1.8)).add(f.scale(1.2)), dir(f, 40F), null, 7F, 0, 0, 0.045F);
                }
            }
            case 30 -> {     // the Kishi Warp's slash: warped in, a huge flat slash and a long streak
                if (due(tr, 0, t, at = 0.28F)) {
                    add(CRESCENT, start + at * dur, 10, o.add(UP.scale(1.0)).add(f.scale(0.3)), r, dir(f, 25F), 1.8F, -10F, 190F, 0.5F);
                    add(STREAK, start + at * dur + 1, 7, o.add(UP.scale(0.9)).add(f.scale(1.4)), dir(f, -4F), null, 8F, 0, 0, 0.045F);
                }
            }
            case 20 -> {     // Ki Burst: a blue-white blast out of his palm, point-blank
                if (due(tr, 0, t, at = 0.33F)) add(BLAST, start + at * dur, 9, o.add(UP.scale(0.95)).add(f.scale(0.6)), dir(f, -12F), UP, 3.4F, 0, 0, 0);
            }
            case 21 -> {     // Rising Leap: dust off the take-off, the claw rising with him
                if (due(tr, 0, t, at = 0.26F)) add(CRESCENT, start + at * dur, 9, o.add(UP.scale(1.3)).add(f.scale(0.6)), f, UP, 1.3F, -60F, 100F, 0.4F);
            }
            case 10 -> {     // air: the rising leap's claw
                if (due(tr, 0, t, at = 0.3F)) add(CRESCENT, start + at * dur, 8, o.add(UP.scale(1.1)).add(f.scale(0.6)), f, UP, 1.2F, -60F, 100F, 0.38F);
            }
            case 11 -> {     // air: a flat crescent and a dark streak
                if (due(tr, 0, t, at = 0.3F)) {
                    add(CRESCENT, start + at * dur, 9, o.add(UP.scale(1.1)).add(f.scale(0.25)), r, dir(f, 30F), 1.45F, 0F, 180F, 0.42F);
                    add(STREAK, start + at * dur + 1, 6, o.add(UP.scale(1.1)).add(f.scale(1.2)), dir(f, -8F), null, 6F, 0, 0, 0.035F);
                }
            }
            case 12 -> {     // air: a close strike
                if (due(tr, 0, t, at = 0.34F)) add(BURST, start + at * dur, 7, o.add(UP.scale(1.0)).add(f.scale(1.0)), f, UP, 0.38F, 0, 0, 0);
            }
            case 13 -> {     // air: the big slash, magenta-edged, a long light streak through it
                if (due(tr, 0, t, at = 0.28F)) {
                    add(CRESCENT, start + at * dur, 10, o.add(UP.scale(1.2)).add(f.scale(0.5)), f, UP.scale(0.9).add(r.scale(0.4)).normalize(), 1.7F, 110F, -60F, 0.52F);
                    add(STREAK, start + at * dur + 1, 7, o.add(UP.scale(1.0)).add(f.scale(1.3)), dir(f, -12F), null, 7.5F, 0, 0, 0.045F);
                }
            }
            case 14 -> {     // air: the spike slam - a slash straight down and a dark trail after them
                if (due(tr, 0, t, at = 0.3F)) {
                    add(CRESCENT, start + at * dur, 9, o.add(UP.scale(1.0)).add(f.scale(0.7)), f, UP, 1.4F, 110F, -80F, 0.48F);
                    add(TRAIL, start + at * dur + 1, 10, o.add(f.scale(1.1)).add(UP.scale(0.6)), UP.scale(-1), null, 3.5F, 0, 0, 0.35F);
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ bits for ComboClient / KishiWarpClient

    private static final boolean TEST = System.getenv("XW_UITEST_FOCUS") != null;

    /** A test log line (only in the UI test). */
    public static void log(String s) { if (TEST) XenoWarriors.LOGGER.info("XW-TEST rhalixcombo client " + s); }

    /** Charging a held attack: sparks gathering in his palm (blue-white at half, his violet once it's full). */
    public static void chargeSparks(AbstractClientPlayer p, boolean full) {
        Vec3 f = fwd(p), r = f.cross(UP).normalize();
        Vec3 hand = p.position().add(0, 0.85, 0).add(f.scale(-0.1)).add(r.scale(0.38));
        var rnd = p.getRandom();
        for (int i = 0; i < (full ? 3 : 2); i++) {
            Vec3 from = hand.add((rnd.nextDouble() - 0.5) * 0.8, (rnd.nextDouble() - 0.5) * 0.8, (rnd.nextDouble() - 0.5) * 0.8);
            Vec3 v = hand.subtract(from).scale(0.25);
            p.level().addParticle(full ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, v.x, v.y, v.z);
        }
    }

    /** A cloud of dust where he takes off (the Rising Leap). */
    public static void takeOffDust(AbstractClientPlayer p) {
        var level = p.level();
        var below = level.getBlockState(p.blockPosition().below());
        var rnd = p.getRandom();
        for (int i = 0; i < 12; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            if (!below.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), p.getX() + Math.cos(a) * 0.4, p.getY() + 0.05, p.getZ() + Math.sin(a) * 0.4, Math.cos(a) * 0.15, 0.1, Math.sin(a) * 0.15);
            if (i % 3 == 0) level.addParticle(ParticleTypes.POOF, p.getX(), p.getY() + 0.1, p.getZ(), Math.cos(a) * 0.06, 0.02, Math.sin(a) * 0.06);
        }
    }

    /** The warp: a burst where he was, dark pillars and a flash where he lands, a pool of Kishi spreading under them. */
    static void warpBurst(ClientLevel level, KishiWarpClient.Wave w) {
        float now = level.getGameTime();
        add(BURST, now, 9, w.from.add(0, 1.0, 0), UP, UP, 0.7F, 0, 0, 0);
        add(FLASH, now, 7, w.to.add(0, 1.1, 0), UP, UP, 1.2F, 0, 0, 0);
        add(PILLARS, now - 3, 14, w.path.get(w.path.size() - 1), UP, UP, 2.6F, 0, 0, 0);
        add(POOL, now, 55, w.path.get(w.path.size() - 1).add(0, 0.03, 0), UP, UP, 1.7F, 0, 0, 0);
    }

    private static void add(int kind, float start, int life, Vec3 o, Vec3 a, Vec3 b, float size, float p0, float p1, float width) {
        if (LIVE.size() < 200) LIVE.add(new Fx(kind, start, life, o, a, b, size, p0, p1, width));
    }

    /** Black feathers drifting off a wing. */
    private static void feathers(ClientLevel level, Vec3 at, int n) {
        var rnd = level.getRandom();
        DustParticleOptions black = new DustParticleOptions(new Vector3f(0.03F, 0.0F, 0.06F), 1.6F);
        for (int i = 0; i < n; i++) {
            double x = at.x + (rnd.nextDouble() - 0.5) * 1.4, y = at.y + (rnd.nextDouble() - 0.5) * 1.2, z = at.z + (rnd.nextDouble() - 0.5) * 1.4;
            level.addParticle(i % 2 == 0 ? ParticleTypes.SQUID_INK : black, x, y, z, (rnd.nextDouble() - 0.5) * 0.05, 0.02 + rnd.nextDouble() * 0.04, (rnd.nextDouble() - 0.5) * 0.05);
        }
    }

    /** Dust kicked up behind him as he skids in. */
    private static void skidDust(ClientLevel level, AbstractClientPlayer p) {
        double sp = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
        if (sp < 0.12 || !p.onGround()) return;
        Vec3 f = fwd(p);
        var below = level.getBlockState(p.blockPosition().below());
        var rnd = level.getRandom();
        double x = p.getX() - f.x * 0.45, z = p.getZ() - f.z * 0.45;
        for (int i = 0; i < 4; i++) {
            if (!below.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), x + (rnd.nextDouble() - 0.5) * 0.4, p.getY() + 0.05, z + (rnd.nextDouble() - 0.5) * 0.4,
                    -f.x * 0.1, 0.12, -f.z * 0.1);
            if (i % 2 == 0) level.addParticle(ParticleTypes.POOF, x, p.getY() + 0.1, z, -f.x * 0.04 + (rnd.nextDouble() - 0.5) * 0.03, 0.03, -f.z * 0.04);
        }
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        Iterable<KishiWarpClient.Wave> waves = KishiWarpClient.waves();
        if (mc.level == null || (LIVE.isEmpty() && TRACKS.isEmpty() && KnockdownClient.DOWN.isEmpty() && !waves.iterator().hasNext())) { KnockdownClient.DOWN.clear(); return; }
        float pt = event.getPartialTick();
        float now = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        Vector3f lv = event.getCamera().getLeftVector(), uv = event.getCamera().getUpVector();
        Vec3 camL = new Vec3(lv.x(), lv.y(), lv.z()), camU = new Vec3(uv.x(), uv.y(), uv.z());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        // pass 0: the dark shapes (normal blending - light can't make black); pass 1: the glows on top (additive)
        for (int pass = 0; pass < 2; pass++) {
            boolean glow = pass == 1;
            if (glow) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            try {
                for (var en : TRACKS.entrySet()) {
                    if (en.getValue().puddle > 0F && mc.level.getEntity(en.getKey()) instanceof LivingEntity e) puddle(bb, m, e, en.getValue().puddle, now, pt, glow);
                }
                for (Fx f : LIVE) {
                    float age = now - f.start;
                    if (age >= 0 && age <= f.life) draw(bb, m, f, age, now, cam, glow);
                }
                for (KishiWarpClient.Wave w : waves) wave(bb, m, w, now, glow);
                if (glow) for (Object[] d : KnockdownClient.DOWN) stars(bb, m, (LivingEntity) d[0], (float) d[1], now, pt, camL, camU);
            } catch (RuntimeException ex) {
                if (!warned) { warned = true; XenoWarriors.LOGGER.error("RhalixComboFx draw failed", ex); }
            }
            BufferUploader.drawWithShader(bb.end());
        }
        KnockdownClient.DOWN.clear();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    private static void draw(BufferBuilder bb, Matrix4f m, Fx f, float age, float now, Vec3 cam, boolean glow) {
        switch (f.kind) {
            case CRESCENT -> {
                // the claw's crescent: drawn out along its arc over 2 ticks, then the tail catches up and it fades
                float head = smooth(age / 2.2F), tail = smooth((age - 2.5F) / (f.life - 2.5F));
                float fade = 1F - smooth((age - f.life * 0.45F) / (f.life * 0.55F));
                float a0 = Mth.lerp(tail * 0.85F, f.p0, f.p1), a1 = Mth.lerp(head, f.p0, f.p1);
                if (glow) {
                    arc(bb, m, f, a0, a1, f.size * 1.03F, f.width * 1.25F, VIOLET, 0.28F * fade);
                    arc(bb, m, f, a0, a1, f.size * 0.995F, f.width * 0.28F, MAGENTA, 0.95F * fade);       // the hot edge
                    arc(bb, m, f, a0, a1, f.size * 0.985F, f.width * 0.1F, 0xFFD6FA, 0.8F * fade);
                } else {
                    arc(bb, m, f, a0, a1, f.size, f.width, INK, 0.88F * fade);
                    arc(bb, m, f, a0, a1, f.size * 1.04F, f.width * 1.35F, SHADE, 0.4F * fade);
                }
            }
            case STREAK -> {
                // a thin streak of light, flicking across the whole slash, inside a dark band
                float grow = smooth(age / 1.6F), fade = 1F - smooth((age - 1.5F) / (f.life - 1.5F));
                Vec3 s = f.o.subtract(f.a.scale(f.size * 0.5)), e = s.add(f.a.scale(f.size * grow));
                if (glow) {
                    band(bb, m, s, e, cam, f.width, LIGHT, 0.95F * fade);
                    band(bb, m, s, e, cam, f.width * 3F, 0x9FC8FF, 0.3F * fade);
                } else band(bb, m, s, e, cam, f.width * 5F, INK, 0.55F * fade);
            }
            case WING -> wing(bb, m, f, age, glow);
            case BLAST -> {
                // the Ki Burst: a bright blue-white blast thrown out of his palm, widening as it goes, then gone
                float k = age / f.life, reach = f.size * smooth(age / 2.5F), fade = 1F - smooth((k - 0.35F) / 0.65F);
                for (int i = 0; i <= 6; i++) {
                    float u = i / 6F;
                    Vec3 c = f.o.add(f.a.scale(reach * u));
                    float r = (0.18F + 0.55F * u) * (0.8F + 0.4F * smooth(k * 2F));
                    if (glow) { sphere(bb, m, c, r * 0.55F, KI_WHITE, 0.75F * fade * (1F - u * 0.4F)); sphere(bb, m, c, r, KI_BLUE, 0.3F * fade); }
                }
                if (glow) sphere(bb, m, f.o.add(f.a.scale(reach)), 0.8F * fade + 0.2F, KI_WHITE, 0.55F * fade);
            }
            case TRAIL -> {           // a dark trail straight down after the slam
                float grow = smooth(age / 2F), fade = 1F - smooth((age - 2F) / (f.life - 2F));
                Vec3 e = f.o.add(f.a.scale(f.size * grow));
                if (glow) band(bb, m, f.o, e, cam, f.width * 0.35F, MAGENTA, 0.5F * fade);
                else band(bb, m, f.o, e, cam, f.width, INK, 0.85F * fade);
            }
            case PILLARS -> {         // shadow pillars shooting up out of the Kishi where it caught them
                float k = age / f.life, h = f.size * smooth(age / 3F), fade = 1F - smooth((k - 0.5F) / 0.5F);
                for (int i = 0; i < 4; i++) {
                    double a = i * 1.7 + f.start;
                    Vec3 b = f.o.add(Math.cos(a) * 0.75, 0, Math.sin(a) * 0.75);
                    float hh = h * (0.6F + 0.4F * ((i * 37) % 10) / 10F);
                    if (glow) band(bb, m, b, b.add(0, hh, 0), cam, 0.12F, VIOLET, 0.3F * fade);
                    else band(bb, m, b, b.add(0, hh, 0), cam, 0.32F, INK, 0.85F * fade);
                }
            }
            case POOL -> {            // the pool of Kishi left on the floor under them
                float a = smooth(age / 4F) * (1F - smooth((age - f.life * 0.6F) / (f.life * 0.4F)));
                disc(bb, m, f.o, f.size * (0.6F + 0.4F * smooth(age / 8F)), a, now, glow);
            }
            case SWIRL -> swirl(bb, m, f, age, now, glow);
            case FLASH -> {
                float k = age / f.life, r = f.size * (0.35F + 0.65F * smooth(k * 1.5F)), fade = 1F - smooth(k);
                if (glow) { sphere(bb, m, f.o, r * 0.5F, 0xFFFFFF, 0.9F * fade); sphere(bb, m, f.o, r, 0xC59BFF, 0.45F * fade); }
                else sphere(bb, m, f.o, r * 1.15F, SHADE, 0.25F * fade);
            }
            case BURST -> {
                float k = age / f.life, r = f.size * (0.5F + 0.8F * smooth(k * 1.4F)), fade = 1F - smooth(k);
                if (glow) {
                    sphere(bb, m, f.o, r * 1.2F, VIOLET, 0.3F * fade);
                    for (int i = 0; i < 6; i++) {        // spikes of dark energy thrown off it
                        double a = i * 1.047 + f.start, b = (i % 3 - 1) * 0.6;
                        Vec3 d = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b));
                        band(bb, m, f.o, f.o.add(d.scale(r * 2.1F)), cam, 0.05F, MAGENTA, 0.6F * fade);
                    }
                } else sphere(bb, m, f.o, r * 0.8F, INK, 0.85F * fade);
            }
            default -> {}
        }
    }

    /** A crescent along an arc in the effect's plane: thick in the middle, pointed at both ends. */
    private static void arc(BufferBuilder bb, Matrix4f m, Fx f, float d0, float d1, float r, float w, int rgb, float a) {
        if (a <= 0.01F || Math.abs(d1 - d0) < 0.5F) return;
        int segs = 26;
        float full0 = f.p0, full1 = f.p1;
        for (int i = 0; i < segs; i++) {
            float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
            float g0 = Mth.lerp(u0, d0, d1), g1 = Mth.lerp(u1, d0, d1);
            float w0 = w * prof((g0 - full0) / (full1 - full0)), w1 = w * prof((g1 - full0) / (full1 - full0));
            Vec3 o0 = on(f, g0), o1 = on(f, g1);
            Vec3 p0 = f.o.add(o0.scale(r)), p1 = f.o.add(o1.scale(r)), q0 = f.o.add(o0.scale(r - w0)), q1 = f.o.add(o1.scale(r - w1));
            quad(bb, m, p0, p1, q1, q0, rgb, a, a);
        }
    }

    /** Crescent thickness along it: 0 at the ends, full a bit past the middle. */
    private static float prof(float u) {
        u = Mth.clamp(u, 0F, 1F);
        return (float) Math.pow(Math.sin(Math.PI * Math.pow(u, 0.8)), 0.9);
    }

    private static Vec3 on(Fx f, float deg) {
        float r = deg * Mth.DEG_TO_RAD;
        return f.a.scale(Mth.cos(r)).add(f.b.scale(Mth.sin(r)));
    }

    /** A camera-facing band from s to e. */
    private static void band(BufferBuilder bb, Matrix4f m, Vec3 s, Vec3 e, Vec3 cam, float w, int rgb, float a) {
        if (a <= 0.01F || s.distanceToSqr(e) < 1e-4) return;
        Vec3 d = e.subtract(s), side = d.cross(cam.subtract(s.add(e).scale(0.5))).normalize().scale(w * 0.5);
        Vec3 tipS = s, tipE = e;
        // tapered at both ends: two quads meeting at the widest point in the middle
        Vec3 mid = s.add(d.scale(0.5));
        quad(bb, m, tipS, tipS, mid.add(side), mid.subtract(side), rgb, a, a);
        quad(bb, m, mid.subtract(side), mid.add(side), tipE, tipE, rgb, a, a);
    }

    /** A crow's wing: long black feathers fanning open in the effect's plane, from low to high, then dissolving. */
    private static void wing(BufferBuilder bb, Matrix4f m, Fx f, float age, boolean glow) {
        int n = 9;
        float fade = 1F - smooth((age - f.life * 0.5F) / (f.life * 0.5F));
        for (int i = 0; i < n; i++) {
            float u = (float) i / (n - 1);
            float grow = smooth((age - u * 3F) / 2.5F);
            if (grow <= 0F) continue;
            float ang = Mth.lerp(u, f.p0, f.p1) * Mth.DEG_TO_RAD;
            Vec3 d = f.a.scale(Mth.cos(ang)).add(f.b.scale(Mth.sin(ang))).normalize();
            Vec3 side = f.a.cross(f.b).normalize();
            Vec3 perp = d.cross(side).normalize();             // in the plane, across the feather
            float len = f.size * (0.55F + 0.45F * Mth.sin((float) Math.PI * (0.25F + 0.65F * u))) * grow;
            float wd = 0.2F + 0.08F * Mth.sin(i * 2.3F);
            Vec3 root = f.o.add(d.scale(0.15F)), mid = root.add(d.scale(len * 0.55F)), tip = root.add(d.scale(len));
            // a ragged feather: wide through the middle, a notch near the tip
            Vec3 m1 = mid.add(perp.scale(wd)), m2 = mid.subtract(perp.scale(wd * 0.6F));
            Vec3 notch = root.add(d.scale(len * 0.82F)).add(perp.scale(wd * 0.3F));
            if (glow) {
                quad(bb, m, root, m1.add(perp.scale(0.05F)), tip.add(d.scale(0.08F)), m2.subtract(perp.scale(0.05F)), VIOLET, 0.16F * fade, 0.0F);
            } else {
                quad(bb, m, root, m1, notch, m2, INK, 0.93F * fade, 0.85F * fade);
                quad(bb, m, notch, m1, tip, tip, INK, 0.85F * fade, 0.7F * fade);
            }
        }
    }

    /** The Gravity Whirl's swirl: dark spiral arms turning round a dim sphere, a blue sheen on them. */
    private static void swirl(BufferBuilder bb, Matrix4f m, Fx f, float age, float now, boolean glow) {
        float k = age / f.life, fade = 1F - smooth((k - 0.55F) / 0.45F), open = smooth(k * 3F);
        Vec3 side = f.a.cross(f.b).normalize();
        // the plane faces sideways a little more than straight across his front, so it reads from any side
        Vec3 pa = f.a.scale(0.8).add(side.scale(0.6)).normalize(), pb = f.b;
        if (!glow) sphere(bb, m, f.o, 0.75F * open, NAVY, 0.45F * fade);
        else sphere(bb, m, f.o, 0.9F * open, BLUE, 0.18F * fade);
        for (int arm = 0; arm < 3; arm++) {
            float base = arm * 120F - age * 38F;
            int segs = 18;
            for (int i = 0; i < segs; i++) {
                float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
                float g0 = (base + u0 * 210F) * Mth.DEG_TO_RAD, g1 = (base + u1 * 210F) * Mth.DEG_TO_RAD;
                float r0 = (0.35F + 0.75F * u0) * open, r1 = (0.35F + 0.75F * u1) * open;
                float w0 = 0.22F * Mth.sin((float) Math.PI * u0), w1 = 0.22F * Mth.sin((float) Math.PI * u1);
                Vec3 o0 = pa.scale(Mth.cos(g0)).add(pb.scale(Mth.sin(g0))), o1 = pa.scale(Mth.cos(g1)).add(pb.scale(Mth.sin(g1)));
                Vec3 p0 = f.o.add(o0.scale(r0)), p1 = f.o.add(o1.scale(r1)), q0 = f.o.add(o0.scale(r0 - w0)), q1 = f.o.add(o1.scale(r1 - w1));
                if (glow) quad(bb, m, p0, p1, f.o.add(o1.scale(r1 - w1 * 0.3F)), f.o.add(o0.scale(r0 - w0 * 0.3F)), arm == 0 ? MAGENTA : BLUE, 0.55F * fade, 0.55F * fade);
                else quad(bb, m, p0, p1, q1, q0, INK, 0.85F * fade, 0.85F * fade);
            }
        }
    }

    /** The puddle of dark aura under his feet: soft-edged, rippling, a faint violet rim. */
    private static void puddle(BufferBuilder bb, Matrix4f m, LivingEntity e, float amt, float now, float pt, boolean glow) {
        Vec3 c = e.getPosition(pt).add(0, 0.03, 0);
        float r = 0.55F + 0.85F * Mth.clamp(amt, 0F, 1F), a = Mth.clamp(amt, 0F, 1F);
        int segs = 28;
        for (int i = 0; i < segs; i++) {
            double g0 = Math.PI * 2 * i / segs, g1 = Math.PI * 2 * (i + 1) / segs;
            float r0 = r * (1F + 0.12F * Mth.sin((float) (g0 * 3) + now * 0.15F) + 0.06F * Mth.sin((float) (g0 * 7) - now * 0.2F));
            float r1 = r * (1F + 0.12F * Mth.sin((float) (g1 * 3) + now * 0.15F) + 0.06F * Mth.sin((float) (g1 * 7) - now * 0.2F));
            Vec3 e0 = new Vec3(Math.cos(g0), 0, Math.sin(g0)), e1 = new Vec3(Math.cos(g1), 0, Math.sin(g1));
            if (glow) {
                Vec3 i0 = c.add(e0.scale(r0 * 0.85F)), i1 = c.add(e1.scale(r1 * 0.85F)), o0 = c.add(e0.scale(r0 * 1.02F)), o1 = c.add(e1.scale(r1 * 1.02F));
                quad2(bb, m, i0, i1, o1, o0, VIOLET, 0F, 0.22F * a);
            } else {
                Vec3 mid0 = c.add(e0.scale(r0 * 0.6F)), mid1 = c.add(e1.scale(r1 * 0.6F)), o0 = c.add(e0.scale(r0)), o1 = c.add(e1.scale(r1));
                quad2(bb, m, c, c, mid1, mid0, INK, 0.72F * a, 0.6F * a);
                quad2(bb, m, mid0, mid1, o1, o0, INK, 0.6F * a, 0F);
            }
        }
    }

    /** The Kishi flooding along the floor: a dark ribbon along its path, spreading out at its front. */
    private static void wave(BufferBuilder bb, Matrix4f m, KishiWarpClient.Wave w, float now, boolean glow) {
        if (w.path.size() < 2) return;
        float fade = w.end < 0 ? 1F : 1F - smooth((now - w.end) / 30F);
        if (fade <= 0.01F) return;
        int n = w.path.size();
        for (int i = 0; i < n - 1; i++) {
            Vec3 a = w.path.get(i).add(0, 0.035, 0), b = w.path.get(i + 1).add(0, 0.035, 0);
            Vec3 d = b.subtract(a);
            if (d.lengthSqr() < 1e-4) continue;
            Vec3 side = d.cross(UP).normalize();
            float u0 = (float) i / (n - 1), u1 = (float) (i + 1) / (n - 1);
            float w0 = 0.35F + 0.35F * u0 + 0.12F * Mth.sin(now * 0.3F + i * 1.3F), w1 = 0.35F + 0.35F * u1 + 0.12F * Mth.sin(now * 0.3F + (i + 1) * 1.3F);
            if (glow) {
                quad(bb, m, a.add(side.scale(w0)), b.add(side.scale(w1)), b.add(side.scale(w1 * 1.15F)), a.add(side.scale(w0 * 1.15F)), VIOLET, 0.25F * fade, 0.25F * fade);
                quad(bb, m, a.subtract(side.scale(w0)), b.subtract(side.scale(w1)), b.subtract(side.scale(w1 * 1.15F)), a.subtract(side.scale(w0 * 1.15F)), VIOLET, 0.25F * fade, 0.25F * fade);
            } else quad(bb, m, a.add(side.scale(w0)), b.add(side.scale(w1)), b.subtract(side.scale(w1)), a.subtract(side.scale(w0)), INK, 0.78F * fade, 0.78F * fade);
        }
        // the front of it: a splash of dark puddle, and little spikes reaching up out of it
        Vec3 head = w.path.get(n - 1);
        disc(bb, m, head.add(0, 0.04, 0), 0.75F, fade, now, glow);
        // under him while he's knelt: the pool it comes out of
        disc(bb, m, w.path.get(0).add(0, 0.03, 0), 1.0F, fade, now, glow);
    }

    /** A soft-edged dark disc on the floor (rippling), a faint violet rim on the glow pass. */
    private static void disc(BufferBuilder bb, Matrix4f m, Vec3 c, float r, float a, float now, boolean glow) {
        if (a <= 0.01F) return;
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            double g0 = Math.PI * 2 * i / segs, g1 = Math.PI * 2 * (i + 1) / segs;
            float r0 = r * (1F + 0.12F * Mth.sin((float) (g0 * 3) + now * 0.15F)), r1 = r * (1F + 0.12F * Mth.sin((float) (g1 * 3) + now * 0.15F));
            Vec3 e0 = new Vec3(Math.cos(g0), 0, Math.sin(g0)), e1 = new Vec3(Math.cos(g1), 0, Math.sin(g1));
            if (glow) quad(bb, m, c.add(e0.scale(r0 * 0.85F)), c.add(e1.scale(r1 * 0.85F)), c.add(e1.scale(r1 * 1.03F)), c.add(e0.scale(r0 * 1.03F)), VIOLET, 0F, 0.22F * a);
            else {
                quad(bb, m, c, c, c.add(e1.scale(r1 * 0.6F)), c.add(e0.scale(r0 * 0.6F)), INK, 0.75F * a, 0.65F * a);
                quad(bb, m, c.add(e0.scale(r0 * 0.6F)), c.add(e1.scale(r1 * 0.6F)), c.add(e1.scale(r1)), c.add(e0.scale(r0)), INK, 0.65F * a, 0F);
            }
        }
    }

    /** Dazed stars circling a knocked-down head. */
    private static void stars(BufferBuilder bb, Matrix4f m, LivingEntity e, float w, float now, float pt, Vec3 camL, Vec3 camU) {
        Vec3 f = fwd(e);
        Vec3 head = e.getPosition(pt).subtract(f.scale(e.getBbHeight() * 0.85F * w)).add(0, 0.45F + e.getBbHeight() * (1F - w), 0);
        for (int i = 0; i < 3; i++) {
            double a = now * 0.22 + i * 2.094;
            Vec3 c = head.add(Math.cos(a) * 0.38, 0.12 * Math.sin(a * 2), Math.sin(a) * 0.38);
            star(bb, m, c, 0.13F, camL, camU, 0xFFD94A, 0.95F * w);
            star(bb, m, c, 0.06F, camL, camU, 0xFFFFFF, 0.9F * w);
        }
    }

    private static void star(BufferBuilder bb, Matrix4f m, Vec3 c, float r, Vec3 l, Vec3 u, int rgb, float a) {
        for (int k = 0; k < 5; k++) {
            double g = Math.PI / 2 + k * Math.PI * 2 / 5, h0 = g - Math.PI / 5, h1 = g + Math.PI / 5;
            Vec3 tip = c.add(l.scale(Math.cos(g) * r)).add(u.scale(Math.sin(g) * r));
            Vec3 s0 = c.add(l.scale(Math.cos(h0) * r * 0.42)).add(u.scale(Math.sin(h0) * r * 0.42));
            Vec3 s1 = c.add(l.scale(Math.cos(h1) * r * 0.42)).add(u.scale(Math.sin(h1) * r * 0.42));
            quad(bb, m, c, s0, tip, s1, rgb, a, a);
        }
    }

    // ------------------------------------------------------------------ primitives

    private static void quad(BufferBuilder bb, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int rgb, float a0, float a1) {
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        bb.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, a0).endVertex();
        bb.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, a0).endVertex();
        bb.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, a1).endVertex();
        bb.vertex(m, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, b, a1).endVertex();
    }

    /** A quad whose first two corners have alpha a0 and last two a1 (soft edges). */
    private static void quad2(BufferBuilder bb, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int rgb, float a0, float a1) {
        quad(bb, m, p0, p1, p2, p3, rgb, a0, a1);
    }

    private static void sphere(BufferBuilder bb, Matrix4f m, Vec3 c, float r, int rgb, float a) {
        if (a <= 0.01F || r <= 0.01F) return;
        int lat = 8, lon = 14;
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                sv(bb, m, c, r, t0, p0, cr, cg, cb, a);
                sv(bb, m, c, r, t0, p1, cr, cg, cb, a);
                sv(bb, m, c, r, t1, p1, cr, cg, cb, a);
                sv(bb, m, c, r, t1, p0, cr, cg, cb, a);
            }
        }
    }

    private static void sv(BufferBuilder bb, Matrix4f m, Vec3 c, float r, double t, double p, float cr, float cg, float cb, float a) {
        bb.vertex(m, (float) (c.x + Math.cos(t) * Math.cos(p) * r), (float) (c.y + Math.sin(t) * r), (float) (c.z + Math.cos(t) * Math.sin(p) * r))
                .color(cr, cg, cb, a).endVertex();
    }
}

package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.compat.Effeks;
import com.xenowarriors.network.UltShowPacket;
import com.xenowarriors.skill.UltShow;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The client half of UltShow: the caster's short cutscene (letterbox, the ultimate's name, three shots - close on them
 * as the power gathers, sweeping round them as it's let go, a wide pull-back over the hit) and everyone's effects, played
 * on the caster as it goes: power gathering in, then the release in the ultimate's style, then what lingers.
 * Uses AAA Particles + EpicFight Nightfall's effects and Lodestone's when they're there; our own particles always.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UltShowClient {
    private UltShowClient() {}

    private record Show(int entity, int style, int color, String name, float yaw, boolean cam, int[] age) {}
    private static final List<Show> SHOWS = new ArrayList<>();
    /** The caster's own cutscene (null when none). */
    private static Show mine;
    private static CameraType saved;
    /** (Tests) shows started; the screen flash. */
    public static int started;
    private static float flash;
    private static int flashColor;

    /** When the power is let go (ticks in). */
    static final int RELEASE = 14;

    public static void start(UltShowPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Show s = new Show(m.entity(), m.style(), m.color() & 0xFFFFFF, m.name(), m.yaw(), m.cam(), new int[]{0});
        SHOWS.add(s);
        started++;
        boolean me = m.cam() && m.entity() == mc.player.getId() && !UltimateCutscene.active() && !TeamUltCutscene.active();
        if (me) {
            if (mine == null) saved = mc.options.getCameraType();
            mine = s;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    public static boolean active() { return mine != null; }

    private static void stopMine(Minecraft mc) {
        if (mine == null) return;
        mine = null;
        if (saved != null) mc.options.setCameraType(saved);
        saved = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (flash > 0) flash = Math.max(0F, flash - 0.08F);
        if (SHOWS.isEmpty()) return;
        if (mc.level == null || mc.player == null) { SHOWS.clear(); mine = null; return; }
        if (mc.isPaused()) return;
        for (Show s : new ArrayList<>(SHOWS)) {
            Entity en = mc.level.getEntity(s.entity);
            int age = s.age[0]++;
            if (en == null || age > UltShow.LENGTH + 10) {
                SHOWS.remove(s);
                if (s == mine) stopMine(mc);
                continue;
            }
            if (s == mine && (age >= UltShow.LENGTH || !mc.player.isAlive())) stopMine(mc);
            try { fx(mc, mc.level, en, s, age); } catch (Throwable t) { SHOWS.remove(s); }
        }
    }

    // ------------------------------------------------------------------ effects

    private static Vector3f rgb(int c, float k) {
        return new Vector3f(Math.min(1F, ((c >> 16) & 255) / 255F * k), Math.min(1F, ((c >> 8) & 255) / 255F * k), Math.min(1F, (c & 255) / 255F * k));
    }

    private static String burstFor(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return r > b + 40 && r > g ? Effeks.BURST_RED : b > r + 30 ? Effeks.BURST_BLUE : Effeks.BURST;
    }

    private static void fx(Minecraft mc, Level l, Entity en, Show s, int age) {
        Vec3 at = en.position();
        Vec3 fwd = Vec3.directionFromRotation(0, s.yaw);
        var rnd = l.random;
        boolean lode = com.xenowarriors.client.compat.lodestone.LodestoneHooks.on();
        DustParticleOptions dust = new DustParticleOptions(rgb(s.color, 1F), 1.6F), bright = new DustParticleOptions(rgb(s.color, 1.5F), 2.2F);
        double near = mc.player.distanceToSqr(en);

        // gathering: power spiralling in, a charge glow at their feet
        if (age < RELEASE) {
            float k = age / (float) RELEASE;
            double rad = 3.2 * (1 - k) + 0.4;
            for (int i = 0; i < 6; i++) {
                double a = (age * 0.55) + i * Math.PI / 3;
                l.addParticle(dust, at.x + Math.cos(a) * rad, at.y + 0.2 + k * 1.6 + i * 0.12, at.z + Math.sin(a) * rad, 0, 0.02, 0);
            }
            if (age % 3 == 0) l.addParticle(ParticleTypes.END_ROD, at.x + (rnd.nextDouble() - .5) * 2.4, at.y + rnd.nextDouble() * 2, at.z + (rnd.nextDouble() - .5) * 2.4, 0, 0.05, 0);
            if (age == 0) {
                Effeks.play(l, Effeks.CHARGING, at, 1.4F, s.yaw, en);
                l.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.6F, false);
                if (lode) com.xenowarriors.client.compat.lodestone.LodestoneFx.aura(l, at.x, at.y + 1, at.z, s.color, 2F);
            }
            if (lode && age % 4 == 0) com.xenowarriors.client.compat.lodestone.LodestoneFx.spinRing(l, at.x, at.y + 0.1, at.z, s.color, k);
            return;
        }
        // the release
        if (age == RELEASE) {
            if (near < 24 * 24) { flash = s.entity == mc.player.getId() ? 0.85F : 0.45F; flashColor = s.color; }
            l.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.65F, false);
            l.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7F, 1.4F, false);
            if (lode) {
                com.xenowarriors.client.compat.lodestone.LodestoneFx.shockwave(l, at.x, at.y, at.z, s.color, 9F);
                if (near < 30 * 30) com.xenowarriors.client.compat.lodestone.LodestoneFx.shake((float) (0.9 * (1 - Math.sqrt(near) / 30)), 22);
            }
            l.addParticle(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 0, 0, 0);
        }
        int t = age - RELEASE;
        switch (s.style) {
            case UltShow.BURST -> {
                if (t == 0) { Effeks.play(l, burstFor(s.color), at.add(0, 1, 0), 2.6F, s.yaw); Effeks.play(l, Effeks.ROCK, at, 1.6F, s.yaw); }
                if (t < 12) ring(l, at.add(0, 0.15, 0), 1.5 + t * 0.9, 28, bright);
                if (t == 6) Effeks.play(l, Effeks.DISORDER, at.add(0, 1, 0), 1.8F, s.yaw);
            }
            case UltShow.SLASH -> {
                if (t % 4 == 0 && t < 16) {
                    float yaw = s.yaw + (t / 4 % 2 == 0 ? -35 : 35);
                    Effeks.play(l, Effeks.CLAW, at.add(fwd.scale(2)).add(0, 1.1, 0), 1.8F, yaw);
                    arc(l, at.add(0, 1.1, 0), fwd, 3.2, t / 4 % 2 == 0 ? 0.6 : -0.6, bright);
                    l.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.7F + t * 0.03F, false);
                }
                if (t == 16) Effeks.play(l, Effeks.DRAGON_FLASH, at.add(fwd.scale(3)).add(0, 1, 0), 1.6F, s.yaw);
            }
            case UltShow.BEAM -> {
                if (t < 14) {
                    Vec3 from = at.add(0, 1.3, 0);
                    for (int i = 1; i < 44; i++) {
                        Vec3 p = from.add(fwd.scale(i * 0.5));
                        double w = 0.25 + 0.15 * Math.sin(t + i * 0.4);
                        l.addParticle(i % 2 == 0 ? bright : dust, p.x + (rnd.nextDouble() - .5) * w, p.y + (rnd.nextDouble() - .5) * w, p.z + (rnd.nextDouble() - .5) * w, 0, 0, 0);
                        if (i % 6 == t % 6) l.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, fwd.x * 0.2, 0, fwd.z * 0.2);
                    }
                }
                if (t == 0) Effeks.play(l, Effeks.SPARK2, at.add(fwd.scale(1.2)).add(0, 1.3, 0), 1.6F, s.yaw);
                if (t == 6) { Vec3 end = at.add(fwd.scale(20)).add(0, 1, 0); Effeks.play(l, burstFor(s.color), end, 2.4F, s.yaw); l.addParticle(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 0, 0, 0); }
            }
            case UltShow.RAIN -> {
                if (t < 20 && t % 2 == 0) {
                    Vec3 hit = at.add(fwd.scale(4 + rnd.nextDouble() * 9)).add((rnd.nextDouble() - .5) * 9, 0, (rnd.nextDouble() - .5) * 9);
                    for (int y = 0; y < 14; y++) l.addParticle(y % 2 == 0 ? bright : dust, hit.x, hit.y + y * 0.9, hit.z, 0, -0.6, 0);
                    l.addParticle(ParticleTypes.EXPLOSION, hit.x, hit.y + 0.3, hit.z, 0, 0, 0);
                    if (t % 6 == 0) { Effeks.play(l, Effeks.SPARK, hit, 1.4F, s.yaw); l.playLocalSound(hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.2F, false); }
                }
            }
            case UltShow.SUMMON -> {
                if (t < 18) for (int k = 0; k < 5; k++) {
                    double a = k * Math.PI * 2 / 5 + t * 0.05;
                    Vec3 c = at.add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
                    l.addParticle(bright, c.x, c.y + t * 0.25, c.z, 0, 0.05, 0);
                    l.addParticle(ParticleTypes.PORTAL, c.x, c.y + 1, c.z, (rnd.nextDouble() - .5), 0.3, (rnd.nextDouble() - .5));
                }
                if (t == 0) Effeks.play(l, Effeks.BREAKOUT, at, 2F, s.yaw);
                if (t == 8) ring(l, at.add(0, 0.2, 0), 3.5, 40, bright);
            }
            default -> { // POWER: a pillar of light, flames wrapping them
                if (t < 22) for (int y = 0; y < 16; y++) {
                    double a = t * 0.4 + y * 0.5, r = 0.9 + 0.2 * Math.sin(t * 0.3);
                    l.addParticle(y % 3 == 0 ? ParticleTypes.END_ROD : bright, at.x + Math.cos(a) * r, at.y + y * 0.8, at.z + Math.sin(a) * r, 0, 0.08, 0);
                }
                if (t == 0) { Effeks.play(l, Effeks.FLAME2, at, 1.8F, s.yaw, en); Effeks.play(l, Effeks.BREAKOUT, at, 1.6F, s.yaw); }
                if (lode && t % 5 == 0 && t < 25) com.xenowarriors.client.compat.lodestone.LodestoneFx.aura(l, at.x, at.y + 1, at.z, s.color, 2.5F);
            }
        }
        // what lingers
        if (t > 10 && t < 30 && t % 3 == 0) {
            for (int i = 0; i < 4; i++) l.addParticle(dust, at.x + (rnd.nextDouble() - .5) * 6, at.y + rnd.nextDouble() * 0.5, at.z + (rnd.nextDouble() - .5) * 6, 0, 0.06, 0);
            if (lode && t % 9 == 0) com.xenowarriors.client.compat.lodestone.LodestoneFx.spinRing(l, at.x, at.y + 0.1, at.z, s.color, 1F);
        }
    }

    private static void ring(Level l, Vec3 c, double r, int n, DustParticleOptions d) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            l.addParticle(d, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, Math.cos(a) * 0.08, 0.01, Math.sin(a) * 0.08);
        }
    }

    /** A slash's arc in front: {@code tilt} leans it one way or the other. */
    private static void arc(Level l, Vec3 c, Vec3 fwd, double r, double tilt, DustParticleOptions d) {
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        for (int i = -10; i <= 10; i++) {
            double a = i / 10.0 * 1.2;
            Vec3 p = c.add(fwd.scale(Math.cos(a) * r)).add(side.scale(Math.sin(a) * r)).add(0, Math.sin(a) * tilt * r * 0.5, 0);
            l.addParticle(d, p.x, p.y, p.z, 0, 0, 0);
            if (i % 3 == 0) l.addParticle(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the caster's cutscene

    private static final int[] SHOTS = {0, RELEASE, 32};
    private static final int BLEND = 6;

    /** {x, y, z, yaw, pitch} for this frame, or null. */
    public static double[] camera(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mine == null || mc.player == null || mc.level == null) return null;
        float age = mine.age[0] + pt;
        int shot = age >= SHOTS[2] ? 2 : age >= SHOTS[1] ? 1 : 0;
        Vec3[] cur = shot(mc, shot, age, pt);
        if (shot > 0 && age - SHOTS[shot] < BLEND) {
            Vec3[] prev = shot(mc, shot - 1, age, pt);
            float w = smooth((age - SHOTS[shot]) / BLEND);
            cur = new Vec3[]{prev[0].lerp(cur[0], w), prev[1].lerp(cur[1], w)};
        }
        Vec3 pos = unstuck(mc, cur[0]);
        Vec3 d = cur[1].subtract(pos);
        double yaw = Math.toDegrees(Mth.atan2(-d.x, d.z));
        double pitch = -Math.toDegrees(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    private static Vec3[] shot(Minecraft mc, int shot, float age, float pt) {
        Vec3 body = mc.player.getPosition(pt);
        Vec3 head = body.add(0, 1.55, 0), chest = body.add(0, 1.1, 0);
        Vec3 fwd = Vec3.directionFromRotation(0, mine.yaw), side = new Vec3(-fwd.z, 0, fwd.x), up = new Vec3(0, 1, 0);
        switch (shot) {
            case 0 -> { // close and low in front of their face, slowly pushing in as the power gathers
                double push = age / RELEASE * 0.6;
                return new Vec3[]{head.add(fwd.scale(2.4 - push)).add(side.scale(0.7)).add(up.scale(-0.35)), head};
            }
            case 1 -> { // sweeping round them as it's let go
                double a = 0.9 - (age - RELEASE) / 18.0 * 1.6;
                Vec3 around = side.scale(Math.cos(a)).add(fwd.scale(Math.sin(a)));
                double r = 4.2 + (age - RELEASE) * 0.08;
                return new Vec3[]{chest.add(around.scale(r)).add(up.scale(0.6)), chest.add(fwd.scale(1.5))};
            }
            default -> { // a wide pull-back, behind and above, over the hit
                double k = smooth((age - SHOTS[2]) / 18F);
                Vec3 target = mine.style == UltShow.POWER ? chest : body.add(fwd.scale(mine.style == UltShow.BEAM ? 10 : 5)).add(0, 1, 0);
                return new Vec3[]{chest.subtract(fwd.scale(6 + 5 * k)).add(side.scale(2.5)).add(up.scale(2.5 + 3 * k)), target};
            }
        }
    }

    private static Vec3 unstuck(Minecraft mc, Vec3 pos) {
        for (int i = 0; i < 8; i++) {
            BlockPos bp = BlockPos.containing(pos);
            if (mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) return pos;
            pos = pos.add(0, 0.5, 0);
        }
        return pos;
    }

    /** Letterbox bars, the ultimate's name, and the flash. */
    public static void overlay(GuiGraphics g, float pt, int width, int height) {
        if (flash > 0.01F) {
            int a = Math.round(Math.min(1F, flash) * 150);
            g.fill(0, 0, width, height, (a << 24) | flashColor);
            g.fill(0, 0, width, height, (Math.round(flash * 90) << 24) | 0xFFFFFF);
        }
        if (mine == null) return;
        float age = mine.age[0] + pt;
        float bars = smooth(age / 6F) * (1F - smooth((age - (UltShow.LENGTH - 6)) / 6F));
        int bar = Math.round(height * 0.11F * bars);
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);
        g.fill(0, height - bar, Math.round(width * Mth.clamp(age / UltShow.LENGTH, 0F, 1F)), height - bar + 1, 0xFF000000 | mine.color);
        float title = Mth.clamp((age - 3F) / 5F, 0F, 1F) * (1F - Mth.clamp((age - (UltShow.LENGTH - 8)) / 6F, 0F, 1F));
        if (title > 0.02F) {
            int a = Math.round(title * 255) << 24;
            var font = Minecraft.getInstance().font;
            float slide = (1F - smooth((age - 3F) / 6F)) * 40F;
            g.pose().pushPose();
            g.pose().translate(24 - slide, height - bar - 36, 0);
            g.drawString(font, Component.translatable("hud.xenowarriors.ultimate"), 0, 0, (mine.color) | a);
            g.pose().scale(2.2F, 2.2F, 1F);
            g.drawString(font, Component.literal(mine.name).withStyle(st -> st.withBold(true)), 0, 6, 0xFFFFFF | a);
            g.pose().popPose();
        }
    }

    /** (Tests) a show of this style on the local player, as the server would send it. */
    public static void testStart(int style, int color, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) start(new UltShowPacket(mc.player.getId(), style, color, name, mc.player.getYRot(), true));
    }
}

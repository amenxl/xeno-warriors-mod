package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.DmzFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * DragonMineZ's ki particles for our attacks (compat/DmzFx sends them; KiFx adds them to the Xeno Blast). Found by
 * registry id (dragonminez:ki_trail, ki_shedding, ki_splash, ki_flash, ki_explosion_splash, ki_lightning) and tinted
 * through the particle's own colour setters (setKiColor / setSplashColor / setLightningColor), by reflection.
 */
public final class DmzKiParticles {
    private DmzKiParticles() {}

    private static final Map<String, ParticleOptions> TYPES = new HashMap<>();
    private static final Map<Class<?>, Method[]> SETTERS = new HashMap<>();     // {colour(fff), scale(f)}
    /** For the UI test: particles made so far. */
    public static int spawned;
    private static boolean warned;

    private static ParticleOptions type(String name) {
        return TYPES.computeIfAbsent(name, n -> {
            ParticleType<?> t = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation("dragonminez", n));
            return t instanceof ParticleOptions o ? o : null;          // (all of DragonMineZ's ki particles are simple types)
        });
    }

    private static Method[] setters(Class<?> c) {
        return SETTERS.computeIfAbsent(c, k -> {
            Method col = null, scale = null;
            for (String n : new String[]{"setKiColor", "setSplashColor", "setLightningColor"})
                try { col = k.getMethod(n, float.class, float.class, float.class); break; } catch (NoSuchMethodException ignored) {}
            for (String n : new String[]{"setKiScale", "setSplashScale", "setLightningScale"})
                try { scale = k.getMethod(n, float.class); break; } catch (NoSuchMethodException ignored) {}
            return new Method[]{col, scale};
        });
    }

    /** One DragonMineZ particle, tinted; false if it isn't there. */
    public static boolean spawn(String name, double x, double y, double z, double vx, double vy, double vz, int rgb, float scale) {
        Minecraft mc = Minecraft.getInstance();
        ParticleOptions t = type(name);
        if (t == null || mc.level == null) return false;
        try {
            Particle p = mc.particleEngine.createParticle(t, x, y, z, vx, vy, vz);
            if (p == null) return false;
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            Method[] s = setters(p.getClass());
            if (s[0] != null) s[0].invoke(p, r, g, b); else p.setColor(r, g, b);
            if (s[1] != null && scale > 0) s[1].invoke(p, scale);
            spawned++;
            return true;
        } catch (Throwable ex) {
            if (!warned) { warned = true; XenoWarriors.LOGGER.warn("DragonMineZ ki particle {} failed", name, ex); }
            return false;
        }
    }

    public static boolean available() { return type("ki_trail") != null; }

    public static void play(DmzFxPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !available()) return;
        RandomSource rnd = mc.level.random;
        Vec3 a = new Vec3(m.ax(), m.ay(), m.az());
        float s = m.size();
        switch (m.kind()) {
            case DmzFxPacket.BALL -> ball(a, new Vec3(m.bx(), m.by(), m.bz()), m.rgb(), s, rnd);
            case DmzFxPacket.BEAM -> beam(a, new Vec3(m.bx(), m.by(), m.bz()), m.rgb(), s, rnd);
            case DmzFxPacket.BURST -> burst(a, m.rgb(), s, rnd);
            default -> {}
        }
    }

    /** A ki ball: a dense bright core and a short streaming tail behind it. */
    public static void ball(Vec3 c, Vec3 dir, int rgb, float s, RandomSource rnd) {
        int core = lighter(rgb);
        for (int i = 0; i < 5; i++)
            spawn("ki_trail", c.x + rnd.nextGaussian() * 0.08 * s, c.y + rnd.nextGaussian() * 0.08 * s, c.z + rnd.nextGaussian() * 0.08 * s,
                    0, 0, 0, i < 2 ? core : rgb, 2.6F * s);
        for (int i = 0; i < 4; i++) {
            Vec3 q = c.subtract(dir.normalize().scale(0.35 * (i + 1)));
            spawn("ki_trail", q.x, q.y, q.z, -dir.x * 0.02, -dir.y * 0.02, -dir.z * 0.02, rgb, (1.8F - i * 0.3F) * s);
        }
        if (rnd.nextInt(3) == 0) spawn("ki_shedding", c.x, c.y, c.z, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, rgb, s);
    }

    /** One tick of a ki beam: a thick column of ki along it, with sparks shedding off. */
    public static void beam(Vec3 a, Vec3 b, int rgb, float w, RandomSource rnd) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.01) return;
        Vec3 u = d.scale(1 / len);
        int core = lighter(rgb);
        for (double t = 0; t < len; t += 0.7) {
            Vec3 q = a.add(u.scale(t));
            double j = 0.12 * w;
            spawn("ki_trail", q.x + rnd.nextGaussian() * j, q.y + rnd.nextGaussian() * j, q.z + rnd.nextGaussian() * j, u.x * 0.05, u.y * 0.05, u.z * 0.05, rgb, 3.2F * w);
            if (((int) (t / 0.7)) % 2 == 0) spawn("ki_trail", q.x, q.y, q.z, 0, 0, 0, core, 1.8F * w);
            if (rnd.nextInt(6) == 0)
                spawn("ki_shedding", q.x, q.y, q.z, rnd.nextGaussian() * 0.08, rnd.nextGaussian() * 0.08, rnd.nextGaussian() * 0.08, rgb, w);
        }
        if (rnd.nextInt(4) == 0) spawn("ki_lightning", b.x, b.y, b.z, 0, 0, 0, rgb, w);
        ball(b, u, rgb, w * 1.4F, rnd);          // the head where it hits
    }

    /** Ki going off: DragonMineZ's flash, splash wave and explosion splash, tinted. */
    public static void burst(Vec3 c, int rgb, float s, RandomSource rnd) {
        spawn("ki_flash", c.x, c.y, c.z, 0, 0, 0, lighter(rgb), s);
        spawn("ki_explosion_splash", c.x, c.y, c.z, 0, 0, 0, rgb, s);
        spawn("ki_splash_wave", c.x, c.y, c.z, 0, 0, 0, rgb, s);
        for (int i = 0; i < 6 + 4 * s; i++)
            spawn("ki_splash", c.x, c.y, c.z, rnd.nextGaussian() * 0.25 * s, rnd.nextGaussian() * 0.25 * s, rnd.nextGaussian() * 0.25 * s, rgb, s);
    }

    private static int lighter(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | (b + 255) / 2;
    }
}

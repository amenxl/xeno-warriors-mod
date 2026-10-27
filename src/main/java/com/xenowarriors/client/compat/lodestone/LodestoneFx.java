package com.xenowarriors.client.compat.lodestone;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import team.lodestar.lodestone.handlers.ScreenshakeHandler;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleRegistry;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
import team.lodestar.lodestone.systems.screenshake.ScreenshakeInstance;

import java.awt.Color;

/**
 * Lodestone effects (only touched when Lodestone is installed): glowing wisps and sparks for skills, ultimates,
 * karma auras, KAs and vehicles, plus screen shake on the big hits.
 */
public final class LodestoneFx {
    private LodestoneFx() {}

    private static Color c(int rgb) { return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255); }
    private static Color lighter(int rgb) { Color k = c(rgb); return new Color(Math.min(255, k.getRed() + 90), Math.min(255, k.getGreen() + 90), Math.min(255, k.getBlue() + 90)); }

    private static WorldParticleBuilder wisp(int rgb, float size, int life) {
        return WorldParticleBuilder.create(LodestoneParticleRegistry.WISP_PARTICLE)
                .setScaleData(GenericParticleData.create(size, size * 0.2F, 0F).build())
                .setTransparencyData(GenericParticleData.create(0.85F, 0F).build())
                .setColorData(ColorParticleData.create(lighter(rgb), c(rgb)).build())
                .setLifetime(life).enableNoClip();
    }

    private static WorldParticleBuilder spark(int rgb, float size, int life) {
        return WorldParticleBuilder.create(LodestoneParticleRegistry.SPARK_PARTICLE)
                .setScaleData(GenericParticleData.create(size, 0F).build())
                .setTransparencyData(GenericParticleData.create(1F, 0F).build())
                .setColorData(ColorParticleData.create(lighter(rgb), c(rgb)).build())
                .setLifetime(life);
    }

    public static void shake(float intensity, int ticks) {
        ScreenshakeHandler.addScreenshake(new ScreenshakeInstance(ticks).setIntensity(intensity, 0F));
    }

    /** A skill going off: a puff of glowing wisps and a few sparks flying out. */
    public static void burst(Level l, double x, double y, double z, int rgb, float scale) {
        wisp(rgb, 0.45F * scale, 18).setRandomOffset(0.4 * scale).setRandomMotion(0.08 * scale).repeat(l, x, y, z, (int) (10 * scale));
        spark(rgb, 0.25F * scale, 10).setRandomMotion(0.25 * scale).repeat(l, x, y, z, (int) (8 * scale));
    }

    /** An ultimate / big hit: an expanding ring on the ground, a bright column, screen shake. */
    public static void shockwave(Level l, double x, double y, double z, int rgb, float radius) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            wisp(rgb, 0.6F, 22).setMotion(Math.cos(a) * radius * 0.06, 0.01, Math.sin(a) * radius * 0.06).spawn(l, x, y + 0.2, z);
        }
        wisp(rgb, 1.2F, 16).setRandomOffset(0.3).setMotion(0, 0.25, 0).repeat(l, x, y, z, 12);
        spark(rgb, 0.4F, 14).setRandomMotion(0.4, 0.3, 0.4).repeat(l, x, y + 1, z, 20);
    }

    /** A karma aura around someone charging (or a demon's dark aura). */
    public static void aura(Level l, double x, double y, double z, int rgb, float strength) {
        wisp(rgb, 0.35F * strength, 14).setRandomOffset(0.45, 0.9, 0.45).setMotion(0, 0.06 * strength, 0).repeat(l, x, y + 1.0, z, Math.max(1, (int) (3 * strength)));
    }

    /** A KA activating: a ring of stars that spins out. */
    public static void kaRing(Level l, double x, double y, double z, int rgb) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            WorldParticleBuilder.create(LodestoneParticleRegistry.STAR_PARTICLE)
                    .setScaleData(GenericParticleData.create(0.35F, 0F).build())
                    .setTransparencyData(GenericParticleData.create(1F, 0F).build())
                    .setColorData(ColorParticleData.create(lighter(rgb), c(rgb)).build())
                    .setLifetime(20).setMotion(Math.cos(a) * 0.12, 0.02, Math.sin(a) * 0.12).spawn(l, x + Math.cos(a) * 0.6, y + 1.0, z + Math.sin(a) * 0.6);
        }
    }

    /** A spinning ring (Logan's Redirect while it's up). */
    public static void spinRing(Level l, double x, double y, double z, int rgb, float t) {
        for (int i = 0; i < 6; i++) {
            double a = t * 0.35 + i * Math.PI / 3;
            wisp(rgb, 0.25F, 8).spawn(l, x + Math.cos(a) * 1.3, y + 1.0 + Math.sin(a * 2) * 0.35, z + Math.sin(a) * 1.3);
        }
    }

    /** A glowing eye (Maddie's KA). */
    public static void eye(Level l, double x, double y, double z) {
        wisp(0xFF1010, 0.6F, 20).repeat(l, x, y, z, 4);
        spark(0xFF3030, 0.3F, 12).setRandomMotion(0.15).repeat(l, x, y, z, 6);
    }

    /** A trail behind a karma blast. */
    public static void trail(Level l, Vec3 at, int rgb) {
        wisp(rgb, 0.3F, 10).setRandomOffset(0.05).spawn(l, at.x, at.y, at.z);
    }

    public static void exhaust(Level l, double x, double y, double z, double speed) {
        WorldParticleBuilder.create(LodestoneParticleRegistry.SMOKE_PARTICLE)
                .setScaleData(GenericParticleData.create(0.25F, 0.6F).build())
                .setTransparencyData(GenericParticleData.create(0.45F, 0F).build())
                .setColorData(ColorParticleData.create(new Color(110, 110, 115), new Color(60, 60, 64)).build())
                .setLifetime(24).setRandomMotion(0.01, 0.02, 0.01).spawn(l, x, y, z);
    }

    public static void rotorWash(Level l, double x, double y, double z, double strength) {
        for (int i = 0; i < 4; i++) {
            double a = Math.random() * Math.PI * 2, r = 1.8 + Math.random() * 2.5;
            WorldParticleBuilder.create(LodestoneParticleRegistry.SMOKE_PARTICLE)
                    .setScaleData(GenericParticleData.create(0.3F, 0.8F).build())
                    .setTransparencyData(GenericParticleData.create((float) (0.35 * strength), 0F).build())
                    .setColorData(ColorParticleData.create(new Color(190, 180, 160), new Color(150, 140, 120)).build())
                    .setLifetime(20).setMotion(Math.cos(a) * 0.18, 0.01, Math.sin(a) * 0.18).spawn(l, x + Math.cos(a) * r, y, z + Math.sin(a) * r);
        }
    }
}

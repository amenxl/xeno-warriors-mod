package com.xenowarriors.client.mikiko;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Particles and sounds that make Mikiko's attacks and idle tricks pop. */
final class MikikoEffects {
    private MikikoEffects() {}

    /** The instant a move connects: slash arcs of fire (Hekublade) or sparks (sword), gusts for fists and kicks. */
    static void strike(ClientLevel level, Player p, Move move, int blade) {
        float yaw = p.getYHeadRot() * ((float) Math.PI / 180F);
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 chest = p.position().add(0, 1.15, 0);

        switch (move.kind) {
            case PUNCH -> {
                boolean left = move == Move.JAB_L || move == Move.UPPERCUT_L || move == Move.AIR_PUNCH_L;
                Vec3 fist = chest.add(fwd.scale(1.1)).add(right.scale(left ? -0.3 : 0.3)).add(0, move == Move.UPPERCUT_L ? 0.4 : 0, 0);
                for (int i = 0; i < 6; i++) MikikoAnimations.particle(level, ParticleTypes.CRIT, fist, rand(p, 0.25).add(fwd.scale(0.2)));
                for (int i = 0; i < 3; i++) MikikoAnimations.particle(level, ParticleTypes.CLOUD, fist, fwd.scale(0.25).add(rand(p, 0.05)));
                MikikoAnimations.sound(level, p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.4F, 1.5F);
            }
            case KICK -> {
                Vec3 foot = p.position().add(0, 0.8, 0).add(fwd.scale(1.1));
                for (int i = 0; i < 10; i++) {
                    double a = i / 10.0 * Math.PI * 2;
                    Vec3 ring = right.scale(Math.cos(a) * 0.35).add(0, Math.sin(a) * 0.35, 0);
                    MikikoAnimations.particle(level, ParticleTypes.CLOUD, foot.add(ring), fwd.scale(0.18).add(ring.scale(0.15)));
                }
                for (int i = 0; i < 5; i++) MikikoAnimations.particle(level, ParticleTypes.CRIT, foot, rand(p, 0.3));
                MikikoAnimations.sound(level, p, SoundEvents.PLAYER_ATTACK_STRONG, 0.45F, 1.3F);
            }
            default -> {
                slashArc(level, p, move.kind, chest, fwd, right, up, blade);
                MikikoAnimations.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, blade == 2 ? 0.9F : 1.25F);
                if (blade == 2) MikikoAnimations.sound(level, p, SoundEvents.BLAZE_SHOOT, 0.35F, 1.6F);
            }
        }
    }

    private static void slashArc(ClientLevel level, Player p, Move.Kind kind, Vec3 chest, Vec3 fwd, Vec3 right, Vec3 up, int blade) {
        int points = kind == Move.Kind.SLASH_SPIN || kind == Move.Kind.SLASH_FLIP ? 36 : 18;
        double r = 1.7;
        Vec3 mid = null;
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Vec3 at;
            switch (kind) {
                case SLASH_H -> { double a = Math.toRadians(80 - 160 * t); at = chest.add(fwd.scale(Math.cos(a) * r)).add(right.scale(-Math.sin(a) * r)); }
                case SLASH_DOWN -> { double a = Math.toRadians(75 - 120 * t); at = chest.add(fwd.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).add(right.scale(0.2)); }
                case SLASH_UP -> { double a = Math.toRadians(-45 + 120 * t); at = chest.add(fwd.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).add(right.scale(0.6 - 1.0 * t)); }
                case SLASH_SPIN -> { double a = Math.toRadians(360 * t); at = chest.add(fwd.scale(Math.cos(a) * r)).add(right.scale(Math.sin(a) * r)); }
                case THRUST -> at = chest.add(fwd.scale(0.6 + 1.8 * t)).add(right.scale(0.15));
                case SLASH_DIAG -> { double a = Math.toRadians(70 - 140 * t); at = chest.add(fwd.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r * 0.8)).add(right.scale(Math.sin(a) * r * 0.6)); }
                default -> { double a = Math.toRadians(90 - 360 * t); at = chest.add(fwd.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)); }
            }
            if (i == points / 2) mid = at;
            Vec3 drift = at.subtract(chest).normalize().scale(0.03);
            if (blade == 2) {
                MikikoAnimations.particle(level, ParticleTypes.FLAME, at, drift);
                if (i % 2 == 0) MikikoAnimations.particle(level, ParticleTypes.SMALL_FLAME, at, drift.scale(2));
            } else if (blade == 1) {
                MikikoAnimations.particle(level, ParticleTypes.CRIT, at, drift);
                if (i % 3 == 0) MikikoAnimations.particle(level, ParticleTypes.END_ROD, at, drift.scale(0.3));
            } else {
                MikikoAnimations.particle(level, ParticleTypes.CLOUD, at, drift);
            }
        }
        if (mid != null && kind != Move.Kind.THRUST) MikikoAnimations.particle(level, ParticleTypes.SWEEP_ATTACK, mid, Vec3.ZERO);
        if (blade == 2 && kind == Move.Kind.THRUST) {
            for (int i = 0; i < 8; i++) MikikoAnimations.particle(level, ParticleTypes.FLAME, chest.add(fwd.scale(2.4)), rand(p, 0.12));
        }
    }

    /** The Hekublade bursting into his hand in a swirl of fire, or vanishing in smoke. */
    static void blade(ClientLevel level, Player p, boolean summon) {
        float yaw = p.yBodyRot * ((float) Math.PI / 180F);
        Vec3 hand = p.position().add(0, 0.9, 0).add(new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(0.4));
        if (summon) {
            for (int i = 0; i < 14; i++) {
                double a = i / 14.0 * Math.PI * 2;
                Vec3 o = new Vec3(Math.cos(a) * 0.4, i * 0.05, Math.sin(a) * 0.4);
                MikikoAnimations.particle(level, ParticleTypes.FLAME, hand.add(o), o.scale(-0.1));
            }
            MikikoAnimations.sound(level, p, SoundEvents.FIRECHARGE_USE, 0.45F, 1.6F);
        } else {
            for (int i = 0; i < 8; i++) MikikoAnimations.particle(level, ParticleTypes.SMOKE, hand, rand(p, 0.05));
            for (int i = 0; i < 5; i++) MikikoAnimations.particle(level, ParticleTypes.SMALL_FLAME, hand, rand(p, 0.04));
            MikikoAnimations.sound(level, p, SoundEvents.FIRE_EXTINGUISH, 0.25F, 1.8F);
        }
    }

    // ---- idle fire trick ----

    static void fireStart(ClientLevel level, Player p) {
        Vec3 palm = MikikoAnimations.seatedPalm(p);
        for (int i = 0; i < 6; i++) MikikoAnimations.particle(level, ParticleTypes.FLAME, palm, rand(p, 0.03).add(0, 0.03, 0));
        MikikoAnimations.sound(level, p, SoundEvents.FIRECHARGE_USE, 0.25F, 1.8F);
    }

    /** A little flame orbiting and spinning above his palm, flaring and shrinking. */
    static void fireSpin(ClientLevel level, Player p, int t) {
        Vec3 palm = MikikoAnimations.seatedPalm(p).add(0, 0.25, 0);
        float life = t < 90 ? 1F : 1F - (t - 90) / 20F;
        double size = 0.14 * (0.7 + 0.3 * Math.sin(t * 0.3)) * life;
        for (int k = 0; k < 3; k++) {
            double a = t * 0.55 + k * Math.PI * 2 / 3;
            Vec3 at = palm.add(Math.cos(a) * size, Math.sin(t * 0.2 + k) * 0.05, Math.sin(a) * size);
            MikikoAnimations.particle(level, ParticleTypes.SMALL_FLAME, at, new Vec3(0, 0.01, 0));
        }
        if (t % 2 == 0 && life > 0.3F) MikikoAnimations.particle(level, ParticleTypes.FLAME, palm, new Vec3(0, 0.015, 0));
        if (t % 25 == 0) MikikoAnimations.sound(level, p, SoundEvents.FIRE_AMBIENT, 0.2F, 1.4F);
    }

    static void fireOut(ClientLevel level, Player p) {
        Vec3 palm = MikikoAnimations.seatedPalm(p).add(0, 0.25, 0);
        for (int i = 0; i < 6; i++) MikikoAnimations.particle(level, ParticleTypes.SMOKE, palm, rand(p, 0.02).add(0, 0.04, 0));
        MikikoAnimations.sound(level, p, SoundEvents.FIRE_EXTINGUISH, 0.2F, 1.9F);
    }

    private static Vec3 rand(Player p, double s) {
        var r = p.getRandom();
        return new Vec3((r.nextDouble() - 0.5) * 2 * s, (r.nextDouble() - 0.5) * 2 * s, (r.nextDouble() - 0.5) * 2 * s);
    }
}

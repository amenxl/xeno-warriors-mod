package com.xenowarriors.client.mikiko;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
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

    // ---- Hekublade summon: red, orange and white light spiralling up into his hand, then a burst ----
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.1F), 1.1F);
    private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.08F), 1.1F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1.0F, 0.97F, 0.92F), 0.9F);
    private static final DustParticleOptions[] STRANDS = {RED, ORANGE, WHITE};
    private static final java.util.Map<Integer, int[]> SUMMONS = new java.util.HashMap<>();   // id -> {start tick, length}

    /** quick = the short version used when the blade flashes in mid-combo. */
    static void startSummon(Player p, boolean quick) {
        SUMMONS.put(p.getId(), new int[]{p.tickCount, quick ? 5 : 11});
        XL.put(p.getId(), new int[]{p.tickCount + (quick ? 5 : 11), quick ? 1 : 0});
    }

    // ---- the little "XL" (AmenXL Mikiko) that pops up over his hand as the blade forms ----
    private static final java.util.Map<Integer, int[]> XL = new java.util.HashMap<>();   // id -> {show tick, quick}
    private static final int XL_LIFE = 28;

    static void renderXl(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (XL.isEmpty()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) { XL.clear(); return; }
        float partial = event.getPartialTick();
        var cam = event.getCamera();
        var buffers = mc.renderBuffers().bufferSource();
        var font = mc.font;
        var it = XL.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!(mc.level.getEntity(e.getKey()) instanceof Player p)) { it.remove(); continue; }
            float age = p.tickCount + partial - e.getValue()[0];
            if (age > XL_LIFE) { it.remove(); continue; }
            if (age < 0) continue;
            boolean quick = e.getValue()[1] == 1;
            float pop = age < 4 ? 1.6F - 0.6F * (age / 4F) : 1F;                  // pops in big, settles
            float fade = Mth.clamp((XL_LIFE - age) / 10F, 0F, 1F);
            Vec3 at = rightHand(p).add(0, 0.38 + age * 0.016, 0).subtract(cam.getPosition());
            float scale = (quick ? 0.014F : 0.02F) * pop;
            var pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(cam.rotation());
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.sin(age * 0.5F) * 4F));
            pose.scale(-scale, -scale, scale);
            int alpha = Math.max(8, Math.round(fade * 255)) << 24;
            float w = font.width("XL");
            var x = net.minecraft.network.chat.Component.literal("X").withStyle(s -> s.withBold(true)).getVisualOrderText();
            var l = net.minecraft.network.chat.Component.literal("L").withStyle(s -> s.withBold(true)).getVisualOrderText();
            int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            font.drawInBatch8xOutline(x, -w / 2F, -4, 0xE81E1E | alpha, 0xFFF4E8 | alpha, pose.last().pose(), buffers, light);
            font.drawInBatch8xOutline(l, -w / 2F + font.width("X") + 1, -4, 0xFF8A12 | alpha, 0xFFF4E8 | alpha, pose.last().pose(), buffers, light);
            pose.popPose();
        }
        buffers.endBatch();
    }

    static void tickSummons(ClientLevel level) {
        var it = SUMMONS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!(level.getEntity(e.getKey()) instanceof Player p)) { it.remove(); continue; }
            int len = e.getValue()[1];
            int t = p.tickCount - e.getValue()[0];
            if (t > len) { it.remove(); continue; }
            Vec3 hand = rightHand(p);
            if (t < len) {
                // three strands coiling up from his feet and tightening onto his hand
                float k = t / (float) len;
                for (int sub = 0; sub < 3; sub++) {
                    float kk = k + sub / (3F * len);
                    double radius = 1.05 * (1 - kk) + 0.08;
                    for (int s = 0; s < 3; s++) {
                        double a = kk * Math.PI * 3.2 + s * Math.PI * 2 / 3;
                        Vec3 c = p.position().add(0, 0.1 + kk * (hand.y - p.getY() - 0.1), 0);
                        Vec3 at = new Vec3(c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius).lerp(hand, kk * kk * 0.6);
                        MikikoAnimations.particle(level, STRANDS[s], at, Vec3.ZERO);
                    }
                }
                if (t % 2 == 0) MikikoAnimations.particle(level, ParticleTypes.SMALL_FLAME, hand, rand(p, 0.04));
            } else {
                // the blade forms: white flash, red/orange burst, sparks and a ring rolling outward
                for (int i = 0; i < 26; i++) {
                    Vec3 v = rand(p, 1).normalize().scale(0.12 + p.getRandom().nextDouble() * 0.18);
                    MikikoAnimations.particle(level, STRANDS[i % 3], hand.add(v.scale(2)), v);
                }
                for (int i = 0; i < 10; i++) MikikoAnimations.particle(level, ParticleTypes.END_ROD, hand, rand(p, 0.16));
                for (int i = 0; i < 8; i++) MikikoAnimations.particle(level, ParticleTypes.FLAME, hand, rand(p, 0.09));
                MikikoAnimations.particle(level, ParticleTypes.FLASH, hand, Vec3.ZERO);
                if (len > 6) {
                    for (int i = 0; i < 24; i++) {
                        double a = i / 24.0 * Math.PI * 2;
                        MikikoAnimations.particle(level, ParticleTypes.END_ROD, p.position().add(0, 0.1, 0),
                                new Vec3(Math.cos(a) * 0.22, 0.01, Math.sin(a) * 0.22));
                    }
                }
            }
        }
    }

    private static Vec3 rightHand(Player p) {
        float yaw = p.yBodyRot * ((float) Math.PI / 180F);
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return p.position().add(0, 0.95, 0).add(right.scale(0.38)).add(fwd.scale(0.2));
    }

    /** The Hekublade flashing into his hand mid-combo, or vanishing in smoke. */
    static void blade(ClientLevel level, Player p, boolean summon) {
        Vec3 hand = rightHand(p);
        if (summon) {
            startSummon(p, true);
            level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.45F, 1.25F, false);
        } else {
            for (int i = 0; i < 8; i++) MikikoAnimations.particle(level, ParticleTypes.SMOKE, hand, rand(p, 0.05));
            for (int i = 0; i < 5; i++) MikikoAnimations.particle(level, ParticleTypes.SMALL_FLAME, hand, rand(p, 0.04));
            MikikoAnimations.sound(level, p, SoundEvents.FIRE_EXTINGUISH, 0.25F, 1.8F);
        }
    }

    /** A burst of wind (and a few embers) under his feet as he kicks off the air. */
    static void doubleJump(ClientLevel level, Player p) {
        Vec3 feet = p.position();
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            MikikoAnimations.particle(level, ParticleTypes.CLOUD, feet.add(dir.scale(0.3)), dir.scale(0.18).add(0, -0.03, 0));
        }
        for (int i = 0; i < 5; i++) MikikoAnimations.particle(level, ParticleTypes.SMALL_FLAME, feet, rand(p, 0.08));
        MikikoAnimations.particle(level, ParticleTypes.SWEEP_ATTACK, feet.add(0, 0.1, 0), Vec3.ZERO);
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

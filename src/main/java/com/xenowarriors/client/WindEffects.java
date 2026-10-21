package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;

/** Mikiko is a wind user: while he's flying, gusts of wind swirl around him. */
public final class WindEffects {
    private static final float TWO_PI = (float) (Math.PI * 2);

    private WindEffects() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer player : mc.level.players()) {
            FlightTracker.tick(player);
            if (player.isInvisible() || ClientCharacterData.get(player) != CharacterType.MIKIKO) continue;
            if (!FlightTracker.isFlying(player)) continue;
            // don't fill your own screen with wind in first person
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            spawnSwirl(mc, player, firstPerson);
        }
    }

    private static void spawnSwirl(Minecraft mc, AbstractClientPlayer p, boolean firstPerson) {
        float t = p.tickCount;
        int streams = firstPerson ? 1 : 3;
        for (int i = 0; i < streams; i++) {
            float angle = t * 0.45F + i * (TWO_PI / streams);
            double radius = firstPerson ? 1.4 : 0.85 + 0.1 * Mth.sin(t * 0.2F + i);
            double height = ((t * 0.08 + i * 0.6) % 2.0);
            double x = p.getX() + Mth.cos(angle) * radius;
            double y = p.getY() + 0.1 + height;
            double z = p.getZ() + Mth.sin(angle) * radius;
            // drift sideways around the body (tangent to the circle) and slightly upward
            double vx = -Mth.sin(angle) * 0.14;
            double vz = Mth.cos(angle) * 0.14;
            mc.level.addParticle(ParticleTypes.CLOUD, x, y, z, vx, 0.02, vz);
        }
        // a gust ring under his feet every so often
        if (!firstPerson && p.tickCount % 6 == 0) {
            for (int i = 0; i < 10; i++) {
                float a = i * TWO_PI / 10;
                mc.level.addParticle(ParticleTypes.POOF, p.getX() + Mth.cos(a) * 0.6, p.getY() - 0.1,
                        p.getZ() + Mth.sin(a) * 0.6, Mth.cos(a) * 0.08, -0.02, Mth.sin(a) * 0.08);
            }
        }
    }
}

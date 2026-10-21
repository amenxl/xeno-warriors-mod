package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/** Mikiko is a wind user: while he's flying, a soft wind follows him. */
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

    private static final net.minecraft.core.particles.DustParticleOptions WISP =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.93F, 0.96F, 1.0F), 0.45F);

    /**
     * Soft, quiet wind: a few pale wisps left trailing behind him as he flies (more the faster he goes),
     * and the occasional fleck drifting up under his feet while he hovers. Nothing big or flashy.
     */
    private static void spawnSwirl(Minecraft mc, AbstractClientPlayer p, boolean firstPerson) {
        double speed = Math.sqrt(Math.pow(p.getX() - p.xo, 2) + Math.pow(p.getY() - p.yo, 2) + Math.pow(p.getZ() - p.zo, 2));
        var r = p.getRandom();
        if (speed > 0.15 && !firstPerson) {
            int n = speed > 0.6 ? 2 : 1;
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * TWO_PI;
                mc.level.addParticle(WISP, p.getX() + Mth.cos((float) a) * 0.5, p.getY() + 0.2 + r.nextDouble() * 1.6,
                        p.getZ() + Mth.sin((float) a) * 0.5, 0, 0, 0);
            }
        } else if (p.tickCount % 5 == 0) {
            double a = r.nextDouble() * TWO_PI;
            mc.level.addParticle(WISP, p.getX() + Mth.cos((float) a) * 0.45, p.getY() - 0.15,
                    p.getZ() + Mth.sin((float) a) * 0.45, 0, 0.02, 0);
        }
    }
}

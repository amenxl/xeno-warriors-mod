package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.entity.HekubladeShardEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mikiko before he can fly: with a strong enough Hekublade (blade level {@link #BLADE_LEVEL}+) he jumps three times -
 * ground, double jump, and on the third the Hekublade appears under his feet and he rides it (Hekublade Scatter's
 * blade-surf, as long as his blade level allows). Once he learns to fly (see WindFlight) this is gone.
 */
public final class BladeRideJump {
    public static final int BLADE_LEVEL = 30;
    private static final int COOLDOWN = 20;
    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();

    private BladeRideJump() {}

    public static boolean able(ServerPlayer p) {
        return ModCapabilities.getType(p) == CharacterType.MIKIKO && BladeLevel.level(p) >= BLADE_LEVEL
                && !WindFlight.canFly(p) && !p.getAbilities().mayfly;
    }

    /** The third jump: summon a blade under him and ride it. */
    public static boolean ride(ServerPlayer p) {
        if (!able(p) || p.onGround() || p.isPassenger() || p.isInWater() || p.isFallFlying() || p.isSpectator()) return false;
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN && now >= last) return false;
        LAST.put(p.getUUID(), now);
        HekubladeShardEntity blade = HekubladeShardEntity.create(p, 0);
        p.level().addFreshEntity(blade);
        blade.surf(p);
        p.fallDistance = 0F;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.1F);
        XenoWarriors.LOGGER.info("XW-BLADERIDE {} rides the Hekublade (blade level {}, {} ticks)", p.getName().getString(),
                BladeLevel.level(p), blade.rideMax());
        return true;
    }
}

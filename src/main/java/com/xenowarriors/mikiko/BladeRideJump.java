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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeRideJump {
    public static final int BLADE_LEVEL = 30;
    private static final int COOLDOWN = 20;
    private static final java.util.Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();
    /** Rode since they last stood on the ground: no second ride until they land (the packet alone could chain rides into flight). */
    private static final java.util.Set<java.util.UUID> AIRBORNE = new java.util.HashSet<>();
    /** (tests) rides refused because they hadn't landed since the last one. */
    public static int refusedAirborne;

    private BladeRideJump() {}

    public static boolean able(ServerPlayer p) {
        return ModCapabilities.getType(p) == CharacterType.MIKIKO && BladeLevel.level(p) >= BLADE_LEVEL
                && !WindFlight.canFly(p) && !p.getAbilities().mayfly;
    }

    /** The third jump: summon a blade under him and ride it. */
    public static boolean ride(ServerPlayer p) {
        if (!able(p) || !p.isAlive() || p.onGround() || p.isPassenger() || p.isInWater() || p.isFallFlying() || p.isSpectator()) return false;
        if (com.xenowarriors.combat.Grounded.is(p) || com.xenowarriors.party.Duels.cantAct(p)) return false;     // (legs broken / knocked out)
        if (AIRBORNE.contains(p.getUUID())) { refusedAirborne++; return false; }
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN && now >= last) return false;
        LAST.put(p.getUUID(), now);
        AIRBORNE.add(p.getUUID());
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

    /** Landed (or in water, or dead): the next triple jump may ride again. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || AIRBORNE.isEmpty() || !(event.player instanceof ServerPlayer p)) return;
        if (p.onGround() || p.isInWater() || !p.isAlive()) AIRBORNE.remove(p.getUUID());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
        AIRBORNE.remove(event.getEntity().getUUID());
    }

    /** (tests) forget who rode. */
    public static void resetForTest() { LAST.clear(); AIRBORNE.clear(); }
}

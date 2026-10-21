package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PoisonStatePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mikiko's bare-handed combo does real damage: punch, punch, kick, then three Hekublade slashes
 * (the blade is summoned for a moment, so those burn). Matches the client animation's order.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikikoCombat {
    public static final int COMBO_LENGTH = 6;
    public static final int COMBO_TIMEOUT = 30;   // ticks between hits before the combo resets
    private static final float[] DAMAGE = {3F, 3F, 4.5F, 6F, 6F, 7F};

    private static final Map<UUID, int[]> COMBO = new HashMap<>();   // {step, last hit tick}
    private static final Map<UUID, Integer> STATUS = new HashMap<>();

    private MikikoCombat() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player) || event.getSource().getEntity() != player) return;
        if (ModCapabilities.getType(player) != CharacterType.MIKIKO || !player.getMainHandItem().isEmpty()) return;
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;

        // only fully charged punches count toward the combo (spam-clicking does normal weak hits)
        if (event.getAmount() < 0.85F) return;
        int now = player.tickCount;
        int[] c = COMBO.computeIfAbsent(player.getUUID(), k -> new int[]{0, -1000});
        if (now - c[1] > COMBO_TIMEOUT) c[0] = 0;
        int step = c[0];
        c[0] = (step + 1) % COMBO_LENGTH;
        c[1] = now;

        // vanilla already scaled the fist's 1 damage by attack charge (and crits); keep that scaling
        float charge = Math.max(0.25F, event.getAmount());
        event.setAmount(DAMAGE[step] * charge);
        LivingEntity target = event.getEntity();
        Vec3 dir = target.position().subtract(player.position()).normalize();
        if (step == 2) target.push(dir.x * 0.5, 0.25, dir.z * 0.5);          // the kick
        if (step >= 3) {
            target.setSecondsOnFire(2);                                         // Hekublade slashes burn
            if (player.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        8, 0.25, 0.3, 0.25, 0.04);
            }
        }
        if (step == COMBO_LENGTH - 1) target.push(dir.x * 0.8, 0.35, dir.z * 0.8);  // finisher
    }

    /** Count up Mikiko's time in the air, and share it so everyone sees how well he flies. */
    @SubscribeEvent
    public static void onFlightTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) return;
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if ((p.getAbilities().flying || p.isFallFlying()) && d.flightTicks < 1_000_000) d.flightTicks++;
            if (p.tickCount % 40 == 0) ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.FlightSkillPacket(p.getId(), d.flightTicks), p);
        });
    }

    /** Fire is his element - flames and burning only do 40% damage to him. */
    @SubscribeEvent
    public static void onFireHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player p && ModCapabilities.getType(p) == CharacterType.MIKIKO
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            event.setAmount(event.getAmount() * 0.4F);
        }
    }

    /** Mikiko always lands on his feet - no fall damage. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player p && ModCapabilities.getType(p) == CharacterType.MIKIKO) {
            event.setDamageMultiplier(0F);
        }
    }

    /** Tell nearby players when a character is poisoned/weakened (poison pose, sick face). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        if (ModCapabilities.getType(player) == CharacterType.NONE) return;
        int flags = (player.hasEffect(MobEffects.POISON) ? PoisonStatePacket.POISON : 0)
                | (player.hasEffect(MobEffects.WEAKNESS) ? PoisonStatePacket.WEAKNESS : 0);
        Integer before = STATUS.put(player.getUUID(), flags);
        // send on change, and every 2s while affected so players who walk up see it too
        if (before == null || before != flags || (flags != 0 && player.tickCount % 40 == 0)) {
            ModNetwork.sendToTrackingAndSelf(new PoisonStatePacket(player.getId(), flags), player);
        }
    }
}

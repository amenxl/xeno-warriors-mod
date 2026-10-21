package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.StatLevelPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Earning stat XP, levelling up, and applying the bonuses. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Stats {
    private static final UUID SPEED_ID = UUID.fromString("6f3c1f0e-8a3b-4c51-9a1e-5b7d2a9c0e11");
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();

    private Stats() {}

    public static int level(ServerPlayer player, Stat stat) {
        return player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.statLevel[stat.ordinal()]).orElse(0);
    }

    /** Adds XP and handles level ups (with a notification). */
    public static void addXp(ServerPlayer player, Stat stat, float amount) {
        if (amount <= 0 || player.isCreative() || player.isSpectator()) return;
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            int i = stat.ordinal();
            if (d.statLevel[i] >= Stat.MAX_LEVEL) return;
            d.statXp[i] += amount;
            boolean leveled = false;
            while (d.statLevel[i] < Stat.MAX_LEVEL && d.statXp[i] >= stat.needed(d.statLevel[i])) {
                d.statXp[i] -= stat.needed(d.statLevel[i]);
                d.statLevel[i]++;
                leveled = true;
            }
            if (leveled) {
                if (d.statLevel[i] >= Stat.MAX_LEVEL) d.statXp[i] = 0;
                ModNetwork.sendToPlayer(new StatLevelPacket(i, d.statLevel[i]), player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.5F, 1.4F);
                if (stat == Stat.SPEED) applySpeed(player, d);
            }
        });
    }

    private static void applySpeed(ServerPlayer player, PlayerCharacter d) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_ID);
        int lv = d.statLevel[Stat.SPEED.ordinal()];
        if (lv > 0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Xeno Warriors running level",
                    lv * Stat.SPEED.bonusPerLevel, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private static void reapply(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> applySpeed(player, d));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) reapply(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) reapply(p);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) reapply(p);
    }

    /** Running: XP for every block sprinted on the ground. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        double[] last = LAST_POS.get(p.getUUID());
        LAST_POS.put(p.getUUID(), new double[]{p.getX(), p.getZ()});
        if (last == null || !p.isSprinting() || !p.onGround() || p.isPassenger()) return;
        double dx = p.getX() - last[0], dz = p.getZ() - last[1];
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.5) addXp(p, Stat.SPEED, (float) dist);   // ignore teleports
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POS.remove(event.getEntity().getUUID());
    }

    /** Attack and defense: bonuses applied to every hit, and XP for dealing/taking damage. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        Entity source = event.getSource().getEntity();
        float amount = event.getAmount();

        if (source instanceof ServerPlayer attacker && attacker != target) {
            amount *= 1F + level(attacker, Stat.ATTACK) * Stat.ATTACK.bonusPerLevel;
            addXp(attacker, Stat.ATTACK, Math.min(amount, target.getHealth()));
        }
        if (target instanceof ServerPlayer defender && source instanceof LivingEntity && source != defender) {
            amount *= 1F - level(defender, Stat.DEFENSE) * Stat.DEFENSE.bonusPerLevel;
            addXp(defender, Stat.DEFENSE, amount);
        }
        event.setAmount(amount);
    }
}

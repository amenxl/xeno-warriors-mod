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
    private static final UUID HEALTH_ID = UUID.fromString("6f3c1f0e-8a3b-4c51-9a1e-5b7d2a9c0e12");
    /** Character Level = Speed + Attack + Defense levels (each up to 50), so 0-150. */
    public static final int MAX_CHARACTER_LEVEL = 88888;

    public static int characterLevel(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_CHARACTER)
                .map(d -> Math.max(1, fromStats(d.statLevel[0] + d.statLevel[1] + d.statLevel[2]))).orElse(1);   // everyone starts at level 1
    }

    /** How far toward the next level (average of the three stats' XP bars, 0..1). */
    private static float progress(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> {
            float sum = 0;
            int n = 0;
            for (Stat s : Stat.values()) {
                int i = s.ordinal();
                if (d.statLevel[i] >= Stat.MAX_LEVEL) continue;
                sum += Math.min(1F, d.statXp[i] / s.needed(d.statLevel[i]));
                n++;
            }
            return n == 0 ? 1F : sum / n;
        }).orElse(0F);
    }

    /** +1 heart (2 health) for every 10 Character Levels. */
    /** All three stats maxed is exactly Character Level 88,888 (otherwise it's just the three levels added up). */
    public static int fromStats(int sum) {
        int top = Stat.MAX_LEVEL * 3;     // 90,000 stat levels = Character Level 88,888
        if (sum >= top) return MAX_CHARACTER_LEVEL;
        return (int) Math.min(MAX_CHARACTER_LEVEL - 1, Math.round(sum * (double) MAX_CHARACTER_LEVEL / top));
    }

    /** +1 heart every 10 levels up to level 1,000, then +1 heart every 1,000 levels (about 188 at the cap). */
    public static int bonusHearts(int level) {
        return level <= 1000 ? level / 10 : 100 + (level - 1000) / 1000;
    }

    private static void applyHealth(ServerPlayer player) {
        AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        hp.removeModifier(HEALTH_ID);
        int hearts = bonusHearts(characterLevel(player));
        if (hearts > 0) {
            hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, "Xeno Warriors character level", hearts * 2.0,
                    AttributeModifier.Operation.ADDITION));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** All stat levels + the Character Level to the client (after switching characters). */
    public static void syncAll(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            for (int i = 0; i < 3; i++) ModNetwork.sendToPlayer(new StatLevelPacket(i, d.statLevel[i]), player);
        });
        syncLevel(player);
    }

    public static void syncLevel(ServerPlayer player) {
        ModNetwork.sendToPlayer(new com.xenowarriors.network.CharacterLevelPacket(characterLevel(player), progress(player), level(player, Stat.SPEED)), player);
    }
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();

    private Stats() {}

    public static int level(ServerPlayer player, Stat stat) {
        return player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.statLevel[stat.ordinal()]).orElse(0);
    }

    /** Adds XP and handles level ups (with a notification). */
    public static void addXp(ServerPlayer player, Stat stat, float amount) {
        if (amount <= 0 || player.isCreative() || player.isSpectator()) return;
        int before = characterLevel(player);
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
        int after = characterLevel(player);
        if (after != before) {
            applyHealth(player);
            syncLevel(player);
            if (after > before) com.xenowarriors.network.PopupPacket.send(player, com.xenowarriors.network.PopupPacket.GOOD, player.getUUID(), ModCapabilities.getType(player).ordinal(), -1, "Level up!",
                    "Character Level " + after + (bonusHearts(after) > bonusHearts(before) ? "  +1 max heart" : ""), "normal");
            if (bonusHearts(after) > bonusHearts(before)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Character Level " + after + "! +1 max heart")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                player.heal(2F);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    private static void applySpeed(ServerPlayer player, PlayerCharacter d) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_ID);
        int lv = d.statLevel[Stat.SPEED.ordinal()];
        if (lv > 0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, "Xeno Warriors running level",
                    Stat.SPEED.bonus(lv), AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    public static void reapply(ServerPlayer player) {
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> applySpeed(player, d));
        applyHealth(player);
        syncLevel(player);
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
        if (p.tickCount % 40 == 0) syncLevel(p);
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
            amount *= 1F + Stat.ATTACK.bonus(level(attacker, Stat.ATTACK));
            addXp(attacker, Stat.ATTACK, Math.min(amount, target.getHealth()));
        }
        if (target instanceof ServerPlayer defender && source instanceof LivingEntity && source != defender) {
            amount *= 1F - Stat.DEFENSE.bonus(level(defender, Stat.DEFENSE));
            addXp(defender, Stat.DEFENSE, amount);
        }
        event.setAmount(amount);
    }
}

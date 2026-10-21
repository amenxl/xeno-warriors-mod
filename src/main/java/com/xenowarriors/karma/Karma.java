package com.xenowarriors.karma;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.network.KarmaSyncPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Karma, the energy people in the show turn into their elements and abilities.
 * Every skill costs Karma (on top of its cooldown). It refills slowly on its own, much faster
 * while you crouch and stand still to focus, all the way after a night's sleep, and with
 * Karma Potions.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Karma {
    public static final float MAX = 100F;
    /** Per second. */
    public static final float REGEN = 1.5F;
    public static final float FOCUS_REGEN = 6F;

    private Karma() {}

    public static float get(ServerPlayer player) {
        return player.getCapability(ModCapabilities.PLAYER_CHARACTER).map(PlayerCharacter::getKarma).orElse(0F);
    }

    public static boolean has(ServerPlayer player, float amount) {
        return player.isCreative() || get(player) >= amount;
    }

    /** Takes Karma if the player has enough; otherwise tells them they're out and returns false. */
    public static boolean trySpend(ServerPlayer player, float amount) {
        if (player.isCreative()) return true;
        if (!has(player, amount)) {
            notEnough(player, amount);
            return false;
        }
        add(player, -amount);
        return true;
    }

    public static void notEnough(ServerPlayer player, float amount) {
        player.displayClientMessage(Component.translatable("message.xenowarriors.no_karma",
                Math.round(amount), Math.round(get(player))), true);
    }

    public static void add(ServerPlayer player, float amount) {
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            data.setKarma(data.getKarma() + amount);
            sync(player, data);
        });
    }

    public static void sync(ServerPlayer player, PlayerCharacter data) {
        data.lastSyncedKarma = data.getKarma();
        ModNetwork.sendToPlayer(new KarmaSyncPacket(data.getKarma(), MAX), player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            if (data.getType() == CharacterType.NONE || !player.isAlive()) return;
            boolean focusing = player.isShiftKeyDown() && player.onGround()
                    && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4;
            if (data.getKarma() < MAX) {
                data.setKarma(data.getKarma() + (focusing ? FOCUS_REGEN : REGEN) / 20F);
            }
            // send to the HUD when it has changed by a whole point, or right away the first time
            if (data.lastSyncedKarma < 0 || Math.abs(data.getKarma() - data.lastSyncedKarma) >= 1F
                    || (data.getKarma() >= MAX && data.lastSyncedKarma < MAX)) {
                sync(player, data);
            }
        });
    }

    /** "I couldn't get any sleep" - a full night's sleep restores all your Karma. */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.wakeImmediately()
                && player.level().isDay()) {
            player.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
                data.setKarma(MAX);
                sync(player, data);
            });
        }
    }
}

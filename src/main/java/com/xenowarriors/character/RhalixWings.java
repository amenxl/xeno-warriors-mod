package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.WingsPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rhalix can put his wings away (no flying) and take them out again. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RhalixWings {
    private static final String LAST = "xenowarriors_wings_toggle";

    private RhalixWings() {}

    public static boolean out(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(c -> c.wingsOut).orElse(true);
    }

    public static void toggle(ServerPlayer p) {
        if (!ModCapabilities.getType(p).skylander()) return;
        long now = p.level().getGameTime();
        if (now - p.getPersistentData().getLong(LAST) < 30) return;     // let the animation finish
        p.getPersistentData().putLong(LAST, now);
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(c -> c.wingsOut = !c.wingsOut);
        boolean out = out(p);
        var abilities = p.getAbilities();
        if (!out && !p.isCreative() && !p.isSpectator()) {
            if (abilities.flying || !p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
            abilities.flying = false;
            abilities.mayfly = false;
            p.onUpdateAbilities();
        }
        p.level().playSound(null, p.blockPosition(), out ? SoundEvents.ENDER_DRAGON_FLAP : SoundEvents.ARMOR_EQUIP_ELYTRA,
                SoundSource.PLAYERS, out ? 0.6F : 0.9F, out ? 1.5F : 1.1F);
        sync(p, true);
    }

    public static void sync(ServerPlayer p, boolean animate) {
        ModNetwork.sendToTrackingAndSelf(new WingsPacket(p.getUUID(), out(p), animate), p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p, false);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p, false);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p, false);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendToPlayer(new WingsPacket(target.getUUID(), out(target), false), viewer);
        }
    }
}

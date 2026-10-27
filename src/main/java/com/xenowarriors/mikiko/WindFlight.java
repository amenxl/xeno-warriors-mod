package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.RhalixFlight;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mikiko masters his wind at Character Level 50: from then on he can fly in survival (double-tap jump), riding
 * the wind. (His double jump turns into flight.)
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WindFlight {
    public static final int LEVEL = 50;
    private static final String GRANTED = "xenowarriors_wind_flight";

    private WindFlight() {}

    public static boolean canFly(ServerPlayer p) {
        return ModCapabilities.getType(p) == CharacterType.MIKIKO && Stats.characterLevel(p) >= LEVEL;
    }

    /**
     * Going to another dimension (portals, /tp, the Hekublade) gives you a fresh player on your side that forgets it can
     * fly, while the server still thinks it can - so the flight never got sent again. Re-send it after a dimension
     * change or respawn (once right away and once a moment later, after everything settles).
     */
    private static final java.util.Map<java.util.UUID, Integer> RESYNC = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onChangeDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) resync(p);
    }

    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) resync(p);
    }

    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) resync(p);
    }

    private static void resync(ServerPlayer p) {
        RESYNC.put(p.getUUID(), 10);
        grantIfAble(p);
    }

    private static void grantIfAble(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        if (canFly(p) || RhalixFlight.canFly(p)) p.getAbilities().mayfly = true;
        p.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer sp && RESYNC.containsKey(sp.getUUID())) {
            int left = RESYNC.get(sp.getUUID()) - 1;
            if (left <= 0) {
                RESYNC.remove(sp.getUUID());
                grantIfAble(sp);
            } else {
                RESYNC.put(sp.getUUID(), left);
            }
        }
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        if (p.isCreative() || p.isSpectator()) return;
        var abilities = p.getAbilities();
        boolean granted = p.getPersistentData().getBoolean(GRANTED);
        if (canFly(p)) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                p.onUpdateAbilities();
            }
            // faster with more Speed, with Netsu burning, in Fox King mode
            float speed = 0.05F * com.xenowarriors.stats.FlightBoost.factor(p);
            if (Math.abs(abilities.getFlyingSpeed() - speed) > 0.002F) {
                abilities.setFlyingSpeed(speed);
                p.onUpdateAbilities();
            }
            if (!granted) {
                p.getPersistentData().putBoolean(GRANTED, true);
                // told only the first time ever (kept through deaths and respawns)
                var keep = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                if (!keep.getBoolean("xw_flight_announced")) {
                    keep.putBoolean("xw_flight_announced", true);
                    p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, keep);
                    p.sendSystemMessage(Component.literal("After a lot of thought and consideration, why not use my wing to fly?")
                            .withStyle(ChatFormatting.AQUA));
                    p.sendSystemMessage(Component.literal("(Double-tap jump to take off.)").withStyle(ChatFormatting.GRAY));
                    p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1F, 1.4F);
                }
            }
        } else if (granted) {
            // not Mikiko any more (or levels were reset): the wind lets go
            p.getPersistentData().remove(GRANTED);
            if (!ModCapabilities.getType(p).skylander()) {
                abilities.mayfly = false;
                abilities.flying = false;
                abilities.setFlyingSpeed(0.05F);
                p.onUpdateAbilities();
            }
        }
    }
}

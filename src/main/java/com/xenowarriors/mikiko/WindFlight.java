package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
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

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        if (p.isCreative() || p.isSpectator()) return;
        var abilities = p.getAbilities();
        boolean granted = p.getPersistentData().getBoolean(GRANTED);
        if (canFly(p)) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                p.onUpdateAbilities();
            }
            if (!granted) {
                p.getPersistentData().putBoolean(GRANTED, true);
                p.sendSystemMessage(Component.literal("After a lot of thought and consideration, why not use my wing to fly?")
                        .withStyle(ChatFormatting.AQUA));
                p.sendSystemMessage(Component.literal("(Double-tap jump to take off.)").withStyle(ChatFormatting.GRAY));
                p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1F, 1.4F);
            }
        } else if (granted) {
            // not Mikiko any more (or levels were reset): the wind lets go
            p.getPersistentData().remove(GRANTED);
            if (ModCapabilities.getType(p) != CharacterType.RHALIX) {
                abilities.mayfly = false;
                abilities.flying = false;
                p.onUpdateAbilities();
            }
        }
    }
}

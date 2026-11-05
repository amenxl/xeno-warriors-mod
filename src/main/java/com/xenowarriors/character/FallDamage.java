package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Falling as a Xeno Warrior: anyone with wings (Rhalix and the Skylanders, winged You characters - not a demon in
 * human form) and Mikiko take no fall damage at all; everyone else takes a quarter of it. Party bots the same.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FallDamage {
    /** What's left of fall damage for wingless characters (75% less). */
    public static final float WINGLESS = 0.25F;

    private FallDamage() {}

    /** The fall damage multiplier for this character (1 = vanilla). */
    public static float multiplier(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.NONE) return 1F;
        if (t == CharacterType.MIKIKO || RhalixFlight.winged(p)) return 0F;
        return WINGLESS;
    }

    public static float multiplier(CharacterType t) {
        if (t == null || t == CharacterType.NONE) return 1F;
        return t == CharacterType.MIKIKO || t.skylander() ? 0F : WINGLESS;
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        float m;
        if (event.getEntity() instanceof Player p) m = multiplier(p);
        else if (event.getEntity() instanceof com.xenowarriors.bot.PartyBotEntity b) m = multiplier(b.character());
        else return;
        if (m >= 1F) return;
        event.setDamageMultiplier(event.getDamageMultiplier() * m);
        if (m <= 0F) { event.setDistance(0F); event.getEntity().fallDistance = 0F; }
    }
}

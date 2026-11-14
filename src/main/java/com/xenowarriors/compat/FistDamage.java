package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Kaeru only ever fights hand to hand: her bare-hand punches (the Sparks of Black style borrows weapon moves, which
 * expect a weapon's damage) hit a little harder than anyone else's. Everyone else's bare-hand hits are at least as
 * strong as Mikiko's glove punches.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FistDamage {
    private FistDamage() {}

    public static final float KAERU_FLOOR = 3.6F, KAERU_SCALE = 1.6F;
    public static final float FIST_FLOOR = 3.0F;
    public static int boosted, floored;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        var src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !"player".equals(src.getMsgId())) return;
        if (!p.getMainHandItem().isEmpty()) return;
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.KAERU) {
            event.setAmount(Math.max(event.getAmount() * KAERU_SCALE, KAERU_FLOOR * Math.max(0.2F, Math.min(1F, event.getAmount() / 0.6F))));
            boosted++;
        } else if (t != CharacterType.NONE && event.getAmount() < FIST_FLOOR) {
            // the borrowed bare-hand styles (Rhalix's Ender Blaster strikes, Kyrie's Iron Fist...) expect a weapon's damage:
            // a character's punch is never weaker than Mikiko's glove punches
            event.setAmount(FIST_FLOOR);
            floored++;
        }
    }
}

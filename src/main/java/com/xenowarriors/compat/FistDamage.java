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
 * expect a weapon's damage) hit a little harder than anyone else's.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FistDamage {
    private FistDamage() {}

    public static final float KAERU_FLOOR = 3.6F, KAERU_SCALE = 1.6F;
    public static int boosted;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        var src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !"player".equals(src.getMsgId())) return;
        if (!p.getMainHandItem().isEmpty() || ModCapabilities.getType(p) != CharacterType.KAERU) return;
        event.setAmount(Math.max(event.getAmount() * KAERU_SCALE, KAERU_FLOOR * Math.max(0.2F, Math.min(1F, event.getAmount() / 0.6F))));
        boosted++;
    }
}

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
 * strong as Mikiko's glove punches, and Mikiko's bare hands punch as fast as the glove.
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

    /** Mikiko's bare hands punch as fast as Epic Fight's glove (attack speed 4.0): his punches play at the glove's speed (the user). */
    public static final double GLOVE_SPEED = 4.0;
    private static final java.util.UUID FAST_FISTS = java.util.UUID.fromString("6c1d6a3e-3f1b-4a8e-9b2f-5e9a1d2c7f41");

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (attr == null) return;
        boolean want = ModCapabilities.getType(p) == CharacterType.MIKIKO && p.getMainHandItem().isEmpty();
        var mod = attr.getModifier(FAST_FISTS);
        double without = attr.getValue() - (mod == null ? 0 : mod.getAmount());
        double need = want ? Math.max(0, GLOVE_SPEED - without) : 0;
        if (mod != null && Math.abs(mod.getAmount() - need) < 0.001) return;
        if (mod != null) attr.removeModifier(FAST_FISTS);
        if (need > 0) attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(FAST_FISTS, "Mikiko's fast fists",
                need, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
    }
}

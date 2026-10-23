package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Bare-handed, Mikiko and Rhalix get a weapon-like attack tempo: the attack indicator fills like it does with a
 * sword, so each punch/kick of the combo comes out on a fully charged swing instead of every few ticks.
 * Plain fists swing at speed 4 (5 ticks); this brings it to 1.8 (about 11 ticks, a little quicker than a sword).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FistTempo {
    private static final UUID ID = UUID.fromString("7c1f3a52-9d4e-4b8a-a6e1-2f5d8c0b3e71");
    public static final double FIST_SPEED_CHANGE = -2.6;     // 1.4: about the pace of Epic Fight combos

    private FistTempo() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        // server side only: attack speed is a synced attribute, so the client's indicator follows
        if (event.phase != TickEvent.Phase.START || event.player.level().isClientSide) return;
        Player p = event.player;
        AttributeInstance speed = p.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) return;
        CharacterType type = ModCapabilities.getType(p);
        boolean want = type != CharacterType.NONE && p.getMainHandItem().isEmpty();
        boolean has = speed.getModifier(ID) != null;
        if (want && !has) speed.addTransientModifier(new AttributeModifier(ID, "Xeno Warriors fist tempo", FIST_SPEED_CHANGE,
                AttributeModifier.Operation.ADDITION));
        else if (!want && has) speed.removeModifier(ID);
    }
}

package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** No fighting while you're on the phone - hang up first. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhoneEvents {
    private PhoneEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (PhoneItem.onCall(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player p && PhoneItem.onCall(p) && event.getSource().getDirectEntity() == p) event.setCanceled(true);
    }
}

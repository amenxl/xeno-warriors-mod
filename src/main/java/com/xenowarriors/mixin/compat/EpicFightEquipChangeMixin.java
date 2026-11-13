package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Epic Fight + other mods' mobs: when a mob swaps its held item, Epic Fight adds the new weapon's attack-speed and
 * reach bonuses - and throws ("Modifier is already applied on this attribute!") if the old ones were never taken off,
 * which crashed the whole world (the user's crash reports: Super Lucky Update's Shadow Assassin). The swap still
 * happens; only that one held-item update is skipped (and logged once).
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.events.EntityEvents", remap = false)
public abstract class EpicFightEquipChangeMixin {
    private static boolean xenowarriors$warned;

    @Redirect(method = "equipChangeEvent", at = @At(value = "INVOKE",
            target = "Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;updateHeldItem(Lyesman/epicfight/world/capabilities/item/CapabilityItem;Lyesman/epicfight/world/capabilities/item/CapabilityItem;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)V"),
            remap = false, require = 0)
    private static void xenowarriors$safeHeldItem(yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> patch,
                                                  yesman.epicfight.world.capabilities.item.CapabilityItem from,
                                                  yesman.epicfight.world.capabilities.item.CapabilityItem to,
                                                  net.minecraft.world.item.ItemStack fromStack, net.minecraft.world.item.ItemStack toStack,
                                                  net.minecraft.world.InteractionHand hand) {
        try {
            patch.updateHeldItem(from, to, fromStack, toStack, hand);
        } catch (IllegalArgumentException e) {
            if (!xenowarriors$warned) {
                xenowarriors$warned = true;
                com.xenowarriors.XenoWarriors.LOGGER.warn("Epic Fight couldn't update a mob's held weapon ({}); skipped instead of crashing: {}",
                        patch.getOriginal(), e.getMessage());
            }
        }
    }
}

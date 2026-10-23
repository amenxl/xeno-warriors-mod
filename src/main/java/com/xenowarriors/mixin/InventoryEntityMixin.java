package com.xenowarriors.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drawing your character in a menu (inventory, M menu outfits): it's never "first person" there - show the head. */
@Mixin(InventoryScreen.class)
public abstract class InventoryEntityMixin {
    @Inject(method = "renderEntityInInventory", at = @At("HEAD"))
    private static void xenowarriors$guiStart(GuiGraphics g, int x, int y, int scale, Quaternionf pose, Quaternionf cameraOrientation,
                                              LivingEntity entity, CallbackInfo ci) {
        com.xenowarriors.client.FirstPersonHead.inGui = true;
    }

    @Inject(method = "renderEntityInInventory", at = @At("RETURN"))
    private static void xenowarriors$guiEnd(GuiGraphics g, int x, int y, int scale, Quaternionf pose, Quaternionf cameraOrientation,
                                            LivingEntity entity, CallbackInfo ci) {
        com.xenowarriors.client.FirstPersonHead.inGui = false;
    }
}

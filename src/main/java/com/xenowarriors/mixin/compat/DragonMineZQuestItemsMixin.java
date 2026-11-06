package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ missions that want items: QuestEvents.countItems counts only the player's own inventory - the bots'
 * things (bag and extra tabs) are added here (see compat.DmzItemMissions). It only counts; DragonMineZ never takes the
 * items, so nothing is removed from anyone.
 */
@Pseudo
@Mixin(targets = "com.dragonminez.server.events.QuestEvents", remap = false)
public abstract class DragonMineZQuestItemsMixin {
    @Inject(method = "countItems", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$botBags(net.minecraft.server.level.ServerPlayer player, String itemId, CallbackInfoReturnable<Integer> cir) {
        int extra = com.xenowarriors.compat.DmzItemMissions.botItems(player, itemId);
        if (extra > 0) cir.setReturnValue(cir.getReturnValueI() + extra);
    }
}

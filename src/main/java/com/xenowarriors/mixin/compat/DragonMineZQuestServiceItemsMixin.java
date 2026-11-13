package com.xenowarriors.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ 2.2: item missions count through QuestService now (QuestEvents.countItems is gone) - your bots' bags
 * still count towards them (see DragonMineZQuestItemsMixin for the older versions).
 */
@Pseudo
@Mixin(targets = "com.dragonminez.common.quest.QuestService", remap = false)
public abstract class DragonMineZQuestServiceItemsMixin {
    @Inject(method = "countItemById", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$botBagsById(net.minecraft.server.level.ServerPlayer player, String itemId, CallbackInfoReturnable<Integer> cir) {
        int extra = com.xenowarriors.compat.DmzItemMissions.botItems(player, itemId);
        if (extra > 0) cir.setReturnValue(cir.getReturnValueI() + extra);
    }

    @Inject(method = "countItem", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void xenowarriors$botBags(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.Item item, CallbackInfoReturnable<Integer> cir) {
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return;
        int extra = com.xenowarriors.compat.DmzItemMissions.botItems(player, id.toString());
        if (extra > 0) cir.setReturnValue(cir.getReturnValueI() + extra);
    }
}

package com.xenowarriors.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right before a player is written out (autosave, /save-all, logout, the singleplayer host into level.dat), their
 * live bot teammates are written into their saved data (PartyBots.beforePlayerSave), so a crash can't lose what the
 * bots carry. Only an @Inject at HEAD (require = 0): other mods' mixins on saving still work, and if this can't
 * apply the bots are still written every second (PartyBots.onServerTick).
 */
@Mixin(Entity.class)
public abstract class PlayerSaveSnapshotMixin {
    @Inject(method = "saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), require = 0)
    private void xenowarriors$botSnapshot(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if ((Object) this instanceof ServerPlayer p) com.xenowarriors.bot.PartyBots.beforePlayerSave(p);
    }
}

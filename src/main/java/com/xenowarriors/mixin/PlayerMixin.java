package com.xenowarriors.mixin;

import com.xenowarriors.character.CharacterHolder;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stores the chosen character on the player and saves it with the player's data. */
@Mixin(Player.class)
public abstract class PlayerMixin implements CharacterHolder {
    @Unique
    private final PlayerCharacter xenowarriors$character = new PlayerCharacter();

    @Override
    public PlayerCharacter xenowarriors$getCharacter() {
        return xenowarriors$character;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void xenowarriors$save(CompoundTag tag, CallbackInfo ci) {
        tag.put("XenoWarriors", xenowarriors$character.save());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void xenowarriors$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("XenoWarriors")) {
            xenowarriors$character.load(tag.getCompound("XenoWarriors"));
        }
    }
}

package com.xenowarriors.mixin;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swaps a player's skin for their chosen character's skin. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$characterSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        CharacterType type = ClientCharacterData.get((AbstractClientPlayer) (Object) this);
        if (type != CharacterType.NONE) {
            int o = ClientCharacterData.outfit((AbstractClientPlayer) (Object) this);
            cir.setReturnValue(o > 0 ? com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_"
                    + ClientCharacterData.OUTFIT_KEYS[o] + ".png") : type.getSkin());
        }
    }

    /** Both characters use the wide (4px) arm model. */
    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$characterModel(CallbackInfoReturnable<String> cir) {
        if (ClientCharacterData.get((AbstractClientPlayer) (Object) this) != CharacterType.NONE) {
            cir.setReturnValue("default");
        }
    }
}

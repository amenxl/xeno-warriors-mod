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
        if (type == CharacterType.YOU) {                                    // your own character: its own skin, if it has one
            var look = com.xenowarriors.client.you.YouLooks.of((AbstractClientPlayer) (Object) this);
            if (look != null) xenowarriors$set(cir, type, look.tex());
            return;
        }
        if (type != CharacterType.NONE) {
            if (com.xenowarriors.client.kaeru.ChangeClient.silhouette(((AbstractClientPlayer) (Object) this).getId())) {
                xenowarriors$set(cir, type, com.xenowarriors.client.kaeru.ChangeClient.SIL_SKIN);
                return;
            }
            var fusion = com.xenowarriors.client.FusionLook.of((AbstractClientPlayer) (Object) this);
            if (fusion != null) { xenowarriors$set(cir, type, fusion.skin()); return; }   // a fusion: both of them in one look
            int o = ClientCharacterData.outfit((AbstractClientPlayer) (Object) this);
            if (com.xenowarriors.client.render.CharacterDetailLayer.shiyokenOutfit((AbstractClientPlayer) (Object) this, type)) {   // Shiyoken: his fire outfit
                xenowarriors$set(cir, type, com.xenowarriors.client.cel.Cel.tex(com.xenowarriors.client.render.CharacterDetailLayer.SHIYO_SKIN));
                return;
            }
            if (o == 0 && type == CharacterType.RHALIX && com.xenowarriors.client.LunarClient.active(((AbstractClientPlayer) (Object) this).getId())) {
                xenowarriors$set(cir, type, com.xenowarriors.client.cel.Cel.tex(com.xenowarriors.XenoWarriors.id("textures/entity/character/rhalix_lunar.png")));   // Lunar: white coat
                return;
            }
            if (com.xenowarriors.client.compat.DmzForms.topOff((AbstractClientPlayer) (Object) this)) {     // Super Saiyan 4 / Buffed: the top comes off (every outfit)
                String form = com.xenowarriors.client.compat.DmzForms.buffed((AbstractClientPlayer) (Object) this) ? "_buff.png" : "_ssj4.png";
                xenowarriors$set(cir, type, com.xenowarriors.client.cel.Cel.tex(com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + (o > 0 ? "_" + type.outfitKeys()[o] : "") + form)));
                return;
            }
            xenowarriors$set(cir, type, com.xenowarriors.client.cel.Cel.tex(o > 0 ? com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_"
                    + type.outfitKeys()[o] + ".png") : type.getSkin()));
        }
    }

    /** The skin it ends up with - recoloured for their DragonMineZ race (client/race/RaceLook). */
    @org.spongepowered.asm.mixin.Unique
    private void xenowarriors$set(CallbackInfoReturnable<ResourceLocation> cir, CharacterType type, ResourceLocation tex) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        cir.setReturnValue(com.xenowarriors.client.race.RaceLook.tint(com.xenowarriors.client.race.FormHair.tint(tex, self), self, type));
    }

    /** Characters use the wide (4px) arm model; the girls use the slim one (see FemaleBody). */
    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void xenowarriors$characterModel(CallbackInfoReturnable<String> cir) {
        CharacterType t = ClientCharacterData.get((AbstractClientPlayer) (Object) this);
        if (t == CharacterType.YOU) {
            var look = com.xenowarriors.client.you.YouLooks.of((AbstractClientPlayer) (Object) this);
            if (look != null) cir.setReturnValue(look.slim() ? "slim" : "default");
            return;
        }
        if (t != CharacterType.NONE) {
            cir.setReturnValue(com.xenowarriors.client.FusionLook.of((AbstractClientPlayer) (Object) this) == null
                    && com.xenowarriors.client.FemaleBody.is((AbstractClientPlayer) (Object) this) ? "slim" : "default");   // (fusions: the wide body)
        }
    }
}

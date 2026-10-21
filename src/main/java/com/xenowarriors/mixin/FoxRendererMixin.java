package com.xenowarriors.mixin;

import com.xenowarriors.client.fox.FoxClientFx;
import net.minecraft.client.renderer.entity.FoxRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pet foxes wear the skin their owner picked. */
@Mixin(FoxRenderer.class)
public abstract class FoxRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/animal/Fox;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true)
    private void xenowarriors$skin(Fox fox, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation skin = FoxClientFx.texture(fox);
        if (skin != null) cir.setReturnValue(skin);
    }
}

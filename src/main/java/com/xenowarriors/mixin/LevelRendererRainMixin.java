package com.xenowarriors.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rain stops at an open Heku-umbrella: the rain renderer asks each column how high the ground is, and the columns
 * under the canopy answer with the canopy's height (FormsFx.rainTop). Optional (require = 0): if another mod has
 * changed the rain renderer the umbrella still works, the rain is just drawn through it.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererRainMixin {
    @Redirect(method = "renderSnowAndRain", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int xenowarriors$rainStopsAtUmbrella(Level level, Heightmap.Types type, int x, int z) {
        return com.xenowarriors.client.forms.FormsFx.rainTop(x, z, level.getHeight(type, x, z));
    }
}

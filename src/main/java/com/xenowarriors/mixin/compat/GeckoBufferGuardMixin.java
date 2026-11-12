package com.xenowarriors.mixin.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A crash guard for GeckoLib-drawn bodies (DragonMineZ's players in its forms, and its first-person body): if the
 * buffer a bone is about to be drawn into has already been closed (something drew with another render type in between
 * - seen with Oculus), GeckoLib writes into a closed buffer and the game crashes with "BufferBuilder not started".
 * Here the bone just gets a fresh buffer of the same render type instead.
 */
@Mixin(value = GeoEntityRenderer.class, remap = false)
public abstract class GeckoBufferGuardMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIIFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenowarriors$freshBuffer(PoseStack pose, Entity animatable, GeoBone bone, RenderType type, MultiBufferSource buffers, VertexConsumer buffer,
                                          boolean reRender, float partial, int light, int overlay, float r, float g, float b, float a, CallbackInfo ci) {
        if (!(buffer instanceof BufferBuilder bb) || bb.building() || buffers == null || type == null) return;
        VertexConsumer fresh = buffers.getBuffer(type);
        if (fresh == buffer) { if (bb.building()) com.xenowarriors.client.GeoBufferGuard.saved++; return; }   // (the same builder, started again: it can carry on as it is)
        if (fresh instanceof BufferBuilder fb && !fb.building()) return;      // (nothing better to hand it)
        com.xenowarriors.client.GeoBufferGuard.saved++;
        ci.cancel();
        ((GeoEntityRenderer) (Object) this).renderRecursively(pose, animatable, bone, type, buffers, fresh, reRender, partial, light, overlay, r, g, b, a);
    }
}

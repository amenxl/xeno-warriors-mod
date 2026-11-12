package com.xenowarriors.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Outgrown gear wears out faster (stats.OutgrownWear): the use is re-run with the bigger amount. */
@Mixin(ItemStack.class)
public abstract class ItemStackWearMixin {
    @Unique private static final ThreadLocal<Boolean> xenowarriors$inside = ThreadLocal.withInitial(() -> false);

    @Inject(method = "hurtAndBreak", at = @At("HEAD"), cancellable = true, require = 0)
    private <T extends LivingEntity> void xenowarriors$outgrown(int amount, T entity, Consumer<T> onBroken, CallbackInfo ci) {
        if (xenowarriors$inside.get()) return;
        ItemStack self = (ItemStack) (Object) this;
        int more;
        try { more = com.xenowarriors.stats.OutgrownWear.wear(self, amount, entity); } catch (Throwable t) { return; }
        if (more == amount) return;
        ci.cancel();
        xenowarriors$inside.set(true);
        try { self.hurtAndBreak(more, entity, onBroken); } finally { xenowarriors$inside.set(false); }
    }
}

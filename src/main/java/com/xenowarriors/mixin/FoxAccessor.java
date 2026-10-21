package com.xenowarriors.mixin;

import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

/** Lets Mikiko's foxes trust him and wake up / stand up when he needs them. */
@Mixin(Fox.class)
public interface FoxAccessor {
    @Invoker("addTrustedUUID")
    void xenowarriors$addTrusted(UUID uuid);

    @Invoker("setSleeping")
    void xenowarriors$setSleeping(boolean sleeping);

    @Invoker("setIsInterested")
    void xenowarriors$setInterested(boolean interested);

    @Invoker("setSitting")
    void xenowarriors$setSitting(boolean sitting);
}

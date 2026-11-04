package com.xenowarriors.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Copies a bot's walk cycle onto the player body it's drawn with (see client/bot/BotAvatar). */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationAccessor {
    @Accessor("speedOld") float xenowarriors$speedOld();
    @Accessor("speedOld") void xenowarriors$setSpeedOld(float v);
    @Accessor("position") float xenowarriors$position();
    @Accessor("position") void xenowarriors$setPosition(float v);
}

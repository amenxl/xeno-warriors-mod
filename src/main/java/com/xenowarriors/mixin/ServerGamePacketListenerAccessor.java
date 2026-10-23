package com.xenowarriors.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets sky-walking (Sky Boots) not count as flying without permission. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerAccessor {
    @Accessor("aboveGroundTickCount")
    void xenowarriors$setAboveGroundTickCount(int ticks);
}

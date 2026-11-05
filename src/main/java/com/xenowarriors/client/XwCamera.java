package com.xenowarriors.client;

import net.minecraft.world.entity.Entity;

/**
 * Our camera changes (cutscene cameras, the vehicle camera), added to Minecraft's Camera by mixin/CameraMixin. Normally
 * applied at the end of Camera.setup; when another mod builds the third-person camera itself and skips vanilla's
 * setup (Epic Fight's camera), that mod's "camera built" hook applies them instead.
 */
public interface XwCamera {
    void xenowarriors$applyOurs(Entity entity, boolean detached, float partialTick);
}

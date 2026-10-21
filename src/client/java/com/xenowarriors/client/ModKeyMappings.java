package com.xenowarriors.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Skill keys. Players can change these in Options > Controls > Key Binds > Xeno Warriors. */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.xenowarriors";

    public static final KeyMapping[] SKILLS = {
            key("skill_1", GLFW.GLFW_KEY_Z),
            key("skill_2", GLFW.GLFW_KEY_X),
            key("skill_3", GLFW.GLFW_KEY_C),
            key("skill_4", GLFW.GLFW_KEY_V),
            key("ultimate", GLFW.GLFW_KEY_R),
    };

    private ModKeyMappings() {}

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.xenowarriors." + name, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }
}

package com.xenowarriors.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Skill keys (and Mikiko's summon key). Players can change these in Options > Controls > Key Binds > Xeno Warriors. */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.xenowarriors";

    // (renamed from skill_1.. so the new default keys apply - Z is lock-on now)
    public static final KeyMapping[] SKILLS = {
            key("skill1", GLFW.GLFW_KEY_X),
            key("skill2", GLFW.GLFW_KEY_C),
            key("skill3", GLFW.GLFW_KEY_V),
            key("skill4", GLFW.GLFW_KEY_B),
            key("ultimate_skill", GLFW.GLFW_KEY_R),
    };

    /** Lock on to the target you're facing (press again to let go). */
    public static final KeyMapping LOCK_ON = key("lock_on", GLFW.GLFW_KEY_Z);

    /** Sense the Karma of everything nearby. */
    public static final KeyMapping KARMA_SENSE = key("karma_sense", GLFW.GLFW_KEY_K);
    public static final KeyMapping STATS_MENU = key("stats_menu", GLFW.GLFW_KEY_M);

    /** Mikiko: summon / put away the Hekublade. */
    public static final KeyMapping SUMMON_BLADE = key("summon_blade", GLFW.GLFW_KEY_G);

    /** Rhalix: take out / put away his wings. */
    public static final KeyMapping TOGGLE_WINGS = key("toggle_wings", GLFW.GLFW_KEY_H);

    private ModKeyMappings() {}

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.xenowarriors." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }
}

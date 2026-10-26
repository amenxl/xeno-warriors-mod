package com.xenowarriors.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Skill keys (and Mikiko's summon key). Players can change these in Options > Controls > Key Binds > Xeno Warriors. */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.xenowarriors";

    // Left Control + 1-5 (renamed again so the new default keys replace the old saved ones)
    public static final KeyMapping[] SKILLS = {
            ctrlKey("skill_ctrl1", GLFW.GLFW_KEY_1),
            ctrlKey("skill_ctrl2", GLFW.GLFW_KEY_2),
            ctrlKey("skill_ctrl3", GLFW.GLFW_KEY_3),
            ctrlKey("skill_ctrl4", GLFW.GLFW_KEY_4),
            ctrlKey("ultimate_ctrl5", GLFW.GLFW_KEY_5),
    };

    /** Lock on to the target you're facing (press again to let go). */
    /** Super ultimate (KAs and really big attacks): Ctrl + 6. */
    public static final KeyMapping SUPER_ULT = ctrlKey("super_ultimate_ctrl6", GLFW.GLFW_KEY_6);
    public static final KeyMapping LOCK_ON = key("lock_on", GLFW.GLFW_KEY_Z);

    /** Sense the Karma of everything nearby. */
    public static final KeyMapping KARMA_SENSE = key("karma_sense", GLFW.GLFW_KEY_K);
    public static final KeyMapping STATS_MENU = key("stats_menu", GLFW.GLFW_KEY_M);
    public static final KeyMapping FOX_KING = key("fox_king", GLFW.GLFW_KEY_N);

    /** Mikiko: summon / put away the Hekublade. */
    public static final KeyMapping SUMMON_BLADE = key("summon_blade", GLFW.GLFW_KEY_G);

    /** Pull your hood up / push it back down (hooded outfits and the Karma-Reducing Hoodie). */
    public static final KeyMapping TOGGLE_HOOD = key("toggle_hood", GLFW.GLFW_KEY_J);

    /** Mikiko: open / leave the Hekublade Dimension (with the Hekublade in hand). */
    public static final KeyMapping HEKUBLADE_REALM = key("hekublade_realm", GLFW.GLFW_KEY_U);

    /** Rhalix: take out / put away his wings. */
    public static final KeyMapping TOGGLE_WINGS = key("toggle_wings", GLFW.GLFW_KEY_H);

    /** Accept / decline the request card on screen (party invites, battles, team ultimate). */
    public static final KeyMapping ACCEPT_REQUEST = key("accept_request", GLFW.GLFW_KEY_Y);
    public static final KeyMapping DECLINE_REQUEST = key("decline_request", GLFW.GLFW_KEY_X);

    private ModKeyMappings() {}

    private static KeyMapping ctrlKey(String name, int defaultKey) {
        return new KeyMapping("key.xenowarriors." + name, KeyConflictContext.IN_GAME,
                net.minecraftforge.client.settings.KeyModifier.CONTROL, InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }

    /** Holding Control (so 1-5 use skills instead of picking a hotbar slot). */
    public static boolean ctrlHeld() {
        long w = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        if (!InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL) && !InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL)) return false;
        for (KeyMapping k : SKILLS) if (k.isDown()) return true;
        if (SUPER_ULT.isDown()) return true;    // only when a skill number is pressed
        return false;
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.xenowarriors." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, CATEGORY);
    }
}

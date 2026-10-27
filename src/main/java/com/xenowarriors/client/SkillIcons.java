package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Skill pictures (textures/gui/skill/&lt;skill id&gt;.png, made by tools/xw/skillicons): a little blocky character doing
 * the move. Shown instead of the old 2-letter short names on the skill bar, the loadout and the skill lists.
 */
public final class SkillIcons {
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private SkillIcons() {}

    /** The picture for this skill id, or null if it has none. */
    public static ResourceLocation get(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            ResourceLocation rl = XenoWarriors.id("textures/gui/skill/" + k + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent() ? rl : null;
        });
    }

    /** The Xeno Blast picture for this character (Mikiko's is orange, Rhalix's purple). */
    public static ResourceLocation xenoBlast(CharacterType t) {
        return get(t == CharacterType.MIKIKO ? "xeno_blast_mikiko" : "xeno_blast");
    }

    /** Draws the skill's picture in a box; false if it has none (draw the old text then). */
    public static boolean draw(GuiGraphics g, String id, int x, int y, int size) {
        ResourceLocation rl = get(id);
        if (rl == null) return false;
        draw(g, rl, x, y, size);
        return true;
    }

    public static void draw(GuiGraphics g, ResourceLocation rl, int x, int y, int size) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(rl, x, y, size, size, 0, 0, 32, 32, 32, 32);
    }

    public static void clear() { CACHE.clear(); }
}

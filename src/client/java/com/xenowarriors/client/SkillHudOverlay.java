package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The 5 skill slots in the bottom-right corner, with key labels and cooldowns. */
public class SkillHudOverlay implements HudRenderCallback {
    private static final int SIZE = 22;
    private static final int GAP = 3;

    @Override
    public void onHudRender(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        CharacterType type = ClientCharacterData.getLocal();
        if (type == CharacterType.NONE) return;

        int width = g.guiWidth();
        int height = g.guiHeight();
        Font font = mc.font;
        int left = width - SkillManager.SLOTS * (SIZE + GAP) - 4;
        int top = height - SIZE - 6;

        for (int slot = 0; slot < SkillManager.SLOTS; slot++) {
            int x = left + slot * (SIZE + GAP);
            Skill skill = SkillManager.get(type, slot);
            boolean ultimate = slot == SkillManager.ULTIMATE_SLOT;

            int border = skill == null ? 0xFF3A3A3A : ultimate ? 0xFFFFD24A : type.getColor();
            g.fill(x - 1, top - 1, x + SIZE + 1, top + SIZE + 1, border);
            g.fill(x, top, x + SIZE, top + SIZE, 0xCC0E0E14);

            if (skill != null) {
                String name = skill.getName().getString();
                String shortName = name.length() <= 2 ? name : name.substring(0, 2);
                g.drawCenteredString(font, shortName, x + SIZE / 2, top + 4, 0xFFFFFF);

                float cd = ClientSkillState.cooldownFraction(slot);
                if (cd > 0) {
                    int shade = Math.round(SIZE * cd);
                    g.fill(x, top + SIZE - shade, x + SIZE, top + SIZE, 0xB0000000);
                    int seconds = (ClientSkillState.cooldownTicks(slot) + 19) / 20;
                    g.drawCenteredString(font, String.valueOf(seconds), x + SIZE / 2, top + 8, 0xFF5555);
                }
            }

            String key = ModKeyMappings.SKILLS[slot].getTranslatedKeyMessage().getString();
            if (key.length() > 3) key = key.substring(0, 3);
            g.pose().pushPose();
            g.pose().translate(x + SIZE - 1 - font.width(key) * 0.6F, top + SIZE - 6, 0);
            g.pose().scale(0.6F, 0.6F, 1F);
            g.drawString(font, key, 0, 0, 0xBBBBBB);
            g.pose().popPose();
        }

        int kishi = ClientSkillState.kishiLevel(mc.player.getUUID());
        if (kishi > 0) {
            g.drawString(font, Component.translatable("hud.xenowarriors.kishi_level", kishi), left, top - 12, 0xD9C8FF);
        }
    }
}

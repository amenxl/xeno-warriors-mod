package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** The 5 skill slots in the bottom-right corner, with key labels and cooldowns. */
public class SkillHudOverlay implements IGuiOverlay {
    private static final int SIZE = 22;
    private static final int GAP = 3;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        if (com.xenowarriors.client.KhHud.active()) return;      // the Kingdom Hearts HUD covers this
        CharacterType type = ClientCharacterData.getLocal();
        if (type == CharacterType.NONE) return;

        Font font = mc.font;
        int left = width - SkillManager.SLOTS * (SIZE + GAP) - 4;
        int top = height - SIZE - 6 - HudLayout.skillBarLift();

        for (int slot = 0; slot < SkillManager.SLOTS; slot++) {
            int x = left + slot * (SIZE + GAP);
            Skill skill = com.xenowarriors.skill.SkillPool.inSlot(type, ClientSkillState.loadout(), slot);
            boolean ultimate = slot == SkillManager.ULTIMATE_SLOT;

            int border = skill == null ? 0xFF3A3A3A : ultimate ? 0xFFFFD24A : type.getColor();
            g.fill(x - 1, top - 1, x + SIZE + 1, top + SIZE + 1, border);
            g.fill(x, top, x + SIZE, top + SIZE, 0xCC0E0E14);

            if (skill != null) {
                String shortKey = "skill.xenowarriors." + skill.getId() + ".short";
                String name = skill.getName().getString();
                String shortName = net.minecraft.client.resources.language.I18n.exists(shortKey)
                        ? net.minecraft.client.resources.language.I18n.get(shortKey)
                        : name.length() <= 2 ? name : name.substring(0, 2);
                g.drawCenteredString(font, shortName, x + SIZE / 2, top + 4, 0xFFFFFF);

                // not enough Karma: tint the slot blue and show the cost in red
                boolean canAfford = mc.player.isCreative() || ClientSkillState.karma() >= skill.getKarmaCost();
                if (!canAfford) g.fill(x, top, x + SIZE, top + SIZE, 0x66203A8A);
                String cost = String.valueOf(skill.getKarmaCost());
                g.pose().pushPose();
                g.pose().translate(x + 1.5F, top + SIZE - 6, 0);
                g.pose().scale(0.6F, 0.6F, 1F);
                g.drawString(font, cost, 0, 0, canAfford ? 0x7FD8FF : 0xFF5555);
                g.pose().popPose();

                // cooldown shade + seconds left
                float cd = ClientSkillState.cooldownFraction(slot);
                if (cd > 0) {
                    int shade = Math.round(SIZE * cd);
                    g.fill(x, top + SIZE - shade, x + SIZE, top + SIZE, 0xB0000000);
                    int seconds = (ClientSkillState.cooldownTicks(slot) + 19) / 20;
                    g.drawCenteredString(font, String.valueOf(seconds), x + SIZE / 2, top + 8, 0xFF5555);
                }
            }

            // key label in the bottom corner
            var km = ModKeyMappings.SKILLS[slot];
            String key = km.getKey().getDisplayName().getString();
            if (key.length() > 3) key = key.substring(0, 3);
            if (km.getKeyModifier() == net.minecraftforge.client.settings.KeyModifier.CONTROL) key = "^" + key;
            g.pose().pushPose();
            g.pose().translate(x + SIZE - 1 - font.width(key) * 0.6F, top + SIZE - 6, 0);
            g.pose().scale(0.6F, 0.6F, 1F);
            g.drawString(font, key, 0, 0, 0xBBBBBB);
            g.pose().popPose();
        }

        // super ultimate (KAs / really big attacks): a purple slot left of the row - locked until their KA is revealed
        {
            int x = left - SIZE - GAP - 4;
            g.fill(x - 1, top - 1, x + SIZE + 1, top + SIZE + 1, 0xFF8A3CC8);
            g.fill(x, top, x + SIZE, top + SIZE, 0xCC140A1E);
            var ka = ClientSkillState.ka();
            g.drawCenteredString(font, ka == null ? "?" : ka.title().substring(0, 2), x + SIZE / 2, top + 4, ka == null ? 0xB070FF : ka.color);
            float scd = ClientSkillState.cooldownFraction(com.xenowarriors.skill.SkillManager.SUPER_SLOT);
            if (ka != null && scd > 0) {
                int shade = Math.round(SIZE * scd);
                g.fill(x, top + SIZE - shade, x + SIZE, top + SIZE, 0xB0000000);
                g.drawCenteredString(font, String.valueOf((ClientSkillState.cooldownTicks(com.xenowarriors.skill.SkillManager.SUPER_SLOT) + 19) / 20), x + SIZE / 2, top + 8, 0xFF5555);
            }
            var km = ModKeyMappings.SUPER_ULT;
            String key = km.getKey().getDisplayName().getString();
            if (key.length() > 3) key = key.substring(0, 3);
            if (km.getKeyModifier() == net.minecraftforge.client.settings.KeyModifier.CONTROL) key = "^" + key;
            g.pose().pushPose();
            g.pose().translate(x + SIZE - 1 - font.width(key) * 0.6F, top + SIZE - 6, 0);
            g.pose().scale(0.6F, 0.6F, 1F);
            g.drawString(font, key, 0, 0, 0xBBBBBB);
            g.pose().popPose();
        }

        // Karma bar across the top of the skill slots
        int barLeft = left - 1, barRight = left + SkillManager.SLOTS * (SIZE + GAP) - GAP + 1;
        int barTop = top - 9, barBottom = top - 4;
        float karma = ClientSkillState.karma(), max = Math.max(1F, ClientSkillState.maxKarma());
        int fill = Math.round((barRight - barLeft - 2) * Math.min(1F, karma / max));
        g.fill(barLeft, barTop, barRight, barBottom, 0xFF0A0A12);
        g.fill(barLeft + 1, barTop + 1, barRight - 1, barBottom - 1, 0xFF1B2140);
        if (fill > 0) {
            g.fill(barLeft + 1, barTop + 1, barLeft + 1 + fill, barBottom - 1, 0xFF3FA9FF);
            g.fill(barLeft + 1, barTop + 1, barLeft + 1 + fill, barTop + 2, 0xFFA8E2FF);
        }
        String label = Component.translatable("hud.xenowarriors.karma", Math.round(karma), Math.round(max)).getString();
        g.pose().pushPose();
        g.pose().translate(barLeft, barTop - 7, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, label, 0, 0, 0x9FD8FF);
        g.pose().popPose();

        // Kishi gravity level while the dome is up
        int kishi = ClientSkillState.kishiLevel(mc.player.getUUID());
        if (kishi > 0) {
            g.drawString(font, Component.translatable("hud.xenowarriors.kishi_level", kishi),
                    left, barTop - 18, 0xD9C8FF);
        }
    }
}

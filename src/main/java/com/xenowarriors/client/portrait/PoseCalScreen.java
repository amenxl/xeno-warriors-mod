package com.xenowarriors.client.portrait;

import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (UI test) Pose calibration sheet: the same character in a grid of right-arm settings, so the angle conventions for the
 * portrait poses can be checked by eye. Row 1: arm x sweep (elbow 0). Row 2: elbow sweep (arm forward). Row 3: arm z
 * sweep (arm down). Row 4: arm y sweep with arm forward and elbow bent.
 */
public class PoseCalScreen extends Screen {
    private final CharacterType type;
    private final float yaw;

    public PoseCalScreen(CharacterType type, float yaw) { super(Component.literal("cal")); this.type = type; this.yaw = yaw; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF707880);
        float[][] rows = {{0F, -0.5F, -1F, -1.5F, -2F, -2.5F, -3F}, {0F, -0.5F, -1F, -1.5F, -2F, -2.5F, 0.8F}, {0F, 0.4F, 0.8F, 1.2F, -0.4F, -0.8F, 1.6F}, {0F, 0.4F, 0.8F, -0.4F, -0.8F, 1.2F, -1.2F}};
        String[] names = {"arm x", "elbow (arm x -1.5)", "arm z", "arm y (arm x -1.5, elbow -1.5)"};
        int cw = width / 7, ch = height / 4, sc = (int) (ch * 0.42F);
        for (int r = 0; r < 4; r++) {
            g.drawString(font, names[r], 2, r * ch + 2, 0xFFFFFF, true);
            for (int c = 0; c < 7; c++) {
                float v = rows[r][c];
                float ax = r == 0 ? v : r == 2 ? 0F : -1.5F, ay = r == 3 ? v : 0F, az = r == 2 ? v : 0F, el = r == 1 ? v : r == 3 ? -1.5F : 0F;
                PortraitPoses.override = new float[]{0F, 0F, 0F, 0F, 0F, ax, ay, az, 0F, 0F, 0F, 0F, 0F, 0F, 0F, el, 0F, 0F, 0F};
                PortraitScreen.drawCharacter(g, Minecraft.getInstance().player, type, c * cw + cw / 2, r * ch + ch - 4, sc, yaw);
                g.drawString(font, String.valueOf(v), c * cw + 4, r * ch + 12, 0xFFFF80, true);
            }
        }
        PortraitPoses.override = null;
    }
}

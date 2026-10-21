package com.xenowarriors.client.screen;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SelectCharacterPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** The "Choose Your Warrior" screen shown when a player first joins. */
public class CharacterSelectScreen extends Screen {
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 200;
    private static final int GAP = 24;

    /** Players who already have a character (opened via /xeno choose) can back out with Esc. */
    private final boolean canClose;

    public CharacterSelectScreen() {
        super(Component.translatable("screen.xenowarriors.select.title"));
        this.canClose = ClientCharacterData.getLocal() != CharacterType.NONE;
    }

    private int cardLeft(int index) {
        CharacterType[] choices = CharacterType.PLAYABLE;
        int total = choices.length * CARD_WIDTH + (choices.length - 1) * GAP;
        return (width - total) / 2 + index * (CARD_WIDTH + GAP);
    }

    private int cardTop() {
        return Math.max(40, (height - CARD_HEIGHT) / 2 + 10);
    }

    @Override
    protected void init() {
        CharacterType[] choices = CharacterType.PLAYABLE;
        for (int i = 0; i < choices.length; i++) {
            CharacterType type = choices[i];
            int x = cardLeft(i);
            addRenderableWidget(Button.builder(
                            Component.translatable("screen.xenowarriors.select.play_as", type.getDisplayName()),
                            b -> choose(type))
                    .bounds(x + 10, cardTop() + CARD_HEIGHT - 28, CARD_WIDTH - 20, 20)
                    .build());
        }
    }

    private void choose(CharacterType type) {
        ModNetwork.sendToServer(new SelectCharacterPacket(type));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int top = cardTop();
        g.pose().pushPose();
        g.pose().scale(2F, 2F, 1F);
        g.drawCenteredString(font, title, width / 4, (top - 34) / 2, 0xFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("screen.xenowarriors.select.subtitle"),
                width / 2, top - 12, 0xAAAAAA);

        CharacterType[] choices = CharacterType.PLAYABLE;
        for (int i = 0; i < choices.length; i++) {
            CharacterType type = choices[i];
            int x = cardLeft(i);
            int centerX = x + CARD_WIDTH / 2;
            boolean hovered = mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= top && mouseY < top + CARD_HEIGHT;

            // card: colored border + dark inside
            int border = hovered ? type.getColor() : (type.getColor() & 0x00FFFFFF) | 0x99000000;
            g.fill(x - 2, top - 2, x + CARD_WIDTH + 2, top + CARD_HEIGHT + 2, border);
            g.fill(x, top, x + CARD_WIDTH, top + CARD_HEIGHT, 0xE0101016);

            // names
            g.drawCenteredString(font, type.getFullName(), centerX, top + 8, type.getColor());
            g.drawCenteredString(font, type.getTitle(), centerX, top + 20, 0xDDDDDD);
            g.drawCenteredString(font, type.getElements(), centerX, top + 32, 0x999999);

            // 3D preview of the player wearing this character
            int modelY = top + CARD_HEIGHT - 42;
            ClientCharacterData.beginPreview(type);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, centerX, modelY, 50,
                        (float) (centerX - mouseX), (float) (modelY - 80 - mouseY), minecraft.player);
            } finally {
                ClientCharacterData.endPreview();
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return canClose;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

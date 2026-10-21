package com.xenowarriors.client.wing;

import com.xenowarriors.client.fox.FoxNameScreen;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.OpenWingPetPacket;
import com.xenowarriors.network.WingPetActionPacket;
import com.xenowarriors.wing.WingPets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A companion's card: name, health, Bag, Rename, and the skin picker. */
public class WingPetScreen extends Screen {
    private final OpenWingPetPacket pet;
    private int skin;

    public WingPetScreen(OpenWingPetPacket pet) {
        super(Component.literal(pet.name()));
        this.pet = pet;
        this.skin = pet.skin();
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 10;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> pick(-1)).bounds(cx - 80, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> pick(1)).bounds(cx + 60, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bag"), b -> {
            ModNetwork.sendToServer(new WingPetActionPacket(pet.pet(), WingPetActionPacket.BAG, 0));
        }).bounds(cx - 80, y + 28, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rename"), b ->
                minecraft.setScreen(new FoxNameScreen(pet.pet(), pet.name(), false, "companion"))).bounds(cx + 2, y + 28, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(cx - 40, y + 56, 80, 20).build());
    }

    private void pick(int dir) {
        skin = Math.floorMod(skin + dir, WingPets.SKINS.length);
        WingSkinLayer.set(pet.entityId(), skin);
        ModNetwork.sendToServer(new WingPetActionPacket(pet.pet(), WingPetActionPacket.SKIN, skin));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - 10;
        g.fill(cx - 90, y - 52, cx + 90, y + 84, 0xC0101018);
        g.fill(cx - 90, y - 52, cx + 90, y - 51, 0xFFC77DFF);
        g.drawCenteredString(font, pet.name(), cx, y - 44, 0xF0D0FF);
        g.drawCenteredString(font, pet.kind() + "  -  " + Math.round(pet.health()) + " / " + Math.round(pet.maxHealth()) + " HP",
                cx, y - 30, 0xAAAAAA);
        g.drawCenteredString(font, "Food in its bag is eaten when it's hurt", cx, y - 18, 0x777777);
        g.drawCenteredString(font, "Skin: " + WingPets.SKINS[skin], cx, y + 6, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.xenowarriors.client.fox;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RenameFoxPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** A little box to give one of Mikiko's foxes a name. */
public class FoxNameScreen extends Screen {
    private final UUID fox;
    private final String current;
    private final boolean justTamed;
    private EditBox box;

    public FoxNameScreen(UUID fox, String current, boolean justTamed) {
        super(Component.literal("Name your fox"));
        this.fox = fox;
        this.current = current;
        this.justTamed = justTamed;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        box = new EditBox(font, cx - 80, cy - 10, 160, 20, Component.literal("Name"));
        box.setMaxLength(24);
        box.setValue(current);
        box.setHighlightPos(0);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> done()).bounds(cx - 80, cy + 18, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(cx + 2, cy + 18, 78, 20).build());
    }

    private void done() {
        String name = box.getValue().trim();
        if (!name.isEmpty() && !name.equals(current)) ModNetwork.sendToServer(new RenameFoxPacket(fox, name));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            done();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2, cy = height / 2;
        g.fill(cx - 92, cy - 46, cx + 92, cy + 46, 0xC0101018);
        g.fill(cx - 92, cy - 46, cx + 92, cy - 44, 0xFFFF8A2A);
        g.drawCenteredString(font, justTamed ? "Your new fox! What's its name?" : "Name your fox", cx, cy - 34, 0xFFB060);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

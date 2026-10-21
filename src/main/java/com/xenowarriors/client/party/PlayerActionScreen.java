package com.xenowarriors.client.party;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PlayerActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Right-clicking another player: invite them to your party (or remove them / leave), or challenge them to a battle. */
public class PlayerActionScreen extends Screen {
    private final UUID target;
    private final String name;

    public PlayerActionScreen(UUID target, String name) {
        super(Component.literal(name));
        this.target = target;
        this.name = name;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 30;
        boolean together = PartyClient.inMyParty(target);
        if (!together) {
            addRenderableWidget(Button.builder(Component.literal("Invite to Party"), b -> send(PlayerActionPacket.INVITE))
                    .bounds(cx - 75, y, 150, 20).build());
        } else if (PartyClient.amLeader()) {
            addRenderableWidget(Button.builder(Component.literal("Remove from Party"), b -> send(PlayerActionPacket.KICK))
                    .bounds(cx - 75, y, 150, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Leave Party"), b -> send(PlayerActionPacket.LEAVE))
                    .bounds(cx - 75, y, 150, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("⚔ Request PvP Battle"), b -> send(PlayerActionPacket.DUEL))
                .bounds(cx - 75, y + 24, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(cx - 75, y + 52, 150, 20).build());
    }

    private void send(int action) {
        ModNetwork.sendToServer(new PlayerActionPacket(target, action));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - 30;
        g.fill(cx - 85, y - 26, cx + 85, y + 80, 0xC0101018);
        g.fill(cx - 85, y - 26, cx + 85, y - 24, 0xFF5CE1E6);
        g.drawCenteredString(font, name, cx, y - 16, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

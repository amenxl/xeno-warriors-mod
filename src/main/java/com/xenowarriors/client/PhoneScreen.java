package com.xenowarriors.client;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PhoneActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The phone: everyone online to call (with Simple Voice Chat you'll hear each other anywhere), or a pretend call. */
public class PhoneScreen extends Screen {
    private static final int W = 150, H = 220;
    private int page;

    public PhoneScreen() {
        super(Component.literal("Phone"));
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        clearWidgets();
        Minecraft mc = Minecraft.getInstance();
        List<net.minecraft.client.multiplayer.PlayerInfo> people = new ArrayList<>();
        if (mc.getConnection() != null && mc.player != null)
            for (var info : mc.getConnection().getOnlinePlayers()) if (!info.getProfile().getId().equals(mc.player.getUUID())) people.add(info);
        people.sort((a, b) -> a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName()));
        int perPage = 7, x = left() + 12, y = top() + 30;
        int start = page * perPage;
        for (int i = start; i < Math.min(people.size(), start + perPage); i++) {
            var info = people.get(i);
            UUID id = info.getProfile().getId();
            addRenderableWidget(Button.builder(Component.literal("📞 " + info.getProfile().getName()), b -> {
                ModNetwork.sendToServer(new PhoneActionPacket(0, id));
                onClose();
            }).bounds(x, y, W - 24, 18).build());
            y += 21;
        }
        if (people.isEmpty()) addRenderableOnly((g, mx, my, pt) -> g.drawCenteredString(font, "Nobody else online", left() + W / 2, top() + 60, 0x9090A0));
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; init(); }).bounds(x, top() + 180, 20, 16).build());
        if (start + perPage < people.size()) addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; init(); }).bounds(left() + W - 32, top() + 180, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Pretend call"), b -> {
            ModNetwork.sendToServer(new PhoneActionPacket(2, new UUID(0, 0)));
            onClose();
        }).bounds(x + 24, top() + 180, W - 72, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 4, y - 4, x + W + 4, y + H + 4, 0xFF101014);           // the phone's case
        g.fill(x, y, x + W, y + H, 0xFF1A1E2C);                            // screen
        g.fill(x + W / 2 - 14, y - 2, x + W / 2 + 14, y + 2, 0xFF050507);  // notch
        g.drawCenteredString(font, "Contacts", x + W / 2, y + 12, 0xFFFFFF);
        g.fill(x + W / 2 - 18, y + H - 10, x + W / 2 + 18, y + H - 7, 0xFF404660);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

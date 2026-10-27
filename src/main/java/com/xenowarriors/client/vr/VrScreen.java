package com.xenowarriors.client.vr;

import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.OpenVrPacket;
import com.xenowarriors.network.VrActionPacket;
import com.xenowarriors.vr.Vr;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Yuri's VR console: free training in any environment, or a challenge with rules. */
public class VrScreen extends Screen {
    private final OpenVrPacket m;
    private int env;
    private static final int W = 300;

    public VrScreen(OpenVrPacket m) { super(Component.literal("VR Console")); this.m = m; }

    private int x0() { return (width - W) / 2; }
    private int y0() { return Math.max(10, height / 2 - 125); }

    private void send(int kind, int arg) { ModNetwork.sendToServer(new VrActionPacket(kind, arg)); onClose(); }

    @Override
    protected void init() {
        int x = x0(), y = y0() + 26;
        if (m.inSession()) {
            addRenderableWidget(Button.builder(Component.literal("Leave VR"), b -> send(2, 0)).bounds(x + W / 2 - 60, y + 20, 120, 20).build());
            return;
        }
        addRenderableWidget(Button.builder(Component.literal("< " + Vr.ENVS[env].name() + " >"), b -> {
            env = (env + 1) % Vr.ENVS.length; b.setMessage(Component.literal("< " + Vr.ENVS[env].name() + " >"));
        }).bounds(x + 8, y + 12, 180, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Train vs VR Ultima"), b -> send(0, env)).bounds(x + 192, y + 12, W - 200, 20).build());
        int cy = y + 52;
        for (int i = 0; i < Vr.CHALLENGES.length; i++) {
            var c = Vr.CHALLENGES[i];
            int idx = i, col = i % 2, row = i / 2;
            boolean done = i < m.done().length && m.done()[i];
            Button b = Button.builder(Component.literal((done ? "✔ " : "") + c.name()), btn -> send(1, idx))
                    .bounds(x + 8 + col * (W / 2 - 4), cy + row * 24, W / 2 - 12, 20).build();
            String rules = c.desc() + "\nParty: " + (c.partyMin() == c.partyMax() ? c.partyMin() : c.partyMin() + "-" + c.partyMax())
                    + (c.character() != null ? "\nCharacter: " + c.character().getDisplayName().getString() : "")
                    + (c.noHeal() ? "\nNo healing" : "") + (c.timeLimit() > 0 ? "\nTime limit: " + c.timeLimit() + "s" : "")
                    + "\nArena: " + Vr.ENVS[c.env()].name() + (done ? "\n(cleared - smaller rewards now)" : "\nFirst clear: big rewards");
            b.setTooltip(Tooltip.create(Component.literal(rules)));
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = x0(), y = y0(), h = m.inSession() ? 80 : 60 + 26 + ((Vr.CHALLENGES.length + 1) / 2) * 24 + 10;
        g.fill(x - 2, y - 2, x + W + 2, y + h + 2, 0xFF3AF0A0);
        g.fill(x, y, x + W, y + h, 0xF00C1A16);
        for (int k = 0; k < h; k += 4) g.fill(x, y + k, x + W, y + k + 1, 0x1030FFA0);          // scanlines
        g.drawCenteredString(font, "YURI'S VR ROOM", x + W / 2, y + 8, 0xFF7CFFC8);
        if (m.inSession()) { g.drawCenteredString(font, "You're in VR right now.", x + W / 2, y + 30, 0xFFCCFFE8); super.render(g, mx, my, pt); return; }
        g.drawString(font, "Free training (VR Ultima runs at 20%):", x + 8, y + 26, 0xFFB8F0D8, false);
        g.drawString(font, "Challenges (hover for the rules):", x + 8, y + 26 + 40, 0xFFB8F0D8, false);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}

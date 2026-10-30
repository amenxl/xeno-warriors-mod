package com.xenowarriors.client.you;

import com.xenowarriors.client.journey.JourneyScreen;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.YouActionPacket;
import com.xenowarriors.you.YouSlot;
import net.minecraft.client.gui.GuiGraphics;

/** Your ten You characters: play one, make one in an empty slot, or delete one. */
public class YouSlotsScreen extends JourneyScreen {
    private int confirmDelete = -1;

    public YouSlotsScreen() { super("Your own warriors"); }

    @Override
    protected void draw(GuiGraphics g) {
        sc(g, "YOUR OWN WARRIORS", 14, 10, 1.4F, 0xFFFFFF);
        sc(g, "Warriors made with your own skin. Pick a name, race and element - each keeps its own level and skills.", 14, 26, 0.65F, 0x9AA8B8);
        int cw = 72, ch = 74, gx = 14, gy = 40;
        for (int slot = 0; slot < YouSlot.SLOTS; slot++) {
            final int i = slot;
            int c = i % 5, r = i / 5;
            int x = gx + c * (cw + 4), y = gy + r * (ch + 6);
            YouSlot s = i < YouClient.slots.length ? YouClient.slots[i] : null;
            boolean has = s != null && s.exists(), cur = i == YouClient.current;
            int col = has ? s.color() : 0x3A4450;
            g.fill(x, y, x + cw, y + ch, cur ? 0xF0203040 : 0xE0101820);
            box(g, x, y, x + cw, y + ch, 0xFF000000 | (cur ? ACCENT : col));
            sc(g, "SLOT " + (i + 1), x + 4, y + 3, 0.55F, 0x7A8898);
            if (has) {
                face(g, x + cw / 2 - 14, y + 11, 28, col);
                float k = Math.min(0.8F, (cw - 6) / (float) Math.max(1, font.width(s.name)) * 0.8F);
                sc(g, s.name, x + cw / 2F - font.width(s.name) * k / 2F, y + 41, k, 0xFFFFFF);
                String sub = s.race.title + " • " + s.element.title();
                float k2 = Math.min(0.55F, (cw - 6) / (float) Math.max(1, font.width(sub)) * 0.55F);
                sc(g, sub, x + cw / 2F - font.width(sub) * k2 / 2F, y + 50, k2, 0xFF000000 | col);
                // level in the top-right corner (it used to sit on top of the PLAY button)
                String lv = "Lv " + Math.max(1, i < YouClient.levels.length ? YouClient.levels[i] : 1);
                sc(g, lv, x + cw - 4 - font.width(lv) * 0.55F, y + 3, 0.55F, 0xFFD24A);
                if (cur) sc(g, "PLAYING", x + cw - 4 - font.width("PLAYING") * 0.55F, y + ch - 14, 0.55F, ACCENT);
                else {
                    button(g, x + 3, y + ch - 13, x + cw / 2 - 1, y + ch - 2, "PLAY", ACCENT, true, () -> ModNetwork.sendToServer(new YouActionPacket(YouActionPacket.PLAY, i, null)));
                    boolean ask = confirmDelete == i;
                    button(g, x + cw / 2 + 1, y + ch - 13, x + cw - 3, y + ch - 2, ask ? "SURE?" : "DELETE", ask ? 0xFF5050 : 0x8A6070, true, () -> {
                        if (confirmDelete == i) { ModNetwork.sendToServer(new YouActionPacket(YouActionPacket.DELETE, i, null)); confirmDelete = -1; }
                        else confirmDelete = i;
                    });
                }
            } else {
                sc(g, "empty", x + cw / 2F - font.width("empty") * 0.6F / 2F, y + 26, 0.6F, 0x5A6470);
                button(g, x + 8, y + ch - 30, x + cw - 8, y + ch - 8, "+ CREATE", 0x60D080, true, () -> minecraft.setScreen(new YouCreatorScreen(i)));
            }
        }
        button(g, VW - 70, VH - 22, VW - 10, VH - 6, "BACK", 0x9AA8B8, true, () -> minecraft.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()));
    }

    /** Your own head (your real skin), on a colour. */
    static void face(GuiGraphics g, int x, int y, int size, int col) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        g.fillGradient(x, y, x + size, y + size, 0xFF000000 | mix(col, 0.55F), 0xFF000000 | mix(col, 0.2F));
        var e = mc.player;
        if (e == null) return;
        org.joml.Matrix4f m = g.pose().last().pose();
        org.joml.Vector4f a = m.transform(new org.joml.Vector4f(x, y, 0, 1)), b = m.transform(new org.joml.Vector4f(x + size, y + size, 0, 1));
        int scale = Math.round(size * 1.25F);
        float yb = e.yBodyRot, ybo = e.yBodyRotO, yr = e.getYRot(), xr = e.getXRot(), yh = e.yHeadRot, yho = e.yHeadRotO;
        e.yBodyRot = 180F; e.yBodyRotO = 180F; e.setYRot(180F); e.setXRot(0F); e.yHeadRot = 180F; e.yHeadRotO = 180F;
        g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
        com.xenowarriors.client.ClientCharacterData.beginPreview(com.xenowarriors.character.CharacterType.YOU);
        try {
            org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(new org.joml.Quaternionf().rotateY(-0.35F));
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(g, x + size / 2, Math.round(y + size * 0.55F + 1.55F * scale), scale, pose, null, e);
        } catch (Throwable ignored) {
        } finally {
            com.xenowarriors.client.ClientCharacterData.endPreview();
            g.disableScissor();
            e.yBodyRot = yb; e.yBodyRotO = ybo; e.setYRot(yr); e.setXRot(xr); e.yHeadRot = yh; e.yHeadRotO = yho;
        }
    }

    static int mix(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), gg = (int) (((c >> 8) & 255) * f), bb = (int) ((c & 255) * f);
        return (r << 16) | (gg << 8) | bb;
    }
}

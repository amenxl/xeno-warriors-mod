package com.xenowarriors.client.you;

import com.xenowarriors.client.journey.JourneyScreen;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.YouActionPacket;
import com.xenowarriors.you.YouSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Picking a look for one of your own characters: the outfits show characters gave you (ask them for their gear at
 * bond 50), worn over any armor - or your own look. A preview shows it on you.
 */
public class YouLookScreen extends JourneyScreen {
    private final int slot;
    private final Screen back;
    private final YouSlot s;
    private int scroll;
    private static final int ROWS = 9;

    public YouLookScreen(int slot, YouSlot of, Screen back) {
        super("Looks");
        this.slot = slot;
        this.back = back;
        this.s = of.copy();
    }

    @Override
    protected void draw(GuiGraphics g) {
        sc(g, s.name.toUpperCase() + "'S OUTFIT", 14, 8, 1.3F, 0xFFFFFF);
        sc(g, "Outfits the show's characters gave you (ask them for their gear at bond 50) - worn over any armor.", 14, 21, 0.6F, 0x9AA8B8);
        int col = s.color();
        g.fill(14, 32, 124, 210, 0xE0101820); box(g, 14, 32, 124, 210, 0xFF000000 | col);
        var p = minecraft.player;
        if (p != null) {
            org.joml.Matrix4f m = g.pose().last().pose();
            org.joml.Vector4f a = m.transform(new org.joml.Vector4f(15, 33, 0, 1)), b = m.transform(new org.joml.Vector4f(123, 209, 0, 1));
            g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
            com.xenowarriors.client.ClientCharacterData.beginPreview(com.xenowarriors.character.CharacterType.YOU);
            YouLooks.previewSkin = s.skin;
            YouFeatures.preview = s;
            try { InventoryScreen.renderEntityInInventoryFollowsMouse(g, 69, 196, 62, (float) (69 - vx), (float) (196 - 62 * 1.6F - vy), p); }
            catch (Throwable ignored) {}
            finally { YouFeatures.preview = null; YouLooks.previewSkin = null; com.xenowarriors.client.ClientCharacterData.endPreview(); g.disableScissor(); }
        }
        String[] looks = YouClient.looks;
        int x = 136, w = 250;
        boolean own = s.look == null || s.look.isEmpty();
        button(g, x, 34, x + w, 49, "YOUR OWN LOOK", own ? 0xFFD24A : 0x9AB8D8, true, () -> s.look = "");
        if (own) box(g, x - 1, 33, x + w + 1, 50, 0xFFFFD24A);
        if (looks.length == 0) sc(g, "No outfits yet - ask a character for their gear (their bot's menu).", x, 58, 0.6F, 0x7A8898);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, looks.length - ROWS)));
        for (int i = scroll; i < Math.min(looks.length, scroll + ROWS); i++) {
            String id = looks[i];
            int y = 53 + (i - scroll) * 17;
            boolean on = id.equals(s.look);
            button(g, x, y, x + w, y + 15, com.xenowarriors.you.YouSlots.lookName(id), on ? 0xFFD24A : 0x9AB8D8, true, () -> s.look = id);
            if (on) box(g, x - 1, y - 1, x + w + 1, y + 16, 0xFFFFD24A);
        }
        if (looks.length > ROWS) sc(g, "scroll for more (" + looks.length + ")", x, 53 + ROWS * 17, 0.55F, 0x7A8898);
        button(g, VW - 150, VH - 22, VW - 80, VH - 6, "WEAR IT", 0x60D080, true, () -> {
            YouSlot d = new YouSlot(); d.name = s.name; d.look = s.look == null ? "" : s.look;
            ModNetwork.sendToServer(new YouActionPacket(YouActionPacket.LOOK, slot, d));
            minecraft.setScreen(back);
        });
        button(g, VW - 70, VH - 22, VW - 10, VH - 6, "BACK", 0x9AA8B8, true, () -> minecraft.setScreen(back));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { minecraft.setScreen(back); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** UI test: pick a look (shown in the preview). */
    public void testPick(String look) { s.look = look; }
}

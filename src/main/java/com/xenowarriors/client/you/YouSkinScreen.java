package com.xenowarriors.client.you;

import com.xenowarriors.client.journey.JourneyScreen;
import com.xenowarriors.you.YouSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.List;
import java.util.function.Consumer;

/**
 * Picking one of your own characters' look: your own skin, a Minecraft player's skin (by name), or a skin file from
 * your skins folder ({@code <game folder>/xenowarriors/skins}). The preview shows it on you; DONE hands it back.
 */
public class YouSkinScreen extends JourneyScreen {
    private final String who;
    private final Screen back;
    private final Consumer<String> done;
    private String skin, name = "";
    private boolean typing;
    private List<YouSkinClient.LocalSkin> files;
    private int scroll;
    private String note = "";

    public YouSkinScreen(String who, String current, Screen back, Consumer<String> done) {
        super("Pick a skin");
        this.who = who;
        this.skin = current == null ? "" : current;
        this.back = back;
        this.done = done;
        if (skin.startsWith("p:")) name = skin.substring(2);
        files = YouSkinClient.localSkins();
    }

    @Override
    protected void draw(GuiGraphics g) {
        sc(g, "SKIN FOR " + who.toUpperCase(), 14, 8, 1.2F, 0xFFFFFF);
        sc(g, "Now: " + YouSkins.describe(skin), 14, 21, 0.6F, 0x9AA8B8);
        // the preview: you, in the skin being picked
        g.fill(14, 30, 104, 180, 0xE0101820); box(g, 14, 30, 104, 180, 0xFF000000 | ACCENT);
        var p = minecraft.player;
        if (p != null) {
            org.joml.Matrix4f m = g.pose().last().pose();
            org.joml.Vector4f a = m.transform(new org.joml.Vector4f(15, 31, 0, 1)), b = m.transform(new org.joml.Vector4f(103, 179, 0, 1));
            g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
            com.xenowarriors.client.ClientCharacterData.beginPreview(com.xenowarriors.character.CharacterType.YOU);
            YouLooks.previewSkin = skin;
            try { InventoryScreen.renderEntityInInventoryFollowsMouse(g, 59, 170, 60, (float) (59 - vx), (float) (170 - 60 * 1.6F - vy), p); }
            catch (Throwable ignored) {}
            finally { YouLooks.previewSkin = null; com.xenowarriors.client.ClientCharacterData.endPreview(); g.disableScissor(); }
        }
        if (!skin.isEmpty() && YouSkinClient.look(skin) == null) sc(g, "loading...", 40, 172, 0.55F, 0x9AA8B8);
        int x = 114;
        // your own
        button(g, x, 30, x + 110, 44, "YOUR OWN SKIN", skin.isEmpty() ? 0xFFFFFF : ACCENT, true, () -> { skin = ""; note = ""; });
        // a player's
        sc(g, "A PLAYER'S SKIN (their Minecraft name)", x, 50, 0.6F, 0x9AA8B8);
        g.fill(x, 58, x + 160, 72, 0xF0141C26); box(g, x, 58, x + 160, 72, 0xFF000000 | (typing ? ACCENT : 0x3A4450));
        boolean blink = typing && (System.currentTimeMillis() / 500) % 2 == 0;
        sc(g, name + (blink ? "_" : ""), x + 4, 62, 0.8F, 0xFFFFFF);
        if (name.isEmpty() && !typing) sc(g, "click to type a name", x + 5, 63, 0.6F, 0x4A5460);
        area(x, 58, x + 160, 72, () -> typing = true);
        button(g, x + 164, 58, x + 204, 72, "USE", 0x60D080, YouSkins.validName(name), () -> { skin = "p:" + name; typing = false; note = "Looking up " + name + "'s skin (needs the internet)"; });
        // files
        sc(g, "A SKIN FILE (64x64 PNG in your skins folder)", x, 78, 0.6F, 0x9AA8B8);
        int rows = 7, y0 = 86;
        for (int i = 0; i < rows; i++) {
            int idx = i + scroll;
            if (idx >= files.size()) break;
            var f = files.get(idx);
            int y = y0 + i * 13;
            boolean on = f.problem() == null && skin.equals("f:" + f.sha());
            String label = f.name() + (f.problem() != null ? "  - " + f.problem() : "");
            g.fill(x, y, VW - 14, y + 12, on ? 0xF0203040 : 0xE0101820);
            box(g, x, y, VW - 14, y + 12, 0xFF000000 | (on ? 0xFFFFFF : f.problem() == null ? 0x3A5470 : 0x5A3A3A));
            sc(g, label.length() > 52 ? label.substring(0, 52) + "..." : label, x + 4, y + 3, 0.6F, f.problem() == null ? 0xDDE6EE : 0xA07070);
            if (f.problem() == null) area(x, y, VW - 14, y + 12, () -> { skin = YouSkinClient.use(f); note = "Added " + f.name() + " - the server keeps a copy so friends see it too"; });
        }
        if (files.isEmpty()) sc(g, "No skin files yet - put a skin PNG in your skins folder, then REFRESH.", x, y0 + 2, 0.55F, 0x7A8898);
        if (files.size() > rows) {
            button(g, VW - 40, y0 + rows * 13 + 2, VW - 28, y0 + rows * 13 + 14, "^", 0x9AA8B8, scroll > 0, () -> scroll--);
            button(g, VW - 26, y0 + rows * 13 + 2, VW - 14, y0 + rows * 13 + 14, "v", 0x9AA8B8, scroll + rows < files.size(), () -> scroll++);
        }
        int by = y0 + rows * 13 + 2;
        button(g, x, by, x + 76, by + 12, "OPEN FOLDER", ACCENT, true, YouSkinClient::openFolder);
        button(g, x + 80, by, x + 140, by + 12, "REFRESH", ACCENT, true, () -> { files = YouSkinClient.localSkins(); scroll = 0; });
        if (!note.isEmpty()) sc(g, note, x, by + 16, 0.55F, 0x9AA8B8);
        if (!YouSkinClient.lastProblem.isEmpty() && skin.startsWith("p:") && YouSkinClient.look(skin) == null && YouSkinClient.lastProblem.contains(skin.substring(2)))
            sc(g, "Couldn't get that skin: " + YouSkinClient.lastProblem, x, by + 24, 0.55F, 0xFF7070);
        button(g, VW - 150, VH - 22, VW - 80, VH - 6, "DONE", 0x60D080, true, () -> { done.accept(skin); });
        button(g, VW - 70, VH - 22, VW - 10, VH - 6, "BACK", 0x9AA8B8, true, () -> minecraft.setScreen(back));
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (typing && name.length() < 16 && (Character.isLetterOrDigit(c) || c == '_') && c < 128) { name += c; return true; }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (typing && key == 259 && !name.isEmpty()) { name = name.substring(0, name.length() - 1); return true; }
        if (typing && (key == 257 || key == 335)) { if (YouSkins.validName(name)) { skin = "p:" + name; note = "Looking up " + name + "'s skin (needs the internet)"; } typing = false; return true; }
        if (key == 256) { minecraft.setScreen(back); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** (Tests) pick this skin. */
    public void testPick(String s) { skin = s; }
    public String picked() { return skin; }
}

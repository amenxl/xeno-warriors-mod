package com.xenowarriors.client.you;

import com.xenowarriors.client.journey.JourneyScreen;
import com.xenowarriors.client.compat.YouTails;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.YouActionPacket;
import com.xenowarriors.you.YouSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * A made Fox Kin's tail or a made Skylander's wings: the style and colours (each wing its own), with a preview you
 * can turn with the mouse. The first pick is free; after that each change takes a wish on the Dragon Balls
 * ("Restyle my tail or wings").
 */
public class YouFeatureScreen extends JourneyScreen {
    private final int slot;
    private final Screen back;
    private final YouSlot s;          // (a copy being styled)
    private final boolean locked;

    public YouFeatureScreen(int slot, YouSlot of, Screen back) {
        super("Tail and wings");
        this.slot = slot;
        this.back = back;
        this.s = of.copy();
        this.locked = of.featuresSet && of.restyles <= 0;
    }

    private static String hex(int c, String usual) { return c < 0 ? usual : String.format("#%06X", c); }

    @Override
    protected void draw(GuiGraphics g) {
        boolean fox = s.foxTail();
        String what = fox ? "TAIL" : "WINGS";
        sc(g, s.name.toUpperCase() + "'S " + what, 14, 8, 1.3F, 0xFFFFFF);
        String note = !s.featuresSet ? "Pick once for free - after that, changing it takes a wish on the Dragon Balls."
                : s.restyles > 0 ? "A wish granted: you can change it " + s.restyles + " more time" + (s.restyles == 1 ? "" : "s") + "."
                : "Set. Wish on the Dragon Balls (\"Restyle my tail or wings\") to change it again.";
        sc(g, note, 14, 21, 0.6F, locked ? 0xFF9A7A : 0x9AA8B8);
        int col = s.color();
        // the preview (turn it with the mouse)
        g.fill(14, 32, 124, 196, 0xE0101820); box(g, 14, 32, 124, 196, 0xFF000000 | col);
        var p = minecraft.player;
        if (p != null) {
            org.joml.Matrix4f m = g.pose().last().pose();
            org.joml.Vector4f a = m.transform(new org.joml.Vector4f(15, 33, 0, 1)), b = m.transform(new org.joml.Vector4f(123, 195, 0, 1));
            g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
            com.xenowarriors.client.ClientCharacterData.beginPreview(com.xenowarriors.character.CharacterType.YOU);
            YouLooks.previewSkin = s.skin;
            YouFeatures.preview = s;
            float yb = p.yBodyRot, ybo = p.yBodyRotO, yr = p.getYRot(), xr = p.getXRot(), yh = p.yHeadRot, yho = p.yHeadRotO;
            p.yBodyRot = 180F; p.yBodyRotO = 180F; p.setYRot(180F); p.setXRot(0F); p.yHeadRot = 180F; p.yHeadRotO = 180F;
            try {
                float turn = (rear ? (float) Math.PI + 0.55F : -0.4F) + (float) ((vx - 69) / 120.0);   // (the mouse turns it a little more)
                org.joml.Quaternionf q = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(new org.joml.Quaternionf().rotateY(turn));
                InventoryScreen.renderEntityInInventory(g, 69, 186, 58, q, null, p);
            } catch (Throwable ignored) {
            } finally {
                p.yBodyRot = yb; p.yBodyRotO = ybo; p.setYRot(yr); p.setXRot(xr); p.yHeadRot = yh; p.yHeadRotO = yho;
                YouFeatures.preview = null; YouLooks.previewSkin = null; com.xenowarriors.client.ClientCharacterData.endPreview(); g.disableScissor();
            }
        }
        button(g, 14, 198, 124, 210, rear ? "SHOW FRONT" : "SHOW BACK", 0x9AB8D8, true, () -> rear = !rear);

        int x = 136, w = 250;
        if (fox) {
            sc(g, "STYLE", x, 34, 0.65F, 0x9AA8B8);
            for (int i = 0; i < YouSlot.TAILS.length; i++) {
                int bx = x + (i % 3) * 84, by = 44 + (i / 3) * 18;
                final int k = i;
                button(g, bx, by, bx + 80, by + 15, YouSlot.TAILS[i], s.tail == i ? 0xFFD24A : 0x9AB8D8, !locked, () -> s.tail = k);
                if (s.tail == i) box(g, bx - 1, by - 1, bx + 81, by + 16, 0xFFFFD24A);
            }
            colour(g, x, 92, "TAIL COLOUR", s.tailColour, YouTails.BASE, "THE USUAL RED", c -> s.tailColour = c);
            colour(g, x, 122, "TIP COLOUR", s.tipColour, YouTails.TIP, "THE USUAL WHITE", c -> s.tipColour = c);
        } else {
            sc(g, "WINGS", x, 34, 0.65F, 0x9AA8B8);
            for (int i = -1; i < YouSlot.WINGS.length; i++) {
                int j = i + 1, bx = x + (j % 4) * 63, by = 44 + (j / 4) * 17;
                final int k = i;
                String label = i < 0 ? "Usual" : YouSlot.WING_NAMES[i];
                button(g, bx, by, bx + 60, by + 14, label, s.wings == i ? 0xFFD24A : 0x9AB8D8, !locked, () -> s.wings = k);
                if (s.wings == i) box(g, bx - 1, by - 1, bx + 61, by + 15, 0xFFFFD24A);
            }
            colour(g, x, 100, "LEFT WING", s.wingL, 0xFFFFFF, "THE USUAL TINT", c -> s.wingL = c);
            colour(g, x, 130, "RIGHT WING", s.wingR, 0xFFFFFF, "THE USUAL TINT", c -> s.wingR = c);
            button(g, x + 170, 160, x + w, 174, "SAME FOR BOTH", 0x9AB8D8, !locked, () -> s.wingR = s.wingL);
        }
        button(g, VW - 150, VH - 22, VW - 80, VH - 6, s.featuresSet ? "SAVE (USES A WISH)" : "SAVE", 0x60D080, !locked, () -> {
            YouSlot d = new YouSlot(); d.name = s.name;
            d.tail = s.tail; d.tailColour = s.tailColour; d.tipColour = s.tipColour;
            d.wings = s.wings; d.wingL = s.wingL; d.wingR = s.wingR;
            ModNetwork.sendToServer(new YouActionPacket(YouActionPacket.FEATURES, slot, d));
            minecraft.setScreen(back);
        });
        button(g, VW - 70, VH - 22, VW - 10, VH - 6, "BACK", 0x9AA8B8, true, () -> minecraft.setScreen(back));
    }

    /** Turned round to show the back (tails and wings are behind). */
    private boolean rear = true;

    /** A colour row: a swatch with its value, and a button to pick it. */
    private void colour(GuiGraphics g, int x, int y, String label, int c, int usual, String usualName, java.util.function.IntConsumer set) {
        sc(g, label, x, y, 0.65F, 0x9AA8B8);
        int shown = c < 0 ? usual : c;
        g.fill(x, y + 10, x + 40, y + 24, 0xFF000000 | shown); box(g, x, y + 10, x + 40, y + 24, 0xFF000000);
        sc(g, hex(c, usualName.toLowerCase().replace("the ", "")), x + 46, y + 14, 0.6F, 0xE0E8F0);
        button(g, x + 170, y + 10, x + 250, y + 24, "PICK", 0x9AB8D8, !locked, () -> minecraft.setScreen(new YouColourScreen(s.name, c, usual, this, picked -> {
            set.accept(picked);
            minecraft.setScreen(this);
        }).usual(usualName, usualName.toLowerCase(), label.charAt(0) + label.substring(1).toLowerCase())));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { minecraft.setScreen(back); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** UI test: pick these and save. */
    public void testPick(int tail, int tailColour, int tipColour, int wings, int wingL, int wingR) {
        s.tail = tail; s.tailColour = tailColour; s.tipColour = tipColour; s.wings = wings; s.wingL = wingL; s.wingR = wingR;
    }
    public YouSlot testSlot() { return s; }
    public void testRear(boolean b) { rear = b; }
}

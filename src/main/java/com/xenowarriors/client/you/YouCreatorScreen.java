package com.xenowarriors.client.you;

import com.xenowarriors.character.Race;
import com.xenowarriors.client.journey.JourneyScreen;
import com.xenowarriors.ka.Element;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.YouActionPacket;
import com.xenowarriors.you.YouSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.List;

/** The character creator: your Minecraft skin, a name, a race (with what it means), an element, and Lunar/Solar for Skylanders. */
public class YouCreatorScreen extends JourneyScreen {
    private final int slot;
    private String name = "";
    private Race race = Race.HUMAN;
    private Element element = Element.FIRE;
    private int form = YouSlot.FORM_LUNAR;
    private String error = "";

    public YouCreatorScreen(int slot) { super("Create your warrior"); this.slot = slot; }

    private boolean creative() { return minecraft.player != null && minecraft.player.isCreative(); }

    @Override
    protected void draw(GuiGraphics g) {
        sc(g, "CREATE YOUR WARRIOR", 14, 8, 1.3F, 0xFFFFFF);
        sc(g, "Slot " + (slot + 1), VW - 14 - font.width("Slot " + (slot + 1)) * 0.7F, 12, 0.7F, 0x7A8898);
        int col = element.color;
        // the preview: you, in your own skin
        g.fill(14, 26, 104, 170, 0xE0101820); box(g, 14, 26, 104, 170, 0xFF000000 | col);
        var p = minecraft.player;
        if (p != null) {
            org.joml.Matrix4f m = g.pose().last().pose();
            org.joml.Vector4f a = m.transform(new org.joml.Vector4f(15, 27, 0, 1)), b = m.transform(new org.joml.Vector4f(103, 169, 0, 1));
            g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
            com.xenowarriors.client.ClientCharacterData.beginPreview(com.xenowarriors.character.CharacterType.YOU);
            try { InventoryScreen.renderEntityInInventoryFollowsMouse(g, 59, 160, 58, (float) (59 - vx), (float) (160 - 58 * 1.6F - vy), p); }
            catch (Throwable ignored) {}
            finally { com.xenowarriors.client.ClientCharacterData.endPreview(); g.disableScissor(); }
        }
        // name
        sc(g, "NAME", 112, 28, 0.65F, 0x9AA8B8);
        g.fill(112, 36, 232, 50, 0xF0141C26); box(g, 112, 36, 232, 50, 0xFF000000 | (name.isEmpty() ? 0x3A4450 : col));
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        sc(g, (name.isEmpty() ? "" : name) + (blink ? "_" : ""), 116, 40, 0.8F, name.isEmpty() ? 0x6A7480 : 0xFFFFFF);
        if (name.isEmpty()) sc(g, "type a name", 118, 41, 0.65F, 0x4A5460);
        // race
        sc(g, "RACE", 112, 54, 0.65F, 0x9AA8B8);
        int rx = 112, ry = 62;
        for (Race r : Race.CREATABLE) {
            boolean ok = r != Race.ANGEL || creative(), on = r == race;
            int w = Math.round(font.width(r.title) * 0.65F) + 8;
            if (rx + w > VW - 12) { rx = 112; ry += 14; }
            int x0 = rx, y0 = ry;
            g.fill(x0, y0, x0 + w, y0 + 12, on ? 0xFF000000 | mix(r.color, 0.45F) : 0xF0141C26);
            box(g, x0, y0, x0 + w, y0 + 12, ok ? 0xFF000000 | (on ? 0xFFFFFF : r.color) : 0xFF3A3A44);
            sc(g, r.title, x0 + 4, y0 + 3, 0.65F, ok ? (on ? 0xFFFFFF : 0xC8D0D8) : 0x5A5A64);
            if (ok) area(x0, y0, x0 + w, y0 + 12, () -> { race = r; if (!List.of(r.elements()).contains(element)) element = r.elements()[0]; error = ""; });
            rx += w + 3;
        }
        // race info
        int iy = ry + 16;
        g.fill(112, iy, VW - 12, iy + 40, 0xC00C1218);
        box(g, 112, iy, VW - 12, iy + 40, 0xFF000000 | mix(race.color, 0.6F));
        sc(g, race.title.toUpperCase() + (race == Race.ANGEL ? "  (creative only)" : ""), 116, iy + 3, 0.65F, 0xFF000000 | race.color);
        int ty = iy + 12;
        for (var line : font.split(net.minecraft.network.chat.Component.literal(race.info()), (int) ((VW - 12 - 116 - 4) / 0.55F))) {
            if (ty > iy + 36) break;
            g.pose().pushPose(); g.pose().translate(116, ty, 0); g.pose().scale(0.55F, 0.55F, 1F);
            g.drawString(font, line, 0, 0, 0xFFD0D8E0, false); g.pose().popPose();
            ty += 6;
        }
        // element
        int ey = iy + 44;
        sc(g, "ELEMENT", 112, ey, 0.65F, 0x9AA8B8);
        int ex = 112; ey += 8;
        for (Element e : race.elements()) {
            boolean on = e == element;
            int w = Math.round(font.width(e.title()) * 0.6F) + 8;
            if (ex + w > VW - 12) { ex = 112; ey += 13; }
            int x0 = ex, y0 = ey;
            g.fill(x0, y0, x0 + w, y0 + 11, on ? 0xFF000000 | mix(e.color, 0.5F) : 0xF0141C26);
            box(g, x0, y0, x0 + w, y0 + 11, 0xFF000000 | (on ? 0xFFFFFF : e.color));
            sc(g, e.title(), x0 + 4, y0 + 3, 0.6F, on ? 0xFFFFFF : 0xC8D0D8);
            area(x0, y0, x0 + w, y0 + 11, () -> element = e);
            ex += w + 3;
        }
        // Skylander form
        if (race == Race.SKYLANDER) {
            int fy = ey + 15;
            sc(g, "FORM", 112, fy, 0.65F, 0x9AA8B8);
            button(g, 112, fy + 8, 162, fy + 20, "LUNAR", form == YouSlot.FORM_LUNAR ? 0xFFFFFF : 0xB0C0FF, true, () -> form = YouSlot.FORM_LUNAR);
            button(g, 166, fy + 8, 216, fy + 20, "SOLAR", form == YouSlot.FORM_SOLAR ? 0xFFFFFF : 0xFFC860, true, () -> form = YouSlot.FORM_SOLAR);
            sc(g, form == YouSlot.FORM_LUNAR ? "Stronger at night" : "Stronger by day", 220, fy + 11, 0.55F, 0x9AA8B8);
        }
        // starter skills
        int sy = VH - 44;
        sc(g, "YOU START WITH", 14, sy, 0.6F, 0x9AA8B8);
        YouSlot tmp = new YouSlot(); tmp.name = "x"; tmp.race = race; tmp.element = element; tmp.form = form;
        var pool = com.xenowarriors.you.YouSkills.pool(tmp);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, pool.size()); i++) sb.append(i > 0 ? ", " : "").append(pool.get(i).skill().getName().getString());
        sb.append("  +  ").append(com.xenowarriors.you.YouSkills.ultimate(tmp).getName().getString()).append(" (ultimate)");
        sc(g, sb.toString(), 14, sy + 8, 0.55F, 0xFF000000 | col);
        sc(g, "More skills unlock as you level: " + (pool.size() - 4) + " to learn.", 14, sy + 16, 0.5F, 0x7A8898);
        if (!error.isEmpty()) sc(g, error, 14, VH - 16, 0.6F, 0xFF6060);
        boolean can = YouSlot.validName(name.trim());
        button(g, VW - 150, VH - 22, VW - 80, VH - 6, "CREATE", 0x60D080, can, () -> {
            YouSlot s = new YouSlot(); s.name = name.trim(); s.race = race; s.element = element; s.form = race == Race.SKYLANDER ? form : YouSlot.FORM_NONE;
            ModNetwork.sendToServer(new YouActionPacket(YouActionPacket.CREATE, slot, s));
        });
        button(g, VW - 70, VH - 22, VW - 10, VH - 6, "BACK", 0x9AA8B8, true, () -> minecraft.setScreen(new YouSlotsScreen()));
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (name.length() < 16 && (Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '\'' || c == '-') && c < 128) { name += c; error = ""; return true; }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 259 && !name.isEmpty()) { name = name.substring(0, name.length() - 1); return true; }   // backspace
        if (key == 257 || key == 335) {                                                                      // enter
            if (YouSlot.validName(name.trim())) {
                YouSlot s = new YouSlot(); s.name = name.trim(); s.race = race; s.element = element; s.form = race == Race.SKYLANDER ? form : YouSlot.FORM_NONE;
                ModNetwork.sendToServer(new YouActionPacket(YouActionPacket.CREATE, slot, s));
            } else error = "Names are 2-16 letters, numbers, spaces, ' - or _";
            return true;
        }
        if (key == 256) { minecraft.setScreen(new YouSlotsScreen()); return true; }
        return super.keyPressed(key, scan, mods);
    }

    static int mix(int c, float f) { return YouSlotsScreen.mix(c, f); }

    /** UI test: fill the creator in. */
    public void testFill(String n, Race r, Element e) { name = n; race = r; element = e; }
}

package com.xenowarriors.client.menu;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ModKeyMappings;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.Reputation;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RepPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Factions (from the main menu): your standing with the Government, the Sky Rebellion, the Sky Patrol & Skylanders
 * and the Townsfolk, as the character you're playing - a bar from Hated to Revered, what it means right now, and
 * what moves it. Laid out on a 400x225 grid scaled to the window, in your character's colour.
 */
public class FactionsScreen extends Screen {
    private static final int VW = 400, VH = 225;
    private final Screen parent;
    private Boolean hudWas;
    private long opened = net.minecraft.Util.getMillis();

    public FactionsScreen(Screen parent) { super(Component.literal("Factions")); this.parent = parent; }

    /** The faction whose "how to raise / lower" page is open, or null for the list. */
    private Faction detail;

    /** UI test: open a faction's how-to page (null = back to the list). */
    public void testDetail(Faction f) { detail = f; opened = net.minecraft.Util.getMillis(); }

    public static void open(Screen parent) {
        RepPacket.CLIENT = new int[0];                     // (don't show the last character's numbers while we wait)
        Minecraft.getInstance().setScreen(new FactionsScreen(parent));
        ModNetwork.sendToServer(new RepPacket(new int[0]));
    }

    @Override protected void init() { if (hudWas == null) { hudWas = minecraft.options.hideGui; minecraft.options.hideGui = true; } }
    @Override public void removed() { if (hudWas != null) { minecraft.options.hideGui = hudWas; hudWas = null; } }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.3F));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) { minecraft.setScreen(null); return true; }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && detail != null) { testDetail(null); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private float scale() { return Math.min(width / (float) VW, height / (float) VH); }
    private float[] origin() { float s = scale(); return new float[]{(width - VW * s) / 2F, (height - VH * s) / 2F}; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float s = scale(); float[] o = origin();
        double vx = (mx - o[0]) / s, vy = (my - o[1]) / s;
        if (vx < 30 && vy < 22) { if (detail != null) testDetail(null); else onClose(); return true; }
        if (detail != null) { testDetail(null); return true; }           // (anywhere on the page: back to the list)
        for (Faction f : Faction.values()) {
            int y = 28 + f.ordinal() * 48;
            if (vx >= 8 && vx < VW - 8 && vy >= y && vy < y + 44) { testDetail(f); click(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** What your standing means right now. */
    private static String effect(Faction f, Reputation.Tier t) {
        boolean hostile = !t.atLeast(Reputation.Tier.UNFRIENDLY), friendly = t.atLeast(Reputation.Tier.FRIENDLY);
        return switch (f) {
            case GOV -> hostile ? "Guards attack on sight. The Supply Terminal refuses you."
                    : t == Reputation.Tier.UNFRIENDLY ? "+15% at the Supply Terminal."
                    : !friendly ? "Normal Supply Terminal prices."
                    : (t == Reputation.Tier.FRIENDLY ? "-10%" : t == Reputation.Tier.HONORED ? "-20%" : "-30%") + " at the Supply Terminal. No bounties on Gov agents.";
            case SR -> friendly ? "SR members leave you alone. No bounties on the Rebellion."
                    : "SR members attack on sight (an SR hoodie hides you).";
            case SKY -> hostile ? "Skylanders attack you on sight."
                    : friendly ? "Skylanders are glad to see you." : "Skylanders only fight you if you threaten their homes.";
            case TOWN -> hostile ? "Villagers won't trade, fighters attack you, no bounty work."
                    : t == Reputation.Tier.UNFRIENDLY ? "Worse villager prices, -25% bounty pay."
                    : !friendly ? "Normal prices and bounty pay."
                    : "Better villager prices, " + (t == Reputation.Tier.FRIENDLY ? "+25%" : t == Reputation.Tier.HONORED ? "+50%" : "+75%") + " bounty pay.";
        };
    }

    private static String moves(Faction f) {
        return switch (f) {
            case GOV -> "Down: fighting Gov agents, getting caught.  Up: donating / returning gear at a terminal, defending agents, bounties, fighting the Rebellion.";
            case SR -> "Down: fighting SR members.  Up: fighting Gov agents.";
            case SKY -> "Down: hurting Skylanders.  Up: stopping Sky Hater sieges and raiders, fighting the Rebellion.";
            case TOWN -> "Down: hurting townsfolk or villagers.  Up: bounties, defending raids, trading, clearing corruption.";
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderMenu(g, mx, my);
        MenuInterrupt.warning(g, width, height);
    }

    private void renderMenu(GuiGraphics g, int mx, int my) {
        CharacterType type = ClientCharacterData.getLocal();
        int col = type == CharacterType.NONE ? 0x8090A0 : type.getColor() & 0xFFFFFF;
        g.fillGradient(0, 0, width, height, 0xF0101016, 0xF0060608);
        float s = scale(); float[] o = origin();
        double vx = (mx - o[0]) / s, vy = (my - o[1]) / s;
        g.pose().pushPose();
        g.pose().translate(o[0], o[1], 0);
        g.pose().scale(s, s, 1F);
        // header
        g.fill(4, 4, 26, 20, 0xFF16161C);
        box(g, 4, 4, 26, 20, 0xFF000000 | col);
        g.drawString(font, "↶", 11, 8, vx < 30 && vy < 22 ? 0xFFFFFF : col, false);
        g.fill(30, 4, 30 + font.width("FACTIONS") * 2 + 12, 20, 0xFF16161C);
        sc(g, "FACTIONS", 36, 5.5F, 2F, 0xFFFFFF);
        String who = type == CharacterType.NONE ? "" : "As " + type.getDisplayName().getString() + " - each character has their own standing";
        sc(g, who, VW - 6 - font.width(who) * 0.6F, 10, 0.6F, 0x9A9AAA);

        int[] v = RepPacket.CLIENT;
        if (detail != null) { detailPage(g, detail, v, vx, vy); g.pose().popPose(); return; }
        float anim = Math.min(1F, (net.minecraft.Util.getMillis() - opened) / 500F);
        for (Faction f : Faction.values()) {
            int i = f.ordinal();
            int y = 28 + i * 48;
            float slide = (1F - Math.min(1F, Math.max(0F, anim * 4 - i * 0.5F))) * 30F;
            g.pose().pushPose();
            g.pose().translate(slide, 0, 0);
            int val = i < v.length ? v[i] : 0;
            Reputation.Tier t = Reputation.Tier.of(val);
            g.fill(8, y, VW - 8, y + 44, 0xF0181820);
            g.fill(8, y, 11, y + 44, 0xFF000000 | f.color);
            // logo, or an emblem with the initial
            if (f.logo != null) g.blit(new ResourceLocation(com.xenowarriors.XenoWarriors.MOD_ID, "textures/gui/" + f.logo + ".png"), 15, y + 4, 64, 32, 0, 0, 256, 128, 256, 128);
            else { g.fill(31, y + 6, 61, y + 36, 0xFF000000 | f.color); sc(g, "T", 40, y + 11, 2.4F, 0x101010); }
            g.drawString(font, f.title, 84, y + 4, 0xFFFFFF, false);
            String tt = t.title + "  (" + val + ")";
            g.drawString(font, tt, VW - 14 - font.width(tt), y + 4, 0xFF000000 | t.color, false);
            // bar: Hated ... Revered, centre line at 0
            int bx = 84, bw = VW - 100, by = y + 16;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF000000);
            int[] edges = {-1000, -600, -150, -50, 50, 400, 750, 1000};
            int[] cols = {0xB01010, 0xE04040, 0xE09040, 0x707078, 0x5FC05F, 0x30C0A0, 0xE0B030};
            for (int k = 0; k < 7; k++) {
                int x0 = bx + Math.round((edges[k] + 1000) / 2000F * bw), x1 = bx + Math.round((edges[k + 1] + 1000) / 2000F * bw);
                g.fill(x0, by, x1, by + 5, 0x60000000 | cols[k]);
            }
            int mid = bx + bw / 2, at = bx + Math.round((val + 1000) / 2000F * bw * anim + (1 - anim) * bw / 2F);
            g.fill(Math.min(mid, at), by, Math.max(mid, at), by + 5, 0xFF000000 | t.color);
            g.fill(mid, by - 2, mid + 1, by + 7, 0xFFFFFFFF);
            g.fill(at - 1, by - 3, at + 2, by + 8, 0xFFFFFFFF);
            sc(g, effect(f, t), 84, y + 26, 0.7F, 0xE0E0E8);
            sc(g, moves(f), 84, y + 35, 0.6F, 0x8A8A9A);
            boolean hov = vx >= 8 && vx < VW - 8 && vy >= y && vy < y + 44;
            String more = "How to raise / lower \u25b8";
            sc(g, more, VW - 14 - font.width(more) * 0.6F, y + 26, 0.6F, hov ? 0xFFFFFF : 0x000000 | light(f.color));
            if (hov) box(g, 8, y, VW - 8, y + 44, 0xFF000000 | f.color);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    private static int light(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r + (255 - r) / 3) << 16) | ((g + (255 - g) / 3) << 8) | (b + (255 - b) / 3);
    }

    /** One faction's page: where you stand, what it means, and every way to raise or lower it (from faction/RepRules). */
    private void detailPage(GuiGraphics g, Faction f, int[] v, double vx, double vy) {
        int val = f.ordinal() < v.length ? v[f.ordinal()] : 0;
        Reputation.Tier t = Reputation.Tier.of(val);
        float in = Math.min(1F, (net.minecraft.Util.getMillis() - opened) / 250F);
        g.pose().pushPose();
        g.pose().translate((1F - in) * 24F, 0, 0);
        g.fill(8, 26, VW - 8, VH - 6, 0xF0181820);
        g.fill(8, 26, 11, VH - 6, 0xFF000000 | f.color);
        if (f.logo != null) g.blit(new ResourceLocation(com.xenowarriors.XenoWarriors.MOD_ID, "textures/gui/" + f.logo + ".png"), 15, 30, 64, 32, 0, 0, 256, 128, 256, 128);
        sc(g, f.title.toUpperCase(java.util.Locale.ROOT), 84, 31, 1.4F, 0xFFFFFF);
        String tt = t.title + "  (" + val + ")";
        g.drawString(font, tt, VW - 14 - font.width(tt), 32, 0xFF000000 | t.color, false);
        sc(g, effect(f, t), 84, 46, 0.7F, 0xE0E0E8);
        int y = 68;
        y = list(g, "HOW TO RAISE IT", com.xenowarriors.faction.RepRules.lines(f, true), y, 0x7FE07F);
        y = list(g, "HOW TO LOWER IT (what makes them hate you)", com.xenowarriors.faction.RepRules.lines(f, false), y + 4, 0xFF7070);
        sc(g, "Tiers: Hated  -600  Hostile  -150  Unfriendly  -50  Neutral  +50  Friendly  +400  Honored  +750  Revered   (-1000 to 1000, per character)", 14, VH - 24, 0.55F, 0x8A8A9A);
        sc(g, "Click or Esc: back to all factions", 14, VH - 15, 0.6F, 0x9A9AAA);
        g.pose().popPose();
    }

    private int list(GuiGraphics g, String head, java.util.List<String> lines, int y, int color) {
        sc(g, head, 14, y, 0.75F, color);
        y += 9;
        if (lines.isEmpty()) { sc(g, "-", 18, y, 0.65F, 0x8A8A9A); return y + 8; }
        for (String l : lines) {
            for (var part : font.split(Component.literal(l), (int) ((VW - 34) / 0.65F))) {
                if (y > VH - 32) return y;
                g.pose().pushPose();
                g.pose().translate(18, y, 0);
                g.pose().scale(0.65F, 0.65F, 1F);
                g.drawString(font, part, 0, 0, 0xE0E0E8, false);
                g.pose().popPose();
                y += 7;
            }
        }
        return y;
    }

    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    private void sc(GuiGraphics g, String s, float x, float y, float k, int c) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1F);
        g.drawString(font, s, 0, 0, c, false);
        g.pose().popPose();
    }
}

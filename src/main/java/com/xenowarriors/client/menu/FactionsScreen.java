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

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new FactionsScreen(parent));
        ModNetwork.sendToServer(new RepPacket(new int[0]));
    }

    @Override protected void init() { if (hudWas == null) { hudWas = minecraft.options.hideGui; minecraft.options.hideGui = true; } }
    @Override public void removed() { if (hudWas != null) { minecraft.options.hideGui = hudWas; hudWas = null; } }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) { minecraft.setScreen(null); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private float scale() { return Math.min(width / (float) VW, height / (float) VH); }
    private float[] origin() { float s = scale(); return new float[]{(width - VW * s) / 2F, (height - VH * s) / 2F}; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float s = scale(); float[] o = origin();
        if ((mx - o[0]) / s < 30 && (my - o[1]) / s < 22) { onClose(); return true; }
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
            case GOV -> "Down: fighting Gov agents, getting caught.  Up: Terminal purchases, fighting the Rebellion.";
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
            g.pose().popPose();
        }
        g.pose().popPose();
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

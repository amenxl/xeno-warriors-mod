package com.xenowarriors.client.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientMoney;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.KaGuideScreen;
import com.xenowarriors.client.ModKeyMappings;
import com.xenowarriors.client.SkillLoadoutScreen;
import com.xenowarriors.client.UltimaAnim;
import com.xenowarriors.client.cel.Cel;
import com.xenowarriors.client.portrait.PortraitScreen;
import com.xenowarriors.ka.Ka;
import com.xenowarriors.mikiko.BladeLevel;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PlayerStatsPacket;
import com.xenowarriors.network.PlayerStatsRequestPacket;
import com.xenowarriors.network.SetSkillSlotPacket;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.xenowarriors.client.portrait.PortraitScreen.*;

/**
 * Character Customization (was "My Warrior"): your character big on the left, and an info card on the right with tabs
 * along the bottom - Stats, Skills (your loadout, picked right here), Outfits and KAs (only when you have one). The UI takes
 * your character's colour. The card is laid out on a fixed 220x184 grid and scaled to fit the window.
 */
public class CharacterCustomScreen extends Screen {
    private static final int VW = 220, VH = 184;
    private final Screen parent;
    private int tab;                       // 0 stats, 1 skills, 2 outfits, 3 KA
    private PlayerStatsPacket.Entry self;
    private PlayerStatsPacket.Me me;
    private int refresh, selectedSlot, skillScroll;

    public CharacterCustomScreen(Screen parent) {
        super(Component.literal("Character Customization"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new CharacterCustomScreen(parent));
        ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    /** (UI test) show this tab. */
    public void tab(int t) { setTab(t); }

    // ---- animation (like the agent screen in the user's reference): things slide in on open, the film strips scroll,
    // the tab highlight glides between tabs, and a new tab's content slides in with its rows one after another
    private final long opened = net.minecraft.Util.getMillis();
    private long tabAt = opened;
    private int tabDir = 1;
    private float pillX = -1, pillW;

    /** UI test: switch tabs. */
    public void testTab(int t) { setTab(Math.min(t, tabs() - 1)); }

    private void setTab(int t) {
        if (t == tab) return;
        camFrom = camNow.clone();
        tabDir = t > tab ? 1 : -1;
        tab = t;
        tabAt = net.minecraft.Util.getMillis();
        camAt = tabAt;
    }

    // ---- the "camera" on the character, one framing per tab (like the agent screen in the user's video): full body for
    // Stats, a low close angle for Skills, over the shoulder for Outfits, a dramatic front angle for KAs / Last resort.
    // It swoops from one to the next; on opening it starts close and pulls back.
    // {x (fraction of width), feet y (fraction of height), scale (fraction of height), yaw degrees, pitch}
    private static final float[][] CAMS = {
            {0.25F, 1.13F, 0.52F, 22F, -0.06F},
            {0.27F, 1.27F, 0.55F, 38F, 0.22F},         // Skills: close, but the whole head stays on screen
            {0.25F, 0.95F, 0.41F, -20F, -0.04F},       // Outfits: always the full body, so the whole outfit shows
            {0.27F, 1.22F, 0.55F, -16F, 0.10F}};
    private float[] camFrom = {0.30F, 1.70F, 1.05F, 70F, 0.05F};
    private final float[] camNow = CAMS[0].clone();
    private long camAt = opened;

    /** The character's pose on each tab (null = their idle animation), with a little breathing sway. */
    private float[] tabPose(int tab, float t) {
        float s = Mth.sin(t * 2.2F) * 0.03F;
        return switch (tab) {
            case 1 -> new float[]{.22F + s, .25F, 0F, 0F, .25F, -1.15F + s, .2F, 1.0F, .2F, -.6F, -.75F, -.15F, .15F, .15F, -.12F, -.3F, -1.6F, .12F, .1F};   // presenting a move
            case 3 -> new float[]{.15F, 0F, 0F, .1F, -.4F, .35F + s, -.9F, .25F, .25F - s, -.4F, .1F, .5F, .15F, -.6F, -.12F, -1.2F, -1.4F, .2F, .7F};  // gathering power
            default -> null;
        };
    }

    private static float ease(float x) { x = Mth.clamp(x, 0F, 1F); return 1F - (1F - x) * (1F - x) * (1F - x); }

    /** 0..1 for a row of the current tab sliding in (staggered by index). */
    private float rowIn(int i) { return ease((net.minecraft.Util.getMillis() - tabAt - i * 28L) / 220F); }

    public void receive(List<PlayerStatsPacket.Entry> list, PlayerStatsPacket.Me me) {
        for (PlayerStatsPacket.Entry e : list) if (e.self()) self = e;
        this.me = me;
    }

    private CharacterType type() { return ClientCharacterData.getLocal(); }
    private boolean xeno() { return com.xenowarriors.skill.XenoBlast.has(type()); }
    /** The KAs / Last resort tab: a KA, or (Mikiko, Rhalix) the Xeno Blast. */
    private boolean hasKa() { return ClientSkillState.ka() != null || xeno(); }
    private boolean creative() { return minecraft != null && minecraft.player != null && minecraft.player.isCreative(); }
    private int tabs() { return hasKa() ? 4 : 3; }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    private Boolean hudWas;

    @Override
    protected void init() {
        if (hudWas == null) { hudWas = minecraft.options.hideGui; minecraft.options.hideGui = true; }
    }

    @Override
    public void removed() {
        if (hudWas != null) { minecraft.options.hideGui = hudWas; hudWas = null; }
    }

    @Override
    public void tick() {
        if (++refresh % 40 == 0) ModNetwork.sendToServer(new PlayerStatsRequestPacket());
        if (tab >= tabs()) tab = 0;
    }

    // ------------------------------------------------------------------ layout

    /** Card: x, y, scale (virtual 220x184). */
    private float[] card() {
        float x0 = width * 0.50F, y0 = height * 0.15F, aw = width * 0.47F, ah = height * 0.72F;
        float s = Math.min(aw / VW, ah / VH);
        return new float[]{x0 + (aw - VW * s) / 2F, y0, s};
    }

    private float[] tabBar() {         // x, y, w, h
        float[] c = card();
        return new float[]{c[0] + 10 * c[2], c[1] + (VH + 6) * c[2], (VW - 20) * c[2], 15 * c[2]};
    }

    /** Tab widths: the long "KAs / Last resort" tab gets more room. */
    private float tabWeight(int i) { return tabs() == 4 && i == 3 ? 1.9F : 1F; }
    private float tabW(int i) {
        float total = 0;
        for (int k = 0; k < tabs(); k++) total += tabWeight(k);
        return tabBar()[2] * tabWeight(i) / total;
    }
    private float tabX(int i) {
        float x = tabBar()[0];
        for (int k = 0; k < i; k++) x += tabW(k);
        return x;
    }

    private boolean overBack(double mx, double my) { float s = Math.max(1F, height / 240F); return mx < 26 * s && my < 22 * s; }

    // skills tab geometry (virtual units)
    private static final int SLOT = 25, SLOT_Y = 58, LIST_Y = 90, CARD_W = 104, CARD_H = 21;
    private int slotX(int i) { return (VW - (6 * SLOT + 5 * 6)) / 2 + i * (SLOT + 6); }
    private int skillX(int i) { return 4 + (i % 2) * (CARD_W + 4); }
    private int skillY(int i) { return LIST_Y + (i / 2) * (CARD_H + 3) - skillScroll; }
    private int maxSkillScroll() {
        int rows = (SkillPool.of(minecraft.player).size() + 1) / 2;
        return Math.max(0, rows * (CARD_H + 3) - (VH - LIST_Y - 2));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) { minecraft.setScreen(null); return true; }
        if (key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_LEFT) { setTab((tab + tabs() - 1) % tabs()); click(); return true; }
        if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_TAB) { setTab((tab + 1) % tabs()); click(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 1) { skillScroll = Mth.clamp(skillScroll - (int) Math.signum(delta) * (CARD_H + 3), 0, maxSkillScroll()); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overBack(mx, my)) { onClose(); return true; }
        float[] tb = tabBar();
        if (my >= tb[1] && my < tb[1] + tb[3] && mx >= tb[0] && mx < tb[0] + tb[2]) {
            int t = tabs() - 1;
            while (t > 0 && mx < tabX(t)) t--;
            setTab(t);
            click();
            return true;
        }
        float[] c = card();
        double vx = (mx - c[0]) / c[2], vy = (my - c[1]) / c[2];
        if (tab == 0 && blade() != null && inBox(vx, vy, VW / 2 - 50, VH - 18, 100, 14)) {
            if (minecraft.player != null) minecraft.player.connection.sendCommand(blade()[1]);
            if (blade()[1].endsWith("realm")) minecraft.setScreen(null);
            return true;
        }
        if (tab == 1) {
            for (int s = 0; s < SkillManager.ULTIMATE_SLOT; s++)
                if (inBox(vx, vy, slotX(s), SLOT_Y, SLOT, SLOT)) { selectedSlot = s; click(); return true; }
            List<SkillPool.Entry> pool = SkillPool.of(minecraft.player);
            if (vy >= LIST_Y && vy < VH)
                for (int i = 0; i < pool.size(); i++) {
                    if (!inBox(vx, vy, skillX(i), skillY(i), CARD_W, CARD_H)) continue;
                    if (!SkillPool.available(pool.get(i), ClientSkillState.level(), creative(), ClientSkillState.season())) return true;
                    ModNetwork.CHANNEL.sendToServer(new SetSkillSlotPacket(selectedSlot, i));
                    int[] l = ClientSkillState.loadout().clone();
                    for (int k = 0; k < l.length; k++) if (l[k] == i) l[k] = l[selectedSlot];
                    l[selectedSlot] = i;
                    ClientSkillState.setLoadout(l, ClientSkillState.level());
                    selectedSlot = (selectedSlot + 1) % SkillManager.ULTIMATE_SLOT;
                    click();
                    return true;
                }
        }
        if (tab == 2) {
            String[] names = type().outfitNames();
            for (int i = 0; i < names.length; i++) {
                if (!inBox(vx, vy, skillX(i), outfitY(i), CARD_W, OUTFIT_H)) continue;
                if (outfitLocked(i) || minecraft.player == null) return true;
                ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(i));
                ClientCharacterData.setOutfit(minecraft.player.getUUID(), i);
                click();
                return true;
            }
        }
        if (tab == 3 && !xeno() && inBox(vx, vy, VW / 2 - 55, VH - 18, 110, 14)) { minecraft.setScreen(new KaGuideScreen(this)); return true; }
        return super.mouseClicked(mx, my, button);
    }

    /** {label, command} for Mikiko's Hekublade button on the Stats tab, or null. */
    private String[] blade() {
        if (me == null) return null;
        if (me.prisoner()) return new String[]{"Ask to be freed", "xeno blade plea"};
        if (type() == CharacterType.MIKIKO && !me.inRealm()) return new String[]{"Enter the Hekublade", "xeno blade realm"};
        return null;
    }

    private static boolean inBox(double x, double y, float bx, float by, float bw, float bh) { return x >= bx && x < bx + bw && y >= by && y < by + bh; }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.3F));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderMenu(g, mx, my, pt);
        MenuInterrupt.warning(g, width, height);
    }

    private void renderMenu(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        CharacterType type = type();
        int col = type == CharacterType.NONE ? 0x8090A0 : type.getColor() & 0xFFFFFF;
        float r = ((col >> 16) & 255) / 255F, gr = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
        int w = width, h = height;
        float us = Math.max(1F, h / 240F);
        long now = net.minecraft.Util.getMillis();
        float t = (now - opened) / 1000F;
        float inChar = ease(t / 0.45F), inCard = ease((t - 0.08F) / 0.4F), inTop = ease(t / 0.3F), inBar = ease((t - 0.15F) / 0.35F);
        float tabT = ease((now - tabAt) / 250F);

        // background: dark, with slanted film strips tinted in the character's colour
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = begin();
        quad(bb, m, 0, 0, w, 0, w, h, 0, h, 0.09F + r * 0.06F, 0.09F + gr * 0.06F, 0.1F + b * 0.06F, 1F, 0.05F, 0.05F, 0.06F, 1F);
        float slant = h * 0.35F;
        float[][] strips = {{-0.06F, 0.09F, 0.16F}, {0.13F, 0.07F, 0.11F}, {0.30F, 0.05F, 0.08F}, {0.62F, 0.11F, 0.06F}, {0.86F, 0.08F, 0.05F}};
        for (float[] s : strips) {
            float x0 = s[0] * w, sw = s[1] * w, a = s[2];
            quad(bb, m, x0 + slant, 0, x0 + slant + sw, 0, x0 + sw, h, x0, h, 0.85F, 0.85F, 0.88F, a, mix(r, 0.85F), mix(gr, 0.85F), mix(b, 0.88F), a);
            // sprocket holes down both edges
            float scroll = (t * 14F * us * (0.6F + s[1] * 4F)) % (9 * us);   // the film rolls slowly
            for (float y = -9 * us + scroll; y < h; y += 9 * us) {
                float f = y / h, ex = x0 + slant * (1F - f);
                quad(bb, m, ex + 2 * us, y, ex + 5 * us, y, ex + 5 * us, y + 4 * us, ex + 2 * us, y + 4 * us, 0F, 0F, 0F, 0.35F, 0F, 0F, 0F, 0.35F);
                quad(bb, m, ex + sw - 5 * us, y, ex + sw - 2 * us, y, ex + sw - 2 * us, y + 4 * us, ex + sw - 5 * us, y + 4 * us, 0F, 0F, 0F, 0.35F, 0F, 0F, 0F, 0.35F);
            }
        }
        disc(bb, m, w * 0.24F, h * 0.5F, h * 0.55F, h * 0.6F, r, gr, b, 0.28F, 0F);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();

        // the character
        if (mc.player != null) {
            Cel.force = true;
            float ct = ease((now - camAt) / 600F);
            float[] to = CAMS[Math.min(tab, CAMS.length - 1)];
            for (int k = 0; k < 5; k++) camNow[k] = camFrom[k] + (to[k] - camFrom[k]) * ct;
            float cx = w * camNow[0] - (1F - inChar) * w * 0.15F + (mx - w / 2F) * 0.012F;   // slight parallax with the mouse
            try { PortraitScreen.drawCharacterCam(g, mc.player, type, (int) cx, (int) (h * camNow[1]), (int) (h * camNow[2]),
                    camNow[3] + (mx - w / 2F) / w * 10F + (tab == 2 ? Mth.sin(t * 0.5F) * 35F * ct : 0F), camNow[4], tabPose(tab, t)); }   // (Outfits: slowly turns so you see it all round)
            finally { Cel.force = false; }
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        g.pose().pushPose();
        g.pose().translate(0, (1F - inTop) * -26F * us, 0);               // top bars drop in
        // top left: back button + title
        g.fill((int) (4 * us), (int) (4 * us), (int) (22 * us), (int) (18 * us), 0xE0101014);
        outline(g, (int) (4 * us), (int) (4 * us), (int) (22 * us), (int) (18 * us), 0xFF000000 | col);
        scaled(g, "↶", 9 * us, 7 * us, us, overBack(mx, my) ? 0xFFFFFF : light(col));
        String title = "Character Customization";
        g.fill((int) (25 * us), (int) (4 * us), (int) (25 * us + font.width(title) * us + 12 * us), (int) (18 * us), 0xE0101014);
        scaled(g, "⌂ " + title, 29 * us, 7.5F * us, us, 0xFFFFFF);

        // top right: Money and Karma
        String money = com.xenowarriors.economy.Money.fmt(ClientMoney.get()) + " Money";
        String karma = self == null ? "" : Math.round(self.karma()) + " Karma";
        float px = w - 6 * us;
        for (String s : new String[]{money}) {             // (Karma is in the Stats tab)
            if (s.isEmpty()) continue;
            float tw = font.width(s) * us;
            g.fill((int) (px - tw - 12 * us), (int) (4 * us), (int) px, (int) (18 * us), 0xE0101014);
            g.fill((int) (px - tw - 12 * us), (int) (17 * us), (int) px, (int) (18 * us), 0xFF000000 | col);
            scaled(g, s, px - tw - 6 * us, 7.5F * us, us, 0xFFFFFF);
            px -= tw + 16 * us;
        }
        g.pose().popPose();

        // the card
        float[] c = card();
        double vmx = (mx - c[0]) / c[2], vmy = (my - c[1]) / c[2];
        g.pose().pushPose();
        g.pose().translate(c[0] + (1F - inCard) * w * 0.35F, c[1], 0);   // the card slides in from the right
        g.pose().scale(c[2], c[2], 1F);
        g.fill(0, 0, VW, VH, 0xEE141418);
        outline(g, 0, 0, VW, VH, 0xFF3A3A44);
        g.fill(0, 0, VW, 9, 0xFF1E1E26);
        small(g, "WARRIOR INFO", 4, 2, 0x8A8A9A);
        List<String> tip = null;
        header(g, type, col);
        g.pose().pushPose();
        g.pose().translate((1F - tabT) * 16F * tabDir, 0, 0);            // a new tab's content slides in
        switch (tab) {
            case 0 -> statsTab(g, type, col);
            case 1 -> tip = skillsTab(g, type, col, vmx, vmy);
            case 2 -> outfitsTab(g, type, col, vmx, vmy);
            default -> kaTab(g, col, vmx, vmy);
        }
        g.pose().popPose();
        g.pose().popPose();

        // tab bar
        float[] tb = tabBar();
        g.pose().pushPose();
        g.pose().translate(0, (1F - inBar) * 30F * us, 0);                // the tab bar rises in
        // the highlight glides to the selected tab
        float targetX = tabX(Math.min(tab, tabs() - 1)), targetW = tabW(Math.min(tab, tabs() - 1));
        if (pillX < 0) { pillX = targetX; pillW = targetW; }
        pillX += (targetX - pillX) * 0.3F;
        pillW += (targetW - pillW) * 0.3F;
        String[] names = hasKa() ? new String[]{"STATS", "SKILLS", "OUTFITS", "KAS / LAST RESORT"} : new String[]{"STATS", "SKILLS", "OUTFITS"};
        g.fill((int) tb[0], (int) tb[1], (int) (tb[0] + tb[2]), (int) (tb[1] + tb[3]), 0xEE101014);
        outline(g, (int) tb[0], (int) tb[1], (int) (tb[0] + tb[2]), (int) (tb[1] + tb[3]), 0xFF3A3A44);
        float ts = tb[3] / 15F;
        for (int i = 0; i < names.length; i++) {
            float sx = tabX(i), segW = tabW(i);
            boolean sel = i == tab;
            if (i == 0) {
                RenderSystem.enableBlend();
                RenderSystem.disableCull();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                BufferBuilder sb = begin();
                Matrix4f mm = g.pose().last().pose();
                float sk = tb[3] * 0.35F;
                quad(sb, mm, pillX + sk + 1, tb[1] + 1, pillX + pillW + sk - 1, tb[1] + 1, pillX + pillW - sk - 1, tb[1] + tb[3] - 1, pillX - sk + 1, tb[1] + tb[3] - 1,
                        mix(r, 1F, 0.25F), mix(gr, 1F, 0.25F), mix(b, 1F, 0.25F), 1F, r, gr, b, 1F);
                BufferUploader.drawWithShader(sb.end());
                RenderSystem.enableCull();
            }
            float k = Math.min(ts * 1.1F, segW * 0.86F / font.width(names[i]));       // long names shrink to fit
            float tw = font.width(names[i]) * k;
            boolean under = pillX + pillW / 2 > sx && pillX + pillW / 2 < sx + segW;   // text turns dark where the highlight is
            scaled(g, names[i], sx + segW / 2 - tw / 2, tb[1] + tb[3] / 2 - 4F * k, k, under ? dark(col) : 0xD8D8E0);
        }
        g.pose().popPose();
        String hint = "Q / E: switch tabs    Esc: back";
        float hs = Math.max(1F, h / 320F);
        scaled(g, hint, w - font.width(hint) * hs - 6, h - 11 * hs, hs, 0x9090A0);
        g.pose().popPose();

        if (tip != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            boolean first = true;
            for (String l : tip) {
                if (l.isEmpty()) { lines.add(FormattedCharSequence.EMPTY); continue; }
                lines.addAll(font.split(Component.literal(l).withStyle(first ? ChatFormatting.YELLOW : ChatFormatting.GRAY), 240));
                first = false;
            }
            g.renderTooltip(font, lines, mx, my);
        }
    }

    /**
     * The teams each character belongs to (up to two), as {title, logo name}: e.g. Mikiko is a Xeno Warrior and a
     * Freedom Fighter, Technica works for the Government and the Sky Patrol.
     */
    private static String[][] affiliations(CharacterType t) {
        String[] XW = {"Xeno Warriors", "logo_xeno_warriors"}, FF = {"Freedom Fighters", "logo_freedom_fighters"},
                SP = {"Sky Patrol", "logo_sky_patrol"}, GOV = {"Government", "logo_government"}, OR = {"Orenji Clan", "logo_orenji"},
                SR = {"Sky Rebellion", "logo_sky_rebellion"}, DD = {"Disco Dojo", "logo_disco_dojo"}, DEM = {"Ai-oni's Demons", "logo_demons"};
        return switch (t) {
            case MIKIKO -> new String[][]{XW, FF};
            case RHALIX -> new String[][]{XW, SP};
            case ULTIMA -> new String[][]{GOV, FF};
            case KAERU -> new String[][]{OR, FF};
            case TECHNICA -> new String[][]{GOV, SP};
            case YURI -> new String[][]{GOV, FF};
            case ZANE -> new String[][]{FF};
            case MAX -> new String[][]{GOV, SP};
            case LEXY -> new String[][]{SR, SP};
            case KYRIE, SAM -> new String[][]{SP};
            case LOGAN, KILL, KEITA -> new String[][]{SR};
            case STEVIE, GARRY -> new String[][]{DD, FF};
            case AIONI -> new String[][]{DEM};
            default -> new String[0][];
        };
    }

    private static net.minecraft.resources.ResourceLocation logo(String name) {
        return com.xenowarriors.XenoWarriors.id("textures/gui/" + name + ".png");
    }

    /** Group line, name, and an emblem with their initial. */
    private void header(GuiGraphics g, CharacterType type, int col) {
        var team = com.xenowarriors.client.screen.CharacterSelectScreen.Group.of(type);   // their team in the show
        String[][] aff = affiliations(type);
        String names = aff.length == 0 ? team.title : aff.length == 1 ? aff[0][0] : aff[0][0] + " / " + aff[1][0];
        if (font.width(names + "  •  Season " + type.season()) > VW - 20) names = aff.length > 0 ? aff[0][0] : team.title;
        String group = type == CharacterType.NONE ? "No warrior chosen" : names + "  •  Season " + type.season();
        g.fill(5, 15, 10, 20, 0xFF000000 | col);
        g.drawString(font, group, 13, 14, 0xD0D0DC, false);
        String name = type == CharacterType.NONE ? "Choose a warrior" : ClientCharacterData.name(minecraft.player).toUpperCase(java.util.Locale.ROOT);
        scaled(g, name, 5, 26, 2F, 0xFFFFFF);
        // the team's logo (Freedom Fighters, Sky Patrol...), or an emblem with their initial for teams without one
        if (type != CharacterType.NONE && aff.length > 0) {
            RenderSystem.enableBlend();
            // under the team line, beside the name
            if (aff.length == 1) g.blit(logo(aff[0][1]), VW - 60, 22, 52, 26, 0, 0, 256, 128, 256, 128);
            else for (int k = 0; k < 2; k++) g.blit(logo(aff[k][1]), VW - 100 + k * 48, 23, 46, 23, 0, 0, 256, 128, 256, 128);
            g.fill(4, 48, VW - 4, 49, 0xFF2E2E38);
            return;
        }
        int ex = VW - 26, ey = 26;
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = begin();
        Matrix4f m = g.pose().last().pose();
        float r = ((col >> 16) & 255) / 255F, gr = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
        disc(bb, m, ex, ey, 19, 19, r, gr, b, 1F, 1F);
        disc(bb, m, ex, ey, 16, 16, 0.08F, 0.08F, 0.1F, 1F, 1F);
        disc(bb, m, ex, ey, 14, 14, r * 0.4F, gr * 0.4F, b * 0.4F, 1F, 0.6F);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        String init = type == CharacterType.NONE ? "?" : name.substring(0, 1);
        scaled(g, init, ex - font.width(init) * 1.5F + 0.5F, ey - 6.5F, 2.5F, light(col));
        g.fill(4, 48, VW - 4, 49, 0xFF2E2E38);
    }

    private void statsTab(GuiGraphics g, CharacterType type, int col) {
        if (self == null || me == null) { g.drawCenteredString(font, "Loading...", VW / 2, 90, 0xA0A0B0); return; }
        // level row
        int lv = Math.max(1, self.level());
        pill(g, 4, 53, 120, 15);
        g.drawString(font, "Lv. " + String.format("%,d", lv), 10, 57, 0xFFFFFF, false);
        pill(g, 128, 53, VW - 132, 15);
        String hearts = "+" + Stats.bonusHearts(lv) + " ❤";
        g.fill(131, 56, 140, 65, 0xFF000000 | col);
        g.drawString(font, "»", 133, 57, 0x101010, false);
        g.drawString(font, hearts, VW - 8 - font.width(hearts), 57, 0xFF8080, false);
        g.drawString(font, "Hearts", 144, 57, 0xB0B0C0, false);

        List<Object[]> rows = new ArrayList<>();      // label, value, xp (or -1), bar colour
        var ys = ClientCharacterData.you(minecraft.player);
        rows.add(new Object[]{"Race", ys != null ? ys.race.title + (ys.race == com.xenowarriors.character.Race.SKYLANDER ? (ys.form == com.xenowarriors.you.YouSlot.FORM_SOLAR ? " (Solar)" : " (Lunar)") : ys.race == com.xenowarriors.character.Race.DEMON && ys.humanForm ? " (human form)" : "") : com.xenowarriors.character.Race.label(type), -1F, 0});
        if (ys != null) rows.add(new Object[]{"Element", ys.element.title(), -1F, 0});
        rows.add(new Object[]{"HP", Math.round(self.health()) + " / " + Math.round(self.maxHealth()), -1F, 0});
        rows.add(new Object[]{"Karma", String.valueOf(Math.round(self.karma())), -1F, 0});
        rows.add(new Object[]{"Karma Level", com.xenowarriors.karma.KarmaLevel.fmt(self.karmaLevel()), -1F, 0});
        rows.add(new Object[]{"Attack", lvStr(self.attack()), me.attackXp(), 0xFF7744});
        rows.add(new Object[]{"Defense", lvStr(self.defense()), me.defenseXp(), 0x88DD66});
        rows.add(new Object[]{"Speed", lvStr(self.speed()), me.speedXp(), 0x55DDFF});
        rows.add(new Object[]{"Mining", lvStr(me.mining()), me.miningXp(), 0xE0C070});
        rows.add(new Object[]{"Crafting", lvStr(me.craft()), me.craftXp(), 0xC08A50});
        rows.add(new Object[]{"Smelting", lvStr(me.smelt()), me.smeltXp(), 0xFF8A3A});
        rows.add(new Object[]{"Sensing", me.senseRange() + "b, " + me.senseSeconds() + "s", -1F, 0});
        rows.add(new Object[]{"Practice", Math.min(me.senseUses(), 100) + "/100", -1F, 0});
        if (type == CharacterType.MIKIKO) {
            rows.add(new Object[]{"Hekublade", lvStr(me.bladeLevel()), me.bladeXp(), 0xFF7A1A});
            rows.add(new Object[]{"Foxes", String.valueOf(me.pets()), -1F, 0});
            rows.add(new Object[]{"Sealed", String.valueOf(me.sealed()), -1F, 0});
            rows.add(new Object[]{"Blade time", me.cooldownLeft() > 0 ? "locked" : BladeLevel.fmt(me.realmLeft()), -1F, 0});
            rows.add(new Object[]{"Wind flight", lv >= 50 ? me.flightMinutes() + " min" : "Lv 50", -1F, 0});
        } else if (type == CharacterType.RHALIX) {
            rows.add(new Object[]{"Companions", String.valueOf(me.pets()), -1F, 0});
        }
        int rowsN = (rows.size() + 1) / 2, bottom = blade() != null ? VH - 22 : VH - 4;
        float rh = Math.min(14F, (bottom - 73F) / rowsN);
        int colW = (VW - 12) / 2;
        for (int i = 0; i < rows.size(); i++) {
            Object[] o = rows.get(i);
            float rin = rowIn(i / 2 + 1);
            int x = 4 + (i % 2) * (colW + 4) + Math.round((1F - rin) * 18F);
            float y = 73 + (i / 2) * rh;
            int yi = Math.round(y), hh = Math.round(rh) - 2;
            g.fill(x, yi, x + colW, yi + hh, 0xFF1C1C24);
            float xp = (Float) o[2];
            if (xp >= 0) g.fill(x, yi + hh - 1, x + Math.round(colW * Mth.clamp(xp, 0, 1)), yi + hh, 0xFF000000 | (Integer) o[3]);
            float ty = yi + (hh - 7) / 2F;
            scaled(g, (String) o[0], x + 4, ty, 0.85F, 0xB8B8C8);
            String v = (String) o[1];
            scaled(g, v, x + colW - 4 - font.width(v) * 0.85F, ty, 0.85F, 0xFFFFFF);
        }
        String[] bl = blade();
        if (bl != null) {
            g.fill(VW / 2 - 50, VH - 18, VW / 2 + 50, VH - 4, 0xFF000000 | col);
            g.drawCenteredString(font, bl[0], VW / 2, VH - 15, 0xFFFFFF);
        }
    }

    private static String lvStr(int l) { return l >= Stat.MAX_LEVEL ? "MAX" : "Lv " + String.format("%,d", l); }

    private List<String> skillsTab(GuiGraphics g, CharacterType type, int col, double mx, double my) {
        List<String> tip = null;
        int[] loadout = ClientSkillState.loadout();
        g.drawString(font, "Loadout", 5, 52, 0xB0B0C0, false);
        String lvl = "Character Lv " + ClientSkillState.level() + (creative() ? " (creative)" : "");
        scaled(g, lvl, VW - 5 - font.width(lvl) * 0.8F, 53, 0.8F, 0x8A8A9A);
        for (int s = 0; s <= SkillManager.SLOTS; s++) {
            int x = slotX(s), y = SLOT_Y + 3;
            boolean ka = s == SkillManager.SLOTS, ult = s == SkillManager.ULTIMATE_SLOT;
            int border = ka ? 0xFF8A3CC8 : ult ? 0xFFFFD24A : s == selectedSlot ? 0xFFFFFFFF : 0xFF000000 | col;
            g.fill(x - 1, y - 1, x + SLOT + 1, y + SLOT + 1, border);
            g.fill(x, y, x + SLOT, y + SLOT, ka ? 0xFF140A1E : 0xFF101016);
            if (ka) {
                Ka k = ClientSkillState.ka();
                if (xeno()) {
                    com.xenowarriors.client.SkillIcons.draw(g, com.xenowarriors.client.SkillIcons.xenoBlast(type), x + 3, y + 1, SLOT - 8);
                    scaled(g, "LAST", x + SLOT / 2F - 8, y + SLOT - 8, 0.75F, 0xB070FF);
                    if (inBox(mx, my, x, y, SLOT, SLOT)) tip = List.of(SkillLoadoutScreen.XENO_TIP.split("\n"));
                    continue;
                }
                g.drawCenteredString(font, k == null ? "?" : k.title().substring(0, Math.min(2, k.title().length())), x + SLOT / 2, y + 5, 0xB070FF);
                scaled(g, "KA", x + SLOT / 2F - 4, y + SLOT - 8, 0.75F, 0xB070FF);
                if (inBox(mx, my, x, y, SLOT, SLOT)) tip = k == null ? List.of("Super Ultimate: KA", type.getDisplayName().getString() + "'s KA hasn't been revealed yet.")
                        : List.of("KA: " + k.title(), KaGuideScreen.does(k), "Price: " + k.cost);
                continue;
            }
            Skill sk = SkillPool.inSlot(minecraft.player, loadout, s);
            if (sk != null && !com.xenowarriors.client.SkillIcons.draw(g, sk.getId(), x + 3, y + 1, SLOT - 8)) g.drawCenteredString(font, SkillLoadoutScreen.shortName(sk), x + SLOT / 2, y + 5, 0xFFFFFF);
            String lab = ult ? "ULT" : String.valueOf(s + 1);
            scaled(g, lab, x + SLOT / 2F - font.width(lab) * 0.375F, y + SLOT - 8, 0.75F, ult ? 0xFFD24A : 0xA0A0B0);
            if (sk != null && inBox(mx, my, x, y, SLOT, SLOT)) tip = lines(sk.getName().getString() + (ult ? " (ultimate)" : ""), SkillLoadoutScreen.desc(sk));
        }
        List<SkillPool.Entry> pool = SkillPool.of(minecraft.player);
        skillScroll = Math.min(skillScroll, maxSkillScroll());
        float[] c = card();
        g.enableScissor((int) c[0], (int) (c[1] + LIST_Y * c[2]), (int) (c[0] + VW * c[2]), (int) (c[1] + VH * c[2]));
        for (int i = 0; i < pool.size(); i++) {
            SkillPool.Entry e = pool.get(i);
            int x = skillX(i) + Math.round((1F - rowIn(i / 2 + 2)) * 18F), y = skillY(i);
            if (y + CARD_H < LIST_Y || y > VH) continue;
            boolean seasonOk = ClientSkillState.season() >= e.season();
            boolean open = seasonOk && SkillPool.unlocked(e, ClientSkillState.level(), creative());
            int at = indexOf(loadout, i);
            boolean over = my >= LIST_Y && inBox(mx, my, x, y, CARD_W, CARD_H);
            g.fill(x, y, x + CARD_W, y + CARD_H, at >= 0 ? 0xFF203828 : over && open ? 0xFF2A2A3A : 0xFF1C1C24);
            g.fill(x, y, x + 2, y + CARD_H, open ? 0xFF000000 | col : 0xFF555555);
            String nm = seasonOk ? e.skill().getName().getString() : "???";
            boolean pic = seasonOk && com.xenowarriors.client.SkillIcons.get(e.skill().getId()) != null;
            if (pic) { com.xenowarriors.client.SkillIcons.draw(g, e.skill().getId(), x + 3, y + 1, CARD_H - 2); if (!open) g.fill(x + 3, y + 1, x + CARD_H + 1, y + CARD_H - 1, 0xA0101018); }
            int tx = pic ? x + CARD_H + 3 : x + 5;
            scaled(g, font.plainSubstrByWidth(nm, (int) ((x + CARD_W - 3 - tx) / 0.9F)), tx, y + 3, 0.9F, open ? 0xFFFFFF : 0x777777);
            String line = !seasonOk ? "Season " + e.season() : !open ? "Unlocks at Lv " + e.unlockLevel() : at >= 0 ? "Slot " + (at + 1) : UltimaAnim.resource() + " " + e.skill().getKarmaCost() + "  CD " + e.skill().getCooldownTicks() / 20 + "s";
            scaled(g, font.plainSubstrByWidth(line, (int) ((x + CARD_W - 3 - tx) / 0.75F)), tx, y + 12.5F, 0.75F, !open ? 0xAA5555 : at >= 0 ? 0x77DD88 : 0x99AABB);
            if (over) tip = !seasonOk ? List.of("???", "Season " + e.season() + " content (locked, no spoilers)")
                    : lines(e.skill().getName().getString(), SkillLoadoutScreen.desc(e.skill()) + (open ? "" : "\nUnlocks at Character Level " + e.unlockLevel()));
        }
        g.disableScissor();
        if (maxSkillScroll() > 0) scaled(g, skillScroll < maxSkillScroll() ? "▼ scroll" : "▲", VW - 30, VH - 7, 0.7F, 0x8899AA);
        return tip;
    }

    private static final int OUTFIT_H = 26;
    private int outfitY(int i) { return 56 + (i / 2) * (OUTFIT_H + 4); }
    private boolean outfitLocked(int i) { return type().outfitSeason(i) > ClientSkillState.season(); }

    private void outfitsTab(GuiGraphics g, CharacterType type, int col, double mx, double my) {
        String[] names = type.outfitNames();
        int worn = minecraft.player == null ? 0 : ClientCharacterData.outfit(minecraft.player);
        for (int i = 0; i < names.length; i++) {
            int x = skillX(i) + Math.round((1F - rowIn(i / 2)) * 18F), y = outfitY(i);
            boolean locked = outfitLocked(i), on = i == worn, over = inBox(mx, my, x, y, CARD_W, OUTFIT_H);
            g.fill(x, y, x + CARD_W, y + OUTFIT_H, on ? 0xFF000000 | dark2(col) : over && !locked ? 0xFF2A2A3A : 0xFF1C1C24);
            g.fill(x, y, x + 2, y + OUTFIT_H, locked ? 0xFF555555 : 0xFF000000 | col);
            if (on) outline(g, x, y, x + CARD_W, y + OUTFIT_H, 0xFF000000 | light(col));
            String nm = locked ? "\uD83D\uDD12 " + names[i] : names[i];
            scaled(g, font.plainSubstrByWidth(nm, (int) ((CARD_W - 8) / 0.9F)), x + 5, y + 4, 0.9F, locked ? 0x777777 : 0xFFFFFF);
            int season = type.outfitSeason(i);
            String line = locked ? "Season " + season + " - locked" : on ? "Wearing" : "Season " + Math.max(1, season) + "  \u2022  click to wear";
            scaled(g, line, x + 5, y + 15, 0.75F, locked ? 0xAA5555 : on ? 0x77DD88 : 0x99AABB);
        }
        if (names.length <= 1) scaled(g, "More outfits come with later seasons.", 5, outfitY(2) + 4, 0.8F, 0x8A8A9A);
    }

    private static int dark2(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r * 35 / 100) << 16) | ((g * 35 / 100) << 8) | (b * 35 / 100);
    }

    private void kaTab(GuiGraphics g, int col, double mx, double my) {
        if (xeno()) { xenoTab(g, col, mx, my); return; }
        Ka k = ClientSkillState.ka();
        if (k == null) return;
        g.fill(4, 53, 7, 76, 0xFF000000 | k.color);
        scaled(g, k.title().toUpperCase(java.util.Locale.ROOT), 11, 54, 1.6F, 0xFFFFFF);
        g.drawString(font, (k.rare ? "Rare KA" : "Simple KA") + "  •  manipulates " + k.manipulates, 11, 68, 0xB0B0C0, false);
        pill(g, 4, 80, VW - 8, 14);
        g.drawString(font, "Karma cost", 9, 83, 0xB8B8C8, false);
        String kc = String.valueOf(k.karma);
        g.drawString(font, kc, VW - 9 - font.width(kc), 83, 0xC77DFF, false);
        int y = 99;
        y = para(g, "What it does", KaGuideScreen.does(k), y, 0xFFFFFF);
        y = para(g, "The price", k.cost + ". It gets worse every use; too many uses close together burn it out for 5 minutes.", y + 3, 0xFF9A7A);
        boolean over = inBox(mx, my, VW / 2 - 55, VH - 18, 110, 14);
        g.fill(VW / 2 - 55, VH - 18, VW / 2 + 55, VH - 4, over ? 0xFF000000 | light(col) : 0xFF000000 | col);
        g.drawCenteredString(font, "KA & Element Guide", VW / 2, VH - 15, over ? 0x101010 : 0xFFFFFF);
    }

    /** Mikiko's / Rhalix's last resort: the Xeno Blast. */
    private int paraLimit = VH - 30;

    private void xenoTab(GuiGraphics g, int col, double mx, double my) {
        paraLimit = VH - 14;
        g.fill(4, 53, 7, 76, 0xFF000000 | col);
        scaled(g, "XENO BLAST", 11, 54, 1.6F, 0xFFFFFF);
        g.drawString(font, "Last resort  \u2022  replaces a KA", 11, 68, 0xB0B0C0, false);
        pill(g, 4, 80, VW - 8, 14);
        g.drawString(font, "Karma cost", 9, 83, 0xB8B8C8, false);
        g.drawString(font, "ALL", VW - 9 - font.width("ALL"), 83, 0xFF8080, false);
        int y = 99;
        y = para(g, "What it does", "A short charge, then a huge blast that pours out every bit of Karma you have left. The more Karma, the harder and wider it hits (needs at least " + com.xenowarriors.skill.XenoBlast.MIN_KARMA + ").", y, 0xFFFFFF);
        y = para(g, "The price", "Your Karma is drained to nothing and won't regenerate for 30 seconds.", y + 3, 0xFF9A7A);
        int left = com.xenowarriors.client.ClientKarmaLock.secondsLeft();
        paraLimit = VH - 30;
        if (left > 0) scaled(g, "Drained - Karma back in " + left + "s", 5, Math.min(y + 3, VH - 9), 0.8F, 0xFF8080);
    }

    private int para(GuiGraphics g, String head, String text, int y, int color) {
        scaled(g, head.toUpperCase(java.util.Locale.ROOT), 5, y, 0.75F, 0x8A8A9A);
        y += 8;
        for (FormattedCharSequence l : font.split(Component.literal(text), (int) ((VW - 10) / 0.85F))) {
            if (y > paraLimit) break;
            g.pose().pushPose();
            g.pose().translate(5, y, 0);
            g.pose().scale(0.85F, 0.85F, 1F);
            g.drawString(font, l, 0, 0, color, false);
            g.pose().popPose();
            y += 8;
        }
        return y;
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> lines(String head, String body) {
        List<String> l = new ArrayList<>();
        l.add(head);
        java.util.Collections.addAll(l, body.split("\n"));
        return l;
    }

    private static int indexOf(int[] l, int v) { for (int i = 0; i < l.length; i++) if (l[i] == v) return i; return -1; }

    private static float mix(float a, float b) { return a * 0.35F + b * 0.65F; }
    private static float mix(float a, float b, float f) { return a + (b - a) * f; }

    private static int light(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100);
    }

    private static int dark(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return ((r * 18 / 100) << 16) | ((g * 18 / 100) << 8) | (b * 18 / 100);
    }

    private void pill(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF101016);
        outline(g, x, y, x + w, y + h, 0xFF34343E);
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c); g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c); g.fill(x1 - 1, y0, x1, y1, c);
    }

    private void small(GuiGraphics g, String s, float x, float y, int c) { scaled(g, s, x, y, 0.7F, c); }

    private void scaled(GuiGraphics g, String s, float x, float y, float sc, int c) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1F);
        g.drawString(font, s, 0, 0, c, false);
        g.pose().popPose();
    }
}

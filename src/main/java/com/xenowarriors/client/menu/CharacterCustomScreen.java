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
    /** (UI test) show the Building row's tooltip without the mouse over it. */
    public static boolean testBuildingTip;
    private final Screen parent;
    private int tab;                       // the position in the tab bar (what it shows: cur())
    /** What each tab shows (ids): the bar runs Stats, Skills, Passives, R+L Click, Outfits, KAs. */
    private static final int T_STATS = 0, T_SKILLS = 1, T_OUTFITS = 2, T_KA = 3, T_PASSIVES = 4, T_RL = 5;
    private int[] ids() { return hasKa() ? new int[]{T_STATS, T_SKILLS, T_PASSIVES, T_RL, T_OUTFITS, T_KA} : new int[]{T_STATS, T_SKILLS, T_PASSIVES, T_RL, T_OUTFITS}; }
    /** What the selected tab shows. */
    private int cur() { int[] i = ids(); return i[Math.min(tab, i.length - 1)]; }
    /** The bar position of a tab (by id; 0 if it isn't there). */
    private int posOf(int id) { int[] i = ids(); for (int k = 0; k < i.length; k++) if (i[k] == id) return k; return 0; }
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
        ModNetwork.sendToServer(new com.xenowarriors.network.SkillLevelsPacket("", List.of()));   // (ability levels)
        ModNetwork.sendToServer(new com.xenowarriors.network.ToggleActionPacket(com.xenowarriors.network.ToggleActionPacket.REQUEST, ""));   // (passives, R+L click)
    }

    /** (UI test) show this tab. */
    public void tab(int t) { setTab(posOf(t)); }

    /** UI test: click Kaeru's Hood up / Hood down card on the Outfits tab. */
    public void testClickHood() {
        if (!hoodToggle()) return;
        boolean up = !com.xenowarriors.client.mikiko.HoodClient.kaeruUp(minecraft.player);
        ModNetwork.sendToServer(new com.xenowarriors.network.HoodTogglePacket(up));
        com.xenowarriors.client.mikiko.HoodClient.setManual(minecraft.player.getId(), up);
    }

    // ---- animation (like the agent screen in the user's reference): things slide in on open, the film strips scroll,
    // the tab highlight glides between tabs, and a new tab's content slides in with its rows one after another
    private final long opened = net.minecraft.Util.getMillis();
    private long tabAt = opened;
    private int tabDir = 1;
    private float pillX = -1, pillW;

    /** UI test: switch tabs. */
    public void testTab(int t) { setTab(posOf(t)); }

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
            {0.25F, 1.16F, 0.48F, 22F, -0.06F},        // (room above the head for hoods, ears and hair)
            {0.27F, 1.30F, 0.50F, 38F, 0.20F},         // Skills: close, but the whole head stays on screen
            {0.25F, 0.95F, 0.41F, -20F, -0.04F},       // Outfits: always the full body, so the whole outfit shows
            {0.27F, 1.24F, 0.50F, -16F, 0.10F}};
    private float[] camFrom = {0.30F, 1.70F, 1.05F, 70F, 0.05F};
    private final float[] camNow = CAMS[0].clone();
    private long camAt = opened;

    /** The character's pose on each tab (null = their idle animation), with a little breathing sway. */
    private float[] tabPose(int tab, float t) {
        float s = Mth.sin(t * 2.2F) * 0.03F;
        return switch (tab == T_PASSIVES || tab == T_RL ? T_SKILLS : tab) {
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
    private int tabs() { return ids().length; }

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
        if (refresh % 20 == 5 && (cur() == T_PASSIVES || cur() == T_RL))
            ModNetwork.sendToServer(new com.xenowarriors.network.ToggleActionPacket(com.xenowarriors.network.ToggleActionPacket.REQUEST, ""));
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
    private float tabWeight(int i) { int id = ids()[i]; return id == T_KA ? (xeno() ? 1.6F : 1F) : id == T_RL || id == T_PASSIVES ? 1.25F : 1F; }
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
    /** The ultimates to pick between (a card each after the skills, on a new row) - only shown when there's a choice. */
    private List<SkillPool.Entry> ultChoices() { return minecraft.player == null ? List.of() : SkillPool.ultimates(minecraft.player); }
    private int ultCardStart() { int n = menu().size(); return n + (n % 2); }
    /** The pool indices on the skill cards (the right+left click skills live in their own tab). */
    private List<Integer> menu() { return SkillPool.menuIndices(type(), SkillPool.of(minecraft.player)); }

    /** The "upgrade to the next tier" button on a level-10 skill card (virtual units, relative to the card). */
    private static final int UP_W = 24, UP_H = 9;
    private static boolean overUpgrade(double vx, double vy, int x, int y) { return inBox(vx, vy, x + CARD_W - UP_W - 2, y + CARD_H - UP_H - 1, UP_W, UP_H); }

    /** UI test: press the upgrade button on this skill's card. */
    public void testUpgrade(String id) { ModNetwork.sendToServer(new com.xenowarriors.network.SkillLevelsPacket(id, List.of())); }

    private int maxSkillScroll() {
        int rows = (ultCardStart() + (ultChoices().size() > 1 ? ultChoices().size() : 0) + 1) / 2;
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
        if (cur() == T_SKILLS) { skillScroll = Mth.clamp(skillScroll - (int) Math.signum(delta) * (CARD_H + 3), 0, maxSkillScroll()); return true; }
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
        if (cur() == T_STATS && blade() != null && inBox(vx, vy, VW / 2 - 60, VH - 18, 120, 14)) {
            if (blade()[1].equals(MAGNET)) { testToggleMagnet(); click(); return true; }
            if (minecraft.player != null) minecraft.player.connection.sendCommand(blade()[1]);
            if (blade()[1].endsWith("realm")) minecraft.setScreen(null);
            return true;
        }
        if (cur() == T_PASSIVES && passivesClick(vx, vy)) return true;
        if (cur() == T_RL && rlClick(vx, vy)) return true;
        if (cur() == T_SKILLS) {
            for (int s = 0; s < SkillManager.ULTIMATE_SLOT; s++)
                if (inBox(vx, vy, slotX(s), SLOT_Y, SLOT, SLOT)) { selectedSlot = s; click(); return true; }
            List<SkillPool.Entry> pool = SkillPool.of(minecraft.player);
            List<Integer> shown = menu();
            if (vy >= LIST_Y && vy < VH)
                for (int k = 0; k < shown.size(); k++) {
                    int i = shown.get(k);
                    if (!inBox(vx, vy, skillX(k), skillY(k), CARD_W, CARD_H)) continue;
                    if (!SkillPool.available(pool.get(i), ClientSkillState.level(), creative(), ClientSkillState.season())) return true;
                    String sid = pool.get(i).skill().getId();
                    if (com.xenowarriors.client.ClientSkillLevels.canUpgrade(sid) && overUpgrade(vx, vy, skillX(k), skillY(k))) { testUpgrade(sid); click(); return true; }
                    ModNetwork.CHANNEL.sendToServer(new SetSkillSlotPacket(selectedSlot, i));
                    int[] l = ClientSkillState.loadout().clone();
                    for (int q = 0; q < l.length; q++) if (l[q] == i) l[q] = l[selectedSlot];
                    l[selectedSlot] = i;
                    ClientSkillState.setLoadout(l, ClientSkillState.level());
                    selectedSlot = (selectedSlot + 1) % SkillManager.ULTIMATE_SLOT;
                    click();
                    return true;
                }
            List<SkillPool.Entry> ults = ultChoices();
            if (ults.size() > 1 && vy >= LIST_Y && vy < VH)
                for (int k = 0; k < ults.size(); k++) {
                    int i = ultCardStart() + k;
                    if (!inBox(vx, vy, skillX(i), skillY(i), CARD_W, CARD_H)) continue;
                    if (!SkillPool.available(ults.get(k), ClientSkillState.level(), creative(), ClientSkillState.season())) return true;
                    String uid = ults.get(k).skill().getId();
                    if (com.xenowarriors.client.ClientSkillLevels.canUpgrade(uid) && overUpgrade(vx, vy, skillX(i), skillY(i))) { testUpgrade(uid); click(); return true; }
                    ModNetwork.CHANNEL.sendToServer(new SetSkillSlotPacket(SkillManager.ULTIMATE_SLOT, k));
                    ClientSkillState.setUltimate(k);
                    click();
                    return true;
                }
        }
        if (cur() == T_OUTFITS) {
            String[] names = type().outfitNames();
            for (int i = 0; i < names.length; i++) {
                if (!inBox(vx, vy, skillX(i), outfitY(i), CARD_W, OUTFIT_H)) continue;
                if (outfitLocked(i) || minecraft.player == null) return true;
                ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(i));
                ClientCharacterData.setOutfit(minecraft.player.getUUID(), i);
                click();
                return true;
            }
            if (hoodToggle() && inBox(vx, vy, skillX(names.length), outfitY(names.length), CARD_W, OUTFIT_H)) {
                boolean up = !com.xenowarriors.client.mikiko.HoodClient.kaeruUp(minecraft.player);
                ModNetwork.sendToServer(new com.xenowarriors.network.HoodTogglePacket(up));
                com.xenowarriors.client.mikiko.HoodClient.setManual(minecraft.player.getId(), up);     // (shown right away; the server saves it)
                click();
                return true;
            }
        }
        if (cur() == T_KA && !xeno() && inBox(vx, vy, VW / 2 - 55, VH - 18, 110, 14)) { minecraft.setScreen(new KaGuideScreen(this)); return true; }
        return super.mouseClicked(mx, my, button);
    }

    /** {label, command} for Mikiko's Hekublade button on the Stats tab, or null. */
    private String[] blade() {
        if (me == null) return null;
        if (me.prisoner()) return new String[]{"Ask to be freed", "xeno blade plea"};
        if (type() == CharacterType.MIKIKO && !me.inRealm()) return new String[]{"Enter the Hekublade", "xeno blade realm"};
        if (type() == CharacterType.RHALIX)                                                  // his passive: dropped things drift to him
            return new String[]{"Gravity Magnet: " + (com.xenowarriors.client.ClientOptions.get(com.xenowarriors.stats.PlayerOptions.MAGNET) ? "ON" : "OFF"), MAGNET};
        return null;
    }

    private static final String MAGNET = "!magnet";

    /** Rhalix's Gravity Magnet on/off (the Stats tab button; the UI test calls it too). */
    public void testToggleMagnet() {
        boolean on = !com.xenowarriors.client.ClientOptions.get(com.xenowarriors.stats.PlayerOptions.MAGNET);
        com.xenowarriors.client.ClientOptions.set(com.xenowarriors.stats.PlayerOptions.MAGNET, on);
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
            int camId = cur() == T_PASSIVES || cur() == T_RL ? T_SKILLS : cur();
            float[] to = CAMS[Math.min(camId, CAMS.length - 1)];
            for (int k = 0; k < 5; k++) camNow[k] = camFrom[k] + (to[k] - camFrom[k]) * ct;
            float cx = w * camNow[0] - (1F - inChar) * w * 0.15F + (mx - w / 2F) * 0.012F;   // slight parallax with the mouse
            try { PortraitScreen.drawCharacterCam(g, mc.player, type, (int) cx, (int) (h * camNow[1]), (int) (h * camNow[2]),
                    camNow[3] + (mx - w / 2F) / w * 10F + (cur() == T_OUTFITS ? Mth.sin(t * 0.5F) * 35F * ct : 0F), camNow[4], tabPose(cur(), t)); }   // (Outfits: slowly turns so you see it all round)
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
        switch (cur()) {
            case T_STATS -> tip = statsTab(g, type, col, vmx, vmy);
            case T_SKILLS -> tip = skillsTab(g, type, col, vmx, vmy);
            case T_OUTFITS -> outfitsTab(g, type, col, vmx, vmy);
            case T_PASSIVES -> tip = passivesTab(g, type, col, vmx, vmy);
            case T_RL -> tip = rlTab(g, type, col, vmx, vmy);
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
        String[] names = new String[tabs()];
        for (int i = 0; i < names.length; i++) names[i] = switch (ids()[i]) {
            case T_STATS -> "STATS"; case T_SKILLS -> "SKILLS"; case T_PASSIVES -> "PASSIVES"; case T_RL -> "R+L CLICK"; case T_OUTFITS -> "OUTFITS"; default -> xeno() ? "LAST RESORT" : "KAS"; };
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
        scaled(g, name, 5, 26, type == CharacterType.NONE ? 1.5F : 2F, 0xFFFFFF);   // ("Choose a warrior" ran into the emblem at 2x)
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

    private List<String> statsTab(GuiGraphics g, CharacterType type, int col, double mx, double my) {
        if (self == null || me == null) { g.drawCenteredString(font, "Loading...", VW / 2, 90, 0xA0A0B0); return null; }
        List<String> tip = null;
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
        rows.add(new Object[]{"Race", ys != null ? ys.shown().title + (ys.skylander() ? (ys.form == com.xenowarriors.you.YouSlot.FORM_SOLAR ? " (Solar)" : " (Lunar)") : ys.demon() && ys.humanForm ? " (human form)" : "") : com.xenowarriors.character.Race.label(type), -1F, 0});
        if (ys != null) rows.add(new Object[]{"Element", ys.element.title(), -1F, 0});
        if (ys != null) { var k = com.xenowarriors.ka.Ka.byId(ys.ka);
            rows.add(new Object[]{"KA", k != null ? k.title() + " (" + k.manipulates + ")" : ys.kaRolled ? "none" : "may awaken at Lv " + com.xenowarriors.you.YouKa.LEVEL, -1F, 0}); }
        if (ys != null && ys.race == com.xenowarriors.character.Race.SKYLANDER && !ys.ascended) {
            rows.add(new Object[]{"Lunar path", ys.lunarM + " / " + com.xenowarriors.you.YouAngel.MASTERY, ys.lunarM / (float) com.xenowarriors.you.YouAngel.MASTERY, 0x9AD2F2});
            rows.add(new Object[]{"Solar path", ys.solarM + " / " + com.xenowarriors.you.YouAngel.MASTERY, ys.solarM / (float) com.xenowarriors.you.YouAngel.MASTERY, 0xFFC040});
        }
        if (ys != null && ys.demon() && !ys.devil) rows.add(new Object[]{"Devil's Hunger", ys.hunger + " / " + com.xenowarriors.you.YouDevil.HUNGER_MAX, ys.hunger / (float) com.xenowarriors.you.YouDevil.HUNGER_MAX, 0xC0303A});
        rows.add(new Object[]{"HP", Math.round(self.health()) + " / " + Math.round(self.maxHealth()), -1F, 0});
        rows.add(new Object[]{"Karma", String.valueOf(Math.round(self.karma())), -1F, 0});
        rows.add(new Object[]{"Karma Level", com.xenowarriors.karma.KarmaLevel.fmt(self.karmaLevel()), -1F, 0});
        rows.add(new Object[]{"Attack", lvStr(self.attack()), me.attackXp(), 0xFF7744});
        rows.add(new Object[]{"Defense", lvStr(self.defense()), me.defenseXp(), 0x88DD66});
        rows.add(new Object[]{"Speed", lvStr(self.speed()), me.speedXp(), 0x55DDFF});
        rows.add(new Object[]{"Mining", lvStr(me.mining()), me.miningXp(), 0xE0C070});        // (hover: what your bare hands mine like)
        rows.add(new Object[]{"Crafting", lvStr(me.craft()), me.craftXp(), 0xC08A50});
        rows.add(new Object[]{"Smelting", lvStr(me.smelt()), me.smeltXp(), 0xFF8A3A});
        if (type != CharacterType.ULTIMA) rows.add(new Object[]{"Appetite", (me.appetite() >= com.xenowarriors.stats.Appetite.MAX_LEVEL ? "MAX" : "Lv " + me.appetite())
                + "  x" + (com.xenowarriors.stats.Appetite.layers(me.appetite()) + 1), me.appetiteXp(), 0xE8D020});
        rows.add(new Object[]{"Building", lvStr(me.building()), me.buildingXp(), 0x7FD46A});     // (hover: its reach / keep-a-block bonuses)
        if (com.xenowarriors.client.forms.FormsClient.style() == com.xenowarriors.forms.Form.Style.WIRE)   // (Rhalix's wires, Season 2: the guide says their level shows here)
            rows.add(new Object[]{"Wires", lvStr(com.xenowarriors.client.forms.FormsClient.weaponLevel()), com.xenowarriors.client.forms.FormsClient.weaponProgress(), 0xB05CFF});
        if ((net.minecraftforge.fml.ModList.get().isLoaded("tacz") || com.xenowarriors.client.forms.FormsClient.style() != null)
                && com.xenowarriors.network.GunLevelPacket.clientLevel > 0)   // (TACZ guns installed, or Mikiko / Rhalix, who make guns)
            rows.add(new Object[]{"Guns", lvStr(com.xenowarriors.network.GunLevelPacket.clientLevel), com.xenowarriors.network.GunLevelPacket.clientProgress, 0xB0B8C8});
        rows.add(new Object[]{"Sensing", me.senseRange() + "b, " + me.senseSeconds() + "s", -1F, 0});
        rows.add(new Object[]{"Practice", Math.min(me.senseUses(), 100) + "/100", -1F, 0});
        if (type == CharacterType.MIKIKO) {
            rows.add(new Object[]{"Hekublade", lvStr(me.bladeLevel()), me.bladeXp(), 0xFF7A1A});
            float fm = com.xenowarriors.client.mikiko.FoxKingClient.mastery;     // Fox King mastery: the bar fills to 100, the form lasts longer
            rows.add(new Object[]{"Fox King", fm >= 100 ? "MAX" : (int) fm + "/100", fm / 100F, 0xE8361C});
            int fs = com.xenowarriors.mikiko.FoxKing.duration(fm) / 20;
            rows.add(new Object[]{"Form time", fs / 60 + ":" + String.format("%02d", fs % 60), -1F, 0});
            rows.add(new Object[]{"Foxes", String.valueOf(me.pets()), -1F, 0});
            rows.add(new Object[]{"Sealed", String.valueOf(me.sealed()), -1F, 0});
            rows.add(new Object[]{"Blade time", me.cooldownLeft() > 0 ? "locked" : BladeLevel.fmt(me.realmLeft()), -1F, 0});
            rows.add(new Object[]{"Wind flight", lv >= com.xenowarriors.mikiko.WindFlight.LEVEL ? me.flightMinutes() + " min" : "Lv " + com.xenowarriors.mikiko.WindFlight.LEVEL, -1F, 0});
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
            if ("Building".equals(o[0]) && (testBuildingTip || inBox(mx, my, x, yi, colW, hh)))
                tip = com.xenowarriors.client.PlayerStatsScreen.buildingTip(me.building()).stream().map(Component::getString).toList();
            if ("Mining".equals(o[0]) && !testBuildingTip && (com.xenowarriors.client.PlayerStatsScreen.testMiningTip || inBox(mx, my, x, yi, colW, hh)))
                tip = com.xenowarriors.client.PlayerStatsScreen.miningTip(me.mining()).stream().map(Component::getString).toList();
        }
        String[] bl = blade();
        if (bl != null) {
            g.fill(VW / 2 - 60, VH - 18, VW / 2 + 60, VH - 4, 0xFF000000 | col);
            g.drawCenteredString(font, bl[0], VW / 2, VH - 15, 0xFFFFFF);
        }
        return tip;
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
            Skill sk = SkillPool.inSlot(minecraft.player, ClientSkillState.fullLoadout(), s);
            if (sk != null && !com.xenowarriors.client.SkillIcons.draw(g, sk.getId(), x + 3, y + 1, SLOT - 8)) g.drawCenteredString(font, SkillLoadoutScreen.shortName(sk), x + SLOT / 2, y + 5, 0xFFFFFF);
            String lab = ult ? "ULT" : String.valueOf(s + 1);
            scaled(g, lab, x + SLOT / 2F - font.width(lab) * 0.375F, y + SLOT - 8, 0.75F, ult ? 0xFFD24A : 0xA0A0B0);
            if (sk != null && inBox(mx, my, x, y, SLOT, SLOT)) tip = lines(sk.getName().getString() + (ult ? " (ultimate)" : ""), SkillLoadoutScreen.desc(sk));
        }
        List<SkillPool.Entry> pool = SkillPool.of(minecraft.player);
        List<Integer> shown = menu();
        skillScroll = Math.min(skillScroll, maxSkillScroll());
        float[] c = card();
        g.enableScissor((int) c[0], (int) (c[1] + LIST_Y * c[2]), (int) (c[0] + VW * c[2]), (int) (c[1] + VH * c[2]));
        for (int k = 0; k < shown.size(); k++) {
            int i = shown.get(k);
            SkillPool.Entry e = pool.get(i);
            int x = skillX(k) + Math.round((1F - rowIn(k / 2 + 2)) * 18F), y = skillY(k);
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
            scaled(g, ell(nm, (int) ((x + CARD_W - 25 - tx) / 0.9F)), tx, y + 3, 0.9F, open ? 0xFFFFFF : 0x777777);
            String sid = e.skill().getId();
            String line = !seasonOk ? "Season " + e.season() : !open ? "Unlocks at Lv " + e.unlockLevel() : sid.equals("true_empathy") ? (at >= 0 ? "Slot " + (at + 1) + " - toggles it" : "Passive - see Passives")
                    : at >= 0 ? "Slot " + (at + 1)
                    : UltimaAnim.resource() + " " + com.xenowarriors.client.ClientSkillLevels.cost(sid, e.skill().getKarmaCost()) + "  CD " + com.xenowarriors.client.ClientSkillLevels.cooldown(sid, e.skill().getCooldownTicks()) / 20 + "s";
            boolean up = open && levelBadge(g, sid, x, y, mx, my);
            scaled(g, ell(line, (int) ((x + CARD_W - 3 - tx - (up ? UP_W : 0)) / 0.75F)), tx, y + 12.5F, 0.75F, !open ? 0xAA5555 : at >= 0 ? 0x77DD88 : 0x99AABB);
            if (over) tip = !seasonOk ? List.of("???", "Season " + e.season() + " content (locked, no spoilers)")
                    : lines(e.skill().getName().getString(), SkillLoadoutScreen.desc(e.skill()) + (open ? "" : "\nUnlocks at Character Level " + e.unlockLevel()));
        }
        // a choice of ultimates: one card each (gold edge), click to put it in the ULT slot
        List<SkillPool.Entry> ults = ultChoices();
        if (ults.size() > 1) for (int k = 0; k < ults.size(); k++) {
            SkillPool.Entry e = ults.get(k);
            int i = ultCardStart() + k;
            int x = skillX(i) + Math.round((1F - rowIn(i / 2 + 2)) * 18F), y = skillY(i);
            if (y + CARD_H < LIST_Y || y > VH) continue;
            boolean seasonOk = ClientSkillState.season() >= e.season();
            boolean open = seasonOk && SkillPool.unlocked(e, ClientSkillState.level(), creative());
            boolean on = ClientSkillState.ultimate() == k || (k == 0 && ClientSkillState.ultimate() >= ults.size());
            boolean over = my >= LIST_Y && inBox(mx, my, x, y, CARD_W, CARD_H);
            g.fill(x, y, x + CARD_W, y + CARD_H, on ? 0xFF3A3018 : over && open ? 0xFF2A2A3A : 0xFF1C1C24);
            g.fill(x, y, x + 2, y + CARD_H, open ? 0xFFFFD24A : 0xFF555555);
            if (on) outline(g, x, y, x + CARD_W, y + CARD_H, 0xFFFFD24A);
            boolean pic = seasonOk && com.xenowarriors.client.SkillIcons.get(e.skill().getId()) != null;
            if (pic) { com.xenowarriors.client.SkillIcons.draw(g, e.skill().getId(), x + 3, y + 1, CARD_H - 2); if (!open) g.fill(x + 3, y + 1, x + CARD_H + 1, y + CARD_H - 1, 0xA0101018); }
            int tx = pic ? x + CARD_H + 3 : x + 5;
            String nm = seasonOk ? e.skill().getName().getString() : "???";
            scaled(g, ell(nm, (int) ((x + CARD_W - 25 - tx) / 0.9F)), tx, y + 3, 0.9F, open ? 0xFFE8A0 : 0x777777);
            String line = !seasonOk ? "Season " + e.season() : !open ? "🔒 Unlocks at Lv " + e.unlockLevel() : on ? "Ultimate - equipped" : "Ultimate - click to equip";
            boolean up = open && levelBadge(g, e.skill().getId(), x, y, mx, my);
            scaled(g, ell(line, (int) ((x + CARD_W - 3 - tx - (up ? UP_W : 0)) / 0.75F)), tx, y + 12.5F, 0.75F, !open ? 0xAA5555 : on ? 0xFFD24A : 0x99AABB);
            if (over) tip = !seasonOk ? List.of("???", "Season " + e.season() + " content (locked, no spoilers)")
                    : lines(e.skill().getName().getString() + " (ultimate)", SkillLoadoutScreen.desc(e.skill()) + (open ? "" : "\nUnlocks at Character Level " + e.unlockLevel()));
        }
        g.disableScissor();
        if (maxSkillScroll() > 0) {                          // on a dark tab so it doesn't print over the last card's text
            g.pose().pushPose();
            g.pose().translate(0, 0, 50);
            g.fill(VW - 33, VH - 9, VW - 1, VH - 1, 0xF0101016);
            scaled(g, skillScroll < maxSkillScroll() ? "▼ scroll" : "▲", VW - 30, VH - 7, 0.7F, 0x8899AA);
            g.pose().popPose();
        }
        return tip;
    }

    /**
     * A skill card's ability level: "T2 Lv7" top right, a thin XP bar along the bottom, and (at level 10) the button to
     * upgrade it to the next tier. Returns true when the button is drawn.
     */
    private boolean levelBadge(GuiGraphics g, String id, int x, int y, double mx, double my) {
        int[] v = com.xenowarriors.client.ClientSkillLevels.of(id);
        String b = "T" + v[0] + " Lv" + v[1];
        int col = v[0] >= 7 ? 0xFF7AF0 : v[0] >= 4 ? 0xFFB040 : v[0] >= 2 ? 0xFFD24A : 0xA8B8C8;
        scaled(g, b, x + CARD_W - 3 - font.width(b) * 0.6F, y + 2.5F, 0.6F, col);
        float pr = com.xenowarriors.client.ClientSkillLevels.progress(id);
        g.fill(x + 2, y + CARD_H - 1, x + 2 + Math.round((CARD_W - 2) * pr), y + CARD_H, 0xFF000000 | (v[1] >= com.xenowarriors.skill.SkillLevels.MAX_LEVEL ? 0xFFD24A : 0x55C8FF));
        if (!com.xenowarriors.client.ClientSkillLevels.canUpgrade(id)) return false;
        int bx = x + CARD_W - UP_W - 2, by = y + CARD_H - UP_H - 1;
        boolean hov = my >= LIST_Y && inBox(mx, my, bx, by, UP_W, UP_H);
        float pulse = 0.5F + 0.5F * Mth.sin(net.minecraft.Util.getMillis() / 180F);
        g.fill(bx, by, bx + UP_W, by + UP_H, hov ? 0xFFFFE070 : 0xFF000000 | ((int) (0xB0 + 0x40 * pulse) << 16) | ((int) (0x80 + 0x30 * pulse) << 8) | 0x20);
        String t = "\u25b2 T" + (v[0] + 1);
        scaled(g, t, bx + UP_W / 2F - font.width(t) * 0.3F, by + 1.8F, 0.6F, 0x201000);
        return true;
    }

    // ------------------------------------------------------------------ Passives and R+L Click (toggle.Toggles)

    private static final int PROW_Y = 56, PROW_H = 30, SW_W = 30, SW_H = 12;
    private int prowY(int i) { return PROW_Y + i * (PROW_H + 4); }

    /** Transformations, buffs and passive skills: on / off for good, their level and mastery. */
    private List<String> passivesTab(GuiGraphics g, CharacterType type, int col, double mx, double my) {
        List<String> tip = null;
        var rows = com.xenowarriors.network.TogglesPacket.CLIENT.rows();
        if (rows.isEmpty()) {
            scaled(g, "No transformations or passive skills", 8, 62, 0.9F, 0xA0A0B0);
            scaled(g, "for " + type.getDisplayName().getString() + " yet.", 8, 73, 0.9F, 0xA0A0B0);
        }
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            int x = 4 + Math.round((1F - rowIn(i)) * 18F), y = prowY(i), w = VW - 8;
            boolean locked = !r.locked().isEmpty(), over = inBox(mx, my, x, y, w, PROW_H);
            g.fill(x, y, x + w, y + PROW_H, r.active() ? 0xFF000000 | dark2(r.color()) : over ? 0xFF2A2A3A : 0xFF1C1C24);
            g.fill(x, y, x + 2, y + PROW_H, locked ? 0xFF555555 : 0xFF000000 | r.color());
            String kind = r.kind() == 0 ? "TRANSFORMATION" : r.kind() == 1 ? "BUFF" : r.kind() == 3 ? "PASSIVE - WORKS BY ITSELF" : r.kind() == 4 ? "TRACKING" : "PASSIVE";
            scaled(g, kind, x + 5, y + 2.5F, 0.55F, locked ? 0x777777 : light(r.color()));
            scaled(g, ell(r.title(), (int) ((w - SW_W - 50) / 0.95F)), x + 5, y + 8, 0.95F, locked ? 0x888888 : 0xFFFFFF);
            // level and mastery
            String lv = locked ? r.locked() : (r.kind() == 0 ? "Tier " + r.level() + "  •  Mastery " + Math.round(r.mastery()) + "/100"
                    : "Lv " + r.level() + "  •  " + Math.round(r.mastery()) + "% to next");
            scaled(g, ell(lv, (int) ((w - SW_W - 14) / 0.7F)), x + 5, y + 19, 0.7F, locked ? 0xAA5555 : 0x99AABB);
            if (!locked) {
                int bw = w - SW_W - 14;
                g.fill(x + 5, y + PROW_H - 3, x + 5 + bw, y + PROW_H - 2, 0xFF2A2A34);
                g.fill(x + 5, y + PROW_H - 3, x + 5 + Math.round(bw * Math.min(1F, r.mastery() / 100F)), y + PROW_H - 2, 0xFF000000 | (r.mastery() >= 100 ? 0xFFD24A : r.color()));
            }
            // the switch
            int sx = x + w - SW_W - 5, sy = y + (PROW_H - SW_H) / 2;
            boolean on = r.enabled(), sOver = inBox(mx, my, sx, sy, SW_W, SW_H);
            g.fill(sx - 1, sy - 1, sx + SW_W + 1, sy + SW_H + 1, sOver && !locked ? 0xFFFFFFFF : 0xFF000000);
            g.fill(sx, sy, sx + SW_W, sy + SW_H, locked ? 0xFF303036 : on ? 0xFF2E8A44 : 0xFF4A2A2A);
            int knob = on ? sx + SW_W - SW_H : sx;
            g.fill(knob + 1, sy + 1, knob + SW_H - 1, sy + SW_H - 1, locked ? 0xFF505058 : 0xFFE8E8F0);
            scaled(g, on ? "ON" : "OFF", on ? sx + 3 : sx + SW_H + 2, sy + 3, 0.65F, locked ? 0x777777 : 0xFFFFFF);
            if (over) tip = passiveTip(r);
        }
        scaled(g, "Hold the transform key in game for the quick wheel", 6, VH - 10, 0.65F, 0x8A8A9A);
        return tip;
    }

    private static List<String> passiveTip(com.xenowarriors.network.TogglesPacket.Row r) {
        List<String> l = new ArrayList<>();
        l.add(r.title());
        switch (r.title()) {
            case "True Empathy" -> { l.add("Passive. Switched on, he copies the moves he sees and reads attacks coming at him by himself (Karma each time, every 25 s at most)."); l.add("Overused - three times inside 90 seconds - it strains him and switches itself off."); }
            case "Netsu" -> l.add("Buff. Sets his body on fire: much faster and stronger, burns what he touches, hurts him slowly. Off: it can't be lit.");
            case "Attack Prediction" -> { l.add("Passive. Switched on, his chip reads whoever comes at him by itself (Data each time, every 20 s at most)."); l.add("Overused - three times inside 90 seconds - it overheats and switches itself off."); }
            case "Living Weapon" -> l.add("Transformation (the transform key): every part of his body a weapon and a shield. Mastery makes it last longer and come back sooner.");
            case "Lunar Mind" -> l.add("Works by itself, only at night: stronger, faster and healing while the moon is up.");
            case "Gravity Boots" -> l.add("Works by itself: falling far enough to get hurt, the boots fire just before she lands - no fall damage. 20 s to recharge.");
            case "Visor Scan" -> l.add("Works by itself: every time she locks on to someone, her visor scans them and everything moving nearby glows (every 8 s at most).");
            case "Rescue Platform" -> l.add("Works by itself: when two or more teammates are down near her, the platform comes down, picks them up and heals the party. 3 minutes to come again.");
            case "Scent Tracking" -> l.add("From the toggle wheel (hold the transform key): pick from everything she can smell nearby - it's highlighted through walls and its scent trail shows for 45 s.");
            default -> { l.add("Transformation (the transform key). Mastery grows while you're in it: longer, cheaper and back sooner."); l.add("Off: the transform key does nothing (the wheel too)."); }
        }
        if (!r.locked().isEmpty()) l.add(r.locked());
        return l;
    }

    private boolean passivesClick(double vx, double vy) {
        var rows = com.xenowarriors.network.TogglesPacket.CLIENT.rows();
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            int x = 4, y = prowY(i), w = VW - 8, sx = x + w - SW_W - 5, sy = y + (PROW_H - SW_H) / 2;
            if (!inBox(vx, vy, sx - 2, sy - 2, SW_W + 4, SW_H + 4) && !inBox(vx, vy, x, y, w, PROW_H)) continue;
            if (!r.locked().isEmpty()) return true;
            ModNetwork.sendToServer(new com.xenowarriors.network.ToggleActionPacket(r.enabled() ? com.xenowarriors.network.ToggleActionPacket.DISABLE : com.xenowarriors.network.ToggleActionPacket.ENABLE, r.id()));
            click();
            return true;
        }
        return false;
    }

    private static final int RL_Y = 84, RL_H = 30;
    private int rlY(int i) { return RL_Y + (i / 2) * (RL_H + 4); }

    /** The right+left click skill: pick which move it is. */
    private List<String> rlTab(GuiGraphics g, CharacterType type, int col, double mx, double my) {
        List<String> tip = null;
        var tp = com.xenowarriors.network.TogglesPacket.CLIENT;
        scaled(g, "HOLD RIGHT CLICK, THEN TAP LEFT CLICK", 6, 56, 0.75F, light(col));
        scaled(g, "Does this move instead of a swing - and stops a block.", 6, 65, 0.7F, 0xA0A0B0);
        scaled(g, "Its cooldown is a fifth shorter than from a skill slot.", 6, 73, 0.7F, 0xA0A0B0);
        if (tp.rl().isEmpty()) {
            scaled(g, "No right+left click skills for " + type.getDisplayName().getString() + " yet.", 6, RL_Y + 4, 0.85F, 0x8A8A9A);
            return null;
        }
        int n = tp.rl().size();
        for (int i = 0; i <= n; i++) {                                         // (the last card: None)
            var e = i < n ? tp.rl().get(i) : null;
            int x = skillX(i) + Math.round((1F - rowIn(i / 2)) * 18F), y = rlY(i);
            boolean locked = e != null && !e.locked().isEmpty(), on = e == null ? tp.rlSelected().isEmpty() : e.id().equals(tp.rlSelected());
            boolean over = inBox(mx, my, x, y, CARD_W, RL_H);
            g.fill(x, y, x + CARD_W, y + RL_H, on ? 0xFF000000 | dark2(col) : over && !locked ? 0xFF2A2A3A : 0xFF1C1C24);
            g.fill(x, y, x + 2, y + RL_H, locked ? 0xFF555555 : 0xFF000000 | col);
            if (on) outline(g, x, y, x + CARD_W, y + RL_H, 0xFF000000 | light(col));
            boolean pic = e != null && com.xenowarriors.client.SkillIcons.get(e.id()) != null;
            if (pic) { com.xenowarriors.client.SkillIcons.draw(g, e.id(), x + 3, y + 3, RL_H - 6); if (locked) g.fill(x + 3, y + 3, x + RL_H - 3, y + RL_H - 3, 0xA0101018); }
            int tx = pic ? x + RL_H : x + 5;
            String nm = e == null ? "None" : e.name();
            scaled(g, ell(nm, (int) ((x + CARD_W - 3 - tx) / 0.9F)), tx, y + 5, 0.9F, locked ? 0x777777 : 0xFFFFFF);
            String line = e == null ? (on ? "Right+left click off" : "Turn it off")
                    : locked ? e.locked() : (on ? "Equipped  •  " : "") + UltimaAnim.resource() + " " + e.cost() + "  CD " + Math.max(1, Math.round(e.cooldown() / 20F)) + "s";
            scaled(g, ell(line, (int) ((x + CARD_W - 3 - tx) / 0.72F)), tx, y + 17, 0.72F, locked ? 0xAA5555 : on ? 0x77DD88 : 0x99AABB);
            if (over && e != null) {
                Skill sk = null;
                for (SkillPool.Entry en : SkillPool.of(minecraft.player)) if (en.skill().getId().equals(e.id())) sk = en.skill();
                tip = sk == null ? List.of(e.name()) : lines(e.name() + " (right+left click)", SkillLoadoutScreen.desc(sk));
            }
        }
        return tip;
    }

    private boolean rlClick(double vx, double vy) {
        var tp = com.xenowarriors.network.TogglesPacket.CLIENT;
        int n = tp.rl().size();
        if (n == 0) return false;
        for (int i = 0; i <= n; i++) {
            if (!inBox(vx, vy, skillX(i), rlY(i), CARD_W, RL_H)) continue;
            var e = i < n ? tp.rl().get(i) : null;
            if (e != null && !e.locked().isEmpty()) return true;
            ModNetwork.sendToServer(new com.xenowarriors.network.ToggleActionPacket(com.xenowarriors.network.ToggleActionPacket.SET_RL, e == null ? "" : e.id()));
            click();
            return true;
        }
        return false;
    }

    /** Cut a string to a width, with "…" when it had to be shortened. */
    private String ell(String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))).stripTrailing() + "…";
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
            scaled(g, ell(nm, (int) ((CARD_W - 8) / 0.9F)), x + 5, y + 4, 0.9F, locked ? 0x777777 : 0xFFFFFF);
            int season = type.outfitSeason(i);
            String line = locked ? "Season " + season + " - locked" : on ? "Wearing" : "Season " + Math.max(1, season) + "  \u2022  click to wear";
            scaled(g, line, x + 5, y + 15, 0.75F, locked ? 0xAA5555 : on ? 0x77DD88 : 0x99AABB);
        }
        if (hoodToggle()) {                    // Kaeru: her hoodie's hood, up or down
            int i = names.length, x = skillX(i) + Math.round((1F - rowIn(i / 2)) * 18F), y = outfitY(i);
            boolean up = com.xenowarriors.client.mikiko.HoodClient.kaeruUp(minecraft.player), over = inBox(mx, my, x, y, CARD_W, OUTFIT_H);
            g.fill(x, y, x + CARD_W, y + OUTFIT_H, up ? 0xFF000000 | dark2(col) : over ? 0xFF2A2A3A : 0xFF1C1C24);
            g.fill(x, y, x + 2, y + OUTFIT_H, 0xFF000000 | col);
            if (up) outline(g, x, y, x + CARD_W, y + OUTFIT_H, 0xFF000000 | light(col));
            scaled(g, up ? "Hood up" : "Hood down", x + 5, y + 4, 0.9F, 0xFFFFFF);
            scaled(g, "Hoodie  •  click to " + (up ? "put it down" : "put it up"), x + 5, y + 15, 0.75F, up ? 0x77DD88 : 0x99AABB);
        }
        if (names.length <= 1) scaled(g, "More outfits come with later seasons.", 5, outfitY(2) + 4, 0.8F, 0x8A8A9A);
    }

    /** The outfits tab has a Hood up / Hood down card (Kaeru in her hoodie). */
    private boolean hoodToggle() {
        return minecraft.player != null && com.xenowarriors.client.mikiko.HoodClient.kaeru(minecraft.player);
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

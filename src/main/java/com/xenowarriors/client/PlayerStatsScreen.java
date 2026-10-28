package com.xenowarriors.client;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.mikiko.BladeLevel;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PlayerStatsPacket;
import com.xenowarriors.network.PlayerStatsRequestPacket;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.stats.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The M menu. "Warriors" lists every player on the server; "My Warrior" shows your own progress (stats, skills and
 * status in tidy sections, Mikiko's Hekublade button); "Outfits" shows your character and lets you change outfit;
 * "Settings" has your options.
 * Everything is measured to fit its box: long text is shortened with "…", big numbers are shortened (22.4K), and the
 * whole card shrinks on small windows.
 */
public class PlayerStatsScreen extends Screen {

    private static final int ROW = 30, PAD = 8;
    private static final int BG = 0xD0101018, BG_SELF = 0xD01C1C2A, SECTION = 0xFF8C8CA0, LABEL = 0xFFA0A0B0;
    private static int tab;

    /** (UI test) open on this tab. */
    public static void presetTab(int t) { tab = t; }
    private List<PlayerStatsPacket.Entry> players = new ArrayList<>();
    private PlayerStatsPacket.Me me;
    private int scroll, refresh;
    private Button tabPlayers, tabMe, tabOutfits, tabSettings, realm, plea, senseFriendly, efMode, efBattle, hudStyle, vanillaBars, skillsAlways, celShade, drawnFaces;

    public PlayerStatsScreen() {
        super(Component.literal("Warriors"));
    }

    /** The M key: the main menu (which opens these tabs). */
    public static void open() {
        Minecraft.getInstance().setScreen(new com.xenowarriors.client.menu.MainMenuScreen());
        ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    /** Opens straight on a tab; Esc goes back to {@code parent} (the main menu). */
    public static void openTab(Screen parent, int t) {
        tab = t;
        PlayerStatsScreen s = new PlayerStatsScreen();
        s.parent = parent;
        Minecraft.getInstance().setScreen(s);
        ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    private Screen parent;

    @Override
    public void onClose() {
        if (parent != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    public static void receive(List<PlayerStatsPacket.Entry> list, PlayerStatsPacket.Me me) {
        if (Minecraft.getInstance().screen instanceof com.xenowarriors.client.menu.MainMenuScreen mm) mm.receive(list);
        if (Minecraft.getInstance().screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen cc) cc.receive(list, me);
        if (Minecraft.getInstance().screen instanceof PlayerStatsScreen s) {
            s.players = list;
            s.me = me;
            s.updateButtons();
        }
    }

    /** Card width: as wide as is comfortable, never wider than the window. */
    private int cardW() {
        return Math.min(340, width - 20);
    }

    private int cardX() {
        return (width - cardW()) / 2;
    }

    @Override
    protected void init() {
        // tabs: four across, as wide as fits
        int tabsN = com.xenowarriors.client.PacketScreens.collectionUnlocked ? 5 : 4;
        int gap = 4, tw = Math.min(110, (Math.max(cardW(), Math.min(width - 8, 4 * 100)) - gap * (tabsN - 1)) / tabsN), tx = (width - (tw * tabsN + gap * (tabsN - 1))) / 2;
        Screen back = parent != null ? parent : this;
        tabPlayers = addRenderableWidget(Button.builder(Component.literal("Warriors"), b -> setTab(0)).bounds(tx, 6, tw, 16).build());
        tabMe = addRenderableWidget(Button.builder(Component.literal("Customization"), b -> com.xenowarriors.client.menu.CharacterCustomScreen.open(back)).bounds(tx + (tw + gap), 6, tw, 16).build());
        tabSettings = addRenderableWidget(Button.builder(Component.literal("Settings"), b -> setTab(3)).bounds(tx + 2 * (tw + gap), 6, tw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Party"), b -> minecraft.setScreen(new com.xenowarriors.client.party.PartySetupScreen(back))).bounds(tx + 3 * (tw + gap), 6, tw, 16).build());
        // (outfits, skills and KAs live in Character Customization now; the old Outfits tab stays reachable for tests)
        tabOutfits = Button.builder(Component.literal("Outfits"), b -> setTab(2)).bounds(0, 0, 1, 1).build();
        tabOutfits.visible = false;
        if (com.xenowarriors.client.PacketScreens.collectionUnlocked)       // after reading the Collection Book once
            addRenderableWidget(Button.builder(Component.literal("Collection"), b -> com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.CollectionActionPacket(0)))
                    .bounds(tx + 4 * (tw + gap), 6, tw, 16).build());
        // settings: Karma Sensing (with its explanation underneath), then the Epic Fight options under their heading
        int bw = Math.min(240, cardW() - 20), bx = width / 2 - bw / 2;
        senseFriendly = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ClientSettings.setSenseFriendly(!ClientSettings.senseFriendly());
            updateButtons();
        }).bounds(bx, SET_TOP + 34, bw, 20).build());
        efMode = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ClientSettings.setEfWholeBattleMode(!ClientSettings.efWholeBattleMode());
            updateButtons();
        }).bounds(bx, SET_TOP + 226, bw, 20).build());
        efBattle = addRenderableWidget(Button.builder(Component.empty(), b -> {
            ClientSettings.setEfAlwaysBattle(!ClientSettings.efAlwaysBattle());
            updateButtons();
        }).bounds(bx, SET_TOP + 250, bw, 20).build());
        celShade = addRenderableWidget(Button.builder(Component.empty(), b -> { ClientSettings.setCelShading(!ClientSettings.celShading()); updateButtons(); })
                .bounds(bx, SET_TOP + 98, bw, 20).build());
        drawnFaces = addRenderableWidget(Button.builder(Component.empty(), b -> { ClientSettings.setDrawnFaces(!ClientSettings.drawnFaces()); updateButtons(); })
                .bounds(bx, SET_TOP + 122, bw, 20).build());
        hudStyle = addRenderableWidget(Button.builder(Component.empty(), b -> { String s = ClientSettings.hudStyle(); ClientSettings.setHudStyle(s.equals("blazing") ? "kh" : s.equals("kh") ? "classic" : "blazing"); updateButtons(); }).bounds(bx, SET_TOP + 162, bw, 20).build());
        vanillaBars = addRenderableWidget(Button.builder(Component.empty(), b -> { ClientSettings.setVanillaBars(!ClientSettings.vanillaBars()); updateButtons(); }).bounds(bx, SET_TOP + 186, bw / 2 - 2, 20).build());
        skillsAlways = addRenderableWidget(Button.builder(Component.empty(), b -> { ClientSettings.setSkillsAlways(!ClientSettings.skillsAlways()); updateButtons(); }).bounds(bx + bw / 2 + 2, SET_TOP + 186, bw / 2 - 2, 20).build());
        setBaseY.clear();
        for (Button b : new Button[]{senseFriendly, efMode, efBattle, celShade, drawnFaces, hudStyle, vanillaBars, skillsAlways}) setBaseY.put(b, b.getY());
        if (players.isEmpty()) ModNetwork.sendToServer(new PlayerStatsRequestPacket());     // (opened some other way: ask for the list)
        // outfits: one button per outfit, beside (or, on a narrow window, under) the preview of your character
        outfitButtons.clear();
        for (int i = 0; i < MAX_OUTFITS; i++) {
            final int idx = i;
            outfitButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> chooseOutfit(idx)).bounds(0, 0, 10, 20).build()));
        }
        layoutOutfits();
        realm = addRenderableWidget(Button.builder(Component.literal("Enter the Hekublade"), b -> {
            if (minecraft.player != null) minecraft.player.connection.sendCommand("xeno blade realm");
            minecraft.setScreen(null);
        }).bounds(width / 2 - 75, height - 46, 150, 20).build());
        plea = addRenderableWidget(Button.builder(Component.literal("Ask to be freed"), b -> {
            if (minecraft.player != null) minecraft.player.connection.sendCommand("xeno blade plea");
        }).bounds(width / 2 - 75, height - 46, 150, 20).build());
        updateButtons();
    }

    /** Settings scroll (small windows): base Y of each settings button. */
    private final java.util.Map<Button, Integer> setBaseY = new java.util.HashMap<>();
    private int setScroll;

    private int settingsHeight() { return net.minecraftforge.fml.ModList.get().isLoaded("epicfight") ? 276 : 214; }

    private int maxSetScroll() { return Math.max(0, SET_TOP + settingsHeight() + 6 - height); }

    private static final int SET_TOP = 30, MAX_OUTFITS = 6, OUT_TOP = 30;
    private final List<Button> outfitButtons = new ArrayList<>();

    /** The Outfits card: preview box and button column side by side, stacked when the window is narrow. */
    private boolean outfitsStacked() {
        return cardW() < 280;
    }

    private int[] previewBox() {     // {x, y, w, h}
        int x = cardX(), w = cardW(), bottom = height - 20;
        if (outfitsStacked()) {
            int n = outfitCount(), listH = n * 22 + 6;
            int h = Math.max(70, bottom - OUT_TOP - 24 - listH);
            return new int[]{x + 6, OUT_TOP + 20, w - 12, h};
        }
        return new int[]{x + 6, OUT_TOP + 20, Math.round(w * 0.45F), bottom - OUT_TOP - 26};
    }

    private int outfitCount() {
        if (minecraft == null || minecraft.player == null) return 1;
        return Math.min(MAX_OUTFITS, ClientCharacterData.get(minecraft.player).outfitKeys().length);
    }

    private void layoutOutfits() {
        int[] pb = previewBox();
        int x0, y0, bw;
        if (outfitsStacked()) {
            x0 = cardX() + 10; bw = cardW() - 20; y0 = pb[1] + pb[3] + 6;
        } else {
            x0 = pb[0] + pb[2] + 10; bw = cardX() + cardW() - 8 - x0; y0 = OUT_TOP + 24;
        }
        for (int i = 0; i < outfitButtons.size(); i++) {
            Button b = outfitButtons.get(i);
            b.setX(x0); b.setY(y0 + i * 22); b.setWidth(bw);
        }
    }

    private void chooseOutfit(int idx) {
        if (minecraft.player == null) return;
        ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(idx));
        ClientCharacterData.setOutfit(minecraft.player.getUUID(), idx);          // show it right away
        updateButtons();
    }

    private void setTab(int t) {
        tab = t;
        scroll = 0;
        updateButtons();
    }

    private void updateButtons() {
        for (var e : setBaseY.entrySet()) {
            int y = e.getValue() - setScroll;
            e.getKey().setY(y);
        }
        if (realm == null) return;
        tabPlayers.active = tab != 0;
        tabMe.active = tab != 1;
        tabOutfits.active = tab != 2;
        tabSettings.active = tab != 3;
        senseFriendly.visible = tab == 3;
        senseFriendly.setMessage(Component.literal("Sense friendly Karma: " + (ClientSettings.senseFriendly() ? "ON" : "OFF")));
        celShade.visible = tab == 3;
        celShade.setMessage(Component.literal("Cel-shading: " + (ClientSettings.celShading() ? "ON" : "OFF")));
        drawnFaces.visible = tab == 3 && minecraft.player != null && minecraft.player.isCreative();      // (only offered in creative mode)
        drawnFaces.setMessage(Component.literal("Faces: " + (ClientSettings.drawnFaces() ? "Drawn (new)" : "Pixel (old)")));
        hudStyle.visible = vanillaBars.visible = skillsAlways.visible = tab == 3;
        hudStyle.setMessage(Component.literal("HUD: " + switch (ClientSettings.hudStyle()) { case "kh" -> "Modern"; case "classic" -> "Classic"; default -> "Blazing Wind"; }));
        vanillaBars.setMessage(Component.literal("Hearts/food: " + (ClientSettings.vanillaBars() ? "Shown" : "Hidden")));
        skillsAlways.setMessage(Component.literal("Skills: " + (ClientSettings.skillsAlways() ? "Always" : "Hold Ctrl")));
        vanillaBars.active = skillsAlways.active = ClientSettings.khHud();
        efMode.visible = tab == 3 && net.minecraftforge.fml.ModList.get().isLoaded("epicfight");
        efBattle.visible = efMode.visible;
        efBattle.setMessage(Component.literal("Always battle mode: " + (ClientSettings.efAlwaysBattle() ? "ON" : "OFF")));
        efMode.setMessage(Component.literal("Battle mode moves: " + (ClientSettings.efWholeBattleMode() ? "Epic Fight's" : "Xeno Warriors'")));
        boolean mikiko = ClientCharacterData.getLocal() == CharacterType.MIKIKO;
        CharacterType type = minecraft != null && minecraft.player != null ? ClientCharacterData.get(minecraft.player) : CharacterType.NONE;
        String[] names = type.outfitNames();
        int current = minecraft != null && minecraft.player != null ? ClientCharacterData.outfit(minecraft.player) : 0;
        layoutOutfits();
        for (int i = 0; i < outfitButtons.size(); i++) {
            Button b = outfitButtons.get(i);
            b.visible = tab == 2 && type != CharacterType.NONE && i < names.length;
            boolean seasonLocked = i < names.length && ClientSkillState.season() < type.outfitSeason(i);
            if (i < names.length) {
                String label = seasonLocked ? "🔒 " + names[i] + " (S" + type.outfitSeason(i) + ")" : (i == current ? "✔ " : "") + names[i];
                b.setMessage(Component.literal(fit(label, b.getWidth() - 10)));
            }
            b.active = i != current && !seasonLocked;
        }
        boolean prisoner = me != null && me.prisoner();
        realm.visible = tab == 1 && mikiko && me != null && !prisoner;
        realm.setMessage(Component.literal(me != null && me.inRealm() ? "Leave the Hekublade" : "Enter the Hekublade"));
        plea.visible = tab == 1 && prisoner;
            for (var e : setBaseY.entrySet()) if (e.getKey().getY() < 26) e.getKey().visible = false;   // scrolled up under the tabs
    }

    @Override
    public void tick() {
        if (++refresh % 40 == 0) ModNetwork.sendToServer(new PlayerStatsRequestPacket());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ModKeyMappings.STATS_MENU.matches(key, scan)) {
            minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (tab == 3) {
            setScroll = Math.max(0, Math.min(maxSetScroll(), setScroll - (int) Math.signum(delta) * 16));
            updateButtons();
            return true;
        }
        int visible = Math.max(1, (height - 60) / ROW);
        scroll = Math.max(0, Math.min(Math.max(0, players.size() - visible), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        if (tab == 3) renderSettings(g);
        else if (tab == 2) renderOutfits(g, mouseX, mouseY);
        else if (players.isEmpty()) g.drawCenteredString(font, "Loading...", width / 2, 50, 0xAAAAAA);
        else if (tab == 0) renderPlayers(g);
        else renderMe(g);
        g.drawCenteredString(font, parent != null ? "Esc: back to the menu   M: close" : "M or Esc to close", width / 2, height - 12, 0x666666);
        String money = "Money: " + com.xenowarriors.economy.Money.fmt(ClientMoney.get());
        g.drawString(font, money, width - font.width(money) - 8, height - 12, 0x7CE07C);
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------ helpers

    /** 22350 -> "22.4K", 1250000 -> "1.3M" (small numbers stay as they are). */
    static String compact(double n) {
        long v = Math.round(n);
        if (Math.abs(v) < 10000) return String.valueOf(v);
        if (Math.abs(v) < 1_000_000) return String.format("%.1fK", v / 1000.0).replace(".0K", "K");
        return String.format("%.1fM", v / 1_000_000.0).replace(".0M", "M");
    }

    /** 88888 -> "88,888". */
    static String commas(int n) {
        return String.format("%,d", n);
    }

    /** Shorten text with "…" so it fits in maxW pixels. */
    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
    }

    private void text(GuiGraphics g, String s, int x, int y, int maxW, int color) {
        g.drawString(font, fit(s, maxW), x, y, color);
    }

    private void textRight(GuiGraphics g, String s, int right, int y, int maxW, int color) {
        String f = fit(s, maxW);
        g.drawString(font, f, right - font.width(f), y, color);
    }

    private void small(GuiGraphics g, String s, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawString(font, s, 0, 0, color);
        g.pose().popPose();
    }

    private void bar(GuiGraphics g, int x, int y, int w, int h, float fill, int color) {
        g.fill(x, y, x + w, y + h, 0xFF2A2A36);
        int f = Math.round(w * Math.max(0F, Math.min(1F, fill)));
        if (f > 0) g.fill(x, y, x + f, y + h, 0xFF000000 | color);
    }

    private static CharacterType typeOf(PlayerStatsPacket.Entry e) {
        for (CharacterType t : CharacterType.values()) if (t.getId().equals(e.type())) return t;
        return CharacterType.NONE;
    }

    // ------------------------------------------------------------------ Warriors tab

    private void renderPlayers(GuiGraphics g) {
        int w = cardW(), x = cardX(), top = 28;
        int visible = Math.max(1, (height - 60) / ROW);
        // columns: who (38%) | level + health (24%) | stats | karma
        int c1 = x + 7, c2 = x + Math.round(w * 0.40F), c3 = x + Math.round(w * 0.64F), right = x + w - 6;
        for (int i = 0; i < visible && i + scroll < players.size(); i++) {
            PlayerStatsPacket.Entry e = players.get(i + scroll);
            CharacterType type = typeOf(e);
            int color = type.getColor() | 0xFF000000;
            int y = top + i * ROW;
            g.fill(x, y, x + w, y + ROW - 3, e.self() ? BG_SELF : BG);
            g.fill(x, y, x + 2, y + ROW - 3, color);
            String tag = e.self() ? " (you)" : e.party() ? " (party)" : "";
            text(g, e.name() + tag, c1, y + 4, c2 - c1 - 6, 0xFFFFFF);
            text(g, type == CharacterType.NONE ? "No warrior" : type.getDisplayName().getString(), c1, y + 15, c2 - c1 - 6, color);
            text(g, type == CharacterType.NONE ? "-" : "Lv " + commas(Math.max(1, e.level())), c2, y + 4, c3 - c2 - 6, 0xFFD966);
            text(g, "❤ " + compact(e.health()) + "/" + compact(e.maxHealth()), c2, y + 15, c3 - c2 - 6, 0xFF6060);
            // three slim stat bars with their levels
            int statsW = right - c3 - 34;
            stat(g, c3, y + 3, statsW, "SPD", e.speed(), 0x55DDFF);
            stat(g, c3, y + 11, statsW, "ATK", e.attack(), 0xFF7744);
            stat(g, c3, y + 19, statsW, "DEF", e.defense(), 0x88DD66);
            textRight(g, compact(e.karma()), right, y + 4, 30, 0xC77DFF);
            small(g, "karma", right - 20, y + 16, 0x8C6AAA);
        }
        if (players.size() > visible) {
            g.drawCenteredString(font, "scroll for more (" + players.size() + " players)", width / 2, top + visible * ROW, 0x777777);
        }
    }

    private void stat(GuiGraphics g, int x, int y, int w, String label, int level, int color) {
        String s = label + " " + compact(level);
        small(g, s, x, y, 0xDDDDDD);
        int lw = Math.round(font.width("DEF 29.6K") * 0.75F) + 3;
        if (w - lw > 6) bar(g, x + lw, y + 1, w - lw, 4, level / (float) Stat.MAX_LEVEL, color);
    }

    // ------------------------------------------------------------------ My Warrior tab

    private void renderMe(GuiGraphics g) {
        PlayerStatsPacket.Entry self = players.stream().filter(PlayerStatsPacket.Entry::self).findFirst().orElse(null);
        if (self == null || me == null) return;
        CharacterType type = typeOf(self);
        int color = type.getColor() | 0xFF000000;
        boolean mikiko = type == CharacterType.MIKIKO;

        // status items shown in the two-column grid
        List<String[]> items = new ArrayList<>();   // {label, value, color}
        items.add(new String[]{"Karma", compact(self.karma()), "C77DFF"});
        items.add(new String[]{"Karma Level", com.xenowarriors.karma.KarmaLevel.fmt(self.karmaLevel()), "50FF80"});
        items.add(new String[]{"Health", compact(self.health()) + " / " + compact(self.maxHealth()), "FF6060"});
        items.add(new String[]{"Karma Sensing", me.senseRange() + " blocks, " + me.senseSeconds() + "s", "B45CFF"});
        items.add(new String[]{"Sensing practice", Math.min(me.senseUses(), 100) + " / 100", "9A7ACC"});
        if (mikiko) {
            items.add(new String[]{"Foxes", String.valueOf(me.pets()), "FFA040"});
            items.add(new String[]{"Wind flight", self.level() >= 50 ? me.flightMinutes() + " min flown" : "at Lv 50", "DDEEFF"});
            items.add(new String[]{"Sealed in blade", String.valueOf(me.sealed()), "FF5A3A"});
            items.add(new String[]{"Time inside blade", me.cooldownLeft() > 0 ? "locked " + BladeLevel.fmt(me.cooldownLeft())
                    : BladeLevel.fmt(me.realmLeft()) + " / " + BladeLevel.fmt(me.realmMax()), me.cooldownLeft() > 0 ? "FF5A5A" : "FFC080"});
        } else if (type == CharacterType.RHALIX) {
            items.add(new String[]{"Winged companions", String.valueOf(me.pets()), "C77DFF"});
        }
        int statRows = 6 + (mikiko ? 1 : 0);
        int gridRows = (items.size() + 1) / 2;
        int content = 34 + 12 + statRows * 12 + 10 + 12 + gridRows * 22 + 8;
        int availTop = 28, availBottom = (realm.visible || plea.visible) ? height - 52 : height - 18;
        float scale = Math.min(1F, (availBottom - availTop) / (float) content);
        int w = Math.round(cardW() / scale) > width - 20 ? cardW() : cardW();
        g.pose().pushPose();
        g.pose().translate(width / 2F, availTop, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-width / 2F, -availTop, 0);
        int x = cardX(), y = availTop, right = x + w - PAD;
        g.fill(x, y, x + w, y + content, BG);
        g.fill(x, y, x + w, y + 1, color);

        // header: who, level, hearts
        String lv = "Lv " + commas(Math.max(1, self.level())) + " / " + commas(Stats.MAX_CHARACTER_LEVEL);
        int lvW = font.width(lv);
        text(g, type == CharacterType.NONE ? "No warrior" : type.getDisplayName().getString(), x + PAD, y + 6, w - lvW - PAD * 3, color);
        g.drawString(font, lv, right - lvW, y + 6, 0xFFD966);
        int hearts = Stats.bonusHearts(self.level());
        int next = self.level() < 1000 ? (self.level() / 10 + 1) * 10 : (self.level() / 1000 + 1) * 1000;
        String heartLine = self.level() >= Stats.MAX_CHARACTER_LEVEL ? "+" + hearts + " bonus hearts (max)"
                : "+" + hearts + " bonus hearts  •  next at Lv " + commas(next);
        text(g, heartLine, x + PAD, y + 19, w - PAD * 2, 0xFF8080);
        int cy = y + 34;

        // stats and skills
        small(g, "STATS", x + PAD, cy, SECTION);
        cy += 10;
        int labelW = font.width("Hekublade") + 6, lvColW = font.width("Lv 30,000") + 6, pctW = font.width("MAX") + 4;
        int barX = x + PAD + labelW + lvColW, barW = right - pctW - 4 - barX;
        statRow(g, x + PAD, cy, labelW, lvColW, barX, barW, right, "Speed", self.speed(), me.speedXp(), Stat.MAX_LEVEL, 0x55DDFF); cy += 12;
        statRow(g, x + PAD, cy, labelW, lvColW, barX, barW, right, "Attack", self.attack(), me.attackXp(), Stat.MAX_LEVEL, 0xFF7744); cy += 12;
        statRow(g, x + PAD, cy, labelW, lvColW, barX, barW, right, "Defense", self.defense(), me.defenseXp(), Stat.MAX_LEVEL, 0x88DD66); cy += 12;
        statRow(g, x + PAD, cy, labelW, lvColW, barX, barW, right, "Mining", me.mining(), me.miningXp(), Stat.MAX_LEVEL, 0xE0C070); cy += 12;
        statRow(g, x + PAD, cy, labelW, lvColW, barX, barW, right, "Crafting", me.craft(), me.craftXp(), Stat.MAX_LEVEL, 0xC08A50); cy += 12;
        statRow(g, x + PAD, cy, labelW, lvColW, barX, barW, right, "Smelting", me.smelt(), me.smeltXp(), Stat.MAX_LEVEL, 0xFF8A3A); cy += 12;
        if (mikiko) {
            statRow(g, x + PAD, cy, labelW, lvColW, barX, barW, right, "Hekublade", me.bladeLevel(), me.bladeXp(), BladeLevel.MAX, 0xFF7A1A);
            cy += 12;
        }

        // status grid: two columns of label over value
        cy += 8;
        g.fill(x + PAD, cy - 4, right, cy - 3, 0x30FFFFFF);
        small(g, "STATUS", x + PAD, cy, SECTION);
        cy += 12;
        int colW = (w - PAD * 3) / 2;
        for (int i = 0; i < items.size(); i++) {
            String[] it = items.get(i);
            int cx = x + PAD + (i % 2) * (colW + PAD), ry = cy + (i / 2) * 22;
            small(g, it[0], cx, ry, LABEL);
            text(g, it[1], cx, ry + 9, colW, 0xFF000000 | Integer.parseInt(it[2], 16));
        }
        g.pose().popPose();
        if (me.prisoner()) g.drawCenteredString(font, "You're sealed inside a Hekublade.", width / 2, height - 60, 0xFF5A3A);
    }

    private void statRow(GuiGraphics g, int x, int y, int labelW, int lvColW, int barX, int barW, int right,
                         String name, int level, float xp, int max, int color) {
        text(g, name, x, y, labelW - 4, 0xDDDDDD);
        text(g, "Lv " + commas(level), x + labelW, y, lvColW - 4, 0xFFFFFF);
        boolean maxed = level >= max;
        if (barW > 8) bar(g, barX, y + 2, barW, 5, maxed ? 1F : xp, color);
        textRight(g, maxed ? "MAX" : Math.round(xp * 100) + "%", right, y, 40, 0x999999);
    }

    // ------------------------------------------------------------------ Settings tab

    private void renderSettings(GuiGraphics g) {
        int w = cardW(), x = cardX(), y = SET_TOP - setScroll;
        boolean ef = net.minecraftforge.fml.ModList.get().isLoaded("epicfight");
        int h = settingsHeight();
        g.enableScissor(0, 24, width, height);
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + w, y + 1, 0xFF8C8CA0);
        g.drawCenteredString(font, "Settings", width / 2, y + 6, 0xFFFFFF);
        small(g, "KARMA SENSING", x + PAD, y + 22, SECTION);
        String help = ClientSettings.senseFriendly() ? "On: also shows animals, villagers and other friendly life."
                : "Off: only shows other players and anything that's a threat.";
        for (int i = 0, n = 0; n < 2; n++) {
            String line = font.plainSubstrByWidth(help.substring(i), w - 16);
            if (line.isEmpty()) break;
            g.drawCenteredString(font, line.trim(), width / 2, y + 59 + n * 10, 0x999999);
            i += line.length();
            if (i >= help.length()) break;
        }
        g.fill(x + PAD, y + 82, x + w - PAD, y + 83, 0x30FFFFFF);
        small(g, "LOOK", x + PAD, y + 86, SECTION);
        g.fill(x + PAD, y + 146, x + w - PAD, y + 147, 0x30FFFFFF);
        small(g, "HUD", x + PAD, y + 150, SECTION);
        if (ef) {
            g.fill(x + PAD, y + 210, x + w - PAD, y + 211, 0x30FFFFFF);
            small(g, "EPIC FIGHT", x + PAD, y + 214, SECTION);
        }
        g.disableScissor();
        if (maxSetScroll() > 0) g.drawString(font, setScroll < maxSetScroll() ? "\u25bc scroll" : "\u25b2 scroll", x + w - 52, Math.min(height - 10, y + h - 10), 0x8899AA, false);
    }

    // ------------------------------------------------------------------ Outfits tab

    private void renderOutfits(GuiGraphics g, int mouseX, int mouseY) {
        var p = minecraft.player;
        if (p == null) return;
        CharacterType type = ClientCharacterData.get(p);
        int w = cardW(), x = cardX(), y = OUT_TOP, bottom = height - 20;
        int color = type.getColor() | 0xFF000000;
        g.fill(x, y, x + w, bottom, BG);
        g.fill(x, y, x + w, y + 1, color);
        if (type == CharacterType.NONE) {
            g.drawCenteredString(font, "Choose a warrior first.", width / 2, y + 30, 0xAAAAAA);
            return;
        }
        String[] names = type.outfitNames();
        String cur = names[ClientCharacterData.outfit(p)];
        int[] pb = previewBox();
        text(g, type.getDisplayName().getString() + "  •  " + cur, x + PAD, y + 6, w - PAD * 2, color);
        // preview: your character, turning to follow the mouse, standing in a soft spotlight
        g.fill(pb[0], pb[1], pb[0] + pb[2], pb[1] + pb[3], 0x60000000);
        g.fill(pb[0], pb[1] + pb[3] - 14, pb[0] + pb[2], pb[1] + pb[3], 0x30FFFFFF);
        int size = Math.max(18, Math.min(pb[3] * 36 / 100, pb[2] * 55 / 100));
        int cx = pb[0] + pb[2] / 2, feet = pb[1] + pb[3] - 8;
        g.enableScissor(pb[0], pb[1], pb[0] + pb[2], pb[1] + pb[3]);
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, size,
                cx - mouseX, feet - size * 1.6F - mouseY, p);
        g.disableScissor();
        if (!outfitsStacked()) small(g, "OUTFITS", pb[0] + pb[2] + 10, y + 14, SECTION);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

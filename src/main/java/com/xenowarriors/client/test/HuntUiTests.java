package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.Seasons;
import com.xenowarriors.client.ClientSettings;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.PlayerStatsScreen;
import com.xenowarriors.client.RequestPopups;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.forms.Form;
import com.xenowarriors.statboost.BoostStat;
import com.xenowarriors.statboost.BoostTier;
import com.xenowarriors.statboost.StatBoosts;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

/**
 * Bug hunt: the client UI (focus "huntui", "huntuismall" (shots at 854x480 only), "dmzhuntui" with DragonMineZ,
 * "efhuntui" with Epic Fight, "huntuitacz" with TACZ). Every check is an "XW-TEST huntui ..." line:
 * <ol>
 *   <li>lang: every registered thing of ours has a name, item tooltips show no raw keys;</li>
 *   <li>keys: our key binds that share a key with another bind (vanilla or another mod's);</li>
 *   <li>guide: {key:...} references that don't exist, and the pages shown;</li>
 *   <li>probe: every screen opened and drawn off-screen at several sizes in awkward states (no character, no bots,
 *   Season 1) - exceptions, and buttons off-screen or on top of each other;</li>
 *   <li>request cards: answered by a chat button / the ultimate key, the card goes;</li>
 *   <li>creative tab: Season 2 stat boosts show once the client hears Season 2 is unlocked (as on a dedicated server);</li>
 *   <li>screenshots of the screens and HUDs at 854x480 and 1920x1080.</li>
 * </ol>
 */
public final class HuntUiTests {
    private HuntUiTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^(dmz|ef)", "").startsWith("huntui"); }

    private static void log(String s) { UiTest.log("huntui " + s); }

    private static void server(java.util.function.Consumer<ServerPlayer> f) { UiTest.server(f); }

    private static String pass(boolean b) { return b ? "PASS" : "FAIL"; }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        boolean small = focus.contains("small");
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> { p.setGameMode(GameType.CREATIVE); p.serverLevel().setDayTime(6000); }); }, 20));
        l.add(new Step(null, () -> { lang(mc); keys(mc); guide(mc); }, 5));
        // ---- awkward states: no character, no bots, Season 1
        l.add(new Step(null, () -> server(p -> { clearBots(p); Seasons.set(p.server, 1); CharacterManager.setCharacter(p, CharacterType.NONE); com.xenowarriors.skill.SkillManager.syncLoadout(p); }), 40));
        l.add(new Step(null, () -> { if (!(mc.screen instanceof com.xenowarriors.client.screen.CharacterSelectScreen)) mc.setScreen(null); probeAll(mc, "none_s1"); }, 5));
        l.add(new Step("huntui_none_mainmenu", () -> PlayerStatsScreen.open(), 25));
        l.add(new Step("huntui_none_custom", () -> com.xenowarriors.client.menu.CharacterCustomScreen.open(null), 25));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
        l.add(new Step("huntui_none_hud", () -> {}, 10));
        // ---- a normal state: Mikiko, Season 3, two bots
        l.add(new Step(null, () -> server(p -> { Seasons.set(p.server, 3); CharacterManager.setCharacter(p, CharacterType.MIKIKO); levels(p);
            PartyBots.spawn(p, CharacterType.RHALIX, null); PartyBots.spawn(p, CharacterType.KAERU, null); com.xenowarriors.skill.SkillManager.syncLoadout(p); }), 60));
        l.add(new Step(null, () -> { mc.setScreen(null); probeAll(mc, "mikiko_s3"); }, 5));
        l.add(new Step(null, () -> server(p -> CharacterManager.setCharacter(p, CharacterType.RHALIX)), 40));
        l.add(new Step(null, () -> { mc.setScreen(null); probeAll(mc, "rhalix_s3"); }, 5));
        l.add(new Step(null, () -> server(p -> CharacterManager.setCharacter(p, CharacterType.MIKIKO)), 40));

        requestCards(mc, l);
        creativeTab(mc, l);

        // ---- screenshots
        int[][] sizes = small ? new int[][]{{854, 480, 0}} : new int[][]{{854, 480, 0}, {1920, 1080, 0}, {1920, 1080, 3}};
        for (int[] sz : sizes) shots(mc, l, sz[0], sz[1], sz[2]);
        l.add(new Step(null, () -> { mc.setScreen(null); window(mc, 1280, 720, 0); }, 10));
    }

    // ------------------------------------------------------------------ state helpers

    private static void clearBots(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (CharacterType t : CharacterType.PLAYABLE) p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
    }

    private static void levels(ServerPlayer p) {
        p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeLevel = 300; d.miningLevel = 60; });
        com.xenowarriors.rhalix.WireLevel.set(p, 300);
        com.xenowarriors.compat.GunLevel.setLevel(p, 60);
        com.xenowarriors.stats.Mining.sync(p);
        com.xenowarriors.forms.WeaponForms.sync(p, true);
    }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static void window(Minecraft mc, int w, int h, int gs) {
        try { mc.getWindow().setWindowed(w, h); } catch (Throwable t) { log("resize failed " + t); }
        mc.options.guiScale().set(gs);
        mc.resizeDisplay();
    }

    /** Puts the mouse at this GUI position (for hover tooltips in shots). */
    private static void mouse(Minecraft mc, double gx, double gy) {
        try {
            double s = mc.getWindow().getGuiScale();
            var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos"); fx.setAccessible(true); fx.setDouble(mc.mouseHandler, gx * s);
            var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos"); fy.setAccessible(true); fy.setDouble(mc.mouseHandler, gy * s);
        } catch (Throwable t) { log("mouse failed " + t); }
    }

    // ------------------------------------------------------------------ 1. lang

    private static boolean ours(net.minecraft.resources.ResourceLocation id) { return id != null && XenoWarriors.MOD_ID.equals(id.getNamespace()); }

    private static void lang(Minecraft mc) {
        List<String> missing = new ArrayList<>();
        int n = 0;
        for (var e : ForgeRegistries.ITEMS.getEntries()) if (ours(e.getKey().location())) { n++; if (!I18n.exists(e.getValue().getDescriptionId())) missing.add(e.getValue().getDescriptionId()); }
        for (var e : ForgeRegistries.BLOCKS.getEntries()) if (ours(e.getKey().location())) { n++; if (!I18n.exists(e.getValue().getDescriptionId()) && e.getValue().asItem() != net.minecraft.world.item.Items.AIR) missing.add(e.getValue().getDescriptionId()); }
        for (var e : ForgeRegistries.ENTITY_TYPES.getEntries()) if (ours(e.getKey().location())) { n++; if (!I18n.exists(e.getValue().getDescriptionId())) missing.add(e.getValue().getDescriptionId()); }
        for (var e : ForgeRegistries.MOB_EFFECTS.getEntries()) if (ours(e.getKey().location())) { n++; if (!I18n.exists(e.getValue().getDescriptionId())) missing.add(e.getValue().getDescriptionId()); }
        for (var e : ForgeRegistries.ENCHANTMENTS.getEntries()) if (ours(e.getKey().location())) { n++; if (!I18n.exists(e.getValue().getDescriptionId())) missing.add(e.getValue().getDescriptionId()); }
        for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
            String s = t.getDisplayName().getString();
            if (s.startsWith("itemGroup.xenowarriors")) missing.add(s);
        }
        for (KeyMapping k : mc.options.keyMappings) if (k.getName().startsWith("key.xenowarriors")) {
            n++;
            if (!I18n.exists(k.getName())) missing.add(k.getName());
            if (!I18n.exists(k.getCategory())) missing.add(k.getCategory());
        }
        for (CharacterType t : CharacterType.values()) { n++; String s = t.getDisplayName().getString(); if (s.startsWith("character.")) missing.add(s); }
        log("lang: " + n + " names checked, " + missing.size() + " missing: " + missing + " -> " + pass(missing.isEmpty()));
        // tooltips: no raw keys, no crashes
        List<String> raw = new ArrayList<>(), crash = new ArrayList<>();
        int items = 0;
        for (var e : ForgeRegistries.ITEMS.getEntries()) {
            if (!ours(e.getKey().location())) continue;
            items++;
            try {
                for (var c : new ItemStack(e.getValue()).getTooltipLines(mc.player, TooltipFlag.NORMAL)) {
                    String s = c.getString();
                    if (s.matches(".*\\b[a-z_]+\\.xenowarriors\\.[a-z0-9_.]+.*")) raw.add(e.getKey().location().getPath() + ": " + s);
                }
            } catch (Throwable t) { crash.add(e.getKey().location().getPath() + ": " + t); }
        }
        log("tooltips: " + items + " items, raw keys " + raw.size() + " " + raw + ", errors " + crash.size() + " " + crash + " -> " + pass(raw.isEmpty() && crash.isEmpty()));
    }

    // ------------------------------------------------------------------ 2. key conflicts

    private static void keys(Minecraft mc) {
        int n = 0;
        StringBuilder all = new StringBuilder();
        for (KeyMapping k : mc.options.keyMappings) {
            if (!k.getName().startsWith("key.xenowarriors")) continue;
            n++;
            all.append(" ").append(k.getName().substring(17)).append("=").append(k.getKeyModifier() == net.minecraftforge.client.settings.KeyModifier.NONE ? "" : k.getKeyModifier() + "+").append(k.getKey().getName());
            for (KeyMapping o : mc.options.keyMappings) {
                if (o == k || o.getName().startsWith("key.xenowarriors") || !o.getKey().equals(k.getKey()) || k.getKey().getValue() == -1) continue;
                boolean mod = o.getKeyModifier() == k.getKeyModifier();
                boolean ctx = k.getKeyConflictContext().conflicts(o.getKeyConflictContext()) || o.getKeyConflictContext().conflicts(k.getKeyConflictContext());
                log("keys: " + k.getName() + " (" + k.getKeyModifier() + "+" + k.getKey().getName() + ") shares its key with " + o.getName() + " [" + o.getCategory() + "] ("
                        + o.getKeyModifier() + "+" + o.getKey().getName() + ") sameModifier=" + mod + " contextsConflict=" + ctx + (mod && ctx ? " -> CONFLICT" : " -> differs by modifier/context"));
            }
        }
        log("keys: " + n + " binds:" + all + " mods loaded: dmz=" + net.minecraftforge.fml.ModList.get().isLoaded("dragonminez") + " ef=" + net.minecraftforge.fml.ModList.get().isLoaded("epicfight")
                + " tacz=" + net.minecraftforge.fml.ModList.get().isLoaded("tacz"));
    }

    // ------------------------------------------------------------------ 3. guide

    private static void guide(Minecraft mc) {
        var chapters = com.xenowarriors.client.menu.GuideScreen.load();
        int pages = 0;
        List<String> badKeys = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        var pat = java.util.regex.Pattern.compile("\\{key:([a-z0-9_]+)}");
        Set<String> names = new HashSet<>();
        for (KeyMapping k : mc.options.keyMappings) names.add(k.getName());
        for (var c : chapters) {
            sb.append(" ").append(c.title()).append("(").append(c.pages().size()).append(")");
            for (var p : c.pages()) {
                pages++;
                var m = pat.matcher(p.text());
                while (m.find()) if (!names.contains("key.xenowarriors." + m.group(1))) badKeys.add(p.title() + ":" + m.group(1));
            }
        }
        log("guide: " + chapters.size() + " chapters, " + pages + " pages:" + sb + " | unknown keys " + badKeys + " -> " + pass(badKeys.isEmpty() && pages > 0));
        // the chapter list must fit its column: chapters at 12, the open chapter's pages at 9, in 191 rows (30..221)
        int worst = 0; String worstName = "";
        for (var c : chapters) { int h = chapters.size() * 12 + c.pages().size() * 9; if (h > worst) { worst = h; worstName = c.title(); } }
        log("guide list height (worst: " + worstName + " open) " + worst + " of 191 -> " + (worst <= 191 ? "fits" : "OVERFLOWS"));
    }

    // ------------------------------------------------------------------ 4. probes

    private static final int[][] PROBE_SIZES = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {960, 540}, {1920, 1080}};

    private static void probeAll(Minecraft mc, String state) {
        Map<String, Supplier<Screen>> s = new LinkedHashMap<>();
        s.put("mainmenu", com.xenowarriors.client.menu.MainMenuScreen::new);
        for (int t = 0; t < 4; t++) { int tt = t; s.put("stats" + t, () -> { PlayerStatsScreen.presetTab(tt); return new PlayerStatsScreen(); }); }
        for (int t = 0; t < 4; t++) { int tt = t; s.put("custom" + t, () -> { var c = new com.xenowarriors.client.menu.CharacterCustomScreen(null); c.testTab(tt); return c; }); }
        s.put("skills", () -> new com.xenowarriors.client.SkillLoadoutScreen(null));
        s.put("kaguide", () -> new com.xenowarriors.client.KaGuideScreen(null));
        s.put("guide", () -> new com.xenowarriors.client.menu.GuideScreen(null));
        s.put("party", com.xenowarriors.client.party.PartySetupScreen::new);
        s.put("partyold", com.xenowarriors.client.bot.PartyScreen::new);
        s.put("factions", () -> new com.xenowarriors.client.menu.FactionsScreen(null));
        s.put("pets", () -> new com.xenowarriors.client.menu.PetsScreen(null));
        s.put("select", com.xenowarriors.client.screen.CharacterSelectScreen::new);
        s.put("phone", com.xenowarriors.client.PhoneScreen::new);
        s.put("youslots", com.xenowarriors.client.you.YouSlotsScreen::new);
        s.put("youcreator", () -> new com.xenowarriors.client.you.YouCreatorScreen(0));
        s.put("wheel_heku", () -> new com.xenowarriors.client.forms.FormWheelScreen(Form.Style.HEKU, true));
        s.put("wheel_wire", () -> new com.xenowarriors.client.forms.FormWheelScreen(Form.Style.WIRE, true));
        s.put("botmenu_nobot", () -> new com.xenowarriors.client.bot.BotScreen(new com.xenowarriors.network.OpenBotMenuPacket(-1, "nobody", 0, 1, false, new String[0], new int[0])));
        s.put("botpick_empty", () -> new com.xenowarriors.client.bot.BotPickScreen(new com.xenowarriors.network.OpenBotPickPacket(new String[0], new String[0], 0)));
        s.put("collection_empty", () -> new com.xenowarriors.client.screen.CollectionScreen(new com.xenowarriors.network.CollectionPacket(new String[0], new int[0], 0, true, true, 0, 0)));
        s.put("govshop", () -> new com.xenowarriors.client.screen.GovShopScreen(0, 0));
        s.put("dealer", () -> new com.xenowarriors.client.vehicle.DealerScreen(0));
        s.put("playeraction", () -> new com.xenowarriors.client.party.PlayerActionScreen(UUID.randomUUID(), "Somebody"));
        s.put("journeylobby", com.xenowarriors.client.journey.JourneyLobbyScreen::new);
        s.put("journeyteam", com.xenowarriors.client.journey.JourneyTeamScreen::new);
        int ok = 0, bad = 0;
        for (var e : s.entrySet()) for (int[] sz : PROBE_SIZES) { if (probe(mc, state, e.getKey(), e.getValue(), sz[0], sz[1])) ok++; else bad++; }
        log("probe " + state + ": " + ok + " ok, " + bad + " with problems -> " + pass(bad == 0));
        mc.setScreen(null);
        com.xenowarriors.client.forms.FormWheelScreen.testHover = null;
    }

    private static boolean probe(Minecraft mc, String state, String name, Supplier<Screen> make, int w, int h) {
        Screen s = null;
        boolean hidWas = mc.options.hideGui;
        try {
            s = make.get();
            s.init(mc, w, h);
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            s.render(g, w / 2, h / 2, 0F);
            s.tick();
            s.render(g, w / 3, h / 3, 0.5F);
            g.flush();
            List<String> issues = new ArrayList<>();
            List<AbstractWidget> ws = new ArrayList<>();
            for (var c : s.children()) if (c instanceof AbstractWidget aw && aw.visible && aw.getWidth() > 1) ws.add(aw);
            for (AbstractWidget a : ws)
                if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > w || a.getY() + a.getHeight() > h)
                    issues.add("off-screen '" + a.getMessage().getString() + "' " + a.getX() + "," + a.getY() + " " + a.getWidth() + "x" + a.getHeight());
            for (int i = 0; i < ws.size(); i++) for (int j = i + 1; j < ws.size(); j++) {
                AbstractWidget a = ws.get(i), b = ws.get(j);
                if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth() && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight())
                    issues.add("overlap '" + a.getMessage().getString() + "' & '" + b.getMessage().getString() + "'");
            }
            if (!issues.isEmpty()) log("probe " + state + " " + name + " " + w + "x" + h + " widgets: " + issues);
            return issues.isEmpty();
        } catch (Throwable t) {
            log("probe " + state + " " + name + " " + w + "x" + h + " CRASH " + t + " at " + (t.getStackTrace().length > 0 ? t.getStackTrace()[0] : "?"));
            XenoWarriors.LOGGER.error("XW-TEST huntui probe crash " + name, t);
            return false;
        } finally {
            try { if (s != null) s.removed(); } catch (Throwable ignored) {}
            mc.options.hideGui = hidWas;
        }
    }

    // ------------------------------------------------------------------ 5. request cards

    private static void requestCards(Minecraft mc, List<Step> l) {
        // a party invite answered with the chat message's [Decline] button (a click event runs the command unsigned)
        l.add(new Step(null, () -> server(p -> com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Party Invite",
                "Tester invited you to their party!", "/xeno party accept", "/xeno party decline", 600, 0xFF6CC4FF), p)), 10));
        l.add(new Step(null, () -> { log("cards before the chat button: " + RequestPopups.testCount()); mc.player.connection.sendUnsignedCommand("xeno party decline"); }, 15));
        l.add(new Step(null, () -> log("card after answering with the chat button: " + RequestPopups.testCount() + " left (expect 0) -> " + pass(RequestPopups.testCount() == 0)), 3));
        // a typed command
        l.add(new Step(null, () -> server(p -> com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Battle Challenge",
                "Tester challenges you!", "/xeno duel accept", "/xeno duel decline", 600, 0xFFFF6A5A), p)), 10));
        l.add(new Step(null, () -> mc.player.connection.sendCommand("xeno duel accept"), 15));
        l.add(new Step(null, () -> log("card after typing the accept command: " + RequestPopups.testCount() + " left (expect 0) -> " + pass(RequestPopups.testCount() == 0)), 3));
        // a bot asks for a team ultimate; the player answers with the ultimate key (server side: onUltimateKey)
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX);
            if (r == null) { log("team ult card: no Rhalix bot"); return; }
            com.xenowarriors.party.TeamUltimate.ask(r, p, com.xenowarriors.party.TeamUltimate.Kind.BLAZING_JUDGMENT);
        }), 10));
        l.add(new Step(null, () -> { log("team ult cards shown: " + RequestPopups.testCount()); server(com.xenowarriors.party.TeamUltimate::onUltimateKey); }, 15));
        l.add(new Step(null, () -> log("team ult card after the ultimate key: " + RequestPopups.testCount() + " left (expect 0) -> " + pass(RequestPopups.testCount() == 0)), 3));
        // unrelated commands leave a card alone
        l.add(new Step(null, () -> server(p -> com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Party Invite",
                "Tester invited you to their party!", "/xeno party accept", "/xeno party decline", 600, 0xFF6CC4FF), p)), 10));
        l.add(new Step(null, () -> mc.player.connection.sendCommand("xeno season"), 15));
        l.add(new Step(null, () -> { log("card after an unrelated command: " + RequestPopups.testCount() + " (expect 1) -> " + pass(RequestPopups.testCount() == 1)); RequestPopups.testClear(); }, 3));
    }

    // ------------------------------------------------------------------ 6. creative tab

    private static int wiresInTab(Minecraft mc) {
        CreativeModeTab tab = StatBoosts.TAB.get();
        Item w = StatBoosts.item(BoostStat.WIRES, BoostTier.ONE);
        int n = 0;
        for (ItemStack s : tab.getDisplayItems()) if (s.getItem() == w) n++;
        return n;
    }

    private static void creativeTab(Minecraft mc, List<Step> l) {
        // Season 1, the tab built once (as when you first open the creative inventory)
        l.add(new Step(null, () -> mc.player.connection.sendCommand("xeno season lock"), 30));
        l.add(new Step(null, () -> {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            log("creative tab at Season 1: wires boosts " + wiresInTab(mc) + " (expect 0) client season " + ClientSkillState.season());
        }, 5));
        // the operator unlocks Season 2: the client is told and the tab rebuilds (no relog)
        l.add(new Step(null, () -> mc.player.connection.sendCommand("xeno season unlock 2"), 40));
        l.add(new Step(null, () -> {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());   // (what opening the creative inventory does)
            log("creative tab after /xeno season unlock 2: client season " + ClientSkillState.season() + " (expect 2), wires boosts " + wiresInTab(mc) + " (expect 1) -> "
                    + pass(ClientSkillState.season() == 2 && wiresInTab(mc) == 1));
        }, 5));
        // a dedicated server's client: the server's own season setting isn't in this JVM - only what the server sent
        l.add(new Step(null, () -> {
            com.xenowarriors.character.Seasons.testForgetCached();
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());   // (forces a fresh build)
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            log("creative tab as on a dedicated server (server season not in this JVM, client told Season " + ClientSkillState.season() + "): wires boosts " + wiresInTab(mc)
                    + " (expect 1) -> " + pass(wiresInTab(mc) == 1));
        }, 5));
        l.add(new Step(null, () -> server(p -> p.setGameMode(GameType.CREATIVE)), 10));
        l.add(new Step(null, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true)), 15));
        l.add(new Step(null, () -> selectTab(mc, StatBoosts.TAB.get()), 10));
        l.add(new Step(null, () -> { if (mc.screen != null) mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, -10); }, 10));
        l.add(new Step("huntui_creative_s2_scrolled", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.player.connection.sendCommand("xeno season unlock 3"); }, 30));
    }

    private static net.minecraft.network.chat.MutableComponent button(String text, String cmd, net.minecraft.ChatFormatting c) {
        return net.minecraft.network.chat.Component.literal(text).withStyle(st -> st.withColor(c).withBold(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd)));
    }

    private static void selectTab(Minecraft mc, CreativeModeTab tab) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen cs)) return;
        try {
            var m = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            m.setAccessible(true);
            m.invoke(cs, tab);
        } catch (Throwable t) { log("selectTab failed " + t); }
    }

    // ------------------------------------------------------------------ 7. screenshots

    private static void shots(Minecraft mc, List<Step> l, int w, int h, int gs) {
        String pre = "huntui_" + w + "g" + gs + "_";
        l.add(new Step(null, () -> { mc.setScreen(null); window(mc, w, h, gs); ClientSettings.setHudStyle("blazing");
            server(p -> { CharacterManager.setCharacter(p, CharacterType.MIKIKO); p.setYRot(0); p.setXRot(10); p.getFoodData().setFoodLevel(20);
                if (PartyBots.bots(p).size() < 2) { clearBots(p); PartyBots.spawn(p, CharacterType.RHALIX, null); PartyBots.spawn(p, CharacterType.KAERU, null); } }); }, 50));
        l.add(new Step(null, () -> log("window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " gui " + mc.getWindow().getGuiScale()
                + " scaled " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight()), 2));
        java.util.function.BiConsumer<String, Runnable> shot = (n, r) -> {
            l.add(new Step(null, () -> { mc.setScreen(null); r.run(); }, 25));
            l.add(new Step(pre + n, () -> log("shot " + pre + n + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())), 3));
        };
        shot.accept("mainmenu", PlayerStatsScreen::open);
        shot.accept("custom_stats", () -> { com.xenowarriors.client.menu.CharacterCustomScreen.testBuildingTip = true; com.xenowarriors.client.menu.CharacterCustomScreen.open(null); });
        l.add(new Step(null, () -> com.xenowarriors.client.menu.CharacterCustomScreen.testBuildingTip = false, 1));
        shot.accept("custom_skills", () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.testTab(1); });
        shot.accept("custom_outfits", () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.testTab(2); });
        shot.accept("custom_ka", () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.testTab(3); });
        shot.accept("warriors", () -> PlayerStatsScreen.openTab(null, 0));
        shot.accept("settings", () -> PlayerStatsScreen.openTab(null, 3));
        shot.accept("settings_scrolled", () -> { PlayerStatsScreen.openTab(null, 3); if (mc.screen != null) mc.screen.mouseScrolled(10, 10, -40); });
        shot.accept("party", () -> mc.setScreen(new com.xenowarriors.client.party.PartySetupScreen()));
        shot.accept("collection", () -> server(p -> com.xenowarriors.collection.Collection.open(p, true)));
        shot.accept("guide_keys", () -> { com.xenowarriors.client.menu.GuideScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.GuideScreen g) g.show(0, 1); });
        shot.accept("guide_characters", () -> { com.xenowarriors.client.menu.GuideScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.GuideScreen g) g.show(3, 99); });
        shot.accept("guide_progression", () -> { com.xenowarriors.client.menu.GuideScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.GuideScreen g) g.show(6, 99); });
        shot.accept("skills", () -> mc.setScreen(new com.xenowarriors.client.SkillLoadoutScreen(null)));
        shot.accept("botmenu", () -> server(p -> { PartyBotEntity b = bot(p, CharacterType.RHALIX); if (b != null) PartyBots.interact(b, p); }));
        shot.accept("botpick", () -> server(PartyBots::openPick));
        shot.accept("altar", () -> server(p -> p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.xenowarriors.altar.KarmaAltarMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.NULL), net.minecraft.network.chat.Component.literal("Karma Altar")))));
        shot.accept("wheel", () -> { com.xenowarriors.client.forms.FormWheelScreen.testHover = Form.RIFLE; com.xenowarriors.client.forms.FormsClient.testOpenWheel(); });
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.forms.FormWheelScreen ws) ws.onClose(); com.xenowarriors.client.forms.FormWheelScreen.testHover = null; mc.setScreen(null); }, 10));
        // HUD: 3 styles with two bots, a team-ultimate card and a party invite card
        for (String style : new String[]{"blazing", "kh", "classic"}) {
            l.add(new Step(null, () -> { mc.setScreen(null); ClientSettings.setHudStyle(style); RequestPopups.testClear(); server(p -> {
                PartyBotEntity r = bot(p, CharacterType.RHALIX);
                if (r != null) com.xenowarriors.party.TeamUltimate.ask(r, p, com.xenowarriors.party.TeamUltimate.Kind.BLAZING_JUDGMENT);
                com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Party Invite", "Tester invited you to their party!", "/xeno party accept", "/xeno party decline", 600, 0xFF6CC4FF), p);
            }); }, 30));
            l.add(new Step(pre + "hud_" + style, () -> {}, 3));
        }
        l.add(new Step(null, () -> { RequestPopups.testClear(); ClientSettings.setHudStyle("blazing"); }, 3));
        // chat buttons
        l.add(new Step(null, () -> server(p -> p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Tester invited you to their party! ")
                .append(button("[Accept]", "/xeno party accept", net.minecraft.ChatFormatting.GREEN)).append(" ")
                .append(button("[Decline]", "/xeno party decline", net.minecraft.ChatFormatting.RED)))), 5));
        shot.accept("chat_buttons", () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
        // starving face, Karma Sense
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> { p.setGameMode(GameType.SURVIVAL); p.getFoodData().setFoodLevel(0); }); }, 30));
        l.add(new Step(pre + "hud_starving", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> { p.getFoodData().setFoodLevel(20); p.setGameMode(GameType.CREATIVE);
            for (int i = 0; i < 3; i++) { var c = net.minecraft.world.entity.EntityType.CHICKEN.create(p.serverLevel()); if (c != null) { c.moveTo(p.getX() - 2 + i * 2, p.getY(), p.getZ() + 5); p.serverLevel().addFreshEntity(c); } } }), 20));
        l.add(new Step(null, () -> com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket(true, true, 12)), 8));
        l.add(new Step(pre + "hud_sense", () -> log("sense active=" + com.xenowarriors.client.KarmaSense.active()), 3));
        l.add(new Step(null, () -> com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket(false, false, 0)), 5));
        if (net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) {
            l.add(new Step(null, () -> { mc.setScreen(null); ClientSettings.setHudStyle("kh"); }, 15));
            l.add(new Step(pre + "hud_kh_dmz", () -> {}, 3));
            l.add(new Step(null, () -> ClientSettings.setHudStyle("classic"), 15));
            l.add(new Step(pre + "hud_classic_dmz", () -> {}, 3));
            l.add(new Step(null, () -> ClientSettings.setHudStyle("blazing"), 5));
        }
    }
}

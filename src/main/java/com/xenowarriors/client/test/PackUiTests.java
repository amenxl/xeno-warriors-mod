package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.screen.CharacterSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * (focus "packui") Every screen of the mod, opened with real data in the real pack, checked at the window sizes
 * people play at (GUI scale on 720p / 1080p): buttons on top of each other or off the screen, labels too long for
 * their button, text drawn over other text or over a different button, text off the screen. A picture of each, and
 * the HUD. Kaeru can be picked on the Orenji Clan tab (she's on two teams - it kept jumping back to Mikiko).
 */
final class PackUiTests {
    private PackUiTests() {}

    /** GUI sizes: 720p at scale 3 / 2, 1080p at scale 4 / 2. */
    private static final int[][] SIZES = {{427, 240}, {480, 270}, {640, 360}, {960, 540}};
    private static final Map<String, List<String>> PROBLEMS = new LinkedHashMap<>();
    private static int screensChecked;
    private static PartyBotEntity bot;

    private static void log(String s) { PackTests.log("ui " + s); }

    // ------------------------------------------------------------------ the checks

    /** Lays the screen out at w x h, draws it (mouse off to the side: no hover tooltips) and lists what overlaps. */
    private static List<String> check(Minecraft mc, Screen s, int w, int h) {
        List<String> issues = new ArrayList<>();
        try {
            s.init(mc, w, h);
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            s.render(g, -500, -500, 0F);                                  // (a first frame: some lay out while drawing)
            UiTextRecorder.start();
            s.render(g, -500, -500, 0.5F);
            List<UiTextRecorder.Text> texts = UiTextRecorder.stop();
            g.flush();
            List<AbstractWidget> ws = new ArrayList<>();
            for (var c : s.children()) if (c instanceof AbstractWidget aw && aw.visible && aw.getWidth() > 1 && aw.getHeight() > 1) ws.add(aw);
            for (AbstractWidget a : ws) {
                if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > w || a.getY() + a.getHeight() > h)
                    issues.add("button off-screen '" + a.getMessage().getString() + "' at " + a.getX() + "," + a.getY() + " " + a.getWidth() + "x" + a.getHeight());
                if (a instanceof AbstractButton && mc.font.width(a.getMessage()) > a.getWidth() - 4)
                    issues.add("label too long for its button '" + a.getMessage().getString() + "' (" + mc.font.width(a.getMessage()) + " in " + a.getWidth() + ")");
            }
            for (int i = 0; i < ws.size(); i++) for (int j = i + 1; j < ws.size(); j++) {
                AbstractWidget a = ws.get(i), b = ws.get(j);
                if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth() && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight())
                    issues.add("buttons overlap '" + a.getMessage().getString() + "' & '" + b.getMessage().getString() + "'");
            }
            for (UiTextRecorder.Text t : texts)
                if (t.x0() < -1 || t.y0() < -1 || t.x1() > w + 1 || t.y1() > h + 1)
                    issues.add("text off-screen '" + cut(t.s()) + "' " + box(t));
            for (int i = 0; i < texts.size(); i++) for (int j = i + 1; j < texts.size(); j++) {
                var a = texts.get(i); var b = texts.get(j);
                if (a.s().equals(b.s())) continue;                          // (the same text drawn again: an outline, a glow)
                float ox = Math.min(a.x1(), b.x1()) - Math.max(a.x0(), b.x0()), oy = Math.min(a.y1(), b.y1()) - Math.max(a.y0(), b.y0());
                if (ox > 2 && oy > 2) issues.add("text over text '" + cut(a.s()) + "' " + box(a) + " & '" + cut(b.s()) + "' " + box(b));
            }
            for (UiTextRecorder.Text t : texts) for (AbstractWidget a : ws) {
                String label = a.getMessage().getString();
                if (!label.isEmpty() && (label.contains(t.s()) || t.s().contains(label))) continue;   // (its own label)
                float ox = Math.min(t.x1(), a.getX() + a.getWidth() - 1) - Math.max(t.x0(), a.getX() + 1), oy = Math.min(t.y1(), a.getY() + a.getHeight() - 1) - Math.max(t.y0(), a.getY() + 1);
                if (ox > 2 && oy > 2) issues.add("text over a button '" + cut(t.s()) + "' " + box(t) + " on '" + cut(label) + "' " + a.getX() + "," + a.getY() + " " + a.getWidth() + "x" + a.getHeight());
            }
        } catch (Throwable t) {
            issues.add("CRASH " + t + " at " + (t.getStackTrace().length > 0 ? t.getStackTrace()[0] : "?"));
            XenoWarriors.LOGGER.error("XW-TEST pack ui crash", t);
        } finally { UiTextRecorder.on = false; }
        return issues;
    }

    private static String cut(String s) { s = s.replace('\n', ' '); return s.length() > 40 ? s.substring(0, 40) + "..." : s; }
    private static String box(UiTextRecorder.Text t) { return "[" + (int) t.x0() + "," + (int) t.y0() + " " + (int) t.w() + "x" + (int) (t.h() + 1) + "]"; }

    /** Checks the open screen at every size, then puts it back at the window's size (for its picture). */
    private static void checkOpen(Minecraft mc, String name) {
        Screen s = mc.screen;
        if (s == null) { record(name, "never opened"); log(name + ": NOT OPEN"); return; }
        screensChecked++;
        log(name + ": " + s.getClass().getSimpleName());
        for (int[] sz : SIZES) {
            List<String> issues = check(mc, s, sz[0], sz[1]);
            for (String i : issues) record(name + " @" + sz[0] + "x" + sz[1], i);
            log(name + " @" + sz[0] + "x" + sz[1] + ": " + (issues.isEmpty() ? "ok" : issues.size() + " problems: " + issues));
        }
        s.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private static void record(String where, String what) { PROBLEMS.computeIfAbsent(where, k -> new ArrayList<>()).add(what); }

    private static void close(Minecraft mc) {
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && mc.player != null) mc.player.closeContainer();
        mc.setScreen(null);
    }

    // ------------------------------------------------------------------ the screens

    private static void client(Minecraft mc, List<Step> l, String name, Supplier<Screen> make) {
        l.add(new Step(null, () -> { close(mc); try { mc.setScreen(make.get()); } catch (Throwable t) { record(name, "CRASH opening: " + t); XenoWarriors.LOGGER.error("XW-TEST pack ui open " + name, t); } }, 12));
        l.add(new Step("ui_" + name, () -> {}, 2));
        l.add(new Step(null, () -> checkOpen(mc, name), 2));
    }

    private static void server(Minecraft mc, List<Step> l, String name, Consumer<ServerPlayer> open) {
        l.add(new Step(null, () -> { close(mc); PackTests.server(open); }, 20));
        l.add(new Step("ui_" + name, () -> {}, 2));
        l.add(new Step(null, () -> checkOpen(mc, name), 2));
    }

    private static CompoundTag dialogue() {
        CompoundTag t = new CompoundTag(), line = new CompoundTag();
        line.putString("s", "mikiko");
        line.putString("t", "We can't let the Sky Patrol find the hideout - not after everything we went through to build it. Are you with me?");
        ListTag ch = new ListTag();
        ch.add(StringTag.valueOf("Always. Let's go."));
        ch.add(StringTag.valueOf("Give me a minute to think about it first..."));
        line.put("choice", ch);
        ListTag lines = new ListTag(); lines.add(line);
        t.put("lines", lines); t.putBoolean("host", true); t.putString("place", "Freedom Fighters Hideout");
        return t;
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { close(mc); mc.options.hideGui = false; com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO); PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            com.xenowarriors.character.Seasons.set(p.server, 5);
            p.getPersistentData().remove("xw_bot_rejoin_garry");
            bot = PartyBots.spawn(p, CharacterType.GARRY, null);
            if (bot != null) { bot.setNoAi(true); bot.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0); }
        }); }, 40));

        // Kaeru on the Orenji Clan tab (the user couldn't pick her there)
        l.add(new Step(null, () -> { CharacterSelectScreen.groupForTest(CharacterSelectScreen.Group.ORENJI); mc.setScreen(new CharacterSelectScreen()); }, 10));
        l.add(new Step(null, () -> { if (mc.screen instanceof CharacterSelectScreen c) c.clickCardForTest(CharacterType.KAERU); }, 5));
        l.add(new Step("ui_select_orenji_kaeru", () -> {
            var c = mc.screen instanceof CharacterSelectScreen cs ? cs : null;
            PackTests.check("select: Kaeru can be picked on the Orenji Clan tab", c != null && c.focusForTest() == CharacterType.KAERU && c.selectActiveForTest(),
                    c == null ? "no screen" : "shown " + c.focusForTest() + ", SELECT " + (c.selectActiveForTest() ? "on" : "off"));
        }, 3));
        l.add(new Step(null, () -> { if (mc.screen instanceof CharacterSelectScreen c) c.clickCardForTest(CharacterType.MIKIKO); }, 5));
        l.add(new Step(null, () -> {
            var c = mc.screen instanceof CharacterSelectScreen cs ? cs : null;
            PackTests.check("select: Mikiko too (Orenji Clan tab)", c != null && c.focusForTest() == CharacterType.MIKIKO, c == null ? "no screen" : "shown " + c.focusForTest());
        }, 3));
        for (CharacterSelectScreen.Group g : CharacterSelectScreen.Group.values())
            client(mc, l, "select_" + g.name().toLowerCase(), () -> { CharacterSelectScreen.groupForTest(g); return new CharacterSelectScreen(); });

        Map<String, Supplier<Screen>> C = new LinkedHashMap<>();
        C.put("mainmenu", com.xenowarriors.client.menu.MainMenuScreen::new);
        C.put("stats", com.xenowarriors.client.PlayerStatsScreen::new);
        C.put("custom", () -> new com.xenowarriors.client.menu.CharacterCustomScreen(null));
        C.put("skills", () -> new com.xenowarriors.client.SkillLoadoutScreen(null));
        C.put("kaguide", () -> new com.xenowarriors.client.KaGuideScreen(null));
        C.put("guide", () -> new com.xenowarriors.client.menu.GuideScreen(null));
        C.put("party", com.xenowarriors.client.bot.PartyScreen::new);
        C.put("partysetup", () -> new com.xenowarriors.client.party.PartySetupScreen());
        C.put("factions", () -> new com.xenowarriors.client.menu.FactionsScreen(null));
        C.put("pets", () -> new com.xenowarriors.client.menu.PetsScreen(null));
        C.put("phone", com.xenowarriors.client.PhoneScreen::new);
        C.put("youslots", com.xenowarriors.client.you.YouSlotsScreen::new);
        C.put("youcreator", () -> new com.xenowarriors.client.you.YouCreatorScreen(0));
        C.put("youlook", () -> new com.xenowarriors.client.you.YouLookScreen(0, new com.xenowarriors.you.YouSlot(), null));
        C.put("youfeature", () -> new com.xenowarriors.client.you.YouFeatureScreen(0, new com.xenowarriors.you.YouSlot(), null));
        C.put("youcolour", () -> new com.xenowarriors.client.you.YouColourScreen("Tester", 0xFF8800, 0, null, c -> {}));
        C.put("youskin", () -> new com.xenowarriors.client.you.YouSkinScreen("Tester", "", null, s -> {}));
        C.put("wheel_heku", () -> new com.xenowarriors.client.forms.FormWheelScreen(com.xenowarriors.forms.Form.Style.HEKU, true));
        C.put("wheel_wire", () -> new com.xenowarriors.client.forms.FormWheelScreen(com.xenowarriors.forms.Form.Style.WIRE, true));
        C.put("togglewheel", () -> new com.xenowarriors.client.ToggleWheelScreen(true));
        C.put("govshop", () -> new com.xenowarriors.client.screen.GovShopScreen(12345, 10));
        C.put("dealer", () -> new com.xenowarriors.client.vehicle.DealerScreen(20, 50000));
        C.put("playeraction", () -> new com.xenowarriors.client.party.PlayerActionScreen(UUID.randomUUID(), "Somebody"));
        C.put("journeylobby", com.xenowarriors.client.journey.JourneyLobbyScreen::new);
        C.put("journeyteam", com.xenowarriors.client.journey.JourneyTeamScreen::new);
        C.put("journeymenu", () -> new com.xenowarriors.client.journey.JourneyMenuScreen(false));
        C.put("journeyresults", () -> new com.xenowarriors.client.journey.JourneyMenuScreen(true));
        C.put("story", () -> new com.xenowarriors.client.story.StoryScreen(null));
        C.put("storydialogue", () -> new com.xenowarriors.client.story.StoryDialogueScreen(dialogue()));
        C.put("boostbag", () -> { int[] c = new int[64]; for (int i = 0; i < c.length; i += 3) c[i] = 1 + i % 7; return new com.xenowarriors.client.BoostBagScreen(c); });
        C.put("rps", () -> new com.xenowarriors.client.party.RpsScreen(new com.xenowarriors.network.RpsPacket(bot == null ? -1 : bot.getId(), "Garry", 1, -1, -1, 0)));
        C.put("wingpet", () -> new com.xenowarriors.client.wing.WingPetScreen(new com.xenowarriors.network.OpenWingPetPacket(UUID.randomUUID(), -1, "Skye", 0, 14F, 20F, "wing")));
        C.put("scent", () -> new com.xenowarriors.client.kaeru.ScentScreen(List.of(new com.xenowarriors.network.ScentPacket.Entry(1, "Zombie", 0, 12),
                new com.xenowarriors.network.ScentPacket.Entry(2, "Sky Patrol Soldier", 1, 40), new com.xenowarriors.network.ScentPacket.Entry(3, "Diamond Ore", 2, 7))));
        C.put("foxname", () -> new com.xenowarriors.client.fox.FoxNameScreen(UUID.randomUUID(), "Foxy", true));
        C.put("inventory", () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        C.put("pause", () -> new net.minecraft.client.gui.screens.PauseScreen(true));

        for (var e : C.entrySet()) client(mc, l, e.getKey(), e.getValue());
        server(mc, l, "botmenu", p -> { if (bot != null) PartyBots.interact(bot, p); });
        server(mc, l, "botbag", p -> { if (bot != null) PartyBots.openBag(p, bot); });
        server(mc, l, "botpick", PartyBots::openPick);
        server(mc, l, "vr", com.xenowarriors.vr.Vr::open);
        server(mc, l, "bounty", com.xenowarriors.bounty.Bounties::open);
        server(mc, l, "govshop_real", com.xenowarriors.gov.GovShop::open);
        server(mc, l, "collection", p -> com.xenowarriors.collection.Collection.open(p, true));
        server(mc, l, "missionbag", com.xenowarriors.compat.MissionBag::open);
        server(mc, l, "karmaaltar", p -> p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.xenowarriors.altar.KarmaAltarMenu(id, inv,
                net.minecraft.world.inventory.ContainerLevelAccess.NULL), net.minecraft.network.chat.Component.literal("Karma Altar"))));
        l.add(new Step(null, () -> { close(mc); com.xenowarriors.client.screen.C2Screen.ask(); }, 20));
        l.add(new Step("ui_c2", () -> {}, 2));
        l.add(new Step(null, () -> checkOpen(mc, "c2"), 2));

        // the HUD: what's drawn over the game (no screen open)
        l.add(new Step(null, () -> { close(mc); mc.options.hideGui = false; UiTextRecorder.start(); }, 6));
        l.add(new Step("ui_hud", () -> {
            List<UiTextRecorder.Text> texts = UiTextRecorder.stop();
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            List<String> issues = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < texts.size(); i++) for (int j = i + 1; j < texts.size(); j++) {
                var a = texts.get(i); var b = texts.get(j);
                if (a.s().equals(b.s())) continue;
                float ox = Math.min(a.x1(), b.x1()) - Math.max(a.x0(), b.x0()), oy = Math.min(a.y1(), b.y1()) - Math.max(a.y0(), b.y0());
                if (ox > 2 && oy > 2 && seen.add(a.s() + "|" + b.s())) issues.add("text over text '" + cut(a.s()) + "' " + box(a) + " & '" + cut(b.s()) + "' " + box(b));
            }
            for (var t : texts) if ((t.x0() < -1 || t.x1() > w + 1 || t.y0() < -1 || t.y1() > h + 1) && seen.add("off|" + t.s())) issues.add("text off-screen '" + cut(t.s()) + "' " + box(t));
            for (String i : issues) record("hud", i);
            log("hud " + w + "x" + h + ": " + texts.size() + " texts, " + (issues.isEmpty() ? "ok" : issues.size() + " problems: " + issues));
        }, 3));

        // the same screens at GUI scale 2 (bigger windows / a smaller GUI): pictures only, the sizes are checked above
        l.add(new Step(null, () -> { close(mc); mc.options.guiScale().set(2); mc.resizeDisplay(); }, 10));
        for (var e : C.entrySet()) {
            String name = e.getKey(); Supplier<Screen> make = e.getValue();
            if (name.equals("inventory") || name.equals("pause")) continue;
            l.add(new Step(null, () -> { close(mc); try { mc.setScreen(make.get()); } catch (Throwable t) { record(name, "CRASH opening (scale 2): " + t); } }, 10));
            l.add(new Step("ui2_" + name, () -> {}, 2));
        }
        for (CharacterSelectScreen.Group g : CharacterSelectScreen.Group.values()) {
            l.add(new Step(null, () -> { close(mc); CharacterSelectScreen.groupForTest(g); mc.setScreen(new CharacterSelectScreen()); }, 10));
            l.add(new Step("ui2_select_" + g.name().toLowerCase(), () -> {}, 2));
        }
        l.add(new Step(null, () -> { close(mc); mc.options.guiScale().set(0); mc.resizeDisplay(); }, 10));

        l.add(new Step("ui_done", () -> {
            close(mc);
            log("screens checked: " + screensChecked);
            int n = 0;
            for (var e : PROBLEMS.entrySet()) { n += e.getValue().size(); log("PROBLEMS " + e.getKey() + ": " + e.getValue()); }
            PackTests.check("ui: every screen opened", !PROBLEMS.values().stream().flatMap(List::stream).anyMatch(s -> s.startsWith("never opened") || s.startsWith("CRASH")),
                    "" + PROBLEMS.entrySet().stream().filter(e -> e.getValue().stream().anyMatch(s -> s.startsWith("never opened") || s.startsWith("CRASH"))).map(Map.Entry::getKey).toList());
            PackTests.check("ui: nothing overlaps", n == 0, n + " problems in " + PROBLEMS.size() + " screen/size combos");
            PackTests.server(p -> { PackTests.clear(p); bot = null; });
        }, 5));
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotBags;
import com.xenowarriors.bot.BotInventory;
import com.xenowarriors.bot.OffDuty;
import com.xenowarriors.bot.OffDuty.Act;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterInventories;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Seasons;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.party.Bonds;
import com.xenowarriors.party.Parties;
import com.xenowarriors.stats.Building;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

/** Off-duty bots (bot.OffDuty), focus "offduty". Logs "XW-TEST offduty ... -> PASS/FAIL" and a summary. */
public final class OffDutyTests {
    private OffDutyTests() {}

    public static boolean handles(String focus) { return "offduty".equals(focus); }

    private static int pass, fail;
    private static final List<String> FAILED = new ArrayList<>();

    private static void log(String s) { UiTest.log("offduty " + s); }

    private static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else { fail++; FAILED.add(what); }
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    private static void server(java.util.function.Consumer<ServerPlayer> c) {
        UiTest.server(p -> { try { c.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); com.xenowarriors.XenoWarriors.LOGGER.error("XW-TEST offduty", t); } });
    }

    private static int count(ServerPlayer p, CharacterType t, java.util.function.Predicate<Item> which) {
        BotInventory inv = new BotInventory();
        inv.loadBag(CharacterInventories.mainItems(CharacterInventories.saved(p, t.getId())));
        inv.loadExtra(p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getCompound("xw_botbags").getList(t.getId(), 10));
        int n = 0;
        for (int i = 0; i < BotInventory.SIZE; i++) if (!inv.getItem(i).isEmpty() && which.test(inv.getItem(i).getItem())) n += inv.getItem(i).getCount();
        return n;
    }

    private static void emptyBag(ServerPlayer p, CharacterType t) {
        CompoundTag s = CharacterInventories.saved(p, t.getId());
        s.put("inv", new ListTag());
        CharacterInventories.putSaved(p, t.getId(), s);
        BotBags.take(p, t.getId());
    }

    private static void fillBag(ServerPlayer p, CharacterType t) {
        CompoundTag s = CharacterInventories.saved(p, t.getId());
        ListTag inv = new ListTag();
        for (int i = 0; i < 36; i++) { CompoundTag it = new ItemStack(Items.STONE, 64).save(new CompoundTag()); it.putByte("Slot", (byte) i); inv.add(it); }
        s.put("inv", inv);
        CharacterInventories.putSaved(p, t.getId(), s);
        BotInventory tabs = new BotInventory();
        for (int i = BotInventory.BAG; i < BotInventory.SIZE; i++) tabs.setItem(i, new ItemStack(Items.DIRT, 64));
        BotBags.take(p, t.getId());
        BotBags.store(p, t.getId(), tabs.extraTag());
    }

    private static int statLv(ServerPlayer p, CharacterType t, int i) {
        int[] lv = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.charStats.getCompound(t.getId()).getIntArray("lv")).orElse(new int[3]);
        return lv.length == 3 ? lv[i] : 0;
    }

    private static void setMining(ServerPlayer p, int lv) { p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.miningLevel = lv; d.miningXp = 0; }); }

    private static int mining(ServerPlayer p) { return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningLevel).orElse(-1); }

    private static void clearBots(ServerPlayer p) { for (PartyBotEntity b : PartyBots.bots(p)) { PartyBots.checkIn(b, p); b.discard(); } }

    private static boolean has(ServerPlayer p, CharacterType t) { return PartyBots.bots(p).stream().anyMatch(b -> b.character() == t); }

    private static String cmd(ServerPlayer p, String c) {
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), c);
        return c;
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        long[] now = new long[1];
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 40));
        l.add(new Step(null, () -> server(p -> {
            clearBots(p);
            Seasons.set(p.server, 3);
            p.setGameMode(GameType.SURVIVAL);
            OffDuty.setEnabled(p.server, true);
            now[0] = p.level().getGameTime();
            for (CharacterType t : CharacterType.PLAYABLE) { OffDuty.setForTest(p, t, Act.NONE, now[0] + 1_000_000, null); Bonds.set(p, t.getId(), 0, 0); }
            log("setup live=" + CharacterInventories.live(p) + " limit=" + Parties.limit() + " enabled=" + OffDuty.enabled());
        }), 10));

        // 1. mining: ore by Mining level, Mining XP, none when the bag's full
        l.add(new Step(null, () -> server(p -> {
            CharacterType t = CharacterType.RHALIX;
            Bonds.set(p, t.getId(), 55, 0);
            String m = OffDuty.assign(p, t, "mining");
            emptyBag(p, t);
            setMining(p, 0);
            OffDuty.simulate(p, t, 300, now[0], RandomSource.create(11));
            int ores = count(p, t, it -> true), rare = count(p, t, OffDuty::rare), dia = count(p, t, it -> it == Items.DIAMOND);
            int lvAfter = mining(p);
            check("mining low level", ores > 50 && lvAfter > 0, "assign='" + m + "' 300 min at Mining 0: " + ores + " ore items, rare " + rare + ", diamonds " + dia + ", Mining 0->" + lvAfter);
            emptyBag(p, t);
            setMining(p, 120);
            OffDuty.simulate(p, t, 300, now[0], RandomSource.create(11));
            int ores2 = count(p, t, it -> true), rare2 = count(p, t, OffDuty::rare), dia2 = count(p, t, it -> it == Items.DIAMOND);
            check("mining high level rarer", rare2 > rare * 2 && dia2 > dia, "300 min at Mining 120: " + ores2 + " ore items, rare " + rare2 + ", diamonds " + dia2);
            fillBag(p, t);
            int before = count(p, t, it -> true), mlv = mining(p);
            float mxp = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningXp).orElse(0F);
            OffDuty.simulate(p, t, 120, now[0], RandomSource.create(5));
            float mxp2 = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningXp).orElse(0F);
            check("full bag: no mining", OffDuty.bagFull(p, t) && count(p, t, it -> true) == before && mining(p) == mlv && mxp2 == mxp,
                    "items " + before + " -> " + count(p, t, it -> true) + ", Mining xp " + mxp + " -> " + mxp2 + ", status '" + OffDuty.status(p, t) + "'");
            emptyBag(p, t);
            setMining(p, 0);
            OffDuty.assign(p, t, "recall");
        }), 10));

        // 2. training / building gains; wood
        l.add(new Step(null, () -> server(p -> {
            CharacterType t = CharacterType.KYRIE;
            Bonds.set(p, t.getId(), 55, 0);
            OffDuty.assign(p, t, "training");
            int a0 = statLv(p, t, 1), d0 = statLv(p, t, 2);
            OffDuty.simulate(p, t, 240, now[0], RandomSource.create(2));
            check("training levels Attack/Defense", statLv(p, t, 1) > a0 && statLv(p, t, 2) > d0, "Attack " + a0 + "->" + statLv(p, t, 1) + ", Defense " + d0 + "->" + statLv(p, t, 2));
            CharacterType b = CharacterType.LEXY;
            Bonds.set(p, b.getId(), 55, 0);
            OffDuty.assign(p, b, "building");
            int b0 = Building.level(p, b.getId());
            OffDuty.simulate(p, b, 240, now[0], RandomSource.create(2));
            check("building levels Building", Building.level(p, b.getId()) > b0, "Building " + b0 + "->" + Building.level(p, b.getId()));
            CharacterType w = CharacterType.MAX;
            Bonds.set(p, w.getId(), 55, 0);
            OffDuty.assign(p, w, "wood");
            emptyBag(p, w);
            OffDuty.simulate(p, w, 60, now[0], RandomSource.create(2));
            int logs = count(p, w, it -> it == Items.OAK_LOG || it == Items.BIRCH_LOG || it == Items.SPRUCE_LOG || it == Items.DARK_OAK_LOG);
            check("wood into bag", logs > 20, logs + " logs after 60 min");
            for (CharacterType x : new CharacterType[]{t, b, w}) OffDuty.assign(p, x, "recall");
            emptyBag(p, w);
            // relaxing after a lot of downs / long time with you
            OffDuty.setForTest(p, CharacterType.SAM, Act.NONE, 0, null);
            OffDuty.setTeamMinutesForTest(p, CharacterType.SAM, 200);
            Act r = OffDuty.chooseForTest(p, CharacterType.SAM, now[0]);
            check("relax after long time on team", r == Act.RELAX_LONG, "chose " + r + " ('" + OffDuty.status(p, CharacterType.SAM) + "')");
            OffDuty.setForTest(p, CharacterType.SAM, Act.NONE, now[0] + 1_000_000, null);
        }), 10));

        // 3. a party group can't be added; a high bond overrides (and they bring their haul)
        l.add(new Step(null, () -> server(p -> {
            for (CharacterType t : CharacterType.PLAYABLE) OffDuty.setForTest(p, t, Act.NONE, now[0] + 1_000_000, null);
            for (CharacterType t : CharacterType.PLAYABLE) if (t != CharacterType.SAM && t != CharacterType.STEVIE && t != CharacterType.GARRY && t != CharacterType.MIKIKO)
                OffDuty.setForTest(p, t, Act.TRAINING, now[0] + 1_000_000, null);         // (everyone else busy: the party picks Stevie / Garry)
            boolean started = OffDuty.startPartyForTest(p, CharacterType.SAM, p.level().getGameTime(), 30);
            String st = OffDuty.status(p, CharacterType.SAM);
            PartyBots.add(p, new String[]{"sam"});
            check("party group can't be added", started && st.startsWith("Out at a party with") && !has(p, CharacterType.SAM), "status '" + st + "', sam added=" + has(p, CharacterType.SAM));
            CharacterType g2 = OffDuty.act(p, CharacterType.STEVIE) == Act.PARTY ? CharacterType.STEVIE : CharacterType.GARRY;
            Bonds.set(p, g2.getId(), 65, 0);
            OffDuty.giveForTest(p, g2, new ItemStack(Items.DIAMOND, 3));
            PartyBots.add(p, new String[]{g2.getId()});
            PartyBotEntity bot = PartyBots.bots(p).stream().filter(b -> b.character() == g2).findFirst().orElse(null);
            String st2 = OffDuty.status(p, CharacterType.SAM);
            check("high bond overrides", bot != null && OffDuty.act(p, g2) == Act.NONE && !st2.contains(g2.getDisplayName().getString()),
                    g2.getId() + " added=" + (bot != null) + ", sam now '" + st2 + "'");
            check("brings its haul", bot != null && bot.inventory.total(Items.DIAMOND) >= 3, "diamonds in its bag " + (bot == null ? -1 : bot.inventory.total(Items.DIAMOND)));
            for (CharacterType t : CharacterType.PLAYABLE) if (t != g2) OffDuty.setForTest(p, t, Act.NONE, now[0] + 1_000_000, null);
        }), 20));

        // 4. low-bond idle leave
        l.add(new Step(null, () -> server(p -> {
            PartyBotEntity bot = PartyBots.bots(p).stream().findFirst().orElse(null);
            if (bot == null) { check("idle leave", false, "no bot"); return; }
            CharacterType t = bot.character();
            long n = p.level().getGameTime();
            Bonds.set(p, t.getId(), 10, 0);
            OffDuty.setTeamMinutesForTest(p, t, 30);
            OffDuty.resetLeave(p.getUUID());
            OffDuty.setLastCombat(p.getUUID(), n - 5 * OffDuty.MINUTE);
            PartyBotEntity none = OffDuty.teamMinute(p, n, RandomSource.create(1), true);
            check("no leave after recent combat", none == null && has(p, t), "left=" + (none != null));
            OffDuty.setLastCombat(p.getUUID(), n - 25 * OffDuty.MINUTE);
            Bonds.set(p, t.getId(), 40, 0);
            PartyBotEntity hi = OffDuty.teamMinute(p, n, RandomSource.create(1), true);
            check("bond 40 stays", hi == null && has(p, t), "left=" + (hi != null));
            Bonds.set(p, t.getId(), 10, 0);
            PartyBotEntity left = OffDuty.teamMinute(p, n, RandomSource.create(1), true);
            check("low bond leaves after quiet spell", left != null && !has(p, t) && OffDuty.act(p, t) == Act.TRAINING, "left=" + (left != null) + " now '" + OffDuty.status(p, t) + "'");
            PartyBotEntity again = OffDuty.teamMinute(p, n + 60, RandomSource.create(1), true);
            check("leave rate-limited", again == null, "second leave=" + (again != null));
            OffDuty.setForTest(p, t, Act.NONE, now[0] + 1_000_000, null);
        }), 20));

        // 5. jobs by bond; 6. locked characters idle
        l.add(new Step(null, () -> server(p -> {
            CharacterType t = CharacterType.TECHNICA;
            Bonds.set(p, t.getId(), 40, 0);
            String no = OffDuty.assign(p, t, "mining");
            boolean refused = OffDuty.act(p, t) == Act.NONE;
            Bonds.set(p, t.getId(), 55, 0);
            String yes = OffDuty.assign(p, t, "mining");
            OffDuty.simulate(p, t, 3, now[0], RandomSource.create(4));
            boolean on = OffDuty.act(p, t) == Act.MINING && OffDuty.status(p, t).contains("your orders");
            PartyBots.add(p, new String[]{t.getId()});
            boolean joined = has(p, t) && OffDuty.task(p, t).isEmpty();
            check("job needs bond 50", refused && on && joined, "bond 40: '" + no + "' | bond 55: '" + yes + "' | added (recall)=" + joined);
            clearBots(p);
            Seasons.set(p.server, 1);
            CharacterType k = CharacterType.KAERU;
            OffDuty.setForTest(p, k, Act.TRAINING, now[0] + 1_000_000, "training");
            int a0 = statLv(p, k, 1);
            OffDuty.simulate(p, k, 60, now[0], RandomSource.create(4));
            String lockedJob = OffDuty.assign(p, k, "training");
            check("locked character idle", OffDuty.act(p, k) == Act.NONE && statLv(p, k, 1) == a0 && OffDuty.task(p, k).isEmpty(), "act " + OffDuty.act(p, k) + ", attack " + a0 + "->" + statLv(p, k, 1) + ", job: '" + lockedJob + "'");
            Seasons.set(p.server, 3);
        }), 20));

        // 7. commands: creative only; off stops everything; party limit
        l.add(new Step(null, () -> server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            cmd(p, "xeno offduty off");
            boolean s1 = OffDuty.enabled();
            cmd(p, "xeno partylimit 6");
            int lim1 = Parties.limit();
            p.setGameMode(GameType.CREATIVE);
            cmd(p, "xeno offduty off");
            boolean s2 = OffDuty.enabled();
            CharacterType t = CharacterType.YURI;
            OffDuty.setForTest(p, t, Act.TRAINING, now[0] + 1_000_000, "training");
            int a0 = statLv(p, t, 1);
            OffDuty.simulate(p, t, 120, now[0], RandomSource.create(4));
            int a1 = statLv(p, t, 1);
            cmd(p, "xeno offduty on");
            boolean s3 = OffDuty.enabled();
            check("offduty command creative only", s1 && !s2 && s3 && a0 == a1, "survival off -> enabled=" + s1 + ", creative off -> " + s2 + " (attack while off " + a0 + "->" + a1 + "), on -> " + s3);
            cmd(p, "xeno partylimit 6");
            int lim2 = Parties.limit();
            OffDuty.setForTest(p, t, Act.NONE, now[0] + 1_000_000, null);
            for (CharacterType x : CharacterType.PLAYABLE) Bonds.set(p, x.getId(), 70, 0);
            PartyBots.add(p, new String[]{"rhalix", "kaeru", "kyrie", "lexy", "max", "sam", "stevie"});
            int bots = PartyBots.bots(p).size();
            cmd(p, "xeno partylimit 40");
            int lim3 = Parties.limit();
            check("partylimit creative only + bigger party", lim1 == 4 && lim2 == 6 && bots == 5 && lim3 == 6, "survival 6 -> " + lim1 + ", creative 6 -> " + lim2 + ", bots added " + bots + " (cap 5), 40 -> " + lim3 + " (rejected by the 1-16 argument)");
            OffDuty.setPartyLimit(p.server, 99);
            int lim4 = Parties.limit();
            OffDuty.setPartyLimit(p.server, 6);
            check("partylimit clamp", lim4 == 16, "99 -> " + lim4);
        }), 40));
        l.add(new Step("offduty_party_hud", () -> mc.setScreen(null), 30));

        // stills: the pick screen with every kind of activity, and the job menu
        l.add(new Step(null, () -> server(p -> {
            clearBots(p);
            OffDuty.setPartyLimit(p.server, 4);
            long n = p.level().getGameTime() + 1_000_000;
            Act[] show = {Act.TRAINING, Act.MINING, Act.WOOD, Act.BUILDING, Act.DMZ, Act.RELAX_DOWNED, Act.RELAX_LONG, Act.NONE};
            int i = 0;
            for (CharacterType t : CharacterType.PLAYABLE) {
                if (t == CharacterType.MIKIKO) continue;
                OffDuty.setForTest(p, t, i < show.length ? show[i] : Act.NONE, n, null);
                Bonds.set(p, t.getId(), i % 3 == 0 ? 65 : 20, 0);
                i++;
            }
            OffDuty.setForTest(p, CharacterType.STEVIE, Act.NONE, n, null);
            OffDuty.setForTest(p, CharacterType.GARRY, Act.NONE, n, null);
            for (CharacterType t : CharacterType.PLAYABLE) if (t != CharacterType.STEVIE && t != CharacterType.GARRY && t != CharacterType.LOGAN && OffDuty.act(p, t) == Act.NONE && t != CharacterType.MIKIKO)
                OffDuty.setForTest(p, t, Act.RELAX, n, null);
            OffDuty.startPartyForTest(p, CharacterType.LOGAN, p.level().getGameTime(), 600);
            Bonds.set(p, "kyrie", 55, 0);
            OffDuty.assign(p, CharacterType.KYRIE, "mining");
            PartyBots.openPick(p);
        }), 40));
        l.add(new Step("offduty_pick", () -> {
            if (mc.screen instanceof com.xenowarriors.client.bot.BotPickScreen s) {
                int busy = 0, blocked = 0;
                for (CharacterType t : CharacterType.PLAYABLE) if (s.busyBlocked(t)) blocked++;
                log("pick screen open, blocked cards " + blocked);
                check("pick screen shows activities", blocked > 0, "blocked " + blocked);
            } else check("pick screen shows activities", false, "screen " + mc.screen);
        }, 25));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.bot.BotPickScreen s) s.mouseScrolled(0, 0, -10); }, 15));
        l.add(new Step("offduty_pick_scrolled", () -> {}, 5));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.bot.BotPickScreen s) { s.mouseScrolled(0, 0, 10); s.openJobs(CharacterType.KYRIE, mc.getWindow().getGuiScaledWidth() / 2 - 40, 70); } }, 15));
        l.add(new Step("offduty_jobs", () -> {}, 5));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.bot.BotPickScreen s) s.assign(CharacterType.KYRIE, "wood"); }, 40));
        l.add(new Step(null, () -> server(p -> {
            check("job from the screen", OffDuty.act(p, CharacterType.KYRIE) == Act.WOOD, "kyrie '" + OffDuty.status(p, CharacterType.KYRIE) + "'");
            for (CharacterType t : CharacterType.PLAYABLE) { OffDuty.setForTest(p, t, Act.NONE, 0, null); Bonds.set(p, t.getId(), 0, 0); }
            p.setGameMode(GameType.CREATIVE);
        }), 10));
        l.add(new Step("offduty_end", () -> { mc.setScreen(null); log("summary pass=" + pass + " fail=" + fail + (FAILED.isEmpty() ? "" : " failed=" + FAILED)); }, 2));
    }
}

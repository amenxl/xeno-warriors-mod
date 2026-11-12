package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.DmzBotQuests;
import com.xenowarriors.compat.DmzBots;
import com.xenowarriors.compat.DmzItemMissions;
import com.xenowarriors.compat.DmzPerCharacter;
import com.xenowarriors.compat.DmzQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Part of BotBagTests with DragonMineZ (focus "dmzbotbag"): a mission that wants N of an item is met with the items
 * split between you and two bots (and not with one short); DragonMineZ never takes them, and neither do we. Then a
 * bot's own mission with an item objective counts its things, yours and the other bot's.
 */
final class DmzItemTests {
    private DmzItemTests() {}

    interface Check { void check(String what, boolean ok, String detail); }

    private static final String[] Q = new String[1];
    private static final int[] R = new int[4];      // required, total before, objective index, bot required
    private static Item item;

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static int count(Container c, Item it) { int n = 0; for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(it)) n += c.getItem(i).getCount(); return n; }

    private static void wipe(Container c, Item it) { for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(it)) c.setItem(i, ItemStack.EMPTY); }

    /** Into an extra tab of the bot (so the count must look past its bag). */
    private static void giveBot(PartyBotEntity b, int n) {
        if (b == null || n <= 0) return;
        int slot = com.xenowarriors.bot.BotInventory.tabStart(2) + 3;
        ItemStack s = b.inventory.getItem(slot);
        if (s.is(item)) s.grow(n); else b.inventory.setItem(slot, new ItemStack(item, n));
    }

    private static String where(ServerPlayer p) {
        PartyBotEntity k = bot(p, CharacterType.KAERU), y = bot(p, CharacterType.KYRIE);
        return "you=" + count(p.getInventory(), item) + " kaeru=" + (k == null ? -1 : count(k.inventory, item)) + " kyrie=" + (y == null ? -1 : count(y.inventory, item));
    }

    private static int total(ServerPlayer p) {
        int n = count(p.getInventory(), item);
        for (PartyBotEntity b : PartyBots.bots(p)) n += count(b.inventory, item);
        return n;
    }

    private static void reset(ServerPlayer p) {
        wipe(p.getInventory(), item);
        for (PartyBotEntity b : PartyBots.bots(p)) wipe(b.inventory, item);
    }

    private static final Pattern OBJ = Pattern.compile("status=(\\S+) obj\\d+=(\\d+)/(\\d+)");

    public static void build(Minecraft mc, List<Step> l, Check c, Consumer<String> log) {
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            Object d = DmzPerCharacter.live(p);
            Dmz6Tests.call(Dmz6Tests.call(d, "getCharacter"), "setRace", "saiyan");
            DmzPerCharacter.markCreatedForTest(p);
            for (PartyBotEntity b : new java.util.ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
            for (CharacterType t : new CharacterType[]{CharacterType.KAERU, CharacterType.KYRIE}) { p.getPersistentData().remove("xw_bot_rejoin_" + t.getId()); PartyBots.spawn(p, t, null); }
        }); }, 60));
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            List<String[]> qs = DmzItemMissions.itemQuests();
            String[] pick = null;
            for (String[] q : qs) {
                if (!q[1].equals("0")) continue;
                int n = Integer.parseInt(q[4]);
                if (pick == null || (q[3].equals("1") && !pick[3].equals("1")) || (q[3].equals(pick[3]) && n > Integer.parseInt(pick[4]))) pick = q;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, qs.size()); i++) sb.append(String.join("/", qs.get(i))).append(' ');
            log.accept("dmz item missions: " + qs.size() + " found, e.g. " + sb.toString().trim() + " | picked " + (pick == null ? "none" : String.join("/", pick)));
            if (pick == null) { c.check("dmz item mission", false, "no item mission in DragonMineZ"); return; }
            Q[0] = pick[0];
            item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(pick[2]));
            DmzQuests.accept(p, Q[0]);
            int[] o = DmzItemMissions.objective(p, Q[0], 0);
            R[0] = o[1];
            reset(p);
            int n = R[0] - 1, a = n / 3, b = n / 3, rest = n - a - b;          // one short, split three ways
            if (a > 0) p.getInventory().add(new ItemStack(item, a));
            giveBot(bot(p, CharacterType.KAERU), b);
            giveBot(bot(p, CharacterType.KYRIE), rest);
            log.accept("dmz item mission " + Q[0] + " wants " + R[0] + "x " + pick[2] + " (progress " + o[0] + ") - one short, split: " + where(p));
        }); }, 70));
        // the party's mission bag: a needed item picked up goes into it (not the inventory), counts, and comes out only
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            if (Q[0] == null) return;
            com.xenowarriors.compat.MissionBag.of(p).clearContent();
            // one out of your inventory first, so the one picked up into the bag keeps it one short (DragonMineZ never lowers progress)
            p.getInventory().clearOrCountMatchingItems(s -> s.is(item), 1, p.inventoryMenu.getCraftSlots());
            R[1] = count(p.getInventory(), item);
            boolean w = com.xenowarriors.compat.MissionBag.wanted(p, new ItemStack(item));
            log.accept("mission bag: wanted=" + w);
            var e = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), new ItemStack(item, 1));
            e.setNoPickUpDelay();
            p.level().addFreshEntity(e);
        }); }, 30));
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            if (Q[0] == null) return;
            var bag = com.xenowarriors.compat.MissionBag.of(p);
            int inBag = count(bag, item), inv = count(p.getInventory(), item);
            int counted = DmzItemMissions.botItems(p, net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString());
            c.check("mission bag took the picked-up mission item", inBag == 1 && inv == R[1], "bag " + inBag + " inventory " + R[1] + " -> " + inv);
            c.check("mission bag counts for the mission", counted >= 1, "counted " + counted);
            com.xenowarriors.compat.MissionBag.open(p);
            if (p.containerMenu instanceof com.xenowarriors.compat.MissionBagMenu m) {
                boolean noPlace = !m.slots.get(0).mayPlace(new ItemStack(item));
                p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.DIRT, 5));
                int dirtSlot = -1;
                for (int i = com.xenowarriors.compat.MissionBagMenu.ROWS * 9; i < m.slots.size(); i++) if (m.slots.get(i).getItem().is(net.minecraft.world.item.Items.DIRT)) dirtSlot = i;
                ItemStack moved = dirtSlot < 0 ? ItemStack.EMPTY : m.quickMoveStack(p, dirtSlot);
                int bagSlot = -1;
                for (int i = 0; i < com.xenowarriors.compat.MissionBagMenu.ROWS * 9; i++) if (m.slots.get(i).getItem().is(item)) bagSlot = i;
                ItemStack out = bagSlot < 0 ? ItemStack.EMPTY : m.quickMoveStack(p, bagSlot);
                c.check("mission bag is take-only", noPlace && moved.isEmpty() && count(bag, net.minecraft.world.item.Items.DIRT) == 0 && !out.isEmpty()
                        && count(bag, item) == 0 && count(p.getInventory(), item) == R[1] + 1,
                        "noPlace=" + noPlace + " shiftIn=" + moved + " takeOut=" + out + " bag now " + count(bag, item) + " inv " + count(p.getInventory(), item));
                p.closeContainer();
                p.getInventory().clearOrCountMatchingItems(s -> s.is(net.minecraft.world.item.Items.DIRT), 64, p.inventoryMenu.getCraftSlots());
            } else c.check("mission bag opens", false, "menu " + p.containerMenu);
            bag.clearContent();                                                           // (the one taken out keeps it one short)
        }); }, 70));
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            if (Q[0] == null) return;
            int[] o = DmzItemMissions.objective(p, Q[0], 0);
            c.check("dmz item mission one short", o[0] == R[0] - 1 && count(p.getInventory(), item) < R[0] - 1 || R[0] <= 1 && o[0] == 0,
                    "progress " + o[0] + "/" + o[1] + " with " + where(p) + " (you alone have " + count(p.getInventory(), item) + ") " + DmzQuests.progress(p, Q[0], 0));
            giveBot(bot(p, CharacterType.KYRIE), 1);
            R[1] = total(p);
        }); }, 70));
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            if (Q[0] == null) return;
            int[] o = DmzItemMissions.objective(p, Q[0], 0);
            String st = DmzQuests.progress(p, Q[0], 0);
            boolean done = o[0] >= R[0] || st.contains("completed=true");
            c.check("dmz item mission met with items split between you and two bots", done && total(p) == R[1] && DmzItemMissions.raised > 0,
                    "progress " + o[0] + "/" + R[0] + " " + st + " | " + where(p) + " total " + R[1] + " -> " + total(p) + " (removed " + (R[1] - total(p)) + ": DragonMineZ only counts them) raised=" + DmzItemMissions.raised);
        }); }, 5));

        // a bot's own mission with an item objective: its things + yours + the other bot's
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k == null || Q[0] == null) { c.check("bot item mission", false, "no bot/mission"); return; }
            log.accept("bot race " + DmzBots.ensureRace(k));
            DmzBotQuests.resetForTest(k);
            boolean started = DmzBotQuests.startForTest(k, Q[0]);
            String st = DmzBotQuests.status(k, Q[0], 0);
            Matcher m = OBJ.matcher(st);
            R[3] = m.find() ? Integer.parseInt(m.group(3)) : -1;
            reset(p);
            int n = R[3] - 1, a = n / 3, b = n / 3, rest = n - a - b;
            giveBot(k, a);
            if (b > 0) p.getInventory().add(new ItemStack(item, b));
            giveBot(bot(p, CharacterType.KYRIE), rest);
            log.accept("bot mission start=" + started + " " + st + " - one short, split: " + where(p));
        }); }, 70));
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k == null || Q[0] == null) return;
            String st = DmzBotQuests.status(k, Q[0], 0);
            Matcher m = OBJ.matcher(st);
            int prog = m.find() ? Integer.parseInt(m.group(2)) : -1;
            c.check("bot item mission one short", prog == R[3] - 1, st + " | " + where(p));
            R[2] = DmzBotQuests.completions;
            giveBot(bot(p, CharacterType.KYRIE), 1);
            R[1] = total(p);
        }); }, 70));
        l.add(new Step(null, () -> { Dmz6Tests.noDmzScreen(mc); UiTest.server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k == null || Q[0] == null) return;
            String st = DmzBotQuests.status(k, Q[0], 0);
            Matcher m = OBJ.matcher(st);
            boolean found = m.find();
            int prog = found ? Integer.parseInt(m.group(2)) : -1;
            boolean done = prog >= R[3] || DmzBotQuests.completions > R[2];
            c.check("bot's own item mission met (its things + yours + the other bot's)", done, st + " completions " + R[2] + " -> " + DmzBotQuests.completions
                    + " | " + where(p) + " total " + R[1] + " -> " + total(p) + " (a reward of the same item may add some; none taken)");
        }); }, 5));
    }
}

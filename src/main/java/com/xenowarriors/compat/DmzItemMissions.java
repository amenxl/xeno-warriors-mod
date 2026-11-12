package com.xenowarriors.compat;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * DragonMineZ missions that want you to have items count your bot teammates' things too (their bag and all three
 * extra tabs). DragonMineZ 2.1.x counts an item objective as "how many of that item you have" every second
 * (QuestEvents.countItems - it never takes them from you); mixin/compat/DragonMineZQuestItemsMixin adds your bots'
 * count to that. A bot's own missions (DmzBotQuests) count its things, yours and your other bots'.
 */
public final class DmzItemMissions {
    private DmzItemMissions() {}

    /** (For the tests) how often DragonMineZ's count was raised by bots' things, and the last raise. */
    public static int raised, lastExtra;

    private static Item item(String id) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == Items.AIR ? null : it;
    }

    private static int count(Container c, Item it) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(it)) n += c.getItem(i).getCount();
        return n;
    }

    /** From the mixin: how many of this item the player's bot teammates carry (added to what the player has). */
    public static int botItems(ServerPlayer p, String itemId) {
        try {
            if (p == null || p instanceof FakePlayer) return 0;
            Item it = item(itemId);
            if (it == null) return 0;
            int n = count(MissionBag.of(p), it);                                        // the party's mission bag
            for (PartyBotEntity b : PartyBots.bots(p)) if (b.isAlive()) n += count(b.inventory, it);
            if (n > 0) { raised++; lastExtra = n; }
            return n;
        } catch (Throwable t) { return 0; }                                          // (never breaks DragonMineZ's mission tick)
    }

    /** A bot's own mission: its things, its owner's and the owner's other bots'. */
    public static int forBot(PartyBotEntity b, String itemId) {
        Item it = item(itemId);
        if (it == null) return 0;
        int n = count(b.inventory, it);
        ServerPlayer o = b.owner();
        if (o != null) {
            n += count(o.getInventory(), it) + count(MissionBag.of(o), it);
            for (PartyBotEntity other : PartyBots.bots(o)) if (other != b && other.isAlive()) n += count(other.inventory, it);
        }
        return n;
    }

    // ------------------------------------------------------------------ for the tests (reflection; DragonMineZ only)

    private static Object call(Object o, String name, Object... args) throws Exception {
        for (var mm : o.getClass().getMethods()) if (mm.getName().equals(name) && mm.getParameterCount() == args.length) {
            try { return mm.invoke(o, args); } catch (IllegalArgumentException wrong) { /* next */ }
        }
        throw new NoSuchMethodException(name);
    }

    /** The player's DragonMineZ mission data (null without it). */
    public static Object questData(ServerPlayer p) throws Exception { return pqd(p); }

    private static Object pqd(ServerPlayer p) throws Exception {
        var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
        Object d = p.getCapability(cap).resolve().orElse(null);
        return d == null ? null : call(d, "getPlayerQuestData");
    }

    private static Object quest(String key) throws Exception {
        return Class.forName("com.dragonminez.common.quest.QuestRegistry").getMethod("getQuest", String.class).invoke(null, key);
    }

    /** UI test: missions with an item objective: {quest, objective index, item id, objectives in the quest, base count}. */
    public static java.util.List<String[]> itemQuests() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        try {
            Class<?> type = Class.forName("com.dragonminez.common.quest.QuestObjective$ObjectiveType");
            Object item = null;
            for (Object c : type.getEnumConstants()) if (c.toString().equals("ITEM")) item = c;
            @SuppressWarnings("unchecked")
            var ids = (java.util.List<String>) Class.forName("com.dragonminez.common.quest.QuestRegistry").getMethod("getQuestIdsByObjectiveType", type).invoke(null, item);
            for (String id : ids) {
                Object q = quest(id);
                if (q == null) continue;
                var objs = (java.util.List<?>) call(q, "getObjectives");
                for (int i = 0; i < objs.size(); i++) if (objs.get(i).getClass().getSimpleName().equals("ItemObjective"))
                    out.add(new String[]{id, String.valueOf(i), String.valueOf(call(objs.get(i), "getItemId")), String.valueOf(objs.size()), String.valueOf(call(objs.get(i), "getCount"))});
            }
        } catch (Throwable t) { com.xenowarriors.XenoWarriors.LOGGER.warn("XW-TEST item quests lookup failed", t); }
        return out;
    }

    /** UI test: progress and required count of a player's mission objective ({-1,-1} on failure). */
    public static int[] objective(ServerPlayer p, String key, int i) {
        try {
            Object d = pqd(p), q = quest(key);
            return new int[]{(Integer) call(d, "getObjectiveProgress", key, i), (Integer) call(q, "getObjectiveRequired", d, key, i)};
        } catch (Throwable t) { return new int[]{-1, -1}; }
    }
}

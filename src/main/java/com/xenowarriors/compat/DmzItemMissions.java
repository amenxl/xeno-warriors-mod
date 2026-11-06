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
            int n = 0;
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
            n += count(o.getInventory(), it);
            for (PartyBotEntity other : PartyBots.bots(o)) if (other != b && other.isAlive()) n += count(other.inventory, it);
        }
        return n;
    }
}

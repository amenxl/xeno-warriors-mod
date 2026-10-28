package com.xenowarriors.tower;

import com.xenowarriors.collection.Catalog;
import com.xenowarriors.collection.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Tower rewards: beating a boss floor pays Money (more the higher you are, plus a bonus for every enemy you've
 * completed in your Collection) and a roll on the boss loot table (more rolls higher up). Horde floors pay a bonus.
 */
public final class TowerRewards {
    private static final ResourceLocation BOSS_LOOT = com.xenowarriors.XenoWarriors.id("chests/tower_boss");

    private TowerRewards() {}

    /** How many Collection entries this player has finished. */
    public static int completed(ServerPlayer p) {
        int n = 0;
        for (String id : Catalog.all(p.serverLevel())) if (Collection.count(p, id) >= Collection.MAX) n++;
        return n;
    }

    public static void boss(ServerPlayer p, int floor) {
        int done = completed(p);
        long money = 300L * (floor / 10) + 25L * done;
        com.xenowarriors.economy.Money.add(p, money, true);
        var level = p.serverLevel();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, p.position()).withParameter(LootContextParams.THIS_ENTITY, p)
                .withLuck(p.getLuck()).create(LootContextParamSets.CHEST);
        int rolls = 1 + floor / 20;
        int items = 0;
        for (int i = 0; i < rolls; i++)
            for (ItemStack s : level.getServer().getLootData().getLootTable(BOSS_LOOT).getRandomItems(params)) { items++; if (!p.getInventory().add(s)) p.drop(s, false); }
        p.sendSystemMessage(Component.literal("Boss floor " + floor + " cleared! +" + com.xenowarriors.economy.Money.fmt(money) + " Money"
                + (done > 0 ? " (incl. " + (25 * done) + " for " + done + " completed Collection entries)" : "") + ", " + items + " items").withStyle(ChatFormatting.GOLD));
        com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), com.xenowarriors.character.CharacterType.NONE.ordinal(), -1,
                "Boss floor " + floor + " cleared", "+" + com.xenowarriors.economy.Money.fmt(money) + " Money and loot", "");
    }

    public static void horde(ServerPlayer p, int floor) {
        long money = 40L + 8L * floor;
        com.xenowarriors.economy.Money.add(p, money, true);
        p.displayClientMessage(Component.literal("Horde bonus: +" + com.xenowarriors.economy.Money.fmt(money) + " Money").withStyle(ChatFormatting.GOLD), true);
    }
}

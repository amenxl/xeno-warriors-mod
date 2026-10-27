package com.xenowarriors.economy;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.item.ModItems;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Every villager (and the wandering trader) swaps Money notes and emeralds: 2 notes for an emerald, an emerald for 1 note. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillagerMoneyTrades {
    private VillagerMoneyTrades() {}

    private static final VillagerTrades.ItemListing MONEY_FOR_EMERALD = (trader, rand) ->
            new MerchantOffer(new ItemStack(ModItems.MONEY_NOTE.get(), 2), new ItemStack(Items.EMERALD), 32, 1, 0.0F);
    private static final VillagerTrades.ItemListing EMERALD_FOR_MONEY = (trader, rand) ->
            new MerchantOffer(new ItemStack(Items.EMERALD), new ItemStack(ModItems.MONEY_NOTE.get()), 32, 1, 0.0F);

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent e) {
        var level1 = e.getTrades().get(1);
        level1.add(MONEY_FOR_EMERALD);
        level1.add(EMERALD_FOR_MONEY);
    }

    @SubscribeEvent
    public static void onWanderer(WandererTradesEvent e) {
        e.getGenericTrades().add(MONEY_FOR_EMERALD);
        e.getGenericTrades().add(EMERALD_FOR_MONEY);
    }
}

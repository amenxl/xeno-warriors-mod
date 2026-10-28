package com.xenowarriors.journey;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** What an item panel gives: some ore, often a weapon (enchanted on harder journeys), and with DragonMineZ often a piece of its armor. */
public final class JourneyLoot {
    private JourneyLoot() {}

    private record Ore(Item item, int min, int max, int fromDiff) {}

    private static final Ore[] ORES = {
            new Ore(Items.COAL, 8, 16, 0), new Ore(Items.RAW_COPPER, 6, 12, 0), new Ore(Items.IRON_INGOT, 4, 8, 0), new Ore(Items.GOLD_INGOT, 3, 6, 0),
            new Ore(Items.REDSTONE, 8, 16, 0), new Ore(Items.LAPIS_LAZULI, 6, 12, 0), new Ore(Items.EMERALD, 2, 5, 1), new Ore(Items.DIAMOND, 1, 3, 1),
            new Ore(Items.ANCIENT_DEBRIS, 1, 2, 3), new Ore(Items.NETHERITE_SCRAP, 1, 2, 3)};

    private static List<Item> dmzArmor;

    public static List<ItemStack> roll(ServerLevel l, int diff, int stage) {
        var rnd = l.getRandom();
        List<ItemStack> out = new ArrayList<>();
        // ore: one or two stacks, better ones on harder journeys
        List<Ore> ok = new ArrayList<>();
        for (Ore o : ORES) if (diff >= o.fromDiff) ok.add(o);
        int stacks = 1 + (rnd.nextInt(3) < diff ? 1 : 0);
        for (int i = 0; i < stacks; i++) {
            Ore o = ok.get(rnd.nextInt(ok.size()));
            int n = o.min + rnd.nextInt(o.max - o.min + 1) + stage;
            out.add(new ItemStack(o.item, Math.min(64, n)));
        }
        // a weapon, half the time
        if (rnd.nextBoolean()) {
            Item[] weapons = diff >= 3 ? new Item[]{Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.NETHERITE_SWORD, Items.CROSSBOW, Items.BOW, Items.TRIDENT}
                    : diff >= 1 ? new Item[]{Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.BOW, Items.CROSSBOW}
                    : new Item[]{Items.STONE_SWORD, Items.IRON_SWORD, Items.IRON_AXE, Items.BOW};
            ItemStack w = new ItemStack(weapons[rnd.nextInt(weapons.length)]);
            if (diff >= 2 || rnd.nextInt(4) == 0) w = EnchantmentHelper.enchantItem(rnd, w, 8 + diff * 6 + stage * 2, false);
            out.add(w);
            if (w.is(Items.BOW) || w.is(Items.CROSSBOW)) out.add(new ItemStack(Items.ARROW, 16 + diff * 8));
        }
        // DragonMineZ armor, half the time when it's installed
        List<Item> armor = dmzArmor();
        if (!armor.isEmpty() && rnd.nextBoolean()) out.add(new ItemStack(armor.get(rnd.nextInt(armor.size()))));
        return out;
    }

    private static List<Item> dmzArmor() {
        if (dmzArmor != null) return dmzArmor;
        List<Item> l = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS.getValues()) {
            var id = ForgeRegistries.ITEMS.getKey(it);
            if (id != null && id.getNamespace().equals("dragonminez") && it instanceof ArmorItem) l.add(it);
        }
        dmzArmor = List.copyOf(l);
        return dmzArmor;
    }
}

package com.xenowarriors.vehicle;

import com.xenowarriors.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** The vehicle items (one per kind; the colour is in the item's data) and the dealer block's item. */
public final class VehicleItems {
    public static final Map<VehicleKind, RegistryObject<Item>> ITEMS_BY_KIND = new EnumMap<>(VehicleKind.class);

    static {
        for (VehicleKind k : VehicleKind.values())
            ITEMS_BY_KIND.put(k, ModItems.ITEMS.register(k.id, () -> new VehicleItem(k, new Item.Properties().stacksTo(1))));
    }

    public static final RegistryObject<Item> DEALER = ModItems.ITEMS.register("vehicle_dealer",
            () -> new net.minecraft.world.item.BlockItem(VehicleBlocks.DEALER.get(), new Item.Properties()));

    private VehicleItems() {}

    public static void init() {}
}

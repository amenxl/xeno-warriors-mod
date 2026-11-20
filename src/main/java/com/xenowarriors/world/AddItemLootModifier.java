package com.xenowarriors.world;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/** Sometimes adds one item to a chest's loot ("item", "chance") - e.g. a Boost Bag in the world's bonus chest. */
public class AddItemLootModifier extends LootModifier {
    public static final Supplier<Codec<AddItemLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst)
                    .and(ResourceLocation.CODEC.fieldOf("item").forGetter(m -> m.item))
                    .and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance))
                    .apply(inst, AddItemLootModifier::new)));

    private final ResourceLocation item;
    private final float chance;

    public AddItemLootModifier(LootItemCondition[] conditions, ResourceLocation item, float chance) {
        super(conditions);
        this.item = item;
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getRandom().nextFloat() >= chance) return loot;
        Item i = ForgeRegistries.ITEMS.getValue(item);
        if (i != null && i != net.minecraft.world.item.Items.AIR) loot.add(new ItemStack(i));
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() { return CODEC.get(); }
}

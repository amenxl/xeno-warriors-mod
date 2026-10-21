package com.xenowarriors.world;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.item.ModItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

/** Sometimes slips one of the Xeno Warriors music discs into a chest's loot (village houses). */
public class AddDiscLootModifier extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, XenoWarriors.MOD_ID);

    public static final Supplier<Codec<AddDiscLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst)
                    .and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance))
                    .apply(inst, AddDiscLootModifier::new)));

    public static final RegistryObject<Codec<AddDiscLootModifier>> ADD_DISC = SERIALIZERS.register("add_disc", CODEC);

    private final float chance;

    public AddDiscLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getRandom().nextFloat() >= chance) return loot;
        List<Item> discs = ModItems.ITEMS.getEntries().stream().map(RegistryObject::get).filter(i -> i instanceof RecordItem).toList();
        if (!discs.isEmpty()) loot.add(new ItemStack(discs.get(context.getRandom().nextInt(discs.size()))));
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}

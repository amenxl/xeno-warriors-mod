package com.xenowarriors.world;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Takes a later season's items back out of a loot roll while that season is still locked in the world (e.g. Aurora
 * Medicine, Season 2, in Government facility chests). The loot table stays as it is; once /xeno season unlock runs,
 * the items come through.
 */
public class SeasonLootModifier extends LootModifier {
    public static final Supplier<Codec<SeasonLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst)
                    .and(Codec.INT.fieldOf("season").forGetter(m -> m.season))
                    .and(ResourceLocation.CODEC.listOf().fieldOf("items").forGetter(m -> m.items))
                    .apply(inst, SeasonLootModifier::new)));

    private final int season;
    private final List<ResourceLocation> items;

    public SeasonLootModifier(LootItemCondition[] conditions, int season, List<ResourceLocation> items) {
        super(conditions);
        this.season = season;
        this.items = items;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (com.xenowarriors.character.Seasons.unlocked(context.getLevel().getServer(), season)) return loot;
        loot.removeIf(s -> items.contains(ForgeRegistries.ITEMS.getKey(s.getItem())));
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}

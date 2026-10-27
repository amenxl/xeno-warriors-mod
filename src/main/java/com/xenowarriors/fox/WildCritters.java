package com.xenowarriors.fox;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wild foxes and Rhalix's winged critters come in kinds: foxes born ember, midnight, sakura (cherry groves), desert
 * (dry lands), and the rare kitsune and golden; on DragonMineZ's worlds, Namekian, Sacred Kai and Otherworld spirit
 * foxes. Bats, parrots, chickens and bees get shadow, rose, frost, ember, golden or void looks - and Namekian and
 * Sacred Kai ones on those worlds. (More of all of them spawn too - see the biome modifiers.)
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WildCritters {
    public static final String FOX_KIND = "xw_fox_kind", ROLLED = "xw_wild_rolled";
    private static final TagKey<Biome> NAMEK = tag("dragonminez", "is_namekworld"), KAI = tag("dragonminez", "is_sacredkai"),
            OTHER = tag("dragonminez", "is_otherworld"), DMZ_DESERT = tag("dragonminez", "is_desertlike");

    private WildCritters() {}

    private static TagKey<Biome> tag(String ns, String path) {
        return TagKey.create(net.minecraft.core.registries.Registries.BIOME, new ResourceLocation(ns, path));
    }

    private static int skin(String id) {
        for (int i = 0; i < FoxSkins.ALL.length; i++) if (FoxSkins.ALL[i].id().equals(id)) return i;
        return 0;
    }

    /** Bats take after where they live (most of the time). */
    private static int batLook(Holder<Biome> b, RandomSource r) {
        if (b.is(NAMEK)) return 7;
        if (b.is(KAI)) return 8;
        if (b.is(OTHER)) return 17;
        if (r.nextFloat() < 0.02F) return 6;                                                   // rare Void anywhere
        int local = 0;
        if (b.is(BiomeTags.IS_NETHER)) local = 15;
        else if (b.is(BiomeTags.IS_END)) local = 16;
        else if (b.is(Biomes.LUSH_CAVES)) local = 18;
        else if (b.is(Biomes.DEEP_DARK) || b.is(Biomes.DARK_FOREST)) local = 1;
        else if (b.is(Biomes.MUSHROOM_FIELDS)) local = 13;
        else if (b.is(Biomes.CHERRY_GROVE) || b.is(Biomes.FLOWER_FOREST)) local = 2;
        else if (b.is(BiomeTags.IS_BADLANDS) || b.is(Biomes.DESERT) || b.is(BiomeTags.IS_SAVANNA) || b.is(DMZ_DESERT)) local = 9;
        else if (b.is(BiomeTags.IS_JUNGLE)) local = 10;
        else if (b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP)) local = 11;
        else if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_RIVER)) local = 12;
        else if (b.is(Biomes.SNOWY_PLAINS) || b.is(Biomes.ICE_SPIKES) || b.is(Biomes.SNOWY_TAIGA) || b.is(Biomes.FROZEN_PEAKS) || b.is(Biomes.SNOWY_SLOPES) || b.is(Biomes.GROVE)) local = 3;
        else if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(Biomes.WINDSWEPT_HILLS) || b.is(Biomes.STONY_PEAKS)) local = 14;
        if (local != 0 && r.nextFloat() < 0.75F) return local;
        float f = r.nextFloat();
        return f < 0.15F ? 1 + r.nextInt(5) : 0;                                               // Shadow, Rose, Frost, Ember, Golden
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity e = event.getEntity();
        boolean fox = e instanceof Fox, wing = e instanceof Bat || e instanceof Parrot || e instanceof Chicken || e instanceof Bee;
        if (!fox && !wing) return;
        var d = e.getPersistentData();
        if (d.getBoolean(ROLLED)) return;
        d.putBoolean(ROLLED, true);
        if (fox && FoxPets.ownerOf((Fox) e) != null) return;
        if (wing && com.xenowarriors.wing.WingPets.ownerOf(e) != null) return;
        Holder<Biome> b = event.getLevel().getBiome(e.blockPosition());
        RandomSource r = e.level().random;
        if (fox) {
            String kind = null;
            if (b.is(NAMEK)) kind = "namek";
            else if (b.is(KAI)) kind = "kai";
            else if (b.is(OTHER)) kind = "spirit";
            else if (b.is(BiomeTags.IS_BADLANDS) || b.is(Biomes.DESERT) || b.is(BiomeTags.IS_SAVANNA) || b.is(DMZ_DESERT)) kind = "desert";
            else if (b.is(Biomes.CHERRY_GROVE) && r.nextFloat() < 0.5F) kind = "sakura";
            else {
                float f = r.nextFloat();
                if (f < 0.01F) kind = "golden";
                else if (f < 0.03F) kind = "kitsune";
                else if (f < 0.04F && ((Fox) e).getVariant() == Fox.Type.SNOW) kind = "celestial";
                else if (f < 0.11F) kind = "midnight";
                else if (f < 0.24F) kind = "ember";
            }
            if (kind != null) d.putInt(FOX_KIND, skin(kind));
        } else if (e instanceof Bat) {
            int look = batLook(b, r);
            if (look != 0) d.putInt(com.xenowarriors.wing.WingPets.SKIN_KEY, look);
        } else {
            int look = 0;
            if (b.is(NAMEK)) look = 7;
            else if (b.is(KAI)) look = 8;
            else if (b.is(OTHER)) look = 6;
            else {
                float f = r.nextFloat();
                if (f < 0.02F) look = 6;                       // Void
                else if (f < 0.22F) look = 1 + r.nextInt(5);   // Shadow, Rose, Frost, Ember, Golden
            }
            if (look != 0) d.putInt(com.xenowarriors.wing.WingPets.SKIN_KEY, look);
        }
    }
}

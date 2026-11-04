package com.xenowarriors.collection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Every hostile enemy the Collection tracks: vanilla and modded monsters, DragonMineZ enemies, and the show's enemies
 * (Gov agents, Order assassins, Distortions...). Worked out once per server.
 */
public final class Catalog {
    private static final Set<String> EXTRA = Set.of("xenowarriors:gov_agent", "xenowarriors:order_assassin", "xenowarriors:distortion");
    /** Hostile, but not something Tower floors should throw at you. */
    private static final Set<String> NOT_IN_TOWER = Set.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:elder_guardian", "minecraft:guardian",
            "minecraft:giant", "minecraft:shulker", "minecraft:ghast", "minecraft:warden", "minecraft:phantom", "xenowarriors:distortion", "xenowarriors:c_soul");
    private static List<String> all;

    private Catalog() {}

    public static boolean hostile(Entity e) {
        if (e instanceof net.minecraft.world.entity.player.Player) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        if (id == null) return false;
        return e instanceof Enemy || e.getType().getCategory() == MobCategory.MONSTER || EXTRA.contains(id.toString());
    }

    /** All hostile enemy ids, sorted by mod then name. */
    public static List<String> all(ServerLevel level) {
        if (all != null) return all;
        List<String> out = new ArrayList<>();
        for (EntityType<?> t : ForgeRegistries.ENTITY_TYPES.getValues()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(t);
            if (id == null) continue;
            String s = id.toString();
            if (t.getCategory() == MobCategory.MONSTER || EXTRA.contains(s)) { out.add(s); continue; }
            if (id.getNamespace().equals("dragonminez") && t.getCategory() != MobCategory.MISC) {
                try {                                                     // DragonMineZ: check each one is really an enemy
                    Entity e = t.create(level);
                    if (e instanceof LivingEntity && e instanceof Enemy) out.add(s);
                    else if (e instanceof net.minecraft.world.entity.monster.Monster) out.add(s);
                    if (e != null) e.discard();
                } catch (Throwable ignored) {}
            }
        }
        out.sort(Comparator.comparing((String s) -> s.startsWith("minecraft:") ? 0 : s.startsWith("xenowarriors:") ? 1 : 2).thenComparing(s -> s));
        all = List.copyOf(out);
        return all;
    }

    /** A DragonMineZ saga character (a story fighter, not a henchman like a Saibaman or a Frieza soldier). */
    public static boolean sagaCharacter(String id) {
        if (!id.startsWith("dragonminez:saga_")) return false;
        return !(id.contains("saibaman") || id.contains("soldier") || id.contains("cell_jr"));
    }

    /** Season 3 enemies (the Order of Assassins, Distortions): kept out of every pool and list until Season 3 is unlocked. */
    private static final Set<String> SEASON_3 = Set.of("xenowarriors:order_assassin", "xenowarriors:distortion");

    /** Story content of a season that isn't unlocked yet in this world. */
    public static boolean locked(String id, net.minecraft.server.MinecraftServer server) {
        return SEASON_3.contains(id) && !com.xenowarriors.character.Seasons.unlocked(server, 3);
    }

    /** {@link #all}, minus what's still locked behind a season. */
    public static List<String> available(ServerLevel level) {
        List<String> out = new ArrayList<>();
        for (String id : all(level)) if (!locked(id, level.getServer())) out.add(id);
        return out;
    }

    public static boolean inTower(String id) {
        if (NOT_IN_TOWER.contains(id)) return false;
        if (sagaCharacter(id)) return false;          // (Broly, Vegetto... on floor 1 was impossible: they only come in Journey's Dragon Ball battles)
        return ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id)) != null;
    }
}

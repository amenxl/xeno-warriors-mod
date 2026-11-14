package com.xenowarriors.foes;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Epic Fight moves for our foes and fighters (when Epic Fight is installed): each swing plays the next hit of their
 * weapon's combo (the Cynic Dancer: Weapons of Harmony's Tsume; the Ancient Swordstress: the Aatrox greatsword's
 * Solar style; townsfolk / corrupted souls with a Simply Swords weapon: that weapon's own combo), and a blocked hit
 * its guard. Their own AI and damage stay ours: the animation is how it looks, so its hits don't count twice.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FoeAnims {
    private FoeAnims() {}

    private static final String C = "woh:biped/combat/tsume/";
    public static final String[] TSUME = {C + "tsume_auto_1", C + "tsume_auto_2", C + "tsume_auto_3", C + "tsume_auto_4"};
    public static final String[] SOLAR = {"wom:biped/combat/solar_auto_1", "wom:biped/combat/solar_auto_2", "wom:biped/combat/solar_auto_3", "wom:biped/combat/solar_auto_4"};
    public static final String SOLAR_GUARD = "wom:biped/skill/solar_guard_hit";

    private static Boolean ef;
    public static boolean ef() {
        if (ef == null) ef = ModList.get() != null && ModList.get().isLoaded("epicfight");
        return ef;
    }

    /** (Tests) swings and guards played. */
    public static int swings, guards;

    /** A swing: the next hit of these moves (or of whatever its weapon's Epic Fight combo is, when keys is null). */
    public static void swing(LivingEntity e, String[] keys) {
        if (!ef() || e.level().isClientSide) return;
        try { if (com.xenowarriors.compat.epicfight.EfFoes.swing(e, keys)) swings++; } catch (Throwable ignored) {}
    }

    /** A guard (it blocked a hit). */
    public static void guard(LivingEntity e, String key) {
        if (!ef() || e.level().isClientSide) return;
        try { if (com.xenowarriors.compat.epicfight.EfFoes.play(e, key)) guards++; } catch (Throwable ignored) {}
    }

    /** Fighters whose Epic Fight swings are only for show (their own attack already did the damage). */
    public static boolean visualOnly(LivingEntity e) {
        return e instanceof VesselEntity || e instanceof SwordstressEntity;
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!ef()) return;
        var src = event.getSource();
        if (src.getEntity() instanceof LivingEntity a && visualOnly(a) && src.getClass().getName().startsWith("yesman.epicfight"))
            event.setCanceled(true);
    }
}

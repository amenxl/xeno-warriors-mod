package com.xenowarriors.compat.epicfight;

import com.xenowarriors.npc.ModNpcs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Epic Fight for the townsfolk and the permanently possessed: they move and fight with Epic Fight's own humanoid
 * animations - its fist and weapon combos for whatever they're holding, hit reactions, knockdowns.
 */
public final class EfNpcPatches {
    private EfNpcPatches() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EfNpcPatches::onPatches);
    }

    private static void onPatches(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(ModNpcs.HUMAN.get(), e -> () -> new XWHumanPatch<>(Factions.VILLAGER));
        event.getTypeEntry().put(ModNpcs.DARK_POSSESSED.get(), e -> () -> new XWHumanPatch<>(Factions.UNDEAD));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void throwAnimation(LivingEntity e) {
        LivingEntityPatch patch = EpicFightCapabilities.getEntityPatch(e, LivingEntityPatch.class);
        if (patch != null) patch.playAnimationSynchronized(Animations.BIPED_MOB_THROW, 0F);
    }
}

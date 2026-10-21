package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Whatever a pet kills counts as its owner's kill: hits from Mikiko's foxes, Rhalix's winged companions or any tamed
 * animal mark the target as hurt by the owner, so the owner gets the XP orbs (and player-only drops) when it dies.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetKillCredit {
    private PetKillCredit() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled()) return;
        LivingEntity target = event.getEntity();
        if (target instanceof Player || target.level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker instanceof Player) return;
        UUID ownerId = Parties.petOwner(attacker);
        if (ownerId == null || ownerId.equals(target.getUUID())) return;
        if (target.level().getPlayerByUUID(ownerId) instanceof ServerPlayer owner && owner.isAlive()) {
            target.setLastHurtByPlayer(owner);
        }
    }
}

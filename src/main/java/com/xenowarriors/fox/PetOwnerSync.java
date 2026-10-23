package com.xenowarriors.fox;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PetOwnerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/** Tells clients who owns each pet, so lock-on never picks your own or your party's. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetOwnerSync {
    private PetOwnerSync() {}

    public static UUID ownerOf(Entity e) {
        if (e instanceof Fox f && FoxPets.ownerOf(f) != null) return FoxPets.ownerOf(f);
        UUID w = com.xenowarriors.wing.WingPets.ownerOf(e);
        if (w != null) return w;
        if (e instanceof OwnableEntity o) return o.getOwnerUUID();
        return null;
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        UUID o = ownerOf(event.getTarget());
        if (o != null && event.getEntity() instanceof ServerPlayer sp) ModNetwork.sendToPlayer(new PetOwnerPacket(event.getTarget().getId(), o), sp);
    }

    /** (newly tamed ones too: every few seconds to whoever can see them) */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide || e.tickCount % 100 != 7) return;
        UUID o = ownerOf(e);
        if (o != null) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> e), new PetOwnerPacket(e.getId(), o));
    }
}

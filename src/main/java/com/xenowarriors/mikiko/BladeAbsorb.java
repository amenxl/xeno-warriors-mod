package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.AbsorbPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Mikiko doesn't leave bodies behind: whatever he defeats is pulled into the Hekublade and sealed inside it.
 * The loot isn't lost though - the drops (and XP) land right at his feet so he picks them up.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeAbsorb {
    private BladeAbsorb() {}

    private static ServerPlayer absorber(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        if (victim instanceof Player || !(source.getEntity() instanceof ServerPlayer p)) return null;
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO || p.level() != victim.level()) return null;
        if (com.xenowarriors.fox.FoxPets.isOwnedBy(victim, p)) return null;
        return p;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        ServerPlayer p = absorber(victim, event.getSource());
        if (p == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> victim), new AbsorbPacket(victim.getId(), p.getId()));
        HekubladeRealm.seal(p, victim);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (event.isCanceled()) return;
        ServerPlayer p = absorber(event.getEntity(), event.getSource());
        if (p == null) return;
        for (var item : event.getDrops()) {
            item.setPos(p.getX(), p.getY() + 0.3, p.getZ());
            item.setDeltaMovement(0, 0.1, 0);
            item.setPickUpDelay(4);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXp(LivingExperienceDropEvent event) {
        // the orbs spawn where the mob was; give the XP to him straight away instead
        if (event.isCanceled() || !(event.getAttackingPlayer() instanceof ServerPlayer p)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || ModCapabilities.getType(p) != CharacterType.MIKIKO || p.level() != victim.level()) return;
        int xp = event.getDroppedExperience();
        if (xp <= 0) return;
        event.setCanceled(true);
        net.minecraft.world.entity.ExperienceOrb.award(p.serverLevel(), p.position(), xp);
    }
}

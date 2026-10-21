package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.TornPacket;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Explosions can rip Mikiko's and Rhalix's clothes (3 stages, each worse). Their clothes mend themselves
 * one stage at a time once they're back to full health and have stayed still for a while.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TornClothes {
    public static final int MAX = 3;
    private static final int MEND_AFTER = 200;   // 10 s standing still at full health per stage
    private static final Map<UUID, double[]> REST = new HashMap<>();   // {x, y, z, ticks still}

    private TornClothes() {}

    public static int get(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(c -> c.torn).orElse(0);
    }

    private static void set(ServerPlayer p, int torn) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(c -> c.torn = Math.max(0, Math.min(MAX, torn)));
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        ModNetwork.sendToTrackingAndSelf(new TornPacket(p.getUUID(), get(p)), p);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || event.getAmount() <= 0) return;
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION) || ModCapabilities.getType(p) == CharacterType.NONE) return;
        int now = get(p);
        if (now >= MAX) return;
        float chance = Math.min(0.9F, 0.35F + event.getAmount() * 0.04F);
        if (p.getRandom().nextFloat() > chance) return;
        set(p, now + (event.getAmount() >= 10F ? 2 : 1));
        REST.remove(p.getUUID());
        // shreds of cloth flying off
        ItemStack shred = new ItemStack(ModCapabilities.getType(p) == CharacterType.RHALIX ? Items.PURPLE_WOOL : Items.BLACK_WOOL);
        p.serverLevel().sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shred), p.getX(), p.getY() + 1.0, p.getZ(),
                18, 0.3, 0.45, 0.3, 0.12);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1F, 0.8F);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        if (get(p) <= 0) {
            REST.remove(p.getUUID());
            return;
        }
        double[] r = REST.computeIfAbsent(p.getUUID(), k -> new double[]{p.getX(), p.getY(), p.getZ(), 0});
        double dx = p.getX() - r[0], dy = p.getY() - r[1], dz = p.getZ() - r[2];
        boolean still = dx * dx + dy * dy + dz * dz < 0.2 * 0.2 && !p.swinging && p.hurtTime == 0;
        boolean healthy = p.getHealth() >= p.getMaxHealth();
        if (!still || !healthy) {
            r[0] = p.getX(); r[1] = p.getY(); r[2] = p.getZ(); r[3] = 0;
            return;
        }
        r[3] += 5;
        if (r[3] >= MEND_AFTER) {
            r[3] = 0;
            set(p, get(p) - 1);
            // threads weaving the cloth back together
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.35, 0.5, 0.35, 0.01);
            p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) set(p, 0);    // fresh clothes after respawning
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendToPlayer(new TornPacket(target.getUUID(), get(target)), viewer);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        REST.remove(event.getEntity().getUUID());
    }
}

package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Summoning and putting away Mikiko's Hekublade (default key G, changeable in Controls).
 * The blade only exists while summoned: it can't be dropped, stored, or kept by anyone but Mikiko -
 * it just vanishes back to him.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Hekublade {
    private Hekublade() {}

    /** The Hekublade only very rarely sets what it hits on fire. */
    public static final float FIRE_CHANCE = 0.04F;

    /** Rolls the (rare) chance to set the target alight; a burst of flame when it does. */
    public static boolean rollFire(net.minecraft.world.entity.LivingEntity target, int seconds) {
        if (target.getRandom().nextFloat() >= FIRE_CHANCE) return false;
        target.setSecondsOnFire(seconds);
        if (target.level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                    target.getZ(), 16, 0.3, 0.35, 0.3, 0.05);
            server.playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.3F);
        }
        return true;
    }

    public static boolean isBlade(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.HEKUBLADE.get());
    }

    public static void toggle(ServerPlayer player) {
        if (ModCapabilities.getType(player) != CharacterType.MIKIKO || player.isSpectator()) return;
        if (RopeBlade.blockSummon(player)) return;                        // thrown and still on its way back
        if (removeAll(player.getInventory()) > 0) {
            effects(player, false);
            return;
        }
        Inventory inv = player.getInventory();
        // use the selected slot if it's free, otherwise another free hotbar slot,
        // otherwise move the held item into the backpack to make room
        if (!inv.getSelected().isEmpty()) {
            int free = -1;
            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                if (inv.items.get(i).isEmpty()) { free = i; break; }
            }
            if (free >= 0) {
                inv.selected = free;
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(free));
            } else {
                int spare = -1;
                for (int i = Inventory.getSelectionSize(); i < inv.items.size(); i++) {
                    if (inv.items.get(i).isEmpty()) { spare = i; break; }
                }
                if (spare < 0) {
                    player.displayClientMessage(Component.translatable("message.xenowarriors.hekublade.full"), true);
                    return;
                }
                inv.items.set(spare, inv.getSelected().copy());
                inv.items.set(inv.selected, ItemStack.EMPTY);
            }
        }
        inv.items.set(inv.selected, new ItemStack(ModItems.HEKUBLADE.get()));
        inv.setChanged();
        effects(player, true);
    }

    private static void effects(ServerPlayer player, boolean summon) {
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getY() + 1.1, z = player.getZ();
        if (summon) {
            level.playSound(null, x, y, z, com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.HekubladeFxPacket(player.getId()), player);
        } else {
            level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Takes every Hekublade out of an inventory. Returns how many were removed. */
    public static int removeAll(Container container) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (isBlade(container.getItem(i))) {
                container.setItem(i, ItemStack.EMPTY);
                n++;
            }
        }
        return n;
    }

    /** Only Mikiko can hold it - anyone else loses it right away. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        if (ModCapabilities.getType(player) != CharacterType.MIKIKO) removeAll(player.getInventory());
        if (isBlade(player.containerMenu.getCarried()) && ModCapabilities.getType(player) != CharacterType.MIKIKO) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    /** Dropping it just makes it vanish. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (isBlade(event.getEntity().getItem()) && event.getPlayer() instanceof ServerPlayer player) {
            event.setCanceled(true);
            effects(player, false);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(item -> isBlade(item.getItem()));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && isBlade(item.getItem())) event.setCanceled(true);
    }

    /** Can't be stashed in chests or other containers. */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        for (Slot slot : event.getContainer().slots) {
            if (!(slot.container instanceof Inventory) && isBlade(slot.getItem())) slot.set(ItemStack.EMPTY);
        }
    }

    public static boolean holding(Player player) {
        return isBlade(player.getMainHandItem());
    }
}

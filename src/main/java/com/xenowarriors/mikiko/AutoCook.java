package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mikiko's fire element cooks the raw food he carries: every few seconds one raw item in his
 * inventory turns into its cooked version (anything a furnace would cook into food).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoCook {
    public static final int INTERVAL = 60;   // one item every 3 seconds

    private AutoCook() {}

    /** Merge partial stacks of this food together so it doesn't spread over many slots. */
    private static void stackUp(Inventory inv, ItemStack like) {
        for (int a = 0; a < inv.items.size(); a++) {
            ItemStack into = inv.items.get(a);
            if (into.isEmpty() || !ItemStack.isSameItemSameTags(into, like) || into.getCount() >= into.getMaxStackSize()) continue;
            for (int b = a + 1; b < inv.items.size() && into.getCount() < into.getMaxStackSize(); b++) {
                ItemStack from = inv.items.get(b);
                if (from.isEmpty() || !ItemStack.isSameItemSameTags(from, into)) continue;
                int move = Math.min(from.getCount(), into.getMaxStackSize() - into.getCount());
                into.grow(move);
                from.shrink(move);
                if (from.isEmpty()) inv.items.set(b, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % INTERVAL != 0) return;
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO || p.isSpectator()) return;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack raw = inv.items.get(i);
            if (raw.isEmpty()) continue;
            var recipe = p.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(raw), p.level());
            if (recipe.isEmpty()) continue;
            ItemStack cooked = recipe.get().getResultItem(p.level().registryAccess()).copy();
            if (cooked.isEmpty() || !cooked.isEdible()) continue;   // only food (not ores, sand...)
            // cooked food joins any stack of the same food he already has (or takes the raw item's slot)
            raw.shrink(1);
            if (raw.isEmpty()) inv.items.set(i, ItemStack.EMPTY);
            if (!inv.add(cooked)) p.drop(cooked, false);
            stackUp(inv, cooked);
            inv.setChanged();
            p.serverLevel().sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.25, 0.3, 0.25, 0.01);
            p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 0.5F, 1.2F);
            return;   // one item per interval
        }
    }
}

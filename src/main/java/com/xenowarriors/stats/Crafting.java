package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Crafting and Smelting skills (0-50 each, separate from Character Level).
 * Crafting: XP for everything you craft; each level adds 1% chance to get a second copy (50% at level 50).
 * (Recipes made from a single kind of item - storage blocks, ingots from blocks, planks... - never double, so
 * nothing can be looped into infinite items.)
 * Smelting: XP for every item you take out of a furnace, smoker or blast furnace; furnaces you used last cook 3%
 * faster per level (2.5x at level 50) - see FurnaceSpeedMixin.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Crafting {
    public static final int MAX_LEVEL = Stat.MAX_LEVEL;
    public static final float DOUBLE_PER_LEVEL = 0.01F, SMELT_SPEED_PER_LEVEL = 0.03F;
    public static final String FURNACE_OWNER = "xw_smelter";

    private Crafting() {}

    /** 1% per level to 50, tapering after; never more than 100%. */
    public static float doubleChance(int level) {
        return Math.min(1F, Stat.effective(level) * DOUBLE_PER_LEVEL);
    }

    public static float needed(int level) {
        return 30F + level * 15F;
    }

    private static PlayerCharacter cap(ServerPlayer p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
    }

    public static int craftLevel(ServerPlayer p) {
        PlayerCharacter d = cap(p);
        return d == null ? 0 : d.craftLevel;
    }

    public static int smeltLevel(ServerPlayer p) {
        PlayerCharacter d = cap(p);
        return d == null ? 0 : d.smeltLevel;
    }

    public static float craftProgress(ServerPlayer p) {
        PlayerCharacter d = cap(p);
        return d == null ? 0F : d.craftLevel >= MAX_LEVEL ? 1F : Math.min(1F, d.craftXp / needed(d.craftLevel));
    }

    public static float smeltProgress(ServerPlayer p) {
        PlayerCharacter d = cap(p);
        return d == null ? 0F : d.smeltLevel >= MAX_LEVEL ? 1F : Math.min(1F, d.smeltXp / needed(d.smeltLevel));
    }

    private static void addCraftXp(ServerPlayer p, float xp) {
        PlayerCharacter d = cap(p);
        if (d == null || d.craftLevel >= MAX_LEVEL) return;
        d.craftXp += xp;
        boolean up = false;
        while (d.craftLevel < MAX_LEVEL && d.craftXp >= needed(d.craftLevel)) {
            d.craftXp -= needed(d.craftLevel);
            d.craftLevel++;
            up = true;
        }
        if (up) levelUp(p, "Crafting level " + d.craftLevel + "! " + Math.round(doubleChance(d.craftLevel) * 100)
                + "% chance to craft double.");
    }

    private static void addSmeltXp(ServerPlayer p, float xp) {
        PlayerCharacter d = cap(p);
        if (d == null || d.smeltLevel >= MAX_LEVEL) return;
        d.smeltXp += xp;
        boolean up = false;
        while (d.smeltLevel < MAX_LEVEL && d.smeltXp >= needed(d.smeltLevel)) {
            d.smeltXp -= needed(d.smeltLevel);
            d.smeltLevel++;
            up = true;
        }
        if (up) levelUp(p, "Smelting level " + d.smeltLevel + "! Your furnaces cook "
                + Math.round(Stat.effective(d.smeltLevel) * SMELT_SPEED_PER_LEVEL * 100) + "% faster.");
    }

    private static void levelUp(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F);
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getCrafting().isEmpty()) return;
        ItemStack result = event.getCrafting();
        addCraftXp(p, 1F + Math.min(4F, result.getCount() * 0.25F));
        Container grid = event.getInventory();
        Set<Item> kinds = new HashSet<>();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (!s.isEmpty()) kinds.add(s.getItem());
        }
        if (kinds.size() < 2) return;     // single-ingredient recipes never double (no item loops)
        if (p.getRandom().nextFloat() < doubleChance(craftLevel(p))) {
            ItemStack extra = result.copy();
            if (!p.getInventory().add(extra)) p.drop(extra, false);
            p.displayClientMessage(Component.literal("Crafted double!").withStyle(ChatFormatting.YELLOW), true);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
    }

    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !event.getSmelting().isEmpty()) {
            addSmeltXp(p, event.getSmelting().getCount() * 0.6F);
        }
    }

    /** Opening a furnace makes it "yours": it cooks at your Smelting speed. */
    @SubscribeEvent
    public static void onUseFurnace(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer p)) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof AbstractFurnaceBlockEntity) be.getPersistentData().putUUID(FURNACE_OWNER, p.getUUID());
    }

    /** Extra cooking progress per tick for this furnace (0 = normal speed). */
    public static float furnaceBonus(AbstractFurnaceBlockEntity f) {
        if (f.getLevel() == null || f.getLevel().getServer() == null || !f.getPersistentData().hasUUID(FURNACE_OWNER)) return 0F;
        UUID id = f.getPersistentData().getUUID(FURNACE_OWNER);
        ServerPlayer owner = f.getLevel().getServer().getPlayerList().getPlayer(id);
        int level;
        if (owner != null) {
            level = smeltLevel(owner);
            f.getPersistentData().putInt("xw_smelt_level", level);      // remembered for when they log off
        } else {
            level = f.getPersistentData().getInt("xw_smelt_level");
        }
        return Stat.effective(level) * SMELT_SPEED_PER_LEVEL;
    }
}

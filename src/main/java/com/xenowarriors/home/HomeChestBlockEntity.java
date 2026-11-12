package com.xenowarriors.home;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** The Home Chest's 54 slots and its owner. Loaded ones are listed (by world) so bots can find their owner's. */
public class HomeChestBlockEntity extends BaseContainerBlockEntity {
    public static final int SIZE = 54;
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private UUID owner;

    /** Loaded Home Chests, by world. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    public HomeChestBlockEntity(BlockPos pos, BlockState state) { super(HomeBlocks.HOME_CHEST.get(), pos, state); }

    public UUID owner() { return owner; }
    public void setOwner(UUID u) { owner = u; setChanged(); remember(); }

    private void remember() {
        if (level != null && !level.isClientSide) LOADED.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(worldPosition.immutable());
    }

    @Override public void onLoad() { super.onLoad(); remember(); }
    @Override public void setRemoved() { super.setRemoved(); if (level != null) { var s = LOADED.get(level.dimension()); if (s != null) s.remove(worldPosition); } }
    @Override public void onChunkUnloaded() { super.onChunkUnloaded(); if (level != null) { var s = LOADED.get(level.dimension()); if (s != null) s.remove(worldPosition); } }

    /** The loaded Home Chests in this world (positions). */
    public static Collection<BlockPos> loaded(Level level) { return new ArrayList<>(LOADED.getOrDefault(level.dimension(), Set.of())); }

    /** (Server stopped) */
    public static void clear() { LOADED.clear(); }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override protected Component getDefaultName() { return Component.literal("Home Chest"); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return ChestMenu.sixRows(id, inv, this); }
    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int i) { return items.get(i); }
    @Override public ItemStack removeItem(int i, int n) { ItemStack s = ContainerHelper.removeItem(items, i, n); if (!s.isEmpty()) setChanged(); return s; }
    @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }
    @Override public void setItem(int i, ItemStack s) { items.set(i, s); if (s.getCount() > getMaxStackSize()) s.setCount(getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player p) { return net.minecraft.world.Container.stillValidBlockEntity(this, p); }
    @Override public void clearContent() { items.clear(); }

    /** Puts as much of this stack in as fits (merging first); what's left over is returned. */
    public ItemStack insert(ItemStack s) {
        s = s.copy();
        for (int i = 0; i < SIZE && !s.isEmpty(); i++) {
            ItemStack in = items.get(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameTags(in, s) && in.getCount() < in.getMaxStackSize()) {
                int n = Math.min(s.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(n); s.shrink(n);
            }
        }
        for (int i = 0; i < SIZE && !s.isEmpty(); i++) if (items.get(i).isEmpty()) { items.set(i, s.copy()); s = ItemStack.EMPTY; }
        setChanged();
        return s;
    }
}

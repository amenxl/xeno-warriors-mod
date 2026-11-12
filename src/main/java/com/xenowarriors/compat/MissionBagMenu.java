package com.xenowarriors.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The mission bag (see {@link MissionBag}): one page of 54 slots at a time (page buttons 0-3, done on the server like
 * the bot menu's tabs), then your inventory. Things come out - clicked, dragged or shift-clicked - but nothing goes in.
 */
public class MissionBagMenu extends AbstractContainerMenu {
    public static final int ROWS = 6;
    private final Container bag, view;
    private int page;

    public MissionBagMenu(int id, Inventory inv, Container bag) {
        super(com.xenowarriors.fox.ModMenus.MISSION_BAG.get(), id);
        this.bag = bag;
        this.view = bag.getContainerSize() >= MissionBag.SIZE ? new PageView() : bag;   // (client: what the server sends)
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < 9; c++) {
            addSlot(new Slot(view, c + r * 9, 8 + c * 18, 18 + r * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        int py = playerTop();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, py + 58));
        addDataSlot(new DataSlot() {
            @Override public int get() { return page; }
            @Override public void set(int v) { page = Math.max(0, Math.min(MissionBag.PAGES - 1, v)); }
        });
    }

    public static int playerTop() { return 18 + ROWS * 18 + 14; }

    public static MissionBagMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new MissionBagMenu(id, inv, new SimpleContainer(ROWS * 9));
    }

    public int page() { return page; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= MissionBag.PAGES) return false;
        page = id;
        return true;
    }

    @Override public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index >= ROWS * 9 || !slot.hasItem()) return ItemStack.EMPTY;           // (only out of the bag, never in)
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (!moveItemStackTo(stack, ROWS * 9, slots.size(), true)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean canDragTo(Slot slot) { return slot.index >= ROWS * 9; }

    /** One page of the bag (server). */
    private final class PageView implements Container {
        private int at(int i) { return i < 0 || i >= ROWS * 9 ? -1 : page * MissionBag.PAGE + i; }
        @Override public int getContainerSize() { return ROWS * 9; }
        @Override public boolean isEmpty() { for (int i = 0; i < ROWS * 9; i++) if (!getItem(i).isEmpty()) return false; return true; }
        @Override public ItemStack getItem(int i) { int k = at(i); return k < 0 ? ItemStack.EMPTY : bag.getItem(k); }
        @Override public ItemStack removeItem(int i, int n) { int k = at(i); ItemStack s = k < 0 ? ItemStack.EMPTY : bag.removeItem(k, n); bag.setChanged(); return s; }
        @Override public ItemStack removeItemNoUpdate(int i) { int k = at(i); ItemStack s = k < 0 ? ItemStack.EMPTY : bag.removeItemNoUpdate(k); bag.setChanged(); return s; }
        @Override public void setItem(int i, ItemStack s) { int k = at(i); if (k >= 0) { bag.setItem(k, s); bag.setChanged(); } }
        @Override public void setChanged() { bag.setChanged(); }
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void clearContent() { }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    }
}

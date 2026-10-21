package com.xenowarriors.fox;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * A tamed fox's bag: a helmet and a chestplate slot (worn by the fox, protecting it) and 9-27 storage slots
 * depending on its level, above the player's inventory.
 */
public class FoxMenu extends AbstractContainerMenu {
    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.MAINHAND};
    public static final int GEAR = 3;
    public final int rows;
    public final int foxId;
    private final Container storage, armor;
    private final Fox fox;

    public FoxMenu(int id, Inventory inv, Container storage, Container armor, Fox fox, int rows) {
        super(ModMenus.FOX.get(), id);
        this.storage = storage;
        this.armor = armor;
        this.fox = fox;
        this.rows = rows;
        this.foxId = fox == null ? -1 : fox.getId();
        storage.startOpen(inv.player);
        for (int i = 0; i < GEAR; i++) {
            final EquipmentSlot slot = ARMOR[i];
            Slot armorSlot = new Slot(armor, i, 8, 18 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (slot == EquipmentSlot.MAINHAND) return stack.getItem() instanceof net.minecraft.world.item.SwordItem
                            && !com.xenowarriors.mikiko.Hekublade.isBlade(stack);    // the Hekublade stays with Mikiko
                    return stack.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            };
            if (i < 2) {
                armorSlot.setBackground(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,
                        i == 0 ? net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_HELMET
                                : net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
            }
            addSlot(armorSlot);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(storage, c + r * 9, 30 + c * 18, 18 + r * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !com.xenowarriors.mikiko.Hekublade.isBlade(stack);
                }
            });
        }
        int py = 18 + Math.max(rows, GEAR) * 18 + 14;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 30 + c * 18, py + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 30 + c * 18, py + 58));
    }

    public static FoxMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int foxId = buf.readVarInt(), rows = buf.readVarInt();
        Fox fox = inv.player.level().getEntity(foxId) instanceof Fox f ? f : null;
        return new FoxMenu(id, inv, new SimpleContainer(rows * 9), new SimpleContainer(GEAR), fox, rows);
    }

    public Fox fox() {
        return fox;
    }

    @Override
    public boolean stillValid(Player player) {
        return fox == null || (fox.isAlive() && fox.distanceToSqr(player) < 10 * 10);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int foxEnd = GEAR + rows * 9;
        if (index < foxEnd) {
            if (!moveItemStackTo(stack, foxEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < GEAR && !moved; i++) {
                if (slots.get(i).mayPlace(stack) && !slots.get(i).hasItem()) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved && !moveItemStackTo(stack, GEAR, foxEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        storage.stopOpen(player);
        if (fox != null && !player.level().isClientSide) FoxPets.bagClosed(fox.getUUID());
    }

    /** The fox's worn armor, read and written straight on the fox (server side). */
    public static class ArmorContainer extends SimpleContainer {
        private final Fox fox;
        private boolean loading;

        public ArmorContainer(Fox fox) {
            super(GEAR);
            this.fox = fox;
            // fill in what it's wearing without writing back halfway (that used to wipe the later slots)
            loading = true;
            for (int i = 0; i < GEAR; i++) super.setItem(i, fox.getItemBySlot(ARMOR[i]).copy());
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) return;
            for (int i = 0; i < GEAR; i++) {
                fox.setItemSlot(ARMOR[i], getItem(i).copy());
                fox.setDropChance(ARMOR[i], 0F);     // it keeps its armor even if it runs away
            }
        }
    }
}

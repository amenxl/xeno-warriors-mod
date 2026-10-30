package com.xenowarriors.bot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * A bot teammate's things: what it wears (helmet, chestplate, leggings, boots), its offhand and what's in its hand,
 * in a column on the left; its 36-slot bag; then your inventory. The gear slots are the bot's own equipment.
 */
public class BotMenu extends AbstractContainerMenu {
    public static final EquipmentSlot[] GEAR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND};
    public static final int GEAR = GEAR_SLOTS.length, ROWS = 4;
    public final int botId;
    private final Container bag, gear;
    private final PartyBotEntity bot;

    public BotMenu(int id, Inventory inv, Container bag, Container gear, PartyBotEntity bot) {
        super(com.xenowarriors.fox.ModMenus.BOT.get(), id);
        this.bag = bag;
        this.gear = gear;
        this.bot = bot;
        this.botId = bot == null ? -1 : bot.getId();
        bag.startOpen(inv.player);
        for (int i = 0; i < GEAR; i++) {
            final EquipmentSlot es = GEAR_SLOTS[i];
            Slot s = new Slot(gear, i, 8, 18 + i * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    if (es.getType() == EquipmentSlot.Type.ARMOR) return stack.getItem() instanceof ArmorItem a ? a.getEquipmentSlot() == es : stack.canEquip(es, inv.player);
                    return true;
                }
                @Override public int getMaxStackSize() { return es.getType() == EquipmentSlot.Type.ARMOR ? 1 : 64; }
            };
            if (i < 4) s.setBackground(InventoryMenu.BLOCK_ATLAS, switch (i) {
                case 0 -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                case 1 -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                case 2 -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                default -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            });
            else if (i == 4) s.setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            addSlot(s);
        }
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(bag, c + r * 9, 30 + c * 18, 18 + r * 18));
        int py = playerTop();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 30 + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 30 + c * 18, py + 58));
    }

    public static int playerTop() { return 18 + Math.max(ROWS, GEAR) * 18 + 14; }

    public static BotMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int botId = buf.readVarInt();
        PartyBotEntity b = inv.player.level().getEntity(botId) instanceof PartyBotEntity pb ? pb : null;
        return new BotMenu(id, inv, new SimpleContainer(ROWS * 9), new SimpleContainer(GEAR), b);
    }

    public PartyBotEntity bot() { return bot; }

    @Override
    public boolean stillValid(Player player) {
        return bot == null || (bot.isAlive() && bot.distanceToSqr(player) < 10 * 10);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int botEnd = GEAR + ROWS * 9;
        if (index < botEnd) {
            if (!moveItemStackTo(stack, botEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 4 && !moved; i++)                      // armour goes straight on
                if (slots.get(i).mayPlace(stack) && !slots.get(i).hasItem()) moved = moveItemStackTo(stack, i, i + 1, false);
            if (!moved && !moveItemStackTo(stack, GEAR, botEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        bag.stopOpen(player);
    }

    /** The bot's equipment, read and written straight on the bot (server side). */
    public static class GearContainer extends SimpleContainer {
        private final PartyBotEntity bot;
        private boolean loading;

        public GearContainer(PartyBotEntity bot) {
            super(GEAR);
            this.bot = bot;
            loading = true;
            for (int i = 0; i < GEAR; i++) super.setItem(i, bot.getItemBySlot(GEAR_SLOTS[i]).copy());
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (loading) return;
            for (int i = 0; i < GEAR; i++) bot.setItemSlot(GEAR_SLOTS[i], getItem(i).copy());
            bot.handPicked = bot.tickCount;                                       // (what you put in its hand stays a while)
        }

        @Override
        public boolean stillValid(Player p) { return bot.isAlive() && bot.distanceToSqr(p) < 10 * 10; }
    }
}

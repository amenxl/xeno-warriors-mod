package com.xenowarriors.bot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * A bot teammate's things: what it wears (helmet, chestplate, leggings, boots), its offhand and what's in its hand,
 * in a column on the left; its bag - or one of its three extra tabs (see {@link BotInventory}), picked with the tab
 * buttons; then your inventory. The gear slots are the bot's own equipment.
 *
 * The bag area is 36 slots that show the tab picked: the bag (36) or an extra tab (27 - the last row is hidden). The
 * tab lives on the server (a menu button, synced back as data), so the client never guesses what's in a tab.
 */
public class BotMenu extends AbstractContainerMenu {
    public static final EquipmentSlot[] GEAR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND};
    public static final int GEAR = GEAR_SLOTS.length, ROWS = 4;
    /** Menu buttons: 0-3 pick the tab (bag, extra 1-3); SORT tidies its things now. */
    public static final int SORT = 10;
    public final int botId;
    private final Container bag, gear, view;
    private final PartyBotEntity bot;
    /** The tab shown (0 = bag, 1-3 = extra tabs) and whether it has extra tabs (synced from the server). */
    private int tab, tabs;

    public BotMenu(int id, Inventory inv, Container bag, Container gear, PartyBotEntity bot) {
        super(com.xenowarriors.fox.ModMenus.BOT.get(), id);
        this.bag = bag;
        this.gear = gear;
        this.bot = bot;
        this.botId = bot == null ? -1 : bot.getId();
        this.view = bag instanceof BotInventory bi ? new TabView(bi) : bag;      // (server: the real things; client: what the server sends)
        if (bag instanceof BotInventory) tabs = bot != null && !bot.journey() ? 1 : 0;
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
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < 9; c++) {
            final int k = c + r * 9;
            addSlot(new Slot(view, k, 30 + c * 18, 18 + r * 18) {
                @Override public boolean isActive() { return shown(k); }
                @Override public boolean mayPlace(ItemStack stack) { return shown(k); }
                @Override public boolean mayPickup(Player p) { return shown(k); }
            });
        }
        int py = playerTop();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 30 + c * 18, py + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 30 + c * 18, py + 58));
        addDataSlot(new DataSlot() {
            @Override public int get() { return tab; }
            @Override public void set(int v) { tab = Math.max(0, Math.min(BotInventory.TABS, v)); }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return tabs; }
            @Override public void set(int v) { tabs = v; }
        });
    }

    /** Is this bag-area slot in use on the tab shown? (An extra tab has 27 slots: the last row is hidden.) */
    private boolean shown(int k) { return tab == 0 || k < BotInventory.TAB; }

    public static int playerTop() { return 18 + Math.max(ROWS, GEAR) * 18 + 14; }

    public static BotMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        int botId = buf.readVarInt();
        PartyBotEntity b = inv.player.level().getEntity(botId) instanceof PartyBotEntity pb ? pb : null;
        return new BotMenu(id, inv, new SimpleContainer(ROWS * 9), new SimpleContainer(GEAR), b);
    }

    public PartyBotEntity bot() { return bot; }
    /** The tab shown: 0 = bag, 1-3 = extra tabs. */
    public int tab() { return tab; }
    /** Has it the extra tabs (any bot teammate but a Journey one)? */
    public boolean hasTabs() { return tabs > 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id <= BotInventory.TABS) {
            if (id > 0 && tabs == 0) return false;
            tab = id;
            return true;
        }
        if (id == SORT && bot != null && tabs > 0) {
            BotBags.organize(bot);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return bot == null || (bot.isAlive() && bot.distanceToSqr(player) < 10 * 10);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.isActive()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        int botEnd = GEAR + ROWS * 9;
        if (index < botEnd) {
            if (!moveItemStackTo(stack, botEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < 4 && !moved; i++)                      // armour goes straight on
                if (slots.get(i).mayPlace(stack) && !slots.get(i).hasItem()) moved = moveItemStackTo(stack, i, i + 1, false);
            if (!moved && !moveItemStackTo(stack, GEAR, botEnd, false)) return ItemStack.EMPTY;   // (into the tab shown)
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        bag.stopOpen(player);
        if (bot != null && player instanceof net.minecraft.server.level.ServerPlayer sp && sp.getUUID().equals(bot.ownerId())) BotCrafting.bagClosed(bot, sp);   // (you may have just handed it what it needed)
    }

    /** The bag area on the server: the bot's bag, or one of its extra tabs (by the tab shown). */
    private final class TabView implements Container {
        private final BotInventory inv;
        TabView(BotInventory inv) { this.inv = inv; }
        private int at(int i) {
            if (i < 0 || i >= ROWS * 9) return -1;
            if (tab == 0) return i;
            return i < BotInventory.TAB ? BotInventory.tabStart(tab) + i : -1;
        }
        @Override public int getContainerSize() { return ROWS * 9; }
        @Override public boolean isEmpty() { for (int i = 0; i < ROWS * 9; i++) if (!getItem(i).isEmpty()) return false; return true; }
        @Override public ItemStack getItem(int i) { int k = at(i); return k < 0 ? ItemStack.EMPTY : inv.getItem(k); }
        @Override public ItemStack removeItem(int i, int n) { int k = at(i); return k < 0 ? ItemStack.EMPTY : inv.removeItem(k, n); }
        @Override public ItemStack removeItemNoUpdate(int i) { int k = at(i); return k < 0 ? ItemStack.EMPTY : inv.removeItemNoUpdate(k); }
        @Override public void setItem(int i, ItemStack s) { int k = at(i); if (k >= 0) inv.setItem(k, s); }
        @Override public void setChanged() { inv.setChanged(); }
        @Override public boolean stillValid(Player p) { return inv.stillValid(p); }
        @Override public void clearContent() { for (int i = 0; i < ROWS * 9; i++) setItem(i, ItemStack.EMPTY); }
    }

    /**
     * The bot's equipment, read and written straight on the bot (server side) - live, never a copy: the bot changes
     * what it holds and wears while the menu is open (a weapon out for a fight, a shield, armour it crafted), and a
     * copy written back on the next click used to undo that (the new piece gone, the old one doubled).
     */
    public static class GearContainer implements Container {
        private final PartyBotEntity bot;

        public GearContainer(PartyBotEntity bot) { this.bot = bot; }

        @Override public int getContainerSize() { return GEAR; }
        @Override public boolean isEmpty() { for (EquipmentSlot es : GEAR_SLOTS) if (!bot.getItemBySlot(es).isEmpty()) return false; return true; }
        @Override public ItemStack getItem(int i) { return i < 0 || i >= GEAR ? ItemStack.EMPTY : bot.getItemBySlot(GEAR_SLOTS[i]); }

        @Override public ItemStack removeItem(int i, int n) {
            ItemStack s = getItem(i);
            if (s.isEmpty() || n <= 0) return ItemStack.EMPTY;
            ItemStack out = s.split(n);
            bot.setItemSlot(GEAR_SLOTS[i], s.isEmpty() ? ItemStack.EMPTY : s);
            picked(i);
            return out;
        }

        @Override public ItemStack removeItemNoUpdate(int i) {
            ItemStack s = getItem(i);
            if (s.isEmpty()) return ItemStack.EMPTY;
            bot.setItemSlot(GEAR_SLOTS[i], ItemStack.EMPTY);
            return s;
        }

        @Override public void setItem(int i, ItemStack s) {
            if (i < 0 || i >= GEAR) return;
            bot.setItemSlot(GEAR_SLOTS[i], s);
            picked(i);
        }

        private void picked(int i) { if (GEAR_SLOTS[i] == EquipmentSlot.MAINHAND || GEAR_SLOTS[i] == EquipmentSlot.OFFHAND) bot.handPicked = bot.tickCount; }   // (what you put in its hands stays a while)

        @Override public void setChanged() {}
        @Override public void clearContent() { for (EquipmentSlot es : GEAR_SLOTS) bot.setItemSlot(es, ItemStack.EMPTY); }
        @Override public boolean stillValid(Player p) { return bot.isAlive() && bot.distanceToSqr(p) < 10 * 10; }
    }
}

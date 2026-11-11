package com.xenowarriors.bot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A bot teammate's things: its bag (slots 0-35 - that character's own main inventory, which goes home with it and is
 * what you carry when you play that character) and, after it, three extra tabs of 54 slots each (36-197, six rows
 * like a double chest; before v0.219 they had 27 - older saves are moved over slot for slot, see loadExtra) that only
 * exist while the character is a bot. The extra tabs are kept with the bot (its record, and per character in the
 * owner's save while it's away - see {@link BotBags}); they never go into the character's own inventory.
 *
 * Everything that looks through a bot's things (crafting, errands, eating, potions, torches, ammo, gear) goes over the
 * whole container, so all four tabs count; addItem fills the bag first, then the extra tabs, so "full" means every tab
 * is full.
 */
public class BotInventory extends SimpleContainer {
    public static final int BAG = 36, TAB = 54, TABS = 3, EXTRA = TAB * TABS, SIZE = BAG + EXTRA;

    /** Players with this bot's inventory screen open right now (server side): its tidying waits while you look. */
    private int viewers;

    public BotInventory() { super(SIZE); }

    @Override public void startOpen(Player p) { viewers++; }
    @Override public void stopOpen(Player p) { viewers = Math.max(0, viewers - 1); }
    public boolean beingViewed() { return viewers > 0; }

    /** First slot of an extra tab (1-3). */
    public static int tabStart(int tab) { return BAG + (tab - 1) * TAB; }

    /** The bag (slots 0-35) as a list, as its save has always had it (no slot numbers). */
    public ListTag bagTag() {
        ListTag l = new ListTag();
        for (int i = 0; i < BAG; i++) if (!getItem(i).isEmpty()) l.add(getItem(i).save(new CompoundTag()));
        return l;
    }

    /** Slot numbers in the extra tabs' save: "XSlot" = 0-161 (54 a tab); the old "Slot" = 0-80 (27 a tab, before v0.219). */
    public static final String XSLOT = "XSlot", OLD_SLOT = "Slot";
    public static final int OLD_TAB = 27;

    /** The extra tabs as a list, each with its slot (0-161) so the tidy layout survives. */
    public ListTag extraTag() {
        ListTag l = new ListTag();
        for (int i = 0; i < EXTRA; i++) {
            ItemStack s = getItem(BAG + i);
            if (s.isEmpty()) continue;
            CompoundTag t = s.save(new CompoundTag());
            t.putShort(XSLOT, (short) i);
            l.add(t);
        }
        return l;
    }

    /** Where a saved extra-tab item goes (0-161), or -1 (no slot: the first free one). An old save's tab-1/2/3 slot keeps its tab and place. */
    static int extraSlot(CompoundTag t) {
        if (t.contains(XSLOT)) return t.getShort(XSLOT);
        if (t.contains(OLD_SLOT)) {
            int old = t.getByte(OLD_SLOT) & 255;
            return old < OLD_TAB * TABS ? (old / OLD_TAB) * TAB + old % OLD_TAB : -1;
        }
        return -1;
    }

    public boolean extraEmpty() { for (int i = BAG; i < SIZE; i++) if (!getItem(i).isEmpty()) return false; return true; }

    /** The bag from a list (old saves: no slot numbers - in order; a character save: slots 0-35). Clears the bag first. Returns what didn't fit (never in practice). */
    public List<ItemStack> loadBag(ListTag l) {
        for (int i = 0; i < BAG; i++) setItem(i, ItemStack.EMPTY);
        return place(l, 0, BAG, false);
    }

    /**
     * Into the extra tabs from a list (slot numbers kept where free). Adds to what's there. Returns what didn't fit.
     * An old save (27-slot tabs) loads tab for tab: its slot 5 of tab 2 is slot 5 of the bigger tab 2.
     */
    public List<ItemStack> loadExtra(ListTag l) { return place(l, BAG, EXTRA, true); }

    private List<ItemStack> place(ListTag l, int from, int n, boolean slotted) {
        List<ItemStack> left = new ArrayList<>(), later = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            ItemStack s = ItemStack.of(t);
            if (s.isEmpty()) continue;
            int slot = slotted ? extraSlot(t) : t.contains("Slot") ? t.getByte("Slot") & 255 : -1;
            if (slot >= 0 && slot < n && getItem(from + slot).isEmpty()) setItem(from + slot, s);
            else later.add(s);
        }
        for (ItemStack s : later) {
            int free = -1;
            for (int i = from; i < from + n; i++) if (getItem(i).isEmpty()) { free = i; break; }
            if (free < 0) left.add(s); else setItem(free, s);
        }
        return left;
    }

    /** Empties the extra tabs (they went somewhere else). */
    public void clearExtra() { for (int i = BAG; i < SIZE; i++) setItem(i, ItemStack.EMPTY); }

    /** (Tests) how many of this item it has in all four tabs. */
    public int total(net.minecraft.world.item.Item it) { int n = 0; for (int i = 0; i < SIZE; i++) if (getItem(i).is(it)) n += getItem(i).getCount(); return n; }
}

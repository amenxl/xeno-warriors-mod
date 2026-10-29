package com.xenowarriors.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Every character is its own save: inventory, armour and offhand, ender chest and XP levels are kept per character
 * (keyed like the stats: the show character's id, or "you&lt;slot&gt;"). Switching characters puts the old one's things
 * away and brings the new one's out - a character you've never played starts with nothing.
 *
 * The "live" key says whose things are out right now. Journey mode swaps characters for battles without touching the
 * inventory (the key stays your own), so what you find on a Journey comes home with you. The first time a player with
 * no character picks one, what they're carrying goes with them.
 *
 * Bot teammates check that character's things out (see PartyBots) and put them back when they leave.
 */
public final class CharacterInventories {
    private CharacterInventories() {}

    private static final String ROOT = "xw_inventories", LIVE = "xw_inv_live";

    private static CompoundTag root(Player p) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ROOT)) persisted.put(ROOT, new CompoundTag());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted.getCompound(ROOT);
    }

    private static void put(Player p, CompoundTag root) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(ROOT, root);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Whose things are out right now ("" = never switched: whatever they had before the mod). */
    public static String live(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString(LIVE); }

    private static void setLive(Player p, String key) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putString(LIVE, key);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** What's in a character's save right now (empty when they've never played it or it's checked out by a bot). */
    public static CompoundTag saved(Player p, String key) { return root(p).getCompound(key).copy(); }

    public static boolean has(Player p, String key) { return root(p).contains(key); }

    /** Snapshot of what the player is carrying. */
    public static CompoundTag snapshot(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.put("inv", p.getInventory().save(new ListTag()));
        t.put("ender", p.getEnderChestInventory().createTag());
        t.putInt("xp_level", p.experienceLevel);
        t.putFloat("xp_progress", p.experienceProgress);
        t.putInt("xp_total", p.totalExperience);
        return t;
    }

    private static void apply(ServerPlayer p, CompoundTag t) {
        p.closeContainer();
        p.getInventory().clearContent();
        p.getInventory().load(t.getList("inv", net.minecraft.nbt.Tag.TAG_COMPOUND));
        p.getEnderChestInventory().clearContent();
        p.getEnderChestInventory().fromTag(t.getList("ender", net.minecraft.nbt.Tag.TAG_COMPOUND));
        p.experienceLevel = t.getInt("xp_level");
        p.experienceProgress = t.getFloat("xp_progress");
        p.totalExperience = t.getInt("xp_total");
        p.setExperienceLevels(p.experienceLevel);
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.sendAllDataToRemote();
    }

    /** Put the live character's things away under {@code to}'s key? No: away under the live key, then bring {@code to}'s out. */
    public static void switchTo(ServerPlayer p, String to) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        String from = live(p);
        if (from.equals(to)) return;
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(p)) return;        // (a Journey swaps characters for battles; things stay yours)
        CompoundTag root = root(p);
        if (from.isEmpty() && !root.contains(to)) {                             // first character ever: what they have goes with them
            setLive(p, to);
            return;
        }
        if (!from.isEmpty()) root.put(from, snapshot(p));
        put(p, root);
        apply(p, root.getCompound(to));
        setLive(p, to);
    }

    /** Take a character's things out of its save (for a bot teammate). */
    public static CompoundTag checkOut(ServerPlayer p, String key) {
        CompoundTag root = root(p);
        CompoundTag t = root.getCompound(key).copy();
        root.remove(key);
        put(p, root);
        return t;
    }

    /** Put a character's things back (a bot teammate leaving): main inventory from its bag, armour from what it wears. */
    public static void checkIn(ServerPlayer p, String key, ListTag inv, ItemStack[] armour, ItemStack offhand, CompoundTag kept) {
        CompoundTag root = root(p);
        CompoundTag t = kept == null ? new CompoundTag() : kept.copy();
        // rebuild the vanilla inventory list: slots 0-35 main, 100-103 armour, 150 offhand
        ListTag list = new ListTag();
        for (int i = 0; i < inv.size(); i++) {
            CompoundTag it = inv.getCompound(i);
            int slot = it.getByte("Slot") & 255;
            if (slot < 36) list.add(it);
        }
        for (int i = 0; i < 4 && i < armour.length; i++) if (!armour[i].isEmpty()) {
            CompoundTag it = new CompoundTag(); armour[i].save(it); it.putByte("Slot", (byte) (100 + i)); list.add(it);
        }
        if (offhand != null && !offhand.isEmpty()) { CompoundTag it = new CompoundTag(); offhand.save(it); it.putByte("Slot", (byte) 150); list.add(it); }
        t.put("inv", list);
        root.put(key, t);
        put(p, root);
    }

    /** The armour in a save, by EquipmentSlot index (feet, legs, chest, head), and the offhand. */
    public static ItemStack[] armour(CompoundTag saved) {
        ItemStack[] out = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        ListTag inv = saved.getList("inv", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < inv.size(); i++) {
            CompoundTag it = inv.getCompound(i);
            int slot = it.getByte("Slot") & 255;
            if (slot >= 100 && slot < 104) out[slot - 100] = ItemStack.of(it);
            else if (slot == 150) out[4] = ItemStack.of(it);
        }
        return out;
    }

    /** The main-inventory part of a save, as a bag list (slots 0-35 kept as they were). */
    public static ListTag mainItems(CompoundTag saved) {
        ListTag out = new ListTag();
        ListTag inv = saved.getList("inv", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < inv.size(); i++) if ((inv.getCompound(i).getByte("Slot") & 255) < 36) out.add(inv.getCompound(i));
        return out;
    }

    public static EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
}

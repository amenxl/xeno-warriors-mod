package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * A bot's three extra tabs (see {@link BotInventory}): where they wait while the character isn't a bot, and the bot
 * keeping its things tidy.
 * <ul>
 *   <li>While the character is out as a bot its extra tabs are on it (and in its record: logout, crash snapshot,
 *       waiting lists, Journey stash). When it goes home (dismissed, bled out, the void...) they go into the owner's
 *       save under that character ({@code xw_botbags}) - never into the character's own inventory - and come back
 *       the next time it joins as a bot. Playing that character yourself never touches them.</li>
 *   <li>Tidying: every so often when it's idle, and a few seconds after its things change (picked up, mined,
 *       crafted), it merges part stacks, keeps its best weapon, ranged weapon, ammo, shield, totems, some food,
 *       torches and potions in its bag, and puts everything else in the extra tabs sorted by kind (blocks, ores and
 *       materials, food, gear, other). It never throws anything away and never touches what it wears or holds; it
 *       waits while you have its inventory open (the "Sort" button there tidies at once).</li>
 * </ul>
 */
public final class BotBags {
    private BotBags() {}

    private static final String ROOT = "xw_botbags";

    // ---------------------------------------------------------------- kept in the owner's save while it's away

    private static CompoundTag root(Player p) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.getCompound(ROOT);
    }

    private static void putRoot(Player p, CompoundTag root) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (root.isEmpty()) persisted.remove(ROOT); else persisted.put(ROOT, root);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Takes a character's waiting extra tabs out of the save (it's joining as a bot). Empty when there are none. */
    public static ListTag take(ServerPlayer o, String key) {
        CompoundTag root = root(o);
        ListTag l = root.getList(key, Tag.TAG_COMPOUND);
        if (root.contains(key)) { root.remove(key); putRoot(o, root); }
        return l;
    }

    /** Puts a character's extra tabs away in the save (it went home). Adds to anything already there; what can't fit comes to the owner. */
    public static void store(ServerPlayer o, String key, ListTag extra) {
        if (extra == null || extra.isEmpty() || key == null || key.isEmpty()) return;
        CompoundTag root = root(o);
        BotInventory tmp = new BotInventory();
        tmp.loadExtra(root.getList(key, Tag.TAG_COMPOUND));
        for (ItemStack s : tmp.loadExtra(extra)) if (!s.isEmpty() && !o.getInventory().add(s)) o.drop(s, false);
        ListTag out = tmp.extraTag();
        if (out.isEmpty()) root.remove(key); else root.put(key, out);
        putRoot(o, root);
    }

    /** A character is gone for good (a deleted You): its waiting extra tabs come to the player. */
    public static void forget(ServerPlayer o, String key) {
        ListTag l = take(o, key);
        for (int i = 0; i < l.size(); i++) { ItemStack s = ItemStack.of(l.getCompound(i)); if (!s.isEmpty() && !o.getInventory().add(s)) o.drop(s, false); }
    }

    /** (Tests) how many of an item wait in a character's stored extra tabs. */
    public static int stored(ServerPlayer o, String key, Item it) {
        ListTag l = root(o).getList(key, Tag.TAG_COMPOUND);
        int n = 0;
        for (int i = 0; i < l.size(); i++) { ItemStack s = ItemStack.of(l.getCompound(i)); if (s.is(it)) n += s.getCount(); }
        return n;
    }

    // ---------------------------------------------------------------- tidying

    /** Off switch (tests that lay out a bot's bag by hand). */
    public static boolean AUTO = true;
    /** (Tests) how many times a bot tidied its things (changed something). */
    public static int sorts;

    /** From the bot's tick (once a second). */
    static void tick(PartyBotEntity b) {
        if (!AUTO) return;
        boolean due = b.tidyAt > 0 && b.tickCount >= b.tidyAt;
        if (!due && b.tickCount % 1200 != 15) return;                            // (anyway once a minute)
        if (!idle(b)) return;
        b.tidyAt = 0;
        organize(b);
    }

    /** Free to tidy: not fighting, eating, drinking, on an errand, waiting on a crafting answer, down, or being looked at. */
    static boolean idle(PartyBotEntity b) {
        return !b.journey() && !b.downed() && b.getTarget() == null && !b.eating() && !b.drinking() && b.errand == null
                && !b.inventory.beingViewed() && !BotCrafting.offering(b) && !b.isNoAi() && b.mineTarget == null;
    }

    private static final int BLOCKS = 0, MATERIALS = 1, FOOD = 2, GEAR = 3, MISC = 4;

    static int category(ItemStack s) {
        if (food(s)) return FOOD;
        Item it = s.getItem();
        if (s.isDamageableItem() || it instanceof ArmorItem || it instanceof TieredItem || it instanceof ProjectileWeaponItem || BotErrands.isShield(s)) return GEAR;
        if (s.is(Tags.Items.ORES) || s.is(Tags.Items.RAW_MATERIALS) || s.is(Tags.Items.INGOTS) || s.is(Tags.Items.GEMS) || s.is(Tags.Items.NUGGETS)
                || s.is(Tags.Items.DUSTS) || s.is(Tags.Items.RODS) || s.is(Tags.Items.STRING) || s.is(Tags.Items.LEATHER) || s.is(Tags.Items.FEATHERS)
                || s.is(Tags.Items.BONES) || s.is(Tags.Items.GUNPOWDER) || s.is(Tags.Items.SLIMEBALLS) || s.is(Items.COAL) || s.is(Items.CHARCOAL)
                || s.is(Tags.Items.STORAGE_BLOCKS)) return MATERIALS;
        if (it instanceof BlockItem) return BLOCKS;
        return MISC;
    }

    static boolean food(ItemStack s) { return s.isEdible() && !s.is(Items.ROTTEN_FLESH) && !s.is(Items.SPIDER_EYE) && !s.is(Items.PUFFERFISH) && !s.is(Items.POISONOUS_POTATO); }
    static boolean torch(ItemStack s) { return s.is(Items.TORCH) || s.is(Items.SOUL_TORCH); }
    static boolean ammo(ItemStack s) {
        if (s.getItem() instanceof ArrowItem) return true;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && (id.getPath().contains("ammo") || id.getPath().contains("bullet"));
    }

    private static String id(ItemStack s) { var k = ForgeRegistries.ITEMS.getKey(s.getItem()); return k == null ? "" : k.toString(); }

    /**
     * Tidies the bot's things now. Returns whether anything moved. Never loses or makes an item: the same stacks,
     * merged and placed again; what's worn and held is never touched.
     */
    public static boolean organize(PartyBotEntity b) {
        BotInventory inv = b.inventory;
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < BotInventory.SIZE; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            ItemStack c = s.copy();
            for (ItemStack m : all) {                                             // (part stacks merged)
                if (c.isEmpty()) break;
                if (m.getCount() < m.getMaxStackSize() && ItemStack.isSameItemSameTags(m, c)) {
                    int n = Math.min(c.getCount(), m.getMaxStackSize() - m.getCount());
                    m.grow(n); c.shrink(n);
                }
            }
            if (!c.isEmpty()) all.add(c);
        }
        // what stays in its bag: its best weapon, best ranged weapon, ammo, shield, totems, food, torches, potions
        List<ItemStack> keep = new ArrayList<>();
        pickBest(all, keep, s -> BotGear.weaponScore(b, s), 1);
        pickBest(all, keep, s -> BotRanged.score(s), 1);
        pickBest(all, keep, s -> ammo(s) ? s.getCount() : 0F, 3);
        pickBest(all, keep, s -> BotErrands.isShield(s) ? 1F + (s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : 0) : 0F, 1);
        pickBest(all, keep, s -> s.is(Items.TOTEM_OF_UNDYING) ? 1F : 0F, 2);
        pickBest(all, keep, s -> { if (!food(s)) return 0F; var p = s.getFoodProperties(b); return 1F + (p == null ? 0 : p.getNutrition()) * 100F + s.getCount(); }, 2);
        pickBest(all, keep, s -> torch(s) ? s.getCount() : 0F, 2);
        pickBest(all, keep, s -> s.getItem() instanceof PotionItem ? 1F : 0F, 3);
        all.sort(Comparator.<ItemStack>comparingInt(BotBags::category).thenComparing(BotBags::id).thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
                .thenComparingInt(ItemStack::getDamageValue));
        ItemStack[] out = new ItemStack[BotInventory.SIZE];
        Arrays.fill(out, ItemStack.EMPTY);
        int bag = 0;
        for (ItemStack s : keep) out[bag++] = s;
        int x = BotInventory.BAG;
        for (ItemStack s : all) {
            if (x < BotInventory.SIZE) out[x++] = s;
            else if (bag < BotInventory.BAG) out[bag++] = s;                      // (extra tabs full: the rest stays in its bag)
            else { XenoWarriors.LOGGER.warn("XW bot tidy: no room for {} - left as it was", s); return false; }   // (can't happen: merging never needs more slots)
        }
        boolean same = true;
        for (int i = 0; i < BotInventory.SIZE && same; i++) if (!ItemStack.matches(inv.getItem(i), out[i])) same = false;
        if (same) return false;
        b.sorting = true;
        try { for (int i = 0; i < BotInventory.SIZE; i++) inv.setItem(i, out[i]); }
        finally { b.sorting = false; }
        sorts++;
        PartyBots.testLog(b, "tidied its things (" + keep.size() + " kept in its bag, " + (x - BotInventory.BAG) + " stacks in the extra tabs)");
        return true;
    }

    /** Moves up to {@code n} of the best-scoring stacks (score > 0) from {@code from} to {@code to}. */
    private static void pickBest(List<ItemStack> from, List<ItemStack> to, java.util.function.ToDoubleFunction<ItemStack> score, int n) {
        for (int k = 0; k < n; k++) {
            int best = -1; double bs = 0;
            for (int i = 0; i < from.size(); i++) { double sc = score.applyAsDouble(from.get(i)); if (sc > bs) { bs = sc; best = i; } }
            if (best < 0) return;
            to.add(from.remove(best));
        }
    }

    /** (Tests) every item and its count over a container's slots (to prove tidying neither loses nor makes anything). */
    public static Map<String, Integer> census(SimpleContainer c) {
        Map<String, Integer> m = new TreeMap<>();
        for (int i = 0; i < c.getContainerSize(); i++) { ItemStack s = c.getItem(i); if (!s.isEmpty()) m.merge(id(s) + (s.hasTag() ? s.getTag().toString() : ""), s.getCount(), Integer::sum); }
        return m;
    }
}

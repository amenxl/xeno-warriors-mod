package com.xenowarriors.compat;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

/**
 * Inventory Totem ("inventorytotem", Serilum): a Totem of Undying anywhere in your inventory saves you, not just one in
 * your hand. It does that from a normal-priority LivingDeathEvent listener - after our downed system (which turns a
 * lethal hit into "downed" before you ever die) and our other death listeners have already acted. So when that mod is
 * loaded, we treat a totem in the inventory the way vanilla treats one in your hand: our downed system lets the hit
 * through, and the totem is used first thing at death (exactly what Inventory Totem / vanilla do: 1 HP, effects cleared,
 * Regeneration II, Absorption II, Fire Resistance, the totem animation), the death cancelled - which also means
 * Inventory Totem's own listener never runs for it (it doesn't receive cancelled events), so one totem is used, never two.
 * Without that mod, nothing here does anything.
 */
public final class InventoryTotem {
    private InventoryTotem() {}

    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("inventorytotem");
        return loaded;
    }

    /** Test hook: act as if the mod were (or weren't) installed; null = look again. */
    public static void testForce(Boolean on) { loaded = on; }

    private static boolean inHand(Player p) {
        return p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    /** The first totem anywhere in the inventory (hotbar, main, armor and offhand slots), or null. */
    public static ItemStack find(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) return s;
        }
        return null;
    }

    /** Would Inventory Totem save this player from this death (a totem in the inventory, not in a hand, and the damage can be stopped)? */
    public static boolean saves(Player p, DamageSource src) {
        return loaded() && p != null && !p.isCreative() && !p.isSpectator() && (src == null || !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
                && !inHand(p) && find(p) != null;
    }

    /** Uses the inventory totem (as Inventory Totem / vanilla do). True if it saved them; the caller cancels the death. */
    public static boolean use(ServerPlayer p, DamageSource src) {
        if (!saves(p, src)) return false;
        ItemStack totem = find(p);
        p.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
        CriteriaTriggers.USED_TOTEM.trigger(p, totem);
        totem.shrink(1);
        p.setHealth(1.0F);
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        p.serverLevel().broadcastEntityEvent(p, (byte) 35);
        p.getInventory().setChanged();
        lastUsedAt = p.level().getGameTime();
        com.xenowarriors.XenoWarriors.LOGGER.info("Inventory totem used for {} ({} left)", p.getGameProfile().getName(), p.getInventory().countItem(Items.TOTEM_OF_UNDYING));
        return true;
    }

    /** Game time of the last use (tests). */
    public static long lastUsedAt = -1;
}

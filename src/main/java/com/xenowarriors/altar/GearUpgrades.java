package com.xenowarriors.altar;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.sky.ModTools;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Karma Altar upgrades for Sky Crystal and Corrupted weapons and tools (anyone can do these): Sky gear takes Sky
 * Crystals, Corrupted gear takes Corrupted Shards. Three levels, stored on the item ("XwForge"); each one gives
 * weapons +1 attack damage, tools +20% digging speed, and every piece +25% durability (and a full repair).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GearUpgrades {
    public static final String TAG = "XwForge";
    public static final int MAX = 3;
    public static final int[] MATERIALS = {3, 6, 12};
    public static final int[] KARMA = {15, 30, 45};
    public static final float DAMAGE = 1F, DIG = 0.2F, DURABILITY = 0.25F;
    private static final UUID DAMAGE_ID = UUID.fromString("6b1f3c2e-8a8d-4f3b-9e5e-1d4a7c0f2b91");

    private GearUpgrades() {}

    /** "sky" / "dark" for upgradeable Sky Crystal / Corrupted gear, else null. */
    public static String path(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof TieredItem t)) return null;
        Tier tier = t.getTier();
        return tier == ModTools.SKY ? "sky" : tier == ModTools.CORRUPTED ? "dark" : null;
    }

    /** Whether this material is the one this gear takes. */
    public static boolean fits(ItemStack gear, ItemStack material) {
        String p = path(gear);
        return p != null && p.equals(KarmaAltarMenu.path(material));
    }

    public static int level(ItemStack s) { return s.hasTag() ? s.getTag().getInt(TAG) : 0; }

    public static boolean weapon(ItemStack s) { return s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem; }

    public static boolean tool(ItemStack s) { return s.getItem() instanceof TieredItem && !(s.getItem() instanceof SwordItem); }

    /** Raises the level by one (capped) and repairs it fully. */
    public static void upgrade(ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        t.putInt(TAG, Math.min(MAX, level(s) + 1));
        s.setDamageValue(0);
    }

    /** Durability with the upgrade (used by the Sky / Corrupted tool items). */
    public static int maxDamage(ItemStack s, int base) {
        return Math.round(base * (1F + DURABILITY * level(s)));
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;
        ItemStack s = event.getItemStack();
        int lvl = level(s);
        if (lvl <= 0 || path(s) == null || !weapon(s)) return;
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID, "Karma Altar upgrade", DAMAGE * lvl, AttributeModifier.Operation.ADDITION));
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack s = event.getEntity().getMainHandItem();
        int lvl = level(s);
        if (lvl <= 0 || path(s) == null || !tool(s) || !s.isCorrectToolForDrops(event.getState()) && s.getDestroySpeed(event.getState()) <= 1F) return;
        event.setNewSpeed(event.getNewSpeed() * (1F + DIG * lvl));
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack s = event.getItemStack();
        String p = path(s);
        if (p == null) return;
        int lvl = level(s);
        ChatFormatting c = p.equals("sky") ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE;
        StringBuilder pips = new StringBuilder();
        for (int i = 0; i < MAX; i++) pips.append(i < lvl ? '◆' : '◇');
        int at = Math.min(1, event.getToolTip().size());
        event.getToolTip().add(at, Component.literal("Altar Forged " + pips + (lvl >= MAX ? " (max)" : "")).withStyle(c));
        if (lvl > 0) {
            int i = at + 1;
            if (weapon(s)) event.getToolTip().add(i++, Component.literal(" +" + (int) (DAMAGE * lvl) + " attack damage").withStyle(ChatFormatting.DARK_GRAY));
            if (tool(s)) event.getToolTip().add(i++, Component.literal(" +" + Math.round(DIG * lvl * 100) + "% digging speed").withStyle(ChatFormatting.DARK_GRAY));
            event.getToolTip().add(i, Component.literal(" +" + Math.round(DURABILITY * lvl * 100) + "% durability").withStyle(ChatFormatting.DARK_GRAY));
        } else event.getToolTip().add(at + 1, Component.literal(" Upgrade at a Karma Altar with " + (p.equals("sky") ? "Sky Crystals" : "Corrupted Shards"))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

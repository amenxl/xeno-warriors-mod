package com.xenowarriors.sky;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Sky Armor (from Sky Ore): diamond-strong, and each piece has a gift of the sky -
 * helmet +100 max Karma, chestplate faster flight, leggings Karma back as you walk, boots let you walk on the sky.
 */
public class SkyArmorItem extends ArmorItem {
    public static final ArmorMaterial SKY = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return new int[]{429, 495, 528, 363}[3 - type.ordinal()]; }
        @Override public int getDefenseForType(Type type) {
            return switch (type) { case HELMET -> 3; case CHESTPLATE -> 8; case LEGGINGS -> 6; default -> 3; };
        }
        @Override public int getEnchantmentValue() { return 12; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_DIAMOND; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(com.xenowarriors.item.ModItems.SKY_CRYSTAL.get()); }
        @Override public String getName() { return "xenowarriors:sky"; }
        @Override public float getToughness() { return 2F; }
        @Override public float getKnockbackResistance() { return 0F; }
    };

    public SkyArmorItem(Type type, Properties props) {
        super(SKY, type, props);
    }

    public static boolean wearing(Player p, EquipmentSlot slot) {
        return p.getItemBySlot(slot).getItem() instanceof SkyArmorItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = switch (getType()) {
            case HELMET -> "+100 max Karma";
            case CHESTPLATE -> "Fly faster";
            case LEGGINGS -> "Walking slowly restores Karma";
            default -> "Hold sneak to walk on the sky (up to 200 blocks)";
        };
        tooltip.add(Component.literal(key).withStyle(ChatFormatting.AQUA));
    }
}

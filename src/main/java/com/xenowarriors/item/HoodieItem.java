package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Karma-reducing hoodies (the demon hoodie from the show, and SR's version). While worn, Karma
 * Sensing can't find you - but your own Karma is suppressed, so it recovers half as fast.
 */
public abstract class HoodieItem extends ArmorItem {
    protected HoodieItem(String name, Properties properties) {
        super(material(name), Type.CHESTPLATE, properties);
    }

    static ArmorMaterial material(String name) {
        return new ArmorMaterial() {
            @Override public int getDurabilityForType(Type type) { return 220; }
            @Override public int getDefenseForType(Type type) { return 3; }
            @Override public int getEnchantmentValue() { return 15; }
            @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
            @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.BLACK_WOOL); }
            @Override public String getName() { return "xenowarriors:" + name; }
            @Override public float getToughness() { return 0F; }
            @Override public float getKnockbackResistance() { return 0F; }
        };
    }

    public static boolean wearing(Player player) {
        return player.getInventory().getArmor(2).getItem() instanceof HoodieItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc1").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc2").withStyle(ChatFormatting.GRAY));
    }
}

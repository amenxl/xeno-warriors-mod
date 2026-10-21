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
 * The demon Karma-reducing hoodie from the show. While worn, Karma Sensing can't find you - but it
 * also suppresses your own Karma, so it recovers half as fast.
 */
public class KarmaHoodieItem extends ArmorItem {
    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return 220; }
        @Override public int getDefenseForType(Type type) { return 3; }
        @Override public int getEnchantmentValue() { return 15; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_LEATHER; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.BLACK_WOOL); }
        @Override public String getName() { return "xenowarriors:karma_hoodie"; }
        @Override public float getToughness() { return 0F; }
        @Override public float getKnockbackResistance() { return 0F; }
    };

    public KarmaHoodieItem(Properties properties) {
        super(MATERIAL, Type.CHESTPLATE, properties);
    }

    public static boolean wearing(Player player) {
        return player.getInventory().getArmor(2).getItem() instanceof KarmaHoodieItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc1").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc2").withStyle(ChatFormatting.GRAY));
    }
}

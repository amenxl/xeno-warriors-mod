package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The demon Karma-reducing hoodie from the show. While worn, Karma Sensing can't find you - but it
 * also suppresses your own Karma, so it recovers half as fast. Dye it any color like leather armor
 * (the purple trim, drawstrings and demon emblem keep their colors).
 */
public class KarmaHoodieItem extends ArmorItem implements DyeableLeatherItem {
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

    /** Undyed it's the dark charcoal from the show. */
    public static final int DEFAULT_COLOR = 0x2A2333;

    /** Ready-made colors for the creative menu. */
    public static final int[] PRESETS = {DEFAULT_COLOR, 0xB02E26, 0xF9801D, 0xFED83D, 0x5E7C16, 0x3AB3DA, 0x3C44AA,
            0x8932B8, 0xF38BAA, 0xF9FFFE, 0x9D9D97, 0x835432};

    @Override
    public int getColor(ItemStack stack) {
        var tag = stack.getTagElement("display");
        return tag != null && tag.contains("color", 99) ? tag.getInt("color") : DEFAULT_COLOR;
    }

    public static ItemStack colored(net.minecraft.world.item.Item item, int color) {
        ItemStack stack = new ItemStack(item);
        if (color != DEFAULT_COLOR) ((KarmaHoodieItem) item).setColor(stack, color);
        return stack;
    }

    public static boolean wearing(Player player) {
        return player.getInventory().getArmor(2).getItem() instanceof KarmaHoodieItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc1").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc3").withStyle(ChatFormatting.DARK_GRAY));
    }
}

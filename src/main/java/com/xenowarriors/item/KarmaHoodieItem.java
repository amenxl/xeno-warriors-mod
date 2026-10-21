package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The demon Karma-reducing hoodie from the show. Dye it any color like leather armor - the purple
 * trim, drawstrings and demon emblem keep their colors.
 */
public class KarmaHoodieItem extends HoodieItem implements DyeableLeatherItem {
    /** Undyed it's the dark charcoal from the show. */
    public static final int DEFAULT_COLOR = 0x2A2333;

    /** Ready-made colors for the creative menu. */
    public static final int[] PRESETS = {DEFAULT_COLOR, 0xB02E26, 0xF9801D, 0xFED83D, 0x5E7C16, 0x3AB3DA, 0x3C44AA,
            0x8932B8, 0xF38BAA, 0xF9FFFE, 0x9D9D97, 0x835432};

    public KarmaHoodieItem(Properties properties) {
        super("karma_hoodie", properties);
    }

    @Override
    public int getColor(ItemStack stack) {
        var tag = stack.getTagElement("display");
        return tag != null && tag.contains("color", 99) ? tag.getInt("color") : DEFAULT_COLOR;
    }

    public static ItemStack colored(Item item, int color) {
        ItemStack stack = new ItemStack(item);
        if (color != DEFAULT_COLOR) ((KarmaHoodieItem) item).setColor(stack, color);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.xenowarriors.karma_hoodie.desc3").withStyle(ChatFormatting.DARK_GRAY));
    }
}

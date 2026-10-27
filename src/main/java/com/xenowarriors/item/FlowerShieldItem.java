package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The Flower Shield: a round painted shield with a big flower on it (iron and flowers). */
public class FlowerShieldItem extends ShieldItem {
    public FlowerShieldItem(Properties p) { super(p); }

    @Override
    public boolean isValidRepairItem(ItemStack self, ItemStack with) { return with.is(net.minecraft.world.item.Items.IRON_INGOT); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Goes with the Flower Armor").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}

package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** SR's version of the Karma-reducing hoodie: pitch black, hood up, white eye on the chest. */
public class SrHoodieItem extends HoodieItem {
    public SrHoodieItem(Properties properties) {
        super("sr_hoodie", properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.xenowarriors.sr_hoodie.desc").withStyle(ChatFormatting.WHITE));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}

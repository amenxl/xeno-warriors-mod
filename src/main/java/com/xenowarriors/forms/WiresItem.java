package com.xenowarriors.forms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Rhalix's wires as a weapon (Season 2). Attack swings them round at everything up to 4 blocks in front of him; holding
 * use binds his lock-on target (see com.xenowarriors.rhalix.Wires). No melee damage of its own - the whip does it.
 */
public class WiresItem extends Item implements FormItems.Summoned {
    public WiresItem(Properties p) { super(p); }

    @Override public Form.Style style() { return Form.Style.WIRE; }
    @Override public Form form() { return Form.WIRES; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Attack: whip everything up to 4 blocks away").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal("Hold use on your lock-on target: bind it (drains Karma / Ki)").withStyle(ChatFormatting.LIGHT_PURPLE));
        FormTools.tooltip(stack, tip, Form.Style.WIRE);
    }
}

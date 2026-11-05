package com.xenowarriors.forms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Heku-umbrella (Hekublade level 300): held up over his head it keeps the rain off Mikiko - so the rain can't
 * put out his Netsu - and while he's falling with it open he glides down slowly, drifting the way he faces.
 * The open canopy is drawn over him by the client (FormsRender); the item in his hand is just the icon.
 */
public class UmbrellaItem extends Item implements FormItems.Summoned {
    public UmbrellaItem(Properties p) { super(p); }

    @Override public Form.Style style() { return Form.Style.HEKU; }
    @Override public Form form() { return Form.UMBRELLA; }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Keeps the rain off (and your Netsu burning)").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Glide: fall with it out").withStyle(ChatFormatting.AQUA));
        FormTools.tooltip(stack, tip, Form.Style.HEKU);
    }
}

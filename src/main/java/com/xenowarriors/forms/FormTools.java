package com.xenowarriors.forms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Tools (and Rhalix's wire sword) made from the Hekublade or the wires. Netherite harvest level; they dig faster the
 * higher the weapon level was when they were summoned (+1% a level, tapering past 50 like every stat). Unbreakable.
 */
public final class FormTools {
    public static final String LV = "xw_form_lv";

    private FormTools() {}

    /** Mining speed factor from the weapon level stored on the stack. */
    public static float speed(ItemStack stack) {
        int lv = stack.hasTag() ? stack.getTag().getInt(LV) : 1;
        return 1F + com.xenowarriors.stats.Stat.effective(Math.max(1, lv)) * 0.01F;
    }

    static void tooltip(ItemStack stack, List<Component> tip, Form.Style s) {
        int lv = stack.hasTag() ? stack.getTag().getInt(LV) : 0;
        tip.add(Component.literal((s == Form.Style.HEKU ? "Made from the Hekublade" : "Made from Rhalix's wires")
                + (lv > 0 ? " (level " + lv + ")" : "")).withStyle(s == Form.Style.HEKU ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal("Summoned: can't be dropped or stored. Tap the summon key to put it away.").withStyle(ChatFormatting.GRAY));
    }

    public static class Pickaxe extends PickaxeItem implements FormItems.Summoned {
        private final Form.Style style;
        public Pickaxe(Form.Style s, Item.Properties p) { super(FormItems.TIER, 1, -2.8F, p); style = s; }
        @Override public Form.Style style() { return style; }
        @Override public Form form() { return Form.PICKAXE; }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return super.getDestroySpeed(stack, state) * speed(stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) { tooltip(stack, tip, style); }
        @Override public boolean isFoil(ItemStack stack) { return false; }
    }

    public static class Axe extends AxeItem implements FormItems.Summoned {
        private final Form.Style style;
        public Axe(Form.Style s, Item.Properties p) { super(FormItems.TIER, 5F, -3.0F, p); style = s; }
        @Override public Form.Style style() { return style; }
        @Override public Form form() { return Form.AXE; }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return super.getDestroySpeed(stack, state) * speed(stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) { tooltip(stack, tip, style); }
        @Override public boolean isFoil(ItemStack stack) { return false; }
    }

    public static class Shovel extends ShovelItem implements FormItems.Summoned {
        private final Form.Style style;
        public Shovel(Form.Style s, Item.Properties p) { super(FormItems.TIER, 1.5F, -3.0F, p); style = s; }
        @Override public Form.Style style() { return style; }
        @Override public Form form() { return Form.SHOVEL; }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return super.getDestroySpeed(stack, state) * speed(stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) { tooltip(stack, tip, style); }
        @Override public boolean isFoil(ItemStack stack) { return false; }
    }

    public static class Hoe extends HoeItem implements FormItems.Summoned {
        private final Form.Style style;
        public Hoe(Form.Style s, Item.Properties p) { super(FormItems.TIER, -4, 0F, p); style = s; }
        @Override public Form.Style style() { return style; }
        @Override public Form form() { return Form.HOE; }
        @Override public float getDestroySpeed(ItemStack stack, BlockState state) { return super.getDestroySpeed(stack, state) * speed(stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) { tooltip(stack, tip, style); }
        @Override public boolean isFoil(ItemStack stack) { return false; }
    }

    /** Rhalix's sword of wound wires. */
    public static class Sword extends SwordItem implements FormItems.Summoned {
        private final Form.Style style;
        public Sword(Form.Style s, Item.Properties p) { super(FormItems.TIER, 3, -2.4F, p); style = s; }
        @Override public Form.Style style() { return style; }
        @Override public Form form() { return Form.WIRE_SWORD; }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) { tooltip(stack, tip, style); }
        @Override public boolean isFoil(ItemStack stack) { return false; }
        @Override public boolean hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.LivingEntity attacker) {
            if (attacker instanceof net.minecraft.server.level.ServerPlayer p) com.xenowarriors.rhalix.WireLevel.addXp(p, 1.5F);
            return true;
        }
    }
}

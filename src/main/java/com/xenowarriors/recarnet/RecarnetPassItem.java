package com.xenowarriors.recarnet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Recarnet Pass: hold right-click to travel to Recarnet (the show's world) and back. */
public class RecarnetPassItem extends Item {
    public RecarnetPassItem(Properties p) { super(p); }

    @Override public int getUseDuration(ItemStack s) { return 40; }
    @Override public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack s) { return net.minecraft.world.item.UseAnim.BOW; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity e) {
        if (e instanceof ServerPlayer p) { RecarnetEvents.travel(p); p.getCooldowns().addCooldown(this, 100); }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Hold to travel to Recarnet and back").withStyle(ChatFormatting.GRAY));
    }
}

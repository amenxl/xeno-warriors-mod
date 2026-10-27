package com.xenowarriors.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A 100 Money note. Right-click to put it in your balance (sneak: the whole stack). Villagers trade them for emeralds. */
public class MoneyNoteItem extends Item {
    public MoneyNoteItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (p instanceof ServerPlayer sp) {
            int n = p.isShiftKeyDown() ? s.getCount() : 1;
            Money.add(sp, (long) n * Money.NOTE, true);
            s.shrink(n);
            level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("100 Money").withStyle(ChatFormatting.GREEN));
        tip.add(Component.literal("Use to deposit (sneak: the whole stack)").withStyle(ChatFormatting.GRAY));
    }
}

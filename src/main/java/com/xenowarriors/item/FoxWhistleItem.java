package com.xenowarriors.item;

import com.xenowarriors.fox.FoxPets;
import com.xenowarriors.skill.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Mikiko's whistle: calls his foxes to him, sets down the one on his back, and brings back any that fell. */
public class FoxWhistleItem extends Item {
    public FoxWhistleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1F, 1.6F);
            ServerScheduler.later(4, () -> level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(),
                    SoundSource.PLAYERS, 1F, 2.0F));
            FoxPets.whistle(sp);
        }
        player.getCooldowns().addCooldown(this, 60);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Calls Mikiko's foxes to his side").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Brings back foxes that have fallen").withStyle(ChatFormatting.GRAY));
    }
}

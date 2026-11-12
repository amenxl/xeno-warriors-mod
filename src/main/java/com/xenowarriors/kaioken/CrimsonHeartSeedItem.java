package com.xenowarriors.kaioken;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Crimson Heart Seed: a super rare boss drop. Eaten, it steadies the body for {@link KaiokenGuard#DURATION} ticks -
 * Kaioken costs no health while it lasts (the Crimson Control effect).
 */
public class CrimsonHeartSeedItem extends Item {
    public CrimsonHeartSeedItem(Properties p) { super(p.rarity(Rarity.EPIC)); }

    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.EAT; }
    @Override public int getUseDuration(ItemStack s) { return 20; }
    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        if (!level.isClientSide) {
            KaiokenGuard.give(e);
            if (e instanceof ServerPlayer p) {
                p.displayClientMessage(Component.literal("Your heart beats steady and crimson - Kaioken won't cost you health for "
                        + KaiokenGuard.DURATION / 1200 + " minutes.").withStyle(ChatFormatting.RED), true);
                if (!p.isCreative()) stack.shrink(1);
            }
            level.playSound(null, e.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Eat it: Kaioken costs no health for " + KaiokenGuard.DURATION / 1200 + " minutes.").withStyle(ChatFormatting.RED));
        tip.add(Component.literal("Super rare - only bosses drop it.").withStyle(ChatFormatting.DARK_GRAY));
    }
}

package com.xenowarriors.virus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/** Aurora Medicine: cures the Aurora virus and keeps it away for 20 minutes. Government supply. */
public class AuroraMedicineItem extends Item {
    public AuroraMedicineItem(Properties p) { super(p); }

    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.DRINK; }
    @Override public int getUseDuration(ItemStack s) { return 24; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        if (!level.isClientSide) {
            boolean had = AuroraVirus.has(e);
            AuroraVirus.cure(e, 20 * 60 * 20);
            if (e instanceof ServerPlayer p) {
                p.displayClientMessage(Component.literal(had ? "You can breathe again." : "You won't catch the Aurora virus for a while.").withStyle(ChatFormatting.AQUA), true);
                if (!p.isCreative()) stack.shrink(1);
            }
            e.playSound(SoundEvents.HONEY_DRINK, 1F, 1.2F);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Cures the Aurora virus, 20 min protection").withStyle(ChatFormatting.GRAY));
    }
}

package com.xenowarriors.you;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Sunlight in a bottle: a Seedling's meal for the night - a full Sun charge. Anyone else just gets a warm glow. */
public class SunlightItem extends Item {
    public SunlightItem(Properties p) { super(p); }

    @Override public int getUseDuration(ItemStack s) { return 24; }
    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.DRINK; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity e) {
        if (e instanceof ServerPlayer p) {
            if (YouRaces.is(p, com.xenowarriors.character.Race.SEEDLING)) {
                YouRaces.giveSun(p);
                p.displayClientMessage(Component.literal("Sunlight - fully charged").withStyle(ChatFormatting.YELLOW), true);
            } else {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 200, 0));
                p.displayClientMessage(Component.literal("Warm... Seedlings live on this.").withStyle(ChatFormatting.GOLD), true);
            }
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.4F);
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            if (!p.isCreative()) { s.shrink(1); if (s.isEmpty()) return new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE); p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE)); }
        }
        return s;
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
        tip.add(Component.literal("Bottled daylight. A Seedling's night-time meal.").withStyle(ChatFormatting.GRAY));
    }
}

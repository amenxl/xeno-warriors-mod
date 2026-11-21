package com.xenowarriors.item;

import com.xenowarriors.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A Karma Regeneration Potion: instead of 50 Karma at once (the Karma Potion), Karma keeps coming back - 2 a second
 * for a minute (120 in all). Bots who drink one get the same.
 */
public class KarmaRegenPotionItem extends Item {
    /** How long it lasts (ticks), and Karma per second. */
    public static final int TICKS = 60 * 20;
    public static final float PER_SECOND = 2F;

    public KarmaRegenPotionItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.addEffect(new MobEffectInstance(ModEffects.KARMA_REGEN.get(), TICKS, 0));
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, entity.getSoundSource(), 1.0F, 1.2F);
        }
        if (entity instanceof Player player && !player.getAbilities().instabuild) stack.shrink(1);   // (no empty bottle left over)
        return stack;
    }

    @Override public int getUseDuration(ItemStack stack) { return 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public boolean isFoil(ItemStack stack) { return true; }

    /** The effect's tick (once a second): Karma back for a player, or a bot. */
    public static void regen(LivingEntity e, int amp) {
        float add = PER_SECOND * (amp + 1);
        if (e instanceof ServerPlayer p) com.xenowarriors.karma.Karma.add(p, add);
        else if (e instanceof com.xenowarriors.bot.PartyBotEntity b) b.karma = Math.min(100F, b.karma + add);
        regenTicks++;
    }

    /** (Tests) seconds of Karma regeneration given. */
    public static int regenTicks;
}

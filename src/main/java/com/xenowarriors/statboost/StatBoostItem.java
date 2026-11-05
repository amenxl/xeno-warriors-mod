package com.xenowarriors.statboost;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/**
 * A Stat Boost: right-click to add its levels to one stat of the character you're playing (up to the stat's max).
 * Sneak + right-click uses the whole stack at once. Rare drops from enemies, bosses, digging dirt with a shovel and
 * levelling up (see {@link StatBoosts}); all of them are in the "Xeno Warriors: Stat Boosts" creative tab.
 */
public class StatBoostItem extends Item {
    public final BoostStat stat;
    public final BoostTier tier;

    public StatBoostItem(BoostStat stat, BoostTier tier, Properties props) {
        super(props.rarity(tier.rarity));
        this.stat = stat;
        this.tier = tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatableWithFallback(getDescriptionId(), stat.label + " Boost +" + tier.levels).withStyle(tier.color);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier == BoostTier.HUNDRED;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(stack);
        int uses = player.isShiftKeyDown() ? stack.getCount() : 1;
        int used = 0, gained = 0;
        if (!stat.usable(p)) {
            p.displayClientMessage(Component.literal(stat.whoCanUse()).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        for (int i = 0; i < uses; i++) {
            int g = StatBoosts.apply(p, stat, tier.levels);
            if (g <= 0) break;
            gained += g;
            used++;
        }
        if (used == 0) {
            p.displayClientMessage(Component.literal(stat.label + " is already at its max (Lv " + String.format("%,d", stat.max()) + ").")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!p.getAbilities().instabuild) stack.shrink(used);
        p.getCooldowns().addCooldown(this, 5);
        p.sendSystemMessage(Component.literal("+" + gained + " " + stat.label + "! ").withStyle(tier.color)
                .append(Component.literal(stat.label + " is now Lv " + String.format("%,d", stat.level(p))
                        + (stat.level(p) >= stat.max() ? " (MAX)" : "") + (used > 1 ? "  (" + used + " boosts)" : "")).withStyle(ChatFormatting.WHITE)));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F,
                tier == BoostTier.HUNDRED ? 0.7F : 1.1F + tier.ordinal() * 0.1F);
        if (tier.ordinal() >= BoostTier.TEN.ordinal())
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(tier == BoostTier.HUNDRED ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER,
                    p.getX(), p.getY() + 1, p.getZ(), 12 + tier.ordinal() * 8, 0.4, 0.6, 0.4, 0.1);
            sl.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 6 + tier.ordinal() * 4, 0.3, 0.5, 0.3, 0.05);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("+" + tier.levels + " " + stat.label + " level" + (tier.levels == 1 ? "" : "s")).withStyle(tier.color));
        tip.add(Component.literal("Right-click: use on the character you're playing").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Sneak + right-click: use the whole stack").withStyle(ChatFormatting.DARK_GRAY));
        if (stat == BoostStat.HEKUBLADE) tip.add(Component.literal("Mikiko only").withStyle(ChatFormatting.GOLD));
        if (stat == BoostStat.WIRES) tip.add(Component.literal("Rhalix only (Season 2)").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal(tier.label() + (tier == BoostTier.HUNDRED ? " - only from very strong enemies" : "")).withStyle(ChatFormatting.DARK_PURPLE));
    }
}

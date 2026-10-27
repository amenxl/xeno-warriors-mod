package com.xenowarriors.skill.sp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Max's KA - Vampire Cut (blood manipulation, close range). A cut on whoever's within 6 blocks of his aim: for 12 seconds
 * the wound won't heal and Max drinks their health for as long as it stays open. Press again to close it early.
 * The cost: it pulls on his own blood too - he comes out of it hungry and light-headed, worse the more he uses it.
 */
public final class VampireCut {
    private VampireCut() {}
    private static final int COST = 35;

    public static boolean use(ServerPlayer p) {
        LivingEntity t = SpUtil.front(p, 6);
        if (t == null) { p.displayClientMessage(Component.literal("KA Vampire Cut: get close to someone first").withStyle(ChatFormatting.GRAY), true); return false; }
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, COST)) return false;
        t.hurt(p.damageSources().playerAttack(p), 4F);
        SpState.wound(p, t, 240);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        var tag = p.getPersistentData();
        long now = p.serverLevel().getGameTime();
        if (now - tag.getLong("xw_vamp_last") > 6000) tag.putInt("xw_vamp_strain", 0);
        int strain = tag.getInt("xw_vamp_strain") + 1;
        tag.putInt("xw_vamp_strain", strain); tag.putLong("xw_vamp_last", now);
        p.causeFoodExhaustion(6F * strain);
        if (strain >= 3) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60 * strain, 0));
        Component line = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("KA - Vampire Cut!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 40 * 40) o.displayClientMessage(line, true);
        p.level().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1F, 0.6F);
        p.level().playSound(null, t.blockPosition(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1F, 0.5F);
        return true;
    }
}

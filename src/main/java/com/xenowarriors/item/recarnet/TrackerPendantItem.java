package com.xenowarriors.item.recarnet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * F1 Tracker Pendant (Jacob Matador's crest): invisible, and it only switches on inside Aurora - then you glow and every
 * Government guard nearby knows where you are. Carrying it is a curse; the crafty use it as bait.
 */
public class TrackerPendantItem extends Item {
    public TrackerPendantItem(Properties p) { super(p); }

    @Override
    public void inventoryTick(ItemStack st, Level level, Entity e, int slot, boolean selected) {
        if (level.isClientSide || !(e instanceof ServerPlayer p) || p.tickCount % 60 != 0) return;
        if (!com.xenowarriors.recarnet.Recarnet.is(level)) return;
        double d = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
        if (d > com.xenowarriors.recarnet.Recarnet.CITY_R + 40) return;            // Aurora only
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false));
        for (var g : level.getEntitiesOfClass(com.xenowarriors.gov.GovAgentEntity.class, p.getBoundingBox().inflate(40), a -> a.guard() && a.getTarget() == null)) g.setTarget(p);
        if (p.tickCount % 600 == 0) p.displayClientMessage(Component.literal("The tracker pendant pings...").withStyle(ChatFormatting.RED), true);
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Switches on inside Aurora: guards find you").withStyle(ChatFormatting.RED));
        tip.add(Component.literal("\"A gift from Jacob Matador.\"").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}

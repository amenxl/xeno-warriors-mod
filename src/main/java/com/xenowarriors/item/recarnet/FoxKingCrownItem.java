package com.xenowarriors.item.recarnet;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The Fox King Crown: holds fox power for fox kin only. Worn by Mikiko: Strength, Speed and Night Vision. Anyone else: nothing. */
public class FoxKingCrownItem extends Item implements Equipable {
    public FoxKingCrownItem(Properties p) { super(p); }

    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.HEAD; }

    @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) { return swapWithEquipmentSlot(this, l, p, h); }

    @Override
    public void inventoryTick(ItemStack st, Level level, Entity e, int slot, boolean selected) {
        if (level.isClientSide || !(e instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        if (p.getItemBySlot(EquipmentSlot.HEAD) != st) return;
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) {
            if (p.tickCount % 400 == 0) p.displayClientMessage(Component.literal("The crown doesn't react to you.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, true));
    }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Fox kin only (Mikiko): Strength, Speed, Night Vision while worn").withStyle(ChatFormatting.GOLD));
    }
}

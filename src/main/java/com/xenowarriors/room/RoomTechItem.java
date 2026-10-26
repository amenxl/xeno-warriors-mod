package com.xenowarriors.room;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Room Technology (creative only for now): right-click to open a Room around you, right-click again to leave it. */
public class RoomTechItem extends Item {
    public RoomTechItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        if (!p.isCreative()) { p.displayClientMessage(Component.literal("Room Technology is creative-only for now").withStyle(ChatFormatting.RED), true); return InteractionResultHolder.fail(st); }
        RoomTech.use((ServerPlayer) p);
        p.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.success(st);
    }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Right-click: copy the area into a Room and pull nearby enemies in").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Right-click again (or clear it) to return").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Creative only").withStyle(ChatFormatting.DARK_AQUA));
    }
}

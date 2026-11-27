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

/** Room Technology: right-click to open a Room around you, right-click again to leave it. In survival it costs Karma and has a cooldown. */
public class RoomTechItem extends Item {
    /** 1, 2 or 3: Room Technology I / II / III - each tier opens a bigger Room (a bigger fighting area). */
    public final int tier;
    public RoomTechItem(Properties p) { this(p, 1); }
    public RoomTechItem(Properties p, int tier) { super(p); this.tier = tier; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        RoomTech.use((ServerPlayer) p, tier);                // (survival: Karma, enemies nearby, and a minute between Rooms - leaving is always free)
        p.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.success(st);
    }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Right-click: copy the area into a Room and pull nearby enemies in").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Right-click again (or clear it) to return").withStyle(ChatFormatting.GRAY));
        int[] sz = RoomTech.size(tier);
        tip.add(Component.literal("Room: " + (sz[0] * 2 + 1) + " x " + (sz[0] * 2 + 1) + " blocks, " + (sz[1] + sz[2] + 1) + " tall (pulls in enemies within " + sz[0] + ")").withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("Survival: " + (int) RoomTech.TIER_KARMA[tier - 1] + " Karma, needs enemies nearby, 1 min cooldown").withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.literal("Nothing mined in the Room comes back, and its storage won't open - it's only a copy").withStyle(ChatFormatting.DARK_GRAY));
        if (tier < 3) tip.add(Component.literal("Upgrade: craft into Room Technology " + (tier == 1 ? "II" : "III") + " for a bigger Room").withStyle(ChatFormatting.DARK_GRAY));
    }
}

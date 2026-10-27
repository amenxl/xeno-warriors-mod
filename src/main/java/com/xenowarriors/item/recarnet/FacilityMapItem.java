package com.xenowarriors.item.recarnet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Gale's facility map: in Recarnet, right-click to see which way (and how far) the nearest Government facility in Aurora is. */
public class FacilityMapItem extends Item {
    public FacilityMapItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        if (!com.xenowarriors.recarnet.Recarnet.is(level)) { p.displayClientMessage(Component.literal("This map shows Aurora (Recarnet).").withStyle(ChatFormatting.GRAY), true); return InteractionResultHolder.fail(st); }
        long seed = ((ServerLevel) level).getSeed();
        boolean s3 = com.xenowarriors.character.Seasons.unlocked(((ServerLevel) level).getServer(), 3);
        int cell = com.xenowarriors.recarnet.AuroraCityFeature.CELL, best = -1; double bd = 1e18; int bx = 0, bz = 0;
        for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) {
            if (com.xenowarriors.recarnet.AuroraCityFeature.type(seed, cx, cz, s3) != 4) continue;
            int x = cx * cell + 27, z = cz * cell + 27;
            if (Math.sqrt((double) x * x + (double) z * z) > com.xenowarriors.recarnet.Recarnet.CITY_R - 30) continue;
            double d = p.distanceToSqr(x, p.getY(), z);
            if (d < bd) { bd = d; bx = x; bz = z; best = 1; }
        }
        if (best < 0) { p.displayClientMessage(Component.literal("No facility marked on this map.").withStyle(ChatFormatting.GRAY), true); return InteractionResultHolder.fail(st); }
        double dx = bx - p.getX(), dz = bz - p.getZ();
        String[] dirs = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        int i = (int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0) & 7;
        p.displayClientMessage(Component.literal("Government facility: " + Math.round(Math.sqrt(bd)) + " blocks " + dirs[i] + " (" + bx + ", " + bz + ")").withStyle(ChatFormatting.AQUA), false);
        p.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.success(st);
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Right-click in Aurora: nearest Government facility").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("\"Courtesy of the Gale Empire. Hohoho!\"").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}

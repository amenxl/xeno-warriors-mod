package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A phone. Right-click to take a call: you hold it to your ear and can walk around talking, but can't fight until you
 * right-click again to hang up. Comes in three models (smartphone, flip phone, old brick phone) and lots of colors.
 */
public class PhoneItem extends Item {
    public static final String[] STYLES = {"Smartphone", "Flip Phone", "Brick Phone"};
    public static final int[] COLORS = {0x1E1E24, 0xF2F2F4, 0xB8BCC4, 0xD8323A, 0x2E6FD8, 0xF28AC0, 0x8A3CD8, 0xE6C24A,
            0x3CB45A, 0xF2862E, 0x2AC4C8, 0x6A4A32};
    public static final String[] COLOR_NAMES = {"Black", "White", "Silver", "Red", "Blue", "Pink", "Purple", "Gold",
            "Green", "Orange", "Teal", "Brown"};
    private static final String CALL = "XwOnCall", STYLE = "XwStyle", COLOR = "XwColor";

    public PhoneItem(Properties props) {
        super(props);
    }

    public static ItemStack make(Item item, int style, int color) {
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putInt(STYLE, style);
        s.getOrCreateTag().putInt(COLOR, color);
        return s;
    }

    public static int style(ItemStack s) { return s.hasTag() ? Math.floorMod(s.getTag().getInt(STYLE), STYLES.length) : 0; }
    public static int color(ItemStack s) { return s.hasTag() && s.getTag().contains(COLOR) ? s.getTag().getInt(COLOR) : COLORS[0]; }
    public static boolean onCall(ItemStack s) { return s.getItem() instanceof PhoneItem && s.hasTag() && s.getTag().getBoolean(CALL); }

    /** On a call with either hand. */
    public static boolean onCall(Player p) {
        return onCall(p.getMainHandItem()) || onCall(p.getOffhandItem());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        // on a call: hang up; otherwise open the contacts screen (calls go through the server, see PhoneCalls)
        if (level.isClientSide) {
            if (onCall(player)) com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.PhoneActionPacket(1, new java.util.UUID(0, 0)));
            else net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.xenowarriors.client.PacketScreens.openPhone());
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
        if (!onCall(s) || !(e instanceof Player p)) return;
        // put away (not in either hand any more): the call ends
        if (p.getMainHandItem() != s && p.getOffhandItem() != s) {
            s.getTag().putBoolean(CALL, false);
            return;
        }
        if (!level.isClientSide && p.tickCount % 60 == 0 && p instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.literal("📞 On a call... (right-click to hang up)").withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public Component getName(ItemStack s) {
        int c = color(s), idx = 0;
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == c) idx = i;
        return Component.literal(COLOR_NAMES[idx] + " " + STYLES[style(s)]);
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Right-click: take a call / hang up").withStyle(ChatFormatting.GRAY));
        if (onCall(s)) tip.add(Component.literal("On a call").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}

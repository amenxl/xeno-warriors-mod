package com.xenowarriors.vehicle;

import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Buying vehicles at the dealer, for emeralds. */
public final class Dealer {
    private Dealer() {}

    public static int emeralds(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    /** Where each player last opened a dealer (a purchase has to happen near it). */
    private static final java.util.Map<java.util.UUID, net.minecraft.core.GlobalPos> AT = new java.util.HashMap<>();

    public static void open(ServerPlayer p) {
        AT.put(p.getUUID(), net.minecraft.core.GlobalPos.of(p.level().dimension(), p.blockPosition()));
        ModNetwork.sendToPlayer(new com.xenowarriors.network.OpenDealerPacket(emeralds(p)), p);
    }

    public static void buy(ServerPlayer p, int kindIdx, int variant) {
        if (kindIdx < 0 || kindIdx >= VehicleKind.values().length) return;
        VehicleKind k = VehicleKind.values()[kindIdx];
        var at = AT.get(p.getUUID());
        if (at == null || at.dimension() != p.level().dimension() || !at.pos().closerThan(p.blockPosition(), 10)) return;   // (only at the dealer)
        if (!p.isCreative()) {
            if (emeralds(p) < k.price) {
                p.displayClientMessage(Component.literal("You need " + k.price + " emeralds for a " + k.title.toLowerCase() + ".").withStyle(ChatFormatting.RED), true);
                return;
            }
            int left = k.price;
            for (ItemStack s : p.getInventory().items) {
                if (left <= 0) break;
                if (!s.is(Items.EMERALD)) continue;
                int t = Math.min(left, s.getCount()); s.shrink(t); left -= t;
            }
        }
        ItemStack it = VehicleItem.make(k, variant);
        if (!p.addItem(it)) p.drop(it, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
        p.sendSystemMessage(Component.literal("You bought a " + it.getHoverName().getString() + "! Use it on the ground to park it.").withStyle(ChatFormatting.GREEN));
        open(p);
    }
}

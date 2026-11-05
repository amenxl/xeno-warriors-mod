package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: the weapon-form wheel state - whose style (-1 none, 0 Hekublade, 1 wires), which forms are
 * unlocked (bit per Form ordinal), the weapon level and its progress, and the ticks left before a new gun after one broke.
 */
public record WeaponFormsPacket(int style, int mask, int level, float progress, int gunCooldown) {
    public static void encode(WeaponFormsPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.style + 1);
        b.writeVarInt(m.mask);
        b.writeVarInt(m.level);
        b.writeFloat(m.progress);
        b.writeVarInt(m.gunCooldown);
    }

    public static WeaponFormsPacket decode(FriendlyByteBuf b) {
        return new WeaponFormsPacket(b.readVarInt() - 1, b.readVarInt(), b.readVarInt(), b.readFloat(), b.readVarInt());
    }

    public static void handle(WeaponFormsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.forms.FormsClient.state(m));
    }
}

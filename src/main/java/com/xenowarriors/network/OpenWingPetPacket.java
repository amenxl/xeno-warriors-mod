package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server -> owner: open the menu for one of Rhalix's winged companions. */
public record OpenWingPetPacket(UUID pet, int entityId, String name, int skin, float health, float maxHealth, String kind) {
    public static void encode(OpenWingPetPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.pet);
        buf.writeVarInt(m.entityId);
        buf.writeUtf(m.name);
        buf.writeVarInt(m.skin);
        buf.writeFloat(m.health);
        buf.writeFloat(m.maxHealth);
        buf.writeUtf(m.kind);
    }

    public static OpenWingPetPacket decode(FriendlyByteBuf buf) {
        return new OpenWingPetPacket(buf.readUUID(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readUtf());
    }

    public static void handle(OpenWingPetPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.xenowarriors.client.wing.WingPetScreen(m)));
    }
}

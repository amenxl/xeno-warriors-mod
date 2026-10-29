package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: this character has no DragonMineZ character yet - open DragonMineZ's race picker. */
public record DmzRacePickPacket() {
    public static void encode(DmzRacePickPacket m, FriendlyByteBuf buf) {}
    public static DmzRacePickPacket decode(FriendlyByteBuf buf) { return new DmzRacePickPacket(); }
    public static void handle(DmzRacePickPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.minecraft.client.Minecraft.getInstance().tell(com.xenowarriors.client.compat.DmzMenu::open));
        ctx.get().setPacketHandled(true);
    }
}

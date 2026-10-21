package com.xenowarriors.network;

import com.xenowarriors.client.mikiko.MikikoAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: whether a Mikiko player is poisoned (other players' effects aren't normally sent). */
public record PoisonStatePacket(int entityId, boolean poisoned) {
    public static void encode(PoisonStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.poisoned);
    }

    public static PoisonStatePacket decode(FriendlyByteBuf buf) {
        return new PoisonStatePacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PoisonStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MikikoAnimations.setPoisoned(msg.entityId, msg.poisoned));
    }
}

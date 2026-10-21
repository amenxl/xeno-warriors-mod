package com.xenowarriors.network;

import com.xenowarriors.client.mikiko.MikikoAnimations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: whether a character is poisoned / weakened (other players' effects aren't normally sent). */
public record PoisonStatePacket(int entityId, int flags) {
    public static final int POISON = 1, WEAKNESS = 2;

    public static void encode(PoisonStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.flags);
    }

    public static PoisonStatePacket decode(FriendlyByteBuf buf) {
        return new PoisonStatePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(PoisonStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MikikoAnimations.setPoisoned(msg.entityId, (msg.flags & POISON) != 0);
            com.xenowarriors.client.ClientSkillState.setStatus(msg.entityId, msg.flags);
        });
    }
}

package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: cooldown for a skill slot, for the HUD. */
public record SkillCooldownPacket(int slot, int remainingTicks, int totalTicks) {
    public static void encode(SkillCooldownPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
        buf.writeVarInt(msg.remainingTicks);
        buf.writeVarInt(msg.totalTicks);
    }

    public static SkillCooldownPacket decode(FriendlyByteBuf buf) {
        return new SkillCooldownPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SkillCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientSkillState.setCooldown(msg.slot, msg.remainingTicks, msg.totalTicks));
    }
}

package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: a player just used a skill, so play its body animation. */
public record SkillAnimPacket(int entityId, int anim, boolean airborne) {
    public static final int KISHI = 0;
    public static final int STOMP = 1;
    public static final int COMPRESS = 2;
    public static final int BANG = 3;
    public static final int BARRAGE = 4;
    public static final int SCATTER = 5;
    public static final int KITSUNE = 6;
    public static final int NETSU = 7;
    public static final int BLITZ = 8;
    public static final int BLAZING = 9;
    public static final int TEAM_RHALIX = 10, TEAM_MIKIKO = 11;
    public static final int DANCE_KICK = 12, NETSU_BURST = 13, MANIFEST = 14, EMPATHY = 15, KA_RELEASE = 16;

    public static void encode(SkillAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.anim);
        buf.writeBoolean(msg.airborne);
    }

    public static SkillAnimPacket decode(FriendlyByteBuf buf) {
        return new SkillAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SkillAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientSkillState.startAnim(msg.entityId, msg.anim, msg.airborne));
    }
}

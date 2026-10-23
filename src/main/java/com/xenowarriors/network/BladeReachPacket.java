package com.xenowarriors.network;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: Mikiko's summoned-blade combo slash reaching someone just out of fist range. */
public record BladeReachPacket(int targetId) {
    public static void encode(BladeReachPacket m, FriendlyByteBuf buf) { buf.writeVarInt(m.targetId); }

    public static BladeReachPacket decode(FriendlyByteBuf buf) { return new BladeReachPacket(buf.readVarInt()); }

    public static void handle(BladeReachPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.isSpectator() || ModCapabilities.getType(p) != CharacterType.MIKIKO || !p.getMainHandItem().isEmpty()) return;
        if (!(p.level().getEntity(m.targetId) instanceof LivingEntity t) || !t.isAlive() || t == p) return;
        if (p.distanceTo(t) > 7.5 || !p.hasLineOfSight(t)) return;
        p.attack(t);
    }
}

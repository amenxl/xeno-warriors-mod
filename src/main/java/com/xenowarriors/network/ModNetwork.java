package com.xenowarriors.network;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            XenoWarriors.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SelectCharacterPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectCharacterPacket::encode)
                .decoder(SelectCharacterPacket::decode)
                .consumerMainThread(SelectCharacterPacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncCharacterPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncCharacterPacket::encode)
                .decoder(SyncCharacterPacket::decode)
                .consumerMainThread(SyncCharacterPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenSelectScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenSelectScreenPacket::encode)
                .decoder(OpenSelectScreenPacket::decode)
                .consumerMainThread(OpenSelectScreenPacket::handle)
                .add();
        CHANNEL.messageBuilder(UseSkillPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseSkillPacket::encode)
                .decoder(UseSkillPacket::decode)
                .consumerMainThread(UseSkillPacket::handle)
                .add();
        CHANNEL.messageBuilder(SkillCooldownPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillCooldownPacket::encode)
                .decoder(SkillCooldownPacket::decode)
                .consumerMainThread(SkillCooldownPacket::handle)
                .add();
        CHANNEL.messageBuilder(KishiCastPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KishiCastPacket::encode)
                .decoder(KishiCastPacket::decode)
                .consumerMainThread(KishiCastPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToTrackingAndSelf(Object msg, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), msg);
    }
}

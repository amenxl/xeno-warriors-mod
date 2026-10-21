package com.xenowarriors.network;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "8";
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
        CHANNEL.messageBuilder(SkillAnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillAnimPacket::encode)
                .decoder(SkillAnimPacket::decode)
                .consumerMainThread(SkillAnimPacket::handle)
                .add();
        CHANNEL.messageBuilder(KarmaSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KarmaSyncPacket::encode)
                .decoder(KarmaSyncPacket::decode)
                .consumerMainThread(KarmaSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(SummonBladePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SummonBladePacket::encode)
                .decoder(SummonBladePacket::decode)
                .consumerMainThread(SummonBladePacket::handle)
                .add();
        CHANNEL.messageBuilder(PoisonStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PoisonStatePacket::encode)
                .decoder(PoisonStatePacket::decode)
                .consumerMainThread(PoisonStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(DoubleJumpPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DoubleJumpPacket::encode)
                .decoder(DoubleJumpPacket::decode)
                .consumerMainThread(DoubleJumpPacket::handle)
                .add();
        CHANNEL.messageBuilder(DoubleJumpFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DoubleJumpFxPacket::encode)
                .decoder(DoubleJumpFxPacket::decode)
                .consumerMainThread(DoubleJumpFxPacket::handle)
                .add();
        CHANNEL.messageBuilder(StatLevelPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StatLevelPacket::encode)
                .decoder(StatLevelPacket::decode)
                .consumerMainThread(StatLevelPacket::handle)
                .add();
        CHANNEL.messageBuilder(KarmaSensePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KarmaSensePacket::encode)
                .decoder(KarmaSensePacket::decode)
                .consumerMainThread(KarmaSensePacket::handle)
                .add();
        CHANNEL.messageBuilder(KarmaSensedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KarmaSensedPacket::encode)
                .decoder(KarmaSensedPacket::decode)
                .consumerMainThread(KarmaSensedPacket::handle)
                .add();
        CHANNEL.messageBuilder(HekubladeFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HekubladeFxPacket::encode)
                .decoder(HekubladeFxPacket::decode)
                .consumerMainThread(HekubladeFxPacket::handle)
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

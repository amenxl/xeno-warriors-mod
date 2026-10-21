package com.xenowarriors.network;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "18";
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
        CHANNEL.messageBuilder(NetsuPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NetsuPacket::encode)
                .decoder(NetsuPacket::decode)
                .consumerMainThread(NetsuPacket::handle)
                .add();
        CHANNEL.messageBuilder(TrackTargetPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TrackTargetPacket::encode)
                .decoder(TrackTargetPacket::decode)
                .consumerMainThread(TrackTargetPacket::handle)
                .add();
        CHANNEL.messageBuilder(FlightSkillPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlightSkillPacket::encode)
                .decoder(FlightSkillPacket::decode)
                .consumerMainThread(FlightSkillPacket::handle)
                .add();
        CHANNEL.messageBuilder(FoxSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FoxSyncPacket::encode)
                .decoder(FoxSyncPacket::decode)
                .consumerMainThread(FoxSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(RenameFoxPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RenameFoxPacket::encode)
                .decoder(RenameFoxPacket::decode)
                .consumerMainThread(RenameFoxPacket::handle)
                .add();
        CHANNEL.messageBuilder(BackFoxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BackFoxPacket::encode)
                .decoder(BackFoxPacket::decode)
                .consumerMainThread(BackFoxPacket::handle)
                .add();
        CHANNEL.messageBuilder(FoxLevelPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FoxLevelPacket::encode)
                .decoder(FoxLevelPacket::decode)
                .consumerMainThread(FoxLevelPacket::handle)
                .add();
        CHANNEL.messageBuilder(AbsorbPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbsorbPacket::encode)
                .decoder(AbsorbPacket::decode)
                .consumerMainThread(AbsorbPacket::handle)
                .add();
        CHANNEL.messageBuilder(FoxStatsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FoxStatsPacket::encode)
                .decoder(FoxStatsPacket::decode)
                .consumerMainThread(FoxStatsPacket::handle)
                .add();
        CHANNEL.messageBuilder(FoxSkinPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FoxSkinPacket::encode)
                .decoder(FoxSkinPacket::decode)
                .consumerMainThread(FoxSkinPacket::handle)
                .add();
        CHANNEL.messageBuilder(FoxFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FoxFxPacket::encode)
                .decoder(FoxFxPacket::decode)
                .consumerMainThread(FoxFxPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetFoxSkinPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetFoxSkinPacket::encode)
                .decoder(SetFoxSkinPacket::decode)
                .consumerMainThread(SetFoxSkinPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenFoxBagPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenFoxBagPacket::encode)
                .decoder(OpenFoxBagPacket::decode)
                .consumerMainThread(OpenFoxBagPacket::handle)
                .add();
        CHANNEL.messageBuilder(ToggleWingsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleWingsPacket::encode)
                .decoder(ToggleWingsPacket::decode)
                .consumerMainThread(ToggleWingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(WingsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WingsPacket::encode)
                .decoder(WingsPacket::decode)
                .consumerMainThread(WingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(TornPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TornPacket::encode)
                .decoder(TornPacket::decode)
                .consumerMainThread(TornPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenNamePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenNamePacket::encode)
                .decoder(OpenNamePacket::decode)
                .consumerMainThread(OpenNamePacket::handle)
                .add();
        CHANNEL.messageBuilder(PartySyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PartySyncPacket::encode)
                .decoder(PartySyncPacket::decode)
                .consumerMainThread(PartySyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(KnockoutPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KnockoutPacket::encode)
                .decoder(KnockoutPacket::decode)
                .consumerMainThread(KnockoutPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlayerActionPacket::encode)
                .decoder(PlayerActionPacket::decode)
                .consumerMainThread(PlayerActionPacket::handle)
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

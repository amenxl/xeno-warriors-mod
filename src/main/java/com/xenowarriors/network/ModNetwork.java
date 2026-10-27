package com.xenowarriors.network;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "53";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            XenoWarriors.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SkillLoadoutPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkillLoadoutPacket::encode).decoder(SkillLoadoutPacket::decode).consumerMainThread(SkillLoadoutPacket::handle).add();
        CHANNEL.messageBuilder(SetSkillSlotPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSkillSlotPacket::encode).decoder(SetSkillSlotPacket::decode).consumerMainThread(SetSkillSlotPacket::handle).add();
        CHANNEL.messageBuilder(KaEffectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KaEffectPacket::encode).decoder(KaEffectPacket::decode).consumerMainThread(KaEffectPacket::handle).add();
        CHANNEL.messageBuilder(ClashStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClashStatePacket::encode).decoder(ClashStatePacket::decode).consumerMainThread(ClashStatePacket::handle).add();
        CHANNEL.messageBuilder(ClashPushPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClashPushPacket::encode).decoder(ClashPushPacket::decode).consumerMainThread(ClashPushPacket::handle).add();
        CHANNEL.messageBuilder(EntityPosePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EntityPosePacket::encode).decoder(EntityPosePacket::decode).consumerMainThread(EntityPosePacket::handle).add();
        CHANNEL.messageBuilder(EnemyLevelPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EnemyLevelPacket::encode).decoder(EnemyLevelPacket::decode).consumerMainThread(EnemyLevelPacket::handle).add();
        CHANNEL.messageBuilder(OpenBotPickPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenBotPickPacket::encode).decoder(OpenBotPickPacket::decode).consumerMainThread(OpenBotPickPacket::handle).add();
        CHANNEL.messageBuilder(BotPickPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BotPickPacket::encode).decoder(BotPickPacket::decode).consumerMainThread(BotPickPacket::handle).add();
        CHANNEL.messageBuilder(OpenBotMenuPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenBotMenuPacket::encode).decoder(OpenBotMenuPacket::decode).consumerMainThread(OpenBotMenuPacket::handle).add();
        CHANNEL.messageBuilder(BotActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BotActionPacket::encode).decoder(BotActionPacket::decode).consumerMainThread(BotActionPacket::handle).add();
        CHANNEL.messageBuilder(OpenPartyMenuPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenPartyMenuPacket::encode).decoder(OpenPartyMenuPacket::decode).consumerMainThread(OpenPartyMenuPacket::handle).add();
        CHANNEL.messageBuilder(CollectionActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CollectionActionPacket::encode).decoder(CollectionActionPacket::decode).consumerMainThread(CollectionActionPacket::handle).add();
        CHANNEL.messageBuilder(BountyPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BountyPacket::encode).decoder(BountyPacket::decode).consumerMainThread(BountyPacket::handle).add();
        CHANNEL.messageBuilder(BountyClaimPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BountyClaimPacket::encode).decoder(BountyClaimPacket::decode).consumerMainThread(BountyClaimPacket::handle).add();
        CHANNEL.messageBuilder(OpenVrPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenVrPacket::encode).decoder(OpenVrPacket::decode).consumerMainThread(OpenVrPacket::handle).add();
        CHANNEL.messageBuilder(VrActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VrActionPacket::encode).decoder(VrActionPacket::decode).consumerMainThread(VrActionPacket::handle).add();
        CHANNEL.messageBuilder(PetBarsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PetBarsPacket::encode).decoder(PetBarsPacket::decode).consumerMainThread(PetBarsPacket::handle).add();
        CHANNEL.messageBuilder(DemonStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DemonStatePacket::encode).decoder(DemonStatePacket::decode).consumerMainThread(DemonStatePacket::handle).add();
        CHANNEL.messageBuilder(CollectionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CollectionPacket::encode).decoder(CollectionPacket::decode).consumerMainThread(CollectionPacket::handle).add();
        CHANNEL.messageBuilder(FxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FxPacket::encode).decoder(FxPacket::decode).consumerMainThread(FxPacket::handle).add();
        CHANNEL.messageBuilder(CharLevelsPacket.class, id++)
                .encoder(CharLevelsPacket::encode).decoder(CharLevelsPacket::decode).consumerMainThread(CharLevelsPacket::handle).add();
        CHANNEL.messageBuilder(ChangeFormPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChangeFormPacket::encode).decoder(ChangeFormPacket::decode).consumerMainThread(ChangeFormPacket::handle).add();
        CHANNEL.messageBuilder(OpenDealerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDealerPacket::encode).decoder(OpenDealerPacket::decode).consumerMainThread(OpenDealerPacket::handle).add();
        CHANNEL.messageBuilder(DealerBuyPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DealerBuyPacket::encode).decoder(DealerBuyPacket::decode).consumerMainThread(DealerBuyPacket::handle).add();
        CHANNEL.messageBuilder(DataSensedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DataSensedPacket::encode).decoder(DataSensedPacket::decode).consumerMainThread(DataSensedPacket::handle).add();
        CHANNEL.messageBuilder(BigHungerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BigHungerPacket::encode).decoder(BigHungerPacket::decode).consumerMainThread(BigHungerPacket::handle).add();
        CHANNEL.messageBuilder(LunarStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LunarStatePacket::encode).decoder(LunarStatePacket::decode).consumerMainThread(LunarStatePacket::handle).add();
        CHANNEL.messageBuilder(UltimaOutputPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UltimaOutputPacket::encode).decoder(UltimaOutputPacket::decode).consumerMainThread(UltimaOutputPacket::handle).add();
        CHANNEL.messageBuilder(PhoneActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PhoneActionPacket::encode)
                .decoder(PhoneActionPacket::decode)
                .consumerMainThread(PhoneActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(RequestPopupPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RequestPopupPacket::encode)
                .decoder(RequestPopupPacket::decode)
                .consumerMainThread(RequestPopupPacket::handle)
                .add();
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
        CHANNEL.messageBuilder(RhalixHungerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RhalixHungerPacket::encode)
                .decoder(RhalixHungerPacket::decode)
                .consumerMainThread(RhalixHungerPacket::handle)
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
        CHANNEL.messageBuilder(FlightHeightPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlightHeightPacket::encode)
                .decoder(FlightHeightPacket::decode)
                .consumerMainThread(FlightHeightPacket::handle)
                .add();
        CHANNEL.messageBuilder(CharacterLevelPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CharacterLevelPacket::encode)
                .decoder(CharacterLevelPacket::decode)
                .consumerMainThread(CharacterLevelPacket::handle)
                .add();
        CHANNEL.messageBuilder(CorruptionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CorruptionPacket::encode)
                .decoder(CorruptionPacket::decode)
                .consumerMainThread(CorruptionPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenWingPetPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenWingPetPacket::encode)
                .decoder(OpenWingPetPacket::decode)
                .consumerMainThread(OpenWingPetPacket::handle)
                .add();
        CHANNEL.messageBuilder(WingPetActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WingPetActionPacket::encode)
                .decoder(WingPetActionPacket::decode)
                .consumerMainThread(WingPetActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(WingSkinPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WingSkinPacket::encode)
                .decoder(WingSkinPacket::decode)
                .consumerMainThread(WingSkinPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerStatsRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlayerStatsRequestPacket::encode)
                .decoder(PlayerStatsRequestPacket::decode)
                .consumerMainThread(PlayerStatsRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(PlayerStatsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerStatsPacket::encode)
                .decoder(PlayerStatsPacket::decode)
                .consumerMainThread(PlayerStatsPacket::handle)
                .add();
        CHANNEL.messageBuilder(BladeSwingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BladeSwingPacket::encode)
                .decoder(BladeSwingPacket::decode)
                .consumerMainThread(BladeSwingPacket::handle)
                .add();
        CHANNEL.messageBuilder(MiningLevelPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MiningLevelPacket::encode)
                .decoder(MiningLevelPacket::decode)
                .consumerMainThread(MiningLevelPacket::handle)
                .add();
        CHANNEL.messageBuilder(FoxKingPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FoxKingPacket::encode)
                .decoder(FoxKingPacket::decode)
                .consumerMainThread(FoxKingPacket::handle)
                .add();
        CHANNEL.messageBuilder(FoxKingTogglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FoxKingTogglePacket::encode)
                .decoder(FoxKingTogglePacket::decode)
                .consumerMainThread(FoxKingTogglePacket::handle)
                .add();
        CHANNEL.messageBuilder(SetOutfitPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetOutfitPacket::encode)
                .decoder(SetOutfitPacket::decode)
                .consumerMainThread(SetOutfitPacket::handle)
                .add();
        CHANNEL.messageBuilder(PoleGustPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PoleGustPacket::encode)
                .decoder(PoleGustPacket::decode)
                .consumerMainThread(PoleGustPacket::handle)
                .add();
        CHANNEL.messageBuilder(BladeChannelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BladeChannelPacket::encode)
                .decoder(BladeChannelPacket::decode)
                .consumerMainThread(BladeChannelPacket::handle)
                .add();
        CHANNEL.messageBuilder(BladeChannelStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BladeChannelStatePacket::encode)
                .decoder(BladeChannelStatePacket::decode)
                .consumerMainThread(BladeChannelStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(PetOwnerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PetOwnerPacket::encode)
                .decoder(PetOwnerPacket::decode)
                .consumerMainThread(PetOwnerPacket::handle)
                .add();
        CHANNEL.messageBuilder(DarkJumpPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DarkJumpPacket::encode)
                .decoder(DarkJumpPacket::decode)
                .consumerMainThread(DarkJumpPacket::handle)
                .add();
        CHANNEL.messageBuilder(SkyOreSensedPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkyOreSensedPacket::encode)
                .decoder(SkyOreSensedPacket::decode)
                .consumerMainThread(SkyOreSensedPacket::handle)
                .add();
        CHANNEL.messageBuilder(PullArrowPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PullArrowPacket::encode)
                .decoder(PullArrowPacket::decode)
                .consumerMainThread(PullArrowPacket::handle)
                .add();
        CHANNEL.messageBuilder(KarmaFocusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(KarmaFocusPacket::encode)
                .decoder(KarmaFocusPacket::decode)
                .consumerMainThread(KarmaFocusPacket::handle)
                .add();
        CHANNEL.messageBuilder(AerialComboPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AerialComboPacket::encode)
                .decoder(AerialComboPacket::decode)
                .consumerMainThread(AerialComboPacket::handle)
                .add();
        CHANNEL.messageBuilder(BladeReachPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BladeReachPacket::encode)
                .decoder(BladeReachPacket::decode)
                .consumerMainThread(BladeReachPacket::handle)
                .add();
        CHANNEL.messageBuilder(RealmKeyPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RealmKeyPacket::encode)
                .decoder(RealmKeyPacket::decode)
                .consumerMainThread(RealmKeyPacket::handle)
                .add();
        CHANNEL.messageBuilder(HoodTogglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HoodTogglePacket::encode)
                .decoder(HoodTogglePacket::decode)
                .consumerMainThread(HoodTogglePacket::handle)
                .add();
        CHANNEL.messageBuilder(HoodStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HoodStatePacket::encode)
                .decoder(HoodStatePacket::decode)
                .consumerMainThread(HoodStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(RopeThrowPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RopeThrowPacket::encode)
                .decoder(RopeThrowPacket::decode)
                .consumerMainThread(RopeThrowPacket::handle)
                .add();
        CHANNEL.messageBuilder(RopeLashPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RopeLashPacket::encode)
                .decoder(RopeLashPacket::decode)
                .consumerMainThread(RopeLashPacket::handle)
                .add();
        CHANNEL.messageBuilder(RopeFxPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RopeFxPacket::encode)
                .decoder(RopeFxPacket::decode)
                .consumerMainThread(RopeFxPacket::handle)
                .add();
        CHANNEL.messageBuilder(AuraMarkPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AuraMarkPacket::encode).decoder(AuraMarkPacket::decode).consumerMainThread(AuraMarkPacket::handle).add();
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

    public static void sendToTrackingEntityAndSelf(Object msg, net.minecraft.world.entity.Entity e) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg);
    }
}

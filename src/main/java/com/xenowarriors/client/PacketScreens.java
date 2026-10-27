package com.xenowarriors.client;

import net.minecraft.client.Minecraft;

/** Client-only: screens opened by packets (kept out of the packet classes so dedicated servers never load Screen). */
public final class PacketScreens {
    private PacketScreens() {}

    public static void openName(java.util.UUID pet, String name, boolean justTamed) {
        Minecraft.getInstance().setScreen(new com.xenowarriors.client.fox.FoxNameScreen(pet, name, justTamed, "companion"));
    }

    /** Latest Collection info (the menu shows the Collection once it's unlocked). */
    public static boolean collectionUnlocked;
    public static int towerFloor;

    public static void openCollection(com.xenowarriors.network.CollectionPacket m) {
        collectionUnlocked = m.unlocked();
        towerFloor = m.floor();
        if (m.show()) Minecraft.getInstance().setScreen(new com.xenowarriors.client.screen.CollectionScreen(m));
    }

    public static void openBotPick(com.xenowarriors.network.OpenBotPickPacket m) { Minecraft.getInstance().setScreen(new com.xenowarriors.client.bot.BotPickScreen(m)); }

    public static void openBotMenu(com.xenowarriors.network.OpenBotMenuPacket m) { Minecraft.getInstance().setScreen(new com.xenowarriors.client.bot.BotScreen(m)); }

    public static void openPartyMenu() { Minecraft.getInstance().setScreen(new com.xenowarriors.client.bot.PartyScreen()); }

    public static void openBounties(com.xenowarriors.network.BountyPacket m) { Minecraft.getInstance().setScreen(new com.xenowarriors.client.screen.BountyScreen(m)); }

    public static void openVr(com.xenowarriors.network.OpenVrPacket m) { Minecraft.getInstance().setScreen(new com.xenowarriors.client.vr.VrScreen(m)); }

    public static void openPhone() { Minecraft.getInstance().setScreen(new com.xenowarriors.client.PhoneScreen()); }

    public static void openWingPet(com.xenowarriors.network.OpenWingPetPacket m) {
        Minecraft.getInstance().setScreen(new com.xenowarriors.client.wing.WingPetScreen(m));
    }
}

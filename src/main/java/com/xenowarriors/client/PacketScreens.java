package com.xenowarriors.client;

import net.minecraft.client.Minecraft;

/** Client-only: screens opened by packets (kept out of the packet classes so dedicated servers never load Screen). */
public final class PacketScreens {
    private PacketScreens() {}

    public static void openName(java.util.UUID pet, String name, boolean justTamed) {
        Minecraft.getInstance().setScreen(new com.xenowarriors.client.fox.FoxNameScreen(pet, name, justTamed, "companion"));
    }

    public static void openWingPet(com.xenowarriors.network.OpenWingPetPacket m) {
        Minecraft.getInstance().setScreen(new com.xenowarriors.client.wing.WingPetScreen(m));
    }
}

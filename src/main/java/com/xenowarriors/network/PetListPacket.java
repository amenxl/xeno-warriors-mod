package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The Pets menu's list. Client -> server with an empty list asks for it; server -> client answers. */
public record PetListPacket(List<Pet> pets) {
    /** kind 0 fox, 1 winged companion; status: 0 with you, 1 on your back, 2 recovering, 3 ready to whistle, 4 elsewhere. */
    public record Pet(int kind, int index, String name, String species, int level, float xp, int xpNeed, int status, int awayLeft,
                      float health, float maxHealth, int entityId, int skin, List<String> skins, List<Integer> skinLevels, int kills) {}

    public static void encode(PetListPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.pets.size());
        for (Pet p : m.pets) {
            b.writeVarInt(p.kind); b.writeVarInt(p.index); b.writeUtf(p.name); b.writeUtf(p.species); b.writeVarInt(p.level); b.writeFloat(p.xp);
            b.writeVarInt(p.xpNeed); b.writeVarInt(p.status); b.writeVarInt(p.awayLeft); b.writeFloat(p.health); b.writeFloat(p.maxHealth);
            b.writeVarInt(p.entityId); b.writeVarInt(p.skin);
            b.writeVarInt(p.skins.size()); for (int i = 0; i < p.skins.size(); i++) { b.writeUtf(p.skins.get(i)); b.writeVarInt(p.skinLevels.get(i)); }
            b.writeVarInt(p.kills);
        }
    }

    public static PetListPacket decode(FriendlyByteBuf b) {
        int n = b.readVarInt();
        List<Pet> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int kind = b.readVarInt(), index = b.readVarInt(); String name = b.readUtf(), species = b.readUtf(); int level = b.readVarInt(); float xp = b.readFloat();
            int need = b.readVarInt(), status = b.readVarInt(), away = b.readVarInt(); float hp = b.readFloat(), max = b.readFloat();
            int eid = b.readVarInt(), skin = b.readVarInt();
            int sn = b.readVarInt(); List<String> skins = new ArrayList<>(sn); List<Integer> lv = new ArrayList<>(sn);
            for (int k = 0; k < sn; k++) { skins.add(b.readUtf()); lv.add(b.readVarInt()); }
            int kills = b.readVarInt();
            l.add(new Pet(kind, index, name, species, level, xp, need, status, away, hp, max, eid, skin, skins, lv, kills));
        }
        return new PetListPacket(l);
    }

    public static void handle(PetListPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) { ModNetwork.sendToPlayer(com.xenowarriors.pet.PetCare.list(sender), sender); return; }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.menu.PetsScreen.receive(m));
    }
}

package com.xenowarriors.network;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.party.Parties;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The party setup screen's cards: everyone in your party (or just you) and the bots, with their level and pets.
 * Client -> server with an empty list asks for it; server -> client answers.
 */
public record PartyCardsPacket(List<Card> cards, boolean inParty, boolean leader) {
    public record Card(UUID id, int entityId, String name, String owner, int type, int outfit, int level, boolean bot,
                       boolean leader, boolean mine, List<String> pets) {}

    public static void encode(PartyCardsPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.inParty); b.writeBoolean(m.leader);
        b.writeVarInt(m.cards.size());
        for (Card c : m.cards) {
            b.writeUUID(c.id); b.writeVarInt(c.entityId); b.writeUtf(c.name); b.writeUtf(c.owner); b.writeVarInt(c.type); b.writeVarInt(c.outfit);
            b.writeVarInt(c.level); b.writeBoolean(c.bot); b.writeBoolean(c.leader); b.writeBoolean(c.mine);
            b.writeVarInt(c.pets.size()); for (String s : c.pets) b.writeUtf(s);
        }
    }

    public static PartyCardsPacket decode(FriendlyByteBuf b) {
        boolean in = b.readBoolean(), lead = b.readBoolean();
        int n = b.readVarInt();
        List<Card> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = b.readUUID(); int eid = b.readVarInt(); String name = b.readUtf(), owner = b.readUtf(); int type = b.readVarInt(), outfit = b.readVarInt(), level = b.readVarInt();
            boolean bot = b.readBoolean(), leader = b.readBoolean(), mine = b.readBoolean();
            int pn = b.readVarInt(); List<String> pets = new ArrayList<>(pn); for (int k = 0; k < pn; k++) pets.add(b.readUtf());
            l.add(new Card(id, eid, name, owner, type, outfit, level, bot, leader, mine, pets));
        }
        return new PartyCardsPacket(l, in, lead);
    }

    public static void handle(PartyCardsPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sender = ctx.get().getSender();
        if (sender != null) { ModNetwork.sendToPlayer(build(sender), sender); return; }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.party.PartySetupScreen.receive(m));
    }

    private static List<String> pets(ServerPlayer p) {
        List<String> l = new ArrayList<>();
        for (String s : com.xenowarriors.fox.FoxPets.summary(p)) l.add("fox:" + s);
        for (String s : com.xenowarriors.wing.WingPets.summary(p)) l.add("wing:" + s);
        return l;
    }

    public static PartyCardsPacket build(ServerPlayer me) {
        List<ServerPlayer> people = Parties.inParty(me) ? Parties.members(me) : List.of(me);
        List<Card> cards = new ArrayList<>();
        boolean amLeader = !Parties.inParty(me) || Parties.isLeader(me);
        for (ServerPlayer p : people) {
            boolean lead = Parties.inParty(me) ? Parties.isLeader(p) : p == me;
            cards.add(new Card(p.getUUID(), p.getId(), p.getName().getString(), "", ModCapabilities.getType(p).ordinal(),
                    com.xenowarriors.character.CharacterManager.outfit(p), com.xenowarriors.stats.Stats.characterLevel(p), false, lead, p == me, pets(p)));
        }
        for (ServerPlayer p : people)
            for (PartyBotEntity b : PartyBots.bots(p))
                cards.add(new Card(b.getUUID(), b.getId(), b.character().getDisplayName().getString(), p.getName().getString(), b.character().ordinal(),
                        0, b.botLevel(), true, false, p == me, List.of()));
        return new PartyCardsPacket(cards, Parties.inParty(me), amLeader);
    }
}

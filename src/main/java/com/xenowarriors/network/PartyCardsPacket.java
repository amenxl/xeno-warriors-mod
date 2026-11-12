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
public record PartyCardsPacket(List<Card> cards, boolean inParty, boolean leader, List<Syn> synergies, int bondHearts, int bestBond) {
    /** {@code bond}: your bond with this member's character (-1 for yourself); {@code bondXp} 0..1 toward the next. */
    public record Card(UUID id, int entityId, String name, String owner, int type, int outfit, int level, boolean bot,
                       boolean leader, boolean mine, List<String> pets, int bond, float bondXp) {}

    /**
     * A synergy for the party menu. state: 2 = active now, 1 = your character is part of it but the partner isn't here,
     * 0 = not one for your character. bond: the bond behind it (for an active one).
     */
    public record Syn(String name, String members, String effect, String downside, int state, int bond, String needs) {}

    public PartyCardsPacket(List<Card> cards, boolean inParty, boolean leader) { this(cards, inParty, leader, List.of(), 0, 0); }

    public static void encode(PartyCardsPacket m, FriendlyByteBuf b) {
        b.writeBoolean(m.inParty); b.writeBoolean(m.leader);
        b.writeVarInt(m.cards.size());
        for (Card c : m.cards) {
            b.writeUUID(c.id); b.writeVarInt(c.entityId); b.writeUtf(c.name); b.writeUtf(c.owner); b.writeVarInt(c.type); b.writeVarInt(c.outfit);
            b.writeVarInt(c.level); b.writeBoolean(c.bot); b.writeBoolean(c.leader); b.writeBoolean(c.mine);
            b.writeVarInt(c.pets.size()); for (String s : c.pets) b.writeUtf(s);
            b.writeVarInt(c.bond + 1); b.writeFloat(c.bondXp);
        }
        b.writeVarInt(m.synergies.size());
        for (Syn y : m.synergies) { b.writeUtf(y.name); b.writeUtf(y.members); b.writeUtf(y.effect); b.writeUtf(y.downside); b.writeVarInt(y.state); b.writeVarInt(y.bond); b.writeUtf(y.needs); }
        b.writeVarInt(m.bondHearts); b.writeVarInt(m.bestBond);
    }

    public static PartyCardsPacket decode(FriendlyByteBuf b) {
        boolean in = b.readBoolean(), lead = b.readBoolean();
        int n = b.readVarInt();
        List<Card> l = new ArrayList<>(Math.min(n, 64));            // (n is from the wire: a request from a client could claim billions)
        for (int i = 0; i < n; i++) {
            UUID id = b.readUUID(); int eid = b.readVarInt(); String name = b.readUtf(), owner = b.readUtf(); int type = b.readVarInt(), outfit = b.readVarInt(), level = b.readVarInt();
            boolean bot = b.readBoolean(), leader = b.readBoolean(), mine = b.readBoolean();
            int pn = b.readVarInt(); List<String> pets = new ArrayList<>(Math.min(pn, 64)); for (int k = 0; k < pn; k++) pets.add(b.readUtf());
            int bond = b.readVarInt() - 1; float bx = b.readFloat();
            l.add(new Card(id, eid, name, owner, type, outfit, level, bot, leader, mine, pets, bond, bx));
        }
        int sn = b.readVarInt();
        List<Syn> syn = new ArrayList<>(Math.min(sn, 64));
        for (int i = 0; i < sn; i++) syn.add(new Syn(b.readUtf(), b.readUtf(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readUtf()));
        return new PartyCardsPacket(l, in, lead, syn, b.readVarInt(), b.readVarInt());
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
            String pc = ModCapabilities.getType(p).getId();
            cards.add(new Card(p.getUUID(), p.getId(), p.getName().getString(), "", ModCapabilities.getType(p).ordinal(),
                    com.xenowarriors.character.CharacterManager.outfit(p), com.xenowarriors.stats.Stats.characterLevel(p), false, lead, p == me, pets(p),
                    p == me ? -1 : com.xenowarriors.party.Bonds.level(me, pc), p == me ? 0F : com.xenowarriors.party.Bonds.progress(me, pc)));
        }
        for (ServerPlayer p : people)
            for (PartyBotEntity b : PartyBots.bots(p))
                cards.add(new Card(b.getUUID(), b.getId(), b.ownCharacter() && b.you() != null ? b.you().name : b.character().getDisplayName().getString(), p.getName().getString(), b.character().ordinal(),
                        0, b.botLevel(), true, false, p == me, List.of(), com.xenowarriors.party.Bonds.level(me, b.charKey()), com.xenowarriors.party.Bonds.progress(me, b.charKey())));
        return new PartyCardsPacket(cards, Parties.inParty(me), amLeader, synergies(me), com.xenowarriors.party.Bonds.hearts(com.xenowarriors.party.Bonds.best(me)), com.xenowarriors.party.Bonds.best(me));
    }

    /** Every synergy, with whether it's on for you now (and how strong). */
    public static List<Syn> synergies(ServerPlayer me) {
        String mine = ModCapabilities.getType(me).getId();
        java.util.Set<String> active = com.xenowarriors.party.Synergy.of(me);
        List<Syn> out = new ArrayList<>();
        for (com.xenowarriors.party.Synergy.Def d : com.xenowarriors.party.Synergy.DEFS) {
            boolean on = active.contains(d.name());
            int state = on ? 2 : d.involves(mine) ? 1 : 0;
            int bond = on ? com.xenowarriors.party.Synergy.bond(me, d.name()) : 0;
            if (!on && state == 1) for (String c : d.partnersFor(mine)) bond = Math.max(bond, com.xenowarriors.party.Bonds.level(me, c));   // (what it'd be)
            String down = d.downside() == null ? "" : com.xenowarriors.party.Bonds.downside(bond) <= 0 ? "(" + d.downside() + " - gone: bond " + com.xenowarriors.party.Bonds.NO_DOWNSIDE + "+)"
                    : d.downside() + " (" + Math.round(com.xenowarriors.party.Bonds.downside(bond) * 100) + "% - gone at bond " + com.xenowarriors.party.Bonds.NO_DOWNSIDE + ")";
            List<String> need = new ArrayList<>();
            for (String c : d.partnersFor(mine)) need.add(com.xenowarriors.character.CharacterType.byId(c).getDisplayName().getString());
            out.add(new Syn(d.name(), d.members(), com.xenowarriors.party.Synergy.effect(d.name(), bond), down, state, bond, need.size() > 2 ? need.get(0) + " or another" : String.join(" or ", need)));
        }
        out.sort((a, b) -> Integer.compare(b.state(), a.state()));
        return out;
    }
}

package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartySyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parties of up to 4 players: invite with right-click (or /xeno party invite), accept from the clickable chat
 * message. Members see each other's health and Karma, and can't hurt each other (except in a PvP battle).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Parties {
    public static final int MAX_SIZE = 4;
    private static final int INVITE_TICKS = 60 * 20;

    private static final class Party {
        UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
    }

    private record Invite(UUID from, long expires) {}

    private static final Map<UUID, Party> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    private Parties() {}

    private static MutableComponent msg(String text) {
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    public static boolean sameParty(Player a, Player b) {
        Party p = BY_PLAYER.get(a.getUUID());
        return p != null && p == BY_PLAYER.get(b.getUUID());
    }

    public static boolean sameParty(UUID a, UUID b) {
        Party p = BY_PLAYER.get(a);
        return p != null && p == BY_PLAYER.get(b);
    }

    /** Who owns this pet: Mikiko's foxes, Rhalix's winged pets, or any vanilla tamed animal. */
    public static UUID petOwner(net.minecraft.world.entity.Entity e) {
        var data = e.getPersistentData();
        if (data.hasUUID("xw_owner")) return data.getUUID("xw_owner");
        if (data.hasUUID("xw_wing_owner")) return data.getUUID("xw_wing_owner");
        if (e instanceof net.minecraft.world.entity.OwnableEntity o) return o.getOwnerUUID();
        return null;
    }

    /** Online party members of this player, including them (just them if not in a party). */
    public static List<ServerPlayer> members(ServerPlayer p) {
        Party party = BY_PLAYER.get(p.getUUID());
        if (party == null) return List.of(p);
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : party.members) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(id);
            if (m != null) out.add(m);
        }
        return out;
    }

    public static boolean inParty(Player p) {
        return BY_PLAYER.containsKey(p.getUUID());
    }

    // ---------------------------------------------------------------- inviting

    public static void invite(ServerPlayer from, ServerPlayer to) {
        if (from == to) return;
        if (sameParty(from, to)) {
            from.sendSystemMessage(msg(to.getName().getString() + " is already in your party."));
            return;
        }
        if (inParty(to)) {
            from.sendSystemMessage(msg(to.getName().getString() + " is already in another party."));
            return;
        }
        Party party = BY_PLAYER.get(from.getUUID());
        if (party != null && party.members.size() >= MAX_SIZE) {
            from.sendSystemMessage(msg("Your party is full (" + MAX_SIZE + " players)."));
            return;
        }
        INVITES.put(to.getUUID(), new Invite(from.getUUID(), from.level().getGameTime() + INVITE_TICKS));
        from.sendSystemMessage(msg("Invited " + to.getName().getString() + " to your party."));
        to.sendSystemMessage(msg(from.getName().getString() + " invited you to their party! ")
                .append(button("[Accept]", "/xeno party accept", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(button("[Decline]", "/xeno party decline", ChatFormatting.RED)));
        to.level().playSound(null, to.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Party Invite",
                from.getName().getString() + " invited you to their party!", "/xeno party accept", "/xeno party decline", INVITE_TICKS, 0xFF6CC4FF), to);
    }

    static MutableComponent button(String text, String command, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    public static void accept(ServerPlayer p) {
        Invite inv = INVITES.remove(p.getUUID());
        MinecraftServer server = p.getServer();
        ServerPlayer from = inv == null || server == null ? null : server.getPlayerList().getPlayer(inv.from);
        if (inv == null || from == null || p.level().getGameTime() > inv.expires) {
            p.sendSystemMessage(msg("You don't have a party invite (or it ran out)."));
            return;
        }
        leave(p, false);
        Party party = BY_PLAYER.get(from.getUUID());
        if (party == null) {
            party = new Party();
            party.leader = from.getUUID();
            party.members.add(from.getUUID());
            BY_PLAYER.put(from.getUUID(), party);
        }
        if (party.members.size() >= MAX_SIZE) {
            p.sendSystemMessage(msg("That party is full."));
            return;
        }
        party.members.add(p.getUUID());
        BY_PLAYER.put(p.getUUID(), party);
        tell(server, party, p.getName().getString() + " joined the party!");
        syncParty(server, party);
    }

    public static void decline(ServerPlayer p) {
        Invite inv = INVITES.remove(p.getUUID());
        if (inv == null) return;
        p.sendSystemMessage(msg("Invite declined."));
        ServerPlayer from = p.getServer() == null ? null : p.getServer().getPlayerList().getPlayer(inv.from);
        if (from != null) from.sendSystemMessage(msg(p.getName().getString() + " declined your party invite."));
    }

    public static void leave(ServerPlayer p, boolean announce) {
        Party party = BY_PLAYER.remove(p.getUUID());
        if (party == null) return;
        party.members.remove(p.getUUID());
        ModNetwork.sendToPlayer(new PartySyncPacket(List.of()), p);
        MinecraftServer server = p.getServer();
        if (announce) p.sendSystemMessage(msg("You left the party."));
        if (party.members.size() <= 1) {
            // a party of one isn't a party
            for (UUID id : party.members) {
                BY_PLAYER.remove(id);
                ServerPlayer last = server == null ? null : server.getPlayerList().getPlayer(id);
                if (last != null) {
                    last.sendSystemMessage(msg("Your party broke up."));
                    ModNetwork.sendToPlayer(new PartySyncPacket(List.of()), last);
                }
            }
            return;
        }
        if (p.getUUID().equals(party.leader)) party.leader = party.members.iterator().next();
        if (server != null) {
            tell(server, party, p.getName().getString() + " left the party.");
            syncParty(server, party);
        }
    }

    public static void kick(ServerPlayer leader, ServerPlayer target) {
        Party party = BY_PLAYER.get(leader.getUUID());
        if (party == null || !leader.getUUID().equals(party.leader) || !party.members.contains(target.getUUID())) {
            leader.sendSystemMessage(msg("Only the party leader can remove someone from the party."));
            return;
        }
        target.sendSystemMessage(msg("You were removed from the party."));
        leave(target, false);
    }

    public static List<Component> describe(ServerPlayer p) {
        List<Component> out = new ArrayList<>();
        Party party = BY_PLAYER.get(p.getUUID());
        if (party == null) return out;
        MinecraftServer server = p.getServer();
        for (UUID id : party.members) {
            ServerPlayer m = server == null ? null : server.getPlayerList().getPlayer(id);
            String name = m == null ? "(offline)" : m.getName().getString();
            out.add(Component.literal(" - " + name + (id.equals(party.leader) ? " (leader)" : "")).withStyle(ChatFormatting.AQUA));
        }
        return out;
    }

    private static void tell(MinecraftServer server, Party party, String text) {
        for (UUID id : party.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(id);
            if (m != null) m.sendSystemMessage(msg(text));
        }
    }

    // ---------------------------------------------------------------- syncing health & karma

    private static void syncParty(MinecraftServer server, Party party) {
        List<PartySyncPacket.Member> list = new ArrayList<>();
        for (UUID id : party.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(id);
            if (m == null) continue;
            list.add(new PartySyncPacket.Member(id, m.getName().getString(), m.getHealth(), m.getMaxHealth(),
                    Karma.get(m), Karma.MAX, ModCapabilities.getType(m).ordinal(), Duels.isKnockedOut(m),
                    id.equals(party.leader)));
        }
        for (UUID id : party.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(id);
            if (m != null) ModNetwork.sendToPlayer(new PartySyncPacket(list), m);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 10 != 0) return;
        Set<Party> done = new java.util.HashSet<>();
        for (Party party : BY_PLAYER.values()) if (done.add(party)) syncParty(event.getServer(), party);
    }

    // ---------------------------------------------------------------- no friendly fire

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player victim && event.getSource().getEntity() instanceof Player attacker
                && victim != attacker && sameParty(victim, attacker) && !Duels.dueling(victim, attacker)) {
            event.setCanceled(true);
            return;
        }
        // you can't hurt your own pets or your party members' pets
        if (!(event.getEntity() instanceof Player) && event.getSource().getEntity() instanceof Player attacker) {
            UUID owner = petOwner(event.getEntity());
            if (owner != null && (owner.equals(attacker.getUUID()) || sameParty(owner, attacker.getUUID()))) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) leave(p, false);
        INVITES.remove(event.getEntity().getUUID());
    }
}

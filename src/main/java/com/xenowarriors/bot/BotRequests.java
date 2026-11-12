package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RequestPopupPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * The requests bots make in quiet times (spar, 2v2, a party, a picnic, the Dragon Radar, "I'm one short"): sent
 * through here so that, the moment an enemy attacks you or one of your bots, any still waiting is turned down by
 * itself and its card goes away - nobody stops for a picnic in the middle of a fight.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotRequests {
    private BotRequests() {}

    private record Pending(String accept, String decline, long until) {}
    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
    /** (Tests) requests turned down by an attack. */
    public static int autoDeclined;

    /** Shows a bot's request card to its player and remembers it until it's answered or runs out. */
    public static void send(ServerPlayer o, RequestPopupPacket card) {
        ModNetwork.sendToPlayer(card, o);
        List<Pending> l = PENDING.computeIfAbsent(o.getUUID(), k -> new ArrayList<>());
        long now = o.level().getGameTime();
        l.removeIf(p -> p.until < now || p.accept.equals(card.accept()));
        l.add(new Pending(card.accept(), card.decline(), now + card.ticks()));
    }

    /** Any bot request still waiting for this player? */
    public static boolean waiting(ServerPlayer o) {
        List<Pending> l = PENDING.get(o.getUUID());
        if (l == null) return false;
        long now = o.level().getGameTime();
        l.removeIf(p -> p.until < now);
        return !l.isEmpty();
    }

    /** Turns down everything waiting (the decline command runs as if they'd pressed Decline). */
    public static void declineAll(ServerPlayer o, String why) {
        List<Pending> l = PENDING.remove(o.getUUID());
        if (l == null) return;
        long now = o.level().getGameTime();
        boolean any = false;
        for (Pending p : l) {
            if (p.until < now) continue;
            any = true;
            autoDeclined++;
            RequestPopupPacket.dismiss(o, p.accept);
            if (!p.decline.isEmpty())
                o.server.getCommands().performPrefixedCommand(o.createCommandSourceStack().withSuppressedOutput(), p.decline.startsWith("/") ? p.decline.substring(1) : p.decline);
        }
        if (any) o.displayClientMessage(Component.literal("Request called off - " + why + "!").withStyle(ChatFormatting.RED), true);
    }

    /** An enemy hits you or one of your bots: the quiet-time requests are off. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingAttackEvent e) {
        if (e.isCanceled() || PENDING.isEmpty()) return;
        Entity src = com.xenowarriors.combat.Allies.actor(e.getSource().getEntity());
        if (src == null || src == e.getEntity()) return;
        ServerPlayer o = null;
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) o = p;
        else if (e.getEntity() instanceof PartyBotEntity b) o = b.owner();
        if (o == null || !PENDING.containsKey(o.getUUID())) return;
        if (PartyBots.sparFoes(e.getEntity(), src) || src instanceof PartyBotEntity) return;   // (a spar is not an attack)
        boolean enemy = src instanceof Enemy || src instanceof Mob m && m.getTarget() != null
                || src instanceof ServerPlayer sp && !com.xenowarriors.party.Parties.sameParty(sp.getUUID(), o.getUUID()) && sp != o;
        if (!enemy) return;
        declineAll(o, e.getEntity() == o ? "you're under attack" : "your team's under attack");
    }
}

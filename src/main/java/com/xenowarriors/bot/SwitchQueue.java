package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.BusyCharsPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Switching to a character who's off doing something (OffDuty): you can't just take them over. With a bond of
 * {@value OffDuty#OVERRIDE_BOND}+ (or when it's a job you gave them) they drop it for you - unless they're out hunting
 * the Dragon Balls, which nobody walks away from. Otherwise picking them queues the switch: once they're done, and
 * you're not in a fight, you become them.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SwitchQueue {
    private SwitchQueue() {}

    private static final String KEY = "xw_switch_queue";

    /** Busy right now (off duty with something on). */
    public static boolean busy(ServerPlayer p, CharacterType t) {
        return OffDuty.enabled() && OffDuty.offDuty(p, t) && OffDuty.act(p, t) != OffDuty.Act.NONE;
    }

    /** Can switch to them although they're busy: a strong bond (or your own job for them) - never mid Dragon Ball hunt. */
    public static boolean mayOverride(ServerPlayer p, CharacterType t) {
        if (OffDuty.act(p, t) == OffDuty.Act.DRAGON_BALLS) return false;
        return !OffDuty.task(p, t).isEmpty() || com.xenowarriors.party.Bonds.level(p, t.getId()) >= OffDuty.OVERRIDE_BOND;
    }

    public static CharacterType queued(ServerPlayer p) {
        String id = p.getPersistentData().getString(KEY);
        return id.isEmpty() ? CharacterType.NONE : CharacterType.byId(id);
    }

    public static void clear(ServerPlayer p) { p.getPersistentData().remove(KEY); }

    /**
     * Picking a character: null = go ahead (anything they were doing is dropped), otherwise they're busy - the switch
     * is queued and this is what to tell the player.
     */
    public static String check(ServerPlayer p, CharacterType t) {
        if (!busy(p, t)) { if (queued(p) == t) clear(p); return null; }
        String name = t.getDisplayName().getString();
        if (mayOverride(p, t)) {
            p.sendSystemMessage(Component.literal(name + " drops " + OffDuty.verbOf(OffDuty.act(p, t)) + " for you" + (OffDuty.task(p, t).isEmpty() ? " (bond " + com.xenowarriors.party.Bonds.level(p, t.getId()) + ")" : "") + ".")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            OffDuty.joined(p, t);
            clear(p);
            return null;
        }
        p.getPersistentData().putString(KEY, t.getId());
        long left = Math.max(0, OffDuty.rec(p, t).getLong("u") - p.server.overworld().getGameTime());
        String why = OffDuty.act(p, t) == OffDuty.Act.DRAGON_BALLS ? " - nobody leaves a Dragon Ball hunt halfway" : "";
        return name + " is busy (" + OffDuty.status(p, t) + why + "). Queued: you'll switch to them when they're done (~" + BotCalls.minutes(left) + " min), as long as you're not fighting.";
    }

    /** Once a second: a queued switch whose character is free now (and you're out of combat) happens. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 20 != 11) return;
        CharacterType t = queued(p);
        if (t == CharacterType.NONE) return;
        if (com.xenowarriors.character.ModCapabilities.getType(p) == t || !OffDuty.offDuty(p, t)) { clear(p); return; }
        if (busy(p, t) || !p.isAlive() || !BotSpar.quiet(p) || com.xenowarriors.character.CharacterManager.switchBlocked(p) != null) return;
        clear(p);
        p.sendSystemMessage(Component.literal(t.getDisplayName().getString() + " is done - switching to them now!").withStyle(ChatFormatting.LIGHT_PURPLE));
        com.xenowarriors.network.SelectCharacterPacket.select(p, t);
    }

    /** What the character select screen shows for each busy character. */
    public static void send(ServerPlayer p) {
        List<BusyCharsPacket.Entry> l = new ArrayList<>();
        long now = p.server.overworld().getGameTime();
        CharacterType q = queued(p);
        for (CharacterType t : CharacterType.PLAYABLE) {
            if (!busy(p, t)) continue;
            boolean ordered = !OffDuty.task(p, t).isEmpty();
            long left = Math.max(0, OffDuty.rec(p, t).getLong("u") - now);
            l.add(new BusyCharsPacket.Entry(t.getId(), OffDuty.act(p, t).ordinal(), OffDuty.status(p, t), ordered ? -1 : BotCalls.minutes(left), mayOverride(p, t), q == t));
        }
        ModNetwork.sendToPlayer(new BusyCharsPacket(l), p);
    }
}

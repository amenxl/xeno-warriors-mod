package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PhoneTeamPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Phoning a busy teammate (the phone's Teammates page): they tell you when they'll be done with what they're doing
 * (in minutes and on the in-game clock), and once they are they come and join you - straight away if your party has
 * room, otherwise they ring back to say they're ready, so you can make room and add them from the party menu.
 */
public final class BotCalls {
    private BotCalls() {}

    /** Asked to join when done (in the off-duty record). */
    static final String CALLED = "call";

    /** The phone's Teammates page: everyone off duty and busy right now. */
    public static void open(ServerPlayer p) {
        List<PhoneTeamPacket.Entry> out = new ArrayList<>();
        long now = p.server.overworld().getGameTime();
        if (OffDuty.enabled()) for (CharacterType t : CharacterType.PLAYABLE) {
            if (!OffDuty.offDuty(p, t)) continue;
            OffDuty.Act a = OffDuty.act(p, t);
            if (a == OffDuty.Act.NONE) continue;
            CompoundTag c = OffDuty.rec(p, t);
            boolean ordered = !OffDuty.task(p, t).isEmpty();
            long left = ordered ? -1 : Math.max(0, c.getLong("u") - now);
            out.add(new PhoneTeamPacket.Entry(t.getId(), a.ordinal(), OffDuty.status(p, t), ordered ? -1 : minutes(left),
                    ordered ? "" : clock(p, left), c.getBoolean(CALLED), ordered));
        }
        ModNetwork.sendToPlayer(new PhoneTeamPacket(out), p);
    }

    /** Ring a busy teammate: ask them to come when they're done. */
    public static void call(ServerPlayer p, CharacterType t) {
        String name = t.getDisplayName().getString();
        if (t == CharacterType.NONE || !OffDuty.offDuty(p, t)) { say(p, name + " is already with you.", ChatFormatting.GRAY); return; }
        OffDuty.Act a = OffDuty.act(p, t);
        if (a == OffDuty.Act.NONE) { say(p, name + ": \"I'm free right now - add me from the party menu!\"", ChatFormatting.AQUA); return; }
        if (!OffDuty.task(p, t).isEmpty()) {
            say(p, name + ": \"I'm still on the job you gave me. Call me back from the party menu when you need me.\"", ChatFormatting.AQUA); return;
        }
        CompoundTag c = OffDuty.rec(p, t);
        long left = Math.max(0, c.getLong("u") - p.server.overworld().getGameTime());
        c.putBoolean(CALLED, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
        say(p, name + ": \"Sure! I'm " + OffDuty.verbOf(a) + " - I'll be done in about " + minutes(left) + " min (around " + clock(p, left) + "). I'll come find you then.\"", ChatFormatting.AQUA);
        open(p);                                                                  // (the page shows them as called)
    }

    /** Their activity just ended and you'd called them: join if there's room, otherwise ring to say they're ready. */
    static void done(ServerPlayer p, CharacterType t) {
        CompoundTag c = OffDuty.rec(p, t);
        if (!c.getBoolean(CALLED)) return;
        c.remove(CALLED);
        String name = t.getDisplayName().getString();
        if (!OffDuty.offDuty(p, t)) return;
        if (com.xenowarriors.journey.Journey.inLobbyOrRun(p) || com.xenowarriors.vr.Vr.is(p.level()) || !p.isAlive() || PartyBots.room(p) <= 0) {
            ring(p);
            say(p, name + " called back: \"I'm done and ready to join! Make some room in your party and add me from the party menu.\"", ChatFormatting.LIGHT_PURPLE);
            return;
        }
        int before = PartyBots.bots(p).size();
        PartyBots.add(p, new String[]{t.getId()});
        if (PartyBots.bots(p).size() > before) say(p, name + " finished up and came to join you, like they said.", ChatFormatting.LIGHT_PURPLE);
        else { ring(p); say(p, name + " is done and ready to join - add them from the party menu.", ChatFormatting.LIGHT_PURPLE); }
    }

    private static void ring(ServerPlayer p) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.9F);
    }

    private static void say(ServerPlayer p, String s, ChatFormatting col) { p.sendSystemMessage(Component.literal("📞 " + s).withStyle(col)); }

    static int minutes(long ticks) { return (int) Math.max(1, (ticks + OffDuty.MINUTE - 1) / OffDuty.MINUTE); }

    /** The in-game time of day {@code ticks} from now, e.g. "6:30 PM". */
    static String clock(ServerPlayer p, long ticks) {
        long t = (p.level().getDayTime() + ticks + 6000) % 24000;              // (tick 0 is 6 AM)
        int h = (int) (t / 1000), m = (int) (t % 1000 * 60 / 1000);
        return (h % 12 == 0 ? 12 : h % 12) + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? " AM" : " PM");
    }
}

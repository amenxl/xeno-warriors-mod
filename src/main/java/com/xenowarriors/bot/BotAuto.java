package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auto mode (a bot mode): the bot watches what you do and does it too. Mine a lot (stone, ore) or chop trees and it
 * mines; hit animals a lot and it hunts them; fight monsters and it goes after them; fish and it fishes. It keeps at
 * what it last saw you doing until it sees you doing something else (a few of the new thing in half a minute).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotAuto {
    private BotAuto() {}

    public enum Doing { NOTHING("following you"), MINING("mining"), HUNTING("hunting animals"), FIGHTING("fighting monsters"), FISHING("fishing");
        public final String what; Doing(String w) { what = w; } }

    /** How many of a new thing (in {@value #WINDOW} ticks) before the bots switch to it. */
    static final int NEED = 4, WINDOW = 20 * 30;
    private record Seen(Doing doing, int count, long since) {}
    private static final Map<UUID, Doing> NOW = new HashMap<>();
    private static final Map<UUID, Seen> CANDIDATE = new HashMap<>();
    /** (Tests) switches seen. */
    public static int switches;

    /** What this player's Auto bots are doing (what they last saw them do). */
    public static Doing doing(ServerPlayer o) { return o == null ? Doing.NOTHING : NOW.getOrDefault(o.getUUID(), Doing.NOTHING); }

    /** A bot's mode in effect: its own, or - in Auto - what it saw you doing. */
    public static int actMode(PartyBotEntity b) {
        if (b.mode() != PartyBotEntity.AUTO) return b.mode();
        return switch (doing(b.owner())) {
            case MINING -> PartyBotEntity.MINING;
            case FIGHTING -> PartyBotEntity.AGGRESSIVE;
            default -> PartyBotEntity.NEUTRAL;
        };
    }

    /** In Auto mode and it saw you hunting. */
    public static boolean hunting(PartyBotEntity b) { return b.mode() == PartyBotEntity.AUTO && doing(b.owner()) == Doing.HUNTING; }
    /** In Auto mode and it saw you fishing. */
    public static boolean fishing(PartyBotEntity b) { return b.mode() == PartyBotEntity.AUTO && doing(b.owner()) == Doing.FISHING; }

    /** You did one of a thing: enough of it in a short while and your Auto bots switch to it. */
    public static void saw(ServerPlayer p, Doing d) {
        if (NOW.getOrDefault(p.getUUID(), Doing.NOTHING) == d) { CANDIDATE.remove(p.getUUID()); return; }
        long now = p.level().getGameTime();
        Seen c = CANDIDATE.get(p.getUUID());
        int n = c != null && c.doing == d && now - c.since < WINDOW ? c.count + 1 : 1;
        CANDIDATE.put(p.getUUID(), new Seen(d, n, n == 1 ? now : c.since));
        if (n < NEED) return;
        CANDIDATE.remove(p.getUUID());
        NOW.put(p.getUUID(), d);
        switches++;
        boolean any = false;
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.mode() == PartyBotEntity.AUTO) { any = true; b.setTarget(null); b.mineTarget = null; }
        if (any) p.displayClientMessage(Component.literal("Your bots on Auto see you " + d.what + " - they start " + d.what + " too.").withStyle(ChatFormatting.AQUA), true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        var st = e.getState();
        if (st.is(BlockTags.LOGS) || st.is(BlockTags.MINEABLE_WITH_PICKAXE)) saw(p, Doing.MINING);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        var v = e.getEntity();
        if (v instanceof Animal a && !(a instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame())) saw(p, Doing.HUNTING);
        else if (v instanceof Enemy) saw(p, Doing.FIGHTING);
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { saw(p, Doing.FISHING); saw(p, Doing.FISHING); }   // (fishing's slow: a catch counts double)
    }
}

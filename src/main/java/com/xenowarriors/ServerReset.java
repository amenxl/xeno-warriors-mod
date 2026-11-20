package com.xenowarriors;

import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A world closing clears what was going on in it - side missions, story runs, parties, party games, duels, trials... In
 * single player the game keeps running between worlds, so these used to carry over into the next world you opened
 * (the same player, so a side mission from the old world was still "active" in a new one).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class ServerReset {
    private ServerReset() {}

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        run(com.xenowarriors.story.StorySides::serverReset);
        run(com.xenowarriors.story.StoryRun::serverReset);
        run(com.xenowarriors.bot.BotPartyTime::serverReset);
        run(com.xenowarriors.bot.BotPartyGames::serverReset);
        run(com.xenowarriors.bot.BotPicnic::serverReset);
        run(com.xenowarriors.bot.BotRps::serverReset);
        run(com.xenowarriors.you.YouAngel::serverReset);
        run(com.xenowarriors.you.BeatboxFans::serverReset);
        run(com.xenowarriors.party.Duels::serverReset);
        run(com.xenowarriors.party.Parties::serverReset);
    }

    private static void run(Runnable r) {
        try { r.run(); } catch (Throwable t) { XenoWarriors.LOGGER.warn("Reset after the world closed", t); }
    }
}

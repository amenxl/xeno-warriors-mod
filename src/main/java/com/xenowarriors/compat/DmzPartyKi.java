package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartyKiPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * With DragonMineZ: the party HUD's ki bars. Four times a second, each player with party members or bots gets everyone's
 * ki (current / max: players from their DragonMineZ character, bots from theirs - see DmzBotPower) - only when a number
 * has changed since the last one sent. Without DragonMineZ nothing is ever sent.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzPartyKi {
    private DmzPartyKi() {}

    private static Boolean loaded;
    /** What each player was last sent ("id:ki/max;..."), to send only changes. */
    private static final Map<UUID, String> LAST = new HashMap<>();
    /** (Tests) packets sent so far. */
    public static int sent;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 5 != 1) return;
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (p instanceof FakePlayer) continue;
            List<PartyKiPacket.Entry> list = entries(p);
            StringBuilder key = new StringBuilder();
            for (var x : list) key.append(x.id()).append(':').append(Math.round(x.ki())).append('/').append(Math.round(x.maxKi())).append(';');
            String k = key.toString(), before = LAST.get(p.getUUID());
            if (k.equals(before) || before == null && list.isEmpty()) continue;
            LAST.put(p.getUUID(), k);
            ModNetwork.sendToPlayer(new PartyKiPacket(list), p);
            sent++;
        }
    }

    /** Everyone's ki in this player's party (them, the other players, and all their bots); empty with no one else in it. */
    public static List<PartyKiPacket.Entry> entries(ServerPlayer p) {
        List<ServerPlayer> members = com.xenowarriors.party.Parties.members(p);
        int others = members.size() - 1;
        for (ServerPlayer m : members) others += PartyBots.bots(m).size();
        if (others <= 0) return List.of();
        List<PartyKiPacket.Entry> out = new ArrayList<>();
        for (ServerPlayer m : members) {
            float[] k = DmzBotPower.playerKi(m);
            if (k != null) out.add(new PartyKiPacket.Entry(m.getUUID(), k[0], k[1]));
            for (PartyBotEntity b : PartyBots.bots(m)) {
                float[] bk = DmzBotPower.ki(b);
                if (bk != null) out.add(new PartyKiPacket.Entry(b.getUUID(), bk[0], bk[1]));
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { LAST.remove(e.getEntity().getUUID()); }
}

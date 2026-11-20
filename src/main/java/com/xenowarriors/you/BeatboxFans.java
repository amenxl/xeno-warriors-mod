package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.BotPartyTime;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.BeatboxFansPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.party.Bonds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Your own characters who've got close to Garry and Stevie (bond {@value #FAN} with both) can't help dancing when
 * they beatbox - Garry's beatbox while he stands about, and the beatboxing at a party. Players playing one of their
 * own characters and bots of them. The server knows the bonds, so it tells the clients who (GarryBeatbox dances them).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class BeatboxFans {
    public static final int FAN = 70;
    /** How close to a party's beatbox you have to be to groove to it. */
    public static final double PARTY_RANGE = 10;
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, int[][]> LAST = new HashMap<>();

    private BeatboxFans() {}

    /** Is this one of your own characters, close enough to both of them? */
    public static boolean fan(Entity e) {
        if (e instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer))
            return ModCapabilities.getType(p) == CharacterType.YOU && Bonds.level(p, "garry") >= FAN && Bonds.level(p, "stevie") >= FAN;
        if (e instanceof PartyBotEntity b)
            return b.character() == CharacterType.YOU && Bonds.botOwn(b, "garry") >= FAN && Bonds.botOwn(b, "stevie") >= FAN;
        return false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l) || l.getGameTime() % 20 != 9 || l.players().isEmpty()) return;
        List<Integer> fans = new ArrayList<>(), grooving = new ArrayList<>();
        List<BlockPos> beats = BotPartyTime.beatboxing(l);
        List<Entity> who = new ArrayList<>(l.players());
        for (ServerPlayer p : l.players())                                      // (bots are only ever near their owners' parties / camps)
            who.addAll(l.getEntitiesOfClass(PartyBotEntity.class, p.getBoundingBox().inflate(48), b -> b.character() == CharacterType.YOU));
        for (Entity x : who) {
            if (fans.contains(x.getId()) || !fan(x)) continue;
            fans.add(x.getId());
            for (BlockPos c : beats) if (x.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) < PARTY_RANGE * PARTY_RANGE) { grooving.add(x.getId()); break; }
        }
        int[][] now = {fans.stream().mapToInt(Integer::intValue).sorted().toArray(), grooving.stream().mapToInt(Integer::intValue).sorted().toArray()};
        int[][] was = LAST.get(l.dimension());
        if (was != null && Arrays.equals(was[0], now[0]) && Arrays.equals(was[1], now[1]) && l.getGameTime() % 200 != 9) return;   // (unchanged: again every 10 s for anyone who just came in)
        LAST.put(l.dimension(), now);
        if (was == null && now[0].length == 0) return;
        sent++;
        for (ServerPlayer p : l.players()) ModNetwork.sendToPlayer(new BeatboxFansPacket(now[0], now[1]), p);
    }

    /** (Tests) updates sent. */
    public static int sent;

    /** The world closed (back to the title screen / another world): nothing of this one carries over. (ServerReset) */
    public static void serverReset() { LAST.clear(); }
}

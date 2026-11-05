package com.xenowarriors.client.party;

import com.xenowarriors.network.PartyKiPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * With DragonMineZ: each party member's ki (players and bots) for the party HUD's ki bars (sent by DmzPartyKi only
 * when the numbers change). Without DragonMineZ nothing arrives and the party HUD looks as it always did.
 */
public final class PartyKiClient {
    private PartyKiClient() {}

    private static final Map<UUID, float[]> KI = com.xenowarriors.client.ClientReset.track(new HashMap<>());
    /** (UI tests) packets received. */
    public static int received;

    public static void set(List<PartyKiPacket.Entry> entries) {
        received++;
        KI.clear();
        for (var e : entries) if (e.maxKi() > 0) KI.put(e.id(), new float[]{e.ki(), e.maxKi()});
    }

    /** This member's {ki, max}, or null (no DragonMineZ / none known). */
    public static float[] of(UUID id) {
        if (KI.isEmpty() || !com.xenowarriors.client.DmzHud.on()) return null;
        return KI.get(id);
    }

    /** Any ki to show at all (the party frames make room for the bars). */
    public static boolean any() { return !KI.isEmpty() && com.xenowarriors.client.DmzHud.on(); }

    public static float frac(float[] k) { return k == null || k[1] <= 0 ? 0 : Math.max(0F, Math.min(1F, k[0] / k[1])); }

    /** (UI tests) "uuid=ki/max ..." for everyone known. */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        for (var e : KI.entrySet()) sb.append(e.getKey().toString(), 0, 8).append('=').append(Math.round(e.getValue()[0])).append('/').append(Math.round(e.getValue()[1])).append(' ');
        return sb.toString().trim();
    }

    public static final int KI_COLOR = 0xFFFFCD3C, KI_BACK = 0xFF3A3010, KI_TEXT = 0xFFFFE8A8;
}

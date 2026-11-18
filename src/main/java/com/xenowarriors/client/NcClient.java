package com.xenowarriors.client;

import java.util.HashMap;
import java.util.Map;

/** The new characters' states on the client (NcStatePacket): who's in a form, who can't be locked on to. */
public final class NcClient {
    private NcClient() {}
    private static final Map<Integer, Integer> FLAGS = com.xenowarriors.client.ClientReset.track(new HashMap<>());

    public static void set(int id, int flags) { if (flags == 0) FLAGS.remove(id); else FLAGS.put(id, flags); }

    /** In Demon Form / Heavenly Demon. */
    public static boolean form(int id) { return (FLAGS.getOrDefault(id, 0) & 1) != 0; }

    /** Hidden Will / Vanish: nobody locks on to them. */
    public static boolean hidden(int id) { return (FLAGS.getOrDefault(id, 0) & 2) != 0; }
}

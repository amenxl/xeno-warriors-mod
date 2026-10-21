package com.xenowarriors.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How each player is talking in Simple Voice Chat right now, so their mouth can follow their voice.
 * The mouth shape comes straight from the loudness of each little chunk of audio (so it naturally opens and
 * closes with syllables), and a long, steady sound (holding a note) keeps it open in a round "ooh".
 * Fed from the voice chat audio threads; read while rendering.
 */
public final class VoiceLevels {
    /** Your own microphone. */
    public static final UUID LOCAL = new UUID(0L, 0L);
    private static final long SILENT_MS = 140, PHRASE_GAP_MS = 180, HOLD_MS = 550;

    private static final class Voice {
        float level, avg, dev;
        long last, start;
    }

    private static final Map<UUID, Voice> VOICES = new ConcurrentHashMap<>();

    private VoiceLevels() {}

    public static void onAudio(UUID id, short[] audio) {
        if (id == null || audio == null || audio.length == 0) return;
        double sum = 0;
        for (short s : audio) sum += (double) s * s;
        float raw = (float) Math.min(1.0, Math.sqrt(sum / audio.length) / 32768.0 * 7.0);
        long now = System.currentTimeMillis();
        Voice v = VOICES.computeIfAbsent(id, k -> new Voice());
        synchronized (v) {
            if (now - v.last > PHRASE_GAP_MS) {     // a new phrase starts
                v.start = now;
                v.avg = raw;
                v.dev = 0.2F;
                v.level = raw;
            }
            // opens fast, closes a bit softer (no flicker)
            v.level = raw > v.level ? raw * 0.75F + v.level * 0.25F : raw * 0.45F + v.level * 0.55F;
            v.avg += (raw - v.avg) * 0.08F;
            v.dev += (Math.abs(raw - v.avg) - v.dev) * 0.1F;
            v.last = now;
        }
    }

    /** 0 = silent, otherwise how loud (0..1). */
    public static float level(UUID id) {
        Voice v = VOICES.get(id);
        if (v == null || System.currentTimeMillis() - v.last > SILENT_MS) return 0F;
        return v.level;
    }

    /**
     * Mouth shape: 0 closed, 1 barely parted, 2 small, 3 open, 4 wide, 5 round "ooh" (a held note).
     */
    public static int mouth(UUID id) {
        Voice v = VOICES.get(id);
        if (v == null) return 0;
        synchronized (v) {
            long now = System.currentTimeMillis();
            if (now - v.last > SILENT_MS) {
                if (now - v.last > 5000) VOICES.remove(id);
                return 0;
            }
            boolean held = now - v.start > HOLD_MS && v.dev < 0.07F && v.avg > 0.12F;
            if (held) return 5;
            float l = v.level;
            return l < 0.06F ? 0 : l < 0.16F ? 1 : l < 0.32F ? 2 : l < 0.55F ? 3 : 4;
        }
    }
}

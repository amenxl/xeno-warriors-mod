package com.xenowarriors.voice;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How loud each player is talking in Simple Voice Chat right now (0..1), so their mouth can move.
 * Fed from the voice chat audio threads; read while rendering.
 */
public final class VoiceLevels {
    /** Your own microphone. */
    public static final UUID LOCAL = new UUID(0L, 0L);
    private static final long FADE_MS = 160;

    private record Level(float value, long time) {}

    private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();

    private VoiceLevels() {}

    public static void onAudio(UUID id, short[] audio) {
        if (id == null || audio == null || audio.length == 0) return;
        double sum = 0;
        for (short s : audio) sum += (double) s * s;
        double rms = Math.sqrt(sum / audio.length) / 32768.0;
        float level = (float) Math.min(1.0, rms * 7.0);
        LEVELS.put(id, new Level(level, System.currentTimeMillis()));
    }

    /** 0 when silent, fading out quickly after the last bit of audio. */
    public static float level(UUID id) {
        Level l = LEVELS.get(id);
        if (l == null) return 0F;
        long age = System.currentTimeMillis() - l.time;
        if (age > FADE_MS) {
            if (age > 5000) LEVELS.remove(id);
            return 0F;
        }
        return l.value * (1F - age / (float) FADE_MS * 0.5F);
    }
}

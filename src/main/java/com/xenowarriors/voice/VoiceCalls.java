package com.xenowarriors.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phone calls over Simple Voice Chat: each caller's voice is taken from their mic, run through what their phone sounds
 * like, and played straight to the other person (anywhere in the world). A smartphone is clear, a flip phone sounds
 * like an old phone line, and the brick phone is crunchy, hissy and cuts out now and then.
 */
public final class VoiceCalls {
    static VoicechatServerApi api;
    private static final Map<UUID, UUID> PARTNER = new ConcurrentHashMap<>();
    private static final Map<UUID, Line> LINES = new ConcurrentHashMap<>();
    private static final UUID CHANNEL_SALT = UUID.fromString("6f1c2d6e-4b3a-4a8e-9f0d-2c7b5e1a9d33");

    private VoiceCalls() {}

    /** One speaker's audio path: codec state, filter state and their phone's quality (0 smart, 1 flip, 2 brick). */
    private static final class Line {
        final OpusDecoder dec = api.createDecoder();
        final OpusEncoder enc = api.createEncoder();
        final int quality;
        final UUID channel;
        final Random rnd = new Random();
        float lp1, lp2, hp1, hp2, hold;
        int holdCount, dropout;
        Line(int quality, UUID channel) { this.quality = quality; this.channel = channel; }
        void close() { dec.close(); enc.close(); }
    }

    /** Starts a call between two players (with their phone styles); false if voice chat isn't running. */
    public static boolean connect(UUID a, int styleA, UUID b, int styleB) {
        if (api == null || api.getConnectionOf(a) == null && api.getConnectionOf(b) == null) return false;
        PARTNER.put(a, b);
        PARTNER.put(b, a);
        LINES.put(a, new Line(styleA, new UUID(a.getMostSignificantBits() ^ CHANNEL_SALT.getMostSignificantBits(), a.getLeastSignificantBits())));
        LINES.put(b, new Line(styleB, new UUID(b.getMostSignificantBits() ^ CHANNEL_SALT.getMostSignificantBits(), b.getLeastSignificantBits())));
        return true;
    }

    public static void disconnect(UUID... players) {
        for (UUID id : players) {
            PARTNER.remove(id);
            Line l = LINES.remove(id);
            if (l != null) l.close();
        }
    }

    /** A mic packet from someone on a call: through their phone, to the person they're talking to. */
    static void onMic(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (api == null || sender == null) return;
        UUID me = sender.getPlayer().getUuid();
        UUID other = PARTNER.get(me);
        Line line = LINES.get(me);
        if (other == null || line == null) return;
        VoicechatConnection to = api.getConnectionOf(other);
        if (to == null) return;
        byte[] data = event.getPacket().getOpusEncodedData();
        if (data == null || data.length == 0) return;
        short[] pcm = line.dec.decode(data);
        filter(line, pcm);
        byte[] out = line.enc.encode(pcm);
        api.sendStaticSoundPacketTo(to, event.getPacket().staticSoundPacketBuilder().channelId(line.channel).opusEncodedData(out).build());
    }

    private static float alpha(float hz) {
        return (float) (1.0 - Math.exp(-2.0 * Math.PI * hz / 48000.0));
    }

    private static void filter(Line l, short[] pcm) {
        float hpA, lpA, noise, gain;
        int hold, bits;
        switch (l.quality) {
            case 0 -> { hpA = alpha(110); lpA = alpha(16000); noise = 0; gain = 1.05F; hold = 1; bits = 16; }      // smartphone: clean
            case 1 -> { hpA = alpha(320); lpA = alpha(3300); noise = 90; gain = 1.6F; hold = 1; bits = 16; }       // flip phone: phone line
            default -> { hpA = alpha(520); lpA = alpha(2100); noise = 520; gain = 2.2F; hold = 6; bits = 6; }      // brick phone: crunchy
        }
        if (l.quality == 2) {
            if (l.dropout > 0) l.dropout--;
            else if (l.rnd.nextFloat() < 0.015F) l.dropout = 2 + l.rnd.nextInt(5);      // the signal cuts out for a moment
        }
        int step = 1 << (16 - bits);
        for (int i = 0; i < pcm.length; i++) {
            float x = pcm[i];
            // band-limit: two one-pole high-passes and two low-passes
            l.hp1 += hpA * (x - l.hp1); x -= l.hp1;
            l.hp2 += hpA * (x - l.hp2); x -= l.hp2;
            l.lp1 += lpA * (x - l.lp1); x = l.lp1;
            l.lp2 += lpA * (x - l.lp2); x = l.lp2;
            x *= gain;
            // squashed like a small speaker
            x = (float) (32767 * Math.tanh(x / 32767.0 * (l.quality == 0 ? 1.0 : 1.8)));
            if (hold > 1) {                                   // lower sample rate
                if (l.holdCount++ % hold == 0) l.hold = x;
                x = l.hold;
            }
            if (step > 1) x = Math.round(x / step) * step;     // fewer bits
            if (noise > 0) x += (l.rnd.nextFloat() - 0.5F) * 2 * noise;
            if (l.quality == 2 && l.rnd.nextFloat() < 0.0006F) x += (l.rnd.nextBoolean() ? 1 : -1) * 9000;     // crackle
            if (l.dropout > 0) x *= 0.05F;
            pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(x)));
        }
    }
}

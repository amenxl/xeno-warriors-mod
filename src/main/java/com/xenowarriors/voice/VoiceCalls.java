package com.xenowarriors.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;

import java.util.UUID;

/** Phone calls over Simple Voice Chat: both callers go into a hidden group so they hear each other anywhere. */
public final class VoiceCalls {
    static VoicechatServerApi api;

    private VoiceCalls() {}

    /** Puts the players in one hidden call group; false if voice chat isn't running for them. */
    public static boolean connect(UUID... players) {
        if (api == null) return false;
        Group g = api.groupBuilder().setName("Phone call").setHidden(true).setPersistent(false).setType(Group.Type.NORMAL).build();
        boolean any = false;
        for (UUID id : players) {
            VoicechatConnection c = api.getConnectionOf(id);
            if (c != null) { c.setGroup(g); any = true; }
        }
        return any;
    }

    public static void disconnect(UUID... players) {
        if (api == null) return;
        for (UUID id : players) {
            VoicechatConnection c = api.getConnectionOf(id);
            if (c != null && c.isInGroup() && "Phone call".equals(c.getGroup().getName())) c.setGroup(null);
        }
    }
}

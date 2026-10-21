package com.xenowarriors.voice;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Simple Voice Chat plugin (only loaded when that mod is installed): listens to who's talking and how
 * loud, so Mikiko's and Rhalix's mouths move along with their voice.
 */
@ForgeVoicechatPlugin
public class XenoVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return "xenowarriors";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class,
                e -> VoiceLevels.onAudio(e.getEntityId(), e.getRawAudio()));
        registration.registerEvent(ClientSoundEvent.class, e -> VoiceLevels.onAudio(VoiceLevels.LOCAL, e.getRawAudio()));
    }
}

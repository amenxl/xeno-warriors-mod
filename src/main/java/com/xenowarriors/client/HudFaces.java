package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which face a HUD portrait shows: hit, big hit, low health, talking (mouth flapping after a chat message), going all
 * out (ultimate / KA), blinking, or normal. Tracks you and your party members.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HudFaces {
    private static final class State { float lastHp = -1; int hitUntil, bigHitUntil, talkUntil; String bubble; int bubbleUntil; }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static int clock;
    public static final int BUBBLE_MAX = 32;

    private HudFaces() {}

    private static State st(UUID id) { return STATES.computeIfAbsent(id, k -> new State()); }

    /** Call each frame with the person's current health. */
    public static void health(UUID id, float hp, float max) {
        State s = st(id);
        if (s.lastHp >= 0 && hp < s.lastHp - 0.01F) {
            float lost = s.lastHp - hp;
            if (lost >= max * 0.2F) s.bigHitUntil = clock + 14;
            else s.hitUntil = clock + 10;
        }
        s.lastHp = hp;
    }

    private static void talk(UUID id, String msg) {
        State s = st(id);
        s.talkUntil = clock + Math.min(80, 16 + msg.length() * 2);
        if (msg.length() <= BUBBLE_MAX) { s.bubble = msg; s.bubbleUntil = clock + Math.min(100, 40 + msg.length() * 2); }
    }

    @SubscribeEvent
    public static void onSend(ClientChatEvent event) {
        String m = event.getMessage();
        var p = Minecraft.getInstance().player;
        if (p != null && !m.startsWith("/")) talk(p.getUUID(), m);
    }

    @SubscribeEvent
    public static void onReceive(ClientChatReceivedEvent event) {
        UUID sender = event.getSender();
        var p = Minecraft.getInstance().player;
        if (sender == null || event.isSystem() || (p != null && sender.equals(p.getUUID()))) return;
        String text = event.getMessage().getString();
        int i = text.indexOf("> ");
        talk(sender, i >= 0 ? text.substring(i + 2) : text);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) { if (event.phase == TickEvent.Phase.END) clock++; }

    /** A short chat line shown as a bubble by the portrait, or null. */
    public static String bubble(UUID id) {
        State s = STATES.get(id);
        return s != null && s.bubble != null && clock < s.bubbleUntil ? s.bubble : null;
    }

    public static boolean talking(UUID id) { State s = STATES.get(id); return s != null && clock < s.talkUntil; }

    /** The face for this portrait right now. */
    public static String face(UUID id, float hp, float max, boolean allOut, boolean down) {
        State s = STATES.get(id);
        if (down) return "exhausted";
        if (allOut) return "rage";
        if (s != null && clock < s.talkUntil) return (clock / 4) % 2 == 0 ? "shout" : "normal";
        if (s != null && clock < s.bigHitUntil) return "damage1";
        if (s != null && clock < s.hitUntil) return "hurt";
        if (max > 0 && hp / max <= 0.25F) return "exhausted";
        if ((clock + id.hashCode() % 50) % 90 < 3) return "blink";
        return "normal";
    }

    public static ResourceLocation texture(CharacterType type, String face) {
        String ch = type == CharacterType.RHALIX ? "rhalix" : "mikiko";
        return XenoWarriors.id("textures/gui/hud/portrait_" + ch + "_" + face + ".png");
    }

    public static int clock() { return clock; }

    /** Is the local player in an ultimate, team attack, or KA right now (for the "all out" face)? */
    public static boolean allOut(Player p) {
        var a = ClientSkillState.currentAnim(p);
        if (a != null && (a.anim() == com.xenowarriors.network.SkillAnimPacket.BLAZING || a.anim() == com.xenowarriors.network.SkillAnimPacket.BARRAGE
                || a.anim() == com.xenowarriors.network.SkillAnimPacket.TEAM_MIKIKO || a.anim() == com.xenowarriors.network.SkillAnimPacket.TEAM_RHALIX)) return true;
        int act = EntityPoses.action(p);
        return act == com.xenowarriors.anim.Poses.KA_CAST || act == com.xenowarriors.anim.Poses.KA_BREAK || com.xenowarriors.client.ClashClient.active();
    }
}

package com.xenowarriors.client.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps one BotAvatar per bot teammate in view, synced every tick, and lets the client's player animation code
 * (idles, moods, flight, wings...) see them as players: {@link #players} is the world's players plus the bots' bodies.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BotAvatars {
    private BotAvatars() {}

    private static final Map<PartyBotEntity, BotAvatar> AVATARS = new WeakHashMap<>();
    /** Bodies of the bots in the world right now (rebuilt each tick). */
    private static List<BotAvatar> inWorld = List.of();
    /** Off (falls back to the plain bot look) if drawing one ever fails. */
    public static boolean disabled = "0".equals(System.getenv("XW_BOT_AVATARS"));
    private static final boolean EF = net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("epicfight");

    /** The body this bot is drawn with (made on first use). */
    public static BotAvatar of(PartyBotEntity b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        BotAvatar a = AVATARS.get(b);
        if (a == null || a.level() != mc.level) {
            if (a != null) a.forget();
            a = new BotAvatar(mc.level, b);
            AVATARS.put(b, a);
        }
        return a;
    }

    /** A bot's body if it has one already (null otherwise). */
    public static BotAvatar existing(PartyBotEntity b) { return AVATARS.get(b); }

    /** The world's players, and the bot teammates' bodies (for animation code that goes over every player). */
    public static List<AbstractClientPlayer> players(ClientLevel level) {
        List<AbstractClientPlayer> real = level.players();
        if (inWorld.isEmpty() || disabled) return real;
        List<AbstractClientPlayer> out = new ArrayList<>(real.size() + inWorld.size());
        out.addAll(real);
        for (BotAvatar a : inWorld) if (a.level() == level) out.add(a);
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || disabled) {
            if (!AVATARS.isEmpty()) { AVATARS.values().forEach(BotAvatar::forget); AVATARS.clear(); }
            inWorld = List.of();
            return;
        }
        List<BotAvatar> now = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PartyBotEntity b) || b.isRemoved()) continue;
            BotAvatar a = of(b);
            if (a == null) continue;
            a.sync(b);
            now.add(a);
        }
        // bots that left: forget them
        AVATARS.entrySet().removeIf(en -> {
            if (en.getKey().isRemoved()) { en.getValue().forget(); return true; }
            return false;
        });
        inWorld = now;
        if (EF) com.xenowarriors.client.compat.epicfight.EfBotBodies.tick(now);   // (bots on Epic Fight's body, like players)
    }

    /** No name tag over the body (bots never had one). */
    @SubscribeEvent
    public static void onNameTag(net.minecraftforge.client.event.RenderNameTagEvent event) {
        if (event.getEntity() instanceof BotAvatar) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }
}

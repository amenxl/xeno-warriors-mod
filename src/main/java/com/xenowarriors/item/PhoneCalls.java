package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RequestPopupPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calling other players: pick someone on your phone's contacts screen, their phone rings (an Accept/Decline card), and
 * once they pick up you both hold your phones to your ears and - with Simple Voice Chat - hear each other anywhere in
 * the world. Either one hangs up by right-clicking the phone again (or putting it away).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhoneCalls {
    private static final int RING_TICKS = 30 * 20;
    private record Ring(UUID from, long until) {}
    private static final Map<UUID, Ring> RINGING = new HashMap<>();        // by who's being called
    private static final Map<UUID, UUID> CALLS = new HashMap<>();          // both ways

    private PhoneCalls() {}

    private static boolean hasPhone(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof PhoneItem) return true;
        return p.getOffhandItem().getItem() instanceof PhoneItem;
    }

    private static Component msg(String s, ChatFormatting c) { return Component.literal("📞 " + s).withStyle(c); }

    public static void call(ServerPlayer from, UUID toId) {
        ServerPlayer to = from.server.getPlayerList().getPlayer(toId);
        if (to == null || to == from) return;
        if (CALLS.containsKey(from.getUUID())) { from.displayClientMessage(msg("You're already on a call", ChatFormatting.GRAY), true); return; }
        if (!hasPhone(to)) { from.displayClientMessage(msg(to.getName().getString() + " doesn't have a phone on them", ChatFormatting.GRAY), true); return; }
        if (CALLS.containsKey(toId) || RINGING.containsKey(toId)) { from.displayClientMessage(msg(to.getName().getString() + " is busy", ChatFormatting.GRAY), true); return; }
        RINGING.put(toId, new Ring(from.getUUID(), from.level().getGameTime() + RING_TICKS));
        from.displayClientMessage(msg("Calling " + to.getName().getString() + "...", ChatFormatting.AQUA), true);
        ModNetwork.sendToPlayer(new RequestPopupPacket("Incoming Call", from.getName().getString() + " is calling you!",
                "/xeno phone answer", "/xeno phone decline", RING_TICKS, 0xFF5AD2FF), to);
    }

    public static void answer(ServerPlayer to) {
        Ring r = RINGING.remove(to.getUUID());
        ServerPlayer from = r == null ? null : to.server.getPlayerList().getPlayer(r.from);
        if (from == null || CALLS.containsKey(from.getUUID())) { to.displayClientMessage(msg("Nobody's calling", ChatFormatting.GRAY), true); return; }
        CALLS.put(from.getUUID(), to.getUUID());
        CALLS.put(to.getUUID(), from.getUUID());
        for (ServerPlayer p : new ServerPlayer[]{from, to}) {
            setOnCall(p, true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, 1.8F);
        }
        boolean voice = ModList.get().isLoaded("voicechat") && Voice.connect(from.getUUID(), to.getUUID());
        String note = voice ? "" : " (no voice chat - talk in chat)";
        from.sendSystemMessage(msg("Connected with " + to.getName().getString() + note, ChatFormatting.GREEN));
        to.sendSystemMessage(msg("Connected with " + from.getName().getString() + note, ChatFormatting.GREEN));
    }

    public static void decline(ServerPlayer to) {
        Ring r = RINGING.remove(to.getUUID());
        if (r == null) return;
        ServerPlayer from = to.server.getPlayerList().getPlayer(r.from);
        if (from != null) from.displayClientMessage(msg(to.getName().getString() + " declined the call", ChatFormatting.GRAY), true);
    }

    public static boolean inCall(ServerPlayer p) { return CALLS.containsKey(p.getUUID()); }

    /** Hangs up whatever call they're on (both sides). */
    public static void hangUp(ServerPlayer p) {
        UUID other = CALLS.remove(p.getUUID());
        setOnCall(p, false);
        if (other == null) return;
        CALLS.remove(other);
        ServerPlayer o = p.server.getPlayerList().getPlayer(other);
        if (o != null) {
            setOnCall(o, false);
            o.displayClientMessage(msg(p.getName().getString() + " hung up", ChatFormatting.GRAY), true);
            o.level().playSound(null, o.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
        }
        p.displayClientMessage(msg("Call ended", ChatFormatting.GRAY), true);
        if (ModList.get().isLoaded("voicechat")) Voice.disconnect(p.getUUID(), other);
    }

    /** Phone to the ear (moving one into your hand if it's in your bag), or put down. */
    public static void setOnCall(ServerPlayer p, boolean on) {
        Inventory inv = p.getInventory();
        ItemStack phone = p.getMainHandItem().getItem() instanceof PhoneItem ? p.getMainHandItem()
                : p.getOffhandItem().getItem() instanceof PhoneItem ? p.getOffhandItem() : ItemStack.EMPTY;
        if (phone.isEmpty() && on) {
            for (int i = 0; i < inv.items.size(); i++) {
                if (inv.items.get(i).getItem() instanceof PhoneItem) {
                    ItemStack held = inv.items.get(inv.selected);
                    inv.items.set(inv.selected, inv.items.get(i));
                    inv.items.set(i, held);
                    phone = inv.items.get(inv.selected);
                    break;
                }
            }
        }
        if (on && !phone.isEmpty()) phone.getOrCreateTag().putBoolean("XwOnCall", true);
        if (!on) {
            for (ItemStack s : inv.items) if (s.getItem() instanceof PhoneItem && s.hasTag()) s.getTag().putBoolean("XwOnCall", false);
            if (p.getOffhandItem().getItem() instanceof PhoneItem && p.getOffhandItem().hasTag()) p.getOffhandItem().getTag().putBoolean("XwOnCall", false);
        }
        inv.setChanged();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 5 == 0) ring(server, tick);
        if (tick % 20 != 0) return;
        long now = server.overworld().getGameTime();
        RINGING.entrySet().removeIf(e -> {
            if (now <= e.getValue().until) return false;
            ServerPlayer from = server.getPlayerList().getPlayer(e.getValue().from);
            if (from != null) from.displayClientMessage(msg("No answer", ChatFormatting.GRAY), true);
            return true;
        });
        // put the phone away: the call drops
        for (UUID id : new java.util.ArrayList<>(CALLS.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !PhoneItem.onCall(p)) {
                if (p != null) hangUp(p);
                else {
                    UUID o = CALLS.remove(id);
                    if (o != null) { CALLS.remove(o); ServerPlayer op = server.getPlayerList().getPlayer(o); if (op != null) hangUp(op); }
                }
            }
        }
    }

    /**
     * The phone ringing: a ringtone everyone nearby can hear (two quick bursts of notes, then a pause), music notes
     * popping off the person being called, and a reminder on their screen; the caller hears the line ringing.
     */
    private static void ring(MinecraftServer server, int tick) {
        int beat = (tick / 5) % 12;                         // 3 s cycle: notes on beats 0-3 and 5-8, quiet 9-11
        boolean note = beat < 4 || (beat >= 5 && beat < 9);
        float[] tune = {1.2F, 1.6F, 1.35F, 1.8F};
        for (var e : RINGING.entrySet()) {
            ServerPlayer to = server.getPlayerList().getPlayer(e.getKey());
            ServerPlayer from = server.getPlayerList().getPlayer(e.getValue().from);
            if (to == null) continue;
            if (note) {
                to.level().playSound(null, to.getX(), to.getY() + 1, to.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS,
                        0.9F, tune[beat % 4]);
                to.level().playSound(null, to.getX(), to.getY() + 1, to.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS,
                        0.5F, tune[beat % 4]);
                to.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, to.getX(), to.getY() + 2.1, to.getZ(),
                        1, 0.3, 0.1, 0.3, 1.0);
            }
            if (beat == 0) {
                to.displayClientMessage(msg((from != null ? from.getName().getString() : "Someone") + " is calling... [Y] answer  [X] decline",
                        ChatFormatting.AQUA), true);
                if (from != null) from.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 0.5F);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && CALLS.containsKey(p.getUUID())) hangUp(p);
        RINGING.remove(event.getEntity().getUUID());
    }

    /** (kept apart so nothing touches Simple Voice Chat's classes unless it's installed) */
    private static final class Voice {
        static boolean connect(UUID a, UUID b) { return com.xenowarriors.voice.VoiceCalls.connect(a, b); }
        static void disconnect(UUID a, UUID b) { com.xenowarriors.voice.VoiceCalls.disconnect(a, b); }
    }
}

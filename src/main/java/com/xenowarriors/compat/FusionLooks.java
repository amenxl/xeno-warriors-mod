package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fusions with their own look (original designs, tools/xw/fusion): Rhalix + Mikiko fuse into Rhaliko, Mikiko + Yuri
 * into Mikuri. While a player is fused (with a bot - DmzFusion - or with another player through DragonMineZ, as the
 * one leading), everyone sees them as the fusion: its own hair, outfit, details and faces.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class FusionLooks {
    private FusionLooks() {}

    public static final Map<Set<String>, String> PAIRS = Map.of(
            Set.of("rhalix", "mikiko"), "fusion_rhaliko",
            Set.of("mikiko", "yuri"), "fusion_mikuri");
    public static final Map<String, String> NAMES = Map.of("fusion_rhaliko", "Rhaliko", "fusion_mikuri", "Mikuri");

    private static final Map<UUID, String> SENT = new HashMap<>();

    /** The fusion look for these two characters, or null. */
    public static String lookFor(String a, String b) { return a == null || b == null || a.equals(b) ? null : PAIRS.get(Set.of(a, b)); }

    private static Method getStatus, isFused, isLeader, partner;

    /** The other character in this player's fusion (a bot's, or another player's when they lead a DragonMineZ fusion), or null. */
    static String partnerChar(ServerPlayer p) {
        String bot = DmzFusion.fusedChar(p);
        if (bot != null) return bot;
        if (!DmzFusion.ready()) return null;
        try {
            Object d = DmzPerCharacter.live(p);
            if (d == null) return null;
            if (getStatus == null) {
                getStatus = d.getClass().getMethod("getStatus");
                Class<?> st = getStatus.getReturnType();
                isFused = st.getMethod("isFused"); isLeader = st.getMethod("isFusionLeader"); partner = st.getMethod("getFusionPartnerUUID");
            }
            Object s = getStatus.invoke(d);
            if (!(Boolean) isFused.invoke(s) || !(Boolean) isLeader.invoke(s)) return null;
            UUID u = (UUID) partner.invoke(s);
            if (u == null || u.equals(p.getUUID())) return null;
            ServerPlayer q = p.server.getPlayerList().getPlayer(u);
            return q == null ? null : ModCapabilities.getType(q).getId();
        } catch (Throwable t) { return null; }
    }

    public static String current(ServerPlayer p) { return lookFor(ModCapabilities.getType(p).getId(), partnerChar(p)); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 3 || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        String now = current(p), was = SENT.get(p.getUUID());
        if (java.util.Objects.equals(now, was)) return;
        if (now == null) SENT.remove(p.getUUID()); else SENT.put(p.getUUID(), now);
        ModNetwork.sendToTrackingAndSelf(new FusionLookPacket(p.getId(), now == null ? "" : now), p);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer w && SENT.containsKey(t.getUUID()))
            ModNetwork.sendToPlayer(new FusionLookPacket(t.getId(), SENT.get(t.getUUID())), w);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SENT.remove(e.getEntity().getUUID()); }

    /** Server -> client: this player looks like that fusion ("" = themselves again). */
    public record FusionLookPacket(int id, String key) {
        public static void encode(FusionLookPacket m, net.minecraft.network.FriendlyByteBuf b) { b.writeVarInt(m.id); b.writeUtf(m.key); }
        public static FusionLookPacket decode(net.minecraft.network.FriendlyByteBuf b) { return new FusionLookPacket(b.readVarInt(), b.readUtf(64)); }
        public static void handle(FusionLookPacket m, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.xenowarriors.client.FusionLook.set(m.id, m.key.isEmpty() ? null : m.key));
            ctx.get().setPacketHandled(true);
        }
    }
}

package com.xenowarriors.kaeru;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.HoodStatePacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Kaeru's hoodie hood, up or down - her choice (Character Customization's Outfits tab, or the hood key), saved with
 * the player (kept through deaths and relogs) and shown to everyone who can see her.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KaeruHood {
    private static final String KEY = "xw_kaeru_hood";

    private KaeruHood() {}

    /** Playing Kaeru in her hoodie (her default outfit). */
    public static boolean applies(Player p) {
        return ModCapabilities.getType(p) == CharacterType.KAERU && com.xenowarriors.character.CharacterManager.outfit(p) == 0;
    }

    private static CompoundTag kept(ServerPlayer p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean up(ServerPlayer p) { return kept(p).getBoolean(KEY); }

    public static void set(ServerPlayer p, boolean up) {
        CompoundTag keep = kept(p);
        keep.putBoolean(KEY, up);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
        sync(p);
    }

    public static void sync(ServerPlayer p) {
        if (ModCapabilities.getType(p) == CharacterType.KAERU) ModNetwork.sendToTrackingAndSelf(new HoodStatePacket(p.getId(), up(p)), p);
    }

    /** Players last seen as Kaeru (switching to her sends her hood state once). */
    private static final java.util.Set<java.util.UUID> AS_KAERU = new java.util.HashSet<>();

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        boolean k = ModCapabilities.getType(p) == CharacterType.KAERU;
        if (k && AS_KAERU.add(p.getUUID())) sync(p);
        else if (!k) AS_KAERU.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AS_KAERU.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && ModCapabilities.getType(target) == CharacterType.KAERU)
            ModNetwork.sendToPlayer(new HoodStatePacket(target.getId(), up(target)), viewer);
    }
}

package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Client state that belongs to one world/server visit. Entity ids start again from scratch on another server
 * (and your own UUID is the same in every world), so per-entity caches left over from the last world would show
 * up on the wrong entity: poses, faces, wings, skill animations, sensed levels... Everything registered here is
 * emptied when you leave a world or server.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientReset {
    private ClientReset() {}

    private static final List<Map<?, ?>> MAPS = new ArrayList<>();
    private static final List<Collection<?>> COLLECTIONS = new ArrayList<>();
    private static final List<Runnable> HOOKS = new ArrayList<>();

    /** Registers a per-world map (emptied on logout); returns it. */
    public static synchronized <T extends Map<?, ?>> T track(T map) { MAPS.add(map); return map; }

    /** Registers a per-world set/list (emptied on logout); returns it. */
    public static synchronized <T extends Collection<?>> T trackAll(T c) { COLLECTIONS.add(c); return c; }

    /** Something else to reset on logout (fields that get replaced rather than emptied). */
    public static synchronized void onReset(Runnable r) { HOOKS.add(r); }

    /** How many maps/collections are registered (for the UI test). */
    public static synchronized int tracked() { return MAPS.size() + COLLECTIONS.size(); }

    /** Empties everything; also used by the UI test. */
    public static synchronized void resetAll() {
        for (Map<?, ?> m : MAPS) {
            try { m.clear(); } catch (RuntimeException e) { XenoWarriors.LOGGER.warn("ClientReset: couldn't clear a map", e); }
        }
        for (Collection<?> c : COLLECTIONS) {
            try { c.clear(); } catch (RuntimeException e) { XenoWarriors.LOGGER.warn("ClientReset: couldn't clear a list", e); }
        }
        // fields that are replaced rather than emptied
        ClashClient.set(false, 0.5F, 0, "");          // (an unfinished clash would keep eating every click in the next world)
        ScannerGlow.set(new int[0]);
        DataSenseClient.reset();
        KarmaScanClient.reset();
        ClientKarmaLock.reset();
        for (Runnable r : HOOKS) {
            try { r.run(); } catch (RuntimeException e) { XenoWarriors.LOGGER.warn("ClientReset: a reset hook failed", e); }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAll();
    }

    /** Texture lookups that check what's in the resource packs: looked up again when the packs change. */
    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Reload {
        private Reload() {}

        @SubscribeEvent
        public static void onRegister(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
                com.xenowarriors.client.render.CharacterDetailLayer.clearFaceCache();
                com.xenowarriors.client.cel.Cel.clear();
                SkillIcons.clear();
            });
        }
    }
}

package com.xenowarriors.gov;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prototype U-0's arena (a Prototype Facility's battlefield, walls included) can't be broken while the prototype is
 * there: no digging, no explosions, no mobs or skills tearing it up (DragonMineZ ki attacks included). Creative
 * operators can still build.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaProtect {
    private ArenaProtect() {}

    private record Zone(ResourceKey<Level> dim, AABB box) {}
    private static final Map<UUID, Zone> ZONES = new ConcurrentHashMap<>();   // by prototype
    /** (Tests) blocks kept from breaking. */
    public static int kept;

    /** (From the prototype) its arena is protected while it's there. */
    static void mark(UltimaPrototypeEntity boss) {
        if (boss.home() == null) return;
        ZONES.put(boss.getUUID(), new Zone(boss.level().dimension(), GovDungeons.arena(boss.home()).inflate(1.5, 1.0, 1.5)));
    }

    static void unmark(UltimaPrototypeEntity boss) { ZONES.remove(boss.getUUID()); }

    /** Inside a protected Prototype arena. */
    public static boolean protectedAt(LevelAccessor level, BlockPos pos) {
        if (ZONES.isEmpty() || !(level instanceof Level l)) return false;
        for (Zone z : ZONES.values()) if (z.dim == l.dimension() && z.box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return true;
        return false;
    }

    private static boolean builder(Player p) { return p.isCreative() && p.hasPermissions(2); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() != null && builder(e.getPlayer())) return;
        if (!protectedAt(e.getLevel(), e.getPos())) return;
        e.setCanceled(true);
        kept++;
        if (e.getPlayer() instanceof ServerPlayer sp && sp.tickCount % 10 == 0)
            sp.displayClientMessage(Component.literal("The Prototype's arena can't be broken").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (builder(e.getEntity()) || !protectedAt(e.getLevel(), e.getPos())) return;
        e.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        e.setCanceled(true);
    }

    /** Explosions still hurt, but the arena stays. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExplode(ExplosionEvent.Detonate e) {
        if (ZONES.isEmpty()) return;
        int n = e.getAffectedBlocks().size();
        e.getAffectedBlocks().removeIf(p -> protectedAt(e.getLevel(), p));
        kept += n - e.getAffectedBlocks().size();
    }

    @SubscribeEvent
    public static void onLivingDestroy(LivingDestroyBlockEvent e) {
        if (protectedAt(e.getEntity().level(), e.getPos())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMobGrief(EntityMobGriefingEvent e) {
        if (e.getEntity() != null && protectedAt(e.getEntity().level(), e.getEntity().blockPosition())) e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onStopped(net.minecraftforge.event.server.ServerStoppedEvent e) { ZONES.clear(); }
}

package com.xenowarriors.modes;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The game modes' arenas and the Hekublade's realm are not for digging:
 * - Journey and the Tower (both in the Tower dimension) and VR: no breaking or placing blocks at all.
 * - The Hekublade realm: nothing can be broken (placing is still fine - Mikiko furnishes his house).
 * Nothing else breaks blocks there either: explosions, mobs (endermen, ravagers, withers), trampled farmland, bots.
 * Operators in creative mode can still build (to fix or decorate an arena).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModeRules {
    private ModeRules() {}

    /** In one of the modes' arenas right now. */
    public static boolean inMode(Player p) {
        return com.xenowarriors.journey.Journey.inRun(p) || com.xenowarriors.tower.Tower.is(p.level()) || com.xenowarriors.vr.Vr.inVr(p);
    }

    /** A mode's arena (Journey, the Tower, VR): no building at all. */
    public static boolean arena(LevelAccessor level) {
        return level instanceof Level l && (com.xenowarriors.tower.Tower.is(l) || com.xenowarriors.vr.Vr.is(l));
    }

    /** May blocks be broken in this level (by anyone or anything)? */
    public static boolean canGrief(LevelAccessor level) {
        return !(arena(level) || (level instanceof Level l && l.dimension() == com.xenowarriors.mikiko.HekubladeRealm.KEY));
    }

    private static boolean builder(Player p) { return p.isCreative() && p.hasPermissions(2); }

    private static void tell(Player p, String what) {
        if (p instanceof ServerPlayer sp && sp.tickCount % 10 == 0) sp.displayClientMessage(Component.literal(what).withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent e) {
        Player p = e.getPlayer();
        if (p == null || builder(p)) return;
        if (!canGrief(e.getLevel()) || inMode(p)) { e.setCanceled(true); tell(p, "Nothing can be broken here"); }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!arena(e.getLevel())) {
            if (e.getEntity() instanceof Player p && inMode(p) && !builder(p)) { e.setCanceled(true); tell(p, "No building here"); }
            return;
        }
        if (e.getEntity() instanceof Player p && builder(p)) return;
        e.setCanceled(true);
        if (e.getEntity() instanceof Player p) tell(p, "No building here");
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        Player p = e.getEntity();
        if (builder(p)) return;
        if (!canGrief(p.level()) || inMode(p)) {
            e.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            e.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            e.setCanceled(true);
        }
    }

    /** Explosions still hurt, but leave the ground alone. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExplode(ExplosionEvent.Detonate e) {
        if (!canGrief(e.getLevel())) e.getAffectedBlocks().clear();
    }

    /** Endermen, ravagers, zombies on doors, silverfish, snow golems...: no griefing. */
    @SubscribeEvent
    public static void onMobGrief(EntityMobGriefingEvent e) {
        if (e.getEntity() != null && !canGrief(e.getEntity().level())) e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    /** Withers and dragons smashing through. */
    @SubscribeEvent
    public static void onLivingDestroy(LivingDestroyBlockEvent e) {
        if (!canGrief(e.getEntity().level())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent e) {
        if (!canGrief(e.getLevel())) e.setCanceled(true);
    }

    /** Fluids (lava/water buckets) can't be poured into an arena either. */
    @SubscribeEvent
    public static void onBucket(net.minecraftforge.event.entity.player.FillBucketEvent e) {
        Player p = e.getEntity();
        if (builder(p)) return;
        if (arena(p.level()) || inMode(p)) e.setCanceled(true);
    }
}

package com.xenowarriors.gov;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Ultima Prototype's arena is sealed while it's alive: whoever is in the arena when it's fighting can't leave
 * (walking, flying, warps, ender pearls, portals) until it's destroyed - or they go down. Party bots in there too.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class ArenaLock {
    private ArenaLock() {}

    /** Who's sealed in -> the boss's id (in that level). */
    private static final Map<UUID, Integer> SEALED = new HashMap<>();
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    /** (From the boss, every few ticks) seal in everyone inside its arena. */
    static void seal(UltimaPrototypeEntity boss, AABB arena) {
        if (!(boss.level() instanceof ServerLevel l)) return;
        for (Entity e : l.getEntitiesOfClass(Entity.class, arena, x -> x.isAlive() && (x instanceof ServerPlayer sp && !sp.isSpectator() && !sp.isCreative()
                || x instanceof com.xenowarriors.bot.PartyBotEntity))) SEALED.put(e.getUUID(), boss.getId());
    }

    /** The boss is gone: everyone's free. */
    static void release(UltimaPrototypeEntity boss) {
        SEALED.values().removeIf(id -> id == boss.getId());
    }

    public static boolean sealed(Entity e) { return SEALED.containsKey(e.getUUID()); }

    private static UltimaPrototypeEntity boss(Entity e) {
        Integer id = SEALED.get(e.getUUID());
        if (id == null) return null;
        if (e.level().getEntity(id) instanceof UltimaPrototypeEntity b && b.isAlive() && b.home() != null) return b;
        SEALED.remove(e.getUUID());
        return null;
    }

    private static void tell(Entity e) {
        if (!(e instanceof ServerPlayer p)) return;
        long now = p.level().getGameTime();
        if (now - TOLD.getOrDefault(p.getUUID(), -100L) < 60) return;
        TOLD.put(p.getUUID(), now);
        p.displayClientMessage(Component.literal("The arena is sealed - destroy Prototype U-0 to get out!").withStyle(ChatFormatting.RED), true);
    }

    /** Keep them inside: anyone outside the arena's walls is put back just inside. */
    static void hold(Entity e) {
        UltimaPrototypeEntity b = boss(e);
        if (b == null) return;
        if (e instanceof net.minecraft.world.entity.LivingEntity le && (!le.isAlive() || le instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator()))) { SEALED.remove(e.getUUID()); return; }
        AABB in = GovDungeons.arena(b.home()).deflate(1.0, 0, 1.0);
        Vec3 p = e.position();
        if (in.contains(p.x, Math.min(p.y, in.maxY - 2.1), p.z) && p.y < in.maxY) return;
        double x = Math.max(in.minX + 0.5, Math.min(in.maxX - 0.5, p.x)), z = Math.max(in.minZ + 0.5, Math.min(in.maxZ - 0.5, p.z));
        double y = Math.max(in.minY, Math.min(in.maxY - 2.1, p.y));
        if (e instanceof ServerPlayer sp) sp.connection.teleport(x, y, z, sp.getYRot(), sp.getXRot());
        else e.teleportTo(x, y, z);
        e.setDeltaMovement(Vec3.ZERO);
        tell(e);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p && !SEALED.isEmpty() && sealed(p)) hold(p);
    }

    @SubscribeEvent
    public static void onBotTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        if (!SEALED.isEmpty() && e.getEntity() instanceof com.xenowarriors.bot.PartyBotEntity b && !b.level().isClientSide && b.tickCount % 5 == 0 && sealed(b)) hold(b);
    }

    /** Ender pearls, chorus fruit, /tp-like warps: not out of the arena. */
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent e) {
        if (e instanceof EntityTeleportEvent.TeleportCommand || e instanceof EntityTeleportEvent.SpreadPlayersCommand) return;   // (commands still work)
        Entity en = e.getEntity();
        UltimaPrototypeEntity b = SEALED.isEmpty() ? null : boss(en);
        if (b == null) return;
        if (!GovDungeons.arena(b.home()).contains(e.getTargetX(), e.getTargetY(), e.getTargetZ())) { e.setCanceled(true); tell(en); }
    }

    @SubscribeEvent
    public static void onDimension(EntityTravelToDimensionEvent e) {
        if (!SEALED.isEmpty() && boss(e.getEntity()) != null) { e.setCanceled(true); tell(e.getEntity()); }
    }

    @SubscribeEvent
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) { SEALED.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { SEALED.remove(e.getEntity().getUUID()); }
}

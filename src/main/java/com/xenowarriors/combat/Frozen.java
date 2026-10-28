package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Frozen in place (the Xeno Blast cutscene): can't move, jump or attack until it ends. Mobs are held where they stood;
 * players are pulled back if they drift.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class Frozen {
    private record Hold(Vec3 pos, long until) {}
    private static final Map<Integer, Hold> HELD = new HashMap<>();

    private Frozen() {}

    public static void freeze(LivingEntity e, int ticks) {
        HELD.put(e.getId(), new Hold(e.position(), e.level().getGameTime() + ticks));
        if (e instanceof Mob m) { m.getNavigation().stop(); m.setTarget(null); }
    }

    public static void release(LivingEntity e) { HELD.remove(e.getId()); }

    public static boolean is(LivingEntity e) {
        Hold h = HELD.get(e.getId());
        return h != null && h.until > e.level().getGameTime();
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        Hold h = HELD.get(e.getId());
        if (h == null) return;
        if (h.until <= e.level().getGameTime() || !e.isAlive()) { HELD.remove(e.getId()); return; }
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(0, Math.min(0, v.y), 0);
        e.setSprinting(false);
        if (e instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
            if (e.position().distanceToSqr(h.pos) > 0.01) e.setPos(h.pos.x, e.getY(), h.pos.z);
        } else if (e instanceof ServerPlayer p && p.position().distanceToSqr(h.pos) > 0.25) {
            p.connection.teleport(h.pos.x, p.getY(), h.pos.z, p.getYRot(), p.getXRot());
        }
    }

    /** Frozen things can't attack either. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity a && is(a) && !(event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE))) {
            if (!(a instanceof ServerPlayer p && com.xenowarriors.skill.XenoBlast.casting(p))) event.setCanceled(true);
        }
    }
}

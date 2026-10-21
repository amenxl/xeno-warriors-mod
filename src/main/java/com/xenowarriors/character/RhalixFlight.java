package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rhalix can fly in survival (double-tap jump, like creative) on his wings - but only up to
 * MAX_HEIGHT blocks above whatever is under him. His own client stops him rising past it; the
 * server stops the flight if someone gets far beyond it anyway.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RhalixFlight {
    public static final double MAX_HEIGHT = 24;
    private static final String GRANTED = "xenowarriors_granted_flight";

    private RhalixFlight() {}

    public static boolean canFly(Player p) {
        return ModCapabilities.getType(p) == CharacterType.RHALIX && !p.isCreative() && !p.isSpectator();
    }

    /** Blocks between the player's feet and the ground/water below (MAX_HEIGHT + 16 if there's nothing in range). */
    public static double heightAboveGround(Player p) {
        Vec3 from = p.position();
        Vec3 to = from.subtract(0, MAX_HEIGHT + 16, 0);
        var hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.MISS ? MAX_HEIGHT + 16 : from.y - hit.getLocation().y;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        var abilities = p.getAbilities();
        boolean granted = p.getPersistentData().getBoolean(GRANTED);
        if (canFly(p)) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                p.getPersistentData().putBoolean(GRANTED, true);
                p.onUpdateAbilities();
            }
            // way past the limit (lag or a modified client): drop out of flight
            if (abilities.flying && heightAboveGround(p) > MAX_HEIGHT + 8) {
                abilities.flying = false;
                p.onUpdateAbilities();
            }
        } else if (granted && !p.isCreative() && !p.isSpectator()) {
            // switched away from Rhalix: take the flight back
            abilities.mayfly = false;
            abilities.flying = false;
            p.getPersistentData().remove(GRANTED);
            p.onUpdateAbilities();
        } else if (granted) {
            p.getPersistentData().remove(GRANTED);
        }
    }
}

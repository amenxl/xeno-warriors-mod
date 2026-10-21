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
    /** Starting flight height (blocks above the ground); each level he gains (any stat) lets him go one block higher. */
    public static final double BASE_HEIGHT = 20, MAX_LIMIT = 200;
    /** The limit as last told by the server (client side). */
    public static double clientMax = BASE_HEIGHT;
    /** Flying speed: normal at first, building up the longer he keeps flying forward (about 10 seconds to top speed). */
    private static final float BASE_SPEED = 0.05F, TOP_SPEED = 0.135F;
    private static final int SPEED_UP_TICKS = 200;
    private static final java.util.Map<java.util.UUID, double[]> CRUISE = new java.util.HashMap<>();   // {x, z, ticks}
    private static final String GRANTED = "xenowarriors_granted_flight";

    private RhalixFlight() {}

    public static boolean canFly(Player p) {
        return ModCapabilities.getType(p) == CharacterType.RHALIX && !p.isCreative() && !p.isSpectator() && RhalixWings.out(p);
    }

    /** How high he can fly: 20 blocks to start, +1 for every level he's gained in Speed, Attack and Defense. */
    public static double maxHeight(Player p) {
        if (p.level().isClientSide) return clientMax;
        int levels = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.statLevel[0] + d.statLevel[1] + d.statLevel[2]).orElse(0);
        return Math.min(MAX_LIMIT, BASE_HEIGHT + levels);
    }

    /** Blocks between the player's feet and the ground/water below (limit + 16 if there's nothing in range). */
    public static double heightAboveGround(Player p) {
        double range = maxHeight(p) + 16;
        Vec3 from = p.position();
        Vec3 to = from.subtract(0, range, 0);
        var hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.MISS ? range : from.y - hit.getLocation().y;
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
            if (p.tickCount % 40 == 0) {
                com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.FlightHeightPacket((float) maxHeight(p)), p);
            }
            // gaining speed while flying forward
            double[] c = CRUISE.computeIfAbsent(p.getUUID(), k -> new double[]{p.getX(), p.getZ(), 0});
            double moved = Math.hypot(p.getX() - c[0], p.getZ() - c[1]);
            c[0] = p.getX();
            c[1] = p.getZ();
            c[2] = abilities.flying && moved > 0.9 ? Math.min(SPEED_UP_TICKS, c[2] + 5) : Math.max(0, c[2] - 15);
            float speed = BASE_SPEED + (TOP_SPEED - BASE_SPEED) * (float) (c[2] / SPEED_UP_TICKS);
            if (Math.abs(abilities.getFlyingSpeed() - speed) > 0.002F) {
                abilities.setFlyingSpeed(speed);
                p.onUpdateAbilities();
            }
            if (abilities.flying && heightAboveGround(p) > maxHeight(p) + 8) {
                abilities.flying = false;
                p.onUpdateAbilities();
            }
        } else if (granted && !p.isCreative() && !p.isSpectator()) {
            // switched away from Rhalix: take the flight back
            abilities.mayfly = false;
            abilities.flying = false;
            abilities.setFlyingSpeed(BASE_SPEED);
            CRUISE.remove(p.getUUID());
            p.getPersistentData().remove(GRANTED);
            p.onUpdateAbilities();
        } else if (granted) {
            p.getPersistentData().remove(GRANTED);
        }
    }
}

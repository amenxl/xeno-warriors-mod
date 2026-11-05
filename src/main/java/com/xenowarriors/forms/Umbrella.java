package com.xenowarriors.forms;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Heku-umbrella: rain doesn't reach whoever holds it open (Netsu checks {@link #covers}; the client stops the rain
 * at the canopy), and falling with it out is a glide - down at about 2.4 blocks a second, drifting forward the way
 * he faces, no fall damage.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Umbrella {
    public static final double GLIDE_FALL = -0.12, DRIFT = 0.03, MAX_DRIFT = 0.2;
    /** Canopy: centred this high over the feet, this wide (radius, blocks). */
    public static final double HEIGHT = 2.55, RADIUS = 1.15;

    private Umbrella() {}

    public static boolean holding(Player p) {
        return p.getMainHandItem().getItem() instanceof UmbrellaItem;
    }

    public static boolean covers(Player p) { return holding(p) || p.getOffhandItem().getItem() instanceof UmbrellaItem; }

    /** Falling with it open (not flying, swimming, riding or climbing). */
    public static boolean gliding(Player p) {
        return covers(p) && !p.onGround() && !p.getAbilities().flying && !p.isInWater() && !p.isPassenger() && !p.onClimbable()
                && !p.isFallFlying() && p.getDeltaMovement().y < 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player p = event.player;
        if (!gliding(p)) return;
        if (!p.level().isClientSide) { p.resetFallDistance(); return; }        // (the server keeps the fall harmless)
        if (!p.isLocalPlayer()) return;                                          // (the moving player's own client steers)
        Vec3 v = p.getDeltaMovement();
        double y = Math.max(v.y, GLIDE_FALL);
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
        double hx = v.x + f.x * DRIFT, hz = v.z + f.z * DRIFT, h = Math.sqrt(hx * hx + hz * hz);
        if (h > MAX_DRIFT) { hx *= MAX_DRIFT / h; hz *= MAX_DRIFT / h; }
        p.setDeltaMovement(hx, y, hz);
        p.resetFallDistance();
    }
}

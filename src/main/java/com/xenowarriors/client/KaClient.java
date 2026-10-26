package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.ka.Ka;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** KAs that work on your controls: Grip (you slide), Flip (left/right and forward/back swap), Sight (your camera locks on). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KaClient {
    private static int grip, flip, sight, sightSrc = -1;
    private static Vec3 slide = Vec3.ZERO;

    private KaClient() {}

    public static void set(int ka, int ticks, int src) {
        if (ka < 0) { grip = flip = sight = 0; return; }
        Ka k = Ka.values()[ka];
        switch (k) {
            case GRIP -> grip = Math.max(grip, ticks);
            case FLIP -> flip = Math.max(flip, ticks);
            case SIGHT -> { sight = Math.max(sight, ticks); sightSrc = src; }
            default -> {}
        }
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (flip <= 0) return;
        var in = event.getInput();
        in.leftImpulse = -in.leftImpulse;
        in.forwardImpulse = -in.forwardImpulse;
        boolean l = in.left; in.left = in.right; in.right = l;
        boolean u = in.up; in.up = in.down; in.down = u;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        var p = Minecraft.getInstance().player;
        if (event.phase != TickEvent.Phase.END || p == null) return;
        if (flip > 0) flip--;
        if (grip > 0) {
            grip--;
            // no friction: keep most of the old momentum, steering barely changes it
            Vec3 v = p.getDeltaMovement();
            slide = new Vec3(slide.x * 0.93 + v.x * 0.12, 0, slide.z * 0.93 + v.z * 0.12);
            p.setDeltaMovement(slide.x * 1.6, v.y, slide.z * 1.6);
        } else slide = Vec3.ZERO;
        if (sight > 0) {
            sight--;
            Entity src = p.level().getEntity(sightSrc);
            if (src != null) {
                Vec3 d = src.getEyePosition().subtract(p.getEyePosition());
                float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F);
                float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                p.setYRot(p.getYRot() + net.minecraft.util.Mth.wrapDegrees(yaw - p.getYRot()) * 0.6F);
                p.setXRot(p.getXRot() + (pitch - p.getXRot()) * 0.6F);
            }
        }
    }
}

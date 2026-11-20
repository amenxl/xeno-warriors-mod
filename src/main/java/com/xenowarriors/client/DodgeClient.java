package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Weaving out of gunfire (BulletDodge): a quick bob-and-weave - ducking and slipping the upper body to one side, guard
 * up at the face, then back - or, knocking a bullet back, the same lean with a backhand swipe across. Players and bots
 * alike, on our model (Epic Fight doesn't draw them for the moment it lasts). Also tells the server when your Epic
 * Fight guard is up (only your client knows it).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DodgeClient {
    private DodgeClient() {}

    public static final float LENGTH = 9F;
    private record At(long start, int side, int kind) {}
    private static final Map<Integer, At> ON = ClientReset.track(new HashMap<>());
    /** Models we moved last frame (head, body and arms shifted sideways: put back when no one's weaving). */
    private static final Map<HumanoidModel<?>, Boolean> MOVED = new java.util.WeakHashMap<>();
    /** (Tests) weaves seen. */
    public static int seen;

    public static void set(int id, int side, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ON.put(id, new At(mc.level.getGameTime(), side == 0 ? 1 : side, kind));
        seen++;
    }

    /** How far into the weave (0..1), or -1. */
    public static float progress(Entity e, float partial) {
        At a = ON.get(e.getId());
        Minecraft mc = Minecraft.getInstance();
        if (a == null || mc.level == null) return -1F;
        float t = (mc.level.getGameTime() - a.start + partial) / LENGTH;
        if (t >= 1F) { if (t > 2F) ON.remove(e.getId()); return -1F; }
        return Math.max(0F, t);
    }

    public static boolean weaving(Entity e) { return progress(e, Minecraft.getInstance().getFrameTime()) >= 0F; }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        float t = progress(p, Minecraft.getInstance().getFrameTime());
        if (t < 0F) {
            if (MOVED.remove(m) != null) { m.head.x = 0F; m.hat.x = 0F; m.body.x = 0F; }
            return;
        }
        At a = ON.get(p.getId());
        float w = Mth.sin(t * Mth.PI);                                           // in and back out
        float s = a.side;
        float off = s * 3.2F * w, duck = 2.6F * w;
        MOVED.put(m, true);
        m.head.x = off * 1.25F; m.hat.x = m.head.x;
        m.head.y += duck; m.hat.y = m.head.y;
        m.head.zRot = s * 0.3F * w;
        m.body.x = off * 0.6F;
        m.body.y += duck * 0.6F;
        m.body.zRot = s * 0.28F * w;
        m.body.xRot += 0.38F * w;
        m.rightArm.x += off * 0.9F; m.leftArm.x += off * 0.9F;
        m.rightArm.y += duck * 0.8F; m.leftArm.y += duck * 0.8F;
        // guard up at the face
        m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -1.75F);
        m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.45F);
        m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, 0.15F);
        if (a.kind == 1) {                                                        // knocking it back: a backhand swipe across
            m.rightArm.xRot = -1.55F;
            m.rightArm.yRot = Mth.lerp(t, 0.9F, -0.9F);
            m.rightArm.zRot = 0F;
        } else {
            m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -1.75F);
            m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.45F);
            m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, -0.15F);
        }
        if (m.leftLeg != null) { m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -0.25F); m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, 0.2F); }
    }

    private static boolean lastGuard;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) { lastGuard = false; return; }
        boolean g = false;
        try {
            if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) g = com.xenowarriors.client.compat.epicfight.EpicFightCompat.guarding(mc.player);
        } catch (Throwable ignored) {}
        if (g != lastGuard) {
            lastGuard = g;
            com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.GuardStatePacket(g));
        }
    }
}

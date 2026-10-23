package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Origins 1 outfit: sprinting, young Mikiko drops onto all fours and runs like a fox - his whole body tips forward from
 * the hips, hands and feet bounding in a gallop, head up, tail streaming out behind.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FoxRun {
    private static final Map<Integer, float[]> W = new HashMap<>();   // id -> {weight, previous weight}
    private static final Set<Integer> TIPPED = new HashSet<>();
    private static final float TILT = 78F;

    private FoxRun() {}

    private static boolean wants(AbstractClientPlayer p) {
        return ClientCharacterData.outfit(p) == ClientCharacterData.ORIGINS_1 && p.isSprinting() && !p.isPassenger()
                && !p.isSwimming() && !p.isInWater() && !p.isFallFlying() && !p.isUsingItem() && !p.getAbilities().flying
                && !FlightTracker.isFlying(p) && !Busy.busy(p) && !com.xenowarriors.client.mikiko.MikikoAnimations.attacking(p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            boolean on = wants(p);
            float[] w = W.get(p.getId());
            if (w == null) {
                if (!on) continue;
                W.put(p.getId(), w = new float[2]);
            }
            w[1] = w[0];
            w[0] = Mth.clamp(w[0] + (on ? 0.25F : -0.2F), 0F, 1F);
            if (w[0] <= 0F && w[1] <= 0F) W.remove(p.getId());
        }
        W.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /** 0..1, eased. */
    public static float weight(Player p) {
        float[] w = W.get(p.getId());
        if (w == null) return 0F;
        float t = Mth.lerp(Minecraft.getInstance().getFrameTime(), w[1], w[0]);
        return t * t * (3 - 2 * t);
    }

    /** Limbs for the gallop (the body itself is tipped over in onRenderPre). Applied after the other run poses. */
    public static void apply(HumanoidModel<?> m, Player p, float age) {
        float w = weight(p);
        if (w <= 0F) return;
        float pos = p.walkAnimation.position(Minecraft.getInstance().getFrameTime());
        float ph = pos * 0.75F;
        float front = Mth.sin(ph), back = Mth.sin(ph + (float) Math.PI * 0.9F);
        boolean airborne = !p.onGround();
        float reach = airborne ? 0.5F : 0F;                                   // stretched out mid-bound
        m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -1.5F + front * 0.75F - reach);
        m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -1.5F + Mth.sin(ph + 0.45F) * 0.75F - reach);
        m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, 0F);
        m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0F);
        m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.08F);
        m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.08F);
        m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, -1.35F + back * 0.7F + reach);
        m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -1.35F + Mth.sin(ph + (float) Math.PI * 0.9F + 0.45F) * 0.7F + reach);
        m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, 0F);
        m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, 0F);
        m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, 0.06F);
        m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, -0.06F);
        m.rightLeg.z = Mth.lerp(w, m.rightLeg.z, 0F);
        m.leftLeg.z = Mth.lerp(w, m.leftLeg.z, 0F);
        m.rightLeg.y = Mth.lerp(w, m.rightLeg.y, 12F);
        m.leftLeg.y = Mth.lerp(w, m.leftLeg.y, 12F);
        // hind legs fold like a fox's; front paws flick back as they push off
        LimbBend.extraKnees(p, (0.9F + Math.max(0F, back) * 0.6F) * w, (0.9F + Math.max(0F, -back) * 0.6F) * w);
        LimbBend.extraElbows(p, Math.max(0F, -front) * 0.5F * w, Math.max(0F, front) * 0.5F * w);
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0F);
        m.body.yRot = Mth.lerp(w, m.body.yRot, 0F);
        m.body.zRot = Mth.lerp(w, m.body.zRot, 0F);
        m.head.xRot = Mth.lerp(w, m.head.xRot, -1.25F + Mth.sin(ph * 2F) * 0.06F);   // head up, looking ahead
        m.head.zRot = Mth.lerp(w, m.head.zRot, 0F);
        m.hat.copyFrom(m.head);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer p) || FirstPersonHead.ownFirstPerson(p)) return;
        float w = weight(p);
        if (w <= 0F) return;
        var pose = event.getPoseStack();
        PoseGuard.push(pose);
        TIPPED.add(p.getId());
        float yaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        float bob = Mth.abs(Mth.sin(p.walkAnimation.position(event.getPartialTick()) * 0.75F)) * 0.06F * w;
        pose.translate(0, 0.75 + bob, 0);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(TILT * w));           // from the hips, chest down
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        pose.translate(0, -0.75, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (TIPPED.remove(event.getEntity().getId())) PoseGuard.pop(event.getPoseStack());
    }
}

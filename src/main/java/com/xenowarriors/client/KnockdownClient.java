package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.anim.Poses;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Knocked down ({@link Poses#KNOCKDOWN}, Rhalix's Gravity Whirl): anyone - a zombie, a player, any mob - goes over
 * backwards and lies flat on their back, swaying a little, dazed stars circling their head (drawn by RhalixComboFx),
 * then gets back up. The whole body is tipped over, so it works on any model.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KnockdownClient {
    private static final Set<Integer> TIPPED = ClientReset.perLifeSet(new HashSet<>());
    /** Who was drawn lying down this frame (for the stars): {entity, how far over 0..1}. */
    static final List<Object[]> DOWN = new ArrayList<>();

    /** (tests) how many times someone was drawn tipped over. */
    public static int tipped;

    private KnockdownClient() {}

    /** How far over they are right now (0 standing .. 1 flat on their back). */
    public static float down(LivingEntity e, float partial) {
        float[] s = EntityPoses.state(e, Poses.KNOCKDOWN, partial);
        if (s == null) return 0F;
        float t = s[0], dur = s[1];
        return smooth(t / 3.5F) * (1F - smooth((t - (dur - 5F)) / 5F));
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    /** A mob Epic Fight draws itself (in this event, at normal priority): tipped over before it does. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPreEarly(RenderLivingEvent.Pre<?, ?> event) {
        if (PoseGuard.otherModDraws.test(event.getEntity())) onPre(event);
    }

    /** It was drawn (and the event cancelled) by the other mod: no "after" event comes, so close the transform here. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPreDone(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() && TIPPED.remove(event.getEntity().getId())) PoseGuard.pop(event.getPoseStack());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (TIPPED.contains(e.getId())) return;                               // (already tipped, early)
        float w = down(e, event.getPartialTick());
        if (w <= 0F) return;
        PoseStack pose = event.getPoseStack();
        PoseGuard.push(pose);
        TIPPED.add(e.getId());
        tipped++;
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot);
        float t = e.tickCount + event.getPartialTick();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, e.getBbWidth() * 0.4F * w, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-86F * w));                    // over onto the back
        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.35F) * 6F * w));   // rolling his head about, dazed
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        DOWN.add(new Object[]{e, w});
    }

    @SubscribeEvent
    public static void onPost(RenderLivingEvent.Post<?, ?> event) {
        if (TIPPED.remove(event.getEntity().getId())) PoseGuard.pop(event.getPoseStack());
    }
}

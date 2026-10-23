package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Our render tweaks push the pose before an entity is drawn and pop it after. If another mod (Epic Fight, DragonMineZ,
 * a bridge between them...) cancels the drawing in between, the "after" event never comes and the leftover push
 * crashes the game ("Pose stack not empty"). Every push goes through here; anything still open once the entities are
 * drawn gets popped, and a pop never happens without its push.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PoseGuard {
    private static final Deque<PoseStack> OPEN = new ArrayDeque<>();
    private static boolean warned;

    private PoseGuard() {}

    public static void push(PoseStack stack) {
        stack.pushPose();
        OPEN.push(stack);
    }

    public static void pop(PoseStack stack) {
        if (OPEN.remove(stack)) stack.popPose();       // (removes the most recent push on this stack)
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || OPEN.isEmpty()) return;
        PoseStack stack = event.getPoseStack();
        int closed = 0;
        for (Iterator<PoseStack> it = OPEN.iterator(); it.hasNext(); ) {
            if (it.next() == stack) {
                it.remove();
                stack.popPose();
                closed++;
            }
        }
        if (closed > 0 && !warned) {
            warned = true;
            XenoWarriors.LOGGER.info("Closed {} render transform(s) another mod's renderer left open", closed);
        }
    }
}

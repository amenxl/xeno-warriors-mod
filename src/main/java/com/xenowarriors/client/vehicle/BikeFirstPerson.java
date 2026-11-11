package com.xenowarriors.client.vehicle;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Riding the motorbike in first person: your own body is drawn (head left out - FirstPersonHead), so looking down you
 * see yourself leaning over the tank with your hands on the bars. The floating first-person arm goes while your hands
 * are empty (they're on the bars); holding something, it stays as usual.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BikeFirstPerson {
    private BikeFirstPerson() {}

    /** (tests) frames the body was drawn. */
    public static int drawn;

    private static boolean active(Minecraft mc) {
        return mc.player != null && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()
                && VehicleRiderPose.BIKE_POSE && VehicleRiderPose.onBike(mc.player);
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) return;
        var p = mc.player;
        var cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        double x = Mth.lerp(pt, p.xo, p.getX()) - cam.x, y = Mth.lerp(pt, p.yo, p.getY()) - cam.y, z = Mth.lerp(pt, p.zo, p.getZ()) - cam.z;
        var buffers = mc.renderBuffers().bufferSource();
        var d = mc.getEntityRenderDispatcher();
        boolean shadows = mc.options.entityShadows().get();
        d.setRenderShadow(false);
        try {
            d.render(p, x, y, z, Mth.lerp(pt, p.yRotO, p.getYRot()), pt, event.getPoseStack(), buffers, d.getPackedLightCoords(p, pt));
            buffers.endBatch();
            drawn++;
        } finally {
            d.setRenderShadow(shadows);
        }
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (active(mc) && event.getItemStack().isEmpty()) event.setCanceled(true);
    }
}

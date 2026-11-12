package com.xenowarriors.client.bot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/** A bot flying on its Flying Nimbus: DragonMineZ's own nimbus drawn under its feet (a stand-in cloud, never in the world). */
public final class NimbusUnder {
    private NimbusUnder() {}

    private static Entity cloud;
    private static boolean broken;

    /** Riding the cloud right now (flying on its nimbus). */
    public static boolean riding(PartyBotEntity b) { return b.nimbus() && b.isNoGravity() && !b.isPassenger(); }

    public static void render(PartyBotEntity b, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (broken || !riding(b)) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (cloud == null || cloud.level() != mc.level) {
                EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dragonminez", "flying_nimbus"));
                cloud = t == null ? null : t.create(mc.level);
                if (cloud == null) { broken = true; return; }
            }
            cloud.setPos(b.getX(), b.getY() - 0.35, b.getZ());
            cloud.xo = b.xo; cloud.yo = b.yo - 0.35; cloud.zo = b.zo;
            cloud.setYRot(b.yBodyRot); cloud.yRotO = b.yBodyRotO;
            if (cloud instanceof net.minecraft.world.entity.LivingEntity l) { l.yBodyRot = b.yBodyRot; l.yBodyRotO = b.yBodyRotO; l.tickCount = b.tickCount; }
            var base = com.xenowarriors.client.PoseGuard.mark(pose);
            pose.pushPose();
            pose.translate(0, -0.35, 0);
            try { mc.getEntityRenderDispatcher().getRenderer(cloud).render(cloud, b.yBodyRot, pt, pose, buffers, light); }
            finally { com.xenowarriors.client.PoseGuard.restore(pose, base); }
        } catch (Throwable t) {
            broken = true;
            com.xenowarriors.XenoWarriors.LOGGER.warn("XW: couldn't draw a bot's Flying Nimbus", t);
        }
    }
}

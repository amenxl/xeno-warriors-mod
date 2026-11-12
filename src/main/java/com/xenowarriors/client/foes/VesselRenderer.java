package com.xenowarriors.client.foes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.npc.NpcRenderer;
import com.xenowarriors.foes.VesselEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Vessels: a Shadow Recarnet member's own look - drained dark with red eyes once the demon inside has taken over, and
 * the demon's bat wings growing out of their back while it fuses (full size and beating once it has).
 */
public class VesselRenderer extends NpcRenderer<VesselEntity> {
    public VesselRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false), VesselEntity::look,
                e -> e.state() == VesselEntity.FUSED);
        addLayer(new Wings(this));
    }

    private static class Wings extends RenderLayer<VesselEntity, PlayerModel<VesselEntity>> {
        Wings(RenderLayerParent<VesselEntity, PlayerModel<VesselEntity>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, VesselEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float yaw, float pitch) {
            float grow = e.fuseProgress();
            if (grow <= 0.02F || e.isInvisible()) return;
            var m = NpcRenderer.model("wing_bat");
            if (m == null || m.rightWing == null || m.leftWing == null) return;
            boolean fused = e.state() == VesselEntity.FUSED;
            m.setupWings(true, fused && !e.onGround(), age * 1.4F, fused ? 0.4F : 0F);
            var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(NpcRenderer.tex("wing_bat_details")));
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0F);
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            for (var w : new net.minecraft.client.model.geom.ModelPart[]{m.rightWing, m.leftWing}) {
                w.xScale = w.yScale = w.zScale = 0.25F + 0.95F * grow;
                w.render(pose, vc, light, overlay, 0.35F, 0.12F, 0.16F, 1F);      // demon-dark, a red tinge
                w.xScale = w.yScale = w.zScale = 1F;
            }
            pose.popPose();
        }
    }
}

package com.xenowarriors.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.render.DetailModel;
import com.xenowarriors.sr.SrMemberEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** SR members: their Skylander wings only show while they've summoned them to fly - dark, tattered wings, beating. */
public class SrRenderer extends NpcRenderer<SrMemberEntity> {
    private static final String STYLE = "tattered";

    public SrRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SrModel(ctx.bakeLayer(ModelLayers.PLAYER)), new SrModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM)), SrMemberEntity::look, e -> false);
        addLayer(new Wings(this));
    }

    @Override
    protected void setupRotations(SrMemberEntity e, PoseStack pose, float bob, float yaw, float pt) {
        super.setupRotations(e, pose, bob, yaw, pt);
        float lean = SrModel.lean(e, pt);
        if (lean != 0F) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(lean));
        float spin = SrModel.spin(e, pt);
        if (spin > 0) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-360F * spin));
        boolean forcedFly = "fly".equals(SrModel.force);
        if (!e.wings() && !forcedFly) return;
        float sp = forcedFly ? 0.6F : (float) Math.min(1.0, e.getDeltaMovement().horizontalDistance() / 0.3);
        pose.translate(0, net.minecraft.util.Mth.sin(bob * 0.45F) * 0.08F, 0);
        pose.translate(0, 0.9F, 0);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(55F * sp));
        pose.translate(0, -0.9F, 0);
    }

    private static class Wings extends RenderLayer<SrMemberEntity, PlayerModel<SrMemberEntity>> {
        Wings(RenderLayerParent<SrMemberEntity, PlayerModel<SrMemberEntity>> p) { super(p); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, SrMemberEntity e, float limb, float limbAmt, float pt, float age, float yaw, float pitch) {
            if ((!e.wings() && !"fly".equals(SrModel.force)) || e.isInvisible()) return;
            String k = e.key();
            if (k.equals("persephone") || k.equals("ava")) return;             // demons, not Skylanders: they just float
            // the named members' own wings
            String style = switch (k) { case "senju" -> "falcon"; case "logan" -> "hawk"; case "keita" -> "bat"; case "maddie" -> "angel_maddie"; default -> STYLE; };
            DetailModel m = NpcRenderer.model("wing_" + style);
            if (m == null || m.rightWing == null || m.leftWing == null) return;
            m.setupWings(true, true, (SrModel.force != null ? SrModel.clock : age) * 1.3F, 0F);
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(NpcRenderer.tex("wing_" + style + "_details")));
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0F);
            float r = e.demon() ? 0.55F : 0.3F, g = 0.26F, b = e.demon() ? 0.3F : 0.34F;     // dark wings (reddish once the demon's awake)
            switch (k) {
                case "senju" -> { r = 0.16F; g = 0.13F; b = 0.15F; }
                case "logan" -> { r = 1F; g = 0.86F; b = 0.3F; }
                case "keita" -> { r = 0.18F; g = 0.2F; b = 0.42F; }
                case "maddie" -> { r = 1F; g = 1F; b = 1F; }
                default -> {}
            }
            m.rightWing.render(pose, vc, light, overlay, r, g, b, 1F);
            m.leftWing.render(pose, vc, light, overlay, r, g, b, 1F);
            pose.popPose();
        }
    }
}

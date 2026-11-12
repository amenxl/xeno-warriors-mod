package com.xenowarriors.client.you;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;

/** A Devil (a You character awakened by YouDevil): two curved horns, and a dark aura - in demon form only. */
public class DevilHornsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/devil_horns.png");
    private static ModelPart horns;

    public DevilHornsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    private static ModelPart horns() {
        if (horns == null) {
            MeshDefinition mesh = new MeshDefinition();
            var root = mesh.getRoot();
            for (int s = -1; s <= 1; s += 2) {
                var base = root.addOrReplaceChild("base" + s, CubeListBuilder.create().texOffs(0, 0).addBox(-1F, -3F, -1F, 2F, 3F, 2F),
                        PartPose.offsetAndRotation(s * 2.6F, -7.6F, -1.5F, -0.25F, 0F, s * 0.45F));
                var mid = base.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(0, 5).addBox(-0.75F, -3F, -0.75F, 1.5F, 3F, 1.5F),
                        PartPose.offsetAndRotation(0F, -2.7F, 0F, -0.35F, 0F, s * -0.35F));
                mid.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 10).addBox(-0.5F, -2.5F, -0.5F, 1F, 2.5F, 1F),
                        PartPose.offsetAndRotation(0F, -2.7F, 0F, -0.45F, 0F, s * -0.3F));
            }
            horns = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        }
        return horns;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float yaw, float pitch) {
        var s = ClientCharacterData.you(p);
        if (s == null || !s.devil || s.humanForm || p.isInvisible()) return;
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        horns().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, LivingEntityRenderer.getOverlayCoords(p, 0F));
        pose.popPose();
        if (p.tickCount % 6 == 0 && p.level().random.nextFloat() < 0.7F)                 // the dark aura
            p.level().addParticle(p.level().random.nextInt(4) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE, p.getRandomX(0.7), p.getRandomY(), p.getRandomZ(0.7), 0, 0.02, 0);
    }
}

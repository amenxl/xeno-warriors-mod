package com.xenowarriors.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** DragonMineZ compatibility: a Saiyan Mikiko grows a bushy fox tail (orange with a white tip) instead of a Saiyan tail. */
public class FoxTailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("fox_tail"), "main");
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/character/fox_tail.png");
    private static final ResourceLocation RED = XenoWarriors.id("textures/entity/character/fox_tail_red.png");

    /** Origins outfits: young Mikiko still had his red fox tail out. */
    public static boolean outfitTail(AbstractClientPlayer p) {
        int o = ClientCharacterData.outfit(p);
        return o == ClientCharacterData.ORIGINS_1 || o == ClientCharacterData.ORIGINS_2;
    }
    private final ModelPart tail, mid, tip;

    public FoxTailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(LAYER);
        this.tail = root.getChild("tail");
        this.mid = tail.getChild("mid");
        this.tip = mid.getChild("tip");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition t = r.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, 0, -1.5F, 3, 5, 3),
                PartPose.offset(0, 10.5F, 2.2F));
        PartDefinition m = t.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(12, 0).addBox(-2.5F, 0, -2.5F, 5, 6, 5),
                PartPose.offset(0, 4.2F, 0));
        m.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 11).addBox(-2, 0, -2, 4, 4, 4), PartPose.offset(0, 5.5F, 0));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float yaw, float pitch) {
        if (p.isInvisible() || ClientCharacterData.get(p) != CharacterType.MIKIKO) return;
        boolean outfit = outfitTail(p);
        if (!outfit && !(DmzCompat.tail(p) && !DmzForms.giantFox(p))) return;
        if (DmzForms.giantFox(p)) return;
        float run = com.xenowarriors.client.FoxRun.weight(p);
        // sticks out behind and sways - livelier when he runs
        float sway = Mth.sin(age * 0.12F) * 0.25F + Mth.sin(limbSwing * 0.6F) * 0.3F * limbSwingAmount;
        tail.xRot = 0.95F + limbSwingAmount * 0.4F + Mth.sin(age * 0.07F) * 0.06F + run * 0.5F;   // streams out behind on all fours
        tail.yRot = sway;
        tail.zRot = 0F;
        mid.xRot = -0.25F + Mth.sin(age * 0.09F + 0.6F) * 0.08F;
        mid.yRot = sway * 0.6F;
        tip.xRot = -0.2F;
        tip.yRot = sway * 0.4F;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        tail.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(outfit ? RED : TEX)), light, LivingEntityRenderer.getOverlayCoords(p, 0F));
        pose.popPose();
    }
}

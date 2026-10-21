package com.xenowarriors.client.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

/**
 * A sword held crosswise in a fox's mouth, built as a real 3D sword (pommel, grip, cross-guard, blade with
 * a bright edge and a point) and colored by what it's made of - instead of the flat item sprite.
 */
public class FoxSwordLayer extends RenderLayer<Fox, FoxModel<Fox>> {
    private static final ResourceLocation TEXTURE = XenoWarriors.id("textures/entity/fox/fox_sword.png");
    private final ModelPart blade, edge, guard, grip, pommel;

    public FoxSwordLayer(RenderLayerParent<Fox, FoxModel<Fox>> parent) {
        super(parent);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("blade", CubeListBuilder.create()
                .texOffs(0, 0).addBox(2.2F, -1F, -0.3F, 9F, 2F, 0.6F)
                .texOffs(0, 4).addBox(11.2F, -0.6F, -0.25F, 1F, 1.2F, 0.5F)
                .texOffs(0, 4).addBox(12.2F, -0.25F, -0.2F, 0.6F, 0.5F, 0.4F), PartPose.ZERO);
        r.addOrReplaceChild("edge", CubeListBuilder.create()
                .texOffs(0, 8).addBox(2.2F, -1.05F, -0.15F, 9.2F, 0.35F, 0.3F, new CubeDeformation(0.02F)), PartPose.ZERO);
        r.addOrReplaceChild("guard", CubeListBuilder.create()
                .texOffs(0, 10).addBox(1.4F, -2.6F, -0.65F, 0.8F, 5.2F, 1.3F), PartPose.ZERO);
        r.addOrReplaceChild("grip", CubeListBuilder.create()
                .texOffs(16, 10).addBox(-1.6F, -0.5F, -0.5F, 3F, 1F, 1F), PartPose.ZERO);
        r.addOrReplaceChild("pommel", CubeListBuilder.create()
                .texOffs(24, 10).addBox(-2.6F, -0.8F, -0.8F, 1F, 1.6F, 1.6F), PartPose.ZERO);
        ModelPart root = LayerDefinition.create(mesh, 32, 16).bakeRoot();
        blade = root.getChild("blade");
        edge = root.getChild("edge");
        guard = root.getChild("guard");
        grip = root.getChild("grip");
        pommel = root.getChild("pommel");
    }

    /** Swords we draw ourselves (the Hekublade already has its own 3D model). */
    public static boolean handles(ItemStack stack) {
        return stack.getItem() instanceof SwordItem && !com.xenowarriors.mikiko.Hekublade.isBlade(stack);
    }

    /** {blade, guard, grip} colors for the sword's material. */
    private static int[] colors(ItemStack stack) {
        Tier tier = stack.getItem() instanceof SwordItem s ? s.getTier() : Tiers.IRON;
        if (tier == Tiers.WOOD) return new int[]{0xA8804C, 0x6E4C2A, 0x5A3C22};
        if (tier == Tiers.STONE) return new int[]{0x8E8E8E, 0x6E4C2A, 0x5A3C22};
        if (tier == Tiers.GOLD) return new int[]{0xF6D650, 0xC8961E, 0x6A4A26};
        if (tier == Tiers.DIAMOND) return new int[]{0x5EE8DE, 0x2E3438, 0x4A3424};
        if (tier == Tiers.NETHERITE) return new int[]{0x54484E, 0x2A2428, 0x3A2A22};
        return new int[]{0xE2E6EC, 0x3C3238, 0x4E3624};    // iron (and modded swords)
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, Fox fox, float limbSwing, float limbAmount,
                       float partialTick, float age, float yaw, float pitch) {
        ItemStack stack = fox.getMainHandItem();
        if (!handles(stack) || fox.isInvisible()) return;
        pose.pushPose();
        if (fox.isBaby()) {
            pose.scale(0.75F, 0.75F, 0.75F);
            pose.translate(0F, 0.5F, 0.209375F);
        }
        // in its mouth: follow the head, then out to the front of the muzzle
        getParentModel().head.translateAndRotate(pose);
        pose.translate(1F / 16F, 3.6F / 16F, -6.6F / 16F);
        // held crosswise, the grip in its teeth and the blade sticking out to one side
        pose.translate(-0.5F / 16F, 0F, 0F);
        int[] c = colors(stack);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        draw(blade, pose, vc, light, c[0], 1F);
        draw(edge, pose, vc, light, c[0], 1.25F);
        draw(guard, pose, vc, light, c[1], 1F);
        draw(grip, pose, vc, light, c[2], 1F);
        draw(pommel, pose, vc, light, c[1], 1F);
        if (stack.hasFoil()) {
            VertexConsumer glint = buffers.getBuffer(RenderType.entityGlintDirect());
            for (ModelPart p : new ModelPart[]{blade, edge, guard, grip, pommel}) p.render(pose, glint, light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }

    private static void draw(ModelPart part, PoseStack pose, VertexConsumer vc, int light, int rgb, float bright) {
        float r = Math.min(1F, (rgb >> 16 & 255) / 255F * bright);
        float g = Math.min(1F, (rgb >> 8 & 255) / 255F * bright);
        float b = Math.min(1F, (rgb & 255) / 255F * bright);
        part.render(pose, vc, light, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
    }
}

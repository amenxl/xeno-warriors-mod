package com.xenowarriors.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.boss.EnderSaiyanEntity;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the Ender Saiyan: an Enderman's body (the game's own), eyes glowing in its stage's colour, and its crest - a
 * crown of void crystals on its head that grows with each stage (three small shards, then more and taller, gold when
 * Overcharged, a tall dark-violet crown in its Void Form). Jaw open when it's after someone, arms up holding a block
 * when it throws, lying flat when it falls apart.
 */
public class EnderSaiyanRenderer extends MobRenderer<EnderSaiyanEntity, EndermanModel<EnderSaiyanEntity>> {
    private static final ResourceLocation TEX = new ResourceLocation("minecraft", "textures/entity/enderman/enderman.png");
    private static final ResourceLocation EYES = new ResourceLocation("minecraft", "textures/entity/enderman/enderman_eyes.png");
    private static final ResourceLocation CREST = XenoWarriors.id("textures/entity/boss/ender_saiyan_crest.png");
    public static final ModelLayerLocation CREST_LAYER = new ModelLayerLocation(XenoWarriors.id("ender_saiyan_crest"), "main");

    public EnderSaiyanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new EndermanModel<>(ctx.bakeLayer(ModelLayers.ENDERMAN)), 0.5F);
        addLayer(new Eyes(this));
        addLayer(new Crest(this, ctx.bakeLayer(CREST_LAYER)));
    }

    @Override public ResourceLocation getTextureLocation(EnderSaiyanEntity e) { return TEX; }

    @Override
    public void render(EnderSaiyanEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        EndermanModel<EnderSaiyanEntity> m = getModel();
        m.creepy = e.getTarget() != null || e.form() > EnderSaiyanEntity.BASE;
        m.carrying = e.act() == EnderSaiyanEntity.ACT_THROW;
        super.render(e, yaw, pt, pose, buf, light);
    }

    @Override
    protected void setupRotations(EnderSaiyanEntity e, PoseStack pose, float age, float yaw, float pt) {
        super.setupRotations(e, pose, age, yaw, pt);
        if (e.ko() > 0) { pose.mulPose(Axis.XP.rotationDegrees(-90F)); pose.translate(0F, -1.2F, 0F); }   // (fallen apart: flat on the ground)
    }

    static float[] rgb(int c) { return new float[]{(c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F}; }

    static final class Eyes extends RenderLayer<EnderSaiyanEntity, EndermanModel<EnderSaiyanEntity>> {
        Eyes(RenderLayerParent<EnderSaiyanEntity, EndermanModel<EnderSaiyanEntity>> p) { super(p); }
        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, EnderSaiyanEntity e, float limb, float amount, float pt, float age, float yaw, float pitch) {
            if (e.ko() > 0) return;
            float[] c = rgb(EnderSaiyanEntity.COLOR[e.form()]);
            getParentModel().renderToBuffer(pose, buf.getBuffer(RenderType.eyes(EYES)), 0xF000F0, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1F);
        }
    }

    /** The crown of void crystals: shards per stage (the model has all of them; the stage picks how many show). */
    static final class Crest extends RenderLayer<EnderSaiyanEntity, EndermanModel<EnderSaiyanEntity>> {
        private final ModelPart root;
        private final ModelPart[] shards = new ModelPart[9];
        Crest(RenderLayerParent<EnderSaiyanEntity, EndermanModel<EnderSaiyanEntity>> p, ModelPart root) {
            super(p);
            this.root = root;
            for (int i = 0; i < shards.length; i++) shards[i] = root.getChild("s" + i);
        }
        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, EnderSaiyanEntity e, float limb, float amount, float pt, float age, float yaw, float pitch) {
            int f = e.form();
            if (f == EnderSaiyanEntity.BASE || e.ko() > 0) return;
            int show = new int[]{0, 3, 5, 7, 9}[f];
            float grow = new float[]{0, 0.6F, 0.85F, 1.0F, 1.35F}[f];
            for (int i = 0; i < shards.length; i++) { shards[i].visible = i < show; shards[i].yScale = grow * (1F + 0.06F * net.minecraft.util.Mth.sin(age * 0.2F + i)); }
            pose.pushPose();
            getParentModel().getHead().translateAndRotate(pose);
            float[] c = rgb(EnderSaiyanEntity.COLOR[f]);
            root.render(pose, buf.getBuffer(RenderType.entityTranslucentEmissive(CREST)), 0xF000F0, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 0.9F);
            pose.popPose();
        }

        /** Nine shards round the top of the head, the middle ones tallest, leaning out a little. */
        static LayerDefinition create() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition r = mesh.getRoot();
            float[][] at = {{0, -3.5F, 0}, {-2.5F, -2, 1}, {2.5F, -2, 1}, {-3.5F, 0.5F, 2}, {3.5F, 0.5F, 2}, {-1.5F, 3, 3}, {1.5F, 3, 3}, {-3.8F, 3.2F, 4}, {3.8F, 3.2F, 4}};
            for (int i = 0; i < at.length; i++) {
                float h = 7F - at[i][2] * 1.1F;
                r.addOrReplaceChild("s" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, -h, -0.75F, 1.5F, h, 1.5F),
                        PartPose.offsetAndRotation(at[i][0], -7.6F, at[i][1], at[i][1] * 0.08F, 0F, -at[i][0] * 0.09F));
            }
            return LayerDefinition.create(mesh, 16, 16);
        }
    }

    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Reg {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(com.xenowarriors.boss.EnderSaiyans.ENDER_SAIYAN.get(), EnderSaiyanRenderer::new);
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(CREST_LAYER, Crest::create);
        }
    }
}

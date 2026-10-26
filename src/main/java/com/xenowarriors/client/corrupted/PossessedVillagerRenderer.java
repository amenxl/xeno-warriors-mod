package com.xenowarriors.client.corrupted;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.corrupted.CorruptedVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A possessed villager still looks like a villager - robe, nose and all - but drained dark, with glowing red eyes. It
 * unfolds its arms to swing them at you, lunges, and flinches when hit.
 */
public class PossessedVillagerRenderer extends MobRenderer<CorruptedVillagerEntity, PossessedVillagerRenderer.Model> {
    private static final ResourceLocation BASE = new ResourceLocation("textures/entity/villager/villager.png");
    private static final String[] TYPES = {"plains", "desert", "savanna", "taiga", "snow", "jungle", "swamp"};
    private static final String[] JOBS = {"none", "farmer", "fisherman", "shepherd", "librarian", "cleric", "armorer",
            "butcher", "cartographer", "fletcher", "leatherworker", "mason", "toolsmith", "weaponsmith"};
    private static final RenderType EYES = RenderType.eyes(XenoWarriors.id("textures/entity/corrupted/possessed_villager_eyes.png"));

    public PossessedVillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        addLayer(new Overlays(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CorruptedVillagerEntity e) {
        return BASE;
    }

    @Override
    protected void scale(CorruptedVillagerEntity e, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    /** Biome clothes and the job they had, drawn over the body - tinted dark like the rest - then the red eyes. */
    private static class Overlays extends RenderLayer<CorruptedVillagerEntity, Model> {
        Overlays(RenderLayerParent<CorruptedVillagerEntity, Model> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CorruptedVillagerEntity e, float limbSwing,
                           float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
            if (e.isInvisible()) return;
            int h = e.getUUID().hashCode();
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0F);
            Model m = getParentModel();
            String type = TYPES[Math.floorMod(h, TYPES.length)];
            m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("textures/entity/villager/type/" + type + ".png"))),
                    light, overlay, 1F, 1F, 1F, 1F);
            String job = JOBS[Math.floorMod(h >> 8, JOBS.length)];
            if (!job.equals("none"))
                m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("textures/entity/villager/profession/" + job + ".png"))),
                        light, overlay, 1F, 1F, 1F, 1F);
            m.glow = true;
            m.renderToBuffer(pose, buffers.getBuffer(EYES), 0xF000F0, overlay, 1F, 1F, 1F, 1F);
            m.glow = false;
        }
    }

    public static class Model extends VillagerModel<CorruptedVillagerEntity> {
        private final ModelPart head, body, arms;
        boolean glow;

        public Model(ModelPart root) {
            super(root);
            head = root.getChild("head");
            body = root.getChild("body");
            arms = root.getChild("arms");
        }

        @Override
        public void setupAnim(CorruptedVillagerEntity e, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
            super.setupAnim(e, limbSwing, limbSwingAmount, age, yaw, pitch);
            arms.y = 3F; arms.z = -1F; arms.xRot = -0.75F;
            // head twitches and a hurt flinch
            float tw = (age + e.getId() * 11) % 60F;
            head.zRot = tw < 4F ? (e.getId() % 2 == 0 ? 0.5F : -0.5F) * Mth.sin(tw / 4F * Mth.PI) : 0F;
            if (e.hurtTime > 0) {
                float k = Mth.sin(e.hurtTime / (float) Math.max(1, e.hurtDuration) * Mth.PI);
                head.xRot -= 0.5F * k;
                arms.xRot += 0.4F * k;
            }
            body.xRot = 0F;
            // chasing: hunched forward, arms already coming up
            if (e.isAggressive()) {
                arms.xRot = -1.1F;
                head.xRot += 0.1F;
            }
            float attack = attackTime;
            if (attack > 0F) {
                float s = Mth.sin(attack * (float) Math.PI);
                switch (e.attackStyle()) {
                    case CorruptedVillagerEntity.SLAM -> {
                        float up = attack < 0.4F ? attack / 0.4F : 1F - (attack - 0.4F) / 0.6F;
                        arms.xRot = -0.75F - 2.2F * up + (attack > 0.4F ? 0.9F * (attack - 0.4F) / 0.6F : 0F);
                        body.xRot = attack > 0.4F ? 0.4F * s : -0.15F * up;
                    }
                    case CorruptedVillagerEntity.LEFT -> {
                        arms.xRot = -0.75F - 1.0F * s;
                        arms.yRot = 0.45F * s;
                        body.xRot = 0.2F * s;
                    }
                    default -> {
                        arms.xRot = -0.75F - 1.0F * s;
                        arms.yRot = -0.45F * s;
                        body.xRot = 0.2F * s;
                    }
                }
            } else arms.yRot = 0F;
            if (e.hurtTime > 0) {
                float k = Mth.sin(e.hurtTime / (float) Math.max(1, e.hurtDuration) * (float) Math.PI);
                head.xRot -= 0.5F * k;
                body.xRot -= 0.2F * k;
                arms.xRot += 0.4F * k;
            }
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
            if (glow) super.renderToBuffer(pose, vc, light, overlay, r, g, b, a);
            else super.renderToBuffer(pose, vc, light, overlay, r * 0.38F, g * 0.3F, b * 0.42F, a);   // drained, faintly purple
        }
    }
}

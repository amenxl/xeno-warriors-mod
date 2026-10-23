package com.xenowarriors.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.render.DetailModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Townsfolk and the possessed: the player-shaped body in their skin, their 3D hair and outfit on top, and their face.
 * The possessed version is drained dark with red eyes that glow.
 */
public class NpcRenderer<T extends Mob> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private static final Map<String, DetailModel> MODELS = new HashMap<>();
    private final Function<T, String> look;
    private final Predicate<T> dark;

    public NpcRenderer(EntityRendererProvider.Context ctx, PlayerModel<T> model, Function<T, String> look, Predicate<T> dark) {
        super(ctx, model, 0.5F);
        this.look = look;
        this.dark = dark;
        addLayer(new Details(this));
    }

    public static <T extends Mob> NpcRenderer<T> plain(EntityRendererProvider.Context ctx, Function<T, String> look, Predicate<T> dark) {
        return new NpcRenderer<>(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), look, dark);
    }

    private static final ResourceLocation ARMBAND_TEX = XenoWarriors.id("textures/entity/npc/armband.png");
    private static net.minecraft.client.model.geom.ModelPart armband;

    private static net.minecraft.client.model.geom.ModelPart armband() {
        if (armband == null) {
            var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
            mesh.getRoot().addOrReplaceChild("band", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1.35F, 2.6F, -2.35F, 4.7F, 1.8F, 4.7F), net.minecraft.client.model.geom.PartPose.ZERO);
            armband = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 32, 16).bakeRoot();
        }
        return armband;
    }

    /** This one's wings: {scale, r, g, b, extra spread, extra sweep}, or null to draw them as modelled. */
    protected float[] wingLook(T e) { return null; }

    /** Per-entity tweaks before the details are drawn (wings etc.). */
    protected void prepare(T e, DetailModel m, float age) {}

    /** "_face", or another expression. */
    protected String faceSuffix(T e) { return "_face"; }

    public static ResourceLocation tex(String name) {
        return XenoWarriors.id("textures/entity/npc/" + name + ".png");
    }

    private boolean darkened(T e) {
        return dark.test(e) && !look.apply(e).startsWith("dark_");      // (the permanently possessed are dark already)
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return tex(look.apply(e) + (darkened(e) ? "_dark" : ""));
    }

    static DetailModel model(String key) {
        return MODELS.computeIfAbsent(key, k -> DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/detail/npc/" + k + ".json"));
    }

    private class Details extends RenderLayer<T, PlayerModel<T>> {
        Details(RenderLayerParent<T, PlayerModel<T>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float yaw, float pitch) {
            if (e.isInvisible()) return;
            String key = look.apply(e);
            DetailModel m = model(key);
            if (m == null) return;
            prepare(e, m, age);
            PlayerModel<T> p = getParentModel();
            m.head.copyFrom(p.head); m.body.copyFrom(p.body);
            m.rightArm.copyFrom(p.rightArm); m.leftArm.copyFrom(p.leftArm);
            m.rightLeg.copyFrom(p.rightLeg); m.leftLeg.copyFrom(p.leftLeg);
            com.xenowarriors.client.LimbBend.copy(p.rightArm, m.rightArm);
            com.xenowarriors.client.LimbBend.copy(p.leftArm, m.leftArm);
            com.xenowarriors.client.LimbBend.copy(p.rightLeg, m.rightLeg);
            com.xenowarriors.client.LimbBend.copy(p.leftLeg, m.leftLeg);
            boolean d = darkened(e), possessedLook = dark.test(e) || key.startsWith("dark_");
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0F);
            var detailVc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex(key + (d ? "_dark_details" : "_details"))));
            float[] wing = wingLook(e);
            if (wing != null && m.rightWing != null && m.leftWing != null && m.rightWing.visible) {
                // this one's own wings: drawn on their own, sized and tinted
                m.rightWing.visible = false;
                m.leftWing.visible = false;
                m.root.render(pose, detailVc, light, overlay);
                m.rightWing.visible = true;
                m.leftWing.visible = true;
                pose.pushPose();
                m.body.translateAndRotate(pose);
                for (var w : new net.minecraft.client.model.geom.ModelPart[]{m.rightWing, m.leftWing}) {
                    float zr = w.zRot, xr = w.xRot;
                    w.xScale = w.yScale = w.zScale = wing[0];
                    w.zRot += (w == m.rightWing ? 1 : -1) * wing[4];      // spread
                    w.xRot += wing[5];                                     // swept back
                    w.render(pose, detailVc, light, overlay, wing[1], wing[2], wing[3], 1F);
                    w.xScale = w.yScale = w.zScale = 1F;
                    w.zRot = zr;
                    w.xRot = xr;
                }
                pose.popPose();
            } else {
                m.root.render(pose, detailVc, light, overlay);
            }
            if (m.faceRoot != null) {
                m.faceHead.copyFrom(p.head);
                ResourceLocation face = tex(key + (d ? "_face_dark" : faceSuffix(e)));
                m.faceRoot.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(face)), light, overlay);
                if (possessedLook) m.faceRoot.render(pose, buffers.getBuffer(RenderType.eyes(face)), light, overlay);    // glowing red eyes
            }
            // Sky Haters wear a red armband
            if (e instanceof com.xenowarriors.npc.HumanNpcEntity h && h.skyHater()) {
                pose.pushPose();
                p.leftArm.translateAndRotate(pose);
                armband().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ARMBAND_TEX)), light, overlay);
                pose.popPose();
            }
        }
    }
}

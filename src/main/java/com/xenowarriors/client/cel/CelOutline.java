package com.xenowarriors.client.cel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.client.render.DetailModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Draws the cel outline around a character: their body (an inflated player model) and their 3D details. */
public final class CelOutline {
    public static final float BODY = 0.24F, DETAIL = 0.17F;
    private static final float INK = 0.05F;
    private static ModelPart bodyRoot;
    private static PlayerModel<LivingEntity> body;
    private static final Map<DetailModel, DetailModel> DETAILS = new WeakHashMap<>();

    private CelOutline() {}

    private static PlayerModel<LivingEntity> body() {
        if (body == null) {
            bodyRoot = LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(BODY), false), 64, 64).bakeRoot();
            body = new PlayerModel<>(bodyRoot, false);
        }
        return body;
    }

    private static void sync(ModelPart from, ModelPart to) {
        List<ModelPart> a = from.getAllParts().toList(), b = to.getAllParts().toList();
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            ModelPart s = a.get(i), d = b.get(i);
            d.copyFrom(s);
            d.xScale = s.xScale; d.yScale = s.yScale; d.zScale = s.zScale;
            d.visible = s.visible;
        }
    }

    /** The body outline, matching the player model's pose this frame. */
    public static void body(HumanoidModel<?> parent, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        PlayerModel<LivingEntity> o = body();
        sync(parent.head, o.head); sync(parent.body, o.body);
        sync(parent.rightArm, o.rightArm); sync(parent.leftArm, o.leftArm);
        sync(parent.rightLeg, o.rightLeg); sync(parent.leftLeg, o.leftLeg);
        o.hat.visible = false; o.jacket.visible = false; o.leftSleeve.visible = false; o.rightSleeve.visible = false;
        o.leftPants.visible = false; o.rightPants.visible = false;
        VertexConsumer vc = buffers.getBuffer(CelRenderTypes.OUTLINE);
        for (ModelPart p : new ModelPart[]{o.head, o.body, o.rightArm, o.leftArm, o.rightLeg, o.leftLeg}) p.render(pose, vc, light, overlay, INK, INK, INK, 1F);
    }

    /** The details' outline (hair, clothes, wings...), matching their pose this frame. */
    public static void details(DetailModel m, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (m == null) return;
        DetailModel o = DETAILS.computeIfAbsent(m, DetailModel::outlineCopy);
        if (o == null) return;
        sync(m.root, o.root);
        o.root.render(pose, buffers.getBuffer(CelRenderTypes.OUTLINE), light, overlay, INK, INK, INK, 1F);
    }
}

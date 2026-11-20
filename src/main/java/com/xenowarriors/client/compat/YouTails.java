package com.xenowarriors.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.you.YouSlot;
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
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A made Fox Kin's own tail (YouSlot.tail and its colours): Classic (the usual), Fluffy, Long, Twin Tails or Nine Tails,
 * each posed like the usual tail (FoxTailLayer) and swaying with it. With colours picked, it's drawn from a pale fur
 * texture tinted to them - the tail in one colour, the tip in another. Drawn in the body's space.
 */
public final class YouTails {
    private YouTails() {}

    static final ResourceLocation FUR = XenoWarriors.id("textures/entity/character/you_tail_fur.png");
    public static final int BASE = 0xD2502E, TIP = 0xF4F0EA;           // (the usual: a red fox tail, white tip)

    /** One tail of a style: its parts and how it's set off from the others. */
    private record Tail(ModelPart base, ModelPart mid, ModelPart tip, float yaw, float pitch, float roll) {}
    private static List<Tail>[] STYLES;

    @SuppressWarnings("unchecked")
    private static void build() {
        if (STYLES != null) return;
        STYLES = new List[YouSlot.TAILS.length];
        STYLES[0] = List.of(tail(0, 3, 5, 3, 4.2F, 5, 6, 5, 5.5F, 4, 4, 4, 0, 0, 0, 0));
        STYLES[1] = List.of(tail(0, 4, 4, 4, 3.5F, 7, 8, 7, 7.5F, 6, 5, 6, 0, 0, 0, 0));
        STYLES[2] = List.of(tail(0, 2, 7, 2, 6.5F, 3, 9, 3, 8.5F, 3, 5, 3, 0, -0.1F, 0, 0));
        STYLES[3] = List.of(tail(-0.7F, 3, 5, 3, 4.2F, 5, 6, 5, 5.5F, 4, 4, 4, -0.38F, 0, -0.15F, 0),
                tail(0.7F, 3, 5, 3, 4.2F, 5, 6, 5, 5.5F, 4, 4, 4, 0.38F, 0, 0.15F, 0));
        List<Tail> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            float k = (i - 4) / 4F;                                                 // fanned out behind, the middle ones highest
            nine.add(tail(k * 0.9F, 2, 4, 2, 3.6F, 4, 5, 4, 4.6F, 3, 3, 3, k * 1.05F, 0.35F - Math.abs(k) * 0.3F, k * 0.5F, 0));
        }
        STYLES[4] = nine;
    }

    private static Tail tail(float x, int bw, int bh, int bd, float midAt, int mw, int mh, int md, float tipAt, int tw, int th, int td,
                             float yaw, float pitch, float roll, int unused) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition t = r.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0).addBox(-bw / 2F, 0, -bd / 2F, bw, bh, bd),
                PartPose.offset(x, 10.5F, 2.2F));
        PartDefinition m = t.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(0, 16).addBox(-mw / 2F, 0, -md / 2F, mw, mh, md),
                PartPose.offset(0, midAt, 0));
        m.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(32, 0).addBox(-tw / 2F, 0, -td / 2F, tw, th, td), PartPose.offset(0, tipAt, 0));
        ModelPart base = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("tail");
        ModelPart mid = base.getChild("mid");
        return new Tail(base, mid, mid.getChild("tip"), yaw, pitch, roll);
    }

    /** (Tests) tails drawn, by style. */
    public static final int[] DRAWN = new int[YouSlot.TAILS.length];

    /**
     * A made Fox Kin's tail, in the body's space: true if drawn (or it's theirs and there's none to draw now - the usual
     * one is then skipped). {@code poser} poses one tail the usual way and gives the usual texture (null: none out).
     */
    static boolean render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, YouSlot s,
                          java.util.function.Function<ModelPart[], ResourceLocation> poser) {
        build();
        int style = Math.floorMod(s.tail, STYLES.length);
        boolean tinted = s.tailColour >= 0 || s.tipColour >= 0 || style != 0;
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0F);
        boolean any = false;
        for (Tail t : STYLES[style]) {
            ResourceLocation usual = poser.apply(new ModelPart[]{t.base, t.mid, t.tip});
            if (usual == null) return true;
            t.base.yRot += t.yaw; t.base.xRot += t.pitch; t.base.zRot = t.roll;
            any = true;
            if (!tinted) { t.base.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(usual)), light, overlay); continue; }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FUR));
            int c = s.tailColour >= 0 ? s.tailColour : BASE, tc = s.tipColour >= 0 ? s.tipColour : TIP;
            t.tip.visible = false;
            t.base.render(pose, vc, light, overlay, r(c), g(c), b(c), 1F);
            t.tip.visible = true;
            pose.pushPose();
            t.base.translateAndRotate(pose);
            t.mid.translateAndRotate(pose);
            t.tip.render(pose, vc, light, overlay, r(tc), g(tc), b(tc), 1F);
            pose.popPose();
        }
        if (any) DRAWN[style]++;
        return true;
    }

    static float r(int c) { return (c >> 16 & 255) / 255F; }
    static float g(int c) { return (c >> 8 & 255) / 255F; }
    static float b(int c) { return (c & 255) / 255F; }
}

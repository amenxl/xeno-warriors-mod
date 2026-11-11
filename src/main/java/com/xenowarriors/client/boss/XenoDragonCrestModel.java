package com.xenowarriors.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.boss.XenoDragonEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * The Xeno Dragon's "hair": a crest of spikes on its dragon head, shaped like each Saiyan form - none in its base
 * form (just the horns), Super Saiyan's upswept spikes (Super Saiyan 2: more, sharper), Super Saiyan 3's long spiked
 * mane down its back, Super Saiyan God's slim swept-back crest, Ultra Instinct's swept-back silver spikes. The
 * renderer tints it per form and makes it glow.
 */
public class XenoDragonCrestModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("xeno_dragon"), "crest");
    private final ModelPart root, ssj, ssj2, ssj3, god, ui;

    public XenoDragonCrestModel(ModelPart root) {
        this.root = root;
        ssj = root.getChild("ssj"); ssj2 = root.getChild("ssj2"); ssj3 = root.getChild("ssj3"); god = root.getChild("god"); ui = root.getChild("ui");
    }

    private static int n;

    /** A spike from (x,y,z), len long, pointing up (xr > 0 tips it back). */
    private static void spike(PartDefinition g, float x, float y, float z, float w, float len, float d, float xr, float yr, float zr) {
        g.addOrReplaceChild("s" + (n++), CubeListBuilder.create().texOffs((n * 7) % 40, (n * 11) % 30).addBox(-w / 2, -len, -d / 2, w, len, d),
                PartPose.offsetAndRotation(x, y, z, -xr, yr, zr));
    }

    /** A lock hanging down from (x,y,z) (xr > 0 swings it back). */
    private static void lock(PartDefinition g, float x, float y, float z, float w, float len, float d, float xr, float yr, float zr) {
        g.addOrReplaceChild("l" + (n++), CubeListBuilder.create().texOffs((n * 5) % 40, (n * 9) % 30).addBox(-w / 2, 0, -d / 2, w, len, d),
                PartPose.offsetAndRotation(x, y, z, xr, yr, zr));
    }

    public static LayerDefinition create() {
        n = 0;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        // (the skull's top is y=-6, from x -3.5..3.5, z -4..3)
        PartDefinition s = r.addOrReplaceChild("ssj", CubeListBuilder.create(), PartPose.ZERO);
        spike(s, 0, -5.8F, -1.5F, 2.6F, 7.5F, 2.6F, -0.1F, 0, 0);
        spike(s, -2.2F, -5.8F, -1, 2.2F, 7, 2.2F, -0.05F, 0, -0.35F);
        spike(s, 2.2F, -5.8F, -1, 2.2F, 7, 2.2F, -0.05F, 0, 0.35F);
        spike(s, -3.2F, -5.5F, 0.8F, 2, 6, 2, 0.2F, 0, -0.75F);
        spike(s, 3.2F, -5.5F, 0.8F, 2, 6, 2, 0.2F, 0, 0.75F);
        spike(s, -1.3F, -5.8F, 1.6F, 2.2F, 6.5F, 2.2F, 0.5F, 0, -0.2F);
        spike(s, 1.3F, -5.8F, 1.6F, 2.2F, 6.5F, 2.2F, 0.5F, 0, 0.2F);
        spike(s, 0, -5, 2.8F, 2.6F, 5.5F, 2, 0.95F, 0, 0);
        PartDefinition s2 = r.addOrReplaceChild("ssj2", CubeListBuilder.create(), PartPose.ZERO);   // (on top of ssj: sharper, more)
        spike(s2, -0.9F, -5.8F, -2.6F, 1.6F, 8.5F, 1.6F, -0.2F, 0, -0.12F);
        spike(s2, 0.9F, -5.8F, -2.6F, 1.6F, 8.5F, 1.6F, -0.2F, 0, 0.12F);
        spike(s2, -3.4F, -4, 2, 1.8F, 5, 1.8F, 0.9F, 0, -1.0F);
        spike(s2, 3.4F, -4, 2, 1.8F, 5, 1.8F, 0.9F, 0, 1.0F);
        PartDefinition t = r.addOrReplaceChild("ssj3", CubeListBuilder.create(), PartPose.ZERO);
        spike(t, 0, -5.8F, -1, 2.8F, 6, 2.6F, 0, 0, 0);
        spike(t, -2.4F, -5.6F, -0.5F, 2.4F, 5.5F, 2.4F, 0, 0, -0.45F);
        spike(t, 2.4F, -5.6F, -0.5F, 2.4F, 5.5F, 2.4F, 0, 0, 0.45F);
        lock(t, 0, -5.5F, 3.2F, 6.5F, 24, 2.4F, 0.18F, 0, 0);
        lock(t, -2.8F, -5, 3, 2.6F, 21, 2, 0.2F, 0, 0.18F);
        lock(t, 2.8F, -5, 3, 2.6F, 21, 2, 0.2F, 0, -0.18F);
        lock(t, -1.4F, -4, 3.8F, 2.2F, 25, 1.4F, 0.24F, 0, 0.06F);
        lock(t, 1.4F, -4, 3.8F, 2.2F, 25, 1.4F, 0.24F, 0, -0.06F);
        PartDefinition g = r.addOrReplaceChild("god", CubeListBuilder.create(), PartPose.ZERO);
        spike(g, 0, -5.8F, -1.5F, 2, 6, 2, 0.75F, 0, 0);
        spike(g, -1.8F, -5.6F, -0.5F, 1.8F, 6, 1.8F, 0.9F, 0, -0.25F);
        spike(g, 1.8F, -5.6F, -0.5F, 1.8F, 6, 1.8F, 0.9F, 0, 0.25F);
        spike(g, -3, -5, 1, 1.6F, 5, 1.6F, 1.1F, 0, -0.55F);
        spike(g, 3, -5, 1, 1.6F, 5, 1.6F, 1.1F, 0, 0.55F);
        spike(g, 0, -4.5F, 2.6F, 2, 6, 1.6F, 1.35F, 0, 0);
        PartDefinition u = r.addOrReplaceChild("ui", CubeListBuilder.create(), PartPose.ZERO);
        spike(u, 0, -5.8F, -2, 2.4F, 6.5F, 2.2F, 0.45F, 0, 0);
        spike(u, -2, -5.8F, -1.2F, 2, 6.5F, 2, 0.55F, 0, -0.4F);
        spike(u, 2, -5.8F, -1.2F, 2, 6.5F, 2, 0.55F, 0, 0.4F);
        spike(u, -3.3F, -5, 0.5F, 1.8F, 5.5F, 1.8F, 0.7F, 0, -0.85F);
        spike(u, 3.3F, -5, 0.5F, 1.8F, 5.5F, 1.8F, 0.7F, 0, 0.85F);
        spike(u, -1.2F, -5.6F, 1.5F, 2, 6, 2, 1.0F, 0, -0.2F);
        spike(u, 1.2F, -5.6F, 1.5F, 2, 6, 2, 1.0F, 0, 0.2F);
        spike(u, 0, -4.2F, 2.8F, 2.4F, 5.5F, 1.8F, 1.3F, 0, 0);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Follows the head; false when this form has no crest. */
    public boolean setup(ModelPart head, int form) {
        root.x = head.x; root.y = head.y; root.z = head.z;
        root.xRot = head.xRot; root.yRot = head.yRot; root.zRot = head.zRot;
        ssj.visible = form == XenoDragonEntity.SSJ1 || form == XenoDragonEntity.SSJ2;
        ssj2.visible = form == XenoDragonEntity.SSJ2;
        ssj3.visible = form == XenoDragonEntity.SSJ3;
        god.visible = form == XenoDragonEntity.SSG;
        ui.visible = form == XenoDragonEntity.UI;
        return form != XenoDragonEntity.BASE;
    }

    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vc, light, overlay, r, g, b, a);
    }
}

package com.xenowarriors.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.boss.ZombieSaiyanEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * Saiyan hair, built from spikes around the head: Goku's wild black hair (base), the upswept gold spikes of Super
 * Saiyan 1 and 2 (with their bangs), Super Saiyan 3's mane down past the waist, and Super Saiyan 4's wild black hair
 * down the back. All of it follows the head; the renderer tints it per form.
 */
public class SaiyanHairModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("zombie_saiyan"), "hair");
    private final ModelPart root, cap, base, ssj, bang1, bang2, ssj3, ssj4;

    public SaiyanHairModel(ModelPart root) {
        this.root = root;
        cap = root.getChild("cap"); base = root.getChild("base"); ssj = root.getChild("ssj");
        bang1 = root.getChild("bang1"); bang2 = root.getChild("bang2"); ssj3 = root.getChild("ssj3"); ssj4 = root.getChild("ssj4");
    }

    private static int n;

    /** A spike from (x,y,z) on the head, len long, pointing up (rotated by xr/yr/zr). */
    private static void spike(PartDefinition g, float x, float y, float z, float w, float len, float d, float xr, float yr, float zr) {
        g.addOrReplaceChild("s" + (n++), CubeListBuilder.create().texOffs((n * 7) % 40, (n * 11) % 30).addBox(-w / 2, -len, -d / 2, w, len, d),
                PartPose.offsetAndRotation(x, y, z, -xr, yr, zr));                   // (xr > 0 tips it back: an upward spike needs -xRot for that)
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
        // the hair over the scalp, the sides and the back of the head
        PartDefinition cap = r.addOrReplaceChild("cap", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -8.7F, -4.5F, 9, 3, 9)
                .texOffs(0, 12).addBox(-4.6F, -8.0F, -3.6F, 1, 4, 7)
                .texOffs(16, 12).addBox(3.6F, -8.0F, -3.6F, 1, 4, 7)
                .texOffs(0, 24).addBox(-4.5F, -8.0F, 3.4F, 9, 7, 1.4F), PartPose.ZERO);
        // base form: Goku's spikes going every way
        PartDefinition b = r.addOrReplaceChild("base", CubeListBuilder.create(), PartPose.ZERO);
        spike(b, 0, -8.4F, 0.5F, 3, 5, 3, -0.4F, 0, 0);
        spike(b, -2.5F, -8.2F, -0.5F, 2.6F, 5, 2.6F, -0.2F, 0, -0.9F);
        spike(b, 2.5F, -8.2F, -0.5F, 2.6F, 5, 2.6F, -0.2F, 0, 0.9F);
        spike(b, -3, -7.5F, 2, 2.4F, 5, 2.4F, 0.4F, 0, -1.3F);
        spike(b, 3, -7.5F, 2, 2.4F, 5, 2.4F, 0.4F, 0, 1.3F);
        spike(b, 0, -7, 3.8F, 3, 5, 2.4F, 1.25F, 0, 0);
        spike(b, -2.2F, -5.5F, 3.8F, 2.4F, 4.5F, 2.2F, 1.55F, 0, -0.45F);
        spike(b, 2.2F, -5.5F, 3.8F, 2.4F, 4.5F, 2.2F, 1.55F, 0, 0.45F);
        spike(b, -4.2F, -5.5F, 1, 2.2F, 4, 2.2F, 0, 0, -1.75F);
        spike(b, 4.2F, -5.5F, 1, 2.2F, 4, 2.2F, 0, 0, 1.75F);
        lock(b, -2.6F, -8.2F, -4.2F, 2, 3.5F, 1, -0.25F, 0, 0.25F);
        lock(b, 0.2F, -8.4F, -4.3F, 2.2F, 3.8F, 1, -0.3F, 0, -0.1F);
        lock(b, 2.8F, -8.2F, -4.2F, 2, 3.2F, 1, -0.25F, 0, -0.3F);
        // Super Saiyan 1/2: swept up
        PartDefinition s = r.addOrReplaceChild("ssj", CubeListBuilder.create(), PartPose.ZERO);
        spike(s, 0, -8.5F, 0.3F, 3.2F, 7.5F, 3, -0.12F, 0, 0);
        spike(s, -2.3F, -8.5F, -0.3F, 2.8F, 7, 2.8F, -0.1F, 0, -0.35F);
        spike(s, 2.3F, -8.5F, -0.3F, 2.8F, 7, 2.8F, -0.1F, 0, 0.35F);
        spike(s, -3.6F, -8, 0.4F, 2.6F, 6, 2.6F, 0, 0, -0.75F);
        spike(s, 3.6F, -8, 0.4F, 2.6F, 6, 2.6F, 0, 0, 0.75F);
        spike(s, -1.6F, -8, 2.6F, 2.6F, 6.5F, 2.6F, 0.5F, 0, -0.2F);
        spike(s, 1.6F, -8, 2.6F, 2.6F, 6.5F, 2.6F, 0.5F, 0, 0.2F);
        spike(s, 0, -7, 3.7F, 3, 5.5F, 2.4F, 0.95F, 0, 0);
        spike(s, -2.4F, -5.5F, 3.8F, 2.2F, 4.5F, 2, 1.25F, 0, -0.35F);
        spike(s, 2.4F, -5.5F, 3.8F, 2.2F, 4.5F, 2, 1.25F, 0, 0.35F);
        spike(s, -4.3F, -6.5F, 0, 2.2F, 4.2F, 2.2F, 0, 0, -1.25F);
        spike(s, 4.3F, -6.5F, 0, 2.2F, 4.2F, 2.2F, 0, 0, 1.25F);
        // bangs: Super Saiyan's few, Super Saiyan 2's single one
        PartDefinition b1 = r.addOrReplaceChild("bang1", CubeListBuilder.create(), PartPose.ZERO);
        lock(b1, -1.8F, -8.3F, -4.3F, 1.8F, 3.2F, 1, -0.2F, 0, 0.35F);
        lock(b1, 1.6F, -8.3F, -4.3F, 1.8F, 3.0F, 1, -0.2F, 0, -0.4F);
        PartDefinition b2 = r.addOrReplaceChild("bang2", CubeListBuilder.create(), PartPose.ZERO);
        lock(b2, 0.6F, -8.3F, -4.4F, 1.6F, 4.6F, 1, -0.15F, 0, 0.12F);
        // Super Saiyan 3: the long mane
        PartDefinition t = r.addOrReplaceChild("ssj3", CubeListBuilder.create(), PartPose.ZERO);
        spike(t, 0, -8.5F, 0.5F, 3.4F, 6, 3.2F, -0.05F, 0, 0);
        spike(t, -2.6F, -8.4F, 0, 3, 5.5F, 3, 0, 0, -0.45F);
        spike(t, 2.6F, -8.4F, 0, 3, 5.5F, 3, 0, 0, 0.45F);
        spike(t, -4.2F, -7, 0.5F, 2.6F, 4.5F, 2.6F, 0, 0, -1.1F);
        spike(t, 4.2F, -7, 0.5F, 2.6F, 4.5F, 2.6F, 0, 0, 1.1F);
        lock(t, 0, -7.5F, 4.2F, 9.2F, 24, 2.6F, 0.14F, 0, 0);
        lock(t, -3.6F, -7, 4.0F, 3.4F, 21, 2.2F, 0.16F, 0, 0.16F);
        lock(t, 3.6F, -7, 4.0F, 3.4F, 21, 2.2F, 0.16F, 0, -0.16F);
        lock(t, -1.8F, -6, 4.8F, 3, 25, 1.6F, 0.2F, 0, 0.06F);
        lock(t, 1.8F, -6, 4.8F, 3, 25, 1.6F, 0.2F, 0, -0.06F);
        lock(t, -4.7F, -6.5F, 0.5F, 1.4F, 12, 3.4F, 0.08F, 0, 0.12F);
        lock(t, 4.7F, -6.5F, 0.5F, 1.4F, 12, 3.4F, 0.08F, 0, -0.12F);
        lock(t, 0.4F, -8.3F, -4.3F, 2, 4, 1, -0.2F, 0, -0.15F);
        // Super Saiyan 4: wild black hair down the back
        PartDefinition f = r.addOrReplaceChild("ssj4", CubeListBuilder.create(), PartPose.ZERO);
        spike(f, 0, -8.4F, 0.5F, 3.2F, 6, 3, -0.35F, 0, 0);
        spike(f, -2.6F, -8.2F, -0.4F, 2.8F, 6, 2.8F, -0.25F, 0, -0.85F);
        spike(f, 2.6F, -8.2F, -0.4F, 2.8F, 6, 2.8F, -0.25F, 0, 0.85F);
        spike(f, -3.6F, -7, 2, 2.6F, 5.5F, 2.6F, 0.4F, 0, -1.35F);
        spike(f, 3.6F, -7, 2, 2.6F, 5.5F, 2.6F, 0.4F, 0, 1.35F);
        spike(f, -4.3F, -5, 0.5F, 2.2F, 4.5F, 2.2F, 0, 0, -1.8F);
        spike(f, 4.3F, -5, 0.5F, 2.2F, 4.5F, 2.2F, 0, 0, 1.8F);
        lock(f, 0, -7.5F, 4.2F, 9, 15, 2.4F, 0.22F, 0, 0);
        lock(f, -3, -6.5F, 4.3F, 3.4F, 13, 2.2F, 0.28F, 0, 0.35F);
        lock(f, 3, -6.5F, 4.3F, 3.4F, 13, 2.2F, 0.28F, 0, -0.35F);
        lock(f, 0, -5.5F, 5, 3.4F, 17, 1.8F, 0.3F, 0, 0);
        lock(f, -2.2F, -8.2F, -4.3F, 2, 4, 1, -0.3F, 0, 0.35F);
        lock(f, 1.2F, -8.4F, -4.3F, 2.2F, 4.4F, 1, -0.32F, 0, -0.2F);
        lock(f, 3.2F, -8.1F, -4.2F, 1.8F, 3.4F, 1, -0.25F, 0, -0.45F);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Follows the head (its position and turn) and shows the hair of this form. */
    public void setup(ModelPart head, int form) {
        root.x = head.x; root.y = head.y; root.z = head.z;
        root.xRot = head.xRot; root.yRot = head.yRot; root.zRot = head.zRot;
        base.visible = form == ZombieSaiyanEntity.BASE;
        ssj.visible = form == ZombieSaiyanEntity.SSJ1 || form == ZombieSaiyanEntity.SSJ2;
        bang1.visible = form == ZombieSaiyanEntity.SSJ1;
        bang2.visible = form == ZombieSaiyanEntity.SSJ2;
        ssj3.visible = form == ZombieSaiyanEntity.SSJ3;
        ssj4.visible = form == ZombieSaiyanEntity.SSJ4;
        cap.visible = true;
    }

    public void render(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vc, light, overlay, r, g, b, a);
    }
}

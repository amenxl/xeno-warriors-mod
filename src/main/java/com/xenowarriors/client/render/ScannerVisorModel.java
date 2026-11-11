package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.gov.KarmaScanner;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The Karma Scanners worn in 3D: a headset with an earpiece, an arm reaching round to a lens in front of the eye (one
 * eye for the basic and Advanced visors, both for Elite and Omni), antennas on the bigger ones. Drawn as the helmet's
 * armor model (Forge's armor-model hook), so it follows the head on players, bots, Gov guards and armor stands; our
 * characters' own armor layer draws it the same way. Two passes like dyed leather: the lens and lights tinted with the
 * dye, then the tier's frame. Cubes: ScannerVisorGeo (tools/xw/scanner/scanners.py).
 */
public final class ScannerVisorModel {
    private ScannerVisorModel() {}

    private static final HumanoidModel<LivingEntity>[] MODELS = newArray();

    @SuppressWarnings("unchecked")
    private static HumanoidModel<LivingEntity>[] newArray() { return new HumanoidModel[KarmaScanner.Tier.values().length]; }

    /** The model for a tier (built once). */
    public static HumanoidModel<LivingEntity> get(KarmaScanner.Tier t) {
        HumanoidModel<LivingEntity> m = MODELS[t.ordinal()];
        if (m == null) MODELS[t.ordinal()] = m = new HumanoidModel<>(layer(t).bakeRoot());
        return m;
    }

    public static LayerDefinition layer(KarmaScanner.Tier t) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder head = CubeListBuilder.create();
        for (float[] c : ScannerVisorGeo.CUBES[t.ordinal()])
            head.texOffs((int) c[0], (int) c[1]).addBox(c[2], c[3], c[4], c[5], c[6], c[7]);
        root.addOrReplaceChild("head", head, PartPose.ZERO);
        for (String n : new String[]{"hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"})
            root.addOrReplaceChild(n, CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** How many cubes the worn model of a tier has (tests). */
    public static int cubes(KarmaScanner.Tier t) { return ScannerVisorGeo.CUBES[t.ordinal()].length; }

    public static ResourceLocation texture(KarmaScanner.Tier t, boolean overlay) {
        return XenoWarriors.id("textures/models/armor/" + t.id + (overlay ? "_visor_overlay" : "_visor") + ".png");
    }

    /** Draws the visor on a head (for our characters' own armor layer): tinted lens pass, then the frame. */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ModelPart parentHead, ItemStack stack) {
        if (!(stack.getItem() instanceof KarmaScanner.Item k)) return;
        HumanoidModel<LivingEntity> m = get(k.tier);
        m.setAllVisible(false);
        m.head.visible = true;
        m.head.copyFrom(parentHead);
        int c = k.getColor(stack);
        float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, b = (c & 255) / 255F;
        m.head.render(pose, ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(texture(k.tier, false)), false, stack.hasFoil()), light, overlay, r, g, b, 1F);
        m.head.render(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture(k.tier, true))), light, overlay, 1F, 1F, 1F, 1F);
    }
}

package com.xenowarriors.client.render;

import com.xenowarriors.client.LimbBend;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The 3D shape of an outfit worn by Mikiko or Rhalix: a loose gi/armor cut built around their body instead of paint on
 * the skin - a raised collar, the gi top flaring over the waist, a thick belt, wristbands and pant cuffs bunching above
 * the boots, and for armors a chest plate and shoulder pads. Every piece takes its colors straight from the outfit's
 * own texture (the matching spot of the skin layout), so it fits all of DragonMineZ's outfits, and anything the texture
 * leaves empty simply isn't there. Knees and elbows bend with him.
 */
public final class GiShapes {
    public static final String FLAP = "gi_flap", COLLAR = "gi_collar", BELT = "gi_belt", CHEST = "gi_chest", PAD = "gi_pad", CUFF = "gi_cuff";

    private GiShapes() {}

    public static PlayerModel<net.minecraft.client.player.AbstractClientPlayer> build(float inflate) {
        MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(inflate), false);      // (split limbs: see LimbBend)
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        float d = inflate;
        // gi: top flaring over the waist, a raised collar, a thick belt
        body.addOrReplaceChild(FLAP, CubeListBuilder.create().texOffs(16, 25).addBox(-4F, 9F, -2F, 8F, 3F, 4F, new CubeDeformation(d + 0.45F)), PartPose.ZERO);
        body.addOrReplaceChild(COLLAR, CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 2F, 4F, new CubeDeformation(d + 0.35F)), PartPose.ZERO);
        body.addOrReplaceChild(BELT, CubeListBuilder.create().texOffs(16, 24).addBox(-4F, 8.5F, -2F, 8F, 2F, 4F, new CubeDeformation(d + 0.6F)), PartPose.ZERO);
        // armor: a chest plate standing off the body
        body.addOrReplaceChild(CHEST, CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 8F, 4F, new CubeDeformation(d + 0.55F)), PartPose.ZERO);
        // arms: shoulder pads (armor) and wristbands
        root.getChild("right_arm").addOrReplaceChild(PAD, CubeListBuilder.create().texOffs(40, 16).addBox(-3.4F, -2.3F, -2F, 4F, 3F, 4F, new CubeDeformation(d + 0.75F)), PartPose.ZERO);
        root.getChild("left_arm").addOrReplaceChild(PAD, CubeListBuilder.create().texOffs(32, 48).addBox(-0.6F, -2.3F, -2F, 4F, 3F, 4F, new CubeDeformation(d + 0.75F)), PartPose.ZERO);
        lower(root, "right_arm").addOrReplaceChild(CUFF, CubeListBuilder.create().texOffs(40, 26).addBox(-3F, 4F, -2F, 4F, 2F, 4F, new CubeDeformation(d + 0.4F)), PartPose.ZERO);
        lower(root, "left_arm").addOrReplaceChild(CUFF, CubeListBuilder.create().texOffs(32, 58).addBox(-1F, 4F, -2F, 4F, 2F, 4F, new CubeDeformation(d + 0.4F)), PartPose.ZERO);
        // legs: baggy pants bunching above the boots
        lower(root, "right_leg").addOrReplaceChild(CUFF, CubeListBuilder.create().texOffs(0, 24).addBox(-2F, 2F, -2F, 4F, 2F, 4F, new CubeDeformation(d + 0.45F)), PartPose.ZERO);
        lower(root, "left_leg").addOrReplaceChild(CUFF, CubeListBuilder.create().texOffs(16, 56).addBox(-2F, 2F, -2F, 4F, 2F, 4F, new CubeDeformation(d + 0.45F)), PartPose.ZERO);
        return new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
    }

    private static PartDefinition lower(PartDefinition root, String limb) {
        PartDefinition l = root.getChild(limb);
        try {
            return l.getChild(LimbBend.LOWER);
        } catch (Exception e) {
            return l;
        }
    }

    private static ModelPart lowerPart(ModelPart limb) {
        return limb.hasChild(LimbBend.LOWER) ? limb.getChild(LimbBend.LOWER) : limb;
    }

    /** Armor-type outfits (Saiyan armor and the like) get the chest plate and shoulder pads instead of the gi flap. */
    public static boolean armorStyle(ResourceLocation tex) {
        String p = tex.getPath();
        return p.contains("armor") || p.contains("soldier") || p.contains("raditz") || p.contains("king_vegeta") || p.contains("nappa")
                || p.contains("cooler") || p.contains("frieza");
    }

    /** Which extra pieces show for this outfit and slot. */
    public static void setup(PlayerModel<?> m, ResourceLocation tex, net.minecraft.world.entity.EquipmentSlot slot) {
        boolean armor = armorStyle(tex);
        boolean top = slot == net.minecraft.world.entity.EquipmentSlot.CHEST, pants = slot == net.minecraft.world.entity.EquipmentSlot.LEGS;
        m.body.getChild(FLAP).visible = top && !armor;
        m.body.getChild(COLLAR).visible = top;
        m.body.getChild(BELT).visible = pants || (top && !armor);
        m.body.getChild(CHEST).visible = top && armor;
        m.rightArm.getChild(PAD).visible = top && armor;
        m.leftArm.getChild(PAD).visible = top && armor;
        lowerPart(m.rightArm).getChild(CUFF).visible = top;
        lowerPart(m.leftArm).getChild(CUFF).visible = top;
        lowerPart(m.rightLeg).getChild(CUFF).visible = pants;
        lowerPart(m.leftLeg).getChild(CUFF).visible = pants;
    }
}

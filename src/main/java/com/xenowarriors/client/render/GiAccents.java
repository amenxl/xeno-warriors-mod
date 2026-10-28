package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;

/**
 * Hand-made 3D details for well-known DragonMineZ outfits, on top of their 3D cut (GiShapes): capes (King Vegeta,
 * Piccolo, Great Saiyaman), Piccolo's big shoulder pads, the curved shoulder flaps and hip guards of Saiyan armor,
 * the knotted sash tails of the Turtle School gi, scouters, and Potara earrings. Keyed by the outfit's texture name;
 * each piece is a plain shape tinted with that outfit's colour. The cape and sash tails swing as you move.
 */
public final class GiAccents {
    private enum Kind { CAPE, BIG_PADS, SAIYAN_FLAPS, HIP_GUARDS, SASH, SCOUTER, POTARA }
    private record A(Kind kind, int color) {}

    private static final int BLUE = 0x2850C8, RED = 0xB01818, WHITE = 0xF2F2EE, YELLOW = 0xE6C24A, BROWN = 0x5A3A22, OLIVE = 0x4A5530,
            GOLD = 0xF0C020, GREEN = 0x30C040;
    private static final Map<String, List<A>> BY_OUTFIT = Map.ofEntries(
            Map.entry("goku_gi", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("goku_gi_damaged", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("goku_super", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("goku_kid", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("goten_gi", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("gohan_dbs", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("future_gohan", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("gogeta", List.of(new A(Kind.SASH, BLUE))),
            Map.entry("demon_gi_gohan", List.of(new A(Kind.SASH, RED))),
            Map.entry("mystic", List.of(new A(Kind.SASH, RED))),
            Map.entry("piccolo_gi", List.of(new A(Kind.CAPE, WHITE), new A(Kind.BIG_PADS, WHITE), new A(Kind.SASH, BLUE))),
            Map.entry("slug", List.of(new A(Kind.CAPE, WHITE), new A(Kind.BIG_PADS, WHITE))),
            Map.entry("king_vegeta", List.of(new A(Kind.CAPE, RED))),
            Map.entry("great_saiyaman_2", List.of(new A(Kind.CAPE, RED))),
            Map.entry("saiyaman_gi", List.of(new A(Kind.CAPE, RED))),
            Map.entry("saiyaman_gi_damaged", List.of(new A(Kind.CAPE, RED))),
            Map.entry("vegeta_saiyan_armor", List.of(new A(Kind.SAIYAN_FLAPS, YELLOW), new A(Kind.HIP_GUARDS, YELLOW), new A(Kind.SCOUTER, GREEN))),
            Map.entry("vegeta_saiyan_armor_damaged", List.of(new A(Kind.SAIYAN_FLAPS, YELLOW), new A(Kind.HIP_GUARDS, YELLOW))),
            Map.entry("vegetaz_armor", List.of(new A(Kind.SAIYAN_FLAPS, YELLOW), new A(Kind.HIP_GUARDS, YELLOW))),
            Map.entry("trunks_armor", List.of(new A(Kind.SAIYAN_FLAPS, YELLOW), new A(Kind.HIP_GUARDS, YELLOW))),
            Map.entry("bardock_armor", List.of(new A(Kind.SAIYAN_FLAPS, OLIVE), new A(Kind.HIP_GUARDS, OLIVE), new A(Kind.SCOUTER, GREEN))),
            Map.entry("bardockdbs_armor", List.of(new A(Kind.SAIYAN_FLAPS, OLIVE), new A(Kind.HIP_GUARDS, OLIVE))),
            Map.entry("turles_armor", List.of(new A(Kind.SAIYAN_FLAPS, BROWN), new A(Kind.HIP_GUARDS, BROWN), new A(Kind.SCOUTER, GREEN))),
            Map.entry("raditz", List.of(new A(Kind.SAIYAN_FLAPS, BROWN), new A(Kind.HIP_GUARDS, BROWN), new A(Kind.SCOUTER, GREEN))),
            Map.entry("vegetto", List.of(new A(Kind.POTARA, GOLD))),
            Map.entry("zamasu_gi", List.of(new A(Kind.POTARA, GREEN))),
            Map.entry("fzamasu_gi", List.of(new A(Kind.POTARA, GREEN))),
            Map.entry("kaioshin", List.of(new A(Kind.POTARA, GREEN))),
            Map.entry("kibito", List.of(new A(Kind.POTARA, GREEN))),
            Map.entry("blackgoku", List.of(new A(Kind.POTARA, GREEN))));
    /** DragonMineZ item names (minus "_armor_chestplate") for outfits whose texture is named differently. */
    private static final Map<String, String> ITEM_ALIAS = Map.ofEntries(
            Map.entry("goku", "goku_gi"), Map.entry("goten", "goten_gi"), Map.entry("gohan_super", "gohan_dbs"), Map.entry("demon_gi_blue", "demon_gi_gohan"),
            Map.entry("piccolo", "piccolo_gi"), Map.entry("great_saiyaman", "saiyaman_gi"), Map.entry("vegeta_z", "vegetaz_armor"),
            Map.entry("trunks_z", "trunks_armor"), Map.entry("bardock_dbz", "bardock_armor"), Map.entry("bardock_super", "bardockdbs_armor"),
            Map.entry("turles", "turles_armor"), Map.entry("zamasu", "zamasu_gi"), Map.entry("fusion_zamasu", "fzamasu_gi"), Map.entry("shin", "kaioshin"));

    private static final ResourceLocation TEX = com.xenowarriors.XenoWarriors.id("textures/models/gi_accents.png");
    private static ModelPart root, head, body, rightArm, leftArm;

    private GiAccents() {}

    /** The outfit's name from its texture ("dragonminez:textures/armor/goku_gi_layer1.png" -> "goku_gi"). */
    static String outfit(ResourceLocation tex) {
        String p = tex.getPath();
        p = p.substring(p.lastIndexOf('/') + 1);
        if (p.endsWith(".png")) p = p.substring(0, p.length() - 4);
        return p.replaceAll("_layer_?[12]$", "");
    }

    private static List<A> find(ResourceLocation tex, net.minecraft.world.item.ItemStack stack) {
        List<A> l = BY_OUTFIT.get(outfit(tex));
        if (l != null || stack == null) return l;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return null;
        String n = id.getPath().replaceAll("_armor_(chestplate|leggings|helmet|boots)$", "");
        return BY_OUTFIT.get(ITEM_ALIAS.getOrDefault(n, n));
    }

    private static void build() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        CubeDeformation no = CubeDeformation.NONE;
        PartDefinition h = r.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition b = r.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition ra = r.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition la = r.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        // cape: hangs from the shoulders to the knees
        b.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-5.5F, 0F, 0F, 11F, 21F, 0.6F, no)
                .texOffs(0, 24).addBox(-5.8F, -0.6F, -0.8F, 11.6F, 1.4F, 1.6F, no), PartPose.offset(0F, 0.2F, 2.9F));
        // Piccolo's big shoulder pads
        ra.addOrReplaceChild("big_pad", CubeListBuilder.create().texOffs(24, 0).addBox(-5.2F, -3.6F, -3.2F, 7F, 3F, 6.4F, no), PartPose.ZERO);
        la.addOrReplaceChild("big_pad", CubeListBuilder.create().texOffs(24, 0).addBox(-1.8F, -3.6F, -3.2F, 7F, 3F, 6.4F, no), PartPose.ZERO);
        // Saiyan armor: curved flaps over the shoulders, tilting up and out
        ra.addOrReplaceChild("flap", CubeListBuilder.create().texOffs(24, 10).addBox(-4.6F, -0.6F, -2.8F, 5F, 1F, 5.6F, no)
                .texOffs(24, 17).addBox(-4.9F, -0.2F, -2.8F, 0.8F, 1.8F, 5.6F, no), PartPose.offsetAndRotation(-0.5F, -2.6F, 0F, 0F, 0F, 0.42F));
        la.addOrReplaceChild("flap", CubeListBuilder.create().texOffs(24, 10).addBox(-0.4F, -0.6F, -2.8F, 5F, 1F, 5.6F, no)
                .texOffs(24, 17).addBox(4.1F, -0.2F, -2.8F, 0.8F, 1.8F, 5.6F, no), PartPose.offsetAndRotation(0.5F, -2.6F, 0F, 0F, 0F, -0.42F));
        // Saiyan armor: hip guards hanging below the belt, front and back
        b.addOrReplaceChild("hip_fr", CubeListBuilder.create().texOffs(0, 30).addBox(-4.3F, 0F, -0.4F, 3.8F, 4.2F, 0.6F, no), PartPose.offsetAndRotation(0F, 11F, -2.5F, -0.12F, 0F, 0F));
        b.addOrReplaceChild("hip_fl", CubeListBuilder.create().texOffs(0, 30).addBox(0.5F, 0F, -0.4F, 3.8F, 4.2F, 0.6F, no), PartPose.offsetAndRotation(0F, 11F, -2.5F, -0.12F, 0F, 0F));
        b.addOrReplaceChild("hip_br", CubeListBuilder.create().texOffs(0, 30).addBox(-4.3F, 0F, -0.2F, 3.8F, 4.2F, 0.6F, no), PartPose.offsetAndRotation(0F, 11F, 2.5F, 0.12F, 0F, 0F));
        b.addOrReplaceChild("hip_bl", CubeListBuilder.create().texOffs(0, 30).addBox(0.5F, 0F, -0.2F, 3.8F, 4.2F, 0.6F, no), PartPose.offsetAndRotation(0F, 11F, 2.5F, 0.12F, 0F, 0F));
        // gi sash: a knot at the front-left of the belt and two tails hanging from it
        b.addOrReplaceChild("knot", CubeListBuilder.create().texOffs(10, 30).addBox(1.4F, 9.2F, -3.3F, 2.4F, 2F, 1.2F, no), PartPose.ZERO);
        b.addOrReplaceChild("tail_a", CubeListBuilder.create().texOffs(20, 30).addBox(-0.6F, 0F, -0.25F, 1.2F, 5.5F, 0.5F, no), PartPose.offset(2.0F, 11F, -3F));
        b.addOrReplaceChild("tail_b", CubeListBuilder.create().texOffs(20, 30).addBox(-0.6F, 0F, -0.25F, 1.2F, 4.6F, 0.5F, no), PartPose.offset(3.3F, 11F, -3F));
        // scouter on the left eye: ear box, arm round to the front, green lens
        h.addOrReplaceChild("scouter", CubeListBuilder.create().texOffs(40, 24).addBox(4.0F, -6F, -1.6F, 1.2F, 3F, 3F, no)
                .texOffs(40, 30).addBox(3.7F, -6.4F, -4.9F, 1F, 1F, 3.8F, no), PartPose.ZERO);
        h.addOrReplaceChild("lens", CubeListBuilder.create().texOffs(50, 24).addBox(0.6F, -6.6F, -5.0F, 3.4F, 2.6F, 0.3F, no), PartPose.ZERO);
        // Potara earrings
        h.addOrReplaceChild("potara", CubeListBuilder.create().texOffs(40, 36).addBox(-4.7F, -3.4F, -0.6F, 0.7F, 1.6F, 1.2F, no)
                .texOffs(40, 36).addBox(4.0F, -3.4F, -0.6F, 0.7F, 1.6F, 1.2F, no), PartPose.ZERO);
        root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        head = root.getChild("head"); body = root.getChild("body"); rightArm = root.getChild("right_arm"); leftArm = root.getChild("left_arm");
    }

    private static void only(String... show) {
        for (ModelPart p : new ModelPart[]{head, body, rightArm, leftArm})
            p.getAllParts().forEach(c -> { if (c != p) c.visible = false; });
        for (String s : show) for (ModelPart p : new ModelPart[]{head, body, rightArm, leftArm}) if (p.hasChild(s)) p.getChild(s).visible = true;
    }

    /** Draws this outfit's details (if it has any) on a body posed like `pose`. */
    public static void render(PoseStack ps, MultiBufferSource buffers, ResourceLocation tex, net.minecraft.world.item.ItemStack stack, HumanoidModel<?> pose, float swing, float ageInTicks, int light) {
        List<A> list = find(tex, stack);
        if (list == null) return;
        if (root == null) build();
        head.copyFrom(pose.head); body.copyFrom(pose.body); rightArm.copyFrom(pose.rightArm); leftArm.copyFrom(pose.leftArm);
        // the cape and sash tails swing with movement and a little breeze
        float breeze = Mth.sin(ageInTicks * 0.08F) * 0.04F;
        body.getChild("cape").xRot = 0.06F + Mth.clamp(swing, 0F, 1F) * 0.75F + breeze;
        body.getChild("tail_a").xRot = -0.1F - Mth.clamp(swing, 0F, 1F) * 0.5F + breeze;
        body.getChild("tail_b").xRot = -0.05F - Mth.clamp(swing, 0F, 1F) * 0.45F - breeze;
        var vc = buffers.getBuffer(RenderType.entityTranslucent(TEX));
        for (A a : list) {
            float r = (a.color >> 16 & 255) / 255F, g = (a.color >> 8 & 255) / 255F, b = (a.color & 255) / 255F;
            switch (a.kind) {
                case CAPE -> only("cape");
                case BIG_PADS -> only("big_pad");
                case SAIYAN_FLAPS -> only("flap");
                case HIP_GUARDS -> only("hip_fr", "hip_fl", "hip_br", "hip_bl");
                case SASH -> only("knot", "tail_a", "tail_b");
                case SCOUTER -> only("scouter");
                case POTARA -> only("potara");
            }
            float rr = a.kind == Kind.SCOUTER ? 0.2F : r, gg = a.kind == Kind.SCOUTER ? 0.22F : g, bb = a.kind == Kind.SCOUTER ? 0.26F : b;
            for (ModelPart p : new ModelPart[]{head, body, rightArm, leftArm}) p.render(ps, vc, light, OverlayTexture.NO_OVERLAY, rr, gg, bb, 1F);
            if (a.kind == Kind.SCOUTER) {          // the lens: see-through green
                only("lens");
                head.render(ps, vc, light, OverlayTexture.NO_OVERLAY, r, g, b, 0.6F);
            }
        }
    }
}

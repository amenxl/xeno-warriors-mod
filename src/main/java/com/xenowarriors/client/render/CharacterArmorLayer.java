package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Custom armor for the characters. When Mikiko or Rhalix wears armor, the normal armor model is
 * hidden (it would clip through their hair and clothes) and these fitted plates are drawn instead,
 * sitting just outside the outfit. They're tinted by the armor's material - leather keeps its dye -
 * and keep the enchantment shimmer.
 */
public class CharacterArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private final Map<CharacterType, Map<EquipmentSlot, DetailModel>> models = new EnumMap<>(CharacterType.class);
    private final Map<CharacterType, DetailModel> hoodies = new EnumMap<>(CharacterType.class);
    private final Map<CharacterType, DetailModel> hoodieTrims = new EnumMap<>(CharacterType.class);
    private final Map<CharacterType, DetailModel> srHoodies = new EnumMap<>(CharacterType.class);
    private final Map<CharacterType, Map<EquipmentSlot, DetailModel>> skyModels = new EnumMap<>(CharacterType.class);
    private static final ResourceLocation SKY_TEX = XenoWarriors.id("textures/entity/character/sky_armor.png");
    private final Map<CharacterType, Map<EquipmentSlot, DetailModel>> corruptedModels = new EnumMap<>(CharacterType.class);
    /** Vanilla materials, each with its own 3D cut (tools/xw/armor/vanilla_armor.py). */
    private static final String[] VANILLA = {"leather", "chainmail", "iron", "gold", "diamond", "netherite"};
    private final Map<CharacterType, Map<String, DetailModel>> vanillaModels = new EnumMap<>(CharacterType.class);
    private static final ResourceLocation CORRUPTED_TEX = XenoWarriors.id("textures/entity/character/corrupted_armor.png");

    public CharacterArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        for (CharacterType type : CharacterType.PLAYABLE) {
            Map<EquipmentSlot, DetailModel> bySlot = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : SLOTS) {
                DetailModel m = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/"
                        + type.getId() + "_" + slotName(slot) + ".json");
                if (m != null) bySlot.put(slot, m);
            }
            models.put(type, bySlot);
            DetailModel hoodie = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/" + type.getId() + "_hoodie.json");
            if (hoodie != null) hoodies.put(type, hoodie);
            DetailModel trim = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/" + type.getId() + "_hoodie_trim.json");
            if (trim != null) hoodieTrims.put(type, trim);
            Map<EquipmentSlot, DetailModel> sky = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : SLOTS) {
                DetailModel m = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/" + type.getId() + "_sky_" + slotName(slot) + ".json");
                if (m != null) sky.put(slot, m);
            }
            skyModels.put(type, sky);
            Map<EquipmentSlot, DetailModel> corr = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : SLOTS) {
                DetailModel m = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/" + type.getId() + "_corrupted_" + slotName(slot) + ".json");
                if (m != null) corr.put(slot, m);
            }
            corruptedModels.put(type, corr);
            Map<String, DetailModel> van = new java.util.HashMap<>();
            for (String mat : VANILLA) for (EquipmentSlot slot : SLOTS) {
                DetailModel m = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/" + type.getId() + "_" + mat + "_" + slotName(slot) + ".json");
                if (m != null) van.put(mat + "_" + slotName(slot), m);
            }
            vanillaModels.put(type, van);
            DetailModel sr = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/" + type.getId() + "_sr_hoodie.json");
            if (sr != null) srHoodies.put(type, sr);
        }
    }

    private net.minecraft.client.model.HumanoidModel<AbstractClientPlayer> inner, outer;
    /** Other mods' skin-style outfits on his own bending body (knees and elbows bend with him, nothing pokes through). */
    private PlayerModel<AbstractClientPlayer> giInner, giOuter;

    /** Is he wearing another mod's armor in this slot? */
    public static boolean foreignWorn(AbstractClientPlayer p, EquipmentSlot slot) {
        ItemStack s = p.getItemBySlot(slot);
        return s.getItem() instanceof ArmorItem a && a.getEquipmentSlot() == slot && (foreign(s) || s.getItem() instanceof com.xenowarriors.item.SkinLayoutArmor);
    }

    /** Armor from another mod (not vanilla's materials, not ours). */
    private static boolean foreign(ItemStack stack) {
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && !key.getNamespace().equals("minecraft") && !key.getNamespace().equals(XenoWarriors.MOD_ID);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderForeign(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack stack,
                               ArmorItem armor, EquipmentSlot slot) {
        if (inner == null) {
            var set = net.minecraft.client.Minecraft.getInstance().getEntityModels();
            inner = new net.minecraft.client.model.HumanoidModel<>(set.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_INNER_ARMOR));
            outer = new net.minecraft.client.model.HumanoidModel<>(set.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR));
        }
        boolean legs = slot == EquipmentSlot.LEGS;
        net.minecraft.client.model.HumanoidModel<AbstractClientPlayer> base = legs ? inner : outer;
        getParentModel().copyPropertiesTo(base);
        showFor(base, slot);
        net.minecraft.client.model.Model model = net.minecraftforge.client.ForgeHooksClient.getArmorModel(player, stack, slot, base);
        boolean giVoxels = false;
        String name = armor.getMaterial().getName();
        String domain = "minecraft";
        int colon = name.indexOf(':');
        if (colon >= 0) { domain = name.substring(0, colon); name = name.substring(colon + 1); }
        String def = String.format(java.util.Locale.ROOT, "%s:textures/models/armor/%s_layer_%d.png", domain, name, legs ? 2 : 1);
        ResourceLocation tex = new ResourceLocation(net.minecraftforge.client.ForgeHooksClient.getArmorTexture(player, stack, def, slot, null));
        // our skin-layout armor (Fox PJs, Flower Armor) has a 64x64 skin-layout texture for characters; the 64x32 one is for vanilla bodies
        if (armor instanceof com.xenowarriors.item.SkinLayoutArmor)
            tex = new ResourceLocation(domain, String.format(java.util.Locale.ROOT, "textures/models/armor/%s_skin_layer_%d.png", name, legs ? 2 : 1));
        // plain skin-layout outfits (vanilla-style or DragonMineZ's ArmorBaseModel): drawn on a model that bends like his
        if (model == base || model.getClass().getSimpleName().equals("ArmorBaseModel")) {
            if (giInner == null) {
                giInner = GiShapes.build(0.4F);         // pants: loose
                giOuter = GiShapes.build(0.62F);        // tops: looser still, over the pants
            }
            PlayerModel<AbstractClientPlayer> gi = legs ? giInner : giOuter;
            PlayerModel<AbstractClientPlayer> parent = getParentModel();
            parent.copyPropertiesTo(gi);
            gi.hat.copyFrom(gi.head);
            gi.jacket.copyFrom(gi.body);
            gi.rightSleeve.copyFrom(gi.rightArm);
            gi.leftSleeve.copyFrom(gi.leftArm);
            gi.rightPants.copyFrom(gi.rightLeg);
            gi.leftPants.copyFrom(gi.leftLeg);
            com.xenowarriors.client.LimbBend.copy(parent.rightArm, gi.rightArm);
            com.xenowarriors.client.LimbBend.copy(parent.leftArm, gi.leftArm);
            com.xenowarriors.client.LimbBend.copy(parent.rightLeg, gi.rightLeg);
            com.xenowarriors.client.LimbBend.copy(parent.leftLeg, gi.leftLeg);
            com.xenowarriors.client.LimbBend.copy(parent.rightArm, gi.rightSleeve);
            com.xenowarriors.client.LimbBend.copy(parent.leftArm, gi.leftSleeve);
            com.xenowarriors.client.LimbBend.copy(parent.rightLeg, gi.rightPants);
            com.xenowarriors.client.LimbBend.copy(parent.leftLeg, gi.leftPants);
            gi.setAllVisible(false);
            switch (slot) {
                case HEAD -> { gi.head.visible = true; gi.hat.visible = true; }
                case CHEST -> { gi.body.visible = gi.jacket.visible = true; gi.rightArm.visible = gi.leftArm.visible = true;
                                gi.rightSleeve.visible = gi.leftSleeve.visible = true; }
                case LEGS -> { gi.body.visible = true; gi.rightLeg.visible = gi.leftLeg.visible = true;
                               gi.rightPants.visible = gi.leftPants.visible = true; }
                default -> { gi.rightLeg.visible = gi.leftLeg.visible = true; gi.rightPants.visible = gi.leftPants.visible = true; }
            }
            // the outer layer comes out in 3D (raised blocks), so its flat version is left off
            gi.hat.visible = false; gi.jacket.visible = false;
            gi.rightSleeve.visible = gi.leftSleeve.visible = gi.rightPants.visible = gi.leftPants.visible = false;
            GiShapes.setup(gi, tex, slot);          // its 3D cut: collar, gi flap, belt, cuffs (or armor plate and pads)
            model = gi;
            giVoxels = true;
        } else if (model != base && model instanceof net.minecraft.client.model.HumanoidModel hm) {
            ((net.minecraft.client.model.HumanoidModel) getParentModel()).copyPropertiesTo(hm);
            showFor(hm, slot);
        }
        float[] c = tint(stack, armor);
        if (!(armor instanceof net.minecraft.world.item.DyeableLeatherItem)) c = new float[]{1F, 1F, 1F};
        VertexConsumer vc = net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(tex), false, stack.hasFoil());
        model.renderToBuffer(pose, vc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1F);
        if (giVoxels) {
            PlayerModel<AbstractClientPlayer> gi = (PlayerModel<AbstractClientPlayer>) model;
            // show the parts this slot covers again so the 3D layer knows where to go
            switch (slot) {
                case HEAD -> gi.head.visible = true;
                case CHEST -> { gi.body.visible = true; gi.rightArm.visible = gi.leftArm.visible = true; }
                case LEGS -> { gi.rightLeg.visible = gi.leftLeg.visible = true; gi.body.visible = false; }
                default -> { gi.rightLeg.visible = gi.leftLeg.visible = true; }
            }
            GiVoxels.render(pose, net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(tex), false, stack.hasFoil()),
                    tex, gi, legs ? 0.32F : 0.62F, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, c[0], c[1], c[2]);
            if (slot == EquipmentSlot.CHEST)          // hand-made details for well-known outfits (capes, Saiyan flaps, sashes, scouters, earrings)
                GiAccents.render(pose, buffers, tex, stack, getParentModel(), player.walkAnimation.speed(), player.tickCount + net.minecraft.client.Minecraft.getInstance().getFrameTime(), light);
        }
    }

    private static void showFor(net.minecraft.client.model.HumanoidModel<?> m, EquipmentSlot slot) {
        m.setAllVisible(false);
        switch (slot) {
            case HEAD -> { m.head.visible = true; m.hat.visible = true; }
            case CHEST -> { m.body.visible = true; m.rightArm.visible = true; m.leftArm.visible = true; }
            case LEGS -> { m.body.visible = true; m.rightLeg.visible = true; m.leftLeg.visible = true; }
            default -> { m.rightLeg.visible = true; m.leftLeg.visible = true; }
        }
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            default -> "boots";
        };
    }

    /** Whether this player's armor is drawn by us instead of the vanilla armor layer. */
    public static boolean replacesVanilla(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof AbstractClientPlayer p && ClientCharacterData.get(p) != CharacterType.NONE && ClientCharacterData.get(p) != CharacterType.YOU;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        CharacterType type = ClientCharacterData.get(player);
        Map<EquipmentSlot, DetailModel> bySlot = models.get(type);
        if (bySlot == null) return;
        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        ResourceLocation texture = XenoWarriors.id("textures/entity/character/" + type.getId() + "_armor.png");
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0F);

        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) continue;
            // other mods' armor (DragonMineZ's gis and armors...): worn as that mod made it, its own model and texture
            // (the Fox PJs are a skin-layout onesie too: worn the same way over any outfit)
            if (foreign(stack) || stack.getItem() instanceof com.xenowarriors.item.SkinLayoutArmor) {
                renderForeign(pose, buffers, light, player, stack, armor, slot);
                continue;
            }
            DetailModel m = bySlot.get(slot);
            boolean sr = stack.getItem() instanceof com.xenowarriors.item.SrHoodieItem;
            boolean hoodie = stack.getItem() instanceof com.xenowarriors.item.KarmaHoodieItem;
            if (hoodie) m = hoodies.get(type);
            if (sr) m = srHoodies.get(type);
            boolean skyArmor = stack.getItem() instanceof com.xenowarriors.sky.SkyArmorItem;     // its own 3D set and texture
            if (skyArmor && skyModels.get(type) != null) m = skyModels.get(type).get(slot);
            boolean corruptArmor = stack.getItem() instanceof com.xenowarriors.sky.CorruptedArmorItem;
            if (corruptArmor && corruptedModels.get(type) != null) m = corruptedModels.get(type).get(slot);
            String mat = vanillaMat(armor);
            DetailModel vm = mat == null || vanillaModels.get(type) == null ? null : vanillaModels.get(type).get(mat + "_" + slotName(slot));
            if (vm != null && !hoodie && !sr && !skyArmor && !corruptArmor) m = vm; else mat = null;
            if (m == null) continue;
            m.head.copyFrom(parent.head);
            m.body.copyFrom(parent.body);
            m.rightArm.copyFrom(parent.rightArm);
            m.leftArm.copyFrom(parent.leftArm);
            m.rightLeg.copyFrom(parent.rightLeg);
            m.leftLeg.copyFrom(parent.leftLeg);
            m.copyBends(parent);
            if (hoodie) {          // the hood: up in the rain, bunched at the neck otherwise
                boolean up = com.xenowarriors.client.mikiko.HoodClient.up(player);
                if (m.hoodUp != null) m.hoodUp.visible = up;
                if (m.hoodDown != null) m.hoodDown.visible = !up;
            }
            float[] c = sr || skyArmor || corruptArmor || (mat != null && !mat.equals("leather")) ? new float[]{1F, 1F, 1F} : tint(stack, armor);
            // Mikiko: no armor on his left arm, so his shoulder plate shows
            m.leftArm.visible = type != CharacterType.MIKIKO;   // Mikiko: nothing on his left arm (armor or hoodies), so his shoulder plate shows   // the hoodie's dye comes through here too (it's dyeable like leather)
            ResourceLocation tex = skyArmor ? SKY_TEX : corruptArmor ? CORRUPTED_TEX
                    : mat != null ? XenoWarriors.id("textures/entity/character/vanilla_" + mat + ".png") : texture;
            if ((hoodie || sr) && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                // hoodies get scuffed below half durability and ripped up below a quarter
                float worn = stack.getDamageValue() / (float) stack.getMaxDamage();
                if (worn >= 0.75F) tex = XenoWarriors.id("textures/entity/character/" + type.getId() + "_armor_torn.png");
                else if (worn >= 0.5F) tex = XenoWarriors.id("textures/entity/character/" + type.getId() + "_armor_worn.png");
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
            m.root.render(pose, vc, light, overlay, c[0], c[1], c[2], 1F);
            if (hoodie && hoodieTrims.get(type) != null) {
                DetailModel t = hoodieTrims.get(type);
                t.body.copyFrom(parent.body);
                t.rightArm.copyFrom(parent.rightArm);
                t.leftArm.copyFrom(parent.leftArm);
                t.copyBends(parent);
                t.leftArm.visible = type != CharacterType.MIKIKO;
                t.root.render(pose, vc, light, overlay, 1F, 1F, 1F, 1F);
                t.leftArm.visible = true;
            }
            if (stack.hasFoil()) {
                m.root.render(pose, buffers.getBuffer(RenderType.armorEntityGlint()), light, overlay, 1F, 1F, 1F, 1F);
            }
            m.leftArm.visible = true;
        }
        foxPjsExtras(pose, buffers, light, player);
    }

    private static final ResourceLocation FOX_EXTRAS_TEX = XenoWarriors.id("textures/entity/fox_pjs_extras.png");
    private static net.minecraft.client.model.geom.ModelPart foxEars, foxTail;

    /** The Fox PJs' 3D bits: ears on the hood, and the white-tipped tail on the back. */
    private void foxPjsExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player) {
        boolean hood = player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof com.xenowarriors.item.FoxPjsItem;
        boolean top = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.xenowarriors.item.FoxPjsItem;
        if (!hood && !top) return;
        if (foxEars == null) {
            var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
            var root = mesh.getRoot();
            var ears = root.addOrReplaceChild("ears", net.minecraft.client.model.geom.builders.CubeListBuilder.create(), net.minecraft.client.model.geom.PartPose.ZERO);
            for (int s = -1; s <= 1; s += 2) {
                ears.addOrReplaceChild("ear" + s, net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                                .texOffs(0, 0).addBox(-1.75F, -3.6F, -0.6F, 3.5F, 3.6F, 1.2F)              // orange outside
                                .texOffs(16, 0).addBox(-1.25F, -3.0F, -0.75F, 2.5F, 2.8F, 0.2F)            // white rim
                                .texOffs(32, 0).addBox(-0.8F, -2.4F, -0.85F, 1.6F, 2.0F, 0.2F)             // dark inside
                                .texOffs(0, 0).addBox(-0.9F, -4.6F, -0.5F, 1.8F, 1.1F, 1.0F),
                        net.minecraft.client.model.geom.PartPose.offsetAndRotation(s * 2.6F, -8.4F, -0.8F, 0F, 0F, s * 0.28F));
            }
            var tail = root.addOrReplaceChild("tail", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-1.4F, -1.4F, 0F, 2.8F, 2.8F, 3.6F)
                            .texOffs(16, 0).addBox(-1.5F, -1.5F, 3.4F, 3.0F, 3.0F, 1.8F),                  // white tip
                    net.minecraft.client.model.geom.PartPose.offsetAndRotation(0F, 10.6F, 2.2F, 0.9F, 0F, 0F));
            var baked = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 32).bakeRoot();
            foxEars = baked.getChild("ears"); foxTail = baked.getChild("tail");
        }
        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FOX_EXTRAS_TEX));
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0F);
        if (hood) {
            pose.pushPose();
            parent.head.translateAndRotate(pose);
            foxEars.render(pose, vc, light, overlay);
            pose.popPose();
        }
        if (top) {
            pose.pushPose();
            parent.body.translateAndRotate(pose);
            float sway = net.minecraft.util.Mth.sin(player.tickCount * 0.12F) * 0.15F;
            foxTail.yRot = sway;
            foxTail.render(pose, vc, light, overlay);
            pose.popPose();
        }
    }

    private static String vanillaMat(ArmorItem armor) {
        if (!(armor.getMaterial() instanceof ArmorMaterials m)) return null;
        return switch (m) {
            case LEATHER -> "leather";
            case CHAIN -> "chainmail";
            case IRON -> "iron";
            case GOLD -> "gold";
            case DIAMOND -> "diamond";
            case NETHERITE -> "netherite";
            default -> null;       // turtle shell keeps the shared cut
        };
    }

    /** Plate color for each armor material. */
    private static float[] tint(ItemStack stack, ArmorItem armor) {
        if (armor instanceof DyeableLeatherItem dye) {
            int rgb = dye.getColor(stack);
            return new float[]{(rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F};
        }
        if (armor.getMaterial() instanceof ArmorMaterials m) {
            return switch (m) {
                case CHAIN -> new float[]{0.62F, 0.64F, 0.7F};
                case IRON -> new float[]{0.88F, 0.89F, 0.93F};
                case GOLD -> new float[]{1.0F, 0.83F, 0.28F};
                case DIAMOND -> new float[]{0.42F, 0.93F, 0.95F};
                case NETHERITE -> new float[]{0.36F, 0.32F, 0.35F};
                case TURTLE -> new float[]{0.4F, 0.78F, 0.38F};
                default -> new float[]{0.7F, 0.55F, 0.4F};
            };
        }
        return new float[]{0.75F, 0.75F, 0.8F};   // armor from other mods
    }
}

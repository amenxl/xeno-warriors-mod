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
            DetailModel sr = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/armor/" + type.getId() + "_sr_hoodie.json");
            if (sr != null) srHoodies.put(type, sr);
        }
    }

    private net.minecraft.client.model.HumanoidModel<AbstractClientPlayer> inner, outer;

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
        if (model != base && model instanceof net.minecraft.client.model.HumanoidModel hm) {
            ((net.minecraft.client.model.HumanoidModel) getParentModel()).copyPropertiesTo(hm);
            showFor(hm, slot);
        }
        String name = armor.getMaterial().getName();
        String domain = "minecraft";
        int colon = name.indexOf(':');
        if (colon >= 0) { domain = name.substring(0, colon); name = name.substring(colon + 1); }
        String def = String.format(java.util.Locale.ROOT, "%s:textures/models/armor/%s_layer_%d.png", domain, name, legs ? 2 : 1);
        ResourceLocation tex = new ResourceLocation(net.minecraftforge.client.ForgeHooksClient.getArmorTexture(player, stack, def, slot, null));
        float[] c = tint(stack, armor);
        if (!(armor instanceof net.minecraft.world.item.DyeableLeatherItem)) c = new float[]{1F, 1F, 1F};
        VertexConsumer vc = net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(tex), false, stack.hasFoil());
        model.renderToBuffer(pose, vc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1F);
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
        return entity instanceof AbstractClientPlayer p && ClientCharacterData.get(p) != CharacterType.NONE;
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
            if (foreign(stack)) {
                renderForeign(pose, buffers, light, player, stack, armor, slot);
                continue;
            }
            DetailModel m = bySlot.get(slot);
            boolean sr = stack.getItem() instanceof com.xenowarriors.item.SrHoodieItem;
            boolean hoodie = stack.getItem() instanceof com.xenowarriors.item.KarmaHoodieItem;
            if (hoodie) m = hoodies.get(type);
            if (sr) m = srHoodies.get(type);
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
            float[] c = sr ? new float[]{1F, 1F, 1F} : tint(stack, armor);
            // Mikiko: no armor on his left arm, so his shoulder plate shows
            m.leftArm.visible = type != CharacterType.MIKIKO;   // Mikiko: nothing on his left arm (armor or hoodies), so his shoulder plate shows   // the hoodie's dye comes through here too (it's dyeable like leather)
            ResourceLocation tex = texture;
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

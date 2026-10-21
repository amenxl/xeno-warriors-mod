package com.xenowarriors.client.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/** Armor a pet fox wears: a helmet over its head and plates along its back, in the armor's material. */
public class FoxArmorLayer extends RenderLayer<Fox, FoxModel<Fox>> {
    private static final float INFLATE = 1.09F;
    private final FoxModel<Fox> model;

    public FoxArmorLayer(RenderLayerParent<Fox, FoxModel<Fox>> parent, EntityModelSet models) {
        super(parent);
        model = new FoxModel<>(models.bakeLayer(ModelLayers.FOX));
    }

    private static String material(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem a && a.getMaterial() instanceof ArmorMaterials m) {
            return switch (m) {
                case LEATHER -> "leather";
                case CHAIN -> "chainmail";
                case GOLD -> "gold";
                case DIAMOND -> "diamond";
                case NETHERITE -> "netherite";
                case TURTLE -> "turtle";
                default -> "iron";
            };
        }
        return "iron";
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, Fox fox, float limbSwing, float limbAmount,
                       float partialTick, float age, float yaw, float pitch) {
        ItemStack head = fox.getItemBySlot(EquipmentSlot.HEAD), chest = fox.getItemBySlot(EquipmentSlot.CHEST);
        if ((head.isEmpty() && chest.isEmpty()) || fox.isInvisible()) return;
        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(fox, limbSwing, limbAmount, partialTick);
        model.setupAnim(fox, limbSwing, limbAmount, age, yaw, pitch);
        ModelPart[] parts = FoxClientFx.parts(model);
        if (parts != null) {
            for (ModelPart p : new ModelPart[]{parts[0], parts[1]}) {
                p.xScale = INFLATE;
                p.yScale = INFLATE;
                p.zScale = INFLATE;
            }
        }
        draw(pose, buffers, light, head, "helmet");
        draw(pose, buffers, light, chest, "chest");
        if (parts != null) {
            for (ModelPart p : new ModelPart[]{parts[0], parts[1]}) {
                p.xScale = 1F;
                p.yScale = 1F;
                p.zScale = 1F;
            }
        }
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack, String piece) {
        if (stack.isEmpty()) return;
        ResourceLocation tex = XenoWarriors.id("textures/entity/fox/armor_" + piece + "_" + material(stack) + ".png");
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        if (stack.hasFoil()) {
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        }
    }
}

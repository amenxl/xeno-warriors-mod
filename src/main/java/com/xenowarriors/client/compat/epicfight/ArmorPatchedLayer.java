package com.xenowarriors.client.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.client.render.CharacterArmorLayer;
import com.xenowarriors.client.render.DetailModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Our fitted armor in Epic Fight's battle mode: the same 3D pieces as without Epic Fight (CharacterArmorLayer), baked
 * onto Epic Fight's skeleton so they bend and swing with its animations - instead of Epic Fight's own blocky vanilla
 * armor, which clipped through the characters' hair, coats and wings.
 */
public class ArmorPatchedLayer extends PatchedLayer<AbstractClientPlayer, AbstractClientPlayerPatch<AbstractClientPlayer>,
        PlayerModel<AbstractClientPlayer>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private static final Map<String, SkinnedMesh> BAKED = new HashMap<>();
    /** (tests) pieces drawn on Epic Fight's skeleton. */
    public static int drawn;

    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                               RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer, PoseStack pose,
                               MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partial) {
        if (player.isInvisible() || !CharacterArmorLayer.replacesVanilla(player)) return;
        for (EquipmentSlot slot : SLOTS) {
            CharacterArmorLayer.Pick pk = CharacterArmorLayer.pick(player, slot);
            if (pk == null) continue;
            String key = pk.path() + (pk.noLeftArm() ? "#noleft" : "");
            SkinnedMesh mesh = BAKED.computeIfAbsent(key, k -> {
                DetailModel m = DetailModel.loadWhole(pk.path());
                if (m == null) return null;
                SkinnedMesh[] b = DetailPatchedLayer.bakeModelHiding(m, pk.noLeftArm());
                return b == null ? null : b[0];
            });
            if (mesh == null) continue;
            float[] c = pk.color();
            mesh.draw(pose, buffers, RenderType.entityCutoutNoCull(pk.tex()), light, c[0], c[1], c[2], 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            if (pk.foil()) mesh.draw(pose, buffers, RenderType.armorEntityGlint(), light, 1F, 1F, 1F, 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            drawn++;
        }
    }

    /**
     * Epic Fight's own armor layer, but for our characters only the armor we don't draw ourselves (other mods' outfits,
     * visors): the armor we fit is left off it for the moment it draws.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static class VanillaFilter extends PatchedLayer<AbstractClientPlayer, AbstractClientPlayerPatch<AbstractClientPlayer>,
            PlayerModel<AbstractClientPlayer>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> {
        private final PatchedLayer original;
        public VanillaFilter(PatchedLayer original) { this.original = original; }

        @Override
        protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                                   RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer, PoseStack pose,
                                   MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partial) {
            if (!CharacterArmorLayer.replacesVanilla(player)) {
                original.renderLayer(player, patch, layer, pose, buffers, light, poses, bob, yRot, xRot, partial);
                return;
            }
            var armor = player.getInventory().armor;
            ItemStack[] kept = new ItemStack[armor.size()];
            try {
                for (int i = 0; i < armor.size(); i++) {
                    kept[i] = armor.get(i);
                    if (CharacterArmorLayer.ours(kept[i])) armor.set(i, ItemStack.EMPTY);
                }
                original.renderLayer(player, patch, layer, pose, buffers, light, poses, bob, yRot, xRot, partial);
            } finally {
                for (int i = 0; i < armor.size(); i++) armor.set(i, kept[i]);
            }
        }
    }
}

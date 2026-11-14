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
    private static final Map<String, DetailPatchedLayer.Rig> RIGS = new HashMap<>();
    /** (tests) pieces drawn on Epic Fight's skeleton. */
    public static int drawn, trims;

    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                               RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer, PoseStack pose,
                               MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partial) {
        if (player.isInvisible() || !CharacterArmorLayer.replacesVanilla(player)) return;
        for (EquipmentSlot slot : SLOTS) {
            CharacterArmorLayer.Pick pk = CharacterArmorLayer.pick(player, slot);
            if (pk == null) continue;
            String key = pk.path() + (pk.noLeftArm() ? "#noleft" : "");
            float[] c = pk.color();
            if (DetailPatchedLayer.RIGID) {
                // rigid pieces on the bones, cut at the elbows and knees like our own renderer (the user's video: the
                // Karma Hoodie came out puffed up and long-sleeved when baked into a skinned mesh)
                DetailPatchedLayer.Rig rig = RIGS.computeIfAbsent(key, k -> {
                    DetailModel m = DetailModel.loadForEpicFight(pk.path());
                    return m == null ? null : new DetailPatchedLayer.Rig(m, true, pk.noLeftArm());
                });
                if (rig == null) continue;
                boolean hoodie = pk.path().endsWith("_hoodie.json") && !pk.path().endsWith("_sr_hoodie.json");
                boolean hood = hoodie && com.xenowarriors.client.mikiko.HoodClient.up(player);   // (up in the rain, down otherwise - as without Epic Fight)
                var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(pk.tex()));
                rig.draw(patch.getArmature(), pose, vc, light, OverlayTexture.NO_OVERLAY, poses, hood, c[0], c[1], c[2]);
                if (hoodie) {
                    // the Karma Hoodie's trim (its white bands and design, undyed) - it was missing in battle mode
                    String trimPath = pk.path().replace("_hoodie.json", "_hoodie_trim.json");
                    DetailPatchedLayer.Rig trim = RIGS.computeIfAbsent(trimPath + (pk.noLeftArm() ? "#noleft" : ""), k -> {
                        DetailModel tm = DetailModel.loadForEpicFight(trimPath);
                        return tm == null ? null : new DetailPatchedLayer.Rig(tm, true, pk.noLeftArm());
                    });
                    if (trim != null) { trim.draw(patch.getArmature(), pose, vc, light, OverlayTexture.NO_OVERLAY, poses, hood, 1F, 1F, 1F); trims++; }
                }
                if (pk.foil()) rig.draw(patch.getArmature(), pose, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY, poses, hood, 1F, 1F, 1F);
                drawn++;
                continue;
            }
            SkinnedMesh mesh = BAKED.computeIfAbsent(key, k -> {
                DetailModel m = DetailModel.loadWhole(pk.path());
                if (m == null) return null;
                SkinnedMesh[] b = DetailPatchedLayer.bakeModelHiding(m, pk.noLeftArm());
                return b == null ? null : b[0];
            });
            if (mesh == null) continue;
            mesh.draw(pose, buffers, RenderType.entityCutoutNoCull(pk.tex()), light, c[0], c[1], c[2], 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            if (pk.foil()) mesh.draw(pose, buffers, RenderType.armorEntityGlint(), light, 1F, 1F, 1F, 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            drawn++;
        }
        // other mods' skin-layout outfits (DragonMineZ's gis and armors): their texture on a loose skin-layout body on
        // the skeleton, like the shape we give them without Epic Fight (Epic Fight's own armor layer made a mess of them)
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = player.getItemBySlot(slot);
            if (!(st.getItem() instanceof net.minecraft.world.item.ArmorItem a) || !CharacterArmorLayer.skinForeign(player, st, slot)) continue;
            SkinnedMesh mesh = giArmor(slot);
            if (mesh == null) continue;
            float[] c = CharacterArmorLayer.tintOf(st, a);
            var rt = RenderType.armorCutoutNoCull(CharacterArmorLayer.foreignTex(player, st, a, slot));
            mesh.draw(pose, buffers, rt, light, c[0], c[1], c[2], 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            if (st.hasFoil()) mesh.draw(pose, buffers, RenderType.armorEntityGlint(), light, 1F, 1F, 1F, 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            giDrawn++;
        }
        // other mods' armor in the old 64x32 layout (Epic Fight's own Stray Hat...): the plain armor model on the skeleton
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = player.getItemBySlot(slot);
            if (!(st.getItem() instanceof net.minecraft.world.item.ArmorItem a) || a.getEquipmentSlot() != slot || !CharacterArmorLayer.oldForeign(player, st)) continue;
            SkinnedMesh mesh = oldArmor(slot);
            if (mesh == null) continue;
            float[] c = CharacterArmorLayer.tintOf(st, a);
            mesh.draw(pose, buffers, RenderType.armorCutoutNoCull(CharacterArmorLayer.foreignTex(player, st, a, slot)), light, c[0], c[1], c[2], 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            if (st.hasFoil()) mesh.draw(pose, buffers, RenderType.armorEntityGlint(), light, 1F, 1F, 1F, 1F, OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
            oldDrawn++;
        }
    }

    /** (tests) skin-layout outfits (DragonMineZ's) drawn on Epic Fight's skeleton. */
    public static int giDrawn;
    private static final Map<EquipmentSlot, SkinnedMesh> GI = new java.util.EnumMap<>(EquipmentSlot.class);

    /** A skin-layout (64x64) body for a slot - loose like the gi shapes without Epic Fight - baked for its skeleton. */
    private static SkinnedMesh giArmor(EquipmentSlot slot) {
        if (GI.containsKey(slot)) return GI.get(slot);
        SkinnedMesh mesh = null;
        try {
            // the player-skin layout (64x64: the left arm and leg have their own places), loose like the gi without Epic Fight
            float d = slot == EquipmentSlot.LEGS ? 0.4F : 0.62F;
            var def = new net.minecraft.client.model.geom.builders.CubeDeformation(d);
            var md = new net.minecraft.client.model.geom.builders.MeshDefinition();
            var r = md.getRoot();
            var CL = (java.util.function.Supplier<net.minecraft.client.model.geom.builders.CubeListBuilder>) net.minecraft.client.model.geom.builders.CubeListBuilder::create;
            r.addOrReplaceChild("head", CL.get().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8, def), net.minecraft.client.model.geom.PartPose.ZERO);
            r.addOrReplaceChild("hat", CL.get().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, def.extend(0.5F)), net.minecraft.client.model.geom.PartPose.ZERO);
            r.addOrReplaceChild("body", CL.get().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4, def), net.minecraft.client.model.geom.PartPose.ZERO);
            r.addOrReplaceChild("right_arm", CL.get().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4, def), net.minecraft.client.model.geom.PartPose.offset(-5, 2, 0));
            r.addOrReplaceChild("left_arm", CL.get().texOffs(32, 48).addBox(-1, -2, -2, 4, 12, 4, def), net.minecraft.client.model.geom.PartPose.offset(5, 2, 0));
            r.addOrReplaceChild("right_leg", CL.get().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4, def), net.minecraft.client.model.geom.PartPose.offset(-1.9F, 12, 0));
            r.addOrReplaceChild("left_leg", CL.get().texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4, def), net.minecraft.client.model.geom.PartPose.offset(1.9F, 12, 0));
            var root = net.minecraft.client.model.geom.builders.LayerDefinition.create(md, 64, 64).bakeRoot();
            boolean head = slot == EquipmentSlot.HEAD, chest = slot == EquipmentSlot.CHEST, legs = slot == EquipmentSlot.LEGS, feet = slot == EquipmentSlot.FEET;
            Map<String, net.minecraft.client.model.geom.ModelPart> parts = new HashMap<>();
            parts.put("head", head ? root.getChild("head") : empty());
            parts.put("hat", head ? root.getChild("hat") : empty());
            parts.put("body", chest || legs ? root.getChild("body") : empty());
            parts.put("right_arm", chest ? root.getChild("right_arm") : empty());
            parts.put("left_arm", chest ? root.getChild("left_arm") : empty());
            parts.put("right_leg", legs || feet ? root.getChild("right_leg") : empty());
            parts.put("left_leg", legs || feet ? root.getChild("left_leg") : empty());
            mesh = yesman.epicfight.api.client.model.transformer.HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(
                    new net.minecraft.client.model.HumanoidModel<AbstractClientPlayer>(new net.minecraft.client.model.geom.ModelPart(java.util.List.of(), parts)));
            if (mesh != null) mesh.initialize();
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.error("Couldn't prepare skin-layout outfits for Epic Fight", t);
        }
        GI.put(slot, mesh);
        return mesh;
    }

    /** (tests) old-layout armor pieces drawn on Epic Fight's skeleton. */
    public static int oldDrawn;
    private static final Map<EquipmentSlot, SkinnedMesh> OLD = new java.util.EnumMap<>(EquipmentSlot.class);

    /** The plain (vanilla) armor model's parts for a slot, baked for Epic Fight's skeleton (64x32 texture layout). */
    private static SkinnedMesh oldArmor(EquipmentSlot slot) {
        if (OLD.containsKey(slot)) return OLD.get(slot);
        SkinnedMesh mesh = null;
        try {
            var set = net.minecraft.client.Minecraft.getInstance().getEntityModels();
            var m = slot == EquipmentSlot.HEAD ? new net.minecraft.client.model.HumanoidModel<AbstractClientPlayer>(net.minecraft.client.model.geom.builders.LayerDefinition.create(
                    net.minecraft.client.model.HumanoidModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(CharacterArmorLayer.OLD_HEAD_INFLATE), 0F), 64, 32).bakeRoot())   // (a hat: over the hair)
                    : new net.minecraft.client.model.HumanoidModel<AbstractClientPlayer>(set.bakeLayer(slot == EquipmentSlot.LEGS
                    ? net.minecraft.client.model.geom.ModelLayers.PLAYER_INNER_ARMOR : net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR));
            m.getHead().getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
            boolean head = slot == EquipmentSlot.HEAD, chest = slot == EquipmentSlot.CHEST, legs = slot == EquipmentSlot.LEGS, feet = slot == EquipmentSlot.FEET;
            Map<String, net.minecraft.client.model.geom.ModelPart> parts = new HashMap<>();
            parts.put("head", head ? m.head : empty());
            parts.put("hat", head ? m.hat : empty());
            parts.put("body", chest || legs ? m.body : empty());
            parts.put("right_arm", chest ? m.rightArm : empty());
            parts.put("left_arm", chest ? m.leftArm : empty());
            parts.put("right_leg", legs || feet ? m.rightLeg : empty());
            parts.put("left_leg", legs || feet ? m.leftLeg : empty());
            mesh = yesman.epicfight.api.client.model.transformer.HumanoidModelBaker.VANILLA_TRANSFORMER.transformArmorModel(
                    new net.minecraft.client.model.HumanoidModel<AbstractClientPlayer>(new net.minecraft.client.model.geom.ModelPart(java.util.List.of(), parts)));
            if (mesh != null) mesh.initialize();
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.error("Couldn't prepare old-layout armor for Epic Fight", t);
        }
        OLD.put(slot, mesh);
        return mesh;
    }

    private static net.minecraft.client.model.geom.ModelPart empty() { return new net.minecraft.client.model.geom.ModelPart(java.util.List.of(), Map.of()); }

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
                    EquipmentSlot sl = EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, i);
                    if (CharacterArmorLayer.ours(kept[i]) || CharacterArmorLayer.oldForeign(player, kept[i])
                            || CharacterArmorLayer.skinForeign(player, kept[i], sl)) armor.set(i, ItemStack.EMPTY);   // (old-layout and skin-layout outfits: ours above)
                }
                original.renderLayer(player, patch, layer, pose, buffers, light, poses, bob, yRot, xRot, partial);
            } finally {
                for (int i = 0; i < armor.size(); i++) armor.set(i, kept[i]);
            }
        }
    }
}

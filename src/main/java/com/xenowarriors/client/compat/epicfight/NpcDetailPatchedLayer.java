package com.xenowarriors.client.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.npc.NpcRenderer;
import com.xenowarriors.client.render.DetailModel;
import com.xenowarriors.npc.DarkPossessedEntity;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.NpcLooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.HashMap;
import java.util.Map;

/** The townsfolk's 3D hair and outfits (and faces, glowing red eyes for the possessed) on Epic Fight's skinned body. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class NpcDetailPatchedLayer extends PatchedLayer {
    private static final Map<String, SkinnedMesh[]> BAKED = new HashMap<>();

    private static String look(LivingEntity e) {
        if (e instanceof HumanNpcEntity h) return NpcLooks.human(h.variant());
        if (e instanceof DarkPossessedEntity d) return NpcLooks.dark(d.variant());
        if (e instanceof com.xenowarriors.sr.SrMemberEntity s) return s.look();
        return null;
    }

    @Override
    protected void renderLayer(LivingEntityPatch patch, LivingEntity entity, net.minecraft.client.renderer.entity.layers.RenderLayer layer,
                               PoseStack pose, MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partial) {
        if (entity.isInvisible()) return;
        String key = look(entity);
        if (key == null) return;
        SkinnedMesh[] mesh = BAKED.computeIfAbsent(key, k -> {
            DetailModel m = DetailModel.loadWhole("/assets/" + XenoWarriors.MOD_ID + "/models/detail/npc/" + k + ".json");
            return m == null ? new SkinnedMesh[2] : DetailPatchedLayer.bakeModel(m);
        });
        if (mesh == null) return;
        boolean possessed = entity instanceof DarkPossessedEntity || (entity instanceof HumanNpcEntity h && h.possessed());
        boolean darkened = possessed && !key.startsWith("dark_");
        int overlay = OverlayTexture.NO_OVERLAY;
        if (mesh[0] != null) mesh[0].draw(pose, buffers, RenderType.entityCutoutNoCull(NpcRenderer.tex(key + (darkened ? "_dark_details" : "_details"))),
                light, 1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
        if (mesh[1] != null) {
            ResourceLocation face = NpcRenderer.tex(key + (darkened ? "_face_dark" : "_face"));
            mesh[1].draw(pose, buffers, RenderType.entityCutoutNoCull(face), light, 1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
            if (possessed) mesh[1].draw(pose, buffers, RenderType.eyes(face), light, 1F, 1F, 1F, 1F, overlay, patch.getArmature(), poses);
        }
        // SR members' wings (only while they've summoned them to fly)
        if (entity instanceof com.xenowarriors.sr.SrMemberEntity sr && sr.wings()) {
            String k = sr.key();
            if (k.equals("persephone") || k.equals("ava")) return;
            String style = switch (k) { case "senju" -> "falcon"; case "logan" -> "hawk"; case "keita" -> "bat"; case "maddie" -> "angel_maddie"; default -> "tattered"; };
            float[] c = switch (k) { case "senju" -> new float[]{0.16F, 0.13F, 0.15F}; case "logan" -> new float[]{1F, 0.86F, 0.3F};
                case "keita" -> new float[]{0.18F, 0.2F, 0.42F}; case "maddie" -> new float[]{1F, 1F, 1F};
                default -> sr.demon() ? new float[]{0.55F, 0.26F, 0.3F} : new float[]{0.3F, 0.26F, 0.34F}; };
            SkinnedMesh[] w = BAKED.computeIfAbsent("wing_" + style, kk -> {
                DetailModel m = DetailModel.loadWhole("/assets/" + XenoWarriors.MOD_ID + "/models/detail/npc/" + kk + ".json");
                return m == null ? new SkinnedMesh[2] : DetailPatchedLayer.bakeModel(m);
            });
            if (w != null && w[0] != null) w[0].draw(pose, buffers, RenderType.entityCutoutNoCull(NpcRenderer.tex("wing_" + style + "_details")),
                    light, c[0], c[1], c[2], 1F, overlay, patch.getArmature(), poses);
        }
    }
}

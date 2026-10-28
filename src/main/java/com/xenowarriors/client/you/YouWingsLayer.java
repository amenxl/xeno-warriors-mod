package com.xenowarriors.client.you;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.character.Race;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.FlightTracker;
import com.xenowarriors.client.RhalixWingsClient;
import com.xenowarriors.client.npc.NpcRenderer;
import com.xenowarriors.client.render.DetailModel;
import com.xenowarriors.you.YouSlot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Wings for You characters of a winged race: angel wings for Skylanders, seraph wings for Angels, demon wings for
 * Demons (none in human form). Drawn from the NPC wing models, posed like the show Skylanders' wings, tinted
 * towards the character's element.
 */
public class YouWingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public YouWingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float limbAmt, float pt, float age, float yaw, float pitch) {
        if (p.isInvisible() || !ClientCharacterData.winged(p)) return;
        YouSlot s = ClientCharacterData.you(p);
        if (s == null) return;
        String style = switch (s.race) { case ANGEL -> "seraph"; case DEMON -> "demon"; default -> s.element == com.xenowarriors.ka.Element.CRYSTAL ? "crystal" : "angel"; };
        DetailModel m = NpcRenderer.model("wing_" + style);
        if (m == null || m.rightWing == null || m.leftWing == null) return;
        boolean preview = ClientCharacterData.isPreviewing();
        boolean flying = FlightTracker.isFlying(p);
        boolean falling = !p.onGround() && !p.isInWater() && p.fallDistance > 1.5F;
        RhalixWingsClient.pose(m, p, preview, flying, falling, false, 0F, age);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(NpcRenderer.tex("wing_" + style + "_details")));
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0F);
        float r, g, b;
        if (s.race == Race.ANGEL) { r = 1F; g = 0.97F; b = 0.85F; }
        else if (s.race == Race.DEMON) { r = 0.45F; g = 0.12F; b = 0.14F; }
        else {                                                             // Skylander: white, warmed towards the element
            int c = s.element.color;
            r = 0.7F + 0.3F * ((c >> 16 & 255) / 255F); g = 0.7F + 0.3F * ((c >> 8 & 255) / 255F); b = 0.7F + 0.3F * ((c & 255) / 255F);
        }
        String form = YouClient.form;
        if (p == net.minecraft.client.Minecraft.getInstance().player && !form.isEmpty()) {     // Lunar: silver-blue, Solar: gold
            if (form.equals("lunar")) { r = 0.75F; g = 0.85F; b = 1F; } else { r = 1F; g = 0.85F; b = 0.45F; }
        }
        m.rightWing.render(pose, vc, light, overlay, r, g, b, 1F);
        m.leftWing.render(pose, vc, light, overlay, r, g, b, 1F);
        pose.popPose();
    }
}

package com.xenowarriors.client.foes;

import com.xenowarriors.client.npc.NpcRenderer;
import com.xenowarriors.foes.VesselEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The Cynic Dancer (a Vessel): hooded and shadow-black with glowing red eyes, passing for a C-soul - and, evolved,
 * white-skinned in its new outfit (both on the slim body: see FemaleBody).
 */
public class VesselRenderer extends NpcRenderer<VesselEntity> {
    public VesselRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true),
                VesselEntity::look, e -> false);
    }

    @Override protected boolean glow(VesselEntity e) { return e.state() != VesselEntity.FUSED; }
}

package com.xenowarriors.client.compat.epicfight;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSettings;
import com.xenowarriors.client.ClientSkillState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import yesman.epicfight.api.animation.types.EntityState;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.forgeevent.RenderEpicFightPlayerEvent;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;

/**
 * Epic Fight support (only loaded when Epic Fight is installed).
 * <ul>
 * <li>In battle mode Epic Fight draws the player with its own skinned body; we add a layer that draws Mikiko's and
 *     Rhalix's hair, clothes, wings and face on it, bent with Epic Fight's elbows and knees.</li>
 * <li>Epic Fight plays its attacks, dodges and skills (much smoother, with real elbows and knees); the rest of the
 *     time - idles, flying, surfing, skills, faces, dances - it hands the body back to our own animations.
 *     (A setting in the M menu lets Epic Fight animate everything instead.)</li>
 * <li>With DragonMineZ also installed (through the DragonMineZ x Epic Fight Bridge), DragonMineZ's model is the one
 *     drawn, so we never let Epic Fight draw our characters.</li>
 * </ul>
 */
public final class EpicFightCompat {
    private EpicFightCompat() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(EpicFightCompat::onModifyRenderers);
        MinecraftForge.EVENT_BUS.addListener(EpicFightCompat::onRenderPlayer);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void onModifyRenderers(PatchedRenderersEvent.Modify event) {
        if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer renderer) {
            renderer.addCustomLayer(new DetailPatchedLayer());
        }
    }

    private static void onRenderPlayer(RenderEpicFightPlayerEvent event) {
        if (!(event.getPlayerPatch().getOriginal() instanceof AbstractClientPlayer p)) return;
        if (ClientCharacterData.get(p) == CharacterType.NONE || !event.getShouldRender()) return;
        if (ModList.get().isLoaded("dragonminez")) {
            event.setShouldRender(false);
            return;
        }
        if (ClientSettings.efWholeBattleMode()) return;
        boolean action = Boolean.TRUE.equals(event.getPlayerPatch().getEntityState().getState(EntityState.INACTION));
        // our own skill casts always show; otherwise Epic Fight only takes over for its actions
        if (!action || ClientSkillState.currentAnim(p) != null) event.setShouldRender(false);
    }
}

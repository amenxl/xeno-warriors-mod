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
 * <li>In battle mode Mikiko and Rhalix keep their own animations: each Epic Fight attack plays the character's own
 *     attack (their punches, kicks and blade work). A setting in the M menu lets Epic Fight animate them instead.</li>
 * <li>With DragonMineZ also installed (through the DragonMineZ x Epic Fight Bridge), DragonMineZ's model is the one
 *     drawn, so we never let Epic Fight draw our characters.</li>
 * </ul>
 */
public final class EpicFightCompat {
    private EpicFightCompat() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(EpicFightCompat::onModifyRenderers);
        MinecraftForge.EVENT_BUS.addListener(EpicFightCompat::onRenderPlayer);
        MinecraftForge.EVENT_BUS.addListener(EpicFightCompat::onClientTick);
        com.xenowarriors.client.AttackHooks.externalAttack = p -> STARTED.remove(p.getId()) != null;
    }

    private static final java.util.Map<Integer, Boolean> ATTACKING = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> STARTED = new java.util.HashMap<>();

    /** Each new Epic Fight attack (its "attacking" state switching on) starts our own attack animation for it. */
    private static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            ATTACKING.clear();
            STARTED.clear();
            return;
        }
        STARTED.clear();
        for (AbstractClientPlayer p : level.players()) {
            if (ClientCharacterData.get(p) == CharacterType.NONE) continue;
            yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch;
            try {
                patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(p,
                        yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.class);
            } catch (Throwable t) {
                continue;
            }
            boolean attacking = patch != null && patch.isEpicFightMode()
                    && Boolean.TRUE.equals(patch.getEntityState().getState(EntityState.ATTACKING));
            boolean was = Boolean.TRUE.equals(ATTACKING.put(p.getId(), attacking));
            if (attacking && !was) STARTED.put(p.getId(), p.tickCount);
        }
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
        // Mikiko and Rhalix keep their own look and moves in battle mode: Epic Fight runs the combat, and every
        // attack it starts plays that character's own attack animation (now with elbows and knees).
        // (The M menu setting hands everything to Epic Fight's animations instead.)
        if (!ClientSettings.efWholeBattleMode() || ClientSkillState.currentAnim(p) != null) event.setShouldRender(false);
    }
}

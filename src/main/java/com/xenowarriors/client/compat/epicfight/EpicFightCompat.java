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
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, EpicFightCompat::onRenderPlayer);
        MinecraftForge.EVENT_BUS.addListener(EpicFightCompat::onClientTick);
        com.xenowarriors.client.AttackHooks.externalAttack = p -> STARTED.remove(p.getId()) != null;
        // one lock-on: in battle mode the lock-on key uses Epic Fight's (camera, target switching), and ours - the
        // reticle, target health, skills aiming at it - follows whatever it's locked on to, from either key
        com.xenowarriors.client.LockOn.externalToggle = () -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            var patch = mc.player == null ? null : patch(mc.player);
            if (patch == null || !patch.isEpicFightMode()) return false;
            try {
                yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance().toggleLockOn();
                return true;
            } catch (Throwable t) {
                return false;
            }
        };
        com.xenowarriors.client.LockOn.externalTarget = () -> {
            try {
                var api = yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance();
                return api != null && api.isLockingOnTarget() ? api.getFocusingEntity() : null;
            } catch (Throwable t) {
                return null;
            }
        };
        com.xenowarriors.client.AttackHooks.externalBusy = p -> {
            var patch = patch(p);
            return patch != null && patch.isEpicFightMode()
                    && Boolean.TRUE.equals(patch.getEntityState().getState(EntityState.INACTION));
        };
    }

    private static final java.util.Map<Integer, Object[]> LAST = new java.util.HashMap<>();   // id -> {animation, elapsed}
    private static final java.util.Map<Integer, Integer> STARTED = new java.util.HashMap<>();

    /** The animation Epic Fight is playing on the player's main layer right now (null if none / unknown). */
    private static Object currentAnimation(yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch) {
        try {
            yesman.epicfight.api.animation.Animator animator = patch.getAnimator();
            var player = animator.getPlayerFor(null);
            if (player == null || player.isEmpty()) return null;
            var real = player.getRealAnimation();
            return real == null ? null : real.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static float elapsed(yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch) {
        try {
            yesman.epicfight.api.animation.Animator animator = patch.getAnimator();
            var player = animator.getPlayerFor(null);
            return player == null ? 0F : player.getElapsedTime();
        } catch (Throwable t) {
            return 0F;
        }
    }

    /** One of Epic Fight's normal combo attacks (the ones Mikiko and Rhalix have their own versions of). */
    private static boolean basicAttack(Object anim) {
        return anim instanceof yesman.epicfight.api.animation.types.BasicAttackAnimation
                || anim instanceof yesman.epicfight.api.animation.types.AirSlashAnimation
                || anim instanceof yesman.epicfight.api.animation.types.DashAttackAnimation;
    }

    /** Bare hands or a sword: what Mikiko and Rhalix have their own attack animations for. */
    public static boolean ownWeapon(net.minecraft.world.entity.player.Player p) {
        var stack = p.getMainHandItem();
        return stack.isEmpty() || stack.getItem() instanceof net.minecraft.world.item.SwordItem;
    }

    private static yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch(net.minecraft.world.entity.player.Player p) {
        try {
            return yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(p,
                    yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Each new Epic Fight combo attack (with fists or a sword) starts the character's own attack animation. */
    private static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        STARTED.clear();
        if (level == null) {
            LAST.clear();
            return;
        }
        // always in battle mode (Epic Fight's mode key would switch to mining mode: it switches straight back)
        var me = net.minecraft.client.Minecraft.getInstance().player;
        if (me != null && me.tickCount % 5 == 0 && ClientSettings.efAlwaysBattle()) {
            var mine = patch(me);
            if (mine != null && !mine.isEpicFightMode()) {
                try {
                    mine.toEpicFightMode(true);
                } catch (Throwable ignored) {}
            }
        }
        for (AbstractClientPlayer p : level.players()) {
            if (ClientCharacterData.get(p) == CharacterType.NONE) continue;
            var patch = patch(p);
            if (patch == null || !patch.isEpicFightMode()) {
                LAST.remove(p.getId());
                continue;
            }
            Object anim = currentAnimation(patch);
            float time = elapsed(patch);
            Object[] last = LAST.put(p.getId(), new Object[]{anim, time});
            boolean fresh = last == null || last[0] != anim || time + 0.01F < (float) last[1];
            if (fresh && basicAttack(anim) && ownWeapon(p)) STARTED.put(p.getId(), p.tickCount);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void onModifyRenderers(PatchedRenderersEvent.Modify event) {
        if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer renderer) {
            renderer.addCustomLayer(new DetailPatchedLayer());
        }
    }

    /**
     * Who animates Mikiko and Rhalix in battle mode:
     * <ul>
     * <li>their own animations for everything they have - idles, running, flying, surfing, skills, faces, and their
     *     combo attacks with bare hands or a sword (each Epic Fight combo hit plays their own attack);</li>
     * <li>Epic Fight's animations for everything they don't - axes, spears, greatswords, daggers, bows and other
     *     weapons (stance, walk and attacks), dodges and rolls, guarding, Epic Fight skills, being knocked down.</li>
     * </ul>
     * DragonMineZ forms it draws itself (Oozaru, giant forms) stay DragonMineZ's. The M menu setting hands
     * everything to Epic Fight.
     */
    private static void onRenderPlayer(RenderEpicFightPlayerEvent event) {
        if (!(event.getPlayerPatch().getOriginal() instanceof AbstractClientPlayer p)) return;
        if (ClientCharacterData.get(p) == CharacterType.NONE) return;
        if (ModList.get().isLoaded("dragonminez")
                && (com.xenowarriors.client.compat.DmzForms.dmzDraws(p) || com.xenowarriors.client.compat.DmzForms.giantFox(p))) {
            event.setShouldRender(false);
            return;
        }
        if (!event.getPlayerPatch().isEpicFightMode()) {
            event.setShouldRender(false);
            return;
        }
        if (ClientSkillState.currentAnim(p) != null) {               // our skill casts always show
            event.setShouldRender(false);
            return;
        }
        if (ClientSettings.efWholeBattleMode()) {
            event.setShouldRender(true);
            return;
        }
        var patch = event.getPlayerPatch();
        boolean action = Boolean.TRUE.equals(patch.getEntityState().getState(EntityState.INACTION));
        boolean own = ownWeapon(p);
        boolean ef;
        if (action) {
            ef = !(own && basicAttack(currentAnimation(patch)));    // their own combos; Epic Fight's other actions
        } else {
            boolean special = com.xenowarriors.client.FlightTracker.isFlying(p) || p.isPassenger() || p.isSleeping();
            ef = !own && !special;                                  // another weapon in hand: its Epic Fight stance
        }
        event.setShouldRender(ef);
    }
}

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
        com.xenowarriors.client.AttackHooks.externalHitTicks = p -> HIT.getOrDefault(p.getId(), -1F);
        com.xenowarriors.client.AttackHooks.externalFace = EpicFightCompat::face;
        com.xenowarriors.client.AttackHooks.externalStanceFace = p -> {
            var patch = patch(p);
            if (patch == null || !patch.isEpicFightMode() || ownWeapon(p)) return null;
            // weapon at the ready: steady and focused, a blink now and then, a confident look every so often
            int t = (p.tickCount + p.getId() * 13) % 200;
            if (t < 3) return "blink";
            boolean rhalix = ClientCharacterData.get(p) == CharacterType.RHALIX;
            return t > 160 ? (rhalix ? "smug" : "cool") : "focused";
        };
        // busy only while Epic Fight is really playing an action (an attack, dodge, guard...) - not just because
        // battle mode is on, or a mode switch flickered the state
        com.xenowarriors.client.AttackHooks.externalBusy = p -> {
            var patch = patch(p);
            if (patch == null || !patch.isEpicFightMode()
                    || !Boolean.TRUE.equals(patch.getEntityState().getState(EntityState.INACTION))) return false;
            if (!(currentAnimation(patch) instanceof yesman.epicfight.api.animation.types.ActionAnimation action)) return false;
            try {
                yesman.epicfight.api.animation.Animator animator = patch.getAnimator();
                var player = animator.getPlayerFor(null);
                return player != null && !player.isEnd() && player.getElapsedTime() < action.getTotalTime();
            } catch (Throwable t) {
                return false;
            }
        };
    }

    private static final java.util.Map<Integer, Object[]> LAST = new java.util.HashMap<>();   // id -> {animation, elapsed}
    private static final java.util.Map<Integer, Integer> STARTED = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Float> HIT = new java.util.HashMap<>();
    private static int battleRequests;

    /** Ticks from the start of an Epic Fight attack to its first hit (its attack speed included), or -1. */
    private static float hitTicks(yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch, Object anim) {
        try {
            if (anim instanceof yesman.epicfight.api.animation.types.AttackAnimation attack && attack.phases.length > 0) {
                float speed = Math.max(0.1F, attack.getPlaySpeed(patch, attack));
                return attack.phases[0].contact * 20F / speed;
            }
        } catch (Throwable ignored) {}
        return -1F;
    }

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

    /**
     * Faces for Epic Fight's moves: each swing of a combo gets its own (gritted, yelling, steely), heavy and skill moves
     * a shout, guarding a focused stare, dodges a startled look, and getting knocked about a wince.
     */
    private static String face(net.minecraft.world.entity.player.Player p) {
        var patch = patch(p);
        if (patch == null || !patch.isEpicFightMode()) return null;
        Object anim = currentAnimation(patch);
        if (anim == null) return null;
        String name = anim.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        String path = String.valueOf(anim).toLowerCase(java.util.Locale.ROOT);
        boolean rhalix = ClientCharacterData.get(p) == CharacterType.RHALIX;
        if (name.contains("knockdown") || name.contains("hit") || path.contains("knockdown") || path.contains("hit_")) return "hurt";
        if (name.contains("guard") || path.contains("guard")) return path.contains("hit") ? "strained" : "focused";
        if (name.contains("dodge") || path.contains("roll") || path.contains("step") || path.contains("dodge")) return rhalix ? "cool" : "surprised";
        if (anim instanceof yesman.epicfight.api.animation.types.AttackAnimation) {
            if (!basicAttack(anim)) return "shout";                                        // skills, heavy and special attacks
            String[] swing = rhalix ? new String[]{"focused", "fierce", "smug"} : new String[]{"fierce", "shout", "concentrate"};
            float t = elapsed(patch);
            if (t < 0.12F) return "concentrate";                                           // winding up
            return swing[Math.floorMod(anim.hashCode(), swing.length)];
        }
        boolean action = Boolean.TRUE.equals(patch.getEntityState().getState(EntityState.INACTION));
        return action ? "shout" : null;
    }

    /** Mikiko swinging the Metal Pole, and this is the last hit of its combo (before the dash and air attacks). */
    private static boolean finalComboHit(net.minecraft.world.entity.player.Player p,
                                         yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch, Object anim) {
        if (ClientCharacterData.get(p) != CharacterType.MIKIKO || !p.getMainHandItem().is(com.xenowarriors.item.ModItems.METAL_POLE.get())) return false;
        try {
            var cap = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapability(p.getMainHandItem());
            var list = cap.getAutoAttackMotion(patch);
            if (list == null || list.size() < 3) return false;
            int last = list.size() - 3;                                 // combo..., dash attack, air attack
            for (int i = 0; i < list.size(); i++) if (list.get(i).get() == anim) return i == last;
        } catch (Throwable ignored) {}
        return false;
    }

    /** One of Epic Fight's normal combo attacks (the ones Mikiko and Rhalix have their own versions of). */
    private static boolean basicAttack(Object anim) {
        return anim instanceof yesman.epicfight.api.animation.types.BasicAttackAnimation
                || anim instanceof yesman.epicfight.api.animation.types.AirSlashAnimation
                || anim instanceof yesman.epicfight.api.animation.types.DashAttackAnimation;
    }

    /**
     * Uses Mikiko's and Rhalix's own moves: bare hands, the Hekublade, or anything Epic Fight has no weapon moves for.
     * Every Epic Fight weapon (its swords, longswords, tachis, axes, spears, daggers...) plays Epic Fight's own animations.
     */
    public static boolean ownWeapon(net.minecraft.world.entity.player.Player p) {
        var stack = p.getMainHandItem();
        if (stack.isEmpty() || stack.getItem() instanceof com.xenowarriors.item.HekubladeItem) return true;
        try {
            var cap = yesman.epicfight.world.capabilities.EpicFightCapabilities.getItemStackCapability(stack);
            return cap == null || cap.isEmpty() || cap == yesman.epicfight.world.capabilities.item.CapabilityItem.EMPTY;
        } catch (Throwable t) {
            return false;
        }
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
        if (me != null && me.tickCount % 20 == 0 && ClientSettings.efAlwaysBattle() && battleRequests < 6) {
            var mine = patch(me);
            if (mine != null && !mine.isEpicFightMode()) {
                battleRequests++;                       // (gives up after a few if the server won't allow it)
                try {
                    mine.toEpicFightMode(true);
                } catch (Throwable ignored) {}
            } else if (mine != null) {
                battleRequests = 0;
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
            if (fresh && basicAttack(anim) && p == net.minecraft.client.Minecraft.getInstance().player && finalComboHit(p, patch, anim)) {
                com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.PoleGustPacket());   // Metal Pole gust
            }
            if (fresh && basicAttack(anim) && ownWeapon(p)) {
                STARTED.put(p.getId(), p.tickCount);
                HIT.put(p.getId(), hitTicks(patch, anim));
            }
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

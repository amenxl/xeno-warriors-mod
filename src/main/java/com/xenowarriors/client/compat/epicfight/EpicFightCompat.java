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
        modBus.addListener(EpicFightCompat::onAddRenderers);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, EpicFightCompat::onRenderPlayer);
        MinecraftForge.EVENT_BUS.addListener(EpicFightCompat::onClientTick);
        com.xenowarriors.client.AttackHooks.externalAttack = p -> STARTED.remove(p.getId()) != null;
        com.xenowarriors.client.AttackHooks.externalCombat = p -> {
            var patch = patch(p);
            return patch != null && patch.isEpicFightMode();
        };
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
        // our own lock-on (outside battle mode) with its separate third-person camera ("always"): the camera turns to the target too
        com.xenowarriors.client.LockOn.externalCamera = (wantYaw, wantPitch, f) -> {
            try {
                var api = yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance();
                if (api == null || api.isLockingOnTarget() || !api.isTPSMode()) return;
                float yaw = api.getCameraYRot() + net.minecraft.util.Mth.wrapDegrees(wantYaw - api.getCameraYRot()) * f;
                float pitch = net.minecraft.util.Mth.clamp(api.getCameraXRot() + (wantPitch - api.getCameraXRot()) * f, -90F, 90F);
                api.setCameraRotations(pitch, yaw, true);
                lockOnCameraTurns++;
            } catch (Throwable t) {
                // (an Epic Fight without this camera API: the character still turns)
            }
        };
        // driving: its third-person camera turns on its own (the player's yaw stays put), so the vehicle follows the camera
        com.xenowarriors.vehicle.VehicleEntity.driverView = new com.xenowarriors.vehicle.VehicleEntity.DriverView() {
            @Override public float yaw(net.minecraft.world.entity.player.Player p) {
                try {
                    var api = yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance();
                    return api != null && p == net.minecraft.client.Minecraft.getInstance().player && api.isTPSMode() ? api.getCameraYRot() : Float.NaN;
                } catch (Throwable t) { return Float.NaN; }
            }
            @Override public boolean turn(net.minecraft.world.entity.player.Player p, float degrees) {
                try {
                    var api = yesman.epicfight.api.client.camera.EpicFightCameraAPI.getInstance();
                    if (api == null || p != net.minecraft.client.Minecraft.getInstance().player || !api.isTPSMode()) return false;
                    api.setCameraRotations(api.getCameraXRot(), api.getCameraYRot() + degrees, true);
                    return true;
                } catch (Throwable t) { return false; }
            }
        };
        com.xenowarriors.client.AttackHooks.externalHitTicks = p -> HIT.getOrDefault(p.getId(), -1F);
        // Epic Fight draws its mobs from inside RenderLivingEvent.Pre and cancels it: our whole-body poses go on first
        com.xenowarriors.client.PoseGuard.otherModDraws = EpicFightCompat::drawsMob;
        // its third-person camera skips vanilla's camera setup (and our cutscene / vehicle camera at its end): put ours
        // on once it has built its own
        try {
            yesman.epicfight.api.client.event.EpicFightClientHooks.Camera.BUILD_TRANSFORM_POST.registerEvent(e -> {
                net.minecraft.client.Camera cam = e.getCamera();
                if (cam != null && cam.getEntity() != null && (Object) cam instanceof com.xenowarriors.client.XwCamera ours) {
                    ours.xenowarriors$applyOurs(cam.getEntity(), cam.isDetached(), e.getPartialTick());
                    cameraPosts++;
                }
            }, "xenowarriors", -97_311);    // (its hooks keep one listener per priority number: an odd, low one - last, and nobody else's)
        } catch (Throwable t) {
            com.xenowarriors.XenoWarriors.LOGGER.warn("Epic Fight camera hook unavailable: {}", t.toString());
        }
        // Mikiko and Rhalix never take Epic Fight's fists-up guard: they show their own fighting stance instead
        com.xenowarriors.client.AttackHooks.externalGuard = p -> ownStance(p) && ownWeapon(p) && guarding(p);
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

    /** (tests) times our camera was put on after Epic Fight built its own. */
    public static int cameraPosts;
    /** (tests) frames our lock-on turned Epic Fight's own third-person camera. */
    public static int lockOnCameraTurns;

    /**
     * Something Epic Fight draws itself this frame (from inside RenderLivingEvent.Pre, then cancelling it). A player:
     * whether it drew them last time (its RenderEpicFightPlayerEvent, which it only asks once it's drawing, after our
     * early transforms went on).
     */
    private static boolean drawsMob(net.minecraft.world.entity.LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.player.Player) {
            if (!Boolean.TRUE.equals(EF_DREW.get(e.getId()))) return false;
            try {
                var engine = yesman.epicfight.client.ClientEngine.getInstance().renderEngine;
                return engine != null && engine.hasRendererFor(e);
            } catch (Throwable t) {
                return false;
            }
        }
        try {
            var engine = yesman.epicfight.client.ClientEngine.getInstance().renderEngine;
            if (engine == null || !engine.hasRendererFor(e)) return false;
            var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(e,
                    yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch.class);
            return patch != null && patch.overrideRender();
        } catch (Throwable t) {
            return false;
        }
    }

    private static final java.util.Map<Integer, Object[]> LAST = com.xenowarriors.client.ClientReset.perLife(new java.util.HashMap<>());   // id -> {animation, elapsed}
    private static final java.util.Map<Integer, Integer> STARTED = com.xenowarriors.client.ClientReset.perLife(new java.util.HashMap<>());
    private static final java.util.Map<Integer, Float> HIT = com.xenowarriors.client.ClientReset.perLife(new java.util.HashMap<>());
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
        return lastOfCombo(p, patch, anim);
    }

    /** This attack is the last hit of the held weapon's Epic Fight combo (before its dash and air attacks). */
    private static boolean lastOfCombo(net.minecraft.world.entity.player.Player p,
                                       yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch, Object anim) {
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
            if (fresh && basicAttack(anim) && p == net.minecraft.client.Minecraft.getInstance().player
                    && com.xenowarriors.client.mikiko.RopeBladeClient.eligible(p) && lastOfCombo(p, patch, anim)) {
                com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.RopeLashPacket());    // Season 3 rope lash
            }
            if (fresh && basicAttack(anim) && ownWeapon(p)) {
                STARTED.put(p.getId(), p.tickCount);
                HIT.put(p.getId(), hitTicks(patch, anim));
            }
        }
    }

    /** The townsfolk and the possessed on Epic Fight's humanoid body, with their hair, outfits and faces. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void onAddRenderers(PatchedRenderersEvent.Add event) {
        for (EntityType<?> t : java.util.List.of(com.xenowarriors.npc.ModNpcs.HUMAN.get(), com.xenowarriors.npc.ModNpcs.DARK_POSSESSED.get(),
                com.xenowarriors.sr.SrEntities.SR_MEMBER.get())) {
            event.addPatchedEntityRenderer(t, type -> {
                var r = new yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer(
                        yesman.epicfight.api.client.model.Meshes.BIPED_OUTLAYER, event.getContext(), type);
                r.addCustomLayer(new NpcDetailPatchedLayer());
                return r;
            });
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
    /** Player id -> whether Epic Fight drew them last frame (see drawsMob). */
    private static final java.util.Map<Integer, Boolean> EF_DREW = com.xenowarriors.client.ClientReset.track(new java.util.HashMap<>());

    private static void onRenderPlayer(RenderEpicFightPlayerEvent event) {
        decideRender(event);
        var p = event.getPlayerPatch().getOriginal();
        if (p != null) {
            if (EF_DREW.size() > 256) EF_DREW.clear();
            EF_DREW.put(p.getId(), event.getShouldRender());
        }
    }

    private static void decideRender(RenderEpicFightPlayerEvent event) {
        if (!(event.getPlayerPatch().getOriginal() instanceof AbstractClientPlayer p)) return;
        if (ClientCharacterData.get(p) == CharacterType.NONE) return;
        if (ClientCharacterData.isPreviewing() && com.xenowarriors.client.FirstPersonHead.inGui) {   // character previews: never the player's live animation
            event.setShouldRender(false);
            return;
        }
        if (ModList.get().isLoaded("dragonminez")
                && (com.xenowarriors.client.compat.DmzForms.dmzDraws(p) || com.xenowarriors.client.compat.DmzForms.giantFox(p))) {
            event.setShouldRender(false);
            return;
        }
        if (!event.getPlayerPatch().isEpicFightMode()) {
            event.setShouldRender(false);
            return;
        }
        if (ClientSkillState.currentAnim(p) != null || com.xenowarriors.client.EntityPoses.posed(p)          // our skill casts and poses always show
                || p.getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity) {                       // (and our vehicle seats / riding on top / hanging on)
            event.setShouldRender(false);
            return;
        }
        if (ClientSettings.efWholeBattleMode()) {
            // (even with everything handed to Epic Fight: Mikiko and Rhalix never stand in its fists-up idle or guard)
            Object anim = currentAnimation(event.getPlayerPatch());
            String path = anim == null ? "" : String.valueOf(anim).toLowerCase(java.util.Locale.ROOT);
            event.setShouldRender(!(ownStance(p) && ownWeapon(p) && (guarding(p) || path.contains("/idle"))));
            return;
        }
        event.setShouldRender(efDraws(p, event.getPlayerPatch()));
    }

    /** In battle mode (with the whole-battle-mode setting off): does Epic Fight draw this player right now? */
    private static boolean efDraws(AbstractClientPlayer p, yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch<?> patch) {
        boolean action = Boolean.TRUE.equals(patch.getEntityState().getState(EntityState.INACTION));
        boolean own = ownWeapon(p);
        Object anim = currentAnimation(patch);
        if (own && ownStance(p) && (guarding(p) || !efOnly(anim))) return false;   // no fists-up pose of Epic Fight's: ours
        if (action) return !(own && basicAttack(anim));    // their own combos; Epic Fight's other actions
        boolean special = com.xenowarriors.client.FlightTracker.isFlying(p) || p.isPassenger() || p.isSleeping();
        return !own && !special && !com.xenowarriors.client.DiscTracker.dancing(p);   // another weapon: its stance (unless dancing)
    }

    /** (tests) what's deciding how this player is drawn. */
    public static String debugDraw(AbstractClientPlayer p) {
        var patch = patch(p);
        if (patch == null) return "no patch";
        Object anim = currentAnimation(patch);
        return "battle=" + patch.isEpicFightMode() + " action=" + Boolean.TRUE.equals(patch.getEntityState().getState(EntityState.INACTION))
                + " anim=" + anim + " efDraws=" + efDraws(p, patch) + " guard=" + guarding(p);
    }

    /** Characters with their own fighting stance (Mikiko and Rhalix): Epic Fight's bare-handed poses aren't used on them. */
    private static boolean ownStance(net.minecraft.world.entity.player.Player p) {
        CharacterType t = ClientCharacterData.get(p);
        return t == CharacterType.MIKIKO || t == CharacterType.RHALIX;
    }

    /**
     * What only Epic Fight can show for Mikiko and Rhalix with bare hands or the Hekublade: dodges and rolls, being hit
     * or knocked down, and its skills / special attacks. Anything else (its combat idle with the fists up, a guard,
     * a transition between them) is drawn with their own animations.
     */
    private static boolean efOnly(Object anim) {
        if (anim == null) return false;
        String path = (anim.getClass().getSimpleName() + " " + anim).toLowerCase(java.util.Locale.ROOT);
        if (path.contains("guard")) return false;
        if (path.contains("dodge") || path.contains("roll") || path.contains("step") || path.contains("knockdown")
                || path.contains("hit") || path.contains("stun") || path.contains("fall") || path.contains("death")) return true;
        return anim instanceof yesman.epicfight.api.animation.types.AttackAnimation;
    }

    /** Epic Fight's guard (or another fists-up stance) is playing. */
    public static boolean guarding(net.minecraft.world.entity.player.Player p) {
        var patch = patch(p);
        if (patch == null || !patch.isEpicFightMode()) return false;
        Object anim = currentAnimation(patch);
        if (anim == null) return false;
        String path = (anim.getClass().getSimpleName() + " " + anim).toLowerCase(java.util.Locale.ROOT);
        return path.contains("guard") && !path.contains("hit") && !path.contains("break");
    }
}

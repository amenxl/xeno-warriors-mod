package com.xenowarriors.client.mikiko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.FlightTracker;
import com.xenowarriors.item.ModItems;
import com.xenowarriors.mikiko.Hekublade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * All of Mikiko's body animations, worked out on each client for every Mikiko player in view:
 *  - fighting: flashy sword slashes (any sword or the Hekublade); bare-handed 6-hit combos -
 *    2 punches, a kick, then 3 slashes with the Hekublade summoned for the moment - in 5 random
 *    variants; flying versions of everything
 *  - idle: looks around at the scenery, and after a while sits on the floor with his legs out, bored, and
 *    spins a little fire in his palm until it goes out
 *  - poisoned and standing still: down on one knee, hand on the ground, trying to get up
 *  - double jump: press jump in mid-air for a wind-powered front flip
 *  - flying: clueless at first - flailing arms, paddling legs, wobbling all over - and the more he flies
 *    (total, saved) the steadier he gets, until he hovers loosely and speeds along in a superhero lean
 *  - eating: holds the food up to his mouth with both hands and chews
 *  - holding the Hekublade while idle: flips it up spinning and catches it, and now and then slings it on
 *    his back, then later draws it again
 *  - holding a normal sword while idle: now and then summons the Hekublade into his other hand and
 *    crosses the two blades in an X
 *  - landing from a big fall: drops into a low three-point crouch - one knee down, a hand planted on the
 *    ground, the other arm out - then stands back up
 *  - walking: after 15 seconds of walking he relaxes into a stroll with his hands behind his head
 *  - running: leans into the sprint, fists pumping hard, long strides
 *  - sleeping: curled on his side, head sunk in the pillow, one arm stretched out, little z's floating up
 *  - "mall" on a nearby jukebox while he stands still: vibes to it - nodding on the beat, bouncing,
 *    swaying - and after a while sits down cross-legged, still nodding along
 * Attack animations only play on a fully charged swing (after the attack cooldown); spam-clicking
 * just does the normal weak swing and doesn't move the combo along.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MikikoAnimations {
    private static final Move[][] COMBOS = {
            {Move.JAB_R, Move.JAB_L, Move.KICK_FRONT, Move.BLADE_H, Move.BLADE_UP, Move.BLADE_DOWN},
            {Move.HOOK_R, Move.UPPERCUT_L, Move.KICK_ROUNDHOUSE, Move.BLADE_DOWN, Move.BLADE_H, Move.BLADE_SPIN},
            {Move.JAB_L, Move.HOOK_R, Move.KICK_SPIN, Move.BLADE_UP, Move.BLADE_THRUST, Move.BLADE_DOWN},
            {Move.UPPERCUT_L, Move.JAB_R, Move.KNEE_FLYING, Move.BLADE_SPIN, Move.BLADE_UP, Move.BLADE_THRUST},
            {Move.JAB_R, Move.UPPERCUT_L, Move.KICK_SIDE, Move.BLADE_H, Move.BLADE_SPIN, Move.BLADE_DOWN},
    };
    private static final Move[] SWORD = {Move.BLADE_H, Move.BLADE_DOWN, Move.BLADE_UP, Move.BLADE_THRUST};
    private static final int COMBO_TIMEOUT = 30;
    private static final int BLADE_LINGER = 14;
    private static final float STRIKE = 0.36F;
    // idle timing (ticks standing still)
    private static final int LOOK_AROUND_AFTER = 80;
    private static final int SIT_AFTER = 600;
    private static final int FIRE_CYCLE = 1100;     // the fire trick only now and then (~55 s)
    private static final int JUMP_DURATION = 12;
    private static final int STROLL_AFTER = 15 * 20;
    private static final int VIBE_SIT_AFTER = 20 * 20;
    private static final float BEAT = 11.4F;                 // ticks per beat (~105 BPM)
    private static final java.util.List<net.minecraft.client.resources.sounds.SoundInstance> MALL = new java.util.ArrayList<>();
    private static final java.util.List<net.minecraft.client.resources.sounds.SoundInstance> CAT = new java.util.ArrayList<>();
    /** GeckoLib idle animations he cycles through while standing around (replaces the old look-around). */
    private static final String[] IDLES = {"idle_stretch", "idle_scratch", "idle_kick_dirt"};
    private static final int IDLE_CYCLE = 240;
    private static boolean lastJumpDown;

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Set<Integer> POISONED = new HashSet<>();
    private static final Set<Integer> TRANSFORMED = new HashSet<>();
    private static final Random RANDOM = new Random();

    private MikikoAnimations() {}

    static final class State {
        Move move;
        int moveStart;
        boolean moveBlade;          // is the summoned blade used for this move
        int comboStep, variant = -1, lastAttack = -1000;
        Move lastSword;
        int bladeUntil = -1;        // summoned blade stays out until this tick
        boolean bladeShown;
        boolean lastSwinging;
        int lastSwingTime;
        int still;
        float sit, sitO, look, lookO, poison, poisonO, fire, fireO;
        float fly, flyO, fast, fastO, roll, rollO;
        float stroll, strollO, run, runO;
        int walkTicks, stopTicks;
        String discAnim;
        float discW, discWO;
        int discStart;
        float groove, grooveO, alert, alertO, hype, hypeO;
        int grooveStart, alertStart, hypeStart;
        float vibe, vibeO, robot, robotO, pet, petO, tired, tiredO, swordTired, swordTiredO;
        int absorbStart = -1000, absorbUntil = -1000;
        boolean offhandShown;
        int vibeTicks;
        int jumpStart = -100;
        boolean doubleJumped;
        int airTicks;
        double peakY = Double.NaN;
        int landStart = -100;
        float strengthLastTick = 1F;   // your own attack charge as of last tick
        int lastSwingStart = -1000;    // other players: when they last swung
    }

    public static void setPoisoned(int entityId, boolean poisoned) {
        if (poisoned) POISONED.add(entityId);
        else POISONED.remove(entityId);
    }

    private static boolean isMikiko(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == CharacterType.MIKIKO;
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            POISONED.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!isMikiko(p)) {
                STATES.remove(p.getId());
                continue;
            }
            tick(mc.level, p, STATES.computeIfAbsent(p.getId(), k -> new State()));
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        MikikoEffects.tickSummons(mc.level);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (ClientSkillState.netsuActive(p.getId())) MikikoEffects.netsuAura(mc.level, p);
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            if (isMikiko(p) && p.isSleeping() && p.tickCount % 40 == 0) MikikoEffects.snore(p);
        }
    }

    private static void tick(ClientLevel level, AbstractClientPlayer p, State s) {
        int now = p.tickCount;

        // double jump (your own player) and flight
        // big falls end in a hero landing
        boolean flyingNow = FlightTracker.isFlying(p) || p.isFallFlying() || p.getAbilities().flying;
        if (!p.onGround() && !p.isInWater() && !flyingNow) {
            s.peakY = Double.isNaN(s.peakY) ? p.getY() : Math.max(s.peakY, p.getY());
        } else if (p.onGround() && !Double.isNaN(s.peakY)) {
            if (s.peakY - p.getY() >= 6 && !p.isInWater()) {
                s.landStart = now;
                MikikoEffects.heroLanding(level, p);
            }
            s.peakY = Double.NaN;
        } else if (flyingNow || p.isInWater()) {
            s.peakY = Double.NaN;
        }
        s.airTicks = p.onGround() ? 0 : s.airTicks + 1;
        if (p.onGround() || p.isInWater() || p.onClimbable()) s.doubleJumped = false;
        if (p == Minecraft.getInstance().player) tryDoubleJump(level, (net.minecraft.client.player.LocalPlayer) p, s, now);
        // other players' flight is guessed from air time, so wait a bit longer to not mistake a big jump for flying
        boolean flying = FlightTracker.isFlying(p) && !p.isFallFlying() && !p.onGround()
                && (p == Minecraft.getInstance().player || s.airTicks > 28);
        double speed = Math.sqrt(Math.pow(p.getX() - p.xo, 2) + Math.pow(p.getZ() - p.zo, 2));
        s.flyO = s.fly;
        s.fastO = s.fast;
        s.rollO = s.roll;
        s.fly = approach(s.fly, flying ? 1F : 0F, 0.12F);
        s.fast = approach(s.fast, flying ? (float) Mth.clamp((speed - 0.1) / 0.45, 0, 1) : 0F, 0.08F);
        float turn = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
        s.roll = s.roll + (Mth.clamp(turn * 2.5F, -30F, 30F) * s.fast - s.roll) * 0.2F;

        // walking / running
        double groundSpeed = Math.sqrt(Math.pow(p.getX() - p.xo, 2) + Math.pow(p.getZ() - p.zo, 2));
        boolean walking = p.onGround() && groundSpeed > 0.02 && !p.isSprinting() && !p.isCrouching()
                && !p.isPassenger() && !p.isSwimming() && !p.isUsingItem();
        if (walking) { s.walkTicks++; s.stopTicks = 0; }
        else if (++s.stopTicks > 10 || p.isSprinting() || !p.onGround()) s.walkTicks = 0;   // short pauses are fine
        boolean running = p.isSprinting() && p.onGround() && !p.isSwimming() && !p.isPassenger();
        s.strollO = s.stroll;
        s.runO = s.run;
        s.stroll = approach(s.stroll, walking && s.walkTicks >= STROLL_AFTER && s.move == null ? 1F : 0F, 0.06F);
        s.run = approach(s.run, running ? 1F : 0F, 0.15F);

        // a new swing of the main hand?
        boolean swingStart = p.swinging && p.swingingArm == InteractionHand.MAIN_HAND
                && (!s.lastSwinging || p.swingTime < s.lastSwingTime);
        s.lastSwinging = p.swinging;
        s.lastSwingTime = p.swingTime;
        if (swingStart && !mining(p) && charged(p, s, now)) startAttack(p, s, now);
        if (swingStart) s.lastSwingStart = now;
        if (p == Minecraft.getInstance().player) s.strengthLastTick = p.getAttackStrengthScale(0F);

        // strike effects
        if (s.move != null) {
            float pPrev = (now - 1 - s.moveStart) / (float) s.move.duration;
            float pNow = (now - s.moveStart) / (float) s.move.duration;
            if (pPrev < STRIKE && pNow >= STRIKE) MikikoEffects.strike(level, p, s.move, bladeKind(p, s));
            if (pNow > 1F) s.move = null;
        }

        boolean off = offhandBlade(p);
        if (off != s.offhandShown) {
            MikikoEffects.blade(level, p, off);
            s.offhandShown = off;
        }

        // summoned blade flashes in and out
        boolean show = bladeVisible(p, s, now);
        if (show != s.bladeShown) {
            MikikoEffects.blade(level, p, show);
            s.bladeShown = show;
        }

        // idle / poison
        boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4
                || Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) > 0.01
                || !p.onGround() || p.isCrouching() || p.swinging || p.hurtTime > 0 || p.isUsingItem()
                || p.isPassenger() || p.isSleeping() || p.isSwimming() || p.isFallFlying() || s.move != null
                || ClientSkillState.currentAnim(p) != null;
        s.still = moving ? 0 : s.still + 1;
        boolean poisoned = POISONED.contains(p.getId()) || (p == Minecraft.getInstance().player
                && p.hasEffect(net.minecraft.world.effect.MobEffects.POISON));
        s.sitO = s.sit;
        s.lookO = s.look;
        s.poisonO = s.poison;
        s.fireO = s.fire;
        s.poison = approach(s.poison, poisoned && s.still >= 10 ? 1F : 0F, 0.12F);
        // "It's Time to Get Groovy": breakdancing
        boolean groovy = !poisoned && s.still >= 10 && com.xenowarriors.client.DiscTracker.near(p, com.xenowarriors.client.DiscTracker.GROOVY);
        if (groovy && s.groove <= 0F) s.grooveStart = p.tickCount;
        s.grooveO = s.groove;
        s.groove = approach(s.groove, groovy ? 1F : 0F, 0.1F);
        // "SkyBound Pursuit": jumping around, hyped
        boolean hyped = !poisoned && !groovy && s.still >= 10
                && com.xenowarriors.client.DiscTracker.near(p, com.xenowarriors.client.DiscTracker.SKYBOUND);
        if (hyped && s.hype <= 0F) s.hypeStart = p.tickCount;
        s.hypeO = s.hype;
        s.hype = approach(s.hype, hyped ? 1F : 0F, 0.15F);
        groovy |= hyped;     // (counts as dancing for everything else: no sitting, idles, vibing...)
        // more songs: shadow boxing (with a rage yell), a sad slow dance, chill vibing
        String want = null;
        if (!poisoned && !groovy && s.still >= 10) {
            if (com.xenowarriors.client.DiscTracker.near(p, com.xenowarriors.client.DiscTracker.VIRTUAL_GRIND)) want = "shadow_box";
            else if (com.xenowarriors.client.DiscTracker.near(p, com.xenowarriors.client.DiscTracker.CORRUPTED_PULSE)) want = "sad_dance";
            else if (com.xenowarriors.client.DiscTracker.near(p, com.xenowarriors.client.DiscTracker.CHILL)) want = "chill_vibe";
        }
        if (want != null && !want.equals(s.discAnim)) {
            s.discAnim = want;
            s.discStart = p.tickCount;
            s.discW = 0F;
        }
        s.discWO = s.discW;
        s.discW = approach(s.discW, want != null ? 1F : 0F, 0.1F);
        if (want == null && s.discW <= 0F) s.discAnim = null;
        if ("shadow_box".equals(s.discAnim) && s.discW > 0.5F) {
            int rt = (p.tickCount - s.discStart) % 200;
            if (rt >= 150 && rt < 186 && rt % 4 == 0) {
                // yelling with rage: steam and sparks flaring off him
                level.addParticle(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2.1, p.getZ(), 0, 0, 0);
                for (int i = 0; i < 3; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, p.getX() + Math.cos(a) * 0.5, p.getY() + 0.3,
                            p.getZ() + Math.sin(a) * 0.5, 0, 0.06, 0);
                }
            }
        }
        groovy |= want != null;
        // "Mimicry Eye Catcher": on alert, guard up, looking all around
        boolean alert = !poisoned && !groovy && s.still >= 10
                && com.xenowarriors.client.DiscTracker.near(p, com.xenowarriors.client.DiscTracker.MIMICRY);
        if (alert && s.alert <= 0F) s.alertStart = p.tickCount;
        s.alertO = s.alert;
        s.alert = approach(s.alert, alert ? 1F : 0F, 0.15F);
        boolean robotDance = !poisoned && !groovy && !alert && s.still >= 10 && discNear(p, CAT);
        s.robotO = s.robot;
        s.robot = approach(s.robot, robotDance ? 1F : 0F, 0.12F);
        boolean vibing = !poisoned && !robotDance && !groovy && !alert && s.still >= 10 && mallNear(p);
        s.vibeTicks = vibing ? s.vibeTicks + 1 : 0;
        s.vibeO = s.vibe;
        s.vibe = approach(s.vibe, vibing ? 1F : 0F, 0.08F);
        boolean holdingBlade = Hekublade.isBlade(p.getMainHandItem());
        // badly hurt: hands on his knees catching his breath, or a sword slung over his shoulder
        boolean low = lowHealth(p);
        boolean anySword = p.getMainHandItem().getItem() instanceof SwordItem;
        s.tiredO = s.tired;
        s.tired = approach(s.tired, low && !anySword && s.still >= 10 && !poisoned && !p.isUsingItem() ? 1F : 0F, 0.08F);
        s.swordTiredO = s.swordTired;
        s.swordTired = approach(s.swordTired, low && anySword && s.move == null && !p.isUsingItem() && s.fly < 0.5F ? 1F : 0F, 0.1F);
        s.sit = approach(s.sit, !poisoned && !holdingBlade && !robotDance && !groovy && !alert && !low && (s.still >= SIT_AFTER || s.vibeTicks >= VIBE_SIT_AFTER) ? 1F : 0F,
                s.still == 0 ? 0.3F : 0.06F);
        s.look = 0F;   // (the old look-around idle is gone: GeckoLib idles play instead, see apply)
        boolean fire = s.sit > 0.95F && fireActive(s.still) && !vibing && !robotDance && !groovy;
        // a pet fox sitting at his side: he pets it
        net.minecraft.world.entity.animal.Fox petted = s.sit > 0.9F && !fire && !vibing ? petFox(p) : null;
        s.petO = s.pet;
        s.pet = approach(s.pet, petted != null ? 1F : 0F, 0.08F);
        if (petted != null && s.pet > 0.9F && p.tickCount % 35 == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.HEART, petted.getX(), petted.getY() + 0.8, petted.getZ(), 0, 0.02, 0);
        }
        s.fire = approach(s.fire, fire ? 1F : 0F, fire ? 0.12F : 0.2F);
        if (s.sit > 0.95F && !vibing) {
            int c = (s.still - SIT_AFTER) % FIRE_CYCLE;
            if (c >= 40 && c < 200) MikikoEffects.fireShow(level, p, c - 40);
        }
    }

    private static void tryDoubleJump(ClientLevel level, net.minecraft.client.player.LocalPlayer p, State s, int now) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.options.keyJump.isDown();
        boolean pressed = down && !lastJumpDown;
        lastJumpDown = down;
        if (!pressed || s.doubleJumped || s.airTicks < 2 || p.onGround() || p.getAbilities().flying || p.getAbilities().mayfly
                || p.isFallFlying() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()
                || com.xenowarriors.client.UltimateCutscene.active() || mc.screen != null) return;
        s.doubleJumped = true;
        float yaw = p.getYRot() * ((float) Math.PI / 180F);
        Vec3 v = p.getDeltaMovement();
        double fwd = p.input.forwardImpulse > 0 ? 0.28 : 0.08;
        // the second jump goes higher the more Speed he has: about 2 blocks at Speed 0, about 5.5 at Speed 50
        double up = 0.62 + com.xenowarriors.client.LevelHud.speed() * 0.008;
        p.setDeltaMovement(v.x * 0.6 - Mth.sin(yaw) * fwd, up, v.z * 0.6 + Mth.cos(yaw) * fwd);
        p.fallDistance = 0F;
        com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.DoubleJumpPacket());
        sound(level, p, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.35F, 1.7F);
        s.jumpStart = now;
        MikikoEffects.doubleJump(level, p);
    }

    /** Hekublade summon effect (from the server when Mikiko presses the summon key). */
    public static void summonFx(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(entityId) instanceof AbstractClientPlayer p) MikikoEffects.startSummon(p, false);
    }

    /** Another player's double jump (from the server). */
    public static void onDoubleJump(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer p)) return;
        State s = STATES.computeIfAbsent(entityId, k -> new State());
        s.jumpStart = p.tickCount;
        MikikoEffects.doubleJump(mc.level, p);
    }

    private static boolean fireActive(int still) {
        int c = (still - SIT_AFTER) % FIRE_CYCLE;
        return still >= SIT_AFTER && c >= 34 && c < 196;
    }

    private static float approach(float v, float target, float speed) {
        return v < target ? Math.min(target, v + speed) : Math.max(target, v - speed);
    }

    /** Holding attack on a block swings the arm constantly - leave that to vanilla. */
    private static boolean mining(AbstractClientPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
        return p.pick(4.5, 1F, false).getType() == HitResult.Type.BLOCK;
    }

    /** Was this swing fully charged? Exact for your own player; for others, judged from the time since their last swing. */
    private static boolean charged(AbstractClientPlayer p, State s, int now) {
        if (p == Minecraft.getInstance().player) return s.strengthLastTick >= 0.9F;
        double speed = 4.0;
        for (var mod : p.getMainHandItem().getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) {
            if (mod.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) speed += mod.getAmount();
        }
        double delay = 20.0 / Math.max(0.1, speed);
        return now - s.lastSwingStart >= delay * 0.9;
    }

    private static void startAttack(AbstractClientPlayer p, State s, int now) {
        ItemStack held = p.getMainHandItem();
        boolean flying = FlightTracker.isFlying(p) || p.isFallFlying();
        Move move;
        if (held.isEmpty()) {
            if (now - s.lastAttack > COMBO_TIMEOUT || s.comboStep >= COMBOS[0].length || s.variant < 0) {
                s.comboStep = 0;
                s.variant = RANDOM.nextInt(COMBOS.length);
            }
            move = COMBOS[s.variant][s.comboStep++];
            s.moveBlade = move.blade;
            if (move.blade) s.bladeUntil = now + move.duration + BLADE_LINGER;
        } else if (held.getItem() instanceof SwordItem) {
            if (RANDOM.nextFloat() < 0.15F) move = Move.BLADE_SPIN;
            else {
                do move = SWORD[RANDOM.nextInt(SWORD.length)]; while (move == s.lastSword);
            }
            s.lastSword = move;
            s.moveBlade = false;
        } else {
            return;     // tools etc. keep the vanilla swing
        }
        if (flying) move = move.inAir();
        s.move = move;
        s.moveStart = now;
        s.lastAttack = now;
    }

    /** 0 = fist, 1 = normal sword, 2 = Hekublade (held or summoned). */
    private static int bladeKind(AbstractClientPlayer p, State s) {
        ItemStack held = p.getMainHandItem();
        if (Hekublade.isBlade(held) || (held.isEmpty() && s.moveBlade)) return 2;
        return held.getItem() instanceof SwordItem ? 1 : 0;
    }

    private static boolean bladeVisible(AbstractClientPlayer p, State s, int now) {
        return p.getMainHandItem().isEmpty() && (now < s.bladeUntil && s.comboStep > 3 || now < s.absorbUntil);
    }

    // ---------------- crossed swords: normal sword + summoned Hekublade in the other hand ----------------
    private static final int CROSS_AFTER = 60, CROSS_CYCLE = 260, CROSS_IN = 16, CROSS_HOLD = 100, CROSS_OUT = 116;

    /** 0..1 how far into the crossed-swords pose he is (0 when not doing it). */
    public static float crossX(Player p, float partial) {
        State s = STATES.get(p.getId());
        ItemStack main = p.getMainHandItem();
        if (s == null || s.move != null || p.isSleeping() || !(main.getItem() instanceof SwordItem) || Hekublade.isBlade(main)
                || !p.getOffhandItem().isEmpty() || s.still < CROSS_AFTER || lowHealth(p)) return 0F;
        float c = (s.still - CROSS_AFTER + partial) % CROSS_CYCLE;
        if (c < CROSS_IN) return smooth(c / CROSS_IN);
        if (c < CROSS_HOLD) return 1F;
        if (c < CROSS_OUT) return 1F - smooth((c - CROSS_HOLD) / (CROSS_OUT - CROSS_HOLD));
        return 0F;
    }

    /** Is the summoned Hekublade in his off hand right now (for the crossed-swords pose)? */
    public static boolean offhandBlade(Player p) {
        State s = STATES.get(p.getId());
        if (s == null || crossX(p, 0F) <= 0F) return false;
        float c = (s.still - CROSS_AFTER) % CROSS_CYCLE;
        return c >= 4 && c < CROSS_OUT - 4;
    }

    // ---------------- Hekublade idle: toss & catch, sling it on his back, draw it again ----------------
    public static final int BI_TOSS = 0, BI_SHEATHE = 1, BI_BACK = 2, BI_DRAW = 3, BI_HOLD = 4;
    private static final int BLADE_IDLE_AFTER = 60, BACK_TIME = 20 * 20;           // on his back for 20 seconds
    private static final int BLADE_CYCLE = 136 + BACK_TIME + 16 + 28;

    /** {phase, ticks into that phase} while he's idly holding the Hekublade, or null. */
    public static float[] bladeIdle(Player p, float partial) {
        State s = STATES.get(p.getId());
        if (s == null || s.move != null || !Hekublade.isBlade(p.getMainHandItem()) || s.still < BLADE_IDLE_AFTER || p.isSleeping()
                || lowHealth(p)) return null;
        float c = (s.still - BLADE_IDLE_AFTER + partial) % BLADE_CYCLE;
        if (c < 34) return new float[]{BI_TOSS, c};
        if (c < 120) return new float[]{BI_HOLD, c - 34};
        if (c < 136) return new float[]{BI_SHEATHE, c - 120};
        if (c < 136 + BACK_TIME) return new float[]{BI_BACK, c - 136};
        if (c < 152 + BACK_TIME) return new float[]{BI_DRAW, c - 136 - BACK_TIME};
        return new float[]{BI_HOLD, c - 152 - BACK_TIME};
    }

    /** The blade is in the air or on his back right now (so the hand shouldn't draw it). */
    public static boolean bladeAwayFromHand(net.minecraft.world.entity.LivingEntity e) {
        if (!(e instanceof Player p)) return false;
        float[] bi = bladeIdle(p, 0F);
        if (bi == null) return false;
        int ph = (int) bi[0];
        return (ph == BI_TOSS && bi[1] >= 6 && bi[1] < 30) || ph == BI_BACK || (ph == BI_SHEATHE && bi[1] >= 8) || (ph == BI_DRAW && bi[1] < 8);
    }

    private static void bladeIdlePose(HumanoidModel<?> model, int phase, float t) {
        switch (phase) {
            case BI_TOSS -> {
                // flick it up, arm up waiting, catch and bring it down
                float flick = smooth(t / 6F), down = smooth((t - 30F) / 4F);
                float x = Mth.lerp(down, Mth.lerp(flick, -0.9F, -1.9F), -0.5F);
                set(model.rightArm, x, -0.1F, 0.15F, smooth(t / 3F) * (1F - smooth((t - 32F) / 2F)) + down * 0F);
                if (t >= 6 && t < 30) model.head.xRot -= 0.45F * smooth((t - 6F) / 6F) * (1F - smooth((t - 24F) / 6F));   // watching it
            }
            // with the sword lying across his back he keeps his right hand on its hilt, at his hip
            case BI_SHEATHE -> set(model.rightArm, 0.5F, 0F, 0.55F, smooth(t / 8F));
            case BI_BACK -> set(model.rightArm, 0.5F, 0F, 0.55F, 1F);
            case BI_DRAW -> set(model.rightArm, 0.5F, 0F, 0.55F, 1F - smooth(t / 8F));
            default -> { }
        }
    }

    /**
     * Mikiko's face for what he's doing right now, or null for his normal face:
     * scared while he still can't fly, focused flying fast or sprinting, happy vibing / strolling / playing with
     * fire / hovering once he's got the hang of it, surprised watching his tossed blade, smug after the catch,
     * bored sitting around with the odd big yawn.
     */
    public static String mood(Player p) {
        State s = STATES.get(p.getId());
        if (s == null) return null;
        if (s.fly > 0.5F) {
            float clumsy = clumsiness(p);
            if (clumsy > 0.45F) {
                // having a really hard time: scared, then full panic (tears!), then dizzy from tumbling
                int beat = (p.tickCount / 20) % 5;
                return beat < 2 ? "scared" : beat < 4 ? "panic" : "dizzy";
            }
            if (clumsy > 0.15F) return "focused";       // concentrating hard, getting there
            // a natural now: his mood drifts around while he flies instead of grinning the whole time
            String[] moods = s.fast > 0.5F ? new String[]{"grin", "excited", "cool", "happy", "focused"}
                    : new String[]{"happy", "calm", "cool", "glance_l", "glance_r", "hum", "smug"};
            int seg = (p.tickCount + p.getId() * 41) / 110;
            int pick = Math.floorMod(seg * 2654435761L >>> 7, moods.length);
            return moods[(int) pick];
        }
        float[] bi = bladeIdle(p, 0F);
        if (bi != null && (int) bi[0] == BI_TOSS && bi[1] >= 6) return bi[1] < 30 ? "surprised" : "smug";
        if (bi != null && (int) bi[0] == BI_HOLD && bi[1] < 25 && (s.still - BLADE_IDLE_AFTER) % BLADE_CYCLE < 80) return "smug";
        if (s.hype > 0.5F) {
            String[] h = {"headbang", "excited", "sing", "grin", "headbang", "tongue"};
            return h[((p.tickCount - s.hypeStart) / 20) % h.length];
        }
        if (s.discAnim != null && s.discW > 0.5F) {
            switch (s.discAnim) {
                case "shadow_box" -> {
                    int rt = (p.tickCount - s.discStart) % 200;
                    if (rt >= 146 && rt < 190) return "rage";             // yelling with rage
                    String[] b = {"fierce", "concentrate", "focused", "fierce", "grin"};
                    return b[(rt / 28) % b.length];
                }
                case "sad_dance" -> {
                    int st = (p.tickCount - s.discStart) % 160;
                    if (st >= 50 && st < 100) return "crying";           // hand on his heart
                    return (st / 40) % 2 == 0 ? "sad" : "melancholy";
                }
                case "chill_vibe" -> {
                    String[] c = {"calm", "hum", "whistle", "happy", "sing", "calm"};
                    return c[(p.tickCount / 50) % c.length];
                }
                default -> { }
            }
        }
        if (s.groove > 0.5F) {
            // faces follow the breakdance: hyped toprock, concentrating on footwork, dizzy from the headspin, cool freeze
            int t = (p.tickCount - s.grooveStart) % 320;
            if (t < 80) {
                String[] g = {"wink", "grin", "tongue", "excited"};
                return g[(t / 20) % g.length];
            }
            if (t < 100) return "excited";
            if (t < 180) return (t / 25) % 2 == 0 ? "concentrate" : "cool";
            if (t < 240) return (t / 20) % 2 == 0 ? "grin" : "headbang";
            if (t < 290) return "dizzy";
            if (t < 306) return "cool";
            return "wink";
        }
        if (s.alert > 0.5F) {
            // eyes darting with each look around
            float t = (p.tickCount - s.alertStart) % 120;
            if (t < 15) return "focused";
            if (t < 32) return "glance_r";
            if (t < 52) return "glance_l";
            if (t < 68) return "look_up";
            if (t < 84) return "glance_r";
            if (t < 100) return "surprised";
            return "glance_l";
        }
        if (s.robot > 0.5F) {
            String[] r = {"robot", "robot_l", "robot", "robot_r"};
            return r[(p.tickCount / 10) % r.length];          // screen eyes flicking on the beat
        }
        if (s.pet > 0.5F) return "happy";
        if (p.tickCount - s.absorbStart < 30) return "smug";
        if (s.vibe > 0.5F) {
            String[] v = {"happy", "sing", "hum", "happy", "whistle"};
            return v[(p.tickCount / 45) % v.length];
        }
        if (s.run > 0.5F) return "focused";
        if (s.fire > 0.5F) return "happy";
        if (s.sit > 0.9F) {
            if ((s.still % 300) >= 100 && (s.still % 300) < 135) return "yawn";
            long time = p.level().getDayTime() % 24000L;
            return time > 13000 && time < 23000 ? "sleepy" : "bored";   // nodding off at night
        }
        if (s.stroll > 0.5F) return "happy";
        return null;
    }

    /** Is he in the middle of an attack move? (for his fierce face) */
    /** Busy with his own air moves (double-jump flip, hero landing, an attack, flying) - skip the normal jump pose. */
    public static boolean busyInAir(Player p, float age) {
        State s = STATES.get(p.getId());
        if (s == null) return false;
        float jump = (age - s.jumpStart) / JUMP_DURATION;
        float land = age - s.landStart;
        return s.move != null || (jump >= 0F && jump <= 1F) || (land >= 0F && land <= LAND_DURATION) || s.fly > 0.2F;
    }

    public static boolean attacking(Player p) {
        State s = STATES.get(p.getId());
        return s != null && s.move != null;
    }

    /** Whether to draw the summoned Hekublade in his empty hand (combo slashes). */
    public static boolean showSummonedBlade(AbstractClientPlayer p) {
        State s = STATES.get(p.getId());
        return s != null && s.bladeShown && p.getMainHandItem().isEmpty();
    }

    public static ItemStack bladeStack() {
        return new ItemStack(ModItems.HEKUBLADE.get());
    }

    // ------------------------------------------------------------------ limb poses

    /** Called from the player model after the normal pose is set. */
    public static void apply(HumanoidModel<?> model, Player player, float ageInTicks) {
        if (ageInTicks == 0F || !isMikiko(player)) return;     // 0 = first-person arm render
        State s = STATES.get(player.getId());
        if (s == null) return;
        float partial = Mth.clamp(ageInTicks - player.tickCount, 0F, 1F);
        if (player.isSleeping()) {
            sleepPose(model, ageInTicks);
            return;
        }

        float sit = Mth.lerp(partial, s.sitO, s.sit);
        float look = Mth.lerp(partial, s.lookO, s.look);
        float poison = Mth.lerp(partial, s.poisonO, s.poison);
        float fire = Mth.lerp(partial, s.fireO, s.fire);

        // standing around: now and then a GeckoLib idle (a big stretch, scratching his head, kicking the dirt)
        float robot = Mth.lerp(partial, s.robotO, s.robot);
        if (s.still >= LOOK_AROUND_AFTER && sit < 0.5F && poison <= 0F && s.vibe <= 0F && robot <= 0F && s.move == null
                && s.tired <= 0F && s.swordTired <= 0F && s.groove <= 0F && s.alert <= 0F
                && !Hekublade.isBlade(player.getMainHandItem()) && crossX(player, partial) <= 0F && !player.isUsingItem()) {
            int since = s.still - LOOK_AROUND_AFTER;
            String idle = IDLES[Math.floorMod(since / IDLE_CYCLE + player.getId(), IDLES.length)];
            float t = since % IDLE_CYCLE + partial, len = GeoAnims.length(idle);
            if (t <= len) GeoAnims.apply(model, idle, t, false, GeoAnims.envelope(t, len, 6F) * (1F - sit * 2F));
        }
        float stroll = Mth.lerp(partial, s.strollO, s.stroll);
        if (stroll > 0F) strollPose(model, stroll, player, partial, ageInTicks);
        float run = Mth.lerp(partial, s.runO, s.run);
        if (run > 0F) runPose(model, run, player, partial);
        float fly = Mth.lerp(partial, s.flyO, s.fly);
        if (fly > 0F) flyPose(model, fly, Mth.lerp(partial, s.fastO, s.fast), ageInTicks, clumsiness(player));
        float jump = (ageInTicks - s.jumpStart) / JUMP_DURATION;
        if (jump >= 0F && jump <= 1F) jumpPose(model, jump);
        float land = ageInTicks - s.landStart;
        if (land >= 0F && land <= LAND_DURATION) landPose(model, landWeight(land));
        if (sit > 0F) sitPose(model, sit, fire, ageInTicks);
        if (fire > 0F && sit > 0.5F) {
            // the toss at the end of the fire trick: arm thrown up, watching the fireball go
            float ft = (s.still - SIT_AFTER) % FIRE_CYCLE - 40 + partial;
            float toss = smooth((ft - 118F) / 6F) * (1F - smooth((ft - 146F) / 8F));
            float gather = smooth(ft / 4F) * (1F - smooth((ft - 20F) / 6F));
            set(model.rightArm, -2.4F, 0.2F, -0.1F, toss * fire);
            model.head.xRot = Mth.lerp(toss * fire, model.head.xRot, -0.45F);
            // snapping his fingers to call it: the other hand comes up too
            set(model.leftArm, -1.0F, -0.5F, 0.1F, gather * fire);
        }
        float pet = Mth.lerp(partial, s.petO, s.pet);
        if (pet > 0F) {
            // reaching down to his side, stroking the fox, looking over at it
            float stroke = Mth.sin(ageInTicks * 0.2F);
            set(model.rightArm, -0.35F + stroke * 0.22F, 0.1F, 0.95F + stroke * 0.06F, pet);
            model.head.yRot = Mth.lerp(pet, model.head.yRot, 0.65F);
            model.head.xRot = Mth.lerp(pet, model.head.xRot, 0.35F);
            model.head.zRot += Mth.sin(ageInTicks * 0.05F) * 0.06F * pet;
        }
        float vibe = Mth.lerp(partial, s.vibeO, s.vibe);
        if (vibe > 0F) vibePose(model, vibe, sit, ageInTicks);
        if (poison > 0F) poisonPose(model, poison, s.still + partial, ageInTicks);
        // "cat" on a jukebox: the robot
        if (robot > 0F) GeoAnims.apply(model, "robot_dance", ageInTicks, true, robot);
        float groove = Mth.lerp(partial, s.grooveO, s.groove);
        if (groove > 0F) GeoAnims.apply(model, "breakdance", ageInTicks - s.grooveStart, true, groove);
        float discW = Mth.lerp(partial, s.discWO, s.discW);
        if (s.discAnim != null && discW > 0F && s.move == null) GeoAnims.apply(model, s.discAnim, ageInTicks - s.discStart, true, discW);
        float hypeW = Mth.lerp(partial, s.hypeO, s.hype);
        if (hypeW > 0F) GeoAnims.apply(model, "hype", ageInTicks - s.hypeStart, true, hypeW);
        float alertW = Mth.lerp(partial, s.alertO, s.alert);
        if (alertW > 0F && s.move == null) GeoAnims.apply(model, "alert", ageInTicks - s.alertStart, true, alertW);
        float x = crossX(player, partial);
        if (x > 0F && s.move == null) {
            // both arms forward, wrists crossed, so the two blades make an X
            float breathe = Mth.sin(ageInTicks * 0.1F) * 0.03F;
            set(model.rightArm, -1.5F + breathe, 0.55F, 0.1F, x);
            set(model.leftArm, -1.5F - breathe, -0.55F, -0.1F, x);
        }
        float[] bi = bladeIdle(player, partial);
        if (bi != null && s.move == null) bladeIdlePose(model, (int) bi[0], bi[1]);
        if (s.move != null) {
            // in fast flight his whole body is tipped forward; the attack poses are made for standing, so turn the arms
            // by that lean too (otherwise his arm swings back toward his feet)
            float flyLean = 65F * Mth.lerp(partial, s.fastO, s.fast) * fly * 0.35F;
            movePose(model, s.move, (ageInTicks - s.moveStart) / s.move.duration, fly > 0.05F ? flyLean : -1F);
        }
        float tired = Mth.lerp(partial, s.tiredO, s.tired);
        if (tired > 0F) tiredPose(model, tired, ageInTicks);
        float swordTired = Mth.lerp(partial, s.swordTiredO, s.swordTired);
        if (swordTired > 0F && s.move == null) swordTiredPose(model, swordTired, ageInTicks, s.still > 0);
        // sealing a defeated mob into the Hekublade: the blade raised up
        float absorbT = ageInTicks - s.absorbStart, absorbLen = GeoAnims.length("absorb");
        if (s.move == null && absorbT >= 0F && absorbT <= absorbLen) {
            GeoAnims.apply(model, "absorb", absorbT, false, GeoAnims.envelope(absorbT, absorbLen, 3F));
        }
        if (player.isUsingItem() && player.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT) {
            eatPose(model, player.getTicksUsingItem() + partial, ageInTicks);
        }
    }

    private static void movePose(HumanoidModel<?> model, Move m, float p) {
        movePose(model, m, p, -1F);
    }

    /** @param flyLean degrees his body is leaned forward by flight (negative = not flying) */
    private static void movePose(HumanoidModel<?> model, Move m, float p, float flyLean) {
        if (p < 0F || p > 1F) return;
        float wind = smooth(p / 0.28F);
        float strike = smooth((p - 0.26F) / 0.16F);
        float amount = Math.max(wind, strike) * (1F - smooth((p - 0.72F) / 0.28F));
        if (amount <= 0F) return;
        float[] t = new float[13];
        for (int i = 0; i < 13; i++) t[i] = Mth.lerp(strike, m.wind[i], m.strike[i]);
        // punches and thrusts follow where he's looking up/down
        if (m.kind == Move.Kind.PUNCH || m.kind == Move.Kind.THRUST) {
            float pitch = model.head.xRot * strike;
            if (m == Move.JAB_L || m == Move.AIR_PUNCH_L || m == Move.UPPERCUT_L) t[3] += pitch;
            else t[0] += pitch;
        }
        if (flyLean >= 0F && m != Move.AIR_FLIP_SLASH && m != Move.AIR_SPIN_KICK) {
            float lean = (flyLean + m.body(p, strike)[3]) * Mth.DEG_TO_RAD;
            t[0] = Math.max(-3.2F, t[0] - lean);
            t[3] = Math.max(-3.2F, t[3] - lean);
        }
        set(model.rightArm, t[0], t[1], t[2], amount);
        set(model.leftArm, t[3], t[4], t[5], amount);
        set(model.rightLeg, t[6], t[7], t[8], amount);
        set(model.leftLeg, t[9], t[10], t[11], amount);
        twist(model, Mth.lerp(amount, model.body.yRot, t[12]));
    }

    /** 1 = has no idea how to fly, 0 = a natural. Eases off as his total flying time grows. */
    private static float clumsiness(Player p) {
        float skill = ClientSkillState.flightSkill(p.getId());
        return 1F - skill * skill * (3F - 2F * skill);
    }

    private static void flyPose(HumanoidModel<?> model, float fly, float fast, float age, float clumsy) {
        float bob = Mth.sin(age * 0.12F);
        // hovering: arms loose and out, one knee bent, swaying
        float[] hover = {-0.15F + bob * 0.05F, 0F, 0.4F, -0.15F - bob * 0.05F, 0F, -0.4F,
                0.1F + bob * 0.06F, 0F, 0.06F, -0.45F - bob * 0.06F, 0F, -0.06F};
        // fast: superhero lean - right fist ahead, left arm swept back, legs trailing and fluttering
        float flutter = Mth.sin(age * 0.5F) * 0.1F;
        float[] dash = {-3.0F, -0.1F, 0.15F, 0.3F, 0F, -0.12F, 0.15F + flutter, 0F, 0.05F, 0.05F - flutter, 0F, -0.05F};
        float[] t = new float[12];
        for (int i = 0; i < 12; i++) t[i] = Mth.lerp(fast, hover[i], dash[i]);
        set(model.rightArm, t[0], t[1], t[2], fly);
        set(model.leftArm, t[3], t[4], t[5], fly);
        set(model.rightLeg, t[6], t[7], t[8], fly);
        set(model.leftLeg, t[9], t[10], t[11], fly);
        // keep looking ahead while the body leans forward
        model.head.xRot -= 1.0F * fast * fly * (1F - clumsy);
        if (clumsy > 0.01F) {
            // no idea what he's doing: arms windmilling, legs paddling like he's swimming, looking around in a panic
            float w = fly * clumsy;
            float mill = age * 0.55F;
            set(model.rightArm, -1.6F + Mth.sin(mill) * 1.5F, 0F, 1.0F + Mth.cos(mill) * 0.5F, w);
            set(model.leftArm, -1.6F + Mth.sin(mill + 2.6F) * 1.5F, 0F, -1.0F - Mth.cos(mill + 2.6F) * 0.5F, w);
            set(model.rightLeg, Mth.sin(age * 0.7F) * 0.9F, 0F, 0.12F, w);
            set(model.leftLeg, -Mth.sin(age * 0.7F) * 0.9F, 0F, -0.12F, w);
            model.head.xRot += Mth.sin(age * 0.21F) * 0.35F * w;
            model.head.yRot += Mth.sin(age * 0.13F) * 0.5F * w;
        }
    }

    /** Asleep on his side: knees drawn up, one arm reaching out past the pillow, head nestled in, slow breathing. */
    private static void sleepPose(HumanoidModel<?> model, float age) {
        float breathe = Mth.sin(age * 0.08F) * 0.03F;
        model.head.xRot = 0.2F;
        model.head.yRot = 0F;
        model.head.zRot = 0.3F;
        set(model.rightArm, -1.55F + breathe, 0.35F, 0.1F, 1F);       // stretched out in front
        set(model.leftArm, -0.9F - breathe, -0.25F, -0.15F, 1F);      // resting, bent
        set(model.rightLeg, -0.75F, 0.1F, 0.05F, 1F);                 // curled
        set(model.leftLeg, -0.45F, -0.1F, -0.05F, 1F);
    }

    /** Is a jukebox playing "mall" close by? */
    private static boolean mallNear(Player p) {
        var sounds = Minecraft.getInstance().getSoundManager();
        MALL.removeIf(s -> !sounds.isActive(s));
        for (var s : MALL) {
            if (p.distanceToSqr(s.getX(), s.getY(), s.getZ()) < 20 * 20) return true;
        }
        return false;
    }

    private static boolean discNear(Player p, java.util.List<net.minecraft.client.resources.sounds.SoundInstance> list) {
        var sounds = Minecraft.getInstance().getSoundManager();
        list.removeIf(s -> !sounds.isActive(s));
        for (var s : list) {
            if (p.distanceToSqr(s.getX(), s.getY(), s.getZ()) < 20 * 20) return true;
        }
        return false;
    }

    /** A defeated mob is being pulled into his Hekublade. */
    public static void onAbsorb(int playerId) {
        State s = STATES.get(playerId);
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null || !(mc.level.getEntity(playerId) instanceof Player p)) return;
        s.absorbStart = p.tickCount;
        s.absorbUntil = p.tickCount + 22;
    }

    @SubscribeEvent
    public static void onSound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        var sound = event.getSound();
        if (sound != null && sound.getLocation().getPath().equals("music_disc.mall")) MALL.add(sound);
        if (sound != null && sound.getLocation().getPath().equals("music_disc.cat")) CAT.add(sound);
    }

    /** Vibing to the song: head nods on the beat, a little shoulder bounce and sway, loose arms (taps his knees when sitting). */
    private static void vibePose(HumanoidModel<?> model, float w, float sit, float age) {
        float beat = age / BEAT * (float) Math.PI * 2F;
        float nod = Math.max(0F, Mth.sin(beat)) * 0.28F;
        model.head.xRot += nod * w;
        model.head.zRot += Mth.sin(beat * 0.5F) * 0.1F * w;
        float bob = Mth.sin(beat) * 0.12F;
        float standing = w * (1F - sit);
        set(model.rightArm, -0.35F + bob, 0.1F, 0.18F + Mth.sin(beat * 0.5F) * 0.08F, standing);
        set(model.leftArm, -0.35F - bob, -0.1F, -0.18F + Mth.sin(beat * 0.5F) * 0.08F, standing);
        set(model.rightLeg, Mth.sin(beat * 0.5F) * 0.06F, 0F, 0.05F, standing * 0.6F);
        set(model.leftLeg, -Mth.sin(beat * 0.5F) * 0.06F, 0F, -0.05F, standing * 0.6F);
        if (sit > 0F) {
            // tapping his knees along with the beat
            model.rightArm.xRot += Math.max(0F, Mth.sin(beat)) * 0.18F * sit * w;
            model.leftArm.xRot += Math.max(0F, Mth.sin(beat + (float) Math.PI)) * 0.18F * sit * w;
        }
    }

    /** Relaxed stroll: hands laced behind his head, elbows out, head bobbing along. Legs keep walking. */
    private static void strollPose(HumanoidModel<?> model, float w, Player p, float partial, float age) {
        float step = p.walkAnimation.position(partial) * 0.6662F;
        float sway = Mth.sin(step) * 0.05F;
        set(model.rightArm, -2.75F + sway, 0.35F, 0.62F, w);
        set(model.leftArm, -2.75F - sway, -0.35F, -0.62F, w);
        model.head.zRot += Mth.sin(step) * 0.05F * w;
        model.head.xRot -= 0.08F * w;     // chin up, easygoing
    }

    /** Sprint: fists pumping hard against the legs, long strides (the lean is in bodyMotion). */
    private static void runPose(HumanoidModel<?> model, float w, Player p, float partial) {
        float step = p.walkAnimation.position(partial) * 0.6662F;
        float amt = Math.min(1F, p.walkAnimation.speed(partial) * 1.2F);
        float swing = Mth.cos(step) * amt;
        // elbows bent (arms forward-ish) and pumping opposite to the legs
        set(model.rightArm, -0.55F + swing * 1.25F, 0.12F, 0.1F, w);
        set(model.leftArm, -0.55F - swing * 1.25F, -0.12F, -0.1F, w);
        set(model.rightLeg, -swing * 1.35F - 0.15F, 0F, 0.02F, w);
        set(model.leftLeg, swing * 1.35F - 0.15F, 0F, -0.02F, w);
        model.head.xRot -= 0.3F * w;      // eyes forward while leaning in
    }

    /** Both hands bring the food up to his mouth; little chewing bobs. */
    private static void eatPose(HumanoidModel<?> model, float using, float age) {
        float w = smooth(using / 4F);
        float chew = Mth.sin(age * 1.7F) * 0.07F;
        set(model.rightArm, -1.5F + chew, -0.5F, 0.05F, w);
        set(model.leftArm, -1.5F + chew, 0.5F, -0.05F, w);
        model.head.xRot = Mth.lerp(w, model.head.xRot, 0.12F + chew * 0.6F);
    }

    private static final int LAND_DURATION = 34;

    /** Slams into the crouch instantly, holds it, then rises. */
    private static float landWeight(float t) {
        return Math.min(smooth(t / 1.5F), 1F - smooth((t - 20F) / (LAND_DURATION - 20F)));
    }

    /** Three-point landing: left knee down behind, right foot planted forward, left hand flat on the ground,
     * right arm flung out to the side (the low crouch and lean are in bodyMotion). */
    private static void landPose(HumanoidModel<?> model, float w) {
        if (w <= 0F) return;
        set(model.rightLeg, -1.35F, 0.1F, 0.1F, w);
        set(model.leftLeg, 0.95F, -0.1F, -0.05F, w);
        set(model.leftArm, -0.65F, 0.25F, -0.15F, w);
        set(model.rightArm, -0.55F, 0F, 1.15F, w);
        model.head.xRot -= 0.35F * w;            // eyes up, looking ahead
    }

    private static void jumpPose(HumanoidModel<?> model, float p) {
        float w = smooth(p / 0.12F) * (1F - smooth((p - 0.75F) / 0.25F));
        // tucked tight for the flip
        set(model.rightLeg, -1.75F, 0F, 0.12F, w);
        set(model.leftLeg, -1.55F, 0F, -0.12F, w);
        set(model.rightArm, -0.9F, 0F, 0.35F, w);
        set(model.leftArm, -0.9F, 0F, -0.35F, w);
    }

    private static void sitPose(HumanoidModel<?> model, float s, float fire, float age) {
        // sitting on the floor with his legs stretched out, leaning back on his hands, bored
        set(model.rightLeg, -1.5F, 0.08F, 0.06F, s);
        set(model.leftLeg, -1.5F, -0.08F, -0.06F, s);
        set(model.rightArm, 0.55F, 0F, 0.3F, s);
        set(model.leftArm, 0.55F, 0F, -0.3F, s);
        model.head.xRot += 0.22F * s;
        model.head.zRot += 0.1F * s * Mth.sin(age * 0.03F);
        if (fire > 0F) {
            // palm up in front of him, watching the flame spin
            set(model.rightArm, -1.25F, 0.3F, -0.1F, fire);
            model.head.yRot = Mth.lerp(fire, model.head.yRot, 0.3F);
            model.head.xRot = Mth.lerp(fire, model.head.xRot, 0.5F);
        }
    }

    private static void poisonPose(HumanoidModel<?> model, float k, float still, float age) {
        float rise = riseAttempt(still);
        float down = k * (1F - rise);
        // right knee on the ground, left foot planted
        set(model.rightLeg, 1.0F, 0F, 0.1F, down);
        set(model.leftLeg, -1.25F, -0.1F, -0.1F, down);
        // right hand pushing on the ground, left hand on his knee
        set(model.rightArm, -0.35F, 0F, 0.15F, k);
        set(model.leftArm, -1.05F, 0.35F, -0.1F, k);
        model.head.xRot += 0.5F * k * (1F - rise * 0.6F) + Mth.sin(age * 0.25F) * 0.04F * k;
    }

    /** Every few seconds he tries to push himself up, then sinks back down. */
    private static float riseAttempt(float still) {
        float c = still % 80F;
        if (c < 22F) return 0.45F * smooth(c / 22F);
        if (c < 34F) return 0.45F * (1F - smooth((c - 22F) / 12F));
        return 0F;
    }

    private static float[] lookOffset(int id, float still) {
        float seg = still / 70F;
        int i = (int) seg;
        float[] a = lookTarget(id, i - 1), b = lookTarget(id, i);
        float t = smooth((seg - i) * 70F / 22F);
        return new float[]{Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1])};
    }

    private static float[] lookTarget(int id, int seg) {
        if (seg < 0) return new float[]{0, 0};
        Random r = new Random(id * 9781L + seg * 6271L);
        return new float[]{(r.nextFloat() - 0.5F) * 2.2F, (r.nextFloat() - 0.6F) * 0.5F};
    }

    /** The badly-hurt pose with a sword: drawn lying across his shoulders behind his head instead of in his hand. */
    public static boolean swordShouldered(net.minecraft.world.entity.LivingEntity e) {
        if (!(e instanceof Player p)) return false;
        State s = STATES.get(p.getId());
        return s != null && s.swordTired > 0.5F && p.getMainHandItem().getItem() instanceof SwordItem;
    }

    /**
     * Whole-body moves of the breakdance (t = 0..320 ticks), matching the "breakdance" limb animation:
     * {lift, forward, spin, lean, roll}. Toprock, drop, footwork spinning round, windmills on his back,
     * a headspin, a freeze, then flipping back up.
     */
    private static float[] breakdanceBody(float t) {
        float[] o = new float[5];
        if (t < 80) {                                   // toprock: bouncing on the beat, swaying round
            o[0] = Math.abs(Mth.sin(t * 0.157F)) * 0.06F;
            o[2] = Mth.sin(t * 0.0785F) * 18F;
        } else if (t < 100) {                           // drop down to the floor
            float d = smooth((t - 80) / 20F);
            o[0] = -0.72F * d;
            o[3] = 20F * Mth.sin(d * Mth.PI);
        } else if (t < 180) {                           // footwork, turning all the way round
            o[0] = -0.72F;
            o[2] = (t - 100) * 4.5F;
            o[3] = -8F;
        } else if (t < 240) {                           // windmills: on his back, legs spinning round
            float in = smooth((t - 180) / 6F);
            o[0] = -0.72F;
            o[3] = -85F * in;
            o[2] = (t - 180) * 24F;
            o[4] = Mth.sin((t - 180) * 0.4F) * 25F * in;
        } else if (t < 290) {                           // headspin: upside down on his head
            float flip = smooth((t - 240) / 8F);
            o[0] = Mth.lerp(flip, -0.72F, 0.02F);
            o[3] = Mth.lerp(flip, -85F, -180F);
            o[2] = (t - 240) * 28.8F;
        } else if (t < 306) {                           // freeze, balanced on one hand
            float f = smooth((t - 290) / 4F);
            o[0] = Mth.lerp(f, 0.02F, -0.1F);
            o[3] = Mth.lerp(f, -180F, -150F);
            o[4] = 25F * f;
        } else {                                        // flip back up onto his feet
            float u = smooth((t - 306) / 14F);
            o[3] = Mth.lerp(u, -150F, -360F);
            o[4] = 25F * (1F - u);
            o[0] = Mth.lerp(u, -0.1F, 0F) + Mth.sin(u * Mth.PI) * 0.35F;
        }
        return o;
    }

    /** 30% health or less. */
    public static boolean lowHealth(Player p) {
        return p.isAlive() && !p.isCreative() && p.getHealth() <= p.getMaxHealth() * 0.3F;
    }

    /** Is he worn out right now (hands on knees, or sword over his shoulder)? For his exhausted face. */
    public static boolean exhausted(Player p) {
        State s = STATES.get(p.getId());
        return s != null && (s.tired > 0.5F || s.swordTired > 0.5F);
    }

    /**
     * Badly hurt and standing still: bent over with his hands on his knees, legs apart, head up,
     * shoulders heaving as he pants.
     */
    private static void tiredPose(HumanoidModel<?> m, float w, float age) {
        float breath = Mth.sin(age * 0.32F);
        // bent forward at the hips (like a crouch, with the shoulders and head moved to match)
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0.5F + breath * 0.03F);
        m.body.y = Mth.lerp(w, m.body.y, 3.2F + breath * 0.15F);
        m.head.y = Mth.lerp(w, m.head.y, 4.2F + breath * 0.2F);
        m.rightArm.y = Mth.lerp(w, m.rightArm.y, 5.2F + breath * 0.2F);
        m.leftArm.y = Mth.lerp(w, m.leftArm.y, 5.2F + breath * 0.2F);
        m.rightLeg.z = Mth.lerp(w, m.rightLeg.z, 4.0F);
        m.leftLeg.z = Mth.lerp(w, m.leftLeg.z, 4.0F);
        m.rightLeg.y = Mth.lerp(w, m.rightLeg.y, 12.2F);
        m.leftLeg.y = Mth.lerp(w, m.leftLeg.y, 12.2F);
        // legs apart, hands braced on his knees
        set(m.rightLeg, -0.05F, 0.05F, 0.2F, w);
        set(m.leftLeg, -0.05F, -0.05F, -0.2F, w);
        set(m.rightArm, -0.32F, 0.05F, -0.12F, w);
        set(m.leftArm, -0.32F, -0.05F, 0.12F, w);
        // looking up ahead, head bobbing with each breath
        set(m.head, -0.45F + breath * 0.06F, m.head.yRot * 0.3F, 0F, w);
    }

    /**
     * Badly hurt with a sword: resting it over his right shoulder (blade hanging down behind him), the other arm
     * hanging limp, head drooping to the side.
     */
    private static void swordTiredPose(HumanoidModel<?> m, float w, float age, boolean still) {
        float breath = Mth.sin(age * 0.3F);
        // right hand reaching up and back to hold the sword lying across his shoulders behind his head
        set(m.rightArm, -3.75F + breath * 0.04F, 0F, 0.12F, w);
        if (still) {
            set(m.leftArm, 0.06F + breath * 0.03F, 0F, -0.04F, w);
            m.body.xRot = Mth.lerp(w, m.body.xRot, 0.12F);
            m.head.zRot = Mth.lerp(w, m.head.zRot, 0.22F);
            m.head.xRot = Mth.lerp(w, m.head.xRot, 0.2F + breath * 0.04F);
        }
    }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    /** Twist the torso and keep the shoulders attached (same maths as the vanilla swing). */
    private static void twist(HumanoidModel<?> model, float yRot) {
        model.body.yRot = yRot;
        model.rightArm.z = Mth.sin(yRot) * 5F;
        model.rightArm.x = -Mth.cos(yRot) * 5F;
        model.leftArm.z = -Mth.sin(yRot) * 5F;
        model.leftArm.x = Mth.cos(yRot) * 5F;
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    // ------------------------------------------------------------------ whole-body motion

    /** {lift, forward, spin, lean, roll} for this frame. */
    private static float[] bodyMotion(AbstractClientPlayer p, float partial) {
        State s = STATES.get(p.getId());
        if (s == null) return null;
        float[] out = new float[5];
        float age = p.tickCount + partial;
        if (s.move != null) {
            float pr = (age - s.moveStart) / s.move.duration;
            if (pr >= 0F && pr <= 1F) {
                float strike = smooth((pr - 0.26F) / 0.16F) * (1F - smooth((pr - 0.72F) / 0.28F));
                float[] b = s.move.body(pr, strike);
                float fade = 1F - smooth((pr - 0.8F) / 0.2F);
                for (int i = 0; i < 5; i++) out[i] += (i == 2 || (i == 3 && s.move == Move.AIR_FLIP_SLASH)) ? b[i] : b[i] * fade;
            }
        }
        float fly = Mth.lerp(partial, s.flyO, s.fly);
        if (fly > 0F) {
            float fast = Mth.lerp(partial, s.fastO, s.fast);
            float attacking = s.move != null ? 0.35F : 1F;
            float clumsy = clumsiness(p);
            out[0] += Mth.sin(age * 0.12F) * 0.05F * fly * (1F - fast);
            out[3] += 65F * fast * fly * attacking * (1F - 0.75F * clumsy);
            out[4] += Mth.lerp(partial, s.rollO, s.roll) * fly * attacking;
            // wobbling and tipping all over the place while he's still learning
            out[4] += (Mth.sin(age * 0.17F) * 22F + Mth.sin(age * 0.41F) * 8F) * clumsy * fly;
            out[3] += (Mth.sin(age * 0.11F) * 16F) * clumsy * fly;
            out[0] += Mth.sin(age * 0.23F) * 0.12F * clumsy * fly;
        }
        var skill = ClientSkillState.currentAnim(p);
        if (skill != null && skill.anim() == com.xenowarriors.network.SkillAnimPacket.KITSUNE) {
            float t = age - skill.startTick();
            float crouch = smooth(t / 3F) * (1F - smooth((t - 36F) / 8F));
            float fin = smooth((t - 6F) / 3F) * (1F - smooth((t - 36F) / 8F));
            out[0] -= 0.25F * crouch + 0.2F * fin;     // low to the ground
            out[3] += 14F * crouch + 14F * fin;        // leaning in
        }
        if (skill != null && skill.anim() == com.xenowarriors.network.SkillAnimPacket.BLAZING) {
            float t = age - skill.startTick();
            out[3] -= 8F * smooth((t - 42F) / 4F) * (1F - smooth((t - 90F) / 10F));   // braced back as he pushes
        }
        float vibe = Mth.lerp(partial, s.vibeO, s.vibe);
        if (vibe > 0F) {
            float beat = age / BEAT * (float) Math.PI * 2F;
            out[0] += Math.max(0F, Mth.sin(beat)) * 0.045F * vibe;          // bounce on the beat
            out[4] += Mth.sin(beat * 0.5F) * 4F * vibe;                      // sway side to side
        }
        float groove = Mth.lerp(partial, s.grooveO, s.groove);
        if (groove > 0F) {
            float[] b = breakdanceBody((age - s.grooveStart) % 320F);
            for (int i = 0; i < 5; i++) out[i] += b[i] * groove;
        }
        float hypeB = Mth.lerp(partial, s.hypeO, s.hype);
        if (hypeB > 0F) out[0] += com.xenowarriors.client.DiscTracker.hypeLift(age - s.hypeStart) * hypeB;
        float alertB = Mth.lerp(partial, s.alertO, s.alert);
        if (alertB > 0F) out[0] -= 0.12F * alertB;      // crouched low, ready
        float robot = Mth.lerp(partial, s.robotO, s.robot);
        if (robot > 0F) {
            // stiff little up-down pops on each beat
            out[0] += ((int) (age / 10F) % 2 == 0 ? 0.03F : -0.02F) * robot;
        }
        float run = Mth.lerp(partial, s.runO, s.run);
        if (run > 0F) {
            float step = p.walkAnimation.position(partial) * 0.6662F;
            out[3] += 15F * run;                                           // lean into the sprint
            out[0] += Math.abs(Mth.sin(step)) * 0.06F * run;                // bounce with each stride
        }
        float land = age - s.landStart;
        if (land >= 0F && land <= LAND_DURATION) {
            float w = landWeight(land);
            out[0] -= 0.52F * w;      // down low
            out[3] += 22F * w;        // leaning over his planted hand
        }
        float jump = (age - s.jumpStart) / JUMP_DURATION;
        if (jump >= 0F && jump <= 1F) {
            float x = Mth.clamp((jump - 0.05F) / 0.75F, 0F, 1F);
            out[3] += 360F * x * x * (3F - 2F * x);
        }
        float sit = Mth.lerp(partial, s.sitO, s.sit);
        out[0] -= 0.66F * sit;
        out[3] -= 10F * sit;      // leaning back on his hands
        float poison = Mth.lerp(partial, s.poisonO, s.poison);
        if (poison > 0F) {
            float rise = riseAttempt(s.still + partial);
            out[0] -= (0.42F * (1F - rise) - Mth.sin(age * 0.25F) * 0.015F) * poison;
            out[3] += 18F * poison * (1F - rise * 0.6F);
        }
        boolean any = false;
        for (float v : out) any |= Math.abs(v) > 1.0E-3F;
        return any ? out : null;
    }

    @SubscribeEvent
    public static void onRenderLevel(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) MikikoEffects.renderXl(event);
    }

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !isMikiko(p)) return;
        if (p.isSleeping()) {
            // roll from lying on his back onto his side (the pillow side), around the length of his body
            net.minecraft.core.Direction bed = p.getBedOrientation();
            if (bed == null) return;
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            TRANSFORMED.add(p.getId());
            pose.translate(0, 0.15, 0);
            pose.mulPose(Axis.of(new org.joml.Vector3f(bed.getStepX(), 0, bed.getStepZ())).rotationDegrees(75F));
            pose.translate(0, -0.15, 0);
            return;
        }
        float[] m = bodyMotion(p, event.getPartialTick());
        if (m == null) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        TRANSFORMED.add(p.getId());
        float yaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        // work in his own facing: +z forward, +x to his left
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, m[0], m[1]);
        pose.translate(0, 0.9, 0);
        pose.mulPose(Axis.YP.rotationDegrees(m[2]));
        pose.mulPose(Axis.XP.rotationDegrees(m[3]));
        pose.mulPose(Axis.ZP.rotationDegrees(m[4]));
        pose.translate(0, -0.9, 0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (TRANSFORMED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    /** World position of his right palm while sitting (for the fire trick). */
    /** Where a fox sits to be petted: right at his side. */
    static Vec3 petSpot(Player p) {
        float yaw = p.yBodyRot * ((float) Math.PI / 180F);
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return p.position().add(right.scale(0.85)).add(fwd.scale(0.15));
    }

    private static net.minecraft.world.entity.animal.Fox petFox(Player p) {
        Vec3 spot = petSpot(p);
        net.minecraft.world.entity.animal.Fox best = null;
        double bestD = 0.9 * 0.9;
        for (var f : p.level().getEntitiesOfClass(net.minecraft.world.entity.animal.Fox.class, p.getBoundingBox().inflate(2.5),
                com.xenowarriors.client.fox.FoxClientFx::isPet)) {
            double d = f.position().distanceToSqr(spot);
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
    }

    static Vec3 seatedPalm(Player p) {
        float yaw = p.yBodyRot * ((float) Math.PI / 180F);
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return p.position().add(0, 0.95, 0).add(fwd.scale(0.55)).add(right.scale(0.28));
    }

    static void sound(ClientLevel level, Player p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    static void particle(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 v) {
        level.addParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
    }
}

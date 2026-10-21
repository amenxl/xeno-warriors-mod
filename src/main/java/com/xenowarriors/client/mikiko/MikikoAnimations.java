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
 *  - idle: looks around at the scenery, and after a while sits down cross-legged, bored, and
 *    spins a little fire in his palm until it goes out
 *  - poisoned and standing still: down on one knee, hand on the ground, trying to get up
 *  - double jump: press jump in mid-air for a wind-powered front flip
 *  - flying: hovers loosely with a bob; speeds into a superhero lean, banking into turns
 *  - eating: holds the food up to his mouth with both hands and chews
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
    private static final int FIRE_CYCLE = 260;
    private static final int JUMP_DURATION = 12;
    private static final int STROLL_AFTER = 15 * 20;
    private static final int VIBE_SIT_AFTER = 20 * 20;
    private static final float BEAT = 11.4F;                 // ticks per beat (~105 BPM)
    private static final java.util.List<net.minecraft.client.resources.sounds.SoundInstance> MALL = new java.util.ArrayList<>();
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
        float vibe, vibeO;
        int vibeTicks;
        int jumpStart = -100;
        boolean doubleJumped;
        int airTicks;
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
            if (isMikiko(p) && p.isSleeping() && p.tickCount % 40 == 0) MikikoEffects.snore(p);
        }
    }

    private static void tick(ClientLevel level, AbstractClientPlayer p, State s) {
        int now = p.tickCount;

        // double jump (your own player) and flight
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
        boolean vibing = !poisoned && s.still >= 10 && mallNear(p);
        s.vibeTicks = vibing ? s.vibeTicks + 1 : 0;
        s.vibeO = s.vibe;
        s.vibe = approach(s.vibe, vibing ? 1F : 0F, 0.08F);
        s.sit = approach(s.sit, !poisoned && (s.still >= SIT_AFTER || s.vibeTicks >= VIBE_SIT_AFTER) ? 1F : 0F,
                s.still == 0 ? 0.3F : 0.06F);
        s.look = approach(s.look, !poisoned && !vibing && s.still >= LOOK_AROUND_AFTER && s.sit < 0.5F ? 1F : 0F, 0.08F);
        boolean fire = s.sit > 0.95F && fireActive(s.still) && !vibing;
        s.fire = approach(s.fire, fire ? 1F : 0F, fire ? 0.12F : 0.2F);
        if (s.sit > 0.95F && !vibing) {
            int c = (s.still - SIT_AFTER) % FIRE_CYCLE;
            if (c == 46) MikikoEffects.fireStart(level, p);
            if (c >= 46 && c < 156) MikikoEffects.fireSpin(level, p, c - 46);
            if (c == 156) MikikoEffects.fireOut(level, p);
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
        p.setDeltaMovement(v.x * 0.6 - Mth.sin(yaw) * fwd, 0.62, v.z * 0.6 + Mth.cos(yaw) * fwd);
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
        return still >= SIT_AFTER && c >= 40 && c < 160;
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
        return p.getMainHandItem().isEmpty() && now < s.bladeUntil && s.comboStep > 3;
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

        if (look > 0F) {
            float[] l = lookOffset(player.getId(), s.still + partial);
            model.head.yRot += l[0] * look;
            model.head.xRot += l[1] * look;
        }
        float stroll = Mth.lerp(partial, s.strollO, s.stroll);
        if (stroll > 0F) strollPose(model, stroll, player, partial, ageInTicks);
        float run = Mth.lerp(partial, s.runO, s.run);
        if (run > 0F) runPose(model, run, player, partial);
        float fly = Mth.lerp(partial, s.flyO, s.fly);
        if (fly > 0F) flyPose(model, fly, Mth.lerp(partial, s.fastO, s.fast), ageInTicks);
        float jump = (ageInTicks - s.jumpStart) / JUMP_DURATION;
        if (jump >= 0F && jump <= 1F) jumpPose(model, jump);
        if (sit > 0F) sitPose(model, sit, fire, ageInTicks);
        float vibe = Mth.lerp(partial, s.vibeO, s.vibe);
        if (vibe > 0F) vibePose(model, vibe, sit, ageInTicks);
        if (poison > 0F) poisonPose(model, poison, s.still + partial, ageInTicks);
        if (s.move != null) movePose(model, s.move, (ageInTicks - s.moveStart) / s.move.duration);
        if (player.isUsingItem() && player.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT) {
            eatPose(model, player.getTicksUsingItem() + partial, ageInTicks);
        }
    }

    private static void movePose(HumanoidModel<?> model, Move m, float p) {
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
        set(model.rightArm, t[0], t[1], t[2], amount);
        set(model.leftArm, t[3], t[4], t[5], amount);
        set(model.rightLeg, t[6], t[7], t[8], amount);
        set(model.leftLeg, t[9], t[10], t[11], amount);
        twist(model, Mth.lerp(amount, model.body.yRot, t[12]));
    }

    private static void flyPose(HumanoidModel<?> model, float fly, float fast, float age) {
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
        model.head.xRot -= 1.0F * fast * fly;
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

    @SubscribeEvent
    public static void onSound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        var sound = event.getSound();
        if (sound != null && sound.getLocation().getPath().equals("music_disc.mall")) MALL.add(sound);
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

    private static void jumpPose(HumanoidModel<?> model, float p) {
        float w = smooth(p / 0.12F) * (1F - smooth((p - 0.75F) / 0.25F));
        // tucked tight for the flip
        set(model.rightLeg, -1.75F, 0F, 0.12F, w);
        set(model.leftLeg, -1.55F, 0F, -0.12F, w);
        set(model.rightArm, -0.9F, 0F, 0.35F, w);
        set(model.leftArm, -0.9F, 0F, -0.35F, w);
    }

    private static void sitPose(HumanoidModel<?> model, float s, float fire, float age) {
        // cross-legged: thighs forward, crossed over each other
        set(model.rightLeg, -1.45F, -0.68F, 0.1F, s);
        set(model.leftLeg, -1.45F, 0.68F, -0.1F, s);
        // elbows resting on knees, slouched and bored
        set(model.rightArm, -0.8F, -0.3F, 0.1F, s);
        set(model.leftArm, -0.8F, 0.3F, -0.1F, s);
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
            out[0] += Mth.sin(age * 0.12F) * 0.05F * fly * (1F - fast);
            out[3] += 65F * fast * fly * attacking;
            out[4] += Mth.lerp(partial, s.rollO, s.roll) * fly * attacking;
        }
        float vibe = Mth.lerp(partial, s.vibeO, s.vibe);
        if (vibe > 0F) {
            float beat = age / BEAT * (float) Math.PI * 2F;
            out[0] += Math.max(0F, Mth.sin(beat)) * 0.045F * vibe;          // bounce on the beat
            out[4] += Mth.sin(beat * 0.5F) * 4F * vibe;                      // sway side to side
        }
        float run = Mth.lerp(partial, s.runO, s.run);
        if (run > 0F) {
            float step = p.walkAnimation.position(partial) * 0.6662F;
            out[3] += 15F * run;                                           // lean into the sprint
            out[0] += Math.abs(Mth.sin(step)) * 0.06F * run;                // bounce with each stride
        }
        float jump = (age - s.jumpStart) / JUMP_DURATION;
        if (jump >= 0F && jump <= 1F) {
            float x = Mth.clamp((jump - 0.05F) / 0.75F, 0F, 1F);
            out[3] += 360F * x * x * (3F - 2F * x);
        }
        float sit = Mth.lerp(partial, s.sitO, s.sit);
        out[0] -= 0.66F * sit;
        out[3] += 6F * sit;
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

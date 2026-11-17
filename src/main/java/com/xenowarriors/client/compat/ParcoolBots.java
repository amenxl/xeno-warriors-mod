package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.BotParkour;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A bot's parkour move (BotParkour) drawn with ParCool's own poses on the bot's body (BotAvatar, a player for drawing):
 * the move number from the server picks ParCool's animator, the matching ParCool action is marked as going on (some of
 * its poses last only while it is), and the animation is ticked here (ParCool only ticks the world's real players).
 * Reflection only: without ParCool, or after a ParCool update that renames things, the poses are skipped (logged once).
 */
public final class ParcoolBots {
    private ParcoolBots() {}

    private static boolean broken;
    private static Method animGet, setAnimator, animTick, removeAnimator, pkGet, actionGet, setDoing, shouldRemoved;
    private static java.lang.reflect.Field animatorField;
    private static Class<?> roll, slide, fastRun, cling, catLeap;
    private static Constructor<?> speedVault, climbUp, clingAnim, wallJump, rollAnim, slideAnim, fastRunAnim, dodgeAnim, catLeapAnim;
    private static Object vaultRight, vaultLeft, rollFront, dodgeLeft, dodgeRight;
    private static final Map<AbstractClientPlayer, Integer> SHOWN = new WeakHashMap<>();
    /** (tests) the ParCool poses put on bots, by animator name. */
    public static final java.util.Set<String> APPLIED = new java.util.LinkedHashSet<>();

    private static boolean ready() {
        if (broken || !ParcoolCompat.loaded()) return false;
        if (animGet != null) return true;
        try {
            Class<?> anim = Class.forName("com.alrex.parcool.common.capability.Animation");
            Class<?> pk = Class.forName("com.alrex.parcool.common.capability.Parkourability");
            Class<?> animator = Class.forName("com.alrex.parcool.client.animation.Animator");
            Class<?> action = Class.forName("com.alrex.parcool.common.action.Action");
            animGet = anim.getMethod("get", Player.class);
            setAnimator = anim.getMethod("setAnimator", animator);
            animTick = anim.getMethod("tick", AbstractClientPlayer.class, pk);
            removeAnimator = anim.getMethod("removeAnimator");
            animatorField = anim.getDeclaredField("animator");
            animatorField.setAccessible(true);
            pkGet = pk.getMethod("get", Player.class);
            actionGet = pk.getMethod("get", Class.class);
            setDoing = action.getMethod("setDoing", boolean.class);
            shouldRemoved = animator.getMethod("shouldRemoved", Player.class, pk);
            String a = "com.alrex.parcool.common.action.impl.", c = "com.alrex.parcool.client.animation.impl.";
            roll = Class.forName(a + "Roll"); slide = Class.forName(a + "Slide"); fastRun = Class.forName(a + "FastRun");
            cling = Class.forName(a + "ClingToCliff"); catLeap = Class.forName(a + "CatLeap");
            Class<?> vType = Class.forName(c + "SpeedVaultAnimator$Type");
            Class<?> rDir = Class.forName(a + "Roll$Direction");
            Class<?> dDir = Class.forName(a + "Dodge$DodgeDirection");
            vaultRight = enumOf(vType, "Right"); vaultLeft = enumOf(vType, "Left");
            rollFront = enumOf(rDir, "Front");
            dodgeLeft = enumOf(dDir, "Left"); dodgeRight = enumOf(dDir, "Right");
            speedVault = Class.forName(c + "SpeedVaultAnimator").getConstructor(vType);
            climbUp = Class.forName(c + "ClimbUpAnimator").getConstructor();
            clingAnim = Class.forName(c + "ClingToCliffAnimator").getConstructor();
            wallJump = Class.forName(c + "WallJumpAnimator").getConstructor(boolean.class);
            rollAnim = Class.forName(c + "RollAnimator").getConstructor(rDir);
            slideAnim = Class.forName(c + "SlidingAnimator").getConstructor();
            fastRunAnim = Class.forName(c + "FastRunningAnimator").getConstructor();
            dodgeAnim = Class.forName(c + "DodgeAnimator").getConstructor(dDir);
            catLeapAnim = Class.forName(c + "CatLeapAnimator").getConstructor();
            return true;
        } catch (Throwable t) {
            broken = true; animGet = null;
            XenoWarriors.LOGGER.warn("Bots' parkour poses off (ParCool's API changed?)", t);
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumOf(Class<?> e, String name) { return Enum.valueOf((Class) e, name); }

    /** Each client tick, for each bot's body. */
    public static void tick(AbstractClientPlayer body, PartyBotEntity bot) {
        if (!ready()) return;
        try {
            Object anim = animGet.invoke(null, body), pk = pkGet.invoke(null, body);
            if (anim == null || pk == null) return;
            int packed = bot.parkour();
            Integer was = SHOWN.put(body, packed);
            if (was == null || was != packed) start(body, bot, anim, pk, BotParkour.codeOf(packed));
            animTick.invoke(anim, body, pk);
            Object a = animatorField.get(anim);
            if (a != null && (boolean) shouldRemoved.invoke(a, body, pk)) removeAnimator.invoke(anim);
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("Bots' parkour poses off (ParCool's API changed?)", t);
        }
    }

    private static void start(AbstractClientPlayer body, PartyBotEntity bot, Object anim, Object pk, int code) throws Exception {
        for (Class<?> c : new Class<?>[]{roll, slide, fastRun, cling, catLeap}) setDoing.invoke(actionGet.invoke(pk, c), false);
        Object animator = null;
        Class<?> doing = null;
        switch (code) {
            case BotParkour.VAULT_R -> animator = speedVault.newInstance(vaultRight);
            case BotParkour.VAULT_L -> animator = speedVault.newInstance(vaultLeft);
            case BotParkour.CLING -> { animator = clingAnim.newInstance(); doing = cling; }
            case BotParkour.CLIMB -> animator = climbUp.newInstance();
            case BotParkour.WALLJUMP_R -> animator = wallJump.newInstance(true);
            case BotParkour.WALLJUMP_L -> animator = wallJump.newInstance(false);
            case BotParkour.ROLL -> { animator = rollAnim.newInstance(rollFront); doing = roll; }
            case BotParkour.SLIDE -> { animator = slideAnim.newInstance(); doing = slide; }
            case BotParkour.FAST_RUN -> {
                // (Mikiko and Rhalix have runs of their own: ParCool's isn't laid over them, as for players)
                CharacterType t = bot.character();
                if (t != CharacterType.MIKIKO && t != CharacterType.RHALIX) { animator = fastRunAnim.newInstance(); doing = fastRun; }
            }
            case BotParkour.DODGE_L -> animator = dodgeAnim.newInstance(dodgeLeft);
            case BotParkour.DODGE_R -> animator = dodgeAnim.newInstance(dodgeRight);
            case BotParkour.LEAP -> { animator = catLeapAnim.newInstance(); doing = catLeap; }
            default -> { }
        }
        if (doing != null) setDoing.invoke(actionGet.invoke(pk, doing), true);
        if (animator != null) { setAnimator.invoke(anim, animator); APPLIED.add(animator.getClass().getSimpleName()); }
        else if (code == BotParkour.NONE || code == BotParkour.FAST_RUN) {
            Object a = animatorField.get(anim);
            if (a != null && (boolean) shouldRemoved.invoke(a, body, pk)) removeAnimator.invoke(anim);
        }
    }
}

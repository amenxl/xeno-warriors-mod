package com.xenowarriors.client.compat.epicfight;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.client.bot.BotAvatar;
import net.minecraft.client.Minecraft;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Bots on Epic Fight's body, like players: the player body a bot is drawn with (client/bot/BotAvatar) gets Epic Fight
 * running on it - in battle mode, its animator ticked - so a bot holds and swings its weapon, walks and runs with Epic
 * Fight's animations, and each of its swings plays the next hit of the same combo a player of that character gets
 * (Mikiko's glove punches, Kyrie's Iron Fist, a sword's combo...). Everything else stays as for players
 * (EpicFightCompat decides who draws what). The avatar is never in the world, so this starts and ticks it by hand.
 */
public final class EfBotBodies {
    private EfBotBodies() {}

    private static final java.util.Map<BotAvatar, int[]> STATE = new java.util.WeakHashMap<>();   // {started, combo index, was swinging, last swing tick, last swing time}
    /** On (XW_EF_BOTS=0 turns it off). A bot in its menu is drawn by our renderer, not Epic Fight's (EpicFightCompat.decideRender). */
    public static boolean off = "0".equals(System.getenv("XW_EF_BOTS"));
    private static int errors;
    /** (Tests) bodies started, swings played, the last error. */
    public static int started, swings;
    public static String lastError;

    /** From the client tick, after the avatars copied their bots. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void tick(java.util.List<? extends net.minecraft.client.player.AbstractClientPlayer> avatars) {
        com.xenowarriors.client.bot.BotAvatar.efBodies = !off;
        if (off) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (var p : avatars) {
            if (!(p instanceof BotAvatar a) || a.bot() == null) continue;
            try {
                PlayerPatch patch = EpicFightCapabilities.getEntityPatch(a, PlayerPatch.class);
                if (patch == null) continue;
                int[] st = STATE.computeIfAbsent(a, k -> new int[]{0, -1, 0, -100, 0});
                if (st[0] == 0) {
                    patch.onJoinWorld(a, new net.minecraftforge.event.entity.EntityJoinLevelEvent(a, mc.level));
                    st[0] = 1;
                    started++;
                }
                if (!patch.isEpicFightMode()) patch.toEpicFightMode(false);
                walkMotion(patch, a);                                            // (Epic Fight never works out a remote player's walking itself: it's told)
                patch.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(a));
                walkMotion(patch, a);                                            // (and again after: its tick put a remote player back to idle)
                PartyBotEntity b = a.bot();
                boolean swinging = b.swinging;
                // a new swing: it started swinging, or its swing started over (a bot attacking non-stop never stops "swinging")
                boolean fresh = swinging && (st[2] == 0 || b.swingTime < st[4]);
                if (fresh && !b.downed() && a.tickCount - st[3] > 4) swing(patch, a, st);
                st[2] = swinging || a.tickCount - st[3] <= 4 ? 1 : 0;
                st[4] = swinging ? b.swingTime : 0;
                errors = 0;
            } catch (Throwable t) {
                lastError = String.valueOf(t);
                if (++errors > 400) {
                    off = true;
                    com.xenowarriors.XenoWarriors.LOGGER.warn("[Xeno Warriors] bots on Epic Fight's body keep failing - off for this session", t);
                } else if (errors == 1) com.xenowarriors.XenoWarriors.LOGGER.warn("[Xeno Warriors] a bot's Epic Fight body failed this tick", t);
            }
        }
    }

    /** (Tests) walk / run motions set on bots' bodies. */
    public static int walkSet;

    /**
     * Idle, walking or running, from how the bot moves. Epic Fight takes a player's motion from that player's own game
     * (sent over the network) - a bot's body has nobody's game behind it, so it stayed "idle" and its legs never moved
     * whenever an Epic Fight pose covered them (Sam walking with his bat). Only those three are set; anything else
     * Epic Fight has on (falling, flying, a hit...) is left alone.
     */
    private static void walkMotion(PlayerPatch<?> patch, BotAvatar a) {
        var cur = patch.currentLivingMotion;
        boolean ours = cur == yesman.epicfight.api.animation.LivingMotions.IDLE || cur == yesman.epicfight.api.animation.LivingMotions.WALK
                || cur == yesman.epicfight.api.animation.LivingMotions.RUN;
        if (!ours) return;
        PartyBotEntity b = a.bot();
        double dx = b.getX() - b.xo, dz = b.getZ() - b.zo;
        boolean moving = (b.onGround() || a.onGround()) && !b.isPassenger() && dx * dx + dz * dz > 0.015 * 0.015;
        var want = !moving ? yesman.epicfight.api.animation.LivingMotions.IDLE : a.isSprinting() ? yesman.epicfight.api.animation.LivingMotions.RUN : yesman.epicfight.api.animation.LivingMotions.WALK;
        patch.currentLivingMotion = want;
        if (patch.currentCompositeMotion == yesman.epicfight.api.animation.LivingMotions.IDLE || patch.currentCompositeMotion == yesman.epicfight.api.animation.LivingMotions.WALK
                || patch.currentCompositeMotion == yesman.epicfight.api.animation.LivingMotions.RUN) patch.currentCompositeMotion = want;
        if (want != yesman.epicfight.api.animation.LivingMotions.IDLE) walkSet++;
    }

    /**
     * A bot's combo hit from the server (its swings in a fight come this way: the plain arm-swing packet doesn't reach a
     * bot Epic Fight looks after). Plays that hit of the combo on its body.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    /** (Tests) which hits of the combo bots played. */
    public static final java.util.Set<Integer> PLAYED = new java.util.TreeSet<>();

    /** (Tests) the Epic Fight parkour animations bots played, by name. */
    public static final java.util.Set<String> PARKOUR = new java.util.LinkedHashSet<>();
    private static final java.util.Map<String, Object> PK_ANIMS = new java.util.HashMap<>();

    /**
     * A bot's parkour move (BotParkour) on its Epic Fight body: the Epic Fight x ParCool add-on's animation for it
     * (its vaults, climb up, wall jump, roll, slide, cat leap), else Epic Fight's own step for a dodge. Its held poses
     * (hanging on a ledge, fast running, sliding) come from the add-on by itself once ParCool's pose is on the body.
     * False if there was nothing to play.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean parkour(PartyBotEntity b, String anim) {
        if (off || anim == null) return false;
        BotAvatar a = com.xenowarriors.client.bot.BotAvatars.existing(b);
        if (a == null) return false;
        try {
            PlayerPatch patch = EpicFightCapabilities.getEntityPatch(a, PlayerPatch.class);
            int[] st = STATE.get(a);
            if (patch == null || st == null || st[0] == 0) return false;
            Object acc = PK_ANIMS.computeIfAbsent(anim, k -> {
                try {
                    if (k.startsWith("ef:")) return yesman.epicfight.gameasset.Animations.class.getField(k.substring(3)).get(null);
                    if (!net.minecraftforge.fml.ModList.get().isLoaded("epicparcool")) return null;
                    return Class.forName("com.yesman.epicparcool.animations.ParCoolAnimations").getField(k).get(null);
                } catch (Throwable t) { return null; }
            });
            if (acc == null) return false;
            patch.getClientAnimator().playAnimation((yesman.epicfight.api.asset.AssetAccessor) acc, 0F);
            PARKOUR.add(anim);
            return true;
        } catch (Throwable t) {
            lastError = String.valueOf(t);
            return false;
        }
    }

    public static boolean comboHit(PartyBotEntity b, int hit) {
        if (off || b.downed()) return false;
        BotAvatar a = com.xenowarriors.client.bot.BotAvatars.existing(b);
        if (a == null) return false;
        try {
            PlayerPatch patch = EpicFightCapabilities.getEntityPatch(a, PlayerPatch.class);
            int[] st = STATE.get(a);
            if (patch == null || st == null || st[0] == 0) return false;
            var cap = patch.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND);
            java.util.List list = cap == null ? null : cap.getAutoAttackMotion(patch);
            if (list == null || list.isEmpty()) return false;
            int combo = Math.max(1, list.size() - 2);
            boolean air = !b.onGround() && !b.isNoGravity() && list.size() >= 3;
            int i = air ? list.size() - 1 : Math.floorMod(hit, combo);
            st[1] = i; st[2] = 1; st[3] = a.tickCount; PLAYED.add(i);                         // (and the arm swing that comes with it isn't a second hit)
            patch.getClientAnimator().playAnimation((yesman.epicfight.api.asset.AssetAccessor) list.get(i), 0F);
            swings++; comboHits++;
            return true;
        } catch (Throwable t) { lastError = String.valueOf(t); return false; }
    }

    /** (Tests) hits played from the bots' combo packets. */
    public static int comboHits;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void swing(PlayerPatch patch, BotAvatar a, int[] st) {
        var cap = patch.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND);
        java.util.List list = cap == null ? null : cap.getAutoAttackMotion(patch);
        if (list == null || list.isEmpty()) return;
        int combo = Math.max(1, list.size() - 2);                              // (the combo, without the dash and air attacks)
        boolean air = !a.onGround() && !a.getAbilities().flying && list.size() >= 3;
        int i;
        if (air) i = list.size() - 1;
        else {
            st[1] = a.tickCount - st[3] < 30 ? (st[1] + 1) % combo : 0;           // (a pause starts the combo over)
            i = st[1];
        }
        st[3] = a.tickCount;
        var anim = (yesman.epicfight.api.asset.AssetAccessor) list.get(i);
        patch.getClientAnimator().playAnimation(anim, 0F);
        swings++;
    }
}

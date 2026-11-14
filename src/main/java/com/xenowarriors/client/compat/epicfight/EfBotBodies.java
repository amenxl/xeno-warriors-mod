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

    private static final java.util.Map<BotAvatar, int[]> STATE = new java.util.WeakHashMap<>();   // {started, combo index, was swinging, last swing tick}
    public static boolean off = "0".equals(System.getenv("XW_EF_BOTS"));
    private static int errors;
    /** (Tests) bodies started, swings played, the last error. */
    public static int started, swings;
    public static String lastError;

    /** From the client tick, after the avatars copied their bots. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void tick(java.util.List<? extends net.minecraft.client.player.AbstractClientPlayer> avatars) {
        if (off) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (var p : avatars) {
            if (!(p instanceof BotAvatar a) || a.bot() == null) continue;
            try {
                PlayerPatch patch = EpicFightCapabilities.getEntityPatch(a, PlayerPatch.class);
                if (patch == null) continue;
                int[] st = STATE.computeIfAbsent(a, k -> new int[]{0, -1, 0, -100});
                if (st[0] == 0) {
                    patch.onJoinWorld(a, new net.minecraftforge.event.entity.EntityJoinLevelEvent(a, mc.level));
                    st[0] = 1;
                    started++;
                }
                if (!patch.isEpicFightMode()) patch.toEpicFightMode(false);
                patch.tick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(a));
                PartyBotEntity b = a.bot();
                boolean swinging = b.swinging;
                if (swinging && st[2] == 0 && !b.downed()) swing(patch, a, st);
                st[2] = swinging ? 1 : 0;
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

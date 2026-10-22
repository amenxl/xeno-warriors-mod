package com.xenowarriors.client;

import com.xenowarriors.client.mikiko.GeoAnims;
import com.xenowarriors.entity.HekubladeShardEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Riding a Hekublade like a hoverboard: standing sideways-wide on the blade, knees loose, arms out for balance.
 * Attacking up there he fights from the board - carving slashes, a rising backhand, a snap kick off the front,
 * a leaping overhead chop, a spinning slash and a wind palm - feet staying planted.
 */
public final class SurfPose {
    private static final String[][] COMBOS = {
            {"surf_carve_slash", "surf_rising_slash", "surf_board_kick", "surf_spin_slash", "surf_overhead_chop"},
            {"surf_rising_slash", "surf_carve_slash", "surf_wind_palm", "surf_board_kick", "surf_spin_slash"},
            {"surf_board_kick", "surf_carve_slash", "surf_rising_slash", "surf_wind_palm", "surf_overhead_chop"},
    };
    private static final int COMBO_TIMEOUT = 30;
    private static final Random RANDOM = new Random();
    /** id -> {combo, step, last attack tick, move start tick, move index or -1} */
    private static final Map<Integer, int[]> STATE = new HashMap<>();

    private SurfPose() {}

    public static boolean surfing(Player p) {
        return p.getVehicle() instanceof HekubladeShardEntity blade && blade.surfing();
    }

    /** A charged swing while surfing: the next move of a board combo. */
    public static void startAttack(Player p, int now) {
        int[] s = STATE.computeIfAbsent(p.getId(), k -> new int[]{0, 0, -1000, 0, -1});
        if (now - s[2] > COMBO_TIMEOUT || s[1] >= 5) {
            s[0] = RANDOM.nextInt(COMBOS.length);
            s[1] = 0;
        }
        s[4] = s[0] * 5 + s[1]++;
        s[3] = now;
        s[2] = now;
    }

    public static boolean attacking(Player p) {
        int[] s = STATE.get(p.getId());
        if (s == null || s[4] < 0) return false;
        String move = COMBOS[s[4] / 5][s[4] % 5];
        return p.tickCount - s[3] <= GeoAnims.length(move);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!surfing(p)) return;
        float sway = Mth.sin(age * 0.25F) * 0.08F;
        m.rightLeg.xRot = -0.15F;
        m.leftLeg.xRot = 0.15F;
        m.rightLeg.yRot = 0.25F;
        m.leftLeg.yRot = -0.25F;
        m.rightLeg.zRot = 0.22F;
        m.leftLeg.zRot = -0.22F;
        m.rightArm.xRot = -0.25F + sway;
        m.leftArm.xRot = 0.2F - sway;
        m.rightArm.yRot = 0F;
        m.leftArm.yRot = 0F;
        m.rightArm.zRot = 1.15F + sway;
        m.leftArm.zRot = -1.0F + sway;
        m.body.xRot = 0.2F;
        m.head.xRot = Math.max(m.head.xRot, -0.3F);
        int[] s = STATE.get(p.getId());
        if (s != null && s[4] >= 0) {
            String move = COMBOS[s[4] / 5][s[4] % 5];
            float t = age - s[3], len = GeoAnims.length(move);
            if (t >= 0F && t <= len) GeoAnims.apply(m, move, t, false, GeoAnims.envelope(t, len, 1.5F));
            else s[4] = -1;
        }
        m.hat.copyFrom(m.head);
    }
}

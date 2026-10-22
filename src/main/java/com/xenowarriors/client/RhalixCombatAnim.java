package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.mikiko.GeoAnims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Rhalix fights bare-handed with quick punches and a lot of kicks: every charged swing plays the next move of a
 * five-hit combo (jabs, crosses, an uppercut, front kicks, a roundhouse, low kicks, an axe kick, a spinning back kick).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixCombatAnim {
    private static final String[][] COMBOS = {
            {"rh_jab_right", "rh_jab_left", "rh_front_kick_right", "rh_roundhouse_left", "rh_axe_kick_right"},
            {"rh_low_kick_right", "rh_jab_left", "rh_gut_kick_left", "rh_uppercut_right", "rh_spin_back_kick"},
            {"rh_front_kick_right", "rh_gut_kick_left", "rh_roundhouse_left", "rh_jab_right", "rh_spin_back_kick"},
            {"rh_jab_left", "rh_low_kick_right", "rh_jab_right", "rh_front_kick_right", "rh_axe_kick_right"},
    };
    private static final int COMBO_TIMEOUT = 30;
    private static final Random RANDOM = new Random();

    private static final class State {
        boolean lastSwinging;
        int lastSwingTime, lastSwingStart = -100, lastAttack = -100, step, variant = -1;
        String move;
        int moveStart;
        float strength;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private RhalixCombatAnim() {}

    private static boolean rhalix(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == CharacterType.RHALIX;
    }

    public static boolean attacking(Player p) {
        State s = STATES.get(p.getId());
        return s != null && s.move != null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) {
            if (mc.level == null) STATES.clear();
            return;
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!rhalix(p)) continue;
            State s = STATES.computeIfAbsent(p.getId(), k -> new State());
            int now = p.tickCount;
            boolean start = p.swinging && p.swingingArm == InteractionHand.MAIN_HAND && (!s.lastSwinging || p.swingTime < s.lastSwingTime);
            s.lastSwinging = p.swinging;
            s.lastSwingTime = p.swingTime;
            boolean charged = p == mc.player ? s.strength >= 0.9F : now - s.lastSwingStart >= 9;
            if (start && p.getMainHandItem().isEmpty() && charged && !(mc.hitResult != null && p == mc.player
                    && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)) {
                if (now - s.lastAttack > COMBO_TIMEOUT || s.step >= 5 || s.variant < 0) {
                    s.step = 0;
                    s.variant = RANDOM.nextInt(COMBOS.length);
                }
                s.move = COMBOS[s.variant][s.step++];
                s.moveStart = now;
                s.lastAttack = now;
            }
            if (start) s.lastSwingStart = now;
            if (p == mc.player) s.strength = p.getAttackStrengthScale(0F);
            if (s.move != null && now - s.moveStart > GeoAnims.length(GeoAnims.RHALIX, s.move) + 1) s.move = null;   // (length is in ticks)
        }
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        State s = STATES.get(p.getId());
        if (s == null || s.move == null || !rhalix(p)) return;
        float t = age - s.moveStart;                                  // ticks, like GeoAnims expects
        float len = GeoAnims.length(GeoAnims.RHALIX, s.move);
        if (t < 0 || t > len) return;
        GeoAnims.apply(m, GeoAnims.RHALIX, s.move, t, false, GeoAnims.envelope(t, len, 1.5F));
        m.hat.copyFrom(m.head);
    }
}

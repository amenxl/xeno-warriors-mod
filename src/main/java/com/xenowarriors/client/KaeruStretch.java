package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Kaeru's idle: stand still a few seconds and she stretches - arms right up over her head, a lean to one side and the
 * other, an arm pulled across her chest (each), a foot pulled up behind her (each), a high kick with each leg, and a
 * wide side lunge each way; a breather, and again.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KaeruStretch {
    public static final String ID = "kaeru_stretch";
    private static final int AFTER = 90, CYCLE = 592;
    private static final Map<Integer, int[]> STILL = com.xenowarriors.client.ClientReset.track(new HashMap<>());
    private KaeruStretch() {}

    private static boolean kaeru(Player p) { return IdleBorrow.idlesAs(p, CharacterType.KAERU); }   // (or a made character borrowing her idle)

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STILL.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : com.xenowarriors.client.bot.BotAvatars.players(mc.level)) {
            if (!kaeru(p)) { STILL.remove(p.getId()); continue; }
            int[] s = STILL.computeIfAbsent(p.getId(), k -> new int[1]);
            boolean calm = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && !p.isCrouching()
                    && !Busy.action(p) && !Idles.taken(p, ID) && p.hurtTime == 0 && !p.isUsingItem();
            s[0] = calm ? s[0] + 1 : 0;
            if (s[0] >= AFTER) Idles.hold(p, ID);
        }
    }

    /** Ticks into the stretch (or -1). */
    public static int time(Player p) {
        int[] s = STILL.get(p.getId());
        return s == null || s[0] < AFTER ? -1 : s[0] - AFTER;
    }

    // A pose: rArm x y z, lArm x y z, body x z (kept 0), head x z, rLeg x z, lLeg x z (+ z swings the right one out, - the left one),
    // then the knees (right, left: + folds the shin back) - so a pulled-up foot, a kick's push-off and a lunge's bent knee look right
    private static float[] P(float... v) { return v; }
    private static final float[] REST    = P(0F, 0F, 0F,        0F, 0F, 0F,        0F, 0F,      0F, 0F,       0F, 0F,       0F, 0F,      0F, 0F);
    private static final float[] UP      = P(-3.05F, 0.1F, -0.18F, -3.05F, -0.1F, 0.18F, 0F, 0F,     -0.45F, 0F,   0.04F, 0F,    0.04F, 0F,   0F, 0F);
    private static final float[] LEAN_R  = P(-2.85F, 0.1F, 0.35F, -2.75F, -0.1F, 0.75F, 0F, 0F,     -0.2F, 0.3F,  0F, 0.06F,      0F, -0.16F,  0F, 0.15F);
    private static final float[] LEAN_L  = P(-2.75F, 0.1F, -0.75F, -2.85F, -0.1F, -0.35F, 0F, 0F,   -0.2F, -0.3F, 0F, 0.16F,      0F, -0.06F,  0.15F, 0F);
    private static final float[] CROSS_R = P(-1.5F, -0.95F, 0F,   -1.15F, 0.55F, 0F,  0F, 0F,     0.1F, -0.2F,  0F, 0.04F,    0F, -0.04F,   0F, 0F);
    private static final float[] CROSS_L = P(-1.15F, -0.55F, 0F,  -1.5F, 0.95F, 0F,   0F, 0F,     0.1F, 0.2F,   0F, 0.04F,    0F, -0.04F,   0F, 0F);
    // quad stretch: a foot pulled up behind her, that hand holding it, the other arm out for balance
    private static final float[] QUAD_R  = P(0.55F, 0.1F, 0.08F,  -0.2F, 0F, -1.2F,   0F, 0F,      0.15F, 0.1F,  0.3F, 0.02F,   0F, -0.03F,  1.9F, 0F);
    private static final float[] QUAD_L  = P(-0.2F, 0F, 1.2F,     0.55F, -0.1F, -0.08F, 0F, 0F,     0.15F, -0.1F, 0F, 0.03F,    0.3F, -0.02F, 0F, 1.9F);
    // high kick: a leg swung straight up in front (like her art), arms out
    private static final float[] KICK_R  = P(0.2F, 0F, 1.25F,     0.2F, 0F, -1.25F,  0F, 0F,      -0.15F, 0F,   -1.45F, 0.04F, 0.08F, 0F,  0F, 0.2F);
    private static final float[] KICK_L  = P(0.2F, 0F, 1.25F,     0.2F, 0F, -1.25F,  0F, 0F,      -0.15F, 0F,   0.08F, 0F,    -1.45F, -0.04F, 0.2F, 0F);
    // side lunge: a leg out wide, leaning over it, hands on the other knee
    private static final float[] LUNGE_R = P(-0.7F, 0.35F, 0.05F, -0.7F, -0.15F, 0F,  0F, 0F,      0.35F, 0.15F, -0.15F, 0.42F, -0.25F, -0.08F, 0.25F, 0.55F);
    private static final float[] LUNGE_L = P(-0.7F, 0.15F, 0F,    -0.7F, -0.35F, -0.05F, 0F, 0F,    0.35F, -0.15F, -0.25F, 0.08F, -0.15F, -0.42F, 0.55F, 0.25F);

    /** The stretch's keyframes: {start tick, pose}; it holds each pose and eases between them. */
    private static final Object[][] KEYS = {
        {0, REST}, {25, UP}, {70, UP}, {90, LEAN_R}, {125, LEAN_R}, {150, LEAN_L}, {185, LEAN_L}, {205, UP}, {215, REST},
        {225, CROSS_R}, {255, CROSS_R}, {265, CROSS_L}, {292, CROSS_L}, {305, REST},
        {320, QUAD_R}, {355, QUAD_R}, {372, QUAD_L}, {407, QUAD_L}, {422, REST},
        {434, KICK_R}, {440, KICK_R}, {452, REST}, {462, KICK_L}, {468, KICK_L}, {480, REST},
        {494, LUNGE_R}, {526, LUNGE_R}, {544, LUNGE_L}, {576, LUNGE_L}, {CYCLE, REST}};
    private static final int REST_GAP = 120;

    private static float[] pose(float t) {
        if (t >= CYCLE) return REST;
        for (int i = 0; i < KEYS.length - 1; i++) {
            int a = (Integer) KEYS[i][0], b = (Integer) KEYS[i + 1][0];
            if (t >= a && t < b) {
                float x = (t - a) / (float) (b - a); x = x * x * (3 - 2 * x);
                float[] p = (float[]) KEYS[i][1], q = (float[]) KEYS[i + 1][1], o = new float[p.length];
                for (int k = 0; k < p.length; k++) o[k] = Mth.lerp(x, p[k], q[k]);
                return o;
            }
        }
        return REST;
    }

    private static void set(ModelPart m, float w, float x, float y, float z) {
        m.xRot = Mth.lerp(w, m.xRot, x); m.yRot = Mth.lerp(w, m.yRot, y); m.zRot = Mth.lerp(w, m.zRot, z);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!kaeru(p)) return;
        int t0 = time(p);
        if (t0 < 0) return;
        float t = (t0 + (age - (int) age)) % (CYCLE + REST_GAP);
        if (t >= CYCLE) return;                                            // (a breather between stretches)
        float w = Mth.clamp(Math.min(t, CYCLE - t) / 12F, 0F, 1F);
        float[] q = pose(t);
        float bob = t > 25 && t < 70 || t > 320 && t < 407 ? Mth.sin(age * 0.25F) * 0.04F : 0F;   // (a little wobble: up on her toes, on one foot)
        set(m.rightArm, w, q[0] + bob, q[1], q[2]);
        set(m.leftArm, w, q[3] + bob, q[4], q[5]);
        // (the body itself never tips: it turns about the neck, so the hips would swing away from the legs and they'd look
        //  cut off - the lean is all in the arms, head and legs, which turn at the shoulders and hips and stay joined on)
        set(m.head, w, q[8], m.head.yRot, q[9]);
        set(m.rightLeg, w, q[10], 0F, q[11]);
        set(m.leftLeg, w, q[12], 0F, q[13]);
        if (q[14] != 0F || q[15] != 0F) LimbBend.extraKnees(p, q[14] * w, q[15] * w);
        m.hat.copyFrom(m.head);                                             // (PlayerModel copies the rest onto the sleeves after this)
    }

    // her tail: three pieces hanging off the back of her waist (one pivot, so they move as one)
    private static final String[] TAIL = {"n48", "n49", "n50"};

    /**
     * Her tail while she stretches her arms up: it lifts out behind her and swishes, and swings the other way to each
     * side lean (back to hanging down once her arms come down). Called by CharacterDetailLayer each frame.
     */
    public static void tail(com.xenowarriors.client.render.DetailModel m, Player p, float age) {
        ModelPart b = m.body;
        ModelPart[] parts = new ModelPart[TAIL.length];
        for (int i = 0; i < TAIL.length; i++) {
            if (!b.hasChild(TAIL[i])) return;
            parts[i] = b.getChild(TAIL[i]);
            parts[i].resetPose();                                           // (the model is shared: back to hanging every frame)
        }
        if (!kaeru(p)) return;
        int t0 = time(p);
        if (t0 < 0) return;
        float t = (t0 + (age - (int) age)) % (CYCLE + REST_GAP);
        float up = ramp(t, 10, 25) * (1F - ramp(t, 205, 218));              // arms up: overhead, both leans, back up
        if (up <= 0F) return;
        float side = t < 80 ? 0F : t < 92 ? ramp(t, 80, 92) : t < 125 ? 1F : t < 150 ? 1F - 2F * ramp(t, 125, 150)
                : t < 185 ? -1F : -1F + ramp(t, 185, 205);                  // (+1 leaning right, -1 left)
        float swish = Mth.sin(age * 0.22F) * 0.3F + Mth.sin(age * 0.53F) * 0.08F;
        for (ModelPart part : parts) {
            part.xRot -= 1.05F * up + Mth.sin(age * 0.17F) * 0.06F * up;     // lifted out behind her (a little bounce)
            part.yRot += (swish - side * 0.45F) * up;                        // swishing, swung away from the lean
        }
        tailMoves++;
    }
    /** Frames her tail was posed by the stretch (tests). */
    public static int tailMoves;

    private static float ramp(float t, float a, float b) {
        float x = Mth.clamp((t - a) / (b - a), 0F, 1F);
        return x * x * (3 - 2 * x);
    }
}

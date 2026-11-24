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
 * Kaeru's idle: stand still a few seconds and she stretches - arms right up over her head (up on her toes), a lean to
 * one side and the other, then an arm pulled across her chest, one and then the other; a breather, and again.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KaeruStretch {
    public static final String ID = "kaeru_stretch";
    private static final int AFTER = 90, CYCLE = 300;
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

    //                                   rArm x     y      z    lArm x     y      z   body x    z    head x    z
    private static final float[] REST   = {0F,      0F,    0F,    0F,      0F,    0F,    0F,   0F,    0F,   0F};
    private static final float[] UP     = {-3.05F,  0.1F, -0.18F, -3.05F, -0.1F,  0.18F, -0.1F, 0F,  -0.45F, 0F};
    private static final float[] LEAN_R = {-2.85F,  0.1F,  0.35F, -2.75F, -0.1F,  0.75F, -0.05F, 0.14F, -0.2F, 0.3F};
    private static final float[] LEAN_L = {-2.75F,  0.1F, -0.75F, -2.85F, -0.1F, -0.35F, -0.05F, -0.14F, -0.2F, -0.3F};
    private static final float[] CROSS_R = {-1.5F, -0.95F, 0F,   -1.15F,  0.55F, 0F,    0F,   0F,    0.1F, -0.2F};
    private static final float[] CROSS_L = {-1.15F, -0.55F, 0F,  -1.5F,   0.95F, 0F,    0F,   0F,    0.1F,  0.2F};

    /** The stretch's keyframes: {start tick, pose}; it holds each pose and eases between them. */
    private static final Object[][] KEYS = {
        {0, REST}, {25, UP}, {70, UP}, {90, LEAN_R}, {125, LEAN_R}, {150, LEAN_L}, {185, LEAN_L}, {205, UP}, {215, REST},
        {225, CROSS_R}, {255, CROSS_R}, {265, CROSS_L}, {292, CROSS_L}, {CYCLE, REST}};
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
        float bob = t > 25 && t < 70 ? Mth.sin(age * 0.25F) * 0.03F : 0F;   // (a little wobble, up on her toes)
        set(m.rightArm, w, q[0] + bob, q[1], q[2]);
        set(m.leftArm, w, q[3] + bob, q[4], q[5]);
        set(m.body, w, q[6], 0F, q[7]);
        set(m.head, w, q[8], m.head.yRot, q[9]);
        m.hat.copyFrom(m.head);                                             // (PlayerModel copies the rest onto the sleeves after this)
    }
}

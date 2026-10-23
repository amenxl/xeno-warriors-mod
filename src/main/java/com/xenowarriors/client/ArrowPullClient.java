package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
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
 * Arrows stuck in Mikiko or Rhalix: standing still, they reach for one, grip it, wince and yank it out - one after
 * another until they're all gone.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ArrowPullClient {
    private static final int START_AFTER = 20, CYCLE = 34, YANK = 24;
    private static final Map<Integer, int[]> STATE = new HashMap<>();   // id -> {ticks still, ticks pulling, which spot}

    private ArrowPullClient() {}

    private static boolean warrior(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) != CharacterType.NONE;
    }

    /** Ticks into the current pull, or -1. */
    public static int pulling(Player p) {
        int[] s = STATE.get(p.getId());
        return s == null || s[1] <= 0 ? -1 : (s[1] - 1) % CYCLE;
    }

    public static boolean active(Player p) {
        return pulling(p) >= 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STATE.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!warrior(p) || p.getArrowCount() <= 0) { STATE.remove(p.getId()); continue; }
            int[] s = STATE.computeIfAbsent(p.getId(), k -> new int[3]);
            boolean still = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && p.onGround() && !p.swinging
                    && !p.isUsingItem() && p.hurtTime == 0 && !p.isSleeping() && !p.isPassenger();
            s[0] = still ? s[0] + 1 : 0;
            if (s[0] < START_AFTER) { s[1] = 0; continue; }
            s[1]++;
            int t = (s[1] - 1) % CYCLE;
            if (t == 0) s[2] = p.getRandom().nextInt(3);                     // where this one's stuck
            if (t == YANK && p == mc.player) com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.PullArrowPacket());
        }
    }

    private static float sm(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        int[] s = STATE.get(p.getId());
        if (s == null || s[1] <= 0 || LimbBend.firstPerson) return;
        float t = (s[1] - 1) % CYCLE + Minecraft.getInstance().getFrameTime();
        float reach = sm(t / 8F) * (1F - sm((t - YANK - 4) / 6F));
        float yank = sm((t - YANK + 2) / 3F) * (1F - sm((t - YANK - 3) / 6F));
        float grip = t > 8 && t < YANK ? Mth.sin(t * 1.7F) * 0.03F : 0F;     // working it loose
        // the spot: 0 = left shoulder, 1 = chest/side, 2 = left thigh
        float[][] spots = {{-2.1F, -0.75F, 0.1F, -1.5F}, {-1.0F, -0.85F, 0F, -1.3F}, {-0.35F, -0.45F, 0.15F, -0.5F}};
        float[] sp = spots[s[2]];
        m.rightArm.xRot = Mth.lerp(reach, m.rightArm.xRot, sp[0] + grip);
        m.rightArm.yRot = Mth.lerp(reach, m.rightArm.yRot, sp[1]);
        m.rightArm.zRot = Mth.lerp(reach, m.rightArm.zRot, sp[2]);
        // yanked out and away
        m.rightArm.xRot = Mth.lerp(yank, m.rightArm.xRot, -0.9F);
        m.rightArm.yRot = Mth.lerp(yank, m.rightArm.yRot, 0.2F);
        m.rightArm.zRot = Mth.lerp(yank, m.rightArm.zRot, 0.9F);
        LimbBend.extraElbows(p, Mth.lerp(yank, sp[3], -0.2F) * reach, 0F);
        // head looking down at it, flinching as it comes out
        m.head.xRot = Mth.lerp(reach, m.head.xRot, s[2] == 2 ? 0.75F : 0.5F) - 0.3F * yank;
        m.head.yRot = Mth.lerp(reach, m.head.yRot, s[2] == 0 ? 0.35F : 0F);
        if (s[2] == 2) m.body.xRot = Mth.lerp(reach, m.body.xRot, 0.25F);
        m.hat.copyFrom(m.head);
    }

    /** Wincing as it comes out, gritting while working it loose. */
    public static String face(Player p) {
        int t = pulling(p);
        if (t < 0) return null;
        if (t >= YANK - 1 && t < YANK + 6) return "hurt";
        return t >= 8 ? "strained" : null;
    }
}

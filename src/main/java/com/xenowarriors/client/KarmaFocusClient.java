package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Focusing on Karma Sensing (holding the key), eyes closed:
 * <ul>
 * <li>Rhalix rises off the ground and floats cross-legged, wings spread wide, hands together in front of him;</li>
 * <li>Mikiko folds his arms - and the longer he keeps at it, he brings a hand up to his head, concentrating.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KarmaFocusClient {
    private static final int HAND_TO_HEAD = 70;
    private static final Map<Integer, float[]> W = new HashMap<>();   // id -> {weight, prev, on, ticks focusing}
    private static final Set<Integer> LIFTED = new HashSet<>();

    private KarmaFocusClient() {}

    public static void set(int id, boolean on) {
        float[] w = W.computeIfAbsent(id, k -> new float[4]);
        if (on && w[2] == 0) w[3] = 0;
        w[2] = on ? 1 : 0;
    }

    public static boolean focusing(Player p) {
        float[] w = W.get(p.getId());
        return w != null && w[2] > 0;
    }

    public static float weight(Player p) {
        float[] w = W.get(p.getId());
        if (w == null) return 0F;
        float t = Mth.lerp(Minecraft.getInstance().getFrameTime(), w[1], w[0]);
        return t * t * (3 - 2 * t);
    }

    private static float ticks(Player p) {
        float[] w = W.get(p.getId());
        return w == null ? 0F : w[3] + Minecraft.getInstance().getFrameTime();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        W.entrySet().removeIf(e -> {
            float[] w = e.getValue();
            w[1] = w[0];
            w[0] = Mth.clamp(w[0] + (w[2] > 0 ? 0.12F : -0.15F), 0F, 1F);
            if (w[2] > 0) w[3]++;
            if (w[2] > 0 && mc.level.getEntity(e.getKey()) instanceof AbstractClientPlayer p && w[0] > 0.5F) aura(mc, p, w[3]);
            return w[0] <= 0F && w[1] <= 0F && w[2] == 0 || mc.level.getEntity(e.getKey()) == null;
        });
    }

    /** A soft glow of their Karma color rising around them while they focus. */
    private static void aura(Minecraft mc, AbstractClientPlayer p, float t) {
        if ((int) t % 3 != 0) return;
        boolean mikiko = ClientCharacterData.get(p) == CharacterType.MIKIKO;
        int rgb = ClientCharacterData.get(p).getColor();
        Vector3f c = mikiko ? new Vector3f(1F, 0.4F, 0.15F) : new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        var r = p.getRandom();
        double a = r.nextDouble() * Math.PI * 2, rad = 0.7 + r.nextDouble() * 0.4;
        mc.level.addParticle(new DustParticleOptions(c, 0.9F), p.getX() + Math.cos(a) * rad, p.getY() + r.nextDouble() * 1.6 + (mikiko ? 0 : 0.6),
                p.getZ() + Math.sin(a) * rad, 0, 0.03, 0);
    }

    private static boolean rhalix(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == CharacterType.RHALIX;
    }

    /** Wings opened wide (Rhalix floating). */
    public static float wingSpread(Player p) {
        return rhalix(p) ? weight(p) : 0F;
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        float w = weight(p);
        if (w <= 0F || LimbBend.firstPerson) return;
        if (rhalix(p)) meditate(m, p, age, w);
        else if (ClientCharacterData.get((AbstractClientPlayer) p) == CharacterType.MIKIKO) concentrate(m, p, age, w);
    }

    /** Like the art: floating cross-legged, hands together in a triangle in front of his lap, head a little bowed. */
    private static void meditate(HumanoidModel<?> m, Player p, float age, float w) {
        m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, -1.41F);
        m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, 0.32F);
        m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, 0.08F);
        m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -1.41F);
        m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, -0.32F);
        m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, -0.08F);
        LimbBend.extraKnees(p, 1.75F * w, 1.75F * w);                      // shins folded across
        m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.5F);
        m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.35F);
        m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.12F);
        m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -0.5F);
        m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.35F);
        m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.12F);
        LimbBend.extraElbows(p, -0.85F * w, -0.85F * w);
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0F);
        m.head.xRot = Mth.lerp(w, m.head.xRot, 0.15F + Mth.sin(age * 0.05F) * 0.02F);
        m.head.yRot = Mth.lerp(w, m.head.yRot, 0F);
        m.hat.copyFrom(m.head);
    }

    /** Arms folded, then (the longer he keeps at it) a hand up to his temple, concentrating hard. */
    private static void concentrate(HumanoidModel<?> m, Player p, float age, float w) {
        float t = ticks(p);
        float head = Mth.clamp((t - HAND_TO_HEAD) / 14F, 0F, 1F);
        head = head * head * (3 - 2 * head);
        // folded arms
        float fold = w;
        m.leftArm.xRot = Mth.lerp(fold, m.leftArm.xRot, -0.95F);
        m.leftArm.yRot = Mth.lerp(fold, m.leftArm.yRot, 0.6F);
        m.leftArm.zRot = Mth.lerp(fold, m.leftArm.zRot, 0F);
        float rx = Mth.lerp(head, -0.85F, -2.25F), ry = Mth.lerp(head, -0.65F, -0.45F), rz = Mth.lerp(head, 0F, 0.3F);
        m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, rx);
        m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, ry);
        m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, rz);
        LimbBend.extraElbows(p, Mth.lerp(head, -1.45F, -1.9F) * w, -1.45F * w);
        m.head.xRot = Mth.lerp(w, m.head.xRot, 0.2F + 0.15F * head);
        m.head.yRot = Mth.lerp(w, m.head.yRot, -0.1F * head);
        m.hat.copyFrom(m.head);
    }

    /** Rhalix rises off the ground as he settles into it, gently bobbing. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer p) || !rhalix(p)
                || FirstPersonHead.ownFirstPerson(p)) return;
        float w = weight(p);
        if (w <= 0F) return;
        var pose = event.getPoseStack();
        PoseGuard.push(pose);
        LIFTED.add(p.getId());
        float age = p.tickCount + event.getPartialTick();
        pose.translate(0, (0.55F + Mth.sin(age * 0.08F) * 0.06F) * w, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (LIFTED.remove(event.getEntity().getId())) PoseGuard.pop(event.getPoseStack());
    }
}

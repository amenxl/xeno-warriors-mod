package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.mikiko.Hekublade;
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
 * Standing around in the rain (no hood up), now and then:
 * <ul>
 * <li>Mikiko looks up into it, squinting, and holds the Hekublade (or his hand) over his eyes to keep the rain out;</li>
 * <li>Rhalix ducks down and folds his wings around himself as a shelter.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RainIdle {
    private static final int START_AFTER = 80, CYCLE = 420, LENGTH = 110;
    private static final Map<Integer, int[]> STILL = new HashMap<>();   // id -> {ticks standing in the rain}

    private RainIdle() {}

    private static boolean wanted(AbstractClientPlayer p) {
        CharacterType type = ClientCharacterData.get(p);
        if (type != CharacterType.MIKIKO && type != CharacterType.RHALIX) return false;
        if (!p.level().isRainingAt(p.blockPosition().above()) || p.isUnderWater() || p.isPassenger() || p.isSleeping()) return false;
        if (com.xenowarriors.client.mikiko.HoodClient.wearingHoodie(p)) return false;     // hood's got it covered
        return Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && p.onGround() && !p.swinging && !p.isUsingItem()
                && p.hurtTime == 0 && !p.isCrouching();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STILL.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!wanted(p)) { STILL.remove(p.getId()); continue; }
            STILL.computeIfAbsent(p.getId(), k -> new int[1])[0]++;
        }
    }

    /** 0..1 how far into the rain idle they are right now. */
    public static float weight(Player p) {
        int[] s = STILL.get(p.getId());
        if (s == null || s[0] < START_AFTER) return 0F;
        float t = (s[0] - START_AFTER + Minecraft.getInstance().getFrameTime() + p.getId() * 31) % CYCLE;
        if (t >= LENGTH) return 0F;
        float in = Mth.clamp(t / 10F, 0F, 1F), out = Mth.clamp((LENGTH - t) / 10F, 0F, 1F);
        float w = Math.min(in, out);
        return w * w * (3 - 2 * w);
    }

    public static boolean active(Player p) {
        return weight(p) > 0F;
    }

    /** Rhalix's wings folded forward around him as a shelter (0..1). */
    public static float shelter(Player p) {
        return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == CharacterType.RHALIX ? weight(p) : 0F;
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        float w = weight(p);
        if (w <= 0F || LimbBend.firstPerson || !(p instanceof AbstractClientPlayer cp)) return;
        if (ClientCharacterData.get(cp) == CharacterType.MIKIKO) {
            // looking up into the rain, blade (or hand) held flat over his eyes like a visor
            boolean blade = Hekublade.isBlade(p.getMainHandItem());
            float sway = Mth.sin(age * 0.07F) * 0.03F;
            m.head.xRot = Mth.lerp(w, m.head.xRot, -0.55F + sway);
            m.head.yRot = Mth.lerp(w, m.head.yRot, 0F);
            m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, blade ? -2.55F : -2.35F);
            m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, blade ? 0.55F : 0.4F);
            m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, blade ? -0.15F : 0F);
            LimbBend.extraElbows(p, (blade ? -0.9F : -1.6F) * w, 0F);
            m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.05F);
            m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.08F);
        } else {
            // ducking down, arms hugged in, wings wrapped around him (see shelter)
            float sink = 2.2F * w;
            m.body.xRot = Mth.lerp(w, m.body.xRot, 0.45F);
            m.head.xRot = Mth.lerp(w, m.head.xRot, 0.45F);
            m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -0.95F);
            m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.55F);
            m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -0.95F);
            m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.55F);
            LimbBend.extraElbows(p, -1.3F * w, -1.3F * w);
            m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, -0.35F);
            m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -0.35F);
            LimbBend.extraKnees(p, 0.75F * w, 0.75F * w);
            m.rightLeg.y += sink * 0.5F;
            m.leftLeg.y += sink * 0.5F;
            m.body.y += sink;
            m.head.y += sink;
            m.rightArm.y += sink;
            m.leftArm.y += sink;
            m.rightLeg.z += 1.5F * w;
            m.leftLeg.z += 1.5F * w;
        }
        m.hat.copyFrom(m.head);
    }

    /** Mikiko squinting up into the rain (blinking the drops away); Rhalix grumbling under his wings. */
    public static String face(Player p) {
        if (weight(p) < 0.4F || !(p instanceof AbstractClientPlayer cp)) return null;
        if (ClientCharacterData.get(cp) == CharacterType.RHALIX) return "annoyed";
        int t = (p.tickCount + p.getId() * 7) % 50;
        return t < 4 ? "blink" : t < 30 ? "look_up" : "annoyed";
    }
}

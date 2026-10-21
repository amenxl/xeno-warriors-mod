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
 * A proper jump for Mikiko and Rhalix: a quick dip and arm swing as they push off, one knee driven up and
 * arms up while rising, arms out for balance as they come down, and a little crouch to soak up the landing.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class JumpAnimations {
    private static final int LAND_TIME = 6;

    private static final class State {
        boolean wasOnGround = true, jumping;
        int jumpTick = -100, landTick = -100, count;
        float vy, vyO;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();

    private JumpAnimations() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (ClientCharacterData.get(p) == CharacterType.NONE) continue;
            State s = STATES.computeIfAbsent(p.getId(), k -> new State());
            s.vyO = s.vy;
            s.vy = (float) (p.getY() - p.yo);
            boolean ground = p.onGround();
            if (!ground && s.wasOnGround && s.vy > 0.15F && !p.isInWater() && !p.isPassenger()) {
                s.jumping = true;
                s.jumpTick = p.tickCount;
                s.count++;
            }
            if (ground && !s.wasOnGround && s.jumping) {
                s.jumping = false;
                s.landTick = p.tickCount;
            }
            if (p.isInWater() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger()) s.jumping = false;
            s.wasOnGround = ground;
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (age == 0F) return;
        State s = STATES.get(p.getId());
        if (s == null || p.isSleeping() || p.isSwimming() || p.isFallFlying() || p.getAbilities().flying || FlightTracker.isFlying((AbstractClientPlayer) p)) return;
        if (ClientCharacterData.get(p) == CharacterType.MIKIKO && com.xenowarriors.client.mikiko.MikikoAnimations.busyInAir(p, age)) return;
        if (ClientSkillState.currentAnim(p) != null) return;
        float partial = Mth.clamp(age - p.tickCount, 0F, 1F);
        boolean usingHand = p.swinging || p.isUsingItem();
        float side = s.count % 2 == 0 ? 1F : -1F;      // alternate which knee leads

        if (s.jumping) {
            float t = age - s.jumpTick;
            float vy = Mth.lerp(partial, s.vyO, s.vy);
            float rise = Mth.clamp(vy / 0.35F, 0F, 1F);
            float fall = Mth.clamp(-vy / 0.5F, 0F, 1F);
            float in = smooth(t / 3F);
            // push off: arms swing up hard
            float push = smooth(t / 2F) * (1F - smooth((t - 4F) / 4F));
            // rising: knee up, arms up and slightly out
            float up = in * Math.max(rise, 0.35F * (1F - fall));
            ModelPart lead = side > 0 ? m.rightLeg : m.leftLeg, trail = side > 0 ? m.leftLeg : m.rightLeg;
            set(lead, -0.85F, 0F, 0F, up);
            set(trail, 0.35F, 0F, 0F, up);
            if (!usingHand) {
                set(m.rightArm, -1.9F - 0.6F * push, 0F, 0.35F, Math.max(up, push));
                set(m.leftArm, -1.9F - 0.6F * push, 0F, -0.35F, Math.max(up, push));
            }
            m.head.xRot -= 0.2F * up;
            // coming down: arms out to the sides for balance, legs reaching for the ground
            float down = in * fall;
            set(lead, -0.25F, 0F, 0.04F * side, down);
            set(trail, 0.1F, 0F, -0.04F * side, down);
            if (!usingHand) {
                set(m.rightArm, -0.45F, 0F, 1.05F, down);
                set(m.leftArm, -0.45F, 0F, -1.05F, down);
            }
            m.head.xRot += 0.25F * down;       // looking at where he'll land
        }

        float land = age - s.landTick;
        if (land >= 0F && land <= LAND_TIME) {
            // soak up the landing: a quick crouch, arms forward
            float w = Mth.sin(land / LAND_TIME * Mth.PI);
            m.body.xRot = Mth.lerp(w, m.body.xRot, 0.42F);
            m.body.y = Mth.lerp(w, m.body.y, 3F);
            m.head.y = Mth.lerp(w, m.head.y, 4F);
            m.rightArm.y = Mth.lerp(w, m.rightArm.y, 5F);
            m.leftArm.y = Mth.lerp(w, m.leftArm.y, 5F);
            m.rightLeg.z = Mth.lerp(w, m.rightLeg.z, 3.6F);
            m.leftLeg.z = Mth.lerp(w, m.leftLeg.z, 3.6F);
            m.rightLeg.y = Mth.lerp(w, m.rightLeg.y, 12.2F);
            m.leftLeg.y = Mth.lerp(w, m.leftLeg.y, 12.2F);
            if (!usingHand) {
                set(m.rightArm, -0.6F, 0F, 0.2F, w);
                set(m.leftArm, -0.6F, 0F, -0.2F, w);
            }
        }
    }
}

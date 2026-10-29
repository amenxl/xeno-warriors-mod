package com.xenowarriors.client.kaeru;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.network.DoubleJumpPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Kaeru's moves: a double jump off a burst of sound (like Mikiko's wind jump, with her own flair), and the tuck-and-flip
 * pose while she's in it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KaeruClient {
    private KaeruClient() {}

    private static final Map<Integer, int[]> JUMP = new HashMap<>();     // entity id -> {start tick, air ticks, jumped}
    private static boolean lastJumpDown;
    private static final int FLIP_TICKS = 14;

    private static boolean kaeru(Player p) { return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == CharacterType.KAERU; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (event.phase != TickEvent.Phase.END || p == null || mc.level == null || mc.isPaused()) return;
        if (!kaeru(p)) { JUMP.remove(p.getId()); lastJumpDown = mc.options.keyJump.isDown(); return; }
        int[] j = JUMP.computeIfAbsent(p.getId(), k -> new int[]{-100, 0, 0});
        j[1] = p.onGround() ? 0 : j[1] + 1;
        if (p.onGround() || p.isInWater() || p.onClimbable()) j[2] = 0;
        boolean down = mc.options.keyJump.isDown();
        boolean pressed = down && !lastJumpDown;
        lastJumpDown = down;
        if (!pressed || j[2] != 0 || j[1] < 2 || p.onGround() || p.getAbilities().flying || p.getAbilities().mayfly
                || p.isFallFlying() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()
                || com.xenowarriors.client.UltimateCutscene.active() || mc.screen != null) return;
        j[2] = 1;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 v = p.getDeltaMovement();
        double fwd = p.input.forwardImpulse > 0 ? 0.3 : 0.06;
        double up = 0.6 + com.xenowarriors.client.LevelHud.speed() * 0.007;
        p.setDeltaMovement(v.x * 0.5 - Mth.sin(yaw) * fwd, up, v.z * 0.5 + Mth.cos(yaw) * fwd);
        p.fallDistance = 0F;
        ModNetwork.sendToServer(new DoubleJumpPacket());
        onDoubleJump(p.getId());
    }

    /** The sound-burst jump: a ring of notes and a pop (for everyone who sees her). */
    public static void onDoubleJump(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer p)) return;
        JUMP.computeIfAbsent(entityId, k -> new int[]{-100, 0, 0})[0] = p.tickCount;
        for (int i = 0; i < 12; i++) {
            double a = i / 12.0 * Math.PI * 2;
            mc.level.addParticle(ParticleTypes.NOTE, p.getX() + Math.cos(a) * 0.7, p.getY() + 0.1, p.getZ() + Math.sin(a) * 0.7, Math.random(), 0, 0);
        }
        mc.level.addParticle(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 0.2, p.getZ(), 0, 0, 0);
        mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.5F, false);
    }

    /** 0..1 through her flip, or -1. */
    private static float flip(Player p, float age) {
        int[] j = JUMP.get(p.getId());
        if (j == null) return -1F;
        float t = age - j[0];
        return t >= 0 && t < FLIP_TICKS ? t / FLIP_TICKS : -1F;
    }

    /** The pose during the jump: knees tucked, arms swept up, then kicking out as she comes round. */
    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!kaeru(p)) return;
        float f = flip(p, age);
        if (f < 0F) return;
        float k = Mth.sin(f * Mth.PI);
        m.rightLeg.xRot = -1.7F * k; m.leftLeg.xRot = -1.4F * k + 0.3F * f;
        m.rightArm.xRot = -2.6F * k; m.leftArm.xRot = -2.6F * k;
        m.rightArm.zRot = 0.35F * k; m.leftArm.zRot = -0.35F * k;
        m.body.xRot = 0.5F * k;
        m.head.xRot = -0.3F * k;
        m.hat.copyFrom(m.head);
    }
}

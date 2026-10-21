package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Climbing ladders and vines hand over hand (Mikiko and Rhalix): arms reaching up one after the other, knees stepping,
 * in step with how far they've climbed. Mikiko also builds up speed the longer he keeps climbing.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClimbPose {
    /** Climbing speed: vanilla 0.2 blocks/tick; Mikiko ramps up to 0.55 over about 3 seconds. */
    public static final double BASE = 0.2, MAX = 0.55;
    public static final int RAMP_TICKS = 60;
    private static int climbTicks;

    private ClimbPose() {}

    private static boolean climbing(Player p) {
        return p.onClimbable() && !p.onGround() && !p.getAbilities().flying && !p.isSwimming();
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!(p instanceof AbstractClientPlayer cp) || ClientCharacterData.get(cp) == CharacterType.NONE || !climbing(p)) return;
        // step in time with height climbed (one full hand-over-hand cycle per block)
        float y = (float) Mth.lerp(Minecraft.getInstance().getFrameTime(), p.yo, p.getY());
        float phase = y * (float) Math.PI * 2F;
        float moving = Math.abs(p.getY() - p.yo) > 0.01 ? 1F : 0F;
        float s = Mth.sin(phase) * moving;
        m.rightArm.xRot = -2.55F + s * 0.45F;
        m.leftArm.xRot = -2.55F - s * 0.45F;
        m.rightArm.yRot = 0F;
        m.leftArm.yRot = 0F;
        m.rightArm.zRot = 0.12F;
        m.leftArm.zRot = -0.12F;
        m.rightLeg.xRot = -0.55F * Math.max(0F, -s) - 0.1F;
        m.leftLeg.xRot = -0.55F * Math.max(0F, s) - 0.1F;
        m.rightLeg.zRot = 0F;
        m.leftLeg.zRot = 0F;
        m.body.xRot = 0F;
        m.head.xRot = Math.min(m.head.xRot, -0.2F);     // looking up the ladder
        m.hat.copyFrom(m.head);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (event.phase != TickEvent.Phase.END || p == null || mc.isPaused()) return;
        boolean up = climbing(p) && ClientCharacterData.getLocal() == CharacterType.MIKIKO && p.getDeltaMovement().y > 0.1
                && (p.horizontalCollision || p.input.jumping) && !p.isShiftKeyDown();
        if (!up) {
            climbTicks = 0;
            return;
        }
        climbTicks++;
        double speed = BASE + (MAX - BASE) * Math.min(1.0, climbTicks / (double) RAMP_TICKS);
        Vec3 v = p.getDeltaMovement();
        if (v.y < speed) p.setDeltaMovement(v.x, speed, v.z);
    }
}

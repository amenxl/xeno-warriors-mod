package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Garry's idle: standing around, he pulls out his mic and beatboxes (kick, snare, hats and a bass line).
 * Stevie, Mikiko and Kaeru standing near him dance to it, old-school (running man, cabbage patch, the robot).
 * Some sessions Stevie pulls out his own mic and copies him instead - Garry catches him, stops, and the two square
 * up like they're about to fight... then let it go, and Garry starts again.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GarryBeatbox {
    private static final int AFTER = 140, MIC = 12, RANGE = 8;
    /** Copy sessions: Stevie copies from COPY, Garry notices at CAUGHT, they square up until CALM, back to it at RESUME. */
    private static final int COPY = 90, CAUGHT = 170, CALM = 270, RESUME = 310;
    private static final Map<Integer, int[]> STILL = new HashMap<>();   // id -> {ticks still, session seed}

    private GarryBeatbox() {}

    private static CharacterType type(Player p) {
        return p instanceof AbstractClientPlayer a ? ClientCharacterData.get(a) : CharacterType.NONE;
    }

    private static boolean calm(AbstractClientPlayer p) {
        return Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && !Busy.busy(p)
                && !p.isCrouching() && !p.isPassenger() && !p.isUsingItem() && !FlightTracker.isFlying(p) && !p.isSleeping()
                && p.getMainHandItem().isEmpty() && p.hurtTime == 0;
    }

    /** Ticks into Garry's beatbox (-1 if he isn't). */
    private static float time(Player garry, float partial) {
        int[] s = STILL.get(garry.getId());
        if (s == null || s[0] < AFTER) return -1F;
        return s[0] - AFTER + partial;
    }

    /** Does this session have Stevie copying (and is a still Stevie close by)? */
    private static AbstractClientPlayer copier(Player garry) {
        int[] s = STILL.get(garry.getId());
        if (s == null || s[1] % 3 != 0) return null;                      // about one session in three
        AbstractClientPlayer best = null;
        for (AbstractClientPlayer o : Minecraft.getInstance().level.players())
            if (o != garry && type(o) == CharacterType.STEVIE && o.distanceToSqr(garry) < RANGE * RANGE && calmish(o)) best = o;
        return best;
    }

    private static boolean calmish(AbstractClientPlayer p) {
        int[] s = STILL.get(p.getId());
        return s != null && s[0] > 20;
    }

    /** The beatboxing Garry this player is reacting to (dancing / copying), or null. */
    private static AbstractClientPlayer garryNear(Player p) {
        if (Minecraft.getInstance().level == null) return null;
        for (AbstractClientPlayer o : Minecraft.getInstance().level.players())
            if (o != p && type(o) == CharacterType.GARRY && o.distanceToSqr(p) < RANGE * RANGE && time(o, 0F) >= MIC) return o;
        return null;
    }

    /** Is the beat going right now (not while they're squaring up)? */
    private static boolean beating(Player garry, float t) {
        if (t < MIC) return false;
        return copier(garry) == null || t < CAUGHT || t >= RESUME;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STILL.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            CharacterType t = type(p);
            if (t != CharacterType.GARRY && t != CharacterType.STEVIE && t != CharacterType.MIKIKO && t != CharacterType.KAERU) { STILL.remove(p.getId()); continue; }
            int[] s = STILL.computeIfAbsent(p.getId(), k -> new int[2]);
            if (calm(p)) s[0]++;
            else { if (s[0] > 0) s[1] = (int) ((mc.level.getGameTime() / 20) & 0xFFFF); s[0] = 0; }
            if (t != CharacterType.GARRY) continue;
            float bt = time(p, 0F);
            if (bt < 0) continue;
            if (beating(p, bt)) beat(mc, p, (int) bt);
            if (copier(p) != null && (int) bt == CAUGHT)
                mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 0.7F, false);
            if (copier(p) != null && bt >= CAUGHT && bt < CALM && p.getRandom().nextInt(6) == 0)
                mc.level.addParticle(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 2.2, p.getZ(), 0, 0, 0);
        }
    }

    // 16 steps, one every 3 ticks (about 100 BPM): K kick, S snare, H hat, B bass, - rest
    private static final String PATTERN = "K-H-S-HKK-HBS-H-";

    private static void beat(Minecraft mc, AbstractClientPlayer g, int t) {
        if (t % 3 != 0) return;
        int step = (t / 3) % 16;
        char c = PATTERN.charAt(step);
        SoundEvent s; float pitch = 1F, vol = 0.55F;
        switch (c) {
            case 'K' -> { s = SoundEvents.NOTE_BLOCK_BASEDRUM.value(); pitch = 0.8F; vol = 0.8F; }
            case 'S' -> { s = SoundEvents.NOTE_BLOCK_SNARE.value(); pitch = 1.1F; }
            case 'H' -> { s = SoundEvents.NOTE_BLOCK_HAT.value(); pitch = 1.6F; vol = 0.35F; }
            case 'B' -> { s = SoundEvents.NOTE_BLOCK_BASS.value(); pitch = ((t / 48) % 2 == 0) ? 0.6F : 0.75F; vol = 0.7F; }
            default -> { return; }
        }
        mc.level.playLocalSound(g.getX(), g.getEyeY(), g.getZ(), s, SoundSource.PLAYERS, vol, pitch, false);
        if (c == 'K' && g.getRandom().nextInt(3) == 0)
            mc.level.addParticle(ParticleTypes.NOTE, g.getX(), g.getY() + 2.3, g.getZ(), g.getRandom().nextDouble(), 0, 0);
    }

    // ------------------------------------------------------------------ poses

    private static float sm(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    /** Head turn (radians) for looking at someone else. */
    private static float lookAt(Player p, Player o) {
        double dx = o.getX() - p.getX(), dz = o.getZ() - p.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        return Mth.clamp(Mth.wrapDegrees(yaw - p.yBodyRot), -70F, 70F) * Mth.DEG_TO_RAD;
    }

    /** Mic up at the mouth, other hand cupped round it, bobbing to the beat. */
    private static void beatboxPose(HumanoidModel<?> m, Player p, float t, float w, float age) {
        float bob = Mth.sin(t * (float) Math.PI / 6F);                      // two bobs per bar
        set(m.rightArm, -1.95F, -0.5F, 0.15F, w);
        set(m.leftArm, -1.7F, 0.6F, -0.1F, w * 0.8F);
        LimbBend.extraElbows(p, 1.85F * w, 1.6F * w * 0.8F);
        m.head.xRot = Mth.lerp(w, m.head.xRot, 0.12F + 0.12F * Math.abs(bob));
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0.05F * Math.abs(bob));
        LimbBend.extraKnees(p, 0.18F * w * Math.abs(bob), 0.18F * w * Math.abs(bob));
    }

    /** Squaring up: chest out, fists up and shaking, glaring at the other one. */
    private static void faceOff(HumanoidModel<?> m, Player p, Player other, float w, float age) {
        float shake = Mth.sin(age * 2.6F) * 0.05F;
        set(m.rightArm, -0.6F + shake, 0.1F, 0.35F, w);
        set(m.leftArm, -0.6F - shake, -0.1F, -0.35F, w);
        LimbBend.extraElbows(p, 1.4F * w, 1.4F * w);
        m.body.xRot = Mth.lerp(w, m.body.xRot, -0.12F);
        if (other != null) m.head.yRot = Mth.lerp(w, m.head.yRot, lookAt(p, other));
        m.head.xRot = Mth.lerp(w, m.head.xRot, -0.1F);
        LimbBend.extraKnees(p, 0.2F * w, 0.2F * w);
    }

    /** Old-school dancing on Garry's beat: each dancer has their move. */
    private static void dance(HumanoidModel<?> m, Player p, float beatT, float w) {
        float a = beatT * (float) Math.PI / 6F;                            // in time with the bar
        int move = Math.floorMod(p.getId() + (int) (beatT / 192), 3);       // changes every few bars
        switch (move) {
            case 0 -> {                                                   // the running man
                float s = Mth.sin(a);
                set(m.rightLeg, -0.6F * s, 0F, 0F, w); set(m.leftLeg, 0.6F * s, 0F, 0F, w);
                set(m.rightArm, -0.9F + 0.6F * s, 0F, 0.1F, w); set(m.leftArm, -0.9F - 0.6F * s, 0F, -0.1F, w);
                LimbBend.extraElbows(p, 1.2F * w, 1.2F * w);
                LimbBend.extraKnees(p, Math.max(0F, s) * 0.9F * w, Math.max(0F, -s) * 0.9F * w);
            }
            case 1 -> {                                                   // the cabbage patch: fists circling in front
                set(m.rightArm, -1.3F + 0.35F * Mth.sin(a), -0.3F, 0.35F * Mth.cos(a), w);
                set(m.leftArm, -1.3F + 0.35F * Mth.sin(a), 0.3F, 0.35F * Mth.cos(a), w);
                LimbBend.extraElbows(p, 1.5F * w, 1.5F * w);
                m.body.yRot = Mth.lerp(w, m.body.yRot, 0.25F * Mth.sin(a));
                LimbBend.extraKnees(p, 0.3F * w * Math.abs(Mth.cos(a)), 0.3F * w * Math.abs(Mth.cos(a)));
            }
            default -> {                                                  // the robot: stiff, snapping between poses
                int k = Math.floorMod((int) (beatT / 6F), 4);
                float[][] P = {{-1.57F, 0F, 0F, 0F}, {0F, 0F, -1.57F, 0F}, {-1.57F, 1.57F, 0F, 0F}, {0F, 0F, 0F, 1.57F}};
                set(m.rightArm, P[k][0], 0F, 0F, w); set(m.leftArm, P[k][2], 0F, 0F, w);
                LimbBend.extraElbows(p, P[k][1] * w, P[k][3] * w);
                m.head.yRot = Mth.lerp(w, m.head.yRot, (k % 2 == 0 ? 0.5F : -0.5F));
            }
        }
        m.hat.copyFrom(m.head);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        CharacterType ct = type(p);
        float pt = Minecraft.getInstance().getFrameTime();
        if (ct == CharacterType.GARRY) {
            float t = time(p, pt);
            if (t < 0F) return;
            float w = sm(t / MIC);
            AbstractClientPlayer stevie = copier(p);
            if (stevie != null && t >= CAUGHT && t < RESUME) {
                float f = sm((t - CAUGHT) / 6F) * (1F - sm((t - CALM) / 20F));
                beatboxPose(m, p, t, 1F - sm((t - CAUGHT) / 6F) + sm((t - CALM) / 30F) * 0.0F, age);
                faceOff(m, p, stevie, f, age);
                if (t >= CALM) {                                           // letting it go: a shrug
                    float sh = sm((t - CALM) / 10F) * (1F - sm((t - CALM - 25F) / 10F));
                    set(m.rightArm, -0.3F, 0F, 0.6F, sh); set(m.leftArm, -0.3F, 0F, -0.6F, sh);
                }
            } else beatboxPose(m, p, t, w, age);
            m.hat.copyFrom(m.head);
            return;
        }
        if (ct != CharacterType.STEVIE && ct != CharacterType.MIKIKO && ct != CharacterType.KAERU) return;
        if (!(p instanceof AbstractClientPlayer ap) || !calmish(ap)) return;
        AbstractClientPlayer g = garryNear(p);
        if (g == null) return;
        float t = time(g, pt);
        float w = sm((t - MIC) / 10F);
        if (ct == CharacterType.STEVIE && copier(g) == p) {
            if (t >= COPY && t < CAUGHT) { beatboxPose(m, p, t, sm((t - COPY) / MIC), age); m.hat.copyFrom(m.head); return; }
            if (t >= CAUGHT && t < RESUME) {
                float f = sm((t - CAUGHT) / 6F) * (1F - sm((t - CALM) / 20F));
                faceOff(m, p, g, f, age);
                m.hat.copyFrom(m.head);
                return;
            }
        }
        if (!beating(g, t)) return;
        dance(m, p, t, w);
    }

    /** Is this player holding a mic up right now (Garry beatboxing, or Stevie copying him)? */
    public static boolean mic(Player p, float partial) {
        CharacterType ct = type(p);
        if (ct == CharacterType.GARRY) {
            float t = time(p, partial);
            if (t < 4F) return false;
            AbstractClientPlayer s = copier(p);
            return s == null || t < CAUGHT + 4 || t >= CALM + 30;
        }
        if (ct == CharacterType.STEVIE) {
            AbstractClientPlayer g = garryNear(p);
            if (g == null || copier(g) != p) return false;
            float t = time(g, partial);
            return t >= COPY + 4 && t < CAUGHT + 4;
        }
        return false;
    }

    /** Draws the mic in his right hand (model space, after the player model). */
    public static void render(Player p, HumanoidModel<?> parent, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
        if (!mic(p, partial)) return;
        pose.pushPose();
        parent.translateToHand(net.minecraft.world.entity.HumanoidArm.RIGHT, pose);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180F));
        pose.translate(-1F / 16F, 0.125F, -0.625F);
        Minecraft.getInstance().getItemRenderer().renderStatic(p, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.MICROPHONE.get()),
                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, p.level(), light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, p.getId());
        pose.popPose();
    }
}

package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.mikiko.GeoAnims;
import com.xenowarriors.client.render.DetailModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rhalix's wings on the client: out or put away, the take-out animation (a big stretch, then the wings
 * burst out, spread wide and give one big beat), folding them away, resting sway on the ground, deep
 * slow beats while hovering and swept-back beats in fast flight - plus his hovering body pose.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixWingsClient {
    public static final float OUT_LEN = 50F, IN_LEN = 24F;
    private static final DustParticleOptions FEATHER = new DustParticleOptions(new Vector3f(0.32F, 0.16F, 0.45F), 0.8F);
    private static final DustParticleOptions FEATHER_LIGHT = new DustParticleOptions(new Vector3f(0.85F, 0.8F, 0.95F), 0.6F);

    private record State(boolean out, long changed, boolean animate) {}

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<Integer, float[]> HOVER = new HashMap<>();   // id -> {now, before}
    /** Dancing to music: id -> {gravity dance, before, sad vibe, before, start tick, still ticks, smoke, before, smoke start}. */
    private static final Map<Integer, float[]> DANCE = new HashMap<>();

    public static float gravityDance(Player p, float partial) {
        float[] d = DANCE.get(p.getId());
        return d == null ? 0F : Mth.lerp(partial, d[1], d[0]);
    }

    public static float sadVibe(Player p, float partial) {
        float[] d = DANCE.get(p.getId());
        return d == null ? 0F : Mth.lerp(partial, d[3], d[2]);
    }

    public static float chill(Player p, float partial) {
        float[] d = DANCE.get(p.getId());
        return d == null || d.length < 15 ? 0F : Mth.lerp(partial, d[13], d[12]);
    }

    public static float hyped(Player p, float partial) {
        float[] d = DANCE.get(p.getId());
        return d == null || d.length < 12 ? 0F : Mth.lerp(partial, d[10], d[9]);
    }

    private static float hypeTime(Player p, float age) {
        float[] d = DANCE.get(p.getId());
        return d == null || d.length < 12 ? 0F : age - d[11];
    }

    public static float smoking(Player p, float partial) {
        float[] d = DANCE.get(p.getId());
        return d == null ? 0F : Mth.lerp(partial, d[7], d[6]);
    }

    /** Where in the 6-second smoke loop he is (ticks), or -1. */
    public static float smokeTime(Player p, float age) {
        float[] d = DANCE.get(p.getId());
        return d == null || d[6] <= 0F ? -1F : (age - d[8]) % 120F;
    }

    /** A thin wisp off the tip, and a long breath of smoke after each drag. */
    private static void smokeParticles(Minecraft mc, Player p, float t) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x).scale(-1);
        boolean drag = t >= 32 && t < 52;
        Vec3 mouth = p.position().add(0, 1.5, 0).add(fwd.scale(0.3));
        Vec3 tip = drag ? mouth.add(fwd.scale(0.15)).add(right.scale(0.05))
                : p.position().add(0, 0.95, 0).add(fwd.scale(0.3)).add(right.scale(0.38));
        if (p.tickCount % 6 == 0) mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, tip.x, tip.y + 0.05, tip.z, 0, 0.015, 0);
        if (t >= 56 && t < 84 && p.tickCount % 2 == 0) {
            Vec3 v = fwd.scale(0.04).add(0, 0.02, 0);
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, mouth.x, mouth.y, mouth.z, v.x * 0.5, 0.008, v.z * 0.5);
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, v.x, v.y, v.z);
        }
    }

    public static float gravityTime(Player p, float age) {
        return danceTime(p, age);
    }

    private static final Map<Integer, float[]> SUPER = new HashMap<>();      // id -> {now, before}

    /** 0..1: flying forward fast enough for the Superman pose. */
    public static float superman(Player p, float partial) {
        float[] s = SUPER.get(p.getId());
        return s == null ? 0F : Mth.lerp(partial, s[1], s[0]);
    }

    private static void lerp(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    private static float danceTime(Player p, float age) {
        float[] d = DANCE.get(p.getId());
        return d == null ? 0F : age - d[4];
    }

    private RhalixWingsClient() {}

    public static void set(UUID player, boolean out, boolean animate) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        STATES.put(player, new State(out, now, animate));
    }

    private static float time(State s, float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 999F : mc.level.getGameTime() - s.changed + partial;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /** 0..1: how far out the wings are right now. */
    public static float grow(Player p, float partial) {
        State s = STATES.get(p.getUUID());
        if (s == null) return 1F;
        float t = time(s, partial);
        if (!s.animate) return s.out ? 1F : 0F;
        if (s.out) {
            // burst out after the stretch, overshooting a little
            float g = smooth((t - 14F) / 12F);
            return g * (1F + 0.12F * Mth.sin(Mth.clamp((t - 14F) / 20F, 0F, 1F) * Mth.PI));
        }
        return 1F - smooth((t - 4F) / 14F);
    }

    /** Called by the detail layer each frame to pose the wings. */
    public static void pose(DetailModel model, AbstractClientPlayer p, boolean preview, boolean flying, boolean falling,
                            boolean kishi, float skillSpread, float age) {
        float partial = Mth.clamp(age - p.tickCount, 0F, 1F);
        float grow = preview || skillSpread > 0F || kishi ? Math.max(grow(p, partial), skillSpread > 0F || kishi ? 1F : 0F)
                : grow(p, partial);
        if (LunarClient.active(p.getId()) && ClientCharacterData.outfit(p) == 0) grow = 1F;     // the crescent moons are always out
        State s = STATES.get(p.getUUID());
        float t = s == null ? 999F : time(s, partial);
        boolean animOut = s != null && s.animate && s.out && t < OUT_LEN;
        boolean animIn = s != null && s.animate && !s.out && t < IN_LEN;

        float flap, sweep, tuck, spread = skillSpread;
        float hover = hover(p, partial);
        if (flying) {
            // hovering: deep, slow, powerful beats; moving: quicker beats with the wings swept back
            float speed = Mth.lerp(hover, 0.36F, 0.2F);
            float amp = Mth.lerp(hover, 0.24F, 0.46F);
            float phase = age * speed;
            // sharper downstroke than upstroke
            float beat = Mth.sin(phase) + 0.25F * Mth.sin(phase * 2F);
            flap = beat * amp;
            sweep = Mth.cos(phase) * 0.12F * hover;
            tuck = Mth.lerp(hover, 0.35F, -0.15F);
        } else if (falling) {
            // gliding down: held open with a slow wobble
            flap = 0.1F + Mth.sin(age * 0.2F) * 0.05F;
            sweep = 0F;
            tuck = -0.2F;
            spread = Math.max(spread, 0.45F);
        } else {
            // resting on the ground: folded back, breathing sway, and a little ruffle every so often
            flap = Mth.sin(age * 0.08F) * 0.04F;
            sweep = 0F;
            tuck = 0.3F;
            float ruffle = (age + p.getId() * 13) % 240F;
            if (ruffle < 20F) {
                float r = Mth.sin(ruffle / 20F * Mth.PI);
                flap += Mth.sin(ruffle * 1.2F) * 0.22F * r;
                tuck -= 0.3F * r;
            }
        }
        // dancing: wings opening and beating along with the music
        float dance = gravityDance(p, partial);
        if (dance > 0F) {
            spread = Math.max(spread, dance * (0.35F + 0.3F * Mth.sin(age * 0.2F)));
            flap += Mth.sin(age * 0.4F) * 0.2F * dance;
        }
        float hypeWing = hyped(p, partial);
        if (hypeWing > 0F) flap += Mth.sin(age * 0.63F) * 0.35F * hypeWing;     // flapping with each jump
        float sadW = sadVibe(p, partial);
        if (sadW > 0F) {
            tuck += 0.25F * sadW;                    // drooping, folded in close
            flap -= 0.08F * sadW;
        }
        if (animOut) {
            // spread wide as they come out, then one big beat, then settle
            float open = smooth((t - 18F) / 8F) * (1F - smooth((t - 38F) / 10F));
            spread = Math.max(spread, open);
            flap += Mth.sin(Mth.clamp((t - 32F) / 14F, 0F, 1F) * Mth.PI * 2F) * 0.4F;
            tuck *= smooth((t - 40F) / 10F);
        } else if (animIn) {
            spread = Math.max(spread, 0.4F * (1F - smooth(t / 10F)));
        }
        // rain shelter: wings swung forward and wrapped around him, tips raised over his head
        float shelter = RainIdle.shelter(p);
        if (shelter > 0F) {
            tuck = Mth.lerp(shelter, tuck, -2.3F);
            flap = Mth.lerp(shelter, flap, 0.3F);
            spread *= 1F - shelter;
        }
        model.poseWings(true, grow, flap, tuck, sweep, Mth.clamp(spread, 0F, 1F));
    }

    // ---------------------------------------------------------------- body

    private static float hover(Player p, float partial) {
        float[] h = HOVER.get(p.getId());
        return h == null ? 0F : Mth.lerp(partial, h[1], h[0]);
    }

    /** Rhalix's body: the stretch when the wings come out, a shrug putting them away, and his hover pose. */
    public static void applyBody(HumanoidModel<?> model, Player p, float age) {
        if (age == 0F || ClientCharacterData.get(p) != CharacterType.RHALIX) return;
        float partial = Mth.clamp(age - p.tickCount, 0F, 1F);
        float hover = hover(p, partial);
        if (hover > 0F) GeoAnims.apply(model, GeoAnims.RHALIX, "hover_idle", age, true, hover);
        float zoom = superman(p, partial);
        if (zoom > 0F) {
            // Superman: both arms straight back along his sides, legs together streaming behind
            float flutter = Mth.sin(age * 0.6F) * 0.06F, sway = Mth.sin(age * 0.45F) * 0.03F;
            lerp(model.rightArm, 0.14F + sway, 0F, 0.1F, zoom);
            lerp(model.leftArm, 0.14F - sway, 0F, -0.1F, zoom);
            lerp(model.rightLeg, 0.08F + flutter, 0F, 0.04F, zoom);
            lerp(model.leftLeg, 0.12F - flutter, 0F, -0.04F, zoom);
            model.head.xRot = Mth.lerp(zoom, model.head.xRot, -1.15F);       // still looking ahead, not at the ground
        }
        float sad = sadVibe(p, partial);
        if (sad > 0F) GeoAnims.apply(model, GeoAnims.RHALIX, "sad_vibe", age, true, sad);
        float dance = gravityDance(p, partial);
        if (dance > 0F) GeoAnims.apply(model, GeoAnims.RHALIX, "gravity_dance", danceTime(p, age), true, dance);
        float chillW = chill(p, partial);
        if (chillW > 0F) {
            float[] d = DANCE.get(p.getId());
            GeoAnims.apply(model, GeoAnims.RHALIX, "chill_vibe", age - d[14], true, chillW);
        }
        float hype = hyped(p, partial);
        if (hype > 0F) GeoAnims.apply(model, GeoAnims.RHALIX, "hype", hypeTime(p, age), true, hype);
        float smoke = smoking(p, partial);
        if (smoke > 0F) GeoAnims.apply(model, GeoAnims.RHALIX, "smoke_break", smokeTime(p, age), true, smoke);
        State s = STATES.get(p.getUUID());
        if (s == null || !s.animate) return;
        float t = time(s, partial);
        String name = s.out ? "wings_out_stretch" : "wings_in";
        float len = GeoAnims.length(GeoAnims.RHALIX, name);
        if (t <= len) GeoAnims.apply(model, GeoAnims.RHALIX, name, t, false, GeoAnims.envelope(t, len, 4F));
    }

    private static final java.util.Set<Integer> LIFTED = new java.util.HashSet<>();

    /** Gravity dance: he floats a little off the ground, bobbing, and every so often spins all the way round. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRenderPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (p instanceof AbstractClientPlayer fp && FirstPersonHead.ownFirstPerson(fp)) return;   // (see MikikoAnimations)
        float w = gravityDance(p, event.getPartialTick());
        float hw = hyped(p, event.getPartialTick());
        float sw = ClientCharacterData.get(p).skylander() ? superman(p, event.getPartialTick()) : 0F;
        if (w <= 0F && hw <= 0F && sw <= 0F) return;
        float age = p.tickCount + event.getPartialTick();
        if (sw > 0F) {
            // whole body tipped forward into the flight, pivoting around his middle
            var sp = event.getPoseStack();
            com.xenowarriors.client.PoseGuard.push(sp);
            LIFTED.add(p.getId());
            float yaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot);
            sp.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
            sp.translate(0, 0.9, 0);
            sp.mulPose(com.mojang.math.Axis.XP.rotationDegrees(72F * sw));
            sp.translate(0, -0.9, 0);
            sp.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            return;
        }
        if (hw > 0F) {
            // jumping on every beat
            var hp = event.getPoseStack();
            com.xenowarriors.client.PoseGuard.push(hp);
            LIFTED.add(p.getId());
            hp.translate(0, DiscTracker.hypeLift(hypeTime(p, age)) * hw, 0);
            return;
        }
        float t = danceTime(p, age) % 160F;
        var pose = event.getPoseStack();
        com.xenowarriors.client.PoseGuard.push(pose);
        LIFTED.add(p.getId());
        pose.translate(0, (0.25F + 0.08F * Mth.sin(age * 0.15F)) * w, 0);
        if (t >= 110F && t < 120F) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(smooth((t - 110F) / 10F) * 360F));
    }

    @SubscribeEvent
    public static void onRenderPost(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
        if (LIFTED.remove(event.getEntity().getId())) com.xenowarriors.client.PoseGuard.pop(event.getPoseStack());
    }

    /** Dancing, vibing or on a smoke break right now (so other idles stay out of the way). */
    public static boolean busy(Player p) {
        float[] d = DANCE.get(p.getId());
        return d != null && (d[0] > 0F || d[2] > 0F || d[6] > 0F || d[9] > 0F || d[12] > 0F);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            HOVER.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!ClientCharacterData.get(p).skylander()) {
                HOVER.remove(p.getId());
                continue;
            }
            // dancing to "Gravity Glitch Duel", sadly vibing to "Lunar Ascendence" (when he's standing about)
            float[] d = DANCE.computeIfAbsent(p.getId(), k -> new float[15]);
            boolean stillNow = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && !Busy.busy(p);
            d[5] = stillNow ? d[5] + 1 : 0;
            boolean standing = d[5] >= 10 && !FlightTracker.isFlying(p) && ClientCharacterData.get(p) == CharacterType.RHALIX;   // (his dances are his own)
            boolean hyped = standing && DiscTracker.near(p, DiscTracker.SKYBOUND);
            if (hyped && d[9] <= 0F) d[11] = p.tickCount;
            d[10] = d[9];
            d[9] += Mth.clamp((hyped ? 1F : 0F) - d[9], -0.15F, 0.15F);
            boolean gravity = standing && !hyped && DiscTracker.near(p, DiscTracker.GRAVITY);
            boolean lunar = standing && !gravity && !hyped && DiscTracker.near(p, DiscTracker.LUNAR);
            boolean smoke = standing && !gravity && !lunar && !hyped && DiscTracker.near(p, DiscTracker.SMOKE);
            if (smoke && d[6] <= 0F) d[8] = p.tickCount;
            d[7] = d[6];
            d[6] += Mth.clamp((smoke ? 1F : 0F) - d[6], -0.08F, 0.08F);
            if (d[6] > 0.5F) smokeParticles(mc, p, (p.tickCount - d[8]) % 120F);
            boolean chill = standing && !gravity && !lunar && !smoke && !hyped && DiscTracker.near(p, DiscTracker.CHILL);
            if (chill && d[12] <= 0F) d[14] = p.tickCount;
            d[13] = d[12];
            d[12] += Mth.clamp((chill ? 1F : 0F) - d[12], -0.1F, 0.1F);
            if (gravity && d[0] <= 0F) d[4] = p.tickCount;
            d[1] = d[0];
            d[0] += Mth.clamp((gravity ? 1F : 0F) - d[0], -0.1F, 0.1F);
            d[3] = d[2];
            d[2] += Mth.clamp((lunar ? 1F : 0F) - d[2], -0.06F, 0.06F);
            // hovering = flying but hardly moving sideways
            boolean flying = FlightTracker.isFlying(p);
            double moved = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo);
            float target = flying && moved < 0.12 ? 1F : 0F;
            float[] sup = SUPER.computeIfAbsent(p.getId(), k -> new float[2]);
            sup[1] = sup[0];
            float want = flying ? Mth.clamp((float) (moved - 0.25) / 0.35F, 0F, 1F) : 0F;
            sup[0] += Mth.clamp(want - sup[0], -0.1F, 0.08F);
            if (sup[0] > 0.6F && moved > 0.9 && p.tickCount % 2 == 0) {
                // wind streaks peeling off him at speed
                float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
                for (int i = -1; i <= 1; i += 2) {
                    Vec3 tip = p.position().add(0, 1.1, 0).add(new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(0.9 * i));
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, tip.x, tip.y, tip.z, 0, 0, 0);
                }
            }
            float[] h = HOVER.computeIfAbsent(p.getId(), k -> new float[2]);
            h[1] = h[0];
            h[0] += Mth.clamp(target - h[0], -0.08F, 0.08F);

            // feathers drifting off while the wings come out or fold away
            State s = STATES.get(p.getUUID());
            if (s != null && s.animate) {
                long t = mc.level.getGameTime() - s.changed;
                boolean burst = s.out ? t >= 14 && t < 34 : t < 16;
                if (burst) {
                    float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
                    Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)).scale(0.35);
                    Vec3 right = new Vec3(-back.z, 0, back.x).normalize();
                    for (int i = 0; i < 3; i++) {
                        double side = (mc.level.random.nextDouble() - 0.5) * 2.4;
                        Vec3 at = p.position().add(0, 1.3 + mc.level.random.nextDouble() * 0.5, 0).add(back).add(right.scale(side));
                        mc.level.addParticle(i == 0 ? FEATHER_LIGHT : FEATHER, at.x, at.y, at.z,
                                back.x * 0.1, -0.02, back.z * 0.1);
                    }
                }
            }
        }
    }
}

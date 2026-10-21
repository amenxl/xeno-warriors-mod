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
        if (animOut) {
            // spread wide as they come out, then one big beat, then settle
            float open = smooth((t - 18F) / 8F) * (1F - smooth((t - 38F) / 10F));
            spread = Math.max(spread, open);
            flap += Mth.sin(Mth.clamp((t - 32F) / 14F, 0F, 1F) * Mth.PI * 2F) * 0.4F;
            tuck *= smooth((t - 40F) / 10F);
        } else if (animIn) {
            spread = Math.max(spread, 0.4F * (1F - smooth(t / 10F)));
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
        State s = STATES.get(p.getUUID());
        if (s == null || !s.animate) return;
        float t = time(s, partial);
        String name = s.out ? "wings_out_stretch" : "wings_in";
        float len = GeoAnims.length(GeoAnims.RHALIX, name);
        if (t <= len) GeoAnims.apply(model, GeoAnims.RHALIX, name, t, false, GeoAnims.envelope(t, len, 4F));
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
            if (ClientCharacterData.get(p) != CharacterType.RHALIX) {
                HOVER.remove(p.getId());
                continue;
            }
            // hovering = flying but hardly moving sideways
            boolean flying = FlightTracker.isFlying(p);
            double moved = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo);
            float target = flying && moved < 0.12 ? 1F : 0F;
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

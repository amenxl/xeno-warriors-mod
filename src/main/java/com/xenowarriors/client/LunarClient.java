package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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

/**
 * Lunar Rhalix on the client: the lunar textures (vivid purple hair with lavender tips, crescents on his cheeks -
 * see CharacterDetailLayer), a crescent moon outline with stars hanging behind his back, and Moon Reflect's mirror.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LunarClient {
    private static final Map<Integer, boolean[]> STATE = new HashMap<>();
    private static final DustParticleOptions RIM = new DustParticleOptions(new Vector3f(0.85F, 0.35F, 0.72F), 0.75F),
            DARK = new DustParticleOptions(new Vector3f(0.08F, 0.05F, 0.12F), 1.1F),
            MIRROR = new DustParticleOptions(new Vector3f(0.85F, 0.9F, 1F), 0.9F);

    private LunarClient() {}

    public static void set(int id, boolean active, boolean reflect) {
        if (!active && !reflect) STATE.remove(id); else STATE.put(id, new boolean[]{active, reflect});
    }

    public static boolean active(int id) { boolean[] s = STATE.get(id); return s != null && s[0]; }

    public static boolean reflecting(int id) { boolean[] s = STATE.get(id); return s != null && s[1]; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused() || STATE.isEmpty()) return;
        for (Player p : mc.level.players()) {
            if (!STATE.containsKey(p.getId()) || p.isInvisible()) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (active(p.getId()) && p.tickCount % 3 == 0) crescent(mc, p);
            if (reflecting(p.getId())) mirror(mc, p);
        }
    }

    /** A crescent outline (rim + dark body) with a few stars, hanging behind his shoulders like in the art. */
    private static void crescent(Minecraft mc, Player p) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 c = p.position().add(0, 1.55, 0).subtract(fwd.scale(0.75));
        double r = 1.25;
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(40 + (i + p.getRandom().nextDouble()) * 260 / 12.0);   // open toward the upper right
            Vec3 pt = c.add(right.scale(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0);
            mc.level.addParticle(RIM, pt.x, pt.y, pt.z, 0, 0, 0);
            double ri = r * (0.55 + 0.25 * Math.abs(Math.sin(a - Math.toRadians(170))));      // inner edge: thick at the bottom
            Vec3 in = c.add(right.scale(Math.cos(a) * ri - 0.25)).add(0, Math.sin(a) * ri - 0.15, 0);
            if (i % 2 == 0) mc.level.addParticle(DARK, in.x, in.y, in.z, 0, 0, 0);
        }
        if (p.tickCount % 12 == 0) {
            for (double[] s : new double[][]{{1.3, 1.2}, {1.5, 0.1}, {-1.3, 0.5}}) {
                Vec3 st = c.add(right.scale(s[0])).add(0, s[1], 0);
                mc.level.addParticle(ParticleTypes.END_ROD, st.x, st.y, st.z, 0, 0.002, 0);
            }
        }
    }

    private static void mirror(Minecraft mc, Player p) {
        double t = p.tickCount * 0.35;
        for (int i = 0; i < 4; i++) {
            double a = t + i * Math.PI / 2;
            mc.level.addParticle(MIRROR, p.getX() + Math.cos(a) * 0.9, p.getY() + 0.2 + (i % 2) * 0.9 + Math.sin(t) * 0.3, p.getZ() + Math.sin(a) * 0.9, 0, 0, 0);
        }
    }
}

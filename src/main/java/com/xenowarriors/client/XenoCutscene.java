package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.XenoCutscenePacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Xeno Blast cutscene (20 seconds), for the caster and everyone frozen by it: letterbox bars, the move's name, and
 * a cinematic camera - close on their face, slowly circling them as the power builds, over their shoulder as it
 * peaks, a wide side shot as it's let go, the impact from high up, then pulling back over the aftermath.
 * Shares UltimateCutscene's hooks (camera, locked controls, hidden HUD).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class XenoCutscene {
    private static XenoCutscenePacket cur;
    private static int age = -1;
    private static CameraType saved;
    private static float lockYaw;

    private XenoCutscene() {}

    public static void start(XenoCutscenePacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.ticks() <= 0) { if (cur != null) stop(mc); return; }     // cancelled (Ultima pushed it back)
        mash = 0; counters = 0;
        if (cur == null) saved = mc.options.getCameraType();
        cur = m;
        age = 0;
        lockYaw = m.yaw();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    public static boolean active() { return cur != null; }

    // ---- fighting back while frozen in it
    private static int mash, counters;

    /** True while you're caught in someone else's Xeno Blast (your skill keys fire back instead). */
    public static boolean frozenTarget() { return cur != null && !caster(); }

    private static boolean ultima() { return com.xenowarriors.client.ClientCharacterData.getLocal() == com.xenowarriors.character.CharacterType.ULTIMA; }

    public static void counter(int slot) {
        if (cur == null || age > com.xenowarriors.skill.XenoBlast.COUNTER_UNTIL) return;
        counters++;
        com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.XenoCounterPacket(cur.caster(), slot));
    }

    public static void mashProgress(int p) { mash = p; }

    private static boolean caster() {
        Minecraft mc = Minecraft.getInstance();
        return cur != null && mc.player != null && mc.player.getId() == cur.caster();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || cur == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ++age >= cur.ticks()) { stop(mc); return; }
        // Ultima: mash Jump to push the wave back
        if (frozenTarget() && ultima()) while (mc.options.keyJump.consumeClick())
            com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.XenoMashPacket(cur.caster(), 0));
        if (caster()) {           // the caster keeps aiming the way they cast it
            mc.player.setYRot(lockYaw);
            mc.player.setYHeadRot(lockYaw);
            mc.player.yBodyRot = lockYaw;
        }
    }

    private static void stop(Minecraft mc) {
        cur = null;
        age = -1;
        if (saved != null) mc.options.setCameraType(saved);
        saved = null;
    }

    private static final int[] SHOTS = {0, 40, 120, 200, 260, 330};
    private static final int BLEND = 12;

    /** {x, y, z, yaw, pitch} for this frame. */
    public static double[] camera(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (cur == null || mc.level == null) return null;
        float t = age + pt;
        int shot = 0;
        for (int i = 0; i < SHOTS.length; i++) if (t >= SHOTS[i]) shot = i;
        Vec3[] c = shot(shot, t, pt);
        if (shot > 0 && t - SHOTS[shot] < BLEND) {
            Vec3[] prev = shot(shot - 1, t, pt);
            float w = smooth((t - SHOTS[shot]) / BLEND);
            c = new Vec3[]{prev[0].lerp(c[0], w), prev[1].lerp(c[1], w)};
        }
        Vec3 pos = unstuck(mc, c[0]);
        Vec3 d = c[1].subtract(pos);
        double yaw = Math.toDegrees(Mth.atan2(-d.x, d.z));
        double pitch = -Math.toDegrees(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        // a little shake when it's let go
        if (t >= cur.release() && t < cur.release() + 40) {
            float k = 1F - (t - cur.release()) / 40F;
            yaw += Mth.sin(t * 2.3F) * 0.8F * k;
            pitch += Mth.cos(t * 2.9F) * 0.6F * k;
        }
        return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
    }

    private static Vec3[] shot(int shot, float t, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Entity c = mc.level.getEntity(cur.caster());
        Vec3 base = c != null ? c.getPosition(pt) : new Vec3(cur.x(), cur.y(), cur.z());
        Vec3 body = base.add(0, 1.3, 0), head = base.add(0, 1.65, 0);
        Vec3 fwd = Vec3.directionFromRotation(0, cur.yaw()), side = new Vec3(-fwd.z, 0, fwd.x), up = new Vec3(0, 1, 0);
        Vec3 hands = cur.rhalix() ? head.add(fwd.scale(1.3)).add(side.scale(-0.25)).subtract(0, 0.3, 0) : body.add(side.scale(0.45)).subtract(fwd.scale(0.35)).subtract(0, 0.35, 0);
        Vec3 impact = body.add(fwd.scale(cur.rhalix() ? 16 : 18));
        switch (shot) {
            case 0 -> {   // close on their face, slowly pushing in
                double push = t / 40.0 * 0.6;
                return new Vec3[]{head.add(fwd.scale(2.4 - push)).add(side.scale(-0.7)).add(up.scale(-0.15)), head};
            }
            case 1 -> {   // slowly circling them as the power builds
                double a = (t - 40) / 80.0 * Math.PI * 0.9 + Math.PI * 0.35;
                Vec3 around = fwd.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
                return new Vec3[]{body.add(around.scale(4.2)).add(up.scale(0.6 + (t - 40) / 80.0)), hands.lerp(body, 0.4)};
            }
            case 2 -> {   // low, close on the energy in their hands, creeping closer as it peaks
                double k = (t - 120) / 80.0;
                return new Vec3[]{hands.add(side.scale(-2.6 + k)).add(fwd.scale(1.4)).add(up.scale(-0.5)), hands};
            }
            case 3 -> {   // wide from the side as it's let go
                Vec3 mid = body.lerp(impact, 0.45);
                return new Vec3[]{mid.add(side.scale(-14)).add(up.scale(3)), mid};
            }
            case 4 -> {   // high above the impact
                return new Vec3[]{impact.subtract(fwd.scale(6)).add(side.scale(7)).add(up.scale(10 + (t - 260) * 0.03)), impact};
            }
            default -> {  // pulling back behind them over the aftermath
                double k = (t - 330) / 70.0;
                return new Vec3[]{body.subtract(fwd.scale(4 + k * 6)).add(side.scale(1.5)).add(up.scale(1.5 + k * 4)), body.add(fwd.scale(10))};
            }
        }
    }

    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    private static Vec3 unstuck(Minecraft mc, Vec3 pos) {
        for (int i = 0; i < 8; i++) {
            BlockPos bp = BlockPos.containing(pos);
            if (mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) return pos;
            pos = pos.add(0, 0.5, 0);
        }
        return pos;
    }

    /** Letterbox, the move's name, and (for the frozen) a note that they can't move. */
    public static void overlay(GuiGraphics g, float pt, int width, int height) {
        if (cur == null) return;
        float t = age + pt, len = cur.ticks();
        float bars = smooth(t / 10F) * (1F - smooth((t - (len - 10)) / 10F));
        int bar = Math.round(height * 0.11F * bars);
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);
        var font = Minecraft.getInstance().font;
        float title = Mth.clamp((t - 6F) / 8F, 0F, 1F) * (1F - Mth.clamp((t - 70F) / 10F, 0F, 1F));
        if (title > 0.02F) {
            int a = Math.round(title * 255) << 24;
            g.pose().pushPose();
            g.pose().translate(24, height - bar - 36, 0);
            g.drawString(font, "LAST RESORT", 0, 0, (cur.rhalix() ? 0xE0B0FF : 0xFFD0A0) | a);
            g.pose().scale(2.4F, 2.4F, 1F);
            g.drawString(font, "XENO BLAST", 0, 5, 0xFFFFFF | a, true);
            g.pose().popPose();
        }
        // a flash of light the moment it's let go
        float flash = 1F - Mth.clamp((t - cur.release()) / 12F, 0F, 1F);
        if (t >= cur.release() && flash > 0) {
            int col = cur.rhalix() ? 0xD9B0FF : 0xFFF0C0;
            g.fill(0, 0, width, height, (Math.round(flash * 160) << 24) | col);
        }
        if (!caster()) {
            String s = "Frozen in place...";
            g.drawString(font, s, width - font.width(s) - 10, height - bar - 14, 0xC0C0D0, true);
            if (t < com.xenowarriors.skill.XenoBlast.COUNTER_UNTIL) {
                String hint = ultima() ? "MASH [JUMP] to push the wave back!   or use a projectile skill to fight back"
                        : "Fight back! Use a projectile skill to soften the blow";
                boolean blink = (age / 6) % 2 == 0;
                g.drawCenteredString(font, hint, width / 2, bar + 8, blink ? 0xFFE070 : 0xFFFFFF);
            }
            if (ultima()) {          // the push-back meter
                int bw = Math.min(220, width / 2), bx = width / 2 - bw / 2, by = bar + 22;
                float f = Mth.clamp(mash / (float) com.xenowarriors.skill.XenoBlast.MASH_NEEDED, 0F, 1F);
                g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF000000);
                g.fill(bx, by, bx + bw, by + 6, 0xFF203028);
                g.fill(bx, by, bx + Math.round(bw * f), by + 6, 0xFF40FF8C);
            }
        }
    }
}

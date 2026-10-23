package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.TeamUltEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.xenowarriors.entity.TeamUltEntity.*;

/**
 * Gravity Fox Fire as a cutscene for both Mikiko and Rhalix: the same cinematic camera for the two of them (a wide
 * shot as Rhalix rises, a low angle looking up at the slam, following Mikiko's leap, a slow orbit round the dome
 * through the storm, looking down on the dive, then a hero shot of them back to back), letterbox, no HUD, and each
 * player's body flown along the choreography in TeamUltEntity.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TeamUltCutscene {
    private static final int[] SHOTS = {0, RISE_END, 24, 34, DIVE, 90};
    private static final int BLEND = 6;
    private static TeamUltEntity current;
    private static CameraType savedCamera;

    private TeamUltCutscene() {}

    public static boolean active() {
        return current != null;
    }

    private static TeamUltEntity find(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof TeamUltEntity u && u.isAlive() && u.tickCount <= END
                    && (u.rhalixId() == mc.player.getId() || u.mikikoId() == mc.player.getId())) return u;
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        TeamUltEntity found = find(mc);
        if (found != null && current == null) {
            savedCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (found == null && current != null) {
            if (savedCamera != null) mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
        current = found;
        if (current == null || mc.player == null) return;
        LocalPlayer p = mc.player;
        float t = current.tickCount;
        boolean rhalix = current.rhalixId() == p.getId();
        Vec3 pos = rhalix ? current.rhalixAt(t) : current.mikikoAt(t);
        p.setPos(pos.x, pos.y, pos.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0F;
        // facing: at the dome; Mikiko spins on top of it; at the end they face away from each other
        Vec3 c = current.position();
        float yaw;
        if (t >= 98) {
            Vec3 out = rhalix ? current.side().scale(-1) : current.side();
            yaw = (float) Math.toDegrees(Mth.atan2(-out.x, out.z));
        } else {
            Vec3 look = c.add(0, 1, 0).subtract(pos);
            yaw = (float) Math.toDegrees(Mth.atan2(-look.x, look.z));
            if (!rhalix && t >= TOP && t < DIVE) yaw += (t - TOP) * 38F;
        }
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
        p.setXRot(rhalix && t >= SLAM && t < 82 ? 30F : 0F);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (!active()) return;
        var in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (active()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active() && !event.getOverlay().id().equals(XenoWarriors.id("team_cutscene"))) event.setCanceled(true);
    }

    /** {x, y, z, yaw, pitch} for this frame, or null. */
    public static double[] camera(float partial) {
        TeamUltEntity u = current;
        Minecraft mc = Minecraft.getInstance();
        if (u == null || !u.isAlive() || mc.player == null || mc.level == null) return null;
        float age = u.tickCount + partial;
        int shot = 0;
        for (int i = 0; i < SHOTS.length; i++) if (age >= SHOTS[i]) shot = i;
        Vec3[] cur = shot(u, shot, age);
        if (shot > 0 && age - SHOTS[shot] < BLEND) {
            Vec3[] prev = shot(u, shot - 1, age);
            float w = smooth((age - SHOTS[shot]) / BLEND);
            cur = new Vec3[]{prev[0].lerp(cur[0], w), prev[1].lerp(cur[1], w)};
        }
        Vec3 pos = cur[0];
        // impacts shake the camera: the slam and the finale
        float shake = Math.max(age >= SLAM && age < SLAM + 6 ? (SLAM + 6 - age) / 6F : 0F, age >= FINALE && age < FINALE + 10 ? (FINALE + 10 - age) / 10F * 1.4F : 0F);
        if (shake > 0) pos = pos.add(Math.sin(age * 9.1) * 0.25 * shake, Math.sin(age * 11.7) * 0.2 * shake, Math.cos(age * 8.3) * 0.25 * shake);
        pos = unstuck(mc, pos);
        Vec3 d = cur[1].subtract(pos);
        double yaw = Math.toDegrees(Mth.atan2(-d.x, d.z));
        double pitch = -Math.toDegrees(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
    }

    private static Vec3[] shot(TeamUltEntity u, int shot, float age) {
        Vec3 c = u.position(), d = u.dir(), s = u.side(), up = new Vec3(0, 1, 0);
        Vec3 r = u.rhalixAt(age).add(0, 1.2, 0), m = u.mikikoAt(age).add(0, 1.2, 0);
        switch (shot) {
            case 0 -> { // wide from the side: Rhalix taking off, Mikiko gathering fire
                Vec3 mid = u.rhalixStart().lerp(u.mikikoStart(), 0.5);
                return new Vec3[]{mid.subtract(d.scale(5)).add(s.scale(7)).add(up.scale(1.5)), r.lerp(m, 0.3)};
            }
            case 1 -> { // low, looking up at him as he slams the dome down
                return new Vec3[]{c.add(d.scale(7)).add(s.scale(2.5)).add(up.scale(0.8)), r.lerp(c.add(0, 2, 0), smooth((age - RISE_END) / 8F) * 0.6F)};
            }
            case 2 -> { // following Mikiko's leap
                return new Vec3[]{m.subtract(d.scale(4.5)).add(s.scale(2.5)).add(up.scale(1.5)), m.add(d.scale(2))};
            }
            case 3 -> { // slow orbit around the dome through the storm
                double base = Math.atan2(s.z, s.x);
                double ang = base + (age - 34) / 44.0 * Math.toRadians(200);
                return new Vec3[]{c.add(Math.cos(ang) * 14, 6.5, Math.sin(ang) * 14), c.add(0, 3.5, 0)};
            }
            case 4 -> { // high above, watching him dive through
                return new Vec3[]{c.add(up.scale(15)).add(s.scale(3.5)).add(d.scale(-2)), m.lerp(c, 0.5)};
            }
            default -> { // hero shot: the two of them back to back, slowly pushing in
                double push = Math.min(1, (age - 90) / 14.0);
                return new Vec3[]{c.add(d.scale(5.5 - push * 1.5)).add(up.scale(1.2 + push * 0.2)), c.add(0, 1.3, 0)};
            }
        }
    }

    private static Vec3 unstuck(Minecraft mc, Vec3 pos) {
        for (int i = 0; i < 8; i++) {
            BlockPos bp = BlockPos.containing(pos);
            if (mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) return pos;
            pos = pos.add(0, 0.5, 0);
        }
        return pos;
    }

    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            TeamUltEntity u = current;
            if (u == null) return;
            float age = u.tickCount + partialTick;
            float bars = smooth(age / 8F) * (1F - smooth((age - (END - 8)) / 8F));
            int bar = Math.round(height * 0.12F * bars);
            g.fill(0, 0, width, bar, 0xFF000000);
            g.fill(0, height - bar, width, height, 0xFF000000);
            // white flash on the finale
            if (age >= FINALE && age < FINALE + 6) {
                int a = Math.round((1F - (age - FINALE) / 6F) * 200);
                g.fill(0, 0, width, height, (a << 24) | 0xFFFFFF);
            }
            float title = Mth.clamp((age - 4F) / 6F, 0F, 1F) * (1F - Mth.clamp((age - 40F) / 8F, 0F, 1F));
            if (title > 0.02F) {
                int a = Math.round(title * 255) << 24;
                var font = Minecraft.getInstance().font;
                g.pose().pushPose();
                g.pose().translate(24, height - bar - 34, 0);
                g.drawString(font, Component.literal("TEAM ULTIMATE"), 0, 0, 0xFFC870 | a);
                g.pose().scale(2F, 2F, 1F);
                g.drawString(font, Component.literal("Gravity Fox Fire").withStyle(st -> st.withBold(true)), 0, 6, 0xFFFFFF | a);
                g.pose().popPose();
            }
        }
    }
}

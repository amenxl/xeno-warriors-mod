package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.GravityBarrageEntity;
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

import static com.xenowarriors.entity.GravityBarrageEntity.*;

/**
 * Plays Rhalix's ultimate as a cutscene for the player using it: a cinematic camera that cuts
 * between shots (watching him rise, over his shoulder, orbiting the barrage, following him down,
 * then a wide shot of the explosions), letterbox bars, no HUD, and movement locked while his
 * body flies along the path.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UltimateCutscene {
    private static final int[] SHOT_START = {0, 26, 50, DESCEND, LANDED};
    private static final int BLEND = 8;

    private static GravityBarrageEntity current;
    private static CameraType savedCamera;

    private UltimateCutscene() {}

    public static boolean active() {
        return current != null;
    }

    private static GravityBarrageEntity find(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GravityBarrageEntity u && u.isAlive() && u.ownerId() == mc.player.getId()) return u;
        }
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        GravityBarrageEntity found = find(mc);
        if (found != null && current == null) {
            savedCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (found == null && current != null) {
            if (savedCamera != null) mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
        current = found;
        if (current == null || mc.player == null) return;

        // fly Rhalix's body along the path and keep him facing the targets
        LocalPlayer p = mc.player;
        float age = current.tickCount;
        Vec3 start = current.position();
        p.setPos(start.x, start.y + riseFraction(age) * current.rise(), start.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0F;
        Vec3 look = current.center().add(0, 1, 0).subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Mth.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        p.setYRot(yaw);
        p.setXRot(Mth.clamp(pitch, -60F, 75F));
        p.setYHeadRot(yaw);
        p.yBodyRot = yaw;
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

    /** Hide every HUD element except our letterbox while the cutscene plays. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active() && !event.getOverlay().id().equals(XenoWarriors.id("cutscene"))) event.setCanceled(true);
    }

    /** Camera position and angles for this frame: {x, y, z, yaw, pitch}, or null when not in a cutscene. */
    public static double[] camera(float partialTick) {
        GravityBarrageEntity u = current;
        Minecraft mc = Minecraft.getInstance();
        if (u == null || !u.isAlive() || mc.player == null) return null;
        float age = u.tickCount + partialTick;
        int shot = 0;
        for (int i = 0; i < SHOT_START.length; i++) if (age >= SHOT_START[i]) shot = i;
        Vec3[] cur = shot(u, shot, age, partialTick);
        if (shot > 0 && age - SHOT_START[shot] < BLEND) {
            Vec3[] prev = shot(u, shot - 1, age, partialTick);
            float w = GravityBarrageEntity.smooth((age - SHOT_START[shot]) / BLEND);
            cur = new Vec3[]{prev[0].lerp(cur[0], w), prev[1].lerp(cur[1], w)};
        }
        Vec3 pos = unstuck(mc, cur[0]);
        Vec3 d = cur[1].subtract(pos);
        double yaw = Math.toDegrees(Mth.atan2(-d.x, d.z));
        double pitch = -Math.toDegrees(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
    }

    /** {camera position, point to look at} for one shot. */
    private static Vec3[] shot(GravityBarrageEntity u, int shot, float age, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 start = u.position();
        Vec3 body = mc.player.getPosition(partialTick).add(0, 1.2, 0);
        Vec3 center = u.center().add(0, 1, 0);
        Vec3 flat = new Vec3(center.x - start.x, 0, center.z - start.z);
        Vec3 dir = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0, mc.player.getYRot()) : flat.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 up = new Vec3(0, 1, 0);
        switch (shot) {
            case 0 -> { // in front of him, low, watching him take off
                return new Vec3[]{start.add(dir.scale(4.5)).add(side.scale(1.8)).add(up.scale(0.6)), body};
            }
            case 1 -> { // over his shoulder at the targets as the rocks rise
                return new Vec3[]{body.subtract(dir.scale(3.8)).add(side.scale(1.6)).add(up.scale(0.8)), center};
            }
            case 2 -> { // slow orbit around the barrage
                Vec3 mid = body.lerp(center, 0.6);
                double dist = Math.max(8, body.distanceTo(center) * 0.7 + 6);
                double base = Math.atan2(-dir.z, -dir.x) + 0.6;
                double ang = base + (age - 50) / 60.0 * Math.toRadians(110);
                return new Vec3[]{mid.add(Math.cos(ang) * dist, 3.5, Math.sin(ang) * dist), center.lerp(body, 0.25)};
            }
            case 3 -> { // following him down
                return new Vec3[]{body.subtract(dir.scale(5)).add(up.scale(2.5)).add(side.scale(-1.2)), body.add(dir.scale(3))};
            }
            default -> { // wide high shot as the rocks explode
                return new Vec3[]{center.subtract(dir.scale(9)).add(side.scale(4)).add(up.scale(7)), center};
            }
        }
    }

    /** Lift the camera out of any block it would be stuck inside. */
    private static Vec3 unstuck(Minecraft mc, Vec3 pos) {
        for (int i = 0; i < 8; i++) {
            BlockPos bp = BlockPos.containing(pos);
            if (mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) return pos;
            pos = pos.add(0, 0.5, 0);
        }
        return pos;
    }

    /** Black cinematic bars and the ultimate's name. */
    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            GravityBarrageEntity u = current;
            if (u == null) return;
            float age = u.tickCount + partialTick;
            float bars = GravityBarrageEntity.smooth(age / 8F) * (1F - GravityBarrageEntity.smooth((age - (END - 8)) / 8F));
            int bar = Math.round(height * 0.11F * bars);
            g.fill(0, 0, width, bar, 0xFF000000);
            g.fill(0, height - bar, width, height, 0xFF000000);

            float title = Mth.clamp((age - 4F) / 6F, 0F, 1F) * (1F - Mth.clamp((age - 46F) / 8F, 0F, 1F));
            if (title > 0.02F) {
                int a = Math.round(title * 255) << 24;
                var font = Minecraft.getInstance().font;
                Component name = Component.translatable("skill.xenowarriors.gravity_barrage").withStyle(s -> s.withBold(true));
                Component label = Component.translatable("hud.xenowarriors.ultimate");
                g.pose().pushPose();
                g.pose().translate(24, height - bar - 34, 0);
                g.drawString(font, label, 0, 0, 0xD9C8FF | a);
                g.pose().scale(2F, 2F, 1F);
                g.drawString(font, name, 0, 6, 0xFFFFFF | a);
                g.pose().popPose();
            }
        }
    }
}

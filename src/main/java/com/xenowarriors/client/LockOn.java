package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.render.FxDraw;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Lock-on (default Z): locks onto the creature or player closest to your crosshair, keeps your view
 * turned toward it, marks it with a spinning reticle and shows its health at the top of the screen.
 * Press again to let go; it also lets go if the target dies, gets too far, or stays out of sight.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LockOn {
    private static final double RANGE = 28, KEEP_RANGE = 36, CONE = 32;
    private static final int LOST_SIGHT_TICKS = 60;
    private static final float TURN_PER_TICK = 0.45F;

    private static LivingEntity target;
    private static int unseen;
    private static int lockedAt;
    private static float shownHealth = -1;

    private LockOn() {}

    public static LivingEntity target() {
        return target;
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (target != null) {
            release(true);
            return;
        }
        LivingEntity found = find(mc.player);
        if (found == null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 0.25F));
            return;
        }
        target = found;
        unseen = 0;
        lockedAt = mc.player.tickCount;
        shownHealth = found.getHealth();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.9F));
    }

    private static void release(boolean sound) {
        target = null;
        if (sound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.4F));
    }

    private static LivingEntity find(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1F);
        double cos = Math.cos(Math.toRadians(CONE));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        AABB box = player.getBoundingBox().inflate(RANGE);
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box, LockOn::lockable)) {
            if (e == player) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > RANGE || dist < 0.5) continue;
            double dot = to.scale(1 / dist).dot(look);
            if (dot < cos || !visible(player, e)) continue;
            double score = Math.acos(Math.min(1, dot)) * 3 + dist / RANGE;   // mostly "closest to the crosshair"
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static boolean lockable(LivingEntity e) {
        return e.isAlive() && !(e instanceof ArmorStand) && !(e instanceof Player p && p.isSpectator()) && !e.isInvisible();
    }

    private static boolean visible(Player player, Entity e) {
        Vec3 eye = player.getEyePosition();
        return player.level().clip(new ClipContext(eye, e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS
                || player.level().clip(new ClipContext(eye, e.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || target.level() != mc.level || !lockable(target) || target.isRemoved()
                || mc.player.isDeadOrDying() || target.distanceTo(mc.player) > KEEP_RANGE) {
            release(false);
            return;
        }
        unseen = visible(mc.player, target) ? 0 : unseen + 1;
        if (unseen > LOST_SIGHT_TICKS) release(true);
        shownHealth = Mth.lerp(0.25F, shownHealth, target.getHealth());
    }

    /** Turn the view toward the target every frame, smoothly. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.isPaused() || UltimateCutscene.active()) return;
        float partial = event.renderTickTime;
        Vec3 eye = p.getEyePosition(partial);
        Vec3 aim = target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0);
        Vec3 d = aim.subtract(eye);
        float wantYaw = (float) Math.toDegrees(Mth.atan2(-d.x, d.z));
        float wantPitch = (float) -Math.toDegrees(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        float f = 1F - (float) Math.pow(1F - TURN_PER_TICK, Math.max(0.01F, mc.getDeltaFrameTime()));
        float yaw = p.getYRot() + Mth.wrapDegrees(wantYaw - p.getYRot()) * f;
        float pitch = Mth.clamp(p.getXRot() + (wantPitch - p.getXRot()) * f, -90F, 90F);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    /** Spinning red brackets around the locked target. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = event.getCamera();
        float partial = event.getPartialTick();
        Vec3 pos = target.getPosition(partial).add(0, target.getBbHeight() * 0.55, 0).subtract(cam.getPosition());
        float age = mc.player.tickCount + partial;
        float pop = Mth.clamp((age - lockedAt) / 5F, 0F, 1F);
        float size = Math.max(0.6F, Math.max(target.getBbWidth(), target.getBbHeight()) * 0.6F) * (1.8F - 0.8F * pop);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(cam.rotation());
        pose.mulPose(Axis.ZP.rotationDegrees(age * 4F));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = FxDraw.glow(buffers);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float a = 0.9F * pop;
        for (int i = 0; i < 4; i++) {
            float ang = i * (float) Math.PI / 2F;
            float c = Mth.cos(ang), s = Mth.sin(ang);
            // an L-shaped corner bracket
            corner(vc, m, n, c, s, size, a);
        }
        // small center dot
        float d = 0.06F;
        FxDraw.vertex(vc, m, n, -d, -d, 0, 1F, 0.3F, 0.2F, a, 0, 0, 1);
        FxDraw.vertex(vc, m, n, d, -d, 0, 1F, 0.3F, 0.2F, a, 0, 0, 1);
        FxDraw.vertex(vc, m, n, d, d, 0, 1F, 0.3F, 0.2F, a, 0, 0, 1);
        FxDraw.vertex(vc, m, n, -d, d, 0, 1F, 0.3F, 0.2F, a, 0, 0, 1);
        pose.popPose();
        buffers.endBatch();
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Matrix3f n, float c, float s, float size, float a) {
        float len = size * 0.4F, w = size * 0.07F;
        // corner point at (size, size) rotated by (c, s)
        float[][] bars = {{size - len, size - w, size, size}, {size - w, size - len, size, size}};
        for (float[] b : bars) {
            float[][] pts = {{b[0], b[1]}, {b[2], b[1]}, {b[2], b[3]}, {b[0], b[3]}};
            for (float[] p : pts) {
                float x = p[0] * c - p[1] * s, y = p[0] * s + p[1] * c;
                FxDraw.vertex(vc, m, n, x, y, 0, 1F, 0.35F, 0.25F, a, 0, 0, 1);
            }
        }
    }

    /** Target name and health bar at the top of the screen. */
    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            LivingEntity t = target;
            Minecraft mc = Minecraft.getInstance();
            if (t == null || mc.player == null || mc.options.hideGui) return;
            Font font = mc.font;
            int w = 120, x = width / 2 - w / 2, y = 18;
            float frac = Mth.clamp(shownHealth / Math.max(1F, t.getMaxHealth()), 0F, 1F);
            float real = Mth.clamp(t.getHealth() / Math.max(1F, t.getMaxHealth()), 0F, 1F);
            g.drawCenteredString(font, t.getDisplayName(), width / 2, y - 11, 0xFFFFFF);
            g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xFF0A0A10);
            g.fill(x, y, x + w, y + 5, 0xFF2A1416);
            g.fill(x, y, x + Math.round(w * frac), y + 5, 0xFFFFD27A);       // trailing chip
            g.fill(x, y, x + Math.round(w * real), y + 5, 0xFFE0352B);
            g.fill(x, y, x + Math.round(w * real), y + 1, 0xFFFF7A6A);
            String hp = Math.round(t.getHealth()) + " / " + Math.round(t.getMaxHealth());
            g.pose().pushPose();
            g.pose().translate(width / 2F - font.width(hp) * 0.35F, y + 8, 0);
            g.pose().scale(0.7F, 0.7F, 1F);
            g.drawString(font, hp, 0, 0, 0xDDDDDD);
            g.pose().popPose();
        }
    }
}

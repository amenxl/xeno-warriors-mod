package com.xenowarriors.client.party;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.LockOn;
import com.xenowarriors.client.render.FxDraw;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.ReviveWarpPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Revive warp, your side: locked on to a teammate who's down (player or bot), the lock-on turns green and says
 * REVIVE; hold right-click for a second (empty hand or any item - while locked on to them right-click doesn't use
 * your item) to warp to them for 30 Karma. Also draws Rhalix's Karma shield domes.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReviveWarpClient {
    public static final int HOLD = 20;
    private static int hold;
    private static boolean sent;

    private ReviveWarpClient() {}

    /** A teammate (your party's player, or your / your party's bot) who's down and needs reviving. */
    public static boolean downedAlly(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == null || mc.player == null || e == mc.player || !e.isAlive()) return false;
        if (e instanceof com.xenowarriors.bot.PartyBotEntity b)
            return b.downed() && b.ownerId() != null && (b.ownerId().equals(mc.player.getUUID()) || PartyClient.inMyParty(b.ownerId()));
        return e instanceof Player p && PartyClient.inMyParty(p.getUUID()) && PartyClient.knockedOut(p);
    }

    /** 0..1 how far the hold has got. */
    public static float progress() { return Math.min(1F, hold / (float) HOLD); }

    private static LivingEntity target() {
        LivingEntity t = LockOn.target();
        return downedAlly(t) ? t : null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { SHIELDS.clear(); return; }
        long now = mc.level.getGameTime();
        SHIELDS.values().removeIf(s -> now > s.end);
        LivingEntity t = target();
        boolean down = t != null && mc.screen == null && mc.options.keyUse.isDown() && mc.player != null && !PartyClient.knockedOut(mc.player);
        if (!down) { hold = 0; sent = false; return; }
        if (sent) return;
        if (++hold >= HOLD) {
            sent = true;
            ModNetwork.sendToServer(new ReviveWarpPacket(t.getId()));
        }
    }

    /** Locked on to someone down: right-click is the warp, not your item. */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && target() != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Test hook: pretend right-click has been held this long. */
    public static void testHold(int ticks) { hold = ticks; sent = false; }

    // ------------------------------------------------------------------ Karma shield domes
    private static final class Dome { double x, y, z; float r; long start, end; }
    private static final Map<Integer, Dome> SHIELDS = new HashMap<>();

    public static void shield(int owner, double x, double y, double z, float r, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (ticks <= 0) { SHIELDS.remove(owner); return; }
        Dome d = new Dome();
        d.x = x; d.y = y; d.z = z; d.r = r; d.start = mc.level.getGameTime(); d.end = d.start + ticks;
        SHIELDS.put(owner, d);
    }

    public static int shields() { return SHIELDS.size(); }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHIELDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = FxDraw.glow(buffers);
        for (Dome d : SHIELDS.values()) {
            float age = mc.level.getGameTime() - d.start + pt;
            float pop = Mth.clamp(age / 6F, 0F, 1F);
            float size = d.r * (0.4F + 0.6F * (1F - (1F - pop) * (1F - pop)));
            pose.pushPose();
            pose.translate(d.x - cam.x, d.y - cam.y, d.z - cam.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(age * 1.5F));
            FxDraw.sphere(pose, vc, size, 0.62F, 0.32F, 1F, 0.42F, age, 0.12F);
            FxDraw.sphere(pose, vc, size * 0.97F, 0.85F, 0.6F, 1F, 0.16F, -age, 0.2F);
            FxDraw.disc(pose, vc, size * 0.9F, size, 0.7F, 0.4F, 1F, 0.2F, 0.75F);
            pose.popPose();
        }
        buffers.endBatch();
    }

    // ------------------------------------------------------------------ the REVIVE marker
    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            LivingEntity t = target();
            if (t == null || mc.player == null || mc.options.hideGui) return;
            int y = Math.max(18, com.xenowarriors.client.HudLayout.bossBottom() + 14) + 14;     // (under the lock-on health bar)
            long tick = mc.level.getGameTime();
            int col = (tick / 8) % 2 == 0 ? 0x5CFF7A : 0xB8FFC4;
            g.pose().pushPose();
            g.pose().translate(width / 2F, y, 0);
            g.pose().scale(1.5F, 1.5F, 1F);
            g.drawCenteredString(mc.font, "REVIVE", 0, 0, col);
            g.pose().popPose();
            if (t.distanceTo(mc.player) < com.xenowarriors.party.ReviveCircle.RADIUS) {      // already there: just stay in the circle
                g.drawCenteredString(mc.font, "In their circle - reviving...", width / 2, y + 15, 0x9CFFB0);
                return;
            }
            String key = mc.options.keyUse.getTranslatedKeyMessage().getString();
            String hint = "Hold " + key + " to warp to them (" + Math.round(com.xenowarriors.party.ReviveWarp.COST) + " Karma)";
            g.drawCenteredString(mc.font, hint, width / 2, y + 15, 0xD8D8E0);
            int w = 100, x = width / 2 - w / 2, by = y + 26;
            g.fill(x - 1, by - 1, x + w + 1, by + 4, 0xC0101010);
            g.fill(x, by, x + Math.round(w * progress()), by + 3, 0xFF4CE070);
        }
    }
}

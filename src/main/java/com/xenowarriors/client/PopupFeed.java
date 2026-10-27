package com.xenowarriors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.PartySyncPacket;
import com.xenowarriors.network.PopupPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.xenowarriors.client.portrait.PortraitScreen.*;

/**
 * The pop-up feed on the left side of the screen: slanted banners with a picture of who it's about (a party member,
 * bot or pet) - someone's down, wants a team attack, joined, a pet ran away or levelled up... At most 3 at a time; a new
 * one goes on top of the last, older ones fade out sooner, and each fades away on its own after a few seconds.
 * Party ones come from the synced party list (watched here); the rest arrive as PopupPackets from the server.
 */
public final class PopupFeed implements IGuiOverlay {
    private static final long LIFE = 7000, FADE = 1500, SLIDE = 250;
    private static final int MAX = 3;

    private record Pop(int style, UUID subject, int type, int pet, String title, String text, String face, long born) {}

    private static final List<Pop> POPS = new ArrayList<>();     // oldest first

    public static void push(int style, UUID subject, int type, int pet, String title, String text, String face) {
        long now = Util.getMillis();
        // the same news twice in a row (e.g. a re-sync): skip
        for (Pop p : POPS) if (p.subject.equals(subject) && p.title.equals(title) && now - p.born < 3000) return;
        POPS.add(new Pop(style, subject, type, pet, title, text, face, now));
        while (POPS.size() > MAX) POPS.remove(0);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && style != PopupPacket.INFO)
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    style == PopupPacket.DANGER ? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value() : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                    style == PopupPacket.DANGER ? 0.7F : 1.5F, 0.5F));
    }

    // ------------------------------------------------------------------ party news (from the synced party list)

    private record Seen(boolean down, boolean low, String name, int type, boolean bot) {}
    private static final Map<UUID, Seen> SEEN = new HashMap<>();
    private static boolean primed;

    /** Called whenever the party list arrives. */
    public static void party(List<PartySyncPacket.Member> list) {
        Minecraft mc = Minecraft.getInstance();
        UUID me = mc.player == null ? null : mc.player.getUUID();
        Map<UUID, Seen> now = new HashMap<>();
        for (PartySyncPacket.Member m : list) {
            boolean low = m.maxHealth() > 0 && m.health() / m.maxHealth() < 0.3F && !m.knockedOut();
            now.put(m.id(), new Seen(m.knockedOut(), low, m.name(), m.type(), m.bot()));
            if (m.id().equals(me) || !primed) continue;
            Seen was = SEEN.get(m.id());
            if (was == null) push(PopupPacket.GOOD, m.id(), m.type(), -1, m.bot() ? "Bot joined" : "Joined the party", m.name() + (m.bot() ? " is fighting with you." : " joined your party."), "normal");
            else {
                if (m.knockedOut() && !was.down) push(PopupPacket.DANGER, m.id(), m.type(), -1, "Down!", m.name() + " is down! Get to them.", "exhausted");
                else if (!m.knockedOut() && was.down) push(PopupPacket.GOOD, m.id(), m.type(), -1, "Back up", m.name() + " is back on their feet.", "normal");
                else if (low && !was.low) push(PopupPacket.DANGER, m.id(), m.type(), -1, "Low health", m.name() + " needs help!", "hurt");
            }
        }
        if (primed) for (Map.Entry<UUID, Seen> e : SEEN.entrySet())
            if (!now.containsKey(e.getKey()) && !e.getKey().equals(me) && !list.isEmpty())
                push(PopupPacket.INFO, e.getKey(), e.getValue().type(), -1, "Left the party", e.getValue().name() + (e.getValue().bot() ? " was sent home." : " left the party."), "normal");
        SEEN.clear();
        SEEN.putAll(now);
        primed = true;
    }

    public static void reset() { SEEN.clear(); POPS.clear(); primed = false; }

    // ------------------------------------------------------------------ drawing

    private static float[] colors(int style) {   // top-left rgb, bottom-right rgb
        return switch (style) {
            case PopupPacket.DANGER -> new float[]{0.95F, 0.35F, 0.35F, 0.55F, 0.08F, 0.18F};
            case PopupPacket.REQUEST -> new float[]{1F, 0.72F, 0.3F, 0.85F, 0.3F, 0.35F};
            case PopupPacket.GOOD -> new float[]{0.3F, 0.85F, 0.6F, 0.15F, 0.45F, 0.7F};
            case PopupPacket.PET -> new float[]{1F, 0.6F, 0.25F, 0.65F, 0.35F, 0.85F};
            default -> new float[]{0.2F, 0.7F, 0.95F, 0.42F, 0.3F, 0.85F};
        };
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (POPS.isEmpty() || mc.player == null || mc.options.hideGui) return;
        long now = Util.getMillis();
        Font font = mc.font;
        int w = 150, h = 26, gap = 9;
        float baseY = height * 0.47F;                         // left side, above the chat area
        int n = POPS.size();
        for (int i = n - 1; i >= 0; i--) {
            Pop p = POPS.get(i);
            int age = (int) (now - p.born), depth = n - 1 - i;    // 0 = newest
            long life = LIFE - depth * 1800L;                     // older ones go sooner
            if (age > life) { POPS.remove(i); continue; }
            float alpha = Mth.clamp((life - age) / (float) FADE, 0F, 1F) * (depth == 0 ? 1F : depth == 1 ? 0.85F : 0.65F);
            float slide = ease(Mth.clamp(age / (float) SLIDE, 0F, 1F));
            int x = Math.round(6 - (1F - slide) * (w + 20));
            int y = Math.round(baseY - (i + 1) * (h + gap));          // oldest at the bottom, each new one above the last
            drawPop(g, font, p, x, y, w, h, alpha);
        }
    }

    private static float ease(float t) { return 1F - (1F - t) * (1F - t) * (1F - t); }

    private static void drawPop(GuiGraphics g, Font font, Pop p, int x, int y, int w, int h, float a) {
        float[] c = colors(p.style);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = begin();
        float sk = 6;
        // tech lines above and below, offset like the reference
        quad(bb, m, x + 10, y - 3, x + w * 0.62F, y - 3, x + w * 0.62F - 1, y - 2, x + 9, y - 2, c[0], c[1], c[2], 0.9F * a, c[3], c[4], c[5], 0.5F * a);
        quad(bb, m, x + w * 0.35F, y + h + 2, x + w - 4, y + h + 2, x + w - 5, y + h + 3, x + w * 0.35F - 1, y + h + 3, c[3], c[4], c[5], 0.5F * a, c[0], c[1], c[2], 0.9F * a);
        // dark backing, then the colour gradient banner (slanted ends)
        quad(bb, m, x + sk - 1, y - 1, x + w + 1, y - 1, x + w - sk + 1, y + h + 1, x - 1, y + h + 1, 0F, 0F, 0F, 0.55F * a, 0F, 0F, 0F, 0.55F * a);
        quad(bb, m, x + sk, y, x + w, y, x + w - sk, y + h, x, y + h, c[0], c[1], c[2], 0.92F * a, c[3], c[4], c[5], 0.92F * a);
        // a lighter top band and little star specks
        quad(bb, m, x + sk, y, x + w, y, x + w - 1.5F, y + 4, x + sk - 1.5F, y + 4, 1F, 1F, 1F, 0.22F * a, 1F, 1F, 1F, 0.08F * a);
        long seed = p.born;
        for (int i = 0; i < 9; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            float sx = x + 10 + ((seed >>> 33) % (w - 50)), sy = y + 6 + ((seed >>> 20) % (h - 9));
            quad(bb, m, sx, sy, sx + 1, sy, sx + 1, sy + 1, sx, sy + 1, 1F, 1F, 1F, 0.7F * a, 1F, 1F, 1F, 0.7F * a);
        }
        // the left edge accent
        quad(bb, m, x - 4, y + 3, x - 2, y + 3, x - 2, y + h - 3, x - 4, y + h - 3, c[0], c[1], c[2], a, c[0], c[1], c[2], a);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();

        int ta = Math.max(5, Math.round(a * 255)) << 24;
        g.pose().pushPose();
        g.pose().translate(x + 7, y + 3.5F, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, p.title.toUpperCase(java.util.Locale.ROOT), 0, 0, ta | 0xFFF6D8, true);
        g.pose().popPose();
        String line = font.plainSubstrByWidth(p.text, (int) ((w - 44) / 0.8F));
        g.pose().pushPose();
        g.pose().translate(x + 7, y + 13.5F, 0);
        g.pose().scale(0.8F, 0.8F, 1F);
        g.drawString(font, line, 0, 0, ta | 0xFFFFFF, true);
        g.pose().popPose();

        // the picture: overlapping the right end, poking out above the banner
        if (a > 0.25F) picture(g, p, x + w - 34, y - 6, 32);
    }

    private static void picture(GuiGraphics g, Pop p, int x, int y, int size) {
        if (p.pet >= 0) {
            LivingEntity e = HudHead.find(p.subject);
            if (e != null) {
                float bh = Math.max(0.4F, e.getBbHeight());
                int scale = p.pet == 2 ? Math.round(size * 1.2F / Math.max(0.8F, bh * 0.55F)) : Math.round(size * (p.pet == 0 ? 1.15F : 1.4F));
                g.fill(x, y, x + size, y + size, 0x90000000);
                g.enableScissor(x, y, x + size, y + size + 6);
                try {
                    InventoryScreen.renderEntityInInventory(g, x + size / 2, Math.round(y + size * (p.pet == 2 ? 0.45F : 0.62F) + bh * 0.8F * scale), scale,
                            new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateY(-0.6F)), null, e);
                } finally { g.disableScissor(); }
            } else {
                g.pose().pushPose();
                g.pose().translate(x + 4, y + 8, 0);
                g.pose().scale(1.5F, 1.5F, 1F);
                g.renderItem(new ItemStack(p.pet == 0 ? Items.FOX_SPAWN_EGG : p.pet == 1 ? Items.BAT_SPAWN_EGG : Items.WITHER_SKELETON_SKULL), 0, 0);
                g.pose().popPose();
            }
            return;
        }
        CharacterType t = CharacterType.values()[Mth.clamp(p.type, 0, CharacterType.values().length - 1)];
        if (t == CharacterType.NONE) return;
        if (HudFaces.hasArt(t)) KhHud.portrait(g, HudFaces.texture(t, p.face.isEmpty() ? "normal" : p.face), x, y, size, p.style == PopupPacket.DANGER);
        else HudHead.draw(g, HudHead.find(p.subject), t, x, y, size, p.style == PopupPacket.DANGER);
    }

    static { XenoWarriors.LOGGER.debug("PopupFeed ready"); }
}

package com.xenowarriors.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.render.CharacterDetailLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Live HUD portraits: the round drawn close-up with the face the person's model is wearing RIGHT NOW keyed into it -
 * the same blink, expression, talking mouth, battle damage, Demon/Lunar eyes, drawn-face set, outfit, Fox King hair,
 * hood, glasses, goggles and DragonMineZ form hair colour as the model. The face comes from
 * {@link CharacterDetailLayer#faceFrame} (what the model draws), so nothing extra is synced: the model's own state is reused.
 *
 * Each portrait slot (you, each party member) owns one small texture, re-composed only when what it shows changes
 * (a blink, a new mouth frame...). The frames (head + ring, the face cut out) come from tools/xw/hud/portrait_frames.py.
 */
public final class HudPortrait {
    private HudPortrait() {}

    /** Where the face's 8x8 front sits in a 128px portrait (printed by portrait_frames.py). */
    public static final float RX = 22.589F, RY = 25.552F, RS = 82.941F;
    private static final int N = 128;
    /** Behind see-through face pixels: the ring's inner fill (as in the drawn portraits), or the silhouette's dark. */
    private static final int BG = 0x1E2234, SIL = 0x0D0D0F;

    private static final class Slot {
        DynamicTexture tex;
        ResourceLocation id;
        String key;
    }

    /** One slot per person shown (kept to a handful; the oldest is let go). */
    private static final Map<UUID, Slot> SLOTS = new LinkedHashMap<>(16, 0.75F, true);
    private static final int MAX_SLOTS = 24;
    /** Decoded images (face sheets, frames), or empty when there's no such file. */
    private static final Map<ResourceLocation, Optional<NativeImage>> IMAGES = new HashMap<>();
    private static int made;
    /** Times a slot was re-composed (for the UI test: proves it only happens on a change). */
    public static int composes;
    /** UI test: the face each portrait last showed (uuid -> "face|damage|mouth"; see CharacterDetailLayer.recordFaces). */
    public static final Map<UUID, String> SHOWN = ClientReset.track(new HashMap<>());
    /** What the last portrait showed (for the UI test). */
    public static String lastKey;

    private static final CharacterDetailLayer.FaceFrame FF = new CharacterDetailLayer.FaceFrame();

    static {
        ClientReset.onReset(HudPortrait::clear);
    }

    /** The body whose face a portrait mirrors: a player, or a bot teammate's drawn body. Null if there's none in view. */
    public static AbstractClientPlayer body(LivingEntity e) {
        if (e instanceof AbstractClientPlayer p) return p;
        if (e instanceof com.xenowarriors.bot.PartyBotEntity b && !com.xenowarriors.client.bot.BotAvatars.disabled)
            return com.xenowarriors.client.bot.BotAvatars.existing(b);
        return null;
    }

    /**
     * Draws the live portrait of {@code e} (if it has a live face and this character has frames) and returns true;
     * false when the caller should draw its static portrait instead (not in view, or no frame for this look).
     */
    public static boolean draw(GuiGraphics g, LivingEntity e, CharacterType type, int x, int y, int size, boolean redFlash) {
        ResourceLocation tex = texture(e, type);
        if (tex == null) return false;
        KhHud.portrait(g, tex, x, y, size, redFlash);
        return true;
    }

    /** The live portrait texture for {@code e}, or null (see draw). */
    public static ResourceLocation texture(LivingEntity e, CharacterType type) {
        if (e == null || !HudFaces.hasArt(type)) return null;
        if (e instanceof com.xenowarriors.bot.PartyBotEntity b && body(e) == null) return botTexture(b, type);
        AbstractClientPlayer p = body(e);
        if (p == null || ClientCharacterData.get(p) != type) return null;
        try {
            String[] look = CharacterDetailLayer.portraitLook(p, type);
            ResourceLocation frame = frame(look[1]);
            String lookName = look[1];
            if (frame == null) { frame = frame(look[0]); lookName = look[0]; }
            if (frame == null) { frame = frame(type.getId()); lookName = type.getId(); }
            if (frame == null) return null;
            CharacterDetailLayer.FaceFrame f = CharacterDetailLayer.faceFrame(p, FF);
            if (CharacterDetailLayer.recordFaces) SHOWN.put(e.getUUID(), f.face + "|" + f.damage + "|" + f.mouth);
            int hair = f.silhouette ? -1 : com.xenowarriors.client.compat.DmzForms.hairColor(p);
            ResourceLocation hairLayer = hair >= 0 ? frameFile(lookName + "_hair") : null;
            ResourceLocation mask = f.silhouette ? frameFile(lookName + "_mask") : null;
            String key = frame + "|" + f.face + "|" + f.damage + "|" + f.mouth + "|" + (hairLayer != null ? hair : -1) + "|" + f.silhouette;
            return slot(e.getUUID(), key, frame, f.face, f.damage, f.mouth, hairLayer, hair, mask, f.silhouette);
        } catch (RuntimeException ex) {
            XenoWarriors.LOGGER.warn("HUD portrait: couldn't draw the live face", ex);
            return null;
        }
    }

    /** A bot drawn the plain way (bot bodies turned off): its plain face, or the pod's sleeping one - as the model shows it. */
    private static ResourceLocation botTexture(com.xenowarriors.bot.PartyBotEntity b, CharacterType type) {
        ResourceLocation frame = frame(type.getId());
        if (frame == null) return null;
        ResourceLocation face = com.xenowarriors.client.pod.PodFace.withFace(CharacterDetailLayer.baseFace(type), b);
        return slot(b.getUUID(), frame + "|" + face, frame, face, null, null, null, -1, null, false);
    }

    /** The frame for a look, if there is one. */
    private static ResourceLocation frame(String look) {
        if (look == null) return null;
        return frameFile(look);
    }

    private static ResourceLocation frameFile(String name) {
        ResourceLocation rl = XenoWarriors.id("textures/gui/hud/frame/" + name + ".png");
        return image(rl) != null ? rl : null;
    }

    private static NativeImage image(ResourceLocation rl) {
        return IMAGES.computeIfAbsent(rl, k -> {
            var res = Minecraft.getInstance().getResourceManager().getResource(k);
            if (res.isEmpty()) return Optional.empty();
            try (var in = res.get().open()) {
                return Optional.of(NativeImage.read(NativeImage.Format.RGBA, in));
            } catch (Exception ex) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static ResourceLocation slot(UUID who, String key, ResourceLocation frame, ResourceLocation face, ResourceLocation damage,
                                         ResourceLocation mouth, ResourceLocation hairLayer, int hair, ResourceLocation mask, boolean silhouette) {
        lastKey = key;
        Slot s = SLOTS.get(who);
        if (s == null) {
            s = new Slot();
            s.tex = new DynamicTexture(N, N, true);
            s.id = XenoWarriors.id("hud_portrait/" + (made++));
            Minecraft.getInstance().getTextureManager().register(s.id, s.tex);
            SLOTS.put(who, s);
            if (SLOTS.size() > MAX_SLOTS) {
                var it = SLOTS.entrySet().iterator();
                var oldest = it.next();
                it.remove();
                Minecraft.getInstance().getTextureManager().release(oldest.getValue().id);
            }
        }
        if (!key.equals(s.key)) {
            compose(s.tex.getPixels(), image(frame), image(face), damage == null ? null : image(damage), mouth == null ? null : image(mouth),
                    hairLayer == null ? null : image(hairLayer), hair, mask == null ? null : image(mask), silhouette);
            s.tex.upload();
            s.key = key;
            composes++;
        }
        return s.id;
    }

    // ---------------------------------------------------------------- composing (NativeImage pixels are ABGR)

    private static void compose(NativeImage out, NativeImage frame, NativeImage face, NativeImage damage, NativeImage mouth,
                                NativeImage hairLayer, int hair, NativeImage mask, boolean silhouette) {
        int bg = silhouette ? SIL : BG;
        // the face: each portrait pixel averages a 4x4 grid of face texels (like the drawn portraits' 4x supersample)
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int a = 0, r = 0, gg = 0, b = 0;
                if (x + 1 > RX && x < RX + RS && y + 1 > RY && y < RY + RS) {
                    for (int sy = 0; sy < 4; sy++) {
                        for (int sx = 0; sx < 4; sx++) {
                            float u = (x + (sx + 0.5F) / 4F - RX) / RS, v = (y + (sy + 0.5F) / 4F - RY) / RS;
                            int c = bg;
                            if (u >= 0 && u < 1 && v >= 0 && v < 1) c = facePixel(face, damage, mouth, u, v, bg);
                            r += (c >> 16) & 255; gg += (c >> 8) & 255; b += c & 255;
                        }
                    }
                    r /= 16; gg /= 16; b /= 16; a = 255;
                }
                int fr = frame == null ? 0 : frame.getPixelRGBA(x, y);       // the head and ring over it
                int[] px = over(a, r, gg, b, fr);
                if (hairLayer != null) {                                     // a form's hair colour over the hair
                    int hp = hairLayer.getPixelRGBA(x, y);
                    int ha = (hp >>> 24) & 255;
                    if (ha > 0) {
                        int hr = (hp & 255) * ((hair >> 16) & 255) / 255, hg = ((hp >> 8) & 255) * ((hair >> 8) & 255) / 255,
                                hb = ((hp >> 16) & 255) * (hair & 255) / 255;
                        px = blend(px, ha, hr, hg, hb);
                    }
                }
                if (silhouette && mask != null) {                            // Kaeru's Change: the head goes dark
                    int m = (mask.getPixelRGBA(x, y) >>> 24) & 255;
                    if (m > 0) px = new int[]{px[0], lerp(px[1], 13, m), lerp(px[2], 13, m), lerp(px[3], 15, m)};
                }
                out.setPixelRGBA(x, y, (px[0] << 24) | (px[3] << 16) | (px[2] << 8) | px[1]);
            }
        }
    }

    /** One face sample (RGB): the face, the damage layer blended over it, the mouth cut out over that. */
    private static int facePixel(NativeImage face, NativeImage damage, NativeImage mouth, float u, float v, int bg) {
        int c = bg;
        int f = sample(face, u, v);
        if (((f >>> 24) & 255) >= 26) c = rgb(f);                   // (cutout, like the model)
        if (damage != null) {
            int d = sample(damage, u, v);
            int da = (d >>> 24) & 255;
            if (da > 0) c = mix(c, rgb(d), da);
        }
        if (mouth != null) {
            int m = sample(mouth, u, v);
            if (((m >>> 24) & 255) >= 26) c = rgb(m);
        }
        return c;
    }

    /** The face sheet's front (its top-left quarter: the 8x8 face of a 16x16 box-UV sheet). */
    private static int sample(NativeImage img, float u, float v) {
        if (img == null) return 0;
        int w = img.getWidth() / 2, h = img.getHeight() / 2;
        return img.getPixelRGBA(Math.min(w - 1, (int) (u * w)), Math.min(h - 1, (int) (v * h)));
    }

    private static int rgb(int abgr) { return ((abgr & 255) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 255); }

    private static int mix(int c, int o, int a) {
        return (lerp((c >> 16) & 255, (o >> 16) & 255, a) << 16) | (lerp((c >> 8) & 255, (o >> 8) & 255, a) << 8) | lerp(c & 255, o & 255, a);
    }

    private static int lerp(int a, int b, int t) { return a + (b - a) * t / 255; }

    /** {a, r, g, b}: the frame pixel (ABGR) over the face pixel. */
    private static int[] over(int a, int r, int g, int b, int frame) {
        int fa = (frame >>> 24) & 255;
        return blend(new int[]{a, r, g, b}, fa, frame & 255, (frame >> 8) & 255, (frame >> 16) & 255);
    }

    private static int[] blend(int[] under, int a, int r, int g, int b) {
        if (a == 0) return under;
        int oa = a + under[0] * (255 - a) / 255;
        if (oa == 0) return new int[]{0, 0, 0, 0};
        int ur = under[1] * under[0] * (255 - a) / 255, ug = under[2] * under[0] * (255 - a) / 255, ub = under[3] * under[0] * (255 - a) / 255;
        return new int[]{oa, (r * a + ur) / oa, (g * a + ug) / oa, (b * a + ub) / oa};
    }

    /** Resource packs changed (or you left the world): decode again, recompose. */
    public static void clear() {
        for (var o : IMAGES.values()) o.ifPresent(NativeImage::close);
        IMAGES.clear();
        if (RenderSystem.isOnRenderThread()) {
            for (Slot s : SLOTS.values()) Minecraft.getInstance().getTextureManager().release(s.id);
            SLOTS.clear();
        } else {
            for (Slot s : SLOTS.values()) s.key = null;
        }
    }

    /** For the UI test: the key the portrait would show for {@code e} right now, built from the model's face (or null). */
    public static String keyFor(LivingEntity e, CharacterType type) {
        ResourceLocation t = texture(e, type);
        return t == null ? null : lastKey;
    }
}

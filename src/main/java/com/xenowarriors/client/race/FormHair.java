package com.xenowarriors.client.race;

import com.mojang.blaze3d.platform.NativeImage;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.compat.DmzForms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Their own hair changes colour when they transform: the pixels of a character's hair - on the skin's head and on the
 * 3D hair pieces (masks made by tools/xw/formfaces/hair_masks.py) - recoloured to the form's hair colour (Super Saiyan
 * gold, SSJ4's near-black...), keeping its shading. Mikiko and Rhalix have their own hair systems.
 */
public final class FormHair {
    private FormHair() {}

    private static final Map<String, ResourceLocation> DONE = new HashMap<>();
    /** our recoloured copies -> the texture they were made from (so the race recolour can work on top of them) */
    private static final Map<ResourceLocation, ResourceLocation> ORIGIN = new HashMap<>();
    public static int recoloured;

    /** The form's hair colour for this player's own hair (RGB), or -1 to leave it. */
    public static int color(AbstractClientPlayer p) {
        CharacterType t = ClientCharacterData.get(p);
        if (t == CharacterType.NONE || t == CharacterType.YOU || t == CharacterType.MIKIKO || t == CharacterType.RHALIX) return -1;
        if (com.xenowarriors.client.FusionLook.of(p) != null) return -1;
        return DmzForms.hairColor(p);
    }

    /** {@code tex} (a skin or a details sheet) with their hair in the form's colour; {@code tex} itself if there's none to change. */
    public static ResourceLocation tint(ResourceLocation tex, AbstractClientPlayer p) {
        if (tex == null) return null;
        int col = color(p);
        if (col < 0) return tex;
        ResourceLocation mask = maskFor(tex);
        if (mask == null) return tex;
        String key = tex + "|" + Integer.toHexString(col);
        ResourceLocation done = DONE.get(key);
        if (done != null) return done;
        ResourceLocation out = tex;
        try (NativeImage src = load(tex); NativeImage m = load(mask)) {
            if (src != null && m != null && m.getWidth() == src.getWidth() && m.getHeight() == src.getHeight()) {
                NativeImage im = new NativeImage(src.getWidth(), src.getHeight(), true);
                im.copyFrom(src);
                recolor(im, m, col);
                out = XenoWarriors.id("formhair/" + Integer.toHexString(key.hashCode()));
                Minecraft.getInstance().getTextureManager().register(out, new DynamicTexture(im));
                ORIGIN.put(out, tex);
                recoloured++;
            }
        } catch (Exception e) {
            XenoWarriors.LOGGER.warn("Couldn't recolour the hair on {}", tex, e);
        }
        DONE.put(key, out);
        return out;
    }

    public static void clear() { DONE.clear(); ORIGIN.clear(); }

    /**
     * The pixels of one of our recoloured copies (made again from its texture), for the race recolour to work on; null
     * if {@code id} isn't one of ours. The caller closes it.
     */
    public static NativeImage loadRecoloured(ResourceLocation id, AbstractClientPlayer p) {
        ResourceLocation orig = ORIGIN.get(id);
        if (orig == null) return null;
        ResourceLocation mask = maskFor(orig);
        int col = color(p);
        NativeImage src = load(orig);
        if (src == null || mask == null || col < 0) return src;
        try (NativeImage m = load(mask)) {
            if (m != null && m.getWidth() == src.getWidth() && m.getHeight() == src.getHeight()) recolor(src, m, col);
        }
        return src;
    }

    /** textures/entity/character/max_royal[_ssj4|_buff].png -> max_royal_hairmask.png; ..._details[_tornN].png -> ..._details_hairmask.png */
    private static ResourceLocation maskFor(ResourceLocation tex) {
        String path = tex.getPath();
        if (!tex.getNamespace().equals(XenoWarriors.MOD_ID) || !path.startsWith("textures/entity/character/") || !path.endsWith(".png")) return null;
        String base = path.substring(0, path.length() - 4).replaceAll("_torn\\d$", "");
        for (String s : new String[]{"_ssj4", "_buff"}) if (base.endsWith(s)) base = base.substring(0, base.length() - s.length());
        ResourceLocation m = XenoWarriors.id(base + "_hairmask.png");
        return Minecraft.getInstance().getResourceManager().getResource(m).isPresent() ? m : null;
    }

    private static NativeImage load(ResourceLocation rl) {
        var res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) return null;
        try (var in = res.get().open()) { return NativeImage.read(in); } catch (Exception e) { return null; }
    }

    private static float lum(int abgr) { return 0.3F * (abgr & 255) + 0.59F * ((abgr >> 8) & 255) + 0.11F * ((abgr >> 16) & 255); }

    private static void recolor(NativeImage im, NativeImage mask, int col) {
        // the hair's main shade: the commonest masked colour
        Map<Integer, Integer> count = new HashMap<>();
        for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++)
            if ((mask.getPixelRGBA(x, y) >>> 24) > 100 && (im.getPixelRGBA(x, y) >>> 24) > 100) count.merge(im.getPixelRGBA(x, y) & 0xFFFFFF, 1, Integer::sum);
        int ref = -1, n = 0;
        for (var e : count.entrySet()) if (e.getValue() > n) { n = e.getValue(); ref = e.getKey(); }
        if (ref < 0) return;
        float rl = Math.max(8F, lum(ref));
        int cr = (col >> 16) & 255, cg = (col >> 8) & 255, cb = col & 255;
        for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) {
            if ((mask.getPixelRGBA(x, y) >>> 24) <= 100) continue;
            int c = im.getPixelRGBA(x, y);
            int a = c >>> 24;
            if (a == 0) continue;
            float k = Math.max(0.45F, Math.min(1.35F, lum(c) / rl));
            int nr = Math.min(255, Math.round(cr * k)), ng = Math.min(255, Math.round(cg * k)), nb = Math.min(255, Math.round(cb * k));
            im.setPixelRGBA(x, y, (a << 24) | (nb << 16) | (ng << 8) | nr);
        }
    }
}

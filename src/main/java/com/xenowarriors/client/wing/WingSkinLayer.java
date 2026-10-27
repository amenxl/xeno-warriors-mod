package com.xenowarriors.client.wing;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * Skins for Rhalix's winged companions, drawn over the vanilla look: a colored wash (Shadow, Rose) or a soft
 * glow (Frost, Ember, Golden), and Void does both.
 */
public class WingSkinLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final Int2IntOpenHashMap SKIN = new Int2IntOpenHashMap();
    //                                   wash r,g,b,a                  glow r,g,b
    private static final float[][] LOOK = {
            null,
            {0.12F, 0.1F, 0.18F, 0.6F, 0, 0, 0},           // Shadow
            {1F, 0.45F, 0.7F, 0.5F, 0, 0, 0},              // Rose
            {0.7F, 0.9F, 1F, 0.35F, 0.25F, 0.45F, 0.65F},   // Frost
            {1F, 0.5F, 0.2F, 0.3F, 0.6F, 0.25F, 0.05F},     // Ember
            {1F, 0.85F, 0.3F, 0.35F, 0.55F, 0.42F, 0.1F},   // Golden
            {0.35F, 0.1F, 0.6F, 0.55F, 0.35F, 0.1F, 0.55F}, // Void
            {0.35F, 0.85F, 0.35F, 0.5F, 0.1F, 0.3F, 0.08F},  // Namekian (green)
            {0.95F, 0.9F, 1F, 0.4F, 0.45F, 0.3F, 0.6F},      // Sacred Kai (pale, violet glow)
            {0.95F, 0.8F, 0.5F, 0.5F, 0, 0, 0},              // Sand (deserts, badlands, savannas)
            {0.3F, 0.75F, 0.25F, 0.5F, 0, 0, 0},             // Jungle
            {0.4F, 0.5F, 0.25F, 0.55F, 0.08F, 0.18F, 0.04F}, // Swamp
            {0.3F, 0.6F, 1F, 0.45F, 0.05F, 0.22F, 0.4F},     // Ocean
            {1F, 0.22F, 0.22F, 0.5F, 0, 0, 0},               // Mushroom
            {0.8F, 0.85F, 0.95F, 0.4F, 0.25F, 0.3F, 0.6F},   // Storm (mountains)
            {0.8F, 0.1F, 0.1F, 0.55F, 0.5F, 0.08F, 0.04F},   // Crimson (the Nether)
            {0.1F, 0.05F, 0.15F, 0.6F, 0.1F, 0.45F, 0.4F},   // Ender (the End)
            {0.95F, 0.95F, 0.7F, 0.4F, 0.5F, 0.5F, 0.3F},    // Spirit (DragonMineZ Otherworld)
            {0.6F, 0.9F, 0.4F, 0.4F, 0.4F, 0.35F, 0.05F},    // Lush (lush caves, glowing like glow berries)
    };

    /** Bats get a fully recoloured coat per look (dark, light): the vanilla bat texture re-painted along this ramp. */
    private static final int[][] BAT_RAMP = {
            null,
            {0x0a0810, 0x463c5a}, {0x5a1432, 0xff96c8}, {0x3c5a82, 0xe6f5ff}, {0x3c0a00, 0xff8c28}, {0x5a3c00, 0xffe16e},
            {0x0f001e, 0x963cdc}, {0x143c14, 0x8ce678}, {0x5a506e, 0xfaf5ff}, {0x6e5028, 0xf5dca0}, {0x14320a, 0x78c83c},
            {0x1e2814, 0x829650}, {0x0a285a, 0x6ec8ff}, {0x6e0a0a, 0xff5a50}, {0x3c4050, 0xdce1f0}, {0x3c0000, 0xe6281e},
            {0x050a0f, 0x3cc8b4}, {0x6e6e46, 0xffffc8}, {0x284614, 0xdce65a}};
    private static final java.util.Map<Integer, net.minecraft.resources.ResourceLocation> BAT_TEX = new java.util.HashMap<>();

    private static net.minecraft.resources.ResourceLocation batTexture(int skin, net.minecraft.resources.ResourceLocation vanilla) {
        if (skin <= 0 || skin >= BAT_RAMP.length) return null;
        return BAT_TEX.computeIfAbsent(skin, k -> {
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                var res = mc.getResourceManager().getResource(vanilla);
                if (res.isEmpty()) return null;
                com.mojang.blaze3d.platform.NativeImage img;
                try (var in = res.get().open()) { img = com.mojang.blaze3d.platform.NativeImage.read(in); }
                int dark = BAT_RAMP[k][0], light = BAT_RAMP[k][1];
                for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                    int c = img.getPixelRGBA(x, y);               // ABGR
                    int a = c >>> 24 & 255; if (a == 0) continue;
                    int r = c & 255, g = c >> 8 & 255, b = c >> 16 & 255;
                    float l = Math.min(1F, (float) Math.pow((r * 0.3F + g * 0.55F + b * 0.15F) / 255F, 0.7) * 1.6F);
                    int nr = (int) ((dark >> 16 & 255) + ((light >> 16 & 255) - (dark >> 16 & 255)) * l);
                    int ng = (int) ((dark >> 8 & 255) + ((light >> 8 & 255) - (dark >> 8 & 255)) * l);
                    int nb = (int) ((dark & 255) + ((light & 255) - (dark & 255)) * l);
                    img.setPixelRGBA(x, y, a << 24 | nb << 16 | ng << 8 | nr);
                }
                return mc.getTextureManager().register("xw_bat_" + k, new net.minecraft.client.renderer.texture.DynamicTexture(img));
            } catch (Exception ex) { return null; }
        });
    }

    public WingSkinLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    public static void set(int id, int skin) {
        if (skin == 0) SKIN.remove(id);
        else SKIN.put(id, skin);
    }

    public static int get(int id) {
        return SKIN.getOrDefault(id, 0);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float yaw, float pitch) {
        int skin = SKIN.getOrDefault(e.getId(), 0);
        if (skin <= 0 || skin >= LOOK.length || e.isInvisible()) return;
        float[] l = LOOK[skin];
        var tex = getTextureLocation(e);
        if (e instanceof net.minecraft.world.entity.ambient.Bat) {
            var coat = batTexture(skin, tex);
            if (coat != null) {                                // bats: a whole new coat, then the glow if it has one
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(coat)), light,
                        net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(e, 0F), 1F, 1F, 1F, 1F);
                if (l[4] + l[5] + l[6] > 0) {
                    float pulse = 0.5F + 0.2F * (float) Math.sin(age * 0.1F);
                    getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(coat)), LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, l[4] * pulse, l[5] * pulse, l[6] * pulse, 1F);
                }
                return;
            }
        }
        if (l[3] > 0) {
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light,
                    OverlayTexture.NO_OVERLAY, l[0], l[1], l[2], l[3]);
        }
        if (l[4] + l[5] + l[6] > 0) {
            float pulse = 0.75F + 0.25F * (float) Math.sin(age * 0.1F);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, l[4] * pulse, l[5] * pulse, l[6] * pulse, 1F);
        }
    }
}

package com.xenowarriors.client.mikiko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the Hekublade: the sword itself, plus its 8 crystals drawn separately so they can move.
 * Every so often the crystals swing slowly around the X - around the blade's axis, through the front
 * and back of the guard - half a turn, back into the same pattern.
 */
public class HekubladeRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation BASE = XenoWarriors.id("item/hekublade_base");
    public static final ResourceLocation[] CRYSTALS = new ResourceLocation[8];
    /** Mikiko's outfits carry the blade's look from that time: Origins 2's broadsword, Season 3's hooked blade. */
    public static final ResourceLocation ORIGINS_2 = XenoWarriors.id("item/hekublade_origins2"), SEASON_3 = XenoWarriors.id("item/hekublade_s3"),
            SEASON_3_GLOBE = XenoWarriors.id("item/hekublade_s3_globe");
    static {
        for (int i = 0; i < 8; i++) CRYSTALS[i] = XenoWarriors.id("item/hekublade_crystal_" + i);
    }
    /** Center of the X guard and each crystal's offset from it, in model pixels. */
    private static final float GUARD = 5.5605F;
    private static final float[][] OFFSETS = {{-2.0F, 2.0F}, {-1.2F, 2.7F}, {-2.7F, 1.2F}, {2.0F, -2.0F}, {2.7F, -1.2F},
            {1.2F, -2.7F}, {-2.55F, 2.55F}, {2.55F, -2.55F}};
    private static final int CYCLE = 240, MOVE = 90;
    private static HekubladeRenderer instance;

    // ---- the crystals glow after an enemy is sealed into the blade
    private static final java.util.Map<Integer, Long> GLOW = new java.util.HashMap<>();
    private static final int GLOW_TIME = 90;
    /** Who's holding the blade being drawn right now (null = you, e.g. first person). */
    public static net.minecraft.world.entity.Entity current;

    public static void glow(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) GLOW.put(entityId, mc.level.getGameTime());
    }

    private static float glowFor(net.minecraft.world.entity.Entity e, float time) {
        if (e == null) return 0F;
        Long start = GLOW.get(e.getId());
        if (start == null) return 0F;
        float t = time - start;
        if (t < 0F || t > GLOW_TIME) {
            if (t > GLOW_TIME) GLOW.remove(e.getId());
            return 0F;
        }
        float in = Mth.clamp(t / 4F, 0F, 1F), out = Mth.clamp((GLOW_TIME - t) / 45F, 0F, 1F);
        return Math.min(in, out) * (0.8F + 0.2F * Mth.sin(t * 0.5F));
    }

    private static final net.minecraft.util.RandomSource RAND = net.minecraft.util.RandomSource.create();

    /** An extra additive pass over a crystal so it shines, tinted warm red-white, faded by the glow amount. */
    private static void drawGlow(BakedModel model, PoseStack pose, VertexConsumer vc, float glow) {
        drawGlow(model, pose, vc, glow, glow * 0.55F, glow * 0.4F);
    }

    private static void drawGlow(BakedModel model, PoseStack pose, VertexConsumer vc, float r, float g, float b) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) quads(model, dir, pose, vc, r, g, b);
        quads(model, null, pose, vc, r, g, b);
    }

    private static void quads(BakedModel model, net.minecraft.core.Direction dir, PoseStack pose, VertexConsumer vc, float r, float g, float b) {
        RAND.setSeed(42L);
        for (var q : model.getQuads(null, dir, RAND)) {
            vc.putBulkData(pose.last(), q, r, g, b, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }

    public HekubladeRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static HekubladeRenderer get() {
        if (instance == null) instance = new HekubladeRenderer();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer items = mc.getItemRenderer();
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, Sheets.cutoutBlockSheet(), true, stack.hasFoil());
        net.minecraft.world.entity.Entity holder = current != null ? current : mc.player;
        int outfit = holder instanceof net.minecraft.world.entity.player.Player hp ? com.xenowarriors.client.ClientCharacterData.mikikoOutfit(hp) : 0;
        if (outfit == com.xenowarriors.client.ClientCharacterData.ORIGINS_2 || outfit >= com.xenowarriors.client.ClientCharacterData.SEASON_3) {
            BakedModel alt = mc.getModelManager().getModel(outfit == com.xenowarriors.client.ClientCharacterData.ORIGINS_2 ? ORIGINS_2 : SEASON_3);
            items.renderModelLists(alt, stack, light, overlay, pose, vc);
            if (outfit != com.xenowarriors.client.ClientCharacterData.ORIGINS_2) {
                // the globe in the guard: a slowly turning galaxy that glows faintly
                BakedModel globe = mc.getModelManager().getModel(SEASON_3_GLOBE);
                items.renderModelLists(globe, stack, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, pose, vc);
                drawGlow(globe, pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(
                        net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)), 0.3F, 0.3F, 0.38F);
            }
            return;
        }
        BakedModel base = mc.getModelManager().getModel(BASE);
        items.renderModelLists(base, stack, light, overlay, pose, vc);

        float time = mc.level != null ? mc.level.getGameTime() + mc.getFrameTime() : Util.getMillis() / 50F;
        float turn = turn(time);
        float glow = glowFor(current != null ? current : mc.player, time);
        int crystalLight = glow > 0.05F ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
        VertexConsumer glowVc = glow > 0.05F ? buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(
                net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)) : null;
        // the crystals swing around the blade's own axis (through the front and back of the guard), so
        // they circle the X without ever passing through the blade
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.translate(GUARD / 16F, GUARD / 16F, 8F / 16F);
            pose.mulPose(Axis.of(new org.joml.Vector3f(0.70710677F, 0.70710677F, 0F)).rotation(-turn));
            pose.translate(-GUARD / 16F, -GUARD / 16F, -8F / 16F);
            BakedModel crystal = mc.getModelManager().getModel(CRYSTALS[i]);
            items.renderModelLists(crystal, stack, crystalLight, overlay, pose, vc);
            if (glowVc != null) drawGlow(crystal, pose, glowVc, glow);
            pose.popPose();
        }
    }

    /** Extra rotation of the crystals right now (radians): 0 most of the time, a slow half turn every so often. */
    private static float turn(float time) {
        int cycle = (int) Math.floor(time / CYCLE);
        if (Math.floorMod(cycle * 7919 + 13, 3) == 0) return 0F;    // skip some cycles so it feels occasional
        float t = (time - cycle * (float) CYCLE) / MOVE;
        if (t <= 0F || t >= 1F) return 0F;
        float s = t * t * (3F - 2F * t);
        return s * ((float) Math.PI);
    }
}

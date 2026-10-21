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
 * Every so often the crystals drift slowly around the X (half a turn, back into the same pattern).
 */
public class HekubladeRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation BASE = XenoWarriors.id("item/hekublade_base");
    public static final ResourceLocation[] CRYSTALS = new ResourceLocation[8];
    static {
        for (int i = 0; i < 8; i++) CRYSTALS[i] = XenoWarriors.id("item/hekublade_crystal_" + i);
    }
    /** Center of the X guard and each crystal's offset from it, in model pixels. */
    private static final float GUARD = 5.5605F;
    private static final float[][] OFFSETS = {{-2.0F, 2.0F}, {-1.2F, 2.7F}, {-2.7F, 1.2F}, {2.0F, -2.0F}, {2.7F, -1.2F},
            {1.2F, -2.7F}, {-2.55F, 2.55F}, {2.55F, -2.55F}};
    private static final int CYCLE = 240, MOVE = 90;
    private static HekubladeRenderer instance;

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
        BakedModel base = mc.getModelManager().getModel(BASE);
        items.renderModelLists(base, stack, light, overlay, pose, vc);

        float time = mc.level != null ? mc.level.getGameTime() + mc.getFrameTime() : Util.getMillis() / 50F;
        float turn = turn(time);
        for (int i = 0; i < 8; i++) {
            float baseAng = (float) Math.atan2(OFFSETS[i][1], OFFSETS[i][0]);
            float ang = baseAng + turn;
            // slide in front of the blade while passing over it (the blade runs along 45 and 225 degrees)
            float overBlade = Math.max(0F, Mth.cos(2F * (ang - ((float) Math.PI) / 4F)));
            float lift = turn != 0F ? (float) Math.pow(overBlade, 6) * 1.25F : 0F;
            pose.pushPose();
            pose.translate(GUARD / 16F, GUARD / 16F, (8F + lift) / 16F);
            pose.mulPose(Axis.ZP.rotation(turn));
            pose.translate(-GUARD / 16F, -GUARD / 16F, -8F / 16F);
            items.renderModelLists(mc.getModelManager().getModel(CRYSTALS[i]), stack, light, overlay, pose, vc);
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

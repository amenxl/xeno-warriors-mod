package com.xenowarriors.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.boss.ZombieSaiyanEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws the Zombie Saiyan: its rotting skin in a torn orange gi (Super Saiyan 4: shirtless, red fur), glowing eyes
 * (red as a zombie, teal as a Super Saiyan, gold as SSJ4), its hair per form (black, gold, the long SSJ3 mane, black
 * again for SSJ4), a Dragon Ball aura (gold, SSJ4 red), lightning around SSJ2/3 (DragonMineZ's own ki lightning when
 * it's there), and lying flat while knocked out.
 */
public class ZombieSaiyanRenderer extends MobRenderer<ZombieSaiyanEntity, ZombieSaiyanModel> {
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/boss/zombie_saiyan.png");
    private static final ResourceLocation TEX4 = XenoWarriors.id("textures/entity/boss/zombie_saiyan_ssj4.png");
    private static final ResourceLocation EYES = XenoWarriors.id("textures/entity/boss/zombie_saiyan_eyes.png");
    private static final ResourceLocation HAIR = XenoWarriors.id("textures/entity/boss/saiyan_hair.png");
    /** Hair colour per form. */
    public static final int[] HAIR_RGB = {0x26262E, 0xFFE070, 0xFFE878, 0xFFD860, 0x1C1C22};
    public static final int[] EYE_RGB = {0xFF2A1A, 0x40F0D0, 0x40F0D0, 0x40F0D0, 0xFFC020};

    public ZombieSaiyanRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieSaiyanModel(ctx.bakeLayer(ZombieSaiyanModel.LAYER)), 0.55F);
        addLayer(new Hair(this, new SaiyanHairModel(ctx.bakeLayer(SaiyanHairModel.LAYER))));
        addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieSaiyanEntity e) { return e.form() == ZombieSaiyanEntity.SSJ4 ? TEX4 : TEX; }

    @Override
    protected void scale(ZombieSaiyanEntity e, PoseStack pose, float pt) {
        float s = e.form() == ZombieSaiyanEntity.SSJ4 ? 1.1F : 1.04F;
        pose.scale(s, s, s);
    }

    @Override
    protected void setupRotations(ZombieSaiyanEntity e, PoseStack pose, float age, float yaw, float pt) {
        super.setupRotations(e, pose, age, yaw, pt);
        int ko = e.ko();
        if (ko > 0) {                                                            // flat on his back; sits up in the last half second
            float k = Mth.clamp((ko - pt) / 10F, 0F, 1F);
            pose.translate(0, 0.15 * k, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-88F * k));
            pose.translate(0, -0.9 * k, 0);
        }
    }

    @Override
    public void render(ZombieSaiyanEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        int f = e.form(), act = e.act();
        boolean powering = act == ZombieSaiyanEntity.ACT_POWER;
        int aura = e.ko() > 0 ? 0 : powering ? ZombieSaiyanEntity.AURA[Math.min(4, f + 1)] : ZombieSaiyanEntity.AURA[f];
        if (aura != 0) com.xenowarriors.client.DbzAura.mark(e.getId(), aura);
        effects(e, f, powering);
        super.render(e, yaw, pt, pose, buf, light);
    }

    private static int lastFx;

    /** Lightning (SSJ2, SSJ3), sparks while powering up - once a tick. */
    private static void effects(ZombieSaiyanEntity e, int f, boolean powering) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || e.ko() > 0) return;
        int key = e.getId() * 31 + e.tickCount;
        if (key == lastFx) return;
        lastFx = key;
        var r = e.getRandom();
        boolean lightning = f == ZombieSaiyanEntity.SSJ2 || f == ZombieSaiyanEntity.SSJ3 || (powering && f >= ZombieSaiyanEntity.SSJ1);
        if (lightning && r.nextInt(2) == 0) {
            double x = e.getX() + (r.nextDouble() - .5) * 1.2, y = e.getY() + r.nextDouble() * 2.1, z = e.getZ() + (r.nextDouble() - .5) * 1.2;
            if (!com.xenowarriors.client.compat.DmzKiParticles.spawn("ki_lightning", x, y, z, 0, 0, 0, 0x9FE8FF, 0.6F))
                for (int i = 0; i < 3; i++) mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + i * 0.15, z, (r.nextDouble() - .5) * .3, .1, (r.nextDouble() - .5) * .3);
        }
        if (powering || (f > ZombieSaiyanEntity.BASE && r.nextInt(3) == 0)) {
            int rgb = ZombieSaiyanEntity.AURA[Math.max(1, Math.min(4, powering ? f + 1 : f))];
            double x = e.getX() + (r.nextDouble() - .5) * 1.4, y = e.getY() + r.nextDouble() * 0.6, z = e.getZ() + (r.nextDouble() - .5) * 1.4;
            if (!com.xenowarriors.client.compat.DmzKiParticles.spawn("ki_shedding", x, y, z, 0, 0.12, 0, rgb, 0.5F))
                mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.08, 0);
        }
    }

    /** The hair: tinted per form, glowing in the gold forms. */
    static final class Hair extends RenderLayer<ZombieSaiyanEntity, ZombieSaiyanModel> {
        private final SaiyanHairModel hair;
        Hair(RenderLayerParent<ZombieSaiyanEntity, ZombieSaiyanModel> p, SaiyanHairModel hair) { super(p); this.hair = hair; }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, ZombieSaiyanEntity e, float limb, float amount, float pt, float age, float yaw, float pitch) {
            int f = e.form();
            hair.setup(getParentModel().head, f);
            int rgb = HAIR_RGB[f];
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            boolean gold = f >= ZombieSaiyanEntity.SSJ1 && f <= ZombieSaiyanEntity.SSJ3;
            int l = gold ? 0xF000F0 : light;
            hair.render(pose, buf.getBuffer(RenderType.entityCutoutNoCull(HAIR)), l, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
            if (gold) {                                                          // a soft glow on top
                float pulse = 0.25F + 0.1F * Mth.sin(age * 0.3F);
                hair.render(pose, buf.getBuffer(RenderType.eyes(HAIR)), 0xF000F0, OverlayTexture.NO_OVERLAY, r * pulse, g * pulse, b * pulse * 0.6F, 1F);
            }
        }
    }

    /** Glowing eyes (shut while knocked out). */
    static final class Eyes extends RenderLayer<ZombieSaiyanEntity, ZombieSaiyanModel> {
        Eyes(RenderLayerParent<ZombieSaiyanEntity, ZombieSaiyanModel> p) { super(p); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, ZombieSaiyanEntity e, float limb, float amount, float pt, float age, float yaw, float pitch) {
            if (e.ko() > 0) return;
            int rgb = EYE_RGB[e.form()];
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            getParentModel().renderToBuffer(pose, buf.getBuffer(RenderType.eyes(EYES)), 0xF000F0, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
        }
    }

    /** Registration (client, mod bus). */
    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Reg {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(com.xenowarriors.boss.ZombieSaiyans.ZOMBIE_SAIYAN.get(), ZombieSaiyanRenderer::new);
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(ZombieSaiyanModel.LAYER, ZombieSaiyanModel::create);
            e.registerLayerDefinition(SaiyanHairModel.LAYER, SaiyanHairModel::create);
        }
    }
}

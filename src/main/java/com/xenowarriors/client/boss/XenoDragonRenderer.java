package com.xenowarriors.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.boss.XenoDragonEntity;
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
 * Draws the Xeno Dragon: black dragon scales, glowing eyes (Ender purple; teal as a Super Saiyan, red as a god,
 * silver in Ultra Instinct), its crest per form (gold, red, silver - glowing), a Dragon Ball aura in the form's
 * colour, a faint sheen over the scales as a god (red) and in Ultra Instinct (silver), lightning around SSJ2/3, ender
 * particles in its base form, and the emerging cutscene (it grows out of a purple glow).
 */
public class XenoDragonRenderer extends MobRenderer<XenoDragonEntity, XenoDragonModel> {
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/boss/xeno_dragon.png");
    private static final ResourceLocation EYES = XenoWarriors.id("textures/entity/boss/xeno_dragon_eyes.png");
    private static final ResourceLocation HAIR = XenoWarriors.id("textures/entity/boss/saiyan_hair.png");
    public static final int[] CREST_RGB = {0x3A3046, 0xFFE070, 0xFFE878, 0xFFD860, 0xFF4058, 0xE4E8F4};
    public static final int[] EYE_RGB = {0xD65CFF, 0x40F0D0, 0x40F0D0, 0x40F0D0, 0xFF3050, 0xE8ECFF};
    public static final float SCALE = 1.6F;

    public XenoDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new XenoDragonModel(ctx.bakeLayer(XenoDragonModel.LAYER)), 1.1F);
        addLayer(new Crest(this, new XenoDragonCrestModel(ctx.bakeLayer(XenoDragonCrestModel.LAYER))));
        addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(XenoDragonEntity e) { return TEX; }

    @Override
    protected void scale(XenoDragonEntity e, PoseStack pose, float pt) {
        float s = SCALE;
        if (e.act() == XenoDragonEntity.ACT_EMERGE) {                            // takes shape over the first 1.5 s
            float done = XenoDragonEntity.EMERGE_TICKS - e.actTicks() + pt;
            s *= Mth.clamp(0.25F + done / 30F * 0.75F, 0.25F, 1F);
        }
        pose.scale(s, s, s);
    }

    @Override
    protected void setupRotations(XenoDragonEntity e, PoseStack pose, float age, float yaw, float pt) {
        super.setupRotations(e, pose, age, yaw, pt);
        if (e.ko() > 0) {                                                        // flat on its back; up in the last half second
            float k = Mth.clamp((e.ko() - pt) / 10F, 0F, 1F);
            pose.translate(0, 0.15 * k, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-85F * k));
            pose.translate(0, -0.9 * k, 0);
        } else if (e.act() == XenoDragonEntity.ACT_DIVE) {
            pose.mulPose(Axis.XP.rotationDegrees(35F));
        }
    }

    /** The aura colour now (the next form's while powering up; none while down or emerging). */
    public static int aura(XenoDragonEntity e) {
        int f = e.form(), a = e.act();
        if (e.ko() > 0 || a == XenoDragonEntity.ACT_EMERGE) return 0;
        if (a == XenoDragonEntity.ACT_POWER) return XenoDragonEntity.AURA[Math.min(XenoDragonEntity.SSJ3, f + 1)];
        if (a == XenoDragonEntity.ACT_GOD) return XenoDragonEntity.AURA[XenoDragonEntity.SSG];
        if (a == XenoDragonEntity.ACT_AWAKEN) return XenoDragonEntity.AURA[XenoDragonEntity.UI];
        if (f == XenoDragonEntity.BASE && a == XenoDragonEntity.ACT_BOMB) return 0xB050FF;
        return XenoDragonEntity.AURA[f];
    }

    @Override
    public void render(XenoDragonEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        int aura = aura(e);
        if (aura != 0) com.xenowarriors.client.DbzAura.mark(e.getId(), aura);
        effects(e);
        super.render(e, yaw, pt, pose, buf, light);
    }

    private static int lastFx;

    private static void effects(XenoDragonEntity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || e.ko() > 0) return;
        int key = e.getId() * 31 + e.tickCount;
        if (key == lastFx) return;
        lastFx = key;
        var r = e.getRandom();
        int f = e.form(), a = e.act();
        double h = e.getBbHeight();
        if ((f == XenoDragonEntity.SSJ2 || f == XenoDragonEntity.SSJ3 || a == XenoDragonEntity.ACT_POWER) && r.nextInt(2) == 0) {
            double x = e.getX() + (r.nextDouble() - .5) * 2, y = e.getY() + r.nextDouble() * h, z = e.getZ() + (r.nextDouble() - .5) * 2;
            if (!com.xenowarriors.client.compat.DmzKiParticles.spawn("ki_lightning", x, y, z, 0, 0, 0, 0x9FE8FF, 0.9F))
                for (int i = 0; i < 3; i++) mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y + i * 0.2, z, (r.nextDouble() - .5) * .3, .1, (r.nextDouble() - .5) * .3);
        }
        if (f == XenoDragonEntity.BASE && r.nextInt(3) == 0)
            mc.level.addParticle(ParticleTypes.PORTAL, e.getX() + (r.nextDouble() - .5) * 1.6, e.getY() + r.nextDouble() * h, e.getZ() + (r.nextDouble() - .5) * 1.6, (r.nextDouble() - .5), -0.3, (r.nextDouble() - .5));
        if (f == XenoDragonEntity.SSG && r.nextInt(2) == 0)
            mc.level.addParticle(ParticleTypes.FLAME, e.getX() + (r.nextDouble() - .5) * 1.4, e.getY() + r.nextDouble() * h, e.getZ() + (r.nextDouble() - .5) * 1.4, 0, 0.05, 0);
        if (f == XenoDragonEntity.UI && r.nextInt(2) == 0)
            mc.level.addParticle(ParticleTypes.END_ROD, e.getX() + (r.nextDouble() - .5) * 1.8, e.getY() + r.nextDouble() * h, e.getZ() + (r.nextDouble() - .5) * 1.8, 0, 0.04, 0);
        int rgb = aura(e);
        if (rgb != 0 && r.nextInt(3) == 0) {
            double x = e.getX() + (r.nextDouble() - .5) * 2, y = e.getY() + r.nextDouble() * 0.8, z = e.getZ() + (r.nextDouble() - .5) * 2;
            com.xenowarriors.client.compat.DmzKiParticles.spawn("ki_shedding", x, y, z, 0, 0.12, 0, rgb, 0.7F);
        }
        if (a == XenoDragonEntity.ACT_EMERGE)
            for (int i = 0; i < 4; i++) mc.level.addParticle(ParticleTypes.DRAGON_BREATH, e.getX() + (r.nextDouble() - .5) * 2, e.getY() + r.nextDouble() * h, e.getZ() + (r.nextDouble() - .5) * 2, 0, 0.02, 0);
    }

    /** The crest: tinted per form, glowing in the Saiyan / god / Ultra Instinct forms. */
    static final class Crest extends RenderLayer<XenoDragonEntity, XenoDragonModel> {
        private final XenoDragonCrestModel crest;
        Crest(RenderLayerParent<XenoDragonEntity, XenoDragonModel> p, XenoDragonCrestModel crest) { super(p); this.crest = crest; }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, XenoDragonEntity e, float limb, float amount, float pt, float age, float yaw, float pitch) {
            int f = e.form();
            if (!crest.setup(getParentModel().head, f)) return;
            int rgb = CREST_RGB[f];
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            crest.render(pose, buf.getBuffer(RenderType.entityCutoutNoCull(HAIR)), 0xF000F0, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
            float pulse = 0.25F + 0.1F * Mth.sin(age * 0.3F);
            crest.render(pose, buf.getBuffer(RenderType.eyes(HAIR)), 0xF000F0, OverlayTexture.NO_OVERLAY, r * pulse, g * pulse, b * pulse, 1F);
        }
    }

    /** Glowing eyes (shut while down), and the god / Ultra Instinct sheen over its scales. */
    static final class Glow extends RenderLayer<XenoDragonEntity, XenoDragonModel> {
        Glow(RenderLayerParent<XenoDragonEntity, XenoDragonModel> p) { super(p); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, XenoDragonEntity e, float limb, float amount, float pt, float age, float yaw, float pitch) {
            if (e.ko() > 0) return;
            int f = e.form();
            int rgb = EYE_RGB[f];
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            getParentModel().renderToBuffer(pose, buf.getBuffer(RenderType.eyes(EYES)), 0xF000F0, OverlayTexture.NO_OVERLAY, r, g, b, 1F);
            if (f == XenoDragonEntity.SSG || f == XenoDragonEntity.UI) {
                float s = (f == XenoDragonEntity.UI ? 0.5F : 0.4F) + 0.08F * Mth.sin(age * 0.15F);
                float sr = f == XenoDragonEntity.UI ? 0.85F : 1F, sg = f == XenoDragonEntity.UI ? 0.9F : 0.25F, sb = f == XenoDragonEntity.UI ? 1F : 0.3F;
                getParentModel().renderToBuffer(pose, buf.getBuffer(RenderType.eyes(TEX)), 0xF000F0, OverlayTexture.NO_OVERLAY, sr * s, sg * s, sb * s, 1F);
            }
        }
    }

    /** Registration (client, mod bus). */
    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class XenoDragonReg {
        @SubscribeEvent
        public static void xenoDragonRenderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(com.xenowarriors.boss.XenoDragons.XENO_DRAGON.get(), XenoDragonRenderer::new);
            e.registerEntityRenderer(com.xenowarriors.boss.XenoDragons.DRAGON_BOMB.get(), DragonBombRenderer::new);
        }

        @SubscribeEvent
        public static void xenoDragonLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(XenoDragonModel.LAYER, XenoDragonModel::create);
            e.registerLayerDefinition(XenoDragonCrestModel.LAYER, XenoDragonCrestModel::create);
        }
    }
}

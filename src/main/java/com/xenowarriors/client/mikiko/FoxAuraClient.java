package com.xenowarriors.client.mikiko;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.FirstPersonHead;
import com.xenowarriors.client.compat.ShaderCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Mikiko's Shiyoken fox aura. With DragonMineZ, Mikiko whose DragonMineZ race is Human, in its "Shiyoken" forms:
 * <ul>
 * <li>Level 1 - Shiyoken: a blazing orange-red fire aura, flame fox spirits running in circles round him, embers,
 *     and glowing fire lines over his outfit.</li>
 * <li>Level 2 - Shin Shiyoken (and Chou Shiyoken, bigger still): more and bigger fire foxes, a huge flame fox head
 *     rising behind him, black orbs with glowing rims floating round him, flame ribbons, and brighter lines all over
 *     his outfit.</li>
 * </ul>
 * Everything is drawn on each player's own game from DragonMineZ's synced form, so everyone nearby sees it. The aura
 * is drawn in the world (not by the body's renderer), so it shows the same with Epic Fight drawing him; with an
 * Oculus shader pack the glowing shapes switch to plain see-through drawing (see ShaderCompat). Cheap: a handful
 * of quads and two or three particles a tick, only within 64 blocks.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FoxAuraClient {
    private static final ResourceLocation FOX = XenoWarriors.id("textures/entity/aura/fox_spirit.png");
    private static final ResourceLocation HEAD = XenoWarriors.id("textures/entity/aura/fox_head.png");
    private static final ResourceLocation ORB = XenoWarriors.id("textures/entity/aura/void_orb.png");
    private static final ResourceLocation RIBBON = XenoWarriors.id("textures/entity/aura/fox_ribbon.png");
    private static final ResourceLocation SHELL = XenoWarriors.id("textures/entity/aura/dbz_aura.png");
    private static final ResourceLocation SHELL_A = XenoWarriors.id("textures/entity/aura/dbz_aura_a.png");
    private static final ResourceLocation TRIM1 = XenoWarriors.id("textures/entity/aura/fox_trim1.png");
    private static final ResourceLocation TRIM2 = XenoWarriors.id("textures/entity/aura/fox_trim2.png");
    private static final double RANGE = 64;

    /** (tests) forces a level on everyone's Mikiko (-1 = off, read DragonMineZ). */
    public static int forceLevel = -1;
    /** (tests) what the last frame drew: auras, fox spirits, orbs. */
    public static int drawnAuras, drawnFoxes, drawnOrbs, drawnHeads, trimDrawn;

    private FoxAuraClient() {}

    /** 0 none, 1 Shiyoken, 2 Shin Shiyoken, 3 Chou Shiyoken. */
    public static int level(Player p) {
        if (forceLevel >= 0) return p instanceof AbstractClientPlayer cp && ClientCharacterData.get(cp) == com.xenowarriors.character.CharacterType.MIKIKO ? forceLevel : 0;
        if (!net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) return 0;
        return com.xenowarriors.client.compat.DmzForms.shiyokenLevel(p);
    }

    private static boolean shown(Player p, Minecraft mc) {
        return !p.isInvisible() && !p.isSpectator() && p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < RANGE * RANGE
                && !(p instanceof AbstractClientPlayer cp && FirstPersonHead.ownFirstPerson(cp));
    }

    // ---------------------------------------------------------------- the aura in the world

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int auras = 0, foxes = 0, orbs = 0, heads = 0;
        MultiBufferSource.BufferSource buffers = null;
        for (Player p : mc.level.players()) {
            int lvl = level(p);
            if (lvl <= 0 || !shown(p, mc)) continue;
            if (buffers == null) buffers = mc.renderBuffers().bufferSource();
            int[] n = draw(event.getPoseStack(), event.getCamera(), buffers, p, lvl, event.getPartialTick());
            auras++; foxes += n[0]; orbs += n[1]; heads += n[2];
        }
        if (buffers != null) buffers.endBatch();
        drawnAuras = auras; drawnFoxes = foxes; drawnOrbs = orbs; drawnHeads = heads;
    }

    /** Glowing fire: added on top (glows) without a shader pack, plain see-through with one. */
    private static RenderType fire(ResourceLocation tex) {
        return ShaderCompat.packInUse() ? RenderType.entityTranslucent(tex) : RenderType.eyes(tex);
    }

    private static int[] draw(PoseStack pose, Camera cam, MultiBufferSource buffers, Player p, int lvl, float partial) {
        float age = p.tickCount + partial;
        boolean two = lvl >= 2;
        float s = (p.getBbHeight() / 1.8F) * (lvl == 3 ? 1.6F : lvl == 2 ? 1.38F : 1.17F);    // (DragonMineZ's form sizes)
        Vec3 at = new Vec3(Mth.lerp(partial, p.xo, p.getX()), Mth.lerp(partial, p.yo, p.getY()), Mth.lerp(partial, p.zo, p.getZ()))
                .subtract(cam.getPosition());
        boolean pack = ShaderCompat.packInUse();
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);

        // the fire aura: flame sheets behind and over him, facing the camera
        pose.pushPose();
        pose.translate(0, -0.3F * s, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180F - cam.getYRot()));
        int frame = (int) (age * 0.8F) % 8;
        float big = two ? 1.25F : 1F;
        VertexConsumer tr = buffers.getBuffer(pack ? RenderType.entityTranslucent(SHELL_A) : RenderType.entityTranslucentEmissive(SHELL_A));
        sheet(tr, pose, -0.45F, 2.7F * s * big, 3.4F * s * big, frame, 1F, 0.32F, 0.06F, 0.75F);
        sheet(tr, pose, 0.45F, 2.5F * s * big, 3.2F * s * big, (frame + 5) % 8, 1F, 0.45F, 0.1F, 0.14F);
        VertexConsumer add = buffers.getBuffer(fire(SHELL));
        float k = pack ? 0.5F : 1F;
        sheet(add, pose, -0.4F, 2.7F * s * big, 3.4F * s * big, frame, 0.6F * k, 0.2F * k, 0.04F * k, pack ? 0.5F : 1F);
        sheet(add, pose, -0.34F, 1.9F * s * big, 2.7F * s * big, (frame + 3) % 8, 0.75F * k, 0.55F * k, 0.25F * k, pack ? 0.5F : 1F);
        pose.popPose();

        // the huge fox head rising behind him (level 2)
        int heads = 0;
        if (two) {
            Vector3f look = cam.getLookVector();
            Vec3 back = new Vec3(look.x(), 0, look.z()).normalize().scale(0.9);
            pose.pushPose();
            pose.translate(back.x, 1.75F * s + Mth.sin(age * 0.05F) * 0.08F, back.z);
            pose.mulPose(cam.rotation());
            float hs = (lvl == 3 ? 2.6F : 2.1F) * s;
            float hf = (int) (age * 0.15F) % 2;
            quad(buffers.getBuffer(fire(HEAD)), pose, hs, hs, hf / 2F, (hf + 1) / 2F, 0F, 1F, 1F, 0.8F, 0.6F, pack ? 0.55F : 0.85F, pack);
            pose.popPose();
            heads = 1;
        }

        // fox spirits running round him
        int nFox = lvl == 3 ? 9 : two ? 7 : 4;
        Vector3f left = cam.getLeftVector();
        VertexConsumer fox = buffers.getBuffer(fire(FOX));
        for (int i = 0; i < nFox; i++) {
            float dir = i % 3 == 2 ? -1F : 1F;
            float a = age * 0.055F * dir + i * Mth.TWO_PI / nFox;
            float r = (1.0F + 0.18F * Mth.sin(age * 0.05F + i * 2.1F)) * s * (two ? 1.15F : 1F);
            float y = s * (0.35F + 0.75F * (0.5F + 0.5F * Mth.sin(age * 0.035F + i * 1.7F)) + (i % 2) * 0.35F);
            float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
            // moving right or left across the screen? (the sprite runs to the right)
            float vx = -Mth.sin(a) * dir, vz = Mth.cos(a) * dir;
            boolean flip = vx * -left.x() + vz * -left.z() < 0;
            float size = (two ? 0.85F + (i % 3) * 0.22F : 0.7F) * s;
            int f = (int) (age * 0.35F + i) % 4;
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(cam.rotation());
            float u0 = f / 4F, u1 = (f + 1) / 4F;
            quad(fox, pose, size, size, flip ? u1 : u0, flip ? u0 : u1, 0F, 1F, 1F, 0.75F, 0.45F, pack ? 0.65F : 0.95F, pack);
            pose.popPose();
        }

        int orbs = 0;
        if (two) {
            // black orbs with glowing rims floating round him
            VertexConsumer orb = buffers.getBuffer(RenderType.entityTranslucent(ORB));
            int nOrb = lvl == 3 ? 7 : 5;
            for (int i = 0; i < nOrb; i++) {
                float a = -age * 0.02F + i * Mth.TWO_PI / nOrb + 0.4F;
                float r = 1.55F * s;
                float y = s * (0.4F + 1.6F * ((i * 0.37F) % 1F)) + Mth.sin(age * 0.06F + i) * 0.12F;
                pose.pushPose();
                pose.translate(Mth.cos(a) * r, y, Mth.sin(a) * r);
                pose.mulPose(cam.rotation());
                float os = (0.26F + 0.06F * (i % 3)) * s;
                quad(orb, pose, os, os, 0F, 1F, 0F, 1F, 1F, 1F, 1F, 0.95F, true);
                pose.popPose();
                orbs++;
            }
            // flame ribbons sweeping round him
            VertexConsumer rib = buffers.getBuffer(fire(RIBBON));
            for (int i = 0; i < 4; i++) {
                float a = age * 0.09F + i * Mth.HALF_PI;
                float r = 1.25F * s;
                pose.pushPose();
                pose.translate(Mth.cos(a) * r, s * (0.3F + i * 0.45F), Mth.sin(a) * r);
                pose.mulPose(cam.rotation());
                pose.mulPose(Axis.ZP.rotation(Mth.sin(age * 0.07F + i) * 0.6F + (i % 2 == 0 ? 0.35F : -0.35F)));
                quad(rib, pose, 1.6F * s, 0.32F * s, 0F, 1F, 0F, 1F, 1F, 0.6F, 0.25F, pack ? 0.5F : 0.8F, pack);
                pose.popPose();
            }
        }
        pose.popPose();
        return new int[]{nFox, orbs, heads};
    }

    /** A camera-facing sprite centred on the current origin (particle convention: local -x is the texture's right). */
    private static void quad(VertexConsumer vc, PoseStack pose, float w, float h, float u0, float u1, float v0, float v1,
                             float r, float g, float b, float a, boolean alphaBlend) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        if (!alphaBlend) { r *= a; g *= a; b *= a; a = 1F; }                   // (added on top: brightness is the fade)
        float x = w / 2F, y = h / 2F;
        float[][] c = {{-x, -y, u1, v1}, {-x, y, u1, v0}, {x, y, u0, v0}, {x, -y, u0, v1}};
        for (float[] q : c)
            vc.vertex(m, q[0], q[1], 0).color(r, g, b, a).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0).normal(n, 0, 0, 1).endVertex();
    }

    /** One of the aura's flame sheets (DbzAura's sheet), both sides. */
    private static void sheet(VertexConsumer vc, PoseStack pose, float z, float width, float height, int frame, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float u0 = frame / 8F, u1 = (frame + 1) / 8F, x = width / 2;
        float[][] c = {{-x, 0, u0, 1}, {x, 0, u1, 1}, {x, height, u1, 0}, {-x, height, u0, 0}};
        for (int side = 0; side < 2; side++)
            for (int i = 0; i < 4; i++) {
                float[] q = c[side == 0 ? i : 3 - i];
                vc.vertex(m, q[0], q[1], z).color(r, g, b, a).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(0xF000F0).normal(n, 0, 0, 1).endVertex();
            }
    }

    // ---------------------------------------------------------------- embers

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        for (Player p : mc.level.players()) {
            int lvl = level(p);
            if (lvl <= 0 || !shown(p, mc)) continue;
            var rnd = p.getRandom();
            int n = lvl >= 2 ? 3 : 2;
            double s = p.getBbHeight() / 1.8 * (lvl >= 2 ? 1.38 : 1.17);
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = (0.35 + rnd.nextDouble() * 0.9) * s;
                mc.level.addParticle(i == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, p.getX() + Math.cos(a) * r,
                        p.getY() + rnd.nextDouble() * 2.0 * s, p.getZ() + Math.sin(a) * r,
                        (rnd.nextDouble() - 0.5) * 0.03, 0.04 + rnd.nextDouble() * 0.06, (rnd.nextDouble() - 0.5) * 0.03);
            }
            if (lvl >= 2 && p.tickCount % 3 == 0)
                mc.level.addParticle(ParticleTypes.LAVA, p.getX(), p.getY() + 1.0 * s, p.getZ(), 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- glowing lines over his outfit

    /** Fire lines glowing over his clothes: the body drawn again, a touch bigger, with only the lines. */
    public static final class TrimLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public TrimLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing,
                           float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
            if (ClientCharacterData.isPreviewing() || FirstPersonHead.inGui && forceLevel < 0) return;
            int lvl = level(p);
            if (lvl <= 0 || p.isInvisible()) return;
            float pulse = 0.75F + 0.25F * Mth.sin(age * 0.25F);
            pose.pushPose();
            pose.translate(0, 0.75F, 0);
            pose.scale(1.09F, 1.02F, 1.12F);
            pose.translate(0, -0.75F, 0);
            boolean head = getParentModel().head.visible, hat = getParentModel().hat.visible;
            getParentModel().head.visible = false;
            getParentModel().hat.visible = false;
            try {
                getParentModel().renderToBuffer(pose, buffers.getBuffer(ShaderCompat.glow(lvl >= 2 ? TRIM2 : TRIM1)), 0xF000F0,
                        OverlayTexture.NO_OVERLAY, 1F, lvl >= 2 ? 0.85F : 0.7F, 0.45F, pulse);
            } finally {
                getParentModel().head.visible = head;
                getParentModel().hat.visible = hat;
                pose.popPose();
            }
            trimDrawn++;
        }
    }
}

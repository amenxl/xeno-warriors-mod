package com.xenowarriors.client.bot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.render.DetailModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/** A bot teammate looks exactly like that character: their skin, 3D details and face. Downed bots lie on the ground. */
public class BotRenderer extends HumanoidMobRenderer<PartyBotEntity, PlayerModel<PartyBotEntity>> {
    private static final Map<CharacterType, DetailModel> MODELS = new EnumMap<>(CharacterType.class);

    private final PlayerModel<PartyBotEntity> wide, girl;

    public BotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wide = this.model;
        girl = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new Details(this));
        addLayer(new com.xenowarriors.client.PartyHats.Layer<>(this));
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new net.minecraft.client.model.HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
    }

    private static DetailModel model(CharacterType t) {
        DetailModel m = MODELS.get(t);
        if (m == null && !MODELS.containsKey(t)) {      // (a missing model is remembered too - not looked up again every frame)
            m = DetailModel.loadPath("/assets/" + XenoWarriors.MOD_ID + "/models/detail/" + t.getId() + ".json");
            MODELS.put(t, m);
        }
        return m;
    }

    private static int failures;
    private static boolean plainWarned;

    /** Girl bots get the girls' slim body (see FemaleBody). */
    @Override
    public void render(PartyBotEntity e, float yaw, float pt, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        // drawn as a player of that character (see BotAvatar): their animations, faces, wings and details
        if (!BotAvatars.disabled) {
            BotAvatar a = BotAvatars.of(e);
            if (a != null) {
                a.sync(e);
                var r = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(a);
                PoseStack.Pose base = pose.last();
                pose.pushPose();
                try {
                    r.render(a, yaw, pt, pose, buffers, light);
                    NimbusUnder.render(e, yaw, pt, pose, buffers, light);
                    failures = 0;
                    return;
                } catch (Throwable t) {
                    // (one bad frame - another mod's renderer caught mid-batch, say - skips this bot for a frame; only if it
                    //  keeps failing do bots switch to the plain look for the session)
                    if (++failures >= 200) { BotAvatars.disabled = true; XenoWarriors.LOGGER.error("Drawing bots as their characters keeps failing - using the plain bot look", t); }
                    else if (failures == 1) XenoWarriors.LOGGER.warn("Drawing a bot as its character failed this frame (skipped)", t);
                    return;
                } finally {
                    com.xenowarriors.client.render.PoseGuard.restore(pose, base);   // (all the way back: a renderer that threw may have left its own pushes on the stack)
                }
            }
        }
        boolean g = com.xenowarriors.client.FemaleBody.is(e.character(), "");
        this.model = g ? girl : wide;
        com.xenowarriors.client.FemaleBody.npcGirl = g;
        PoseStack.Pose base = pose.last();
        try { super.render(e, yaw, pt, pose, buffers, light); }
        catch (Throwable t) { com.xenowarriors.client.render.PoseGuard.restore(pose, base); if (!plainWarned) { plainWarned = true; XenoWarriors.LOGGER.warn("Drawing a bot's plain look failed (another mod's overlay?) - skipped", t); } }   // (never a crash: Epic Fight's health bar has no data for bots)
        finally { com.xenowarriors.client.FemaleBody.npcGirl = false; this.model = wide; }
    }

    @Override
    public ResourceLocation getTextureLocation(PartyBotEntity e) { return com.xenowarriors.client.cel.Cel.tex(e.character().getSkin()); }

    @Override
    protected void setupRotations(PartyBotEntity e, PoseStack pose, float bob, float yaw, float partial) {
        super.setupRotations(e, pose, bob, yaw, partial);
        if (e.downed()) {                                           // lying on their back, waiting for a revive
            pose.translate(0, 0.25, 0);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90F));
            pose.translate(0, -0.9, 0);
        }
    }

    private static class Details extends RenderLayer<PartyBotEntity, PlayerModel<PartyBotEntity>> {
        Details(RenderLayerParent<PartyBotEntity, PlayerModel<PartyBotEntity>> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, PartyBotEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float yaw, float pitch) {
            if (e.isInvisible()) return;
            CharacterType t = e.character();
            DetailModel m = model(t);
            if (m == null) return;
            PlayerModel<PartyBotEntity> p = getParentModel();
            m.head.copyFrom(p.head); m.body.copyFrom(p.body);
            m.rightArm.copyFrom(p.rightArm); m.leftArm.copyFrom(p.leftArm);
            m.rightLeg.copyFrom(p.rightLeg); m.leftLeg.copyFrom(p.leftLeg);
            com.xenowarriors.client.LimbBend.copy(p.rightArm, m.rightArm);
            com.xenowarriors.client.LimbBend.copy(p.leftArm, m.leftArm);
            com.xenowarriors.client.LimbBend.copy(p.rightLeg, m.rightLeg);
            com.xenowarriors.client.LimbBend.copy(p.leftLeg, m.leftLeg);
            // Rhalix's coat tails swing with the legs like on a player (standing still they'd let the legs through)
            m.animateCoat(p.rightLeg.xRot, p.leftLeg.xRot, p.rightLeg.zRot, -p.leftLeg.zRot, 0F, 0F, age, e.isPassenger() || p.riding);
            m.untwistCoat(p.body.yRot);
            int overlay = LivingEntityRenderer.getOverlayCoords(e, 0F);
            m.root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.client.cel.Cel.tex(t.getDetailTexture()))), light, overlay);
            if (com.xenowarriors.client.cel.Cel.on()) {
                com.xenowarriors.client.cel.CelOutline.body(p, pose, buffers, light, overlay);
                com.xenowarriors.client.cel.CelOutline.details(m, pose, buffers, light, overlay);
            }
            if (m.faceRoot != null) {
                m.faceHead.copyFrom(p.head);
                m.faceRoot.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.client.pod.PodFace.withFace(com.xenowarriors.client.render.CharacterDetailLayer.baseFace(t), e))), light, overlay);
            }
        }
    }
}

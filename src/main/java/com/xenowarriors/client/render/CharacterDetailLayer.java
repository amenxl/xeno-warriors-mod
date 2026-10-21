package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.FlightTracker;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws each character's 3D details on top of the player model. The detail bones copy the
 * player model's pose every frame, so hair follows the head, sleeves follow the arms, etc.
 * Rhalix's wings come out when he's in the air (flying, gliding, falling) or casting Kishi,
 * like in the show, and are always shown on the selection screen.
 */
public class CharacterDetailLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final Map<CharacterType, DetailModel> models = new EnumMap<>(CharacterType.class);

    public CharacterDetailLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
        for (CharacterType type : CharacterType.PLAYABLE) {
            DetailModel model = DetailModel.load(type);
            if (model != null) models.put(type, model);
        }
    }

    /**
     * Which face to show: sleeping/snoring (Mikiko), hurt when hit, shouting while using a skill, fierce while
     * attacking, sick when poisoned or weakened, a blink now and then - otherwise the normal face (null).
     */
    private static String expression(AbstractClientPlayer p, CharacterType type) {
        if (p.isSleeping()) {
            if (type != CharacterType.MIKIKO) return "blink";
            return (p.tickCount % 40) < 22 ? "snore" : "sleep";   // snores in time with the z's
        }
        if (p.hurtTime > 0) return "hurt";
        if (ClientSkillState.currentAnim(p) != null) return "shout";
        if (p.swinging || com.xenowarriors.client.mikiko.MikikoAnimations.attacking(p)) return "fierce";
        if (p.isUsingItem() && p.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.EAT
                || p.isUsingItem() && p.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK) {
            return (p.getTicksUsingItem() / 4) % 2 == 0 ? "eat" : "eat2";      // munching
        }
        if (p.getHealth() <= p.getMaxHealth() * 0.3F && p.isAlive()) return "strained";
        if (ClientSkillState.feelingSick(p)) return "sick";
        if (p == net.minecraft.client.Minecraft.getInstance().player && com.xenowarriors.client.StatToasts.justLeveledUp()) return "sparkle";
        var pos = p.blockPosition();
        boolean snowing = p.level().isRaining() && p.level().canSeeSky(pos) && p.level().getBiome(pos).value().coldEnoughToSnow(pos);
        if (p.getTicksFrozen() > 0 || snowing) return "freezing";
        boolean still = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.05;
        if (still && p.level().isRainingAt(pos.above())) return "annoyed";
        String mood = type == CharacterType.MIKIKO ? com.xenowarriors.client.mikiko.MikikoAnimations.mood(p) : null;
        if (type == CharacterType.RHALIX) {
            if (FlightTracker.isFlying(p)) mood = "smug";          // right at home in the air
            else if (p.isSprinting()) mood = "focused";
        }
        int bt = p.tickCount + p.getId() * 37;
        boolean blink = bt % 97 < 3 || (bt % 233 >= 8 && bt % 233 < 11);
        if (mood != null) {
            // moods with open eyes still blink now and then
            boolean eyesOpen = !mood.equals("happy") && !mood.equals("yawn") && !mood.equals("robot");
            return blink && eyesOpen ? "blink" : mood;
        }
        int t = p.tickCount + p.getId() * 37;
        if (t % 97 < 3 || (t % 233 >= 8 && t % 233 < 11)) return "blink";   // blinks every few seconds, sometimes twice
        return null;
    }

    private static final java.util.Set<String> OWN_MOUTH = java.util.Set.of("sleep", "snore", "eat", "eat2", "shout", "hurt", "yawn", "panic");

    /** 0 = not talking, 1-3 = how wide the mouth is open (louder = wider, flapping between syllables). */
    private static int talkingMouth(AbstractClientPlayer p, String expr) {
        if (expr != null && OWN_MOUTH.contains(expr)) return 0;
        boolean self = p == net.minecraft.client.Minecraft.getInstance().player;
        float level = Math.max(com.xenowarriors.voice.VoiceLevels.level(p.getUUID()),
                self ? com.xenowarriors.voice.VoiceLevels.level(com.xenowarriors.voice.VoiceLevels.LOCAL) : 0F);
        if (level < 0.04F) return 0;
        int open = level < 0.22F ? 1 : level < 0.5F ? 2 : 3;
        // flap: every few ticks the mouth closes a step, so it moves like real speech
        int phase = (p.tickCount + p.getId()) % 5;
        if (phase == 0) open = Math.max(0, open - 2);
        else if (phase == 3) open = Math.max(1, open - 1);
        return open;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        CharacterType type = ClientCharacterData.get(player);
        DetailModel model = models.get(type);
        if (model == null) return;

        PlayerModel<AbstractClientPlayer> parent = getParentModel();
        model.head.copyFrom(parent.head);
        model.body.copyFrom(parent.body);
        model.rightArm.copyFrom(parent.rightArm);
        model.leftArm.copyFrom(parent.leftArm);
        model.rightLeg.copyFrom(parent.rightLeg);
        model.leftLeg.copyFrom(parent.leftLeg);

        if (model.hasWings()) {
            boolean preview = ClientCharacterData.isPreviewing();
            boolean flying = FlightTracker.isFlying(player);
            boolean falling = !player.onGround() && !player.isInWater() && player.fallDistance > 1.5F;
            boolean kishi = ClientSkillState.isCastingKishi(player.getUUID());
            float spread = com.xenowarriors.client.SkillAnimations.wingSpread(player, ageInTicks);
            model.setupWings(preview || flying || falling || kishi || spread > 0F, flying || falling, ageInTicks, spread);
        }

        // hood up (SR hoodie): the hair tucks under it, only the front bangs show
        if (model.hair != null) {
            model.hair.visible = !(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
                    instanceof com.xenowarriors.item.SrHoodieItem);
        }

        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(type.getDetailTexture()));
        model.root.render(poseStack, buffer, packedLight, overlay);

        if (model.faceRoot != null) {
            model.faceHead.copyFrom(parent.head);
            String expr = expression(player, type);
            var faceTexture = expr == null ? type.getFaceTexture()
                    : com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_face_" + expr + ".png");
            VertexConsumer faceBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(faceTexture));
            model.faceRoot.render(poseStack, faceBuffer, packedLight, overlay);

            // talking in voice chat: an animated mouth drawn over the face
            int mouth = talkingMouth(player, expr);
            if (mouth > 0) {
                var mouthTexture = com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_mouth_" + mouth + ".png");
                model.faceRoot.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(mouthTexture)), packedLight, overlay);
            }
        }
    }
}

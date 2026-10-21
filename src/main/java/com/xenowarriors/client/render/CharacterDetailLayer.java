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
        if (type == CharacterType.MIKIKO && com.xenowarriors.client.mikiko.MikikoAnimations.exhausted(p)) {
            // worn out: droopy eyes, sweat, panting
            return (p.tickCount / 7) % 2 == 0 ? "exhausted" : "exhausted2";
        }
        if (p.getHealth() <= p.getMaxHealth() * 0.3F && p.isAlive()) return "strained";
        if (ClientSkillState.feelingSick(p)) return "sick";
        if (p == net.minecraft.client.Minecraft.getInstance().player && com.xenowarriors.client.StatToasts.justLeveledUp()) return "sparkle";
        var pos = p.blockPosition();
        boolean snowing = p.level().isRaining() && p.level().canSeeSky(pos) && p.level().getBiome(pos).value().coldEnoughToSnow(pos);
        if (p.getTicksFrozen() > 0 || snowing) return "freezing";
        if (p.level().isRainingAt(pos.above()) && !p.isUnderWater()) {
            // caught in the rain: soaked, drops running down the face (two frames so they drip)
            int bt = p.tickCount + p.getId() * 37;
            if (bt % 97 < 3) return "blink";
            return (bt / 9) % 2 == 0 ? "rain" : "rain2";
        }
        String mood = type == CharacterType.MIKIKO ? com.xenowarriors.client.mikiko.MikikoAnimations.mood(p) : null;
        if (type == CharacterType.RHALIX) {
            if (com.xenowarriors.client.RhalixWingsClient.hyped(p, 0F) > 0.5F) {
                String[] h = {"headbang", "grin", "sing", "excited", "headbang", "wink"};
                return h[(p.tickCount / 20) % h.length];
            }
            if (com.xenowarriors.client.RhalixWingsClient.sadVibe(p, 0F) > 0.5F) {
                String[] l = {"sad", "melancholy", "sad", "melancholy", "crying"};
                return l[(p.tickCount / 60) % l.length];
            }
            if (com.xenowarriors.client.RhalixWingsClient.chill(p, 0F) > 0.5F) {
                String[] c = {"calm", "smug", "whistle", "hum", "calm"};
                return c[(p.tickCount / 50) % c.length];
            }
            if (com.xenowarriors.client.RhalixWingsClient.smoking(p, 0F) > 0.5F) {
                float st = com.xenowarriors.client.RhalixWingsClient.smokeTime(p, p.tickCount);
                return st >= 56 && st < 84 ? "hum" : "calm";      // eyes closed, breathing out
            }
            if (com.xenowarriors.client.RhalixWingsClient.gravityDance(p, 0F) > 0.5F) {
                // glitching out during the stutter-snaps, otherwise smug and loving it
                float gt = com.xenowarriors.client.RhalixWingsClient.gravityTime(p, p.tickCount) % 160F;
                if (gt >= 40F && gt < 66F) return "glitch";
                String[] g = {"smug", "grin", "cool", "wink", "excited"};
                return g[(p.tickCount / 40) % g.length];
            }
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
        return idleFace(p);
    }

    /**
     * Neutral faces so they don't stare straight ahead all the time: glancing around, looking up, thinking,
     * calm, humming to themselves - about half the time it's just the normal face.
     */
    private static String idleFace(AbstractClientPlayer p) {
        int seg = (p.tickCount + p.getId() * 53) / 70;
        long h = (seg * 2654435761L) ^ (p.getId() * 40503L);
        int pick = (int) Math.floorMod(h >>> 5, 20);
        boolean still = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.05;
        return switch (pick) {
            case 10, 11 -> "glance_l";
            case 12, 13 -> "glance_r";
            case 14 -> "look_up";
            case 15 -> still ? "thinking" : null;
            case 16, 17 -> "calm";
            case 18 -> still ? "hum" : null;
            default -> null;
        };
    }

    private static final java.util.Map<Integer, float[]> HAIR = new java.util.HashMap<>();

    /** which = 0: how far the hair is blown back, 1: how much it flutters (smoothed so it eases in and out). */
    private static float hairStream(AbstractClientPlayer p, int which) {
        float[] h = HAIR.computeIfAbsent(p.getId(), k -> new float[3]);
        if (which == 0) {
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            float speed = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / 0.4);
            boolean flying = FlightTracker.isFlying(p) || p.isFallFlying();
            boolean air = !p.onGround() && !p.isInWater();
            float stream = speed * (flying ? 0.65F : p.isSprinting() ? 0.38F : 0.14F) + (air && !flying ? 0.08F : 0F);
            float flutter = (flying ? 0.15F : p.isSprinting() ? 0.1F : 0.035F) * Math.min(1F, speed * 1.4F) + (air ? 0.03F : 0F);
            h[0] += (stream - h[0]) * 0.12F;
            h[1] += (flutter - h[1]) * 0.12F;
            return h[0];
        }
        return h[1];
    }

    private static final java.util.Map<java.util.UUID, Integer> TORN = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setTorn(java.util.UUID player, int torn) {
        if (torn <= 0) TORN.remove(player);
        else TORN.put(player, torn);
    }

    /** 0 = fine, 1 = hurt (half health or less), 2 = badly hurt (a quarter or less). */
    private static int damageLevel(AbstractClientPlayer p) {
        if (!p.isAlive() || p.isCreative()) return 0;
        float hp = p.getHealth() / Math.max(1F, p.getMaxHealth());
        return hp <= 0.25F ? 2 : hp <= 0.5F ? 1 : 0;
    }

    private static final java.util.Set<String> OWN_MOUTH = java.util.Set.of("sleep", "snore", "eat", "eat2", "shout", "hurt", "yawn", "panic");

    /** 0 = not talking, otherwise the mouth shape (1-5) following the voice. */
    private static int talkingMouth(AbstractClientPlayer p, String expr) {
        if (expr != null && OWN_MOUTH.contains(expr)) return 0;
        int mouth = com.xenowarriors.voice.VoiceLevels.mouth(p.getUUID());
        if (p == net.minecraft.client.Minecraft.getInstance().player) {
            mouth = Math.max(mouth, com.xenowarriors.voice.VoiceLevels.mouth(com.xenowarriors.voice.VoiceLevels.LOCAL));
        }
        return mouth;
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
            com.xenowarriors.client.RhalixWingsClient.pose(model, player, preview, flying, falling, kishi, spread, ageInTicks);
        }

        // hood up (SR hoodie): the hair tucks under it, only the front bangs show
        if (model.hair != null) {
            model.hair.visible = !(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
                    instanceof com.xenowarriors.item.SrHoodieItem);
        }

        // Rhalix's cigarette while he's on his smoke break
        if (type == CharacterType.RHALIX && com.xenowarriors.client.RhalixWingsClient.smoking(player, partialTick) > 0.3F) {
            Cigarette.render(parent.rightArm, poseStack, buffers, packedLight);
        }

        // hair streaming back and fluttering when running or flying
        // (far away you can't see it anyway: skip the work)
        boolean nearCam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(player.position()) < 32 * 32;
        if (nearCam) model.animateHair(hairStream(player, 0), hairStream(player, 1), ageInTicks);
        else model.animateHair(0F, 0F, ageInTicks);

        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        // torn clothes (explosions): holes ripped in the clothing, skin showing through, strips hanging off
        int torn = ClientCharacterData.isPreviewing() ? 0 : TORN.getOrDefault(player.getUUID(), 0);
        var detailTex = torn > 0 ? com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_details_torn" + torn + ".png")
                : type.getDetailTexture();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(detailTex));
        model.root.render(poseStack, buffer, packedLight, overlay);
        if (torn > 0) {
            parent.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(com.xenowarriors.XenoWarriors.id(
                    "textures/entity/character/" + type.getId() + "_torn" + torn + ".png"))), packedLight, overlay, 1F, 1F, 1F, 1F);
            BattleDamageModel.get().renderCloth(parent, poseStack, buffers, packedLight, overlay, Math.min(2, torn),
                    type == CharacterType.MIKIKO ? 0x3A3440 : 0x7A3AA8);
        }

        // battle damage: scratches, grime and torn clothes as they get hurt
        int damage = damageLevel(player);
        if (damage > 0 && !ClientCharacterData.isPreviewing()) {
            String base = "textures/entity/character/" + type.getId();
            parent.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(
                    com.xenowarriors.XenoWarriors.id(base + "_damage" + damage + ".png"))), packedLight, overlay, 1F, 1F, 1F, 1F);
            model.root.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(
                    com.xenowarriors.XenoWarriors.id(base + "_details_damage" + damage + ".png"))), packedLight, overlay);
            // 3D: bandages and torn, hanging cloth
            BattleDamageModel.get().render(parent, poseStack, buffers, packedLight, overlay, damage,
                    type == CharacterType.MIKIKO ? 0x3A3440 : 0x7A3AA8);
        }

        if (model.faceRoot != null) {
            model.faceHead.copyFrom(parent.head);
            String expr = expression(player, type);
            var faceTexture = expr == null ? type.getFaceTexture()
                    : com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_face_" + expr + ".png");
            VertexConsumer faceBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(faceTexture));
            model.faceRoot.render(poseStack, faceBuffer, packedLight, overlay);

            if (damage > 0 && !ClientCharacterData.isPreviewing()) {
                model.faceRoot.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(com.xenowarriors.XenoWarriors.id(
                        "textures/entity/character/" + type.getId() + "_face_damage" + damage + ".png"))), packedLight, overlay);
            }

            // talking in voice chat: an animated mouth drawn over the face
            int mouth = talkingMouth(player, expr);
            if (mouth > 0) {
                var mouthTexture = com.xenowarriors.XenoWarriors.id("textures/entity/character/" + type.getId() + "_mouth_" + mouth + ".png");
                model.faceRoot.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(mouthTexture)), packedLight, overlay);
            }
        }
    }
}

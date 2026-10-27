package com.xenowarriors.client.portrait;

import com.xenowarriors.client.LimbBend;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Hero poses for the character portraits (PortraitScreen): one still pose per character that fits them. Applied last
 * in the player model's setupAnim while a portrait is being drawn, so it wins over idle animations.
 *
 * Each pose: head x,y,z | body x,y | right arm x,y,z | left arm x,y,z | right leg x,z | left leg x,z |
 * elbows right,left | knees right,left (radians; elbows negative = forearm up, knees positive = shin back).
 */
public final class PortraitPoses {
    /** The character id being posed (null = not drawing a portrait). */
    public static String active;

    private static final Map<String, float[]> POSES = new HashMap<>();

    static {
        //                     head            body       right arm          left arm            r leg      l leg       elbows      knees
        put("mikiko",     .12F, -.25F, 0F,   0F, .35F,  -1.35F, .15F, .1F,  -.55F, 0F, -.15F,  -.35F, .12F, .3F, -.12F,  -1.5F, -1.7F,  .5F, .45F);   // Bajiquan guard
        put("rhalix",    -.12F, .2F, 0F,     0F, 0F,    -.95F, -.62F, .12F, -1.05F, .62F, -.12F, 0F, .06F,  .05F, -.1F,  -1.6F, -1.6F,  0F, .15F);   // arms crossed, chin up
        put("ultima",    -.3F, 0F, 0F,       0F, 0F,    -2.9F, 0F, .1F,     .1F, 0F, -.35F,    -.1F, .15F,  .1F, -.1F,   -.2F, -.3F,    0F, .1F);    // fist to the sky
        put("kaeru",      .05F, 0F, -.15F,   0F, 0F,    -2.2F, -.4F, .2F,   .15F, .7F, -.75F,  0F, .05F,   -.15F, -.15F, -1.9F, -1.5F,  0F, .2F);    // peace sign, hand on hip
        put("kyrie",      .2F, -.3F, 0F,     .1F, .3F,  -1.2F, -.3F, .1F,   -1.4F, .25F, -.1F, -.3F, .15F,  .25F, -.1F,  -1.8F, -1.9F,  .45F, .4F);  // boxing stance
        put("lexy",      -.05F, .25F, 0F,    0F, 0F,    -1.5F, -.1F, 0F,    .15F, .7F, -.75F,  -.15F, .1F,  .1F, -.1F,   -.1F, -1.5F,   0F, .1F);    // pointing forward
        put("max",       -.05F, .3F, 0F,     0F, 0F,    -.3F, .1F, .15F,    -1.9F, .5F, -.1F,  0F, .1F,    .1F, -.05F,   -.5F, -2.1F,   0F, .2F);    // knife low, smoke break
        put("sam",        0F, -.2F, 0F,      0F, 0F,    -2.6F, -.6F, 0F,    .1F, .6F, -.7F,    0F, .12F,   .05F, -.12F,  -1.6F, -1.5F,  0F, .15F);   // bat on the shoulder
        put("technica",  -.15F, .1F, 0F,     0F, 0F,    -2.4F, -.5F, 0F,    .1F, .6F, -.7F,    0F, .08F,   .1F, -.1F,    -2.2F, -1.5F,  0F, .2F);    // hand on goggles
        put("stevie",    -.2F, -.3F, 0F,     0F, 0F,    -2.8F, 0F, .4F,     .5F, 0F, -.4F,     0F, .25F,   -.1F, -.05F,  -.1F, -.3F,    0F, .4F);    // disco point
        put("garry",     -.1F, 0F, 0F,       0F, 0F,    -1.9F, -.35F, 0F,   -.9F, .4F, -.3F,   -.1F, .12F, .1F, -.12F,   -1.9F, -1.0F,  .25F, .25F); // mic up
        put("aioni",      .25F, 0F, 0F,      .15F, 0F,  -.5F, .1F, .45F,    -.5F, -.1F, -.45F, 0F, .2F,    0F, -.2F,     -1.3F, -1.3F,  .15F, .15F); // menacing
        put("yuri",       .05F, 0F, 0F,      0F, 0F,    -2.3F, -.55F, 0F,   -.4F, .5F, -.15F,  0F, .06F,   .08F, -.06F,  -2.3F, -1.4F,  0F, .15F);   // pushing her glasses
        put("zane",      -.3F, 0F, 0F,       0F, 0F,    -2.7F, 0F, .2F,     .2F, 0F, -.3F,     -.2F, .12F, .15F, -.08F,  -.3F, -.4F,    .1F, 0F);    // broom raised
        put("logan",      .05F, -.35F, 0F,   0F, .4F,   -1.55F, .3F, 0F,    -.3F, 0F, -.4F,    -.6F, .1F,  .45F, -.1F,   -.15F, -.6F,   .7F, .3F);   // sword forward, lunging
        put("kill",       .35F, 0F, 0F,      .3F, 0F,   -1.1F, .3F, .5F,    -1.1F, -.3F, -.5F, -.4F, .2F,  -.4F, -.2F,   -1.4F, -1.4F,  .6F, .6F);   // claws out, crouched
        put("keita",      .1F, .35F, .15F,  -.05F, 0F,  .1F, 0F, .12F,      .1F, 0F, -.12F,    0F, .1F,    .15F, -.05F,  -.4F, -.4F,    0F, .25F);   // lazy, hands in pockets
    }

    private PortraitPoses() {}

    private static void put(String id, float... v) { POSES.put(id, v); }

    public static boolean has(String id) { return POSES.containsKey(id); }

    public static void apply(HumanoidModel<?> m, LivingEntity e) {
        if (active == null || !(e instanceof Player)) return;
        float[] v = POSES.get(active);
        if (v == null) return;
        m.head.setRotation(v[0], v[1], v[2]);
        m.hat.copyFrom(m.head);
        m.body.xRot = v[3]; m.body.yRot = v[4]; m.body.zRot = 0F;
        // arms follow the shoulders when the body turns (like the vanilla attack swing)
        float s = Mth.sin(v[4]), c = Mth.cos(v[4]);
        m.rightArm.z = s * 5F; m.rightArm.x = -c * 5F;
        m.leftArm.z = -s * 5F; m.leftArm.x = c * 5F;
        m.rightArm.setRotation(v[5], v[6], v[7]);
        m.leftArm.setRotation(v[8], v[9], v[10]);
        m.rightLeg.xRot = v[11]; m.rightLeg.yRot = 0F; m.rightLeg.zRot = v[12];
        m.leftLeg.xRot = v[13]; m.leftLeg.yRot = 0F; m.leftLeg.zRot = v[14];
        // the bend code adds its own automatic bend; ask only for the difference so the totals are ours
        LimbBend.extraElbows(e, v[15] - LimbBend.elbow(v[5]), v[16] - LimbBend.elbow(v[8]));
        LimbBend.extraKnees(e, v[17] - LimbBend.knee(v[11], v[13], false), v[18] - LimbBend.knee(v[13], v[11], false));
    }
}

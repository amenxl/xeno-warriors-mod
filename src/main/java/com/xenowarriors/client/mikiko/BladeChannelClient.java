package com.xenowarriors.client.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ModKeyMappings;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The summon key: a tap calls / sends away the Hekublade; holding it starts channelling (see BladeChannel): blade arm
 * raised high, the other hand held out, head bowed.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BladeChannelClient {
    private static final int HOLD = 8;                       // ticks before a press counts as holding
    private static final Map<Integer, float[]> W = new HashMap<>();   // id -> {weight, prev, channelling 0/1}
    private static int held = -1;
    private static boolean channelling;

    private BladeChannelClient() {}

    public static void set(int id, boolean on) {
        W.computeIfAbsent(id, k -> new float[3])[2] = on ? 1 : 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == id && !on) channelling = false;
    }

    /** Called from the key handling instead of sending the summon at once. */
    public static void keyTick(Minecraft mc) {
        boolean mikiko = mc.player != null && ClientCharacterData.getLocal() == CharacterType.MIKIKO && mc.screen == null;
        boolean down = mikiko && ModKeyMappings.SUMMON_BLADE.isDown();
        if (down) {
            held++;
            if (held == HOLD && !channelling) {
                channelling = true;
                ModNetwork.sendToServer(new com.xenowarriors.network.BladeChannelPacket(true));
            }
        } else if (held >= 0) {
            if (held < HOLD && mikiko) ModNetwork.sendToServer(new com.xenowarriors.network.SummonBladePacket());   // a tap
            if (channelling) ModNetwork.sendToServer(new com.xenowarriors.network.BladeChannelPacket(false));
            channelling = false;
            held = -1;
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) return;
        W.entrySet().removeIf(e -> {
            float[] w = e.getValue();
            w[1] = w[0];
            w[0] = Mth.clamp(w[0] + (w[2] > 0 ? 0.2F : -0.15F), 0F, 1F);
            return w[0] <= 0F && w[1] <= 0F && w[2] == 0 || mc.level.getEntity(e.getKey()) == null;
        });
    }

    public static float weight(Player p) {
        float[] w = W.get(p.getId());
        if (w == null) return 0F;
        float t = Mth.lerp(Minecraft.getInstance().getFrameTime(), w[1], w[0]);
        return t * t * (3 - 2 * t);
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        float w = weight(p);
        if (w <= 0F) return;
        float tremble = Mth.sin(age * 2.3F) * 0.03F;
        m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -2.95F + tremble);        // blade held up high
        m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, 0.1F);
        m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, -0.12F);
        m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0.08F - tremble);           // other hand down at his side
        m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0F);
        m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.12F);
        m.head.xRot = Mth.lerp(w, m.head.xRot, 0.65F);                            // head bowed
        m.head.yRot = Mth.lerp(w, m.head.yRot, 0F);
        m.body.xRot = Mth.lerp(w, m.body.xRot, 0.08F);
        m.hat.copyFrom(m.head);
    }
}

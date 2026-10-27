package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Keita's idle: he's always trying to sleep - stand still a few seconds and he nods off on his feet (head drooping, Zzz). */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KeitaDoze {
    private static final int AFTER = 80;
    private static final Map<Integer, int[]> STILL = new HashMap<>();
    private KeitaDoze() {}

    private static boolean keita(Player p) { return p instanceof AbstractClientPlayer a && ClientCharacterData.get(a) == CharacterType.KEITA; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STILL.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!keita(p)) { STILL.remove(p.getId()); continue; }
            int[] s = STILL.computeIfAbsent(p.getId(), k -> new int[1]);
            boolean calm = Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && !Busy.busy(p) && p.hurtTime == 0 && !p.isUsingItem();
            s[0] = calm ? s[0] + 1 : 0;
            if (s[0] > AFTER + 20 && s[0] % 30 == 0)
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE, p.getX() + 0.3, p.getY() + 2.3, p.getZ(), 0.6, 0, 0);
        }
    }

    public static float doze(Player p) {
        int[] s = STILL.get(p.getId());
        if (s == null || s[0] < AFTER) return 0F;
        float x = Mth.clamp((s[0] - AFTER) / 30F, 0F, 1F); return x * x * (3 - 2 * x);
    }

    /** Asleep enough for the closed-eyes face? Snores now and then. */
    public static String face(Player p) {
        float d = doze(p);
        if (d <= 0.3F) return null;
        return (p.tickCount % 50) < 25 ? "snore" : "sleep";
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!keita(p)) return;
        float d = doze(p);
        if (d <= 0F) return;
        float nod = Mth.sin(age * 0.06F) * 0.08F;
        m.head.xRot = Mth.lerp(d, m.head.xRot, 0.65F + nod);
        m.head.zRot = Mth.lerp(d, m.head.zRot, 0.12F);
        m.body.xRot = Mth.lerp(d, m.body.xRot, 0.12F);
        m.rightArm.xRot = Mth.lerp(d, m.rightArm.xRot, 0.1F); m.leftArm.xRot = Mth.lerp(d, m.leftArm.xRot, 0.1F);
        m.rightArm.zRot = Mth.lerp(d, m.rightArm.zRot, 0.08F); m.leftArm.zRot = Mth.lerp(d, m.leftArm.zRot, -0.08F);
        m.hat.copyFrom(m.head);
    }
}

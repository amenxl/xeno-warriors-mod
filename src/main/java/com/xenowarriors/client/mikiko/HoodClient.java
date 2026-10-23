package com.xenowarriors.client.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.ClientCharacterData;
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

/**
 * Rain and a hoodie: Mikiko in Origins 2 or Recarnet Season 3 reaches back and pulls his hood up when it starts to
 * rain (and pushes it back down once it stops). The hood swaps over halfway through the reach.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HoodClient {
    private static final int PULL = 16;
    private static final Map<Integer, int[]> STATE = new HashMap<>();   // id -> {up 0/1, ticks since it changed}

    private HoodClient() {}

    /** Outfits with a hood he can put up. */
    public static boolean hasHood(Player p) {
        int o = ClientCharacterData.mikikoOutfit(p);
        return o == ClientCharacterData.ORIGINS_2 || o == ClientCharacterData.SEASON_3;
    }

    /** Wearing a hoodie at all - from the outfit (hood up or down) or a hoodie in the chest slot. */
    public static boolean wearingHoodie(Player p) {
        if (p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof com.xenowarriors.item.HoodieItem) return true;
        if (hasHood(p)) return true;
        return ClientCharacterData.rhalixOutfit(p) == ClientCharacterData.RHALIX_ORIGINS_2;   // his hooded coat
    }

    /** A Karma-Reducing Hoodie in the chest slot (Mikiko or Rhalix): its hood goes up in the rain too. */
    public static boolean karmaHoodie(Player p) {
        return p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof com.xenowarriors.item.KarmaHoodieItem
                && ClientCharacterData.get(p) != com.xenowarriors.character.CharacterType.NONE;
    }

    /** Put up / down by hand (the hood key): {up, was it raining then}. Holds until pressed again or the weather turns. */
    private static final Map<Integer, boolean[]> MANUAL = new HashMap<>();

    public static void setManual(int id, boolean up) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(id) instanceof Player p)) return;
        MANUAL.put(id, new boolean[]{up, raining(p)});
    }

    private static boolean raining(Player p) {
        return p.level().isRainingAt(p.blockPosition().above()) && !p.isUnderWater();
    }

    public static boolean hooded(Player p) {
        return hasHood(p) || karmaHoodie(p);
    }

    public static boolean wantsUp(Player p) {
        if (!hooded(p)) return false;
        boolean rain = raining(p);
        boolean[] m = MANUAL.get(p.getId());
        if (m != null) {
            if (m[1] == rain) return m[0];
            MANUAL.remove(p.getId());              // the weather changed: back to pulling it up for rain
        }
        return rain;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STATE.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        MANUAL.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!hooded(p)) { STATE.remove(p.getId()); continue; }
            int[] s = STATE.computeIfAbsent(p.getId(), k -> new int[]{wantsUp(p) ? 1 : 0, PULL * 4});
            int want = wantsUp(p) ? 1 : 0;
            if (want != s[0] && s[1] > PULL) { s[0] = want; s[1] = 0; }
            else s[1]++;
        }
    }

    /** The hood is up (over his head) right now. */
    public static boolean up(Player p) {
        int[] s = STATE.get(p.getId());
        if (s == null) return false;
        return s[1] >= PULL / 2 ? s[0] == 1 : s[0] == 0;          // swaps halfway through the pull
    }

    /** His right hand reaching back over his head to the hood and drawing it forward. */
    public static void apply(HumanoidModel<?> m, Player p, float age) {
        int[] s = STATE.get(p.getId());
        if (s == null || s[1] > PULL) return;
        float t = (s[1] + Minecraft.getInstance().getFrameTime()) / PULL;
        float w = Mth.sin(Math.min(1F, t) * (float) Math.PI);
        float reach = s[0] == 1 ? Mth.lerp(t, -2.9F, -2.3F) : Mth.lerp(t, -2.3F, -2.9F);   // back over the head, then forward
        m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, reach);
        m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.35F);
        m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.25F);
        if (s[0] == 1) {                                                        // both hands for pulling it up
            m.leftArm.xRot = Mth.lerp(w * 0.8F, m.leftArm.xRot, reach + 0.2F);
            m.leftArm.yRot = Mth.lerp(w * 0.8F, m.leftArm.yRot, 0.35F);
            m.leftArm.zRot = Mth.lerp(w * 0.8F, m.leftArm.zRot, -0.25F);
        }
        m.head.xRot = Mth.lerp(w, m.head.xRot, 0.25F);
        m.hat.copyFrom(m.head);
    }
}

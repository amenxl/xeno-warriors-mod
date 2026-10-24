package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.item.PhoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** On a call: the phone held up to the ear (the other arm free), head tilted a little; no attacking until you hang up. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PhoneClient {
    private PhoneClient() {}

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        boolean main = PhoneItem.onCall(p.getMainHandItem());
        if (!main && !PhoneItem.onCall(p.getOffhandItem())) return;
        boolean rightHand = (p.getMainArm() == HumanoidArm.RIGHT) == main;
        float talk = Mth.sin(age * 0.12F) * 0.04F;
        if (rightHand) {
            m.rightArm.xRot = -1.45F + talk;
            m.rightArm.yRot = -0.55F;
            m.rightArm.zRot = 0.2F;
            LimbBend.extraElbows(p, -1.55F, 0F);
            m.head.zRot = -0.12F;
        } else {
            m.leftArm.xRot = -1.45F + talk;
            m.leftArm.yRot = 0.55F;
            m.leftArm.zRot = -0.2F;
            LimbBend.extraElbows(p, 0F, -1.55F);
            m.head.zRot = 0.12F;
        }
        m.hat.copyFrom(m.head);
    }

    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null && PhoneItem.onCall(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }
}

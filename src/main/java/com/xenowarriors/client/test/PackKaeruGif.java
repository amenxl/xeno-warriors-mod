package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;

import java.util.List;

/** (focus "packkaerugif") Frames of Kaeru's stretching idle, seen from the front (made into a GIF). */
final class PackKaeruGif {
    private PackKaeruGif() {}

    static void build(Minecraft mc, List<Step> l) {
        int[] cam = {-1};
        l.add(new Step(null, () -> { PackWomTests.battle(false); com.xenowarriors.client.UiTest.character(CharacterType.KAERU); }, 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.getAbilities().flying = false; p.onUpdateAbilities();
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0F, 0F);
            p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0);
            p.serverLevel().setDayTime(6000);
            var s = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            if (s != null) {
                s.moveTo(p.getX() + 0.6, p.getY() - 0.25, p.getZ() + 3.4, 170F, 6F);
                s.setYHeadRot(170F);
                s.setInvisible(true); s.setNoGravity(true); s.setInvulnerable(true);
                p.serverLevel().addFreshEntity(s);
                cam[0] = s.getId();
            }
        }), 15));
        l.add(new Step(null, () -> {
            mc.setScreen(null); mc.options.hideGui = true;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            var c = mc.level.getEntity(cam[0]);
            if (c != null) mc.setCameraEntity(c);
        }, 60));
        // she starts after 90 still ticks; the stretch runs 300 - frames every 4 ticks from just before it
        for (int f = 0; f < 80; f++) {
            int k = f;
            l.add(new Step(String.format("kaerugif_%03d", f), () -> {
                if (k % 10 == 0 && mc.player != null) PackTests.log("kaerugif frame " + k + " t " + com.xenowarriors.client.KaeruStretch.time(mc.player) + " holder " + com.xenowarriors.client.Idles.holder(mc.player));
            }, 4));
        }
        l.add(new Step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; }, 5));
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** (focus "packcombogif") Frames of Mikiko's and Rhalix's bare-hands combo in battle mode, seen from the side (made into a GIF). */
final class PackComboGif {
    private PackComboGif() {}

    static void build(Minecraft mc, List<Step> l) {
        for (CharacterType who : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX}) {
            int[] ids = {-1, -1};
            l.add(new Step(null, () -> UiTest.character(who), 30));
            l.add(new Step(null, () -> PackTests.server(p -> {
                PackTests.arena(p); PackTests.clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getAbilities().flying = false; p.onUpdateAbilities();
                p.getInventory().selected = 0;
                p.getInventory().setItem(0, ItemStack.EMPTY);
                p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(5);
                var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (z != null) {
                    z.moveTo(p.getX(), p.getY(), p.getZ() + 1.8, 180F, 0F);
                    z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
                    z.setHealth(1000F);
                    p.serverLevel().addFreshEntity(z);
                    ids[0] = z.getId();
                }
                // the camera: an invisible stand off to the side, looking at the fight
                var s = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
                if (s != null) {
                    s.moveTo(p.getX() + 4.2, p.getY() - 0.2, p.getZ() + 0.9, 90F, 8F);
                    s.setYHeadRot(90F);
                    s.setInvisible(true); s.setNoGravity(true); s.setInvulnerable(true);
                    p.serverLevel().addFreshEntity(s);
                    ids[1] = s.getId();
                }
            }), 15));
            l.add(new Step(null, () -> {
                try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null && !pp.isEpicFightMode()) pp.toEpicFightMode(true); } catch (Throwable ignored) {}
                mc.setScreen(null); mc.options.hideGui = true;
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                var cam = mc.level.getEntity(ids[1]);
                if (cam != null) mc.setCameraEntity(cam);
                PackTests.log("combogif " + who.getId() + " camera " + (cam != null));
            }, 30));
            int f = 0;
            for (int k = 0; k < 7; k++) {
                l.add(new Step(String.format("combogif_%s_%03d", who.getId(), f++), () -> { net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey()); PackTests.server(p -> trace(who, p, ids[0], "click")); }, 1));
                for (int t = 0; t < 5; t++) l.add(new Step(String.format("combogif_%s_%03d", who.getId(), f++), () -> PackTests.server(p -> trace(who, p, ids[0], "")), 1));
            }
            for (int t = 0; t < 14; t++) l.add(new Step(String.format("combogif_%s_%03d", who.getId(), f++), () -> PackTests.server(p -> trace(who, p, ids[0], "")), 1));
            l.add(new Step(null, () -> {
                mc.setCameraEntity(mc.player); mc.options.hideGui = false;
                PackTests.server(p -> {
                    var z = p.serverLevel().getEntity(ids[0]);
                    PackTests.log("combogif " + who.getId() + " dummy " + (z instanceof net.minecraft.world.entity.LivingEntity le ? le.getHealth() : -1));
                    if (z != null) z.discard();
                    var s = p.serverLevel().getEntity(ids[1]);
                    if (s != null) s.discard();
                });
            }, 5));
        }
    }

    /** Each frame: the move playing (and how far in), how far apart they are, the dummy's health and whether it's flashing red. */
    private static void trace(CharacterType who, net.minecraft.server.level.ServerPlayer p, int zid, String tag) {
        var z = p.serverLevel().getEntity(zid);
        var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
        String anim = "-";
        try { var pl = patch.getAnimator().getPlayerFor(null); if (pl != null) anim = pl.getRealAnimation() + "@" + String.format(java.util.Locale.ROOT, "%.2f", pl.getElapsedTime()); } catch (Throwable ignored) {}
        var le = z instanceof net.minecraft.world.entity.LivingEntity x ? x : null;
        PackTests.log(String.format(java.util.Locale.ROOT, "combotrace %s %s t%d anim %s dist %.2f hp %.1f hurt %d inv %d",
                who.getId(), tag, p.serverLevel().getGameTime() % 100000, anim, z == null ? -1 : Math.sqrt(p.distanceToSqr(z)),
                le == null ? -1 : le.getHealth(), le == null ? -1 : le.hurtTime, le == null ? -1 : le.invulnerableTime));
    }
}

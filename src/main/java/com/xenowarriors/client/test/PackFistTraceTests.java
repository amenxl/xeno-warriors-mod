package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * (focus "packfisttrace") Why Kyrie's Iron Fist hits nothing: Kyrie and Technica (a control) click at a still dummy
 * in battle mode; every few ticks the server logs where the player is, which way they face, the animation playing,
 * whether they're flying, and the dummy's health.
 */
final class PackFistTraceTests {
    private PackFistTraceTests() {}

    static void build(Minecraft mc, List<Step> l) { build(mc, l, new CharacterType[]{CharacterType.TECHNICA, CharacterType.KYRIE}); }

    /** (focus "packrhalixtrace") Rhalix's bare hands in battle mode: should be the Ender Blaster's strikes. */
    static void rhalix(Minecraft mc, List<Step> l) {
        build(mc, l, new CharacterType[]{CharacterType.RHALIX});
        l.add(new Step(null, () -> PackTests.check("rhalix: bare hands play the Ender Blaster's strikes", SEEN.stream().anyMatch(s -> s.contains("enderblaster")), "animations " + SEEN), 2));
    }

    static final java.util.Set<String> SEEN = new java.util.LinkedHashSet<>();

    static void build(Minecraft mc, List<Step> l, CharacterType[] who0) {
        for (CharacterType who : who0) {
            int[] zid = {-1};
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
                    z.moveTo(p.getX(), p.getY(), p.getZ() + 1.6, 180F, 0F);
                    z.setNoAi(true);
                    z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                    z.setHealth(200F);
                    p.serverLevel().addFreshEntity(z);
                    zid[0] = z.getId();
                }
            }), 15));
            l.add(new Step(null, () -> PackTests.log("fisttrace " + who.getId() + " combo for bare hands: " + (com.xenowarriors.compat.epicfight.WomMoves.movesFor(mc.player) == null ? "none (ours not swapped in)"
                    : java.util.Arrays.toString(com.xenowarriors.compat.epicfight.WomMoves.movesFor(mc.player)) + " missing " + com.xenowarriors.compat.epicfight.WomMoves.MISSING)), 2));
            l.add(new Step(null, () -> {
                try { var pp = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch(); if (pp != null && !pp.isEpicFightMode()) pp.toEpicFightMode(true); } catch (Throwable ignored) {}
                mc.setScreen(null); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }, 30));
            for (int k = 0; k < 4; k++) {
                l.add(new Step(null, () -> net.minecraft.client.KeyMapping.click(mc.options.keyAttack.getKey()), 2));
                for (int t = 0; t < 4; t++) l.add(new Step(null, () -> PackTests.server(p -> trace(who, p, zid[0], mc)), 3));
            }
            l.add(new Step("pack_fisttrace_" + who.getId(), () -> PackTests.server(p -> {
                var z = p.serverLevel().getEntity(zid[0]);
                PackTests.log("fisttrace " + who.getId() + " end: dummy " + (z instanceof net.minecraft.world.entity.LivingEntity le ? le.getHealth() : -1)
                        + " guarded " + com.xenowarriors.compat.epicfight.WomMoves.GUARDED + " lunge stops " + com.xenowarriors.compat.EfTargetAssist.stops);
                PackTests.check("fisttrace " + who.getId() + ": more than one hit lands", z instanceof net.minecraft.world.entity.LivingEntity le2 && le2.getHealth() < (who == CharacterType.KYRIE ? 240F : 220F) - 7F,
                        "dummy " + (z instanceof net.minecraft.world.entity.LivingEntity le3 ? le3.getHealth() : -1));
                if (z != null) z.discard();
            }), 4));
        }
    }

    private static void trace(CharacterType who, net.minecraft.server.level.ServerPlayer p, int zid, Minecraft mc) {
        var z = p.serverLevel().getEntity(zid);
        var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
        Object anim = null;
        try { var pl = patch.getAnimator().getPlayerFor(null); anim = pl == null ? null : pl.getRealAnimation(); } catch (Throwable ignored) {}
        if (anim != null) SEEN.add(String.valueOf(anim));
        PackTests.log(String.format(java.util.Locale.ROOT, "fisttrace %s: pos %.2f %.2f %.2f yaw %.0f ground %s flying %s battle %s anim %s target %s dummy at %s hp %s",
                who.getId(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.onGround(), p.getAbilities().flying, patch != null && patch.isEpicFightMode(), anim,
                patch == null ? null : patch.getTarget(), z == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", z.getX(), z.getY(), z.getZ()),
                z instanceof net.minecraft.world.entity.LivingEntity le ? le.getHealth() : -1));
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * (Pack test, focus "packmikikofight", and the full pack run) Mikiko's bare-hand combo in Epic Fight's battle mode,
 * with real left clicks: his own punches and Bajiquan must be what's drawn (not Epic Fight's / Weapons of Miracles'
 * moves). Logs, tick by tick, which animation Epic Fight plays and who draws him; shots mid-combo.
 */
final class PackMikikoFightTests {
    private PackMikikoFightTests() {}

    private static void press(Minecraft mc, net.minecraft.client.KeyMapping base, boolean down) {
        for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
            if (!k.getKey().equals(base.getKey())) continue;
            k.setDown(down);
            if (down) try {
                for (var fld : net.minecraft.client.KeyMapping.class.getDeclaredFields())
                    if (fld.getType() == int.class && !java.lang.reflect.Modifier.isStatic(fld.getModifiers())) { fld.setAccessible(true); fld.setInt(k, fld.getInt(k) + 1); }
            } catch (Throwable t) { PackTests.log("mikikofight press: " + t); }
        }
    }

    private static void battle(boolean on) {
        try {
            var p = yesman.epicfight.client.ClientEngine.getInstance().getPlayerPatch();
            if (p == null) return;
            if (on && !p.isEpicFightMode()) p.toEpicFightMode(true);
            if (!on && p.isEpicFightMode()) p.toVanillaMode(true);
        } catch (Throwable t) { PackTests.log("mikikofight: battle " + t); }
    }

    static int ours, theirs, attackTicks;
    static final StringBuilder TRACE = new StringBuilder();

    static void build(Minecraft mc, List<Step> l) {
        if (!ModList.get().isLoaded("epicfight")) return;
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY);
            p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0);
        }), 15));
        l.add(new Step(null, () -> { battle(true); mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            ours = theirs = attackTicks = 0; TRACE.setLength(0); }, 25));
        for (int hit = 0; hit < 6; hit++) {
            int h = hit;
            l.add(new Step(null, () -> press(mc, mc.options.keyAttack, true), 1));
            l.add(new Step(null, () -> press(mc, mc.options.keyAttack, false), 1));
            for (int t = 0; t < 7; t++) {
                int tt = t;
                l.add(new Step(tt == 3 && (h == 1 || h == 3) ? "pack_mikikofight_hit" + h : null, () -> {
                    String d = com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player);
                    boolean mine = com.xenowarriors.client.mikiko.MikikoAnimations.attacking(mc.player);
                    boolean efAct = d.contains("action=true");
                    if (efAct || mine) {
                        attackTicks++;
                        if (d.contains("efDraws=true")) theirs++; else ours++;
                    }
                    if (tt % 3 == 0 && TRACE.length() < 3000) TRACE.append("\n  hit").append(h).append(" t").append(tt).append(": own=").append(mine).append(' ').append(d);
                }, 1));
            }
        }
        l.add(new Step(null, () -> {}, 20));
        // the user's video: Fox King Mikiko with a sword in battle mode, cel shading on, seen from behind - a dark
        // "shadow" moving with him
        l.add(new Step(null, () -> { RenderProbe.reset(); com.xenowarriors.client.cel.Cel.force = true; com.xenowarriors.client.mikiko.FoxKingClient.set(mc.player.getId(), true);
            PackTests.server(p -> p.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD)));
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 25));
        l.add(new Step("pack_mikikofight_sword_idle", () -> PackTests.log("mikikofight sword idle: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), 3));
        for (int hit = 0; hit < 3; hit++) {
            int h = hit;
            l.add(new Step(null, () -> press(mc, mc.options.keyAttack, true), 1));
            l.add(new Step(null, () -> press(mc, mc.options.keyAttack, false), 4));
            l.add(new Step("pack_mikikofight_sword" + h, () -> PackTests.log("mikikofight sword swing " + h + ": " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)
                    + " vanillaRuns " + com.xenowarriors.client.render.CharacterArmorLayer.layerRuns + " " + RenderProbe.read()), 10));
        }
        // the same with cel shading off (the outline is the suspect for the dark shapes)
        l.add(new Step(null, () -> com.xenowarriors.client.cel.Cel.force = false, 25));
        l.add(new Step("pack_mikikofight_sword_idle_nocel", () -> {}, 3));
        l.add(new Step(null, () -> press(mc, mc.options.keyAttack, true), 1));
        l.add(new Step(null, () -> press(mc, mc.options.keyAttack, false), 4));
        l.add(new Step("pack_mikikofight_sword0_nocel", () -> {}, 10));
        l.add(new Step(null, () -> { com.xenowarriors.client.cel.Cel.force = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 25));
        l.add(new Step(null, () -> press(mc, mc.options.keyAttack, true), 1));
        l.add(new Step(null, () -> press(mc, mc.options.keyAttack, false), 4));
        l.add(new Step("pack_mikikofight_sword_front", () -> PackTests.log("mikikofight sword front: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)), 10));
        // with a shader pack on, no outline shell is drawn (it showed as a black shadow body under shaders)
        l.add(new Step(null, () -> { com.xenowarriors.client.compat.ShaderCompat.forceForTest = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.outlineDraws = 0; }, 10));
        l.add(new Step(null, () -> { int n = com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.outlineDraws;
            com.xenowarriors.client.compat.ShaderCompat.forceForTest = false; com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.outlineDraws = 0;
            PackTests.check("mikikofight: no cel outline shell under a shader pack", n == 0, "outline draws " + n); }, 10));
        l.add(new Step(null, () -> PackTests.check("mikikofight: no cel outline shell on Epic Fight's body (it showed as a black shadow)", com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.outlineDraws == 0,
                "outline draws " + com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.outlineDraws), 2));
        l.add(new Step(null, () -> { com.xenowarriors.client.cel.Cel.force = false; com.xenowarriors.client.mikiko.FoxKingClient.set(mc.player.getId(), false);
            PackTests.server(p -> p.getInventory().setItem(0, ItemStack.EMPTY)); }, 5));
        l.add(new Step(null, () -> {
            PackTests.log("mikikofight trace:" + TRACE);
            PackTests.check("mikikofight: Mikiko's bare-hand combo in battle mode is his own moves, drawn by Epic Fight", attackTicks > 0 && theirs > 0 && TRACE.indexOf("biped/xw/mikiko_") >= 0,
                    "attack ticks " + attackTicks + ", his own drawn " + ours + ", Epic Fight drew " + theirs + ", wom loaded " + ModList.get().isLoaded("wom"));
            battle(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.server(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE));
        }, 5));
    }
}

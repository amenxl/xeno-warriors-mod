package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.compat.epicfight.EfBotBodies;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * (focus "packefwalk") Bots on Epic Fight's body walking and running with a weapon in hand: vanilla ones, Epic Fight's
 * own (greatsword, spear, longsword, tachi - they have their own two-handed walks) and ours. What Epic Fight plays on
 * the body while it moves must be a walk / run, never the standing idle (legs frozen while it slides along).
 */
final class PackEfWalkTests {
    private PackEfWalkTests() {}

    private static final String[] WEAPONS = {"minecraft:iron_sword", "minecraft:iron_axe", "epicfight:iron_greatsword", "epicfight:iron_spear",
            "epicfight:iron_longsword", "epicfight:iron_tachi", "xenowarriors:hekublade", "xenowarriors:sam_bat", "xenowarriors:kyrie_rapier",
            "xenowarriors:swordstress_greatsword"};

    private static PartyBotEntity bot;

    private static net.minecraft.client.player.AbstractClientPlayer avatar(Minecraft mc, CharacterType t) {
        if (mc.level == null) return null;
        return com.xenowarriors.client.bot.BotAvatars.players(mc.level).stream()
                .filter(x -> x instanceof com.xenowarriors.client.bot.BotAvatar a && a.bot() != null && a.bot().character() == t).findFirst().orElse(null);
    }

    /** What Epic Fight's animator plays on the body (the whole-body layer), and its upper-body layer if any. */
    private static String anim(net.minecraft.client.player.AbstractClientPlayer av) {
        try {
            var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(av, yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch.class);
            if (patch == null) return "no patch";
            var pl = patch.getAnimator().getPlayerFor(null);
            String base = pl == null || pl.isEmpty() || pl.getRealAnimation() == null ? "none" : String.valueOf(pl.getRealAnimation().get());
            String comp = "";
            try {                                                                 // (the upper-body layer: by reflection, its API moves between versions)
                Object an = patch.getAnimator();
                for (var m : an.getClass().getMethods()) {
                    if (!m.getName().equals("getCompositeLayer") || m.getParameterCount() != 1) continue;
                    Object pr = null;
                    for (Object c : m.getParameterTypes()[0].getEnumConstants()) if (String.valueOf(c).equals("MIDDLE")) pr = c;
                    Object layer = m.invoke(an, pr);
                    var f = layer.getClass().getField("animationPlayer");
                    Object ap = f.get(layer);
                    if (ap == null || Boolean.TRUE.equals(ap.getClass().getMethod("isEmpty").invoke(ap))) continue;
                    Object ra = ap.getClass().getMethod("getRealAnimation").invoke(ap);
                    if (ra != null) comp = " | upper " + ra.getClass().getMethod("get").invoke(ra);
                }
            } catch (Throwable ignored) {}
            return base + comp + " [" + patch.currentLivingMotion + "/" + patch.currentCompositeMotion + " held " + patch.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND) + "]";
        } catch (Throwable t) { return "err " + t; }
    }

    private static boolean moving(String a) {
        String s = a.toLowerCase(java.util.Locale.ROOT);
        int cut = s.indexOf(" [");
        if (cut > 0) s = s.substring(0, cut);
        return s.contains("walk") || s.contains("run") || s.contains("sneak");
    }

    static void build(Minecraft mc, List<Step> l) {
        CharacterType t = CharacterType.KYRIE;
        List<String> noWalk = new ArrayList<>(), noRun = new ArrayList<>();
        l.add(new Step(null, () -> { mc.setScreen(null); EfBotBodies.off = false; UiTest_character(); PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); }); }, 30));
        for (String w : WEAPONS) {
            String n = w.substring(w.indexOf(':') + 1);
            for (int run = 0; run < 2; run++) {
                boolean sprint = run == 1;
                Set<String> seen = new LinkedHashSet<>();
                boolean[] ok = {false};
                l.add(new Step(null, () -> PackTests.server(p -> {
                    Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(w));
                    if (it == null || it == net.minecraft.world.item.Items.AIR) { PackTests.log("efwalk: no item " + w); return; }
                    if (bot == null || bot.isRemoved()) {
                        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() != t) b.discard();
                        bot = PartyBots.bots(p).stream().filter(b -> b.character() == t).findFirst().orElse(null);
                        if (bot == null) bot = PartyBots.spawn(p, t, null);
                    }
                    if (bot == null) return;
                    bot.setNoAi(false);
                    bot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(it));
                    bot.setTarget(null);
                    bot.moveTo(PackTests.HOME[0] - 7, PackTests.HOME[1], PackTests.HOME[2] + 6, -90F, 0F);
                    bot.getNavigation().stop();
                    p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 12F);
                }), 25));
                l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                    PackTests.server(p -> { if (bot == null) return;
                        bot.setSprinting(sprint);
                        bot.getNavigation().moveTo(PackTests.HOME[0] + 8, PackTests.HOME[1], PackTests.HOME[2] + 6, sprint ? 1.6 : 0.9); }); }, 4));
                for (int k = 0; k < 4; k++) {
                    int kk = k;
                    l.add(new Step(kk == 1 ? "efwalk_" + n + (sprint ? "_run" : "_walk") : null, () -> {
                        if (sprint) PackTests.server(p -> { if (bot != null) bot.setSprinting(true); });
                        var av = avatar(mc, t);
                        if (av == null) { seen.add("no avatar"); return; }
                        var b = ((com.xenowarriors.client.bot.BotAvatar) av).bot();
                        double sp = Math.hypot(b.getX() - b.xo, b.getZ() - b.zo);
                        String a = anim(av);
                        PackTests.log("efwalk " + n + (sprint ? " run " : " walk ") + kk + ": speed " + PackTests.f(sp * 20) + " b/s sprinting " + av.isSprinting() + " anim " + a);
                        if (sp > 0.02) { seen.add(a.substring(0, Math.max(0, a.indexOf(" [")))); if (moving(a)) ok[0] = true; }
                    }, 5));
                }
                l.add(new Step(null, () -> {
                    PackTests.log("efwalk " + n + (sprint ? " run" : " walk") + " played while moving: " + seen);
                    if (!ok[0]) (sprint ? noRun : noWalk).add(n + " " + seen);
                }, 2));
            }
        }
        l.add(new Step("efwalk_done", () -> {
            PackTests.check("efwalk: bots walk on Epic Fight's body with every weapon (legs move)", noWalk.isEmpty(), noWalk.toString());
            PackTests.check("efwalk: bots run on Epic Fight's body with every weapon", noRun.isEmpty(), noRun.toString());
            PackTests.check("efwalk: no errors on the bots' Epic Fight bodies", EfBotBodies.lastError == null, String.valueOf(EfBotBodies.lastError));
            mc.options.hideGui = false;
            PackTests.server(p -> { PackTests.clear(p); bot = null; });
        }, 5));
    }

    private static void UiTest_character() { com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO); }
}

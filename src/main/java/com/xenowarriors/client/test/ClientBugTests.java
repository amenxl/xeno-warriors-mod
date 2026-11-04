package com.xenowarriors.client.test;

import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.render.CharacterDetailLayer;
import net.minecraft.client.Minecraft;

/**
 * Client-side bug sweep checks (focuses starting with "cbugs", run from UiTest). Logged as "XW-TEST cbugs ...".
 * <ul>
 *   <li>faces: expressions a face set doesn't have (drawn Mikiko/Ultima, Rhalix's Demon/Lunar) resolve to an existing
 *   close face instead of the missing texture.</li>
 *   <li>botmenu: a bot menu whose bot isn't there closes by itself.</li>
 *   <li>reset: leaving a world (the real LoggingOut event) empties the per-world client state.</li>
 * </ul>
 */
public final class ClientBugTests {
    private ClientBugTests() {}

    @FunctionalInterface
    public interface Adder { void add(String shot, Runnable action, int delay); }

    public static boolean handles(String focus) { return focus != null && focus.startsWith("cbugs"); }

    private static void log(String s) { UiTest.log("cbugs " + s); }

    private static boolean exists(net.minecraft.resources.ResourceLocation rl) {
        return Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
    }

    public static void build(Minecraft mc, String focus, Adder l) {
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 20);
        // faces
        l.add(null, () -> {
            String[][] cases = {{"mikiko_drawn", "shocked"}, {"mikiko_drawn", "laugh"}, {"mikiko_drawn", "smirk"}, {"ultima_drawn", "determined"},
                    {"rhalix_demon", "shocked"}, {"rhalix_lunar", "smirk"}, {"rhalix_demon", "nosuchface"}, {"mikiko", "shocked"}, {"ultima", "overload"}};
            int ok = 0;
            for (String[] c : cases) {
                var rl = CharacterDetailLayer.face(c[0], c[1]);
                boolean there = exists(rl);
                if (there) ok++;
                log("face " + c[0] + "/" + c[1] + " -> " + rl.getPath() + " exists=" + there);
            }
            log("faces ok=" + ok + "/" + cases.length);
        }, 10);
        // a bot menu for a bot that isn't there
        l.add(null, () -> mc.setScreen(new com.xenowarriors.client.bot.BotScreen(new com.xenowarriors.network.OpenBotMenuPacket(
                -12345, "mikiko", 0, 1, false, new String[0], new int[0]))), 2);
        l.add(null, () -> log("botmenu opened=" + (mc.screen instanceof com.xenowarriors.client.bot.BotScreen)), 20);
        l.add(null, () -> log("botmenu closed_when_gone=" + !(mc.screen instanceof com.xenowarriors.client.bot.BotScreen)), 2);
        // leaving the world
        l.add(null, () -> {
            var p = mc.player;
            if (p == null) { log("reset skipped (no player)"); return; }
            com.xenowarriors.client.LockOn.LEVELS.put(987654, 42);
            com.xenowarriors.client.HudFaces.health(p.getUUID(), 20F, 20F);
            com.xenowarriors.client.HudFaces.health(p.getUUID(), 2F, 20F);   // big hit -> "damage1"
            com.xenowarriors.client.ClashClient.set(true, 0.5F, 10, "Test");
            com.xenowarriors.client.ClientSkillState.setSeason(3);
            com.xenowarriors.client.ClientSkillState.setFlightSkill(987654, 12000);
            log("reset before tracked=" + com.xenowarriors.client.ClientReset.tracked() + " levels=" + com.xenowarriors.client.LockOn.LEVELS.size()
                    + " face=" + com.xenowarriors.client.HudFaces.face(p.getUUID(), 2F, 20F, false, false)
                    + " clash=" + com.xenowarriors.client.ClashClient.active() + " season=" + com.xenowarriors.client.ClientSkillState.season()
                    + " flight=" + com.xenowarriors.client.ClientSkillState.flightSkill(987654));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut(
                    mc.gameMode, p, mc.getConnection() == null ? null : mc.getConnection().getConnection()));
            boolean ok = com.xenowarriors.client.LockOn.LEVELS.isEmpty() && !com.xenowarriors.client.ClashClient.active()
                    && com.xenowarriors.client.ClientSkillState.season() == 1 && com.xenowarriors.client.ClientSkillState.flightSkill(987654) == 0F
                    && !"damage1".equals(com.xenowarriors.client.HudFaces.face(p.getUUID(), 2F, 20F, false, false));
            log("reset after levels=" + com.xenowarriors.client.LockOn.LEVELS.size() + " clash=" + com.xenowarriors.client.ClashClient.active()
                    + " season=" + com.xenowarriors.client.ClientSkillState.season() + " flight=" + com.xenowarriors.client.ClientSkillState.flightSkill(987654)
                    + " ok=" + ok);
        }, 10);
        l.add(null, () -> log("done"), 10);
    }
}

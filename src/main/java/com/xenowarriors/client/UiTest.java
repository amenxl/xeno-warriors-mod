package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * UI test (CI only, XW_UITEST=1): once in a world, opens every screen and takes a screenshot, then shoots each
 * character from the front (cel-shading off and on), then quits. Screenshots land in run/screenshots.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UiTest {
    private static final boolean ON = "1".equals(System.getenv("XW_UITEST"));
    private record Step(String name, Runnable action, int delay) {}
    private static List<Step> steps;
    private static int idx = -1, wait, ingame, pre;

    private UiTest() {}

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) f.accept(p); });
    }

    private static void character(CharacterType t) {
        server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t));
    }

    private static List<Step> build() {
        Minecraft mc = Minecraft.getInstance();
        List<Step> l = new ArrayList<>();
        l.add(new Step(null, () -> server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 3);
            p.server.getPlayerList().op(p.getGameProfile());
            p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).putBoolean("xw_collection_seen", true);
            CompoundHolder.unlock(p);
            p.serverLevel().setDayTime(6000);
            // a clean stage for the model shots: clear the grass around, stone floor
            var c = p.blockPosition();
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                for (int dy = 0; dy <= 4; dy++) p.serverLevel().setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                p.serverLevel().setBlock(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
            p.serverLevel().setWeatherParameters(6000, 0, false, false);
        }), 20));
        l.add(new Step(null, () -> character(CharacterType.MIKIKO), 40));
        l.add(new Step("hud_mikiko", () -> mc.setScreen(null), 30));
        l.add(new Step("select_screen", () -> mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()), 20));
        for (int t = 0; t < 4; t++) {
            int tt = t;
            l.add(new Step("menu_tab" + t, () -> { PlayerStatsScreen.presetTab(tt); PlayerStatsScreen.open(); }, 25));
        }
        l.add(new Step("skills", () -> mc.setScreen(new SkillLoadoutScreen(null)), 20));
        l.add(new Step("ka_guide", () -> mc.setScreen(new KaGuideScreen(null)), 20));
        l.add(new Step("collection", () -> { mc.setScreen(null); server(p -> com.xenowarriors.collection.Collection.open(p, true)); }, 30));
        l.add(new Step("bounties", () -> { mc.setScreen(null); server(com.xenowarriors.bounty.Bounties::open); }, 30));
        l.add(new Step("vr_console", () -> { mc.setScreen(null); server(com.xenowarriors.vr.Vr::open); }, 30));
        l.add(new Step("bot_pick", () -> { mc.setScreen(null); server(com.xenowarriors.bot.PartyBots::openPick); }, 30));
        l.add(new Step("party_menu", () -> mc.setScreen(new com.xenowarriors.client.bot.PartyScreen()), 20));
        // the characters from the front, cel-shading off and on
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10F); }, 20));
        for (CharacterType t : CharacterType.PLAYABLE) {
            for (boolean cel : new boolean[]{false, true}) {
                l.add(new Step(null, () -> { character(t); ClientSettings.setCelShading(cel); }, 40));
                l.add(new Step("model_" + t.getId() + (cel ? "_cel" : ""), () -> {}, 10));
            }
        }
        l.add(new Step(null, () -> { ClientSettings.setCelShading(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        return l;
    }

    /** Small helper so the Collection counts as read (and the menu shows its tab). */
    private static final class CompoundHolder {
        static void unlock(ServerPlayer p) {
            var root = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            root.putBoolean("xw_collection_seen", true);
            p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, root);
            com.xenowarriors.collection.Collection.open(p, false);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // loading the test world: click through "experimental settings" / backup warnings
            if (mc.screen != null && ++pre % 40 == 0) {
                XenoWarriors.LOGGER.info("XW-UITEST waiting on screen {}", mc.screen.getClass().getName());
                for (var w : mc.screen.children()) {
                    if (w instanceof net.minecraft.client.gui.components.AbstractButton b) {
                        String t = b.getMessage().getString().toLowerCase();
                        if (t.contains("proceed") || t.contains("yes") || t.contains("know what") || t.contains("continue") || t.contains("load")) {
                            XenoWarriors.LOGGER.info("XW-UITEST pressing '{}'", t);
                            b.onPress();
                            break;
                        }
                    }
                }
            }
            return;
        }
        if (++ingame < 200) return;                                      // let the world settle
        if (steps == null) { steps = build(); idx = -1; wait = 0; XenoWarriors.LOGGER.info("XW-UITEST start ({} steps)", steps.size()); }
        if (wait > 0) { wait--; return; }
        if (idx >= 0 && idx < steps.size() && steps.get(idx).name != null) {
            String n = steps.get(idx).name;
            Screenshot.grab(mc.gameDirectory, "xw_" + n + ".png", mc.getMainRenderTarget(), c -> XenoWarriors.LOGGER.info("XW-UITEST shot {}", n));
        }
        idx++;
        if (idx >= steps.size()) { XenoWarriors.LOGGER.info("XW-UITEST done"); mc.stop(); return; }
        try { steps.get(idx).action.run(); } catch (Throwable t) { XenoWarriors.LOGGER.error("XW-UITEST step failed", t); }
        wait = steps.get(idx).delay;
    }
}

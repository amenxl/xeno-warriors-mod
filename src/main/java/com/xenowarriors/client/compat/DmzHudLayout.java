package com.xenowarriors.client.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.KhHud;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * With DragonMineZ: its HUD panels and ours share the screen without sitting on each other.
 * <ul>
 *   <li>Its health / ki / stamina bars are already hidden while our HUD shows them (KhHud) - that HUD reads the same
 *       DragonMineZ numbers, and adds the form and power release.</li>
 *   <li>Its tracked-mission panel (top right, where our party cards are) moves to the top left.</li>
 *   <li>Its technique bar, when it's set to the right side of the screen, sits above our HUD instead of across it.</li>
 * </ul>
 * Done by shifting where those overlays draw (their own drawing, untouched); nothing changes in DragonMineZ's config.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DmzHudLayout {
    private DmzHudLayout() {}

    private static Boolean loaded;
    private static Method userConfig, rightSide, drawBar;
    private static boolean shifted;
    /** (Tests) draw the technique bar as if its key were held; and how it was last shifted. */
    public static boolean forceTechniqueBarForTest, offForTest;
    public static String lastShift = "";

    private static boolean on() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
            if (loaded) try {
                userConfig = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getUserConfig");
                rightSide = userConfig.getReturnType().getMethod("getTechniqueHotbarRightSide");
            } catch (Throwable t) { userConfig = null; }
        }
        return loaded;
    }

    private static boolean techniquesRight() {
        try { return Boolean.TRUE.equals(rightSide.invoke(userConfig.invoke(null))); } catch (Throwable t) { return false; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pre(RenderGuiOverlayEvent.Pre e) {
        shifted = false;
        if (e.isCanceled() || !on()) return;
        var id = e.getOverlay().id();
        if (!id.getNamespace().equals("dragonminez") || !KhHud.active()) return;
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        float dx = 0, dy = 0;
        switch (id.getPath()) {
            case "tracked_quest_hud" -> dx = -(w - 200);                       // (its panel is 180 wide at width-190: to x=10)
            case "techniquehud" -> {
                if (techniquesRight() || forceTechniqueBarForTest) {
                    int top = KhHud.top >= 0 ? KhHud.top : h - 80;
                    dy = Math.min(0, (top - 4) - (h - 51));                    // (its rows end 51 px from the bottom: they end above our HUD now)
                }
            }
            default -> { return; }
        }
        if (offForTest) { dx = 0; dy = 0; }                                         // (tests: DragonMineZ's own layout, for the "before" shots)
        if (dx == 0 && dy == 0 && !forceTechniqueBarForTest) return;
        e.getGuiGraphics().pose().pushPose();
        e.getGuiGraphics().pose().translate(dx, dy, 0);
        shifted = true;
        lastShift = id.getPath() + " dx=" + dx + " dy=" + dy;
        if (forceTechniqueBarForTest && id.getPath().equals("techniquehud")) {
            drawTechniquesForTest(e, w, h);
            e.getGuiGraphics().pose().popPose();
            shifted = false;
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(RenderGuiOverlayEvent.Post e) {
        if (!shifted) return;
        shifted = false;
        e.getGuiGraphics().pose().popPose();
    }

    /** (Tests) DragonMineZ's technique bar drawn the way it draws itself when its key is held (slots 1-4). */
    private static void drawTechniquesForTest(RenderGuiOverlayEvent.Pre e, int w, int h) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (drawBar == null) {
                Class<?> sd = Class.forName("com.dragonminez.common.stats.StatsData");
                for (Method m : Class.forName("com.dragonminez.client.gui.hud.TechniqueHotbarHUD").getDeclaredMethods())
                    if (m.getName().equals("lambda$static$0") && m.getParameterCount() == 6) drawBar = m;
                drawBar.setAccessible(true);
            }
            var c = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
            Object data = mc.player.getCapability(c).resolve().orElse(null);
            if (data != null) drawBar.invoke(null, mc, w, h, e.getGuiGraphics(), 0, data);
        } catch (Throwable t) { lastShift = "test draw failed " + t; }
    }
}

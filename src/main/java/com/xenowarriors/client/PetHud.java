package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Small pet cards under the party cards (top right): each pet out with you - name, HP, Karma, and Ki with DragonMineZ. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PetHud {
    private static int[] ids = new int[0];
    private static float[] karma = new float[0], kmax = new float[0], ki = new float[0], kimax = new float[0];
    private static long stamp;

    private PetHud() {}

    public static void set(int[] i, float[] k, float[] km, float[] q, float[] qm) {
        ids = i; karma = k; kmax = km; ki = q; kimax = qm;
        var mc = Minecraft.getInstance();
        stamp = mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static boolean shown(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.options.hideGui || ids.length == 0 || mc.level.getGameTime() - stamp > 60) return false;
        return ClientSettings.khHud() || ClientSettings.vanillaBars();
    }

    /** How much of the top-right column the pet cards need (HudLayout puts request cards under them). */
    public static int blockHeight() {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc)) return 0;
        int h = 10;
        for (int i = 0; i < ids.length; i++)
            if (mc.level.getEntity(ids[i]) instanceof LivingEntity) h += (kimax[i] > 0 ? 24 : 19) + 4;
        return h + 2;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc)) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int cw = 96, x = w - cw - 4, y = HudLayout.petTop();      // (its own slot in the top-right column - it used to sit under request cards)
        g.drawString(mc.font, "Pets", x, y, 0xFFE8D8A0, true);
        y += 10;
        for (int i = 0; i < ids.length; i++) {
            Entity e = mc.level.getEntity(ids[i]);
            if (!(e instanceof LivingEntity le)) continue;
            boolean hasKi = kimax[i] > 0;
            int h = hasKi ? 24 : 19;
            g.fill(x - 1, y - 1, x + cw + 1, y + h + 1, 0x99000000);
            String name = e.getDisplayName().getString();
            if (name.length() > 14) name = name.substring(0, 13) + ".";
            g.pose().pushPose(); g.pose().translate(x + 2, y + 1, 0); g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(mc.font, name, 0, 0, 0xFFFFFFFF, true);
            g.pose().popPose();
            bar(g, x + 2, y + 9, cw - 4, 3, le.getHealth() / Math.max(1F, le.getMaxHealth()), 0xFF5AD25A);
            bar(g, x + 2, y + 14, cw - 4, 2, karma[i] / Math.max(1F, kmax[i]), 0xFFB45CFF);
            if (hasKi) bar(g, x + 2, y + 19, cw - 4, 2, ki[i] / Math.max(1F, kimax[i]), 0xFF5AC8FF);
            y += h + 4;
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int col) {
        g.fill(x, y, x + w, y + h, 0xFF202020);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, f))), y + h, col);
    }
}

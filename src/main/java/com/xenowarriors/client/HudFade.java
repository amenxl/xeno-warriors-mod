package com.xenowarriors.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Out of combat your HUD slowly fades see-through, and comes straight back when a fight starts. A fight is: being hit,
 * swinging, losing or gaining health, spending Karma, being low on health, or an angry monster close by.
 */
public final class HudFade {
    private HudFade() {}

    /** Seconds of quiet before the fade starts, how long it takes, and how faint it gets. */
    private static final int WAIT = 6 * 20, FADE = 3 * 20;
    private static final float MIN = 0.25F;
    /** UI tests keep the HUD fully shown unless they're testing the fade. */
    public static boolean forceFull;
    /** True while the Blazing Wind HUD draws, so shared helpers (portraits, heads) fade with it. */
    public static boolean on;

    private static int lastTick = -1, quiet;
    private static float lastHp = -1, lastKarma = -1;
    private static float alpha = 1F;
    private static boolean angry;

    /** How see-through the HUD is right now (1 = fully shown). Updated once a game tick. */
    public static float alpha() {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || forceFull) return 1F;
        if (p.tickCount != lastTick) {
            int ticks = lastTick < 0 || p.tickCount < lastTick ? 1 : p.tickCount - lastTick;
            lastTick = p.tickCount;
            if (p.tickCount % 5 == 0) angry = angryNear(p);
            float hp = p.getHealth(), karma = ClientSkillState.karma();
            boolean fight = p.hurtTime > 0 || p.swinging || p.isDeadOrDying()
                    || (lastHp >= 0 && Math.abs(hp - lastHp) > 0.01F)
                    || (lastKarma >= 0 && karma < lastKarma - 0.01F)
                    || hp / Math.max(1F, p.getMaxHealth()) <= 0.25F
                    || (com.xenowarriors.client.journey.JourneyClient.active() && com.xenowarriors.client.journey.JourneyClient.phase() == 3)
                    || angry;
            lastHp = hp; lastKarma = karma;
            quiet = fight ? 0 : quiet + ticks;
            float target = quiet <= WAIT ? 1F : Math.max(MIN, 1F - (quiet - WAIT) / (float) FADE * (1F - MIN));
            alpha = target >= alpha ? Math.min(target, alpha + 0.25F * ticks) : target;   // (back quickly, away slowly)
        }
        return alpha;
    }

    private static boolean angryNear(net.minecraft.world.entity.player.Player p) {
        return !p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16),
                m -> m instanceof Enemy && m.isAggressive() && m.isAlive()).isEmpty();
    }

    /** Set the shader colour with the fade applied. */
    public static void color(float r, float g, float b, float a) { RenderSystem.setShaderColor(r, g, b, on ? a * alpha() : a); }

    /** Start drawing faded HUD parts. */
    public static void begin() { on = true; RenderSystem.enableBlend(); reset(); }

    /** Back to normal drawing. */
    public static void end() { on = false; RenderSystem.setShaderColor(1F, 1F, 1F, 1F); }

    /** Plain white with the fade applied (what the HUD resets to between tinted parts). */
    public static void reset() { color(1F, 1F, 1F, 1F); }
}

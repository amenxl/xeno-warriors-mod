package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of Karma Sensing: sensed entities get a glowing outline you can see through walls,
 * colored by what they are (hostile red, players violet, animals green, others gold), plus a purple
 * pulse across the screen and a ring of light rolling out from you.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KarmaSense {
    private static final Map<Integer, Long> SENSED = new HashMap<>();   // entity id -> game time it stops glowing
    private static final Map<Integer, Long> TRACKED = new HashMap<>();  // Fire Blitz targets (orange outline)
    private static long pulseStart = -1000;
    private static final Map<Integer, Integer> LEVELS = new HashMap<>();  // entity id -> how strong it is (1-150)
    private static boolean showLevels;

    public static void track(int id, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) TRACKED.put(id, mc.level.getGameTime() + duration);
    }

    private KarmaSense() {}

    private static final java.util.Set<Integer> SOULS = new java.util.HashSet<>();   // sensed with a corrupted soul inside

    /** Sensed as a corrupted soul (or carrying one, even hidden). */
    public static boolean soul(Entity e) {
        return SOULS.contains(e.getId()) && isSensed(e);
    }

    private static final Map<Integer, int[]> STATS = new HashMap<>();     // entity id -> {attack x10, defense}

    /** How strong a sensed thing is (its level), or 0 if unknown / hidden. */
    public static int level(int id) {
        Integer lv = LEVELS.get(id);
        return lv == null ? 0 : Math.max(0, lv);
    }

    private static boolean allStats;       // sensing past your render distance: everyone's stats, however strong

    /** You're stronger than it (or sensing that far): you can read its stats. */
    public static boolean outclass(Entity e) {
        Integer lv = LEVELS.get(e.getId());
        if (allStats && lv != null && lv >= 0) return true;
        return lv != null && lv >= 0 && com.xenowarriors.client.LevelHud.level() > lv;
    }

    public static void sensed(int[] ids, int[] levels, int duration, boolean readLevels, int[] souls, int[] damage, int[] armor, boolean first, boolean allStats) {
        KarmaSense.allStats = allStats;
        STATS.clear();
        for (int i = 0; i < ids.length && i < damage.length && i < armor.length; i++) STATS.put(ids[i], new int[]{damage[i], armor[i]});
        SOULS.clear();
        for (int id : souls) SOULS.add(id);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long until = mc.level.getGameTime() + duration;
        LEVELS.clear();
        for (int i = 0; i < ids.length; i++) {
            SENSED.put(ids[i], until);
            if (i < levels.length) LEVELS.put(ids[i], levels[i]);
        }
        showLevels = readLevels;
        if (first) pulseStart = mc.level.getGameTime();          // (not again on each refresh while focusing)
    }

    /** Karma Sensing is showing things right now. */
    public static boolean active() {
        return !SENSED.isEmpty();
    }

    public static boolean isSensed(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Long until = SENSED.get(e.getId());
        Long tracked = TRACKED.get(e.getId());
        long now = mc.level.getGameTime();
        return (until != null && now < until) || (tracked != null && now < tracked);
    }

    /** Sensed through a Karma-reducing hoodie: just a faint grey trace. */
    public static boolean faint(Entity e) {
        Integer lv = LEVELS.get(e.getId());
        return lv != null && lv < 0;
    }

    public static int color(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (faint(e)) return 0x6E6A7E;
        if (SOULS.contains(e.getId())) return 0x5A1A8A;                 // corrupted soul: dark purple
        Long tracked = TRACKED.get(e.getId());
        if (tracked != null && mc.level != null && mc.level.getGameTime() < tracked) return 0xFF7A1A;
        if (e instanceof Enemy) return 0xFF3A3A;
        if (e instanceof Player p) {
            // each warrior's Karma has its own color: Mikiko's burns orange-red
            if (ClientCharacterData.get(p) == com.xenowarriors.character.CharacterType.MIKIKO) return 0xFF4A1A;
            return 0xB45CFF;
        }
        if (e instanceof Animal) return 0x5CFF7A;
        return 0xFFC94A;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SENSED.clear();
            TRACKED.clear();
            return;
        }
        long now = mc.level.getGameTime();
        SENSED.values().removeIf(until -> until < now);
        LEVELS.keySet().removeIf(id -> !SENSED.containsKey(id));
        STATS.keySet().removeIf(id -> !SENSED.containsKey(id));
        // (with DragonMineZ its own Ki Sense auras show them instead, in these colors - see DmzKiSenseLink)
        if (now % 3 == 0 && !com.xenowarriors.client.compat.DmzKiSenseLink.karmaDriven()) auras(mc);
        TRACKED.values().removeIf(until -> until < now);
        // a ring of light rolling outward for the first second
        long age = now - pulseStart;
        if (mc.player != null && age >= 0 && age < 20) {
            double r = 1 + age * 2.2;
            for (int i = 0; i < 40; i++) {
                double a = i / 40.0 * Math.PI * 2;
                mc.level.addParticle(ParticleTypes.END_ROD, mc.player.getX() + Math.cos(a) * r, mc.player.getY() + 0.2,
                        mc.player.getZ() + Math.sin(a) * r, 0, 0.01, 0);
            }
        }
    }

    /**
     * Each sensed thing gives off an aura of its Karma color - the stronger it is, the bigger and denser the aura.
     */
    private static void auras(Minecraft mc) {
        for (Map.Entry<Integer, Integer> en : LEVELS.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || !e.isAlive() || e.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 96 * 96) continue;
            boolean faint = en.getValue() < 0;
            if (faint && mc.level.random.nextInt(3) != 0) continue;     // thin and flickering
            // the size shows how strong it is next to you: weaker things give off a small aura, equals a medium one
            float ratio = faint ? 0F : en.getValue() / (float) Math.max(1, com.xenowarriors.client.LevelHud.level());
            float power = faint ? 0.05F : Mth.clamp(0.08F + ratio * 0.45F, 0.08F, 1F);
            int c = color(e);
            net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((c >> 16) & 255) / 255F,
                    ((c >> 8) & 255) / 255F, (c & 255) / 255F), 0.6F + power * 1.4F);
            // stronger than you? the aura swells to show it: up to ~4 extra blocks wide and a towering column
            float threat = faint ? 0F : Math.max(0F, en.getValue() / (float) Math.max(10, com.xenowarriors.client.LevelHud.level()) - 1F);
            float swell = Math.min(1F, threat / 2F);                 // full size at 3x your level
            double radius = e.getBbWidth() * 0.6 + 0.3 + power * 2.2 + swell * 4.0;
            double height = e.getBbHeight() + power * 1.5 + swell * 5.0;
            int count = 2 + Math.round(power * 10) + Math.round(swell * 22);
            if (swell > 0F) dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((c >> 16) & 255) / 255F,
                    ((c >> 8) & 255) / 255F, (c & 255) / 255F), Math.min(4F, 0.6F + power * 1.4F + swell * 1.6F));
            var r = mc.level.random;
            for (int i = 0; i < count; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double rr = radius * (0.6 + r.nextDouble() * 0.4);
                mc.level.addParticle(dust, e.getX() + Math.cos(a) * rr, e.getY() + r.nextDouble() * height,
                        e.getZ() + Math.sin(a) * rr, 0, 0.02 + power * 0.04 + swell * 0.12, 0);
            }
            // a pillar of energy over something truly dangerous
            if (swell > 0.5F && r.nextInt(2) == 0) {
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, e.getX() + (r.nextDouble() - 0.5) * radius,
                        e.getY() + r.nextDouble() * height, e.getZ() + (r.nextDouble() - 0.5) * radius, 0, 0.15, 0);
            }
        }
    }

    /** At higher Karma Sensing, "Lv N" floats over everything sensed, visible through walls. */
    @SubscribeEvent
    public static void onRenderLiving(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
        net.minecraft.world.entity.LivingEntity e = event.getEntity();
        Integer lv = LEVELS.get(e.getId());
        if (lv == null || !isSensed(e)) return;
        boolean stats = outclass(e) && STATS.containsKey(e.getId());
        if (!showLevels && !stats) return;
        Minecraft mc = Minecraft.getInstance();
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, e.getBbHeight() + (e instanceof Player || e.hasCustomName() ? 0.85 : 0.5), 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.03F;
        pose.scale(-s, -s, s);
        int col = 0xFF000000 | color(e);
        float y = 0;
        if (stats) {
            // stronger than them: their stats laid bare (above the level)
            int[] st = STATS.get(e.getId());
            String hp = "\u2764 " + fmt(e.getHealth()) + "/" + fmt(e.getMaxHealth());
            String atk = "ATK " + fmt(st[0] / 10F);
            String def = "DEF " + st[1];
            String line = hp + "   " + atk + "   " + def;
            float lx = -mc.font.width(line) / 2F;
            var buf = event.getMultiBufferSource();
            var mat = pose.last().pose();
            var mode = net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH;
            int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            mc.font.drawInBatch(hp, lx, -10, 0xFFFF5A5A, false, mat, buf, mode, 0x60000000, light);
            lx += mc.font.width(hp + "   ");
            mc.font.drawInBatch(atk, lx, -10, 0xFFFFB04A, false, mat, buf, mode, 0x60000000, light);
            lx += mc.font.width(atk + "   ");
            mc.font.drawInBatch(def, lx, -10, 0xFF7AB8FF, false, mat, buf, mode, 0x60000000, light);
        }
        if (showLevels || stats) {
            String text = lv < 0 ? "Lv ?" : "Lv " + lv;
            float x = -mc.font.width(text) / 2F;
            mc.font.drawInBatch(text, x, y, col, false, pose.last().pose(), event.getMultiBufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0x60000000, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Purple pulse around the edges of the screen when you sense. */
    public static class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            float age = mc.level.getGameTime() - pulseStart + partialTick;
            if (age < 0 || age > 24) return;
            float a = Mth.sin(Mth.clamp(age / 24F, 0F, 1F) * (float) Math.PI) * 0.45F;
            int alpha = Math.round(a * 255);
            int edge = Math.max(8, width / 10);
            int tint = ClientCharacterData.getLocal() == com.xenowarriors.character.CharacterType.MIKIKO ? 0xFF4A1A : 0x8A3CFF;
            for (int i = 0; i < edge; i += 2) {
                int col = (Math.round(alpha * (1F - i / (float) edge)) << 24) | tint;
                g.fill(i, i, width - i, i + 2, col);
                g.fill(i, height - i - 2, width - i, height - i, col);
                g.fill(i, i, i + 2, height - i, col);
                g.fill(width - i - 2, i, width - i, height - i, col);
            }
        }
    }
}

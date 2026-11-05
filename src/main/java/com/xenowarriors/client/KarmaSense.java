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
 * Client side of Karma Sensing: sensed entities get a glowing outline and a big flickering aura you can see through
 * walls, colored by what they are (hostile red, players violet, animals green, others gold), plus a purple pulse across
 * the screen and a ring of light rolling out from you. The aura's size shows how strong each one is next to you: weaker
 * things a modest glow around them, your equals a tall one, and anything far stronger (or a boss) a giant blazing
 * column you can't miss.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KarmaSense {
    private static final Map<Integer, Long> SENSED = com.xenowarriors.client.ClientReset.track(new HashMap<>());   // entity id -> game time it stops glowing
    private static final Map<Integer, Long> TRACKED = com.xenowarriors.client.ClientReset.track(new HashMap<>());  // Fire Blitz targets (orange outline)
    private static long pulseStart = -1000;
    private static final Map<Integer, Integer> LEVELS = com.xenowarriors.client.ClientReset.track(new HashMap<>());  // entity id -> how strong it is (1-150)
    private static boolean showLevels;

    public static void track(int id, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) TRACKED.put(id, mc.level.getGameTime() + duration);
    }

    private static final Map<Integer, Long> DATA = com.xenowarriors.client.ClientReset.track(new HashMap<>());     // Ultima's Data Sense: green, scanned look

    /** Glow from Ultima's Data Sense. */
    public static void data(int id, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) DATA.put(id, mc.level.getGameTime() + duration);
    }

    private KarmaSense() {}

    private static final java.util.Set<Integer> SOULS = com.xenowarriors.client.ClientReset.trackAll(new java.util.HashSet<>());   // sensed with a corrupted soul inside

    /** Sensed as a corrupted soul (or carrying one, even hidden). */
    public static boolean soul(Entity e) {
        return SOULS.contains(e.getId()) && isSensed(e);
    }

    private static final Map<Integer, int[]> STATS = com.xenowarriors.client.ClientReset.track(new HashMap<>());     // entity id -> {attack x10, defense}

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

    private static final Map<Integer, Float> MIGHT = com.xenowarriors.client.ClientReset.track(new HashMap<>());     // entity id -> real strength (see KarmaSensing.strength)
    private static final java.util.Set<Integer> BOSSES = com.xenowarriors.client.ClientReset.trackAll(new java.util.HashSet<>());
    private static float selfMight = 1F;

    public static void sensed(int[] ids, int[] levels, int duration, boolean readLevels, int[] souls, int[] damage, int[] armor, boolean first, boolean allStats) {
        sensed(ids, levels, duration, readLevels, souls, damage, armor, first, allStats, new int[0], new int[0], 0F);
    }

    public static void sensed(int[] ids, int[] levels, int duration, boolean readLevels, int[] souls, int[] damage, int[] armor, boolean first, boolean allStats,
                              int[] might, int[] bosses, float self) {
        MIGHT.clear();
        for (int i = 0; i < ids.length && i < might.length; i++) MIGHT.put(ids[i], might[i] / 10F);
        BOSSES.clear();
        for (int id : bosses) BOSSES.add(id);
        selfMight = self > 0 ? self : 0F;
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
        Long data = DATA.get(e.getId());
        long now = mc.level.getGameTime();
        return (until != null && now < until) || (tracked != null && now < tracked) || (data != null && now < data);
    }

    /** Sensed through a Karma-reducing hoodie: just a faint grey trace. */
    public static boolean faint(Entity e) {
        Integer lv = LEVELS.get(e.getId());
        return lv != null && lv < 0;
    }

    public static int color(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (faint(e)) return 0x6E6A7E;
        Long dt = DATA.get(e.getId());
        if (dt != null && mc.level != null && mc.level.getGameTime() < dt && !SENSED.containsKey(e.getId())) return 0x33FF88;   // Data Sense: green
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
        DATA.values().removeIf(until -> until < now);
        LEVELS.keySet().removeIf(id -> !SENSED.containsKey(id));
        STATS.keySet().removeIf(id -> !SENSED.containsKey(id));
        MIGHT.keySet().removeIf(id -> !SENSED.containsKey(id));
        BOSSES.removeIf(id -> !SENSED.containsKey(id));
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

    // ------------------------------------------------------------------ the big auras

    /** (Tests / screenshots) true: no big auras - only the old outline and sparks. */
    public static boolean aurasOff;
    /** (Tests) auras drawn in the last frame. */
    public static int aurasDrawn;
    private static boolean auraWarned;
    /** Most auras drawn at once (nearest first would need a sort; these are simply the first ones in range). */
    public static final int MAX_AURAS = 256;
    public static final double AURA_RANGE = 192;

    /** How strong it is next to you (1 = your equal; above 1 stronger). Bosses never read below 6x. -1: unknown / masked. */
    public static float relative(Entity e) {
        Integer lv = LEVELS.get(e.getId());
        if (lv != null && lv < 0) return -1F;
        Float m = MIGHT.get(e.getId());
        float rel;
        if (m != null && m > 0 && selfMight > 0) rel = m / selfMight;
        else if (lv != null) rel = lv / (float) Math.max(1, com.xenowarriors.client.LevelHud.level());   // (no strength sent: by level)
        else return -1F;
        if (BOSSES.contains(e.getId())) rel = Math.max(rel, 6F);
        return rel;
    }

    /** Sensed as a boss. */
    public static boolean boss(Entity e) { return BOSSES.contains(e.getId()); }

    /**
     * The aura around a sensed thing: {radius, height, strength 0-1}. Everything gets a clear glow at least a body
     * wide; it grows with how strong the thing is next to you (log scale: a quarter of you or less = 0, your equal
     * ~0.33, 4x you ~0.67, 16x you or a boss ~0.85-1), up to a ~7-block-wide, ~15-block-tall blaze.
     */
    public static float[] auraSize(Entity e) {
        float rel = relative(e);
        boolean faint = rel < 0;
        float t = faint ? 0F : Mth.clamp((float) ((Math.log(Math.max(1e-3, rel)) / Math.log(2) + 2) / 6.0), 0F, 1F);
        if (!faint && boss(e)) t = Math.max(t, 0.85F);
        float radius = e.getBbWidth() * 0.5F + 0.9F + 0.8F * t + 4.8F * t * t;
        float height = e.getBbHeight() * (1.5F + 0.4F * t) + 1.0F + 11F * t * t;
        if (faint) { radius *= 0.7F; height *= 0.8F; }
        return new float[]{radius, height, faint ? -1F : t};
    }

    @SubscribeEvent
    public static void onRenderAuras(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SENSED.isEmpty()) { aurasDrawn = 0; return; }
        if (aurasOff) { aurasDrawn = 0; return; }
        float pt = event.getPartialTick();
        long gt = mc.level.getGameTime();
        float time = gt + pt;
        net.minecraft.world.phys.Vec3 cam = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        pose.pushPose();
        // (vertices are made relative to the camera in aura(): world coordinates as floats shake far from 0,0)
        org.joml.Matrix4f m = pose.last().pose();
        java.util.List<Entity> list = new java.util.ArrayList<>();
        for (Map.Entry<Integer, Long> en : SENSED.entrySet()) {
            if (en.getValue() < gt || list.size() >= MAX_AURAS) continue;
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || !e.isAlive() || e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (e.distanceToSqr(cam) > AURA_RANGE * AURA_RANGE) continue;
            list.add(e);
        }
        int drawn = 0;
        if (!list.isEmpty()) {
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.disableCull();
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();          // (seen through walls, like the outline)
            // pass 0: a soft tint (shows on bright sky too); pass 1: the glow itself (additive)
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 0) com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                else com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
                var bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
                bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
                for (Entity e : list) {
                    try { aura(bb, m, e, cam, pt, time, pass == 1); if (pass == 1) drawn++; }
                    catch (RuntimeException ex) { if (!auraWarned) { auraWarned = true; XenoWarriors.LOGGER.error("Karma aura draw failed", ex); } }
                }
                com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
            }
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
            com.mojang.blaze3d.systems.RenderSystem.enableCull();
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
        pose.popPose();
        aurasDrawn = drawn;
    }

    private static final int SEG = 36;

    /** One aura: a camera-facing (upright) flame of rings - a dim middle, a bright band, fading out to flickering tongues. */
    private static void aura(com.mojang.blaze3d.vertex.BufferBuilder bb, org.joml.Matrix4f m, Entity e, net.minecraft.world.phys.Vec3 cam, float pt, float time, boolean glow) {
        float[] size = auraSize(e);
        float radius = size[0], height = size[1], t = Math.max(0F, size[2]);
        boolean faint = size[2] < 0;
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
        double dx = x - cam.x, dz = z - cam.z, len = Math.sqrt(dx * dx + dz * dz);
        float rx, rz;
        if (len < 1e-3) { rx = 1; rz = 0; } else { rx = (float) (-dz / len); rz = (float) (dx / len); }
        int c = color(e);
        float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
        float hot = Mth.clamp((t - 0.55F) * 1.6F, 0F, 0.7F);                     // the strongest burn white-hot inside
        float ir = r + (1 - r) * hot, ig = g + (1 - g) * hot, ib = b + (1 - b) * hot;
        float seed = (e.getId() * 0.6180339F) % 1F * 20F;
        float pulse = 1F + 0.18F * t * Mth.sin(time * (0.25F + 0.2F * t) + seed);
        float a = faint ? 0.18F : (0.42F + 0.4F * t) * pulse;
        if (!glow) a *= faint ? 0.3F : 0.35F;
        float cy = height * 0.42F, hh = height * 0.58F;
        float[] scale = {0.5F, 0.8F, 1F};
        float[] alpha = {a * 0.9F, a * 0.75F, 0F};
        float[][] col = {{ir, ig, ib}, {r, g, b}, {r, g, b}};
        float[][] px = new float[3][SEG + 1], py = new float[3][SEG + 1];
        for (int k = 0; k < 3; k++) for (int i = 0; i <= SEG; i++) {
            float th = (float) (i * Math.PI * 2 / SEG);
            float s = Mth.sin(th), co = Mth.cos(th);
            float wob = 1F + 0.08F * Mth.sin(th * 7 + time * 0.35F + seed) + 0.05F * Mth.sin(th * 3 - time * 0.21F);
            float tongue = s > 0 ? 0.45F * s * s * s * (0.55F + 0.45F * Mth.sin(th * 9 - time * (0.5F + 0.4F * t) + seed)) * (k == 0 ? 0.6F : 1F) : 0F;
            px[k][i] = co * radius * scale[k] * wob;
            py[k][i] = cy + s * hh * scale[k] * (s > 0 ? 1F + tongue : 0.8F);
        }
        float ca = a * 0.35F;
        x -= cam.x; y -= cam.y; z -= cam.z;
        for (int i = 0; i < SEG; i++) {
            // the middle: dim, so what's inside still shows
            v(bb, m, x, y + cy, z, rx, rz, 0, 0, ir, ig, ib, ca);
            v(bb, m, x, y, z, rx, rz, px[0][i], py[0][i], ir, ig, ib, alpha[0]);
            v(bb, m, x, y, z, rx, rz, px[0][i + 1], py[0][i + 1], ir, ig, ib, alpha[0]);
            for (int k = 0; k < 2; k++) {
                float[] c0 = col[k], c1 = col[k + 1];
                v(bb, m, x, y, z, rx, rz, px[k][i], py[k][i], c0[0], c0[1], c0[2], alpha[k]);
                v(bb, m, x, y, z, rx, rz, px[k + 1][i], py[k + 1][i], c1[0], c1[1], c1[2], alpha[k + 1]);
                v(bb, m, x, y, z, rx, rz, px[k + 1][i + 1], py[k + 1][i + 1], c1[0], c1[1], c1[2], alpha[k + 1]);
                v(bb, m, x, y, z, rx, rz, px[k][i], py[k][i], c0[0], c0[1], c0[2], alpha[k]);
                v(bb, m, x, y, z, rx, rz, px[k + 1][i + 1], py[k + 1][i + 1], c1[0], c1[1], c1[2], alpha[k + 1]);
                v(bb, m, x, y, z, rx, rz, px[k][i + 1], py[k][i + 1], c0[0], c0[1], c0[2], alpha[k]);
            }
        }
    }

    private static void v(com.mojang.blaze3d.vertex.BufferBuilder bb, org.joml.Matrix4f m, double x, double y, double z, float rx, float rz, float u, float h,
                          float r, float g, float b, float a) {
        bb.vertex(m, (float) (x + rx * u), (float) (y + h), (float) (z + rz * u)).color(r, g, b, Mth.clamp(a, 0F, 1F)).endVertex();
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

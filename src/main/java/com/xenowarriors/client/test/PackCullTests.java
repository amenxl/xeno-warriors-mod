package com.xenowarriors.client.test;

import com.xenowarriors.client.KarmaSense;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.entity.BlazingWindEntity;
import com.xenowarriors.entity.GravityCompressEntity;
import com.xenowarriors.pickup.HealthOrbEntity;
import com.xenowarriors.pickup.HealthOrbs;
import com.xenowarriors.pickup.XpOrbs;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Pack test part 2 (run from {@link PackTests}): the things EntityCulling and Clumps could break. EntityCulling hides
 * entities whose box it thinks is behind blocks and skips their client ticks; Clumps merges experience orbs.
 * <ul>
 *   <li>Karma Sense: a husk behind a wall still glows and has its aura.</li>
 *   <li>Blazing Wind (a still tornado, as in Blazing Judgment) whose base is behind a wall: the funnel rising over it is drawn.</li>
 *   <li>Gravity Compress around a husk behind a low wall: the sphere poking over it is drawn.</li>
 *   <li>Health orbs: dropped, seen on the client, drift to you and heal.</li>
 *   <li>XP: orbs picked up the normal way give all their experience; handed out directly (a Journey's leftovers) too.</li>
 * </ul>
 * Logs "XW-TEST pack cull ..." through PackTests.check.
 */
final class PackCullTests {
    private PackCullTests() {}

    // ------------------------------------------------------------------ EntityCulling's per-entity state (by reflection; the mod is optional)

    private static Class<?> cullable;
    private static Method isCulled, isForced, isOut;
    private static boolean looked;

    static boolean ecLoaded() { return ModList.get().isLoaded("entityculling"); }

    private static void look() {
        if (looked) return;
        looked = true;
        try {
            cullable = Class.forName("dev.tr7zw.entityculling.access.Cullable");
            isCulled = cullable.getMethod("isCulled");
            isForced = cullable.getMethod("isForcedVisible");
            isOut = cullable.getMethod("isOutOfCamera");
        } catch (Throwable t) {
            cullable = null;
        }
    }

    /** True if EntityCulling is currently hiding this entity (culled and not kept visible). */
    static boolean hidden(Entity e) {
        look();
        if (cullable == null || e == null || !cullable.isInstance(e)) return false;
        try { return (boolean) isCulled.invoke(e) && !(boolean) isForced.invoke(e) && !e.noCulling; } catch (Throwable t) { return false; }
    }

    static String state(Entity e) {
        look();
        if (e == null) return "missing on the client";
        if (cullable == null) return "entityculling " + (ecLoaded() ? "loaded, API not found" : "not loaded") + " noCulling=" + e.noCulling;
        try {
            return "culled=" + isCulled.invoke(e) + " forcedVisible=" + isForced.invoke(e) + " outOfCamera=" + isOut.invoke(e) + " noCulling=" + e.noCulling;
        } catch (Throwable t) { return String.valueOf(t); }
    }

    private static <T extends Entity> T near(Minecraft mc, Class<T> c, double r) {
        List<T> l = mc.level.getEntitiesOfClass(c, mc.player.getBoundingBox().inflate(r));
        return l.isEmpty() ? null : l.get(0);
    }

    /** A stone wall across the arena, {@code dz} in front of home, {@code h} blocks high. */
    private static void wall(ServerPlayer p, int dz, int h) {
        BlockPos base = BlockPos.containing(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2]);
        for (int x = -14; x <= 14; x++) for (int y = 0; y < 12; y++)
            p.serverLevel().setBlock(base.offset(x, y, dz), y < h ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
    }

    private static void look(Minecraft mc, float pitch) {
        var p = mc.player;
        if (p == null) return;
        p.setYRot(0F); p.yRotO = 0F; p.setYHeadRot(0F); p.yBodyRot = 0F; p.setXRot(pitch); p.xRotO = pitch;
    }

    private static void discardAll(ServerPlayer p, Class<? extends Entity> c) {
        p.serverLevel().getEntitiesOfClass(c, p.getBoundingBox().inflate(64)).forEach(Entity::discard);
    }

    // ------------------------------------------------------------------ the steps

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> {
            mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false;
            UiTest.character(com.xenowarriors.character.CharacterType.MIKIKO);
            PackTests.log("cull entityculling loaded=" + ecLoaded() + " clumps loaded=" + ModList.get().isLoaded("clumps")
                    + " jade=" + ModList.get().isLoaded("jade") + " jei=" + ModList.get().isLoaded("jei"));
        }, 30));
        sense(mc, l);
        wind(mc, l);
        compress(mc, l);
        orbs(mc, l);
        xp(mc, l);
        l.add(new Step(null, () -> PackTests.server(p -> { wall(p, 4, 0); PackTests.clear(p); PackTests.arena(p); }), 10));
    }

    /** Karma Sense on a husk hidden behind a 5-high wall: it glows (outline through the wall) and gets its aura. */
    private static void sense(Minecraft mc, List<Step> l) {
        int[] id = {-1};
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p); p.setGameMode(GameType.SURVIVAL);
            p.getPersistentData().remove("xw_karma_lock_until");
            com.xenowarriors.karma.Karma.add(p, 200);
            wall(p, 4, 5);
            id[0] = PackTests.husk(p, 1, 12).getId();
        }), 40));
        l.add(new Step(null, () -> { look(mc, 0F); com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket(true, true, mc.options.getEffectiveRenderDistance())); }, 40));
        l.add(new Step("pack_cull_sense_wall", () -> PackTests.client("cull sense", () -> {
            look(mc, 0F);
            Entity e = mc.level.getEntity(id[0]);
            boolean sensed = e != null && KarmaSense.isSensed(e);
            boolean glow = e != null && mc.shouldEntityAppearGlowing(e);
            PackTests.check("cull karma sense through a wall", e != null && sensed && glow && !hidden(e) && KarmaSense.aurasDrawn > 0,
                    "husk behind a 5-high wall: sensed=" + sensed + " glowing=" + glow + " auras drawn " + KarmaSense.aurasDrawn + ", " + state(e));
        }), 3));
        l.add(new Step(null, () -> com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket(false, false, 0)), 20));
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.clear(p); p.setGameMode(GameType.CREATIVE); }), 10));
    }

    /** A still Blazing Wind tornado 12 blocks out, its base (the entity's box) behind a 5-high wall; the 16-high funnel rises over it. */
    private static void wind(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            wall(p, 4, 5);
            var w = BlazingWindEntity.createStill(p, p, new Vec3(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] + 12), 600);
            p.serverLevel().addFreshEntity(w);
        }), 70));
        l.add(new Step(null, () -> look(mc, -32F), 30));
        l.add(new Step("pack_cull_wind_wall", () -> PackTests.client("cull wind", () -> {
            look(mc, -32F);
            BlazingWindEntity w = near(mc, BlazingWindEntity.class, 40);
            PackTests.check("cull blazing wind behind its origin", w != null && !hidden(w),
                    "tornado base behind a 5-high wall, funnel height " + (w == null ? "-" : PackTests.f(w.height(w.tickCount))) + ": " + state(w));
        }), 3));
        l.add(new Step(null, () -> PackTests.server(p -> discardAll(p, BlazingWindEntity.class)), 10));
    }

    /** Gravity Compress around a husk behind a 2-high wall: the husk and the effect's box are hidden, its 3.4-block sphere isn't. */
    private static void compress(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.clear(p);
            wall(p, 4, 2);
            LivingEntity h = PackTests.husk(p, 0, 10);
            p.serverLevel().addFreshEntity(GravityCompressEntity.create(p.serverLevel(), p, h));
        }), 12));
        l.add(new Step("pack_cull_compress_wall", () -> PackTests.client("cull compress", () -> {
            look(mc, 0F);
            GravityCompressEntity g = near(mc, GravityCompressEntity.class, 40);
            PackTests.check("cull gravity compress behind a wall", g != null && !hidden(g),
                    "sphere radius " + (g == null ? "-" : PackTests.f(g.radius(g.tickCount))) + " around a husk behind a 2-high wall: " + state(g));
        }), 3));
        l.add(new Step(null, () -> PackTests.server(p -> { discardAll(p, GravityCompressEntity.class); PackTests.clear(p); wall(p, 4, 0); }), 10));
    }

    /** Health orbs: two dropped 4 blocks in front of a hurt player; seen on the client, then they drift in and heal. */
    private static void orbs(Minecraft mc, List<Step> l) {
        int[] before = new int[1];
        float[] hp = new float[1];
        // (at full health first: orbs only drift to someone who's hurt, so they wait to be seen even at shader FPS)
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p); discardAll(p, HealthOrbEntity.class);
            p.setGameMode(GameType.SURVIVAL);
            p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
            p.setHealth(p.getMaxHealth());
            before[0] = HealthOrbs.collectedCount;
            LivingEntity h = PackTests.husk(p, 0, 4);
            HealthOrbs.drop(p.serverLevel(), h);
            HealthOrbs.drop(p.serverLevel(), h);
            h.discard();
        }), 30));
        l.add(new Step("pack_cull_orbs", () -> PackTests.client("cull orbs seen", () -> {
            look(mc, 20F);
            var seen = mc.level.getEntitiesOfClass(HealthOrbEntity.class, mc.player.getBoundingBox().inflate(16));
            PackTests.check("health orbs on the client", !seen.isEmpty() && seen.stream().noneMatch(PackCullTests::hidden),
                    seen.size() + " orbs, " + (seen.isEmpty() ? "-" : state(seen.get(0))));
        }), 3));
        l.add(new Step(null, () -> PackTests.server(p -> { p.setHealth(p.getMaxHealth() * 0.5F); hp[0] = p.getHealth(); }), 80));
        l.add(new Step(null, () -> PackTests.server(p -> {
            int got = HealthOrbs.collectedCount - before[0];
            PackTests.check("health orbs picked up", got == 2 && p.getHealth() > hp[0],
                    got + " collected, health " + PackTests.f(hp[0]) + " -> " + PackTests.f(p.getHealth()) + ", left " + p.serverLevel().getEntitiesOfClass(HealthOrbEntity.class, p.getBoundingBox().inflate(16)).size());
            p.setHealth(p.getMaxHealth());
        }), 5));
    }

    /** XP: 7 x5 + 3 + 11 = 49 dropped at your feet and picked up; the same far away handed out like a Journey's leftovers. */
    private static void xp(Minecraft mc, List<Step> l) {
        int[] start = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p); discardAll(p, ExperienceOrb.class);
            p.setGameMode(GameType.SURVIVAL);
            p.setExperienceLevels(0); p.setExperiencePoints(0); p.totalExperience = 0;
            start[0] = p.totalExperience;
            Vec3 at = p.position().add(0, 0.5, 1.5);
            for (int i = 0; i < 5; i++) ExperienceOrb.award(p.serverLevel(), at, 7);
            ExperienceOrb.award(p.serverLevel(), at, 3);
            ExperienceOrb.award(p.serverLevel(), at, 11);
        }), 5));
        l.add(new Step("pack_cull_xp", () -> PackTests.server(p -> PackTests.log("cull xp orbs on the ground now: "
                + p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(8)).size())), 90));
        l.add(new Step(null, () -> PackTests.server(p -> {
            int gained = p.totalExperience - start[0];
            int left = p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, p.getBoundingBox().inflate(8)).size();
            PackTests.check("xp pickup", gained == 49 && left == 0, "gained " + gained + " of 49 (level " + p.experienceLevel + "), orbs left " + left);
            // the same amount far away, handed straight to the player (Journey.handOutDrops)
            Vec3 far = new Vec3(PackTests.HOME[0], PackTests.HOME[1] + 0.5, PackTests.HOME[2] + 20);
            for (int i = 0; i < 5; i++) ExperienceOrb.award(p.serverLevel(), far, 7);
            ExperienceOrb.award(p.serverLevel(), far, 3);
            ExperienceOrb.award(p.serverLevel(), far, 11);
        }), 10));
        l.add(new Step(null, () -> PackTests.server(p -> {
            Vec3 far = new Vec3(PackTests.HOME[0], PackTests.HOME[1] + 0.5, PackTests.HOME[2] + 20);
            var orbs = p.serverLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(far, far).inflate(6));
            int face = 0;
            for (var o : orbs) face += o.getValue();
            int before = p.totalExperience, given = 0;
            for (var o : orbs) given += XpOrbs.giveAll(p, o);
            PackTests.check("xp handed out", p.totalExperience - before == 49,
                    orbs.size() + " orbs (merged), face values add up to " + face + ", handed out " + given + " (total +" + (p.totalExperience - before) + ")");
            p.setGameMode(GameType.CREATIVE);
        }), 10));
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotPotions;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.bot.ReviveAftermath;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.ServerScheduler;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * UI tests for the party bots' AI (focus "botai..."): getting about (gap jumps, stepping up, flying up to what it
 * can't walk to, a turn about the sky, refusing cliffs, out of water and lava), and fighting (potions, bows with no
 * arrows, TACZ guns, never touching the owner's Soul Splitter soul, enemies leaving the downed for the reviver, and
 * the revive's invincibility and stunning shockwave). Each result is a "XW-TEST botai ..." line in the log.
 *   botai     - getting about
 *   botai2    - potions, bow, soul, downed / revive
 *   botaitacz - a TACZ gun (CI loads TACZ for any focus with "tacz" in it)
 */
public final class BotAiTests {
    private BotAiTests() {}

    /** Where UiTest's steps go: (screenshot name or null, what to do, ticks to wait after). */
    public interface StepSink { void add(String name, Runnable action, int delay); }

    private static int X, Y, Z;                                  // the arena: feet level on platform A, its centre

    private static void log(String s) { com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST botai " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); com.xenowarriors.XenoWarriors.LOGGER.error("botai", t); } });
    }

    private static String f(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        if (p == null) return null;   // (a scheduled check can still run after the client has left)
        for (var b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static PartyBotEntity spawnBot(ServerPlayer p, CharacterType t, double x, double y, double z) {
        PartyBotEntity b = bot(p, t);
        if (b == null) b = PartyBots.spawn(p, t, null);
        if (b == null) { log("spawn " + t.getId() + " FAILED"); return null; }
        b.inventory.clearContent();
        b.moveTo(x, y, z, 0, 0);
        b.setTarget(null);
        b.setMode(PartyBotEntity.NEUTRAL);
        var hp = b.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(200);                    // (tough enough to sit in lava a while, or take a zombie's hits)
        b.setHealth(b.getMaxHealth());
        return b;
    }

    private static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (BlockPos q : BlockPos.betweenClosed(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1))) l.setBlock(q, s, 3);
    }

    /** Dummies' health, set again a few ticks after they spawn (after the mob scaling has had its go). */
    static final float DUMMY_HP = 300F;

    private static Zombie dummy(ServerPlayer p, double x, double y, double z, boolean ai, String tag) { return dummy(p, x, y, z, ai, tag, DUMMY_HP); }

    /** A zombie to fight (hp: 150+ counts as a boss for stuns and staggers). Kept out of the level scaling so its health stays put. */
    private static Zombie dummy(ServerPlayer p, double x, double y, double z, boolean ai, String tag, float hp) {
        Zombie zb = EntityType.ZOMBIE.create(p.serverLevel());
        if (zb == null) return null;
        zb.moveTo(x, y, z, 180, 0);
        zb.setNoAi(!ai);
        zb.setPersistenceRequired();
        zb.addTag("xw_botai"); zb.addTag(tag);
        zb.getPersistentData().putInt("xw_lvl", 0);                              // (EnemyScaling: already has a level, leave it be)
        p.serverLevel().addFreshEntity(zb);
        zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp); zb.setHealth(hp);
        return zb;
    }

    private static Zombie tagged(ServerPlayer p, String tag) {
        var l = p.serverLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(64), z -> z.getTags().contains(tag));
        return l.isEmpty() ? null : l.get(0);
    }

    private static void clearDummies(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(80), z -> z.getTags().contains("xw_botai")).forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** Every `every` ticks for `ticks` ticks, on the server. */
    private static void sample(int ticks, int every, Runnable r) {
        for (int t = every; t <= ticks; t += every) ServerScheduler.later(t, r);
    }

    /** Point the camera at an entity (client side). */
    private static void lookAt(Minecraft mc, LivingEntity e) {
        if (mc.player == null || e == null) return;
        var c = mc.level.getEntity(e.getId());
        if (c == null) return;
        double dx = c.getX() - mc.player.getX(), dy = c.getY() + c.getBbHeight() * 0.5 - mc.player.getEyeY(), dz = c.getZ() - mc.player.getZ();
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    public static void build(String focus, Minecraft mc, StepSink l) {
        l.add(null, () -> {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
            mc.setScreen(null);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.MIKIKO));
        }, 40);
        l.add(null, () -> server(BotAiTests::arena), 40);
        switch (focus) {
            case "botai2" -> fighting(mc, l);
            case "botaitacz" -> gun(mc, l);
            default -> moving(mc, l);
        }
        l.add(null, () -> server(p -> { clearDummies(p); log("done"); }), 10);
    }

    /**
     * A stone platform up in the sky (A: 13x13), a second one (B) across a 2-block gap, a lower one 10 blocks down off
     * the west edge, a 6-high pillar, an 8-high tower, a water pool and a lava pool on the south side.
     */
    private static void arena(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        X = p.getBlockX(); Z = p.getBlockZ(); Y = Math.min(lv.getMaxBuildHeight() - 30, p.getBlockY() + 30);
        BlockState st = Blocks.SMOOTH_STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        fill(lv, X - 20, Y - 12, Z - 12, X + 16, Y + 10, Z + 14, air);
        fill(lv, X - 6, Y - 1, Z - 6, X + 6, Y - 1, Z + 6, st);                                  // A
        fill(lv, X + 9, Y - 1, Z - 2, X + 14, Y - 1, Z + 2, st);                                 // B (x 7, 8: the gap)
        fill(lv, X - 18, Y - 11, Z - 3, X - 10, Y - 11, Z + 3, st);                              // the lower platform, 10 down
        fill(lv, X + 3, Y, Z - 5, X + 3, Y + 5, Z - 5, st);                                      // the pillar (top at Y+6)
        fill(lv, X - 5, Y, Z - 5, X - 4, Y + 7, Z - 4, st);                                      // the tower (top at Y+8)
        fill(lv, X + 4, Y, Z + 2, X + 5, Y, Z + 3, st);                                          // a one-block step
        // the pools: stone basins against A's south edge, water (3 deep) and lava (2 deep)
        fill(lv, X - 3, Y - 4, Z + 6, X + 3, Y - 1, Z + 12, st);
        fill(lv, X - 2, Y - 3, Z + 7, X + 2, Y - 1, Z + 11, Blocks.WATER.defaultBlockState());
        fill(lv, X + 4, Y - 3, Z + 6, X + 10, Y - 1, Z + 12, st);
        fill(lv, X + 5, Y - 2, Z + 7, X + 9, Y - 1, Z + 11, Blocks.LAVA.defaultBlockState());
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        p.getAbilities().flying = false; p.onUpdateAbilities();
        p.teleportTo(lv, X + 0.5, Y, Z + 0.5, 0, 10);
        p.serverLevel().setDayTime(6000);
        clearDummies(p);
        log("arena at " + X + " " + Y + " " + Z);
    }

    // ---------------------------------------------------------------- getting about

    private static void moving(Minecraft mc, StepSink l) {
        final double[] m = new double[4];
        // 1) a gap: Kaeru (no wings) after a dummy on platform B, two blocks of sky between
        l.add(null, () -> server(p -> {
            p.teleportTo(p.serverLevel(), X - 1.5, Y, Z + 0.5, -90, 10);
            var k = spawnBot(p, CharacterType.KAERU, X + 3.5, Y, Z + 0.5);
            Zombie zb = dummy(p, X + 12.5, Y, Z + 0.5, false, "gap");
            if (k != null && zb != null) { k.setTarget(zb); m[0] = DUMMY_HP; }
            m[1] = 1e9;
            sample(160, 5, () -> { var kk = bot(sp(), CharacterType.KAERU); if (kk != null) m[1] = Math.min(m[1], kk.getY()); });
        }), 100);
        l.add("botai_gap", () -> {}, 60);
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU); Zombie zb = tagged(p, "gap");
            if (k == null) return;
            boolean onB = k.getX() >= X + 9 && k.getY() >= Y - 0.5;
            log("gap: kaeru at " + f(k.getX() - X) + "," + f(k.getY() - Y) + " onB=" + onB + " lowestY=" + f(m[1] - Y) + " fell=" + (m[1] < Y - 2) + " leap=[" + k.lastLeap + "] dummy dealt=" + f(zb == null ? -1 : m[0] - zb.getHealth()));
            if (zb != null) zb.discard();
            k.setTarget(null);
            k.moveTo(X + 0.5, Y, Z + 2.5);
        }), 10);
        // 2) a 10-block drop: a dummy on the lower platform; it may not walk off (and won't be pushed off by its own legs)
        l.add(null, () -> server(p -> {
            p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 0.5, 90, 10);
            var k = bot(p, CharacterType.KAERU);
            Zombie zb = dummy(p, X - 14.5, Y - 10, Z + 0.5, false, "drop");
            if (k == null || zb == null) return;
            k.moveTo(X - 3.5, Y, Z + 0.5);
            k.setTarget(zb);
            m[1] = 1e9; m[2] = k.edgeStops;
            sample(200, 5, () -> { var kk = bot(sp(), CharacterType.KAERU); if (kk != null) m[1] = Math.min(m[1], kk.getY()); });
        }), 100);
        l.add(null, () -> server(p -> {                                         // then walk it straight at the edge, no path, for 5 s
            var k = bot(p, CharacterType.KAERU);
            if (k == null) return;
            k.setTarget(null);
            for (int t = 0; t < 100; t++) ServerScheduler.later(t, () -> { var kk = bot(sp(), CharacterType.KAERU); if (kk != null) kk.getMoveControl().setWantedPosition(X - 14.5, Y, Z + 0.5, 1.2); });
        }), 110);
        l.add("botai_cliff", () -> {}, 3);
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU);
            if (k == null) return;
            log("cliff: kaeru at x=" + f(k.getX() - X) + " y=" + f(k.getY() - Y) + " lowestY=" + f(m[1] - Y) + " fell=" + (m[1] < Y - 1.5) + " edge stops=" + (int) (k.edgeStops - m[2]) + " (edge of A is x=-6.0)");
            Zombie zb = tagged(p, "drop"); if (zb != null) zb.discard();
            k.moveTo(X + 0.5, Y, Z + 2.5);
        }), 10);
        // 3) a one-block step: a dummy up on it
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU);
            Zombie zb = dummy(p, X + 5.5, Y + 1, Z + 3.5, false, "step");
            if (k == null || zb == null) return;
            k.moveTo(X - 1.5, Y, Z + 2.5);
            k.setTarget(zb); m[0] = DUMMY_HP;
        }), 120);
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU); Zombie zb = tagged(p, "step");
            if (k == null) return;
            log("step: kaeru y=" + f(k.getY() - Y) + " dist=" + f(zb == null ? -1 : k.distanceTo(zb)) + " dummy dealt=" + f(zb == null ? -1 : m[0] - zb.getHealth()));
            if (zb != null) zb.discard();
            k.setTarget(null);
        }), 5);
        // 4) Rhalix (wings) after a dummy on top of a 6-high pillar
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU);
            if (k != null) PartyBots.action(p, k.getId(), PartyBots.ACT_DISMISS, 0);
            p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 3.5, 180, -20);
            var r = spawnBot(p, CharacterType.RHALIX, X - 1.5, Y, Z + 1.5);
            Zombie zb = dummy(p, X + 3.5, Y + 6, Z - 4.5, false, "pillar");
            if (r == null || zb == null) return;
            r.setTarget(zb); m[0] = DUMMY_HP; m[1] = 0;
            sample(160, 5, () -> { var rr = bot(sp(), CharacterType.RHALIX); if (rr != null) m[1] = Math.max(m[1], rr.getY() - Y); });
        }), 80);
        l.add("botai_pillar", () -> { ServerPlayer p = sp(); if (p != null) lookAt(mc, tagged(p, "pillar")); }, 80);
        l.add(null, () -> server(p -> {
            var r = bot(p, CharacterType.RHALIX); Zombie zb = tagged(p, "pillar");
            if (r == null) return;
            log("pillar: rhalix highest=" + f(m[1]) + " (dummy stands at +6) air hits=" + r.airHits + " dummy dealt=" + f(zb == null ? -1 : m[0] - zb.getHealth()) + " goals=[" + r.goalsForTest() + "]");
            if (zb != null) zb.discard();
            r.setTarget(null);
        }), 60);
        l.add(null, () -> server(p -> { var r = bot(p, CharacterType.RHALIX); if (r != null) log("pillar after: rhalix y=" + f(r.getY() - Y) + " onGround=" + r.onGround() + " noGravity=" + r.isNoGravity() + " health=" + f(r.getHealth()) + "/" + f(r.getMaxHealth())); }), 5);
        // 5) the owner up on an 8-high tower: Rhalix flies up to them
        l.add(null, () -> server(p -> {
            var r = bot(p, CharacterType.RHALIX);
            if (r != null) r.moveTo(X + 4.5, Y, Z + 4.5);
            p.teleportTo(p.serverLevel(), X - 4.0, Y + 8, Z - 4.0, 135, 30);
        }), 140);
        l.add("botai_tower", () -> server(p -> { var r = bot(p, CharacterType.RHALIX);
            if (r != null) log("tower: owner at +8, rhalix at y=" + f(r.getY() - Y) + " dist=" + f(r.distanceTo(p)) + " flying=" + r.selfFlyingForTest() + " goals=[" + r.goalsForTest() + "]"); }), 40);
        l.add(null, () -> server(p -> { var r = bot(p, CharacterType.RHALIX); if (r != null) log("tower after 2s more: rhalix y=" + f(r.getY() - Y) + " dist=" + f(r.distanceTo(p)) + " onGround=" + r.onGround()); }), 5);
        // 6) a turn about the sky while the owner stands about
        l.add(null, () -> server(p -> {
            p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 0.5, 0, -25);
            var r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            r.moveTo(X + 2.5, Y, Z + 2.5);
        }), 40);
        l.add(null, () -> server(p -> {
            var r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            r.flourishNow(); m[0] = r.flourishes;
            sample(300, 20, () -> { var rr = bot(sp(), CharacterType.RHALIX); ServerPlayer o = sp(); if (rr != null && o != null) log("flyabout t: up=" + f(rr.getY() - o.getY()) + " dist=" + f(rr.distanceTo(o)) + " flying=" + rr.selfFlyingForTest() + " onGround=" + rr.onGround()); });
        }), 70);
        l.add("botai_flyabout", () -> { ServerPlayer p = sp(); if (p != null) lookAt(mc, bot(p, CharacterType.RHALIX)); }, 3);
        l.add(null, () -> {}, 240);
        l.add(null, () -> server(p -> { var r = bot(p, CharacterType.RHALIX);
            if (r != null) log("flyabout: flights=" + (r.flourishes - (int) m[0]) + " peak above owner=" + f(r.flourishPeak) + " farthest=" + f(r.flourishFar) + " landed: onGround=" + r.onGround() + " noGravity=" + r.isNoGravity() + " flying=" + r.selfFlyingForTest()); }), 5);
        // 7) water: Kaeru dropped in the deep end with her owner close by on the shore (she used to just bob there)
        l.add(null, () -> server(p -> {
            var r = bot(p, CharacterType.RHALIX);
            if (r != null) PartyBots.action(p, r.getId(), PartyBots.ACT_DISMISS, 0);
            p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 4.0, 0, 30);
            var k = spawnBot(p, CharacterType.KAERU, X + 0.5, Y - 2.8, Z + 9.5);
            m[0] = -1;
            for (int t = 1; t <= 400; t++) { int tt = t; ServerScheduler.later(t, () -> { var kk = bot(sp(), CharacterType.KAERU); if (kk != null && m[0] < 0 && !kk.isInWater() && kk.onGround()) m[0] = tt; }); }
        }), 30);
        l.add("botai_water", () -> {}, 200);
        l.add(null, () -> server(p -> { var k = bot(p, CharacterType.KAERU);
            if (k != null) log("water: out on dry land after " + (m[0] < 0 ? "NEVER (400+)" : (int) m[0] + " ticks (" + f(m[0] / 20.0) + "s)") + " now inWater=" + k.isInWater() + " at y=" + f(k.getY() - Y) + " [" + k.lastEscape + "]"); }), 5);
        // 8) lava: Kaeru dropped in the lava pool
        l.add(null, () -> server(p -> {
            p.teleportTo(p.serverLevel(), X + 7.5, Y, Z + 4.0, 0, 35);
            var k = bot(p, CharacterType.KAERU);
            if (k == null) return;
            k.setHealth(k.getMaxHealth()); m[1] = k.getHealth();
            k.moveTo(X + 7.5, Y - 1.8, Z + 9.5);
            m[0] = -1;
            for (int t = 1; t <= 300; t++) { int tt = t; ServerScheduler.later(t, () -> { var kk = bot(sp(), CharacterType.KAERU); if (kk != null && m[0] < 0 && !kk.isInLava()) m[0] = tt; }); }
        }), 40);
        l.add("botai_lava", () -> {}, 140);
        l.add(null, () -> server(p -> { var k = bot(p, CharacterType.KAERU);
            if (k != null) log("lava: out of the lava after " + (m[0] < 0 ? "NEVER (300+)" : (int) m[0] + " ticks (" + f(m[0] / 20.0) + "s)") + " health " + f(m[1]) + " -> " + f(k.getHealth()) + " downed=" + k.downed() + " [" + k.lastEscape + "]");
            if (k != null) k.clearFire(); }), 5);
    }

    // ---------------------------------------------------------------- fighting

    private static ItemStack potion(net.minecraft.world.item.alchemy.Potion p) { return PotionUtils.setPotion(new ItemStack(Items.POTION), p); }

    private static int count(PartyBotEntity b, java.util.function.Predicate<ItemStack> f) {
        int n = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (f.test(b.inventory.getItem(i))) n += b.inventory.getItem(i).getCount();
        return n;
    }

    private static String id(ItemStack s) { return s.isEmpty() ? "empty" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath(); }

    private static void fighting(Minecraft mc, StepSink l) {
        final float[] v = new float[6];
        // 1) hurt, with a healing and a regeneration potion in her bag
        l.add(null, () -> server(p -> {
            var k = spawnBot(p, CharacterType.KAERU, X + 2.5, Y, Z + 0.5);
            if (k == null) return;
            k.inventory.addItem(potion(Potions.HEALING));
            k.inventory.addItem(potion(Potions.REGENERATION));
            k.setHealth(k.getMaxHealth() * 0.3F); v[0] = k.getHealth();
            log("potion: health set to " + f(v[0]) + "/" + f(k.getMaxHealth()));
        }), 50);
        l.add(null, () -> server(p -> { var k = bot(p, CharacterType.KAERU);
            if (k != null) log("potion: 2.5s later health=" + f(k.getHealth()) + " drank=[" + BotPotions.last(k) + "] drinks=" + k.drinks + " regen effect=" + k.hasEffect(MobEffects.REGENERATION)); }), 60);
        l.add("botai_potion", () -> {}, 3);
        l.add(null, () -> server(p -> { var k = bot(p, CharacterType.KAERU);
            if (k != null) log("potion: 5.5s health " + f(v[0]) + " -> " + f(k.getHealth()) + " drinks=" + k.drinks + " last=[" + BotPotions.last(k) + "] potions left=" + count(k, s -> s.is(Items.POTION)) + " bottles=" + count(k, s -> s.is(Items.GLASS_BOTTLE)) + " regen=" + k.hasEffect(MobEffects.REGENERATION)); }), 5);
        // 2) low on Karma in a fight, with a Karma potion
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU);
            Zombie zb = dummy(p, X + 4.5, Y, Z + 0.5, false, "karma");
            if (k == null || zb == null) return;
            k.setHealth(k.getMaxHealth());
            k.inventory.addItem(new ItemStack(com.xenowarriors.item.ModItems.KARMA_POTION.get()));
            k.karma = 10F; v[1] = k.karma; v[2] = k.drinks; v[4] = k.karma;
            k.setTarget(zb);
            sample(60, 1, () -> { var kk = bot(sp(), CharacterType.KAERU); if (kk != null) v[4] = Math.max(v[4], kk.karma); });
        }), 60);
        l.add(null, () -> server(p -> { var k = bot(p, CharacterType.KAERU);
            if (k != null) log("karma: " + f(v[1]) + " -> highest " + f(v[4]) + " -> now " + f(k.karma) + " in 3s (regen alone would be ~+5; it spends Karma on skills) drank=[" + BotPotions.last(k) + "] karma potions left=" + count(k, s -> s.is(com.xenowarriors.item.ModItems.KARMA_POTION.get())) + " drinks=" + (k.drinks - (int) v[2]));
            Zombie zb = tagged(p, "karma"); if (zb != null) zb.discard(); if (k != null) k.setTarget(null); }), 5);
        // 3) on fire, with a fire resistance potion
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU);
            if (k == null) return;
            k.inventory.addItem(potion(Potions.FIRE_RESISTANCE));
            k.setSecondsOnFire(8);
        }), 50);
        l.add(null, () -> server(p -> { var k = bot(p, CharacterType.KAERU);
            if (k != null) log("fire: fire resistance=" + k.hasEffect(MobEffects.FIRE_RESISTANCE) + " drank=[" + BotPotions.last(k) + "] onFire=" + k.isOnFire()); }), 5);
        // 4) a bow (and no arrows) against a dummy 14 blocks off; Rhalix (no bow) beside her never shoots
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU);
            var r = spawnBot(p, CharacterType.RHALIX, X - 2.5, Y, Z - 2.5);
            if (k == null || r == null) return;
            k.clearFire();
            k.inventory.clearContent();
            k.inventory.addItem(new ItemStack(Items.IRON_SWORD));
            k.inventory.addItem(new ItemStack(Items.BOW));
            k.moveTo(X - 4.5, Y, Z + 0.5, -90, 0);
            p.teleportTo(p.serverLevel(), X - 5.5, Y, Z + 2.5, -90, 5);
            Zombie zb = dummy(p, X + 12.5, Y, Z + 0.5, false, "bow");
            if (zb == null) return;
            k.setTarget(zb); v[0] = DUMMY_HP; v[1] = k.shots; v[2] = r.shots;
            Zombie z2 = dummy(p, X + 12.5, Y, Z - 1.5, false, "bow2");              // (Rhalix's own far target: no bow, so he flies over instead)
            if (z2 != null) r.setTarget(z2);
        }), 100);
        l.add("botai_bow", () -> {}, 60);
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU); var r = bot(p, CharacterType.RHALIX); Zombie zb = tagged(p, "bow");
            if (k == null || zb == null) return;
            int arrowsFlying = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, p.getBoundingBox().inflate(40)).size();
            log("bow: shots=" + (k.shots - (int) v[1]) + " dummy dealt=" + f(v[0] - zb.getHealth()) + " hand=" + id(k.getMainHandItem()) + " arrows in bag=" + count(k, s -> s.getItem() instanceof net.minecraft.world.item.ArrowItem)
                    + " arrow entities=" + arrowsFlying + " dist=" + f(k.distanceTo(zb)) + " | rhalix (no bow) shots=" + (r == null ? -1 : r.shots - (int) v[2]) + " air hits=" + (r == null ? -1 : r.airHits));
            Zombie z2 = tagged(p, "bow2"); if (z2 != null) z2.discard(); if (r != null) r.setTarget(null);
            k.inventory.addItem(new ItemStack(Items.SPECTRAL_ARROW, 5));            // arrows in the bag: that kind, none used up
            v[1] = k.shots; v[0] = zb.getHealth();
        }), 100);
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU); Zombie zb = tagged(p, "bow");
            if (k == null || zb == null) return;
            log("bow+arrows: shots=" + (k.shots - (int) v[1]) + " spectral arrows in bag=" + count(k, s -> s.is(Items.SPECTRAL_ARROW)) + " (gave 5) dummy glowing=" + zb.hasEffect(MobEffects.GLOWING) + " dealt=" + f(v[0] - zb.getHealth()));
            zb.discard(); k.setTarget(null);
            var arrows = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, p.getBoundingBox().inflate(40));
            int pickable = 0; for (var a : arrows) if (a.pickup == net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED) pickable++;
            log("bow: arrows lying about=" + arrows.size() + " that can be picked up=" + pickable);
            arrows.forEach(net.minecraft.world.entity.Entity::discard);
        }), 5);
        // 5) the owner's Soul Splitter soul right beside Rhalix (aggressive) and a zombie: Rhalix never goes for the soul
        l.add(null, () -> server(p -> {
            var r = bot(p, CharacterType.RHALIX);
            if (r == null) return;
            var soul = com.xenowarriors.soul.SoulEntities.SOUL.get().create(p.serverLevel());
            if (soul == null) { log("soul spawn FAILED"); return; }
            soul.setCharacter(CharacterType.KAERU); soul.setOwner(p.getUUID());
            soul.moveTo(r.getX() + 1.5, r.getY(), r.getZ());
            soul.addTag("xw_botai");
            p.serverLevel().addFreshEntity(soul);
            v[3] = soul.getHealth();
            r.setTarget(soul);
            log("soul: after setTarget(soul) rhalix target=" + (r.getTarget() == null ? "none" : r.getTarget().getType().toShortString()) + " friendly=" + PartyBots.friendly(r, soul));
            boolean hit = r.doHurtTarget(soul);
            log("soul: rhalix hitting it directly landed=" + hit + " soul health " + f(v[3]) + " -> " + f(soul.getHealth()));
            r.setMode(PartyBotEntity.AGGRESSIVE);
            Zombie zb = dummy(p, r.getX() + 3.5, r.getY(), r.getZ() + 1, false, "soulz");
            final int[] onSoul = {0};
            sample(120, 5, () -> { var rr = bot(sp(), CharacterType.RHALIX); if (rr != null && rr.getTarget() instanceof com.xenowarriors.soul.SoulEntity) onSoul[0]++; });
            ServerScheduler.later(121, () -> log("soul: 6s with a zombie about in aggressive mode, ticks targeting the soul=" + onSoul[0] + " soul health=" + f(soul.getHealth()) + " alive=" + soul.isAlive()));
        }), 140);
        l.add(null, () -> server(p -> { clearDummies(p); var r = bot(p, CharacterType.RHALIX); if (r != null) { r.setMode(PartyBotEntity.NEUTRAL); r.setTarget(null); } }), 20);
        // 6) the owner goes down with a zombie on them: it goes for whoever comes to revive; back up = invincible + a shockwave
        final String[] tgt = {""};
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.teleportTo(p.serverLevel(), X + 0.5, Y, Z + 0.5, 0, 20);
            p.setHealth(p.getMaxHealth());
            PartyBotEntity k = bot(p, CharacterType.KAERU), r = bot(p, CharacterType.RHALIX);
            if (k != null) { k.moveTo(X - 4.5, Y, Z - 3.5); k.setHealth(k.getMaxHealth()); }
            if (r != null) { r.moveTo(X + 5.5, Y, Z - 4.5); r.setHealth(r.getMaxHealth()); }
            Zombie zb = dummy(p, X + 0.5, Y, Z + 3.5, true, "downz", 60F);           // (an ordinary zombie: not a boss, so the full stun)
            if (zb == null) return;
            zb.setTarget(p);
            p.hurt(p.damageSources().generic(), 1000F);
            log("downed: player downed=" + com.xenowarriors.party.Duels.isDowned(p) + " zombie target right after=" + name(zb.getTarget()));
            sample(80, 10, () -> { Zombie z2 = tagged(sp(), "downz"); if (z2 != null) tgt[0] += name(z2.getTarget()) + " "; });
            watchRevive();
        }), 30);
        l.add("botai_downed", () -> {}, 60);
        l.add(null, () -> server(p -> log("downed: zombie targets while down (every 0.5s) = [" + tgt[0].trim() + "] player still downed=" + com.xenowarriors.party.Duels.isDowned(p))), 1);
        l.add("botai_revived", () -> {}, 60);
        l.add(null, () -> {}, 200);
        // 7) a bot goes down with a zombie on it: the zombie goes for whoever's reviving it
        final String[] bt = {""};
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            clearDummies(p);
            PartyBotEntity k = bot(p, CharacterType.KAERU), r = bot(p, CharacterType.RHALIX);
            if (k == null || r == null) return;
            p.teleportTo(p.serverLevel(), X - 5.5, Y, Z + 5.5, 135, 20);
            r.moveTo(X + 0.5, Y, Z + 0.5); k.moveTo(X + 4.5, Y, Z - 4.5);
            var kh = k.getAttribute(Attributes.MAX_HEALTH); if (kh != null) kh.setBaseValue(200); k.setHealth(k.getMaxHealth());
            Zombie zb = dummy(p, X + 0.5, Y, Z + 3.5, true, "botdownz", 60F);
            if (zb == null) return;
            zb.setTarget(r);
            r.die(p.damageSources().generic());
            log("bot downed: rhalix downed=" + r.downed() + " zombie target right after=" + name(zb.getTarget()));
            sample(100, 10, () -> { Zombie z2 = tagged(sp(), "botdownz"); var rr = bot(sp(), CharacterType.RHALIX); var kk = bot(sp(), CharacterType.KAERU); if (z2 != null) bt[0] += name(z2.getTarget()) + (rr != null && !rr.downed() ? "(up)" : "") + (kk != null && rr != null ? "[k " + f(kk.distanceTo(rr)) + "m hp" + f(kk.getHealth()) + "]" : "") + " "; });
        }), 110);
        l.add("botai_botrevive", () -> server(p -> { var r = bot(p, CharacterType.RHALIX);
            log("bot downed: zombie targets (every 0.5s) = [" + bt[0].trim() + "] rhalix downed now=" + (r == null ? "?" : r.downed()) + " invincible ticks=" + (r == null ? -1 : ReviveAftermath.invulnerableTicks(r)) + " shockwave " + ReviveAftermath.lastBurst); }), 5);
    }


    private static final double[] zd = new double[3];

    /** From the moment the player goes down: when they're back up, what the shockwave did, the stun, and the invincibility. */
    private static void watchRevive() {
        zd[0] = 0;
            for (int t = 1; t <= 400; t++) { int tt = t; ServerScheduler.later(t, () -> {
                ServerPlayer o = sp(); Zombie z2 = tagged(o, "downz");
                if (o == null || zd[0] != 0 || com.xenowarriors.party.Duels.isDowned(o)) return;
                zd[0] = tt; zd[1] = z2 == null ? -1 : z2.distanceTo(o);
                log("revive: player up after +" + tt + " ticks, invincible ticks=" + ReviveAftermath.invulnerableTicks(o) + " glowing=" + o.hasEffect(MobEffects.GLOWING) + " shockwave " + ReviveAftermath.lastBurst
                        + " zombie stunned=" + (z2 != null && ReviveAftermath.stunned(z2)));
                ServerScheduler.later(10, () -> { Zombie z3 = tagged(sp(), "downz"); if (z3 != null) { zd[2] = z3.distanceTo(sp()); log("revive: zombie pushed from " + f(zd[1]) + " to " + f(zd[2]) + " blocks away in 0.5s"); } });
                ServerScheduler.later(20, () -> { Zombie z3 = tagged(sp(), "downz"); if (z3 != null) zd[1] = z3.distanceTo(sp()); });
                ServerScheduler.later(80, () -> { Zombie z3 = tagged(sp(), "downz"); ServerPlayer o2 = sp();
                    if (z3 != null && o2 != null) {
                        float before = o2.getHealth(); o2.invulnerableTime = 0;
                        boolean took = o2.hurt(o2.damageSources().mobAttack(z3), 6F);
                        log("revive +4s: zombie stunned=" + ReviveAftermath.stunned(z3) + " moved " + f(Math.abs(z3.distanceTo(o2) - zd[1])) + " blocks since +1s, a 6-damage hit on the player landed=" + took + " health " + f(before) + " -> " + f(o2.getHealth()) + " invincible ticks left=" + ReviveAftermath.invulnerableTicks(o2));
                    } });
                ServerScheduler.later(130, () -> { Zombie z3 = tagged(sp(), "downz"); if (z3 != null) log("revive +6.5s: zombie stunned=" + ReviveAftermath.stunned(z3) + " target=" + name(z3.getTarget())); });
                ServerScheduler.later(215, () -> { Zombie z3 = tagged(sp(), "downz"); ServerPlayer o2 = sp();
                    if (z3 != null && o2 != null) { float before = o2.getHealth(); o2.invulnerableTime = 0; boolean took = o2.hurt(o2.damageSources().mobAttack(z3), 2F);
                        log("revive +10.75s: invincible ticks left=" + ReviveAftermath.invulnerableTicks(o2) + " a 2-damage hit landed=" + took + " health " + f(before) + " -> " + f(o2.getHealth())); } });
            }); }
    }

    private static String name(LivingEntity e) {
        if (e == null) return "none";
        if (e instanceof PartyBotEntity b) return b.character().getId() + "-bot";
        if (e instanceof net.minecraft.world.entity.player.Player) return "player";
        return e.getType().toShortString();
    }

    // ---------------------------------------------------------------- a TACZ gun

    private static void gun(Minecraft mc, StepSink l) {
        final float[] v = new float[3];
        l.add(null, () -> server(p -> {
            log("tacz available=" + com.xenowarriors.compat.TaczGuns.available());
            var k = spawnBot(p, CharacterType.KAERU, X - 4.5, Y, Z + 0.5);
            if (k == null) return;
            ItemStack g = com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 0);         // (an empty magazine, and no ammo in the bag)
            log("gun: made " + id(g) + " isGun=" + com.xenowarriors.compat.TaczGuns.isGun(g));
            k.inventory.addItem(new ItemStack(Items.IRON_SWORD));
            k.inventory.addItem(g);
            p.teleportTo(p.serverLevel(), X - 5.5, Y, Z + 3.5, -90, 5);
            Zombie zb = dummy(p, X + 9.5, Y, Z + 0.5, false, "gunz");
            if (zb == null) return;
            k.setTarget(zb); v[0] = DUMMY_HP; v[1] = k.shots;
        }), 100);
        l.add("botai_gun", () -> {}, 60);
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU); Zombie zb = tagged(p, "gunz");
            if (k == null || zb == null) return;
            log("gun: shots=" + (k.shots - (int) v[1]) + " dummy dealt=" + f(v[0] - zb.getHealth()) + " hand=" + id(k.getMainHandItem()) + " in 8s");
            zb.discard(); k.setTarget(null);
        }), 5);
    }
}

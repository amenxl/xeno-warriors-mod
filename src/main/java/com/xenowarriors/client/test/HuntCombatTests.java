package com.xenowarriors.client.test;

import com.xenowarriors.bot.BotTeamUlt;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.entity.TeamUltEntity;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.FormGunItem;
import com.xenowarriors.forms.WeaponForms;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.party.TeamUltimate;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.statboost.StatBoosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Bug-hunt checks for combat / skills / stats / items (focus "huntcombat"; "dmzhuntcombat" with DragonMineZ). Only
 * uses things that existed before the fixes, so the same steps show the bugs on the old code. Logged as
 * "XW-TEST huntcombat ...".
 * <ol>
 *   <li>Team ultimate cut short by leaving the dimension: the lent flight must not stay (it did: survival flight for good).</li>
 *   <li>Summoned gun: put away + summon again must not repair it.</li>
 *   <li>Summoned gun: no shots from the death screen.</li>
 *   <li>Shovel on dirt you just placed: no Stat Boost rolls; Mining XP: nothing for a block you just placed.</li>
 *   <li>Hekublade ride: no second ride before landing.</li>
 *   <li>Gun XP: a hit that does no damage trains nothing.</li>
 *   <li>Fox King: speed / attack boosts come back if the player object lost them (End return).</li>
 *   <li>Karma Sense auras still draw (render change), with a still.</li>
 * </ol>
 */
public final class HuntCombatTests {
    private HuntCombatTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^(dmz|ef)", "").startsWith("huntcombat"); }

    private static void log(String s) { UiTest.log("huntcombat " + s); }

    private static Vec3 origin;
    private static PartyBotEntity bot;
    private static final double[] D = new double[16];
    private static Husk husk;

    private static void server(java.util.function.Consumer<ServerPlayer> f) { UiTest.server(f); }

    private static void clearArea(ServerPlayer p) {
        for (var e : p.serverLevel().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(48))) e.discard();
        for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(48))) e.discard();
        for (var b : new java.util.ArrayList<>(PartyBots.bots(p))) b.discard();
        TeamUltimate.clearForTest();
    }

    private static void stats(ServerPlayer p, int a, int b, int c) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.statLevel[0] = a; d.statLevel[1] = b; d.statLevel[2] = c; });
        com.xenowarriors.stats.Stats.reapply(p);
    }

    private static Husk husk(ServerPlayer p, Vec3 at, float hp) {
        Husk h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(at.x, at.y, at.z, 180, 0);
        h.setPersistenceRequired();
        h.setNoAi(true);
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        h.setHealth(hp);
        p.serverLevel().addFreshEntity(h);
        return h;
    }

    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    private static String pf(boolean ok) { return ok ? "PASS" : "FAIL"; }

    public static void build(net.minecraft.client.Minecraft mc, List<Step> l, String focus) {
        // ---- arena: a stone floor up in the air, open sky, survival
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.RHALIX);
            server(p -> { p.setGameMode(GameType.SURVIVAL); p.serverLevel().setDayTime(6000); clearArea(p);
                p.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, p.server);
                ServerLevel l0 = p.serverLevel();
                BlockPos base = BlockPos.containing(p.getX(), 150, p.getZ());
                for (int x = -16; x <= 16; x++) for (int z = -10; z <= 22; z++) {
                    l0.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                    for (int y = 0; y < 14; y++) l0.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                origin = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
                p.setHealth(p.getMaxHealth()); p.fallDistance = 0; }); }, 40));

        // ---- 1. Blazing Judgment, the player leaves for the Nether in the middle: flight must not stay
        l.add(new Step(null, () -> server(p -> {
            stats(p, 10, 10, 10);
            p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities();
            for (int i = 0; i < 4; i++) husk(p, origin.add(Math.cos(i) * 3, 0, 10 + Math.sin(i) * 3), 200);
            bot = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (bot != null) bot.moveTo(origin.x + 3, origin.y, origin.z, 0, 0);
        }), 30));
        l.add(new Step(null, () -> server(p -> {
            SkillManager.resetCooldowns(p); Karma.add(p, 200);
            if (bot != null) BotTeamUlt.readyForTest(bot);
            p.setShiftKeyDown(true);
            boolean took = TeamUltimate.onUltimateKey(p);
            p.setShiftKeyDown(false);
            int n = p.serverLevel().getEntitiesOfClass(TeamUltEntity.class, p.getBoundingBox().inflate(40)).size();
            log("team ult started: handled=" + took + " entities=" + n + " -> " + pf(took && n == 1));
        }), 20));
        l.add(new Step(null, () -> server(p -> {
            D[0] = p.getAbilities().mayfly ? 1 : 0;
            ServerLevel nether = p.server.getLevel(Level.NETHER);
            log("mid-cutscene (tick ~20): mayfly=" + p.getAbilities().mayfly + " -> going to the Nether");
            if (nether != null) {
                BlockPos np = BlockPos.containing(0, 100, 0);
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    nether.setBlock(np.offset(x, -1, z), Blocks.OBSIDIAN.defaultBlockState(), 2);
                    for (int y = 0; y < 3; y++) nether.setBlock(np.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                p.teleportTo(nether, 0.5, 100, 0.5, 0F, 0F);
            }
        }), 160));
        l.add(new Step(null, () -> server(p -> {
            boolean fly = p.getAbilities().mayfly;
            boolean legit = com.xenowarriors.character.RhalixFlight.canFly(p) || com.xenowarriors.mikiko.WindFlight.canFly(p);
            log("after the cutscene ended without him (in " + p.level().dimension().location() + "): mayfly=" + fly + " flying=" + p.getAbilities().flying
                    + " (can fly anyway: " + legit + ", had it mid-cutscene: " + (D[0] > 0) + ") -> " + pf(D[0] > 0 && (!fly || legit)));
            p.teleportTo(p.server.overworld(), origin.x, origin.y, origin.z, 0F, 0F);
            p.fallDistance = 0;
        }), 40));
        l.add(new Step(null, () -> server(p -> {
            log("back in the overworld: mayfly=" + p.getAbilities().mayfly + " -> " + pf(!p.getAbilities().mayfly
                    || com.xenowarriors.character.RhalixFlight.canFly(p)));
            clearArea(p); p.setHealth(p.getMaxHealth());
        }), 10));

        // ---- 2. summoned gun: put away + summon again keeps the wear
        l.add(new Step(null, () -> { UiTest.character(CharacterType.MIKIKO); server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeLevel = 60; d.bladeXp = 0; });
            com.xenowarriors.compat.GunLevel.setLevel(p, 50);
            stats(p, 0, 0, 0);
            p.getInventory().clearContent();
            p.getPersistentData().remove(WeaponForms.BROKEN);
            p.getPersistentData().remove("xw_gun_wear");
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
        }); }, 20));
        l.add(new Step(null, () -> server(p -> {
            boolean ok = WeaponForms.summon(p, Form.PISTOL, false);
            ItemStack g = p.getMainHandItem();
            D[1] = FormGunItem.durMax(g);
            g.getOrCreateTag().putInt(FormGunItem.DUR, 7);                        // (as if it had fired most of its shots)
            log("pistol summoned=" + ok + " durability " + FormGunItem.dur(g) + "/" + FormGunItem.durMax(g) + " (worn down to 7)");
            WeaponForms.tap(p);                                                   // put away (Mikiko: the Hekublade toggle)
            WeaponForms.removeForms(p.getInventory());
            boolean again = WeaponForms.summon(p, Form.PISTOL, false);
            ItemStack g2 = p.getMainHandItem();
            int d = g2.getItem() instanceof FormGunItem ? FormGunItem.dur(g2) : -1;
            log("put away + summoned again (" + again + "): durability " + d + "/" + FormGunItem.durMax(g2) + " -> " + pf(again && d == 7));
            // swapping to another gun form and back: still worn
            WeaponForms.summon(p, Form.SHOTGUN, false);
            WeaponForms.summon(p, Form.PISTOL, false);
            int d2 = FormGunItem.dur(p.getMainHandItem());
            log("pistol -> shotgun -> pistol: durability " + d2 + " -> " + pf(d2 == 7));
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            // rested as long as the broken-gun wait: fresh again
            var wear = p.getPersistentData().getCompound("xw_gun_wear").getCompound(Form.PISTOL.name());
            if (!wear.isEmpty()) wear.putLong("t", p.serverLevel().getGameTime() - WeaponForms.GUN_BREAK_COOLDOWN - 1);
            WeaponForms.removeForms(p.getInventory());
            WeaponForms.summon(p, Form.PISTOL, false);
            int d = FormGunItem.dur(p.getMainHandItem());
            log("after resting " + WeaponForms.GUN_BREAK_COOLDOWN / 20 + "s: durability " + d + "/" + (int) D[1] + " -> " + pf(d == (int) D[1]));
            // broken: the wait, then a fresh gun (no leftover wear)
            p.getMainHandItem().getOrCreateTag().putInt(FormGunItem.DUR, 1);
            p.getCooldowns().removeCooldown(p.getMainHandItem().getItem());
            int before = WeaponForms.SHOTS;
            ((FormGunItem) p.getMainHandItem().getItem()).fire(p, p.getMainHandItem());
            boolean broke = !(p.getMainHandItem().getItem() instanceof FormGunItem) && WeaponForms.gunCooldownLeft(p) > 0;
            p.getPersistentData().remove(WeaponForms.BROKEN);
            WeaponForms.summon(p, Form.PISTOL, false);
            int d3 = FormGunItem.dur(p.getMainHandItem());
            log("last shot broke it (" + broke + ", shots " + (WeaponForms.SHOTS - before) + "), after the wait: durability " + d3 + " -> " + pf(broke && d3 == (int) D[1]));
        }), 10));

        // ---- 3. no shots from the death screen
        l.add(new Step(null, () -> server(p -> {
            husk = husk(p, origin.add(0, 0, 6), 200);
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
            p.setYRot(0F); p.setXRot(8F);
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            ItemStack g = p.getMainHandItem();
            float hp = husk.getHealth();
            p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
            int before = WeaponForms.SHOTS, hits = 0;
            if (g.getItem() instanceof FormGunItem gun) for (int i = 0; i < 3; i++) {
                hits += gun.fire(p, p.getMainHandItem(), true, 0.2F, 8F, 0F);
            }
            log("dead (alive=" + p.isAlive() + ", gun in hand " + (p.getMainHandItem().getItem() instanceof FormGunItem) + "): 3 FIRE packets -> shots " + (WeaponForms.SHOTS - before)
                    + ", hits " + hits + ", husk hp " + f(hp) + " -> " + f(husk.getHealth()) + " -> " + pf(WeaponForms.SHOTS == before && husk.getHealth() >= hp));
        }), 20));
        l.add(new Step(null, () -> { if (mc.player != null && !mc.player.isAlive()) mc.player.respawn(); mc.setScreen(null); }, 40));
        l.add(new Step(null, () -> server(p -> {
            log("respawned: alive=" + p.isAlive());
            clearArea(p);
            p.getInventory().clearContent();
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
        }), 20));

        // ---- 4. dirt / Mining farm: place + dig on one spot
        l.add(new Step(null, () -> server(p -> {
            StatBoosts.testRoll = 0F;                                             // (every roll would drop)
            StatBoosts.testDirtDrops = 0;
            BlockPos spot = BlockPos.containing(origin.x + 2, origin.y, origin.z + 2);
            ServerLevel lv = p.serverLevel();
            int placedDrops = 0;
            float mxBefore = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningXp + d.miningLevel * 1000F).orElse(0F);
            for (int i = 0; i < 5; i++) {
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));
                p.gameMode.useItemOn(p, lv, p.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(spot.below()).add(0, 0.5, 0), Direction.UP, spot.below(), false));
                boolean placed = lv.getBlockState(spot).is(Blocks.DIRT);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
                int b = StatBoosts.testDirtDrops;
                p.gameMode.destroyBlock(spot);
                placedDrops += StatBoosts.testDirtDrops - b;
                if (i == 0) log("placed dirt: " + placed + ", dug: " + lv.getBlockState(spot).isAir());
            }
            float mxAfter = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningXp + d.miningLevel * 1000F).orElse(0F);
            log("place + dig the same spot x5 (shovel, every roll a hit): boosts " + placedDrops + " -> " + pf(placedDrops == 0));
            // dirt that was already there (not placed by them) still rolls
            BlockPos nat = spot.east(3);
            lv.setBlock(nat, Blocks.DIRT.defaultBlockState(), 3);
            int b = StatBoosts.testDirtDrops;
            p.gameMode.destroyBlock(nat);
            log("dig dirt that was there: boosts " + (StatBoosts.testDirtDrops - b) + " -> " + pf(StatBoosts.testDirtDrops - b == 1));
            // Mining XP from placing + breaking stone on one spot
            float m0 = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningXp + d.miningLevel * 1000F).orElse(0F);
            for (int i = 0; i < 5; i++) {
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
                p.gameMode.useItemOn(p, lv, p.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(spot.below()).add(0, 0.5, 0), Direction.UP, spot.below(), false));
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                p.gameMode.destroyBlock(spot);
            }
            float m1 = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningXp + d.miningLevel * 1000F).orElse(0F);
            lv.setBlock(nat, Blocks.STONE.defaultBlockState(), 3);
            p.gameMode.destroyBlock(nat);
            float m2 = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.miningXp + d.miningLevel * 1000F).orElse(0F);
            log("mining XP: dirt loop " + f(mxAfter - mxBefore) + ", stone place + break x5 " + f(m1 - m0) + " -> " + pf(m1 - m0 < 0.01)
                    + "; natural stone " + f(m2 - m1) + " -> " + pf(m2 - m1 > 0.1));
            StatBoosts.testRoll = null;
            for (var e : lv.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(16))) e.discard();
            p.getInventory().clearContent();
        }), 10));

        // ---- 5. Hekublade ride: no chaining rides in the air
        l.add(new Step(null, () -> server(p -> {
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.bladeLevel = 40);
            stats(p, 3, 3, 3);
            p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities();
            p.connection.teleport(origin.x, origin.y + 6, origin.z, 0F, 0F);
        }), 4));
        l.add(new Step(null, () -> server(p -> {
            boolean r1 = com.xenowarriors.mikiko.BladeRideJump.ride(p);
            log("ride 1 (in the air): " + r1 + " vehicle=" + (p.getVehicle() != null));
            p.stopRiding();
            p.connection.teleport(origin.x, origin.y + 8, origin.z, 0F, 0F);
        }), 25));
        l.add(new Step(null, () -> server(p -> {
            boolean r2 = com.xenowarriors.mikiko.BladeRideJump.ride(p);
            log("ride 2 (still in the air, not landed since): " + r2 + " -> " + pf(!r2));
            p.stopRiding();
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
        }), 30));
        l.add(new Step(null, () -> server(p -> {
            log("landed: onGround=" + p.onGround());
            p.connection.teleport(origin.x, origin.y + 6, origin.z, 0F, 0F);
        }), 4));
        l.add(new Step(null, () -> server(p -> {
            boolean r3 = com.xenowarriors.mikiko.BladeRideJump.ride(p);
            log("ride 3 (after landing): " + r3 + " -> " + pf(r3));
            p.stopRiding();
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
            p.fallDistance = 0;
        }), 30));

        // ---- 6. gun XP: only for damage really done
        l.add(new Step(null, () -> server(p -> {
            husk = husk(p, origin.add(0, 0, 5), 200);
            husk.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 2000, 4));
            p.getPersistentData().remove(WeaponForms.BROKEN);
            p.getPersistentData().remove("xw_gun_wear");
            WeaponForms.summon(p, Form.PISTOL, false);
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
            p.setYRot(0F); p.setXRot(10F);
        }), 10));
        l.add(new Step(null, () -> server(p -> {
            float x0 = com.xenowarriors.compat.GunLevel.xp(p);
            int lv0 = com.xenowarriors.compat.GunLevel.level(p);
            int hits = 0;
            ItemStack g = p.getMainHandItem();
            for (int i = 0; i < 5; i++) { p.getCooldowns().removeCooldown(g.getItem()); hits += ((FormGunItem) g.getItem()).fire(p, g); }
            float x1 = com.xenowarriors.compat.GunLevel.xp(p);
            log("5 shots at a Resistance V husk: hits " + hits + ", hp " + f(husk.getHealth()) + "/200, gun xp " + f(x0) + " -> " + f(x1)
                    + " (level " + lv0 + " -> " + com.xenowarriors.compat.GunLevel.level(p) + ") -> " + pf(hits > 0 && Math.abs(x1 - x0) < 0.01 && lv0 == com.xenowarriors.compat.GunLevel.level(p)));
            husk.removeAllEffects();
            float x2 = com.xenowarriors.compat.GunLevel.xp(p);
            for (int i = 0; i < 3; i++) { p.getCooldowns().removeCooldown(g.getItem()); ((FormGunItem) g.getItem()).fire(p, g); }
            log("3 shots at a normal husk: gun xp " + f(x2) + " -> " + f(com.xenowarriors.compat.GunLevel.xp(p)) + " -> "
                    + pf(com.xenowarriors.compat.GunLevel.xp(p) > x2 || com.xenowarriors.compat.GunLevel.level(p) > lv0));
            WeaponForms.removeForms(p.getInventory());
        }), 10));

        // ---- 7. Fox King: the boosts come back if the player lost them (as after the End)
        l.add(new Step(null, () -> server(p -> {
            com.xenowarriors.mikiko.FoxKing.resetCooldown(p);
            Karma.add(p, 200);
            com.xenowarriors.mikiko.FoxKing.toggle(p);
            var spd = p.getAttribute(Attributes.MOVEMENT_SPEED);
            D[2] = spd.getValue();
            for (var m : new java.util.ArrayList<>(spd.getModifiers())) if ("Fox King".equals(m.getName())) spd.removeModifier(m.getId());
            var atk = p.getAttribute(Attributes.ATTACK_DAMAGE);
            for (var m : new java.util.ArrayList<>(atk.getModifiers())) if ("Fox King".equals(m.getName())) atk.removeModifier(m.getId());
            log("fox king on: active=" + com.xenowarriors.mikiko.FoxKing.active(p) + " speed " + f(D[2]) + ", modifiers stripped -> " + f(spd.getValue()));
        }), 30));
        l.add(new Step(null, () -> server(p -> {
            double s = p.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
            log("a second later: speed " + f(s) + " (in form " + f(D[2]) + ") -> " + pf(Math.abs(s - D[2]) < 0.001));
            com.xenowarriors.mikiko.FoxKing.stop(p, true);
            com.xenowarriors.mikiko.FoxKing.resetCooldown(p);
            clearArea(p);
        }), 10));

        // ---- 8. Karma Sense auras (moved to camera-relative drawing): still drawn
        l.add(new Step(null, () -> server(p -> {
            for (int i = 0; i < 3; i++) husk(p, origin.add(-3 + i * 3, 0, 7), 40);
            p.connection.teleport(origin.x, origin.y, origin.z, 0F, 0F);
            p.setYRot(0F); p.setXRot(10F);
            Karma.add(p, 200);
        }), 10));
        l.add(new Step(null, () -> {
            mc.player.setYRot(0F); mc.player.setXRot(10F); mc.player.yRotO = 0F; mc.player.xRotO = 10F;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            server(p -> com.xenowarriors.karma.KarmaSensing.use(p));
        }, 30));
        l.add(new Step("huntcombat_auras", () -> {}, 4));
        l.add(new Step(null, () -> {
            log("karma auras drawn " + com.xenowarriors.client.KarmaSense.aurasDrawn + " -> " + pf(com.xenowarriors.client.KarmaSense.aurasDrawn >= 3));
            server(p -> { com.xenowarriors.karma.KarmaSensing.release(p); clearArea(p); p.setGameMode(GameType.CREATIVE); });
            log("done");
        }, 10));
    }
}

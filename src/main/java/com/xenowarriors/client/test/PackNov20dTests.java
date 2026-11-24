package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.vehicle.VehicleEntity;
import com.xenowarriors.vehicle.VehicleKind;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * (focus "packnov20d") The middle click on one of your own characters; the big ship (bots board it, a bot at the
 * helm sails it); ghost ships fire their cannons and leave treasure when sunk; bots make boats (Crafting 20) and
 * take another boat when yours is full; bots make torches in the dark (fire lights sticks); side-mission foes come
 * back when they vanish; the new Sky Patrol hideout.
 */
final class PackNov20dTests {
    private PackNov20dTests() {}

    /** The sea: open water (4 deep) off to the side of the arena, 56 x 28, a stone shore at the south end. */
    static final int SEA_X = 300, SEA_W = 28, SEA_L = 56;
    static BlockPos sea;

    private static void buildSea(ServerPlayer p) {
        var l = p.serverLevel();
        sea = BlockPos.containing(PackTests.HOME[0] + SEA_X, 120, PackTests.HOME[2]);
        for (int x = -SEA_W / 2 - 1; x <= SEA_W / 2 + 1; x++) for (int z = -SEA_L / 2 - 1; z <= SEA_L / 2 + 8; z++) {
            boolean shore = z > SEA_L / 2;
            boolean rim = Math.abs(x) > SEA_W / 2 || z < -SEA_L / 2;
            l.setBlock(sea.offset(x, -5, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = -4; y <= 0; y++) l.setBlock(sea.offset(x, y, z), shore || rim ? (y == 0 && shore ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState())
                    : y == 0 ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
            for (int y = 1; y <= 30; y++) l.setBlock(sea.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void you(ServerPlayer p, int slot, String name, com.xenowarriors.character.Race race, com.xenowarriors.ka.Element el) {
        var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
        var s = new com.xenowarriors.you.YouSlot(); s.name = name; s.race = race; s.element = el;
        d.youSlots[slot] = s;
        com.xenowarriors.character.CharacterManager.setYou(p, slot);
        com.xenowarriors.you.YouRaces.apply(p);
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); }); }, 30));
        // ---- the hunger bar drains steadily: fuller stomach heals faster for the same food a second; Speed levels don't make running cost more
        float[] hx = new float[3];
        l.add(new Step(null, () -> PackTests.server(p -> {
            float lo = 99, hi = 0;
            for (int bars = 1; bars <= 11; bars++) { int ev = com.xenowarriors.character.BigHunger.regenInterval(bars); float perSec = 4F * ev / 40F * 20F / ev; lo = Math.min(lo, perSec); hi = Math.max(hi, perSec); }
            hx[2] = hi - lo;
            var c = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            hx[1] = c.statLevel[0]; c.statLevel[0] = 50;
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getFoodData().setExhaustion(0F);
            p.setSprinting(true);
            hx[0] = com.xenowarriors.character.BigHunger.slowed;
            p.causeFoodExhaustion(1F);
        }), 3));
        l.add(new Step(null, () -> PackTests.server(p -> {
            float ex = p.getFoodData().getExhaustionLevel();
            PackTests.check("hunger: the same food a second however full you are, and running at Speed 50 (+75%) costs 1/1.75 a block",
                    hx[2] < 0.001F && Math.abs(ex - 1F / 1.75F) < 0.05F && com.xenowarriors.character.BigHunger.slowed > hx[0], "spread " + hx[2] + " exhaustion " + PackTests.f(ex));
            var c = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            c.statLevel[0] = (int) hx[1];
            p.setSprinting(false);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 5));
        // ---- Kaeru's idle: standing still she stretches (arms up, side to side, arms across)
        l.add(new Step(null, () -> { PackWomTests.battle(false); com.xenowarriors.client.UiTest.character(CharacterType.KAERU); }, 40));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 135));
        l.add(new Step("kaeru_stretch_up", () -> {}, 3));
        l.add(new Step(null, () -> {}, 55));
        l.add(new Step("kaeru_stretch_lean", () -> {}, 3));
        l.add(new Step(null, () -> {}, 130));
        l.add(new Step("kaeru_stretch_across", () -> {
            var p = mc.player;
            PackTests.check("idle: Kaeru stretches when she stands still", p != null && com.xenowarriors.client.KaeruStretch.time(p) > 150 && "kaeru_stretch".equals(com.xenowarriors.client.Idles.holder(p)),
                    "time " + (p == null ? -1 : com.xenowarriors.client.KaeruStretch.time(p)) + " holder " + (p == null ? "-" : com.xenowarriors.client.Idles.holder(p)));
        }, 3));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO); }, 30));
        // ---- Pod Recall: going down, sent straight to a free healing pod - 40 blocks off needs rank 2 (48 blocks)
        Object[] pr = new Object[3];
        l.add(new Step(null, () -> PackTests.server(p -> {
            var lv = p.serverLevel();
            BlockPos pod = p.blockPosition().offset(40, 0, 0);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { lv.setBlock(pod.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3); for (int y = 0; y <= 2; y++) lv.setBlock(pod.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3); }
            var st = com.xenowarriors.sky.ModBlocks.HEALING_POD.get().defaultBlockState().setValue(com.xenowarriors.gov.HealingPodBlock.FACING, net.minecraft.core.Direction.NORTH);
            lv.setBlock(pod, st, 3);
            lv.setBlock(pod.above(), st.setValue(com.xenowarriors.gov.HealingPodBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            pr[0] = pod; pr[1] = p.gameMode.getGameModeForPlayer();
            com.xenowarriors.gov.HealingPods.wake(p);
            int base = com.xenowarriors.apl.Apl.podRecallRange(p);
            com.xenowarriors.apl.Apl.setForTest(p, com.xenowarriors.apl.Apl.Ability.POD_RECALL, 2);
            pr[2] = base + " -> " + com.xenowarriors.apl.Apl.podRecallRange(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setHealth(1F);
            p.invulnerableTime = 0;
            com.xenowarriors.gov.HealingPods.recallWhy = "never called";
            boolean hit = p.hurt(p.damageSources().generic(), 1_000_000F);
            pr[2] = pr[2] + " | hit " + hit + " hp " + p.getHealth() + "/" + p.getMaxHealth() + " dead " + p.isDeadOrDying() + " downed " + com.xenowarriors.party.Duels.isDowned(p)
                    + " why " + com.xenowarriors.gov.HealingPods.recallWhy + " mode " + p.gameMode.getGameModeForPlayer();
            if (!com.xenowarriors.gov.HealingPods.inPod(p) && !com.xenowarriors.party.Duels.isDowned(p) && p.isAlive()) {
                p.setHealth(0F);                                                // (the pack's own damage handling may keep the hit off: go down the way dying does)
                boolean saved = net.minecraftforge.common.ForgeHooks.onLivingDeath(p, p.damageSources().generic());
                if (p.getHealth() <= 0F) p.setHealth(1F);
                pr[2] = pr[2] + " | death event cancelled " + saved + " " + com.xenowarriors.gov.HealingPods.recallWhy;
            }
        }), 20));
        l.add(new Step(null, () -> PackTests.server(p -> {
            BlockPos pod = (BlockPos) pr[0];
            boolean in = com.xenowarriors.gov.HealingPods.inPod(p);
            double d = Math.sqrt(p.blockPosition().distSqr(pod));
            PackTests.check("pod recall: going down 40 blocks from a free pod with Pod Recall rank 2, you're sent into it", in && p.isAlive() && d < 2 && com.xenowarriors.gov.HealingPods.recalled > 0,
                    "range " + pr[2] + " in pod " + in + " alive " + p.isAlive() + " dist " + PackTests.f((float) d) + " recalled " + com.xenowarriors.gov.HealingPods.recalled);
            com.xenowarriors.gov.HealingPods.wake(p);
            com.xenowarriors.apl.Apl.setForTest(p, com.xenowarriors.apl.Apl.Ability.POD_RECALL, 0);
        }), 60));
        l.add(new Step(null, () -> PackTests.server(p -> {
            BlockPos pod = (BlockPos) pr[0];
            p.serverLevel().setBlock(pod.above(), Blocks.AIR.defaultBlockState(), 3);
            p.serverLevel().setBlock(pod, Blocks.AIR.defaultBlockState(), 3);
            if (pr[1] instanceof net.minecraft.world.level.GameType g) p.setGameMode(g);
            p.setHealth(p.getMaxHealth());
            PackTests.arena(p); PackTests.clear(p);
        }), 40));
        // ---- a bot putting the Dragon Balls down: the other bots step out of its ring
        Object[] dbr = new Object[3];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PartyBotEntity a = PartyBots.spawn(p, CharacterType.MIKIKO, null), b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (a == null || b == null) return;
            BlockPos c = p.blockPosition().offset(4, 0, 0);
            a.moveTo(c.getX() + 0.5, p.getY(), c.getZ() + 0.5, 0F, 0F);
            b.moveTo(c.getX() + 1.5, p.getY(), c.getZ() + 0.5, 0F, 0F);
            dbr[0] = a; dbr[1] = b; dbr[2] = c;
            for (int t = 0; t <= 60; t += 10) com.xenowarriors.skill.ServerScheduler.later(t, () -> com.xenowarriors.compat.DmzDragonBalls.makeRoom(a, p.serverLevel(), c));
        }), 80));
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (!(dbr[1] instanceof PartyBotEntity b)) return;
            BlockPos c = (BlockPos) dbr[2];
            double d = Math.sqrt(Math.pow(b.getX() - c.getX() - 0.5, 2) + Math.pow(b.getZ() - c.getZ() - 0.5, 2));
            PackTests.check("dragon balls: the other bots move out of the way of the one putting them down", d >= 4 && com.xenowarriors.compat.DmzDragonBalls.madeRoom > 0, "dist " + PackTests.f((float) d) + " moves " + com.xenowarriors.compat.DmzDragonBalls.madeRoom);
            PackTests.clear(p);
        }), 5));
        // ---- Karma potions from enemies, and the Karma Regeneration Potion
        int[] kr = new int[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0F, 0F);
            p.serverLevel().addFreshEntity(z);
            com.xenowarriors.item.KarmaPotionDrops.forced = true;
            kr[0] = com.xenowarriors.item.KarmaPotionDrops.dropped;
            z.hurt(p.damageSources().playerAttack(p), 1000F);
        }), 10));
        l.add(new Step(null, () -> PackTests.server(p -> {
            com.xenowarriors.item.KarmaPotionDrops.forced = false;
            int regen = 0;
            for (var it : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(8)))
                if (it.getItem().is(com.xenowarriors.item.ModItems.KARMA_REGEN_POTION.get()) || it.getItem().is(com.xenowarriors.item.ModItems.KARMA_POTION.get())) { regen++; it.discard(); }
            PackTests.check("karma potions: an enemy you beat can drop a Karma Potion and a Karma Regeneration Potion", com.xenowarriors.item.KarmaPotionDrops.dropped - kr[0] == 2 && regen == 2, "dropped " + (com.xenowarriors.item.KarmaPotionDrops.dropped - kr[0]) + " on the ground " + regen);
            kr[1] = com.xenowarriors.item.KarmaRegenPotionItem.regenTicks;
            new ItemStack(com.xenowarriors.item.ModItems.KARMA_REGEN_POTION.get()).finishUsingItem(p.serverLevel(), p);
        }), 70));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("karma potions: the Karma Regeneration Potion gives Karma back every second", p.hasEffect(com.xenowarriors.ModEffects.KARMA_REGEN.get()) && com.xenowarriors.item.KarmaRegenPotionItem.regenTicks - kr[1] >= 2,
                    "ticks " + (com.xenowarriors.item.KarmaRegenPotionItem.regenTicks - kr[1]));
            p.removeAllEffects();
        }), 5));
        // ---- a bot low on Karma drinks a Karma Regeneration Potion from its bag
        Object[] kb = new Object[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (b == null) return;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) b.inventory.setItem(i, ItemStack.EMPTY);
            b.inventory.addItem(new ItemStack(com.xenowarriors.item.ModItems.KARMA_REGEN_POTION.get()));
            b.karma = 25F;
            kb[0] = b; kb[1] = com.xenowarriors.bot.BotPotions.regenDrinks;
        }), 80));
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (!(kb[0] instanceof PartyBotEntity b)) { PackTests.check("bots: a bot low on Karma drinks a Karma Regeneration Potion", false, "no bot"); return; }
            boolean drank = com.xenowarriors.bot.BotPotions.regenDrinks > (Integer) kb[1];
            PackTests.check("bots: a bot low on Karma drinks a Karma Regeneration Potion and gets Karma back", drank && b.hasEffect(com.xenowarriors.ModEffects.KARMA_REGEN.get()) && b.karma > 25F,
                    "drank " + drank + " effect " + b.hasEffect(com.xenowarriors.ModEffects.KARMA_REGEN.get()) + " karma " + PackTests.f(b.karma) + " last " + com.xenowarriors.bot.BotPotions.last(b));
            PackTests.clear(p);
        }), 5));
        // ---- the middle click as one of your own characters (looking up at the sky: nothing to pick)
        int[] rl = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            you(p, 6, "Clicky", com.xenowarriors.character.Race.HUMAN, com.xenowarriors.ka.Element.LIGHTNING);
            com.xenowarriors.karma.Karma.add(p, 1000F);
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0F, -85F);
        }), 60));
        l.add(new Step(null, () -> {
            rl[0] = com.xenowarriors.client.TransformKeyClient.rlSent;
            PackTests.log("midclick: before - type " + com.xenowarriors.client.ClientCharacterData.getLocal() + " why '" + com.xenowarriors.client.TransformKeyClient.rlWhy() + "' hit "
                    + (mc.hitResult == null ? "null" : mc.hitResult.getType()));
            net.minecraft.client.KeyMapping.click(mc.options.keyPickItem.getKey());
        }, 15));
        l.add(new Step("you_midclick", () -> PackTests.server(p -> {
            long cd = com.xenowarriors.skill.SkillManager.freeCooldownLeft(p, new com.xenowarriors.you.YouSkillSet.KarmaStrike());
            PackTests.check("midclick: a created character's middle click fires its skill (pointing at nothing)", com.xenowarriors.client.TransformKeyClient.rlSent > rl[0] && cd > 0,
                    "sent " + (com.xenowarriors.client.TransformKeyClient.rlSent - rl[0]) + " cooldown " + cd + " why '" + com.xenowarriors.client.TransformKeyClient.rlWhy() + "'");
        }), 5));
        // ---- the sea, the ship
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.connection.teleport(PackTests.HOME[0] + SEA_X, 125, PackTests.HOME[2] + SEA_L / 2 + 4, 180F, 10F);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 60));
        Object[] ship = new Object[3];
        double[] z0 = new double[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            buildSea(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
            var lv = p.serverLevel();
            VehicleEntity v = com.xenowarriors.vehicle.Vehicles.TYPES.get(VehicleKind.SHIP).get().create(lv);
            v.moveTo(sea.getX() + 0.5, sea.getY() - 0.5, sea.getZ() + 10.5, 180F, 0F);
            v.setOwner(p.getUUID());
            lv.addFreshEntity(v);
            ship[0] = v;
            p.connection.teleport(sea.getX() + 3.5, sea.getY() + 1, sea.getZ() + 14.5, 180F, 0F);
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            VehicleEntity v = (VehicleEntity) ship[0];
            p.startRiding(v, true);
            for (CharacterType t : new CharacterType[]{CharacterType.SAM, CharacterType.KAERU}) {
                PartyBotEntity b = PartyBots.spawn(p, t, null);
                if (b != null) { b.moveTo(v.getX() + 2, v.getY() + 2.5, v.getZ() + 1, 0F, 0F); com.xenowarriors.bot.BotRides.boardForTest(b, p); }
            }
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> {                        // (a slow start - the sea's chunks still loading: bots left behind try again)
            VehicleEntity v = (VehicleEntity) ship[0];
            if (p.getVehicle() != v) p.startRiding(v, true);
            for (PartyBotEntity b : PartyBots.bots(p)) if (!b.isPassenger()) { b.moveTo(v.getX() + 2, v.getY() + 2.5, v.getZ() + 1, 0F, 0F); com.xenowarriors.bot.BotRides.boardForTest(b, p); }
        }), 40));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(20); }, 30));
        l.add(new Step("ship_aboard", () -> PackTests.server(p -> {
            VehicleEntity v = (VehicleEntity) ship[0];
            double top = v.waterTop();
            PackTests.check("ship: it floats (keel under the water)", v.afloat() && v.getY() < top - 0.2 && v.getY() > top - VehicleEntity.DRAFT - 0.5, "y " + PackTests.f(v.getY()) + " water " + PackTests.f(top));
            PackTests.check("ship: you and your bots are aboard", v.getPassengers().size() == 3 && v.getFirstPassenger() == p, "aboard " + v.getPassengers().size());
        }), 5));
        // a bot at the helm: you get off into a boat, and it sails after you
        l.add(new Step(null, () -> PackTests.server(p -> {
            VehicleEntity v = (VehicleEntity) ship[0];
            com.xenowarriors.vehicle.Ships.helm(v, p);
            var boat = new net.minecraft.world.entity.vehicle.Boat(p.serverLevel(), sea.getX() + 0.5, sea.getY() - 0.4, sea.getZ() - 18.5);
            p.serverLevel().addFreshEntity(boat);
            ship[1] = boat;
            z0[0] = v.getZ();
        }), 10));
        l.add(new Step(null, () -> PackTests.server(p -> {
            VehicleEntity v = (VehicleEntity) ship[0];
            PackTests.check("ship: a bot takes the helm", v.driver() instanceof PartyBotEntity, "driver " + v.driver());
            p.stopRiding();
            p.startRiding((net.minecraft.world.entity.Entity) ship[1], true);
        }), 120));
        l.add(new Step("ship_bot_helm", () -> PackTests.server(p -> {
            VehicleEntity v = (VehicleEntity) ship[0];
            PackTests.check("ship: the bot at the helm sails after you", z0[0] - v.getZ() > 3, "moved " + PackTests.f(z0[0] - v.getZ()) + " speed " + PackTests.f(v.speed()));
            p.stopRiding();
            for (var e : new java.util.ArrayList<>(v.getPassengers())) e.stopRiding();
            v.discard(); ((net.minecraft.world.entity.Entity) ship[1]).discard();
            PackTests.clear(p);
        }), 5));
        // ---- a ghost ship: its cannons, and the treasure when it's sunk
        int[] gs = new int[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.connection.teleport(sea.getX() + 0.5, sea.getY() + 1, sea.getZ() + SEA_L / 2 + 3.5, 180F, 15F);
            gs[0] = com.xenowarriors.vehicle.CorruptShips.fired; gs[1] = com.xenowarriors.vehicle.CorruptShips.sunk;
            ship[2] = com.xenowarriors.vehicle.CorruptShips.place(p.serverLevel(), sea.getX() + 0.5, sea.getY() - 0.5 - VehicleEntity.DRAFT + 0.4, sea.getZ() - 4.5, p);
        }), 30));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setXRot(10); }, 60));
        l.add(new Step("ghost_ship", () -> {}, 80));
        l.add(new Step(null, () -> PackTests.server(p -> {
            VehicleEntity v = (VehicleEntity) ship[2];
            PackTests.check("ghost ship: it comes with a crew and fires its cannons", v != null && v.getPassengers().size() >= 4 && com.xenowarriors.vehicle.CorruptShips.fired > gs[0],
                    "crew " + (v == null ? -1 : v.getPassengers().size()) + " fired " + (com.xenowarriors.vehicle.CorruptShips.fired - gs[0]));
            if (v != null) { ship[1] = v.blockPosition(); v.hurt(p.damageSources().playerAttack(p), 10000F); }
        }), 20));
        l.add(new Step("ghost_ship_sunk", () -> PackTests.server(p -> {
            boolean chest = false;
            if (ship[1] instanceof BlockPos at) for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -2, -2), at.offset(2, 4, 2))) if (p.serverLevel().getBlockState(q).is(Blocks.CHEST)) chest = true;
            PackTests.check("ghost ship: sunk, it leaves a treasure chest", com.xenowarriors.vehicle.CorruptShips.sunk > gs[1] && chest, "sunk " + (com.xenowarriors.vehicle.CorruptShips.sunk - gs[1]) + " chest " + chest);
            p.serverLevel().getEntitiesOfClass(com.xenowarriors.corrupted.CorruptedSoulEntity.class, p.getBoundingBox().inflate(80)).forEach(e -> e.discard());
        }), 5));
        // ---- bots and boats: Crafting 20, one has to cross the water - it makes a boat and paddles over
        int[] bb = new int[4];
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.craftLevel = 25);
            bb[0] = com.xenowarriors.bot.BotBoats.crafted; bb[1] = com.xenowarriors.bot.BotBoats.launched; bb[2] = com.xenowarriors.bot.BotBoats.paddled; bb[3] = com.xenowarriors.bot.BotBoats.packed;
            p.connection.teleport(sea.getX() + 0.5, sea.getY() + 1, sea.getZ() + SEA_L / 2 + 3.5, 180F, 30F);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (b == null) return;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) b.inventory.setItem(i, ItemStack.EMPTY);
            b.inventory.addItem(new ItemStack(Items.OAK_LOG, 3));
            b.moveTo(sea.getX() + 0.5, sea.getY() - 0.5, sea.getZ() - 10.5, 180F, 0F);       // (out in the water)
            ship[0] = b;
        }), 20));
        l.add(new Step("bot_boat", () -> {}, 160));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = (PartyBotEntity) ship[0];
            PackTests.check("boats: a Crafting 20 bot makes a boat and paddles it across", com.xenowarriors.bot.BotBoats.crafted > bb[0] && com.xenowarriors.bot.BotBoats.launched > bb[1]
                    && com.xenowarriors.bot.BotBoats.paddled > bb[2], "made " + (com.xenowarriors.bot.BotBoats.crafted - bb[0]) + " launched " + (com.xenowarriors.bot.BotBoats.launched - bb[1])
                    + " paddled " + (com.xenowarriors.bot.BotBoats.paddled - bb[2]) + " packed " + (com.xenowarriors.bot.BotBoats.packed - bb[3]) + " dist " + (b == null ? -1 : Math.round(b.distanceTo(p)))
                    + " last out: " + com.xenowarriors.bot.BotBoats.why);
        }), 200));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = (PartyBotEntity) ship[0];
            PackTests.check("boats: at the far side it gets out and packs the boat away", com.xenowarriors.bot.BotBoats.packed > bb[3] && b != null && !b.isPassenger(),
                    "packed " + (com.xenowarriors.bot.BotBoats.packed - bb[3]) + " riding " + (b != null && b.isPassenger()) + " dist " + (b == null ? -1 : Math.round(b.distanceTo(p))));
            PackTests.clear(p);
        }), 5));
        // your boat's full: the third bot takes another boat
        int[] ob = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            var lv = p.serverLevel();
            var mine = new net.minecraft.world.entity.vehicle.Boat(lv, sea.getX() + 0.5, sea.getY() - 0.4, sea.getZ() + 20.5);
            var spare = new net.minecraft.world.entity.vehicle.Boat(lv, sea.getX() + 4.5, sea.getY() - 0.4, sea.getZ() + 22.5);
            lv.addFreshEntity(mine); lv.addFreshEntity(spare);
            ship[1] = spare;
            p.connection.teleport(sea.getX() + 0.5, sea.getY() + 0.2, sea.getZ() + 20.5, 180F, 20F);
            p.startRiding(mine, true);
            PartyBotEntity a = PartyBots.spawn(p, CharacterType.KAERU, null), c = PartyBots.spawn(p, CharacterType.SAM, null);
            if (a != null) { a.moveTo(mine.getX() + 1, mine.getY() + 0.5, mine.getZ(), 0F, 0F); a.startRiding(mine, true); }
            if (c != null) c.moveTo(spare.getX(), spare.getY() + 1, spare.getZ() - 1.5, 0F, 0F);
            ob[0] = com.xenowarriors.bot.BotBoats.boarded;
            ship[0] = c;
        }), 60));
        l.add(new Step("bot_other_boat", () -> PackTests.server(p -> {
            var c = (PartyBotEntity) ship[0];
            PackTests.check("boats: your boat's full - a bot gets into another boat", c != null && c.getVehicle() == ship[1] && com.xenowarriors.bot.BotBoats.boarded > ob[0],
                    "vehicle " + (c == null ? "-" : c.getVehicle()) + " boarded " + (com.xenowarriors.bot.BotBoats.boarded - ob[0]));
            p.stopRiding();
            PackTests.clear(p);
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.vehicle.Boat.class, p.getBoundingBox().inflate(80)).forEach(e -> e.discard());
        }), 5));
        // ---- torches in the dark: one crafts them (coal + sticks), Mikiko lights sticks with fire
        int[] tc = new int[3];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            var lv = p.serverLevel();
            BlockPos base = BlockPos.containing(p.getX() + 8, p.getY(), p.getZ() + 6);
            for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++) for (int y = -1; y <= 4; y++) {
                boolean shell = Math.abs(x) == 4 || Math.abs(z) == 3 || y == -1 || y == 4;
                lv.setBlock(base.offset(x, y, z), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            tc[0] = com.xenowarriors.bot.BotSupport.crafted; tc[1] = com.xenowarriors.bot.BotSupport.lit; tc[2] = com.xenowarriors.bot.BotSupport.torches;
            PartyBotEntity a = PartyBots.spawn(p, CharacterType.SAM, null), m = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (a != null) { for (int i = 0; i < a.inventory.getContainerSize(); i++) a.inventory.setItem(i, ItemStack.EMPTY); a.inventory.addItem(new ItemStack(Items.COAL, 2)); a.inventory.addItem(new ItemStack(Items.STICK, 2));
                a.moveTo(base.getX() - 2 + 0.5, base.getY(), base.getZ() + 0.5, 0F, 0F); a.setNoAi(false); }
            if (m != null) { for (int i = 0; i < m.inventory.getContainerSize(); i++) m.inventory.setItem(i, ItemStack.EMPTY); m.inventory.addItem(new ItemStack(Items.STICK, 3));
                m.moveTo(base.getX() + 2 + 0.5, base.getY(), base.getZ() + 0.5, 0F, 0F); }
        }), 120));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("torches: a bot in the dark crafts torches (coal and sticks) and puts them up", com.xenowarriors.bot.BotSupport.crafted > tc[0] && com.xenowarriors.bot.BotSupport.torches > tc[2],
                    "crafted " + (com.xenowarriors.bot.BotSupport.crafted - tc[0]) + " placed " + (com.xenowarriors.bot.BotSupport.torches - tc[2]));
            PackTests.check("torches: a fire bot lights sticks into torches", com.xenowarriors.bot.BotSupport.lit > tc[1], "lit " + (com.xenowarriors.bot.BotSupport.lit - tc[1]));
            PackTests.clear(p);
        }), 5));
        // ---- side missions: foes that vanish without being beaten come back
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p); com.xenowarriors.story.StorySides.talkForTest(p, "sam"); }), 20));
        for (int k = 0; k < 6; k++) l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.story.StoryDialogueScreen d) d.testNext(0); }, 8));
        int[] rs = new int[1];
        l.add(new Step(null, () -> mc.setScreen(null), 90));                      // (its foes turn up first)
        l.add(new Step(null, () -> PackTests.server(p -> { rs[0] = com.xenowarriors.story.StorySides.respawned; com.xenowarriors.story.StorySides.vanishForTest(p); }), 100));
        l.add(new Step(null, () -> PackTests.server(p -> {
            boolean active = com.xenowarriors.story.StorySides.testActive(p);
            PackTests.check("side mission: vanished foes come back", !active || com.xenowarriors.story.StorySides.respawned > rs[0] && !com.xenowarriors.story.StorySides.testFoes(p).isEmpty(),
                    "active " + active + " respawned " + (com.xenowarriors.story.StorySides.respawned - rs[0]) + " out " + com.xenowarriors.story.StorySides.testFoes(p).size() + " " + com.xenowarriors.story.StorySides.debugForTest(p));
            if (active) com.xenowarriors.story.StorySides.cancel(p);
            PackTests.clear(p);
        }), 5));
        // ---- phoning a busy teammate needs their number: a high enough bond
        l.add(new Step(null, () -> PackTests.server(p -> {
            int before = com.xenowarriors.bot.BotCalls.noNumber;
            com.xenowarriors.party.Bonds.set(p, "zane", 0, 0);
            boolean had = com.xenowarriors.bot.BotCalls.hasNumber(p, CharacterType.ZANE);
            com.xenowarriors.party.Bonds.set(p, "zane", com.xenowarriors.bot.BotCalls.NUMBER_BOND, 0);
            boolean has = com.xenowarriors.bot.BotCalls.hasNumber(p, CharacterType.ZANE);
            com.xenowarriors.party.Bonds.set(p, "zane", 0, 0);
            if (com.xenowarriors.bot.OffDuty.offDuty(p, CharacterType.ZANE)) com.xenowarriors.bot.BotCalls.call(p, CharacterType.ZANE);
            PackTests.check("phone: no number below bond " + com.xenowarriors.bot.BotCalls.NUMBER_BOND + " (can't call them), their number at it", !had && has
                    && (!com.xenowarriors.bot.OffDuty.offDuty(p, CharacterType.ZANE) || com.xenowarriors.bot.BotCalls.noNumber > before), "had " + had + " has " + has + " turned away " + (com.xenowarriors.bot.BotCalls.noNumber - before));
        }), 5));
        // ---- Quiet Strength (Lexy): its Sky Haters stay to be beaten (raid clean-up used to send them off in a couple of seconds)
        int[] qs = new int[2];
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p);
            com.xenowarriors.story.StorySides.readyNowForTest(p, "lexy");
            com.xenowarriors.story.StorySides.talkForTest(p, "lexy"); }), 20));
        for (int k = 0; k < 6; k++) l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.story.StoryDialogueScreen d) d.testNext(0); }, 8));
        l.add(new Step(null, () -> { mc.setScreen(null); PackTests.server(p -> qs[0] = com.xenowarriors.story.StorySides.respawned); }, 200));
        l.add(new Step(null, () -> PackTests.server(p -> {
            boolean active = com.xenowarriors.story.StorySides.testActive(p);
            int out = com.xenowarriors.story.StorySides.testFoes(p).size(), back = com.xenowarriors.story.StorySides.respawned - qs[0];
            PackTests.check("side mission: Quiet Strength's Sky Haters stay put (none vanished in 10 s)", active && out > 0 && back == 0, "active " + active + " out " + out + " vanished " + back + " " + com.xenowarriors.story.StorySides.debugForTest(p));
            for (var m : com.xenowarriors.story.StorySides.testFoes(p)) m.hurt(p.damageSources().playerAttack(p), 1000F);
            qs[1] = out;
        }), 50));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("side mission: beating them counts", com.xenowarriors.story.StorySides.objective(p) == null || !com.xenowarriors.story.StorySides.objective(p).endsWith(": 0/6"), String.valueOf(com.xenowarriors.story.StorySides.objective(p)));
            if (com.xenowarriors.story.StorySides.testActive(p)) com.xenowarriors.story.StorySides.cancel(p);
            PackTests.clear(p);
        }), 5));
        // ---- parties: more cakes for more guests, barrels of supplies for close bonds, party crashers (beaten in time: double bond)
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            int i = 0;
            for (CharacterType t : new CharacterType[]{CharacterType.SAM, CharacterType.KAERU, CharacterType.MIKIKO, CharacterType.TECHNICA}) {
                PartyBotEntity b = PartyBots.spawn(p, t, null);
                if (b == null) continue;
                b.moveTo(p.getX() + (i % 2 == 0 ? 2 : -2), p.getY(), p.getZ() + 2 + i, 0F, 0F);
                com.xenowarriors.party.Bonds.set(p, b.charKey(), 80, 0);
                i++;
            }
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.bot.BotPartyTime.start(p)), 40));
        l.add(new Step("party_cakes", () -> PackTests.server(p -> {
            int cakes = com.xenowarriors.bot.BotPartyTime.cakesForTest(p), barrels = com.xenowarriors.bot.BotPartyTime.barrelsForTest(p);
            PackTests.check("party: more cakes for a bigger party", cakes == com.xenowarriors.bot.BotPartyTime.cakesFor(5) && cakes >= 3, "cakes " + cakes);
            PackTests.check("party: close bonds bring barrels of supplies", barrels == 3, "barrels " + barrels);
            com.xenowarriors.bot.BotPartyTime.crashNow(p);
            PackTests.check("party: party crashers turn up", com.xenowarriors.bot.BotPartyTime.crashersForTest(p) > 0, "crashers " + com.xenowarriors.bot.BotPartyTime.crashersForTest(p));
        }), 40));
        l.add(new Step("party_crashers", () -> PackTests.server(p -> {
            for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40), m -> m.getTags().contains("xw_party_crasher"))) m.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("party: beating the crashers in time doubles the party's bond", com.xenowarriors.bot.BotPartyTime.doubledForTest(p) && com.xenowarriors.bot.BotPartyTime.active(p),
                    "doubled " + com.xenowarriors.bot.BotPartyTime.doubledForTest(p) + " active " + com.xenowarriors.bot.BotPartyTime.active(p));
            p.setHealth(p.getMaxHealth());
        }), 5));
        // ---- party games: bots arm wrestle each other; a bot challenges you to arm wrestle (mash left click) and to Memory
        int[] pg = new int[8];
        l.add(new Step(null, () -> PackTests.server(p -> {
            pg[0] = com.xenowarriors.bot.BotPartyGames.botArms; pg[1] = com.xenowarriors.bot.BotPartyGames.armWins; pg[2] = com.xenowarriors.bot.BotPartyGames.memPlayed;
            pg[3] = com.xenowarriors.party.Bonds.revivedBoosts;
            com.xenowarriors.bot.BotPartyGames.nowForTest(p, 0);
        }), 120));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("party games: two bots arm wrestle", com.xenowarriors.bot.BotPartyGames.botArms > pg[0], "bot games " + (com.xenowarriors.bot.BotPartyGames.botArms - pg[0]));
            com.xenowarriors.bot.BotPartyGames.nowForTest(p, 1);
        }), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("party games: a bot asks you to arm wrestle (a card)", com.xenowarriors.bot.BotPartyGames.askingForTest(p), "asking " + com.xenowarriors.bot.BotPartyGames.askingForTest(p));
            com.xenowarriors.bot.BotPartyGames.answerForTest(p, 1);
        }), 50));
        for (int k = 0; k < 30; k++) l.add(new Step(k == 4 ? "party_arm_wrestle" : null, () -> { if (mc.screen instanceof com.xenowarriors.client.party.PartyGameScreens.Arm a) { a.mash(); a.mash(); a.mash(); } }, 2));
        l.add(new Step(null, () -> {
            var a = mc.screen instanceof com.xenowarriors.client.party.PartyGameScreens.Arm x ? x : null;
            PackTests.check("party games: mashing left click wins the arm wrestle", com.xenowarriors.client.party.PartyGameScreens.Arm.lastOutcome == 1, "screen " + (a != null) + " outcome " + (a == null ? -9 : a.outcomeForTest()) + " bar " + (a == null ? -1 : a.barForTest()));
        }, 60));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("party games: winning gives XP (and TP with DragonMineZ)", com.xenowarriors.bot.BotPartyGames.armWins > pg[1], "wins " + (com.xenowarriors.bot.BotPartyGames.armWins - pg[1]));
            com.xenowarriors.bot.BotPartyGames.nowForTest(p, 2);
        }), 30));
        l.add(new Step(null, () -> PackTests.server(p -> com.xenowarriors.bot.BotPartyGames.answerForTest(p, 2)), 30));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.party.PartyGameScreens.Memory m) m.flip(0); }, 3));
        l.add(new Step("party_memory", () -> {}, 3));
        for (int k = 0; k < 90; k++) l.add(new Step(null, () -> {
            if (!(mc.screen instanceof com.xenowarriors.client.party.PartyGameScreens.Memory m) || m.botTurnForTest() || m.busyForTest() || m.doneForTest()) return;
            for (int i = 0; i < 16; i++) if (!m.takenForTest(i)) {
                int j = m.pairOfForTest(i);
                m.flip(i); m.flip(j); break;
            }
        }, 4));
        l.add(new Step("party_memory_done", () -> {
            int[] r = com.xenowarriors.client.party.PartyGameScreens.Memory.lastResult;
            PackTests.check("party games: Memory - flip two, find the pairs, it takes turns with you", r[0] + r[1] == 8, "you " + r[0] + " bot " + r[1] + " seen " + com.xenowarriors.client.party.PartyGameScreens.Memory.seen);
        }, 3));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("party games: the Memory game's result reaches the server", com.xenowarriors.bot.BotPartyGames.memPlayed > pg[2], "played " + (com.xenowarriors.bot.BotPartyGames.memPlayed - pg[2]));
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            if (b != null) com.xenowarriors.party.Bonds.revived(b, p);
            PackTests.check("revive: getting a bot back up gives a little extra bond each time", com.xenowarriors.party.Bonds.revivedBoosts > pg[3], "boosts " + (com.xenowarriors.party.Bonds.revivedBoosts - pg[3]));
        }), 20));
        if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight")) {
            l.add(new Step(null, () -> { pg[4] = com.xenowarriors.client.PartyHats.efDraws; pg[7] = com.xenowarriors.client.PartyHats.layerDraws; PackWomTests.battle(true); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 30));
            l.add(new Step("party_hat_ef", () -> {
                Boolean ef = com.xenowarriors.client.compat.epicfight.EpicFightCompat.efDrewForTest(mc.player.getId());
                PackTests.check("party: hats stay on in Epic Fight's stance", com.xenowarriors.client.PartyHats.efDraws > pg[4] || !Boolean.TRUE.equals(ef) && com.xenowarriors.client.PartyHats.layerDraws > pg[7],   // (Epic Fight drawing: on its head bone; our own pose: our layer)
                        "drawn " + (com.xenowarriors.client.PartyHats.efDraws - pg[4]) + " wearing " + com.xenowarriors.client.PartyHats.wearing(mc.player) + " ef drew " + ef + " " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player));
            }, 3));
            l.add(new Step(null, () -> { PackWomTests.battle(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        }
        l.add(new Step(null, () -> PackTests.server(p -> {
            pg[5] = com.xenowarriors.bot.BotPartyTime.earlyBond;
            com.xenowarriors.bot.BotPartyTime.endEarlyForTest(p);
            PackTests.check("party: one that breaks up early still gives some bond", com.xenowarriors.bot.BotPartyTime.earlyBond > pg[5], "early bond " + (com.xenowarriors.bot.BotPartyTime.earlyBond - pg[5]));
            PackTests.clear(p);
        }), 20));
        // ---- flying with a bot: you hang from its hand
        Object[] fl = new Object[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            pg[6] = com.xenowarriors.client.HangClient.hangs;
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.LEXY, null);
            if (b == null) return;
            b.moveTo(p.getX() + 1, p.getY(), p.getZ() + 1, 0F, 0F);
            fl[0] = b;
        }), 30));
        l.add(new Step(null, () -> PackTests.server(p -> { if (fl[0] instanceof PartyBotEntity b) { PartyBots.action(p, b.getId(), PartyBots.ACT_FLY, 0); b.setDeltaMovement(0, 0.6, 0); b.setNoGravity(true); b.moveTo(b.getX(), b.getY() + 4, b.getZ()); } }), 30));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 20));
        l.add(new Step(null, () -> PackTests.server(p -> { if (fl[0] instanceof PartyBotEntity b) { b.setDeltaMovement(0, 0, 0); b.moveTo(b.getX(), p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getBlockX(), b.getBlockZ()) + 5, b.getZ()); } }), 3));   // (well up: low down you're held off the ground)
        l.add(new Step("bot_hand_flight", () -> PackTests.server(p -> {
            var b = fl[0] instanceof PartyBotEntity x ? x : null;
            int floorY = b == null ? 0 : p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getBlockX(), b.getBlockZ());
            PackTests.check("flying with a bot: you hang from its hand below it (held off the ground when it flies low)", b != null && p.getVehicle() == b
                            && (p.getY() < b.getY() - 1.0 || p.getY() <= b.getY() && Math.abs(p.getY() - floorY) < 0.05 && b.getY() < floorY - PartyBotEntity.HANG_DROP + 0.5),
                    "riding " + (p.getVehicle() == b) + " you y " + String.format("%.1f", p.getY()) + " it " + (b == null ? "-" : String.format("%.1f", b.getY())) + " floor " + floorY);
            PackTests.check("flying with a bot: the hanging pose plays", com.xenowarriors.client.HangClient.hangs > pg[6], "hangs " + (com.xenowarriors.client.HangClient.hangs - pg[6]));
            p.stopRiding();
            PackTests.clear(p);
        }), 5));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        // ---- Auto Eat fills the hunger bar right up (every layer of a big stomach too)
        int[] ae = new int[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.apl.Apl.setForTest(p, com.xenowarriors.apl.Apl.Ability.AUTO_EAT, 1);
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 32));
            p.getInventory().add(new ItemStack(Items.BREAD, 16));
            p.getFoodData().setFoodLevel(4);
            p.getPersistentData().remove("xw_food_total");
            ae[0] = com.xenowarriors.apl.Apl.eatenForTest();
        }), 60));
        l.add(new Step(null, () -> PackTests.server(p -> {
            int max = com.xenowarriors.character.BigHunger.max(p);
            int have = max > 20 ? com.xenowarriors.character.BigHunger.total(p) : p.getFoodData().getFoodLevel();
            PackTests.check("auto eat: eats till the hunger bar is completely full", have >= max && com.xenowarriors.apl.Apl.eatenForTest() - ae[0] >= 2,
                    "food " + have + "/" + max + " ate " + (com.xenowarriors.apl.Apl.eatenForTest() - ae[0]));
            com.xenowarriors.apl.Apl.setForTest(p, com.xenowarriors.apl.Apl.Ability.AUTO_EAT, 0);
            PackTests.clear(p); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 5));
        // ---- health orb abilities: Orb Luck, Orb Magnet, Karma Orbs
        int[] orbT = new int[4];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.apl.Apl.setForTest(p, com.xenowarriors.apl.Apl.Ability.ORB_LUCK, 3);
            com.xenowarriors.apl.Apl.setForTest(p, com.xenowarriors.apl.Apl.Ability.ORB_MAGNET, 2);
            com.xenowarriors.apl.Apl.setForTest(p, com.xenowarriors.apl.Apl.Ability.KARMA_ORBS, 1);
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setKarma(d.karmaMax * 0.3F));
            orbT[0] = com.xenowarriors.pickup.HealthOrbs.dropped; orbT[1] = com.xenowarriors.pickup.HealthOrbEntity.magnetized; orbT[2] = com.xenowarriors.pickup.HealthOrbEntity.karmaTaken;
            for (int i = 0; i < 60; i++) {                                         // (60 kills: about 10 orbs at 16%)
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                if (z == null) continue;
                z.moveTo(p.getX() + 14, p.getY(), p.getZ() + (i % 5) - 2, 0F, 0F);
                z.addTag("xw_pack");
                p.serverLevel().addFreshEntity(z);
                z.hurt(p.damageSources().playerAttack(p), 1000F);
            }
        }), 120));
        l.add(new Step("karma_orbs", () -> PackTests.server(p -> {
            int dropped = com.xenowarriors.pickup.HealthOrbs.dropped - orbT[0];
            PackTests.check("orbs: Orb Luck - more orbs drop (16% at rank 3)", dropped >= 4, "dropped " + dropped + " of 60");
            PackTests.check("orbs: Orb Magnet - they come to you from 14 blocks away", com.xenowarriors.pickup.HealthOrbEntity.magnetized > orbT[1], "magnetized " + (com.xenowarriors.pickup.HealthOrbEntity.magnetized - orbT[1]));
            PackTests.check("orbs: Karma Orbs - they fill Karma", com.xenowarriors.pickup.HealthOrbEntity.karmaTaken > orbT[2], "karma orbs taken " + (com.xenowarriors.pickup.HealthOrbEntity.karmaTaken - orbT[2]));
            for (var a : new com.xenowarriors.apl.Apl.Ability[]{com.xenowarriors.apl.Apl.Ability.ORB_LUCK, com.xenowarriors.apl.Apl.Ability.ORB_MAGNET, com.xenowarriors.apl.Apl.Ability.KARMA_ORBS})
                com.xenowarriors.apl.Apl.setForTest(p, a, 0);
            PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 5));
        // ---- no team attack with a bot you're sparring; Netsu in the rain steams at a high level
        int[] ns = new int[3];
        l.add(new Step(null, () -> com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) return;
            b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0F, 0F);
            boolean before = com.xenowarriors.party.TeamUltimate.partners(p).contains(b);
            PartyBots.action(p, b.getId(), PartyBots.ACT_SPAR, 0);
            boolean during = com.xenowarriors.party.TeamUltimate.partners(p).contains(b);
            PackTests.check("team attacks: none with a bot you're sparring", before && !during, "partner before " + before + " while sparring " + during);
            b.setSparring(false); b.setTarget(null);
            PackTests.clear(p);
        }), 20));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p);
            p.serverLevel().setWeatherParameters(0, 6000, true, false);
            com.xenowarriors.skill.SkillLevels.setForTest(p, "netsu", 4, 1);
            ns[0] = com.xenowarriors.mikiko.Netsu.steamTicks;
            com.xenowarriors.mikiko.Netsu.start(p);
            ns[1] = (int) com.xenowarriors.mikiko.Netsu.remaining(p);
        }), 40));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 20));
        l.add(new Step("netsu_steam", () -> PackTests.server(p -> {
            int lost = ns[1] - (int) com.xenowarriors.mikiko.Netsu.remaining(p);
            boolean rain = com.xenowarriors.mikiko.Netsu.rainedOn(p);
            PackTests.check("netsu: high level in the rain - steam rolls off, it still burns down faster (2x, not 3x)", !rain || com.xenowarriors.mikiko.Netsu.steamTicks > ns[0] && lost > 70 && lost < 150,
                    "rain " + rain + " steam " + (com.xenowarriors.mikiko.Netsu.steamTicks - ns[0]) + " lost " + lost + " in ~60 ticks");
            com.xenowarriors.mikiko.Netsu.stop(p);
            p.serverLevel().setWeatherParameters(6000, 0, false, false);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 3));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        // ---- a made character of the wind learns to fly; Soulslike Universe helmets tuck the hair away
        l.add(new Step(null, () -> PackTests.server(p -> {
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            d.youSlots[9] = new com.xenowarriors.you.YouSlot();
            var ys = new com.xenowarriors.you.YouSlot(); ys.name = "Gale"; ys.race = com.xenowarriors.character.Race.FOX_KIN; ys.element = com.xenowarriors.ka.Element.WIND;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.CREATE, 9, ys);
            boolean before = com.xenowarriors.mikiko.WindFlight.canFly(p);
            for (int k = 0; k < 3; k++) d.statLevel[k] = Math.max(d.statLevel[k], 60);
            PackTests.check("wind: a made character of the wind learns to fly at a high level", !before && com.xenowarriors.mikiko.WindFlight.canFly(p),
                    "before " + before + " after " + com.xenowarriors.mikiko.WindFlight.canFly(p) + " level " + com.xenowarriors.stats.Stats.characterLevel(p));
            for (int k = 0; k < 3; k++) d.statLevel[k] = 1;
        }), 20));
        l.add(new Step(null, () -> { com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO); }, 30));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("slu", "ashen_knight_helmet"));
            if (it != null && it != Items.AIR) p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(it));
            pg[7] = com.xenowarriors.client.render.CharacterDetailLayer.helmetTucks;
            p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 180F, 0F);
        }), 10));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 20));
        l.add(new Step("slu_helmet_hair", () -> {
            boolean slu = net.minecraftforge.fml.ModList.get().isLoaded("slu");
            PackTests.check("helmets: Soulslike Universe's helmets tuck the hair away", !slu || com.xenowarriors.client.render.CharacterDetailLayer.helmetTucks > pg[7],
                    "slu " + slu + " tucks " + (com.xenowarriors.client.render.CharacterDetailLayer.helmetTucks - pg[7]));
            PackTests.server(p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY));
        }, 3));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        // ---- a bot makes its own furnace to smelt ore, and packs up when you leave
        int[] fu = new int[3];
        Object[] fb = new Object[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            fu[0] = com.xenowarriors.bot.BotFurnaces.made; fu[1] = com.xenowarriors.bot.BotFurnaces.packed; fu[2] = com.xenowarriors.bot.BotFurnaces.smelted;
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (b == null) return;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) b.inventory.setItem(i, ItemStack.EMPTY);
            b.inventory.addItem(new ItemStack(Items.RAW_IRON, 3)); b.inventory.addItem(new ItemStack(Items.COBBLESTONE, 8));
            b.inventory.addItem(new ItemStack(Items.COAL, 3)); b.inventory.addItem(new ItemStack(Items.CRAFTING_TABLE));
            b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 3, 0F, 0F);
            com.xenowarriors.bot.BotFurnaces.forceForTest = true;
            fb[0] = b;
        }), 20));
        l.add(new Step("bot_furnace", () -> {}, 400));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("furnace: a bot makes a furnace, sets it down and smelts its ore", com.xenowarriors.bot.BotFurnaces.made > fu[0], "made " + (com.xenowarriors.bot.BotFurnaces.made - fu[0])
                    + " smelted " + (com.xenowarriors.bot.BotFurnaces.smelted - fu[2]));
            p.connection.teleport(p.getX(), p.getY(), p.getZ() + 40, 0F, 0F);
        }), 60));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var b = (PartyBotEntity) fb[0];
            int iron = 0;
            if (b != null) for (int i = 0; i < b.inventory.getContainerSize(); i++) { var s = b.inventory.getItem(i); if (s.is(Items.RAW_IRON) || s.is(Items.IRON_INGOT)) iron += s.getCount(); }
            PackTests.check("furnace: you leave - it grabs everything (and its furnace)", com.xenowarriors.bot.BotFurnaces.packed > fu[1] || com.xenowarriors.bot.BotFurnaces.smelted - fu[2] >= 3,
                    "packed " + (com.xenowarriors.bot.BotFurnaces.packed - fu[1]) + " iron in bag " + iron + " smelted " + (com.xenowarriors.bot.BotFurnaces.smelted - fu[2]));
            com.xenowarriors.bot.BotFurnaces.forceForTest = false;
            PackTests.clear(p);
        }), 5));
        // ---- a bot gives you its side mission from its menu
        Object[] sb = new Object[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            String ch = null;
            for (String k : com.xenowarriors.story.StoryDefs.SIDES.keySet()) if (!k.equals("sam") && CharacterType.byId(k) != CharacterType.NONE) { ch = k; break; }
            if (ch == null) { PackTests.check("side from bot: a character with side missions", false, "none"); return; }
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.byId(ch), null);
            if (b == null) return;
            b.moveTo(p.getX() + 1.5, p.getY(), p.getZ() + 2, 180F, 0F);
            sb[0] = b;
        }), 30));
        l.add(new Step(null, () -> PackTests.server(p -> { if (sb[0] instanceof PartyBotEntity b) PartyBots.action(p, b.getId(), PartyBots.ACT_SIDE, 0); }), 30));
        l.add(new Step("side_from_bot", () -> {}, 5));
        for (int k = 0; k < 6; k++) l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.story.StoryDialogueScreen d) d.testNext(0); }, 8));
        l.add(new Step(null, () -> PackTests.server(p -> {
            boolean on = com.xenowarriors.story.StorySides.testActive(p);
            PackTests.check("side from bot: your bot gives you its side mission from its menu", on, "active " + on);
            if (on) com.xenowarriors.story.StorySides.cancel(p);
            PackTests.clear(p);
        }), 40));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
        // ---- Repairstones: a bot mends your gear with 10 of them
        l.add(new Step(null, () -> PackTests.server(p -> {
            var stone = com.xenowarriors.bot.BotRepairStones.stone();
            if (stone == null) { PackTests.log("repairstones: Souls-like Universe isn't here - skipped"); return; }
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (b == null) return;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) b.inventory.setItem(i, ItemStack.EMPTY);
            b.inventory.addItem(new ItemStack(stone, 15));
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(sword.getMaxDamage() * 6 / 10);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, sword);
            com.xenowarriors.bot.BotRepairStones.offer(b, p, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            boolean ok = com.xenowarriors.bot.BotRepairStones.accept(p, b, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            int left = 0; for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(stone)) left += b.inventory.getItem(i).getCount();
            PackTests.check("repairstones: a bot mends your sword with 10 Repairstones", ok && p.getMainHandItem().getDamageValue() == 0 && left == 5, "ok " + ok + " damage " + p.getMainHandItem().getDamageValue() + " stones left " + left);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            PackTests.clear(p);
        }), 20));
        // ---- a Government soldier's gun drops with its ammo
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (!com.xenowarriors.compat.TaczGuns.available()) { PackTests.log("gov ammo: TaCZ isn't here - skipped"); return; }
            int before = com.xenowarriors.gov.GovAgentEntity.ammoDropped;
            var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel());
            g.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0F, 0F);
            g.addTag("xw_pack");
            p.serverLevel().addFreshEntity(g);
            g.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30));
            g.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 2F);
            g.hurt(p.damageSources().playerAttack(p), 100000F);
            PackTests.check("gov ammo: a soldier's dropped gun comes with its ammo", com.xenowarriors.gov.GovAgentEntity.ammoDropped > before, "rounds " + (com.xenowarriors.gov.GovAgentEntity.ammoDropped - before));
        }), 20));
        // ---- a bot with a Gov Keycard opens a vault
        int[] kv = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            BlockPos v = BlockPos.containing(p.getX() + 5, p.getY(), p.getZ() + 4);
            p.serverLevel().setBlock(v, com.xenowarriors.gov.GovItems.VAULT.get().defaultBlockState(), 3);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (b == null) return;
            b.moveTo(p.getX() + 1, p.getY(), p.getZ() + 1, 0F, 0F);
            b.inventory.addItem(new ItemStack(com.xenowarriors.gov.GovItems.KEYCARD.get()));
            kv[0] = com.xenowarriors.bot.BotKeycards.opened;
        }), 200));
        l.add(new Step("bot_vault", () -> PackTests.server(p -> {
            PackTests.check("keycard: a bot with a Gov Keycard opens the vault", com.xenowarriors.bot.BotKeycards.opened > kv[0], "opened " + (com.xenowarriors.bot.BotKeycards.opened - kv[0]));
            PackTests.clear(p);
        }), 5));
        // ---- enemies spread out onto your bots
        int[] ag = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for (CharacterType t : new CharacterType[]{CharacterType.SAM, CharacterType.KAERU}) { PartyBotEntity b = PartyBots.spawn(p, t, null); if (b != null) b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0F, 0F); }
            ag[0] = com.xenowarriors.bot.BotAggro.turned;
            for (int i = 0; i < 7; i++) { var h = PackTests.husk(p, -6 + i * 2, 12); if (h instanceof net.minecraft.world.entity.Mob m) m.setTarget(p); }
        }), 100));
        l.add(new Step("aggro_bots", () -> PackTests.server(p -> {
            int on = 0;
            for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(30), x -> x.getTarget() instanceof PartyBotEntity && !(x instanceof PartyBotEntity))) on++;
            PackTests.check("aggro: in a big fight some enemies go after your bots", com.xenowarriors.bot.BotAggro.turned > ag[0] && on > 0, "turned " + (com.xenowarriors.bot.BotAggro.turned - ag[0]) + " on bots now " + on);
            PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 5));
        // ---- story: a split fight in the SR tunnels (Rhalix in the other room), the bars open when your room's clear; a bot going down counts
        int[] sp = new int[1];
        int[] rtBefore = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            sp[0] = com.xenowarriors.story.StoryRun.splitBarsOpened;
            rtBefore[0] = com.xenowarriors.story.StoryRun.roomTargets;
            com.xenowarriors.story.StoryRun.testStep(p, "rhalix", "r2", 3);
        }), 80));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setXRot(5); }, 10));
        l.add(new Step("story_split", () -> PackTests.server(p -> {
            PackTests.check("story split: the SR tunnels fight splits you from Rhalix behind bars", com.xenowarriors.story.StoryRun.splitForTest(p) && com.xenowarriors.story.StoryRun.barsShutForTest(p)
                    && !com.xenowarriors.story.StoryRun.roomBForTest(p).isEmpty(), "split " + com.xenowarriors.story.StoryRun.splitForTest(p) + " other room " + com.xenowarriors.story.StoryRun.roomBForTest(p).size()
                    + " " + com.xenowarriors.story.StoryRun.stepForTest(p));
            int[] rf = com.xenowarriors.story.StoryRun.reachForTest(p);
            PackTests.check("story: enemies only appear where you can get to them", rf[1] > 0 && rf[0] == rf[1], "reachable " + rf[0] + " of " + rf[1]);
            PackTests.check("story: buildings can be broken, the ground can't", rf[2] == 1 && rf[3] == 0, "building " + rf[2] + " ground " + rf[3]);
            int held = 0, following = 0;
            for (PartyBotEntity b : PartyBots.bots(p)) if (com.xenowarriors.story.StoryRun.holdsRoom(b)) { held++; if (b.getTarget() == null && b.distanceToSqr(p) < 4 * 4) following++; }
            PackTests.check("story split: your bots behind the bars fight the foes on their side (not trying to follow you)", held == 0 || com.xenowarriors.story.StoryRun.roomTargets > rtBefore[0] && following == 0,
                    "holding " + held + " picked " + (com.xenowarriors.story.StoryRun.roomTargets - rtBefore[0]) + " by you " + following);
            com.xenowarriors.story.StoryRun.killRoomForTest(p, 0);
        }), 60));
        l.add(new Step("story_split_open", () -> PackTests.server(p -> {
            PackTests.check("story split: clearing your room opens the bars", com.xenowarriors.story.StoryRun.splitBarsOpened > sp[0], "opened " + (com.xenowarriors.story.StoryRun.splitBarsOpened - sp[0]) + " " + com.xenowarriors.story.StoryRun.stepForTest(p));
            String before = com.xenowarriors.story.StoryRun.stepForTest(p);
            for (PartyBotEntity b : PartyBots.bots(p)) if (b.isAlive() && !b.downed()) { b.hurt(p.damageSources().generic(), 100000F); break; }
            String after = com.xenowarriors.story.StoryRun.stepForTest(p);
            PackTests.check("story: a bot teammate going down counts toward the mission's downs", !after.contains("downs 0/"), before + " -> " + after);
            com.xenowarriors.story.StoryRun.quit(p, null);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 40));
        // ---- the Ender Saiyan in Vegeta's armor (DragonMineZ)
        Object[] es = new Object[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            var e = com.xenowarriors.boss.EnderSaiyans.ENDER_SAIYAN.get().create(p.serverLevel());
            e.moveTo(p.getX(), p.getY(), p.getZ() + 5, 180F, 0F);
            e.addTag("xw_pack");
            p.serverLevel().addFreshEntity(e);
            es[0] = e;
        }), 40));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(0); mc.player.setXRot(-12); }, 10));
        l.add(new Step("ender_saiyan_vegeta", () -> PackTests.server(p -> {
            var e = (net.minecraft.world.entity.LivingEntity) es[0];
            var chest = e == null ? ItemStack.EMPTY : e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(chest.getItem());
            PackTests.check("ender saiyan: wears Vegeta's Saiyan armor (DragonMineZ)", key != null && key.getPath().startsWith("vegeta_saiyan_armor"), "chest " + key);
            if (e != null) e.discard();
        }), 5));
        // ---- knocked out: no Epic Fight stance; with a sword, down on one knee leaning on it
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }), 20));
        l.add(new Step(null, () -> { com.xenowarriors.client.party.PartyClient.setKnockedOut(mc.player.getUUID(), 400); mc.options.hideGui = true; }, 30));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(0); mc.player.setXRot(10); }, 15));
        l.add(new Step("ko_sword", () -> {
            Boolean ef = com.xenowarriors.client.compat.epicfight.EpicFightCompat.efDrewForTest(mc.player.getId());
            PackTests.check("knocked out with a sword: leaning on it, not Epic Fight's battle stance", com.xenowarriors.client.party.PartyClient.leaning(mc.player) && !Boolean.TRUE.equals(ef), "ef drew " + ef);
        }, 5));
        l.add(new Step(null, () -> PackTests.server(p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY)), 15));
        l.add(new Step("ko_fists", () -> {
            com.xenowarriors.client.party.PartyClient.setKnockedOut(mc.player.getUUID(), 0);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            PackTests.server(p -> p.setGameMode(net.minecraft.world.level.GameType.CREATIVE));
        }, 5));
        // ---- faction backup from the phone: ten of them, they fight what you fight, and go home when you leave
        int[] fb2 = new int[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.faction.Reputation.set(p, com.xenowarriors.faction.Faction.GOV, 800);
            com.xenowarriors.faction.Reputation.set(p, com.xenowarriors.faction.Faction.SR, -200);
        }), 10));
        l.add(new Step(null, () -> com.xenowarriors.client.PhoneScreen.openFactions(), 30));
        l.add(new Step("phone_factions", () -> mc.setScreen(null), 5));
        l.add(new Step(null, () -> PackTests.server(p -> {
            fb2[0] = com.xenowarriors.faction.FactionBackup.sent;
            p.getPersistentData().remove("xw_backup_cd_GOV");
            boolean ok = com.xenowarriors.faction.FactionBackup.call(p, com.xenowarriors.faction.Faction.GOV);
            boolean no = com.xenowarriors.faction.FactionBackup.why(p, com.xenowarriors.faction.Faction.SR) != null;
            PackTests.check("backup: Friendly with the Government - ten of them come; the Sky Rebellion won't", ok && no && com.xenowarriors.faction.FactionBackup.sent - fb2[0] >= 8, "sent " + (com.xenowarriors.faction.FactionBackup.sent - fb2[0]) + " sr refuses " + no);
            var h = PackTests.husk(p, 0, 10);
            if (h instanceof net.minecraft.world.entity.Mob m) { m.addTag("xw_pack"); m.setTarget(p); }
            p.setLastHurtMob(h);
        }), 80));
        l.add(new Step("backup_fight", () -> PackTests.server(p -> {
            int on = 0;
            for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40), x -> com.xenowarriors.faction.FactionBackup.ownerOf(x) != null))
                if (m.getTarget() instanceof net.minecraft.world.entity.monster.Husk) on++;
            PackTests.check("backup: they go for what you're fighting", on > 0, "on the husk " + on);
            fb2[1] = com.xenowarriors.faction.FactionBackup.gone;
            p.connection.teleport(p.getX() + 140, p.getY() + 20, p.getZ(), 0F, 0F);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 80));
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.check("backup: you go far away - they go home", com.xenowarriors.faction.FactionBackup.gone - fb2[1] >= 8 || p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    new net.minecraft.world.phys.AABB(p.blockPosition()).inflate(200), x -> com.xenowarriors.faction.FactionBackup.ownerOf(x) != null).isEmpty(), "gone " + (com.xenowarriors.faction.FactionBackup.gone - fb2[1]));
            PackTests.arena(p); PackTests.clear(p);
        }), 20));
        // ---- reviving: hand out to them, karma flowing in
        int[] rv = new int[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (b == null) return;
            b.moveTo(p.getX() + 1.5, p.getY(), p.getZ() + 1.2, 0F, 0F);
            b.hurt(p.damageSources().generic(), 100000F);
            rv[0] = com.xenowarriors.party.ReviveCircle.flows;
        }), 16));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(30); mc.player.setXRot(15); }, 6));
        l.add(new Step("reviving", () -> PackTests.server(p -> PackTests.check("revive: the reviver holds a hand out and their karma flows into them",
                com.xenowarriors.party.ReviveCircle.flows > rv[0] && com.xenowarriors.client.party.ReviveClient.reviving(mc.player), "flows " + (com.xenowarriors.party.ReviveCircle.flows - rv[0])
                        + " pose " + com.xenowarriors.client.party.ReviveClient.reviving(mc.player))), 5));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); PackTests.server(p -> { PackTests.clear(p); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }); }, 30));
        // ---- Zenkai boost (a DragonMineZ Saiyan revived)
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (!com.xenowarriors.compat.DmzZenkai.saiyan(p)) { PackTests.log("zenkai: the tester isn't a DragonMineZ Saiyan - skipped"); return; }
            int b0 = com.xenowarriors.compat.DmzZenkai.boosts;
            com.xenowarriors.compat.DmzZenkai.forceForTest = true;
            com.xenowarriors.compat.DmzZenkai.revived(p);
            PackTests.check("zenkai: a Saiyan revived gets a Zenkai boost", com.xenowarriors.compat.DmzZenkai.boosts > b0, "boosts " + (com.xenowarriors.compat.DmzZenkai.boosts - b0));
        }), 10));
        // ---- story difficulty: Xeno mode's enemies are much tougher than Story mode's
        float[] hp = new float[2];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.story.Story.unlockForTest(p, 0);
            com.xenowarriors.story.Story.setDifficulty(p, 0);
            com.xenowarriors.story.StoryRun.testStep(p, "rhalix", "r2", 1);
        }), 60));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var f = com.xenowarriors.story.StoryRun.testFoes(p);
            hp[0] = (float) f.stream().mapToDouble(m -> m.getMaxHealth()).min().orElse(0);   // (the weakest of each: a captain's not a grunt)
            com.xenowarriors.story.StoryRun.quit(p, null);
            boolean locked = !com.xenowarriors.story.Story.setDifficulty(p, 4);
            com.xenowarriors.story.Story.unlockForTest(p, 4);
            boolean ok = com.xenowarriors.story.Story.setDifficulty(p, 4);
            PackTests.check("difficulty: Xeno mode is locked until unlocked, then it can be picked", locked && ok && com.xenowarriors.story.Story.difficulty(p) == 4 && com.xenowarriors.story.Story.done(p).isEmpty(), "locked " + locked + " ok " + ok);
            com.xenowarriors.story.StoryRun.testStep(p, "rhalix", "r2", 1);
        }), 60));
        l.add(new Step("story_xeno", () -> PackTests.server(p -> {
            var f = com.xenowarriors.story.StoryRun.testFoes(p);
            hp[1] = (float) f.stream().mapToDouble(m -> m.getMaxHealth()).min().orElse(0);
            PackTests.check("difficulty: Xeno mode's enemies are far tougher", hp[0] > 0 && hp[1] > hp[0] * 5, "story " + PackTests.f(hp[0]) + " xeno " + PackTests.f(hp[1]) + " foes " + f.size());
            com.xenowarriors.story.StoryRun.quit(p, null);
            com.xenowarriors.story.Story.unlockForTest(p, 0);
            com.xenowarriors.story.Story.setDifficulty(p, 0);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 5));
        // ---- Kishi at a high ability level crushes the ground inside its dome (never bedrock)
        int[] kc = new int[1];
        BlockPos[] kp = new BlockPos[1];
        l.add(new Step(null, () -> PackTests.server(p -> p.connection.teleport(PackTests.HOME[0] - 200, 125, PackTests.HOME[2], 0F, 30F)), 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var lv = p.serverLevel();
            BlockPos c = BlockPos.containing(PackTests.HOME[0] - 200, 120, PackTests.HOME[2] + 6);
            kp[0] = c;
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                lv.setBlock(c.offset(x, -4, z), Blocks.BEDROCK.defaultBlockState(), 2);
                lv.setBlock(c.offset(x, -3, z), Blocks.DEEPSLATE.defaultBlockState(), 2);
                lv.setBlock(c.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
                lv.setBlock(c.offset(x, -1, z), Blocks.DIRT.defaultBlockState(), 2);
                for (int y = 0; y < 6; y++) lv.setBlock(c.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            kc[0] = com.xenowarriors.entity.KishiDomeEntity.crushed;
            var dome = com.xenowarriors.entity.KishiDomeEntity.create(lv, p, net.minecraft.world.phys.Vec3.atBottomCenterOf(c));
            dome.setPowerForTest(95);
            lv.addFreshEntity(dome);
        }), 100));
        l.add(new Step("kishi_crush", () -> PackTests.server(p -> {
            var lv = p.serverLevel();
            BlockPos c = kp[0];
            int dirt = 0, stone = 0, deep = 0, bedrock = 0;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                if (lv.getBlockState(c.offset(x, -1, z)).isAir()) dirt++;
                if (lv.getBlockState(c.offset(x, -2, z)).isAir()) stone++;
                if (lv.getBlockState(c.offset(x, -3, z)).isAir()) deep++;
                if (lv.getBlockState(c.offset(x, -4, z)).is(Blocks.BEDROCK)) bedrock++;
            }
            PackTests.check("kishi: a very high level dome crushes dirt, stone and deeper inside it - never bedrock", com.xenowarriors.entity.KishiDomeEntity.crushed > kc[0] && dirt > 0 && stone > 0 && deep > 0 && bedrock == 9,
                    "crushed " + (com.xenowarriors.entity.KishiDomeEntity.crushed - kc[0]) + " dirt " + dirt + " stone " + stone + " deepslate " + deep + " bedrock left " + bedrock);
            p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);
        }), 30));
        // ---- a fire teammate you're close with cooks your food
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.SAM);
            PartyBotEntity m = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (m != null) m.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0F, 0F);
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.getInventory().add(new ItemStack(Items.BEEF, 3));
            PartyBotEntity m = null;
            for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == CharacterType.MIKIKO) m = b;
            com.xenowarriors.party.Bonds.set(p, m == null ? "mikiko" : m.charKey(), 20, 0);
            boolean low = com.xenowarriors.mikiko.AutoCook.cookFor(p) == null;
            com.xenowarriors.party.Bonds.set(p, m == null ? "mikiko" : m.charKey(), 60, 0);
            var cook = com.xenowarriors.mikiko.AutoCook.cookFor(p);
            boolean cooked = cook != null && com.xenowarriors.mikiko.AutoCook.cookOne(p, cook) && p.getInventory().countItem(Items.COOKED_BEEF) > 0;
            PackTests.check("cooking: a fire teammate cooks your food once your bond's high enough", low && cooked, "not at bond 20 " + low + ", cooked at 60 " + cooked);
            p.getInventory().clearContent();
            PackTests.clear(p);
        }), 10));
        // ---- Auto mode: the bot does what it sees you doing
        Object[] au = new Object[1];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (b == null) return;
            b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0F, 0F);
            b.setMode(PartyBotEntity.AUTO);
            au[0] = b;
            for (int i = 0; i < 4; i++) com.xenowarriors.bot.BotAuto.saw(p, com.xenowarriors.bot.BotAuto.Doing.HUNTING);
            var cow = net.minecraft.world.entity.EntityType.COW.create(p.serverLevel());
            cow.moveTo(p.getX() + 6, p.getY(), p.getZ() + 6, 0F, 0F);
            cow.addTag("xw_pack");
            p.serverLevel().addFreshEntity(cow);
        }), 60));
        l.add(new Step("auto_hunt", () -> PackTests.server(p -> {
            var b = (PartyBotEntity) au[0];
            boolean hunts = b != null && (b.getTarget() instanceof net.minecraft.world.entity.animal.Cow || p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, p.getBoundingBox().inflate(20)).isEmpty());
            PackTests.check("auto mode: it sees you hunting and hunts too", com.xenowarriors.bot.BotAuto.doing(p) == com.xenowarriors.bot.BotAuto.Doing.HUNTING && hunts, "doing " + com.xenowarriors.bot.BotAuto.doing(p) + " target " + (b == null ? "-" : b.getTarget()));
            for (int i = 0; i < 4; i++) com.xenowarriors.bot.BotAuto.saw(p, com.xenowarriors.bot.BotAuto.Doing.MINING);
            PackTests.check("auto mode: you start mining - it switches to mining", b != null && b.actMode() == PartyBotEntity.MINING, "act " + (b == null ? -1 : b.actMode()));
            com.xenowarriors.bot.BotAuto.saw(p, com.xenowarriors.bot.BotAuto.Doing.FIGHTING);
            PackTests.check("auto mode: one swing at something else doesn't switch it", b != null && b.actMode() == PartyBotEntity.MINING, "act " + (b == null ? -1 : b.actMode()));
            PackTests.clear(p);
        }), 5));
        // ---- your own (You) warrior's bare hands: a fist combo that Epic Fight draws (a demon: beast style)
        if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight") && com.xenowarriors.compat.epicfight.WomMoves.loaded()) {
            l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p);
                var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
                d.youSlots[6] = new com.xenowarriors.you.YouSlot();
                var ys = new com.xenowarriors.you.YouSlot(); ys.name = "Brawler"; ys.race = com.xenowarriors.character.Race.DEMON; ys.element = com.xenowarriors.ka.Element.FIRE;
                com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.CREATE, 6, ys);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY);
                p.setYRot(0); p.setYHeadRot(0); p.yBodyRot = 0; p.setXRot(0);
            }), 40));
            l.add(new Step(null, () -> { PackWomTests.battle(true); mc.options.hideGui = true; mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 25));
            l.add(new Step(null, () -> PackTests.server(p -> {
                p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY);
                String[] sv = com.xenowarriors.compat.epicfight.WomMoves.movesFor(p), cl = com.xenowarriors.compat.epicfight.WomMoves.movesFor(mc.player);
                PackTests.check("you fists: your own warrior has a bare-hands combo (a demon: beast style), both sides",
                        sv == com.xenowarriors.compat.epicfight.WomMoves.BEAST && cl == com.xenowarriors.compat.epicfight.WomMoves.BEAST,
                        "type " + com.xenowarriors.character.ModCapabilities.getType(p) + " server " + (sv == null ? "none" : sv[0]) + " client " + (cl == null ? "none" : cl[0]));
                PackWomTests.swing(p, 0);
            }), 8));
            l.add(new Step("you_fists_1", () -> {
                Boolean drew = com.xenowarriors.client.compat.epicfight.EpicFightCompat.efDrewForTest(mc.player.getId());
                PackTests.check("you fists: the punch plays (Epic Fight draws it)", Boolean.TRUE.equals(drew),
                        "drew " + drew + " " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player));
            }, 4));
            l.add(new Step(null, () -> PackTests.server(p -> PackWomTests.swing(p, 1)), 10));
            l.add(new Step("you_fists_2", () -> {}, 4));
            l.add(new Step(null, () -> { PackWomTests.battle(false); mc.options.setCameraType(CameraType.FIRST_PERSON); com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO); }, 30));
        }
        // ---- a made Fox Kin's tail and a made Skylander's wings: picked once free, then a Dragon Ball wish each change
        int[] ft = new int[4];
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.arena(p); PackTests.clear(p);
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            d.youSlots[7] = new com.xenowarriors.you.YouSlot();
            var ys = new com.xenowarriors.you.YouSlot(); ys.name = "Kitsu"; ys.race = com.xenowarriors.character.Race.FOX_KIN; ys.element = com.xenowarriors.ka.Element.FIRE;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.CREATE, 7, ys);
            p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY);
            ft[0] = com.xenowarriors.you.YouSlots.featuresSet;
        }), 40));
        l.add(new Step(null, () -> { var s = com.xenowarriors.client.you.YouClient.slots.length > 7 ? com.xenowarriors.client.you.YouClient.slots[7] : null;
            if (s != null) { var sc = new com.xenowarriors.client.you.YouFeatureScreen(7, s, null); sc.testPick(4, 0x3A2A5A, 0x80E0FF, -1, -1, -1); mc.setScreen(sc); } }, 20));
        l.add(new Step("you_tail_screen", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); PackTests.server(p -> {
            var d = new com.xenowarriors.you.YouSlot(); d.name = "Kitsu"; d.tail = 4; d.tailColour = 0x3A2A5A; d.tipColour = 0x80E0FF;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.FEATURES, 7, d);
            var d2 = new com.xenowarriors.you.YouSlot(); d2.name = "Kitsu"; d2.tail = 1;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.FEATURES, 7, d2);   // (refused: it's set)
            var s = com.xenowarriors.you.You.slot(p);
            PackTests.check("you tail: the first pick is free, then it's set", s != null && s.featuresSet && s.tail == 4 && s.tailColour == 0x3A2A5A, "tail " + (s == null ? -1 : s.tail) + " set " + (s != null && s.featuresSet));
            com.xenowarriors.you.YouKa.restyleWish(p);
            var d3 = new com.xenowarriors.you.YouSlot(); d3.name = "Kitsu"; d3.tail = 4; d3.tailColour = 0xF0F0F0; d3.tipColour = 0x202020;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.FEATURES, 7, d3);
            s = com.xenowarriors.you.You.slot(p);
            PackTests.check("you tail: a Dragon Ball wish lets you change it once more", s != null && s.tailColour == 0xF0F0F0 && s.restyles == 0 && com.xenowarriors.you.YouSlots.featuresSet - ft[0] == 2,
                    "colour " + (s == null ? "-" : Integer.toHexString(s.tailColour)) + " wishes " + (s == null ? -1 : s.restyles) + " set " + (com.xenowarriors.you.YouSlots.featuresSet - ft[0]));
            p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 180F, 0F);
        }); ft[1] = com.xenowarriors.client.compat.YouTails.DRAWN[4]; mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 30));
        l.add(new Step("you_nine_tails", () -> PackTests.check("you tail: the nine tails are drawn", com.xenowarriors.client.compat.YouTails.DRAWN[4] > ft[1], "drawn " + (com.xenowarriors.client.compat.YouTails.DRAWN[4] - ft[1])), 3));
        // a Skylander: butterfly wings, each its own colour
        l.add(new Step(null, () -> PackTests.server(p -> {
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            d.youSlots[8] = new com.xenowarriors.you.YouSlot();
            var ys = new com.xenowarriors.you.YouSlot(); ys.name = "Aerin"; ys.race = com.xenowarriors.character.Race.SKYLANDER; ys.element = com.xenowarriors.ka.Element.WIND; ys.form = com.xenowarriors.you.YouSlot.FORM_LUNAR;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.CREATE, 8, ys);
            var f = new com.xenowarriors.you.YouSlot(); f.name = "Aerin"; f.wings = 6; f.wingL = 0xE83A3A; f.wingR = 0x4060FF;
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.FEATURES, 8, f);
            var s = com.xenowarriors.you.You.slot(p);
            PackTests.check("you wings: a Skylander picks butterfly wings, each wing its own colour", s != null && s.wings == 6 && s.wingL == 0xE83A3A && s.wingR == 0x4060FF, "wings " + (s == null ? -1 : s.wings));
            p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 180F, 0F);
        }), 40));
        l.add(new Step(null, () -> ft[2] = com.xenowarriors.client.you.YouWingsLayer.wingsDrawn, 10));
        l.add(new Step("you_butterfly_wings", () -> PackTests.check("you wings: drawn", com.xenowarriors.client.you.YouWingsLayer.wingsDrawn > ft[2], "drawn " + (com.xenowarriors.client.you.YouWingsLayer.wingsDrawn - ft[2])), 3));
        l.add(new Step(null, () -> { var s = com.xenowarriors.client.you.YouClient.slots.length > 8 ? com.xenowarriors.client.you.YouClient.slots[8] : null;
            if (s != null) mc.setScreen(new com.xenowarriors.client.you.YouFeatureScreen(8, s, null)); }, 20));
        l.add(new Step("you_wings_screen", () -> {}, 3));
        // ---- a show character's gear for your own character: armor + outfit at bond 50, enchanted at 75, everything at 100
        int[] cg = new int[4];
        l.add(new Step(null, () -> { mc.setScreen(null); PackTests.server(p -> {
            PackTests.clear(p);
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.PLAY, 7, null);   // Kitsu again
            com.xenowarriors.you.CharacterGear.setGivenForTest(p, CharacterType.MIKIKO, 0);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (b == null) { PackTests.check("gear: a Mikiko bot", false, "no bot"); return; }
            b.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0F, 0F);
            cg[0] = b.getId();
            com.xenowarriors.party.Bonds.set(p, b.charKey(), 30, 0);
            p.getInventory().clearContent();
            com.xenowarriors.you.CharacterGear.ask(p, b);
            int none = count(p);
            com.xenowarriors.party.Bonds.set(p, b.charKey(), 55, 0);
            com.xenowarriors.you.CharacterGear.ask(p, b);
            int plain = count(p);
            boolean outfit = com.xenowarriors.you.CharacterGear.looks(p).contains("mikiko");
            PackTests.check("gear: nothing below bond 50, then Mikiko's armor (4 pieces) and outfit at 55", none == 0 && plain == 4 && outfit, "below " + none + " at 55 " + plain + " outfit " + outfit + " looks " + com.xenowarriors.you.CharacterGear.looks(p));
            ItemStack chest = p.getInventory().items.stream().filter(s -> s.getItem() == com.xenowarriors.item.ModItems.CHAR_CHESTPLATE.get()).findFirst().orElse(ItemStack.EMPTY);
            PackTests.check("gear: the plain set isn't enchanted", !chest.isEmpty() && !chest.isEnchanted(), "chest " + chest);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest.copy()); chest.setCount(0);
            p.getInventory().clearContent();
            com.xenowarriors.party.Bonds.set(p, b.charKey(), 100, 0);
            com.xenowarriors.you.CharacterGear.ask(p, b);
            ItemStack top = p.getInventory().items.stream().filter(s -> s.getItem() == com.xenowarriors.item.ModItems.CHAR_CHESTPLATE.get()).findFirst().orElse(ItemStack.EMPTY);
            int ench = top.getAllEnchantments().size();
            PackTests.check("gear: at bond 100 the set comes with every enchantment", ench >= 3, "enchantments " + ench + " " + top.getAllEnchantments());
            var d = new com.xenowarriors.you.YouSlot(); d.name = "Kitsu"; d.look = "mikiko";
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.LOOK, 7, d);
            var s = com.xenowarriors.you.You.slot(p);
            PackTests.check("gear: Mikiko's outfit worn as a look", s != null && "mikiko".equals(s.look), "look " + (s == null ? "-" : s.look));
            p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 180F, 0F);
        }); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 40));
        l.add(new Step(null, () -> { mc.setScreen(null); cg[1] = com.xenowarriors.client.you.YouCostume.drawn; }, 10));
        l.add(new Step("you_outfit_mikiko", () -> PackTests.check("gear: the outfit is drawn on your character", com.xenowarriors.client.you.YouCostume.drawn > cg[1], "drawn " + (com.xenowarriors.client.you.YouCostume.drawn - cg[1])), 3));
        l.add(new Step(null, () -> { var s = com.xenowarriors.client.you.YouClient.slots.length > 7 ? com.xenowarriors.client.you.YouClient.slots[7] : null;
            if (s != null) mc.setScreen(new com.xenowarriors.client.you.YouLookScreen(7, s, null)); }, 20));
        l.add(new Step("you_outfit_screen", () -> {}, 3));
        // ---- close to Garry and Stevie (bond 70+): your own character dances when Garry beatboxes
        int[] bx = new int[3];
        l.add(new Step(null, () -> { mc.setScreen(null); PackTests.server(p -> {
            com.xenowarriors.party.Bonds.set(p, "garry", 75, 0); com.xenowarriors.party.Bonds.set(p, "stevie", 75, 0);
            p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY);
            PartyBotEntity g = PartyBots.spawn(p, CharacterType.GARRY, null);
            if (g == null) return;
            g.moveTo(p.getX() + 2.5, p.getY(), p.getZ() + 1, 90F, 0F); g.setNoAi(true); g.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            bx[0] = g.getId();
            PackTests.check("beatbox: your character counts as a fan of Garry and Stevie at bond 75", com.xenowarriors.you.BeatboxFans.fan(p), "garry " + com.xenowarriors.party.Bonds.level(p, "garry") + " stevie " + com.xenowarriors.party.Bonds.level(p, "stevie"));
        }); bx[1] = com.xenowarriors.client.GarryBeatbox.fanDances; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 240));
        l.add(new Step("you_fan_dance", () -> PackTests.check("beatbox: your character dances to Garry's beatbox", com.xenowarriors.client.GarryBeatbox.fanDances > bx[1],
                "dances " + (com.xenowarriors.client.GarryBeatbox.fanDances - bx[1]) + " sent " + com.xenowarriors.you.BeatboxFans.sent), 3));
        l.add(new Step(null, () -> PackTests.server(p -> {
            var g = p.serverLevel().getEntity(bx[0]); if (g != null) g.discard();
            com.xenowarriors.party.Bonds.set(p, "garry", 0, 0); com.xenowarriors.party.Bonds.set(p, "stevie", 0, 0); }), 5));
        l.add(new Step(null, () -> { mc.setScreen(null); PackTests.server(p -> {
            var d = new com.xenowarriors.you.YouSlot(); d.name = "Kitsu"; d.look = "";
            com.xenowarriors.you.YouSlots.act(p, com.xenowarriors.network.YouActionPacket.LOOK, 7, d);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
            var b = p.serverLevel().getEntity(cg[0]); if (b != null) b.discard(); }); }, 10));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO); }, 30));
        // ---- blocking gunfire: a higher gun level weaves out of the way (and at 300+ knocks some back); rapid fire still gets some through
        if (com.xenowarriors.compat.TaczGuns.available()) {
            int[] gd = new int[8];   // shooter id, camera id, dodged, reflected, through at start; bot id
            l.add(new Step(null, () -> com.xenowarriors.client.UiTest.character(CharacterType.MIKIKO), 30));
            l.add(new Step(null, () -> PackTests.server(p -> {
                PackTests.arena(p); PackTests.clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getInventory().selected = 0; p.getInventory().setItem(0, ItemStack.EMPTY);
                com.xenowarriors.compat.GunLevel.setLevel(p, 400);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 60, 3, false, false));
                p.connection.teleport(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);
                com.xenowarriors.combat.BulletDodge.FORCE_GUARD.add(p.getUUID());
                var h = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                if (h != null) {
                    h.moveTo(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] + 5, 180F, 0F);
                    h.setYHeadRot(180F); h.setNoAi(true); h.setInvulnerable(true); h.addTag("xw_pack");
                    h.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30));
                    p.serverLevel().addFreshEntity(h);
                    com.xenowarriors.compat.TaczGuns.draw(h);
                    gd[0] = h.getId();
                }
                var st = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
                if (st != null) {
                    st.moveTo(PackTests.HOME[0] + 3.6, PackTests.HOME[1] - 0.3, PackTests.HOME[2] + 1.2, 90F, 6F);
                    st.setYHeadRot(90F); st.setInvisible(true); st.setNoGravity(true); st.setInvulnerable(true); st.addTag("xw_pack");
                    p.serverLevel().addFreshEntity(st);
                    gd[1] = st.getId();
                }
            }), 30));
            l.add(new Step(null, () -> {}, 30));                                   // (the gun drawn first)
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                var cam = mc.level.getEntity(gd[1]); if (cam != null) mc.setCameraEntity(cam);
                gd[2] = com.xenowarriors.combat.BulletDodge.dodged; gd[3] = com.xenowarriors.combat.BulletDodge.reflected; gd[4] = com.xenowarriors.combat.BulletDodge.through; gd[6] = com.xenowarriors.client.DodgeClient.seen; }, 10));
            // single shots, rolls set so the GIF shows weaves both ways, a knock-back and a hit
            float[] rolls = {0.5F, 0.5F, 0.05F, 0.97F, 0.5F, 0.05F};
            int f = 0;
            for (int k = 0; k < rolls.length; k++) {
                float r = rolls[k];
                l.add(new Step(String.format("dodgegif_%03d", f++), () -> PackTests.server(p -> {
                    com.xenowarriors.combat.BulletDodge.forceRoll = r;
                    p.setHealth(p.getMaxHealth());
                    if (p.serverLevel().getEntity(gd[0]) instanceof net.minecraft.world.entity.LivingEntity h) shootAt(h, p);
                }), 1));
                for (int t = 0; t < 11; t++) {
                    boolean again = t == 3 || t == 7;                                   // (a few shots each roll: some go wide)
                    l.add(new Step(String.format("dodgegif_%03d", f++), () -> { if (again) PackTests.server(p -> { com.xenowarriors.combat.BulletDodge.forceRoll = r; p.setHealth(p.getMaxHealth()); if (p.serverLevel().getEntity(gd[0]) instanceof net.minecraft.world.entity.LivingEntity h) shootAt(h, p); }); }, 1));
                }
            }
            l.add(new Step(null, () -> PackTests.server(p -> {
                com.xenowarriors.combat.BulletDodge.forceRoll = null;
                int dd = com.xenowarriors.combat.BulletDodge.dodged - gd[2], rf = com.xenowarriors.combat.BulletDodge.reflected - gd[3], th = com.xenowarriors.combat.BulletDodge.through - gd[4];
                PackTests.check("bullets: blocking with the higher gun level, you weave out of the way", dd >= 2, "dodged " + dd + " reflected " + rf + " through " + th);
                PackTests.check("bullets: at gun level 300+ some get knocked back at the shooter", rf >= 1, "reflected " + rf);
                PackTests.check("bullets: the weave plays (seen on your client)", com.xenowarriors.client.DodgeClient.seen > gd[6], "seen " + (com.xenowarriors.client.DodgeClient.seen - gd[6]));
                gd[2] = com.xenowarriors.combat.BulletDodge.dodged; gd[3] = com.xenowarriors.combat.BulletDodge.reflected; gd[4] = com.xenowarriors.combat.BulletDodge.through;
            }), 5));
            // rapid fire: some dodged or knocked back, some get through
            for (int k = 0; k < 24; k++) l.add(new Step(null, () -> PackTests.server(p -> {
                p.setHealth(p.getMaxHealth());
                if (p.serverLevel().getEntity(gd[0]) instanceof net.minecraft.world.entity.LivingEntity h) shootAt(h, p);
            }), 2));
            l.add(new Step(null, () -> {}, 10));
            l.add(new Step(null, () -> PackTests.server(p -> {
                int dd = com.xenowarriors.combat.BulletDodge.dodged - gd[2], rf = com.xenowarriors.combat.BulletDodge.reflected - gd[3], th = com.xenowarriors.combat.BulletDodge.through - gd[4];
                PackTests.check("bullets: rapid fire - some dodged or knocked back, some still hit", dd + rf >= 3 && th >= 2, "dodged " + dd + " reflected " + rf + " through " + th);
                com.xenowarriors.combat.BulletDodge.FORCE_GUARD.remove(p.getUUID());
                com.xenowarriors.compat.GunLevel.setLevel(p, 1);
                // a bot under fire: guard up, then it weaves too
                PartyBotEntity b = PartyBots.spawn(p, CharacterType.SAM, null);
                if (b != null) {
                    com.xenowarriors.compat.GunLevel.setLevelForTest(b, 400);
                    b.moveTo(PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2] + 2, 0F, 0F);
                    b.setYHeadRot(0F); b.setYBodyRot(0F); b.setNoAi(true);
                    gd[5] = b.getId();
                }
                p.connection.teleport(PackTests.HOME[0] - 3, PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);
                gd[2] = com.xenowarriors.combat.BulletDodge.dodged; gd[3] = com.xenowarriors.combat.BulletDodge.reflected; gd[4] = com.xenowarriors.combat.BulletDodge.through;
            }), 20));
            for (int k = 0; k < 16; k++) l.add(new Step(k == 6 ? "dodge_bot" : null, () -> PackTests.server(p -> {
                var be = p.serverLevel().getEntity(gd[5]);
                if (be instanceof PartyBotEntity b) { b.setHealth(b.getMaxHealth()); b.setYRot(0F); b.setYHeadRot(0F); b.setYBodyRot(0F); }
                if (p.serverLevel().getEntity(gd[0]) instanceof net.minecraft.world.entity.LivingEntity h && be instanceof net.minecraft.world.entity.LivingEntity t) shootAt(h, t);
            }), 4));
            l.add(new Step(null, () -> PackTests.server(p -> {
                int dd = com.xenowarriors.combat.BulletDodge.dodged - gd[2], rf = com.xenowarriors.combat.BulletDodge.reflected - gd[3], th = com.xenowarriors.combat.BulletDodge.through - gd[4];
                var be = p.serverLevel().getEntity(gd[5]);
                PackTests.check("bullets: a bot with the higher gun level puts its guard up and weaves out of the way too", be instanceof PartyBotEntity b && dd + rf >= 2,
                        "dodged " + dd + " reflected " + rf + " through " + th + " guard " + (be instanceof PartyBotEntity b && com.xenowarriors.combat.BulletDodge.botGuardForTest(b)));
            }), 5));
            l.add(new Step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false;
                PackTests.server(p -> { PackTests.clear(p); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    var h = p.serverLevel().getEntity(gd[0]); if (h != null) h.discard();
                    var st = p.serverLevel().getEntity(gd[1]); if (st != null) st.discard(); }); }, 10));
        }
        // ---- Healingstones: a bot carrying some uses one on a badly hurt teammate (a bot here), not only on you
        Object[] hs = new Object[3];
        l.add(new Step(null, () -> PackTests.server(p -> {
            var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("slu", "healingstone"));
            if (it == null || it == Items.AIR) return;
            PackTests.arena(p); PackTests.clear(p);
            PartyBotEntity a = PartyBots.spawn(p, CharacterType.KAERU, null), b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (a == null || b == null) return;
            a.moveTo(p.getX() + 1.5, p.getY(), p.getZ(), 0F, 0F); b.moveTo(p.getX() - 1.5, p.getY(), p.getZ(), 0F, 0F);
            a.inventory.setItem(0, new ItemStack(it, 4));
            b.setHealth(b.getMaxHealth() * 0.2F);
            hs[0] = a; hs[1] = b; hs[2] = com.xenowarriors.compat.HealingStone.onTeammates;
        }), 60));
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (!(hs[1] instanceof PartyBotEntity b)) { PackTests.log("healingstone: no Soulslike Universe here - skipped"); return; }
            PackTests.check("healingstone: a bot uses one on a badly hurt teammate bot", com.xenowarriors.compat.HealingStone.onTeammates > (Integer) hs[2],
                    "used " + (com.xenowarriors.compat.HealingStone.onTeammates - (Integer) hs[2]) + " hp " + PackTests.f(b.getHealth()) + "/" + PackTests.f(b.getMaxHealth()));
            PackTests.clear(p);
        }), 5));
        // ---- a bot falling back hurt: a healthy teammate bot goes for whatever's chasing it
        Object[] rsc = new Object[4];
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            PartyBotEntity a = PartyBots.spawn(p, CharacterType.LEXY, null), b = PartyBots.spawn(p, CharacterType.SAM, null);
            if (a == null || b == null) return;
            a.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0F, 0F); b.moveTo(p.getX() - 3, p.getY(), p.getZ() + 3, 0F, 0F);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            if (z == null) return;
            z.moveTo(p.getX() + 5, p.getY(), p.getZ() + 6, 0F, 0F);
            p.serverLevel().addFreshEntity(z);
            z.setTarget(a);
            a.setHealth(a.getMaxHealth() * 0.2F);
            rsc[0] = a; rsc[1] = b; rsc[2] = z; rsc[3] = PartyBotEntity.rescues;
        }), 60));
        l.add(new Step(null, () -> PackTests.server(p -> {
            if (!(rsc[1] instanceof PartyBotEntity b) || !(rsc[0] instanceof PartyBotEntity a)) return;
            var z = (net.minecraft.world.entity.LivingEntity) rsc[2];
            PackTests.check("bots: a healthy bot goes for what's chasing a teammate falling back hurt", PartyBotEntity.rescues > (Integer) rsc[3] && (b.getTarget() == z || !z.isAlive()),
                    "fell back " + a.retreating + " rescues " + (PartyBotEntity.rescues - (Integer) rsc[3]) + " helper target " + b.getTarget() + " zombie alive " + z.isAlive());
            z.discard(); PackTests.clear(p);
        }), 5));
        // ---- story places: some barrels have healing potions in them
        l.add(new Step(null, () -> PackTests.server(p -> {
            var tower = p.server.getLevel(com.xenowarriors.tower.Tower.LEVEL);
            if (tower == null) return;
            com.xenowarriors.story.StoryScenes.ensure(tower, 7, "farm");
            int n = 0;
            for (int k = 0; k < 6 && n == 0; k++) n += com.xenowarriors.story.StoryScenes.stockBarrels(tower, 7, "farm");
            PackTests.check("story: healing potions in some of a place's barrels", n > 0, "potions " + n + " (all " + com.xenowarriors.story.StoryScenes.potionsStocked + ")");
        }), 40));
        // ---- the Sky Patrol hideout, from the door
        l.add(new Step(null, () -> PackTests.server(p -> {
            var tower = p.server.getLevel(com.xenowarriors.tower.Tower.LEVEL);
            if (tower == null) return;
            com.xenowarriors.story.StoryScenes.ensure(tower, 7, "sp_hideout");
            BlockPos c = com.xenowarriors.story.StoryScenes.center(7, "sp_hideout");
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            p.teleportTo(tower, c.getX() + 0.5, c.getY() + 2.2, c.getZ() + 0.5, 180F, 8F);
        }), 80));
        l.add(new Step("sp_hideout", () -> {}, 5));
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.teleportTo(p.server.overworld(), PackTests.HOME[0], PackTests.HOME[1], PackTests.HOME[2], 0F, 0F);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 40));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
    }

    /** (Tests) a character's armor pieces in the inventory. */
    static int count(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof com.xenowarriors.you.CharacterGear.CharacterArmorItem) n += s.getCount();
        return n;
    }

    /** (Tests) fires {@code h}'s TaCZ gun at the middle of {@code t}. */
    static void shootAt(net.minecraft.world.entity.LivingEntity h, net.minecraft.world.entity.LivingEntity t) {
        double dx = t.getX() - h.getX(), dz = t.getZ() - h.getZ(), dy = (t.getY() + t.getBbHeight() * 0.6) - h.getEyeY();
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90F;
        float pitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180 / Math.PI);
        h.setYRot(yaw); h.setYHeadRot(yaw); h.setXRot(pitch);
        com.xenowarriors.compat.TaczGuns.shoot(h, pitch, yaw);
    }
}

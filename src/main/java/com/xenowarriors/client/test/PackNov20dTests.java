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
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(20); }, 30));
        l.add(new Step("ship_aboard", () -> PackTests.server(p -> {
            VehicleEntity v = (VehicleEntity) ship[0];
            double top = v.waterTop();
            PackTests.check("ship: it floats at its draft", v.afloat() && Math.abs(v.getY() - (top - VehicleEntity.DRAFT)) < 0.35, "y " + PackTests.f(v.getY()) + " water " + PackTests.f(top));
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
                    + " paddled " + (com.xenowarriors.bot.BotBoats.paddled - bb[2]) + " packed " + (com.xenowarriors.bot.BotBoats.packed - bb[3]) + " dist " + (b == null ? -1 : Math.round(b.distanceTo(p))));
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
        l.add(new Step(null, () -> { mc.setScreen(null); PackTests.server(p -> { rs[0] = com.xenowarriors.story.StorySides.respawned; com.xenowarriors.story.StorySides.vanishForTest(p); }); }, 100));
        l.add(new Step(null, () -> PackTests.server(p -> {
            boolean active = com.xenowarriors.story.StorySides.testActive(p);
            PackTests.check("side mission: vanished foes come back", !active || com.xenowarriors.story.StorySides.respawned > rs[0] && !com.xenowarriors.story.StorySides.testFoes(p).isEmpty(),
                    "active " + active + " respawned " + (com.xenowarriors.story.StorySides.respawned - rs[0]) + " out " + com.xenowarriors.story.StorySides.testFoes(p).size());
            if (active) com.xenowarriors.story.StorySides.cancel(p);
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
            com.xenowarriors.bot.BotPartyTime.endForTest(p);
            PackTests.clear(p);
        }), 5));
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
            mc.setScreen(null);
            PackTests.clear(p);
        }), 40));
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
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            sp[0] = com.xenowarriors.story.StoryRun.splitBarsOpened;
            com.xenowarriors.story.StoryRun.testStep(p, "rhalix", "r2", 3);
        }), 80));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setXRot(5); }, 10));
        l.add(new Step("story_split", () -> PackTests.server(p -> {
            PackTests.check("story split: the SR tunnels fight splits you from Rhalix behind bars", com.xenowarriors.story.StoryRun.splitForTest(p) && com.xenowarriors.story.StoryRun.barsShutForTest(p)
                    && !com.xenowarriors.story.StoryRun.roomBForTest(p).isEmpty(), "split " + com.xenowarriors.story.StoryRun.splitForTest(p) + " other room " + com.xenowarriors.story.StoryRun.roomBForTest(p).size()
                    + " " + com.xenowarriors.story.StoryRun.stepForTest(p));
            com.xenowarriors.story.StoryRun.killRoomForTest(p, 0);
        }), 60));
        l.add(new Step("story_split_open", () -> PackTests.server(p -> {
            PackTests.check("story split: clearing your room opens the bars", com.xenowarriors.story.StoryRun.splitBarsOpened > sp[0], "opened " + (com.xenowarriors.story.StoryRun.splitBarsOpened - sp[0]) + " " + com.xenowarriors.story.StoryRun.stepForTest(p));
            String before = com.xenowarriors.story.StoryRun.stepForTest(p);
            for (PartyBotEntity b : PartyBots.bots(p)) if (b.isAlive() && !b.downed()) { b.hurt(p.damageSources().generic(), 100000F); break; }
            String after = com.xenowarriors.story.StoryRun.stepForTest(p);
            PackTests.check("story: a bot teammate going down counts toward the mission's downs", !before.equals(after) && after.contains("downs 1"), before + " -> " + after);
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
}

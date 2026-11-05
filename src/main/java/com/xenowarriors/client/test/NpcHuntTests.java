package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.fox.FoxPets;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;
import java.util.UUID;

/**
 * Bug hunt: NPCs, pets and the economy (focus "npchunt"). Pets vs allies, stale pet copies, whistling a pet across
 * dimensions, the fox bag, the Recarnet villager swap, and forged buy / claim / skin packets. Results are logged as
 * "XW-TEST npchunt ..." with OK / FAIL.
 */
public final class NpcHuntTests {
    private NpcHuntTests() {}

    public static boolean handles(String focus) { return focus != null && focus.startsWith("npchunt"); }

    private static void log(String s) { UiTest.log("npchunt " + s); }

    private static String ok(boolean good) { return good ? " OK" : " FAIL"; }

    private static final class S {
        UUID fox, chicken, piglin, copy;
        boolean chickenTargeted, piglinTargeted;
        float chickenMin = 99;
        int recarnetHumansBefore;
    }

    private static int ownedFoxes(ServerPlayer p) {
        int n = 0;
        for (ServerLevel l : p.server.getAllLevels())
            n += l.getEntities(EntityTypeTest.forClass(Fox.class), f -> f.isAlive() && FoxPets.isOwnedBy(f, p)).size();
        return n;
    }

    private static Fox myFox(ServerPlayer p) {
        var recs = FoxPets.records(p);
        return recs.isEmpty() ? null : FoxPets.loaded(p, recs.getCompound(0));
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<UUID> openBags() {
        try {
            var f = FoxPets.class.getDeclaredField("OPEN_BAGS");
            f.setAccessible(true);
            return (java.util.Set<UUID>) f.get(null);
        } catch (Exception e) { return java.util.Set.of(); }
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        S s = new S();
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); }, 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            p.serverLevel().setDayTime(18000);                     // (night: foxes are awake and hunting)
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 900, 2, false, false));
            p.server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
        }), 20));

        // 1. a pet fox and the same player's winged companion chicken: the fox must not hunt it
        l.add(new Step(null, () -> UiTest.server(p -> {
            ServerLevel lv = p.serverLevel();
            Fox fox = EntityType.FOX.create(lv);
            fox.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
            fox.finalizeSpawn(lv, lv.getCurrentDifficultyAt(fox.blockPosition()), MobSpawnType.EVENT, null, null);
            lv.addFreshEntity(fox);
            FoxPets.tameNow(p, fox);
            s.fox = fox.getUUID();
            Chicken ch = EntityType.CHICKEN.create(lv);
            ch.moveTo(p.getX() + 5, p.getY(), p.getZ() + 1, 0, 0);
            lv.addFreshEntity(ch);
            ch.getPersistentData().putUUID("xw_wing_owner", p.getUUID());    // (as if Rhalix had tamed it - same player)
            ch.setPersistenceRequired();
            s.chicken = ch.getUUID();
            log("ally pet: fox variant=" + fox.getVariant() + " chicken is pet of " + com.xenowarriors.party.Parties.petOwner(ch).equals(p.getUUID()));
        }), 5));
        for (int i = 0; i < 24; i++) l.add(new Step(null, () -> UiTest.server(p -> {
            ServerLevel lv = p.serverLevel();
            if (lv.getEntity(s.fox) instanceof Fox f && lv.getEntity(s.chicken) instanceof Chicken c) {
                if (f.getTarget() == c) s.chickenTargeted = true;
                s.chickenMin = Math.min(s.chickenMin, c.getHealth());
            }
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("ally pet: fox targeted the companion chicken=" + s.chickenTargeted + " chicken min hp=" + s.chickenMin + ok(!s.chickenTargeted));
            if (p.serverLevel().getEntity(s.chicken) instanceof Chicken c) c.discard();
            if (p.serverLevel().getEntity(s.fox) instanceof Fox f) f.setTarget(null);
        }), 5));

        // 2. an armed fox and a calm zombified piglin: it must not start a fight with the whole horde
        l.add(new Step(null, () -> UiTest.server(p -> {
            ServerLevel lv = p.serverLevel();
            ZombifiedPiglin z = EntityType.ZOMBIFIED_PIGLIN.create(lv);
            z.moveTo(p.getX() - 4, p.getY(), p.getZ(), 0, 0);
            z.setPersistenceRequired();
            z.setNoAi(true);
            lv.addFreshEntity(z);
            s.piglin = z.getUUID();
            if (lv.getEntity(s.fox) instanceof Fox f) { f.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)); f.setTarget(null); }
        }), 5));
        for (int i = 0; i < 10; i++) l.add(new Step(null, () -> UiTest.server(p -> {
            if (p.serverLevel().getEntity(s.fox) instanceof Fox f && f.getTarget() instanceof ZombifiedPiglin) s.piglinTargeted = true;
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("neutral mob: armed fox went for a calm zombified piglin=" + s.piglinTargeted + ok(!s.piglinTargeted));
            if (p.serverLevel().getEntity(s.piglin) instanceof ZombifiedPiglin z) z.discard();
            if (p.serverLevel().getEntity(s.fox) instanceof Fox f) { f.setTarget(null); f.teleportTo(p.getX() + 2, p.getY(), p.getZ()); }
        }), 10));

        // 3. opening the fox's bag while it's already open: the bag must still count as open
        l.add(new Step(null, () -> UiTest.server(p -> {
            FoxPets.openBag(p, s.fox);
            FoxPets.openBag(p, s.fox);
            boolean open = openBags().contains(s.fox);
            log("bag reopen: open screen=" + (p.containerMenu != p.inventoryMenu) + " marked open=" + open + ok(open));
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.closeContainer();
            log("bag closed: marked open=" + openBags().contains(s.fox) + ok(!openBags().contains(s.fox)));
        }), 10));

        // 4. a stale copy of the fox that loaded while its owner was away (owner tag put on after it joined)
        l.add(new Step(null, () -> UiTest.server(p -> {
            ServerLevel lv = p.serverLevel();
            if (!(lv.getEntity(s.fox) instanceof Fox f)) { log("stale copy: no fox FAIL"); return; }
            CompoundTag d = f.saveWithoutId(new CompoundTag());
            d.remove("UUID");
            CompoundTag forge = d.getCompound("ForgeData");
            UUID owner = forge.getUUID("xw_owner");
            int gen = forge.getInt("xw_gen");
            forge.remove("xw_owner");
            d.put("ForgeData", forge);
            Fox copy = EntityType.FOX.create(lv);
            copy.load(d);
            copy.moveTo(p.getX() - 2, p.getY(), p.getZ() + 2, 0, 0);
            lv.addFreshEntity(copy);
            copy.getPersistentData().putUUID("xw_owner", owner);
            copy.getPersistentData().putInt("xw_gen", gen);
            s.copy = copy.getUUID();
            log("stale copy: spawned, owned foxes now " + ownedFoxes(p) + " (records " + FoxPets.records(p).size() + ")");
        }), 80));
        l.add(new Step(null, () -> UiTest.server(p -> {
            boolean gone = !(p.serverLevel().getEntity(s.copy) instanceof Fox c) || !c.isAlive();
            log("stale copy: after 4 s gone=" + gone + " owned foxes=" + ownedFoxes(p) + ok(gone && ownedFoxes(p) == 1));
            if (p.serverLevel().getEntity(s.copy) instanceof Fox c) c.discard();
        }), 5));

        // 5. a sword handed over just now, then off to Recarnet and the whistle: the fox comes with its new sword, once.
        //    (the fox left behind in the overworld isn't in his world any more, so nothing re-snapshots it meanwhile)
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (p.serverLevel().getEntity(s.fox) instanceof Fox f) f.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            com.xenowarriors.recarnet.RecarnetEvents.travel(p);
            log("travel: now in " + p.level().dimension().location());
        }), 200));
        for (int i = 0; i < 20; i++) l.add(new Step(null, () -> {}, 30));     // (Recarnet's terrain loading)
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("recarnet loaded: client screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
            FoxPets.whistle(p);
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            Fox f = myFox(p);
            String held = f == null ? "-" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(f.getMainHandItem().getItem()).toString();
            log("whistle in Recarnet: fox here=" + (f != null) + " holds " + held + " owned foxes everywhere=" + ownedFoxes(p)
                    + ok(f != null && f.getMainHandItem().is(Items.DIAMOND_SWORD) && ownedFoxes(p) == 1));
            // the Recarnet villager swap: a villager turning up there becomes a person
            s.recarnetHumansBefore = p.serverLevel().getEntitiesOfClass(com.xenowarriors.npc.HumanNpcEntity.class, p.getBoundingBox().inflate(6)).size();
            Villager v = EntityType.VILLAGER.create(p.serverLevel());
            v.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0);
            p.serverLevel().addFreshEntity(v);
        }), 20));
        l.add(new Step("npchunt_recarnet_fox", () -> UiTest.server(p -> {
            int villagers = p.serverLevel().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(8)).size();
            int humans = p.serverLevel().getEntitiesOfClass(com.xenowarriors.npc.HumanNpcEntity.class, p.getBoundingBox().inflate(6)).size();
            log("recarnet villager swap: villagers=" + villagers + " people " + s.recarnetHumansBefore + " -> " + humans + ok(villagers == 0 && humans == s.recarnetHumansBefore + 1));
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> com.xenowarriors.recarnet.RecarnetEvents.travel(p)), 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            FoxPets.whistle(p);
            Fox f = myFox(p);
            s.fox = f == null ? s.fox : f.getUUID();
            log("whistle back home: fox here=" + (f != null) + " owned foxes everywhere=" + ownedFoxes(p) + ok(f != null && ownedFoxes(p) == 1));
        }), 20));

        // 6. forged packets: buying / claiming / skins without the right to
        long[] money = new long[1];
        int[] emeralds = new int[1];
        l.add(new Step(null, () -> UiTest.server(p -> {
            com.xenowarriors.economy.Money.set(p, 5000);
            p.getInventory().add(new ItemStack(Items.EMERALD, 64));
            money[0] = com.xenowarriors.economy.Money.get(p);
            emeralds[0] = com.xenowarriors.vehicle.Dealer.emeralds(p);
        }), 10));
        l.add(new Step(null, () -> {
            ModNetwork.sendToServer(new com.xenowarriors.network.GovShopPacket(0, 0, 1));            // no terminal near
            ModNetwork.sendToServer(new com.xenowarriors.network.DealerBuyPacket(0, 0));             // never opened a dealer
            ModNetwork.sendToServer(new com.xenowarriors.network.BountyClaimPacket(0));              // not done
            ModNetwork.sendToServer(new com.xenowarriors.network.BountyClaimPacket(99));
            ModNetwork.sendToServer(new com.xenowarriors.network.SetFoxSkinPacket(s.fox, 7));        // Fox King skin at level 1
            ModNetwork.sendToServer(new com.xenowarriors.network.PetActionPacket(0, 0, com.xenowarriors.pet.PetCare.SKIN, 6, ""));
            ModNetwork.sendToServer(new com.xenowarriors.network.RenameFoxPacket(s.fox, "§cA very very long fox name that goes on and on"));
        }, 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            long m = com.xenowarriors.economy.Money.get(p);
            int e = com.xenowarriors.vehicle.Dealer.emeralds(p);
            CompoundTag rec = FoxPets.records(p).getCompound(0);
            log("forged gov buy/dealer/bounty: money " + money[0] + " -> " + m + ", emeralds " + emeralds[0] + " -> " + e + ok(m == money[0] && e == emeralds[0]));
            log("forged locked skin: skin=" + rec.getInt("skin") + ok(rec.getInt("skin") == 0));
            log("forged rename: name='" + rec.getString("name") + "' len=" + rec.getString("name").length() + ok(rec.getString("name").length() <= 24 && !rec.getString("name").contains("§")));
        }), 5));

        // 7. a pretend phone call, hung up
        l.add(new Step(null, () -> UiTest.server(p -> p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.xenowarriors.item.ModItems.PHONE.get()))), 10));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.PhoneActionPacket(2, new UUID(0, 0))), 20));
        l.add(new Step(null, () -> UiTest.server(p -> log("phone: on a pretend call=" + com.xenowarriors.item.PhoneItem.onCall(p))), 5));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.PhoneActionPacket(1, new UUID(0, 0))), 20));
        l.add(new Step(null, () -> UiTest.server(p -> log("phone: hung up, still on call=" + com.xenowarriors.item.PhoneItem.onCall(p) + ok(!com.xenowarriors.item.PhoneItem.onCall(p)))), 5));
        // 8. Journey enemies (tagged xw_journey): killing Gov agents / SR members there doesn't move your real standing
        l.add(new Step(null, () -> UiTest.server(p -> {
            for (var f : com.xenowarriors.faction.Faction.values()) if (f != com.xenowarriors.faction.Faction.TOWN) com.xenowarriors.faction.Reputation.set(p, f, 0);
            ServerLevel lv = p.serverLevel();
            var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(lv);
            g.moveTo(p.getX() + 10, p.getY(), p.getZ() + 4, 0, 0);
            g.setup(true, 1, g.blockPosition());
            g.setNoAi(true);
            g.getPersistentData().putBoolean("xw_journey", true);
            lv.addFreshEntity(g);
            var sr = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(lv);
            sr.moveTo(p.getX() - 10, p.getY(), p.getZ() + 4, 0, 0);
            sr.setup(0, sr.blockPosition());
            sr.setNoAi(true);
            sr.getPersistentData().putBoolean("xw_journey", true);
            lv.addFreshEntity(sr);
            for (int i = 0; i < 6; i++) { g.hurt(p.damageSources().playerAttack(p), 100000F); sr.hurt(p.damageSources().playerAttack(p), 100000F); }
            log("journey foes: gov agent dead=" + !g.isAlive() + " sr member dead=" + !sr.isAlive());
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            int gov = com.xenowarriors.faction.Reputation.get(p, com.xenowarriors.faction.Faction.GOV);
            int srr = com.xenowarriors.faction.Reputation.get(p, com.xenowarriors.faction.Faction.SR);
            int sky = com.xenowarriors.faction.Reputation.get(p, com.xenowarriors.faction.Faction.SKY);
            // (the hit itself still counts as "caught" here: the test isn't inside a real Journey run, so -30 Gov from that is expected)
            log("journey kills standing: gov=" + gov + " sr=" + srr + " sky=" + sky + ok(srr == 0 && sky == 0 && gov == com.xenowarriors.faction.RepRules.GOV_CAUGHT.delta));
            for (var f : com.xenowarriors.faction.Faction.values()) if (f != com.xenowarriors.faction.Faction.TOWN) com.xenowarriors.faction.Reputation.set(p, f, 0);
            p.getPersistentData().remove("xw_gov_wanted_until");
        }), 5));

        // 9. the Pet Whistle item, as Mikiko, also calls back a winged companion that flew off ("calls all your pets")
        l.add(new Step(null, () -> UiTest.server(p -> {
            CompoundTag rec = new CompoundTag();
            rec.putUUID("uuid", UUID.randomUUID());
            rec.putString("name", "Buddy");
            rec.putString("type", "minecraft:chicken");
            rec.putInt("gen", 0);
            rec.putBoolean("dead", true);
            rec.putLong("awayUntil", 0);
            rec.putBoolean("readyTold", true);
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(c -> c.wingPets.add(rec));
            ItemStack w = new ItemStack(com.xenowarriors.item.ModItems.FOX_WHISTLE.get());
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, w);
            w.getItem().use(p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
        }), 20));
        l.add(new Step("npchunt_whistle", () -> UiTest.server(p -> {
            int n = p.serverLevel().getEntitiesOfClass(Chicken.class, p.getBoundingBox().inflate(16), c -> c.isAlive() && com.xenowarriors.wing.WingPets.isOwnedBy(c, p)).size();
            int foxes = ownedFoxes(p);
            log("pet whistle item as Mikiko: winged companions back=" + n + " foxes=" + foxes + ok(n == 1 && foxes == 1));
        }), 5));
        // 11. Money for kills: a town fighter busy with a zombie isn't an enemy (no pay); a raider is (pay). As Rhalix (Mikiko can't hurt townsfolk)
        long[] m0 = new long[1];
        l.add(new Step(null, () -> UiTest.character(CharacterType.RHALIX), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            ServerLevel lv = p.serverLevel();
            m0[0] = com.xenowarriors.economy.Money.get(p);
            var z = EntityType.ZOMBIE.create(lv);
            z.moveTo(p.getX() + 8, p.getY(), p.getZ() - 6, 0, 0);
            z.setNoAi(true);
            lv.addFreshEntity(z);
            var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(lv);
            h.moveTo(p.getX() + 6, p.getY(), p.getZ() - 6, 0, 0);
            h.setNoAi(true);
            lv.addFreshEntity(h);
            h.setTarget(z);
            h.setAggressive(true);
            h.hurt(p.damageSources().playerAttack(p), 100000F);
            long paidTown = com.xenowarriors.economy.Money.get(p) - m0[0];
            var r = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(lv);
            r.moveTo(p.getX() + 6, p.getY(), p.getZ() - 8, 0, 0);
            r.setNoAi(true);
            lv.addFreshEntity(r);
            r.makeRaider(false);
            long before = com.xenowarriors.economy.Money.get(p);
            r.hurt(p.damageSources().playerAttack(p), 100000F);
            long paidRaider = com.xenowarriors.economy.Money.get(p) - before;
            z.discard();
            log("kill money: town fighter (busy with a zombie) dead=" + !h.isAlive() + " paid=" + paidTown + ", raider dead=" + !r.isAlive() + " paid=" + paidRaider
                    + ok(!h.isAlive() && paidTown == 0 && paidRaider > 0));
            com.xenowarriors.faction.Reputation.set(p, com.xenowarriors.faction.Faction.TOWN, 0);
        }), 10));
        // 12. the fox falls while its owner is in another dimension: it still "ran away" (cool-down before the whistle works)
        l.add(new Step(null, () -> UiTest.server(p -> {
            ServerLevel ow = p.serverLevel();
            Fox f = myFox(p);
            if (f == null) { log("away death: no fox FAIL"); return; }
            double x = p.getX(), y = p.getY(), z = p.getZ();
            ServerLevel nether = p.server.getLevel(net.minecraft.world.level.Level.NETHER);
            p.teleportTo(nether, 0.5, 200, 0.5, 0, 0);
            f.hurt(ow.damageSources().generic(), 100000F);
            CompoundTag rec = FoxPets.records(p).getCompound(0);
            boolean dead = rec.getBoolean("dead");
            p.teleportTo(ow, x, y, z, 0, 0);
            FoxPets.whistle(p);
            int back = ownedFoxes(p);
            log("away death: fox died=" + !f.isAlive() + " marked ran away=" + dead + " whistle right after brought back=" + back + ok(!f.isAlive() && dead && back == 0));
        }), 10));
        l.add(new Step(null, () -> log("done"), 5));
    }
}

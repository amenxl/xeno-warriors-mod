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
            z.moveTo(p.getX() - 6, p.getY(), p.getZ(), 0, 0);
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
        }), 5));
        l.add(new Step(null, () -> UiTest.server(p -> {
            boolean gone = !(p.serverLevel().getEntity(s.copy) instanceof Fox c) || !c.isAlive();
            log("stale copy: after 3 s gone=" + gone + " owned foxes=" + ownedFoxes(p) + ok(gone && ownedFoxes(p) == 1));
            if (p.serverLevel().getEntity(s.copy) instanceof Fox c) c.discard();
        }), 60));

        // 5. a sword handed over just now, then off to Recarnet and the whistle: the fox comes with its new sword, once
        l.add(new Step(null, () -> UiTest.server(p -> {
            if (p.serverLevel().getEntity(s.fox) instanceof Fox f) f.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            com.xenowarriors.recarnet.RecarnetEvents.travel(p);        // (right away: no 5-second snapshot in between)
            log("travel: now in " + p.level().dimension().location());
            FoxPets.whistle(p);
            Fox f = myFox(p);
            String held = f == null ? "-" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(f.getMainHandItem().getItem()).toString();
            log("whistle in Recarnet: fox here=" + (f != null) + " holds " + held + " owned foxes everywhere=" + ownedFoxes(p)
                    + ok(f != null && f.getMainHandItem().is(Items.DIAMOND_SWORD) && ownedFoxes(p) == 1));
            // the Recarnet villager swap: a villager turning up there becomes a person
            s.recarnetHumansBefore = p.serverLevel().getEntitiesOfClass(com.xenowarriors.npc.HumanNpcEntity.class, p.getBoundingBox().inflate(6)).size();
            Villager v = EntityType.VILLAGER.create(p.serverLevel());
            v.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0);
            p.serverLevel().addFreshEntity(v);
        }), 10));
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
        l.add(new Step(null, () -> log("done"), 5));
    }
}

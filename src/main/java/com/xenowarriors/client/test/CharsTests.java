package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * UI tests for the chars5 round (focus "chars5", "efchars5", "taczchars5"; extra words pick parts: fall, torch, wings,
 * burger, cure, ride, soul, dance, idle, ef, gun - none = all). Results: "XW-TEST chars ..." lines.
 */
public final class CharsTests {
    private CharsTests() {}

    public static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST chars " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST chars", t); } });
    }

    private static void character(MikikoTests.Steps l, CharacterType t) {
        l.add(null, () -> server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t)), 40);
    }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t, double dx, double dz) {
        PartyBotEntity b = null;
        for (var x : PartyBots.bots(p)) if (x.character() == t) b = x;
        if (b == null) b = PartyBots.spawn(p, t, null);
        if (b == null) { log("spawn " + t.getId() + " FAILED"); return null; }
        b.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180F, 0F);
        b.setTarget(null);
        b.setMode(PartyBotEntity.NEUTRAL);
        return b;
    }

    private static void clearBots(ServerPlayer p) {
        for (var b : new java.util.ArrayList<>(PartyBots.bots(p))) b.discard();
    }

    private static void hold(ServerPlayer p, ItemStack s) {
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, s);
    }

    public static void run(Minecraft mc, String focus, MikikoTests.Steps l) {
        String f = focus.replaceFirst("^(ef|tacz)", "").replaceFirst("^chars5", "");
        boolean all = f.isEmpty() || f.contains("all");
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        if (all || f.contains("dance")) dance(mc, l);
        if (all || f.contains("idle")) idle(mc, l);
        if (focus.startsWith("ef")) ef(mc, l);
        if (focus.contains("tacz")) gun(mc, l);
        if (all || f.contains("fall")) fall(mc, l);
        if (all || f.contains("torch")) torch(mc, l);
        if (all || f.contains("wings")) wings(mc, l);
        if (all || f.contains("burger")) burger(mc, l);
        if (all || f.contains("cure")) cure(mc, l);
        if (all || f.contains("ride")) ride(mc, l);
        if (all || f.contains("soul")) soul(mc, l);
        l.add(null, () -> log("done"), 5);
    }

    // ------------------------------------------------------------------ fall damage

    private static void fall(Minecraft mc, MikikoTests.Steps l) {
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.MIKIKO, CharacterType.GARRY, CharacterType.NONE}) {
            if (t == CharacterType.NONE) l.add(null, () -> server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.NONE)), 30);
            else character(l, t);
            float[] hp = new float[1];
            l.add(null, () -> server(p -> {
                p.setGameMode(GameType.SURVIVAL);
                com.xenowarriors.character.RhalixWings.class.getName();
                p.getAbilities().flying = false; p.getAbilities().mayfly = false; p.onUpdateAbilities();
                p.setHealth(p.getMaxHealth());
                hp[0] = p.getHealth();
                p.teleportTo(p.getX(), p.getY() + 14, p.getZ());
                p.setDeltaMovement(0, 0, 0);
            }), 60);
            l.add(null, () -> server(p -> log("fall " + (t == CharacterType.NONE ? "plain" : t.getId()) + " 14 blocks: hp " + hp[0] + " -> " + p.getHealth()
                    + " (lost " + (hp[0] - p.getHealth()) + ", multiplier " + com.xenowarriors.character.FallDamage.multiplier(p) + ")")), 5);
        }
        l.add(null, () -> server(p -> p.setGameMode(GameType.CREATIVE)), 5);
    }

    // ------------------------------------------------------------------ torch

    private static void torch(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            hold(p, new ItemStack(Items.STICK, 5));
            boolean lit = com.xenowarriors.character.FireTorch.light(p, InteractionHand.MAIN_HAND);
            log("torch mikiko lit=" + lit + " sticks=" + p.getMainHandItem().getCount() + " torches=" + p.getInventory().countItem(Items.TORCH));
            p.getInventory().clearContent();
        }), 10);
        character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> {
            hold(p, new ItemStack(Items.STICK, 5));
            boolean lit = com.xenowarriors.character.FireTorch.light(p, InteractionHand.MAIN_HAND);
            log("torch rhalix(no fire) lit=" + lit + " torches=" + p.getInventory().countItem(Items.TORCH));
            com.xenowarriors.ka.Elements.give(p, com.xenowarriors.ka.Element.FIRE);
            lit = com.xenowarriors.character.FireTorch.light(p, InteractionHand.MAIN_HAND);
            log("torch rhalix(given fire) lit=" + lit + " torches=" + p.getInventory().countItem(Items.TORCH));
            com.xenowarriors.ka.Elements.give(p, null);
            p.getInventory().clearContent();
        }), 10);
    }

    // ------------------------------------------------------------------ Skylander wings

    private static void wings(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            var l2 = p.serverLevel();
            int dropped = 0, n = 40;
            for (int i = 0; i < n; i++) {
                var s = com.xenowarriors.npc.ModNpcs.SKYLANDER.get().create(l2);
                if (s == null) continue;
                s.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0);
                l2.addFreshEntity(s);
                if (s.wingless()) { s.discard(); n--; continue; }
                s.hurt(p.damageSources().playerAttack(p), 100000F);
            }
            l2.getServer().execute(() -> {});
            for (var e : l2.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(12)))
                if (e.getItem().is(com.xenowarriors.item.ModItems.SKYLANDER_WINGS.get())) { dropped += e.getItem().getCount(); e.discard(); }
            log("wings dropped " + dropped + " from " + n + " winged Skylanders (25% each)");
            int[] before = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> new int[]{d.statLevel[0], d.statLevel[1], d.statLevel[2], d.miningLevel, d.craftLevel, d.smeltLevel}).orElse(new int[0]);
            ItemStack w = new ItemStack(com.xenowarriors.item.ModItems.SKYLANDER_WINGS.get());
            w.getItem().finishUsingItem(w, p.level(), p);
            int[] after = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> new int[]{d.statLevel[0], d.statLevel[1], d.statLevel[2], d.miningLevel, d.craftLevel, d.smeltLevel}).orElse(new int[0]);
            log("wings mikiko stats speed/attack/defense/mining/craft/smelt " + java.util.Arrays.toString(before) + " -> " + java.util.Arrays.toString(after));
            hold(p, new ItemStack(com.xenowarriors.item.ModItems.SKYLANDER_WINGS.get()));
            var use = p.getMainHandItem().getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            log("wings mikiko can eat=" + use.getResult().consumesAction());
            p.stopUsingItem();
            p.getInventory().clearContent();
        }), 20);
        character(l, CharacterType.GARRY);
        l.add(null, () -> server(p -> {
            hold(p, new ItemStack(com.xenowarriors.item.ModItems.SKYLANDER_WINGS.get()));
            var use = p.getMainHandItem().getItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            log("wings garry can eat=" + use.getResult().consumesAction());
            p.getInventory().clearContent();
        }), 10);
    }

    // ------------------------------------------------------------------ hamburgers

    private static void burger(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.YURI);
        l.add(null, () -> server(p -> {
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(Items.BREAD, 2));
            p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 1));
            int made = com.xenowarriors.item.HamburgerItem.cook(p.getInventory());
            log("burger yuri cooked " + made + " (bread left " + p.getInventory().countItem(Items.BREAD) + ", beef left " + p.getInventory().countItem(Items.COOKED_BEEF) + ")");
            var r = p.server.getRecipeManager().byKey(XenoWarriors.id("hamburger"));
            log("burger recipe present=" + r.isPresent());
            var tag = net.minecraftforge.registries.ForgeRegistries.ITEMS.tags().getTag(com.xenowarriors.item.HamburgerItem.INGREDIENTS);
            log("burger ingredient tag: " + tag.stream().map(i -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i).toString()).toList());
            p.getInventory().clearContent();
        }), 10);
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.GARRY}) {
            character(l, t);
            l.add(null, () -> server(p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.removeAllEffects();
                p.setHealth(p.getMaxHealth() * 0.2F);
                float before = p.getHealth();
                ItemStack b = new ItemStack(com.xenowarriors.item.ModItems.HAMBURGER.get(), 2);
                hold(p, b);
                b.getItem().finishUsingItem(b, p.level(), p);
                log("burger " + t.getId() + " hp " + before + " -> " + p.getHealth() + " of " + p.getMaxHealth() + " regen=" + p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION)
                        + " onCooldown=" + p.getCooldowns().isOnCooldown(b.getItem()) + " cooldownTicks=" + com.xenowarriors.item.HamburgerItem.cooldown(t));
            }), 70);
            l.add(null, () -> server(p -> { log("burger " + t.getId() + " after 3.5s onCooldown=" + p.getCooldowns().isOnCooldown(com.xenowarriors.item.ModItems.HAMBURGER.get())); }), 40);
            l.add(null, () -> server(p -> { log("burger " + t.getId() + " after 5.5s onCooldown=" + p.getCooldowns().isOnCooldown(com.xenowarriors.item.ModItems.HAMBURGER.get())); p.getInventory().clearContent(); p.setGameMode(GameType.CREATIVE); }), 5);
        }
        l.add(null, () -> server(p -> { p.getInventory().add(new ItemStack(com.xenowarriors.item.ModItems.HAMBURGER.get(), 3)); p.getInventory().add(new ItemStack(com.xenowarriors.item.ModItems.SKYLANDER_WINGS.get(), 2)); }), 10);
        l.add(null, () -> { mc.options.hideGui = false; mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); }, 20);
        l.add("chars_items_inventory", () -> {}, 5);
        l.add(null, () -> { mc.setScreen(null); server(p -> p.getInventory().clearContent()); }, 5);
    }

    // ------------------------------------------------------------------ Aurora virus cure

    private static void cure(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.YURI);
        l.add(null, () -> server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, Math.max(2, com.xenowarriors.character.Seasons.highest(p.server)));
            p.setGameMode(GameType.SURVIVAL);
            clearBots(p);
            var g = bot(p, CharacterType.GARRY, 2, 0);
            if (g == null) return;
            boolean inf = com.xenowarriors.virus.AuroraVirus.infect(g);
            log("cure garry bot infected=" + inf + " has=" + com.xenowarriors.virus.AuroraVirus.has(g));
            boolean ok = com.xenowarriors.virus.VirusCure.cure(p, g);
            log("cure yuri player cured bot=" + ok + " bot has virus=" + com.xenowarriors.virus.AuroraVirus.has(g));
            g.discard();
        }), 30);
        character(l, CharacterType.GARRY);
        l.add(null, () -> server(p -> {
            clearBots(p);
            bot(p, CharacterType.TECHNICA, 2, 1);
            p.getPersistentData().remove("xw_virus_immune");
            p.removeAllEffects();
            boolean inf = com.xenowarriors.virus.AuroraVirus.infect(p);
            log("cure garry player infected=" + inf);
        }), 100);
        l.add(null, () -> server(p -> {
            log("cure technica bot auto-cure: player has virus=" + com.xenowarriors.virus.AuroraVirus.has(p));
            clearBots(p);
            p.setGameMode(GameType.CREATIVE);
        }), 10);
    }

    // ------------------------------------------------------------------ Mikiko's Hekublade ride (third jump)

    private static void ride(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        int[][] cases = {{40, 30}, {10, 30}, {40, 160}};      // {blade level, character level}
        for (int[] c : cases) {
            l.add(null, () -> server(p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeLevel = c[0]; for (int i = 0; i < 3; i++) d.statLevel[i] = c[1] / 3; });
                com.xenowarriors.stats.Stats.reapply(p);
                p.getAbilities().mayfly = com.xenowarriors.mikiko.WindFlight.canFly(p); p.getAbilities().flying = false; p.onUpdateAbilities();
                p.teleportTo(p.getX(), p.getY() + 3, p.getZ());
            }), 3);
            l.add(null, () -> server(p -> {
                boolean rode = com.xenowarriors.mikiko.BladeRideJump.ride(p);
                log("ride blade " + c[0] + " charLevel " + com.xenowarriors.stats.Stats.characterLevel(p) + " (flight at " + com.xenowarriors.mikiko.WindFlight.LEVEL
                        + ", canFly=" + com.xenowarriors.mikiko.WindFlight.canFly(p) + "): rode=" + rode + " vehicle=" + (p.getVehicle() == null ? "none" : p.getVehicle().getType().toShortString()));
            }), 25);
            if (c == cases[0]) {
                l.add(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 3);
                l.add("chars_blade_ride", () -> {}, 5);
            }
            l.add(null, () -> server(p -> { p.stopRiding(); p.setGameMode(GameType.CREATIVE); }), 20);
        }
        l.add(null, () -> server(p -> { p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.bladeLevel = 1; for (int i = 0; i < 3; i++) d.statLevel[i] = 0; }); com.xenowarriors.stats.Stats.reapply(p); }), 5);
    }

    // ------------------------------------------------------------------ Soul Splitter skills

    private static void soul(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        int[] before = new int[1];
        l.add(null, () -> server(p -> {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 600, 4));   // (the owner just watches)
            p.serverLevel().setDayTime(18000);                                  // (no burning zombies)
            com.xenowarriors.karma.Karma.add(p, 100F);
            com.xenowarriors.soul.SoulSplit.setLevel(p, 30);
            before[0] = com.xenowarriors.soul.SoulSkills.casts;
            boolean started = com.xenowarriors.soul.SoulSplit.start(p);
            for (int i = 0; i < 3; i++) {
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                if (z == null) continue;
                z.moveTo(p.getX() + 5 + i, p.getY(), p.getZ() + 4, 0, 0);
                var hp = z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(400);
                z.setHealth(400);
                z.setTarget(p);
                p.serverLevel().addFreshEntity(z);
            }
            log("soul split started=" + started + " level=" + com.xenowarriors.soul.SoulSkills.abilityLevel(p, "soul_splitter"));
        }), 300);
        l.add(null, () -> server(p -> {
            var souls = p.serverLevel().getEntitiesOfClass(com.xenowarriors.soul.SoulEntity.class, p.getBoundingBox().inflate(40));
            log("soul skill casts in 15s: " + (com.xenowarriors.soul.SoulSkills.casts - before[0]) + " last=" + com.xenowarriors.soul.SoulSkills.lastSkill
                    + " owner hp=" + p.getHealth() + "/" + p.getMaxHealth() + " souls=" + souls.size()
                    + (souls.isEmpty() ? "" : " target=" + souls.get(0).getTarget()));
            p.serverLevel().setDayTime(6000);
            if (com.xenowarriors.soul.SoulSplit.active(p)) com.xenowarriors.soul.SoulSplit.end(p, null);
            for (var z : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(30))) z.discard();
            p.setGameMode(GameType.CREATIVE);
        }), 10);
    }

    // ------------------------------------------------------------------ animations: Mikiko dancing to Garry

    private static void front(Minecraft mc, float yaw) {
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.player.setXRot(8F); mc.player.setYRot(yaw); mc.player.yBodyRot = yaw; mc.player.setYHeadRot(yaw);
    }

    private static void dance(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> { clearBots(p); hold(p, ItemStack.EMPTY); p.setYRot(0F); bot(p, CharacterType.GARRY, 1.6, 1.2); }), 20);
        l.add(null, () -> front(mc, 0F), 230);
        for (int i = 0; i < 6; i++) l.add(String.format("chars_dance_%02d", i), () -> front(mc, 0F), 7);
        l.add(null, () -> log("dance mikiko sit=" + com.xenowarriors.client.mikiko.MikikoAnimations.sitWeight(mc.player) + " holder=" + com.xenowarriors.client.Idles.holder(mc.player)), 640);
        l.add("chars_dance_later", () -> {
            front(mc, 0F);
            log("dance mikiko after 32s more: sit=" + com.xenowarriors.client.mikiko.MikikoAnimations.sitWeight(mc.player) + " holder=" + com.xenowarriors.client.Idles.holder(mc.player));
        }, 5);
        l.add(null, () -> server(CharsTests::clearBots), 10);
    }

    /** Overlapping idles: Mikiko standing in the rain long enough to sit down - only one plays at a time. */
    private static void idle(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> { clearBots(p); hold(p, ItemStack.EMPTY); p.serverLevel().setWeatherParameters(0, 6000, true, false); }), 20);
        int[] overlaps = new int[2];
        for (int i = 0; i < 70; i++) {
            final int k = i;
            l.add(k % 14 == 13 ? String.format("chars_idle_%02d", k / 14) : null, () -> {
                front(mc, 0F);
                boolean rain = com.xenowarriors.client.RainIdle.active(mc.player);
                float sit = com.xenowarriors.client.mikiko.MikikoAnimations.sitWeight(mc.player);
                if (rain && sit > 0.05F) overlaps[0]++;
                overlaps[1]++;
                if (k % 7 == 0) log("idle t=" + k * 10 + " rain=" + rain + " sit=" + sit + " holder=" + com.xenowarriors.client.Idles.holder(mc.player));
            }, 10);
        }
        l.add(null, () -> { log("idle overlap samples (rain idle while sitting): " + overlaps[0] + "/" + overlaps[1]); server(p -> p.serverLevel().setWeatherParameters(6000, 0, false, false)); }, 10);
    }

    // ------------------------------------------------------------------ Epic Fight guard: our stance instead

    private static void ef(Minecraft mc, MikikoTests.Steps l) {
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX}) {
            character(l, t);
            l.add(null, () -> { server(p -> hold(p, ItemStack.EMPTY)); com.xenowarriors.compat.epicfight.EfTest.play(mc.player, "none"); }, 20);
            l.add(null, () -> { front(mc, 0F); com.xenowarriors.compat.epicfight.EfTest.play(mc.player, "guard"); }, 6);
            l.add("chars_ef_guard_" + t.getId(), () -> { front(mc, 0F); log("ef " + t.getId() + " guard: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)); }, 3);
            l.add(null, () -> com.xenowarriors.compat.epicfight.EfTest.play(mc.player, "none"), 30);
            l.add("chars_ef_idle_" + t.getId(), () -> { front(mc, 0F); log("ef " + t.getId() + " idle: " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player)); }, 3);
        }
    }

    // ------------------------------------------------------------------ TACZ gun level

    private static void gun(Minecraft mc, MikikoTests.Steps l) {
        for (CharacterType t : new CharacterType[]{CharacterType.TECHNICA, CharacterType.GARRY}) {
            character(l, t);
            l.add(null, () -> server(p -> {
                log("gun " + t.getId() + " tacz=" + com.xenowarriors.compat.GunLevel.on() + " level=" + com.xenowarriors.compat.GunLevel.level(p)
                        + " damage x" + com.xenowarriors.compat.GunLevel.multiplier(p));
                com.xenowarriors.compat.GunLevel.addXp(p, 500F);
                log("gun " + t.getId() + " after 500 xp level=" + com.xenowarriors.compat.GunLevel.level(p) + " damage x" + com.xenowarriors.compat.GunLevel.multiplier(p));
            }), 10);
        }
        character(l, CharacterType.TECHNICA);
        l.add(null, () -> server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            ItemStack g = com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30);
            hold(p, g);
            log("gun stack=" + g + " ammo=" + com.xenowarriors.compat.TaczGuns.ammo(g));
        }), 10);
        l.add(null, () -> server(p -> { com.xenowarriors.compat.TaczGuns.setAmmo(p.getMainHandItem(), 4); log("gun level " + com.xenowarriors.compat.GunLevel.level(p) + ", emptied to " + com.xenowarriors.compat.TaczGuns.ammo(p.getMainHandItem())); }), 10);
        l.add(null, () -> server(p -> { log("gun below 500 ammo after 0.5s=" + com.xenowarriors.compat.TaczGuns.ammo(p.getMainHandItem())); com.xenowarriors.compat.GunLevel.setLevel(p, 500); }), 10);
        l.add(null, () -> server(p -> log("gun at 500 ammo after 0.5s=" + com.xenowarriors.compat.TaczGuns.ammo(p.getMainHandItem()))), 10);
        l.add(null, () -> { mc.options.hideGui = false; com.xenowarriors.client.PlayerStatsScreen.openTab(null, 1); }, 30);
        l.add("chars_gun_stats", () -> log("gun client level shown=" + com.xenowarriors.network.GunLevelPacket.clientLevel), 5);
        l.add(null, () -> { mc.setScreen(null); server(p -> { p.getInventory().clearContent(); p.setGameMode(GameType.CREATIVE); }); }, 5);
    }

    /** (unused) */
    static boolean isMikiko(Player p) { return false; }
}

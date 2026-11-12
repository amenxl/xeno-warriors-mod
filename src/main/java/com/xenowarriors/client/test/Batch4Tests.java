package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.compat.DmzPerCharacter;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;

/**
 * UI tests for batch 4 (focus "batch4..." / "dmzbatch4..." with DragonMineZ; words after "batch4" pick parts):
 * "formlook" (DragonMineZ: Mikiko and Rhalix's form faces, SSJ4 hair and top), "comine" (a bot helping you dig),
 * "botforms" (DragonMineZ: a bot learning its forms with TP), "ultforms" (Kill / Lexy: transformations on the key,
 * new ultimates). Every result is an "XW-TEST batch4 ..." line.
 */
public final class Batch4Tests {
    private Batch4Tests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").replaceFirst("^ef", "").startsWith("batch4"); }

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST batch4 " + s); }

    static void server(java.util.function.Consumer<ServerPlayer> f) {
        Batch3Tests.server(p -> { try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST batch4", t); } });
    }

    public static void run(Minecraft mc, String focus, MikikoTests.Steps l) {
        String f = focus.replaceFirst("^dmz", "").replaceFirst("^ef", "").replaceFirst("^batch4", "");
        boolean ef = focus.startsWith("ef");
        boolean dmz = focus.startsWith("dmz");
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        l.add(null, () -> server(p -> {
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.serverLevel().setDayTime(6000);
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            Batch3Tests.clearBots(p);
        }), 40);
        if (dmz && (f.isEmpty() || f.contains("formlook") || f.contains("ssj4fur"))) formLook(mc, l, f.contains("ssj4fur"));
        if (f.isEmpty() || f.contains("comine")) coMine(mc, l);
        if (dmz && (f.isEmpty() || f.contains("botforms"))) botForms(mc, l);
        if (f.isEmpty() || f.contains("ultforms")) ultForms(mc, l);
        if (f.isEmpty() || f.contains("kaioken")) kaioken(mc, l);
        if (dmz && (f.isEmpty() || f.contains("botweights"))) botWeights(mc, l);
        if (f.isEmpty() || f.contains("botai")) botAi(mc, l);
        if (f.isEmpty() || f.contains("pickup")) pickupMusic(mc, l);
        if (f.isEmpty() || f.contains("partytime")) partyTime(mc, l);
        if (f.isEmpty() || f.contains("creeper")) creepers(mc, l);
        if (f.isEmpty() || f.contains("ender")) enderSaiyan(mc, l);
        if (dmz && (f.isEmpty() || f.contains("bagmerge"))) bagMerge(mc, l);
        if (dmz && (f.isEmpty() || f.contains("oldkai"))) oldKai(mc, l);
        if (f.isEmpty() || f.contains("sparlock")) sparLock(mc, l);
        if (f.contains("fusionlook")) fusionLook(mc, l);
        if (dmz && (f.isEmpty() || f.contains("nimbus"))) nimbus(mc, l);
        if (f.isEmpty() || f.contains("bike")) technicaBike(mc, l);
        if (ef && f.contains("efgear")) efGear(mc, l);
        if (f.isEmpty() || f.contains("hekuteam")) hekuTeam(mc, l);
        if (f.isEmpty() || f.contains("fishing")) fishing(mc, l);
        if (f.isEmpty() || f.contains("taming")) taming(mc, l);
        if (f.isEmpty() || f.contains("outfits")) outfits(mc, l);
        if (dmz && (f.isEmpty() || f.contains("automission"))) autoMission(mc, l);                      // (only when asked: design previews)
        if (dmz && (f.isEmpty() || f.contains("dmzaura"))) dmzAura(mc, l);
        if (f.isEmpty() || f.contains("teamspar")) teamSpar(mc, l);
        if (f.isEmpty() || f.contains("botoutfit")) botOutfit(mc, l);
        if (f.isEmpty() || f.contains("phonecall")) phoneCall(mc, l);
        if (f.isEmpty() || f.contains("offduty")) offDutyCards(mc, l);
        if (f.isEmpty() || f.contains("wirehit")) wireHit(mc, l);
        if (f.isEmpty() || f.contains("regen")) regen(mc, l);
        if (f.isEmpty() || f.contains("picnic")) picnic(mc, l);
        if (f.isEmpty() || f.contains("autodecline")) autoDecline(mc, l);
        if (f.isEmpty() || f.contains("switchq")) switchQueue(mc, l);
        if (f.isEmpty() || f.contains("toggles")) toggles(mc, l);
        if (f.isEmpty() || f.contains("rlclick")) rlClick(mc, l);
        if (f.isEmpty() || f.contains("shiyo")) shiyoken(mc, l);
        if (f.isEmpty() || f.contains("dmzgun")) dmzGun(mc, l);
        if (dmz && (f.isEmpty() || f.contains("radar"))) radarCraft(mc, l);
        if (dmz && (f.isEmpty() || f.contains("yurissj"))) yuriForms(mc, l);
        if (f.isEmpty() || f.contains("hungerbar")) hungerBar(mc, l);
        if (f.isEmpty() || f.contains("botlife")) botLife(mc, l);
        if (f.isEmpty() || f.contains("teamjukebox")) teamJukebox(mc, l);
        if (f.isEmpty() || f.contains("boostpass")) boostPass(mc, l);
        if (f.isEmpty() || f.contains("techbike")) techBike(mc, l);
        if (f.isEmpty() || f.contains("rps")) rps(mc, l);
        if (f.isEmpty() || f.contains("towerpods")) towerPods(mc, l);
        if (f.isEmpty() || f.contains("xenoblasts")) xenoBlasts(mc, l);
        if (f.isEmpty() || f.contains("capture")) capture(mc, l);
        if (f.isEmpty() || f.contains("spread")) spread(mc, l);
        if (f.isEmpty() || f.contains("poseguard")) poseGuard(mc, l);
        if (f.isEmpty() || f.contains("c2")) c2Paths(mc, l);
        if (f.isEmpty() || f.contains("devil")) devil(mc, l);
        if (dmz && (f.isEmpty() || f.contains("namek"))) namekSeedling(mc, l);
        if (f.isEmpty() || f.contains("elementalist")) elementalist(mc, l);
        if (f.isEmpty() || f.contains("vessel")) vessel(mc, l);
        if (f.isEmpty() || f.contains("angel")) angel(mc, l);
        if (dmz && (f.isEmpty() || f.contains("mobgravity"))) mobGravity(mc, l);
        if (f.isEmpty() || f.contains("ownbot")) ownBot(mc, l);
        if (f.isEmpty() || f.contains("geoguard")) geoGuard(mc, l);
        if (f.isEmpty() || f.contains("rlgun")) rlGun(mc, l);
        if (f.isEmpty() || f.contains("boostbag")) boostBag(mc, l);
        if (dmz && (f.isEmpty() || f.contains("dbspeed"))) dbSpeed(mc, l);
        if (dmz && (f.isEmpty() || f.contains("kaibuilder"))) kaiBuilder(mc, l);
        if (dmz && (f.isEmpty() || f.contains("capsules"))) capsules(mc, l);
        if (f.isEmpty() || f.contains("rlall")) rlAll(mc, l);
        if (f.isEmpty() || f.contains("autopass")) autoPassives(mc, l);
        if (f.isEmpty() || f.contains("scent")) scent(mc, l);
        if (f.isEmpty() || f.contains("techsense")) techSense(mc, l);
        if (f.isEmpty() || f.contains("partymusic")) partyMusic(mc, l);
        if (f.isEmpty() || f.contains("headlight")) headlights(mc, l);
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); server(Batch3Tests::clearBots); log("done"); }, 5);
    }

    // ------------------------------------------------------------------ 2v2 spars; bot outfits; phoning busy teammates; off-duty cards; wires

    // ------------------------------------------------------------------ batch 5: regen, picnics, requests, switching, toggles, R+L click, looks

    private static void regen(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        float[] hp = new float[4];
        l.add(null, () -> server(p -> {
            p.getPersistentData().putInt("xw_food_total", 20); p.getPersistentData().putInt("xw_food_last", 19);   // (one full bar)
            p.getFoodData().setFoodLevel(19); p.getFoodData().setSaturation(0F);
            p.setHealth(4F); hp[0] = p.getHealth();
        }), 120);
        l.add(null, () -> server(p -> {
            hp[1] = p.getHealth();
            p.getPersistentData().putInt("xw_food_total", 100); p.getPersistentData().putInt("xw_food_last", 19);   // (five full bars)
            p.setHealth(4F); hp[2] = p.getHealth();
        }), 120);
        l.add(null, () -> server(p -> {
            hp[3] = p.getHealth();
            float one = hp[1] - hp[0], five = hp[3] - hp[2];
            log("regen: full base bar healed " + one + " in 6 s, five full bars " + five + " (intervals " + com.xenowarriors.character.BigHunger.regenInterval(1) + "/" + com.xenowarriors.character.BigHunger.regenInterval(5) + " ticks) -> "
                    + (one >= 2F && five >= one * 2.5F ? "PASS" : "FAIL"));
            p.setHealth(p.getMaxHealth());
        }), 120);
    }

    private static void picnic(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[2];
        float[] bond = new float[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            b[0] = botAt(p, CharacterType.RHALIX, 2, 3); b[1] = botAt(p, CharacterType.KYRIE, -2, 3);
            bond[0] = com.xenowarriors.party.Bonds.level(p, "rhalix") + com.xenowarriors.party.Bonds.progress(p, "rhalix");
            com.xenowarriors.bot.BotPicnic.ask(b[0], p);
        }), 20);
        l.add("batch4_picnic_ask", () -> {}, 5);
        l.add(null, () -> server(p -> p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenopicnic yes " + b[0].getId())), 10);
        l.add(null, () -> server(p -> {
            var basket = com.xenowarriors.bot.BotPicnic.basketForTest(p);
            int blocks = com.xenowarriors.bot.BotPicnic.blocksForTest(p);
            int food = 0;
            if (basket != null && p.level().getBlockEntity(basket) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity be)
                for (int i = 0; i < be.getContainerSize(); i++) food += be.getItem(i).getCount();
            log("picnic: on " + com.xenowarriors.bot.BotPicnic.active(p) + ", table blocks " + blocks + ", food in the basket " + food + " -> " + (com.xenowarriors.bot.BotPicnic.active(p) && blocks >= 9 && food > 20 ? "PASS" : "FAIL"));
            p.setHealth(10F);
            p.getFoodData().setFoodLevel(10);
            ItemStack bread = new ItemStack(net.minecraft.world.item.Items.BREAD);
            net.minecraftforge.event.ForgeEventFactory.onItemUseFinish(p, bread, 0, bread.copy());
            log("picnic: eating at the picnic heals - regeneration " + p.hasEffect(MobEffects.REGENERATION) + " -> " + (p.hasEffect(MobEffects.REGENERATION) ? "PASS" : "FAIL"));
        }), 60);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 30, 30), 20);
        l.add("batch4_picnic_table", () -> {}, 5);
        l.add(null, () -> server(p -> {
            var basket = com.xenowarriors.bot.BotPicnic.basketForTest(p);
            com.xenowarriors.bot.BotPicnic.endForTest(p);
            boolean gone = basket != null && p.level().getBlockState(basket).isAir();
            float got = com.xenowarriors.party.Bonds.level(p, "rhalix") + com.xenowarriors.party.Bonds.progress(p, "rhalix") - bond[0];
            log("picnic: over -> packed away " + gone + ", bond with Rhalix +" + got + " -> " + (gone && !com.xenowarriors.bot.BotPicnic.active(p) && (got > 0 || com.xenowarriors.party.Bonds.level(p, "rhalix") > 0) ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 10);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void autoDecline(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[1];
        int[] before = new int[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            b[0] = botAt(p, CharacterType.RHALIX, 2, 3);
            before[0] = com.xenowarriors.bot.BotRequests.autoDeclined;
            com.xenowarriors.bot.BotSpar.ask(b[0], p);
        }), 20);
        l.add(null, () -> server(p -> {
            boolean waiting = com.xenowarriors.bot.BotRequests.waiting(p);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0); z.setNoAi(true); p.serverLevel().addFreshEntity(z);
            z.setTarget(p);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(z), 1F);
            boolean declined = com.xenowarriors.bot.BotRequests.autoDeclined > before[0] && !com.xenowarriors.bot.BotRequests.waiting(p);
            log("autodecline: spar card waiting " + waiting + ", a zombie hits you -> called off " + declined + " -> " + (waiting && declined ? "PASS" : "FAIL"));
            z.discard();
            Batch3Tests.clearBots(p);
        }), 10);
    }

    private static void switchQueue(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            long now = p.server.overworld().getGameTime(), m = com.xenowarriors.bot.OffDuty.MINUTE;
            com.xenowarriors.party.Bonds.set(p, "kyrie", 10, 0); com.xenowarriors.party.Bonds.set(p, "max", 70, 0); com.xenowarriors.party.Bonds.set(p, "sam", 70, 0);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.KYRIE, com.xenowarriors.bot.OffDuty.Act.TRAINING, now + 3 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.MAX, com.xenowarriors.bot.OffDuty.Act.MINING, now + 9 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.SAM, com.xenowarriors.bot.OffDuty.Act.DRAGON_BALLS, now + 9 * m, null);
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xeno choose");
        }), 30);
        l.add(null, () -> { if (!(mc.screen instanceof com.xenowarriors.client.screen.CharacterSelectScreen)) mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen());
            com.xenowarriors.client.screen.CharacterSelectScreen.focusForTest(CharacterType.KYRIE); mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 20);
        l.add("batch4_switch_select", () -> {}, 5);
        l.add(null, () -> { com.xenowarriors.client.screen.CharacterSelectScreen.focusForTest(null); mc.setScreen(null); server(p -> {
            com.xenowarriors.network.SelectCharacterPacket.select(p, CharacterType.SAM);       // (bond 70, but hunting Dragon Balls)
            boolean samQueued = com.xenowarriors.bot.SwitchQueue.queued(p) == CharacterType.SAM && com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.MIKIKO;
            com.xenowarriors.network.SelectCharacterPacket.select(p, CharacterType.KYRIE);     // (bond 10: queued)
            boolean kyrieQueued = com.xenowarriors.bot.SwitchQueue.queued(p) == CharacterType.KYRIE && com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.MIKIKO;
            log("switchq: Sam on a Dragon Ball hunt (bond 70) queued " + samQueued + ", Kyrie busy (bond 10) queued " + kyrieQueued + " -> " + (samQueued && kyrieQueued ? "PASS" : "FAIL"));
            com.xenowarriors.bot.SwitchQueue.clear(p);
            com.xenowarriors.network.SelectCharacterPacket.select(p, CharacterType.MAX);       // (bond 70, mining: drops it)
            boolean maxNow = com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.MAX && com.xenowarriors.bot.OffDuty.act(p, CharacterType.MAX) == com.xenowarriors.bot.OffDuty.Act.NONE;
            log("switchq: Max busy mining with bond 70 -> switched now " + maxNow + " -> " + (maxNow ? "PASS" : "FAIL"));
            com.xenowarriors.network.SelectCharacterPacket.select(p, CharacterType.KYRIE);     // queue Kyrie again, then she finishes
            long now = p.server.overworld().getGameTime();
            com.xenowarriors.bot.OffDuty.simulate(p, CharacterType.KYRIE, 5, now, p.getRandom());
        }); }, 40);
        l.add(null, () -> server(p -> {
            boolean switched = com.xenowarriors.character.ModCapabilities.getType(p) == CharacterType.KYRIE;
            log("switchq: Kyrie done -> switched to her by the queue " + switched + " -> " + (switched ? "PASS" : "FAIL"));
        }), 5);
    }

    private static void toggles(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); com.xenowarriors.karma.Karma.add(p, 300); }), 10);
        l.add(null, () -> { ToggleWheelShot.open(mc); }, 15);
        l.add("batch4_toggle_wheel", () -> {}, 5);
        l.add(null, () -> { mc.setScreen(null); server(p -> {
            com.xenowarriors.toggle.Toggles.setEnabled(p, com.xenowarriors.toggle.Toggles.Entry.FOX_KING, false);
            com.xenowarriors.network.FoxKingTogglePacket.press(p);
            boolean blocked = !com.xenowarriors.mikiko.FoxKing.active(p);
            com.xenowarriors.toggle.Toggles.setEnabled(p, com.xenowarriors.toggle.Toggles.Entry.FOX_KING, true);
            log("toggles: Fox King switched off -> the key does nothing " + blocked + " -> " + (blocked ? "PASS" : "FAIL"));
            com.xenowarriors.toggle.Toggles.use(p, com.xenowarriors.toggle.Toggles.Entry.NETSU);
            boolean lit = com.xenowarriors.mikiko.Netsu.active(p);
            com.xenowarriors.toggle.Toggles.use(p, com.xenowarriors.toggle.Toggles.Entry.NETSU);
            log("toggles: Netsu from the wheel lit " + lit + ", again: out " + !com.xenowarriors.mikiko.Netsu.active(p) + " -> " + (lit && !com.xenowarriors.mikiko.Netsu.active(p) ? "PASS" : "FAIL"));
            com.xenowarriors.toggle.Toggles.use(p, com.xenowarriors.toggle.Toggles.Entry.TRUE_EMPATHY);
            boolean on = com.xenowarriors.toggle.Toggles.enabled(p, com.xenowarriors.toggle.Toggles.Entry.TRUE_EMPATHY);
            p.getPersistentData().putLongArray("xenowarriors_empathy_strain", new long[]{p.level().getGameTime() - 40, p.level().getGameTime() - 20});
            p.getPersistentData().remove("xw_empathy_passive_next");
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0); z.setNoAi(true); p.serverLevel().addFreshEntity(z); z.setTarget(p); z.addTag("xw_te_test");
            log("toggles: True Empathy passive switched on " + on);
        }); }, 10);
        l.add(null, () -> server(p -> {
            boolean off = !com.xenowarriors.toggle.Toggles.enabled(p, com.xenowarriors.toggle.Toggles.Entry.TRUE_EMPATHY);
            log("toggles: True Empathy read the zombie by itself - third use in 90 s -> switched itself off " + off + " -> " + (off ? "PASS" : "FAIL"));
            for (var z : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(10), x -> x.getTags().contains("xw_te_test"))) z.discard();
            p.removeAllEffects(); p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
        }), 30);
        l.add(null, () -> com.xenowarriors.client.menu.CharacterCustomScreen.open(null), 20);
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen s) s.tab(4); }, 25);
        l.add("batch4_custom_passives", () -> {}, 5);
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen s) s.tab(5); }, 25);
        l.add("batch4_custom_rlclick", () -> {}, 5);
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen s) s.tab(0); }, 15);
        l.add("batch4_custom_stats", () -> {}, 5);
        l.add(null, () -> { mc.setScreen(null); server(p -> p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)); }, 5);
    }

    /** (the wheel needs its own class only so the test can open it the same way the key does) */
    private static final class ToggleWheelShot {
        static void open(Minecraft mc) { com.xenowarriors.client.ToggleWheelScreen.testHover = "netsu"; mc.setScreen(new com.xenowarriors.client.ToggleWheelScreen(true)); }
    }

    private static void rlClick(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            com.xenowarriors.skill.SkillManager.resetCooldowns(p);
            com.xenowarriors.toggle.Toggles.setRl(p, "fire_blitz");
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));
            p.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        }), 10);
        l.add(null, () -> server(p -> {
            boolean blocking = p.isUsingItem();
            com.xenowarriors.toggle.Toggles.useRl(p);
            var fb = new com.xenowarriors.skill.mikiko.FireBlitzSkill();
            long left = com.xenowarriors.skill.SkillManager.freeCooldownLeft(p, fb);
            int full = com.xenowarriors.skill.SkillLevels.cooldownFor(p, "fire_blitz", fb.getCooldownTicks());
            boolean ok = blocking && !p.isUsingItem() && left > 0 && left <= Math.round(full * 0.8F) + 1;
            log("rlclick: blocking " + blocking + " -> Fire Blitz by right+left click, block stopped " + !p.isUsingItem() + ", cooldown " + left + "/" + full + " ticks -> " + (ok ? "PASS" : "FAIL"));
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }), 10);
    }

    private static void shiyoken(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        for (int o : new int[]{0, 1, 3}) {
            l.add(null, () -> server(p -> { p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = o); com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p); }), 10);
            l.add(null, () -> { com.xenowarriors.client.mikiko.FoxAuraClient.forceLevel = 1; Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 8); }, 30);
            l.add("batch4_shiyoken_outfit" + o, () -> {
                boolean skin = mc.player.getSkinTextureLocation().getPath().contains("mikiko_shiyoken");
                boolean model = com.xenowarriors.client.render.CharacterDetailLayer.detailModelPath(mc.player).endsWith("mikiko_shiyoken.json");
                log("shiyoken: outfit " + o + " in Shiyoken -> its fire outfit (skin " + skin + ", model " + model + ") -> " + (skin && model ? "PASS" : "FAIL"));
            }, 3);
        }
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 150, 10), 20);
        l.add("batch4_shiyoken_back", () -> {}, 3);
        l.add(null, () -> { com.xenowarriors.client.mikiko.FoxAuraClient.forceLevel = -1; server(p -> p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0)); }, 10);
        l.add("batch4_shiyoken_off", () -> {
            boolean back = !mc.player.getSkinTextureLocation().getPath().contains("shiyoken");
            log("shiyoken: out of the form -> his own clothes again " + back + " -> " + (back ? "PASS" : "FAIL"));
            mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
        }, 3);
    }

    private static void dmzGun(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> {
            float xp0 = com.xenowarriors.compat.GunLevel.xp(p); int lv0 = com.xenowarriors.compat.GunLevel.level(p);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0); z.setNoAi(true); p.serverLevel().addFreshEntity(z);
            var shot = new net.minecraft.world.entity.projectile.Snowball(p.serverLevel(), p);
            com.xenowarriors.compat.DmzGunWeapons.markForTest(shot);
            float hp = z.getHealth();
            z.invulnerableTime = 0;
            z.hurt(p.damageSources().thrown(shot, p), 4F);
            float dealt = hp - z.getHealth();
            boolean trained = com.xenowarriors.compat.GunLevel.level(p) > lv0 || com.xenowarriors.compat.GunLevel.xp(p) > xp0;
            log("dmzgun: a Blaster Cannon / Merus' Laser shot (4 dmg) dealt " + dealt + " (x" + com.xenowarriors.compat.GunLevel.multiplier(p) + "), gun XP up " + trained + " -> " + (trained && dealt > 0F ? "PASS" : "FAIL"));
            z.discard();
        }), 10);
    }

    private static void radarCraft(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            b[0] = botAt(p, CharacterType.RHALIX, 2, 3);
            for (var r : p.serverLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
                ItemStack res = r.getResultItem(p.serverLevel().registryAccess());
                if (!"dragonminez:dball_radar".equals(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(res.getItem())))) continue;
                for (var ing : r.getIngredients()) if (!ing.isEmpty()) b[0].inventory.addItem(ing.getItems()[0].copyWithCount(1));
                b[0].inventory.addItem(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 8));
                break;
            }
            int before = com.xenowarriors.bot.BotCrafting.radarCrafts;
            var made = com.xenowarriors.bot.BotCrafting.radarCraft(b[0], p);
            boolean has = false;
            for (int i = 0; i < b[0].inventory.getContainerSize(); i++) has |= com.xenowarriors.compat.DmzBotRadar.isRadar(b[0].inventory.getItem(i));
            log("radar: bot with the parts made " + (made == null ? "nothing" : made.getHoverName().getString()) + ", has a radar " + has + " -> " + (made != null && has && com.xenowarriors.bot.BotCrafting.radarCrafts == before + 1 ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 20);
    }

    private static void yuriForms(Minecraft mc, MikikoTests.Steps l) {
        String[][] forms = {{"supersaiyan", "supersaiyan"}, {"supersaiyan", "supersaiyan2"}, {"supersaiyan", "supersaiyan3"}, {"supersaiyan", "supersaiyan4"}, {"legendaryforms", "ssjfullpower"}};
        l.add(null, () -> server(p -> com.xenowarriors.character.Seasons.set(p.server, Math.max(2, com.xenowarriors.character.Seasons.highest(p.server)))), 5);
        Batch3Tests.character(l, CharacterType.YURI);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> { DmzTests.make(p, "saiyan"); p.teleportTo(p.getX(), p.getY(), p.getZ()); }); }, 40);
        for (String[] fm : forms) {
            l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> setForm(p, fm[0], fm[1])); }, 10);
            l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 25, 8); }, 60);
            l.add("batch4_yuri_" + fm[1], () -> {
                var me = mc.player;
                String set = com.xenowarriors.client.compat.DmzForms.faceSet(me);
                int hair = com.xenowarriors.client.compat.DmzForms.hairStyle(me), col = com.xenowarriors.client.compat.DmzForms.hairColor(me);
                boolean clothed = !me.getSkinTextureLocation().getPath().endsWith("_ssj4.png");
                String want = switch (fm[1]) { case "supersaiyan3" -> "ssj3"; case "supersaiyan4" -> "ssj4"; case "ssjfullpower" -> "lssj"; default -> "ssj"; };
                boolean ok = want.equals(set) && hair > 0 && col >= 0 && clothed;
                log("yurissj " + fm[1] + ": faces=" + set + " hair style=" + hair + " colour=" + Integer.toHexString(col) + " keeps clothes " + clothed + " -> " + (ok ? "PASS" : "FAIL"));
            }, 3);
        }
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 160, 10), 20);
        l.add("batch4_yuri_ssj4_back", () -> {}, 3);
        l.add(null, () -> server(p -> setForm(p, null, null)), 10);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    // ------------------------------------------------------------------ batch 6

    private static void rlAll(Minecraft mc, MikikoTests.Steps l) {
        String[][] want = {{"kyrie", "gravity_fetch"}, {"max", "wind_of_sorrow"}, {"sam", "batter_up"}, {"technica", "gravity_rounds"}, {"kaeru", "sound_burst"},
                {"ultima", "energy_blast"}, {"zane", "broom_smack"}, {"yuri", "virus_upload"}, {"stevie", "disco_kick"}, {"garry", "karma_blast"}};
        l.add(null, () -> server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }), 5);
        for (String[] w : want) {
            Batch3Tests.character(l, CharacterType.byId(w[0]));
            l.add(null, () -> server(p -> {
                com.xenowarriors.skill.SkillManager.resetCooldowns(p);
                com.xenowarriors.karma.Karma.add(p, 300);
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(p.getX(), p.getY(), p.getZ() + 3, 0, 0); z.setNoAi(true); z.addTag("xw_rl"); p.serverLevel().addFreshEntity(z);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
                com.xenowarriors.toggle.Toggles.setRl(p, w[1]);
                boolean picked = w[1].equals(com.xenowarriors.toggle.Toggles.rlSkill(p));
                com.xenowarriors.toggle.Toggles.useRl(p);
                com.xenowarriors.skill.Skill s = null;
                for (var en : com.xenowarriors.skill.SkillPool.of(p)) if (en.skill().getId().equals(w[1])) s = en.skill();
                long left = s == null ? -1 : com.xenowarriors.skill.SkillManager.freeCooldownLeft(p, s);
                log("rlall " + w[0] + " " + w[1] + ": picked " + picked + ", went off (cooldown " + left + ") -> " + (picked && left > 0 ? "PASS" : "FAIL"));
                for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(20), x -> x.getTags().contains("xw_rl"))) e.discard();
            }), 15);
        }
        l.add(null, () -> server(p -> { p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.removeAllEffects(); p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false)); }), 5);
    }

    private static void autoPassives(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> server(p -> com.xenowarriors.character.Seasons.set(p.server, 3)), 5);
        // Max: Lunar Mind at night
        Batch3Tests.character(l, CharacterType.MAX);
        l.add(null, () -> server(p -> { p.removeAllEffects(); p.serverLevel().setDayTime(18000); }), 50);
        l.add(null, () -> server(p -> {
            boolean night = p.hasEffect(MobEffects.DAMAGE_BOOST);
            p.removeAllEffects(); p.serverLevel().setDayTime(6000);
            log("autopass: Max's Lunar Mind at night " + night);
        }), 50);
        l.add(null, () -> server(p -> {
            boolean day = p.hasEffect(MobEffects.DAMAGE_BOOST);
            log("autopass: Lunar Mind only at night - by day " + day + " -> " + (!day ? "PASS" : "FAIL"));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
        }), 5);
        // Technica: Gravity Boots on a long fall, Visor Scan on lock-on, Rescue Platform for two downed teammates
        Batch3Tests.character(l, CharacterType.TECHNICA);
        float[] hp = new float[1];
        l.add(null, () -> server(p -> {
            com.xenowarriors.toggle.Passives.resetForTest(p);
            p.removeAllEffects();
            p.setHealth(p.getMaxHealth()); hp[0] = p.getHealth();
            p.teleportTo(p.getX(), p.getY() + 22, p.getZ());
        }), 70);
        l.add(null, () -> server(p -> {
            boolean ok = p.getHealth() >= hp[0] - 0.01F;
            log("autopass: Technica fell 22 blocks -> Gravity Boots fired " + com.xenowarriors.toggle.Passives.bootsSaved + ", health " + p.getHealth() + "/" + hp[0] + " -> " + (ok && com.xenowarriors.toggle.Passives.bootsSaved > 0 ? "PASS" : "FAIL"));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX(), p.getY(), p.getZ() + 4, 0, 0); z.setNoAi(true); z.addTag("xw_visor"); p.serverLevel().addFreshEntity(z);
            int before = com.xenowarriors.toggle.Passives.visorScans;
            com.xenowarriors.toggle.Passives.lockedOn(p, z.getId());
            log("autopass: Technica locks on -> Visor Scan " + (com.xenowarriors.toggle.Passives.visorScans > before) + " -> " + (com.xenowarriors.toggle.Passives.visorScans > before ? "PASS" : "FAIL"));
            z.discard();
            Batch3Tests.clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);                  // (Rescue Platform unlocks at level 30)
            PartyBotEntity a = botAt(p, CharacterType.MAX, 2, 2), b = botAt(p, CharacterType.SAM, -2, 2);
            a.die(p.damageSources().generic()); b.die(p.damageSources().generic());
        }), 10);
        l.add(null, () -> server(p -> {
            long up = PartyBots.bots(p).stream().filter(x -> !x.downed()).count();
            log("autopass: two teammates down -> Rescue Platform " + com.xenowarriors.toggle.Passives.rescues + ", back up " + up + " -> " + (com.xenowarriors.toggle.Passives.rescues > 0 && up >= 2 ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }), 20);
        // Ultima: Living Weapon on the transform key, Attack Prediction switching itself off when overused
        Batch3Tests.character(l, CharacterType.ULTIMA);
        l.add(null, () -> server(p -> {
            com.xenowarriors.skill.SkillManager.resetCooldowns(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            com.xenowarriors.network.FoxKingTogglePacket.press(p);
            boolean lw = com.xenowarriors.toggle.Toggles.active(p, com.xenowarriors.toggle.Toggles.Entry.LIVING_WEAPON);
            log("autopass: Ultima's transform key -> Living Weapon " + lw + " -> " + (lw ? "PASS" : "FAIL"));
            com.xenowarriors.toggle.Toggles.setEnabled(p, com.xenowarriors.toggle.Toggles.Entry.ATTACK_PREDICTION, true);
            p.getPersistentData().putLongArray("xw_predict_strain", new long[]{p.level().getGameTime() - 40, p.level().getGameTime() - 20});
            com.xenowarriors.toggle.Passives.resetForTest(p);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0); z.setNoAi(true); z.addTag("xw_ap"); p.serverLevel().addFreshEntity(z); z.setTarget(p);
        }), 30);
        l.add(null, () -> server(p -> {
            boolean off = !com.xenowarriors.toggle.Toggles.enabled(p, com.xenowarriors.toggle.Toggles.Entry.ATTACK_PREDICTION);
            log("autopass: Attack Prediction read the zombie (" + com.xenowarriors.toggle.Passives.predictions + ") - overused -> switched itself off " + off + " -> " + (off && com.xenowarriors.toggle.Passives.predictions > 0 ? "PASS" : "FAIL"));
            for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(20), x -> x.getTags().contains("xw_ap"))) e.discard();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.removeAllEffects();
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 10, false, false));
        }), 5);
    }

    private static void scent(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> server(p -> com.xenowarriors.character.Seasons.set(p.server, 3)), 5);
        Batch3Tests.character(l, CharacterType.KAERU);
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            for (int i = 0; i < 3; i++) {
                var e = (i == 0 ? net.minecraft.world.entity.EntityType.ZOMBIE : i == 1 ? net.minecraft.world.entity.EntityType.PIG : net.minecraft.world.entity.EntityType.SHEEP).create(p.serverLevel());
                e.moveTo(p.getX() + 6 + i * 3, p.getY(), p.getZ() + 4, 0, 0); ((net.minecraft.world.entity.Mob) e).setNoAi(true); e.addTag("xw_scent"); p.serverLevel().addFreshEntity(e);
            }
            com.xenowarriors.skill.SkillManager.resetCooldowns(p);
            com.xenowarriors.toggle.Toggles.use(p, com.xenowarriors.toggle.Toggles.Entry.SCENT_TRACK);
        }), 20);
        l.add("batch4_scent_picker", () -> {
            boolean open = mc.screen instanceof com.xenowarriors.client.kaeru.ScentScreen;
            log("scent: the picker opened " + open + " -> " + (open ? "PASS" : "FAIL"));
        }, 3);
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.kaeru.ScentScreen s) s.pickFirstForTest(); }, 10);
        l.add(null, () -> server(p -> { for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(30), x -> x.getTags().contains("xw_scent"))) { e.setNoAi(false); } }), 80);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 25), 10);
        l.add("batch4_scent_trail", () -> {
            boolean ok = com.xenowarriors.client.kaeru.ScentClient.target() >= 0;
            log("scent: picked one -> tracking " + ok + ", trail points " + com.xenowarriors.client.kaeru.ScentClient.points() + " -> " + (ok ? "PASS" : "FAIL"));
        }, 3);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> {
            for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40), x -> x.getTags().contains("xw_scent"))) e.discard();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); }); }, 5);
    }

    private static void techSense(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> server(p -> com.xenowarriors.character.Seasons.set(p.server, 3)), 5);
        Batch3Tests.character(l, CharacterType.YURI);
        l.add(null, () -> server(p -> {
            var soul = com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_SOUL.get().create(p.serverLevel());
            soul.moveTo(p.getX() + 6, p.getY(), p.getZ(), 0, 0); soul.setNoAi(true); soul.addTag("xw_ts"); p.serverLevel().addFreshEntity(soul);
            com.xenowarriors.karma.KarmaSensing.unbreakForTest(p);
            float k0 = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.getKarma()).orElse(0F);
            com.xenowarriors.karma.KarmaSensing.use(p, true);
            float k1 = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.getKarma()).orElse(0F);
            boolean ok = k1 >= k0 && com.xenowarriors.karma.KarmaSensing.holding(p) && !com.xenowarriors.karma.KarmaSensing.brokenForTest(p);
            log("techsense: Yuri senses a corrupted soul -> no Karma (" + k0 + " -> " + k1 + "), still sensing " + com.xenowarriors.karma.KarmaSensing.holding(p) + " -> " + (ok ? "PASS" : "FAIL"));
        }), 20);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, -90, 10), 10);
        l.add("batch4_techsense_yuri", () -> {}, 3);
        l.add(null, () -> server(p -> com.xenowarriors.karma.KarmaSensing.release(p)), 5);
        Batch3Tests.character(l, CharacterType.TECHNICA);
        l.add(null, () -> server(p -> {
            var soul = com.xenowarriors.corrupted.ModCorrupted.CORRUPTED_SOUL.get().create(p.serverLevel());   // (a fresh one: the first may have faded)
            soul.moveTo(p.getX() + 6, p.getY(), p.getZ(), 0, 0); soul.setNoAi(true); soul.addTag("xw_ts"); p.serverLevel().addFreshEntity(soul);
            com.xenowarriors.karma.KarmaSensing.unbreakForTest(p);
            com.xenowarriors.karma.KarmaSensing.use(p, true);
            boolean broken = com.xenowarriors.karma.KarmaSensing.brokenForTest(p) && !com.xenowarriors.karma.KarmaSensing.holding(p);
            log("techsense: Technica senses a corrupted soul -> her visor crashes, sensing offline " + broken + " -> " + (broken ? "PASS" : "FAIL"));
            com.xenowarriors.karma.KarmaSensing.unbreakForTest(p);
            for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(20), x -> x.getTags().contains("xw_ts"))) e.discard();
        }), 10);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void partyMusic(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity a = botAt(p, CharacterType.RHALIX, 2, 3);
            a.inventory.addItem(new ItemStack(net.minecraft.world.item.Items.JUKEBOX));
            com.xenowarriors.bot.BotPartyTime.start(p);
            log("partymusic: a bot with a jukebox -> Xeno Warriors music " + com.xenowarriors.bot.BotPartyTime.djForTest(p) + " -> " + (com.xenowarriors.bot.BotPartyTime.djForTest(p) ? "PASS" : "FAIL"));
        }), 40);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 30, 25), 10);
        l.add("batch4_party_jukebox", () -> {}, 3);
        l.add(null, () -> server(p -> { com.xenowarriors.bot.BotPartyTime.endForTest(p); Batch3Tests.clearBots(p); }), 20);
        l.add(null, () -> server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 3);
            botAt(p, CharacterType.GARRY, 2, 3); botAt(p, CharacterType.STEVIE, -2, 3); botAt(p, CharacterType.ZANE, 0, 4);
            com.xenowarriors.bot.BotPartyTime.start(p);
            int bb = com.xenowarriors.bot.BotPartyTime.beatboxersForTest(p);
            com.xenowarriors.bot.BotPartyTime.battleNowForTest(p);
            log("partymusic: no jukebox, Garry and Stevie there -> beatboxers " + bb + " -> " + (bb == 2 ? "PASS" : "FAIL"));
        }), 30);
        l.add(null, () -> server(p -> {
            boolean on = com.xenowarriors.bot.BotPartyTime.battlingForTest(p);
            log("partymusic: rap battle Garry vs Stevie on " + on + " (battles " + com.xenowarriors.bot.BotPartyTime.rapBattles + ") -> " + (on ? "PASS" : "FAIL"));
        }), 60);
        l.add("batch4_party_rapbattle", () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10);
        l.add(null, () -> { mc.setScreen(null); server(p -> { com.xenowarriors.bot.BotPartyTime.endForTest(p); Batch3Tests.clearBots(p); }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10);
    }

    private static void headlights(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            p.serverLevel().setDayTime(18000);
            var v = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.CAR).get().create(p.serverLevel());
            v.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0); v.addTag("xw_hl");
            p.serverLevel().addFreshEntity(v);
            p.startRiding(v, true);
        }), 40);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 15), 20);
        l.add("batch4_headlights", () -> {
            server(p -> {
                var v = p.getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity ve ? ve : null;
                int lit = v == null ? -1 : com.xenowarriors.vehicle.Headlights.litForTest(v);
                log("headlight: driving at night -> lights on the road " + lit + ", beams drawn " + com.xenowarriors.client.vehicle.HeadlightBeams.drawn + " -> " + (lit > 0 && com.xenowarriors.client.vehicle.HeadlightBeams.drawn > 0 ? "PASS" : "FAIL"));
            });
        }, 3);
        l.add(null, () -> server(p -> {
            p.stopRiding();
            for (var e : p.serverLevel().getEntitiesOfClass(com.xenowarriors.vehicle.VehicleEntity.class, p.getBoundingBox().inflate(20), x -> x.getTags().contains("xw_hl"))) e.discard();
            p.serverLevel().setDayTime(6000);
        }), 20);
        l.add(null, () -> server(p -> {                                         // (the car left: its lights come down a few ticks later)
            int left = 0;
            for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-20, -4, -20), p.blockPosition().offset(20, 4, 20))) if (p.level().getBlockState(q).is(net.minecraft.world.level.block.Blocks.LIGHT)) left++;
            log("headlight: the car's gone -> light blocks left " + left + " -> " + (left == 0 ? "PASS" : "FAIL"));
        }), 5);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    // ------------------------------------------------------------------ the hunger bar after eating (how many ticks it lags)

    private static void hungerBar(Minecraft mc, MikikoTests.Steps l) {
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.KYRIE}) {
            Batch3Tests.character(l, t);
            int[] start = new int[1];
            l.add(null, () -> server(p -> {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getInventory().clearContent();
                p.getInventory().selected = 0;
                p.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 8));
                if (com.xenowarriors.character.BigHunger.max(p) > 20) { p.getPersistentData().putInt("xw_food_total", 30); p.getPersistentData().putInt("xw_food_last", 10); }
                p.getFoodData().setFoodLevel(10);
                start[0] = com.xenowarriors.character.BigHunger.total(p);
            }), 30);
            int[] tick = {0}, serverAt = {-1}, hudAt = {-1}, barAt = {-1};
            int[] hud0 = new int[2];
            l.add(null, () -> { mc.setScreen(null); mc.options.hideGui = false; hud0[0] = com.xenowarriors.client.BigHungerHud.total(); hud0[1] = mc.player.getFoodData().getFoodLevel(); mc.options.keyUse.setDown(true); }, 1);
            for (int i = 0; i < 70; i++) l.add(null, () -> {
                tick[0]++;
                var s = mc.getSingleplayerServer();
                var sp = s == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
                int st = sp == null ? -1 : com.xenowarriors.character.BigHunger.total(sp);
                if (serverAt[0] < 0 && st > start[0]) serverAt[0] = tick[0];
                if (hudAt[0] < 0 && com.xenowarriors.client.BigHungerHud.total() > hud0[0] && com.xenowarriors.client.BigHungerHud.max() > 20) hudAt[0] = tick[0];
                if (barAt[0] < 0 && mc.player.getFoodData().getFoodLevel() != hud0[1]) barAt[0] = tick[0];
                if (tick[0] % 5 == 0) log("hungerbar " + t.getId() + " tick " + tick[0] + ": server total " + st + ", client stomach " + com.xenowarriors.client.BigHungerHud.total() + "/" + com.xenowarriors.client.BigHungerHud.max() + ", client bar " + mc.player.getFoodData().getFoodLevel() + ", using " + mc.player.isUsingItem());
            }, 1);
            l.add("batch4_hungerbar_" + t.getId(), () -> {
                mc.options.keyUse.setDown(false);
                boolean big = com.xenowarriors.client.BigHungerHud.max() > 20;
                int lag = big ? (hudAt[0] < 0 ? 999 : hudAt[0] - serverAt[0]) : (barAt[0] < 0 ? 999 : barAt[0] - serverAt[0]);
                log("hungerbar " + t.getId() + ": ate at tick " + serverAt[0] + ", the HUD showed it at tick " + (big ? hudAt[0] : barAt[0]) + " (lag " + lag + " ticks) -> " + (serverAt[0] > 0 && lag <= 3 ? "PASS" : "FAIL"));
            }, 2);
        }
    }

    // ------------------------------------------------------------------ bots: passives, R+L, water, Home Chest, camp, food, gifts, help

    private static void botLife(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.KYRIE);
        PartyBotEntity[] b = new PartyBotEntity[4];
        net.minecraft.world.entity.animal.IronGolem[] g = new net.minecraft.world.entity.animal.IronGolem[1];
        // Ultima's Attack Prediction (switched on for him) reads the hits coming at him
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            Batch3Tests.clearBots(p);
            com.xenowarriors.toggle.Toggles.setForTest(p, CharacterType.ULTIMA, com.xenowarriors.toggle.Toggles.Entry.ATTACK_PREDICTION, true);
            b[0] = botAt(p, CharacterType.ULTIMA, 3, 3);
            b[0].applyLevel(80); b[0].karma = 100;
            com.xenowarriors.bot.BotPassives.resetForTest(b[0]);
            g[0] = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            g[0].moveTo(b[0].getX() + 2, b[0].getY(), b[0].getZ(), 0, 0); g[0].setNoAi(true);
            p.serverLevel().addFreshEntity(g[0]);
            g[0].setTarget(b[0]);
        }), 40);
        l.add(null, () -> server(p -> {
            int r0 = com.xenowarriors.bot.BotPassives.reads, d0 = com.xenowarriors.bot.BotPassives.dodges;
            b[0].invulnerableTime = 0;
            float hp = b[0].getHealth();
            b[0].hurt(p.damageSources().mobAttack(g[0]), 4F);
            log("botlife passives: Ultima bot read " + r0 + ", dodged " + (com.xenowarriors.bot.BotPassives.dodges - d0) + " hit (health " + hp + " -> " + b[0].getHealth() + ") -> "
                    + (r0 > 0 && com.xenowarriors.bot.BotPassives.dodges > d0 && b[0].getHealth() >= hp ? "PASS" : "FAIL"));
            com.xenowarriors.toggle.Toggles.setForTest(p, CharacterType.ULTIMA, com.xenowarriors.toggle.Toggles.Entry.ATTACK_PREDICTION, false);
            com.xenowarriors.bot.BotPassives.resetForTest(b[0]);
        }), 30);
        l.add(null, () -> server(p -> {
            int r0 = com.xenowarriors.bot.BotPassives.reads;
            b[0].karma = 100;
            com.xenowarriors.bot.BotPassives.resetForTest(b[0]);
            com.xenowarriors.skill.ServerScheduler.later(30, () -> log("botlife passives: switched off -> no reads (" + (com.xenowarriors.bot.BotPassives.reads - r0) + ") -> " + (com.xenowarriors.bot.BotPassives.reads == r0 ? "PASS" : "FAIL")));
        }), 40);
        // the right+left click move: Ultima's Energy Blast at the golem
        l.add(null, () -> server(p -> {
            b[0].karma = 100;
            boolean used = com.xenowarriors.bot.BotPassives.rlForTest(b[0], g[0]);
            log("botlife rl: Ultima bot's right+left click move (" + com.xenowarriors.toggle.Toggles.rlSkillFor(p, CharacterType.ULTIMA) + ") went off " + used + ", karma left " + Math.round(b[0].karma) + " -> " + (used && b[0].karma < 100 ? "PASS" : "FAIL"));
            boolean again = com.xenowarriors.bot.BotPassives.rlForTest(b[0], g[0]);
            log("botlife rl: straight again -> cooling down (" + !again + ") -> " + (!again ? "PASS" : "FAIL"));
            g[0].discard();
        }), 20);
        // Technica's Gravity Boots: dropped from 14 blocks, no fall damage
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            b[1] = botAt(p, CharacterType.TECHNICA, 4, 0);
            b[1].applyLevel(80);
            com.xenowarriors.bot.BotPassives.resetForTest(b[1]);
            b[1].setHealth(b[1].getMaxHealth());
            b[1].teleportTo(b[1].getX(), b[1].getY() + 14, b[1].getZ());
        }), 70);
        l.add(null, () -> server(p -> log("botlife boots: Technica bot fell 14 blocks - boots " + com.xenowarriors.bot.BotPassives.boots + ", health " + b[1].getHealth() + "/" + b[1].getMaxHealth()
                + " -> " + (com.xenowarriors.bot.BotPassives.boots > 0 && b[1].getHealth() >= b[1].getMaxHealth() - 0.5F ? "PASS" : "FAIL"))), 5);
        // the Home Chest: a near one (walked to) and a far one (sent home)
        net.minecraft.core.BlockPos[] chest = new net.minecraft.core.BlockPos[2];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            b[1] = botAt(p, CharacterType.SAM, 2, 2);
            chest[0] = p.blockPosition().offset(10, 0, -6);
            p.level().setBlock(chest[0], com.xenowarriors.sky.ModBlocks.HOME_CHEST.get().defaultBlockState(), 3);
            if (p.level().getBlockEntity(chest[0]) instanceof com.xenowarriors.home.HomeChestBlockEntity be) be.setOwner(p.getUUID());
            com.xenowarriors.bot.BotHome.placed(p, p.level(), chest[0]);
            for (int i = 0; i < b[1].inventory.getContainerSize() - 2; i++) b[1].inventory.setItem(i, new ItemStack(i % 2 == 0 ? net.minecraft.world.item.Items.COBBLESTONE : net.minecraft.world.item.Items.RAW_IRON, 64));   // (full stacks: tidying can't free slots)
            b[1].inventory.setItem(0, new ItemStack(net.minecraft.world.item.Items.BREAD, 8));
        }), 40);
        l.add(null, () -> server(p -> { b[1].setHealth(b[1].getMaxHealth()); log("botlife home: Sam calm? " + com.xenowarriors.bot.BotErrands.danger(b[1])); }), 20 * 12);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 200, 25), 10);
        l.add("batch4_botlife_homechest", () -> {}, 3);
        l.add(null, () -> server(p -> {
            int in = 0;
            if (p.level().getBlockEntity(chest[0]) instanceof com.xenowarriors.home.HomeChestBlockEntity be) for (int i = 0; i < be.getContainerSize(); i++) if (!be.getItem(i).isEmpty()) in++;
            boolean breadKept = b[1].inventory.getItem(0).is(net.minecraft.world.item.Items.BREAD);
            log("botlife home: walked over " + com.xenowarriors.bot.BotHome.walks + ", stacks in the chest " + in + ", kept its bread " + breadKept + " -> " + (com.xenowarriors.bot.BotHome.walks > 0 && in > 0 && breadKept ? "PASS" : "FAIL"));
            p.level().setBlock(chest[0], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            chest[1] = p.blockPosition().offset(90, 0, 0);
            p.level().setBlock(chest[1], com.xenowarriors.sky.ModBlocks.HOME_CHEST.get().defaultBlockState(), 3);
            if (p.level().getBlockEntity(chest[1]) instanceof com.xenowarriors.home.HomeChestBlockEntity be) be.setOwner(p.getUUID());
            com.xenowarriors.bot.BotHome.placed(p, p.level(), chest[1]);
            for (int i = 1; i < b[1].inventory.getContainerSize() - 2; i++) b[1].inventory.setItem(i, new ItemStack(net.minecraft.world.item.Items.GRAVEL, 16));
            b[1].getPersistentData().remove("xw_home_next");
        }), 20 * 7);
        l.add(null, () -> server(p -> {
            int in = 0;
            if (p.level().getBlockEntity(chest[1]) instanceof com.xenowarriors.home.HomeChestBlockEntity be) for (int i = 0; i < be.getContainerSize(); i++) if (!be.getItem(i).isEmpty()) in++;
            log("botlife home: far chest (90 blocks) - sent home " + com.xenowarriors.bot.BotHome.sends + ", stacks in it " + in + " -> " + (com.xenowarriors.bot.BotHome.sends > 0 && in > 0 ? "PASS" : "FAIL"));
            p.level().setBlock(chest[1], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }), 5);
        // feeding you
        l.add(null, () -> server(p -> { p.getFoodData().setFoodLevel(6); p.getPersistentData().remove("xw_life_fed"); }), 20 * 6);
        l.add(null, () -> server(p -> log("botlife feed: hungry -> fed " + com.xenowarriors.bot.BotLife.feeds + ", food now " + p.getFoodData().getFoodLevel() + " -> " + (com.xenowarriors.bot.BotLife.feeds > 0 && p.getFoodData().getFoodLevel() > 6 ? "PASS" : "FAIL"))), 5);
        // a gift at bond 70+
        l.add(null, () -> server(p -> {
            com.xenowarriors.party.Bonds.set(p, "sam", 80, 0);
            b[1].errand = null; b[1].setTarget(null); b[1].teleportTo(p.getX() + 2, p.getY(), p.getZ() + 1);
            int before = p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
            com.xenowarriors.bot.BotLife.giftForTest(p);
            int after = p.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
            log("botlife gift: bond 80 -> gifts " + com.xenowarriors.bot.BotLife.gifts + ", items " + before + " -> " + after + " -> " + (com.xenowarriors.bot.BotLife.gifts > 0 && after > before ? "PASS" : "FAIL"));
        }), 10);
        // asking for help: Sam and Max hang out; and bots don't spar while it's on
        l.add(null, () -> server(p -> {
            b[2] = botAt(p, CharacterType.MAX, -3, 2);
            com.xenowarriors.party.Bonds.setBot(b[1], "max", 5, 0);
            com.xenowarriors.party.Bonds.setBot(b[2], "sam", 5, 0);
            com.xenowarriors.bot.BotLife.ask(p, b[1], b[2]);
        }), 10);
        l.add("batch4_botlife_help_card", () -> {}, 3);
        l.add(null, () -> server(p -> {
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenohelp yes " + b[1].getId() + " " + b[2].getId());
            String ev = com.xenowarriors.bot.BotSpar.event(p);
            log("botlife spar: during the hang-out no bot spars (" + ev + ") -> " + (ev != null ? "PASS" : "FAIL"));
        }), 60);
        l.add(null, () -> server(p -> {
            com.xenowarriors.bot.BotLife.endHangForTest(p);
            int a = com.xenowarriors.party.Bonds.botOwn(b[1], "max"), c = com.xenowarriors.party.Bonds.botOwn(b[2], "sam");
            log("botlife help: hang-outs " + com.xenowarriors.bot.BotLife.hangouts + ", bonds Sam->Max " + a + ", Max->Sam " + c + " -> " + (com.xenowarriors.bot.BotLife.hangouts > 0 && (a > 5 || c > 5 || com.xenowarriors.party.Bonds.botOwn(b[1], "max") >= 5) ? "PASS" : "FAIL"));
        }), 10);
        // night camp: in bed, they gather round (and a campfire)
        l.add(null, () -> server(p -> {
            p.serverLevel().setDayTime(18000);
            p.level().setBlock(p.blockPosition(), Blocks.RED_BED.defaultBlockState(), 3);
            p.startSleeping(p.blockPosition());
        }), 20 * 8);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 40), 10);
        l.add("batch4_botlife_camp", () -> {}, 3);
        net.minecraft.core.BlockPos[] fire = new net.minecraft.core.BlockPos[1];
        l.add(null, () -> server(p -> {
            double far = 0; for (PartyBotEntity x : com.xenowarriors.bot.PartyBots.bots(p)) far = Math.max(far, x.distanceTo(p));
            log("botlife camp: camps " + com.xenowarriors.bot.BotLife.camps + ", campfires " + com.xenowarriors.bot.BotLife.campfires + ", furthest bot " + String.format("%.1f", far) + " -> " + (com.xenowarriors.bot.BotLife.camps > 0 && far < 6 ? "PASS" : "FAIL"));
            p.stopSleeping();
            p.level().setBlock(p.blockPosition(), Blocks.AIR.defaultBlockState(), 3);
            p.serverLevel().setDayTime(6000);
        }), 10);
        l.add(null, () -> server(p -> {
            boolean any = false;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -2; dy <= 2; dy++) if (p.level().getBlockState(p.blockPosition().offset(dx, dy, dz)).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) any = true;
            log("botlife camp: up again -> campfire put out " + !any + " -> " + (!any ? "PASS" : "FAIL"));
        }), 5);
        // swimming after you: a pool, you across it, the bot in the water
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            var lv = p.serverLevel();
            net.minecraft.core.BlockPos c = p.blockPosition().offset(0, 0, 30);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -14; dz <= 14; dz++) {
                lv.setBlock(c.offset(dx, -3, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                for (int dy = -2; dy <= 0; dy++) lv.setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
            }
            p.teleportTo(c.getX() + 0.5, c.getY() + 0.2, c.getZ() + 12.5);
            b[3] = botAt(p, CharacterType.KAERU, 0, 0);
            b[3].teleportTo(c.getX() + 0.5, c.getY() - 0.5, c.getZ() - 12.5);
        }), 20);
        double[] d0 = new double[1];
        int[] s0 = new int[1];
        l.add(null, () -> server(p -> { d0[0] = b[3].distanceTo(p); s0[0] = com.xenowarriors.bot.BotMoves.swimStrokes; }), 20 * 6);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 180, 30), 5);
        l.add("batch4_botlife_swim", () -> {}, 3);
        l.add(null, () -> server(p -> {
            double d = b[3].distanceTo(p);
            log("botlife swim: bot swam after you " + String.format("%.1f -> %.1f", d0[0], d) + " blocks, strokes " + (com.xenowarriors.bot.BotMoves.swimStrokes - s0[0]) + " -> " + (d < d0[0] - 8 && com.xenowarriors.bot.BotMoves.swimStrokes > s0[0] ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 5);
        // the bot menu's Dragon Ball button (a bot carrying a radar)
        l.add(null, () -> mc.setScreen(new com.xenowarriors.client.bot.BotScreen(new com.xenowarriors.network.OpenBotMenuPacket(-1, "max", 0, 30, false, new String[0], new int[0], true))), 2);
        l.add("batch4_botlife_radar_menu", () -> log("botlife radar: the menu offers Find the Dragon Balls -> " + (mc.screen != null && mc.screen.children().stream().anyMatch(w -> w instanceof net.minecraft.client.gui.components.Button bt && bt.getMessage().getString().contains("Dragon Balls")) ? "PASS" : "FAIL")), 2);
        l.add(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
        // the party frames: 4 in the party vs 6
        for (String[] ids : new String[][]{{"rhalix", "kaeru", "kyrie"}, {"rhalix", "kaeru", "max", "lexy", "sam"}}) {
            l.add(null, () -> server(p -> {
                Batch3Tests.clearBots(p);
                p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), "xeno partylimit 16");
                for (CharacterType x : CharacterType.PLAYABLE) com.xenowarriors.party.Bonds.set(p, x.getId(), 70, 0);
                com.xenowarriors.bot.PartyBots.add(p, ids);
            }), 80);
            for (String style : new String[]{"classic", "kh", "blazing"}) {
                l.add(null, () -> { com.xenowarriors.client.ClientSettings.setHudStyle(style); mc.setScreen(null); mc.options.hideGui = false; mc.player.setXRot(10F); }, 10);
                l.add("batch4_botlife_partyhud_" + (ids.length + 1) + "_" + style, () -> {}, 2);
            }
        }
        l.add(null, () -> { com.xenowarriors.client.ClientSettings.setHudStyle("classic"); server(p -> { Batch3Tests.clearBots(p);
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), "xeno partylimit 4"); }); }, 5);
    }

    private static PartyBotEntity botAt(ServerPlayer p, CharacterType t, double dx, double dz) {
        PartyBotEntity b = PartyBots.spawn(p, t, null);
        if (b != null) { b.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0); b.setMode(PartyBotEntity.NEUTRAL); b.setHealth(b.getMaxHealth()); }
        return b;
    }

    private static boolean hit(net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.damagesource.DamageSource src, float amount) {
        victim.invulnerableTime = 0;
        return victim.hurt(src, amount);
    }

    private static void teamSpar(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[3];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            b[0] = botAt(p, CharacterType.RHALIX, 2, 4); b[1] = botAt(p, CharacterType.MAX, -2, 4); b[2] = botAt(p, CharacterType.SAM, 0, -3);
            com.xenowarriors.bot.BotSpar.askTeams(b[0], b[1], b[2], p);
        }), 20);
        l.add("batch4_teamspar_ask", () -> {}, 5);
        l.add(null, () -> server(p -> {
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "xenospar team yes " + b[0].getId() + " " + b[1].getId() + " " + b[2].getId());
        }), 10);
        l.add(null, () -> server(p -> {
            boolean started = com.xenowarriors.bot.BotSpar.teamMatchFor(p);
            boolean rules = PartyBots.sparFoes(p, b[0]) && PartyBots.sparFoes(p, b[1]) && !PartyBots.sparFoes(p, b[2]) && PartyBots.sparFoes(b[2], b[0])
                    && !PartyBots.sparFoes(b[0], b[1]) && b[0].sparringOwner() && !b[2].sparringOwner();
            log("teamspar: accepted the card -> started " + started + ", sides right " + rules + " -> " + (started && rules ? "PASS" : "FAIL"));
            boolean allyHit = hit(b[2], p.damageSources().playerAttack(p), 2F);
            boolean foeHit = hit(b[0], p.damageSources().playerAttack(p), 2F);
            boolean botBot = hit(b[1], p.damageSources().mobAttack(b[2]), 2F);
            log("teamspar: you hit your partner " + allyHit + ", you hit a foe " + foeHit + ", your partner hit a foe " + botBot + " -> " + (!allyHit && foeHit && botBot ? "PASS" : "FAIL"));
            hit(b[0], p.damageSources().playerAttack(p), b[0].getMaxHealth() * 2);
            boolean out0 = !com.xenowarriors.bot.BotSpar.inMatch(b[0]) && com.xenowarriors.bot.BotSpar.teamMatchFor(p) && b[0].isAlive() && !b[0].downed();
            log("teamspar: a foe knocked down to 30% is out, match goes on: " + out0 + " (hp " + Math.round(b[0].getHealth()) + "/" + Math.round(b[0].getMaxHealth()) + ") -> " + (out0 ? "PASS" : "FAIL"));
        }), 60);
        l.add("batch4_teamspar_fight", () -> {}, 5);
        l.add(null, () -> server(p -> {
            int fin = com.xenowarriors.bot.BotSpar.teamFinished;
            hit(b[1], p.damageSources().mobAttack(b[2]), b[1].getMaxHealth() * 2);
            boolean over = !com.xenowarriors.bot.BotSpar.teamMatchFor(p) && com.xenowarriors.bot.BotSpar.teamFinished == fin + 1;
            boolean calm = !b[0].sparring() && !b[1].sparring() && !b[2].sparring() && !PartyBots.sparFoes(p, b[1]);
            log("teamspar: last foe out -> match over " + over + ", everyone back to normal " + calm + " -> " + (over && calm ? "PASS" : "FAIL"));
            // the 1-on-1 bot-vs-bot spar: their hits land now
            b[0].setHealth(b[0].getMaxHealth()); b[1].setHealth(b[1].getMaxHealth());
            com.xenowarriors.bot.BotSpar.startBots(b[0], b[1], p);
            boolean lands = hit(b[1], p.damageSources().mobAttack(b[0]), 2F);
            boolean youCant = !hit(b[0], p.damageSources().playerAttack(p), 2F);
            log("teamspar: bot-vs-bot spar hit lands " + lands + ", you can't join in " + youCant + " -> " + (lands && youCant ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 10);
    }

    private static void botOutfit(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setOutfitOf(CharacterType.RHALIX, 1));
            b[0] = botAt(p, CharacterType.RHALIX, 0, 3);
        }), 20);
        l.add(null, () -> {
            var cb = mc.level.getEntitiesOfClass(PartyBotEntity.class, mc.player.getBoundingBox().inflate(10)).stream().findFirst().orElse(null);
            int seen = cb == null ? -1 : cb.outfit();
            log("botoutfit: you last had Rhalix in Origins 1 -> his bot wears " + seen + " (client) -> " + (seen == 1 ? "PASS" : "FAIL"));
        }, 3);
        l.add(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT), 3);
        l.add("batch4_botoutfit_origins1", () -> {}, 10);
        l.add(null, () -> server(p -> {
            PartyBots.action(p, b[0].getId(), PartyBots.ACT_OUTFIT, 2);
            int rec = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.outfitOf(CharacterType.RHALIX)).orElse(-1);
            log("botoutfit: changed from its menu to Origins 2 -> bot " + b[0].outfit() + ", Rhalix's own outfit " + rec + " -> " + (b[0].outfit() == 2 && rec == 2 ? "PASS" : "FAIL"));
        }), 20);
        l.add("batch4_botoutfit_origins2", () -> {}, 5);
        Batch3Tests.character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> {
            int o = com.xenowarriors.character.CharacterManager.outfit(p);
            log("botoutfit: playing Rhalix afterwards wears " + o + " -> " + (o == 2 ? "PASS" : "FAIL"));
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.outfit = 0; d.outfits = new net.minecraft.nbt.CompoundTag(); });
            Batch3Tests.clearBots(p);
        }), 5);
        l.add(null, () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 3);
    }

    private static void phoneCall(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            long now = p.server.overworld().getGameTime();
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.KYRIE, com.xenowarriors.bot.OffDuty.Act.TRAINING, now + 3 * com.xenowarriors.bot.OffDuty.MINUTE, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.MAX, com.xenowarriors.bot.OffDuty.Act.MINING, now + 9 * com.xenowarriors.bot.OffDuty.MINUTE, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.SAM, com.xenowarriors.bot.OffDuty.Act.RELAX, now + 4 * com.xenowarriors.bot.OffDuty.MINUTE, null);
            com.xenowarriors.bot.BotCalls.open(p);
        }), 20);
        l.add("batch4_phone_team", () -> {}, 5);
        l.add(null, () -> {
            boolean open = mc.screen instanceof com.xenowarriors.client.PhoneScreen;
            log("phonecall: Teammates page open " + open + " -> " + (open ? "PASS" : "FAIL"));
            com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.PhoneActionPacket(4, new java.util.UUID(0, CharacterType.KYRIE.ordinal())));
        }, 15);
        l.add("batch4_phone_called", () -> {}, 5);
        l.add(null, () -> server(p -> {
            boolean called = com.xenowarriors.bot.BotCalls.called(p, CharacterType.KYRIE);
            long now = p.server.overworld().getGameTime();
            com.xenowarriors.bot.OffDuty.simulate(p, CharacterType.KYRIE, 5, now, p.getRandom());
            boolean joined = PartyBots.bots(p).stream().anyMatch(x -> x.character() == CharacterType.KYRIE);
            log("phonecall: called Kyrie " + called + ", she joined when done " + joined + " -> " + (called && joined ? "PASS" : "FAIL"));
            // a full party: she rings to say she's ready instead
            for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.LOGAN, CharacterType.KEITA, CharacterType.GARRY, CharacterType.STEVIE})
                if (PartyBots.room(p) > 0) PartyBots.spawn(p, t, null);
            com.xenowarriors.bot.BotCalls.call(p, CharacterType.SAM);
            int before = PartyBots.bots(p).size();
            com.xenowarriors.bot.OffDuty.simulate(p, CharacterType.SAM, 6, now, p.getRandom());
            boolean stayed = PartyBots.bots(p).size() == before && PartyBots.bots(p).stream().noneMatch(x -> x.character() == CharacterType.SAM);
            boolean cleared = !com.xenowarriors.bot.BotCalls.called(p, CharacterType.SAM);
            log("phonecall: party full (room " + PartyBots.room(p) + ") -> Sam didn't squeeze in " + stayed + ", rang back " + cleared + " -> " + (stayed && cleared && PartyBots.room(p) <= 0 ? "PASS" : "FAIL"));
            mc.execute(() -> mc.setScreen(null));
            Batch3Tests.clearBots(p);
        }), 10);
        l.add("batch4_phone_chat", () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 10);
        l.add(null, () -> mc.setScreen(null), 3);
    }

    private static void offDutyCards(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        int[] season = new int[1];
        l.add(null, () -> server(p -> {
            season[0] = com.xenowarriors.character.Seasons.highest(p.server);
            com.xenowarriors.character.Seasons.set(p.server, Math.max(2, season[0]));   // (Ultima is Season 2)
            Batch3Tests.clearBots(p);
            long now = p.server.overworld().getGameTime(), m = com.xenowarriors.bot.OffDuty.MINUTE;
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.RHALIX, com.xenowarriors.bot.OffDuty.Act.RELAX, now + 20 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.ULTIMA, com.xenowarriors.bot.OffDuty.Act.TRAINING, now + 20 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.KYRIE, com.xenowarriors.bot.OffDuty.Act.TRAINING, now + 20 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.MAX, com.xenowarriors.bot.OffDuty.Act.DRAGON_BALLS, now + 20 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.SAM, com.xenowarriors.bot.OffDuty.Act.PARTY, now + 20 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.LOGAN, com.xenowarriors.bot.OffDuty.Act.BUILDING, now + 20 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.KEITA, com.xenowarriors.bot.OffDuty.Act.MINING, now + 20 * m, null);
            com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.GARRY, com.xenowarriors.bot.OffDuty.Act.HUNTING, now + 20 * m, null);
            PartyBots.openPick(p);
        }), 20);
        l.add("batch4_offduty_cards_a", () -> {}, 7);
        l.add("batch4_offduty_cards_b", () -> {}, 13);
        l.add(null, () -> server(p -> {
            mc.execute(() -> mc.setScreen(null));
            // Ultima: never relaxes (even after a long time with you), trains most of all
            boolean tireless = com.xenowarriors.bot.OffDuty.tireless(CharacterType.ULTIMA) && !com.xenowarriors.bot.OffDuty.tireless(CharacterType.KYRIE);
            int relax = 0, train = 0, picks = 0;
            long now = p.server.overworld().getGameTime(), m = com.xenowarriors.bot.OffDuty.MINUTE;
            for (int i = 0; i < 120; i++) {
                com.xenowarriors.bot.OffDuty.setForTest(p, CharacterType.ULTIMA, com.xenowarriors.bot.OffDuty.Act.NONE, 0, null);
                com.xenowarriors.bot.OffDuty.simulate(p, CharacterType.ULTIMA, 4, now + i * 10 * m, p.getRandom());
                var a = com.xenowarriors.bot.OffDuty.act(p, CharacterType.ULTIMA);
                if (a == com.xenowarriors.bot.OffDuty.Act.NONE) continue;
                picks++;
                if (a.relaxing() || a == com.xenowarriors.bot.OffDuty.Act.HUNTING) relax++;
                if (a == com.xenowarriors.bot.OffDuty.Act.TRAINING) train++;
            }
            com.xenowarriors.party.Bonds.set(p, "ultima", 60, 0);
            String job = com.xenowarriors.bot.OffDuty.assign(p, CharacterType.ULTIMA, "relax");
            log("offduty: Ultima tireless " + tireless + "; " + picks + " picks: training " + train + ", relaxing/hunting " + relax + "; told to relax: '" + job + "' -> "
                    + (tireless && relax == 0 && picks > 20 && train * 2 > picks && job.contains("training") ? "PASS" : "FAIL"));
            com.xenowarriors.bot.OffDuty.assign(p, CharacterType.ULTIMA, "recall");
            com.xenowarriors.character.Seasons.set(p.server, season[0]);
        }), 5);
    }

    private static void wireHit(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.RHALIX);
        int[] ids = new int[5], season = new int[1];
        l.add(null, () -> server(p -> {
            season[0] = com.xenowarriors.character.Seasons.highest(p.server);
            com.xenowarriors.character.Seasons.set(p.server, Math.max(2, season[0]));
            Batch3Tests.clearBots(p);
            com.xenowarriors.forms.WeaponForms.summon(p, com.xenowarriors.forms.Form.WIRES, false);
            p.setYRot(0); p.setYHeadRot(0); p.setXRot(0);
            // (facing +z) 5 ahead, 4.5 ahead-left, 3 ahead but 2.5 up, 1.2 behind (wraps round), 4 behind (missed)
            double[][] at = {{0, 0, 5}, {-3, 0, 3.4}, {0, 2.5, 3}, {0.3, 0, -1.2}, {0, 0, -4}};
            for (int i = 0; i < at.length; i++) {
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(p.getX() + at[i][0], p.getY() + at[i][1], p.getZ() + at[i][2], 180, 0);
                z.setNoAi(true); z.setNoGravity(true); z.setPersistenceRequired(); z.addTag("xw_wiretest");
                p.serverLevel().addFreshEntity(z);
                ids[i] = z.getId();
            }
        }), 20);
        l.add(null, () -> server(p -> {
            float[] hp = new float[5];
            for (int i = 0; i < 5; i++) hp[i] = ((net.minecraft.world.entity.LivingEntity) p.level().getEntity(ids[i])).getHealth();
            int hits = com.xenowarriors.rhalix.Wires.whip(p);
            boolean[] got = new boolean[5];
            for (int i = 0; i < 5; i++) got[i] = ((net.minecraft.world.entity.LivingEntity) p.level().getEntity(ids[i])).getHealth() < hp[i];
            log("wirehit: one lash hit " + hits + " - ahead 5 " + got[0] + ", ahead-left " + got[1] + ", ahead+up " + got[2] + ", right behind " + got[3] + ", 4 behind " + got[4]
                    + " -> " + (got[0] && got[1] && got[2] && got[3] && !got[4] ? "PASS" : "FAIL"));
            for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(20), z -> z.getTags().contains("xw_wiretest"))) e.discard();
            com.xenowarriors.character.Seasons.set(p.server, season[0]);
        }), 5);
    }

    // ------------------------------------------------------------------ bots fishing; bots taking missions on their own

    private static void fishing(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            var lv = p.serverLevel();
            BlockPos at = p.blockPosition();
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 8; dz++) { for (int dy = 0; dy <= 3; dy++) lv.setBlockAndUpdate(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState()); lv.setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState()); }
            for (int dx = -2; dx <= 2; dx++) for (int dz = 4; dz <= 7; dz++) { lv.setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.WATER.defaultBlockState()); lv.setBlockAndUpdate(at.offset(dx, -2, dz), Blocks.WATER.defaultBlockState()); }
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("fishing: no bot -> FAIL"); return; }
            b.moveTo(p.getX() + 1, p.getY(), p.getZ(), 0, 0); b.setMode(PartyBotEntity.NEUTRAL);
            b.inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 3));
            b.inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STRING, 2));
            b.inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE));
            com.xenowarriors.bot.BotFishing.forceForTest = true;
        }), 200);
        l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 20); }, 5);
        l.add("batch4_fishing", () -> { mc.options.hideGui = false; }, 400);
        l.add(null, () -> server(p -> {
            log("fishing: rods made " + com.xenowarriors.bot.BotFishing.rodsMade + ", trips " + com.xenowarriors.bot.BotFishing.trips + ", catches " + com.xenowarriors.bot.BotFishing.catches
                    + " -> " + (com.xenowarriors.bot.BotFishing.rodsMade > 0 && com.xenowarriors.bot.BotFishing.catches > 0 ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 5);
    }

    private static void taming(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (b == null) { log("taming: no bot -> FAIL"); return; }
            b.moveTo(p.getX() + 1, p.getY(), p.getZ(), 0, 0); b.setMode(PartyBotEntity.NEUTRAL);
            b.inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RABBIT, 16));
            var fox = net.minecraft.world.entity.EntityType.FOX.create(p.serverLevel());
            fox.moveTo(p.getX() + 5, p.getY(), p.getZ() + 3, 0, 0); fox.addTag("xw_b4");
            p.serverLevel().addFreshEntity(fox);
        }), 600);
        l.add(null, () -> server(p -> {
            int pets = com.xenowarriors.fox.FoxPets.records(p).size();
            log("taming: the Mikiko bot fed the fox " + com.xenowarriors.bot.BotTaming.tries + " times, your foxes " + pets + " -> " + (com.xenowarriors.bot.BotTaming.tries > 0 && pets > 0 ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 5);
    }

    private static void outfits(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 3)), 10);
        Batch3Tests.character(l, CharacterType.MIKIKO);
        int[] seen = new int[3];
        l.add(null, () -> server(p -> { seen[0] = com.xenowarriors.character.CharacterManager.outfit(p); p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 1); }), 10);
        Batch3Tests.character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> seen[1] = com.xenowarriors.character.CharacterManager.outfit(p)), 10);
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            seen[2] = com.xenowarriors.character.CharacterManager.outfit(p);
            log("outfits: Rhalix wore S3 (3); Mikiko then had " + seen[0] + ", Rhalix back " + seen[1] + ", Mikiko back " + seen[2] + " -> " + (seen[0] == 0 && seen[1] == 3 && seen[2] == 1 ? "PASS" : "FAIL"));
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.outfit = 0; d.outfits = new net.minecraft.nbt.CompoundTag(); });
        }), 5);
    }

    private static void autoMission(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (b != null) b.moveTo(p.getX() + 1, p.getY(), p.getZ(), 0, 0);
        }), 100);
        l.add(null, () -> server(p -> {
            var got = com.xenowarriors.compat.DmzBotQuests.autoAccept(p);
            var again = com.xenowarriors.compat.DmzBotQuests.autoAccept(p);
            log("automission: took on its own " + got + ", then (one at a time) " + again + " -> " + (!got.isEmpty() && again.isEmpty() ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 5);
    }

    // ------------------------------------------------------------------ Mikiko + Kaeru: Hekublade Scatter Barrage

    private static void hekuTeam(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (b == null) { log("hekuteam: no bot -> FAIL"); return; }
            b.moveTo(p.getX() + 1.5, p.getY(), p.getZ(), 0, 0); b.setMode(PartyBotEntity.NEUTRAL);
            com.xenowarriors.bot.BotTeamUlt.readyForTest(b);
            p.setYRot(0);
            for (int i = 0; i < 6; i++) {
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                double a = i * Math.PI / 3;
                z.moveTo(p.getX() + Math.cos(a) * 9, p.getY(), p.getZ() + Math.sin(a) * 9, 0, 0); z.setNoAi(true); z.addTag("xw_b4");
                var hp = z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH); if (hp != null) hp.setBaseValue(400); z.setHealth(400);
                p.serverLevel().addFreshEntity(z);
            }
        }), 40);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            var kinds = b == null ? java.util.List.of() : com.xenowarriors.party.TeamUltimate.kinds(p, b);
            boolean partner = b != null && com.xenowarriors.party.TeamUltimate.partners(p).contains(b);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            boolean ok = b != null && com.xenowarriors.party.TeamUltimate.startNow(p, b, com.xenowarriors.party.TeamUltimate.Kind.HEKUBLADE_BARRAGE);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            log("hekuteam: Kaeru is a partner " + partner + ", kinds " + kinds + ", started " + ok + " -> " + (partner && ok ? "PASS" : "FAIL"));
        }), 20);
        l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 30); }, 5);
        l.add("batch4_hekuteam_ring", () -> {}, 40);
        l.add("batch4_hekuteam_barrage", () -> {}, 45);
        l.add("batch4_hekuteam_finale", () -> { mc.options.hideGui = false; }, 30);
        l.add(null, () -> server(p -> {
            var zs = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(20), x -> x.getTags().contains("xw_b4"));
            long hurt = zs.stream().filter(z -> z.getHealth() < 400).count();
            long left = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, p.getBoundingBox().inflate(30), x -> x.getTags().contains("xw_hekublade_barrage")).size();
            log("hekuteam: blades sent " + com.xenowarriors.party.HekubladeBarrage.lastShots + ", hits " + com.xenowarriors.party.HekubladeBarrage.lastHits + ", zombies hurt " + hurt + "/" + zs.size()
                    + ", blades left over " + left + " -> " + (com.xenowarriors.party.HekubladeBarrage.lastShots >= 12 && hurt >= 5 && left == 0 ? "PASS" : "FAIL"));
            for (var z : zs) z.discard();
            Batch3Tests.clearBots(p);
        }), 5);
    }

    // ------------------------------------------------------------------ Epic Fight: armour and held items on our characters

    private static void efGear(Minecraft mc, MikikoTests.Steps l) {
        String[][] items = {{"iron_sword", "ef:SWORD_AUTO1"}, {"diamond_axe", "ef:AXE_AUTO1"}, {"iron_pickaxe", "ef:TOOL_AUTO1"}, {"shield", "guard"}};
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.MIKIKO}) {
            Batch3Tests.character(l, t);
            l.add(null, () -> server(p -> {
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
            }), 20);
            for (String[] it : items) {
                l.add(null, () -> server(p -> {
                    var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(it[0]));
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item));
                    com.xenowarriors.compat.epicfight.EfTest.play(p, "none");
                }), 15);
                l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 30, 8); }, 10);
                l.add("batch4_efgear_" + t.getId() + "_" + it[0] + "_idle", () -> {}, 3);
                l.add(null, () -> server(p -> com.xenowarriors.compat.epicfight.EfTest.play(p, it[1])), 6);
                l.add("batch4_efgear_" + t.getId() + "_" + it[0] + "_swing", () -> {}, 3);
                l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 8), 4);
                l.add("batch4_efgear_" + t.getId() + "_" + it[0] + "_back", () -> { mc.options.hideGui = false; }, 3);
            }
            l.add(null, () -> server(p -> { for (var s : net.minecraft.world.entity.EquipmentSlot.values()) p.setItemSlot(s, net.minecraft.world.item.ItemStack.EMPTY);
                log("efgear " + p.getName().getString() + " done: our armor drawn on Epic Fight's skeleton " + com.xenowarriors.client.compat.epicfight.ArmorPatchedLayer.drawn + " times -> "
                        + (com.xenowarriors.client.compat.epicfight.ArmorPatchedLayer.drawn > 0 ? "PASS" : "FAIL")); }), 5);
        }
    }

    // ------------------------------------------------------------------ Technica's motorcycle

    private static final Object[] BIKE = new Object[2];

    private static void technicaBike(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.TECHNICA);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            var lv = p.serverLevel();
            BlockPos at = p.blockPosition();
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 12; dz++) { for (int dy = 0; dy <= 4; dy++) lv.setBlockAndUpdate(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState()); lv.setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.STONE.defaultBlockState()); }
            boolean key = false;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).getItem() == com.xenowarriors.vehicle.VehicleItems.ITEMS_BY_KIND.get(com.xenowarriors.vehicle.VehicleKind.TECHNICA_BIKE).get()) key = true;
            com.xenowarriors.vehicle.TechnicaBike.setAttachmentsForTest(p, 0b11111);
            p.setYRot(0);
            var v = com.xenowarriors.vehicle.TechnicaBike.summon(p, p);
            BIKE[0] = v;
            log("bike: Technica has her key " + key + "; summoned " + (v != null) + " attachments " + (v == null ? -1 : v.attachments()) + " health " + (v == null ? 0 : v.getMaxHealth())
                    + " top speed " + (v == null ? 0 : Math.round(v.topSpeed() * 100) / 100.0) + " flies " + (v != null && v.canHover())
                    + " -> " + (v != null && v.attachments() == 0b11111 && v.getMaxHealth() >= 119 && v.canHover() ? "PASS" : "FAIL"));
        }), 120);
        l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 140, 15); }, 10);
        l.add("batch4_bike", () -> {}, 3);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 220, 15), 10);
        l.add("batch4_bike_back", () -> { mc.options.hideGui = false; }, 3);
        l.add(null, () -> server(p -> {
            if (!(BIKE[0] instanceof com.xenowarriors.vehicle.VehicleEntity v)) return;
            p.startRiding(v, true);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(v.getX(), v.getY(), v.getZ() + 9, 180, 0); z.setNoAi(true); z.addTag("xw_b4");
            p.serverLevel().addFreshEntity(z);
            BIKE[1] = z;
        }), 10);
        l.add(null, () -> server(p -> {
            if (!(BIKE[1] instanceof net.minecraft.world.entity.monster.Zombie z)) return;
            float hp = z.getHealth();
            int s0 = com.xenowarriors.vehicle.TechnicaBike.shots, r0 = com.xenowarriors.vehicle.TechnicaBike.rockets;
            com.xenowarriors.vehicle.TechnicaBike.fire(p, 0, z);
            com.xenowarriors.vehicle.TechnicaBike.fire(p, 1, z);
            log("bike: driving, blasters " + (com.xenowarriors.vehicle.TechnicaBike.shots - s0) + " rockets " + (com.xenowarriors.vehicle.TechnicaBike.rockets - r0) + ", zombie " + hp + " -> " + z.getHealth()
                    + " -> " + (z.getHealth() < hp || !z.isAlive() ? "PASS" : "FAIL"));
        }), 5);
        l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 15); }, 8);
        l.add("batch4_bike_ride", () -> { mc.options.hideGui = false; }, 3);
        // a full ride: a bot that can't fit (here Technica, with Mikiko playing) calls her bike and follows
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            p.stopRiding();
            for (var e : p.serverLevel().getEntitiesOfClass(com.xenowarriors.vehicle.VehicleEntity.class, p.getBoundingBox().inflate(30))) e.discard();
            com.xenowarriors.vehicle.TechnicaBike.forgetForTest(p.getUUID());
            var car = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.MOTORBIKE).get().create(p.serverLevel());
            car.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
            p.serverLevel().addFreshEntity(car);
            p.startRiding(car, true);
            for (int i = 1; i < car.kind.seats; i++) { var st = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel()); st.moveTo(car.getX(), car.getY(), car.getZ()); p.serverLevel().addFreshEntity(st); st.startRiding(car, true); st.addTag("xw_b4"); }
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.TECHNICA, null);
            if (b != null) { b.moveTo(p.getX() - 2, p.getY(), p.getZ() - 2, 0, 0); b.setMode(PartyBotEntity.NEUTRAL); }
        }), 60);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            boolean got = b != null && com.xenowarriors.bot.BotRides.boardForTest(b, p);
            boolean onBike = b != null && b.getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity v && v.kind.technica() && v.getFirstPassenger() == b;
            log("bike: your motorbike is full -> Technica bot got a ride " + got + ", driving her own motorcycle " + onBike + " -> " + (onBike ? "PASS" : "FAIL"));
        }), 10);
        l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 20); }, 8);
        l.add("batch4_bike_follow", () -> { mc.options.hideGui = false; }, 3);
        l.add(null, () -> server(p -> p.stopRiding()), 30);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            long bikes = p.serverLevel().getEntitiesOfClass(com.xenowarriors.vehicle.VehicleEntity.class, p.getBoundingBox().inflate(40), x -> x.kind.technica()).size();
            log("bike: you got off -> bot off " + (b != null && !b.isPassenger()) + ", summoned bike gone " + (bikes == 0) + " -> " + (b != null && !b.isPassenger() && bikes == 0 ? "PASS" : "FAIL"));
            for (var e : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(40), x -> x.getTags().contains("xw_b4") || x instanceof com.xenowarriors.vehicle.VehicleEntity)) e.discard();
            Batch3Tests.clearBots(p);
        }), 5);
    }

    // ------------------------------------------------------------------ a Flying Nimbus from Korin

    private static void nimbus(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        boolean[] before = new boolean[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.STEVIE, null);
            if (b == null) { log("nimbus: no bot -> FAIL"); return; }
            b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 3, 180, 0);
            b.setMode(PartyBotEntity.NEUTRAL);
            before[0] = b.canFly();
        }), 40);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            if (b == null) return;
            boolean got = com.xenowarriors.compat.DmzBotNimbus.receiveForTest(b);
            boolean again = com.xenowarriors.compat.DmzBotNimbus.receiveForTest(b);
            log("nimbus: Stevie can fly before " + before[0] + "; Korin gave a nimbus " + got + " (a second one " + again + "), rides it " + b.nimbus() + ", can fly " + b.canFly()
                    + " -> " + (!before[0] && got && !again && b.nimbus() && b.canFly() ? "PASS" : "FAIL"));
            b.setNoGravity(true);
            b.moveTo(p.getX() + 1.5, p.getY() + 1.5, p.getZ() + 3, 180, 0);
        }), 6);
        l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 10); }, 6);
        l.add("batch4_nimbus", () -> { mc.options.hideGui = false; }, 4);
        l.add(null, () -> server(Batch3Tests::clearBots), 5);
    }

    // ------------------------------------------------------------------ fusion look previews

    private static void fusionLook(Minecraft mc, MikikoTests.Steps l) {
        String[] keys = {"fusion_rhaliko", "fusion_mikuri"};
        for (String k : keys) {
            CharacterType base = k.equals("fusion_rhaliko") ? CharacterType.RHALIX : CharacterType.YURI;
            Batch3Tests.character(l, base);
            l.add(null, () -> { com.xenowarriors.client.FusionLook.preview = com.xenowarriors.client.FusionLook.LOOKS.get(k); mc.options.hideGui = true; }, 10);
            l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 5), 15);
            l.add("batch4_fusion_" + k, () -> {}, 5);
            l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 5), 15);
            l.add("batch4_fusion_" + k + "_back", () -> {}, 5);
            l.add(null, () -> {
                var lk = com.xenowarriors.client.FusionLook.LOOKS.get(k);
                log("fusionlook " + k + ": model " + (lk.model() != null) + " face " + com.xenowarriors.client.render.CharacterDetailLayer.faceTexture(mc.player)
                        + " pair rhalix+mikiko=" + com.xenowarriors.compat.FusionLooks.lookFor("mikiko", "rhalix") + " mikiko+yuri=" + com.xenowarriors.compat.FusionLooks.lookFor("yuri", "mikiko")
                        + " -> " + (lk.model() != null && com.xenowarriors.client.render.CharacterDetailLayer.faceTexture(mc.player).getPath().contains(k) ? "PASS" : "FAIL"));
                com.xenowarriors.client.FusionLook.preview = null; mc.options.hideGui = false;
            }, 3);
        }
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.MIKIKO, CharacterType.YURI}) {   // (the originals, to compare)
            Batch3Tests.character(l, t);
            l.add(null, () -> { mc.options.hideGui = true; Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 5); }, 15);
            l.add("batch4_fusion_orig_" + t.getId(), () -> { mc.options.hideGui = false; }, 5);
        }
    }

    // ------------------------------------------------------------------ lock on to a bot you're sparring

    private static void sparLock(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b != null) { b.moveTo(p.getX(), p.getY(), p.getZ() + 3, 180, 0); b.setMode(PartyBotEntity.NEUTRAL); }
        }), 30);
        boolean[] before = new boolean[1];
        l.add(null, () -> {
            var b = mc.level.getEntitiesOfClass(PartyBotEntity.class, mc.player.getBoundingBox().inflate(10)).stream().findFirst().orElse(null);
            before[0] = b != null && com.xenowarriors.client.LockOn.attackable(b);
            server(p -> { for (PartyBotEntity x : PartyBots.bots(p)) PartyBots.action(p, x.getId(), PartyBots.ACT_SPAR, 0); });
        }, 20);
        l.add(null, () -> {
            var b = mc.level.getEntitiesOfClass(PartyBotEntity.class, mc.player.getBoundingBox().inflate(10)).stream().findFirst().orElse(null);
            boolean during = b != null && com.xenowarriors.client.LockOn.attackable(b);
            log("sparlock: lockable before " + before[0] + ", while sparring you " + during + " -> " + (!before[0] && during ? "PASS" : "FAIL"));
            server(p -> { for (PartyBotEntity x : PartyBots.bots(p)) x.setSparring(false); });
        }, 10);
        l.add(null, () -> {
            var b = mc.level.getEntitiesOfClass(PartyBotEntity.class, mc.player.getBoundingBox().inflate(10)).stream().findFirst().orElse(null);
            boolean after = b != null && com.xenowarriors.client.LockOn.attackable(b);
            log("sparlock: spar over -> lockable " + after + " -> " + (!after ? "PASS" : "FAIL"));
            server(Batch3Tests::clearBots);
        }, 5);
    }

    // ------------------------------------------------------------------ DragonMineZ's own aura on our characters

    private static void dmzAura(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.RHALIX);
        int[] q = new int[1];
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> { DmzTests.make(p, "saiyan"); p.teleportTo(p.getX(), p.getY(), p.getZ()); }); }, 40);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            setForm(p, "supersaiyan", "supersaiyan");
            Dmz6Tests.call(Dmz6Tests.call(DmzPerCharacter.live(p), "getStatus"), "setAuraActive", true);
            Dmz6Tests.call(Dmz6Tests.call(DmzPerCharacter.live(p), "getStatus"), "setPermanentAura", true);
            DmzPerCharacter.syncStats(p);
            q[0] = com.xenowarriors.client.compat.DmzAuraBridge.queued;
        }), 30);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 10), 20);
        l.add("batch4_dmzaura", () -> {}, 5);
        l.add(null, () -> {
            int n = com.xenowarriors.client.compat.DmzAuraBridge.queued - q[0];
            log("dmzaura: aura on (client sees " + com.xenowarriors.client.compat.DmzAuraBridge.auraOn(mc.player) + ") -> DragonMineZ aura queued " + n + " times, bridge works " + com.xenowarriors.client.compat.DmzAuraBridge.works() + " (" + com.xenowarriors.client.compat.DmzAuraBridge.lastWhy + ") -> " + (n > 0 ? "PASS" : "FAIL"));
            server(p -> { var st = Dmz6Tests.call(DmzPerCharacter.live(p), "getStatus"); Dmz6Tests.call(st, "setAuraActive", false); Dmz6Tests.call(st, "setPermanentAura", false); setForm(p, null, null); });
        }, 5);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 10), 20);
        l.add("batch4_dmzaura_off", () -> {}, 5);
    }

    // ------------------------------------------------------------------ form faces and the SSJ4 look

    private static void setForm(ServerPlayer p, String group, String form) {
        Object d = DmzPerCharacter.live(p);
        Object ch = Dmz6Tests.call(d, "getCharacter");
        if (form == null) Dmz6Tests.call(ch, "clearActiveForm"); else Dmz6Tests.call(ch, "setActiveForm", group, form);
        DmzPerCharacter.syncStats(p);
    }

    private static void formLook(Minecraft mc, MikikoTests.Steps l, boolean ssj4Only) {
        String[][] forms = ssj4Only ? new String[][]{{"supersaiyan", "supersaiyan4"}} : new String[][]{{"supersaiyan", "supersaiyan"}, {"supersaiyan", "supersaiyan3"}, {"supersaiyan", "supersaiyan4"}, {"legendaryforms", "ssjfullpower"}};
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.MIKIKO, CharacterType.KYRIE, CharacterType.YURI}) {
            Batch3Tests.character(l, t);
            l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> { DmzTests.make(p, "saiyan"); p.teleportTo(p.getX(), p.getY(), p.getZ()); }); }, 40);
            for (String[] fm : forms) {
                l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> setForm(p, fm[0], fm[1])); }, 10);
                l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 8); }, 60);
                l.add("batch4_form_" + t.getId() + "_" + fm[1], () -> {
                    var me = mc.player;
                    String form = com.xenowarriors.client.compat.DmzForms.form(me), set = com.xenowarriors.client.compat.DmzForms.faceSet(me);
                    int hair = com.xenowarriors.client.compat.DmzForms.hairStyle(me);
                    boolean own = com.xenowarriors.client.compat.DmzForms.ssj4Own(me), dmzDraws = com.xenowarriors.client.compat.DmzForms.dmzDraws(me);
                    String want = switch (fm[1]) { case "supersaiyan3" -> "ssj3"; case "supersaiyan4" -> "ssj4"; case "ssjfullpower" -> "lssj"; default -> "ssj"; };
                    boolean shirtless = t != CharacterType.KYRIE && t != CharacterType.YURI;
                    boolean ok = want.equals(set) && (!fm[1].equals("supersaiyan4") || (own && hair == 4 && !dmzDraws && me.getSkinTextureLocation().getPath().endsWith("_ssj4.png") == shirtless));
                    if (fm[1].equals("supersaiyan4")) { int fd = com.xenowarriors.client.render.CharacterDetailLayer.furDraws; log("formlook " + t.getId() + " ssj4 fur drawn " + fd + " -> " + (fd > 0 ? "PASS" : "FAIL")); com.xenowarriors.client.render.CharacterDetailLayer.furDraws = 0; }
                    log("formlook " + t.getId() + " " + fm[1] + ": form=" + form + " faces=" + set + " hair=" + hair + " ownSSJ4=" + own + " dmzDraws=" + dmzDraws
                            + " skin=" + me.getSkinTextureLocation().getPath() + " -> " + (ok ? "PASS" : "FAIL"));
                }, 3);
                if (fm[1].equals("supersaiyan4")) {
                    l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 140, 15), 20);
                    l.add("batch4_form_" + t.getId() + "_ssj4_back", () -> {}, 3);
                }
            }
            l.add(null, () -> server(p -> setForm(p, null, null)), 10);
        }
    }

    // ------------------------------------------------------------------ mining together

    private static final BlockPos[] DIG = new BlockPos[1];
    private static final float[] SPEED = new float[1];

    private static void coMine(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            BlockPos at = p.blockPosition();
            var lv = p.serverLevel();
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 3; dy++) lv.setBlockAndUpdate(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) lv.setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
            BlockPos dig = at.offset(0, 0, 2);                                      // (a stone block right ahead of her)
            lv.setBlockAndUpdate(dig, Blocks.STONE.defaultBlockState());
            DIG[0] = dig;
            p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            p.setYRot(0); p.setXRot(25); p.setYHeadRot(0);
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
            SPEED[0] = p.getDigSpeed(lv.getBlockState(dig), dig);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b != null) { b.moveTo(at.getX() - 3.5, at.getY(), at.getZ() - 2.5, 0, 0); b.setMode(PartyBotEntity.NEUTRAL); }
            com.xenowarriors.mining.CoMining.startForTest(p, dig);
            log("comine: digging " + dig + " alone at speed " + Batch3Tests.f1(SPEED[0]) + ", bot " + (b != null));
        }), 30);
        l.add(null, () -> mc.player.setXRot(25), 100);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.FIRST_PERSON, 0, 25), 5);
        l.add("batch4_comine_view", () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            int n = com.xenowarriors.mining.CoMining.helpersForTest(p);
            float sp = p.getDigSpeed(p.level().getBlockState(DIG[0]), DIG[0]);
            boolean inWay = b != null && com.xenowarriors.mining.CoMining.inTheWay(b, p, DIG[0]);
            boolean ok = n >= 1 && sp > SPEED[0] * 1.4F && !inWay;
            log("comine: bot at " + (b == null ? "-" : b.blockPosition()) + " helpers=" + n + " speed " + Batch3Tests.f1(SPEED[0]) + " -> " + Batch3Tests.f1(sp)
                    + " bot in your view=" + inWay + " -> " + (ok ? "PASS" : "FAIL"));
        }), 3);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 30), 5);
        l.add("batch4_comine_side", () -> {}, 5);
        l.add(null, () -> server(p -> {
            com.xenowarriors.mining.CoMining.stopForTest(p);
        }), 40);
        l.add(null, () -> server(p -> {
            int n = com.xenowarriors.mining.CoMining.helpersForTest(p);
            log("comine: stopped digging -> helpers " + n + " -> " + (n == 0 ? "PASS" : "FAIL"));
            p.setYRot(180); p.connection.teleport(p.getX(), p.getY(), p.getZ(), 180, 0);   // (looking away, not swinging)
            com.xenowarriors.mining.CoMining.startForTest(p, DIG[0], 0);          // a click that never got its "stop"
        }), 60);
        l.add(null, () -> server(p -> {
            boolean gone = com.xenowarriors.mining.CoMining.digging(p) == null;
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            boolean helping = b != null && com.xenowarriors.mining.CoMining.botHelpingForTest(b);
            log("comine: a stale click (no swinging, looking away) is dropped: " + gone + ", bot still digging at it " + helping + " -> " + (gone && !helping ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 10);
    }

    // ------------------------------------------------------------------ bots learning forms

    private static void botForms(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            DmzTests.make(p, "saiyan");
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("botforms: no bot -> FAIL"); return; }
            com.xenowarriors.compat.DmzBots.ensureRace(b);
            b.setNoAi(true);
        }); }, 40);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            if (b == null) return;
            log("botforms: setup " + com.xenowarriors.compat.DmzBotForms.setupForTest(b, "saiyan", 2_000_000F));
            int before = com.xenowarriors.compat.DmzBotForms.levelForTest(b, "superforms");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(" | ").append(com.xenowarriors.compat.DmzBotForms.learn(b, true));
            int after = com.xenowarriors.compat.DmzBotForms.levelForTest(b, "superforms");
            log("botforms: learned" + sb + " (superform " + before + " -> " + after + ") -> " + (com.xenowarriors.compat.DmzBotForms.learned >= 1 ? "PASS" : "FAIL"));
            String again = com.xenowarriors.compat.DmzBotForms.learn(b, false);
            log("botforms: right after, not forced: " + again + " -> " + (again.equals("too soon") ? "PASS" : "FAIL"));
            com.xenowarriors.compat.DmzBotForms.setupForTest(b, "saiyan", 0F);
            String broke = com.xenowarriors.compat.DmzBotForms.learn(b, true);
            log("botforms: no TP: " + broke + " -> " + (broke.startsWith("nothing") ? "PASS" : "FAIL"));
            for (String s : com.xenowarriors.compat.DmzBotForms.LOG) log("botforms log: " + s);
        }); }, 20);
    }

    // ------------------------------------------------------------------ Kill / Lexy: transformations on the key

    private static void ultForms(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.KILL);
        l.add(null, () -> server(p -> {
            com.xenowarriors.karma.Karma.add(p, 100F);
            String ult = com.xenowarriors.skill.SkillPool.ultimate(CharacterType.KILL).getId();
            com.xenowarriors.skill.UltForms.resetForTest(p);
            boolean handled = com.xenowarriors.skill.UltForms.press(p);
            boolean on = com.xenowarriors.skill.ffr.SrSkills.devilActive(p);
            log("ultforms: Kill's ultimate=" + ult + ", transform key -> Devil Unleashed on=" + on + " -> " + (handled && on && ult.equals("hellclaw_rend") ? "PASS" : "FAIL"));
        }), 10);
        l.add(null, () -> server(p -> {
            float k0 = com.xenowarriors.karma.Karma.get(p);
            com.xenowarriors.skill.UltForms.press(p);
            log("ultforms: pressed again while on: Karma " + k0 + " -> " + com.xenowarriors.karma.Karma.get(p) + " -> " + (com.xenowarriors.karma.Karma.get(p) == k0 ? "PASS" : "FAIL"));
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX(), p.getY(), p.getZ() + 3, 180, 0); z.setNoAi(true); p.serverLevel().addFreshEntity(z);
            z.addTag("xw_b4");
            boolean used = com.xenowarriors.skill.SkillPool.ultimate(CharacterType.KILL).use(p);
            log("ultforms: Hellclaw Rend used=" + used);
        }), 10);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 15), 20);
        l.add("batch4_ult_hellclaw", () -> {}, 3);
        l.add(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(20), e -> e.getTags().contains("xw_b4")).forEach(e -> e.discard());
        }), 40);
        Batch3Tests.character(l, CharacterType.LEXY);
        l.add(null, () -> server(p -> {
            com.xenowarriors.karma.Karma.add(p, 100F);
            String ult = com.xenowarriors.skill.SkillPool.ultimate(CharacterType.LEXY).getId();
            com.xenowarriors.skill.UltForms.resetForTest(p);
            boolean handled = com.xenowarriors.skill.UltForms.press(p);
            boolean on = com.xenowarriors.skill.sp.SpState.persephone(p);
            log("ultforms: Lexy's ultimate=" + ult + ", transform key -> Persephone on=" + on + " -> " + (handled && on && ult.equals("maynaya_crash") ? "PASS" : "FAIL"));
            for (int i = 0; i < 3; i++) {
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(p.getX() + i * 2 - 2, p.getY(), p.getZ() + 4, 180, 0); z.setNoAi(true); z.addTag("xw_b4"); p.serverLevel().addFreshEntity(z);
            }
        }), 10);
        l.add(null, () -> server(p -> log("ultforms: Maynaya Crash used=" + com.xenowarriors.skill.SkillPool.ultimate(CharacterType.LEXY).use(p))), 10);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 15), 50);
        l.add("batch4_ult_maynaya", () -> {}, 3);
        l.add(null, () -> server(p -> p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(30), e -> e.getTags().contains("xw_b4")).forEach(e -> e.discard())), 40);
    }

    // ------------------------------------------------------------------ Crimson Heart Seed, careful bots, pods

    private static void kaioken(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            var seed = new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.CRIMSON_HEART_SEED.get());
            seed.finishUsingItem(p.level(), p);
            boolean g = com.xenowarriors.kaioken.KaiokenGuard.guarded(p);
            log("kaioken: ate a seed -> Crimson Control " + g + " -> " + (g ? "PASS" : "FAIL"));
            p.removeEffect(com.xenowarriors.ModEffects.CRIMSON_CONTROL.get());
            boolean still = com.xenowarriors.kaioken.KaiokenGuard.guarded(p);
            log("kaioken: for good - effect gone (died, milk), still guarded " + still + " -> " + (still ? "PASS" : "FAIL"));
            PartyBotEntity a = PartyBots.spawn(p, CharacterType.RHALIX, null), b = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (a == null || b == null) { log("kaioken: bots FAIL"); return; }
            a.moveTo(p.getX() + 1.5, p.getY(), p.getZ() + 1.5, 0, 0); b.moveTo(p.getX() - 1.5, p.getY(), p.getZ() + 1.5, 0, 0);
            a.inventory.addItem(new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.CRIMSON_HEART_SEED.get(), 3));
            log("kaioken: bots set up; bot pod threshold " + com.xenowarriors.gov.HealingPods.BOT_LOW);
        }), 20);
        l.add(null, () -> server(p -> {
            var bots = PartyBots.bots(p);
            com.xenowarriors.kaioken.KaiokenGuard.share(p);
            int gave = com.xenowarriors.kaioken.KaiokenGuard.botGave;
            PartyBotEntity b = bots.stream().filter(x -> x.character() == CharacterType.KAERU).findFirst().orElse(null);
            boolean has = b != null && b.inventory.countItem(com.xenowarriors.item.ModItems.CRIMSON_HEART_SEED.get()) > 0;
            log("kaioken: a bot with spares handed one over: gave=" + gave + " Kaeru has one=" + has + " -> " + (has ? "PASS" : "FAIL"));
            boolean ate = b != null && com.xenowarriors.kaioken.KaiokenGuard.botBeforeKaioken(b);
            log("kaioken: Kaeru eats it before Kaioken: guarded=" + ate + " seeds left " + (b == null ? -1 : b.inventory.countItem(com.xenowarriors.item.ModItems.CRIMSON_HEART_SEED.get())) + " -> " + (ate ? "PASS" : "FAIL"));
        }), 140);
        l.add(null, () -> server(p -> {
            com.xenowarriors.kaioken.KaiokenGuard.forced = true;
            var w = net.minecraft.world.entity.EntityType.ELDER_GUARDIAN.create(p.serverLevel());
            w.moveTo(p.getX(), p.getY(), p.getZ() + 3, 0, 0); w.setNoAi(true);
            p.serverLevel().addFreshEntity(w);
            int before = com.xenowarriors.kaioken.KaiokenGuard.dropped;
            w.hurt(p.damageSources().playerAttack(p), 10000F);
            com.xenowarriors.kaioken.KaiokenGuard.forced = false;
            log("kaioken: boss (forced chance) dropped a seed: " + (com.xenowarriors.kaioken.KaiokenGuard.dropped > before ? "PASS" : "FAIL (boss=" + com.xenowarriors.karma.KarmaSensing.boss(w) + ")"));
            Batch3Tests.clearBots(p);
        }), 10);
    }

    private static void botWeights(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            Batch3Tests.clearBots(p);
            DmzTests.make(p, "saiyan");
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("weights: no bot FAIL"); return; }
            com.xenowarriors.compat.DmzBots.ensureRace(b);
            b.setNoAi(true);
        }); }, 40);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            if (b == null) return;
            log("weights: before " + com.xenowarriors.compat.DmzBotWeights.stateForTest(b));
            boolean got = com.xenowarriors.compat.DmzBotWeights.receiveForTest(b, "roshi");
            String st = com.xenowarriors.compat.DmzBotWeights.stateForTest(b);
            log("weights: asked Roshi -> got " + got + ", " + st + " -> " + (got && !st.contains("worn none") ? "PASS" : "FAIL"));
            boolean again = com.xenowarriors.compat.DmzBotWeights.receiveForTest(b, "kingkai");
            log("weights: has weights, asks King Kai again: " + again + " -> " + (!again ? "PASS" : "FAIL"));
            com.xenowarriors.compat.DmzBotMinigames.learnAllForTest(b);
            float tp0 = com.xenowarriors.compat.DmzBotMinigames.tpForTest(b);
            String start = com.xenowarriors.compat.DmzBotMinigames.start(b, true);
            String fin = com.xenowarriors.compat.DmzBotMinigames.finishForTest(b);
            float tp1 = com.xenowarriors.compat.DmzBotMinigames.tpForTest(b);
            log("minigames: " + start + " / " + fin + " TP " + tp0 + " -> " + tp1 + " -> " + (tp1 > tp0 ? "PASS" : "FAIL"));
            for (String s : com.xenowarriors.compat.DmzBotMinigames.LOG) log("minigames log: " + s);
        }); }, 20);
    }

    // ------------------------------------------------------------------ smarter bots, Elder Kai

    private static void botAi(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (b == null) { log("botai: no bot FAIL"); return; }
            b.moveTo(p.getX() + 1, p.getY(), p.getZ() + 1, 0, 0);
            var weak = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            weak.moveTo(p.getX(), p.getY(), p.getZ() + 30, 0, 0); weak.setNoAi(true); weak.addTag("xw_b4"); weak.setHealth(4F);
            p.serverLevel().addFreshEntity(weak);
            var big = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            big.moveTo(p.getX() + 2, p.getY(), p.getZ() + 30, 0, 0); big.setNoAi(true); big.addTag("xw_b4");
            big.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(300); big.setHealth(300);
            p.serverLevel().addFreshEntity(big);
            int hw = PartyBots.hitsToKill(b, weak), hb = PartyBots.hitsToKill(b, big);
            log("botai: hits to kill a weak zombie " + hw + ", a 300 hp one " + hb + " -> " + (hw <= 5 && hb > 5 ? "PASS" : "FAIL"));
            b.setHealth(b.getMaxHealth() * 0.25F);
            var foe = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            foe.moveTo(b.getX() + 2, b.getY(), b.getZ(), 0, 0); foe.addTag("xw_b4"); foe.addTag("xw_b4foe"); foe.setTarget(b);
            foe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
            p.serverLevel().addFreshEntity(foe);
            b.setTarget(foe);
            START[0] = b.distanceTo(foe);
        }), 20);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            var foes = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(80), z -> z.getTags().contains("xw_b4foe"));
            float d = b == null || foes.isEmpty() ? -1 : b.distanceTo(foes.get(0));
            if (!foes.isEmpty()) foes.get(0).setTarget(b);
            log("botai: hurt bot (25%) retreats=" + (b == null ? -1 : b.retreats) + " distance " + Batch3Tests.f1(START[0]) + " -> " + Batch3Tests.f1(d)
                    + " -> " + (b != null && b.retreats > 0 && d > START[0] + 3 ? "PASS" : "FAIL"));
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(80), e -> e.getTags().contains("xw_b4")).forEach(e -> e.discard());
            Batch3Tests.clearBots(p);
        }), 200);
    }
    private static final float[] START = new float[1];

    private static void oldKai(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            DmzTests.make(p, "saiyan");
            Object d = DmzPerCharacter.live(p);
            Object skills = Dmz6Tests.call(d, "getSkills");
            Dmz6Tests.call(skills, "setSkillLevel", "ultimate", 0);
            Dmz6Tests.call(skills, "setSkillLevel", "potentialunlock", 1);
            Dmz6Tests.call(Dmz6Tests.call(d, "getResources"), "setAlignment", 50);
            String low = com.xenowarriors.compat.DmzOldKai.unleash(p);
            Dmz6Tests.call(skills, "setSkillLevel", "potentialunlock", 3);
            String ok = com.xenowarriors.compat.DmzOldKai.unleash(p);
            Object lv = Dmz6Tests.call(skills, "getSkillLevel", "ultimate");
            log("oldkai: Potential Unlock 1 -> " + low + "; level 3, alignment 50 -> " + ok + " (ultimate level " + lv + ") -> " + (low.startsWith("not yet") && ok.equals("unleashed") ? "PASS" : "FAIL"));
        }); }, 30);
    }

    // ------------------------------------------------------------------ bots picking things up, music

    private static void capsules(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[1];
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> { DmzTests.make(p, "saiyan"); Batch3Tests.clearBots(p); b[0] = botAt(p, CharacterType.KAERU, 2, 2); }); }, 40);
        l.add(null, () -> { Dmz6Tests.noDmzScreen(mc); server(p -> {
            com.xenowarriors.compat.DmzBots.ensureRace(b[0]);
            float[] s0 = com.xenowarriors.compat.DmzBotTraining.snapshot(b[0]);
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", "red_capsule"));
            b[0].inventory.addItem(new ItemStack(item, 2));
            boolean used = false;
            try { used = com.xenowarriors.compat.DmzBotCapsules.useOne(b[0]); } catch (Throwable t) { log("capsules: error " + t); }
            float[] s1 = com.xenowarriors.compat.DmzBotTraining.snapshot(b[0]);
            int left = b[0].inventory.countItem(item);
            log("capsules: Kaeru used a red capsule " + used + ", total stats " + (s0 == null ? "?" : (int) s0[1]) + " -> " + (s1 == null ? "?" : (int) s1[1]) + ", capsules left " + left
                    + " -> " + (used && s0 != null && s1 != null && s1[1] > s0[1] && left == 1 ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }); }, 20);
    }

    private static void xenoBlasts(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[1];
        net.minecraft.world.entity.LivingEntity[] dummy = new net.minecraft.world.entity.LivingEntity[1];
        // a Rhalix bot, low, uses his last resort
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            b[0] = botAt(p, CharacterType.RHALIX, 3, 0);
            var g = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            g.moveTo(p.getX() + 3, p.getY(), p.getZ() + 12, 180, 0); g.setNoAi(true);
            p.serverLevel().addFreshEntity(g); dummy[0] = g;
            b[0].karma = 100; b[0].setHealth(b[0].getMaxHealth() * 0.2F); b[0].setTarget(g);
            boolean went = com.xenowarriors.skill.XenoBlast.botLastResort(b[0], g);
            log("xenoblasts: Rhalix bot at 20% health uses his last resort " + went + " (karma now " + Math.round(b[0].karma) + ") -> " + (went && b[0].karma == 0 ? "PASS" : "FAIL"));
        }), 20 * 4);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 10), 5);
        l.add("batch4_xeno_bot_lastresort", () -> {}, 3);
        l.add(null, () -> server(p -> { b[0].setHealth(b[0].getMaxHealth()); b[0].setTarget(null); }), 20 * 9);
        // the team Xeno Blast: you (Mikiko) and the Rhalix bot - he does his own Xeno Blast, the shockwave hits hard
        float[] hp = new float[1];
        l.add(null, () -> server(p -> {
            com.xenowarriors.karma.Karma.unlockForTest(p);
            b[0].teleportTo(p.getX() + 2, p.getY(), p.getZ());
            var g = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            g.moveTo(p.getX() + 20, p.getY(), p.getZ() + 20, 0, 0); g.setNoAi(true);
            g.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000); g.setHealth(2000);
            p.serverLevel().addFreshEntity(g); dummy[0] = g; hp[0] = 2000;
            int t0 = com.xenowarriors.skill.XenoBlast.teamBlasts;
            boolean used = com.xenowarriors.skill.XenoBlast.use(p);
            log("xenoblasts: team Xeno Blast started " + used + " (team " + (com.xenowarriors.skill.XenoBlast.teamBlasts - t0) + ")");
        }), 20 * 6);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 10), 5);
        l.add("batch4_xeno_team_charge", () -> {}, 3);
        l.add(null, () -> {}, 20 * 8);
        l.add(null, () -> server(p -> {
            float lost = hp[0] - dummy[0].getHealth();
            log("xenoblasts: team shockwave took " + Math.round(lost) + " off a 2000-health golem 20 blocks out (team x" + com.xenowarriors.skill.XenoBlast.TEAM_MULT + ") -> " + (lost > 0 ? "PASS" : "FAIL"));
            dummy[0].discard();
            Batch3Tests.clearBots(p);
        }), 20 * 4);
        // Mikiko at level 30,000: Soul Splitter Xeno Blast
        l.add(null, () -> server(p -> {
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { for (int i = 0; i < 3; i++) d.statLevel[i] = com.xenowarriors.stats.Stat.MAX_LEVEL; });
            com.xenowarriors.karma.Karma.unlockForTest(p);
            int s0 = com.xenowarriors.skill.XenoBlast.soulBlasts;
            boolean used = com.xenowarriors.skill.XenoBlast.use(p);
            log("xenoblasts: Mikiko level " + com.xenowarriors.stats.Stats.characterLevel(p) + " - Soul Splitter Xeno Blast " + (com.xenowarriors.skill.XenoBlast.soulBlasts - s0) + " -> " + (used && com.xenowarriors.skill.XenoBlast.soulBlasts > s0 ? "PASS" : "FAIL"));
        }), 20 * 3);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 10), 5);
        l.add("batch4_xeno_soul_charge", () -> {}, 3);
        l.add(null, () -> {}, 20 * 7);
        l.add("batch4_xeno_soul_fire", () -> {}, 3);
        l.add(null, () -> server(p -> p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { for (int i = 0; i < 3; i++) d.statLevel[i] = 1; })), 20 * 12);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void capture(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        net.minecraft.world.entity.animal.IronGolem[] g = new net.minecraft.world.entity.animal.IronGolem[1];
        net.minecraft.world.entity.LivingEntity[] pig = new net.minecraft.world.entity.LivingEntity[1];
        var skill = new com.xenowarriors.you.YouCapture.CaptureSkill();
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.setYRot(0); p.setXRot(10);
            var e = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            var look = p.getLookAngle().multiply(1, 0, 1).normalize();
            e.moveTo(p.getX() + look.x * 5, p.getY(), p.getZ() + look.z * 5, 180, 0);
            p.serverLevel().addFreshEntity(e); g[0] = e;
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, e.position().add(0, 1.4, 0));
        }), 20 * 2);
        l.add(null, () -> server(p -> {
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, g[0].position().add(0, 1.4, 0));
            boolean full = skill.use(p);
            log("capture: a golem at full health can't be captured " + !full + " (" + com.xenowarriors.you.YouCapture.why(p, g[0]) + ") -> " + (!full ? "PASS" : "FAIL"));
            g[0].setHealth(g[0].getMaxHealth() * 0.45F);
            g[0].addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 200, 1));
            g[0].addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
            int n0 = com.xenowarriors.party.Parties.pets(p);
            boolean ok = skill.use(p);
            boolean mine = p.getUUID().equals(com.xenowarriors.party.Parties.petOwner(g[0]));
            log("capture: Suppressed at 45% -> captured " + ok + ", yours " + mine + ", healed " + Math.round(g[0].getHealth()) + "/" + Math.round(g[0].getMaxHealth()) + ", pets " + n0 + "->" + com.xenowarriors.party.Parties.pets(p)
                    + " -> " + (ok && mine && g[0].getHealth() >= g[0].getMaxHealth() - 0.1F && com.xenowarriors.party.Parties.pets(p) == n0 + 1 ? "PASS" : "FAIL"));
        }), 20 * 2);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 6), 5);
        l.add("batch4_capture", () -> {}, 3);
        // it follows: you walk off 14 blocks
        double[] d0 = new double[1];
        l.add(null, () -> server(p -> { p.teleportTo(p.getX() + 14, p.getY(), p.getZ()); d0[0] = g[0].distanceTo(p); }), 20 * 6);
        l.add(null, () -> server(p -> {
            double d = g[0].distanceTo(p);
            log("capture: you walk off - it follows " + Math.round(d0[0]) + " -> " + Math.round(d) + " blocks -> " + (d < d0[0] - 4 ? "PASS" : "FAIL"));
            g[0].getPersistentData().putBoolean(com.xenowarriors.you.YouCapture.SIT, true);
            p.teleportTo(p.getX() - 14, p.getY(), p.getZ());
            d0[0] = g[0].distanceTo(p);
        }), 20 * 5);
        l.add(null, () -> server(p -> {
            double d = g[0].distanceTo(p);
            log("capture: told to wait - it stays " + Math.round(d0[0]) + " -> " + Math.round(d) + " blocks -> " + (Math.abs(d - d0[0]) < 1.5 ? "PASS" : "FAIL"));
            g[0].getPersistentData().putBoolean(com.xenowarriors.you.YouCapture.SIT, false);
            var e = net.minecraft.world.entity.EntityType.PIG.create(p.serverLevel());
            e.moveTo(p.getX() + 3, p.getY(), p.getZ() + 3, 0, 0); e.setNoAi(true);
            e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200); e.setHealth(200);
            p.serverLevel().addFreshEntity(e); pig[0] = e;
            p.setLastHurtMob(e);
        }), 20 * 5);
        l.add(null, () -> server(p -> {
            boolean hunt = g[0].getTarget() == pig[0], hurt = pig[0].getHealth() < 200;
            log("capture: you hit a pig - your golem goes for it " + hunt + " (hurt " + hurt + ") and never at you " + (g[0].getTarget() != p) + " -> " + ((hunt || hurt) && g[0].getTarget() != p ? "PASS" : "FAIL"));
            pig[0].discard();
            com.xenowarriors.you.YouCapture.release(g[0]);
            g[0].discard();
        }), 20 * 2);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void dbSpeed(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            var radar = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", "dball_radar"));
            int[] mins = new int[2];
            CharacterType[] who = {CharacterType.MAX, CharacterType.KAERU};
            int[] speed = {1, 10000};
            for (int k = 0; k < 2; k++) {
                int kk = k;
                p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                    var c = d.charStats.getCompound(who[kk].getId());
                    int[] lv = c.getIntArray("lv"); if (lv.length != 3) lv = new int[3];
                    lv[com.xenowarriors.stats.Stat.SPEED.ordinal()] = speed[kk];
                    c.putIntArray("lv", lv); d.charStats.put(who[kk].getId(), c);
                });
                PartyBotEntity b = botAt(p, who[k], 2 + k * 2, 3);
                b.inventory.setItem(0, new ItemStack(radar));
                int sp = PartyBots.statLevel(b, com.xenowarriors.stats.Stat.SPEED);
                String msg = com.xenowarriors.bot.OffDuty.sendSearching(p, b);
                var m = java.util.regex.Pattern.compile("about (\\d+) minutes").matcher(msg);
                mins[k] = m.find() ? Integer.parseInt(m.group(1)) : -1;
                log("dbspeed: " + who[k].getId() + " Speed " + sp + " -> \"" + msg + "\"");
            }
            log("dbspeed: Speed 1 takes " + mins[0] + " min (20-40), Speed 10,000 takes " + mins[1] + " min (factor " + String.format("%.2f", com.xenowarriors.bot.OffDuty.speedFactor(10000)) + ") -> "
                    + (mins[0] >= 20 && mins[0] <= 40 && mins[1] >= 2 && mins[1] <= 8 ? "PASS" : "FAIL"));
            // back from the search: always all 7 (even with one of them in your hands)
            var three = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", "dball3"));
            if (three != null) p.getInventory().add(new ItemStack(three));
            int balls = com.xenowarriors.bot.OffDuty.finishSearchForTest(p, CharacterType.MAX);
            log("dbspeed: Max back from the search with " + balls + " Dragon Balls (made full: " + com.xenowarriors.compat.DmzBotRadar.madeUp + ") -> " + (balls == 7 ? "PASS" : "FAIL"));
        }), 20);
    }

    private static void kaiBuilder(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        BlockPos[] crater = new BlockPos[1], blast = new BlockPos[1], gold = new BlockPos[1];
        int[] removed = new int[2];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            net.minecraft.server.level.ServerLevel w = p.server.getLevel(com.xenowarriors.kaiplanet.KaiPlanet.OTHERWORLD);
            if (w == null) { log("kaibuilder: no Otherworld -> FAIL"); return; }
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);      // (no race picker over the shots)
            p.teleportTo(w, 66.5, 196, 1092.5, 200, 20);
        }), 20 * 4);
        // he turns up (the blueprint is read, then about 10 s)
        l.add(null, () -> {}, 20 * 14);
        l.add(null, () -> server(p -> {
            net.minecraft.server.level.ServerLevel w = p.serverLevel();
            var b = com.xenowarriors.kaiplanet.KaiPlanet.builderForTest(w);
            int bp = com.xenowarriors.kaiplanet.KaiPlanet.plan() == null ? -1 : com.xenowarriors.kaiplanet.KaiPlanet.plan().size();
            log("kaibuilder: in " + w.dimension().location() + ", blueprint " + bp + " blocks, builder " + (b == null ? "none" : b.blockPosition().toShortString()) + " (spawned " + com.xenowarriors.kaiplanet.KaiPlanet.spawned + ") -> " + (b != null && bp > 100000 ? "PASS" : "FAIL"));
            int already = com.xenowarriors.kaiplanet.KaiPlanet.checkAllForTest(w);
            log("kaibuilder: holes already on the planet when we got here: " + already);
        }), 20);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 8), 5);
        l.add("batch4_kai_builder_home", () -> mc.setScreen(null), 3);
        // a crater (dug) and a blast, with a gold block someone put in one of the holes
        l.add(null, () -> server(p -> {
            net.minecraft.server.level.ServerLevel w = p.serverLevel();
            var plan = com.xenowarriors.kaiplanet.KaiPlanet.plan();
            BlockPos c = w.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(70, 0, 1104)).below();
            crater[0] = c;
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-2, -3, -2), c.offset(2, 0, 2))) if (plan.index(q) >= 0 && !w.getBlockState(q).isAir()) { w.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3); removed[0]++; }
            gold[0] = c.offset(2, 0, 2);
            w.setBlock(gold[0], net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            BlockPos e = w.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(46, 0, 1108));
            blast[0] = e;
            int before = 0;
            for (BlockPos q : BlockPos.betweenClosed(e.offset(-5, -5, -5), e.offset(5, 5, 5))) if (plan.index(q) >= 0 && !w.getBlockState(q).isAir()) before++;
            w.explode(null, e.getX() + .5, e.getY(), e.getZ() + .5, 4F, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
            int after = 0;
            for (BlockPos q : BlockPos.betweenClosed(e.offset(-5, -5, -5), e.offset(5, 5, 5))) if (plan.index(q) >= 0 && !w.getBlockState(q).isAir()) after++;
            removed[1] = before - after;
            p.teleportTo(c.getX() + 8.5, c.getY() + 6, c.getZ() + 0.5);
            log("kaibuilder: dug a crater (" + removed[0] + " blocks) at " + c.toShortString() + ", blasted " + removed[1] + " at " + e.toShortString() + ", gold block in a hole");
        }), 20 * 3);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 250, 25), 5);
        l.add("batch4_kai_builder_working", () -> mc.setScreen(null), 3);
        l.add(null, () -> {}, 20 * 20);
        l.add(null, () -> server(p -> {
            net.minecraft.server.level.ServerLevel w = p.serverLevel();
            var plan = com.xenowarriors.kaiplanet.KaiPlanet.plan();
            int left = 0;
            for (BlockPos[] area : new BlockPos[][]{{crater[0].offset(-2, -3, -2), crater[0].offset(2, 0, 2)}, {blast[0].offset(-5, -5, -5), blast[0].offset(5, 5, 5)}})
                for (BlockPos q : BlockPos.betweenClosed(area[0], area[1])) { int i = plan.index(q); if (i >= 0 && !q.equals(gold[0]) && w.getBlockState(q).isAir()) left++; }
            boolean goldStays = w.getBlockState(gold[0]).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK);
            log("kaibuilder: after 20 s - holes left " + left + " of " + (removed[0] + removed[1]) + " (restored total " + com.xenowarriors.kaiplanet.KaiPlanet.restored + "), the gold block someone put in stays " + goldStays
                    + " -> " + (left == 0 && goldStays && removed[0] + removed[1] > 10 ? "PASS" : "FAIL"));
        }), 20);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 250, 25), 5);
        l.add("batch4_kai_builder_fixed", () -> mc.setScreen(null), 3);
        // can't be hurt; sent far off the planet -> teleports back
        int[] tp0 = new int[1];
        l.add(null, () -> server(p -> {
            net.minecraft.server.level.ServerLevel w = p.serverLevel();
            var b = com.xenowarriors.kaiplanet.KaiPlanet.builderForTest(w);
            if (b == null) { log("kaibuilder: builder gone -> FAIL"); return; }
            float hp = b.getHealth();
            boolean hurt = b.hurt(p.damageSources().playerAttack(p), 50F) || b.getHealth() < hp;
            log("kaibuilder: hit for 50 - hurt " + hurt + " -> " + (!hurt ? "PASS" : "FAIL"));
            tp0[0] = com.xenowarriors.kaiplanet.KaiPlanet.teleports;
            b.blinkForTest(new net.minecraft.world.phys.Vec3(59 + 135, 150, 1097));
        }), 10);
        l.add(null, () -> server(p -> {
            var b = com.xenowarriors.kaiplanet.KaiPlanet.builderForTest(p.serverLevel());
            double d = b == null ? -1 : b.position().distanceTo(new net.minecraft.world.phys.Vec3(59, 120.5, 1097));
            log("kaibuilder: sent 135 blocks off the planet -> back, " + Math.round(d) + " from its middle (teleports " + (com.xenowarriors.kaiplanet.KaiPlanet.teleports - tp0[0]) + ") -> " + (b != null && d < 110 ? "PASS" : "FAIL"));
        }), 20 * 3);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void spread(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[5];
        CharacterType[] who = {CharacterType.RHALIX, CharacterType.ZANE, CharacterType.MAX, CharacterType.LEXY, CharacterType.GARRY};   // (no Kaeru: her Fox Heal stands them up before anyone walks over)
        // reviving: one bot down, four up -> only two go
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), "xeno partylimit 16");
            for (CharacterType x : who) com.xenowarriors.party.Bonds.set(p, x.getId(), 70, 0);
            for (int i = 0; i < 5; i++) b[i] = botAt(p, who[i], -6 + i * 3, 4 + (i % 2) * 3);
        }), 30);
        int[] most = new int[1];
        l.add(null, () -> server(p -> b[0].die(p.damageSources().generic())), 2);
        for (int k = 0; k < 12; k++) l.add(null, () -> server(p -> {             // (watched every few ticks: four of them revive fast)
            int rev = 0;
            for (int i = 1; i < 5; i++) if (com.xenowarriors.bot.PartyBots.revivingForTest(b[i]) == b[0]) rev++;
            most[0] = Math.max(most[0], rev);
        }), 4);
        l.add(null, () -> server(p -> log("spread revive: " + b[0].character().getId() + " down, 4 bots up -> at most " + most[0] + " reviving at once (want " + com.xenowarriors.bot.PartyBots.reviversFor(4) + ") -> " + (most[0] == 2 ? "PASS" : "FAIL"))), 5);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 30), 5);
        l.add("batch4_spread_revive", () -> {}, 3);
        // your target: about half of them go
        net.minecraft.world.entity.LivingEntity[] g = new net.minecraft.world.entity.LivingEntity[1];
        l.add(null, () -> server(p -> {
            b[0].revive();
            for (PartyBotEntity x : b) x.setMode(PartyBotEntity.NEUTRAL);
            var e = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
            e.moveTo(p.getX(), p.getY(), p.getZ() + 10, 180, 0); e.setNoAi(true);
            e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500); e.setHealth(500);
            p.serverLevel().addFreshEntity(e); g[0] = e;
        }), 20 * 3);
        l.add(null, () -> server(p -> {
            g[0].invulnerableTime = 0;
            g[0].hurt(p.damageSources().playerAttack(p), 1F);
            int on = 0;
            for (PartyBotEntity x : b) if (com.xenowarriors.bot.PartyBots.relayForTest(x) == g[0]) on++;
            log("spread attack: you hit a husk, 5 bots -> after it " + on + " (want 3) -> " + (on == 3 ? "PASS" : "FAIL"));
        }), 20);
        l.add(null, () -> server(p -> { g[0].discard(); for (PartyBotEntity x : b) { com.xenowarriors.bot.PartyBots.clearRelayForTest(x); x.setTarget(null); } }), 20);
        // mining: five ores apart -> each to its own
        java.util.List<BlockPos> ores = new java.util.ArrayList<>();
        l.add(null, () -> server(p -> {
            BlockPos c = p.blockPosition();
            for (int i = 0; i < 5; i++) { BlockPos o = c.offset(-8 + i * 4, 0, -6); p.level().setBlock(o, net.minecraft.world.level.block.Blocks.COAL_ORE.defaultBlockState(), 3); ores.add(o); }
            for (int i = 1; i < 4; i++) { b[i].teleportTo(c.getX() + 0.5 + (i - 2), c.getY(), c.getZ() - 2.5); b[i].setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE)); b[i].setMode(PartyBotEntity.MINING); }
            b[0].setMode(PartyBotEntity.NEUTRAL); b[4].setMode(PartyBotEntity.NEUTRAL);
        }), 20);
        java.util.Set<BlockPos> picked = new java.util.HashSet<>();
        l.add(null, () -> server(p -> { for (int i = 1; i < 4; i++) if (b[i].mineTargetForTest() != null) picked.add(b[i].mineTargetForTest()); }), 20 * 2);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 35), 5);
        l.add("batch4_spread_mining", () -> server(p -> {
            for (int i = 1; i < 4; i++) if (b[i].mineTargetForTest() != null) picked.add(b[i].mineTargetForTest());
            log("spread mining: 3 bots, 5 ores -> working on " + picked.size() + " different ones -> " + (picked.size() >= 3 ? "PASS" : "FAIL"));
        }), 3);
        l.add(null, () -> {}, 20 * 8);
        l.add(null, () -> server(p -> {
            int dug = 0;
            for (BlockPos o : ores) if (p.level().getBlockState(o).isAir()) dug++;
            // only one left: they may share it
            BlockPos one = p.blockPosition().offset(0, 0, 6);
            for (BlockPos o : ores) p.level().setBlock(o, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            p.level().setBlock(one, net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState(), 3);
            ores.clear(); ores.add(one);
            log("spread mining: ores dug " + dug + " of 5");
        }), 5);
        l.add(null, () -> server(p -> {
            int on = 0;
            for (int i = 1; i < 4; i++) if (ores.get(0).equals(b[i].mineTargetForTest())) on++;
            log("spread mining: only one ore left -> bots on it " + on + " -> " + (on >= 1 ? "PASS" : "FAIL"));
            for (PartyBotEntity x : b) x.setMode(PartyBotEntity.NEUTRAL);
            Batch3Tests.clearBots(p);
        }), 20 * 2);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void poseGuard(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> { Batch3Tests.clearBots(p); com.xenowarriors.debug.SkillTestRunner.arena(p); botAt(p, CharacterType.KAERU, 0, 4); }), 30);
        l.add(null, () -> { Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10); com.xenowarriors.client.bot.BotRenderer.failForTest = 20; }, 40);
        l.add("batch4_poseguard", () -> log("poseguard: a bot's drawing failed partway " + com.xenowarriors.client.bot.BotRenderer.failedForTest + " times, game still running -> "
                + (com.xenowarriors.client.bot.BotRenderer.failedForTest >= 5 ? "PASS" : "FAIL")), 3);
        l.add(null, () -> { com.xenowarriors.client.bot.BotRenderer.failForTest = 0; server(Batch3Tests::clearBots); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    /** A custom character of this race in slot `slot`, played now, at about this Character Level. */
    private static com.xenowarriors.you.YouSlot you(ServerPlayer p, int slot, String name, com.xenowarriors.character.Race race, com.xenowarriors.ka.Element el, int statEach) {
        var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
        var s = new com.xenowarriors.you.YouSlot(); s.name = name; s.race = race; s.element = el;
        d.youSlots[slot] = s;
        com.xenowarriors.character.CharacterManager.setYou(p, slot);
        d.statLevel[0] = statEach; d.statLevel[1] = statEach; d.statLevel[2] = statEach;
        com.xenowarriors.you.YouRaces.apply(p);
        return com.xenowarriors.you.You.slot(p);
    }

    private static void c2Paths(Minecraft mc, MikikoTests.Steps l) {
        BlockPos[] term = new BlockPos[1];
        net.minecraft.world.entity.Entity[] rec = new net.minecraft.world.entity.Entity[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.character.Seasons.set(p.server, 3);
            you(p, 7, "Testy", com.xenowarriors.character.Race.HUMAN, com.xenowarriors.ka.Element.LIGHTNING, 15);
            com.xenowarriors.faction.Reputation.set(p, com.xenowarriors.faction.Faction.GOV, 800);
            com.xenowarriors.economy.Money.add(p, 500000, false);
            term[0] = p.blockPosition().offset(0, 0, 2);
            p.level().setBlock(term[0], com.xenowarriors.gov.GovItems.TERMINAL.get().defaultBlockState(), 3);
            com.xenowarriors.gov.GovShop.open(p, term[0]);
        }), 30);
        l.add(null, () -> com.xenowarriors.client.screen.C2Screen.ask(), 20);
        l.add("batch4_c2_page", () -> log("c2: the C2 page opens " + (mc.screen instanceof com.xenowarriors.client.screen.C2Screen) + " -> " + (mc.screen instanceof com.xenowarriors.client.screen.C2Screen ? "PASS" : "FAIL")), 3);
        l.add(null, () -> { mc.setScreen(null); server(p -> {
            var demon = com.xenowarriors.you.YouPaths.path(com.xenowarriors.you.YouSlot.PATH_DEMON);
            String why = com.xenowarriors.you.YouPaths.why(p, demon);
            log("c2: Artificial Demon without a card -> \"" + why + "\" -> " + (why != null && why.contains("Card") ? "PASS" : "FAIL"));
            rec[0] = com.xenowarriors.you.YouPaths.spawnRecruiter(p.serverLevel(), p.blockPosition());
            if (rec[0] == null) { var r = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel()); r.moveTo(p.getX() + 2, p.getY(), p.getZ() - 2); r.setup(false, 1, r.blockPosition()); r.addTag(com.xenowarriors.you.YouPaths.RECRUITER); p.serverLevel().addFreshEntity(r); rec[0] = r; }
            rec[0].teleportTo(p.getX() + 2, p.getY(), p.getZ() - 2);
        }); }, 20);
        l.add(null, () -> server(p -> {
            int c0 = com.xenowarriors.you.YouPaths.cardsGiven;
            p.interactOn(rec[0], net.minecraft.world.InteractionHand.MAIN_HAND);
            boolean has = p.getInventory().contains(new ItemStack(com.xenowarriors.gov.GovItems.DEMON_CARD.get()));
            log("c2: recruiter 3602 hands over a Demon Program Card " + has + " (given " + (com.xenowarriors.you.YouPaths.cardsGiven - c0) + ") -> " + (has ? "PASS" : "FAIL"));
            String why = com.xenowarriors.you.YouPaths.why(p, com.xenowarriors.you.YouPaths.path(com.xenowarriors.you.YouSlot.PATH_DEMON));
            log("c2: now the Artificial Demon path is open " + (why == null) + " (" + why + ") -> " + (why == null ? "PASS" : "FAIL"));
        }), 10);
        // Modified Human, through the real request (terminal, money, the wait)
        long[] m0 = new long[1];
        l.add(null, () -> server(p -> { m0[0] = com.xenowarriors.economy.Money.get(p); com.xenowarriors.you.YouPaths.request(p, com.xenowarriors.you.YouSlot.PATH_MODIFIED, 0); }), 40);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.FIRST_PERSON, 0, 0), 3);
        l.add("batch4_c2_procedure", () -> {}, 3);
        l.add(null, () -> {}, 20 * 5);
        l.add(null, () -> server(p -> {
            var s = com.xenowarriors.you.You.slot(p);
            boolean mods = com.xenowarriors.toggle.Toggles.of(p).contains(com.xenowarriors.toggle.Toggles.Entry.MOD_SPEED);
            boolean oc = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(e -> e.skill().getId().equals("you_overclock"));
            boolean speed = p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            boolean plating = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getModifier(java.util.UUID.fromString("3c9a1f6e-2b77-4e0a-9f31-c2d1a0b7e5a1")) != null;
            log("c2: Modified Human: race " + s.shown().title + ", paid " + (m0[0] - com.xenowarriors.economy.Money.get(p)) + ", mods on the wheel " + mods + ", Overclock " + oc + ", Speed Mod on " + speed + ", Plating (Lv " + com.xenowarriors.stats.Stats.characterLevel(p) + ", needs 50) " + plating
                    + " -> " + (s.shown() == com.xenowarriors.character.Race.MODIFIED_HUMAN && mods && oc && speed && !plating && m0[0] > com.xenowarriors.economy.Money.get(p) ? "PASS" : "FAIL"));
            String again = com.xenowarriors.you.YouPaths.why(p, com.xenowarriors.you.YouPaths.path(com.xenowarriors.you.YouSlot.PATH_SKYLANDER));
            log("c2: a second path is refused (\"" + again + "\") -> " + (again != null ? "PASS" : "FAIL"));
        }), 10);
        // Artificial Demon
        int[] town = new int[1];
        l.add(null, () -> server(p -> {
            var s = you(p, 8, "Zacky", com.xenowarriors.character.Race.HUMAN, com.xenowarriors.ka.Element.FIRE, 15);
            town[0] = com.xenowarriors.faction.Reputation.get(p, com.xenowarriors.faction.Faction.TOWN);
            com.xenowarriors.you.YouPaths.finishForTest(p, com.xenowarriors.you.YouSlot.PATH_DEMON, 0);
        }), 20);
        l.add(null, () -> server(p -> {
            var s = com.xenowarriors.you.You.slot(p);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 0));
            boolean poisoned = p.hasEffect(net.minecraft.world.effect.MobEffects.POISON);
            boolean virus = com.xenowarriors.virus.AuroraVirus.immune(p);
            int dt = com.xenowarriors.faction.Reputation.get(p, com.xenowarriors.faction.Faction.TOWN) - town[0];
            boolean dash = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(e -> e.skill().getId().equals("devil_dash"));
            log("c2: Artificial Demon: race " + s.shown().title + ", wings " + com.xenowarriors.you.You.wings(p) + ", Devil Dash " + dash + ", poison " + poisoned + ", virus-immune " + virus + ", Townsfolk " + dt
                    + " -> " + (s.shown() == com.xenowarriors.character.Race.ARTIFICIAL_DEMON && com.xenowarriors.you.You.wings(p) && dash && !poisoned && virus && dt == -100 ? "PASS" : "FAIL"));
        }), 20);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10), 10);
        l.add("batch4_c2_artificial_demon", () -> {}, 3);
        // Artificial Skylander (Solar)
        l.add(null, () -> server(p -> {
            you(p, 9, "Techie", com.xenowarriors.character.Race.HUMAN, com.xenowarriors.ka.Element.WIND, 15);
            com.xenowarriors.you.YouPaths.finishForTest(p, com.xenowarriors.you.YouSlot.PATH_SKYLANDER, com.xenowarriors.you.YouSlot.FORM_SOLAR);
        }), 20);
        l.add(null, () -> server(p -> {
            var s = com.xenowarriors.you.You.slot(p);
            boolean form = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(e -> e.skill().getId().contains("solar"));
            log("c2: Artificial Skylander: race " + s.shown().title + ", wings " + com.xenowarriors.you.You.wings(p) + ", form " + s.form + " skill " + form
                    + " -> " + (s.shown() == com.xenowarriors.character.Race.ARTIFICIAL_SKYLANDER && com.xenowarriors.you.You.wings(p) && s.form == com.xenowarriors.you.YouSlot.FORM_SOLAR ? "PASS" : "FAIL"));
            log("c2: skills " + com.xenowarriors.skill.SkillPool.of(p).stream().map(e -> e.skill().getId()).toList());
        }), 20);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10), 10);
        l.add("batch4_c2_artificial_skylander", () -> {}, 3);
        l.add(null, () -> server(p -> { p.level().removeBlock(term[0], false); if (rec[0] != null) rec[0].discard(); }), 3);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
        Batch3Tests.character(l, CharacterType.MIKIKO);
    }

    private static void devil(Minecraft mc, MikikoTests.Steps l) {
        net.minecraft.world.entity.LivingEntity[] sh = new net.minecraft.world.entity.LivingEntity[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.character.Seasons.set(p.server, 3);
            you(p, 6, "Vex", com.xenowarriors.character.Race.DEMON, com.xenowarriors.ka.Element.SHADOW, 20);
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        }), 30);
        // the hunger: a Gov guard (no AI) killed in demon form
        l.add(null, () -> server(p -> {
            var s = com.xenowarriors.you.You.slot(p);
            int h0 = s.hunger;
            var g = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());       // (killed the same tick: peaceful doesn't get to clear it)
            g.moveTo(p.getX() + 2, p.getY(), p.getZ()); g.setNoAi(true);
            p.serverLevel().addFreshEntity(g);
            g.hurt(p.damageSources().playerAttack(p), 9999F);
            log("devil: Lv " + com.xenowarriors.stats.Stats.characterLevel(p) + " demon kills a husk -> Devil's Hunger " + h0 + " -> " + s.hunger + " -> " + (s.hunger == h0 + 1 ? "PASS" : "FAIL"));
            com.xenowarriors.you.YouDevil.fillForTest(p);
            p.serverLevel().setDayTime(18000);
        }), 20);
        l.add(null, () -> {}, 20 * 3);      // (the night check: once a second)
        l.add(null, () -> server(p -> {
            boolean d = com.xenowarriors.you.YouDevil.dueling(p);
            if (!d) com.xenowarriors.you.YouDevil.start(p);
            for (var e : p.serverLevel().getEntitiesOfClass(com.xenowarriors.you.DevilShadowEntity.class, p.getBoundingBox().inflate(20))) sh[0] = e;
            log("devil: full hunger at night -> the shadow steps out by itself " + d + " (" + (sh[0] == null ? "none" : sh[0].getName().getString() + " hp " + Math.round(sh[0].getMaxHealth())) + ") -> " + (d && sh[0] != null ? "PASS" : "FAIL"));
        }), 10);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10), 10);
        l.add("batch4_devil_shadow", () -> {}, 3);
        l.add(null, () -> server(p -> {
            if (sh[0] == null) return;
            var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            golem.moveTo(sh[0].getX() + 1, sh[0].getY(), sh[0].getZ()); golem.setNoAi(true); p.serverLevel().addFreshEntity(golem);
            float h = sh[0].getHealth();
            sh[0].hurt(sh[0].damageSources().mobAttack(golem), 20F);
            boolean helped = sh[0].getHealth() < h;
            golem.discard();
            sh[0].invulnerableTime = 0;
            sh[0].hurt(p.damageSources().playerAttack(p), 99999F);
            log("devil: someone else's hit does nothing " + !helped + "; you beat your shadow (dead " + !sh[0].isAlive() + ") -> " + (!helped && !sh[0].isAlive() ? "PASS" : "FAIL"));
        }), 10);
        l.add(null, () -> {}, 40);
        l.add(null, () -> server(p -> {
            var s = com.xenowarriors.you.You.slot(p);
            boolean mask = com.xenowarriors.toggle.Toggles.of(p).contains(com.xenowarriors.toggle.Toggles.Entry.KARMA_MASK);
            long kl = com.xenowarriors.karma.KarmaLevel.of(p);
            boolean split = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(e -> e.skill().getId().equals("devil_self_split"));
            float dm = com.xenowarriors.you.YouRaces.damageMult(p);
            log("devil: awakened - race " + s.shown().title + ", devil " + s.devil + ", Karma Mask " + mask + " (Karma Level reads " + kl + "), Self-Split " + split + ", damage x" + dm + ", max hp " + Math.round(p.getMaxHealth())
                    + " -> " + (s.devil && s.shown() == com.xenowarriors.character.Race.DEVIL && mask && kl <= 1 && split && Math.abs(dm - 1.3F) < 0.01 ? "PASS" : "FAIL"));
            p.serverLevel().setDayTime(6000);
        }), 10);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10), 10);
        l.add("batch4_devil_awakened", () -> {}, 3);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 15), 10);
        l.add("batch4_devil_back", () -> {}, 3);
        l.add(null, () -> server(p -> {
            boolean ok = new com.xenowarriors.you.YouDevil.SelfSplit().use(p);
            log("devil: Self-Split -> your other half " + (ok && com.xenowarriors.soul.SoulSplit.active(p)) + " -> " + (ok && com.xenowarriors.soul.SoulSplit.active(p) ? "PASS" : "FAIL"));
        }), 30);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 150, 10), 5);
        l.add("batch4_devil_selfsplit", () -> {}, 3);
        l.add(null, () -> server(p -> { if (com.xenowarriors.soul.SoulSplit.active(p)) com.xenowarriors.soul.SoulSplit.end(p, null); }), 10);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
        Batch3Tests.character(l, CharacterType.MIKIKO);
    }

    private static void namekSeedling(Minecraft mc, MikikoTests.Steps l) {
        float[] home = new float[3];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            you(p, 5, "Sprig", com.xenowarriors.character.Race.SEEDLING, com.xenowarriors.ka.Element.PLANT, 10);
        }), 40);
        l.add(null, () -> server(p -> { home[0] = com.xenowarriors.you.YouRaces.damageMult(p); home[1] = p.getMaxHealth(); }), 10);
        l.add(null, () -> server(p -> {
            var w = p.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation("dragonminez", "namek")));
            if (w == null) { log("namek: no Namek -> FAIL"); return; }
            BlockPos top = null;
            for (int r = 0; r <= 6 && top == null; r++) for (int dx = -r; dx <= r && top == null; dx++) for (int dz = -r; dz <= r; dz++) {
                BlockPos c = new BlockPos(dx * 24, 0, dz * 24);
                w.getChunk(c);
                BlockPos t = w.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, c);
                if (t.getY() > w.getMinBuildHeight() + 2 && w.getFluidState(t.below()).isEmpty() && w.getBlockState(t.below()).isSolid()) { top = t; break; }
            }
            if (top == null) top = w.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
            p.teleportTo(w, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 0, 10);
        }), 20 * 4);
        settle(l, 60);      // (Namek's chunks generate slowly - the server falls well behind)
        l.add(null, () -> mc.setScreen(null), 10);
        l.add(null, () -> server(p -> {
            float dm = com.xenowarriors.you.YouRaces.damageMult(p), hp = p.getMaxHealth();
            boolean fx = p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION) && p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            var g = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            g.moveTo(p.getX() + 2, p.getY(), p.getZ()); g.setNoAi(true); p.serverLevel().addFreshEntity(g);
            float h0 = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(g), 10F);
            float took = h0 - p.getHealth();
            g.discard();
            log("namek: Seedling at home x" + home[0] + " / " + Math.round(home[1]) + " hp -> on Namek x" + dm + " / " + Math.round(hp) + " hp, Regen + Speed " + fx + ", a 10-damage hit took " + took
                    + " -> " + (dm == 2.5F && hp >= home[1] + 19 && fx && took < 10 ? "PASS" : "FAIL"));
        }), 20 * 2);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10), 10);
        l.add("batch4_namek_seedling", () -> {}, 3);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    /** Waits (holding the client) until the server has caught up and run this many more ticks - a lagging server otherwise runs the queued steps back to back. */
    static void settle(MikikoTests.Steps l, int ticks) {
        l.add(null, () -> {
            var srv = Minecraft.getInstance().getSingleplayerServer();
            if (srv == null) return;
            int t0 = srv.getTickCount();
            long end = System.currentTimeMillis() + 60000;
            while (srv.getTickCount() < t0 + ticks && System.currentTimeMillis() < end) { try { Thread.sleep(20); } catch (InterruptedException ignored) { return; } }
        }, 1);
    }

    private static void elementalist(Minecraft mc, MikikoTests.Steps l) {
        com.xenowarriors.foes.ElementalistEntity[] el = new com.xenowarriors.foes.ElementalistEntity[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.character.Seasons.set(p.server, 3);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            you(p, 4, "Ember", com.xenowarriors.character.Race.HUMAN, com.xenowarriors.ka.Element.FIRE, 12);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            var e = com.xenowarriors.foes.Foes.ELEMENTALIST.get().create(p.serverLevel());
            e.setup(com.xenowarriors.ka.Element.ICE, com.xenowarriors.ka.Element.LIGHTNING, 1);
            e.setNoAi(true);
            el[0] = com.xenowarriors.foes.Foes.placeForTest(p.serverLevel(), e, p.blockPosition().offset(0, 0, 3));
        }), 20);
        l.add(null, () -> server(p -> {
            var e = el[0];
            var before = com.xenowarriors.ka.Elements.of(e);
            e.getPersistentData().putLong("xw_el_cd", p.level().getGameTime() + 100);     // (as if it just cast)
            p.getPersistentData().putString("xw_t_el", before == null ? "none" : before.id());
        }), 2);
        settle(l, 12);
        l.add(null, () -> server(p -> {
            var e = el[0];
            var now = com.xenowarriors.ka.Elements.of(e);
            String before = p.getPersistentData().getString("xw_t_el");
            log("elementalist: (alive " + e.isAlive() + ", removed " + e.isRemoved() + ", ticks " + e.tickCount + ", at " + e.blockPosition().toShortString() + " in " + e.level().dimension().location() + ", cd " + e.getPersistentData().getLong("xw_el_cd") + ")");
            log("elementalist: " + e.getName().getString() + " - " + before + " -> after a cast " + (now == null ? "none" : now.id()) + " -> " + (now != null && !now.id().equals(before) && (now == e.first() || now == e.second()) ? "PASS" : "FAIL"));
        }), 5);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10); }, 10);
        l.add("batch4_elementalist", () -> mc.setScreen(null), 3);
        l.add(null, () -> server(p -> {
            ItemStack c = com.xenowarriors.you.YouItems.crystal(com.xenowarriors.ka.Element.ICE);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, c);
            c.use(p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
            var s = com.xenowarriors.you.You.slot(p);
            boolean b2 = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(x -> x.skill().getId().equals("you_bolt2"));
            boolean w2 = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(x -> x.skill().getId().equals("you_wave2"));
            boolean tog = com.xenowarriors.toggle.Toggles.of(p).contains(com.xenowarriors.toggle.Toggles.Entry.SWAP_ELEMENT);
            log("elementalist: Ice Crystal used -> second element " + (s.element2 == null ? "none" : s.element2.id()) + ", Second Bolt " + b2 + ", Second Wave " + w2 + ", wheel toggle " + tog
                    + " -> " + (s.element2 == com.xenowarriors.ka.Element.ICE && b2 && w2 && tog ? "PASS" : "FAIL"));
            el[0].discard();
        }), 10);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void vessel(Minecraft mc, MikikoTests.Steps l) {
        com.xenowarriors.foes.VesselEntity[] v = new com.xenowarriors.foes.VesselEntity[2];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.character.Seasons.set(p.server, 3);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            you(p, 6, "Vex", com.xenowarriors.character.Race.DEMON, com.xenowarriors.ka.Element.SHADOW, 20);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            for (int i = 0; i < 2; i++) {
                var e = com.xenowarriors.foes.Foes.VESSEL.get().create(p.serverLevel());
                e.setLook(i); e.setNoAi(true);
                v[i] = com.xenowarriors.foes.Foes.placeForTest(p.serverLevel(), e, p.blockPosition().offset(i == 0 ? -2 : 2, 0, 4));
            }
        }), 20);
        // one: down to half -> fusing; the other fuses all the way
        settle(l, 5);
        l.add(null, () -> server(p -> {
            v[0].setHealth(v[0].getMaxHealth() * 0.45F);       // (down to under half - DragonMineZ scales a player's hits by their stats)
            v[1].fuseNowForTest();
        }), 2);
        settle(l, 10);
        l.add(null, () -> server(p -> log("vessel: at half health it fuses (state " + v[0].state() + "), the other finished fusing (state " + v[1].state() + ", " + v[1].getName().getString() + ") -> "
                + (v[0].state() == com.xenowarriors.foes.VesselEntity.FUSING && v[1].state() == com.xenowarriors.foes.VesselEntity.FUSED ? "PASS" : "FAIL"))), 5);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10); }, 10);
        l.add("batch4_vessel_fusing", () -> mc.setScreen(null), 3);
        l.add(null, () -> server(p -> {
            int st0 = com.xenowarriors.foes.VesselEntity.stopped;
            var before = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, v[0].getBoundingBox().inflate(6)).size();
            v[0].invulnerableTime = 0;
            v[0].setHealth(1F);
            v[0].hurt(p.damageSources().playerAttack(p), 9999F);
            int essence = 0;
            for (var it : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, v[0].getBoundingBox().inflate(6)))
                if (it.getItem().is(com.xenowarriors.you.YouItems.DEMON_ESSENCE.get())) essence += it.getItem().getCount();
            log("vessel: killed while fusing -> stopped " + (com.xenowarriors.foes.VesselEntity.stopped - st0) + ", Demon Essence dropped " + essence + " -> " + (com.xenowarriors.foes.VesselEntity.stopped == st0 + 1 && essence >= 1 ? "PASS" : "FAIL"));
            var s = com.xenowarriors.you.You.slot(p);
            int h0 = s.hunger;
            ItemStack es = new ItemStack(com.xenowarriors.you.YouItems.DEMON_ESSENCE.get());
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, es);
            es.use(p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
            log("vessel: Demon Essence -> Devil's Hunger " + h0 + " -> " + s.hunger + " -> " + (s.hunger == Math.min(60, h0 + 5) ? "PASS" : "FAIL"));
            for (var it : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(12))) it.discard();
        }), 10);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 160, 8); }, 10);
        l.add("batch4_vessel_fused", () -> mc.setScreen(null), 3);
        l.add(null, () -> server(p -> { for (var e : v) if (e != null) e.discard(); }), 5);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void angel(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.character.Seasons.set(p.server, 3);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            var s = you(p, 3, "Seraph", com.xenowarriors.character.Race.SKYLANDER, com.xenowarriors.ka.Element.LIGHTNING, 30);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            boolean second = com.xenowarriors.skill.SkillPool.of(p).stream().filter(x -> x.skill().getId().equals("you_lunar") || x.skill().getId().equals("you_solar")).count() == 2;
            boolean notYet = !com.xenowarriors.you.YouAngel.ready(p, s);
            for (int i = 0; i < 30; i++) { com.xenowarriors.you.YouAngel.formUsed(p, true); com.xenowarriors.you.YouAngel.formUsed(p, false); }
            boolean ready = com.xenowarriors.you.YouAngel.ready(p, s);
            log("angel: Lv " + com.xenowarriors.stats.Stats.characterLevel(p) + " Skylander - both forms " + second + ", not ready before mastery " + notYet + ", Lunar " + s.lunarM + " Solar " + s.solarM + " -> ready " + ready
                    + " -> " + (second && notYet && ready ? "PASS" : "FAIL"));
            var rec = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
            if (rec != null) p.teleportTo(rec, 6.5, 140, 6.5, 0, 0);
        }), 20 * 2);
        settle(l, 60);
        l.add(null, () -> server(p -> {
            var lv = p.serverLevel();
            lv.getChunk(0, 0);                                           // (loaded for sure before we look for the floor)
            int y = com.xenowarriors.recarnet.RoyalCapitalFeature.FLOOR_Y + 60;     // the Royal Capital's floor (where you'd pray), not the city under it
            while (y > com.xenowarriors.recarnet.RoyalCapitalFeature.FLOOR_Y && (lv.getBlockState(new BlockPos(6, y - 1, 6)).isAir()
                    || !lv.getBlockState(new BlockPos(6, y, 6)).isAir() || !lv.getBlockState(new BlockPos(6, y + 1, 6)).isAir())) y--;
            p.teleportTo(6.5, y, 6.5);
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.getAbilities().invulnerable = true; p.onUpdateAbilities();     // (stays put and up for the trial: this tests the waves, not a fight)
        }), 2);
        settle(l, 60);      // (the client confirms the move first)
        l.add(null, () -> server(p -> {
            var lv = p.serverLevel();
            p.server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 10, false, false));
            com.xenowarriors.you.YouAngel.start(p);
            log("angel: in " + lv.dimension().location() + " at " + p.blockPosition().toShortString() + ", in the Royal Capital " + com.xenowarriors.recarnet.RoyalCapitalFeature.inCapital(lv, p.blockPosition()));
            log("angel: the trial starts - wave " + com.xenowarriors.you.YouAngel.trialWave(p) + ", " + com.xenowarriors.you.YouAngel.foesForTest(p).size() + " foes -> " + (com.xenowarriors.you.YouAngel.trialWave(p) == 1 && !com.xenowarriors.you.YouAngel.foesForTest(p).isEmpty() ? "PASS" : "FAIL"));
        }), 30);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 12); }, 10);
        l.add("batch4_angel_trial", () -> mc.setScreen(null), 3);
        for (int w = 0; w < 3; w++) {
            l.add(null, () -> server(p -> {
                for (var id : com.xenowarriors.you.YouAngel.foesForTest(p))
                    if (p.serverLevel().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity e) { e.invulnerableTime = 0; e.setHealth(1F); e.hurt(p.damageSources().playerAttack(p), 99999F); }
            }), 2);
            settle(l, 45);
        }
        l.add(null, () -> server(p -> {
            p.server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
            p.getAbilities().invulnerable = false; p.onUpdateAbilities();
            var s = com.xenowarriors.you.You.slot(p);
            if (s == null) { log("angel: not playing Seraph any more -> FAIL"); return; }
            float dm = com.xenowarriors.you.YouRaces.damageMult(p);
            boolean eye = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(x -> x.skill().getId().equals("you_angel_eye"));
            boolean light = com.xenowarriors.skill.SkillPool.of(p).stream().anyMatch(x -> x.skill().getId().equals("you_light"));
            log("angel: three waves beaten -> ascended " + s.ascended + ", race " + s.shown().title + ", damage x" + dm + ", max hp " + Math.round(p.getMaxHealth()) + ", Angel Eye " + eye + ", Divine Light " + light
                    + " -> " + (s.ascended && s.shown() == com.xenowarriors.character.Race.ANGEL && Math.abs(dm - 1.5F) < 0.01 && eye && light ? "PASS" : "FAIL"));
            int f0 = com.xenowarriors.you.YouAngel.foreseen;
            new com.xenowarriors.you.YouAngel.AngelEye().use(p);
            var g = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            g.moveTo(p.getX() + 2, p.getY(), p.getZ()); g.setNoAi(true); p.serverLevel().addFreshEntity(g);
            for (int i = 0; i < 20; i++) { p.invulnerableTime = 0; p.hurt(p.damageSources().mobAttack(g), 1F); }
            g.discard();
            int f = com.xenowarriors.you.YouAngel.foreseen - f0;
            log("angel: Angel Eye - " + f + " of 20 hits foreseen -> " + (f >= 4 && f <= 16 ? "PASS" : "FAIL"));
            p.heal(100);
        }), 20);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 15); }, 15);
        l.add("batch4_angel_back", () -> mc.setScreen(null), 3);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10); }, 10);
        l.add("batch4_angel_front", () -> mc.setScreen(null), 3);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.entity.EntityType<?> dmzType(String field) {
        try {
            var ro = (net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.EntityType<?>>) Class.forName("com.dragonminez.common.init.MainEntities").getField(field).get(null);
            return ro.get();
        } catch (Throwable t) { log("mobgravity: no " + field + " " + t); return null; }
    }

    private static void mobGravity(Minecraft mc, MikikoTests.Steps l) {
        net.minecraft.world.entity.Mob[] m = new net.minecraft.world.entity.Mob[3];
        double[] home = new double[2];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            var lv = p.serverLevel();
            var rad = dmzType("SAGA_RADITZ");
            m[0] = rad == null ? null : (net.minecraft.world.entity.Mob) rad.create(lv);
            m[1] = net.minecraft.world.entity.EntityType.ZOMBIE.create(lv);
            for (int i = 0; i < 2; i++) if (m[i] != null) { m[i].moveTo(p.getX() + (i == 0 ? -2 : 2), p.getY(), p.getZ() + 4); m[i].setNoAi(true); m[i].setPersistenceRequired(); lv.addFreshEntity(m[i]); }
            home[0] = m[1] == null ? -1 : com.xenowarriors.compat.DmzMobGravity.applyForTest(m[1]);
            log("mobgravity: no King Kai - zombie penalty " + home[0] + " (" + com.xenowarriors.compat.DmzMobGravity.last + ") -> " + (home[0] == 0 ? "PASS" : "FAIL"));
            var kai = dmzType("MASTER_KAIOSAMA");
            m[2] = kai == null ? null : (net.minecraft.world.entity.Mob) kai.create(lv);
            if (m[2] != null) { m[2].moveTo(p.getX(), p.getY(), p.getZ() + 8); m[2].setNoAi(true); lv.addFreshEntity(m[2]); }
        }), 5);
        settle(l, 120);      // (the King Kais are looked for every 5 s)
        l.add(null, () -> server(p -> {
            if (m[1] == null || m[1].level() == null) return;
            double z = com.xenowarriors.compat.DmzMobGravity.applyForTest(m[1]);
            String zl = com.xenowarriors.compat.DmzMobGravity.last;
            double zs = m[1].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            log("mobgravity: by King Kai - zombie penalty " + Math.round(z * 100) + "% (" + zl + "), speed " + String.format("%.3f", zs) + " -> " + (z > 0.05 && zs < 0.23 * (1 - z) + 0.01 ? "PASS" : "FAIL"));
            if (m[0] != null) {
                double r = com.xenowarriors.compat.DmzMobGravity.applyForTest(m[0]);
                log("mobgravity: by King Kai - Raditz penalty " + Math.round(r * 100) + "% (" + com.xenowarriors.compat.DmzMobGravity.last + ") -> " + (r > 0 ? "PASS" : "FAIL"));
            }
            var gr = p.serverLevel().getEntitiesOfClass(com.xenowarriors.bot.PartyBotEntity.class, p.getBoundingBox().inflate(30));
            if (!gr.isEmpty()) log("mobgravity: a bot " + com.xenowarriors.compat.DmzMobGravity.applyForTest(gr.get(0)) + " " + com.xenowarriors.compat.DmzMobGravity.last);
        }), 10);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10); }, 10);
        l.add("batch4_mob_gravity", () -> mc.setScreen(null), 3);
        l.add(null, () -> server(p -> { for (var e : m) if (e != null) e.discard(); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); }), 5);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static int countCubes(software.bernie.geckolib.cache.object.GeoBone b) {
        int n = b.getCubes().size();
        for (var c : b.getChildBones()) n += countCubes(c);
        return n;
    }

    private static void boostBag(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            p.getInventory().clearContent();
            p.getInventory().setItem(8, new ItemStack(com.xenowarriors.statboost.BoostBag.ITEM.get()));
            var a = com.xenowarriors.statboost.StatBoosts.stack(com.xenowarriors.statboost.BoostStat.ATTACK, com.xenowarriors.statboost.BoostTier.ONE); a.setCount(5);
            p.getInventory().setItem(1, a);
            p.getInventory().setItem(2, com.xenowarriors.statboost.StatBoosts.stack(com.xenowarriors.statboost.BoostStat.MINING, com.xenowarriors.statboost.BoostTier.TEN));
            p.getInventory().selected = 8;
            var bag = p.getMainHandItem();
            bag.getItem().use(p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND);      // (loose ones go in, and it opens)
            int in = com.xenowarriors.statboost.BoostBag.total(bag);
            boolean loose = p.getInventory().getItem(1).isEmpty() && p.getInventory().getItem(2).isEmpty();
            log("boostbag: right-clicked with 6 loose boosts -> " + in + " in the bag, inventory empty of them " + loose + " -> " + (in == 6 && loose ? "PASS" : "FAIL"));
            // one on the ground, picked up
            var drop = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), com.xenowarriors.statboost.StatBoosts.stack(com.xenowarriors.statboost.BoostStat.SPEED, com.xenowarriors.statboost.BoostTier.FIVE));
            drop.setNoPickUpDelay();
            p.level().addFreshEntity(drop);
        }), 30);
        l.add(null, () -> server(p -> {
            var bag = com.xenowarriors.statboost.BoostBag.find(p);
            int sp = com.xenowarriors.statboost.BoostBag.count(bag, com.xenowarriors.statboost.BoostStat.SPEED, com.xenowarriors.statboost.BoostTier.FIVE);
            boolean none = true;
            for (ItemStack it : p.getInventory().items) if (it.getItem() instanceof com.xenowarriors.statboost.StatBoostItem) none = false;
            log("boostbag: a boost picked up went into the bag " + sp + " (none loose " + none + ") -> " + (sp == 1 && none ? "PASS" : "FAIL"));
            int lv0 = com.xenowarriors.statboost.BoostStat.ATTACK.level(p);
            int k = com.xenowarriors.statboost.BoostBag.index(com.xenowarriors.statboost.BoostStat.ATTACK, com.xenowarriors.statboost.BoostTier.ONE);
            com.xenowarriors.statboost.BoostBag.act(p, com.xenowarriors.network.BoostBagPacket.USE, k, 1);
            int lv1 = com.xenowarriors.statboost.BoostStat.ATTACK.level(p);
            com.xenowarriors.statboost.BoostBag.act(p, com.xenowarriors.network.BoostBagPacket.TAKE, k, 64);
            int out = 0;
            for (ItemStack it : p.getInventory().items) if (it.getItem() instanceof com.xenowarriors.statboost.StatBoostItem) out += it.getCount();
            int left = com.xenowarriors.statboost.BoostBag.count(bag, com.xenowarriors.statboost.BoostStat.ATTACK, com.xenowarriors.statboost.BoostTier.ONE);
            log("boostbag: used one from the bag (Attack " + lv0 + " -> " + lv1 + "), took the rest out (" + out + " in inventory, " + left + " left) -> " + (lv1 == lv0 + 1 && out == 4 && left == 0 ? "PASS" : "FAIL"));
            com.xenowarriors.statboost.BoostBag.depositAll(p, bag);
            com.xenowarriors.statboost.BoostBag.open(p, bag);
        }), 20);
        l.add("batch4_boostbag", () -> {}, 3);
        l.add(null, () -> mc.setScreen(null), 3);
    }

    /** No right+left click skill with a gun in hand (right click aims, left click shoots). */
    private static void rlGun(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        float[] k = new float[4];
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            net.minecraft.world.item.Item gun = null;
            for (var it : net.minecraftforge.registries.ForgeRegistries.ITEMS) if (com.xenowarriors.item.Guns.isGun(new ItemStack(it))) { gun = it; break; }
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, gun == null ? ItemStack.EMPTY : new ItemStack(gun));
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setKarma(100F));
            com.xenowarriors.skill.SkillManager.resetCooldowns(p);
            k[0] = com.xenowarriors.karma.Karma.get(p);
            com.xenowarriors.toggle.Toggles.useRl(p);
            k[1] = com.xenowarriors.karma.Karma.get(p);
            log("rlgun: holding " + (gun == null ? "no gun!" : net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gun)) + ", right+left click -> Karma " + k[0] + " -> " + k[1]);
        }), 10);
        l.add(null, () -> log("rlgun: client says a gun's in hand " + com.xenowarriors.client.TransformKeyClient.holdingGun(mc)), 2);
        l.add(null, () -> server(p -> {
            p.getInventory().setItem(0, ItemStack.EMPTY);
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setKarma(100F));
            com.xenowarriors.skill.SkillManager.resetCooldowns(p);
            k[2] = com.xenowarriors.karma.Karma.get(p);
            com.xenowarriors.toggle.Toggles.useRl(p);
            k[3] = com.xenowarriors.karma.Karma.get(p);
            log("rlgun: with a gun nothing happens (" + k[0] + " -> " + k[1] + "), bare-handed it goes off (" + k[2] + " -> " + k[3] + ") -> " + (k[1] == k[0] && k[3] < k[2] ? "PASS" : "FAIL"));
        }), 10);
    }

    /** The "BufferBuilder not started" crash guard: a GeckoLib body handed an already-closed buffer draws instead of crashing. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void geoGuard(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> {
            try {
                var type = com.xenowarriors.vehicle.Vehicles.TYPES.values().iterator().next().get();
                var e = type.create(mc.level);
                var r = (software.bernie.geckolib.renderer.GeoEntityRenderer<com.xenowarriors.vehicle.VehicleEntity>) mc.getEntityRenderDispatcher().getRenderer(e);
                var model = r.getGeoModel().getBakedModel(r.getGeoModel().getModelResource(e));
                var bone = model.topLevelBones().get(0);
                var rt = net.minecraft.client.renderer.RenderType.entityCutoutNoCull(r.getTextureLocation(e));
                var buffers = net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(4096));
                var vc = buffers.getBuffer(rt);
                buffers.endBatch();                                                    // (closed: what another mod's draw in between did)
                String closed;
                try { vc.vertex(0, 0, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(0).uv2(0).normal(0, 1, 0).endVertex(); closed = "took a vertex"; }
                catch (IllegalStateException ex) { closed = "refuses vertices (" + ex.getMessage() + ")"; }
                boolean merged = false;
                for (var mt : software.bernie.geckolib.renderer.GeoEntityRenderer.class.getDeclaredMethods()) if (mt.getName().contains("freshBuffer")) merged = true;
                int cubes = 0;
                for (var b : model.topLevelBones()) cubes += countCubes(b);
                log("geoguard: the closed buffer " + closed + "; the guard is in GeckoLib " + merged + "; the model has " + cubes + " cubes");
                int s0 = com.xenowarriors.client.GeoBufferGuard.saved;
                String result;
                try {
                    for (var b : model.topLevelBones())
                        r.renderRecursively(new com.mojang.blaze3d.vertex.PoseStack(), e, b, rt, buffers, vc, false, 0F, 15728880,
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
                    result = "drew fine";
                } catch (IllegalStateException ex) { result = "crashed: " + ex.getMessage(); }
                buffers.endBatch();
                int saved = com.xenowarriors.client.GeoBufferGuard.saved - s0;
                log("geoguard: a GeckoLib body handed a closed buffer " + result + " (fresh buffers handed out " + saved + ") -> " + (result.equals("drew fine") && saved > 0 ? "PASS" : "FAIL"));
            } catch (Throwable t) { log("geoguard: step error " + t + " -> FAIL"); XenoWarriors.LOGGER.error("geoguard", t); }
        }, 10);
    }

    private static void ownBot(Minecraft mc, MikikoTests.Steps l) {
        String[] sha = new String[1];
        com.xenowarriors.bot.PartyBotEntity[] bot = new com.xenowarriors.bot.PartyBotEntity[1];
        // a skin file of "yours" (Garry's skin, as if dropped in your skins folder), uploaded to the server
        l.add(null, () -> {
            try (var in = mc.getResourceManager().getResource(XenoWarriors.id("textures/entity/character/garry.png")).get().open()) {
                byte[] b = in.readAllBytes();
                sha[0] = com.xenowarriors.you.YouSkins.sha1(b);
                com.xenowarriors.client.you.YouSkinClient.use(new com.xenowarriors.client.you.YouSkinClient.LocalSkin("garry", sha[0], b, null));
            } catch (Throwable t) { log("ownbot: no skin file " + t); }
        }, 20);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            com.xenowarriors.character.Seasons.set(p.server, 3);
            you(p, 3, "Ruk", com.xenowarriors.character.Race.SKYLANDER, com.xenowarriors.ka.Element.WIND, 8);
            you(p, 2, "Vexa", com.xenowarriors.character.Race.DEMON, com.xenowarriors.ka.Element.SHADOW, 20);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            boolean stored = sha[0] != null && com.xenowarriors.you.YouSkins.has(p.server, sha[0]);
            if (stored) d.youSlots[2].skin = "f:" + sha[0];
            log("ownbot: skin file stored on the server " + stored + " (" + com.xenowarriors.you.YouSkins.stored + " stored) -> " + (stored ? "PASS" : "FAIL"));
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
        }), 40);
        l.add(null, () -> server(p -> {
            int lv = com.xenowarriors.bot.PartyBots.levelFor(p, "you2");
            com.xenowarriors.bot.PartyBots.add(p, new String[]{"you2"});
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.charKey().equals("you2")) bot[0] = b;
            var b = bot[0];
            if (b == null) { log("ownbot: no bot joined -> FAIL"); return; }
            b.setMode(com.xenowarriors.bot.PartyBotEntity.NEUTRAL);
            b.moveTo(p.getX() + 2, p.getY(), p.getZ() + 3, 180, 0);
            log("ownbot: added Vexa -> " + b.label().getString() + ", key " + b.charKey() + ", level " + b.botLevel() + " (her save: " + lv + "), max hp " + Math.round(b.getMaxHealth())
                    + ", flies " + b.naturalFlier() + ", skin " + b.you().skin.length() + " chars -> " + (b.charKey().equals("you2") && b.botLevel() == lv && b.naturalFlier() && b.you().skin.startsWith("f:") ? "PASS" : "FAIL"));
            log("ownbot: her skills " + com.xenowarriors.bot.PartyBots.skillsForTest(b));
        }), 30);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 200, 10); }, 30);
        l.add(null, () -> log("ownbot: client sees her skin loaded " + (com.xenowarriors.client.you.YouSkinClient.look("f:" + sha[0]) != null) + " (files " + com.xenowarriors.client.you.YouSkinClient.filesLoaded
                + (com.xenowarriors.client.you.YouSkinClient.lastProblem.isEmpty() ? "" : ", " + com.xenowarriors.client.you.YouSkinClient.lastProblem) + ")"), 2);
        l.add("batch4_ownbot", () -> mc.setScreen(null), 3);
        l.add(null, () -> server(p -> {
            var b = bot[0];
            if (b == null) return;
            var g = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(p.serverLevel());
            g.moveTo(b.getX(), b.getY(), b.getZ() + 4); g.setNoAi(true); p.serverLevel().addFreshEntity(g);
            float h0 = g.getHealth();
            boolean bolt = com.xenowarriors.bot.PartyBots.useSlotForTest(b, g, 2);
            boolean devil = com.xenowarriors.bot.PartyBots.useSlotForTest(b, g, -1);
            log("ownbot: her Element Bolt went off " + bolt + " (golem " + Math.round(h0) + " -> " + Math.round(g.getHealth()) + " hp), Devil Unleashed " + devil
                    + " -> " + (bolt && devil ? "PASS" : "FAIL"));
            g.discard();
            // what she earns is hers
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            int[] before = d.charStats.getCompound("you2").getIntArray("lv").clone();
            com.xenowarriors.bot.PartyBots.addXp(b, com.xenowarriors.stats.Stat.ATTACK, 5000F);
            int[] after = d.charStats.getCompound("you2").getIntArray("lv");
            log("ownbot: XP she earns goes to her save: Attack " + before[1] + " -> " + after[1] + " -> " + (after[1] > before[1] ? "PASS" : "FAIL"));
            var s = b.you();
            int h = s.hunger;
            var husk = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
            husk.moveTo(b.getX() + 1, b.getY(), b.getZ()); husk.setNoAi(true); p.serverLevel().addFreshEntity(husk);
            husk.setHealth(1F);
            husk.hurt(b.damageSources().mobAttack(b), 9999F);
            log("ownbot: her kill feeds her Devil's Hunger " + h + " -> " + s.hunger + " -> " + (s.hunger == h + 1 ? "PASS" : "FAIL"));
            s.devil = true;
            b.refreshYou();
            long kl = com.xenowarriors.karma.KarmaLevel.of(b);
            log("ownbot: as a Devil her Karma Mask hides her (Karma Level reads " + kl + ", masked " + com.xenowarriors.you.YouDevil.masked(b) + ") -> " + (kl <= 1 && com.xenowarriors.you.YouDevil.masked(b) ? "PASS" : "FAIL"));
            boolean refused = !com.xenowarriors.character.CharacterManager.setYou(p, 2);
            log("ownbot: you can't play Vexa while she's your bot " + refused + " -> " + (refused ? "PASS" : "FAIL"));
            com.xenowarriors.bot.PartyBots.openPick(p);
        }), 20);
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.bot.BotPickScreen ps) ps.showOwn(true); }, 15);
        l.add("batch4_ownbot_pick", () -> {}, 3);
        l.add(null, () -> { mc.setScreen(null); server(p -> {
            var b = bot[0];
            if (b == null) return;
            com.xenowarriors.bot.PartyBots.checkIn(b, p);
            b.discard();
            boolean played = com.xenowarriors.character.CharacterManager.setYou(p, 2);
            var d = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).orElse(null);
            var s = com.xenowarriors.you.You.slot(p);
            log("ownbot: dismissed, then played: " + played + ", Attack " + d.statLevel[1] + ", Devil " + (s != null && s.devil) + ", hunger " + (s == null ? -1 : s.hunger)
                    + " -> " + (played && d.statLevel[1] > 20 && s != null && s.devil ? "PASS" : "FAIL"));
        }); }, 20);
        l.add(null, () -> { mc.setScreen(null); Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 180, 10); }, 20);
        l.add("batch4_ownbot_player_skin", () -> {}, 3);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
        Batch3Tests.character(l, CharacterType.MIKIKO);
    }

    private static void towerPods(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[1];
        java.util.List<BlockPos> pods = new java.util.ArrayList<>();
        l.add(null, () -> server(p -> { Batch3Tests.clearBots(p); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); com.xenowarriors.tower.Tower.enter(p); }), 80);
        l.add(null, () -> server(p -> { com.xenowarriors.tower.Tower.forcePods = true; com.xenowarriors.tower.Tower.skipTo(p, 5); com.xenowarriors.tower.Tower.forcePods = false; }), 20);
        l.add(null, () -> server(p -> {
            pods.clear(); pods.addAll(com.xenowarriors.tower.Tower.podsForTest(p, 20 * 30));
            boolean there = !pods.isEmpty() && p.level().getBlockState(pods.get(0)).getBlock() instanceof com.xenowarriors.gov.HealingPodBlock;
            log("towerpods: a healing pod turned up on the floor " + there + " (" + com.xenowarriors.tower.Tower.podsSpawned + ") -> " + (there ? "PASS" : "FAIL"));
            b[0] = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (b[0] != null) { b[0].moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0); b[0].setTarget(null); b[0].setHealth(b[0].getMaxHealth() * 0.3F); }
        }), 20 * 8);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 30), 5);
        l.add("batch4_towerpods", () -> {}, 3);
        l.add(null, () -> server(p -> {
            boolean in = b[0] != null && (com.xenowarriors.gov.HealingPods.inPod(b[0]) || b[0].getHealth() > b[0].getMaxHealth() * 0.5F);
            log("towerpods: the hurt bot used the pod " + in + " (health " + (b[0] == null ? -1 : (int) b[0].getHealth()) + ") -> " + (in ? "PASS" : "FAIL"));
            if (b[0] != null) b[0].discard();
            p.setHealth(1F); p.heal(40F);                                          // (the floor's healing used up)
            p.setHealth(6F);
        }), 20 * 12);
        float[] h = new float[1];
        l.add(null, () -> server(p -> {
            h[0] = p.getHealth();
            p.heal(4F);
            float capped = p.getHealth();
            if (!pods.isEmpty()) com.xenowarriors.gov.HealingPods.start(p.serverLevel(), pods.get(0), p, -1, null);
            log("towerpods: floor healing used up (heal did " + (capped - h[0]) + ") - into the pod " + com.xenowarriors.gov.HealingPods.inPod(p));
            h[0] = capped;
        }), 20 * 4);
        l.add(null, () -> server(p -> {
            log("towerpods: the pod healed you past the floor's limit: " + h[0] + " -> " + p.getHealth() + " -> " + (p.getHealth() > h[0] + 2 ? "PASS" : "FAIL"));
            com.xenowarriors.tower.Tower.leave(p, "test over");
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 40);
    }

    private static void rps(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            botAt(p, CharacterType.GARRY, 2, 2); botAt(p, CharacterType.STEVIE, -2, 2);
        }), 20);
        l.add(null, () -> server(p -> {
            boolean on = com.xenowarriors.bot.BotPartyTime.start(p);
            log("rps: party " + on);
            com.xenowarriors.bot.BotRps.nowForTest(p, false);
        }), 20 * 6);
        l.add(null, () -> server(p -> {
            log("rps: two bots played rock, paper, scissors " + com.xenowarriors.bot.BotRps.botGames + " -> " + (com.xenowarriors.bot.BotRps.botGames > 0 ? "PASS" : "FAIL"));
            com.xenowarriors.bot.BotRps.nowForTest(p, true);
        }), 20);
        l.add(null, () -> mc.options.hideGui = false, 2);
        l.add("batch4_rps_challenge", () -> log("rps: a bot challenged you - the screen is up " + (mc.screen instanceof com.xenowarriors.client.party.RpsScreen) + " -> " + (mc.screen instanceof com.xenowarriors.client.party.RpsScreen ? "PASS" : "FAIL")), 2);
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.party.RpsScreen r) r.pick(0); }, 15);
        l.add("batch4_rps_result", () -> {}, 2);
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.party.RpsScreen r) r.pick(1); }, 15);   // (a draw: again)
        l.add(null, () -> { if (mc.screen instanceof com.xenowarriors.client.party.RpsScreen r) r.pick(2); }, 15);
        l.add(null, () -> server(p -> {
            boolean done = !com.xenowarriors.bot.BotRps.waitingForTest(p);
            log("rps: you played (" + com.xenowarriors.bot.BotRps.playerGames + " games, " + com.xenowarriors.bot.BotRps.playerWins + " won) - it's settled " + done + " -> " + (done && com.xenowarriors.bot.BotRps.playerGames > 0 ? "PASS" : "FAIL"));
            com.xenowarriors.bot.BotPartyTime.endForTest(p);
            Batch3Tests.clearBots(p);
        }), 60);
        l.add(null, () -> mc.setScreen(null), 3);
    }

    private static void techBike(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[1];
        net.minecraft.world.entity.Entity[] car = new net.minecraft.world.entity.Entity[1];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            com.xenowarriors.vehicle.TechnicaBike.setAttachmentsForTest(p, 1);
            b[0] = botAt(p, CharacterType.TECHNICA, 18, 0);
            b[0].inventory.addItem(new ItemStack(com.xenowarriors.vehicle.TechnicaBike.ATTACHMENTS[com.xenowarriors.vehicle.TechnicaBike.ROCKETS].get()));
            var c = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.CAR).get().create(p.serverLevel());
            c.moveTo(p.getX(), p.getY(), p.getZ() + 3, 0, 0);
            p.serverLevel().addFreshEntity(c);
            car[0] = c;
            p.startRiding(c, true);
        }), 20 * 7);
        l.add(null, () -> server(p -> {
            boolean onBike = b[0].getVehicle() instanceof com.xenowarriors.vehicle.VehicleEntity v && v.kind.technica();
            log("techbike: you drive off, Technica too far behind -> she rides her own motorcycle " + onBike + " (bikes " + com.xenowarriors.bot.BotRides.ownBikes + ") -> " + (onBike ? "PASS" : "FAIL"));
            boolean rockets = (com.xenowarriors.vehicle.TechnicaBike.attachments(p) & (1 << com.xenowarriors.vehicle.TechnicaBike.ROCKETS)) != 0;
            boolean used = b[0].inventory.countItem(com.xenowarriors.vehicle.TechnicaBike.ATTACHMENTS[com.xenowarriors.vehicle.TechnicaBike.ROCKETS].get()) == 0;
            log("techbike: she fitted the Rocket Pod herself " + rockets + " (item used " + used + ") -> " + (rockets && used ? "PASS" : "FAIL"));
        }), 5);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 20), 10);
        l.add("batch4_techbike", () -> {}, 3);
        l.add(null, () -> server(p -> { p.stopRiding(); car[0].discard(); }), 40);
        l.add(null, () -> server(p -> {
            log("techbike: you get out -> she's off it " + !b[0].isPassenger() + " -> " + (!b[0].isPassenger() ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 5);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void setBotStat(ServerPlayer p, CharacterType t, int i, int lv) {
        p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            var c = d.charStats.getCompound(t.getId());
            int[] a = c.getIntArray("lv"); if (a.length != 3) a = new int[]{1, 1, 1};
            a[i] = lv; c.putIntArray("lv", a); d.charStats.put(t.getId(), c);
        });
    }

    private static void boostPass(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[2];
        int max = com.xenowarriors.stats.Stat.MAX_LEVEL;
        var atk = com.xenowarriors.statboost.BoostStat.ATTACK;
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);
            setBotStat(p, CharacterType.RHALIX, atk.ordinal(), max);
            setBotStat(p, CharacterType.KYRIE, atk.ordinal(), 5);
            b[0] = botAt(p, CharacterType.RHALIX, 2, 2); b[1] = botAt(p, CharacterType.KYRIE, -2, 2);
            b[0].inventory.addItem(com.xenowarriors.statboost.StatBoosts.stack(atk, com.xenowarriors.statboost.BoostTier.values()[0]));
        }), 20 * 7);
        l.add(null, () -> server(p -> {
            boolean gone = b[0].inventory.countItem(com.xenowarriors.statboost.StatBoosts.item(atk, com.xenowarriors.statboost.BoostTier.values()[0])) == 0;
            log("boostpass: maxed Rhalix passed his Attack Boost to a bot " + com.xenowarriors.bot.BotBoosts.toBots + " (his gone " + gone + ") -> " + (com.xenowarriors.bot.BotBoosts.toBots > 0 && gone ? "PASS" : "FAIL"));
            // no bot can use it, you're maxed too: he keeps it
            setBotStat(p, CharacterType.KYRIE, atk.ordinal(), max);
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.statLevel[atk.ordinal()] = max);
            b[0].inventory.addItem(com.xenowarriors.statboost.StatBoosts.stack(atk, com.xenowarriors.statboost.BoostTier.values()[0]));
            b[1].inventory.clearContent();
        }), 20 * 7);
        l.add(null, () -> server(p -> {
            boolean kept = b[0].inventory.countItem(com.xenowarriors.statboost.StatBoosts.item(atk, com.xenowarriors.statboost.BoostTier.values()[0])) == 1;
            log("boostpass: everyone maxed (you too) -> he keeps it " + kept + ", given to you " + com.xenowarriors.bot.BotBoosts.toPlayer + " -> " + (kept && com.xenowarriors.bot.BotBoosts.toPlayer == 0 ? "PASS" : "FAIL"));
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.statLevel[atk.ordinal()] = 3);
        }), 20 * 7);
        l.add(null, () -> server(p -> {
            boolean got = com.xenowarriors.bot.BotBoosts.toPlayer > 0;
            log("boostpass: you can still use it (and no bot can) -> he gives it to you " + got + " -> " + (got ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 5);
    }

    private static void teamJukebox(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        PartyBotEntity[] b = new PartyBotEntity[2];
        int[] n0 = new int[3];
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            com.xenowarriors.debug.SkillTestRunner.arena(p);                       // (dry ground)
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            b[0] = botAt(p, CharacterType.GARRY, 2, 2); b[1] = botAt(p, CharacterType.STEVIE, -2, 2);
            for (PartyBotEntity x : b) { x.inventory.addItem(new ItemStack(net.minecraft.world.item.Items.JUKEBOX)); }
            b[0].inventory.addItem(new ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_CAT));
            b[1].inventory.addItem(new ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_BLOCKS));
            n0[0] = com.xenowarriors.bot.BotJukebox.played; n0[1] = com.xenowarriors.bot.BotJukebox.newSongs;
            n0[2] = Math.round(com.xenowarriors.party.Bonds.level(p, "garry") * 1000 + com.xenowarriors.party.Bonds.progress(p, "garry"));
        }), 20);
        l.add(null, () -> server(p -> {
            String a = com.xenowarriors.bot.BotJukebox.start(b[0], p.serverLevel(), p, true);
            String c = com.xenowarriors.bot.BotJukebox.start(b[1], p.serverLevel(), p, true);
            int boxes = 0;
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -2; dy <= 2; dy++) if (p.level().getBlockState(p.blockPosition().offset(dx, dy, dz)).is(Blocks.JUKEBOX)) boxes++;
            log("teamjukebox: first bot " + a + "; second bot " + c + "; jukeboxes down " + boxes + " -> " + (a.startsWith("playing") && !c.startsWith("playing") && boxes == 1 ? "PASS" : "FAIL"));
            int ns = com.xenowarriors.bot.BotJukebox.newSongs - n0[1];
            int bond = Math.round(com.xenowarriors.party.Bonds.level(p, "garry") * 1000 + com.xenowarriors.party.Bonds.progress(p, "garry"));
            log("teamjukebox: a song you'd never heard -> new songs " + ns + ", bond with Garry " + n0[2] + " -> " + bond + " -> " + (ns == 1 && bond != n0[2] ? "PASS" : "FAIL"));
        }), 20);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 25), 10);
        l.add("batch4_teamjukebox", () -> {}, 3);
        l.add(null, () -> server(p -> {
            boolean disc = b[0].inventory.countItem(net.minecraft.world.item.Items.MUSIC_DISC_CAT) == 0;
            com.xenowarriors.bot.BotJukebox.songOverForTest(p);
            boolean back = b[0].inventory.countItem(net.minecraft.world.item.Items.MUSIC_DISC_CAT) == 1;
            boolean on = com.xenowarriors.bot.BotJukebox.songOn(p);
            log("teamjukebox: song over -> stops (no loop) " + !on + ", disc back to Garry " + (disc && back) + " -> " + (!on && disc && back ? "PASS" : "FAIL"));
            int before = com.xenowarriors.bot.BotJukebox.played;
            for (int i = 0; i < 60 && com.xenowarriors.bot.BotJukebox.played == before; i++) com.xenowarriors.bot.BotJukebox.tick(b[1]);
            boolean stevie = com.xenowarriors.bot.BotJukebox.playing(b[1]);
            log("teamjukebox: Stevie puts the next song on the same jukebox " + stevie + ", jukeboxes " + com.xenowarriors.bot.BotJukebox.boxesForTest(p) + " -> " + (stevie && com.xenowarriors.bot.BotJukebox.boxesForTest(p) == 1 ? "PASS" : "FAIL"));
            for (int i = 0; i < 60; i++) com.xenowarriors.bot.BotJukebox.tick(b[0]);
            log("teamjukebox: while it plays nobody else puts one on (played " + (com.xenowarriors.bot.BotJukebox.played - before) + ") -> " + (com.xenowarriors.bot.BotJukebox.played - before == 1 ? "PASS" : "FAIL"));
            com.xenowarriors.bot.BotJukebox.endForTest(b[0]);
            // the ride's music: you and Garry in a vehicle, his disc plays from it
            var car = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.CAR).get().create(p.serverLevel());
            car.moveTo(p.getX() + 4, p.getY(), p.getZ() + 4, 0, 0);
            p.serverLevel().addFreshEntity(car);
            p.startRiding(car, true);
            b[0].startRiding(car, true);
            int heard0 = com.xenowarriors.bot.BotJukebox.newSongs;
            b[0].inventory.addItem(new ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_MALL));
            boolean ride = com.xenowarriors.bot.BotJukebox.radioForTest(b[0]);
            log("teamjukebox: in a car together, Garry plays a disc for the ride " + ride + " (songs " + com.xenowarriors.bot.BotJukebox.radioSongs + ", a new one heard " + (com.xenowarriors.bot.BotJukebox.newSongs > heard0) + ") -> " + (ride ? "PASS" : "FAIL"));
            p.stopRiding();
            for (int i = 0; i < 3; i++) com.xenowarriors.bot.BotJukebox.tick(b[0]);
            log("teamjukebox: you get out -> the ride's song stops " + !com.xenowarriors.bot.BotJukebox.radioOn(p) + " -> " + (!com.xenowarriors.bot.BotJukebox.radioOn(p) ? "PASS" : "FAIL"));
            b[0].stopRiding(); car.discard();
            log("teamjukebox: packed up -> jukebox back with Garry " + (b[0].inventory.countItem(net.minecraft.world.item.Items.JUKEBOX) == 1) + ", Blocks disc back with Stevie " + (b[1].inventory.countItem(net.minecraft.world.item.Items.MUSIC_DISC_BLOCKS) == 1)
                    + " -> " + (b[0].inventory.countItem(net.minecraft.world.item.Items.JUKEBOX) == 1 && b[1].inventory.countItem(net.minecraft.world.item.Items.MUSIC_DISC_BLOCKS) == 1 ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        }), 10);
        l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3);
    }

    private static void pickupMusic(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("pickup: no bot FAIL"); return; }
            b.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0);
            var it = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX() + 1.2, p.getY(), p.getZ() + 1.2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
            it.setPickUpDelay(10); it.addTag("xw_b4");
            p.level().addFreshEntity(it);
            b.inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_CAT));
            b.inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 8));
            b.inventory.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1));
        }), 20);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            int dia = b == null ? -1 : b.inventory.countItem(net.minecraft.world.item.Items.DIAMOND);
            log("pickup: your inventory full, the bot picked up the diamonds by you: bot has " + dia + " diamonds -> " + (dia >= 3 ? "PASS" : "FAIL"));
            for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
            if (b != null) b.inventory.removeItemType(net.minecraft.world.item.Items.DIAMOND, Math.max(0, dia - 1));
            String m = b == null ? "-" : com.xenowarriors.bot.BotJukebox.start(b, p.serverLevel(), p, true);
            log("music: " + m + " -> " + (m.startsWith("playing") ? "PASS" : "FAIL"));
        }), 120);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_BACK, 0, 25), 20);
        l.add("batch4_jukebox", () -> {}, 3);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            if (b == null) return;
            com.xenowarriors.bot.BotJukebox.endForTest(b);
            boolean back = b.inventory.countItem(net.minecraft.world.item.Items.JUKEBOX) == 1 && b.inventory.countItem(net.minecraft.world.item.Items.MUSIC_DISC_CAT) == 1;
            log("music: bag now jukebox x" + b.inventory.countItem(net.minecraft.world.item.Items.JUKEBOX) + " disc x" + b.inventory.countItem(net.minecraft.world.item.Items.MUSIC_DISC_CAT) + " playing " + com.xenowarriors.bot.BotJukebox.playingForTest(b));
            log("music: packed up -> jukebox and disc back in its bag " + back + " -> " + (back ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 10);
    }

    // ------------------------------------------------------------------ party time, creepers, the Ender Saiyan, the party Mission Bag

    private static void partyTime(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity a = PartyBots.spawn(p, CharacterType.RHALIX, null), b = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (a != null) a.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 180, 0);
            if (b != null) b.moveTo(p.getX() - 2, p.getY(), p.getZ() + 2, 180, 0);
            START[0] = com.xenowarriors.party.Bonds.progress(p, "rhalix") + com.xenowarriors.party.Bonds.level(p, "rhalix");
            boolean on = com.xenowarriors.bot.BotPartyTime.start(p);
            log("party: started " + on + " -> " + (on ? "PASS" : "FAIL"));
        }), 30);
        l.add(null, () -> Batch3Tests.view(mc, CameraType.THIRD_PERSON_FRONT, 0, 10), 60);
        l.add("batch4_party", () -> {
            int hats = 0;
            for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.LivingEntity le && com.xenowarriors.client.PartyHats.wearing(le)) hats++;
            log("party: hats on " + hats + " -> " + (hats >= 3 ? "PASS" : "FAIL"));
        }, 3);
        l.add(null, () -> server(p -> {
            com.xenowarriors.bot.BotPartyTime.endForTest(p);
            float after = com.xenowarriors.party.Bonds.progress(p, "rhalix") + com.xenowarriors.party.Bonds.level(p, "rhalix");
            log("party: over, bond with Rhalix " + Batch3Tests.f1(START[0]) + " -> " + Batch3Tests.f1(after) + ", fireworks " + com.xenowarriors.bot.BotPartyTime.fireworks
                    + " -> " + (after > START[0] && !com.xenowarriors.bot.BotPartyTime.active(p) ? "PASS" : "FAIL"));
            Batch3Tests.clearBots(p);
        }), 20);
    }

    private static void creepers(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            Batch3Tests.clearBots(p);
            PartyBotEntity slow = PartyBots.spawn(p, CharacterType.KAERU, null);
            if (slow == null) return;
            slow.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0);
            var c = net.minecraft.world.entity.EntityType.CREEPER.create(p.serverLevel());
            c.moveTo(p.getX() + 3, p.getY(), p.getZ() + 4, 0, 0); c.addTag("xw_b4");
            c.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
            p.serverLevel().addFreshEntity(c);
            slow.setTarget(c);
            log("creeper: Kaeru's Speed " + PartyBots.statLevel(slow, com.xenowarriors.stats.Stat.SPEED) + " (needs " + PartyBotEntity.CREEPER_SPEED + ")");
        }), 20);
        l.add(null, () -> server(p -> {
            PartyBotEntity b = PartyBots.bots(p).isEmpty() ? null : PartyBots.bots(p).get(0);
            var cs = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, p.getBoundingBox().inflate(40), x -> x.getTags().contains("xw_b4"));
            if (b == null || cs.isEmpty()) { log("creeper: setup FAIL"); return; }
            boolean quick = PartyBots.statLevel(b, com.xenowarriors.stats.Stat.SPEED) >= PartyBotEntity.CREEPER_SPEED || b.canFly();
            float d = b.distanceTo(cs.get(0));
            log("creeper: " + (quick ? "quick" : "slow") + " bot, distance " + Batch3Tests.f1(d) + ", hits " + b.creeperHits + " -> "
                    + ((quick ? b.creeperHits > 0 : d >= 6.5F && b.creeperHits == 0) ? "PASS" : "FAIL"));
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains("xw_b4")).forEach(e -> e.discard());
            Batch3Tests.clearBots(p);
        }), 160);
    }

    private static final Object[] ENDER = new Object[1];

    private static void enderSaiyan(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var s = com.xenowarriors.boss.EnderSaiyans.ENDER_SAIYAN.get().create(p.serverLevel());
            s.moveTo(p.getX(), p.getY(), p.getZ() + 5, 180, 0); s.setNoAi(true); s.addTag("xw_b4");
            p.serverLevel().addFreshEntity(s);
            ENDER[0] = s;
        }), 20);
        String[] names = {"base", "awakened", "ascended", "overcharged", "void"};
        for (int f = 0; f <= 4; f++) {
            final int ff = f;
            l.add(null, () -> server(p -> { if (ENDER[0] instanceof com.xenowarriors.boss.EnderSaiyanEntity s && ff > 0) s.setFormForTest(ff); }), 25);
            l.add(null, () -> Batch3Tests.view(mc, CameraType.FIRST_PERSON, 0, -8), 5);
            l.add("batch4_ender_" + names[f], () -> {}, 3);
        }
        l.add(null, () -> server(p -> {
            if (!(ENDER[0] instanceof com.xenowarriors.boss.EnderSaiyanEntity s)) { log("ender: FAIL no boss"); return; }
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            s.setNoAi(false);
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 8, p.getY(), p.getZ() + 5, 0, 0); z.setNoAi(true); z.addTag("xw_b4");
            p.serverLevel().addFreshEntity(z);
            s.blinkForTest(z); s.boltsForTest(z); s.pullForTest(); s.clonesForTest(z);
            log("ender: moves blink " + s.blinks + " bolts " + s.bolts + " pull " + s.pulls + " clones " + s.clones + " boss=" + com.xenowarriors.karma.KarmaSensing.boss(s)
                    + " -> " + (s.bolts > 0 && s.pulls > 0 && s.clones > 0 && com.xenowarriors.karma.KarmaSensing.boss(s) ? "PASS" : "FAIL"));
        }), 10);
        l.add(null, () -> server(p -> {                                          // (the throw on its own: the pull above would cut it short)
            if (!(ENDER[0] instanceof com.xenowarriors.boss.EnderSaiyanEntity s)) return;
            var z = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, s.getBoundingBox().inflate(16), x -> x.getTags().contains("xw_b4")).stream().findFirst().orElse(null);
            if (z != null) s.setTarget(z);
            s.throwForTest();
        }), 40);
        l.add(null, () -> server(p -> {
            if (!(ENDER[0] instanceof com.xenowarriors.boss.EnderSaiyanEntity s)) return;
            log("ender: block thrown " + s.throwsDone + " -> " + (s.throwsDone > 0 ? "PASS" : "FAIL"));
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains("xw_b4")).forEach(e -> e.discard());
        }), 60);
    }

    private static void bagMerge(Minecraft mc, MikikoTests.Steps l) {
        Batch3Tests.character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            var bag = com.xenowarriors.compat.MissionBag.of(p);
            bag.clearContent();
            com.xenowarriors.compat.MissionBag.add(p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 5));
            int had = bag.countItem(net.minecraft.world.item.Items.BONE);
            CharacterManagerHop.to(p, CharacterType.RHALIX);
            int after = com.xenowarriors.compat.MissionBag.of(p).countItem(net.minecraft.world.item.Items.BONE);
            log("bag: 5 bones in the bag, switched to Rhalix -> still " + after + " -> " + (had == 5 && after == 5 ? "PASS" : "FAIL"));
        }), 20);
    }

    /** (a character switch from a test) */
    static final class CharacterManagerHop { static void to(ServerPlayer p, CharacterType t) { com.xenowarriors.character.CharacterManager.setCharacter(p, t); } }
}

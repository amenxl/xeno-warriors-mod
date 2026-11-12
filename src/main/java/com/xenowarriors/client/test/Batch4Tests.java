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
        if (dmz && (f.isEmpty() || f.contains("formlook"))) formLook(mc, l);
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
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); server(Batch3Tests::clearBots); log("done"); }, 5);
    }

    // ------------------------------------------------------------------ 2v2 spars; bot outfits; phoning busy teammates; off-duty cards; wires

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

    private static void formLook(Minecraft mc, MikikoTests.Steps l) {
        String[][] forms = {{"supersaiyan", "supersaiyan"}, {"supersaiyan", "supersaiyan3"}, {"supersaiyan", "supersaiyan4"}, {"legendaryforms", "ssjfullpower"}};
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.MIKIKO, CharacterType.KYRIE}) {
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
                    boolean shirtless = t != CharacterType.KYRIE;
                    boolean ok = want.equals(set) && (!fm[1].equals("supersaiyan4") || (own && hair == 4 && !dmzDraws && me.getSkinTextureLocation().getPath().endsWith("_ssj4.png") == shirtless));
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

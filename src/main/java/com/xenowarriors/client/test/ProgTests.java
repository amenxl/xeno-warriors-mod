package com.xenowarriors.client.test;

import com.xenowarriors.character.BigHunger;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientSettings;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.RepRules;
import com.xenowarriors.faction.Reputation;
import com.xenowarriors.party.Bonds;
import com.xenowarriors.party.Synergy;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillLevels;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.stats.Appetite;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * UI tests for progression and menus (focus "prog5"): ability levels and tier upgrades, Appetite and hunger layers,
 * bond levels and synergy, the faction rules (Gov donations, caps, defending agents) and the guide.
 * Results are logged as "XW-TEST prog ...".
 */
public final class ProgTests {
    private ProgTests() {}

    private static void log(String s) { UiTest.log("prog " + s); }

    private static final Object[] ZOMBIE = new Object[1];

    private static Zombie dummy(ServerPlayer p) {
        if (ZOMBIE[0] instanceof Zombie z && z.isAlive()) { z.setHealth(z.getMaxHealth()); return z; }
        Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
        z.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0);
        z.setNoAi(true); z.setPersistenceRequired();
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000); z.setHealth(10000);
        z.getAttribute(Attributes.ARMOR).setBaseValue(0);
        p.serverLevel().addFreshEntity(z);
        ZOMBIE[0] = z;
        return z;
    }

    /** Damage {@code hit} deals to the dummy. */
    private static float dealt(ServerPlayer p, Runnable hit) {
        Zombie z = dummy(p);
        z.invulnerableTime = 0; z.hurtTime = 0;
        float h = z.getHealth();
        hit.run();
        return h - z.getHealth();
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.serverLevel().setDayTime(6000); p.setHealth(p.getMaxHealth()); }); }, 40));
        skills(mc, l);
        appetite(mc, l);
        bonds(mc, l);
        factions(mc, l);
        guide(mc, l);
        l.add(new Step(null, () -> log("done"), 5));
    }

    // ------------------------------------------------------------------------------------------------ 4. ability levels

    private static void skills(Minecraft mc, List<Step> l) {
        final String[] id = new String[2];
        l.add(new Step(null, () -> UiTest.server(p -> {
            Skill s = SkillManager.get(p, 0), s2 = SkillManager.get(p, 1);
            id[0] = s.getId(); id[1] = s2.getId();
            int base = s.getKarmaCost(p), cd = s.getCooldownTicks();
            SkillLevels.set(p, id[0], 1, 9, SkillLevels.needed(1, 9) - 1);       // one use from level 10
            SkillManager.resetCooldowns(p);
            com.xenowarriors.karma.Karma.add(p, 1000F);
            float k0 = com.xenowarriors.karma.Karma.get(p);
            SkillManager.useSkill(p, 0);
            float spent = k0 - com.xenowarriors.karma.Karma.get(p);
            long cdLeft = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.cooldownEnd[0] - p.server.overworld().getGameTime()).orElse(-1L);
            log("skill " + id[0] + " base cost=" + base + " cd=" + cd + " | at T1 Lv9 spent=" + spent + " (expect " + SkillLevels.cost(base, 1, 9) + ") cdSet=" + cdLeft
                    + " (expect " + SkillLevels.cooldown(cd, 1, 9) + ") -> now T" + SkillLevels.tier(p, id[0]) + " Lv" + SkillLevels.level(p, id[0])
                    + " abilityLevel=" + SkillLevels.abilityLevel(p, id[0]));
            for (int t = 1; t <= 10; t++)
                log("tier " + t + ": Lv1 cost=" + SkillLevels.cost(base, t, 1) + " cd=" + SkillLevels.cooldown(cd, t, 1) + "  Lv10 cost=" + SkillLevels.cost(base, t, 10)
                        + " cd=" + SkillLevels.cooldown(cd, t, 10) + "  dmg x" + String.format("%.2f", SkillLevels.damageMult(t)));
            // damage: the same hit, as this skill, at tier 1 and tier 3
            Zombie z = dummy(p);
            float t1 = dealt(p, () -> com.xenowarriors.combat.SkillContext.callSkill(p, id[0], () -> z.hurt(p.damageSources().playerAttack(p), 10F)));
            SkillLevels.set(p, id[0], 3, 1, 0);
            float t3 = dealt(p, () -> com.xenowarriors.combat.SkillContext.callSkill(p, id[0], () -> z.hurt(p.damageSources().playerAttack(p), 10F)));
            float plain = dealt(p, () -> z.hurt(p.damageSources().playerAttack(p), 10F));
            log("damage tier1=" + t1 + " tier3=" + t3 + " ratio=" + String.format("%.3f", t3 / Math.max(0.001F, t1)) + " (expect " + String.format("%.3f", SkillLevels.damageMult(3)) + ") plain hit=" + plain);
            // the upgrade: only from level 10
            SkillLevels.set(p, id[0], 1, 10, 0);
            boolean up = SkillLevels.upgrade(p, id[0]);
            boolean again = SkillLevels.upgrade(p, id[0]);
            log("upgrade at Lv10 ok=" + up + " -> T" + SkillLevels.tier(p, id[0]) + " Lv" + SkillLevels.level(p, id[0]) + "; again at Lv1 refused=" + !again);
            boolean bogus = SkillLevels.upgrade(p, "not_a_skill");
            log("upgrade unknown skill refused=" + !bogus);
            // for the screenshot: the first card ready to upgrade, the second at a high tier
            SkillLevels.set(p, id[0], 2, 10, 0);
            SkillLevels.set(p, id[1], 6, 4, 3);
            // a bot of this character uses its owner's levels
            com.xenowarriors.bot.PartyBotEntity b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b != null) {
                Skill rs = SkillManager.get(CharacterType.RHALIX, 0);
                int before = SkillLevels.costFor(b, rs.getId(), rs.getKarmaCost());
                log("bot accessor rhalix " + rs.getId() + " owner level=" + SkillLevels.abilityLevel(p, CharacterType.RHALIX, rs.getId()) + " bot cost=" + before);
                b.discard();
            }
        }), 30));
        l.add(new Step(null, () -> com.xenowarriors.client.menu.CharacterCustomScreen.open(null), 30));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.testTab(1); }, 40));
        l.add(new Step("prog_skill_cards", () -> log("client levels " + id[0] + "=" + java.util.Arrays.toString(com.xenowarriors.client.ClientSkillLevels.of(id[0]))
                + " canUpgrade=" + com.xenowarriors.client.ClientSkillLevels.canUpgrade(id[0]) + " " + id[1] + "=" + java.util.Arrays.toString(com.xenowarriors.client.ClientSkillLevels.of(id[1]))), 5));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.testUpgrade(id[0]); }, 30));
        l.add(new Step("prog_skill_upgraded", () -> log("after upgrade button " + id[0] + "=" + java.util.Arrays.toString(com.xenowarriors.client.ClientSkillLevels.of(id[0]))), 5));
        l.add(new Step(null, () -> mc.setScreen(null), 20));
        l.add(new Step("prog_skill_hud", () -> {}, 5));
    }

    // ------------------------------------------------------------------------------------------------ 3. appetite

    private static void eat(ServerPlayer p, ItemStack food) {
        ItemStack copy = food.copy();
        ItemStack result = food.finishUsingItem(p.level(), p);
        net.minecraftforge.event.ForgeEventFactory.onItemUseFinish(p, copy, 0, result);
    }

    private static void appetite(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { UiTest.character(CharacterType.MAX); }, 30));                 // (a normal 20-point stomach)
        l.add(new Step(null, () -> UiTest.server(p -> {
            Appetite.set(p, 0, 0);
            p.getFoodData().setFoodLevel(4); p.getFoodData().setSaturation(0);
            eat(p, new ItemStack(Items.COOKED_BEEF));
            log("appetite lv0 steak: food=" + p.getFoodData().getFoodLevel() + " (expect 12) xp=" + String.format("%.2f", Appetite.progress(p)) + " max=" + BigHunger.max(p));
            Appetite.set(p, 50, 0);
            p.getFoodData().setFoodLevel(4);
            eat(p, new ItemStack(Items.COOKED_BEEF));
            log("appetite lv50 steak: food=" + p.getFoodData().getFoodLevel() + " (expect 4+8+4=16) layers=" + Appetite.layers(p) + " max=" + BigHunger.max(p) + " (expect 120)");
            Appetite.set(p, 9, Appetite.needed(9) - 1);
            eat(p, new ItemStack(Items.COOKED_BEEF));
            log("appetite level-up from food: lv=" + Appetite.level(p) + " layers=" + Appetite.layers(p));
            Appetite.set(p, 30, 0);                                                    // 3 extra layers = 80 points
        }), 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("big stomach: max=" + BigHunger.max(p) + " total=" + BigHunger.total(p) + " bar=" + p.getFoodData().getFoodLevel());
            for (int i = 0; i < 6; i++) eat(p, new ItemStack(Items.COOKED_BEEF));
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("after 6 steaks: total=" + BigHunger.total(p) + "/" + BigHunger.max(p) + " bar=" + p.getFoodData().getFoodLevel() + " (bar stays <=19 until full: can still eat, sprint, regen)");
            p.causeFoodExhaustion(40F);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> log("after exhaustion 40: total=" + BigHunger.total(p) + " bar=" + p.getFoodData().getFoodLevel()
                + " sprintable=" + (p.getFoodData().getFoodLevel() > 6))), 10));
        String[] styles = {"classic", "blazing", "kh"};
        String was = ClientSettings.hudStyle();
        for (String st : styles) {
            l.add(new Step(null, () -> { ClientSettings.setHudStyle(st); mc.options.hideGui = false; }, 25));
            l.add(new Step("prog_hunger_" + st, () -> log("hud " + st + " client layers=" + com.xenowarriors.client.BigHungerHud.layers() + " label=" + com.xenowarriors.client.BigHungerHud.layerLabel()), 5));
        }
        l.add(new Step(null, () -> ClientSettings.setHudStyle(was), 5));
    }

    // ------------------------------------------------------------------------------------------------ 1+2. bond & synergy

    private static final Object[] BOT = new Object[1];

    private static void bonds(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            for (var b : new java.util.ArrayList<>(com.xenowarriors.bot.PartyBots.bots(p))) b.discard();
            BOT[0] = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
            Bonds.set(p, "rhalix", 0, 0);
        }), 60));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("synergy active=" + Synergy.of(p) + " partners=" + Synergy.partners(p).size());
            Zombie z = dummy(p);
            float b0 = dealt(p, () -> z.hurt(p.damageSources().playerAttack(p), 10F));
            Bonds.set(p, "rhalix", 100, 0);
            float b100 = dealt(p, () -> z.hurt(p.damageSources().playerAttack(p), 10F));
            log("Partners damage bond0=" + b0 + " bond100=" + b100 + " ratio=" + String.format("%.3f", b100 / Math.max(0.001F, b0)) + " (expect " + String.format("%.3f", 1.30F / 1.15F) + ")");
            log("effect text bond0='" + Synergy.effect(Synergy.PARTNERS, 0) + "' bond100='" + Synergy.effect(Synergy.PARTNERS, 100) + "'");
            log("soul link downside: bond0=" + Bonds.downside(0) + " bond25=" + Bonds.downside(25) + " bond50=" + Bonds.downside(50));
            float hp0 = p.getMaxHealth();
            Bonds.set(p, "rhalix", 0, 0);
            float hpNone = p.getMaxHealth();
            Bonds.set(p, "rhalix", 37, 0);
            log("bond hearts: maxHP bond100=" + hp0 + " bond0=" + hpNone + " bond37=" + p.getMaxHealth() + " (expect +" + Bonds.hearts(37) * 2 + " HP; per10=" + Bonds.heartsPer10() + ")");
            float before = Bonds.progress(p, "rhalix");
            if (BOT[0] instanceof LivingEntity b) Bonds.revived(b, p);
            log("revive bond xp: " + String.format("%.3f", before) + " -> " + String.format("%.3f", Bonds.progress(p, "rhalix")) + " lv=" + Bonds.level(p, "rhalix"));
            Bonds.set(p, "rhalix", 37, 0);
        }), 30));
        l.add(new Step(null, () -> UiTest.server(p -> log("near-xp start=" + String.format("%.3f", Bonds.progress(p, "rhalix")))), 100));
        l.add(new Step(null, () -> UiTest.server(p -> {
            log("near-xp after 5s=" + String.format("%.3f", Bonds.progress(p, "rhalix")) + " (+" + Bonds.NEAR_XP + "/s out of combat, needs " + Bonds.needed(37) + ")");
            // a kill with the bot nearby
            float b = Bonds.progress(p, "rhalix");
            Zombie k = EntityType.ZOMBIE.create(p.serverLevel());
            k.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0); k.setNoAi(true);
            p.serverLevel().addFreshEntity(k);
            k.hurt(p.damageSources().playerAttack(p), 1000F);
            log("kill bond xp: " + String.format("%.3f", b) + " -> " + String.format("%.3f", Bonds.progress(p, "rhalix")));
        }), 30));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.party.PartySetupScreen()), 60));
        l.add(new Step("prog_party_bond", () -> {
            var c = com.xenowarriors.client.party.PartySetupScreen.lastCards();
            log("party cards=" + (c == null ? -1 : c.cards().size()) + " bonds=" + (c == null ? "" : c.cards().stream().map(x -> x.name() + ":" + x.bond()).toList())
                    + " synergies=" + (c == null ? "" : c.synergies().stream().map(x -> x.name() + "/" + x.state() + "/" + x.bond()).toList()) + " hearts=" + (c == null ? 0 : c.bondHearts()));
        }, 5));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.party.PartySetupScreen s) s.testSynergy(true); }, 20));
        l.add(new Step("prog_party_synergy", () -> {}, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> { for (var b : new java.util.ArrayList<>(com.xenowarriors.bot.PartyBots.bots(p))) b.discard(); }); }, 20));
    }

    // ------------------------------------------------------------------------------------------------ 6. factions

    private static void factions(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            Reputation.set(p, Faction.GOV, -200);
            int r0 = Reputation.get(p, Faction.GOV);
            RepRules.GOV_DONATE.apply(p, 10);
            int r1 = Reputation.get(p, Faction.GOV);
            RepRules.GOV_DONATE.apply(p, 50);
            int r2 = Reputation.get(p, Faction.GOV);
            RepRules.GOV_DONATE.apply(p, 5);
            log("gov donate: " + r0 + " -> +10 -> " + r1 + " -> +50 (cap 40/day) -> " + r2 + " -> again " + Reputation.get(p, Faction.GOV));
            // the terminal itself: sneak + right-click with Gov gear
            BlockPos at = p.blockPosition().relative(p.getDirection(), 2);
            p.serverLevel().setBlockAndUpdate(at, com.xenowarriors.gov.GovItems.TERMINAL.get().defaultBlockState());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.xenowarriors.gov.GovItems.KEYCARD.get(), 2));
            p.setShiftKeyDown(true);
            long m0 = com.xenowarriors.economy.Money.get(p);
            int g0 = Reputation.get(p, Faction.GOV);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false);
            var ev = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, at, hit);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev);
            p.setShiftKeyDown(false);
            log("gov return keycard: rep " + g0 + " -> " + Reputation.get(p, Faction.GOV) + " money +" + (com.xenowarriors.economy.Money.get(p) - m0) + " keycards left=" + p.getMainHandItem().getCount() + " cancelled=" + ev.isCanceled());
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // defending a Gov agent
            com.xenowarriors.gov.GovAgentEntity agent = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel());
            agent.moveTo(p.getX() + 5, p.getY(), p.getZ(), 0, 0);
            p.serverLevel().addFreshEntity(agent);
            Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
            z.moveTo(p.getX() + 4, p.getY(), p.getZ() + 1, 0, 0);
            p.serverLevel().addFreshEntity(z);
            z.setTarget(agent);
            int g1 = Reputation.get(p, Faction.GOV);
            z.hurt(p.damageSources().playerAttack(p), 1000F);
            log("gov defend agent: " + g1 + " -> " + Reputation.get(p, Faction.GOV) + " (expect +" + RepRules.GOV_HELP_AGENT.delta + ")");
            agent.discard();
            p.serverLevel().removeBlock(at, false);
            for (Faction f : Faction.values())
                log("howto " + f.name() + " raise=" + RepRules.lines(f, true).size() + " lower=" + RepRules.lines(f, false).size());
        }), 30));
        l.add(new Step(null, () -> com.xenowarriors.client.menu.FactionsScreen.open(null), 40));
        l.add(new Step("prog_factions", () -> {}, 5));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.FactionsScreen f) f.testDetail(Faction.GOV); }, 20));
        l.add(new Step("prog_factions_gov", () -> {}, 5));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.FactionsScreen f) f.testDetail(Faction.TOWN); }, 20));
        l.add(new Step("prog_factions_town", () -> {}, 5));
        l.add(new Step(null, () -> mc.setScreen(null), 10));
    }

    // ------------------------------------------------------------------------------------------------ 5. guide

    private static void guide(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            boolean has = false;
            for (ItemStack s : p.getInventory().items) if (s.is(com.xenowarriors.item.ModItems.GUIDE_BOOK.get())) has = true;
            log("guide book given on first join=" + has);
        }), 5));
        l.add(new Step(null, () -> {
            var g = new com.xenowarriors.client.menu.GuideScreen(null);
            mc.setScreen(g);
            log("guide chapters=" + g.chapters() + " list=" + com.xenowarriors.client.menu.GuideScreen.load().stream().map(c -> c.title() + "(" + c.pages().size() + ")").toList());
        }, 30));
        int[][] pages = {{0, 1}, {1, 5}, {2, 3}, {6, 1}};
        for (int[] pg : pages) {
            l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.GuideScreen g) g.show(pg[0], pg[1]); }, 15));
            l.add(new Step("prog_guide_" + pg[0] + "_" + pg[1], () -> { if (mc.screen instanceof com.xenowarriors.client.menu.GuideScreen g) log("guide page " + pg[0] + "/" + pg[1] + " = " + g.pageTitle()); }, 5));
        }
        l.add(new Step(null, () -> mc.setScreen(null), 10));
    }
}

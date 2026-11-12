package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.stats.Stat;
import com.xenowarriors.statboost.BoostStat;
import com.xenowarriors.statboost.BoostTier;
import com.xenowarriors.statboost.StatBoostItem;
import com.xenowarriors.statboost.StatBoosts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * UI tests for Stat Boosts (focus "statboost", "dmzstatboost" with DragonMineZ): every stat x tier adds exactly its
 * levels and stops at the max, the creative tab holds them all, forced-roll drops from enemies / bosses / very strong
 * enemies (the +100 only from those), bots' kills, shovel on dirt, level-up drops for each stat (players and bots), no
 * chains from boosts. Results are logged as "XW-TEST statboost ...".
 */
public final class StatBoostTests {
    private StatBoostTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("statboost"); }

    private static void log(String s) { UiTest.log("statboost " + s); }

    private static String pass(boolean ok) { return ok ? "PASS" : "FAIL"; }

    private static final BlockPos[] ORIGIN = new BlockPos[1];

    /** Sets a stat straight to a level (test setup). */
    static void set(ServerPlayer p, BoostStat s, int lv) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            switch (s) {
                case SPEED, ATTACK, DEFENSE -> { d.statLevel[s.ordinal()] = lv; d.statXp[s.ordinal()] = 0; }
                case MINING -> { d.miningLevel = lv; d.miningXp = 0; }
                case CRAFTING -> { d.craftLevel = lv; d.craftXp = 0; }
                case SMELTING -> { d.smeltLevel = lv; d.smeltXp = 0; }
                case HEKUBLADE -> { d.bladeLevel = Math.max(1, lv); d.bladeXp = 0; }
                default -> {}
            }
        });
        switch (s) {
            case APPETITE -> com.xenowarriors.stats.Appetite.set(p, lv, 0);
            case BUILDING -> com.xenowarriors.stats.Building.set(p, lv, 0);
            case WIRES -> com.xenowarriors.rhalix.WireLevel.set(p, Math.max(1, lv));
            case GUNS -> com.xenowarriors.compat.GunLevel.setLevel(p, Math.max(1, lv));
            default -> {}
        }
    }

    /** Uses the boost in the main hand once, the way a right-click does on the server. */
    static boolean use(ServerPlayer p, BoostStat s, BoostTier t, int count) {
        p.getCooldowns().removeCooldown(StatBoosts.item(s, t));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StatBoosts.item(s, t), count));
        return p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND).getResult().consumesAction();
    }

    private static int boostsIn(ServerPlayer p, BoostStat s) {
        int n = 0;
        for (ItemStack it : p.getInventory().items) if (it.getItem() instanceof StatBoostItem b && b.stat == s) n += it.getCount();
        return n;
    }

    private static List<StatBoostItem> groundBoosts(ServerLevel l, AABB box, boolean remove) {
        List<StatBoostItem> r = new ArrayList<>();
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().getItem() instanceof StatBoostItem)) {
            for (int i = 0; i < e.getItem().getCount(); i++) r.add((StatBoostItem) e.getItem().getItem());
            if (remove) e.discard();
        }
        return r;
    }

    private static String describe(List<StatBoostItem> l) {
        StringBuilder sb = new StringBuilder("[");
        for (StatBoostItem b : l) sb.append(sb.length() > 1 ? "," : "").append(b.stat.id).append("+").append(b.tier.levels);
        return sb.append("]").toString();
    }

    private static void clearGround(ServerPlayer p) {
        groundBoosts(p.serverLevel(), p.getBoundingBox().inflate(40), true);
    }

    /** Kills a fresh mob of this type at a spot in front of the player, by {@code killer}; returns the boosts it dropped. */
    private static List<StatBoostItem> kill(ServerPlayer p, EntityType<? extends Mob> type, LivingEntity killer, double hp) {
        ServerLevel l = p.serverLevel();
        BlockPos at = ORIGIN[0].offset(6, 0, 0);
        Mob m = type.create(l);
        if (m == null) return List.of();
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        m.setNoAi(true);
        if (hp > 0) { m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp); m.setHealth((float) hp); }
        l.addFreshEntity(m);
        groundBoosts(l, m.getBoundingBox().inflate(4), true);
        groundBoosts(l, p.getBoundingBox().inflate(3), true);
        var src = killer instanceof ServerPlayer sp ? l.damageSources().playerAttack(sp) : l.damageSources().mobAttack(killer);
        for (int i = 0; i < 5 && m.isAlive(); i++) { m.invulnerableTime = 0; m.hurt(src, 1.0E7F); }
        if (m.isAlive()) { log("could not kill " + type + " -> FAIL"); m.discard(); }
        List<StatBoostItem> r = groundBoosts(l, new AABB(at).inflate(5), true);
        r.addAll(groundBoosts(l, p.getBoundingBox().inflate(3), true));    // (Mikiko's kills are absorbed: their drops land at his feet)
        m.discard();
        return r;
    }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> {
                p.setGameMode(GameType.SURVIVAL); p.serverLevel().setDayTime(6000); p.setHealth(p.getMaxHealth());
                ORIGIN[0] = p.blockPosition();
                p.getInventory().clearContent();
                StatBoosts.testRoll = null; StatBoosts.testStatRoll = null;
            }); }, 40));

        // ---- 1. every stat x tier: exact levels, and the max
        l.add(new Step(null, () -> UiTest.server(p -> {
            int ok = 0, total = 0;
            StringBuilder bad = new StringBuilder();
            for (BoostStat s : BoostStat.values()) {
                if (s == BoostStat.WIRES) continue;                      // (Rhalix's: below)
                for (BoostTier t : BoostTier.values()) {
                    int base = s == BoostStat.APPETITE ? (t == BoostTier.HUNDRED ? 0 : 10) : 20;
                    set(p, s, base);
                    int before = s.level(p);
                    boolean used = use(p, s, t, 3);
                    int after = s.level(p), left = p.getMainHandItem().getCount();
                    int want = Math.min(s.max(), before + t.levels);
                    total++;
                    if (used && after == want && left == 2) ok++;
                    else bad.append(" ").append(s.id).append("+").append(t.levels).append(":").append(before).append("->").append(after).append(" left ").append(left);
                }
            }
            log("Mikiko: " + ok + "/" + total + " stat x tier uses gave exactly +N and used one" + bad + " -> " + pass(ok == total && total == 50));
            // the max: +100 three below the cap gives 3; at the cap it isn't used
            ok = 0; total = 0; bad = new StringBuilder();
            for (BoostStat s : BoostStat.values()) {
                if (s == BoostStat.WIRES) continue;
                set(p, s, s.max() - 3);
                boolean used = use(p, s, BoostTier.HUNDRED, 1);
                int a = s.level(p);
                boolean used2 = use(p, s, BoostTier.ONE, 1);
                int b = s.level(p), left = p.getMainHandItem().getCount();
                total++;
                if (used && a == s.max() && !used2 && b == s.max() && left == 1) ok++;
                else bad.append(" ").append(s.id).append(": ").append(a).append("/").append(b).append(" used2=").append(used2);
            }
            log("caps (+100 at max-3 -> max, then +1 refused and kept): " + ok + "/" + total + bad + " -> " + pass(ok == total));
            // whole stack with sneak, capped
            set(p, BoostStat.MINING, 100);
            p.setShiftKeyDown(true);
            use(p, BoostStat.MINING, BoostTier.TEN, 7);
            p.setShiftKeyDown(false);
            log("sneak-use 7x Mining +10 from Lv100: Lv" + BoostStat.MINING.level(p) + " stack left " + p.getMainHandItem().getCount() + " (expect 170, 0) -> "
                    + pass(BoostStat.MINING.level(p) == 170 && p.getMainHandItem().isEmpty()));
            set(p, BoostStat.APPETITE, 95);
            p.setShiftKeyDown(true);
            use(p, BoostStat.APPETITE, BoostTier.TWO, 10);
            p.setShiftKeyDown(false);
            log("sneak-use 10x Appetite +2 from 95 (max 100): Lv" + BoostStat.APPETITE.level(p) + " left " + p.getMainHandItem().getCount() + " (expect 100, 7) -> "
                    + pass(BoostStat.APPETITE.level(p) == 100 && p.getMainHandItem().getCount() == 7));
            // the Attack boost raises the Character Level like a real level
            for (Stat st : Stat.values()) set(p, BoostStat.of(st), 0);
            com.xenowarriors.stats.Stats.reapply(p);
            int cl0 = com.xenowarriors.stats.Stats.characterLevel(p);
            use(p, BoostStat.ATTACK, BoostTier.HUNDRED, 1);
            log("Attack +100: Character Level " + cl0 + " -> " + com.xenowarriors.stats.Stats.characterLevel(p) + " -> " + pass(com.xenowarriors.stats.Stats.characterLevel(p) > cl0));
            // Mikiko can't use the wires boost; creative doesn't use it up
            boolean wires = use(p, BoostStat.WIRES, BoostTier.ONE, 1);
            log("Mikiko uses a Wires boost: used=" + wires + " left " + p.getMainHandItem().getCount() + " (expect false, 1) -> " + pass(!wires && p.getMainHandItem().getCount() == 1));
            p.setGameMode(GameType.CREATIVE);
            set(p, BoostStat.DEFENSE, 5);
            use(p, BoostStat.DEFENSE, BoostTier.FIVE, 1);
            log("creative: Defense 5 -> " + BoostStat.DEFENSE.level(p) + " stack " + p.getMainHandItem().getCount() + " (expect 10, 1 kept) -> "
                    + pass(BoostStat.DEFENSE.level(p) == 10 && p.getMainHandItem().getCount() == 1));
            p.setGameMode(GameType.SURVIVAL);
            for (BoostStat s : BoostStat.values()) set(p, s, 0);
            com.xenowarriors.stats.Stats.reapply(p);
            p.getInventory().clearContent();
        }), 20));
        // Rhalix (Season 2 is unlocked by the test setup): the wires boost, and the Hekublade one refused
        l.add(new Step(null, () -> UiTest.character(CharacterType.RHALIX), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            StringBuilder sb = new StringBuilder();
            int ok = 0;
            for (BoostTier t : BoostTier.values()) {
                set(p, BoostStat.WIRES, 20);
                boolean used = use(p, BoostStat.WIRES, t, 2);
                int lv = BoostStat.WIRES.level(p);
                sb.append(" +").append(t.levels).append("->").append(lv);
                if (used && lv == 20 + t.levels && p.getMainHandItem().getCount() == 1) ok++;
            }
            set(p, BoostStat.WIRES, BoostStat.WIRES.max() - 1);
            use(p, BoostStat.WIRES, BoostTier.TEN, 1);
            boolean capped = BoostStat.WIRES.level(p) == BoostStat.WIRES.max();
            boolean heku = use(p, BoostStat.HEKUBLADE, BoostTier.ONE, 1);
            log("Rhalix wires from 20:" + sb + " cap " + capped + " | Hekublade boost used=" + heku + " (expect 5/5, true, false) -> " + pass(ok == 5 && capped && !heku));
            set(p, BoostStat.WIRES, 1);
            p.getInventory().clearContent();
        }), 10));
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));

        // ---- 2. the creative tab
        l.add(new Step(null, () -> {
            var pl = mc.player;
            CreativeModeTabs.tryRebuildTabContents(pl.connection.enabledFeatures(), true, pl.level().registryAccess());
            CreativeModeTab tab = StatBoosts.TAB.get();
            Set<Item> in = new HashSet<>();
            for (ItemStack s : tab.getDisplayItems()) in.add(s.getItem());
            int missing = 0;
            for (BoostStat s : BoostStat.values()) for (BoostTier t : BoostTier.values()) if (!in.contains(StatBoosts.item(s, t))) missing++;
            boolean listed = CreativeModeTabs.allTabs().contains(tab);
            log("creative tab '" + tab.getDisplayName().getString() + "': " + tab.getDisplayItems().size() + " items, missing " + missing + " of 55, listed=" + listed
                    + " -> " + pass(missing == 0 && tab.getDisplayItems().size() == 55 && listed));
            StringBuilder names = new StringBuilder();
            for (BoostTier t : BoostTier.values()) names.append(" | ").append(new ItemStack(StatBoosts.item(BoostStat.ATTACK, t)).getHoverName().getString())
                    .append(" ").append(new ItemStack(StatBoosts.item(BoostStat.ATTACK, t)).getRarity());
            log("names:" + names);
        }, 5));
        l.add(new Step(null, () -> UiTest.server(p -> p.setGameMode(GameType.CREATIVE)), 20));
        l.add(new Step(null, () -> mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true)), 20));
        l.add(new Step(null, () -> selectTab(mc, StatBoosts.TAB.get()), 20));
        l.add(new Step("statboost_creative_tab", () -> {}, 5));
        l.add(new Step(null, () -> { if (mc.screen != null) mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, -10); }, 10));
        l.add(new Step("statboost_creative_tab_scrolled", () -> {}, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.server(p -> p.setGameMode(GameType.SURVIVAL)); }, 20));

        // ---- 3. enemies and bosses (forced rolls)
        l.add(new Step(null, () -> UiTest.server(p -> {
            for (BoostStat s : BoostStat.values()) set(p, s, 0);
            com.xenowarriors.stats.Stats.reapply(p);
            clearGround(p);
            StatBoosts.testStatRoll = 0F;
            float pStr = com.xenowarriors.karma.KarmaSensing.strength(p);
            StatBoosts.testRoll = 0F;                                    // the rarest slice of each table
            List<StatBoostItem> zombie = kill(p, EntityType.ZOMBIE, p, 0);
            List<StatBoostItem> elder = kill(p, EntityType.ELDER_GUARDIAN, p, 0);
            List<StatBoostItem> ravager = kill(p, EntityType.RAVAGER, p, 2000);
            List<StatBoostItem> cow = kill(p, EntityType.COW, p, 0);
            log("roll 0 (player strength " + Math.round(pStr) + "): zombie " + describe(zombie) + " elder guardian (boss) " + describe(elder)
                    + " ravager 2000hp (strength " + "very strong) " + describe(ravager) + " cow " + describe(cow) + " (expect +10, +100, +100, none) -> "
                    + pass(zombie.size() == 1 && zombie.get(0).tier == BoostTier.TEN && elder.size() == 1 && elder.get(0).tier == BoostTier.HUNDRED
                    && ravager.size() == 1 && ravager.get(0).tier == BoostTier.HUNDRED && cow.isEmpty()));
            StatBoosts.testRoll = 0.005F;
            zombie = kill(p, EntityType.ZOMBIE, p, 0);
            ravager = kill(p, EntityType.RAVAGER, p, 2000);
            List<StatBoostItem> weakRavager = kill(p, EntityType.RAVAGER, p, 0);
            log("roll 0.005: zombie " + describe(zombie) + " strong ravager " + describe(ravager) + " normal ravager " + describe(weakRavager)
                    + " (expect +2, +100, +2) -> " + pass(zombie.size() == 1 && zombie.get(0).tier == BoostTier.TWO && ravager.size() == 1
                    && ravager.get(0).tier == BoostTier.HUNDRED && weakRavager.size() == 1 && weakRavager.get(0).tier == BoostTier.TWO));
            // a strong player: the same 2000-hp ravager isn't "very strong" any more
            set(p, BoostStat.ATTACK, 30000); set(p, BoostStat.DEFENSE, 30000); set(p, BoostStat.SPEED, 30000);
            com.xenowarriors.stats.Stats.reapply(p);
            StatBoosts.testRoll = 0F;
            ravager = kill(p, EntityType.RAVAGER, p, 2000);
            log("maxed player (strength " + Math.round(com.xenowarriors.karma.KarmaSensing.strength(p)) + ") vs 2000hp ravager: " + describe(ravager)
                    + " (expect +10, no +100) -> " + pass(ravager.size() == 1 && ravager.get(0).tier == BoostTier.TEN));
            for (BoostStat s : BoostStat.values()) set(p, s, 0);
            com.xenowarriors.stats.Stats.reapply(p);
            StatBoosts.testRoll = 0.999F;
            elder = kill(p, EntityType.ELDER_GUARDIAN, p, 0);
            zombie = kill(p, EntityType.ZOMBIE, p, 0);
            log("roll 0.999: boss " + describe(elder) + " zombie " + describe(zombie) + " (expect none) -> " + pass(elder.isEmpty() && zombie.isEmpty()));
            // stat choice: Mikiko's pool has Hekublade, never Wires
            StatBoosts.testRoll = 0F;
            Set<BoostStat> seen = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                StatBoosts.testStatRoll = i / 20F;
                for (StatBoostItem b : kill(p, EntityType.ZOMBIE, p, 0)) seen.add(b.stat);
            }
            log("stats seen over 20 rolls: " + seen + " (expect 10, Hekublade yes, Wires no) -> " + pass(seen.size() == 10 && seen.contains(BoostStat.HEKUBLADE) && !seen.contains(BoostStat.WIRES)));
            StatBoosts.testStatRoll = 0F;
            // bots' kills: dropped for the owner
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot spawn failed -> FAIL"); return; }
            b.setNoAi(true);
            List<StatBoostItem> botZ = new ArrayList<>();
            ServerLevel lv = p.serverLevel();
            BlockPos at = ORIGIN[0].offset(6, 0, 0);
            Mob z = EntityType.ZOMBIE.create(lv);
            z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0); z.setNoAi(true); lv.addFreshEntity(z);
            z.hurt(lv.damageSources().mobAttack(b), 1.0E7F);
            List<ItemEntity> ents = lv.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(5), e -> e.getItem().getItem() instanceof StatBoostItem);
            boolean owned = !ents.isEmpty() && ents.stream().allMatch(e -> e.getOwner() == p);
            for (ItemEntity e : ents) botZ.add((StatBoostItem) e.getItem().getItem());
            ents.forEach(ItemEntity::discard);
            z.discard();
            Mob boss = EntityType.ELDER_GUARDIAN.create(lv);
            boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0); boss.setNoAi(true); lv.addFreshEntity(boss);
            boss.hurt(lv.damageSources().mobAttack(b), 1.0E7F);
            List<StatBoostItem> botBoss = groundBoosts(lv, new AABB(at).inflate(5), true);
            boss.discard();
            log("bot (Rhalix) kills: zombie " + describe(botZ) + " owner's=" + owned + ", boss " + describe(botBoss) + " (expect +10 owned, +100) -> "
                    + pass(botZ.size() == 1 && botZ.get(0).tier == BoostTier.TEN && owned && botBoss.size() == 1 && botBoss.get(0).tier == BoostTier.HUNDRED));
            // a boost given to the bot: it uses it on itself (Attack: its character's); a Hekublade one it keeps for you
            int atk0 = PartyBots.statLevel(b, com.xenowarriors.stats.Stat.ATTACK);
            b.inventory.clearContent();
            b.inventory.setItem(0, new net.minecraft.world.item.ItemStack(StatBoosts.item(BoostStat.ATTACK, BoostTier.TEN)));
            b.inventory.setItem(1, new net.minecraft.world.item.ItemStack(StatBoosts.item(BoostStat.HEKUBLADE, BoostTier.TEN)));
            com.xenowarriors.bot.BotBoosts.tick(b);
            com.xenowarriors.bot.BotBoosts.tick(b);
            int atk1 = PartyBots.statLevel(b, com.xenowarriors.stats.Stat.ATTACK);
            boolean kept = b.inventory.getItem(1).getItem() instanceof StatBoostItem;
            log("bot used a given Attack +10: attack " + atk0 + " -> " + atk1 + ", Hekublade boost kept=" + kept + ", attack boost gone=" + b.inventory.getItem(0).isEmpty()
                    + " -> " + pass(atk1 == atk0 + 10 && kept && b.inventory.getItem(0).isEmpty()));
            b.inventory.clearContent();
            b.discard();
            // the tables themselves: +100 rarest, never from normal enemies
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (Object[] tb : new Object[][]{{"normal", StatBoosts.NORMAL}, {"strong", StatBoosts.STRONG}, {"boss", StatBoosts.BOSS}}) {
                int[] c = new int[5];
                int n = 1_000_000;
                for (int i = 0; i < n; i++) { BoostTier t = StatBoosts.pick((float[]) tb[1], (i + 0.5F) / n); if (t != null) c[t.ordinal()]++; }
                sb.append(" ").append(tb[0]).append(":");
                for (BoostTier t : BoostTier.values()) sb.append(" +").append(t.levels).append("=").append(String.format(Locale.ROOT, "%.2f%%", c[t.ordinal()] * 100.0 / n));
                for (int i = 0; i < 4; i++) if (c[4] > 0 && c[4] >= c[i]) ok = false;
                for (int i = 0; i < 3; i++) if (c[i + 1] > c[i]) ok = false;
                if (tb[0].equals("normal") && c[4] != 0) ok = false;
            }
            log("tables (per kill):" + sb + " -> " + pass(ok));
            StatBoosts.testRoll = null; StatBoosts.testStatRoll = null;
        }), 20));

        // ---- 4. shovel on dirt
        l.add(new Step(null, () -> UiTest.server(p -> {
            ServerLevel lv = p.serverLevel();
            BlockPos at = ORIGIN[0].offset(2, -1, 2);
            int[] n = new int[1];
            java.util.function.BiFunction<net.minecraft.world.level.block.state.BlockState, Item, List<StatBoostItem>> dig = (state, tool) -> {
                BlockPos pos = at.offset(n[0]++ % 5, 0, 0);
                lv.setBlock(pos, state, 3);
                p.setItemInHand(InteractionHand.MAIN_HAND, tool == null ? ItemStack.EMPTY : new ItemStack(tool));
                groundBoosts(lv, new AABB(pos).inflate(2), true);
                p.gameMode.destroyBlock(pos);
                List<StatBoostItem> got = groundBoosts(lv, new AABB(pos).inflate(2), true);
                lv.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);   // (the dirt itself)
                return got;
            };
            StatBoosts.testRoll = 0F; StatBoosts.testStatRoll = 0F;
            List<StatBoostItem> dirt = dig.apply(Blocks.DIRT.defaultBlockState(), Items.IRON_SHOVEL);
            List<StatBoostItem> grass = dig.apply(Blocks.GRASS_BLOCK.defaultBlockState(), Items.WOODEN_SHOVEL);
            List<StatBoostItem> mud = dig.apply(Blocks.MUD.defaultBlockState(), Items.DIAMOND_SHOVEL);
            List<StatBoostItem> podzol = dig.apply(Blocks.PODZOL.defaultBlockState(), Items.NETHERITE_SHOVEL);
            List<StatBoostItem> hand = dig.apply(Blocks.DIRT.defaultBlockState(), null);
            List<StatBoostItem> pick = dig.apply(Blocks.DIRT.defaultBlockState(), Items.IRON_PICKAXE);
            List<StatBoostItem> stone = dig.apply(Blocks.STONE.defaultBlockState(), Items.IRON_SHOVEL);
            List<StatBoostItem> sand = dig.apply(Blocks.SAND.defaultBlockState(), Items.IRON_SHOVEL);
            log("roll 0: shovel on dirt " + describe(dirt) + " grass " + describe(grass) + " mud " + describe(mud) + " podzol " + describe(podzol)
                    + " | bare hand " + describe(hand) + " pickaxe " + describe(pick) + " shovel on stone " + describe(stone) + " sand " + describe(sand)
                    + " (expect mining+10 x4, none x4) -> " + pass(dirt.size() == 1 && dirt.get(0).stat == BoostStat.MINING && dirt.get(0).tier == BoostTier.TEN
                    && grass.size() == 1 && mud.size() == 1 && podzol.size() == 1 && hand.isEmpty() && pick.isEmpty() && stone.isEmpty() && sand.isEmpty()));
            StatBoosts.testRoll = StatBoosts.DIRT_CHANCE * 0.5F; StatBoosts.testStatRoll = 0.99F;
            List<StatBoostItem> mid = dig.apply(Blocks.DIRT.defaultBlockState(), Items.IRON_SHOVEL);
            StatBoosts.testRoll = StatBoosts.DIRT_CHANCE * 1.01F;
            List<StatBoostItem> miss = dig.apply(Blocks.DIRT.defaultBlockState(), Items.IRON_SHOVEL);
            log("roll mid-chance + stat roll 0.99: " + describe(mid) + " (expect +1, not Mining); just over the chance " + describe(miss) + " (none) -> "
                    + pass(mid.size() == 1 && mid.get(0).tier == BoostTier.ONE && mid.get(0).stat != BoostStat.MINING && miss.isEmpty()));
            p.setGameMode(GameType.CREATIVE);
            StatBoosts.testRoll = 0F;
            List<StatBoostItem> creative = dig.apply(Blocks.DIRT.defaultBlockState(), Items.IRON_SHOVEL);
            p.setGameMode(GameType.SURVIVAL);
            log("creative shovel on dirt: " + describe(creative) + " (expect none) -> " + pass(creative.isEmpty()));
            // statistical: 20,000 real rolls
            StatBoosts.testRoll = null; StatBoosts.testStatRoll = null;
            int d0 = StatBoosts.testDirtDrops, mining = 0, total = 0;
            int[] tiers = new int[5];
            for (int i = 0; i < 20000; i++) {
                for (StatBoostItem b : dig.apply(Blocks.DIRT.defaultBlockState(), Items.IRON_SHOVEL)) { total++; tiers[b.tier.ordinal()]++; if (b.stat == BoostStat.MINING) mining++; }
            }
            log("20,000 dirt blocks with a shovel: " + total + " boosts (expect ~80 at " + StatBoosts.DIRT_CHANCE * 100 + "%, 45-125), Mining " + mining
                    + ", tiers +1/+2/+5/+10/+100 = " + java.util.Arrays.toString(tiers) + " -> " + pass(total >= 45 && total <= 125 && mining * 2 > total && tiers[4] == 0
                    && total == StatBoosts.testDirtDrops - d0));
        }), 20));

        // ---- 5. level-ups: a boost for that stat (players), no chains
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.getInventory().clearContent();
            for (BoostStat s : BoostStat.values()) set(p, s, 1);
            StatBoosts.testRoll = 0F;
            StringBuilder sb = new StringBuilder();
            int ok = 0;
            Object[][] ups = {
                    {BoostStat.SPEED, (Runnable) () -> com.xenowarriors.stats.Stats.addXp(p, Stat.SPEED, Stat.SPEED.needed(1) + 0.5F)},
                    {BoostStat.ATTACK, (Runnable) () -> com.xenowarriors.stats.Stats.addXp(p, Stat.ATTACK, Stat.ATTACK.needed(1) + 0.5F)},
                    {BoostStat.DEFENSE, (Runnable) () -> com.xenowarriors.stats.Stats.addXp(p, Stat.DEFENSE, Stat.DEFENSE.needed(1) + 0.5F)},
                    {BoostStat.MINING, (Runnable) () -> com.xenowarriors.stats.Mining.addXp(p, com.xenowarriors.stats.Mining.needed(1) + 0.5F)},
                    {BoostStat.CRAFTING, (Runnable) () -> { for (int i = 0; i < 40; i++) net.minecraftforge.event.ForgeEventFactory.firePlayerCraftingEvent(p, new ItemStack(Items.STICK, 4), new net.minecraft.world.SimpleContainer(1)); }},
                    {BoostStat.SMELTING, (Runnable) () -> { for (int i = 0; i < 2; i++) net.minecraftforge.event.ForgeEventFactory.firePlayerSmeltedEvent(p, new ItemStack(Items.IRON_INGOT, 64)); }},
                    {BoostStat.APPETITE, (Runnable) () -> com.xenowarriors.stats.Appetite.addXp(p, com.xenowarriors.stats.Appetite.needed(1) + 0.5F)},
                    {BoostStat.BUILDING, (Runnable) () -> com.xenowarriors.stats.Building.addXp(p, com.xenowarriors.stats.Building.needed(1) + 0.5F)},
                    {BoostStat.HEKUBLADE, (Runnable) () -> com.xenowarriors.mikiko.BladeLevel.addXp(p, com.xenowarriors.mikiko.BladeLevel.needed(1) + 0.5F)},
                    {BoostStat.GUNS, (Runnable) () -> com.xenowarriors.compat.GunLevel.addXp(p, com.xenowarriors.compat.GunLevel.needed(1) + 0.5F)},
            };
            for (Object[] u : ups) {
                BoostStat s = (BoostStat) u[0];
                int lv0 = s.level(p), n0 = boostsIn(p, s);
                ((Runnable) u[1]).run();
                int lv1 = s.level(p), n1 = boostsIn(p, s);
                StatBoostItem got = null;
                for (ItemStack it : p.getInventory().items) if (it.getItem() instanceof StatBoostItem b && b.stat == s) got = b;
                boolean good = lv1 > lv0 && n1 == n0 + 1 && got != null && got.tier == BoostTier.FIVE;
                if (good) ok++;
                sb.append(" ").append(s.id).append(" Lv").append(lv0).append("->").append(lv1).append(" boosts ").append(n0).append("->").append(n1)
                        .append(got == null ? "" : " +" + got.tier.levels);
            }
            log("level-up with roll 0 (expect a +5 of that same stat each):" + sb + " -> " + ok + "/" + ups.length + " " + pass(ok == ups.length));
            // the split: roll just under the chance -> +1
            StatBoosts.testRoll = StatBoosts.LEVEL_UP_CHANCE * 0.9F;
            int m0 = boostsIn(p, BoostStat.MINING);
            com.xenowarriors.stats.Mining.addXp(p, com.xenowarriors.stats.Mining.needed(BoostStat.MINING.level(p)) + 0.1F);
            ItemStack last = ItemStack.EMPTY;
            for (ItemStack it : p.getInventory().items) if (it.getItem() == StatBoosts.item(BoostStat.MINING, BoostTier.ONE)) last = it;
            StatBoosts.testRoll = StatBoosts.LEVEL_UP_CHANCE * 1.01F;
            int m1 = boostsIn(p, BoostStat.MINING);
            com.xenowarriors.stats.Mining.addXp(p, com.xenowarriors.stats.Mining.needed(BoostStat.MINING.level(p)) + 0.1F);
            log("roll 90% of the chance -> Mining +1 (" + !last.isEmpty() + ", boosts " + m0 + "->" + m1 + "); just over the chance -> " + (boostsIn(p, BoostStat.MINING) - m1)
                    + " new (expect true, +1, 0) -> " + pass(!last.isEmpty() && m1 == m0 + 1 && boostsIn(p, BoostStat.MINING) == m1));
            // no chains: using boosts with every roll forced never drops another
            StatBoosts.testRoll = 0F;
            int lu0 = StatBoosts.testLevelUpDrops;
            int before = BoostStat.MINING.level(p);
            for (BoostTier t : BoostTier.values()) use(p, BoostStat.MINING, t, 1);
            use(p, BoostStat.ATTACK, BoostTier.HUNDRED, 1);
            use(p, BoostStat.BUILDING, BoostTier.TEN, 1);
            log("boosts used with roll 0: Mining Lv" + before + " -> " + BoostStat.MINING.level(p) + ", level-up drops " + (StatBoosts.testLevelUpDrops - lu0)
                    + " (expect +118, 0) -> " + pass(BoostStat.MINING.level(p) == before + 118 && StatBoosts.testLevelUpDrops == lu0));
            // full inventory: at their feet
            p.getInventory().clearContent();
            for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
            clearGround(p);
            com.xenowarriors.stats.Mining.addXp(p, com.xenowarriors.stats.Mining.needed(BoostStat.MINING.level(p)) + 0.1F);
            List<StatBoostItem> feet = groundBoosts(p.serverLevel(), p.getBoundingBox().inflate(3), true);
            log("full inventory: level-up boost on the ground " + describe(feet) + " (expect mining+5) -> " + pass(feet.size() == 1 && feet.get(0).stat == BoostStat.MINING));
            p.getInventory().clearContent();
            // creative: no level-ups and no drops
            p.setGameMode(GameType.CREATIVE);
            int c0 = StatBoosts.testLevelUpDrops;
            com.xenowarriors.stats.Stats.addXp(p, Stat.SPEED, 100000);
            log("creative level-up roll: drops " + (StatBoosts.testLevelUpDrops - c0) + " (expect 0) -> " + pass(StatBoosts.testLevelUpDrops == c0));
            p.setGameMode(GameType.SURVIVAL);
        }), 20));
        // Rhalix's wires level-up (Season 2)
        l.add(new Step(null, () -> UiTest.character(CharacterType.RHALIX), 30));
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.getInventory().clearContent();
            StatBoosts.testRoll = 0F;
            set(p, BoostStat.WIRES, 1);
            com.xenowarriors.rhalix.WireLevel.addXp(p, com.xenowarriors.rhalix.WireLevel.needed(1) + 0.5F);
            log("Rhalix wires level-up: Lv" + BoostStat.WIRES.level(p) + " boosts " + boostsIn(p, BoostStat.WIRES) + " (expect 2, 1) -> "
                    + pass(BoostStat.WIRES.level(p) == 2 && boostsIn(p, BoostStat.WIRES) == 1));
            set(p, BoostStat.WIRES, 1);
            p.getInventory().clearContent();
        }), 10));
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 30));

        // ---- 6. bots' level-ups give their owner a boost of that stat
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.getInventory().clearContent();
            StatBoosts.testRoll = 0F;
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot spawn failed -> FAIL"); return; }
            b.setNoAi(true);
            int a0 = boostsIn(p, BoostStat.ATTACK);
            PartyBots.addXp(b, Stat.ATTACK, 100000);
            int a1 = boostsIn(p, BoostStat.ATTACK);
            int b0 = boostsIn(p, BoostStat.BUILDING);
            com.xenowarriors.stats.Building.addXp(b, p, 50);
            int b1 = boostsIn(p, BoostStat.BUILDING);
            int f0 = boostsIn(p, BoostStat.APPETITE);
            com.xenowarriors.stats.Appetite.addXp(b, 30);
            int f1 = boostsIn(p, BoostStat.APPETITE);
            log("bot (Rhalix) level-ups -> owner's boosts: Attack " + a0 + "->" + a1 + " Building " + b0 + "->" + b1 + " Appetite " + f0 + "->" + f1
                    + " (expect +1 each) -> " + pass(a1 == a0 + 1 && b1 == b0 + 1 && f1 == f0 + 1));
            StatBoosts.testRoll = 0.9F;
            int a2 = boostsIn(p, BoostStat.ATTACK);
            PartyBots.addXp(b, Stat.ATTACK, 100000);
            log("bot level-up, roll 0.9: Attack boosts " + a2 + "->" + boostsIn(p, BoostStat.ATTACK) + " (expect none) -> " + pass(boostsIn(p, BoostStat.ATTACK) == a2));
            b.discard();
            StatBoosts.testRoll = null; StatBoosts.testStatRoll = null;
        }), 20));

        // ---- 7. a picture: using a +100 (chat line + particles), boosts in the hotbar
        l.add(new Step(null, () -> UiTest.server(p -> {
            p.getInventory().clearContent();
            for (BoostStat s : BoostStat.values()) set(p, s, 0);
            com.xenowarriors.stats.Stats.reapply(p);
            int i = 0;
            for (BoostTier t : BoostTier.values()) p.getInventory().items.set(i++, new ItemStack(StatBoosts.item(BoostStat.ATTACK, t), 3));
            p.getInventory().items.set(5, new ItemStack(StatBoosts.item(BoostStat.MINING, BoostTier.ONE), 12));
            p.getInventory().items.set(6, new ItemStack(StatBoosts.item(BoostStat.HEKUBLADE, BoostTier.TEN), 2));
            p.getInventory().selected = 4;
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND)), 8));
        l.add(new Step("statboost_use", () -> {}, 5));
        l.add(new Step(null, () -> log("done"), 5));
    }

    /** Opens the given tab in the creative screen (and the page holding it): the select method is private, found by its shape. */
    private static void selectTab(Minecraft mc, CreativeModeTab tab) {
        if (!(mc.screen instanceof CreativeModeInventoryScreen s)) { log("creative screen not open -> FAIL"); return; }
        try {
            for (var f : CreativeModeInventoryScreen.class.getDeclaredFields()) {        // Forge's tab pages
                if (!java.util.List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v = f.get(s);
                if (!(v instanceof java.util.List<?> pages)) continue;
                for (Object pg : pages) {
                    if (pg == null || !pg.getClass().getSimpleName().contains("Page")) continue;
                    var vis = pg.getClass().getMethod("getVisibleTabs");
                    if (((java.util.List<?>) vis.invoke(pg)).contains(tab)) {
                        for (var m : CreativeModeInventoryScreen.class.getMethods())
                            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == pg.getClass() && m.getName().toLowerCase(Locale.ROOT).contains("page")) m.invoke(s, pg);
                    }
                }
            }
            for (var m : CreativeModeInventoryScreen.class.getDeclaredMethods()) {
                if (m.getReturnType() == void.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == CreativeModeTab.class) {
                    m.setAccessible(true);
                    m.invoke(s, tab);
                    log("creative tab selected via " + m.getName());
                    return;
                }
            }
            log("creative tab: no select method -> FAIL");
        } catch (Throwable t) {
            log("creative tab select failed: " + t + " -> FAIL");
        }
    }
}

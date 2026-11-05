package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Seasons;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.*;

/**
 * Bug hunt: the playable characters and the Season 2/3 lock (focus "huntseason", "dmzhuntseason" with DragonMineZ).
 * Lines "XW-TEST huntseason ... -> PASS/FAIL".
 * <ol>
 *   <li>Season 1: Season 2/3 characters, skills, bots, outfits, Rhalix's wires, Lunar Form and the creative tab stay locked;</li>
 *   <li>after /xeno season unlock 3 they work;</li>
 *   <li>locked again while playing a Season 2 character: its ultimate / KA can't be used; a Season 3 outfit comes off;</li>
 *   <li>a bot never uses a skill from a locked season or above its level (from its owner's loadout);</li>
 *   <li>switching characters ends the old character's stances / takeovers / waiting blades;</li>
 *   <li>bots' self-buff skills work for the bot (Persephone), Yuri's Tenjo Medical heals a bot's owner;</li>
 *   <li>every skill and ultimate of every character, used by the player and by a bot of it, never hurts or debuffs the
 *   party bot / owner standing close by (an enemy dummy in front), and never throws.</li>
 * </ol>
 */
public final class SeasonHuntTests {
    private SeasonHuntTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("huntseason"); }

    private static void log(String s) { UiTest.log("huntseason " + s); }

    private static String pass(boolean ok) { return ok ? "PASS" : "FAIL"; }

    private static final int[] FAILS = {0};

    private static void check(String what, boolean ok) {
        if (!ok) FAILS[0]++;
        log(what + " -> " + pass(ok));
    }

    private static void clearBots(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (CharacterType t : CharacterType.PLAYABLE) p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
    }

    private static void cmd(ServerPlayer p, String c) {
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
    }

    private static int[] loadout(ServerPlayer p, CharacterType t) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadout(t).clone()).orElse(new int[0]);
    }

    private static long cd(ServerPlayer p, int slot) { return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.cooldownEnd[slot]).orElse(-1L); }

    private static int outfit(ServerPlayer p) { return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.outfit).orElse(-1); }

    private static Husk dummy(ServerPlayer p, Vec3 at) {
        Husk h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(at.x, at.y, at.z, 180F, 0F);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.addTag("xw_hs");
        h.getPersistentData().putBoolean("xw_mb_rolled", true);
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        p.serverLevel().addFreshEntity(h);
        for (var m : new ArrayList<>(h.getAttribute(Attributes.MAX_HEALTH).getModifiers())) h.getAttribute(Attributes.MAX_HEALTH).removeModifier(m);
        h.setHealth(h.getMaxHealth());
        return h;
    }

    private static void clearDummies(ServerPlayer p) {
        for (var e : p.serverLevel().getEntitiesOfClass(Husk.class, p.getBoundingBox().inflate(64), h -> h.getTags().contains("xw_hs"))) e.discard();
    }

    // ------------------------------------------------------------------ what happens to the allies during the sweep
    private static final Map<UUID, Float> DMG = new HashMap<>();
    private static final Map<UUID, List<String>> BAD_FX = new HashMap<>();
    private static boolean listening;

    private static void listen() {
        if (listening) return;
        listening = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, e -> {
            if (DMG.containsKey(e.getEntity().getUUID()) && !e.isCanceled()) {
                DMG.merge(e.getEntity().getUUID(), e.getAmount(), Float::sum);
                var src = e.getSource();
                SOURCES.add(src.getMsgId() + "/" + describe(src.getEntity(), e.getEntity()) + (src.getDirectEntity() != null && src.getDirectEntity() != src.getEntity() ? " via " + describe(src.getDirectEntity(), e.getEntity()) : "")
                        + String.format(Locale.ROOT, " %.1f", e.getAmount()));
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, MobEffectEvent.Added.class, e -> {
            List<String> l = BAD_FX.get(e.getEntity().getUUID());
            if (l != null && e.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL) l.add(e.getEffectInstance().getEffect().getDescriptionId().replace("effect.minecraft.", "")
                    + "@" + describe(e.getEffectSource(), e.getEntity()));
        });
    }

    private static final Set<String> SOURCES = new LinkedHashSet<>();

    /**
     * Who did it: type, short UUID, namespace (xenowarriors / dragonminez / minecraft), a bot (or a bot's stand-in) with its
     * character, an ally of the victim or not, and how far away it is.
     */
    private static String describe(net.minecraft.world.entity.Entity a, LivingEntity victim) {
        if (a == null) return "-";
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(a.getType());
        String s = (id == null ? a.getType().toShortString() : id.toString()) + "#" + a.getUUID().toString().substring(0, 8);
        if (a instanceof PartyBotEntity b) s += "[bot " + b.character().getId() + "]";
        PartyBotEntity standIn = a instanceof net.minecraft.world.entity.player.Player ? PartyBots.botOf(a) : null;
        if (standIn != null) s += "[stand-in of bot " + standIn.character().getId() + "]";
        if (a instanceof net.minecraftforge.common.util.FakePlayer && standIn == null) s += "[fake " + ((net.minecraft.world.entity.player.Player) a).getGameProfile().getName() + "]";
        if (a instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null) s += "[targets " + m.getTarget().getType().toShortString() + "]";
        s += com.xenowarriors.combat.Allies.isAlly(a, victim) ? "[ally]" : "[foe]";
        return s + String.format(Locale.ROOT, "@%.0fm", a.distanceTo(victim));
    }

    /** (DMZ world mobs that wander into the arena are logged once per step) */
    private static String strays(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (var m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(32),
                m -> !(m instanceof PartyBotEntity) && !m.getTags().contains("xw_hs") && m.isAlive()))
            out.add(describe(m, p));
        return out.toString();
    }

    private static void watch(LivingEntity e) { DMG.put(e.getUUID(), 0F); BAD_FX.put(e.getUUID(), new ArrayList<>()); SOURCES.clear(); }

    // ------------------------------------------------------------------ the run

    private static final Object[] KEEP = new Object[4];

    public static void build(Minecraft mc, List<Step> l, String focus) {
        listen();
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null);
            UiTest.server(p -> { clearBots(p); p.setGameMode(GameType.CREATIVE); p.serverLevel().setDayTime(6000); com.xenowarriors.modes.SwitchRules.clearCombat(p);
                Seasons.set(p.server, 1); CharacterManager.setCharacter(p, CharacterType.MIKIKO); }); }, 40));

        if (focus.contains("sweep")) l.add(new Step(null, () -> UiTest.server(p -> Seasons.set(p.server, 3)), 10));   // ("...sweep": just the skill sweep)
        else {
            locks(mc, l);
            relock(mc, l);
            botSkills(l);
            switching(l);
            botBuffs(l);
        }
        sweep(l, focus);
        l.add(new Step(null, () -> UiTest.server(p -> { clearBots(p); clearDummies(p); Seasons.set(p.server, 1);
            log("done: " + FAILS[0] + " failed checks"); }), 20));
    }

    // ---- 1 + 2: Season 1 locks, then the same after unlocking
    private static void locks(Minecraft mc, List<Step> l) {
        // a Season 2 / 3 character picked from the select screen
        l.add(new Step(null, () -> { ModNetwork.sendToServer(new com.xenowarriors.network.SelectCharacterPacket(CharacterType.KAERU));
            ModNetwork.sendToServer(new com.xenowarriors.network.SelectCharacterPacket(CharacterType.ZANE)); }, 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            check("S1 select Kaeru/Zane refused: now " + ModCapabilities.getType(p).getId() + " (expect mikiko)", ModCapabilities.getType(p) == CharacterType.MIKIKO);
            // Season 2/3 skills into a slot (creative: the level doesn't matter, the season does)
            int[] before = loadout(p, CharacterType.MIKIKO);
            SkillManager.setSlot(p, 0, 8); SkillManager.setSlot(p, 1, 5); SkillManager.setSlot(p, 2, 4);
            int[] after = loadout(p, CharacterType.MIKIKO);
            check("S1 Mikiko Soul Splitter / Netsu Burst / Dance Kick can't be slotted: " + Arrays.toString(before) + " -> " + Arrays.toString(after), Arrays.equals(before, after));
            // bots of Season 2 / 3 characters
            PartyBots.add(p, new String[]{"kaeru", "ultima", "yuri", "zane", "aioni"});
            check("S1 Season 2/3 bots refused: " + PartyBots.bots(p).size() + " bots (expect 0)", PartyBots.bots(p).isEmpty());
            clearBots(p);
        }), 10));
        // outfits: a Season 3 one is refused, a Season 1 one goes on
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(3)), 10));
        l.add(new Step(null, () -> UiTest.server(p -> check("S1 Mikiko Season 3 outfit refused: outfit " + outfit(p) + " (expect 0)", outfit(p) == 0)), 2));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(1)), 10));
        l.add(new Step(null, () -> UiTest.server(p -> { check("S1 Mikiko Origins 1 outfit allowed: outfit " + outfit(p) + " (expect 1)", outfit(p) == 1);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0); }), 2));
        // Rhalix's wires and Lunar Form
        l.add(new Step(null, () -> UiTest.server(p -> {
            CharacterManager.setCharacter(p, CharacterType.RHALIX);
            check("S1 Rhalix has no wires: style " + com.xenowarriors.forms.WeaponForms.style(p) + ", summon " + com.xenowarriors.forms.WeaponForms.summon(p, com.xenowarriors.forms.Form.WIRES, false),
                    com.xenowarriors.forms.WeaponForms.style(p) == null);
            com.xenowarriors.lunar.LunarForm.setUnlocked(p, true);
            p.serverLevel().setDayTime(18000);
        }), 50));
        l.add(new Step(null, () -> UiTest.server(p -> check("S1 Lunar Form stays off at night (trial passed): active " + com.xenowarriors.lunar.LunarForm.active(p), !com.xenowarriors.lunar.LunarForm.active(p))), 2));
        // the creative tab
        l.add(new Step(null, () -> {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            int n = (int) com.xenowarriors.item.ModItems.TAB.get().getDisplayItems().stream().filter(s -> s.is(com.xenowarriors.item.ModItems.AURORA_MEDICINE.get())).count();
            check("S1 creative tab: Aurora Medicine (Season 2) shown " + n + " (expect 0)", n == 0);
        }, 5));

        // ---- unlocked: everything works
        l.add(new Step(null, () -> UiTest.server(p -> Seasons.set(p.server, 3)), 20));
        l.add(new Step(null, () -> UiTest.server(p -> check("S3 Lunar Form turns on at night: active " + com.xenowarriors.lunar.LunarForm.active(p) + ", wires style " + com.xenowarriors.forms.WeaponForms.style(p),
                com.xenowarriors.lunar.LunarForm.active(p) && com.xenowarriors.forms.WeaponForms.style(p) == com.xenowarriors.forms.Form.Style.WIRE)), 40));
        l.add(new Step(null, () -> {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), false, mc.level.registryAccess());
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            int n = (int) com.xenowarriors.item.ModItems.TAB.get().getDisplayItems().stream().filter(s -> s.is(com.xenowarriors.item.ModItems.AURORA_MEDICINE.get())).count();
            check("S3 creative tab: Aurora Medicine shown " + n + " (expect 1)", n == 1);
            UiTest.server(p -> { com.xenowarriors.lunar.LunarForm.setUnlocked(p, false); p.serverLevel().setDayTime(6000); CharacterManager.setCharacter(p, CharacterType.MIKIKO); });
        }, 10));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.SelectCharacterPacket(CharacterType.KAERU)), 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            check("S3 select Kaeru works: now " + ModCapabilities.getType(p).getId(), ModCapabilities.getType(p) == CharacterType.KAERU);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            int[] before = loadout(p, CharacterType.MIKIKO);
            SkillManager.setSlot(p, 0, 8);
            int[] after = loadout(p, CharacterType.MIKIKO);
            check("S3 Mikiko Soul Splitter slots: " + Arrays.toString(before) + " -> " + Arrays.toString(after), after[0] == 8);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setLoadout(CharacterType.MIKIKO, SkillPool.defaultLoadout()));
            PartyBots.add(p, new String[]{"kaeru", "zane"});
            check("S3 Kaeru + Zane bots join: " + PartyBots.bots(p).size(), PartyBots.bots(p).size() == 2);
            clearBots(p);
            PartyBots.spawn(p, CharacterType.RHALIX, null);
        }), 10));
        // Ai-oni is never in a party with Rhalix: not even with a Rhalix bot
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.SelectCharacterPacket(CharacterType.AIONI)), 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            check("S3 with a Rhalix bot, picking Ai-oni is refused: now " + ModCapabilities.getType(p).getId() + " (expect mikiko)", ModCapabilities.getType(p) == CharacterType.MIKIKO);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            clearBots(p);
        }), 10));
    }

    // ---- 3: locked again while playing a Season 2 character / wearing a Season 3 outfit
    private static void relock(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.SetOutfitPacket(3)), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            int was = outfit(p);
            Seasons.set(p.server, 1);
            check("relock: Mikiko's Season 3 outfit comes off now: " + was + " -> " + outfit(p) + " (expect 3 -> 0)", was == 3 && outfit(p) == 0);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0);
            Seasons.set(p.server, 3);
            CharacterManager.setCharacter(p, CharacterType.KAERU);
            Seasons.set(p.server, 1);
            SkillManager.resetCooldowns(p);
            long u0 = cd(p, SkillManager.ULTIMATE_SLOT), s0 = cd(p, SkillManager.SUPER_SLOT);
            SkillManager.useSkill(p, SkillManager.ULTIMATE_SLOT);
            SkillManager.useSkill(p, SkillManager.SUPER_SLOT);
            boolean ult = cd(p, SkillManager.ULTIMATE_SLOT) != u0, ka = cd(p, SkillManager.SUPER_SLOT) != s0 || com.xenowarriors.kaeru.KaeruChange.mode(p) != com.xenowarriors.kaeru.KaeruChange.NONE;
            check("relock: Kaeru (Season 2) can't fire her ultimate (" + ult + ") or KA (" + ka + ")", !ult && !ka);
            if (com.xenowarriors.kaeru.KaeruChange.mode(p) != com.xenowarriors.kaeru.KaeruChange.NONE) com.xenowarriors.kaeru.KaeruChange.revert(p, false);
            Seasons.set(p.server, 3);
            SkillManager.resetCooldowns(p);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
        }), 20));
    }

    // ---- 4: a bot and its owner's loadout
    private static void botSkills(List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            clearBots(p);
            CharacterManager.setCharacter(p, CharacterType.RHALIX);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setLoadout(CharacterType.MIKIKO, new int[]{8, 4, 2, 3}));   // Soul Splitter (S2, lv 10), Dance Kick (S3, lv 15)
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            b.applyLevel(12);
            int[] lo = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadoutWithUlt(CharacterType.MIKIKO)).orElse(null);
            log("bot old pick (no checks): slot0 " + SkillPool.inSlot(CharacterType.MIKIKO, lo, 0).getId() + ", slot1 " + SkillPool.inSlot(CharacterType.MIKIKO, lo, 1).getId());
            String s0 = botPick(b, lo, 0), s1 = botPick(b, lo, 1);
            check("S3 bot lv12: slot0 " + s0 + " (Soul Splitter ok at lv12), slot1 " + s1 + " (Dance Kick needs lv15 -> default kitsune)", "soul_splitter".equals(s0) && !"dance_kick".equals(s1));
            Seasons.set(p.server, 1);
            s0 = botPick(b, lo, 0);
            check("S1 (locked again) bot: slot0 " + s0 + " (expect not soul_splitter)", !"soul_splitter".equals(s0));
            Seasons.set(p.server, 3);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setLoadout(CharacterType.MIKIKO, SkillPool.defaultLoadout()));
            clearBots(p);
        }), 20));
    }

    /** The skill a bot would use from this slot (PartyBots.botSkill when it's there; else what the bot used to do: no checks). */
    private static String botPick(PartyBotEntity b, int[] lo, int slot) {
        try {
            var m = PartyBots.class.getMethod("botSkill", PartyBotEntity.class, int[].class, int.class);
            Skill s = (Skill) m.invoke(null, b, lo, slot);
            return s == null ? "none" : s.getId();
        } catch (NoSuchMethodException e) {
            Skill s = SkillPool.inSlot(b.character(), lo, slot);
            return s == null ? "none" : s.getId() + "(no-checks)";
        } catch (Throwable t) { return "ERR " + t; }
    }

    // ---- 5: switching characters mid-skill
    @SuppressWarnings("unchecked")
    private static boolean feign(ServerPlayer p) {
        try { var f = com.xenowarriors.skill.ffr.SrSkills.class.getDeclaredField("FEIGN"); f.setAccessible(true);
            Long t = ((Map<UUID, Long>) f.get(null)).get(p.getUUID()); return t != null && t > p.level().getGameTime(); } catch (Throwable t) { return true; }
    }

    private static void switching(List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            CharacterManager.setCharacter(p, CharacterType.LEXY);
            new com.xenowarriors.skill.sp.LexySkills.Persephone().use(p);
            boolean on = com.xenowarriors.skill.sp.SpState.persephone(p);
            CharacterManager.setCharacter(p, CharacterType.RHALIX);
            check("switch: Lexy's Persephone (" + on + ") ends when she becomes Rhalix: " + com.xenowarriors.skill.sp.SpState.persephone(p), on && !com.xenowarriors.skill.sp.SpState.persephone(p));

            CharacterManager.setCharacter(p, CharacterType.KYRIE);
            new com.xenowarriors.skill.sp.KyrieSkills.BoxingStance().use(p);
            on = com.xenowarriors.skill.sp.SpState.boxing(p);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            check("switch: Kyrie's Boxing Stance (" + on + ") ends: " + com.xenowarriors.skill.sp.SpState.boxing(p), on && !com.xenowarriors.skill.sp.SpState.boxing(p));

            CharacterManager.setCharacter(p, CharacterType.ZANE);
            com.xenowarriors.skill.ffr.ZaneProjection.use(p);
            on = com.xenowarriors.skill.ffr.ZaneProjection.active(p);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            check("switch: Zane's Projection KA (" + on + ", attacks pass through) ends: " + com.xenowarriors.skill.ffr.ZaneProjection.active(p), on && !com.xenowarriors.skill.ffr.ZaneProjection.active(p));

            CharacterManager.setCharacter(p, CharacterType.KILL);
            new com.xenowarriors.skill.ffr.SrSkills.PlayWeak().use(p);
            on = feign(p);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
            check("switch: Kill's Play Weak (" + on + ", next hit x3) ends: " + feign(p), on && !feign(p));

            // Logan's Royal Guard: knockback immunity for 6 s must not be saved with the player (a logout then kept it for good)
            CharacterManager.setCharacter(p, CharacterType.LOGAN);
            var kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            new com.xenowarriors.skill.ffr.SrSkills.RoyalGuard().use(p);
            net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
            p.saveWithoutId(saved);
            double savedBase = -1;
            for (net.minecraft.nbt.Tag t : saved.getList("Attributes", 10))
                if (((net.minecraft.nbt.CompoundTag) t).getString("Name").equals("minecraft:generic.knockback_resistance")) savedBase = ((net.minecraft.nbt.CompoundTag) t).getDouble("Base");
            check("Royal Guard: knockback resistance now " + kb.getValue() + " (expect 1.0), saved base " + savedBase + " (expect 0.0)", kb.getValue() >= 1.0 && savedBase == 0.0);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);

            SkillManager.resetCooldowns(p);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setLoadout(CharacterType.MIKIKO, SkillPool.defaultLoadout()));
            SkillManager.useSkill(p, 0);                     // Hekublade Scatter: blades wait in the air
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            int n = shards(p);
            CharacterManager.setCharacter(p, CharacterType.RHALIX);
            KEEP[0] = n;
        }), 5));
        l.add(new Step(null, () -> UiTest.server(p -> {
            check("switch: Mikiko's waiting Scatter blades (" + KEEP[0] + ") go when he becomes Rhalix: " + shards(p) + " left", (Integer) KEEP[0] > 0 && shards(p) == 0);
            CharacterManager.setCharacter(p, CharacterType.MIKIKO);
        }), 5));
    }

    private static int shards(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(com.xenowarriors.entity.HekubladeShardEntity.class, p.getBoundingBox().inflate(16), s -> !s.isRemoved() && !s.fired()).size();
    }

    // ---- 6: bots' own buffs and support skills
    private static void botBuffs(List<Step> l) {
        l.add(new Step(null, () -> UiTest.server(p -> {
            clearBots(p); clearDummies(p);
            Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
            PartyBotEntity lexy = PartyBots.spawn(p, CharacterType.LEXY, null);
            lexy.teleportTo(at.x + 3, at.y, at.z + 3); lexy.setNoAi(true);
            Husk a = dummy(p, at.add(3, 0, 4.2)), b = dummy(p, at.add(3, 0, 4.2));
            watch(a); watch(b);
            KEEP[1] = lexy; KEEP[2] = a; KEEP[3] = b;
        }), 10));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity lexy = (PartyBotEntity) KEEP[1];
            Husk a = (Husk) KEEP[2], b = (Husk) KEEP[3];
            lexy.doHurtTarget(a);
            boolean used = PartyBots.useForTest(lexy, new com.xenowarriors.skill.sp.LexySkills.Persephone());
            b.invulnerableTime = 0;
            lexy.doHurtTarget(b);
            float da = DMG.getOrDefault(a.getUUID(), 0F), db = DMG.getOrDefault(b.getUUID(), 0F);
            check(String.format(Locale.ROOT, "bot Lexy's Persephone works for the bot: used %s, on %s, hit %.1f -> %.1f (x%.2f, expect ~1.8)", used,
                    com.xenowarriors.skill.sp.SpState.persephone(lexy), da, db, da > 0 ? db / da : 0), used && com.xenowarriors.skill.sp.SpState.persephone(lexy) && da > 0 && db / da > 1.5F);
            clearBots(p); clearDummies(p);
            // Yuri's Tenjo Medical from a bot heals its owner
            PartyBotEntity yuri = PartyBots.spawn(p, CharacterType.YURI, null);
            yuri.setNoAi(true);
            p.setGameMode(GameType.SURVIVAL);
            p.setHealth(8F);
            float h0 = p.getHealth();
            boolean u = PartyBots.useForTest(yuri, new com.xenowarriors.skill.ffr.YuriSkills.TenjoMedical());
            check(String.format(Locale.ROOT, "bot Yuri's Tenjo Medical heals her owner: used %s, owner %.1f -> %.1f", u, h0, p.getHealth()), u && p.getHealth() > h0 + 4);
            p.setGameMode(GameType.CREATIVE);
            clearBots(p);
        }), 20));
    }

    // ---- 7: every skill, player and bot, with an ally close by
    private static final CharacterType[] ALL = CharacterType.PLAYABLE;

    private static void sweep(List<Step> l, String focus) {
        int[] hurt = {0}, n = {0};
        for (CharacterType t : ALL) {
            int skills = SkillPool.of(t).size();
            l.add(new Step(null, () -> UiTest.server(p -> {
                clearBots(p); clearDummies(p);
                p.setGameMode(GameType.SURVIVAL);
                CharacterManager.setCharacter(p, t);
                cmd(p, "xeno level 100");
                CharacterType allyType = t == CharacterType.KEITA ? CharacterType.LOGAN : CharacterType.KEITA;
                if (t == CharacterType.AIONI) allyType = CharacterType.LOGAN;   // (never with Rhalix; Keita is fine too)
                PartyBotEntity ally = PartyBots.spawn(p, allyType, null);
                ally.setNoAi(true);
                KEEP[1] = ally;
            }), 20));
            for (int i = -1; i < skills; i++) {
                int idx = i;                                      // -1 = the ultimate
                l.add(new Step(null, () -> UiTest.server(p -> {
                    PartyBotEntity ally = (PartyBotEntity) KEEP[1];
                    Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
                    clearDummies(p);
                    p.setHealth(p.getMaxHealth()); p.removeAllEffects();
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4, false, false));
                    p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.setKarma(d.karmaMax); Arrays.fill(d.cooldownEnd, 0); });
                    p.getPersistentData().remove("xw_clash_ready");
                    ally.teleportTo(at.x + 2.2, at.y, at.z + 2.5); ally.setHealth(ally.getMaxHealth()); ally.removeAllEffects(); ally.setDeltaMovement(Vec3.ZERO);
                    dummy(p, at.add(0, 0, 5));
                    watch(ally); watch(p);
                    int slot = idx < 0 ? SkillManager.ULTIMATE_SLOT : 0;
                    if (idx >= 0) p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
                        int[] lo = d.loadout(t).clone();
                        for (int k = 1; k < lo.length; k++) if (lo[k] == idx) lo[k] = lo[0];
                        lo[0] = idx; d.setLoadout(t, lo);
                    });
                    try { SkillManager.useSkill(p, slot); for (int k = 0; k < 3; k++) { int kk = k; com.xenowarriors.skill.ServerScheduler.later(6 + kk * 5, () -> { try { SkillManager.useSkill(p, slot); } catch (Throwable ignored) {} }); } }
                    catch (Throwable e) { log("player " + t.getId() + " skill" + idx + " THREW " + e); FAILS[0]++; }
                }), 50));
                l.add(new Step(null, () -> UiTest.server(p -> {
                    PartyBotEntity ally = (PartyBotEntity) KEEP[1];
                    Skill s = idx < 0 ? SkillPool.ultimate(p) : SkillPool.inSlot(p, loadout(p, t), 0);
                    float d = DMG.getOrDefault(ally.getUUID(), 0F), own = DMG.getOrDefault(p.getUUID(), 0F);
                    List<String> fx = BAD_FX.getOrDefault(ally.getUUID(), List.of());
                    boolean ok = d <= 0 && fx.isEmpty();
                    n[0]++;
                    if (!ok) hurt[0]++;
                    log(String.format(Locale.ROOT, "player %s %s: ally bot dmg %.1f, harmful fx %s, own dmg %.1f%s -> %s", t.getId(), s == null ? "?" : s.getId(), d, fx, own, ok ? "" : " from " + SOURCES + " mobs near: " + strays(p), pass(ok)));
                    if (!ok) FAILS[0]++;
                }), 2));
            }
            // (Persephone drains Lexy herself 20 s later: let that land before the owner is watched again)
            if (t == CharacterType.LEXY) l.add(new Step(null, () -> {}, 400));
            // a bot of this character uses each of its skills, its owner beside it
            l.add(new Step(null, () -> UiTest.server(p -> {
                clearBots(p); clearDummies(p);
                CharacterManager.setCharacter(p, t == CharacterType.MIKIKO ? CharacterType.RHALIX : CharacterType.MIKIKO);
                if (t == CharacterType.RHALIX) CharacterManager.setCharacter(p, CharacterType.MIKIKO);
                if (t == CharacterType.AIONI) CharacterManager.setCharacter(p, CharacterType.MIKIKO);
                Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
                PartyBotEntity b = PartyBots.spawn(p, t, null);
                if (b == null) { log("bot " + t.getId() + " didn't spawn"); return; }
                b.applyLevel(100);
                b.setNoAi(true);
                b.teleportTo(at.x - 2.2, at.y, at.z); b.setYRot(0F); b.setYHeadRot(0F);
                KEEP[1] = b;
            }), 20));
            for (int i = -1; i < skills; i++) {
                int idx = i;
                l.add(new Step(null, () -> UiTest.server(p -> {
                    PartyBotEntity b = (PartyBotEntity) KEEP[1];
                    if (b == null || b.isRemoved()) return;
                    Vec3 at = com.xenowarriors.debug.SkillTestRunner.arena(p);
                    clearDummies(p);
                    p.teleportTo(at.x, at.y, at.z);
                    p.setHealth(p.getMaxHealth()); p.removeAllEffects();
                    b.setHealth(b.getMaxHealth()); b.removeAllEffects(); b.karma = 100F;
                    b.teleportTo(at.x - 2.2, at.y, at.z); b.setYRot(0F); b.setYHeadRot(0F);
                    dummy(p, at.add(-2.2, 0, 5));
                    watch(p);
                    Skill s = idx < 0 ? SkillPool.ultimate(t) : SkillPool.entry(t, idx).skill();
                    KEEP[2] = s.getId();
                    try { boolean u = PartyBots.useForTest(b, s); KEEP[3] = u; }
                    catch (Throwable e) { log("bot " + t.getId() + " " + s.getId() + " THREW " + e); FAILS[0]++; KEEP[3] = false; }
                }), 40));
                l.add(new Step(null, () -> UiTest.server(p -> {
                    if (KEEP[1] == null || ((PartyBotEntity) KEEP[1]).isRemoved()) return;
                    float d = DMG.getOrDefault(p.getUUID(), 0F);
                    List<String> fx = BAD_FX.getOrDefault(p.getUUID(), List.of());
                    boolean ok = d <= 0 && fx.isEmpty();
                    if (!ok) FAILS[0]++;
                    log(String.format(Locale.ROOT, "bot %s %s: used %s, owner dmg %.1f, harmful fx %s%s -> %s", t.getId(), KEEP[2], KEEP[3], d, fx, ok ? "" : " from " + SOURCES + " mobs near: " + strays(p), pass(ok)));
                }), 2));
            }
        }
        l.add(new Step(null, () -> UiTest.server(p -> { p.setGameMode(GameType.CREATIVE); log("sweep: " + hurt[0] + " of " + n[0] + " player skills touched the ally bot"); }), 5));
    }
}

package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.ClientCharacterData;
import com.xenowarriors.client.ClientSkillState;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.client.forms.GunClient;
import com.xenowarriors.forms.Form;
import com.xenowarriors.forms.WeaponForms;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pack smoke test (focus "pack..."; CI runs it in the user's real mod pack on a real Forge install, see
 * tools/pack/run_pack.sh): every character, every Xeno Warriors menu, Mikiko's and Rhalix's skills and ultimates
 * through the real key packets, bot teammates, Karma Sense, every summoned gun form aimed and fired, placing and
 * breaking blocks, a Journey started, the DragonMineZ menu and a team ultimate. Each check logs
 * "XW-TEST pack ... PASS/FAIL"; the last line is "XW-TEST pack summary pass=N fail=N".
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PackTests {
    private PackTests() {}

    private static int pass, fail;
    private static final List<String> FAILED = new ArrayList<>();
    static final double[] HOME = new double[3];

    public static boolean handles(String focus) { return focus != null && focus.startsWith("pack"); }

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST pack " + s); }

    static void check(String what, boolean ok, String detail) {
        if (ok) pass++; else { fail++; FAILED.add(what); }
        log(what + ": " + detail + " -> " + (ok ? "PASS" : "FAIL"));
    }

    static String f(double d) { return String.format(Locale.ROOT, "%.1f", d); }

    private static String screen(Minecraft mc) { return mc.screen == null ? "none" : mc.screen.getClass().getName(); }

    static void server(java.util.function.Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { check("server step", false, String.valueOf(t)); XenoWarriors.LOGGER.error("XW-TEST pack", t); } });
    }

    static void client(String what, Runnable r) {
        try { r.run(); } catch (Throwable t) { check(what, false, String.valueOf(t)); XenoWarriors.LOGGER.error("XW-TEST pack " + what, t); }
    }

    // ------------------------------------------------------------------ the real Forge client: load the test world from the title screen

    private static boolean loading, running, allowDmz;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // DragonMineZ asks each new character for a race (its picker keeps opening): out of the way, except for the DMZ menu checks
        if (running && !allowDmz && mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez")) mc.setScreen(null);
        if (loading) return;
        String world = System.getenv("XW_UITEST_WORLD");
        if (world == null || world.isEmpty()) return;
        if (mc.level == null && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            loading = true;
            XenoWarriors.LOGGER.info("XW-UITEST loading world '{}' from the title screen", world);
            mc.createWorldOpenFlows().loadLevel(mc.screen, world);
        }
    }

    // ------------------------------------------------------------------ the test

    public static void build(Minecraft mc, List<Step> l, String focus) {
        running = true;
        l.add(new Step(null, () -> client("mods", () -> {
            List<String> mods = new ArrayList<>();
            ModList.get().forEachModContainer((id, c) -> mods.add(id + " " + c.getModInfo().getVersion()));
            mods.sort(String::compareTo);
            log("mods loaded (" + mods.size() + "): " + String.join(", ", mods));
            log("dist=" + net.minecraftforge.fml.loading.FMLEnvironment.dist + " production=" + net.minecraftforge.fml.loading.FMLEnvironment.production
                    + " renderer=" + com.mojang.blaze3d.platform.GlUtil.getRenderer() + " gl=" + com.mojang.blaze3d.platform.GlUtil.getOpenGLVersion());
        }), 2));
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> { com.xenowarriors.character.Seasons.set(p.server, 3); arena(p); levels(p); }); }, 30));
        if (focus.startsWith("packscanners")) { ScannerTests.build(mc, l, focus); return; }      // (the Karma Scanner tests alone, in the real pack)
        if (focus.startsWith("packhandmine")) { HandMineTests.build(mc, l, focus); return; }      // (bare-hand mining tiers alone, with Vein Miner / Tree Harvester)
        if (focus.startsWith("packefbody")) { PackEfBodyTests.build(mc, l); return; }
        if (focus.startsWith("packremote")) { PackEfBodyTests.remote(mc, l); return; }      // (another player, as your client sees them)
        if (focus.startsWith("packxenodragon")) { PackXenoDragonTests.build(mc, l); return; }      // (the Xeno Dragon alone)
        if (focus.startsWith("packmikikofight")) { PackMikikoFightTests.build(mc, l); return; }      // (Mikiko's own combo in battle mode)
        if (focus.startsWith("packbakechecks")) { PackBakeTests.checkSkills(mc, l); return; }      // (skill poses)
        if (focus.startsWith("packbakecheckr")) { PackBakeTests.checkRhalix(mc, l); return; }      // (Rhalix only)
        if (focus.startsWith("packbakecheck")) { PackBakeTests.check(mc, l); return; }      // (rebuilt moves vs the originals)
        if (focus.startsWith("packbake")) { PackBakeTests.build(mc, l); return; }      // (records our animations for Epic Fight)
        if (focus.startsWith("packkyrie")) { PackKyrieTests.build(mc, l); return; }      // (Kyrie's Season 2 outfit and rapier)
        if (focus.startsWith("packwom")) { PackWomTests.build(mc, l); return; }      // (Weapons of Miracles' moves on our characters)
        characters(mc, l);
        menus(mc, l);
        skills(mc, l, CharacterType.MIKIKO);
        skills(mc, l, CharacterType.RHALIX);
        bots(mc, l);
        sense(mc, l);
        guns(mc, l, CharacterType.MIKIKO);
        guns(mc, l, CharacterType.RHALIX);
        blocks(mc, l);
        journey(mc, l);
        dmz(mc, l);
        teamUlt(mc, l);
        PackCullTests.build(mc, l);        // EntityCulling / Clumps: effects through walls, health orbs, XP
        PackEfBodyTests.build(mc, l);      // Mikiko in Epic Fight's battle mode with an axe
        PackEfBodyTests.remote(mc, l);     // another player (online), as this client sees them
        PackMikikoFightTests.build(mc, l); // Mikiko's own bare-hand combo in battle mode
        PackKyrieTests.build(mc, l);       // Kyrie's Season 2 outfit and her summoned rapier
        PackWomTests.build(mc, l);         // Weapons of Miracles' moves: bare-hand styles and our weapons
        PackTotemTests.build(mc, l);       // Inventory Totem vs. the downed system and the Xeno Blast
        PackTooltipTests.build(mc, l);     // Equipment Compare / Iceberg: every item's tooltip, comparisons
        PackBotBagTests.build(mc, l);      // a bot's extra tabs and tidying with the pack's inventory mods
        RaidTests.pack(mc, l);        // faction raids: a Government raid's helicopters and convoy shooting from their seats, SR and Townsfolk raids
        //   (before the Xeno Dragon: its bomb knocks the player out to DragonMineZ's Otherworld)
        PackXenoDragonTests.build(mc, l);  // the Xeno Dragon from a /killed Ender Dragon, its forms and Dragon Bomb
        KishiGravTests.pack(mc, l);   // Kishi as DragonMineZ gravity
        HudFaceTests.pack(mc, l);     // HUD portraits show the face the model is making
        l.add(new Step(null, () -> { GunClient.testAim = null; GunClient.testTrigger = null; mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON); }, 20));
        l.add(new Step("pack_end", () -> log("summary pass=" + pass + " fail=" + fail + (FAILED.isEmpty() ? "" : " failed=" + FAILED)), 2));
    }

    // ------------------------------------------------------------------ setup

    /** Levels high enough for every skill slot, the guns (both styles) and the team ultimates. */
    private static void levels(ServerPlayer p) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            d.statLevel[0] = 20; d.statLevel[1] = 20; d.statLevel[2] = 20;   // (Character Level 60: every Season 1 skill a default slot can hold is unlocked)
            d.bladeLevel = 60; d.bladeXp = 0; });
        com.xenowarriors.rhalix.WireLevel.set(p, 60);
        com.xenowarriors.compat.GunLevel.setLevel(p, 50);
        WeaponForms.sync(p, true);
    }

    private static void ready(ServerPlayer p) {
        p.getPersistentData().remove("xw_karma_lock_until");
        SkillManager.resetCooldowns(p);
        Karma.add(p, 200);
        p.setHealth(p.getMaxHealth());
    }

    /** A flat stone floor high up (same place every time), open sky, full health, day. */
    static void arena(ServerPlayer p) {
        var level = p.serverLevel();
        if (HOME[1] == 0) { HOME[0] = Math.floor(p.getX()) + 0.5; HOME[1] = 140; HOME[2] = Math.floor(p.getZ()) + 0.5; }
        BlockPos base = BlockPos.containing(HOME[0], HOME[1], HOME[2]);
        for (int x = -16; x <= 16; x++) for (int z = -10; z <= 22; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            for (int y = 0; y < 12; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        p.connection.teleport(HOME[0], HOME[1], HOME[2], 0F, 0F);
        p.fallDistance = 0;
        p.setGameMode(GameType.CREATIVE);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 1200, 4, false, false));
        level.setDayTime(6000);
    }

    static void clear(ServerPlayer p) {
        p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64), e -> e.getTags().contains("xw_pack")).forEach(e -> e.discard());
        for (var b : new ArrayList<>(com.xenowarriors.bot.PartyBots.bots(p))) b.discard();
        p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(64)).forEach(e -> e.discard());
    }

    static LivingEntity husk(ServerPlayer p, double dx, double dz) {
        var h = EntityType.HUSK.create(p.serverLevel());
        h.moveTo(HOME[0] + dx, HOME[1], HOME[2] + dz, 180F, 0F);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        h.setHealth(2000);
        h.addTag("xw_pack");
        p.serverLevel().addFreshEntity(h);
        return h;
    }

    private static float huskHp(ServerPlayer p) {
        float t = 0;
        for (var e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64), e -> e.getTags().contains("xw_pack"))) t += e.getHealth();
        return t;
    }

    private static void face(Minecraft mc) {
        var p = mc.player;
        if (p == null) return;
        p.setYRot(0F); p.yRotO = 0F; p.setYHeadRot(0F); p.yBodyRot = 0F; p.setXRot(3F); p.xRotO = 3F;
    }

    // ------------------------------------------------------------------ 1. every character

    private static void characters(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 5));
        for (CharacterType t : CharacterType.PLAYABLE) {
            l.add(new Step(null, () -> UiTest.character(t), 30));
            l.add(new Step(t == CharacterType.MIKIKO || t == CharacterType.RHALIX || t == CharacterType.ULTIMA || t == CharacterType.KAERU ? "pack_char_" + t.getId() : null,
                    () -> check("switch to " + t.getId(), ClientCharacterData.getLocal() == t, "client sees " + ClientCharacterData.getLocal()), 3));
        }
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); UiTest.character(CharacterType.MIKIKO); }, 30));
    }

    // ------------------------------------------------------------------ 2. every menu

    private static void menu(Minecraft mc, List<Step> l, String name, Runnable open, int wait) {
        l.add(new Step(null, () -> client("menu " + name, () -> { mc.setScreen(null); open.run(); }), wait));
        l.add(new Step("pack_menu_" + name, () -> check("menu " + name, mc.screen != null, "screen " + screen(mc)), 2));
    }

    private static void menus(Minecraft mc, List<Step> l) {
        menu(mc, l, "main", com.xenowarriors.client.PlayerStatsScreen::open, 25);
        int n = new com.xenowarriors.client.menu.MainMenuScreen().entryCount();
        for (int i = 0; i < n; i++) {
            int ii = i;
            String nm = new com.xenowarriors.client.menu.MainMenuScreen().entryName(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
            menu(mc, l, "main_" + nm, () -> {
                allowDmz = nm.contains("dragon");
                var m = new com.xenowarriors.client.menu.MainMenuScreen();
                mc.setScreen(m);
                m.select(ii);
                m.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            }, 35);
        }
        l.add(new Step(null, () -> allowDmz = false, 1));
        menu(mc, l, "select", () -> mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()), 20);
        for (int t = 0; t < 4; t++) { int tt = t; menu(mc, l, "stats_tab" + t, () -> com.xenowarriors.client.PlayerStatsScreen.openTab(null, tt), 25); }
        menu(mc, l, "skills", () -> mc.setScreen(new com.xenowarriors.client.SkillLoadoutScreen(null)), 20);
        menu(mc, l, "ka_guide", () -> mc.setScreen(new com.xenowarriors.client.KaGuideScreen(null)), 20);
        menu(mc, l, "collection", () -> server(p -> com.xenowarriors.collection.Collection.open(p, true)), 30);
        menu(mc, l, "bounties", () -> server(com.xenowarriors.bounty.Bounties::open), 30);
        menu(mc, l, "vr_console", () -> server(com.xenowarriors.vr.Vr::open), 30);
        menu(mc, l, "bot_pick", () -> server(com.xenowarriors.bot.PartyBots::openPick), 30);
        menu(mc, l, "party", () -> mc.setScreen(new com.xenowarriors.client.bot.PartyScreen()), 20);
        l.add(new Step(null, () -> { mc.setScreen(null); server(PackTests::clear); }, 10));
    }

    // ------------------------------------------------------------------ 3. skills through the real key packets

    private static void skills(Minecraft mc, List<Step> l, CharacterType who) {
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.character(who); UiTest.hold(null); }, 30));
        l.add(new Step(null, () -> server(p -> { arena(p); levels(p); clear(p); p.setGameMode(GameType.SURVIVAL); husk(p, -2, 6); husk(p, 2, 7); husk(p, 0, 9); }), 20));
        for (int slot = 0; slot <= SkillManager.ULTIMATE_SLOT; slot++) {
            int s = slot;
            float[] k = new float[1];
            l.add(new Step(null, () -> { face(mc); server(p -> { ready(p); k[0] = Karma.get(p); }); }, 8));
            l.add(new Step(null, () -> client("skill", () -> { face(mc); ModNetwork.sendToServer(new com.xenowarriors.network.UseSkillPacket(s));
                if (s == SkillManager.ULTIMATE_SLOT) ModNetwork.sendToServer(new com.xenowarriors.network.UseSkillPacket(com.xenowarriors.skill.UltCharge.RELEASE)); }),   // (a tap: the ultimate charges its 1.5 s first)
                    s == SkillManager.ULTIMATE_SLOT ? com.xenowarriors.skill.UltCharge.MIN + 15 : 3));
            l.add(new Step(null, () -> {                                    // (right away: Bang's cooldown is only 7 ticks)
                int cd = ClientSkillState.cooldownTicks(s);
                server(p -> {
                    var sk = SkillManager.get(p, s);
                    float now = Karma.get(p);
                    check(who.getId() + " skill " + s + " (" + (sk == null ? "none" : sk.getId()) + ")", now < k[0] && (cd > 0 || sk != null && sk.getCooldownTicks() <= 10),   // (a tiny cooldown can be over already at low FPS)
                            "client cooldown " + cd + " ticks, karma " + f(k[0]) + " -> " + f(now));
                });
            }, 22));
            l.add(new Step(s == 0 || s == SkillManager.ULTIMATE_SLOT ? "pack_" + who.getId() + "_skill" + s : null, () -> {}, 60));
        }
        l.add(new Step(null, () -> server(p -> { SkillManager.resetCooldowns(p); clear(p); p.setGameMode(GameType.CREATIVE); }), 40));
    }

    // ------------------------------------------------------------------ 4. bots

    private static void bots(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); server(p -> {
            arena(p); clear(p);
            var r = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
            var k = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.KAERU, null);
            if (r != null) r.moveTo(HOME[0] - 1.5, HOME[1], HOME[2] + 3, 180, 0);
            if (k != null) k.moveTo(HOME[0] + 1.5, HOME[1], HOME[2] + 3, 180, 0);
        }); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 60));
        l.add(new Step("pack_bots", () -> server(p -> {
            var b = com.xenowarriors.bot.PartyBots.bots(p);
            check("summon bots", b.size() == 2, b.size() + " bots: " + b.stream().map(x -> x.character().getId() + " alive=" + x.isAlive()).toList());
        }), 3));
        l.add(new Step(null, () -> client("bots seen", () -> {
            long seen = mc.level.getEntitiesOfClass(com.xenowarriors.bot.PartyBotEntity.class, mc.player.getBoundingBox().inflate(30)).size();
            check("bots on the client", seen == 2, seen + " bot entities rendered nearby");
        }), 5));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(PackTests::clear); }, 10));
    }

    // ------------------------------------------------------------------ 5. Karma Sense (the key's packets)

    private static void sense(Minecraft mc, List<Step> l) {
        float[] k = new float[1];
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> { p.setGameMode(GameType.SURVIVAL); ready(p); husk(p, 4, 14); k[0] = Karma.get(p); }); }, 15));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket(true, true, mc.options.getEffectiveRenderDistance())), 30));
        l.add(new Step("pack_sense", () -> server(p -> check("karma sense", Karma.get(p) < k[0], "karma " + f(k[0]) + " -> " + f(Karma.get(p)))), 3));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.KarmaSensePacket(false, false, 0)), 20));
        l.add(new Step(null, () -> server(p -> { clear(p); p.setGameMode(GameType.CREATIVE); }), 10));
    }

    // ------------------------------------------------------------------ 6. every gun form: summon, aim, shoot

    private static void guns(Minecraft mc, List<Step> l, CharacterType who) {
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.character(who); }, 30));
        l.add(new Step(null, () -> server(p -> { arena(p); levels(p); clear(p); p.getInventory().clearContent(); p.getPersistentData().remove(WeaponForms.BROKEN);
            p.setGameMode(GameType.SURVIVAL); husk(p, 0, 10); }), 20));
        for (Form form : Form.values()) {
            if (!form.gun()) continue;
            String nm = who.getId() + "_" + form.name().toLowerCase(Locale.ROOT);
            boolean[] ok = new boolean[1];
            int[] shots = new int[1];
            float[] hp = new float[1];
            l.add(new Step(null, () -> { GunClient.testAim = false; GunClient.testTrigger = false;
                server(p -> { p.getPersistentData().remove(WeaponForms.BROKEN); ok[0] = WeaponForms.summon(p, form, false); hp[0] = huskHp(p); }); }, 25));
            l.add(new Step(null, () -> client("gun " + nm, () -> {
                var g = GunClient.held(mc.player);
                check("summon " + nm, ok[0] && g != null, "summoned=" + ok[0] + " held " + mc.player.getMainHandItem().getItem());
                face(mc); GunClient.testAim = true;
            }), 15));
            l.add(new Step(null, () -> { face(mc); shots[0] = GunClient.shotsSent; GunClient.testTrigger = true; GunClient.testClick(); }, 6));
            l.add(new Step(null, () -> { face(mc); GunClient.testTrigger = false; }, 6));
            l.add(new Step("pack_gun_" + nm, () -> client("gun fire " + nm, () -> {
                check("aim " + nm, GunClient.aiming() && GunClient.aimProgress(1F) > 0.3F, "aiming=" + GunClient.aiming() + " progress " + f(GunClient.aimProgress(1F)) + " fov " + f(GunClient.lastFov));
                check("shoot " + nm, GunClient.shotsSent > shots[0], "shots sent " + shots[0] + " -> " + GunClient.shotsSent);
                server(p -> log(nm + " target hp " + f(hp[0]) + " -> " + f(huskHp(p))));
            }), 3));
            l.add(new Step(null, () -> GunClient.testAim = false, 10));
        }
        l.add(new Step(null, () -> { GunClient.testAim = null; GunClient.testTrigger = null; server(p -> { p.getInventory().clearContent(); clear(p); p.setGameMode(GameType.CREATIVE); }); }, 10));
    }

    // ------------------------------------------------------------------ 7. placing and breaking blocks (the client's real interaction path)

    private static void blocks(Minecraft mc, List<Step> l) {
        BlockPos[] at = new BlockPos[1];
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); server(p -> { arena(p); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_BRICKS, 16)); }); }, 25));
        l.add(new Step(null, () -> client("place", () -> {
            face(mc);
            BlockPos floor = BlockPos.containing(HOME[0], HOME[1] - 1, HOME[2] + 2);
            at[0] = floor.above();
            var r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
            log("place result " + r);
        }), 15));
        l.add(new Step("pack_blocks", () -> server(p -> check("place block", p.serverLevel().getBlockState(at[0]).is(Blocks.STONE_BRICKS), "block at " + at[0].toShortString() + " = " + p.serverLevel().getBlockState(at[0]))), 3));
        l.add(new Step(null, () -> client("break", () -> mc.gameMode.startDestroyBlock(at[0], Direction.UP)), 15));
        l.add(new Step(null, () -> server(p -> check("break block", p.serverLevel().getBlockState(at[0]).isAir(), "block at " + at[0].toShortString() + " = " + p.serverLevel().getBlockState(at[0]))), 3));
        // one of ours: a Xeno Warriors block item placed the same way
        l.add(new Step(null, () -> server(p -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(com.xenowarriors.sky.ModBlocks.HEALING_POD.get().asItem()))), 10));
        l.add(new Step(null, () -> client("place ours", () -> {
            face(mc);
            BlockPos floor = BlockPos.containing(HOME[0] + 2, HOME[1] - 1, HOME[2] + 3);
            at[0] = floor.above();
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
        }), 15));
        l.add(new Step("pack_blocks_ours", () -> server(p -> check("place healing pod", p.serverLevel().getBlockState(at[0]).is(com.xenowarriors.sky.ModBlocks.HEALING_POD.get()),
                "block at " + at[0].toShortString() + " = " + p.serverLevel().getBlockState(at[0]))), 3));
        l.add(new Step(null, () -> server(p -> { p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); arena(p); }), 10));
    }

    // ------------------------------------------------------------------ 8. a Journey

    private static void journey(Minecraft mc, List<Step> l) {
        CharacterType[] eight = {CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.LEXY, CharacterType.MAX, CharacterType.TECHNICA, CharacterType.STEVIE, CharacterType.LOGAN};
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            com.xenowarriors.journey.Journey.setCleared(p, 2);
            com.xenowarriors.journey.Journey.open(p);
            for (CharacterType t : eight) com.xenowarriors.journey.Journey.action(p, 1, t.ordinal(), 0);
        }); }, 30));
        l.add(new Step("pack_journey_lobby", () -> check("journey lobby", mc.screen != null, "screen " + screen(mc)), 2));
        l.add(new Step(null, () -> server(p -> { com.xenowarriors.journey.Journey.action(p, 2, 0, 0); com.xenowarriors.journey.Journey.action(p, 4, 0, 0); }), 80));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 20));
        l.add(new Step("pack_journey", () -> server(p -> check("journey start", com.xenowarriors.journey.Journey.inRun(p),
                "in run=" + com.xenowarriors.journey.Journey.inRun(p) + " dimension " + p.level().dimension().location())), 3));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> com.xenowarriors.journey.Journey.leave(p, "pack test", true)); }, 60));
        l.add(new Step(null, () -> server(p -> { check("journey leave", !com.xenowarriors.journey.Journey.inLobbyOrRun(p), "back in " + p.level().dimension().location());
            UiTest.character(CharacterType.MIKIKO); arena(p); }), 30));
    }

    // ------------------------------------------------------------------ 9. DragonMineZ's menu

    private static void dmz(Minecraft mc, List<Step> l) {
        boolean has = ModList.get().isLoaded("dragonminez");
        l.add(new Step(null, () -> { mc.setScreen(null); log("dragonminez loaded=" + has + " menu available=" + com.xenowarriors.client.compat.DmzMenu.available()); }, 5));
        if (!has) return;
        l.add(new Step(null, () -> client("dmz menu", () -> { allowDmz = true; log("DmzMenu.open -> " + com.xenowarriors.client.compat.DmzMenu.open()); }), 30));
        l.add(new Step("pack_dmz_menu", () -> check("dmz menu", mc.screen != null && mc.screen.getClass().getName().startsWith("com.dragonminez"), "screen " + screen(mc)), 2));
        l.add(new Step(null, () -> { allowDmz = false; mc.setScreen(null); }, 10));
        l.add(new Step(null, () -> server(p -> check("dmz missions ignore biomes", "true".equals(com.xenowarriors.compat.DmzQuests.matchesBiome(p, "minecraft:mushroom_fields"))
                && "true".equals(com.xenowarriors.compat.DmzQuests.biomeObjectiveMet(p, "dragonminez:ajissa_plains")),
                "gates=" + com.xenowarriors.compat.DmzQuests.biomeGates().size() + " matchesBiome=" + com.xenowarriors.compat.DmzQuests.matchesBiome(p, "minecraft:mushroom_fields")
                        + " biome objective=" + com.xenowarriors.compat.DmzQuests.biomeObjectiveMet(p, "dragonminez:ajissa_plains"))), 5));
    }

    // ------------------------------------------------------------------ 10. a team ultimate (Rhalix + a Mikiko bot: Blazing Judgment)

    private static void teamUlt(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.setScreen(null); UiTest.character(CharacterType.RHALIX); }, 30));
        l.add(new Step(null, () -> server(p -> {
            arena(p); levels(p); clear(p);
            p.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < 4; i++) husk(p, Math.cos(i * 1.6) * 4, 9 + Math.sin(i * 1.6) * 3);
            var b = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (b != null) b.moveTo(HOME[0] + 2.5, HOME[1], HOME[2], 0, 0);
        }), 40));
        l.add(new Step(null, () -> server(p -> {
            ready(p);
            for (var b : com.xenowarriors.bot.PartyBots.bots(p)) com.xenowarriors.bot.BotTeamUlt.readyForTest(b);
            com.xenowarriors.party.TeamUltimate.clearForTest();
            var partners = com.xenowarriors.party.TeamUltimate.partners(p);
            p.setShiftKeyDown(true);
            boolean took = com.xenowarriors.party.TeamUltimate.onUltimateKey(p);
            p.setShiftKeyDown(false);
            var u = p.serverLevel().getEntitiesOfClass(com.xenowarriors.entity.TeamUltEntity.class, p.getBoundingBox().inflate(40));
            check("team ultimate", took && u.size() == 1, "partners " + partners.stream().map(com.xenowarriors.party.TeamUltimate::name).toList() + ", handled=" + took
                    + ", entities " + u.size() + (u.isEmpty() ? "" : " kind " + u.get(0).kind()));
        }), 10));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 20));
        l.add(new Step("pack_teamult", () -> {}, 40));
        l.add(new Step(null, () -> {}, 80));
        l.add(new Step("pack_teamult_end", () -> server(p -> log("team ultimate done: Rhalix dealt " + f(com.xenowarriors.entity.TeamUltEntity.lastRhalixDamage)
                + ", Mikiko dealt " + f(com.xenowarriors.entity.TeamUltEntity.lastMikikoDamage))), 3));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> { clear(p); p.setGameMode(GameType.CREATIVE); }); }, 10));
    }
}

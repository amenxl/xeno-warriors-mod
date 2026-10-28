package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * UI test (CI only, XW_UITEST=1): once in a world, opens every screen and takes a screenshot, then shoots each
 * character from the front (cel-shading off and on), then quits. Screenshots land in run/screenshots.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UiTest {
    private static final boolean ON = "1".equals(System.getenv("XW_UITEST"));
    /** Makes characters power up (as if charging Ki) for the aura gallery. */
    public static boolean forceCharge;
    private record Step(String name, Runnable action, int delay) {}
    private static List<Step> steps;
    private static int idx = -1, wait, ingame, pre;

    private UiTest() {}

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) f.accept(p); });
    }

    private static void character(CharacterType t) {
        server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t));
    }

    private static List<Step> build() {
        Minecraft mc = Minecraft.getInstance();
        List<Step> l = new ArrayList<>();
        l.add(new Step(null, () -> server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 3);
            p.server.getPlayerList().op(p.getGameProfile());
            p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).putBoolean("xw_collection_seen", true);
            CompoundHolder.unlock(p);
            p.serverLevel().setDayTime(6000);
            // a clean stage for the model shots: clear the grass around, stone floor
            var c = p.blockPosition();
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                for (int dy = 0; dy <= 4; dy++) p.serverLevel().setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                p.serverLevel().setBlock(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            }
            p.serverLevel().setWeatherParameters(6000, 0, false, false);
            // keep it day and safe for long captures
            p.server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, p.server);
            p.server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
            if (System.getenv("XW_UITEST_FOCUS") != null && !System.getenv("XW_UITEST_FOCUS").isEmpty() && !"bats".equals(System.getenv("XW_UITEST_FOCUS"))) p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, p.getBoundingBox().inflate(64), m -> !(m instanceof com.xenowarriors.sr.SrMemberEntity)).forEach(m -> m.discard());
        }), 20));
        if ("sr".equals(System.getenv("XW_UITEST_FOCUS"))) { srGallery(mc, l); return l; }
        if ("anim".equals(System.getenv("XW_UITEST_FOCUS"))) { srAnims(mc, l); return l; }
        if ("bats".equals(System.getenv("XW_UITEST_FOCUS"))) { bats(mc, l); return l; }
        if ("vehicles".equals(System.getenv("XW_UITEST_FOCUS"))) { vehicles(mc, l); return l; }
        if ("kaeru".equals(System.getenv("XW_UITEST_FOCUS"))) { kaeru(mc, l); return l; }
        if ("sp".equals(System.getenv("XW_UITEST_FOCUS"))) { skyPatrol(mc, l); return l; }
        if ("ffr".equals(System.getenv("XW_UITEST_FOCUS"))) { ffr(mc, l); return l; }
        if ("auras".equals(System.getenv("XW_UITEST_FOCUS"))) { auras(mc, l); return l; }
        if ("beatbox".equals(System.getenv("XW_UITEST_FOCUS"))) { beatbox(mc, l); return l; }
        if ("girls".equals(System.getenv("XW_UITEST_FOCUS"))) { girls(mc, l); return l; }
        if ("allanim".equals(System.getenv("XW_UITEST_FOCUS"))) { allAnims(mc, l); return l; }
        if ("mikiko".equals(System.getenv("XW_UITEST_FOCUS"))) { mikikoCombo(mc, l); return l; }
        if ("combo".equals(System.getenv("XW_UITEST_FOCUS"))) { combos(mc, l); return l; }
        if ("s3fits".equals(System.getenv("XW_UITEST_FOCUS"))) { s3Outfits(mc, l); return l; }
        if ("ores".equals(System.getenv("XW_UITEST_FOCUS"))) { oresAndPjs(mc, l); return l; }
        if ("village".equals(System.getenv("XW_UITEST_FOCUS"))) { village(mc, l); return l; }
        if ("capital".equals(System.getenv("XW_UITEST_FOCUS"))) { capital(mc, l); return l; }
        if ("partyui".equals(System.getenv("XW_UITEST_FOCUS"))) { partyUi(mc, l); return l; }
        if ("allanim2".equals(System.getenv("XW_UITEST_FOCUS"))) { allAnims(mc, l); return l; }
        if ("underlight".equals(System.getenv("XW_UITEST_FOCUS"))) { underlight(mc, l); return l; }
        if ("mainmenu".equals(System.getenv("XW_UITEST_FOCUS"))) { mainMenu(mc, l); return l; }
        if ("posecal".equals(System.getenv("XW_UITEST_FOCUS"))) { poseCal(mc, l); return l; }
        if ("popups".equals(System.getenv("XW_UITEST_FOCUS"))) { popups(mc, l); return l; }
        if ("xenoblast".equals(System.getenv("XW_UITEST_FOCUS"))) { xenoBlast(mc, l); return l; }
        if ("customanim".equals(System.getenv("XW_UITEST_FOCUS"))) { customAnim(mc, l); return l; }
        if ("tower".equals(System.getenv("XW_UITEST_FOCUS"))) { tower(mc, l); return l; }
        if ("efcal".equals(System.getenv("XW_UITEST_FOCUS"))) { efCal(mc, l); return l; }
        if ("townsfolk".equals(System.getenv("XW_UITEST_FOCUS"))) { townsfolk(mc, l); return l; }
        if ("dmzoutfits".equals(System.getenv("XW_UITEST_FOCUS"))) { dmzOutfits(mc, l); return l; }
        if ("dmzmenu".equals(System.getenv("XW_UITEST_FOCUS"))) { dmzMenu(mc, l); return l; }
        if ("taczguard".equals(System.getenv("XW_UITEST_FOCUS"))) { taczGuard(mc, l); return l; }
        if ("xenotest".equals(System.getenv("XW_UITEST_FOCUS"))) { xenoTest(mc, l); return l; }
        if ("govshop".equals(System.getenv("XW_UITEST_FOCUS"))) { govShop(mc, l); return l; }
        if ("charlock".equals(System.getenv("XW_UITEST_FOCUS"))) { charLock(mc, l); return l; }
        if ("kitsunestun".equals(System.getenv("XW_UITEST_FOCUS"))) { kitsuneStun(mc, l); return l; }
        if (System.getenv("XW_UITEST_FOCUS") != null && System.getenv("XW_UITEST_FOCUS").startsWith("portraits")) { portraits(mc, l, System.getenv("XW_UITEST_FOCUS").substring(9)); return l; }
        l.add(new Step(null, () -> character(CharacterType.MIKIKO), 40));
        l.add(new Step("hud_mikiko", () -> mc.setScreen(null), 30));
        l.add(new Step("select_screen", () -> mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()), 20));
        for (int t = 0; t < 4; t++) {
            int tt = t;
            l.add(new Step("menu_tab" + t, () -> PlayerStatsScreen.openTab(null, tt), 25));
        }
        l.add(new Step("skills", () -> mc.setScreen(new SkillLoadoutScreen(null)), 20));
        l.add(new Step("ka_guide", () -> mc.setScreen(new KaGuideScreen(null)), 20));
        l.add(new Step("collection", () -> { mc.setScreen(null); server(p -> com.xenowarriors.collection.Collection.open(p, true)); }, 30));
        l.add(new Step("bounties", () -> { mc.setScreen(null); server(com.xenowarriors.bounty.Bounties::open); }, 30));
        l.add(new Step("vr_console", () -> { mc.setScreen(null); server(com.xenowarriors.vr.Vr::open); }, 30));
        l.add(new Step("bot_pick", () -> { mc.setScreen(null); server(com.xenowarriors.bot.PartyBots::openPick); }, 30));
        l.add(new Step("party_menu", () -> mc.setScreen(new com.xenowarriors.client.bot.PartyScreen()), 20));
        // the characters from the front, cel-shading off and on
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10F); }, 20));
        for (CharacterType t : CharacterType.PLAYABLE) {
            for (boolean cel : new boolean[]{false, true}) {
                l.add(new Step(null, () -> { character(t); ClientSettings.setCelShading(cel); }, 40));
                l.add(new Step("model_" + t.getId() + (cel ? "_cel" : ""), () -> {}, 10));
            }
        }
        l.add(new Step(null, () -> { ClientSettings.setCelShading(false); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
        return l;
    }

    /** The named SR bosses, one at a time in front of the camera (wings out for the Skylanders, demon forms too). */
    private static void srGallery(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setXRot(8F); mc.player.setYRot(0F); }, 20));
        String[] names = com.xenowarriors.sr.SrMemberEntity.NAMED;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            for (int pose = 0; pose < 3; pose++) {
                final int ps = pose;
                if (pose == 2 && !(names[i].equals("persephone") || names[i].equals("ava"))) continue;
                l.add(new Step(null, () -> server(p -> {
                    var lvl = p.serverLevel();
                    lvl.getEntitiesOfClass(com.xenowarriors.sr.SrMemberEntity.class, p.getBoundingBox().inflate(20)).forEach(e -> e.discard());
                    var m = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(lvl);
                    if (m == null) return;
                    p.setYRot(0); p.setXRot(8);
                    m.moveTo(p.getX(), p.getY() + (ps == 1 ? 0.6 : 0), p.getZ() + 3.6, 180, 0);
                    m.setup(2 + idx, p.blockPosition());
                    m.setNoAi(true); m.setYHeadRot(180); m.yBodyRot = 180;
                    lvl.addFreshEntity(m);
                    m.uiTestPose(ps == 1, ps == 2);
                    if (idx == 0 && ps == 0) {             // a plain member with a vanilla sword beside him, to compare how it's held
                        var c = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(lvl);
                        if (c != null) { c.moveTo(p.getX() + 1.8, p.getY(), p.getZ() + 3.6, 150, 0); c.setup(0, p.blockPosition()); c.setNoAi(true); lvl.addFreshEntity(c); }
                    }
                }), 30));
                l.add(new Step("sr_" + names[i] + (ps == 1 ? "_wings" : ps == 2 ? "_demon" : ""), () -> {}, 5));
            }
        }
        l.add(new Step(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(com.xenowarriors.sr.SrMemberEntity.class, p.getBoundingBox().inflate(20)).forEach(e -> e.discard());
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.SENJU_BLADE.get()));
        }), 20));
        l.add(new Step("blade_firstperson", () -> {}, 5));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT), 20));
        l.add(new Step("blade_thirdperson", () -> {}, 5));
        l.add(new Step(null, () -> mc.player.setYRot(90F), 20));
        l.add(new Step("blade_thirdperson_side", () -> {}, 5));
        l.add(new Step(null, () -> { mc.player.setYRot(0F); server(p -> p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD))); }, 20));
        l.add(new Step("vanilla_thirdperson", () -> {}, 5));
        l.add(new Step(null, () -> { mc.player.setYRot(90F); }, 20));
        l.add(new Step("vanilla_thirdperson_side", () -> {}, 5));
        l.add(new Step(null, () -> { mc.player.setYRot(0F); server(p -> p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.SENJU_BLADE.get()))); }, 20));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 20));
        l.add(new Step("blade_hotbar", () -> {}, 5));
    }

    /** Frame-by-frame animation captures of the named SR bosses (driven by SrModel's fixed clock), for GIFs. */
    private static void srAnims(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setXRot(10F); mc.player.setYRot(0F); }, 20));
        String[] names = com.xenowarriors.sr.SrMemberEntity.NAMED;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            final String n = names[i];
            boolean demonic = n.equals("persephone") || n.equals("ava");
            l.add(new Step(null, () -> server(p -> {
                var lvl = p.serverLevel();
                lvl.getEntitiesOfClass(com.xenowarriors.sr.SrMemberEntity.class, p.getBoundingBox().inflate(20)).forEach(e -> e.discard());
                var m = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(lvl);
                if (m == null) return;
                p.setYRot(0); p.setXRot(10);
                m.moveTo(p.getX(), p.getY(), p.getZ() + 4.4, 150, 0);
                m.setup(2 + idx, p.blockPosition());
                m.setNoAi(true); m.setYHeadRot(150); m.yBodyRot = 150; m.setNoGravity(true);
                lvl.addFreshEntity(m);
            }), 30));
            java.util.List<Object[]> states = new java.util.ArrayList<>();
            states.add(new Object[]{"idle", 24, n.equals("keita") ? 5F : 3F});
            states.add(new Object[]{"walk", 16, 1.5F});
            states.add(new Object[]{"attack", 14, 1F});
            states.add(new Object[]{n.equals("logan") ? "redirect" : "special", 15, 2F});
            if (!demonic) states.add(new Object[]{"fly", 16, 2F});
            else states.add(new Object[]{"demon", 20, 2F});
            for (Object[] st : states) {
                String name = (String) st[0]; int count = (int) st[1]; float step = (float) st[2];
                for (int f = 0; f < count; f++) {
                    final int ff = f;
                    l.add(new Step(null, () -> {
                        com.xenowarriors.client.npc.SrModel.force = name;
                        com.xenowarriors.client.npc.SrModel.clock = ff * step;
                        if (name.equals("demon") && ff == count / 2) server(p -> p.serverLevel().getEntitiesOfClass(com.xenowarriors.sr.SrMemberEntity.class,
                                p.getBoundingBox().inflate(20)).forEach(e -> e.uiTestPose(false, true)));
                        animParticles(mc, n, name, ff);
                    }, 1));
                    l.add(new Step(String.format("anim_%s_%s_%02d", n, name, f), () -> {}, 0));
                }
            }
            l.add(new Step(null, () -> com.xenowarriors.client.npc.SrModel.force = null, 2));
        }
        l.add(new Step(null, () -> { com.xenowarriors.client.npc.SrModel.force = null;
            server(p -> p.serverLevel().getEntitiesOfClass(com.xenowarriors.sr.SrMemberEntity.class, p.getBoundingBox().inflate(20)).forEach(e -> e.discard())); }, 5));
    }

    /** The Sky Patrol: each one front/back/flying, a few skills, and both pages of the character select screen. */
    private static void skyPatrol(Minecraft mc, List<Step> l) {
        CharacterType[] sp = {CharacterType.KYRIE, CharacterType.LEXY, CharacterType.MAX, CharacterType.SAM, CharacterType.TECHNICA, CharacterType.STEVIE, CharacterType.GARRY, CharacterType.AIONI, CharacterType.YURI, CharacterType.ZANE, CharacterType.LOGAN, CharacterType.KILL, CharacterType.KEITA};
        for (CharacterType t : sp) {
            l.add(new Step(null, () -> { character(t); server(p -> { p.getAbilities().flying = false; p.onUpdateAbilities(); }); }, 40));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(10F); mc.player.setYRot(0F); }, 30));
            l.add(new Step("sp_" + t.getId() + "_front", () -> {}, 5));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(10F); }, 20));
            l.add(new Step("sp_" + t.getId() + "_back", () -> {}, 5));
            l.add(new Step(null, () -> server(p -> { p.getAbilities().flying = true; p.onUpdateAbilities(); p.teleportTo(p.getX(), p.getY() + 2, p.getZ()); }), 30));
            l.add(new Step("sp_" + t.getId() + "_flying", () -> {}, 5));
            l.add(new Step(null, () -> server(p -> { p.getAbilities().flying = false; p.onUpdateAbilities(); }), 30));
        }
        // a zombie to try things on (no AI)
        l.add(new Step(null, () -> server(p -> {
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            if (z == null) return;
            z.moveTo(p.getX(), p.getY(), p.getZ() + 4, 180, 0); z.setNoAi(true); z.setYHeadRot(180); z.yBodyRot = 180;
            p.serverLevel().addFreshEntity(z); p.setYRot(0); p.setXRot(10);
        }), 20));
        l.add(new Step(null, () -> { character(CharacterType.SAM); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 30));
        l.add(new Step(null, () -> server(p -> { p.setYRot(0); p.setXRot(5); new com.xenowarriors.skill.sp.SamSkills.TickingTimeBomb().use(p); }), 30));
        l.add(new Step("sp_sam_bomb", () -> {}, 5));
        l.add(new Step(null, () -> { character(CharacterType.KYRIE); }, 30));
        l.add(new Step(null, () -> server(p -> new com.xenowarriors.skill.sp.KyrieSkills.BulletStop().use(p)), 20));
        l.add(new Step("sp_kyrie_field", () -> {}, 5));
        l.add(new Step(null, () -> { character(CharacterType.MAX); }, 30));
        l.add(new Step(null, () -> server(p -> new com.xenowarriors.skill.sp.MaxSkills.WindWall().use(p)), 10));
        l.add(new Step("sp_max_windwall", () -> {}, 5));
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 40));
        for (int t = 0; t < com.xenowarriors.client.screen.CharacterSelectScreen.Group.values().length; t++) {
            l.add(new Step("sp_select_team" + t, () -> {}, 5));
            l.add(new Step(null, () -> { mc.setScreen(null); com.xenowarriors.client.screen.CharacterSelectScreen.nextPageForTest();
                mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 30));
        }
        l.add(new Step(null, () -> mc.setScreen(null), 5));
    }

    private static void outfit(int o) {
        server(p -> { p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = o);
            com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p); });
    }

    private static void hold(net.minecraft.world.item.Item it) {
        server(p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, it == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(it)));
    }

    /** Yuri (front/back, glasses + holo laptop idle frames, C1 PJs), Zane (front/back, broom, spells), Max's knife and Sam's bat. */
    private static void ffr(Minecraft mc, List<Step> l) {
        Runnable front = () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10F); mc.player.setYRot(0F); };
        Runnable back = () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(10F); };
        l.add(new Step(null, () -> { character(CharacterType.YURI); hold(null); }, 40));
        l.add(new Step(null, front, 20));
        l.add(new Step("ffr_yuri_front", () -> {}, 5));
        l.add(new Step(null, back, 15));
        l.add(new Step("ffr_yuri_back", () -> {}, 5));
        l.add(new Step(null, () -> { front.run(); mc.player.setYRot(-40F); mc.player.setYHeadRot(-40F); mc.player.yBodyRot = -40F; }, 10));   // three-quarter view
        for (int f = 0; f < 22; f++) l.add(new Step(String.format("ffr_yuri_idle_%02d", f), () -> {}, 3));
        l.add(new Step(null, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK), 6));
        l.add(new Step("ffr_yuri_idle_back", () -> {}, 5));
        l.add(new Step(null, () -> { outfit(1); }, 30));
        l.add(new Step(null, front, 10));
        l.add(new Step("ffr_yuri_pjs_front", () -> {}, 5));
        l.add(new Step(null, back, 15));
        l.add(new Step("ffr_yuri_pjs_back", () -> {}, 5));
        l.add(new Step(null, () -> { outfit(0); character(CharacterType.ZANE); }, 40));
        l.add(new Step(null, front, 20));
        l.add(new Step("ffr_zane_front", () -> {}, 5));
        l.add(new Step(null, back, 15));
        l.add(new Step("ffr_zane_back", () -> {}, 5));
        l.add(new Step(null, () -> { hold(com.xenowarriors.item.ModItems.ZANE_BROOM.get()); front.run(); }, 20));
        l.add(new Step("ffr_zane_broom", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> {
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            if (z == null) return;
            z.moveTo(p.getX(), p.getY(), p.getZ() + 4, 180, 0); z.setNoAi(true); z.setYHeadRot(180); z.yBodyRot = 180;
            p.serverLevel().addFreshEntity(z); p.setYRot(0); p.setXRot(10);
        }), 20));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(p -> { p.setYRot(0); p.setXRot(8); new com.xenowarriors.skill.ffr.ZaneSkills.ThunderSpell().use(p); }); }, 4));
        l.add(new Step("ffr_zane_thunder", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> new com.xenowarriors.skill.ffr.ZaneSkills.IceSpell().use(p)), 6));
        l.add(new Step("ffr_zane_ice", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> new com.xenowarriors.skill.ffr.ZaneSkills.FireSpell().use(p)), 6));
        l.add(new Step("ffr_zane_fire", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.skill.ffr.ZaneProjection.use(p)), 10));
        l.add(new Step("ffr_zane_projection", () -> {}, 5));
        l.add(new Step(null, () -> { character(CharacterType.YURI); hold(null); }, 40));
        l.add(new Step(null, () -> server(p -> new com.xenowarriors.skill.ffr.YuriSkills.Firewall().use(p)), 12));
        l.add(new Step("ffr_yuri_firewall", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> new com.xenowarriors.skill.ffr.YuriSkills.ElevatorHack().use(p)), 16));
        l.add(new Step("ffr_yuri_elevator", () -> {}, 5));
        l.add(new Step(null, () -> { character(CharacterType.MAX); hold(com.xenowarriors.item.ModItems.MAX_KNIFE.get()); }, 40));
        l.add(new Step(null, front, 20));
        l.add(new Step("ffr_max_knife", () -> {}, 5));
        l.add(new Step(null, () -> { character(CharacterType.SAM); hold(com.xenowarriors.item.ModItems.SAM_BAT.get()); }, 40));
        l.add(new Step(null, front, 20));
        l.add(new Step("ffr_sam_bat", () -> {}, 5));
        // Max and Sam's Royal Capital disguises
        for (CharacterType t : new CharacterType[]{CharacterType.MAX, CharacterType.SAM}) {
            l.add(new Step(null, () -> { character(t); hold(null); }, 30));
            l.add(new Step(null, () -> outfit(1), 30));
            l.add(new Step(null, front, 15));
            l.add(new Step("ffr_" + t.getId() + "_royal_front", () -> {}, 5));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(-50F); mc.player.yBodyRot = -50F; mc.player.setYHeadRot(-50F); }, 15));
            l.add(new Step("ffr_" + t.getId() + "_royal_side", () -> {}, 5));
            l.add(new Step(null, back, 15));
            l.add(new Step("ffr_" + t.getId() + "_royal_back", () -> {}, 5));
            l.add(new Step(null, () -> outfit(0), 10));
        }
        // the girls' body on everyone
        for (CharacterType t : new CharacterType[]{CharacterType.KAERU, CharacterType.KYRIE, CharacterType.LEXY, CharacterType.TECHNICA, CharacterType.AIONI, CharacterType.YURI}) {
            l.add(new Step(null, () -> { character(t); hold(null); }, 30));
            l.add(new Step(null, front, 15));
            l.add(new Step("ffr_girl_" + t.getId() + "_front", () -> {}, 5));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(-60F); mc.player.yBodyRot = -60F; mc.player.setYHeadRot(-60F); }, 15));
            l.add(new Step("ffr_girl_" + t.getId() + "_side", () -> {}, 5));
        }
        l.add(new Step(null, () -> { hold(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 40));
        for (int t = 0; t < com.xenowarriors.client.screen.CharacterSelectScreen.Group.values().length; t++) {
            l.add(new Step("ffr_select_team" + t, () -> {}, 5));
            l.add(new Step(null, () -> { mc.setScreen(null); com.xenowarriors.client.screen.CharacterSelectScreen.nextPageForTest();
                mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 30));
        }
        l.add(new Step(null, () -> mc.setScreen(null), 5));
    }

    /** Every playable: an attack swing, each skill and the ultimate, a few frames each (from the front). */
    private static void allAnims(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
            if (z == null) return;
            z.moveTo(p.getX(), p.getY(), p.getZ() + 9, 180, 0); z.setNoAi(true); z.setInvulnerable(true); z.setYHeadRot(180); z.yBodyRot = 180;
            p.serverLevel().addFreshEntity(z); p.setYRot(0); p.setXRot(0);
            p.serverLevel().setDayTime(6000);
        }), 20));
        net.minecraft.world.phys.Vec3[] origin = new net.minecraft.world.phys.Vec3[1];
        for (CharacterType t : CharacterType.PLAYABLE) {
            l.add(new Step(null, () -> { character(t); hold(null); server(p -> {
                if (origin[0] == null) origin[0] = p.position();            // everyone filmed on the same spot of the stone stage
                p.teleportTo(origin[0].x, origin[0].y, origin[0].z); p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); }); }, 30));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setYRot(0); mc.player.setXRot(0); server(p -> { p.setYRot(0); p.setXRot(0); com.xenowarriors.karma.Karma.add(p, 500F); }); }, 15));
            l.add(new Step(null, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND), 2));
            for (int f = 0; f < 3; f++) l.add(new Step("allanim_" + t.getId() + "_attack_" + f, () -> {}, 2));
            var pool = com.xenowarriors.skill.SkillPool.of(t);
            java.util.List<com.xenowarriors.skill.Skill> skills = new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(5, pool.size()); i++) skills.add(pool.get(i).skill());
            if (com.xenowarriors.skill.SkillPool.ultimate(t) != null) skills.add(com.xenowarriors.skill.SkillPool.ultimate(t));
            for (com.xenowarriors.skill.Skill sk : skills) {
                l.add(new Step(null, () -> server(p -> { p.setYRot(0); p.setXRot(0); try { sk.use(p); Integer gen = com.xenowarriors.network.SkillAnimPacket.GENERIC.get(sk.getId());   // (the pose plays even if the skill had nothing to hit)
                    if (gen != null) com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.SkillAnimPacket(p.getId(), gen, false), p); } catch (Exception ex) { XenoWarriors.LOGGER.warn("XW-UITEST skill {} failed", sk.getId(), ex); } }), 4));
                for (int f = 0; f < 3; f++) l.add(new Step("allanim_" + t.getId() + "_" + sk.getId() + "_" + f, () -> {}, 5));
                l.add(new Step(null, () -> server(p -> { if (origin[0] != null) { p.teleportTo(origin[0].x, origin[0].y, origin[0].z); p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); } }), 40));   // let it finish, back to the mark
            }
        }
    }

    /** The girls' body on everyone, as a row of bots turned to each side (front, 3/4, side, back). */
    private static void girls(Minecraft mc, List<Step> l) {
        CharacterType[] g = {CharacterType.KAERU, CharacterType.KYRIE, CharacterType.LEXY, CharacterType.TECHNICA, CharacterType.AIONI, CharacterType.YURI};
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        l.add(new Step(null, () -> server(p -> {
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 20 * 600, 0, false, false));
            for (int i = 0; i < g.length; i++) {
                var b = com.xenowarriors.bot.BotEntities.BOT.get().create(p.serverLevel());
                if (b == null) continue;
                b.setCharacter(g[i]); b.setNoAi(true);
                b.moveTo(p.getX() - 5.5 + i * 2.2, p.getY(), p.getZ() + 6, 180, 0);
                b.setYHeadRot(180); b.yBodyRot = 180;
                p.serverLevel().addFreshEntity(b); ids.add(b.getId());
            }
            p.setYRot(0); p.setXRot(12);
        }), 40));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(0); mc.player.setXRot(12); }, 20));
        float[] yaws = {180, 135, 90, 0, 225};
        String[] names = {"front", "threequarter", "side", "back", "threequarter2"};
        for (int k = 0; k < yaws.length; k++) {
            float yaw = yaws[k];
            l.add(new Step(null, () -> server(p -> {
                for (var b : p.serverLevel().getEntitiesOfClass(com.xenowarriors.bot.PartyBotEntity.class, p.getBoundingBox().inflate(16))) {
                    b.setYRot(yaw); b.setYHeadRot(yaw); b.yBodyRot = yaw; b.yBodyRotO = yaw; b.yHeadRotO = yaw;
                }
            }), 15));
            l.add(new Step("girls_" + names[k], () -> {}, 5));
        }
        l.add(new Step(null, () -> server(p -> p.serverLevel().getEntitiesOfClass(com.xenowarriors.bot.PartyBotEntity.class, p.getBoundingBox().inflate(16)).forEach(e -> e.discard())), 5));
    }

    /** Garry's beatbox idle (mic out, bobbing), and the Fox King mask and crown item. */
    private static void beatbox(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { character(CharacterType.GARRY); hold(null); }, 30));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10F); mc.player.setYRot(-35F); mc.player.yBodyRot = -35F; mc.player.setYHeadRot(-35F); }, 150));
        for (int f = 0; f < 16; f++) l.add(new Step(String.format("beatbox_%02d", f), () -> {}, 3));
        l.add(new Step(null, () -> { character(CharacterType.MIKIKO); }, 30));
        l.add(new Step(null, () -> server(p -> { com.xenowarriors.karma.Karma.add(p, 100F); com.xenowarriors.mikiko.FoxKing.toggle(p); }), 40));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(5F); mc.player.setYRot(-70F); mc.player.yBodyRot = -70F; mc.player.setYHeadRot(-70F); }, 20));
        l.add(new Step("beatbox_foxking_side", () -> {}, 5));
        l.add(new Step(null, () -> { mc.player.setYRot(-25F); mc.player.yBodyRot = -25F; mc.player.setYHeadRot(-25F); }, 10));
        l.add(new Step("beatbox_foxking_front", () -> {}, 5));
        l.add(new Step(null, () -> hold(com.xenowarriors.item.ModItems.FOX_KING_CROWN.get()), 20));
        l.add(new Step("beatbox_crown_item", () -> {}, 5));
        // Fox PJs on a few characters (front and back)
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.KYRIE, CharacterType.RHALIX, CharacterType.YURI}) {
            l.add(new Step(null, () -> { hold(null); character(t); server(p -> {
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FOX_PJS_HOOD.get()));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FOX_PJS_TOP.get()));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FOX_PJS_BOTTOMS.get()));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FOX_PJS_SLIPPERS.get())); }); }, 30));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10F); mc.player.setYRot(-25F); mc.player.yBodyRot = -25F; mc.player.setYHeadRot(-25F); }, 15));
            l.add(new Step("foxpjs_" + t.getId() + "_front", () -> {}, 5));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(10F); }, 15));
            l.add(new Step("foxpjs_" + t.getId() + "_back", () -> {}, 5));
        }
        // Flower Armor and the Flower Shield
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.KAERU}) {
            l.add(new Step(null, () -> { character(t); server(p -> {
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FLOWER_HELMET.get()));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FLOWER_CHESTPLATE.get()));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FLOWER_LEGGINGS.get()));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FLOWER_BOOTS.get()));
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.FLOWER_SHIELD.get())); }); }, 30));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10F); }, 15));
            l.add(new Step("flower_" + t.getId() + "_front", () -> {}, 5));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(10F); }, 15));
            l.add(new Step("flower_" + t.getId() + "_back", () -> {}, 5));
        }
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }, 15));
        l.add(new Step("flower_shield_firstperson", () -> {}, 5));
        l.add(new Step(null, () -> { mc.options.hideGui = true; server(p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY)); }, 5));
        l.add(new Step(null, () -> server(p -> { for (var sl : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) p.setItemSlot(sl, net.minecraft.world.item.ItemStack.EMPTY); }), 5));
        l.add(new Step(null, () -> hold(null), 5));
    }

    /** Every character powering up (their DBZ aura) - a still each and GIF frames for a few - Karma focus auras, and element auras on a row of mobs. */
    private static void auras(Minecraft mc, List<Step> l) {
        Runnable front = () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(12F); mc.player.setYRot(0F); };
        l.add(new Step(null, () -> server(p -> p.serverLevel().setDayTime(18000)), 10));       // night shows the glow
        java.util.Set<CharacterType> gif = java.util.Set.of(CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KAERU, CharacterType.ZANE, CharacterType.LOGAN);
        for (CharacterType t : CharacterType.PLAYABLE) {
            l.add(new Step(null, () -> { forceCharge = false; character(t); }, 30));
            l.add(new Step(null, () -> { front.run(); forceCharge = true; }, 35));
            l.add(new Step("aura_" + t.getId(), () -> {}, 3));
            if (gif.contains(t)) for (int f = 0; f < 16; f++) l.add(new Step(String.format("auragif_%s_%02d", t.getId(), f), () -> {}, 1));
        }
        // a daytime one too
        l.add(new Step(null, () -> { forceCharge = false; server(p -> p.serverLevel().setDayTime(6000)); character(CharacterType.MIKIKO); }, 30));
        l.add(new Step(null, () -> { front.run(); forceCharge = true; }, 35));
        for (int f = 0; f < 16; f++) l.add(new Step(String.format("auragif_day_mikiko_%02d", f), () -> {}, 1));
        l.add(new Step(null, () -> { forceCharge = false; server(p -> p.serverLevel().setDayTime(18000)); }, 20));
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.YURI}) {
            l.add(new Step(null, () -> character(t), 30));
            l.add(new Step(null, () -> { front.run(); KarmaFocusClient.set(mc.player.getId(), true); }, 40));
            l.add(new Step("aura_focus_" + t.getId(), () -> {}, 3));
            for (int f = 0; f < 12; f++) l.add(new Step(String.format("auragif_focus_%s_%02d", t.getId(), f), () -> {}, 1));
            l.add(new Step(null, () -> KarmaFocusClient.set(mc.player.getId(), false), 20));
        }
        com.xenowarriors.ka.Element[] els = {com.xenowarriors.ka.Element.FIRE, com.xenowarriors.ka.Element.ICE, com.xenowarriors.ka.Element.LIGHTNING,
                com.xenowarriors.ka.Element.SHADOW, com.xenowarriors.ka.Element.CORRUPTION, com.xenowarriors.ka.Element.VOID};
        l.add(new Step(null, () -> server(p -> {
            for (int i = 0; i < els.length; i++) {
                var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(p.serverLevel());
                if (z == null) continue;
                z.moveTo(p.getX() - 5 + i * 2, p.getY(), p.getZ() + 6, 180, 0); z.setNoAi(true); z.setYHeadRot(180); z.yBodyRot = 180;
                p.serverLevel().addFreshEntity(z);
                com.xenowarriors.ka.Elements.give(z, els[i]);
            }
            p.setYRot(0); p.setXRot(15);
        }), 20));
        l.add(new Step(null, () -> { character(CharacterType.MIKIKO); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(0); mc.player.setXRot(15); }, 60));
        l.add(new Step("aura_elements", () -> {}, 3));
        for (int f = 0; f < 12; f++) l.add(new Step(String.format("auragif_elements_%02d", f), () -> {}, 1));
        l.add(new Step(null, () -> { mc.options.hideGui = false; server(p -> p.serverLevel().setDayTime(6000)); }, 5));
    }

    /** A tour of the Mountain Village (Pine Cone City) in the Recarnet dimension: from the air, the square, the farm, the school, the dojo, the outskirts. */
    private static void village(Minecraft mc, List<Step> l) {
        int vx = com.xenowarriors.recarnet.Recarnet.VILLAGE_X, vz = com.xenowarriors.recarnet.Recarnet.VILLAGE_Z, by = com.xenowarriors.recarnet.Recarnet.VILLAGE_Y;
        // name, x, y, z, yaw, pitch
        Object[][] shots = {
            {"aerial", vx, by + 70, vz + 150, 180F, 28F},
            {"aerial_side", vx + 170, by + 60, vz, 90F, 22F},
            {"square", vx + 1, by + 4, vz + 16, 180F, 8F},
            {"square_well", vx + 6, by + 3, vz + 6, 135F, 18F},
            {"farm", vx + 64, by + 18, vz + 92, 180F, 20F},
            {"farm_fields", vx + 100, by + 12, vz + 40, 90F, 22F},
            {"school", vx, by + 9, vz - 22, 180F, 6F},
            {"school_hall", vx + 4, by + 2, vz - 46, 180F, 5F},
            {"dojo", vx - 18, by + 5, vz + 16, 90F, 8F},
            {"dojo_inside", vx - 40, by + 2, vz + 18, 90F, 8F},
            {"cottages", vx + 40, by + 8, vz - 40, 225F, 14F},
            {"outskirts", vx - 120, by + 14, vz + 60, 300F, 14F},
            {"pine_woods", vx + 175, by + 4, vz + 60, 120F, 2F},
        };
        l.add(new Step(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            var lvl = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
            if (lvl != null) { lvl.setDayTime(5000); p.teleportTo(lvl, vx + 0.5, by + 70, vz + 150.5, 180F, 28F); }
        }), 200));
        for (Object[] sh : shots) {
            double x = ((Integer) sh[1]) + 0.5, y = (Integer) sh[2], z = ((Integer) sh[3]) + 0.5;
            float yaw = (Float) sh[4], pitch = (Float) sh[5];
            l.add(new Step(null, () -> server(p -> {
                var lvl = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
                if (lvl != null) p.teleportTo(lvl, x, y, z, yaw, pitch);
            }), 140));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(yaw); mc.player.setXRot(pitch); }, 20));
            l.add(new Step("village_" + sh[0], () -> {}, 5));
        }
    }

    /** The Party Setup screen: alone, then with three bots (one selected), and an empty slot selected. */
    private static void partyUi(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { character(CharacterType.MIKIKO); server(p -> { p.serverLevel().setDayTime(6000); }); }, 40));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.party.PartySetupScreen()), 40));
        l.add(new Step("partyui_solo", () -> {}, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> {
            for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.KAERU, CharacterType.MAX})
                com.xenowarriors.bot.PartyBots.spawn(p, t, null);
        }); }, 60));
        l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.party.PartySetupScreen()), 60));
        l.add(new Step("partyui_full", () -> {}, 5));
        l.add(new Step(null, () -> { if (mc.screen != null) mc.screen.mouseClicked(mc.getWindow().getGuiScaledWidth() * 0.38, mc.getWindow().getGuiScaledHeight() * 0.5, 0); }, 20));
        l.add(new Step("partyui_selected", () -> {}, 5));
    }

    /** A tour of the Royal Capital (Section 1) high above Aurora. */
    private static void capital(Minecraft mc, List<Step> l) {
        // name, x, y, z, yaw, pitch
        Object[][] shots = {
            {"overview", 0, 300, 230, 180F, 18F},
            {"overview_side", 250, 290, -20, 90F, 14F},
            {"below", 40, 205, 60, 160F, -35F},
            {"landing", 0, 256, 60, 180F, 6F},
            {"checkpoint_guards", 0, 255, 50, 180F, 12F},
            {"palace_front", 0, 262, 40, 180F, 10F},
            {"throne_room", 0, 256, 2, 180F, 4F},
            {"library", 10, 267, -8, 150F, 12F},
            {"courtyard", 18, 258, 32, 135F, 18F},
            {"market", 104, 258, -12, 180F, 22F},
            {"black_market", 112, 239, -6, 180F, 10F},
            {"school", -100, 256, -36, 180F, 8F},
            {"school_class", -107, 252, -54, 180F, 10F},
            {"barracks", -64, 254, 116, 180F, 14F},
            {"noble", 84, 258, 120, 180F, 16F},
            {"commoner", 186, 242, 68, 180F, 18F},
            {"chains", 54, 250, -34, 70F, 14F},
        };
        l.add(new Step(null, () -> server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            var lvl = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
            if (lvl != null) { lvl.setDayTime(5000); p.teleportTo(lvl, 0.5, 300, 230.5, 180F, 18F); }
        }), 240));
        for (Object[] sh : shots) {
            double x = ((Integer) sh[1]) + 0.5, y = (Integer) sh[2], z = ((Integer) sh[3]) + 0.5;
            float yaw = (Float) sh[4], pitch = (Float) sh[5];
            l.add(new Step(null, () -> server(p -> {
                var lvl = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
                if (lvl != null) p.teleportTo(lvl, x, y, z, yaw, pitch);
            }), 140));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(yaw); mc.player.setXRot(pitch); }, 20));
            l.add(new Step("capital_" + sh[0], () -> {}, 5));
        }
    }

    /**
     * HD portrait cards: every playable character in each outfit, in their hero pose, cel-shaded (PortraitScreen).
     * Focus "portraits" does everyone; "portraits-mikiko" (any character id) redoes one after a model update.
     */
    private static void portraits(Minecraft mc, List<Step> l, String only) {
        String one = only.startsWith("-") ? only.substring(1) : "";
        java.util.Map<CharacterType, java.util.function.Supplier<net.minecraft.world.item.Item>> held = new java.util.HashMap<>();
        held.put(CharacterType.LOGAN, () -> com.xenowarriors.item.ModItems.LOGAN_BLADE.get());
        held.put(CharacterType.KEITA, () -> com.xenowarriors.item.ModItems.KEITA_BLADE.get());
        held.put(CharacterType.ZANE, () -> com.xenowarriors.item.ModItems.ZANE_BROOM.get());
        held.put(CharacterType.MAX, () -> com.xenowarriors.item.ModItems.MAX_KNIFE.get());
        held.put(CharacterType.SAM, () -> com.xenowarriors.item.ModItems.SAM_BAT.get());
        held.put(CharacterType.GARRY, () -> com.xenowarriors.item.ModItems.MICROPHONE.get());
        l.add(new Step(null, () -> { mc.options.guiScale().set(2); mc.resizeDisplay(); ClientSettings.setCelShading(true); noToasts(mc);
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }, 20));
        int k = 0;
        for (CharacterType t : CharacterType.PLAYABLE) {
            if (!one.isEmpty() && !one.equals(t.getId())) continue;
            String[] names = t.outfitNames();
            for (int o = 0; o < Math.max(1, names.length); o++) {
                final int oo = o;
                final float yaw = (k++ % 2 == 0) ? -28F : 28F;
                final String on = names.length == 0 ? "Default" : names[o];
                var it = held.get(t);
                l.add(new Step(null, () -> { mc.setScreen(null); character(t); outfit(oo); hold(it == null ? null : it.get()); }, 50));
                l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.portrait.PortraitScreen(t, on, yaw)), 15));
                l.add(new Step("portrait_" + t.getId() + "_" + o, () -> {}, 3));
            }
        }
        l.add(new Step(null, () -> { mc.setScreen(null); ClientSettings.setCelShading(false); }, 5));
    }

    /** The new M main menu with a few characters (its colours follow the character), at a few GUI scales. */
    /** Pose calibration sheets (arm angle conventions), then the battle HUD portrait for characters without drawn art. */
    private static void poseCal(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { noToasts(mc); character(CharacterType.STEVIE); hold(null); }, 50));
        for (float yaw : new float[]{0F, -70F}) {
            l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.portrait.PoseCalScreen(CharacterType.STEVIE, yaw)), 20));
            l.add(new Step("posecal_" + (int) yaw, () -> {}, 3));
        }
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10));
        for (CharacterType t : new CharacterType[]{CharacterType.KYRIE, CharacterType.GARRY, CharacterType.ZANE, CharacterType.LOGAN, CharacterType.MIKIKO}) {
            l.add(new Step(null, () -> { character(t); }, 50));
            l.add(new Step("hud_" + t.getId(), () -> {}, 3));
        }
    }

    /** The left-side pop-up feed: one, then three stacked, then a fourth pushing the oldest out; a pet one with its picture. */
    private static void popups(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { noToasts(mc); character(CharacterType.KYRIE); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> { var fox = net.minecraft.world.entity.EntityType.FOX.create(p.serverLevel());
                if (fox != null) { fox.moveTo(p.getX() + 2, p.getY(), p.getZ() + 2, 0, 0); p.serverLevel().addFreshEntity(fox); com.xenowarriors.fox.FoxPets.tameNow(p, fox); } }); }, 60));
        l.add(new Step(null, () -> PopupFeed.push(com.xenowarriors.network.PopupPacket.DANGER, java.util.UUID.randomUUID(), CharacterType.MIKIKO.ordinal(), -1, "Down!", "Mikiko is down! Get to them.", "exhausted"), 12));
        l.add(new Step("popup_one", () -> {}, 3));
        l.add(new Step(null, () -> PopupFeed.push(com.xenowarriors.network.PopupPacket.REQUEST, java.util.UUID.randomUUID(), CharacterType.RHALIX.ordinal(), -1, "Team attack!", "Rhalix wants Gravity Fox Fire! Ultimate key to join.", "shout"), 10));
        l.add(new Step(null, () -> PopupFeed.push(com.xenowarriors.network.PopupPacket.GOOD, mc.player.getUUID(), CharacterType.KYRIE.ordinal(), -1, "Bot joined", "Kyrie is fighting with you.", "normal"), 12));
        l.add(new Step("popup_three", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> { for (var t : com.xenowarriors.fox.FoxPets.records(p)) { var r = (net.minecraft.nbt.CompoundTag) t;
            if (r.hasUUID("uuid")) com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.PET, r.getUUID("uuid"), 0, 0, "Pet level up", "Fox reached Lv 5. New skin: Ember!", ""); } }), 14));
        l.add(new Step("popup_four", () -> {}, 3));
        l.add(new Step(null, () -> {}, 90));
        l.add(new Step("popup_fading", () -> {}, 3));
        // the newer kinds: boss nearby (with its picture), level up, bounty complete
        l.add(new Step(null, () -> server(p -> {
            var rav = net.minecraft.world.entity.EntityType.RAVAGER.create(p.serverLevel());
            if (rav == null) return;
            rav.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(300);
            rav.setHealth(300); rav.setNoAi(true);
            rav.moveTo(p.getX() + 6, p.getY(), p.getZ() + 10, 180, 0);
            p.serverLevel().addFreshEntity(rav);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }), 50));
        l.add(new Step(null, () -> server(p -> {
            com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), CharacterType.KYRIE.ordinal(), -1, "Level up!", "Character Level 12  +1 max heart", "normal");
            com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), CharacterType.NONE.ordinal(), -1, "Bounty complete", "+1,120 Money - 12 Zombie", "");
        }), 12));
        l.add(new Step("popup_boss_level_bounty", () -> {}, 3));
    }

    /** Mikiko's and Rhalix's Xeno Blast: charge, the blast (from the side), the drained Karma bar, and the menu tab. */
    private static void xenoBlast(Minecraft mc, List<Step> l) {
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX}) {
            l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(t); hold(null); server(p -> {
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.setYRot(0); p.setXRot(0);
                p.getPersistentData().remove("xw_karma_lock_until");
                com.xenowarriors.skill.SkillManager.resetCooldowns(p);
                com.xenowarriors.karma.Karma.add(p, 200);
                for (int k = 0; k < 3; k++) {
                    var h = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
                    if (h == null) continue;
                    h.moveTo(p.getX() - 1 + k, p.getY(), p.getZ() + 8 + k * 3, 180, 0);
                    h.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 400, 10));
                    p.serverLevel().addFreshEntity(h);
                }
            }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 60));
            l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 5));
            l.add(new Step(null, () -> server(p -> com.xenowarriors.skill.SkillManager.useSkill(p, com.xenowarriors.skill.SkillManager.SUPER_SLOT)), 10));
            // the 20-second cutscene, seen through its own camera: intro, circling, close on the hands, the blast, impact, aftermath
            int[] at = {10, 70, 150, 205, 225, 275, 360};
            int prev = 10;
            for (int f = 0; f < at.length; f++) {
                int wait = at[f] - prev; prev = at[f];
                l.add(new Step(null, () -> {}, Math.max(0, wait - 1)));
                l.add(new Step("xeno_" + t.getId() + "_scene" + f, () -> {}, 1));
            }
            l.add(new Step(null, () -> {}, 60));
            l.add(new Step("xeno_" + t.getId() + "_drained", () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3));
            l.add(new Step(null, () -> { com.xenowarriors.client.menu.CharacterCustomScreen.open(null); if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(3); }, 30));
            l.add(new Step("xeno_" + t.getId() + "_tab", () -> {}, 3));
            l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(1); }, 10));
            l.add(new Step("xeno_" + t.getId() + "_skills", () -> {}, 3));
            l.add(new Step(null, () -> mc.setScreen(new com.xenowarriors.client.SkillLoadoutScreen(null)), 15));
            l.add(new Step("xeno_" + t.getId() + "_loadout", () -> {}, 3));
            l.add(new Step(null, () -> { mc.setScreen(null); server(p -> p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(40)).forEach(e -> e.discard())); }, 10));
        }
        // the beam clash look (a real clash needs two players; here the solid visuals are faked client-side against a husk)
        int[] ids = {-1, -1};
        l.add(new Step(null, () -> { character(CharacterType.MIKIKO); server(p -> {
            p.setYRot(0); p.setXRot(0);
            var h = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
            h.moveTo(p.getX(), p.getY(), p.getZ() + 10, 180, 0); h.setNoAi(true); h.setYHeadRot(180); h.setYBodyRot(180);
            p.serverLevel().addFreshEntity(h); ids[0] = h.getId();
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + 9, p.getY() + 0.6, p.getZ() + 5, 90F, 8F); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(90F);
            p.serverLevel().addFreshEntity(a); ids[1] = a.getId();
        }); }, 40));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(ids[1]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(90F); a.yRotO = 90F; a.setXRot(8); a.xRotO = 8; a.setYHeadRot(90F); a.yHeadRotO = 90F; mc.setCameraEntity(a); }
            mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setYBodyRot(0); mc.player.setYHeadRot(0);
            com.xenowarriors.client.KiFx.receive(new com.xenowarriors.network.XenoFxPacket(mc.player.getId(), com.xenowarriors.client.KiFx.CLASH, ids[0], false, 0, 0, 0, 1, 0, 0, 0.5F, 400)); }, 12));
        l.add(new Step("xeno_clash_0", () -> {}, 1));
        l.add(new Step(null, () -> com.xenowarriors.client.KiFx.receive(new com.xenowarriors.network.XenoFxPacket(mc.player.getId(), com.xenowarriors.client.KiFx.CLASH, ids[0], false, 0, 0, 0, 1, 0, 0, 0.72F, 400)), 6));
        l.add(new Step("xeno_clash_1", () -> {}, 1));
        l.add(new Step(null, () -> com.xenowarriors.client.KiFx.receive(new com.xenowarriors.network.XenoFxPacket(mc.player.getId(), com.xenowarriors.client.KiFx.CLASH, ids[0], false, 0, 0, 0, 1, 0, 0, 0.3F, 400)), 6));
        l.add(new Step("xeno_clash_2", () -> {}, 1));
        l.add(new Step(null, () -> { com.xenowarriors.client.KiFx.receive(new com.xenowarriors.network.XenoFxPacket(mc.player.getId(), com.xenowarriors.client.KiFx.STOP, 0, false, 0, 0, 0, 0, 0, 0, 0, 0));
            com.xenowarriors.client.KiFx.receive(new com.xenowarriors.network.XenoFxPacket(mc.player.getId(), com.xenowarriors.client.KiFx.BURST, 0, false,
                    (float) mc.player.getX(), (float) mc.player.getY() + 1.3F, (float) mc.player.getZ() + 5, 0, 0, 0, 4F, 20)); }, 6));
        l.add(new Step("xeno_clash_3", () -> {}, 1));
        l.add(new Step(null, () -> mc.setCameraEntity(mc.player), 5));
    }

    /** Character Customization's animations: frames of it opening, then of switching tabs. */
    private static void customAnim(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); hold(null); }, 60));
        l.add(new Step(null, () -> com.xenowarriors.client.menu.CharacterCustomScreen.open(null), 1));
        for (int f = 0; f < 5; f++) l.add(new Step("customanim_open_" + f, () -> {}, 2));
        l.add(new Step(null, () -> {}, 20));
        for (int tb : new int[]{1, 2, 3, 0}) {
            l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(tb); }, 0));
            for (int f = 0; f < 4; f++) l.add(new Step("customanim_tab" + tb + "_" + f, () -> {}, 3));
            l.add(new Step(null, () -> {}, 10));
        }
    }

    /**
     * Xeno Blast with a second player (a stand-in FakePlayer, like the party bots use): fighting back with a projectile
     * skill, Ultima mashing to cancel it, and a full beam clash. Results go to the log as "XW-TEST".
     */
    /** Tower mode: the Collection screen, arriving on floor 1, a wave from above and up close, floor cleared, floor 2. */
    private static void tower(Minecraft mc, List<Step> l) {
        int[] ids = {-1};
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); hold(null); server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var t = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            t.putBoolean(com.xenowarriors.collection.Collection.SEEN, true);
            p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, t);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
            com.xenowarriors.collection.Collection.open(p, true);
        }); mc.options.hideGui = false; }, 40));
        l.add(new Step("tower_collection", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> com.xenowarriors.tower.Tower.enter(p)); }, 30));
        l.add(new Step("tower_arrive", () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 3));
        l.add(new Step(null, () -> server(p -> {
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX(), p.getY() + 13, p.getZ() - 17, 0F, 35F); a.setNoGravity(true); a.setInvisible(true);
            p.serverLevel().addFreshEntity(a); ids[0] = a.getId();
        }), 90));
        l.add(new Step("tower_wave", () -> {}, 3));
        l.add(new Step(null, () -> { mc.options.hideGui = true;
            if (mc.level.getEntity(ids[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(0); a.yRotO = 0; a.setXRot(35); a.xRotO = 35; a.setYHeadRot(0); a.yHeadRotO = 0; mc.setCameraEntity(a); } }, 10));
        l.add(new Step("tower_overview", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 15));
        l.add(new Step("tower_fight", () -> {}, 3));
        // clear the floor (every wave)
        for (int w = 0; w < 6; w++) l.add(new Step(null, () -> server(p -> p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40)).forEach(m -> m.kill())), 60));
        l.add(new Step("tower_cleared", () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 3));
        l.add(new Step(null, () -> {}, 120));
        l.add(new Step("tower_floor2", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.tower.Tower.leave(p, "You left the Tower")), 20));
    }

    /** Epic Fight calibration: our test animation, one joint/axis per second, photographed from the side at each peak. */
    private static void efCal(Minecraft mc, List<Step> l) {
        int[] ids = {-1};
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); hold(null); server(p -> {
            p.setYRot(0); p.setXRot(0);
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + 2.3, p.getY() - 0.2, p.getZ() + 2.5, 135F, 5F); a.setNoGravity(true); a.setInvisible(true);
            p.serverLevel().addFreshEntity(a); ids[0] = a.getId();
            com.xenowarriors.compat.epicfight.EfTest.play(p, "none");
            log("epicfight loaded=" + net.minecraftforge.fml.ModList.get().isLoaded("epicfight"));
        }); }, 60));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(ids[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(135F); a.yRotO = 135F; a.setXRot(5); a.xRotO = 5; a.setYHeadRot(135F); a.yHeadRotO = 135F; mc.setCameraEntity(a); }
            mc.player.setYBodyRot(0); mc.player.setYHeadRot(0); mc.player.setYRot(0);
            server(p -> com.xenowarriors.compat.epicfight.EfTest.play(p, "cal")); }, 10));      // the half-second peak of segment 0
        for (int i = 0; i < 10; i++) l.add(new Step("efcal_" + i, () -> {}, 20));
        // then our real attacks, a few frames each
        for (String a : new String[]{"pole1", "pole2", "pole3", "cleave1", "cleave2"}) {
            l.add(new Step(null, () -> server(p -> { com.xenowarriors.compat.epicfight.EfTest.play(p, a); log("ef " + a + " at " + p.position()); }), 3));
            for (int f = 0; f < 4; f++) l.add(new Step("ef_" + a + "_" + f, () -> {}, 4));
            l.add(new Step(null, () -> {}, 15));
        }
        l.add(new Step(null, () -> mc.setCameraEntity(mc.player), 5));
    }

    /** Townsfolk builds: hut, fenced wheat farm round a water hole, well and lamp post (placed at once for the picture). */
    private static void townsfolk(Minecraft mc, List<Step> l) {
        int[] ids = {-1};
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); server(p -> {
            var lv = p.serverLevel();
            net.minecraft.core.BlockPos base = p.blockPosition().offset(0, 0, 12);
            for (int dx = -14; dx <= 14; dx++) for (int dz = -8; dz <= 8; dz++) {
                lv.setBlock(base.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int y = 0; y < 6; y++) lv.setBlock(base.offset(dx, y, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            com.xenowarriors.npc.HumanWorkGoal.testPlace(lv, base.offset(-7, 0, 0), "hut");
            com.xenowarriors.npc.HumanWorkGoal.testPlace(lv, base.offset(3, 0, 0), "farm");
            com.xenowarriors.npc.HumanWorkGoal.testPlace(lv, base.offset(9, 0, -4), "well");
            com.xenowarriors.npc.HumanWorkGoal.testPlace(lv, base.offset(-2, 0, -5), "lamp");
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(lv);
            a.moveTo(p.getX(), p.getY() + 7, p.getZ() - 2, 0F, 30F); a.setNoGravity(true); a.setInvisible(true);
            lv.addFreshEntity(a); ids[0] = a.getId();
            lv.setDayTime(6000);
        }); }, 50));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(ids[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(0); a.yRotO = 0; a.setXRot(30); a.xRotO = 30; a.setYHeadRot(0); a.yHeadRotO = 0; mc.setCameraEntity(a); } }, 20));
        l.add(new Step("townsfolk_builds", () -> {}, 3));
        l.add(new Step(null, () -> mc.setCameraEntity(mc.player), 5));
    }

    /** DragonMineZ outfits on Mikiko with their hand-made 3D details (capes, Saiyan flaps, sashes, scouter, earrings). */
    private static void dmzOutfits(Minecraft mc, List<Step> l) {
        int[] ids = {-1};
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); hold(null); server(p -> {
            p.setYRot(180); p.setXRot(0);
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + 1.2, p.getY() + 0.1, p.getZ() - 3.2, 0F, 0F); a.setNoGravity(true); a.setInvisible(true);
            p.serverLevel().addFreshEntity(a); ids[0] = a.getId();
        }); }, 50));
        String[] outfits = {"goku", "piccolo", "vegeta_saiyan", "king_vegeta", "vegetto", "bardock_dbz", "great_saiyaman_2", "raditz"};
        for (String o : outfits) {
            l.add(new Step(null, () -> server(p -> {
                for (String part : new String[]{"chestplate", "leggings", "boots"}) {
                    var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", o + "_armor_" + part));
                    var slot = part.equals("chestplate") ? net.minecraft.world.entity.EquipmentSlot.CHEST : part.equals("leggings") ? net.minecraft.world.entity.EquipmentSlot.LEGS : net.minecraft.world.entity.EquipmentSlot.FEET;
                    p.setItemSlot(slot, item == null || item == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(item));
                }
                p.setYRot(180); p.setYHeadRot(180); p.setYBodyRot(180);
                log("dmz outfit " + o + ": " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()));
            }), 10));
            l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (mc.level.getEntity(ids[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                    a.setYRot(-20F); a.yRotO = -20F; a.setXRot(5); a.xRotO = 5; a.setYHeadRot(-20F); a.yHeadRotO = -20F; mc.setCameraEntity(a); }
                mc.setScreen(null);
                mc.player.setYBodyRot(160); mc.player.yBodyRotO = 160; mc.player.setYHeadRot(160); }, 10));
            l.add(new Step("dmzoutfit_" + o, () -> mc.setScreen(null), 3));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.player.setYBodyRot(-20); mc.player.yBodyRotO = -20; mc.player.setYHeadRot(-20); mc.player.setYRot(-20); }, 6));
            l.add(new Step("dmzoutfit_" + o + "_back", () -> {}, 3));
        }
        l.add(new Step(null, () -> mc.setCameraEntity(mc.player), 5));
    }

    /** With DragonMineZ installed (CI downloads it for this focus): the main menu shows its entry, and the entry opens DMZ's menu. */
    private static void dmzMenu(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); log("dmz loaded=" + com.xenowarriors.client.compat.DmzMenu.available()); }, 60));
        l.add(new Step(null, () -> { PlayerStatsScreen.open(); if (mc.screen instanceof com.xenowarriors.client.menu.MainMenuScreen m) m.select(5); }, 40));
        l.add(new Step("dmzmenu_main", () -> {}, 3));
        l.add(new Step(null, () -> { if (mc.screen instanceof com.xenowarriors.client.menu.MainMenuScreen m) { m.select(5); m.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0); }
            log("after DMZ entry: screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName())); }, 30));
        l.add(new Step("dmzmenu_opened", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); com.xenowarriors.client.compat.DmzMenu.open(); log("direct open: screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName())); }, 30));
        l.add(new Step("dmzmenu_direct", () -> {}, 3));
    }

    /** With TACZ installed (CI downloads it for this focus): Government guards carry TACZ guns and shoot when you're wanted. */
    private static void taczGuard(Minecraft mc, List<Step> l) {
        int[] ids = {-1};
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); server(p -> {
            log("tacz available=" + com.xenowarriors.compat.TaczGuns.available());
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setYRot(0); p.setXRot(5);
            for (int k = 0; k < 6; k++) {
                var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel());
                if (g == null) continue;
                g.moveTo(p.getX() - 4 + k * 1.6, p.getY(), p.getZ() + 7, 180, 0);
                g.finalizeSpawn(p.serverLevel(), p.serverLevel().getCurrentDifficultyAt(p.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
                g.setup(true, k % 2, p.blockPosition());
                p.serverLevel().addFreshEntity(g);
            }
        }); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 60));
        l.add(new Step(null, () -> server(p -> {
            var gs = p.serverLevel().getEntitiesOfClass(com.xenowarriors.gov.GovAgentEntity.class, p.getBoundingBox().inflate(16));
            int guns = 0; for (var g : gs) if (com.xenowarriors.compat.TaczGuns.isGun(g.getMainHandItem())) guns++;
            StringBuilder sb = new StringBuilder(); for (var g : gs) sb.append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(g.getMainHandItem().getItem())).append(' ');
            log("guards=" + gs.size() + " with TACZ guns=" + guns + " held: " + sb);
        }), 20));
        l.add(new Step("taczguard_armed", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.gov.GovFacilities.makeWanted(p, "were spotted")), 20));
        for (int sec = 1; sec <= 8; sec++) { int ss = sec;
            l.add(new Step(sec == 3 ? "taczguard_shooting" : null, () -> server(p -> log("health " + ss + "s after becoming wanted: " + p.getHealth())), 20)); }
    }

    private static final java.util.Map<String, ServerPlayer> FAKES = new java.util.HashMap<>();
    private static ServerPlayer fake(ServerPlayer near, String name, CharacterType t) {
        ServerPlayer f = net.minecraftforge.common.util.FakePlayerFactory.get(near.serverLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes()), name));
        f.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setType(t));
        f.moveTo(near.getX(), near.getY(), near.getZ() + 8, 180F, 0F);
        com.xenowarriors.karma.Karma.add(f, 200);
        f.getPersistentData().remove("xw_karma_lock_until");
        FAKES.put(name, f);
        return f;
    }
    private static void log(String s) { com.xenowarriors.XenoWarriors.LOGGER.info("XW-TEST " + s); }

    private static void xenoTest(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); hold(null); server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setYRot(0); p.setXRot(0);
            fake(p, "FriendMikiko", CharacterType.MIKIKO); fake(p, "FriendUltima", CharacterType.ULTIMA); fake(p, "FriendRhalix", CharacterType.RHALIX);
        }); }, 40));
        // 1) fight back with a projectile skill, then Ultima pushes it back
        l.add(new Step(null, () -> server(p -> {
            p.getPersistentData().remove("xw_karma_lock_until"); com.xenowarriors.skill.SkillManager.resetCooldowns(p); com.xenowarriors.karma.Karma.add(p, 200);
            com.xenowarriors.skill.SkillManager.useSkill(p, com.xenowarriors.skill.SkillManager.SUPER_SLOT);
            com.xenowarriors.skill.XenoBlast.testJoin(p, FAKES.get("FriendMikiko")); com.xenowarriors.skill.XenoBlast.testJoin(p, FAKES.get("FriendUltima"));
            for (int slot = 0; slot < 4; slot++) com.xenowarriors.skill.XenoBlast.counter(FAKES.get("FriendMikiko"), p.getId(), slot);
            log("after projectile counters: " + com.xenowarriors.skill.XenoBlast.testState(p));
        }), 40));
        l.add(new Step("xenotest_counter", () -> {}, 2));
        l.add(new Step(null, () -> server(p -> {
            for (int i = 0; i < 50; i++) com.xenowarriors.skill.XenoBlast.mash(FAKES.get("FriendUltima"), p.getId());
            log("after Ultima mashing: " + com.xenowarriors.skill.XenoBlast.testState(p) + " casting=" + com.xenowarriors.skill.XenoBlast.casting(p));
        }), 10));
        l.add(new Step("xenotest_pushed_back", () -> {}, 40));
        // 2) a beam clash: Rhalix fires one back, Mikiko (the caster) out-mashes him
        l.add(new Step(null, () -> server(p -> {
            p.getPersistentData().remove("xw_karma_lock_until"); com.xenowarriors.skill.SkillManager.resetCooldowns(p); com.xenowarriors.karma.Karma.add(p, 200);
            com.xenowarriors.skill.SkillManager.useSkill(p, com.xenowarriors.skill.SkillManager.SUPER_SLOT);
            com.xenowarriors.skill.XenoBlast.testJoin(p, FAKES.get("FriendRhalix"));
        }), 20));
        l.add(new Step(null, () -> server(p -> {
            com.xenowarriors.skill.XenoBlast.counter(FAKES.get("FriendRhalix"), p.getId(), com.xenowarriors.skill.SkillManager.SUPER_SLOT);
            log("after Rhalix fired back: " + com.xenowarriors.skill.XenoBlast.testState(p) + " rhalixKarma=" + com.xenowarriors.karma.Karma.get(FAKES.get("FriendRhalix")));
        }), 185));
        l.add(new Step("xenotest_clash_start", () -> {}, 1));
        for (int i = 0; i < 40; i++) {
            int ii = i;
            l.add(new Step(ii == 20 ? "xenotest_clash_mid" : null, () -> server(p -> {
                com.xenowarriors.skill.XenoBlast.mash(p, p.getId()); com.xenowarriors.skill.XenoBlast.mash(p, p.getId());
                if (ii % 3 == 0) com.xenowarriors.skill.XenoBlast.mash(FAKES.get("FriendRhalix"), p.getId());
                if (ii % 10 == 0) log("clash tick " + ii + ": " + com.xenowarriors.skill.XenoBlast.testState(p));
            }), 2));
        }
        l.add(new Step("xenotest_clash_end", () -> server(p -> log("after clash: " + com.xenowarriors.skill.XenoBlast.testState(p))), 30));
    }

    /** Government shop and loot: the terminal (as Ultima, with the Gov discount), Mikiko refused, the vault opened with a keycard. */
    private static void govShop(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.ULTIMA); server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setYRot(0); p.setXRot(20);
            com.xenowarriors.economy.Money.set(p, 3000);
            var at = p.blockPosition().offset(0, 0, 3);
            p.serverLevel().setBlock(at, com.xenowarriors.gov.GovItems.TERMINAL.get().defaultBlockState()
                    .setValue(com.xenowarriors.gov.GovTerminalBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
            p.serverLevel().setBlock(at.offset(2, 0, 0), com.xenowarriors.gov.GovItems.VAULT.get().defaultBlockState()
                    .setValue(com.xenowarriors.gov.GovVaultBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
        }); mc.options.hideGui = false; }, 50));
        l.add(new Step("govshop_blocks", () -> mc.options.setCameraType(CameraType.FIRST_PERSON), 10));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.gov.GovShop.open(p)), 15));
        l.add(new Step("govshop_ultima", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.gov.GovShop.buy(p, 3)), 15));
        l.add(new Step("govshop_bought", () -> {}, 3));
        l.add(new Step(null, () -> { mc.setScreen(null); character(CharacterType.MIKIKO); }, 30));
        l.add(new Step(null, () -> server(p -> { p.getPersistentData().remove("xw_gov_wanted_until"); com.xenowarriors.gov.GovShop.open(p); }), 10));
        l.add(new Step("govshop_mikiko_refused", () -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("")), 8));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> {
            p.getPersistentData().remove("xw_gov_wanted_until");
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.xenowarriors.gov.GovItems.KEYCARD.get()));
            var at = p.blockPosition().offset(2, 0, 3);
            var st = p.serverLevel().getBlockState(at);
            st.use(p.serverLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.NORTH, at, false));
        }); }, 25));
        l.add(new Step("govshop_vault_open", () -> {}, 3));
    }

    /** One character per player: the select screen with other players' characters marked TAKEN (faked client-side). */
    private static void charLock(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); }, 40));
        String[] fake = new String[CharacterType.values().length];
        java.util.Arrays.fill(fake, "");
        fake[CharacterType.RHALIX.ordinal()] = "Friend123";
        fake[CharacterType.ULTIMA.ordinal()] = "Sam_YT";
        for (boolean free : new boolean[]{true, false}) {
            l.add(new Step(null, () -> { com.xenowarriors.client.screen.CharacterSelectScreen.focusForTest(CharacterType.RHALIX);
                mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 20));
            l.add(new Step(null, () -> { com.xenowarriors.network.TakenCharsPacket.CLIENT = fake; com.xenowarriors.network.TakenCharsPacket.CLIENT_ANY_FREE = free;
                com.xenowarriors.client.screen.CharacterSelectScreen.focusForTest(CharacterType.RHALIX);
                if (mc.screen != null) mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()); }, 10));
            l.add(new Step("charlock_" + (free ? "taken" : "allfull"), () -> {}, 3));
            l.add(new Step(null, () -> mc.setScreen(null), 5));
        }
    }

    /** Kitsune Attack on a husk: the dazed stun with dizzy stars (side camera). */
    private static void kitsuneStun(Minecraft mc, List<Step> l) {
        int[] ids = {-1, -1};
        l.add(new Step(null, () -> { noToasts(mc); mc.setScreen(null); character(CharacterType.MIKIKO); hold(null); server(p -> {
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setYRot(0); p.setXRot(10);
            com.xenowarriors.karma.Karma.add(p, 200);
            var h = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());
            h.moveTo(p.getX(), p.getY(), p.getZ() + 5, 180, 0); h.setNoAi(true);
            h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400); h.setHealth(400);
            p.serverLevel().addFreshEntity(h); ids[0] = h.getId();
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + 6, p.getY() - 0.3, p.getZ() + 5, 90F, 6F); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(90F);
            p.serverLevel().addFreshEntity(a); ids[1] = a.getId();
        }); }, 50));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (mc.level.getEntity(ids[1]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(90F); a.yRotO = 90F; a.setXRot(6); a.xRotO = 6; a.setYHeadRot(90F); a.yHeadRotO = 90F; mc.setCameraEntity(a); } }, 10));
        l.add(new Step(null, () -> server(p -> {
            int slot = -1; int[] lo = com.xenowarriors.client.ClientSkillState.loadout();
            com.xenowarriors.skill.SkillManager.useSkill(p, 1);      // Kitsune Attack sits in slot 2 by default
        }), 10));
        for (int f = 0; f < 5; f++) l.add(new Step("kitsune_stun_" + f, () -> {}, 8));
        l.add(new Step(null, () -> mc.setCameraEntity(mc.player), 10));
    }

    private static void noToasts(Minecraft mc) {
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        mc.getToasts().clear();
    }

    private static void mainMenu(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> noToasts(mc), 5));
        Object[][] who = {{CharacterType.MIKIKO, 0, 0}, {CharacterType.RHALIX, 0, 3}, {CharacterType.KYRIE, 0, 5}, {CharacterType.GARRY, 0, 1},
                {CharacterType.ZANE, 0, 6}, {CharacterType.SAM, 2, 2}, {CharacterType.KAERU, 0, 4}, {CharacterType.AIONI, 0, 0}};
        for (int gs : new int[]{0, 3}) {
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.guiScale().set(gs); mc.resizeDisplay(); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10));
            for (Object[] w : who) {
                CharacterType t = (CharacterType) w[0];
                int o = (Integer) w[1], sel = (Integer) w[2];
                if (gs == 3 && t != CharacterType.MIKIKO && t != CharacterType.KYRIE) continue;
                l.add(new Step(null, () -> { mc.setScreen(null); character(t); outfit(o); hold(null); }, 50));
                l.add(new Step(null, () -> { PlayerStatsScreen.open(); if (mc.screen instanceof com.xenowarriors.client.menu.MainMenuScreen m) m.select(sel); }, 40));
                l.add(new Step("mainmenu_" + t.getId() + (gs == 3 ? "_gs3" : ""), () -> {}, 3));
            }
        }
        // the Pets menu (Mikiko with a fox that ran away)
        l.add(new Step(null, () -> { mc.setScreen(null); character(CharacterType.MIKIKO); server(p -> {
            for (int k = 0; k < 2; k++) {
                var fox = net.minecraft.world.entity.EntityType.FOX.create(p.serverLevel());
                if (fox == null) continue;
                fox.moveTo(p.getX() + 2 + k, p.getY(), p.getZ() + 1, 0, 0);
                p.serverLevel().addFreshEntity(fox);
                com.xenowarriors.fox.FoxPets.tameNow(p, fox);
                if (k == 1) fox.kill();                      // this one runs away: shows the cool-down
            }
        }); }, 60));
        l.add(new Step(null, () -> com.xenowarriors.client.menu.PetsScreen.open(null), 40));
        l.add(new Step("pets_menu", () -> {}, 3));
        // Character Customization: each tab, for a few characters
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.MAX}) {
            l.add(new Step(null, () -> { mc.setScreen(null); character(t); outfit(0); hold(null); }, 50));
            for (int tb = 0; tb < 4; tb++) {
                int tt = tb;
                l.add(new Step(null, () -> { if (!(mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen)) com.xenowarriors.client.menu.CharacterCustomScreen.open(null);
                    if (mc.screen instanceof com.xenowarriors.client.menu.CharacterCustomScreen c) c.tab(tt); }, 30));
                l.add(new Step("custom_" + t.getId() + "_tab" + tb, () -> {}, 3));
            }
        }
        l.add(new Step(null, () -> mc.setScreen(null), 5));
    }

    /** The ground under the Royal Capital (should be lit, not in a dark shadow), by day and by night. */
    private static void underlight(Minecraft mc, List<Step> l) {
        int[][] spots = {{6, 6, 160}, {30, -20, 220}, {100, -30, 180}, {-60, 90, 200}};
        for (long time : new long[]{5000L, 18000L}) for (int[] sp : spots) {
            l.add(new Step(null, () -> server(p -> {
                p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                var lvl = p.server.getLevel(com.xenowarriors.recarnet.Recarnet.LEVEL);
                if (lvl == null) return;
                lvl.setDayTime(time);
                p.teleportTo(lvl, sp[0] + 0.5, 150, sp[1] + 0.5, sp[2], 15F);
            }), 120));
            l.add(new Step(null, () -> server(p -> {
                var lvl = p.serverLevel();
                var m = new net.minecraft.core.BlockPos.MutableBlockPos(sp[0], 199, sp[1]);
                while (m.getY() > lvl.getMinBuildHeight() && (lvl.getBlockState(m).isAir() || lvl.getBlockState(m).is(net.minecraft.world.level.block.Blocks.LIGHT))) m.move(0, -1, 0);
                p.teleportTo(lvl, sp[0] + 0.5, m.getY() + 3.0, sp[1] + 0.5, sp[2], 15F);
            }), 140));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(sp[2]); mc.player.setXRot(15F); }, 20));
            l.add(new Step("under_" + (time > 12000 ? "night_" : "day_") + sp[0] + "_" + sp[1], () -> {}, 5));
        }
    }

    /** The new ores in a row (each with its gem floating above it), then the Fox PJs on a few characters. */
    private static void oresAndPjs(Minecraft mc, List<Step> l) {
        net.minecraft.world.level.block.Block[] ores = {com.xenowarriors.sky.ModBlocks.FOX_SHARD_ORE.get(), com.xenowarriors.sky.ModBlocks.DEEPSLATE_FOX_SHARD_ORE.get(),
                com.xenowarriors.sky.ModBlocks.MOONSTONE_ORE.get(), com.xenowarriors.sky.ModBlocks.KARMA_CRYSTAL_ORE.get(), com.xenowarriors.sky.ModBlocks.DEMONITE_ORE.get(),
                com.xenowarriors.sky.ModBlocks.OLD_WORLD_ORE.get(), com.xenowarriors.sky.ModBlocks.AURORA_GEM_ORE.get()};
        net.minecraft.world.item.Item[] gems = {com.xenowarriors.item.ModItems.FOX_SHARD.get(), com.xenowarriors.item.ModItems.FOX_SHARD.get(),
                com.xenowarriors.item.ModItems.MOONSTONE.get(), com.xenowarriors.item.ModItems.KARMA_CRYSTAL.get(), com.xenowarriors.item.ModItems.DEMONITE_SHARD.get(),
                com.xenowarriors.item.ModItems.OLD_WORLD_FRAGMENT.get(), com.xenowarriors.item.ModItems.AURORA_GEM.get()};
        l.add(new Step(null, () -> character(CharacterType.MIKIKO), 30));
        l.add(new Step(null, () -> server(p -> {
            p.serverLevel().setDayTime(6000);
            net.minecraft.core.BlockPos base = p.blockPosition().offset(-3, 0, 4);
            for (int i = 0; i < ores.length; i++) {
                net.minecraft.core.BlockPos at = base.offset(i, 0, 0);
                p.serverLevel().setBlock(at, ores[i].defaultBlockState(), 3);
                p.serverLevel().setBlock(at.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                var it = new net.minecraft.world.entity.item.ItemEntity(p.serverLevel(), at.getX() + 0.5, at.getY() + 1.3, at.getZ() + 0.5,
                        new net.minecraft.world.item.ItemStack(gems[i]), 0, 0, 0);
                it.setNoGravity(true); it.setNeverPickUp(); it.setUnlimitedLifetime();
                p.serverLevel().addFreshEntity(it);
            }
            p.setYRot(0); p.setXRot(20);
        }), 30));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setYRot(0); mc.player.setXRot(20); server(p -> p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 400, 0, false, false))); }, 30));
        l.add(new Step("ores_row", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> { p.teleportTo(p.getX() + 0.0, p.getY(), p.getZ() + 2.2); p.setYRot(0); p.setXRot(30); }), 20));
        l.add(new Step(null, () -> { mc.player.setYRot(0); mc.player.setXRot(30); }, 10));
        l.add(new Step("ores_close", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> { p.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY); p.teleportTo(p.getX(), p.getY(), p.getZ() - 6); }), 20));
        // the Fox PJs (+5% speed a piece), over everyone's outfit
        net.minecraft.world.item.Item[] pjs = {com.xenowarriors.item.ModItems.FOX_PJS_HOOD.get(), com.xenowarriors.item.ModItems.FOX_PJS_TOP.get(),
                com.xenowarriors.item.ModItems.FOX_PJS_BOTTOMS.get(), com.xenowarriors.item.ModItems.FOX_PJS_SLIPPERS.get()};
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.KAERU, CharacterType.RHALIX, CharacterType.MAX}) {
            l.add(new Step(null, () -> { character(t); hold(null); server(p -> { for (int i = 0; i < 4; i++) p.setItemSlot(slots[i], new net.minecraft.world.item.ItemStack(pjs[i])); }); }, 30));
            float[] yaws = {0F, -45F, 0F};
            String[] names = {"front", "threequarter", "back"};
            for (int k = 0; k < 3; k++) {
                float yaw = yaws[k];
                CameraType cam = k == 2 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT;
                l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(cam);
                    mc.player.setXRot(8F); mc.player.setYRot(yaw); mc.player.yBodyRot = yaw; mc.player.yBodyRotO = yaw; mc.player.setYHeadRot(yaw); }, 15));
                l.add(new Step("foxpjs_" + t.getId() + "_" + names[k], () -> {}, 5));
            }
            l.add(new Step(null, () -> server(p -> { for (var sl : slots) p.setItemSlot(sl, net.minecraft.world.item.ItemStack.EMPTY); }), 10));
        }
    }

    /** Max's and Sam's season 3 outfits (with his knife and his bat): front, three-quarter, side, back. */
    private static void s3Outfits(Minecraft mc, List<Step> l) {
        Object[][] who = {{CharacterType.MAX, com.xenowarriors.item.ModItems.MAX_KNIFE.get()}, {CharacterType.SAM, com.xenowarriors.item.ModItems.SAM_BAT.get()}};
        for (Object[] w : who) {
            CharacterType t = (CharacterType) w[0];
            net.minecraft.world.item.Item item = (net.minecraft.world.item.Item) w[1];
            l.add(new Step(null, () -> { character(t); hold(item); }, 30));
            l.add(new Step(null, () -> outfit(2), 30));
            float[] yaws = {0F, -35F, -80F, 0F};
            String[] names = {"front", "threequarter", "side", "back"};
            for (int k = 0; k < yaws.length; k++) {
                float yaw = yaws[k];
                CameraType cam = k == 3 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT;
                l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(cam);
                    mc.player.setXRot(8F); mc.player.setYRot(yaw); mc.player.yBodyRot = yaw; mc.player.yBodyRotO = yaw; mc.player.setYHeadRot(yaw); }, 15));
                l.add(new Step("s3fit_" + t.getId() + "_" + names[k], () -> {}, 5));
            }
            l.add(new Step(null, () -> outfit(0), 10));
        }
    }

    /** Mikiko's bare-hand combo string on a husk, filmed from the side (ends in Tie Shan Kao) - then the wild version at low health. */
    private static void mikikoCombo(Minecraft mc, List<Step> l) {
        comboReel(mc, l, "side", CharacterType.MIKIKO, null, false, false, 44);
        comboReel(mc, l, "wild", CharacterType.MIKIKO, null, true, false, 44);
    }

    /** The universal combos: a string for each kind of weapon, the heavy, and the clicks being spammed (they queue). */
    private static void combos(Minecraft mc, List<Step> l) {
        comboReel(mc, l, "fist_garry", CharacterType.GARRY, null, false, false, 34);
        comboReel(mc, l, "sword_logan", CharacterType.LOGAN, net.minecraft.world.item.Items.IRON_SWORD, false, false, 34);
        comboReel(mc, l, "axe_max", CharacterType.MAX, net.minecraft.world.item.Items.IRON_AXE, false, false, 34);
        comboReel(mc, l, "spear_kill", CharacterType.KILL, net.minecraft.world.item.Items.TRIDENT, false, false, 34);
        comboReel(mc, l, "heavy_rhalix", CharacterType.RHALIX, net.minecraft.world.item.Items.IRON_SWORD, false, true, 16);
    }

    /**
     * Films a combo on a husk from the side: clicks every 3 ticks (spamming - the combo queues them at its own pace),
     * a frame every 2 ticks. The husk stands its ground until the finisher sends it flying.
     */
    private static final net.minecraft.world.phys.Vec3[] REEL_ORIGIN = new net.minecraft.world.phys.Vec3[1];

    private static void comboReel(Minecraft mc, List<Step> l, String tag, CharacterType who, net.minecraft.world.item.Item item,
                                  boolean wild, boolean heavy, int frames) {
        int[] ids = new int[2];
        float camYaw = -90F;
        l.add(new Step(null, () -> { character(who); hold(item); }, 30));
        l.add(new Step(null, () -> server(p -> {
            p.setHealth(wild ? 7F : p.getMaxHealth());                // under half health: Mikiko's wild version
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, p.getBoundingBox().inflate(30)).forEach(e -> e.discard());
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(30)).forEach(e -> e.discard());
            if (REEL_ORIGIN[0] == null) REEL_ORIGIN[0] = p.position();           // every reel starts from the same spot
            p.teleportTo(REEL_ORIGIN[0].x, REEL_ORIGIN[0].y, REEL_ORIGIN[0].z); p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.setYRot(0); p.setXRot(0);
            var z = net.minecraft.world.entity.EntityType.HUSK.create(p.serverLevel());          // (a husk: doesn't burn in the sun)
            z.moveTo(p.getX(), p.getY(), p.getZ() + 1.7, 180, 0); z.setYHeadRot(180); z.yBodyRot = 180;
            z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20 * 600, 20, false, false));
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000); z.setHealth(1000);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            p.serverLevel().addFreshEntity(z); ids[0] = z.getId();
            var cam = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            double cx = p.getX() + net.minecraft.util.Mth.sin(camYaw * net.minecraft.util.Mth.DEG_TO_RAD) * 4.6, cz = p.getZ() + 2.4 - net.minecraft.util.Mth.cos(camYaw * net.minecraft.util.Mth.DEG_TO_RAD) * 4.6;
            cam.moveTo(cx, p.getY() - 0.4, cz, camYaw, 5); cam.setNoGravity(true); cam.setInvisible(true); cam.setYHeadRot(camYaw);
            p.serverLevel().addFreshEntity(cam); ids[1] = cam.getId();
        }), 20));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setYRot(0); mc.player.setXRot(0);
            if (mc.level.getEntity(ids[1]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(camYaw); a.yRotO = camYaw; a.setXRot(8); a.xRotO = 8; a.setYHeadRot(camYaw); a.yHeadRotO = camYaw; mc.setCameraEntity(a); } }, 20));
        for (int f = 0; f < frames; f++) {
            int ff = f;
            l.add(new Step(String.format("combo_%s_%02d", tag, f), () -> {
                if (ff % 2 == 0 && ff < frames - 10) com.xenowarriors.client.combo.ComboClient.press(heavy);   // spamming: every 4 ticks
            }, 2));
        }
        l.add(new Step(null, () -> mc.setCameraEntity(mc.player), 25));
    }

    /** Kaeru: front/back, her Change (silhouette, a zombie, a log), Soul Splitter for her, Rhalix and Mikiko (3 souls). */
    private static void kaeru(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> character(CharacterType.KAERU), 40));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10F); mc.player.setYRot(0F); }, 30));
        l.add(new Step("kaeru_front", () -> {}, 5));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(10F); }, 20));
        l.add(new Step("kaeru_back", () -> {}, 5));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            server(p -> com.xenowarriors.kaeru.KaeruChange.testForm(p, com.xenowarriors.kaeru.KaeruChange.SILHOUETTE, "minecraft:zombie")); }, 25));
        l.add(new Step("kaeru_change_silhouette", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.kaeru.KaeruChange.testForm(p, com.xenowarriors.kaeru.KaeruChange.CREATURE, "minecraft:zombie")), 25));
        l.add(new Step("kaeru_change_zombie", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.kaeru.KaeruChange.testForm(p, com.xenowarriors.kaeru.KaeruChange.CREATURE, "minecraft:fox")), 25));
        l.add(new Step("kaeru_change_fox", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.kaeru.KaeruChange.testForm(p, com.xenowarriors.kaeru.KaeruChange.BLOCK, "minecraft:oak_log")), 25));
        l.add(new Step("kaeru_change_block", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> { com.xenowarriors.kaeru.KaeruChange.revert(p, false); p.removeAllEffects(); }), 20));
        CharacterType[] who = {CharacterType.KAERU, CharacterType.RHALIX, CharacterType.MIKIKO};
        for (CharacterType t : who) {
            l.add(new Step(null, () -> server(p -> {
                com.xenowarriors.soul.SoulSplit.end(p, null);
                com.xenowarriors.character.CharacterManager.setCharacter(p, t);
                com.xenowarriors.soul.SoulSplit.setLevel(p, t == CharacterType.MIKIKO ? 40 : 0);
            }), 30));
            l.add(new Step(null, () -> server(p -> { p.setYRot(0); com.xenowarriors.soul.SoulSplit.start(p); }), 30));
            l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(15F); }, 30));
            l.add(new Step("soul_split_" + t.getId(), () -> {}, 5));
        }
        l.add(new Step(null, () -> { server(p -> com.xenowarriors.soul.SoulSplit.end(p, null)); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10));
    }

    /** The vehicles: lined up, a few colours, a driver in the car, the truck with its guard, the dealer screen. */
    private static void vehicles(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setXRot(18F); mc.player.setYRot(0F); }, 20));
        var K = com.xenowarriors.vehicle.VehicleKind.values();
        for (int i = 0; i < K.length; i++) {
            final var k = K[i];
            for (int v = 0; v < Math.min(2, k.variants.length); v++) {
                final int vv = v;
                l.add(new Step(null, () -> server(p -> {
                    var lvl = p.serverLevel();
                    lvl.getEntitiesOfClass(com.xenowarriors.vehicle.VehicleEntity.class, p.getBoundingBox().inflate(30)).forEach(e -> { e.ejectPassengers(); e.discard(); });
                    p.setYRot(0); p.setXRot(18);
                    var e = com.xenowarriors.vehicle.Vehicles.TYPES.get(k).get().create(lvl);
                    if (e == null) return;
                    double dist = k == com.xenowarriors.vehicle.VehicleKind.HELICOPTER ? 9 : k == com.xenowarriors.vehicle.VehicleKind.MOTORBIKE ? 4 : 7;
                    e.moveTo(p.getX(), p.getY(), p.getZ() + dist, 145, 0); e.yBodyRot = 145; e.yHeadRot = 145;
                    e.setVariant(vv);
                    lvl.addFreshEntity(e);
                    if (k == com.xenowarriors.vehicle.VehicleKind.GOV_TRUCK) {
                        var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(lvl);
                        if (g != null) { g.moveTo(e.position()); g.setup(true, 0, e.blockPosition()); lvl.addFreshEntity(g); g.startRiding(e, true); }
                    }
                }), 30));
                l.add(new Step("vehicle_" + k.id + "_" + v, () -> {}, 5));
            }
        }
        // riding the car (third person)
        l.add(new Step(null, () -> server(p -> {
            var lvl = p.serverLevel();
            lvl.getEntitiesOfClass(com.xenowarriors.vehicle.VehicleEntity.class, p.getBoundingBox().inflate(30)).forEach(e -> { e.ejectPassengers(); e.discard(); });
            var e = com.xenowarriors.vehicle.Vehicles.TYPES.get(com.xenowarriors.vehicle.VehicleKind.CAR).get().create(lvl);
            if (e == null) return;
            e.moveTo(p.getX(), p.getY(), p.getZ() + 1, 0, 0); lvl.addFreshEntity(e); p.startRiding(e, true);
        }), 30));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setXRot(20F); }, 20));
        l.add(new Step("vehicle_riding_car", () -> {}, 5));
        l.add(new Step(null, () -> server(p -> { p.stopRiding(); }), 20));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; server(com.xenowarriors.vehicle.Dealer::open); }, 30));
        l.add(new Step("vehicle_dealer", () -> {}, 5));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
    }

    /** Every bat look, lined up in front of the camera (with name tags). */
    private static void bats(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.setXRot(5F); mc.player.setYRot(0F); }, 20));
        String[] skins = com.xenowarriors.wing.WingPets.SKINS;
        for (int page = 0; page < 4; page++) {
            final int pg = page;
            l.add(new Step(null, () -> server(p -> {
                var lvl = p.serverLevel();
                lvl.getEntitiesOfClass(net.minecraft.world.entity.ambient.Bat.class, p.getBoundingBox().inflate(20)).forEach(e -> e.discard());
                p.setYRot(0); p.setXRot(5);
                for (int i = 0; i < 5; i++) {
                    int skin = pg * 5 + i;
                    if (skin >= skins.length) break;
                    var b = net.minecraft.world.entity.EntityType.BAT.create(lvl);
                    if (b == null) continue;
                    b.getPersistentData().putBoolean(com.xenowarriors.fox.WildCritters.ROLLED, true);
                    b.getPersistentData().putInt(com.xenowarriors.wing.WingPets.SKIN_KEY, skin);
                    int col = i % 5, row = i / 5;
                    b.moveTo(p.getX() - 2.4 + col * 1.2, p.getY() + 1.5, p.getZ() + 3.0, 0, 0); b.setYHeadRot(180); b.yBodyRot = 180; b.setYRot(180);
                    b.setNoAi(true); b.setResting(false); b.setNoGravity(true);
                    b.setCustomName(net.minecraft.network.chat.Component.literal(skins[skin])); b.setCustomNameVisible(true);
                    lvl.addFreshEntity(b);
                }
            }), 40));
            l.add(new Step("bats_" + page, () -> {}, 5));
        }
    }

    /** Client-side effects for the special-move frames (the real ones come from the server). */
    private static void animParticles(Minecraft mc, String n, String state, int f) {
        if (mc.level == null || mc.player == null) return;
        if (!(state.equals("special") || state.equals("redirect") || state.equals("demon"))) return;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ() + 4.4;
        var r = mc.level.random;
        switch (state.equals("redirect") ? "redirect" : state.equals("demon") ? "demon" : n) {
            case "senju" -> { if (f > 4) for (int i = 0; i < 10; i++) { double a = r.nextDouble() * Math.PI * 2; mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.6F, 0.05F, 0.1F), 1.4F), x + Math.cos(a) * 2.5, y + 1, z + Math.sin(a) * 2.5, 0, 0, 0); } }
            case "redirect" -> { for (int i = 0; i < 6; i++) { double a = f * 0.5 + i * Math.PI / 3; mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 0.85F, 0.25F), 1.1F), x + Math.cos(a) * 1.3, y + 1 + Math.sin(a * 2) * 0.4, z + Math.sin(a) * 1.3, 0, 0, 0); }
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x - 0.8, y + 1.3, z - 0.6, 0, 0, 0); }
            case "kill", "keita" -> { if (f > 4 && f < 10) mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, x - 0.6, y + 1.1, z - 0.8, 0, 0, 0); }
            case "maddie" -> { if (f > 4) mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 0.05F, 0.05F), 0.8F), x - 0.15, y + 1.75, z - 0.35, 0, 0, 0); }
            case "demon" -> { if (f >= 10) for (int i = 0; i < 4; i++) mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, x + r.nextGaussian() * 0.4, y + r.nextDouble() * 2, z + r.nextGaussian() * 0.4, 0, 0.03, 0); }
            default -> { if (f > 6) for (int i = 0; i < 8; i++) mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, x + r.nextGaussian() * 1.2, y + 1 + r.nextGaussian() * 0.4, z + r.nextGaussian() * 1.2, 0, 0.02, 0); }
        }
    }

    /** Small helper so the Collection counts as read (and the menu shows its tab). */
    private static final class CompoundHolder {
        static void unlock(ServerPlayer p) {
            var root = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            root.putBoolean("xw_collection_seen", true);
            p.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, root);
            com.xenowarriors.collection.Collection.open(p, false);
        }
    }

    private static int frames, actFrames;

    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent event) { if (ON && event.phase == TickEvent.Phase.END) frames++; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            // loading the test world: click through "experimental settings" / backup warnings
            if (mc.screen != null && ++pre % 40 == 0) {
                XenoWarriors.LOGGER.info("XW-UITEST waiting on screen {}", mc.screen.getClass().getName());
                for (var w : mc.screen.children()) {
                    if (w instanceof net.minecraft.client.gui.components.AbstractButton b) {
                        String t = b.getMessage().getString().toLowerCase();
                        if (t.contains("proceed") || t.contains("yes") || t.contains("know what") || t.contains("continue") || t.contains("load")) {
                            XenoWarriors.LOGGER.info("XW-UITEST pressing '{}'", t);
                            b.onPress();
                            break;
                        }
                    }
                }
            }
            return;
        }
        if (++ingame < 200) return;                                      // let the world settle
        if (steps == null) { steps = build(); idx = -1; wait = 0; XenoWarriors.LOGGER.info("XW-UITEST start ({} steps)", steps.size()); }
        if (wait > 0) { wait--; return; }
        if (frames - actFrames < 2) return;                              // make sure the last change has been drawn
        if (idx >= 0 && idx < steps.size() && steps.get(idx).name != null) {
            String n = steps.get(idx).name;
            Screenshot.grab(mc.gameDirectory, "xw_" + n + ".png", mc.getMainRenderTarget(), c -> XenoWarriors.LOGGER.info("XW-UITEST shot {}", n));
        }
        idx++;
        if (idx >= steps.size()) { XenoWarriors.LOGGER.info("XW-UITEST done"); mc.stop(); return; }
        try { steps.get(idx).action.run(); } catch (Throwable t) { XenoWarriors.LOGGER.error("XW-UITEST step failed", t); }
        wait = steps.get(idx).delay;
        actFrames = frames;
    }
}

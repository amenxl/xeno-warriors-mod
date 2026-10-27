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
        l.add(new Step(null, () -> character(CharacterType.MIKIKO), 40));
        l.add(new Step("hud_mikiko", () -> mc.setScreen(null), 30));
        l.add(new Step("select_screen", () -> mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()), 20));
        for (int t = 0; t < 4; t++) {
            int tt = t;
            l.add(new Step("menu_tab" + t, () -> { PlayerStatsScreen.presetTab(tt); PlayerStatsScreen.open(); }, 25));
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
        CharacterType[] sp = {CharacterType.KYRIE, CharacterType.LEXY, CharacterType.MAX, CharacterType.SAM, CharacterType.TECHNICA, CharacterType.AIONI};
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
        l.add(new Step("sp_select_page1", () -> {}, 5));
        l.add(new Step(null, () -> { mc.setScreen(null); com.xenowarriors.client.screen.CharacterSelectScreen.nextPageForTest();
            mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 40));
        l.add(new Step("sp_select_page2", () -> {}, 5));
        l.add(new Step(null, () -> mc.setScreen(null), 5));
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

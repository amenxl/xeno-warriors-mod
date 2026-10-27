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
        for (CharacterType t : CharacterType.PLAYABLE) {
            l.add(new Step(null, () -> { character(t); hold(null); }, 30));
            l.add(new Step(null, () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setYRot(0); mc.player.setXRot(0); server(p -> { p.setYRot(0); p.setXRot(0); com.xenowarriors.karma.Karma.add(p, 500F); }); }, 15));
            l.add(new Step(null, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND), 2));
            for (int f = 0; f < 3; f++) l.add(new Step("allanim_" + t.getId() + "_attack_" + f, () -> {}, 2));
            var pool = com.xenowarriors.skill.SkillPool.of(t);
            java.util.List<com.xenowarriors.skill.Skill> skills = new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(5, pool.size()); i++) skills.add(pool.get(i).skill());
            if (com.xenowarriors.skill.SkillPool.ultimate(t) != null) skills.add(com.xenowarriors.skill.SkillPool.ultimate(t));
            for (com.xenowarriors.skill.Skill sk : skills) {
                l.add(new Step(null, () -> server(p -> { p.setYRot(0); p.setXRot(0); try { if (sk.use(p)) { Integer gen = com.xenowarriors.network.SkillAnimPacket.GENERIC.get(sk.getId());
                    if (gen != null) com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.SkillAnimPacket(p.getId(), gen, false), p); } } catch (Exception ex) { XenoWarriors.LOGGER.warn("XW-UITEST skill {} failed", sk.getId(), ex); } }), 4));
                for (int f = 0; f < 3; f++) l.add(new Step("allanim_" + t.getId() + "_" + sk.getId() + "_" + f, () -> {}, 5));
                l.add(new Step(null, () -> {}, 40));                    // let it finish
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

    /** Every character powering up (their aura), Karma focus glows, and element auras on a row of mobs. */
    private static void auras(Minecraft mc, List<Step> l) {
        Runnable front = () -> { mc.setScreen(null); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(12F); mc.player.setYRot(0F); };
        l.add(new Step(null, () -> server(p -> p.serverLevel().setDayTime(18000)), 10));       // night shows the glow
        for (CharacterType t : CharacterType.PLAYABLE) {
            l.add(new Step(null, () -> { forceCharge = false; character(t); }, 30));
            l.add(new Step(null, () -> { front.run(); forceCharge = true; }, 35));
            l.add(new Step("aura_" + t.getId(), () -> {}, 3));
            l.add(new Step("aura_" + t.getId() + "_b", () -> {}, 3));
        }
        l.add(new Step(null, () -> forceCharge = false, 20));
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.YURI}) {
            l.add(new Step(null, () -> character(t), 30));
            l.add(new Step(null, () -> { front.run(); KarmaFocusClient.set(mc.player.getId(), true); }, 40));
            l.add(new Step("aura_focus_" + t.getId(), () -> {}, 3));
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
        l.add(new Step(null, () -> { mc.options.hideGui = false; server(p -> p.serverLevel().setDayTime(6000)); }, 5));
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

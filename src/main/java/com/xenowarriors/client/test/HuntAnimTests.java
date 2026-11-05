package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.Seasons;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.List;

/**
 * Bug hunt: rendering and animations (focus "huntanim", "efhuntanim" with Epic Fight, "dmzhuntanim" with DragonMineZ).
 * Extra words pick parts (none = all): gal (every character and outfit, front/back), gear (armor + 3D tools, first
 * person hands), pose (crouch, crawl, swim, sleep, boat, elytra, pod), stuck (animations left over after death /
 * respawn / dimension change), bots (bot / NPC models side by side, mid-combo). Lines: "XW-TEST huntanim ...".
 */
public final class HuntAnimTests {
    private HuntAnimTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^(dmz|ef)", "").startsWith("huntanim"); }

    private static void log(String s) { UiTest.log("huntanim " + s); }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        UiTest.server(p -> { try { f.accept(p); } catch (Throwable t) { log("server step error " + t); XenoWarriors.LOGGER.error("XW-TEST huntanim", t); } });
    }

    private static final double[] HOME = new double[3];
    private static final int[] CAM = {-1};
    private static String SUF = "";

    public static void build(Minecraft mc, List<Step> l, String focus) {
        String f = focus.replaceFirst("^(dmz|ef)", "").replaceFirst("^huntanim", "");
        boolean all = f.isEmpty();
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); mc.options.hideGui = true; server(p -> {
            HOME[0] = p.getX(); HOME[1] = p.getY(); HOME[2] = p.getZ();
            Seasons.set(p.server, 3);
            p.setGameMode(GameType.CREATIVE);
            dmzOk(p);
            clearBots(p);
        }); }, 20));
        if (all || f.contains("stuck")) {
            // first the old behaviour (per-life reset off) for "before" pictures, then as it is now
            SUF = "_before";
            l.add(new Step(null, () -> com.xenowarriors.client.ClientReset.testOff = true, 1));
            stuck(mc, l);
            SUF = "";
            l.add(new Step(null, () -> { com.xenowarriors.client.ClientReset.testOff = false; com.xenowarriors.client.ClientReset.resetAll(); }, 5));
            stuck(mc, l);
        }
        if (all || f.contains("fx")) fx(mc, l);
        if (all || f.contains("inv")) inventory(mc, l);
        if (all || f.contains("track")) tracking(mc, l);
        if (all || f.contains("fpko")) firstPersonDown(mc, l);
        if (all || f.contains("gal")) gallery(mc, l);
        if (all || f.contains("gear")) gear(mc, l);
        if (all || f.contains("pose")) poses(mc, l);
        if (all || f.contains("bots")) bots(mc, l);
        l.add(new Step(null, () -> { me(mc); log("done"); }, 5));
    }

    // ------------------------------------------------------------------ helpers

    private static void dmzOk(ServerPlayer p) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("dragonminez")) com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
    }

    private static void clearBots(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (CharacterType t : CharacterType.PLAYABLE) p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
    }

    private static void setChar(List<Step> l, CharacterType t, int outfit) {
        l.add(new Step(null, () -> server(p -> {
            CharacterManager.setCharacter(p, t);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = outfit);
            CharacterManager.syncToTrackingAndSelf(p);
            dmzOk(p);
            clearSky(p);
        }), 30));
    }

    /** Daytime, no rain (the long runs outlast the clear weather the test world starts with). */
    private static void clearSky(ServerPlayer p) {
        p.server.overworld().setWeatherParameters(24000, 0, false, false);
        p.server.overworld().setDayTime(6000);
    }

    private static void home(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) p.teleportTo(p.server.overworld(), HOME[0], HOME[1], HOME[2], 0F, 0F);
        else p.teleportTo(HOME[0], HOME[1], HOME[2]);
        p.setYRot(0F); p.setXRot(0F); p.setYHeadRot(0F); p.yBodyRot = 0F;
    }

    private static void face(Minecraft mc) {
        var p = mc.player;
        if (p == null) return;
        p.setYRot(0F); p.yRotO = 0F; p.setXRot(0F); p.xRotO = 0F;
        p.yBodyRot = 0F; p.yBodyRotO = 0F; p.yHeadRot = 0F; p.yHeadRotO = 0F;
    }

    /** An invisible armor stand to film from: offset from the home spot, yaw, pitch (the player faces +z). */
    private static void cam(Minecraft mc, List<Step> l, double dx, double dy, double dz, float yaw, float pitch) {
        l.add(new Step(null, () -> server(p -> {
            if (CAM[0] >= 0 && p.serverLevel().getEntity(CAM[0]) != null) p.serverLevel().getEntity(CAM[0]).discard();
            ArmorStand a = EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(yaw);
            a.addTag("xw_ha_cam");
            p.serverLevel().addFreshEntity(a); CAM[0] = a.getId();
        }), 10));
        l.add(new Step(null, () -> {
            if (mc.level.getEntity(CAM[0]) instanceof ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
        }, 4));
    }

    private static void me(Minecraft mc) { mc.setCameraEntity(mc.player); }

    private static void armor(ServerPlayer p, boolean on) {
        p.setItemSlot(EquipmentSlot.HEAD, on ? new ItemStack(Items.IRON_HELMET) : ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.CHEST, on ? new ItemStack(Items.IRON_CHESTPLATE) : ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.LEGS, on ? new ItemStack(Items.IRON_LEGGINGS) : ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.FEET, on ? new ItemStack(Items.IRON_BOOTS) : ItemStack.EMPTY);
    }

    private static void bare(ServerPlayer p) {
        for (EquipmentSlot s : EquipmentSlot.values()) p.setItemSlot(s, ItemStack.EMPTY);
    }

    /** Everything about the local player's animation state, for the stuck checks. */
    public static String snapshot(Minecraft mc) {
        AbstractClientPlayer p = mc.player;
        if (p == null) return "no player";
        StringBuilder b = new StringBuilder();
        b.append("id=").append(p.getId()).append(" alive=").append(p.isAlive()).append(" pose=").append(p.getPose())
                .append(" dim=").append(p.level().dimension().location().getPath())
                .append(" char=").append(com.xenowarriors.client.ClientCharacterData.get(p).getId());
        b.append(" skillAnim=").append(com.xenowarriors.client.ClientSkillState.currentAnim(p));
        b.append(" posed=").append(com.xenowarriors.client.EntityPoses.posed(p));
        b.append(" knock=").append(String.format("%.2f", com.xenowarriors.client.KnockdownClient.down(p, 0F)));
        b.append(" swing=").append(com.xenowarriors.client.combo.ComboClient.swing(p) != null);
        b.append(" idle=").append(com.xenowarriors.client.Idles.holder(p));
        b.append(" action=").append(com.xenowarriors.client.Busy.action(p));
        b.append(" charge=").append(String.format("%.2f", com.xenowarriors.client.ChargePose.weight(p)));
        b.append(" focus=").append(com.xenowarriors.client.KarmaFocusClient.focusing(p));
        b.append(" flying=").append(com.xenowarriors.client.FlightTracker.isFlying(p)).append("/").append(p.getAbilities().flying);
        b.append(" wingsGrow=").append(String.format("%.2f", com.xenowarriors.client.RhalixWingsClient.grow(p, 0F)));
        b.append(" rhAttack=").append(com.xenowarriors.client.RhalixCombatAnim.attacking(p));
        float[] combo = com.xenowarriors.client.RhalixComboAnim.playing(p, 0F);
        b.append(" rhCombo=").append(combo != null);
        b.append(" mkAttack=").append(com.xenowarriors.client.mikiko.MikikoAnimations.attacking(p));
        b.append(" mkDive=").append(String.format("%.2f", com.xenowarriors.client.mikiko.MikikoAnimations.dive(p, 0F)));
        b.append(" mkSit=").append(String.format("%.2f", com.xenowarriors.client.mikiko.MikikoAnimations.sitWeight(p)));
        b.append(" netsu=").append(com.xenowarriors.client.ClientSkillState.netsuActive(p.getId()));
        b.append(" warp=").append(com.xenowarriors.client.KishiWarpClient.warping());
        b.append(" surf=").append(com.xenowarriors.client.SurfPose.surfing(p));
        b.append(" ko=").append(com.xenowarriors.client.party.PartyClient.knockedOut(p));
        b.append(" fox=").append(String.format("%.2f", com.xenowarriors.client.FoxRun.weight(p)));
        b.append(" vehicle=").append(p.getVehicle() == null ? "-" : p.getVehicle().getType().getDescriptionId());
        return b.toString();
    }

    // ------------------------------------------------------------------ 1. stuck after death / respawn / dimension change

    private static void kill(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> { p.setGameMode(GameType.SURVIVAL); p.removeAllEffects(); p.hurt(p.damageSources().genericKill(), 10000F); }), 25));
        l.add(new Step(null, () -> { if (mc.player != null && (mc.player.isDeadOrDying() || mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) mc.player.respawn(); }, 40));
        l.add(new Step(null, () -> { mc.setScreen(null); server(p -> { p.setGameMode(GameType.CREATIVE); home(p); }); }, 30));
    }

    private static void toNether(List<Step> l) {
        l.add(new Step(null, () -> server(p -> {
            ServerLevel n = p.server.getLevel(Level.NETHER);
            if (n == null) { log("no nether"); return; }
            BlockPos at = new BlockPos(0, 100, 0);
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                n.setBlock(at.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState(), 3);
                for (int dy = 0; dy < 4; dy++) n.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
            }
            p.teleportTo(n, 0.5, 100, 0.5, 0F, 0F);
        }), 60));
    }

    private static void back(List<Step> l) {
        l.add(new Step(null, () -> server(HuntAnimTests::home), 60));
    }

    private static void snap(Minecraft mc, List<Step> l, String tag) {
        String suf = SUF; l.add(new Step(null, () -> log("stuck" + suf + " " + tag + ": " + snapshot(mc)), 1));
    }

    /** Nothing left playing on you (no swing, no attack, no pose), and walking isn't held back. */
    private static void check(Minecraft mc, List<Step> l, String what) {
        String suf = SUF;
        l.add(new Step(null, () -> {
            var p = mc.player;
            boolean stuck = com.xenowarriors.client.combo.ComboClient.swing(p) != null || com.xenowarriors.client.mikiko.MikikoAnimations.attacking(p)
                    || com.xenowarriors.client.RhalixCombatAnim.attacking(p) || com.xenowarriors.client.RhalixComboAnim.playing(p, 0F) != null
                    || com.xenowarriors.client.EntityPoses.posed(p) || com.xenowarriors.client.Busy.action(p) || com.xenowarriors.client.combo.ComboClient.committed();
            log("check" + suf + " " + what + ": " + (stuck ? "FAIL" : "PASS") + " (newLives=" + com.xenowarriors.client.ClientReset.newLives + ")");
        }, 1));
    }

    private static void thirdFront(Minecraft mc) {
        me(mc); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); face(mc);
    }

    private static void stuck(Minecraft mc, List<Step> l) {
        // ---- Rhalix: mid ground combo, then killed
        setChar(l, CharacterType.RHALIX, 0);
        l.add(new Step(null, () -> { thirdFront(mc); server(HuntAnimTests::home); }, 10));
        snap(mc, l, "rhalix-start");
        for (int i = 0; i < 4; i++) l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.press(false); }, 3));
        snap(mc, l, "rhalix-midcombo");
        l.add(new Step("huntanim_stuck" + SUF + "_rhalix_midcombo", () -> {}, 1));
        kill(mc, l);
        l.add(new Step("huntanim_stuck" + SUF + "_rhalix_after_death", () -> thirdFront(mc), 2));
        snap(mc, l, "rhalix-after-death");
        // ---- Rhalix: Ki Burst hold, then the nether mid-move
        l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.testHold(10); }, 6));
        snap(mc, l, "rhalix-kiburst");
        toNether(l);
        l.add(new Step("huntanim_stuck" + SUF + "_rhalix_nether", () -> thirdFront(mc), 2));
        snap(mc, l, "rhalix-nether");
        back(l);
        // ---- Rhalix flying with wings, then killed in the air
        l.add(new Step(null, () -> server(p -> { p.setGameMode(GameType.SURVIVAL); p.teleportTo(p.getX(), p.getY() + 6, p.getZ());
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities(); }), 30));
        l.add(new Step("huntanim_stuck" + SUF + "_rhalix_flying", () -> thirdFront(mc), 2));
        snap(mc, l, "rhalix-flying");
        kill(mc, l);
        l.add(new Step("huntanim_stuck" + SUF + "_rhalix_after_flydeath", () -> thirdFront(mc), 2));
        snap(mc, l, "rhalix-after-flydeath");
        // ---- knocked down (Gravity Whirl's pose) on yourself, then killed
        l.add(new Step(null, () -> server(p -> com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KNOCKDOWN, 200)), 20));
        l.add(new Step("huntanim_stuck" + SUF + "_knockdown", () -> thirdFront(mc), 2));
        snap(mc, l, "knockdown");
        kill(mc, l);
        l.add(new Step("huntanim_stuck" + SUF + "_after_knockdown_death", () -> thirdFront(mc), 2));
        snap(mc, l, "after-knockdown-death");
        // ---- knocked down, then the nether
        l.add(new Step(null, () -> server(p -> com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KNOCKDOWN, 200)), 10));
        toNether(l);
        l.add(new Step("huntanim_stuck" + SUF + "_knockdown_nether", () -> thirdFront(mc), 2));
        snap(mc, l, "knockdown-nether");
        back(l);
        l.add(new Step(null, () -> server(com.xenowarriors.anim.Poses::clearState), 5));
        // ---- Mikiko: Netsu on, then the nether and death
        setChar(l, CharacterType.MIKIKO, 0);
        l.add(new Step(null, () -> { thirdFront(mc); server(p -> { home(p); p.setGameMode(GameType.SURVIVAL);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setKarma(1000F));
            com.xenowarriors.mikiko.Netsu.start(p); }); }, 20));
        l.add(new Step("huntanim_stuck" + SUF + "_netsu", () -> thirdFront(mc), 2));
        snap(mc, l, "netsu");
        toNether(l);
        l.add(new Step("huntanim_stuck" + SUF + "_netsu_nether", () -> thirdFront(mc), 2));
        snap(mc, l, "netsu-nether");
        back(l);
        kill(mc, l);
        l.add(new Step("huntanim_stuck" + SUF + "_netsu_after_death", () -> thirdFront(mc), 2));
        snap(mc, l, "netsu-after-death");
        // ---- Mikiko: Fox King, then death
        l.add(new Step(null, () -> server(p -> { p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setKarma(1000F));
            com.xenowarriors.mikiko.FoxKing.resetCooldown(p); com.xenowarriors.mikiko.FoxKing.toggle(p); }), 30));
        l.add(new Step("huntanim_stuck" + SUF + "_foxking", () -> thirdFront(mc), 2));
        snap(mc, l, "foxking");
        kill(mc, l);
        l.add(new Step("huntanim_stuck" + SUF + "_foxking_after_death", () -> thirdFront(mc), 2));
        snap(mc, l, "foxking-after-death");
        // ---- Mikiko: riding his blade, then the nether / death
        l.add(new Step(null, () -> server(p -> {
            p.setGameMode(GameType.SURVIVAL);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 4, false, false));
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.bladeLevel = 200);
            p.teleportTo(p.getX(), p.getY() + 8, p.getZ());
            var b = com.xenowarriors.entity.HekubladeShardEntity.create(p, 0);
            p.level().addFreshEntity(b);
            b.surf(p);
        }), 15));
        l.add(new Step("huntanim_stuck" + SUF + "_bladeride", () -> thirdFront(mc), 2));
        snap(mc, l, "bladeride");
        kill(mc, l);
        l.add(new Step("huntanim_stuck" + SUF + "_bladeride_after_death", () -> thirdFront(mc), 2));
        snap(mc, l, "bladeride-after-death");
        // ---- Mikiko: a combo string, then the nether
        for (int i = 0; i < 4; i++) l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.press(false); }, 3));
        snap(mc, l, "mikiko-midcombo");
        toNether(l);
        l.add(new Step("huntanim_stuck" + SUF + "_mikiko_combo_nether", () -> thirdFront(mc), 2));
        snap(mc, l, "mikiko-combo-nether");
        check(mc, l, "mikiko combo cut off by the nether");
        back(l);
        // ---- Rhalix: a hit, then straight through a "portal"; and with instant respawn, killed mid-swing
        setChar(l, CharacterType.RHALIX, 0);
        for (int i = 0; i < 2; i++) l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.press(false); }, 1));
        l.add(new Step(null, () -> com.xenowarriors.client.combo.ComboClient.press(false), 1));
        snap(mc, l, "rhalix-press");
        toNether(l);
        l.add(new Step("huntanim_stuck" + SUF + "_rhalix_combo_nether", () -> thirdFront(mc), 2));
        snap(mc, l, "rhalix-combo-nether");
        check(mc, l, "rhalix combo cut off by the nether");
        back(l);
        l.add(new Step(null, () -> server(p -> p.server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, p.server)), 5));
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.MIKIKO}) {
            setChar(l, t, 0);
            l.add(new Step(null, () -> { thirdFront(mc); server(p -> { p.setGameMode(GameType.SURVIVAL); home(p); }); }, 10));
            for (int i = 0; i < 3; i++) l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.press(false); }, 2));
            l.add(new Step(null, () -> server(p -> { p.removeAllEffects(); p.hurt(p.damageSources().genericKill(), 10000F); }), 2));
            l.add(new Step(null, () -> { if (mc.player != null && mc.player.isDeadOrDying()) mc.player.respawn(); }, 30));
            l.add(new Step(null, () -> { mc.setScreen(null); server(p -> { home(p); p.setGameMode(GameType.CREATIVE); }); }, 20));
            l.add(new Step("huntanim_stuck" + SUF + "_" + t.getId() + "_instant_respawn", () -> thirdFront(mc), 2));
            snap(mc, l, t.getId() + "-instant-respawn");
            check(mc, l, t.getId() + " combo cut off by an instant respawn");
            // and a new string starts at once
            l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.press(false); }, 2));
            String suf = SUF;
            l.add(new Step(null, () -> log("check" + suf + " " + t.getId() + " new combo after respawn: " + (com.xenowarriors.client.combo.ComboClient.swing(mc.player) != null ? "PASS" : "FAIL")), 1));
        }
        l.add(new Step(null, () -> server(p -> p.server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false, p.server)), 5));
        // ---- sitting idle (stand still a long time), then hurt
        l.add(new Step(null, () -> { thirdFront(mc); server(p -> { p.setGameMode(GameType.SURVIVAL); home(p); }); }, 700));
        l.add(new Step("huntanim_stuck" + SUF + "_mikiko_idle", () -> thirdFront(mc), 2));
        snap(mc, l, "mikiko-idle");
        l.add(new Step(null, () -> server(p -> p.hurt(p.damageSources().generic(), 2F)), 10));
        l.add(new Step("huntanim_stuck" + SUF + "_mikiko_idle_hurt", () -> thirdFront(mc), 2));
        snap(mc, l, "mikiko-idle-hurt");
        // ---- relog-like reset (the client's per-world caches emptied) while posed
        l.add(new Step(null, () -> com.xenowarriors.client.ClientReset.resetAll(), 5));
        snap(mc, l, "after-reset");
        l.add(new Step(null, () -> server(p -> { p.setGameMode(GameType.CREATIVE); home(p); }), 10));
    }

    // ------------------------------------------------------------------ skill effects, normal and as drawn for shader packs

    private static void fx(Minecraft mc, List<Step> l) {
        for (boolean shader : new boolean[]{false, true}) {
            for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX}) {
                for (int slot : new int[]{0, com.xenowarriors.skill.SkillManager.ULTIMATE_SLOT}) {
                    String name = "huntanim_fx_" + t.getId() + "_skill" + slot + (shader ? "_shaderdraw" : "");
                    setChar(l, t, 0);
                    l.add(new Step(null, () -> { com.xenowarriors.client.compat.ShaderCompat.forceForTest = shader; server(p -> {
                        home(p); p.setGameMode(GameType.SURVIVAL);
                        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 30, 4, false, false));
                        com.xenowarriors.skill.SkillManager.resetCooldowns(p);
                        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setKarma(1000F));
                        com.xenowarriors.karma.Karma.sync(p, p.getCapability(ModCapabilities.PLAYER_CHARACTER).orElse(null));
                    }); me(mc); mc.options.hideGui = true; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); face(mc); mc.player.setXRot(15F); }, 10));
                    l.add(new Step(null, () -> { face(mc); mc.player.setXRot(15F); com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.UseSkillPacket(slot)); }, 25));
                    l.add(new Step(name, () -> log("fx " + name + " karma=" + com.xenowarriors.client.ClientSkillState.karma()), 2));
                    l.add(new Step(null, () -> server(p -> { com.xenowarriors.skill.SkillManager.resetCooldowns(p); p.setGameMode(GameType.CREATIVE); }), 80));
                }
            }
        }
        l.add(new Step(null, () -> com.xenowarriors.client.compat.ShaderCompat.forceForTest = false, 2));
    }

    // ------------------------------------------------------------------ someone coming into view of a burning Mikiko

    /**
     * There's no second player here, so: Netsu on, the client forgets it (as a player who wasn't in range when it
     * started would never have heard of it), then "start tracking" is fired as it is when someone comes into view.
     */
    private static void tracking(Minecraft mc, List<Step> l) {
        setChar(l, CharacterType.MIKIKO, 0);
        l.add(new Step(null, () -> server(p -> { home(p); p.setGameMode(GameType.SURVIVAL);
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.setKarma(1000F)); com.xenowarriors.mikiko.Netsu.start(p); }), 10));
        l.add(new Step(null, () -> { com.xenowarriors.client.ClientSkillState.setNetsu(mc.player.getId(), 0);
            log("track netsu forgotten on the client: active=" + com.xenowarriors.client.ClientSkillState.netsuActive(mc.player.getId())); }, 2));
        l.add(new Step(null, () -> server(p -> net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.StartTracking(p, p))), 10));
        l.add(new Step(null, () -> log("check netsu seen by someone coming into view: "
                + (com.xenowarriors.client.ClientSkillState.netsuActive(mc.player.getId()) ? "PASS" : "FAIL")), 2));
        l.add(new Step(null, () -> server(p -> { com.xenowarriors.mikiko.Netsu.stop(p); p.setGameMode(GameType.CREATIVE); }), 10));
    }

    // ------------------------------------------------------------------ first person while knocked down (DragonMineZ draws your body)

    private static void firstPersonDown(Minecraft mc, List<Step> l) {
        setChar(l, CharacterType.RHALIX, 0);
        l.add(new Step(null, () -> { mc.setScreen(null); me(mc); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true; face(mc); mc.player.setXRot(60F);
            server(p -> { home(p); p.setGameMode(GameType.SURVIVAL); }); }, 15));
        l.add(new Step("huntanim_fpko_standing", () -> mc.setScreen(null), 2));
        l.add(new Step(null, () -> server(p -> com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KNOCKDOWN, 200)), 25));
        l.add(new Step("huntanim_fpko_knockdown", () -> { mc.setScreen(null); face(mc); mc.player.setXRot(60F); log("fpko knockdown " + snapshot(mc) + " drawnTipped=" + com.xenowarriors.client.KnockdownClient.tipped); }, 2));
        l.add(new Step(null, () -> server(p -> { com.xenowarriors.anim.Poses.clearState(p); p.setGameMode(GameType.CREATIVE); }), 10));
    }

    // ------------------------------------------------------------------ the inventory's player preview in odd states

    private static void inventory(Minecraft mc, List<Step> l) {
        Runnable open = () -> { me(mc); mc.options.hideGui = false; mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); };
        Runnable close = () -> { mc.setScreen(null); mc.options.hideGui = true; };
        setChar(l, CharacterType.RHALIX, 0);
        l.add(new Step(null, () -> server(p -> { home(p); p.setGameMode(GameType.SURVIVAL); p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 120, 4, false, false)); }), 10));
        l.add(new Step(null, open, 10));
        l.add(new Step("huntanim_inv_rhalix_plain", () -> {}, 2));
        // knocked down
        l.add(new Step(null, () -> { close.run(); server(p -> com.xenowarriors.anim.Poses.play(p, com.xenowarriors.anim.Poses.KNOCKDOWN, 200)); }, 25));
        l.add(new Step(null, open, 6));
        l.add(new Step("huntanim_inv_rhalix_knockdown", () -> log("inv knockdown " + snapshot(mc)), 2));
        l.add(new Step(null, () -> { mc.setScreen(null); mc.setScreen(new com.xenowarriors.client.screen.CharacterSelectScreen()); }, 8));
        l.add(new Step("huntanim_inv_rhalix_knockdown_select", () -> {}, 2));
        l.add(new Step(null, () -> { close.run(); server(com.xenowarriors.anim.Poses::clearState); }, 10));
        // flying with the wings out
        l.add(new Step(null, () -> server(p -> { p.teleportTo(p.getX(), p.getY() + 5, p.getZ()); p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities(); }), 30));
        l.add(new Step(null, open, 6));
        l.add(new Step("huntanim_inv_rhalix_flying", () -> log("inv flying " + snapshot(mc)), 2));
        // mid combo
        l.add(new Step(null, () -> { close.run(); server(p -> { p.getAbilities().flying = false; p.onUpdateAbilities(); home(p); }); }, 30));
        l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.press(false); }, 3));
        l.add(new Step(null, () -> { face(mc); com.xenowarriors.client.combo.ComboClient.press(false); }, 3));
        l.add(new Step(null, open, 1));
        l.add(new Step("huntanim_inv_rhalix_combo", () -> log("inv combo " + snapshot(mc)), 1));
        l.add(new Step(null, close, 20));
        // Mikiko diving from high up
        setChar(l, CharacterType.MIKIKO, 0);
        l.add(new Step(null, () -> server(p -> { p.setGameMode(GameType.SURVIVAL); p.teleportTo(p.getX(), p.getY() + 40, p.getZ()); }), 32));
        l.add(new Step(null, open, 1));
        l.add(new Step("huntanim_inv_mikiko_dive", () -> log("inv dive " + snapshot(mc)), 1));
        l.add(new Step(null, () -> { close.run(); server(p -> { home(p); p.fallDistance = 0; }); }, 20));
        // Mikiko crawling
        l.add(new Step(null, () -> server(p -> { home(p); p.serverLevel().setBlock(p.blockPosition().above(), Blocks.GLASS.defaultBlockState(), 3); p.setPose(net.minecraft.world.entity.Pose.SWIMMING); }), 15));
        l.add(new Step(null, open, 6));
        l.add(new Step("huntanim_inv_mikiko_crawl", () -> {}, 2));
        l.add(new Step(null, () -> { close.run(); server(p -> { p.serverLevel().setBlock(BlockPos.containing(HOME[0], HOME[1] + 1, HOME[2]), Blocks.AIR.defaultBlockState(), 3); p.setGameMode(GameType.CREATIVE); home(p); }); }, 10));
    }

    // ------------------------------------------------------------------ 2. gallery: every character and outfit

    private static void gallery(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> server(p -> { bare(p); home(p); }), 5));
        for (CharacterType t : CharacterType.PLAYABLE) {
            String[] outfits = t.outfitKeys();
            for (int o = 0; o < outfits.length; o++) {
                String name = t.getId() + (outfits[o].isEmpty() ? "" : "_" + outfits[o]);
                setChar(l, t, o);
                l.add(new Step(null, () -> server(HuntAnimTests::home), 5));
                cam(mc, l, 0, 0.05, 2.6, 180F, 2F);
                l.add(new Step("huntanim_gal_" + name + "_front", () -> face(mc), 3));
                cam(mc, l, 1.85, 0.05, 1.85, 135F, 2F);
                l.add(new Step("huntanim_gal_" + name + "_q", () -> face(mc), 3));
                cam(mc, l, 0, 0.05, -2.6, 0F, 2F);
                l.add(new Step("huntanim_gal_" + name + "_back", () -> face(mc), 3));
                l.add(new Step(null, () -> log("gal " + name + " drawn ok"), 1));
            }
        }
        l.add(new Step(null, () -> me(mc), 3));
    }

    // ------------------------------------------------------------------ 3. gear: armor, 3D tools, first person

    private static void gear(Minecraft mc, List<Step> l) {
        ItemStack[] held = {new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.SHIELD)};
        for (CharacterType t : CharacterType.PLAYABLE) {
            setChar(l, t, 0);
            l.add(new Step(null, () -> server(p -> { home(p); armor(p, true); p.setItemSlot(EquipmentSlot.MAINHAND, held[0].copy()); p.setItemSlot(EquipmentSlot.OFFHAND, held[3].copy()); }), 10));
            cam(mc, l, 1.85, 0.05, 1.85, 135F, 2F);
            l.add(new Step("huntanim_gear_" + t.getId() + "_armor", () -> face(mc), 3));
            l.add(new Step(null, () -> server(p -> { armor(p, false); p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA)); p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                p.setItemSlot(EquipmentSlot.MAINHAND, held[1].copy()); }), 10));
            cam(mc, l, 0, 0.05, -2.6, 0F, 2F);
            l.add(new Step("huntanim_gear_" + t.getId() + "_elytra_back", () -> face(mc), 3));
            l.add(new Step(null, () -> server(HuntAnimTests::bare), 5));
        }
        // first person hands with things held
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.ULTIMA}) {
            setChar(l, t, 0);
            for (int i = 0; i < 3; i++) {
                int ii = i;
                l.add(new Step(null, () -> { me(mc); mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; face(mc);
                    server(p -> { home(p); bare(p); if (ii > 0) p.setItemSlot(EquipmentSlot.MAINHAND, held[ii - 1].copy()); }); }, 15));
                l.add(new Step("huntanim_fp_" + t.getId() + "_" + ii, () -> {}, 2));
            }
        }
        l.add(new Step(null, () -> { server(HuntAnimTests::bare); mc.options.hideGui = true; }, 5));
    }

    // ------------------------------------------------------------------ 4. poses

    private static void poses(Minecraft mc, List<Step> l) {
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KYRIE, CharacterType.GARRY}) {
            String n = t.getId();
            setChar(l, t, 0);
            // crouching
            l.add(new Step(null, () -> { server(p -> { home(p); bare(p); }); mc.options.keyShift.setDown(true); }, 10));
            cam(mc, l, 1.85, 0.05, 1.85, 135F, 2F);
            l.add(new Step("huntanim_pose_" + n + "_crouch", () -> face(mc), 3));
            l.add(new Step(null, () -> mc.options.keyShift.setDown(false), 5));
            // crawling under a low ceiling
            l.add(new Step(null, () -> server(p -> { home(p); p.serverLevel().setBlock(p.blockPosition().above(), Blocks.GLASS.defaultBlockState(), 3);
                p.setPose(net.minecraft.world.entity.Pose.SWIMMING); }), 15));
            cam(mc, l, 1.85, 0.05, 1.85, 135F, 2F);
            l.add(new Step("huntanim_pose_" + n + "_crawl", () -> { face(mc); log("pose " + n + " crawl pose=" + mc.player.getPose()); }, 3));
            l.add(new Step(null, () -> server(p -> p.serverLevel().setBlock(BlockPos.containing(HOME[0], HOME[1] + 1, HOME[2]), Blocks.AIR.defaultBlockState(), 3)), 5));
            // asleep in a bed
            l.add(new Step(null, () -> server(p -> {
                home(p);
                BlockPos foot = p.blockPosition().east(2), head = foot.south();
                p.serverLevel().setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT), 3);
                p.serverLevel().setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD), 3);
                p.server.overworld().setDayTime(18000);          // (in daytime you're woken up at once)
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 20, 0, false, false));
                p.startSleeping(head);
            }), 20));
            cam(mc, l, 1.0, 1.2, 1.2, 160F, 35F);
            l.add(new Step(null, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN), 3));
            l.add(new Step("huntanim_pose_" + n + "_sleep", () -> log("pose " + n + " sleeping=" + mc.player.isSleeping() + " " + snapshot(mc)), 3));
            l.add(new Step(null, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL), 1));
            l.add(new Step(null, () -> server(p -> { p.stopSleeping(); clearSky(p); p.removeEffect(MobEffects.NIGHT_VISION); BlockPos foot = BlockPos.containing(HOME[0], HOME[1], HOME[2]).east(2);
                p.serverLevel().setBlock(foot, Blocks.AIR.defaultBlockState(), 3); p.serverLevel().setBlock(foot.south(), Blocks.AIR.defaultBlockState(), 3); home(p); }), 15));
            // in a boat
            l.add(new Step(null, () -> server(p -> {
                var boat = EntityType.BOAT.create(p.serverLevel());
                boat.moveTo(p.getX(), p.getY(), p.getZ(), 0F, 0F);
                boat.addTag("xw_ha_boat");
                p.serverLevel().addFreshEntity(boat);
                p.startRiding(boat, true);
            }), 15));
            cam(mc, l, 1.85, 0.25, 1.85, 135F, 2F);
            l.add(new Step("huntanim_pose_" + n + "_boat", () -> {}, 3));
            l.add(new Step(null, () -> server(p -> { p.stopRiding(); p.serverLevel().getEntities(EntityType.BOAT, b -> b.getTags().contains("xw_ha_boat")).forEach(b -> b.discard()); home(p); }), 10));
            // swimming in water
            l.add(new Step(null, () -> server(p -> {
                BlockPos c = BlockPos.containing(HOME[0], HOME[1], HOME[2]).west(6);
                p.setGameMode(GameType.SURVIVAL);
                p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 30, 0, false, false));
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 8; dz++) {
                    p.serverLevel().setBlock(c.offset(dx, -4, dz), Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = -3; dy <= 0; dy++) p.serverLevel().setBlock(c.offset(dx, dy, dz), Blocks.WATER.defaultBlockState(), 3);
                }
                p.teleportTo(c.getX() + 0.5, c.getY() - 2.5, c.getZ() + 0.5);
            }), 10));
            l.add(new Step(null, () -> { face(mc); mc.player.setXRot(10F); mc.options.keySprint.setDown(true); mc.options.keyUp.setDown(true); mc.player.setSprinting(true); }, 8));
            l.add(new Step(null, () -> { me(mc); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10F); }, 3));
            l.add(new Step("huntanim_pose_" + n + "_swim", () -> log("pose " + n + " swim pose=" + mc.player.getPose() + " swimming=" + mc.player.isSwimming()), 2));
            l.add(new Step(null, () -> { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); mc.player.setSprinting(false); server(p -> {
                BlockPos c = BlockPos.containing(HOME[0], HOME[1], HOME[2]).west(6);
                p.setGameMode(GameType.CREATIVE);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 8; dz++) for (int dy = -3; dy <= 0; dy++) p.serverLevel().setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                home(p); }); }, 10));
            // gliding with an elytra
            l.add(new Step(null, () -> server(p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 4, 0, false, false));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                p.teleportTo(p.getX(), p.getY() + 30, p.getZ());
            }), 10));
            l.add(new Step(null, () -> server(p -> { p.startFallFlying(); p.setDeltaMovement(0, -0.1, 0.6); }), 4));
            l.add(new Step(null, () -> { me(mc); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 3));
            l.add(new Step("huntanim_pose_" + n + "_elytra", () -> log("pose " + n + " elytra fallFlying=" + mc.player.isFallFlying()), 2));
            l.add(new Step(null, () -> server(p -> { p.stopFallFlying(); bare(p); p.setGameMode(GameType.CREATIVE); home(p); }), 15));
        }
        // the healing pod: asleep inside
        l.add(new Step(null, () -> server(p -> {
            BlockPos at = BlockPos.containing(HOME[0], HOME[1], HOME[2]).offset(0, 0, 4);
            var s = com.xenowarriors.sky.ModBlocks.HEALING_POD.get().defaultBlockState().setValue(com.xenowarriors.gov.HealingPodBlock.FACING, Direction.NORTH);
            p.serverLevel().setBlock(at, s, 3);
            p.serverLevel().setBlock(at.above(), s.setValue(com.xenowarriors.gov.HealingPodBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
            p.setGameMode(GameType.SURVIVAL);
            com.xenowarriors.gov.HealingPods.WAKE = 100;
            p.setHealth(6F);
            log("pod start -> " + com.xenowarriors.gov.HealingPods.start(p.serverLevel(), at, p, 200, null));
        }), 20));
        l.add(new Step(null, () -> { me(mc); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }, 20));
        l.add(new Step("huntanim_pose_pod", () -> log("pose pod " + snapshot(mc)), 3));
        l.add(new Step(null, () -> server(p -> { if (com.xenowarriors.gov.HealingPods.inPod(p)) com.xenowarriors.gov.HealingPods.wake(p); p.setGameMode(GameType.CREATIVE); }), 60));
        l.add(new Step(null, () -> server(p -> { BlockPos at = BlockPos.containing(HOME[0], HOME[1], HOME[2]).offset(0, 0, 4);
            p.serverLevel().setBlock(at.above(), Blocks.AIR.defaultBlockState(), 3); p.serverLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3); home(p); }), 10));
    }

    // ------------------------------------------------------------------ 5. bots side by side

    private static void bots(Minecraft mc, List<Step> l) {
        setChar(l, CharacterType.MIKIKO, 0);
        int[] ids = new int[CharacterType.PLAYABLE.length];
        l.add(new Step(null, () -> server(p -> {
            home(p);
            int i = 0;
            for (CharacterType t : CharacterType.PLAYABLE) {
                if (t == CharacterType.MIKIKO) { ids[i++] = -1; continue; }
                var b = com.xenowarriors.bot.BotEntities.BOT.get().create(p.serverLevel());
                if (b == null) { ids[i++] = -1; continue; }
                b.setCharacter(t); b.setNoAi(true);
                b.moveTo(HOME[0] - 8 + (i % 8) * 2.0, HOME[1], HOME[2] + 5 + (i / 8) * 2.5, 180, 0); b.setYHeadRot(180); b.yBodyRot = 180;
                b.addTag("xw_ha_bot");
                p.serverLevel().addFreshEntity(b);
                ids[i++] = b.getId();
            }
            log("bots placed " + i);
        }), 40));
        cam(mc, l, -1, 1.2, -1.5, 0F, 12F);
        l.add(new Step("huntanim_bots_row", () -> {}, 3));
        cam(mc, l, -1, 6, -3, 0F, 45F);
        l.add(new Step("huntanim_bots_top", () -> {}, 3));
        l.add(new Step(null, () -> server(p -> p.serverLevel().getEntities(com.xenowarriors.bot.BotEntities.BOT.get(), b -> b.getTags().contains("xw_ha_bot")).forEach(b -> b.discard())), 10));
        l.add(new Step(null, () -> me(mc), 3));
    }
}

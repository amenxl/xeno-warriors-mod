package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * UI tests for batch 3 (focus "batch3", "efbatch3" for the wings under Epic Fight, "batch3tacz" for guns; extra words
 * after "batch3" pick parts, e.g. "batch3hud"). Every result is an "XW-TEST batch3 ..." line.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class Batch3Tests {
    private Batch3Tests() {}

    public static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST batch3 " + s); }

    static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { log("step error " + t); XenoWarriors.LOGGER.error("XW-TEST batch3", t); } });
    }

    static void character(MikikoTests.Steps l, CharacterType t) {
        l.add(null, () -> server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t)), 40);
    }

    static void hold(ServerPlayer p, ItemStack s) {
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, s);
    }

    static void view(Minecraft mc, CameraType cam, float yaw, float pitch) {
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.options.setCameraType(cam);
        mc.player.setXRot(pitch); mc.player.setYRot(yaw); mc.player.yBodyRot = yaw; mc.player.setYHeadRot(yaw);
    }

    public static void run(Minecraft mc, String focus, MikikoTests.Steps l) {
        boolean ef = focus.startsWith("ef");
        String f = focus.replaceFirst("^ef", "").replaceFirst("^batch3", "").replace("tacz", "");
        boolean all = f.isEmpty();
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        if (ef || f.contains("wings")) wings(mc, l, ef);
        if (ef) { l.add(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); log("done"); }, 5); return; }
        if (focus.contains("tacz")) { guns(mc, l); l.add(null, () -> log("done"), 5); return; }
        if (all || f.contains("dash")) dash(mc, l);
        if (all || f.contains("torch")) torches(mc, l);
        if (all || f.contains("heal")) foxHeal(mc, l);
        if (all || f.contains("hud")) hud(mc, l);
        if (all || f.contains("magnet")) magnet(mc, l);
        if (all || f.contains("step")) step(mc, l);
        if (all || f.contains("faces")) faces(mc, l);
        if (all || f.contains("famished")) famished(mc, l);
        if (all) wings(mc, l, false);
        l.add(null, () -> { mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); server(Batch3Tests::clearBots); log("done"); }, 5);
    }

    // ------------------------------------------------------------------ helpers

    static String f1(double d) { return String.format(java.util.Locale.ROOT, "%.2f", d); }

    static void clearBots(ServerPlayer p) {
        for (var b : new java.util.ArrayList<>(com.xenowarriors.bot.PartyBots.bots(p))) { com.xenowarriors.bot.PartyBots.forgetFake(b.getUUID()); b.discard(); }
        p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(60), e -> e.getTags().contains("xw_b3")
                || e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb).forEach(net.minecraft.world.entity.Entity::discard);
    }

    static com.xenowarriors.bot.PartyBotEntity bot(ServerPlayer p, CharacterType t, double dx, double dz) {
        com.xenowarriors.bot.PartyBotEntity b = null;
        for (var x : com.xenowarriors.bot.PartyBots.bots(p)) if (x.character() == t) b = x;
        if (b == null) b = com.xenowarriors.bot.PartyBots.spawn(p, t, null);
        if (b == null) { log("spawn " + t.getId() + " FAILED"); return null; }
        b.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180F, 0F);
        b.setTarget(null);
        b.setMode(com.xenowarriors.bot.PartyBotEntity.NEUTRAL);
        return b;
    }

    static <T extends net.minecraft.world.entity.Mob> T mob(ServerPlayer p, net.minecraft.world.entity.EntityType<T> type, double dx, double dz, boolean ai, float hp) {
        T m = type.create(p.serverLevel());
        if (m == null) return null;
        m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180F, 0);
        m.setNoAi(!ai);
        m.setPersistenceRequired();
        m.addTag("xw_b3");
        p.serverLevel().addFreshEntity(m);
        var a = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (a != null) { a.setBaseValue(hp); m.setHealth(hp); }
        return m;
    }

    /** A flat, empty stone pad around the player (nothing in the way). */
    static void pad(ServerPlayer p, int r) {
        var c = p.blockPosition();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            for (int dy = 0; dy <= 5; dy++) p.serverLevel().setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            p.serverLevel().setBlock(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
        p.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
    }

    static void setSpeed(ServerPlayer p, int lv) {
        p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.statLevel[com.xenowarriors.stats.Stat.SPEED.ordinal()] = lv);
        com.xenowarriors.stats.Stats.syncAll(p);
        com.xenowarriors.stats.AutoStep.apply(p);
    }

    // ------------------------------------------------------------------ 5. combo dashes only at someone the hit reaches

    private static void dash(Minecraft mc, MikikoTests.Steps l) {
        for (CharacterType t : new CharacterType[]{CharacterType.RHALIX, CharacterType.MIKIKO}) {
            character(l, t);
            double[] at = new double[3];
            int[] d0 = new int[1];
            l.add(null, () -> server(p -> { clearBots(p); hold(p, ItemStack.EMPTY); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); pad(p, 8); p.setYRot(0F); p.setYHeadRot(0F); }), 15);
            // swinging at the air: he stays put
            l.add(null, () -> { view(mc, CameraType.THIRD_PERSON_BACK, 0F, 10F); at[0] = mc.player.getX(); at[1] = mc.player.getZ(); d0[0] = com.xenowarriors.client.mikiko.DashGuard.dashes; }, 5);
            for (int i = 0; i < 4; i++) l.add(null, () -> com.xenowarriors.client.combo.ComboClient.press(false), 9);
            l.add("batch3_dash_air_" + t.getId(), () -> log("dash " + t.getId() + " at the air: moved " + f1(Math.hypot(mc.player.getX() - at[0], mc.player.getZ() - at[1]))
                    + " blocks, dashes " + (com.xenowarriors.client.mikiko.DashGuard.dashes - d0[0]) + ", last skipped=" + com.xenowarriors.client.mikiko.DashGuard.lastSkipped), 30);
            // someone 3.5 blocks in front: he dashes in on them
            l.add(null, () -> server(p -> { p.teleportTo(Math.floor(p.getX()) + 0.5, p.getY(), Math.floor(p.getZ()) + 0.5); p.setYRot(0F); mob(p, net.minecraft.world.entity.EntityType.ZOMBIE, 0, 3.5, false, 500F); }), 15);
            l.add(null, () -> { view(mc, CameraType.THIRD_PERSON_BACK, 0F, 10F); at[0] = mc.player.getX(); at[1] = mc.player.getZ(); d0[0] = com.xenowarriors.client.mikiko.DashGuard.dashes; }, 5);
            for (int i = 0; i < 3; i++) l.add(null, () -> com.xenowarriors.client.combo.ComboClient.press(false), 9);
            l.add("batch3_dash_foe_" + t.getId(), () -> log("dash " + t.getId() + " at a zombie 3.5 ahead: moved " + f1(Math.hypot(mc.player.getX() - at[0], mc.player.getZ() - at[1]))
                    + " blocks (toward z +" + f1(mc.player.getZ() - at[1]) + "), dashes " + (com.xenowarriors.client.mikiko.DashGuard.dashes - d0[0])), 30);
            // someone too far off (9 blocks): no dash at them either
            l.add(null, () -> server(p -> { clearBots(p); p.teleportTo(Math.floor(p.getX()) + 0.5, p.getY(), Math.floor(p.getZ()) + 0.5); p.setYRot(0F); mob(p, net.minecraft.world.entity.EntityType.ZOMBIE, 0, 9, false, 500F); }), 15);
            l.add(null, () -> { at[0] = mc.player.getX(); at[1] = mc.player.getZ(); d0[0] = com.xenowarriors.client.mikiko.DashGuard.dashes; }, 5);
            for (int i = 0; i < 3; i++) l.add(null, () -> com.xenowarriors.client.combo.ComboClient.press(false), 9);
            l.add(null, () -> log("dash " + t.getId() + " at a zombie 9 ahead: moved " + f1(Math.hypot(mc.player.getX() - at[0], mc.player.getZ() - at[1]))
                    + " blocks, dashes " + (com.xenowarriors.client.mikiko.DashGuard.dashes - d0[0])), 30);
            l.add(null, () -> server(Batch3Tests::clearBots), 5);
        }
    }

    // ------------------------------------------------------------------ 1. bots light up the dark

    private static void torches(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        int[] box = new int[3];
        l.add(null, () -> server(p -> {
            clearBots(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var lv = p.serverLevel();
            int x0 = p.getBlockX() + 30, y0 = p.getBlockY(), z0 = p.getBlockZ();
            box[0] = x0; box[1] = y0; box[2] = z0;
            // a sealed stone room 11x4x11 inside: pitch dark (a cave)
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -1; dy <= 4; dy++) {
                boolean shell = Math.abs(dx) == 6 || Math.abs(dz) == 6 || dy == -1 || dy == 4;
                lv.setBlock(new net.minecraft.core.BlockPos(x0 + dx, y0 + dy, z0 + dz), shell ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
            p.teleportTo(x0 + 0.5, y0, z0 + 0.5);
            var b = bot(p, CharacterType.KAERU, 2.5, 0.5);
            if (b != null) { b.inventory.clearContent(); b.inventory.addItem(new ItemStack(net.minecraft.world.item.Items.TORCH, 6)); }
            var b2 = bot(p, CharacterType.RHALIX, -2.5, 0.5);
            if (b2 != null) b2.inventory.clearContent();                         // (no torches: nothing to place)
            log("torch room at " + x0 + " " + y0 + " " + z0 + " light at bot=" + com.xenowarriors.bot.BotSupport.light(lv, new net.minecraft.core.BlockPos(x0 + 2, y0, z0)));
        }), 40);
        l.add(null, () -> server(p -> {
            var lv = p.serverLevel();
            var b = bot(p, CharacterType.KAERU, 2.5, 0.5);
            if (b != null) b.moveTo(box[0] + 2.5, box[1], box[2] + 0.5);
            log("torch light now " + com.xenowarriors.bot.BotSupport.light(lv, new net.minecraft.core.BlockPos(box[0] + 2, box[1], box[2])) + " (dark below " + com.xenowarriors.bot.BotSupport.DARK + ")");
        }), 60);
        l.add(null, () -> server(p -> log("torch after 3s: " + count(p, box) + " torches in the room, kaeru has " + torchesLeft(p, CharacterType.KAERU) + ", rhalix has " + torchesLeft(p, CharacterType.RHALIX))), 5);
        // walking about the dark: it keeps them spaced (only where it's still dark)
        l.add(null, () -> server(p -> { var b = bot(p, CharacterType.KAERU, 0, 0); if (b != null) b.moveTo(box[0] - 4.5, box[1], box[2] - 4.5); p.teleportTo(box[0] - 4.5, box[1], box[2] - 3.5); }), 60);
        l.add(null, () -> server(p -> { var b = bot(p, CharacterType.KAERU, 0, 0); if (b != null) b.moveTo(box[0] - 3.5, box[1], box[2] - 4.5); }), 60);
        l.add(null, () -> server(p -> log("torch after moving: " + count(p, box) + " torches in the room (spaced: none where it was already lit), kaeru has " + torchesLeft(p, CharacterType.KAERU) + " left")), 5);
        l.add(null, () -> server(p -> p.teleportTo(box[0] - 0.5, box[1], box[2] - 0.5)), 10);
        camera(mc, l, 4.0, 0.8, 4.0, -2.0);
        l.add("batch3_torch_room", () -> {}, 5);
        uncamera(mc, l);
        // in the Tower (a mode arena) it never does
        l.add(null, () -> log("torch arena check: overworld=" + arena(mc, false) + " (false = it may light it)"), 3);
        l.add(null, () -> server(p -> { clearBots(p); var c = new net.minecraft.core.BlockPos(box[0], box[1], box[2]); p.teleportTo(c.getX() + 0.5, c.getY() + 8, c.getZ() + 0.5); }), 5);
    }

    private static boolean arena(Minecraft mc, boolean unused) {
        var s = mc.getSingleplayerServer();
        return s != null && com.xenowarriors.tower.Tower.is(s.overworld());
    }

    private static int count(ServerPlayer p, int[] box) {
        int n = 0;
        for (var q : net.minecraft.core.BlockPos.betweenClosed(box[0] - 5, box[1], box[2] - 5, box[0] + 5, box[1] + 3, box[2] + 5)) {
            var s = p.serverLevel().getBlockState(q);
            if (s.is(net.minecraft.world.level.block.Blocks.TORCH) || s.is(net.minecraft.world.level.block.Blocks.WALL_TORCH)) n++;
        }
        return n;
    }

    private static int torchesLeft(ServerPlayer p, CharacterType t) {
        for (var b : com.xenowarriors.bot.PartyBots.bots(p)) if (b.character() == t) return b.inventory.countItem(net.minecraft.world.item.Items.TORCH);
        return -1;
    }

    // ------------------------------------------------------------------ 4. Kaeru's bot heals the party

    private static void foxHeal(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        float[] k0 = new float[1];
        l.add(null, () -> server(p -> {
            clearBots(p); pad(p, 6);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var k = bot(p, CharacterType.KAERU, 2.5, 1.5);
            var r = bot(p, CharacterType.RHALIX, -2.5, 1.5);
            if (k != null) { k.karma = 100F; k0[0] = k.karma; }
            if (r != null) r.setHealth(r.getMaxHealth());
            p.setHealth(p.getMaxHealth() * 0.3F);
            log("heal start: you " + f1(p.getHealth()) + "/" + f1(p.getMaxHealth()) + ", kaeru karma " + (k == null ? -1 : k.karma));
        }), 50);
        l.add(null, () -> server(p -> {
            var k = bot(p, CharacterType.KAERU, 2.5, 1.5);
            log("heal after 2.5s: you " + f1(p.getHealth()) + "/" + f1(p.getMaxHealth()) + " (regen " + p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION) + "), kaeru karma " + (k == null ? -1 : f1(k.karma)) + ", heals so far " + com.xenowarriors.bot.BotSupport.heals);
            // a hurt bot teammate: she's on cooldown now, so not yet
            var r = bot(p, CharacterType.RHALIX, -2.5, 1.5);
            if (r != null) r.setHealth(r.getMaxHealth() * 0.25F);
        }), 50);
        l.add(null, () -> server(p -> {
            var r = bot(p, CharacterType.RHALIX, -2.5, 1.5);
            log("heal cooldown: rhalix bot " + (r == null ? -1 : f1(r.getHealth())) + "/" + (r == null ? -1 : f1(r.getMaxHealth())) + ", heals so far " + com.xenowarriors.bot.BotSupport.heals + " (still 1: Fox Heal's cooldown)");
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.setHealth(p.getMaxHealth());
            clearBots(p);
        }), 5);
    }

    // ------------------------------------------------------------------ 7. party HUD numbers in every style

    private static void hud(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        String was = com.xenowarriors.client.ClientSettings.hudStyle();
        l.add(null, () -> server(p -> {
            clearBots(p); pad(p, 6);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var k = bot(p, CharacterType.KAERU, 2.5, 3.5);
            var r = bot(p, CharacterType.RHALIX, -2.5, 3.5);
            if (k != null) { k.setHealth(k.getMaxHealth() * 0.62F); k.karma = 47F; }
            if (r != null) { r.setHealth(r.getMaxHealth() * 0.33F); r.karma = 81F; }
            p.setHealth(p.getMaxHealth() * 0.8F);
            com.xenowarriors.karma.Karma.add(p, 63F - com.xenowarriors.karma.Karma.get(p));
        }), 60);
        for (String st : new String[]{"blazing", "kh", "classic"}) {
            l.add(null, () -> { com.xenowarriors.client.ClientSettings.setHudStyle(st); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setXRot(10F); }, 30);
            l.add("batch3_hud_" + st, () -> {
                StringBuilder sb = new StringBuilder("hud " + st + " members:");
                for (var m : com.xenowarriors.client.party.PartyClient.members())
                    sb.append(" ").append(m.name()).append(" hp ").append(com.xenowarriors.client.party.PartyHud.nums(m.health(), m.maxHealth())).append(" karma ").append(com.xenowarriors.client.party.PartyHud.nums(m.karma(), m.maxKarma())).append(";");
                sb.append(" you hp ").append(com.xenowarriors.client.party.PartyHud.nums(mc.player.getHealth(), mc.player.getMaxHealth()))
                        .append(" karma ").append(com.xenowarriors.client.party.PartyHud.nums(com.xenowarriors.client.ClientSkillState.karma(), com.xenowarriors.client.ClientSkillState.maxKarma()));
                log(sb.toString());
            }, 2);
        }
        l.add(null, () -> { com.xenowarriors.client.ClientSettings.setHudStyle(was); server(p -> { p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.setHealth(p.getMaxHealth()); clearBots(p); }); }, 5);
    }

    // ------------------------------------------------------------------ 8. Rhalix's Gravity Magnet

    private static void magnet(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.RHALIX);
        java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
        l.add(null, () -> server(p -> { clearBots(p); pad(p, 12); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); com.xenowarriors.stats.PlayerOptions.set(p, com.xenowarriors.stats.PlayerOptions.MAGNET, true); }), 15);
        // the menu button
        l.add(null, () -> { mc.options.hideGui = false; com.xenowarriors.client.menu.CharacterCustomScreen.open(null); }, 30);
        l.add("batch3_magnet_menu", () -> log("magnet menu option on=" + com.xenowarriors.client.ClientOptions.get(com.xenowarriors.stats.PlayerOptions.MAGNET)), 3);
        l.add(null, () -> mc.setScreen(null), 5);
        Runnable drop = () -> server(p -> {
            ids.clear();
            double[][] at = {{6, 4}, {-5, 6}, {7, -3}, {0, 13}};                  // (the last one is 13 blocks off: out of range)
            for (double[] a : at) {
                var it = new net.minecraft.world.entity.item.ItemEntity(p.serverLevel(), p.getX() + a[0], p.getY() + 0.2, p.getZ() + a[1], new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 3));
                it.setDeltaMovement(0, 0, 0); it.addTag("xw_b3");
                p.serverLevel().addFreshEntity(it); ids.add(it.getUUID());
            }
            var orb = new net.minecraft.world.entity.ExperienceOrb(p.serverLevel(), p.getX() - 6, p.getY() + 0.2, p.getZ() - 5, 5);
            p.serverLevel().addFreshEntity(orb); ids.add(orb.getUUID());
        });
        java.util.function.Function<ServerPlayer, String> where = p -> {
            StringBuilder sb = new StringBuilder();
            for (var id : ids) { var e = p.serverLevel().getEntity(id); sb.append(e == null ? " picked-up" : " " + f1(e.distanceTo(p))); }
            return sb.toString();
        };
        camera(mc, l, 3.5, 1.2, -4.5, 1.0);
        l.add(null, () -> { drop.run(); server(p -> log("magnet on: dropped at" + where.apply(p))); }, 7);
        l.add("batch3_magnet_pull", () -> server(p -> log("magnet on after 0.35s:" + where.apply(p))), 1);
        l.add(null, () -> server(p -> log("magnet on after 1s:" + where.apply(p))), 12);
        l.add(null, () -> server(p -> log("magnet on after 2s:" + where.apply(p) + " (pulled ticks " + com.xenowarriors.rhalix.GravityMagnet.pulled + ")")), 30);
        uncamera(mc, l);
        // off: they stay where they fall
        l.add(null, () -> server(p -> { clearBots(p); com.xenowarriors.stats.PlayerOptions.set(p, com.xenowarriors.stats.PlayerOptions.MAGNET, false); }), 5);
        l.add(null, () -> { drop.run(); server(p -> log("magnet off: dropped at" + where.apply(p) + " (client sees on=" + com.xenowarriors.client.ClientOptions.get(com.xenowarriors.stats.PlayerOptions.MAGNET) + ")")); }, 40);
        l.add(null, () -> server(p -> log("magnet off after 2s:" + where.apply(p))), 5);
        // a Rhalix bot: things come to the bot (its owner a Mikiko here, far from the items)
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> server(p -> {
            clearBots(p); com.xenowarriors.stats.PlayerOptions.set(p, com.xenowarriors.stats.PlayerOptions.MAGNET, true);
            var b = bot(p, CharacterType.RHALIX, 0, 0);
            if (b == null) return;
            b.moveTo(p.getX() + 3, p.getY(), p.getZ()); b.setNoAi(true);
            ids.clear();
            var it = new net.minecraft.world.entity.item.ItemEntity(p.serverLevel(), b.getX() + 7, b.getY() + 0.2, b.getZ() + 1, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
            it.setPickUpDelay(10000); it.addTag("xw_b3"); p.serverLevel().addFreshEntity(it); ids.add(it.getUUID());
            log("magnet bot: item " + f1(it.distanceTo(b)) + " from the Rhalix bot, " + f1(it.distanceTo(p)) + " from you (a Mikiko: no magnet)");
        }), 40);
        l.add(null, () -> server(p -> {
            var b = bot(p, CharacterType.RHALIX, 0, 0);
            var e = ids.isEmpty() ? null : p.serverLevel().getEntity(ids.get(0));
            log("magnet bot after 2s: item " + (e == null || b == null ? "gone" : f1(e.distanceTo(b))) + " from the Rhalix bot, " + (e == null ? "-" : f1(e.distanceTo(p))) + " from you");
            if (b != null) b.setNoAi(false);
            clearBots(p);
        }), 5);
    }

    // ------------------------------------------------------------------ 9. auto step-up with Speed over 100

    private static void step(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        int[] lv0 = new int[1];
        l.add(null, () -> server(p -> { lv0[0] = com.xenowarriors.stats.Stats.level(p, com.xenowarriors.stats.Stat.SPEED); com.xenowarriors.client.ClientSettings.setAutoStep(true); }), 5);
        for (int lv : new int[]{50, 101, 199, 201, 350}) {
            l.add(null, () -> server(p -> setSpeed(p, lv)), 25);
            l.add(null, () -> server(p -> log("step speed " + lv + ": server step " + f1(stepOf(p)) + ", client step " + f1(stepOf(mc.player)) + " (expected blocks " + com.xenowarriors.stats.AutoStep.blocks(lv) + ")")), 3);
        }
        l.add(null, () -> com.xenowarriors.client.ClientSettings.setAutoStep(false), 25);
        l.add(null, () -> server(p -> log("step setting off at speed 350: server step " + f1(stepOf(p)) + ", client step " + f1(stepOf(mc.player)))), 3);
        l.add(null, () -> com.xenowarriors.client.ClientSettings.setAutoStep(true), 25);
        // walking into a one-block step at Speed 150: up it without a jump
        double[] y0 = new double[1];
        l.add(null, () -> server(p -> {
            setSpeed(p, 150); pad(p, 6);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var c = p.blockPosition();
            for (int dx = -2; dx <= 2; dx++) for (int dz = 2; dz <= 6; dz++) p.serverLevel().setBlock(c.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            p.setYRot(0F);
        }), 30);
        l.add(null, () -> { view(mc, CameraType.THIRD_PERSON_BACK, 0F, 15F); y0[0] = mc.player.getY(); com.xenowarriors.client.test.Batch3Tests.walk = 30; }, 32);
        l.add("batch3_step_up", () -> log("step walked into a 1-block step: y " + f1(y0[0]) + " -> " + f1(mc.player.getY()) + " (jumped=" + jumped + ")"), 3);
        l.add(null, () -> server(p -> { setSpeed(p, lv0[0]); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }), 5);
    }

    /** How high it steps up without jumping: the vanilla height plus Forge's step-height attribute. */
    static double stepOf(net.minecraft.world.entity.LivingEntity e) {
        var a = e.getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get());
        return e.maxUpStep() + (a == null ? 0 : a.getValue());
    }

    /** (test) holds the local player on this spot every tick (null = off). */
    static double[] pin;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        var pl = Minecraft.getInstance().player;
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || pin == null || pl == null) return;
        pl.setPos(pin[0], pin[1], pin[2]);
        pl.setDeltaMovement(0, Math.min(0, pl.getDeltaMovement().y), 0);
    }

    /** (test) ticks left holding "forward" for the local player, and whether it jumped meanwhile. */
    public static int walk;
    static boolean jumped;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onInput(net.minecraftforge.client.event.MovementInputUpdateEvent e) {
        if (walk <= 0) return;
        walk--;
        e.getInput().forwardImpulse = 1F; e.getInput().up = true;
        e.getInput().jumping = false;
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getDeltaMovement().y > 0.3) jumped = true;
    }

    // ------------------------------------------------------------------ 10. starving faces

    private static void faces(Minecraft mc, MikikoTests.Steps l) {
        for (CharacterType t : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX, CharacterType.KAERU, CharacterType.LOGAN, CharacterType.TECHNICA}) {
            character(l, t);
            l.add(null, () -> server(p -> { clearBots(p); hold(p, ItemStack.EMPTY); p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.getFoodData().setFoodLevel(0); p.setHealth(p.getMaxHealth()); }), 30);
            l.add(null, () -> { view(mc, CameraType.THIRD_PERSON_FRONT, 0F, 0F); mc.options.hideGui = false; }, 20);
            l.add("batch3_starving_hud_" + t.getId(), () -> {}, 2);
            l.add(null, () -> server(p -> { p.setYRot(0F); p.setYHeadRot(0F); p.yBodyRot = 0F; p.setXRot(0F); }), 3);
            camera(mc, l, 0, -0.12, 1.3, 0, 2F);
            l.add("batch3_starving_" + t.getId(), () -> log("starving " + t.getId() + ": food " + mc.player.getFoodData().getFoodLevel() + " starving=" + com.xenowarriors.client.ClientSkillState.starving(mc.player)
                    + " face=" + com.xenowarriors.client.render.CharacterDetailLayer.faceTexture(mc.player) + " hud=" + com.xenowarriors.client.HudFaces.face(mc.player.getUUID(), mc.player.getHealth(), mc.player.getMaxHealth(), false, false)
                    + " portrait=" + com.xenowarriors.client.HudFaces.texture(t, "starving")), 2);
            uncamera(mc, l);
            l.add(null, () -> server(p -> { p.getFoodData().setFoodLevel(20); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }), 20);
            l.add(null, () -> log("fed " + t.getId() + ": starving=" + com.xenowarriors.client.ClientSkillState.starving(mc.player) + " face=" + com.xenowarriors.client.render.CharacterDetailLayer.faceTexture(mc.player)), 2);
        }
        // seen by others: a starving bot-owner's teammate sees it through the status sync (flag on the server)
        l.add(null, () -> server(p -> log("starving status flag sent for food<=" + com.xenowarriors.mikiko.MikikoCombat.STARVING_FOOD)), 2);
    }

    // ------------------------------------------------------------------ 11. Famished (Tower floors, Journey battles)

    private static void famished(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.MIKIKO);
        l.add(null, () -> {
            StringBuilder sb = new StringBuilder("famished chance tower:");
            for (int fl : new int[]{1, 9, 10, 25, 50, 100, 133, 200}) sb.append(" f").append(fl).append("=").append(Math.round(com.xenowarriors.tower.TowerMod.famishedChance(fl) * 100)).append("%");
            sb.append("; journey:");
            for (int d = 0; d < 5; d++) sb.append(" ").append(com.xenowarriors.journey.JourneyRules.DIFF[d]).append("=").append(Math.round(com.xenowarriors.journey.Journey.famishedChance(d) * 100)).append("%");
            log(sb.toString());
            int hits = 0; var r = net.minecraft.util.RandomSource.create(7);
            for (int i = 0; i < 2000; i++) if (r.nextFloat() < com.xenowarriors.tower.TowerMod.famishedChance(60)) hits++;
            log("famished tower floor 60 rolled " + hits + "/2000 (" + Math.round(hits / 20F) + "%)");
        }, 3);
        l.add(null, () -> server(p -> { p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); com.xenowarriors.tower.Tower.enter(p); }), 80);
        l.add(null, () -> server(p -> { com.xenowarriors.tower.TowerMod.forceFamished = true; com.xenowarriors.tower.Tower.skipTo(p, 32); }), 110);
        l.add("batch3_famished_tower", () -> { mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p -> log("famished tower: " + com.xenowarriors.tower.Tower.testInfo(p) + " hunger effect=" + p.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER))); }, 3);
        l.add(null, () -> server(p -> { com.xenowarriors.tower.TowerMod.forceFamished = false; com.xenowarriors.tower.Tower.skipTo(p, 33); }), 110);
        l.add(null, () -> server(p -> log("famished tower next floor: " + com.xenowarriors.tower.Tower.testInfo(p) + " hunger effect=" + p.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER))), 60);
        l.add(null, () -> server(p -> log("famished tower 3s later: hunger effect=" + p.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER))), 3);
        l.add(null, () -> server(p -> { com.xenowarriors.tower.Tower.leave(p, "test over"); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); }), 60);
    }

    // ------------------------------------------------------------------ 2 + 3. guns (TACZ): bots reload and pace their shots; you don't combo

    private static void guns(Minecraft mc, MikikoTests.Steps l) {
        character(l, CharacterType.RHALIX);
        l.add(null, () -> server(p -> {
            clearBots(p); pad(p, 14);
            ItemStack g = com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30);
            hold(p, g);
            log("gun: tacz=" + com.xenowarriors.compat.TaczGuns.available() + " isGun=" + com.xenowarriors.item.Guns.isGun(g) + " tag=" + g.is(com.xenowarriors.item.Guns.TAG)
                    + " stats(mag,reload,rpm)=" + java.util.Arrays.toString(com.xenowarriors.compat.TaczGuns.stats(g))
                    + " combo style=" + com.xenowarriors.combat.ComboStyle.of(p, g, CharacterType.RHALIX) + " (bare hands: " + com.xenowarriors.combat.ComboStyle.of(p, ItemStack.EMPTY, CharacterType.RHALIX) + ")");
        }), 20);
        double[] at = new double[2];
        l.add(null, () -> { view(mc, CameraType.THIRD_PERSON_BACK, 0F, 5F); at[0] = mc.player.getX(); at[1] = mc.player.getZ(); }, 5);
        for (int i = 0; i < 4; i++) l.add(null, () -> com.xenowarriors.client.combo.ComboClient.press(false), 6);
        l.add("batch3_gun_player", () -> log("gun player clicks: swing=" + com.xenowarriors.client.combo.ComboClient.swing(mc.player) + " moved " + f1(Math.hypot(mc.player.getX() - at[0], mc.player.getZ() - at[1]))
                + " attacking anim=" + com.xenowarriors.client.RhalixCombatAnim.attacking(mc.player)), 3);
        // a bot with a gun against a tough target: bursts, a capped rate, a reload when the magazine's empty
        int[] shots0 = new int[1];
        long[] t0 = new long[1];
        l.add(null, () -> server(p -> {
            hold(p, ItemStack.EMPTY);
            var b = bot(p, CharacterType.TECHNICA, 2, 0);
            if (b == null) return;
            b.inventory.clearContent();
            b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, com.xenowarriors.compat.TaczGuns.gun("tacz:m4a1", 30));
            var z = mob(p, net.minecraft.world.entity.EntityType.IRON_GOLEM, 2, 13, false, 5000F);
            b.setMode(com.xenowarriors.bot.PartyBotEntity.AGGRESSIVE);
            b.setTarget(z);
            shots0[0] = b.shots; t0[0] = p.serverLevel().getGameTime();
            log("gun bot start: holding " + b.getMainHandItem() + " ammo " + com.xenowarriors.compat.TaczGuns.ammo(b.getMainHandItem()));
        }), 5);
        camera(mc, l, -4.0, 1.0, -3.0);
        l.add("batch3_gun_bot", () -> {}, 120);
        for (int i = 0; i < 4; i++) l.add(null, () -> server(p -> {
            var b = bot(p, CharacterType.TECHNICA, 2, 0);
            var z = p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.IronGolem.class, p.getBoundingBox().inflate(30), e -> e.getTags().contains("xw_b3")).stream().findFirst().orElse(null);
            if (b == null) return;
            if (z != null && b.getTarget() == null) b.setTarget(z);
            long dt = p.serverLevel().getGameTime() - t0[0];
            int n = b.shots - shots0[0];
            log("gun bot after " + dt + " ticks: " + n + " shots (" + f1(n * 20.0 / Math.max(1, dt)) + "/s), ammo " + com.xenowarriors.compat.TaczGuns.ammo(b.getMainHandItem()) + (z == null ? "" : ", golem hp " + f1(z.getHealth())));
        }), 100);
        uncamera(mc, l);
        l.add(null, () -> server(Batch3Tests::clearBots), 5);
    }

    // ------------------------------------------------------------------ 6. Rhalix's wings (under Epic Fight)

    /** An invisible armor stand to film from, at this offset from the player, looking at them. */
    static void camera(Minecraft mc, MikikoTests.Steps l, double dx, double dy, double dz) { camera(mc, l, dx, dy, dz, 0); }

    /** ... looking at a spot {@code aimZ} blocks south of them. */
    static void camera(Minecraft mc, MikikoTests.Steps l, double dx, double dy, double dz, double aimZ) {
        double ez = dz - aimZ;
        camera(mc, l, dx, dy, dz, aimZ, (float) Math.toDegrees(Math.atan2(dy + 0.7, Math.sqrt(dx * dx + ez * ez))));   // (the stand's eyes are 1.8 up)
    }

    static void camera(Minecraft mc, MikikoTests.Steps l, double dx, double dy, double dz, double aimZ, float pitch) {
        int[] id = {-1};
        double ez = dz - aimZ;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -ez));
        l.add(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(40), a -> a.getTags().contains("xw_b3_cam")).forEach(a -> a.discard());
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(yaw);
            a.addTag("xw_b3_cam");
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
        }), 15);
        l.add(null, () -> {
            if (mc.level.getEntity(id[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
        }, 5);
    }

    static void uncamera(Minecraft mc, MikikoTests.Steps l) {
        l.add(null, () -> { mc.setCameraEntity(mc.player); server(p -> p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(40), a -> a.getTags().contains("xw_b3_cam")).forEach(a -> a.discard())); }, 3);
    }

    private static void wings(Minecraft mc, MikikoTests.Steps l, boolean ef) {
        character(l, CharacterType.RHALIX);
        double[] home = new double[3];
        l.add(null, () -> server(p -> {
            hold(p, ItemStack.EMPTY); p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var c = p.blockPosition();
            home[0] = c.getX() + 0.5; home[1] = c.getY(); home[2] = c.getZ() + 0.5;
            for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 9; dz++) for (int dy = 0; dy <= 4; dy++) p.serverLevel().setBlock(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            p.teleportTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5); p.setYRot(0F); p.setYHeadRot(0F); p.yBodyRot = 0F;
        }), 10);
        boolean wasWhole = com.xenowarriors.client.ClientSettings.efWholeBattleMode();
        if (ef) l.add(null, () -> com.xenowarriors.client.ClientSettings.setEfWholeBattleMode(true), 2);   // (Epic Fight draws him all the time, its own moves too)
        String[] anims = ef ? new String[]{"none", "ef:BIPED_ROLL_FORWARD", "ef:BIPED_STEP_LEFT", "ef:BIPED_STEP_BACKWARD"} : new String[]{"none"};
        double[][] cams = {{-2.4, 0.3, -2.8}, {3.6, -0.1, 0.0}};                // behind his left shoulder; his right side
        for (int ci = 0; ci < cams.length; ci++) {
            final int cam = ci;
            camera(mc, l, cams[ci][0], cams[ci][1], cams[ci][2], 0);
            l.add(null, () -> pin = new double[]{home[0], home[1], home[2]}, 1);   // (kept on the spot: the dodges would carry him out of the picture)
            for (String a0 : ef ? new String[]{"old:", ""} : new String[]{""}) for (String a1 : anims) {
                String a = a1; boolean old = !a0.isEmpty();
                String n = (old ? "old_" : "") + a.replace("ef:", "").toLowerCase(java.util.Locale.ROOT);
                if (ef) l.add(null, () -> setLegacy(old), 1);
                l.add(null, () -> {
                    server(p -> { p.teleportTo(home[0], home[1], home[2]); p.setYRot(0F); p.setYHeadRot(0F); p.yBodyRot = 0F; });
                    mc.player.setYRot(0F); mc.player.yBodyRot = 0F; mc.player.setYHeadRot(0F);
                    com.xenowarriors.client.RhalixWingsClient.set(mc.player.getUUID(), true, false);
                }, 8);
                l.add(null, () -> { if (ef) com.xenowarriors.compat.epicfight.EfTest.play(mc.player, a); }, 2);
                for (int k = 0; k < 4; k++) {
                    String shot = "batch3_wings_c" + cam + "_" + n + "_" + k;
                    l.add(shot, () -> { com.xenowarriors.client.RhalixWingsClient.set(mc.player.getUUID(), true, false);
                        log("wings " + shot + " grow=" + com.xenowarriors.client.RhalixWingsClient.grow(mc.player, 0F)
                                + (ef ? " " + com.xenowarriors.client.compat.epicfight.EpicFightCompat.debugDraw(mc.player) + " efWingDraws=" + efWingDraws() : "")); }, 2);
                }
                l.add(null, () -> { if (ef) com.xenowarriors.compat.epicfight.EfTest.play(mc.player, "none"); }, 16);
            }
        }
        l.add(null, () -> pin = null, 1);
        uncamera(mc, l);
        if (ef) l.add(null, () -> { setLegacy(false); com.xenowarriors.client.ClientSettings.setEfWholeBattleMode(wasWhole); }, 2);
    }

    /** The wing fix off (the old baked wings) or on, for before/after shots. */
    private static void setLegacy(boolean on) {
        try { com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.class.getField("legacyWings").setBoolean(null, on); } catch (Throwable ignored) {}
    }

    /** Epic Fight's wing draws so far (-1 before the fix / without Epic Fight). */
    private static int efWingDraws() {
        try { return com.xenowarriors.client.compat.epicfight.DetailPatchedLayer.class.getField("wingDraws").getInt(null); } catch (Throwable t) { return -1; }
    }
}

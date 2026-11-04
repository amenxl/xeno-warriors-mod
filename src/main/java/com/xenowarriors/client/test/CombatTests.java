package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * UI tests for the combat area (focus "combat..." and, with DragonMineZ, "dmzcombat..."):
 * allies left alone by skills, the bigger Xeno Blast, health orbs, Ultima's poison immunity and sight, lock-on and
 * flying souls; with DragonMineZ the skill damage scaling table, the tamed Xenoverse Robot and arena terrain.
 * Results are logged as "XW-TEST combat ...".
 */
public final class CombatTests {
    private CombatTests() {}

    /** Adds one step: a screenshot name (or null), what to do, ticks to wait after. */
    public interface Sink { void add(String shot, Runnable action, int delay); }

    static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST combat " + s); }

    static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    static void server(Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> {
            ServerPlayer p = sp();
            if (p == null) return;
            try { f.accept(p); } catch (Throwable t) { log("ERROR " + t + " @ " + (t.getStackTrace().length > 0 ? t.getStackTrace()[0] : "")); }
        });
    }

    /** No screens (the fox's naming box...) and no chat over a screenshot. */
    static void clean(Minecraft mc) {
        if (mc.screen != null) mc.setScreen(null);
        mc.gui.getChat().clearMessages(false);
        mc.getToasts().clear();
    }

    static void character(CharacterType t) { server(p -> com.xenowarriors.character.CharacterManager.setCharacter(p, t)); }

    public static boolean handles(String focus) { return focus != null && (focus.startsWith("combat") || focus.startsWith("dmzcombat")); }

    public static void build(Minecraft mc, Sink l, String focus) {
        l.add(null, () -> { mc.getToasts().clear(); mc.setScreen(null); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 10);
        boolean all = focus.equals("combat") || focus.equals("dmzcombat");
        if (focus.startsWith("dmz")) {
            if (all || focus.contains("scale")) dmzScaling(mc, l);
            if (all || focus.contains("robot")) robot(mc, l);
            if (all || focus.contains("terrain")) terrain(mc, l);
            if (focus.contains("xeno")) xenoBlast(mc, l);
            return;
        }
        if (all || focus.contains("allies")) allies(mc, l);
        if (all || focus.contains("xeno")) xenoBlast(mc, l);
        if (all || focus.contains("orbs")) orbs(mc, l);
        if (all || focus.contains("ultima")) ultima(mc, l);
        if (all || focus.contains("soul")) souls(mc, l);
    }

    // ------------------------------------------------------------------ stage helpers
    static final Map<String, LivingEntity> E = new LinkedHashMap<>();
    static final Map<String, Vec3> POS = new LinkedHashMap<>();
    static final Map<String, Float> HP = new LinkedHashMap<>();

    /** A flat stone floor and air above, around the player. */
    static void stage(ServerPlayer p, int r) {
        ServerLevel l = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            for (int dy = 0; dy <= 6; dy++) l.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            l.setBlock(c.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        }
        for (var m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r + 16), m -> !(m instanceof com.xenowarriors.bot.PartyBotEntity))) m.discard();
    }

    static <T extends Mob> T mob(ServerPlayer p, EntityType<T> type, String name, double dx, double dz) {
        T m = type.create(p.serverLevel());
        if (m == null) return null;
        m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0, 0);
        m.setNoAi(true);
        m.setPersistenceRequired();
        p.serverLevel().addFreshEntity(m);
        E.put(name, m);
        return m;
    }

    static void snapshot() {
        POS.clear(); HP.clear();
        for (var e : E.entrySet()) { POS.put(e.getKey(), e.getValue().position()); HP.put(e.getKey(), e.getValue().getHealth()); }
    }

    static void compare(String what) {
        for (var e : E.entrySet()) {
            LivingEntity x = e.getValue();
            Vec3 was = POS.get(e.getKey());
            double moved = was == null ? -1 : x.position().distanceTo(was);
            float lost = HP.getOrDefault(e.getKey(), x.getHealth()) - x.getHealth();
            log(String.format("%s %s moved=%.2f hpLost=%.1f alive=%b ally=%b", what, e.getKey(), moved, lost, x.isAlive(),
                    sp() != null && com.xenowarriors.combat.Allies.isAlly(sp(), x)));
        }
    }

    // ------------------------------------------------------------------ 1) allies
    static void allies(Minecraft mc, Sink l) {
        l.add(null, () -> { character(CharacterType.MIKIKO); server(p -> {
            p.setGameMode(GameType.SURVIVAL); p.setYRot(0); p.setXRot(10);
            p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            stage(p, 14);
            E.clear();
            // a party mate (a stand-in player), their tamed wolf, my fox, my bot, an enemy
            ServerPlayer mate = net.minecraftforge.common.util.FakePlayerFactory.get(p.serverLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("CombatMate".getBytes()), "CombatMate"));
            com.xenowarriors.party.Parties.testParty(p.getUUID(), mate.getUUID());
            var wolf = mob(p, EntityType.WOLF, "mates_wolf", 3, 6);
            if (wolf != null) { wolf.setTame(true); wolf.setOwnerUUID(mate.getUUID()); }
            var fox = mob(p, EntityType.FOX, "my_fox", -3, 6);
            if (fox != null) com.xenowarriors.fox.FoxPets.tameNow(p, fox);
            var bot = com.xenowarriors.bot.PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (bot != null) { bot.moveTo(p.getX() + 6, p.getY(), p.getZ() + 9); bot.setNoAi(true); E.put("my_bot", bot); }
            mob(p, EntityType.ZOMBIE, "enemy_zombie", 0, 12);
            mob(p, EntityType.HUSK, "enemy_husk", -6, 9);
            for (var e : E.values()) {
                e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); e.setHealth(200);
                if (e instanceof Mob m) m.setNoAi(false);                               // (AI on, so pushes and pulls move them...)
                e.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);                 // (...but they can't walk anywhere themselves)
                if (e instanceof Mob m) m.goalSelector.removeAllGoals(g -> true);
                if (e instanceof Mob m) m.targetSelector.removeAllGoals(g -> true);
            }
            log("allies setup: mate party=" + com.xenowarriors.party.Parties.sameParty(p.getUUID(), mate.getUUID()) + " ents=" + E.keySet());
        }); }, 40);
        // the ally table
        l.add(null, () -> server(p -> {
            ServerPlayer mate = net.minecraftforge.common.util.FakePlayerFactory.get(p.serverLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("CombatMate".getBytes()), "CombatMate"));
            com.xenowarriors.soul.SoulSplit.start(p);
            var souls = p.serverLevel().getEntitiesOfClass(com.xenowarriors.soul.SoulEntity.class, p.getBoundingBox().inflate(6));
            if (!souls.isEmpty()) { var s = souls.get(0); s.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); s.goalSelector.removeAllGoals(g -> true); s.targetSelector.removeAllGoals(g -> true); E.put("my_soul", s); }
            log("isAlly self=" + com.xenowarriors.combat.Allies.isAlly(p, p) + " mate=" + com.xenowarriors.combat.Allies.isAlly(p, mate)
                    + " mate->my_fox=" + com.xenowarriors.combat.Allies.isAlly(mate, E.get("my_fox")));
            for (var e : E.entrySet()) log("isAlly " + e.getKey() + "=" + com.xenowarriors.combat.Allies.isAlly(p, e.getValue())
                    + " canTarget=" + com.xenowarriors.skill.SkillTargeting.canTarget(p, e.getValue()));
            var bot = (com.xenowarriors.bot.PartyBotEntity) E.get("my_bot");
            if (bot != null) { bot.setSparring(true); log("sparring bot vs owner: isAlly=" + com.xenowarriors.combat.Allies.isAlly(bot, p) + " (opponents while sparring)"); bot.setSparring(false); }
            snapshot();
            com.xenowarriors.combat.SkillContext.run(p, () -> new com.xenowarriors.skill.mikiko.BlazingWindSkill().use(p));
            log("blazing wind cast");
        }), 70);
        l.add(null, () -> { clean(mc); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }, 2);
        l.add("combat_allies_blazing", () -> clean(mc), 50);
        l.add(null, () -> server(p -> compare("blazing mid")), 160);
        l.add(null, () -> { clean(mc); mc.options.setCameraType(CameraType.FIRST_PERSON); }, 1);
        l.add(null, () -> server(p -> compare("blazing end")), 20);
        // Xeno Blast's freeze: only enemies are frozen/caught
        l.add(null, () -> server(p -> {
            for (var e : E.values()) if (e.isAlive()) { e.setHealth(e.getMaxHealth()); }
            snapshot();
            p.getPersistentData().remove("xw_karma_lock_until"); com.xenowarriors.skill.SkillManager.resetCooldowns(p); com.xenowarriors.karma.Karma.add(p, 200);
            p.setYRot(0); p.setXRot(5);
            com.xenowarriors.skill.SkillManager.useSkill(p, com.xenowarriors.skill.SkillManager.SUPER_SLOT);
            StringBuilder s = new StringBuilder();
            for (var e : E.entrySet()) s.append(e.getKey()).append("=").append(frozen(e.getValue())).append(' ');
            log("xenoblast frozen: " + s);
        }), 260);
        l.add(null, () -> server(p -> compare("xenoblast")), 20);
        // a projectile (Earth bullet style: Fire Blitz) aimed through the mate's wolf at the zombie
        l.add(null, () -> server(p -> {
            com.xenowarriors.soul.SoulSplit.end(p, null);
            com.xenowarriors.party.Parties.leave(net.minecraftforge.common.util.FakePlayerFactory.get(p.serverLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("CombatMate".getBytes()), "CombatMate")), false);
            p.getAbilities().invulnerable = false; p.onUpdateAbilities();
            for (var e : E.values()) if (e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player)) e.discard();
            E.clear();
        }), 5);
    }

    static String frozen(LivingEntity e) {
        try { return String.valueOf(com.xenowarriors.combat.Frozen.is(e)); } catch (Throwable t) { return "?"; }
    }

    // ------------------------------------------------------------------ 2) Xeno Blast
    static void xenoBlast(Minecraft mc, Sink l) {
        l.add(null, () -> server(p -> {
            log(String.format("xenoblast formula: range old=40 new=%d | at 100 Karma dmg old=%.1f new=%.1f | width old=%.2f new=%.2f | Rhalix burst radius old=%.2f new=%.2f",
                    com.xenowarriors.skill.XenoBlast.RANGE, 12F + 100 * 0.5F, com.xenowarriors.skill.XenoBlast.dmgOf(100),
                    1.2 + Math.min(2.0, 100 / 80.0), com.xenowarriors.skill.XenoBlast.widthOf(100), 2.5 + 1.2 + Math.min(2.0, 100 / 80.0),
                    com.xenowarriors.skill.XenoBlast.burstRadius(com.xenowarriors.skill.XenoBlast.widthOf(100))));
            log(String.format("xenoblast formula: at 30 Karma dmg old=%.1f new=%.1f", 12F + 30 * 0.5F, com.xenowarriors.skill.XenoBlast.dmgOf(30)));
        }), 2);
        for (CharacterType ct : new CharacterType[]{CharacterType.MIKIKO, CharacterType.RHALIX}) {
            String n = ct.getId();
            l.add(null, () -> { character(ct); server(p -> {
                p.setGameMode(GameType.SURVIVAL); p.setYRot(0); p.setXRot(0);
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
                stage(p, 6);
                ServerLevel lv = p.serverLevel();
                BlockPos c = p.blockPosition();
                for (int dz = 7; dz <= 70; dz++) for (int dx = -12; dx <= 12; dx++) {                // a long clear lane
                    for (int dy = 0; dy <= 8; dy++) lv.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                    lv.setBlock(c.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                }
                for (int dx = -6; dx <= 6; dx++) for (int dy = 0; dy <= 6; dy++) lv.setBlock(c.offset(dx, dy, 56), Blocks.STONE.defaultBlockState(), 2);   // a wall at 56
                E.clear();
                mob(p, EntityType.HUSK, "z_at_20", 0, 20);
                mob(p, EntityType.HUSK, "z_at_50", 0, 50);                   // past the old 40-block reach
                mob(p, EntityType.HUSK, "z_side_50", 4.5, 53);               // next to where it lands
                for (var e : E.values()) { e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); e.setHealth(1000); }
                snapshot();
            }); }, 30);
            l.add(null, () -> server(p -> {
                p.getPersistentData().remove("xw_karma_lock_until"); com.xenowarriors.skill.SkillManager.resetCooldowns(p);
                com.xenowarriors.karma.Karma.add(p, 200);
                float k = com.xenowarriors.karma.Karma.get(p);
                p.setYRot(0); p.setXRot(0);
                p.setGameMode(GameType.CREATIVE);                   // (creative: full 100 power whatever Karma)
                com.xenowarriors.skill.SkillManager.useSkill(p, com.xenowarriors.skill.SkillManager.SUPER_SLOT);
                log("xenoblast " + n + " cast karma=" + k);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                clean(mc);
            }), com.xenowarriors.skill.XenoBlast.RELEASE + (ct == CharacterType.RHALIX ? 15 : 14));
            l.add("combat_xenoblast_" + n, () -> clean(mc), 40);
            l.add(null, () -> server(p -> { compare("xenoblast " + n); p.getAbilities().invulnerable = false; p.onUpdateAbilities(); mc.options.setCameraType(CameraType.FIRST_PERSON); }), 200);
        }
    }

    // ------------------------------------------------------------------ 6) health orbs
    static void orbs(Minecraft mc, Sink l) {
        l.add(null, () -> { character(CharacterType.MIKIKO); server(p -> {
            p.setGameMode(GameType.SURVIVAL); stage(p, 8);
            com.xenowarriors.pickup.HealthOrbs.dropped = 0;
            int kills = 2000;
            for (int i = 0; i < kills; i++) {
                var z = EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(p.getX() + 30, p.getY() + 200, p.getZ() + 30, 0, 0);    // (high up, away: the orbs don't count here)
                z.setNoAi(true);
                p.serverLevel().addFreshEntity(z);
                z.hurt(p.damageSources().playerAttack(p), 10000F);
            }
            int got = com.xenowarriors.pickup.HealthOrbs.dropped;
            log(String.format("orbs drop rate: %d orbs over %d kills = %.2f%% (target 5%%)", got, kills, got * 100F / kills));
            for (var o : p.serverLevel().getEntitiesOfClass(com.xenowarriors.pickup.HealthOrbEntity.class, p.getBoundingBox().inflate(300))) o.discard();
            // VR: none
            com.xenowarriors.pickup.HealthOrbs.dropped = 0;
            com.xenowarriors.pickup.HealthOrbs.testChance = 1F;
            var vr = p.server.getLevel(com.xenowarriors.vr.Vr.LEVEL);
            if (vr != null) {
                var z = EntityType.ZOMBIE.create(vr); z.moveTo(0, 120, 0, 0, 0); vr.addFreshEntity(z);
                z.hurt(p.damageSources().playerAttack(p), 10000F);
                log("orbs in VR at 100% chance: dropped=" + com.xenowarriors.pickup.HealthOrbs.dropped + " (expect 0)");
            }
            com.xenowarriors.pickup.HealthOrbs.testChance = -1F;
        }); }, 40);
        l.add(null, () -> { clean(mc); mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); server(p -> {
            p.setHealth(p.getMaxHealth()); p.setYRot(0); p.setXRot(30);
            for (int i = 0; i < 3; i++) { var o = com.xenowarriors.pickup.HealthOrbs.drop(p.serverLevel(), p); if (o != null) { o.moveTo(p.getX() - 1.2 + i * 1.2, p.getY() + 0.2, p.getZ() + 3.5 + (i % 2) * 0.8); o.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); } }
        }); }, 40);
        l.add("combat_health_orb", () -> {}, 2);
        l.add(null, () -> { mc.options.hideGui = false; server(p -> { for (var o : p.serverLevel().getEntitiesOfClass(com.xenowarriors.pickup.HealthOrbEntity.class, p.getBoundingBox().inflate(8))) o.discard(); }); }, 2);
        // heal 2.5% at two max-health levels (a weak and a strong character)
        for (double maxHp : new double[]{20, 120}) {
            l.add(null, () -> server(p -> {
                var hp = p.getAttribute(Attributes.MAX_HEALTH);
                hp.removeModifier(java.util.UUID.fromString("0f8c3b38-2d0e-4b8a-9a55-2a0b7c1e4d01"));
                hp.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID.fromString("0f8c3b38-2d0e-4b8a-9a55-2a0b7c1e4d01"), "test", maxHp - hp.getBaseValue(), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                p.setHealth(p.getMaxHealth() * 0.5F);
                p.getPersistentData().putFloat("xw_test_hp", p.getHealth());
                com.xenowarriors.pickup.HealthOrbs.collectedCount = 0;
                var z = EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(p.getX() + 3.5, p.getY(), p.getZ() + 2, 0, 0); z.setNoAi(true); p.serverLevel().addFreshEntity(z);
                com.xenowarriors.pickup.HealthOrbs.testChance = 1F;
                z.hurt(p.damageSources().playerAttack(p), 10000F);
                com.xenowarriors.pickup.HealthOrbs.testChance = -1F;
                p.setYRot(-120); p.setXRot(25);
            }), 100);
            l.add(null, () -> server(p -> {
                float before = p.getPersistentData().getFloat("xw_test_hp");
                log("orbs heal: dropped=" + com.xenowarriors.pickup.HealthOrbs.dropped + " orbs near=" + p.serverLevel().getEntitiesOfClass(com.xenowarriors.pickup.HealthOrbEntity.class, p.getBoundingBox().inflate(12)).stream().map(o -> String.format("%.1f %s", o.distanceTo(p), o.debug())).toList() + " playerHp=" + p.getHealth() + "/" + p.getMaxHealth() + " collector=" + com.xenowarriors.pickup.HealthOrbEntity.collector(p));
                log(String.format("orbs heal: maxHp=%.0f health %.2f -> %.2f = +%.2f%% of max (collected=%d, last=%.2f%%)", p.getMaxHealth(), before, p.getHealth(),
                        (p.getHealth() - before) * 100F / p.getMaxHealth(), com.xenowarriors.pickup.HealthOrbs.collectedCount, com.xenowarriors.pickup.HealthOrbs.lastHealFraction * 100F));
            }), 100);
        }
        l.add(null, () -> server(p -> {
            p.getAttribute(Attributes.MAX_HEALTH).removeModifier(java.util.UUID.fromString("0f8c3b38-2d0e-4b8a-9a55-2a0b7c1e4d01"));
            p.setHealth(p.getMaxHealth());
            // an untaken orb is gone after a minute
            var o = com.xenowarriors.pickup.HealthOrbs.drop(p.serverLevel(), p);
            if (o != null) { o.moveTo(p.getX() + 40, p.getY(), p.getZ()); o.tickCount = com.xenowarriors.pickup.HealthOrbEntity.LIFE - 5; p.getPersistentData().putInt("xw_test_orb", o.getId()); }
        }), 20);
        l.add(null, () -> server(p -> log("orbs despawn after a minute: removed=" + (p.serverLevel().getEntity(p.getPersistentData().getInt("xw_test_orb")) == null))), 5);
    }

    // ------------------------------------------------------------------ 4) Ultima
    static void ultima(Minecraft mc, Sink l) {
        l.add(null, () -> { character(CharacterType.ULTIMA); server(p -> {
            p.setGameMode(GameType.SURVIVAL); stage(p, 10); p.setYRot(0); p.setXRot(8);
            boolean took = p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
            log("ultima poison: addEffect=" + took + " hasPoison=" + p.hasEffect(MobEffects.POISON) + " wither still works=" + p.addEffect(new MobEffectInstance(MobEffects.WITHER, 20, 0)));
            p.removeEffect(MobEffects.WITHER);
            E.clear();
            var z = mob(p, EntityType.HUSK, "invisible_zombie", -1.2, 4);
            if (z != null) { z.setInvisible(true); z.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20000, 0, false, false)); }
            mob(p, EntityType.HUSK, "visible_husk", 2.5, 6);
        }); mc.options.setCameraType(CameraType.FIRST_PERSON); }, 40);
        l.add(null, () -> clean(mc), 2);
        l.add("combat_ultima_sees_invisible", () -> {
            mc.player.setYRot(0); mc.player.setXRot(8);
            com.xenowarriors.client.LockOn.toggle();
            var t = com.xenowarriors.client.LockOn.target();
            log("ultima lock-on target: " + (t == null ? "none" : t.getType().toShortString() + " invisible=" + t.isInvisible()) + " sees=" + com.xenowarriors.client.UltimaSight.local());
        }, 10);
        l.add(null, () -> { if (com.xenowarriors.client.LockOn.target() != null) com.xenowarriors.client.LockOn.toggle(); character(CharacterType.MIKIKO); }, 30);
        l.add(null, () -> clean(mc), 2);
        l.add("combat_mikiko_no_invisible", () -> {
            mc.player.setYRot(0); mc.player.setXRot(8);
            com.xenowarriors.client.LockOn.toggle();
            var t = com.xenowarriors.client.LockOn.target();
            log("mikiko lock-on target: " + (t == null ? "none" : t.getType().toShortString() + " invisible=" + t.isInvisible()) + " sees=" + com.xenowarriors.client.UltimaSight.local());
            server(p -> log("mikiko poison: " + p.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0))));
        }, 10);
        l.add(null, () -> { if (com.xenowarriors.client.LockOn.target() != null) com.xenowarriors.client.LockOn.toggle(); server(p -> { for (var e : E.values()) e.discard(); E.clear(); }); }, 5);
    }

    // ------------------------------------------------------------------ 7) souls: no lock-on, flying
    static void souls(Minecraft mc, Sink l) {
        l.add(null, () -> { character(CharacterType.MIKIKO); server(p -> {
            p.setGameMode(GameType.SURVIVAL); stage(p, 10); p.setYRot(0); p.setXRot(10);
            com.xenowarriors.soul.SoulSplit.end(p, null);
            com.xenowarriors.soul.SoulSplit.start(p);
            var souls = p.serverLevel().getEntitiesOfClass(com.xenowarriors.soul.SoulEntity.class, p.getBoundingBox().inflate(6));
            for (var s : souls) { s.moveTo(p.getX(), p.getY(), p.getZ() + 4); s.setNoAi(true); }
            log("souls out: " + souls.size());
        }); }, 30);
        l.add(null, () -> {
            mc.player.setYRot(0); mc.player.setXRot(10);
            com.xenowarriors.client.LockOn.toggle();
            var t = com.xenowarriors.client.LockOn.target();
            int souls = mc.level.getEntitiesOfClass(com.xenowarriors.soul.SoulEntity.class, mc.player.getBoundingBox().inflate(8)).size();
            log("lock-on with my soul in front (" + souls + " seen client-side): target=" + (t == null ? "none" : t.getType().toShortString()));
            if (t != null) com.xenowarriors.client.LockOn.toggle();
            server(p -> { for (var s : p.serverLevel().getEntitiesOfClass(com.xenowarriors.soul.SoulEntity.class, p.getBoundingBox().inflate(8))) s.setNoAi(false); });
        }, 10);
        // the owner flies up (creative-style flight in survival): the soul follows into the air
        l.add(null, () -> server(p -> {
            p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
            p.teleportTo(p.getX(), p.getY() + 9, p.getZ());
        }), 5);
        for (int i = 0; i < 4; i++) {
            int ii = i;
            l.add(ii == 2 ? "combat_soul_flying" : null, () -> { clean(mc); server(p -> {
                p.getAbilities().flying = true; p.onUpdateAbilities();
                if (ii == 1) { p.teleportTo(p.getX() + 3, p.getY() + 4, p.getZ()); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
                for (var s : p.serverLevel().getEntitiesOfClass(com.xenowarriors.soul.SoulEntity.class, p.getBoundingBox().inflate(40)))
                    log(String.format("soul flying t=%d: owner y=%.1f soul y=%.1f (ground %d) flying=%b noGravity=%b dist=%.1f", ii * 30, p.getY(), s.getY(),
                            p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, s.blockPosition().getX(), s.blockPosition().getZ()),
                            s.soulFlying(), s.isNoGravity(), s.distanceTo(p)));
            }); }, 30);
        }
        l.add(null, () -> server(p -> {               // landing: it walks again
            p.getAbilities().flying = false; p.getAbilities().mayfly = false; p.onUpdateAbilities();
            p.teleportTo(p.getX(), p.getY() - 13, p.getZ());
        }), 60);
        l.add(null, () -> server(p -> {
            for (var s : p.serverLevel().getEntitiesOfClass(com.xenowarriors.soul.SoulEntity.class, p.getBoundingBox().inflate(40)))
                log(String.format("soul after landing: owner y=%.1f soul y=%.1f flying=%b onGround=%b", p.getY(), s.getY(), s.soulFlying(), s.onGround()));
            com.xenowarriors.soul.SoulSplit.end(p, null);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }), 5);
    }

    // ------------------------------------------------------------------ 3) DragonMineZ skill scaling
    static Object dmzData(ServerPlayer p) throws Exception {
        var cap = (net.minecraftforge.common.capabilities.Capability<?>) Class.forName("com.dragonminez.common.stats.StatsCapability").getField("INSTANCE").get(null);
        return p.getCapability(cap).resolve().orElse(null);
    }

    static void dmzStats(ServerPlayer p, int v) throws Exception {
        Object d = dmzData(p);
        Object st = d.getClass().getMethod("getStats").invoke(d);
        for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) st.getClass().getMethod(m, int.class).invoke(st, v);
        // fully powered up (DragonMineZ's power release, which its damage follows)
        try {
            Object res = d.getClass().getMethod("getResources").invoke(d);
            int was0 = (Integer) res.getClass().getMethod("getPowerRelease").invoke(res);
            res.getClass().getMethod("setReleaseLimit", int.class).invoke(res, 100);
            res.getClass().getMethod("setPowerRelease", int.class).invoke(res, 100);
            log("dmz stats " + v + ": power release " + was0 + "% -> " + res.getClass().getMethod("getPowerRelease").invoke(res) + "%");
        } catch (Throwable t) { log("dmz release failed " + t); }
        // (round-trip it through its save, the way a character switch loads it, so its damage figures are worked out)
        boolean was = (Boolean) d.getClass().getMethod("isDataLoaded").invoke(d);
        Object tag = d.getClass().getMethod("save").invoke(d);
        d.getClass().getMethod("load", net.minecraft.nbt.CompoundTag.class).invoke(d, tag);
        log("dmz stats " + v + ": dataLoaded " + was + " -> " + d.getClass().getMethod("isDataLoaded").invoke(d));
    }

    static void dmzCreated(ServerPlayer p) {
        try {
            Object d = dmzData(p);
            Object ch = d.getClass().getMethod("getCharacter").invoke(d);
            ch.getClass().getMethod("setRace", String.class).invoke(ch, "saiyan");
            try { ch.getClass().getMethod("setCharacterClass", String.class).invoke(ch, (String) ch.getClass().getField("CLASS_WARRIOR").get(null)); } catch (Throwable t) { log("dmz class failed " + t); }
            try { Object st0 = d.getClass().getMethod("getStatus").invoke(d); for (var m : st0.getClass().getMethods()) if (m.getName().equals("setDataLoaded") || m.getName().equals("setAlive")) m.invoke(st0, true); } catch (Throwable ignored) {}
            Object st = d.getClass().getMethod("getStatus").invoke(d);
            st.getClass().getMethod("setHasCreatedCharacter", boolean.class).invoke(st, true);
            com.xenowarriors.compat.DmzPerCharacter.markCreatedForTest(p);
        } catch (Throwable t) { log("dmz create failed " + t); }
    }

    static String dmzFigures(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        try {
            Object d = dmzData(p);
            for (String m : new String[]{"getLevel", "getBattlePower", "getKiDamage", "getStrikeDamage", "getMeleeDamage", "getKiAttackDamageModifier", "getMaxHealth"})
                try { sb.append(m.substring(3)).append('=').append(d.getClass().getMethod(m).invoke(d)).append(' '); } catch (Throwable t) { sb.append(m).append("=? "); }
            Object st = d.getClass().getMethod("getStats").invoke(d);
            sb.append("KiPower=").append(st.getClass().getMethod("getKiPower").invoke(st)).append(" StrikePower=").append(st.getClass().getMethod("getStrikePower").invoke(st));
        } catch (Throwable t) { sb.append(t); }
        return sb.toString();
    }

    /** What a hit really took off the dummy. */
    static float hit(ServerPlayer p, LivingEntity z, Runnable r) {
        z.setHealth(z.getMaxHealth()); z.invulnerableTime = 0;
        float before = z.getHealth();
        r.run();
        float took = before - z.getHealth();
        z.setHealth(z.getMaxHealth()); z.invulnerableTime = 0;
        return took;
    }

    /** A real DragonMineZ ki blast (as its basic ki blast key makes it: ki damage x 0.5), fired point-blank at the dummy. */
    static void dmzKiBlast(ServerPlayer p, LivingEntity z) {
        try {
            Object d = dmzData(p);
            double ki = ((Number) d.getClass().getMethod("getKiDamage").invoke(d)).doubleValue();
            var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", "ki_blast"));
            var e = type.create(p.serverLevel());
            e.getClass().getMethod("setup", LivingEntity.class, float.class, float.class, float.class, int.class, int.class).invoke(e, p, (float) (ki * 0.5), 0.5F, 1.5F, 0x66CCFF, 0xFFFFFF);
            Vec3 from = p.getEyePosition(), to = z.position().add(0, 1, 0), dir = to.subtract(from).normalize();
            e.moveTo(from.x + dir.x, from.y + dir.y, from.z + dir.z, 0, 0);
            if (e instanceof net.minecraft.world.entity.projectile.Projectile pr) { pr.setOwner(p); pr.shoot(dir.x, dir.y, dir.z, 1.5F, 0F); }
            z.setHealth(z.getMaxHealth()); z.invulnerableTime = 0;
            p.serverLevel().addFreshEntity(e);
        } catch (Throwable t) { log("dmz ki blast failed " + t); }
    }

    static void dmzScaling(Minecraft mc, Sink l) {
        l.add(null, () -> { character(CharacterType.MIKIKO); server(p -> {
            p.setGameMode(GameType.SURVIVAL); stage(p, 6); dmzCreated(p);
            p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            E.clear();
            var z = mob(p, EntityType.IRON_GOLEM, "dummy", 0, 3);
            if (z != null) { z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000000); z.setHealth(1000000); }
            log("dmz fresh: " + dmzFigures(p));
        }); }, 40);
        for (int lv : new int[]{5, 60, 250}) {
            l.add(null, () -> server(p -> {
                try { dmzStats(p, lv); } catch (Throwable t) { log("dmz stats failed " + t); }
            }), 40);
            l.add(null, () -> server(p -> {
                LivingEntity z = E.get("dummy");
                double[] raw = com.xenowarriors.compat.DmzScaling.raw(p);
                double oldKi = Math.max(1.0, raw[0] / 10.0), oldStrike = Math.max(1.0, raw[1] / 10.0);
                log("dmz stats=" + lv + " figures: " + dmzFigures(p));
                float listed = hit(p, z, () -> z.hurt(com.xenowarriors.damage.ModDamageTypes.source(p.level(), com.xenowarriors.damage.ModDamageTypes.BLAZING_WIND, p, p), 10F));
                float xeno = hit(p, z, () -> com.xenowarriors.combat.SkillContext.run(p, () -> z.hurt(p.damageSources().indirectMagic(p, p), 10F)));
                float kick = hit(p, z, () -> com.xenowarriors.combat.SkillContext.run(p, () -> z.hurt(p.damageSources().playerAttack(p), 10F)));
                float plain = hit(p, z, () -> z.hurt(p.damageSources().indirectMagic(p, p), 10F));
                float punch = hit(p, z, () -> { net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, p, 200, "f_20922_"); p.attack(z); });
                log(String.format("dmz scaling stats=%d | skill multiplier ki old=%.2f new=%.2f, strike old=%.2f new=%.2f", lv,
                        oldKi, com.xenowarriors.compat.DmzScaling.skillMultiplier(p, false), oldStrike, com.xenowarriors.compat.DmzScaling.skillMultiplier(p, true)));
                log(String.format("dmz scaling stats=%d 10-dmg hits: Blazing Wind type took %.1f (old rule: %.1f) | energy skill (Xeno Blast-style, in a skill) took %.1f (old: no scaling) | physical skill (playerAttack in a skill) took %.1f | not a skill took %.1f | a DMZ punch took %.1f",
                        lv, listed, 10 * oldKi, xeno, kick, plain, punch));
                dmzKiBlast(p, z);
            }), 25);
            l.add(null, () -> server(p -> {
                LivingEntity z = E.get("dummy");
                log(String.format("dmz scaling stats=%d a DMZ basic ki blast took %.1f", lv, z.getMaxHealth() - z.getHealth()));
                z.setHealth(z.getMaxHealth());
            }), 5);
        }
        l.add(null, () -> server(p -> { try { dmzStats(p, 5); } catch (Throwable ignored) {} p.getAbilities().invulnerable = false; p.onUpdateAbilities(); for (var e : E.values()) e.discard(); E.clear(); }), 5);
    }

    // ------------------------------------------------------------------ 5) the Xenoverse Robot
    static void robot(Minecraft mc, Sink l) {
        l.add(null, () -> { character(CharacterType.ULTIMA); server(p -> {
            p.setGameMode(GameType.SURVIVAL); stage(p, 10); p.setYRot(0); p.setXRot(15);
            E.clear();
            var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(com.xenowarriors.ultima.RobotPets.ROBOT);
            log("robot type: " + com.xenowarriors.ultima.RobotPets.ROBOT + " -> " + (type == null ? "missing" : type.getDescriptionId()));
            if (type == null) return;
            var e = type.create(p.serverLevel());
            if (!(e instanceof Mob m)) { log("robot not a mob: " + e); return; }
            m.moveTo(p.getX(), p.getY(), p.getZ() + 3, 180, 0);
            p.serverLevel().addFreshEntity(m);
            E.put("robot", m);
            p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            log("robot spawned: class=" + m.getClass().getSimpleName() + " enemy=" + (m instanceof net.minecraft.world.entity.monster.Enemy) + " hp=" + m.getMaxHealth()
                    + " target=" + m.getTarget());
        }); }, 30);
        // feed shards: the first two fail, the third works (the chance is forced for the test), all by right-clicking
        l.add(null, () -> server(p -> {
            Mob m = (Mob) E.get("robot");
            if (m == null) return;
            var shard = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(XenoWarriors.id("data_shard"));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(shard, 10));
            for (int i = 0; i < 3; i++) {
                com.xenowarriors.ultima.RobotPets.forceTame = i == 2;
                var res = p.interactOn(m, net.minecraft.world.InteractionHand.MAIN_HAND);
                log("robot shard " + (i + 1) + ": result=" + res + " owner=" + com.xenowarriors.ultima.RobotPets.ownerOf(m) + " shardsLeft=" + p.getMainHandItem().getCount());
            }
            com.xenowarriors.ultima.RobotPets.forceTame = null;
            log("robot tamed: petOwner=" + (p.getUUID().equals(com.xenowarriors.party.Parties.petOwner(m))) + " isAlly=" + com.xenowarriors.combat.Allies.isAlly(p, m)
                    + " canTarget=" + com.xenowarriors.skill.SkillTargeting.canTarget(p, m) + " pets=" + com.xenowarriors.party.Parties.pets(p) + " target=" + m.getTarget());
            // the chance itself: 3000 simulated tries
            int ok = 0; for (int i = 0; i < 3000; i++) if (p.getRandom().nextFloat() < com.xenowarriors.ultima.RobotPets.TAME_CHANCE) ok++;
            log(String.format("robot tame chance %.3f per shard (simulated %.1f%%)", com.xenowarriors.ultima.RobotPets.TAME_CHANCE, ok / 30F));
            // non-Ultima can't
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            var res = p.interactOn(m, net.minecraft.world.InteractionHand.MAIN_HAND);
            log("robot sit: " + res + " sitting=" + com.xenowarriors.ultima.RobotPets.sitting(m));
            res = p.interactOn(m, net.minecraft.world.InteractionHand.MAIN_HAND);
            log("robot stand: " + res + " sitting=" + com.xenowarriors.ultima.RobotPets.sitting(m));
            m.moveTo(p.getX() + 14, p.getY(), p.getZ() + 6);
        }), 80);
        l.add(null, () -> server(p -> {
            Mob m = (Mob) E.get("robot");
            if (m == null) return;
            log(String.format("robot follow: distance to Ultima after 4s = %.1f (started 15)", m.distanceTo(p)));
            var z = mob(p, EntityType.ZOMBIE, "zombie", 6, 4);
            if (z != null) { z.setNoAi(false); z.setTarget(p); p.setLastHurtByMob(z); z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); z.setHealth(200); }
        }), 60);
        l.add(null, () -> clean(mc), 2);
        l.add("combat_robot_fights", () -> server(p -> {
            Mob m = (Mob) E.get("robot"); LivingEntity z = E.get("zombie");
            if (m == null || z == null) return;
            log("robot guard: target=" + (m.getTarget() == null ? "none" : m.getTarget().getType().toShortString()) + " zombieHp=" + z.getHealth() + "/200 ultimaHurtByRobot=" + (p.getLastHurtByMob() == m));
            // relog: saved and loaded back as a new entity
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            m.save(tag);
            var copy = net.minecraft.world.entity.EntityType.create(tag, p.serverLevel()).orElse(null);
            log("robot saved/loaded: owner kept=" + (copy != null && p.getUUID().equals(com.xenowarriors.ultima.RobotPets.ownerOf(copy))) + " persistent=" + (copy instanceof Mob cm && cm.isPersistenceRequired()));
        }), 100);
        l.add(null, () -> server(p -> { p.getAbilities().invulnerable = false; p.onUpdateAbilities(); for (var e : E.values()) e.discard(); E.clear(); }), 5);
    }

    // ------------------------------------------------------------------ 8) DragonMineZ terrain in the Tower
    static void terrain(Minecraft mc, Sink l) {
        for (var dim : java.util.List.of(com.xenowarriors.tower.Tower.LEVEL, net.minecraft.world.level.Level.OVERWORLD)) {
            String name = dim.location().getPath();
            BlockPos base = new BlockPos(6000, 150, 6000);
            l.add(null, () -> { character(CharacterType.MIKIKO); server(p -> {
                var lvl = p.server.getLevel(dim);
                if (lvl == null) { log("terrain " + name + " missing"); return; }
                for (int x = -6; x <= 6; x++) for (int z = -2; z <= 16; z++) for (int y = -1; y <= 6; y++)
                    lvl.setBlock(base.offset(x, y, z), y == -1 || z >= 10 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                p.teleportTo(lvl, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
                p.setGameMode(GameType.SURVIVAL);
                p.getAbilities().invulnerable = true; p.onUpdateAbilities();
            }); }, 40);
            l.add(null, () -> server(p -> {
                var lvl = p.serverLevel();
                int before = count(lvl, base);
                StringBuilder s = new StringBuilder();
                // DragonMineZ's own ki griefing rule(s): switched on, so the overworld shows what ki does when it's allowed
                lvl.getGameRules().visitGameRuleTypes(new net.minecraft.world.level.GameRules.GameRuleTypeVisitor() {
                    @Override public <T extends net.minecraft.world.level.GameRules.Value<T>> void visit(net.minecraft.world.level.GameRules.Key<T> key, net.minecraft.world.level.GameRules.Type<T> type) {
                        String id = key.getId().toLowerCase();
                        if (!(id.contains("ki") || id.contains("grief") || id.contains("destr") || id.contains("dmz"))) return;
                        var v = lvl.getGameRules().getRule(key);
                        if (v instanceof net.minecraft.world.level.GameRules.BooleanValue b && (id.contains("grief") || id.contains("destr"))) b.set(true, p.server);
                        s.append("rule ").append(key.getId()).append('=').append(lvl.getGameRules().getRule(key)).append(' ');
                    }
                });
                try {
                    var m = Class.forName("com.dragonminez.common.init.MainGameRules").getMethod("canKiGrief", net.minecraft.world.level.Level.class, BlockPos.class, net.minecraft.world.entity.Entity.class);
                    s.append("canKiGrief=").append(m.invoke(null, lvl, base.offset(0, 2, 12), p)).append(' ');
                } catch (Throwable t) { s.append("canKiGrief? ").append(t).append(' '); }
                for (String id : new String[]{"ki_blast", "ki_wave", "ki_disc", "ki_laser"}) {
                    var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", id));
                    if (type == null) { s.append(id).append("=missing "); continue; }
                    var e = type.create(lvl);
                    if (e == null) continue;
                    try {
                        e.getClass().getMethod("setup", LivingEntity.class, float.class, float.class, float.class, int.class, int.class).invoke(e, p, 20F, 2F, 1.2F, 0x66CCFF, 0xFFFFFF);
                    } catch (Throwable t) { s.append(id).append(" setup? ").append(t.getClass().getSimpleName()).append(' '); }
                    try { e.getClass().getMethod("setBlockDestructionEnabled", boolean.class).invoke(e, true); } catch (Throwable ignored) {}
                    e.moveTo(p.getX() + (id.length() % 3 - 1) * 3, p.getY() + 1.5, p.getZ() + 2, 0, 0);
                    if (e instanceof net.minecraft.world.entity.projectile.Projectile pr) { pr.setOwner(p); pr.shoot(0, 0, 1, 1.2F, 0F); }
                    lvl.addFreshEntity(e);
                    s.append(id).append(" destroys=").append(com.xenowarriors.compat.DmzTerrain.destroys(e)).append(' ');
                }
                // a big ki explosion where the wall is
                var ex = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new net.minecraft.resources.ResourceLocation("dragonminez", "ki_explosion"));
                if (ex != null) {
                    var e = ex.create(lvl);
                    if (e != null) {
                        try { e.getClass().getMethod("setup", LivingEntity.class, float.class, float.class, float.class, int.class, int.class).invoke(e, p, 20F, 4F, 0F, 0xFFAA33, 0xFFFFFF); } catch (Throwable ignored) {}
                        try { e.getClass().getMethod("setBlockDestructionEnabled", boolean.class).invoke(e, true); } catch (Throwable ignored) {}
                        e.moveTo(base.getX() + 0.5, base.getY() + 2, base.getZ() + 10, 0, 0);
                        lvl.addFreshEntity(e);
                        s.append("ki_explosion destroys=").append(com.xenowarriors.compat.DmzTerrain.destroys(e));
                    }
                }
                log("terrain " + name + " canGrief=" + com.xenowarriors.modes.ModeRules.canGrief(lvl) + " fired: " + s + " | wall blocks before=" + before);
            }), 100);
            l.add("combat_dmz_terrain_" + name, () -> server(p -> log("terrain " + name + " wall blocks after ki attacks=" + count(p.serverLevel(), base) + " (of 728)")), 5);
        }
        l.add(null, () -> server(p -> {
            p.getAbilities().invulnerable = false; p.onUpdateAbilities();
            var o = p.server.overworld();
            p.teleportTo(o, o.getSharedSpawnPos().getX(), o.getSharedSpawnPos().getY() + 1, o.getSharedSpawnPos().getZ(), 0, 0);
        }), 20);
    }

    static int count(ServerLevel l, BlockPos base) {
        int n = 0;
        for (int x = -6; x <= 6; x++) for (int z = 10; z <= 16; z++) for (int y = -1; y <= 6; y++) if (!l.getBlockState(base.offset(x, y, z)).isAir()) n++;
        return n;
    }
}

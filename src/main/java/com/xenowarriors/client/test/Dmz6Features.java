package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.compat.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;

import static com.xenowarriors.client.test.Dmz6Tests.*;

/** The dmz6 feature tests: fusion, bot power, dragon balls, guns, Skylanders in DragonMineZ's worlds. */
final class Dmz6Features {
    private Dmz6Features() {}

    private static final Object[] BOT = new Object[1];
    private static final Object[] FOE = new Object[1];

    static PartyBotEntity bot(ServerPlayer p) {
        if (BOT[0] instanceof PartyBotEntity b && b.isAlive() && !b.isRemoved()) return b;
        for (PartyBotEntity b : PartyBots.bots(p)) { BOT[0] = b; return b; }
        return null;
    }

    private static int dumped0, dumped1, dumped2, dumped3, dumped4, dumped5;

    static void dump() {
        synchronized (DmzFusion.LOG) { for (int i = dumped0; i < DmzFusion.LOG.size(); i++) log("fusionlog " + DmzFusion.LOG.get(i)); dumped0 = DmzFusion.LOG.size(); }
        synchronized (DmzBotPower.LOG) { for (int i = dumped1; i < DmzBotPower.LOG.size(); i++) log("powerlog " + DmzBotPower.LOG.get(i)); dumped1 = DmzBotPower.LOG.size(); }
        synchronized (DmzDragonBalls.LOG) { for (int i = dumped2; i < DmzDragonBalls.LOG.size(); i++) log("ballslog " + DmzDragonBalls.LOG.get(i)); dumped2 = DmzDragonBalls.LOG.size(); }
        synchronized (DmzGuns.LOG) { for (int i = dumped3; i < DmzGuns.LOG.size(); i++) log("gunlog " + DmzGuns.LOG.get(i)); dumped3 = DmzGuns.LOG.size(); }
        synchronized (DmzSkylanders.LOG) { for (int i = dumped4; i < DmzSkylanders.LOG.size(); i++) log("skylog " + DmzSkylanders.LOG.get(i)); dumped4 = DmzSkylanders.LOG.size(); }
        synchronized (DmzBotKi.LOG) { for (int i = dumped5; i < DmzBotKi.LOG.size(); i++) log("kilog " + DmzBotKi.LOG.get(i)); dumped5 = DmzBotKi.LOG.size(); }
    }

    static void build(Minecraft mc, Dmz6Tests.Adder steps, Consumer<Consumer<ServerPlayer>> server, Consumer<String> log) {
        botSetup(mc);
        fusion(mc);
        power(mc);
        balls(mc);
        guns(mc);
        worlds(mc);
        step(null, () -> server(p -> dump()), 5);
    }

    private static void view(Minecraft mc, boolean front) {
        mc.options.hideGui = false;
        mc.options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
    }

    // ---------------------------------------------------------------- the bot: Rhalix, a Saiyan who can go Super Saiyan

    private static void botSetup(Minecraft mc) {
        step(null, () -> { noDmzScreen(mc); server(p -> {
            Vec3 at = at(p);
            p.teleportTo(at.x, at.y, at.z);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            protect(p); clearHostiles(p);
            PartyBotEntity b = PartyBots.spawn(p, CharacterType.RHALIX, null);
            if (b == null) { log("bot spawn FAILED"); return; }
            b.moveTo(at.x + 2, at.y, at.z + 2, 0, 0);
            BOT[0] = b;
            b.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 30, 3, false, false));
            log("bot race " + DmzBots.ensureRace(b));
            String key = DmzPerCharacter.botKey(b);
            CompoundTag tag = DmzPerCharacter.saved(p, key);
            Object d = DmzPerCharacter.blank(PartyBots.standIn(b));
            if (tag == null || d == null || !DmzPerCharacter.loadData(d, tag.copy())) { log("bot DMZ data FAILED"); return; }
            Object ch = call(d, "getCharacter");
            call(ch, "setRace", "saiyan");
            call(d, "updateTransformationSkillLimits", "saiyan");
            Object sk = call(d, "getSkills");
            StringBuilder all = new StringBuilder();
            if (call(sk, "getAllSkills") instanceof Map<?, ?> m) for (Object k : m.keySet()) {
                Object max = call(sk, "getMaxSkillLevel", k);
                all.append(k).append("(max ").append(max).append(") ");
                if (String.valueOf(k).toLowerCase(Locale.ROOT).contains("super") && max instanceof Integer mx && mx > 0) call(sk, "setSkillLevel", k, Math.min(3, mx));
            }
            log("bot skills " + all);
            call(ch, "setSelectedFormGroup", "supersaiyan");
            Object st = call(d, "getStats");
            for (String m : new String[]{"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"}) call(st, m, 60);
            try {                                                               // a ki blast equipped (so it spends ki)
                Object tech = call(d, "getTechniques");
                var reg = (Map<?, ?>) Class.forName("com.dragonminez.common.stats.techniques.PredefinedTechniques").getField("REGISTRY").get(null);
                String pick = reg.containsKey("kamehameha") ? "kamehameha" : (String) reg.keySet().iterator().next();
                call(tech, "unlockTechnique", reg.get(pick)); call(tech, "equipTechnique", 0, pick);
            } catch (Throwable t) { log("bot equip FAILED " + t); }
            log("bot form group " + call(ch, "getSelectedFormGroup") + " superform=" + call(sk, "getSkillLevel", "superform") + " race=" + call(ch, "getRaceName"));
            log("bot stored " + DmzPerCharacter.store(p, key, DmzPerCharacter.saveData(d)));
            DmzBotKi.forget(b.getUUID());
        }); }, 40);
    }

    // ---------------------------------------------------------------- 1: fusion

    private static void fusion(Minecraft mc) {
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            log("fusion kind with nothing: " + DmzFusion.kind(b) + " whyNot=" + DmzFusion.whyNot(p, b));
            b.inventory.addItem(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("dragonminez", "pothala_right"))));
            log("fusion kind with earrings: " + DmzFusion.kind(b));
            log("fusion before: " + DmzFusion.statsLine(DmzPerCharacter.live(p)));
            p.setYRot(180); p.setXRot(10);
            PartyBots.action(p, b.getId(), PartyBots.ACT_FUSE, 0);
            log("fusion fused=" + DmzFusion.fused(p) + " left=" + DmzFusion.left(p) + " bots now " + PartyBots.bots(p).size());
            dump();
        }), 40);
        step(null, () -> { mc.setScreen(null); view(mc, true); }, 20);
        step("dmz6_fusion_potara", () -> { mc.setScreen(null); server(p -> {}); }, 3);
        step(null, () -> server(p -> log("fusion display name " + p.getDisplayName().getString() + " | " + DmzFusion.statsLine(DmzPerCharacter.live(p)))), 3);
        step(null, () -> view(mc, false), 60);
        step(null, () -> server(p -> {                                         // DragonMineZ's own tick has run a while: still fused?
            log("fusion after 3 s: fused=" + DmzFusion.fused(p) + " left=" + DmzFusion.left(p) + " dmz " + DmzFusion.statsLine(DmzPerCharacter.live(p)));
            DmzFusion.end(p, "test: time's up", true);
            PartyBotEntity b = null; for (PartyBotEntity x : PartyBots.bots(p)) b = x;
            BOT[0] = b;
            int ear = 0; if (b != null) for (int i = 0; i < b.inventory.getContainerSize(); i++) if (String.valueOf(ForgeRegistries.ITEMS.getKey(b.inventory.getItem(i).getItem())).contains("pothala")) ear++;
            log("fusion ended: bot back=" + (b != null) + " earrings left=" + ear + " | " + DmzFusion.statsLine(DmzPerCharacter.live(p)) + " | name " + p.getDisplayName().getString());
            dump();
        }), 20);
        step("dmz6_fusion_after", () -> mc.setScreen(null), 3);
        // the dance: the bot knows it; the cooldown is cleared for the test
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            log("fusion refused while on cooldown: " + DmzFusion.whyNot(p, b));
            for (var e : new ArrayList<>(p.getActiveEffects())) if (String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect())).contains("fusion")) p.removeEffect(e.getEffect());
            String key = DmzPerCharacter.botKey(b);
            Object d = DmzPerCharacter.blank(PartyBots.standIn(b));
            DmzPerCharacter.loadData(d, DmzPerCharacter.saved(p, key).copy());
            call(call(d, "getSkills"), "setSkillLevel", "fusion", 1);
            DmzPerCharacter.store(p, key, DmzPerCharacter.saveData(d));
            log("fusion kind with the dance: " + DmzFusion.kind(b));
            b.moveTo(p.getX() + 2, p.getY(), p.getZ());
            PartyBots.action(p, b.getId(), PartyBots.ACT_FUSE, 0);
            log("dance fused=" + DmzFusion.fused(p) + " | " + DmzFusion.statsLine(DmzPerCharacter.live(p)));
            dump();
        }), 30);
        step(null, () -> { mc.setScreen(null); view(mc, true); }, 10);
        step("dmz6_fusion_dance", () -> mc.setScreen(null), 3);
        step(null, () -> server(p -> {
            DmzFusion.end(p, "test: knocked out", false);                      // (ended early - as on dying, which sends you to DragonMineZ's Otherworld)
        }), 30);
        step(null, () -> server(p -> {
            log("dance ended early: fused=" + DmzFusion.fused(p) + " bots=" + PartyBots.bots(p).size() + " | " + DmzFusion.statsLine(DmzPerCharacter.live(p)));
            BOT[0] = null; bot(p);
            Vec3 at = at(p); p.teleportTo(at.x, at.y, at.z); protect(p);
            PartyBotEntity b = bot(p); if (b != null) b.moveTo(at.x + 2, at.y, at.z + 2);
            dump();
        }), 20);
    }

    // ---------------------------------------------------------------- 6: charging, full power, transforming

    private static void power(Minecraft mc) {
        step(null, () -> { view(mc, false); server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            clearHostiles(p);
            DmzBotPower.setKiForTest(b, 0.1F);
            DmzBotPower.calmForTest(b);
            b.setTarget(null);
            p.setYRot(-45); p.setXRot(15);
            log("power start: " + DmzBotPower.state(b));
        }); }, 50);
        step("dmz6_bot_charging", () -> { mc.setScreen(null); server(p -> {}); }, 3);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b != null) log("power charging: " + DmzBotPower.state(b)); dump(); }), 3);
        step(null, () -> {}, 200);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            log("power after 10 s: " + DmzBotPower.state(b));
            // a strong enemy turns up
            var z = EntityType.HUSK.create(p.serverLevel());
            z.moveTo(b.getX() + 8, b.getY(), b.getZ(), 90, 0);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
            z.setHealth(400); z.getPersistentData().putBoolean("xw_miniboss", true); z.setPersistenceRequired();
            p.serverLevel().addFreshEntity(z);
            FOE[0] = z;
            z.setTarget(b);
            log("power foe spawned: " + z.getType().getDescriptionId() + " hp " + z.getMaxHealth());
        }), 30);
        step("dmz6_bot_transform", () -> server(p -> { PartyBotEntity b = bot(p); if (b != null) log("power in the fight: " + DmzBotPower.state(b) + " " + DmzBotKi.kiDamageForTest(b)); dump(); }), 3);
        step(null, () -> {}, 100);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p);
            if (b != null) log("power fight +5 s: " + DmzBotPower.state(b) + " " + DmzBotKi.kiDamageForTest(b));
            if (FOE[0] instanceof LivingEntity z) z.kill();
            if (b != null) { b.setTarget(null); DmzBotPower.calmForTest(b); }
            dump();
        }), 20);
        step(null, () -> {}, 260);
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b != null) log("power after the fight: " + DmzBotPower.state(b)); dump(); }), 10);
    }

    // ---------------------------------------------------------------- 2: the Dragon Balls

    private static void balls(Minecraft mc) {
        step(null, () -> server(p -> { PartyBotEntity b = bot(p); if (b == null) return;
            clearHostiles(p);
            Vec3 at = at(p);
            p.teleportTo(at.x - 4, at.y, at.z - 4); p.setYRot(-45); p.setXRot(25);
            b.moveTo(at.x + 1, at.y, at.z + 1, 0, 0);
            log("balls set given: " + DmzDragonBalls.giveSetForTest(b));
            boolean started = DmzDragonBalls.start(b, p, null, p.serverLevel(), true);
            log("balls started=" + started);
        }), 70);
        step("dmz6_balls_placing", () -> server(p -> dump()), 50);
        step("dmz6_balls_placed", () -> server(p -> dump()), 60);
        step("dmz6_shenron_wish", () -> {
            log("wish screen: dragon=" + com.xenowarriors.client.compat.DmzWishOpen.lastDragon + " screen=" + com.xenowarriors.client.compat.DmzWishOpen.lastScreen
                    + " now=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            server(p -> dump());
        }, 3);
        step(null, () -> { mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); server(p -> p.setXRot(-25)); }, 20);
        step("dmz6_shenron", () -> {}, 3);
        step(null, () -> { noDmzScreen(mc); server(p -> {
            for (var e : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64), m -> m.getClass().getName().contains("dragon.DragonWish") || m.getClass().getSuperclass().getName().contains("dragon.DragonWish"))) e.discard();
        }); }, 10);
    }

    // ---------------------------------------------------------------- 3: the guns

    private static void guns(Minecraft mc) {
        step(null, () -> { view(mc, false); server(p -> {
            clearHostiles(p);
            Vec3 at = at(p);
            p.teleportTo(at.x, at.y, at.z); p.setYRot(0); p.setXRot(0);
            log("guns registered: " + DmzGuns.items());
            var rm = p.server.getRecipeManager();
            for (String n : new String[]{"blaster_rifle", "burst_cannon", "blaster_mortar"})
                log("recipe " + n + " loaded=" + rm.byKey(new ResourceLocation("xenowarriors", n)).isPresent());
            for (int i = 0; i < 3; i++) {
                var z = EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(at.x - 4 + i * 4, at.y, at.z + 14, 180, 0); z.setNoAi(true); z.setPersistenceRequired();
                z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200); z.setHealth(200);
                p.serverLevel().addFreshEntity(z);
            }
        }); }, 40);
        for (DmzGuns.Kind k : DmzGuns.Kind.values()) {
            step(null, () -> server(p -> {
                var it = DmzGuns.items().stream().filter(i -> DmzGuns.kindOf(new ItemStack(i)) == k).findFirst();
                if (it.isEmpty()) { log("gun " + k + " missing"); return; }
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(it.get()));
                p.setYRot(0); p.setXRot(2);
                hp0 = hpSum(p);
                DmzGuns.fire(p, k, null);
                if (k == DmzGuns.Kind.RIFLE) for (int i = 1; i < 6; i++) com.xenowarriors.skill.ServerScheduler.later(i * k.cooldown, () -> DmzGuns.fire(p, k, null));
            }), k == DmzGuns.Kind.MORTAR ? 22 : 8);
            step("dmz6_gun_" + k.name().toLowerCase(Locale.ROOT), () -> {}, 40);
            step(null, () -> server(p -> { log("gun " + k + " target hp lost " + Math.round(hp0 - hpSum(p))); dump(); }), 10);
        }
        // a mob with one shoots it, and drops it now and then
        step(null, () -> server(p -> {
            Vec3 at = at(p);
            var sk = DmzSkylanders.spawn(p.serverLevel(), BlockPos.containing(at.x + 6, at.y, at.z + 2), true);
            if (sk == null) { log("skylander spawn FAILED"); return; }
            for (var z : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(32))) { sk.setTarget(z); break; }
            hp0 = hpSum(p);
            log("armed skylander " + ForgeRegistries.ITEMS.getKey(sk.getMainHandItem().getItem()) + " target " + (sk.getTarget() == null ? "-" : sk.getTarget().getType().getDescriptionId()));
        }), 120);
        step("dmz6_skylander_shooting", () -> server(p -> { log("skylander shots: target hp lost " + Math.round(hp0 - hpSum(p))); dump(); }), 3);
        step(null, () -> server(p -> {
            Vec3 at = at(p);
            int drops = 0, n = 30;
            log("gun drops: doMobLoot=" + p.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT));
            p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT).set(true, p.server);
            for (int i = 0; i < n; i++) {
                var z = EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(at.x + 10, at.y, at.z - 10, 0, 0); z.setNoAi(true);
                z.setItemSlot(EquipmentSlot.MAINHAND, DmzGuns.random(z.getRandom()));
                p.serverLevel().addFreshEntity(z);
                z.setLastHurtByPlayer(p);                                       // (killed by the player: equipment can drop)
                z.kill();
                if (z.isAlive()) log("gun drops: zombie still alive");
            }
            for (ItemEntity ie : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at.x + 10, at.y, at.z - 10, at.x + 10, at.y, at.z - 10).inflate(4)))
                if (DmzGuns.kindOf(ie.getItem()) != null) { drops++; ie.discard(); }
            log("gun drops: " + drops + "/" + n + " armed zombies killed by the player dropped their gun");
        }), 20);
    }

    private static float hp0;

    private static float hpSum(ServerPlayer p) {
        float s = 0;
        for (var z : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Zombie.class, p.getBoundingBox().inflate(40), z -> z.isNoAi())) s += z.getHealth();
        return s;
    }

    // ---------------------------------------------------------------- 4: Skylanders on Namek (and the other worlds)

    private static void worlds(Minecraft mc) {
        for (String w : new String[]{"namek", "otherworld"}) {
            step(null, () -> { mc.setScreen(null); server(p -> {
                ServerLevel l = p.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation("dragonminez", w)));
                if (l == null) { log("world " + w + " missing"); return; }
                BlockPos top = null;
                search:
                for (int r = 0; r <= 6; r++)                                   // (dry land near the middle: Namek has a lot of sea)
                    for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos c = new BlockPos(dx * 24, 0, dz * 24);
                        l.getChunk(c);
                        BlockPos t = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, c);
                        if (t.getY() > l.getMinBuildHeight() + 2 && l.getFluidState(t.below()).isEmpty() && l.getBlockState(t.below()).isSolid()) { top = t; break search; }
                    }
                if (top == null) top = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO);
                p.teleportTo(l, top.getX() + 0.5, Math.max(top.getY(), l.getMinBuildHeight() + 2), top.getZ() + 0.5, 0, 10);
                protect(p);
                log("world " + w + " at " + p.blockPosition().toShortString());
            }); }, 120);
            step(null, () -> server(p -> {
                ServerLevel l = p.serverLevel();
                int made = 0;
                for (int i = 0; i < 12 && made < 3; i++) if (DmzSkylanders.spawn(l, p.blockPosition().offset(4 + i, 0, 6 - i), true) != null) made++;
                log("world " + l.dimension().location() + " skylanders spawned " + made + " (natural spawner is in DmzSkylanders.onTick)");
                if (w.equals("namek")) {
                    var z = EntityType.HUSK.create(l);                         // (doesn't burn in the sun)
                    BlockPos zp = l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition().offset(12, 0, 12));
                    z.moveTo(zp.getX() + 0.5, zp.getY(), zp.getZ() + 0.5, 0, 0); z.setPersistenceRequired();
                    l.addFreshEntity(z);
                    log("namek zombie for them to shoot at " + zp.toShortString());
                }
                p.setYRot(-45); p.setXRot(10);
                dump();
            }), 160);
            step("dmz6_skylanders_" + w, () -> { mc.setScreen(null); server(p -> {
                for (var sk : p.serverLevel().getEntitiesOfClass(com.xenowarriors.npc.SkylanderEntity.class, p.getBoundingBox().inflate(48)))
                    log("world " + w + " skylander " + ForgeRegistries.ITEMS.getKey(sk.getMainHandItem().getItem()) + " target=" + (sk.getTarget() == null ? "-" : sk.getTarget().getType().getDescriptionId())
                            + " flying=" + sk.flying() + " at " + sk.blockPosition().toShortString());
                dump();
            }); }, 3);
        }
        step(null, () -> server(p -> {
            ServerLevel o = p.server.overworld();
            Vec3 at = at(p);
            p.teleportTo(o, at.x, at.y, at.z, 0, 0);
        }), 60);
    }
}

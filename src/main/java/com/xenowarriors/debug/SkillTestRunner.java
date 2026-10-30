package com.xenowarriors.debug;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.SkillPool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * UI-test helper (server side): uses every skill of every character on a target dummy and logs, per skill,
 * whether it fired, how much health the dummy lost, and any exception. "XW-TEST skill ..." lines.
 */
public final class SkillTestRunner {
    private SkillTestRunner() {}

    private record Job(Runnable setup, String who, int slotIndex, boolean ult) {}

    private static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST " + s); }

    /** Everything queued; returns the total ticks it'll take. */
    public static int runAll(ServerPlayer p, List<CharacterType> types, List<com.xenowarriors.you.YouSlot> yous) {
        List<Job> jobs = new ArrayList<>();
        for (CharacterType t : types) {
            jobs.add(new Job(() -> CharacterManager.setCharacter(p, t), t.getId(), -1, false));
            int n = SkillPool.of(t).size();
            for (int i = 0; i < n; i++) jobs.add(new Job(null, t.getId(), i, false));
            jobs.add(new Job(null, t.getId(), -1, true));
        }
        int slot = 0;
        for (com.xenowarriors.you.YouSlot y : yous) {
            int s = slot++;
            String who = "you:" + y.race.name().toLowerCase();
            jobs.add(new Job(() -> {
                p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.youSlots[s] = y.copy());
                CharacterManager.setYou(p, s);
            }, who, -1, false));
            for (int i = 0; i < com.xenowarriors.you.YouSkills.pool(y).size(); i++) jobs.add(new Job(null, who, i, false));
            jobs.add(new Job(null, who, -1, true));
        }
        int t = 0;
        for (Job j : jobs) {
            ServerScheduler.later(t + 1, () -> run(p, j));
            t += j.setup != null ? 30 : j.ult ? 215 : 110;
        }
        return t;
    }

    private static void run(ServerPlayer p, Job j) {
        try {
            if (j.setup != null) { j.setup.run(); return; }
            prepare(p);
            Skill skill;
            int slot;
            if (j.ult) { slot = SkillManager.ULTIMATE_SLOT; skill = SkillPool.ultimate(p); }
            else {
                slot = 0;
                var d = p.getCapability(ModCapabilities.PLAYER_CHARACTER).orElse(null);
                CharacterType type = ModCapabilities.getType(p);
                int[] l = d.loadout(type).clone();
                for (int k = 1; k < l.length; k++) if (l[k] == j.slotIndex) l[k] = l[0];
                l[0] = j.slotIndex;
                d.setLoadout(type, l);
                skill = SkillPool.inSlot(p, l, 0);
            }
            if (skill == null) { log("skill " + j.who + " slot" + j.slotIndex + " used=false dmg=0 err=no-skill"); return; }
            attempt(p, j, skill, slot, 6, true);
        } catch (Throwable e) {
            log("skill " + j.who + " slot" + j.slotIndex + " used=false dmg=0 err=RUNNER " + e + " @ " + (e.getStackTrace().length > 0 ? e.getStackTrace()[0] : ""));
        }
    }

    /**
     * One try: a dummy `dist` blocks ahead, the skill used (and pressed again a few times, for skills that fire on a
     * second press), then the damage counted. A first try at 6 blocks that didn't fire or did no damage is tried again
     * up close (a melee or close-range move), and both are logged.
     */
    private static void attempt(ServerPlayer p, Job j, Skill skill, int slot, double dist, boolean first) {
        Husk target = spawnDummy(p, dist);
        String id = skill.getId();
        int clash = skill.clashColor();
        int wait = first ? (j.ult ? 120 : 55) : (j.ult ? 80 : 40);
        ServerScheduler.later(5, () -> {                       // (after whatever scales mobs on spawning)
            target.setHealth(target.getMaxHealth());
            DAMAGE.put(target.getUUID(), 0F);
            String err = fire(p, slot);
            for (int k = 0; k < 6; k++) ServerScheduler.later(6 + k * 4, () -> { try { SkillManager.useSkill(p, slot); } catch (Throwable ignored) {} });   // (second presses)
            boolean used = err == null;
            String errF = err == null ? "" : err;
            ServerScheduler.later(wait, () -> {
                float dealt = DAMAGE.getOrDefault(target.getUUID(), 0F);
                DAMAGE.remove(target.getUUID());
                target.discard();
                if (first && errF.isEmpty() && (!used || dealt <= 0)) {
                    log("skill-try " + j.who + " " + id + (j.ult ? "(ult)" : "") + " used=" + used + " dmg=" + Math.round(dealt * 10) / 10F + " dist=" + dist + " -> again up close");
                    prepare(p);
                    attempt(p, j, skill, slot, 2.5, false);
                    return;
                }
                log("skill " + j.who + " " + id + (j.ult ? "(ult)" : "") + " used=" + used + " dmg=" + Math.round(dealt * 10) / 10F + " dist=" + dist
                        + " clash=" + (clash >= 0) + " err=" + errF);
            });
        });
    }

    /** Damage the test dummies have taken (after armor/effects). */
    private static final java.util.Map<java.util.UUID, Float> DAMAGE = new java.util.concurrent.ConcurrentHashMap<>();

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void onDamage(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
            if (DAMAGE.isEmpty()) return;
            DAMAGE.computeIfPresent(e.getEntity().getUUID(), (k, v) -> v + e.getAmount());
        }
    }

    /** Uses the skill in that slot: null if it fired, "" if it didn't, the exception if it threw. */
    private static String fire(ServerPlayer p, int slot) {
        long before = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.cooldownEnd[slot]).orElse(0L);
        try { SkillManager.useSkill(p, slot); } catch (Throwable e) { return e + " @ " + (e.getStackTrace().length > 0 ? e.getStackTrace()[0] : ""); }
        long after = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.cooldownEnd[slot]).orElse(0L);
        return after != before ? null : "";
    }

    private static void prepareCooldowns(ServerPlayer p) {
        SkillManager.resetCooldowns(p);
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.setKarma(d.karmaMax); java.util.Arrays.fill(d.cooldownEnd, 0); });
    }

    private static void prepare(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0, false, false));
        p.getFoodData().setFoodLevel(20);
        SkillManager.resetCooldowns(p);
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.setKarma(d.karmaMax); java.util.Arrays.fill(d.cooldownEnd, 0); });
        p.getPersistentData().remove("xw_clash_ready");
        Vec3 at = home(p);
        p.connection.teleport(at.x, at.y, at.z, 0F, 5F);
        p.setYRot(0F); p.setXRot(5F); p.setYHeadRot(0F);
    }

    private static Vec3 base;

    /** The test floor (built once, high up and clear), with the player put at its start facing +Z. */
    public static Vec3 arena(ServerPlayer p) {
        Vec3 at = home(p);
        p.connection.teleport(at.x, at.y, at.z, 0F, 0F);
        p.setYRot(0F); p.setXRot(0F); p.setYHeadRot(0F);
        return at;
    }

    private static Vec3 home(ServerPlayer p) {
        if (base == null) {
            net.minecraft.core.BlockPos b = new net.minecraft.core.BlockPos(3000, 120, 3000);
            for (int x = -12; x <= 12; x++) for (int z = -6; z <= 30; z++) {
                p.serverLevel().setBlock(b.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) p.serverLevel().setBlock(b.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
            base = new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        }
        return base;
    }

    private static Husk spawnDummy(ServerPlayer p, double dist) {
        Husk h = EntityType.HUSK.create(p.serverLevel());
        Vec3 at = home(p).add(0, 0, dist);
        h.moveTo(at.x, at.y, at.z, 180F, 0F);
        h.setNoAi(true);
        h.setPersistenceRequired();
        h.getPersistentData().putBoolean("xw_mb_rolled", true);
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        p.serverLevel().addFreshEntity(h);
        for (var m : new ArrayList<>(h.getAttribute(Attributes.MAX_HEALTH).getModifiers())) h.getAttribute(Attributes.MAX_HEALTH).removeModifier(m);
        for (var m : new ArrayList<>(h.getAttribute(Attributes.ARMOR).getModifiers())) h.getAttribute(Attributes.ARMOR).removeModifier(m);
        h.getAttribute(Attributes.ARMOR).setBaseValue(0);
        h.setHealth(h.getMaxHealth());
        return h;
    }
}

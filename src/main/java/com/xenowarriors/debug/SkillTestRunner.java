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
            t += j.setup != null ? 30 : j.ult ? 140 : 70;
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
            Husk target = spawnDummy(p);
            float before = target.getHealth();
            long cdBefore = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.cooldownEnd[slot]).orElse(0L);
            String err = "";
            try { SkillManager.useSkill(p, slot); } catch (Throwable e) { err = e + " @ " + (e.getStackTrace().length > 0 ? e.getStackTrace()[0] : ""); }
            long cdAfter = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.cooldownEnd[slot]).orElse(0L);
            boolean used = cdAfter != cdBefore;
            String id = skill.getId(), errF = err;
            int clash = skill.clashColor();
            ServerScheduler.later(j.ult ? 130 : 60, () -> {
                float lost = target.isAlive() ? before - target.getHealth() : before;
                log("skill " + j.who + " " + id + (j.ult ? "(ult)" : "") + " used=" + used + " dmg=" + Math.round(lost * 10) / 10F + " clash=" + (clash >= 0) + " err=" + errF);
                target.discard();
            });
        } catch (Throwable e) {
            log("skill " + j.who + " slot" + j.slotIndex + " used=false dmg=0 err=RUNNER " + e + " @ " + (e.getStackTrace().length > 0 ? e.getStackTrace()[0] : ""));
        }
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

    private static Husk spawnDummy(ServerPlayer p) {
        Husk h = EntityType.HUSK.create(p.serverLevel());
        Vec3 at = home(p).add(0, 0, 6);
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

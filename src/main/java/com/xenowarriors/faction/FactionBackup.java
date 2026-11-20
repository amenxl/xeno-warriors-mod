package com.xenowarriors.faction;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Backup from a faction you're on good terms with (Friendly or better): from the phone (Factions), or on their own the
 * moment a raid starts on you. About ten of that faction's people turn up around you - Government soldiers, Sky
 * Rebellion members, Skylanders, or the town's militia - each about a tenth as strong as you (health and damage). They
 * aren't in your party: they fight whatever you're fighting (and whatever's after you or your bots), never you or
 * yours. When it's over they stay, walking about where they came in; once you're far from there they go home.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionBackup {
    private FactionBackup() {}

    public static final String OWNER = "xw_backup_owner", HOME = "xw_backup_home", TAG = "xw_backup";
    public static final int COUNT = 10;
    /** How far you can go from where they came in before they leave. */
    public static final double LEAVE = 96;
    static final int CALL_GAP = 20 * 60 * 10;
    private static final UUID HP = UUID.fromString("5b1c7e20-9f4a-4c3e-b8d1-6a2f0e9c4d71"), DMG = UUID.fromString("5b1c7e20-9f4a-4c3e-b8d1-6a2f0e9c4d72");
    /** (Tests) backups called, people sent, sent home. */
    public static int called, sent, gone;

    /** Who this backup fights for (null: not backup). */
    public static UUID ownerOf(net.minecraft.world.entity.Entity e) {
        return e != null && e.getPersistentData().hasUUID(OWNER) ? e.getPersistentData().getUUID(OWNER) : null;
    }

    /** Why this player can't call this faction now (null: they can). */
    public static String why(ServerPlayer p, Faction f) {
        if (!Reputation.friendly(p, f)) return f.title + " won't send anyone - you need to be Friendly with them (" + Reputation.tier(p, f).title + " now).";
        if (!near(p, f).isEmpty()) return f.title + " backup is already with you.";
        long left = p.getPersistentData().getLong("xw_backup_cd_" + f.name()) - p.level().getGameTime();
        if (left > 0) return f.title + " can't send anyone again yet (" + (left / 1200 + 1) + " min).";
        if (com.xenowarriors.modes.ModeRules.arena(p.level())) return "No backup can reach you here.";
        return null;
    }

    /** From the phone: calls them in (or says why not). */
    public static boolean call(ServerPlayer p, Faction f) {
        String why = why(p, f);
        if (why != null) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), false); return false; }
        p.getPersistentData().putLong("xw_backup_cd_" + f.name(), p.level().getGameTime() + CALL_GAP);
        return send(p, f, false) > 0;
    }

    /** A raid's started on you: every faction you're Friendly with (not the raiders) sends its people, no call needed. */
    public static void raidStarted(ServerPlayer p, Faction raiders) {
        for (Faction f : Faction.values()) if (f != raiders && Reputation.friendly(p, f) && near(p, f).isEmpty()) send(p, f, true);
    }

    /** Backup of this faction around this player now. */
    static List<Mob> near(ServerPlayer p, Faction f) {
        return p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(LEAVE), m -> m.isAlive() && p.getUUID().equals(ownerOf(m))
                && f.name().equals(m.getPersistentData().getString(TAG)));
    }

    /** Sends {@value #COUNT} of them in around you. How many came. */
    public static int send(ServerPlayer p, Faction f, boolean raid) {
        ServerLevel l = p.serverLevel();
        int n = 0;
        float hp = (float) Math.max(20.0, p.getMaxHealth() * 0.1), dmg = (float) Math.max(3.0, p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.1);
        for (int i = 0; i < COUNT; i++) {
            double a = i * Math.PI * 2 / COUNT + l.random.nextDouble() * 0.4, r = 6 + l.random.nextDouble() * 4;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r), z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - p.getY()) > 8) y = (int) Math.floor(p.getY());
            Mob m = make(l, f, new BlockPos(x, y, z), i);
            if (m == null) continue;
            m.moveTo(x + 0.5, y, z + 0.5, l.random.nextFloat() * 360F, 0F);
            m.getPersistentData().putUUID(OWNER, p.getUUID());
            m.getPersistentData().putString(TAG, f.name());
            m.getPersistentData().putLong(HOME, p.blockPosition().asLong());
            m.setPersistenceRequired();
            var mh = m.getAttribute(Attributes.MAX_HEALTH);
            if (mh != null) { mh.removeModifier(HP); mh.addPermanentModifier(new AttributeModifier(HP, "Backup", hp - mh.getBaseValue(), AttributeModifier.Operation.ADDITION)); }
            var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (md != null) { md.removeModifier(DMG); md.addPermanentModifier(new AttributeModifier(DMG, "Backup", dmg - md.getBaseValue(), AttributeModifier.Operation.ADDITION)); }
            m.setHealth(m.getMaxHealth());
            if (m instanceof PathfinderMob pm) {
                pm.targetSelector.addGoal(0, new Help(pm));
                pm.goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(pm, 1.2, true));
            }
            l.addFreshEntity(m);
            l.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + 0.5, m.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            n++;
        }
        if (n > 0) {
            called++; sent += n;
            l.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 8F, 1.5F);
            p.sendSystemMessage(Component.literal(raid ? f.title + " heard you're under attack - " + n + " of their people are here to help!"
                    : f.title + " sent " + n + " of their people to help you!").withStyle(ChatFormatting.GREEN));
        }
        return n;
    }

    private static Mob make(ServerLevel l, Faction f, BlockPos at, int i) {
        switch (f) {
            case GOV -> {
                var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(l);
                if (g == null) return null;
                g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                g.setup(true, 1, at);
                g.setCustomName(Component.literal("Gov Backup").withStyle(ChatFormatting.AQUA));
                return g;
            }
            case SR -> {
                var s = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(l);
                if (s == null) return null;
                s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                s.setup(i % 2, at);
                s.setCustomName(Component.literal("Sky Rebellion Backup").withStyle(ChatFormatting.RED));
                return s;
            }
            case SKY -> {
                var s = com.xenowarriors.npc.ModNpcs.SKYLANDER.get().create(l);
                if (s == null) return null;
                s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                try { s.finalizeSpawn(l, l.getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
                s.makeGuardian(at);
                s.setCustomName(Component.literal("Sky Patrol Backup").withStyle(ChatFormatting.LIGHT_PURPLE));
                return s;
            }
            default -> {
                var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(l);
                if (h == null) return null;
                h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                try { h.finalizeSpawn(l, l.getCurrentDifficultyAt(at), net.minecraft.world.entity.MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
                h.makeMilitia(i == 0);
                h.setCustomName(Component.literal(i == 0 ? "Militia Captain" : "Town Militia").withStyle(ChatFormatting.GREEN));
                return h;
            }
        }
    }

    /** Who they go for: what you're fighting, what's after you or your side - never anyone on your side. */
    static final class Help extends Goal {
        private final PathfinderMob m;
        Help(PathfinderMob m) { this.m = m; setFlags(EnumSet.of(Flag.TARGET)); }

        private ServerPlayer owner() {
            UUID o = ownerOf(m);
            return o == null || m.getServer() == null ? null : m.getServer().getPlayerList().getPlayer(o);
        }

        @Override public boolean canUse() { return m.tickCount % 10 == 3 && owner() != null; }
        @Override public boolean canContinueToUse() { return false; }

        @Override
        public void start() {
            ServerPlayer o = owner();
            if (o == null || o.level() != m.level()) return;
            LivingEntity t = m.getTarget();
            if (t != null && (!t.isAlive() || com.xenowarriors.combat.Allies.isAlly(o, t) || ownerOf(t) != null && ownerOf(t).equals(o.getUUID()))) { m.setTarget(null); t = null; }
            if (t != null) return;
            LivingEntity pick = null;
            LivingEntity hit = o.getLastHurtMob();
            if (hit != null && hit.isAlive() && o.tickCount - o.getLastHurtMobTimestamp() < 200 && hit.distanceToSqr(m) < 32 * 32 && !com.xenowarriors.combat.Allies.isAlly(o, hit) && ownerOf(hit) == null) pick = hit;
            LivingEntity by = o.getLastHurtByMob();
            if (pick == null && by != null && by.isAlive() && o.tickCount - o.getLastHurtByMobTimestamp() < 200 && !com.xenowarriors.combat.Allies.isAlly(o, by) && ownerOf(by) == null) pick = by;
            if (pick == null) {
                double best = 20 * 20;
                for (Mob x : m.level().getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(20), x -> x.isAlive() && x.getTarget() != null && ownerOf(x) == null
                        && (com.xenowarriors.combat.Allies.isAlly(o, x.getTarget()) || o.getUUID().equals(ownerOf(x.getTarget()))) && !com.xenowarriors.combat.Allies.isAlly(o, x))) {
                    double d = x.distanceToSqr(m);
                    if (d < best) { best = d; pick = x; }
                }
            }
            if (pick != null) m.setTarget(pick);
        }
    }

    /** They don't hit you or yours (or each other), and nothing on your side hurts them. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        var src = e.getSource().getEntity();
        UUID a = ownerOf(src), v = ownerOf(e.getEntity());
        if (a != null && (e.getEntity() instanceof net.minecraft.world.entity.player.Player pl && (pl.getUUID().equals(a) || com.xenowarriors.party.Parties.sameParty(pl.getUUID(), a))
                || v != null || com.xenowarriors.combat.Allies.sameSide(a, com.xenowarriors.combat.Allies.side(e.getEntity())))) { e.setCanceled(true); return; }
        if (v != null && src != null && (ownerOf(src) != null || com.xenowarriors.combat.Allies.sameSide(v, com.xenowarriors.combat.Allies.side(src)))) e.setCanceled(true);
    }

    /** Every 2 seconds: no fight, they wander about where they came in; you're far away (or gone) - they leave. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob m) || m.tickCount % 40 != 21 || !(m.level() instanceof ServerLevel l)) return;
        UUID o = ownerOf(m);
        if (o == null) return;
        ServerPlayer p = l.getServer().getPlayerList().getPlayer(o);
        BlockPos home = BlockPos.of(m.getPersistentData().getLong(HOME));
        if (p == null || p.level() != l || p.blockPosition().distSqr(home) > LEAVE * LEAVE) {
            l.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + 0.5, m.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
            m.discard();
            gone++;
            return;
        }
        if (m.getTarget() != null && m.getTarget().isAlive()) return;
        if (m.blockPosition().distSqr(home) > 18 * 18) m.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
        else if (m.getNavigation().isDone() && l.random.nextInt(3) == 0)
            m.getNavigation().moveTo(home.getX() + 0.5 + l.random.nextInt(21) - 10, home.getY(), home.getZ() + 0.5 + l.random.nextInt(21) - 10, 0.8);
    }
}

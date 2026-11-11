package com.xenowarriors.gov;

import com.xenowarriors.karma.KarmaLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Government soldiers and their Karma Scanners. Guards (and workers with the Scanner Eye mod) wear one: tougher guards
 * and guards far out get better tiers (Commanders an Elite), in a mix of lens colours. Every couple of seconds a guard
 * scans whoever it's fighting, or a player it can see nearby: a beam of light from the lens, a beep, and the person
 * scanned is told their reading. The overload rule is the same as yours: read someone past its capacity and it blows
 * apart - and the guard PANICS and runs (GovAgentEntity's panic goal) instead of fighting. A scanner worn out by hits
 * breaks the same way. In a training fight (Tower, Journey, VR) the guard panics too, runs for 3-5 seconds and then
 * vanishes (teleports out) - it counts as gone for the wave, but it's no kill: no drops, XP or Collection credit. A
 * Tower / Journey boss (a Gov Commander) shrugs it off and keeps fighting instead, so a boss floor can't be skipped.
 */
public final class GovScanning {
    private GovScanning() {}

    public static final String ROLLED = "xw_scanner_rolled", LAST_READ = "xw_scan_last", LAST_WHO = "xw_scan_who";
    /** How long a guard panics after its scanner breaks (ticks). */
    public static final int PANIC_TICKS = 20 * 15;
    /** In a training fight: how long a guard flees before it vanishes (3-5 s). */
    public static final int VANISH_MIN = 60, VANISH_SPREAD = 41;

    /** Give a guard its scanner (once per guard; also for guards from before scanners). */
    public static void equip(GovAgentEntity g) {
        var t = g.getPersistentData();
        if (t.getBoolean(ROLLED)) return;
        t.putBoolean(ROLLED, true);
        if (!g.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        if (!g.guard() && !g.has(GovAgentEntity.SCANNER)) return;            // (office workers: only the ones with the Scanner Eye mod)
        var r = g.getRandom();
        KarmaScanner.Tier tier = KarmaScanner.Tier.BASIC;
        if (g.guard()) {
            int zone = g.level() instanceof ServerLevel l ? com.xenowarriors.scaling.EnemyScaling.zone(l, g.blockPosition()) : 0;
            float adv = Math.min(0.75F, 0.15F + 0.08F * zone + (g.has(GovAgentEntity.ARMOR) || g.has(GovAgentEntity.ATTACK_MODE) ? 0.15F : 0F));
            if (r.nextFloat() < adv) tier = KarmaScanner.Tier.ADVANCED;
            if (zone >= 5 && r.nextFloat() < 0.25F) tier = KarmaScanner.Tier.ELITE;
        }
        give(g, tier, pickColor(g));
    }

    /** A lens colour: mostly the tier's own, often another (black for the Commanders' squads...). */
    static int pickColor(GovAgentEntity g) {
        var r = g.getRandom();
        return r.nextFloat() < 0.4F ? -1 : KarmaScanner.COLORS[r.nextInt(KarmaScanner.COLORS.length)];
    }

    public static void give(GovAgentEntity g, KarmaScanner.Tier tier, int color) {
        var item = GovItems.scanner(tier);
        ItemStack s = color < 0 ? new ItemStack(item) : KarmaScanner.colored(item, color);
        g.setItemSlot(EquipmentSlot.HEAD, s);
        g.setDropChance(EquipmentSlot.HEAD, tier == KarmaScanner.Tier.BASIC ? 0.08F : 0.04F);
        g.getPersistentData().putBoolean(ROLLED, true);
        g.getPersistentData().remove(LAST_WHO);          // (a new scanner takes a fresh reading)
    }

    /** Every 10 ticks (server): scan someone, maybe overload. */
    public static void tick(GovAgentEntity g) {
        if (!(g.level() instanceof ServerLevel l) || g.tickCount % 10 != 0 || g.panicking() || g.isNoAi()) return;    // (no-AI props don't scan)
        KarmaScanner.Item k = KarmaScanner.worn(g);
        if (k == null) return;
        LivingEntity who = subject(g, k.tier.range);
        if (who == null) return;
        var t = g.getPersistentData();
        // the same person: every 3 seconds in a fight, every 30 s otherwise (a new face right away)
        if (t.getInt(LAST_WHO) == who.getId() && g.tickCount - t.getInt("xw_scan_at") < (who == g.getTarget() ? 60 : 600)) return;
        t.putInt(LAST_WHO, who.getId());
        t.putInt("xw_scan_at", g.tickCount);
        long v = KarmaLevel.of(who);
        t.putLong(LAST_READ, v);
        beam(l, g, who, k.getColor(g.getItemBySlot(EquipmentSlot.HEAD)));
        if (k.tier.overloads(v)) { overload(g, who, k.tier, v); return; }
        if (who instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal(g.getName().getString() + " scanned you - Karma Level " + KarmaLevel.fmt(v)).withStyle(ChatFormatting.AQUA), true);
    }

    /** Whom to scan: whoever it's fighting, else the nearest player it can see. */
    private static LivingEntity subject(GovAgentEntity g, double range) {
        LivingEntity t = g.getTarget();
        if (t != null && t.isAlive() && g.distanceTo(t) <= range && g.getSensing().hasLineOfSight(t)) return t;
        Player p = g.level().getNearestPlayer(g.getX(), g.getY(), g.getZ(), Math.min(range, 16), e -> e instanceof Player pl && !pl.isSpectator() && g.getSensing().hasLineOfSight(pl));
        return p;
    }

    /** The scan, seen: dots of the lens colour from its eye to them, a beep. */
    private static void beam(ServerLevel l, GovAgentEntity g, LivingEntity who, int color) {
        g.getLookControl().setLookAt(who, 30F, 30F);
        Vec3 a = g.getEyePosition(), b = who.getEyePosition().add(0, -0.3, 0);
        var dust = new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F), 0.7F);
        int n = (int) Math.min(24, Math.max(4, a.distanceTo(b) * 2));
        for (int i = 1; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            l.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        l.playSound(null, g.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 0.35F, 1.9F);
    }

    private static final String[] PANIC = {
            "It's... it's off the charts! RUN!",
            "Over %s?! That's not human - fall back!",
            "My scanner just exploded! I'm out of here!",
            "No way... nobody's that strong! Retreat!"};

    /** Read someone too strong: the scanner blows apart and the guard panics. */
    public static void overload(GovAgentEntity g, LivingEntity who, KarmaScanner.Tier tier, long v) {
        KarmaScanner.blowUp(g, tier);
        if (training(g) && boss(g)) {          // (a Tower / Journey boss: shrugs it off and keeps going)
            say(g, "Scanner's fried... doesn't matter - take them down!");
            return;
        }
        String line = String.format(PANIC[g.getRandom().nextInt(PANIC.length)], KarmaLevel.fmt(tier.capacity));
        say(g, line);
        if (who instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal(g.getName().getString() + "'s " + tier.title + " overloaded on your Karma Level!").withStyle(ChatFormatting.GOLD), true);
        flee(g, who);
    }

    /** A guard's scanner worn through by hits: same panic. */
    public static void wornOut(GovAgentEntity g, LivingEntity from) {
        if (training(g) && boss(g)) return;
        say(g, "My scanner's busted - I can't read them! Fall back!");
        flee(g, from);
    }

    /** Panic: out in the world it runs for 15 s and goes back to its post; in a training fight it runs 3-5 s and vanishes. */
    private static void flee(GovAgentEntity g, LivingEntity from) {
        if (g.vanishing()) return;
        if (training(g)) g.panicAndVanish(from, VANISH_MIN + g.getRandom().nextInt(VANISH_SPREAD));
        else g.panic(from, PANIC_TICKS);
    }

    /** A Tower / Journey boss (Gov Commander). */
    static boolean boss(GovAgentEntity g) { return com.xenowarriors.tower.TowerBosses.isBoss(g); }

    /** A training fight (Tower, Journey, VR arenas): a panicking guard vanishes there instead of running back to its post. */
    public static boolean training(GovAgentEntity g) {
        var t = g.getPersistentData();
        return t.getBoolean("xw_journey") || t.getBoolean("xw_tower") || com.xenowarriors.modes.ModeRules.arena(g.level());
    }

    static void say(GovAgentEntity g, String line) {
        if (!(g.level() instanceof ServerLevel l)) return;
        Component c = Component.literal("<" + g.getName().getString() + "> ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(g) < 32 * 32) p.sendSystemMessage(c);
    }

    /** Hits wear a guard's scanner down (mobs' armor never wears by itself); when it breaks they panic. */
    public static void onHurt(GovAgentEntity g, net.minecraft.world.damagesource.DamageSource src, float amount) {
        ItemStack s = g.getItemBySlot(EquipmentSlot.HEAD);
        if (!(s.getItem() instanceof KarmaScanner.Item) || !s.isDamageableItem()) return;
        s.hurtAndBreak(Math.max(1, (int) (amount / 2)), g, e -> e.broadcastBreakEvent(EquipmentSlot.HEAD));
        if (g.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || s.isEmpty())
            wornOut(g, src.getEntity() instanceof LivingEntity le ? le : null);
    }
}

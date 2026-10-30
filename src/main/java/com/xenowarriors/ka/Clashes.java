package com.xenowarriors.ka;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ClashStatePacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.Rooting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Element / Karma clashes, like Ki clashes: two blasts meet between the fighters and push against each other. Players
 * mash attack (left click) to push; NPCs push with their Karma. Whoever gets pushed back eats both blasts.
 * Starts when a player fires a clashing skill at an enemy charging an element attack at them, or two players fire
 * clashing skills at each other within half a second.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Clashes {
    public static final int MAX_TICKS = 120;
    private static final List<Clash> ACTIVE = new ArrayList<>();
    private static final String READY = "xw_clash_ready";

    private Clashes() {}

    static final class Clash {
        final LivingEntity a, b;          // a pushes toward b (pos -> 1), b toward a (pos -> 0)
        final Vec3 anchorA, anchorB;
        final int colorA, colorB;
        final float dmg;
        float pos = 0.5F;
        int ticks, clicksA, clicksB;
        /** A DragonMineZ ki attack on b's side (held where the blasts meet), and how much stronger it pushes. */
        net.minecraft.world.entity.Entity kiB;
        float biasB = 1F;

        Clash(LivingEntity a, LivingEntity b, int colorA, int colorB, float dmg) {
            this.a = a; this.b = b; this.colorA = colorA; this.colorB = colorB; this.dmg = dmg;
            anchorA = a.position(); anchorB = b.position();
        }
    }

    /** What happened last (for the UI tests' logs). */
    public static String last = "";

    /** This DragonMineZ ki attack is already held in a clash. */
    static boolean isKiClashing(net.minecraft.world.entity.Entity ki) {
        for (Clash c : ACTIVE) if (c.kiB == ki) return true;
        return false;
    }

    public static boolean inClash(LivingEntity e) {
        for (Clash c : ACTIVE) if (c.a == e || c.b == e) return true;
        return false;
    }

    /** A player used a clashing skill. Returns true if it turned into a clash (so the skill itself doesn't fire). */
    public static boolean tryStart(ServerPlayer p, int color) {
        if (inClash(p)) return true;
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getViewVector(1F);
        // an enemy charging an element attack at them, in front of them
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> Elements.chargingAt(e, p))) {
            Vec3 to = e.getEyePosition().subtract(p.getEyePosition()).normalize();
            if (to.dot(look) < Math.cos(Math.toRadians(30)) || inClash(e)) continue;
            Element el = Elements.of(e);
            Elements.cancelCharge(e);
            start(p, e, color, el == null ? 0xFFFFFF : el.color, 12F);
            return true;
        }
        // a DragonMineZ ki attack coming at them
        if (DmzKi.tryClash(p, color)) return true;
        // another player who just fired one at them
        long now = level.getGameTime();
        for (ServerPlayer o : level.players()) {
            if (o == p || o.distanceToSqr(p) > 24 * 24 || inClash(o)) continue;
            if (o.getPersistentData().getLong(READY) < now) continue;
            Vec3 to = o.getEyePosition().subtract(p.getEyePosition()).normalize();
            if (to.dot(look) < Math.cos(Math.toRadians(30)) || to.scale(-1).dot(o.getViewVector(1F)) < Math.cos(Math.toRadians(35))) continue;
            if (!com.xenowarriors.skill.SkillTargeting.canTarget(p, o)) continue;
            o.getPersistentData().remove(READY);
            start(o, p, o.getPersistentData().getInt(READY + "_color"), color, 14F);
            return true;
        }
        p.getPersistentData().putLong(READY, now + 10);
        p.getPersistentData().putInt(READY + "_color", color);
        return false;
    }

    /** Our player (a) against a DragonMineZ ki attack fired by b (its owner; may be any living thing). */
    static void startKi(ServerPlayer a, LivingEntity b, net.minecraft.world.entity.Entity ki, int ca, int cb, float dmg, float bias) {
        Clash c = new Clash(a, b, ca, cb, dmg);
        c.kiB = ki; c.biasB = bias;
        ACTIVE.add(c);
        last = "start " + a.getName().getString() + " vs " + b.getName().getString() + " (DragonMineZ ki, x" + Math.round(bias * 100) / 100F + ")";
        ServerLevel l = (ServerLevel) a.level();
        l.playSound(null, a.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
        for (LivingEntity e : new LivingEntity[]{a, b})
            if (e instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("CLASH! Mash attack (left click) to push it back!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void start(LivingEntity a, LivingEntity b, int ca, int cb, float dmg) {
        ACTIVE.add(new Clash(a, b, ca, cb, dmg));
        last = "start " + a.getName().getString() + " vs " + b.getName().getString();
        ServerLevel l = (ServerLevel) a.level();
        l.playSound(null, a.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
        for (LivingEntity e : new LivingEntity[]{a, b})
            if (e instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal("CLASH! Mash attack (left click) to push it back!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    /** A player mashing during a clash. */
    public static void push(ServerPlayer p) {
        for (Clash c : ACTIVE) {
            if (c.a == p && c.clicksA < 3) { c.clicksA++; return; }
            if (c.b == p && c.clicksB < 3) { c.clicksB++; return; }
        }
    }

    private static float npcPush(LivingEntity e) {
        float k = EnemyKarma.usesKarma(e) ? EnemyKarma.get(e) : 50F;
        if (EnemyKarma.usesKarma(e) && k > 0.5F) EnemyKarma.spend(e, 0.5F);
        return 0.0045F + Math.min(k, 200F) / 200F * 0.0075F;
    }

    private static float playerPush(Clash c, ServerPlayer p, int clicks) {
        float stat = 1F + com.xenowarriors.stats.Stats.level(p, com.xenowarriors.stats.Stat.ATTACK) * 0.004F;
        if (clicks > 0 && !p.isCreative()) com.xenowarriors.karma.Karma.add(p, -0.6F * clicks);
        boolean karma = com.xenowarriors.karma.Karma.get(p) > 1F || p.isCreative();
        return clicks * 0.012F * Math.min(2F, stat) * (karma ? 1F : 0.35F);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        for (Clash c : new ArrayList<>(ACTIVE)) {
            if (!c.a.isAlive() || !c.b.isAlive() || c.a.level() != c.b.level() || c.a.distanceTo(c.b) > 32) { end(c, -1); continue; }
            c.ticks++;
            Rooting.hold(c.a, c.anchorA);
            Rooting.hold(c.b, c.anchorB);
            if (c.ticks % 8 == 1) { com.xenowarriors.anim.Poses.play(c.a, com.xenowarriors.anim.Poses.CLASH, 12); com.xenowarriors.anim.Poses.play(c.b, com.xenowarriors.anim.Poses.CLASH, 12); }
            float pa = c.a instanceof ServerPlayer sa ? playerPush(c, sa, c.clicksA) : npcPush(c.a);
            float pb = (c.b instanceof ServerPlayer sb ? playerPush(c, sb, c.clicksB) : npcPush(c.b)) * c.biasB;
            c.clicksA = c.clicksB = 0;
            c.pos = Math.max(0F, Math.min(1F, c.pos + pa - pb));
            ServerLevel l = (ServerLevel) c.a.level();
            Vec3 ea = c.a.getEyePosition().subtract(0, 0.3, 0), eb = c.b.getEyePosition().subtract(0, 0.3, 0);
            Vec3 meet = ea.lerp(eb, c.pos);
            if (c.kiB != null) {                                    // the ki attack is held where the blasts meet
                if (!c.kiB.isAlive()) { end(c, 1); continue; }     // (it fizzled out: the push wins)
                DmzKi.hold(c.kiB, meet, c.a);
            }
            if (c.ticks % 2 == 0) {
                beam(l, ea, meet, c.colorA);
                beam(l, eb, meet, c.colorB);
                l.sendParticles(ParticleTypes.EXPLOSION, meet.x, meet.y, meet.z, 1, 0.1, 0.1, 0.1, 0);
                l.sendParticles(ParticleTypes.FLASH, meet.x, meet.y, meet.z, 1, 0, 0, 0, 0);
            }
            if (c.ticks % 6 == 0) l.playSound(null, net.minecraft.core.BlockPos.containing(meet), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 0.6F + c.pos * 0.8F);
            if (c.ticks % 2 == 0) for (LivingEntity e : new LivingEntity[]{c.a, c.b})
                if (e instanceof ServerPlayer sp) ModNetwork.sendToPlayer(new ClashStatePacket(true, e == c.a ? c.pos : 1F - c.pos, c.ticks, (e == c.a ? c.b : c.a).getName().getString()), sp);
            if (c.pos >= 0.97F) end(c, 1);            // b loses
            else if (c.pos <= 0.03F) end(c, 0);       // a loses
            else if (c.ticks >= MAX_TICKS) end(c, 2); // stalemate: it blows up between them
        }
    }

    private static void beam(ServerLevel l, Vec3 from, Vec3 to, int color) {
        Vector3f c = new Vector3f((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        double len = from.distanceTo(to);
        int n = Math.max(2, (int) (len * 3));
        for (int i = 0; i <= n; i++) {
            Vec3 q = from.lerp(to, i / (double) n);
            l.sendParticles(new DustParticleOptions(c, 1.6F), q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    /** winner: 1 = a wins, 0 = b wins, 2 = draw, -1 = broken off. */
    private static void end(Clash c, int result) {
        ACTIVE.remove(c);
        last = "end " + (result == 1 ? c.a.getName().getString() + " won" : result == 0 ? c.b.getName().getString() + " won" : result == 2 ? "draw" : "broken off") + (c.kiB != null ? " (DragonMineZ ki)" : "") + " after " + c.ticks + " ticks";
        if (c.kiB != null) DmzKi.release(c.kiB);                 // the ki attack is spent either way (the loser takes the hit below)
        ServerLevel l = (ServerLevel) c.a.level();
        for (LivingEntity e : new LivingEntity[]{c.a, c.b}) {
            e.getPersistentData().remove(Rooting.ROOTED_UNTIL);
            com.xenowarriors.anim.Poses.clearState(e);
            if (e instanceof ServerPlayer sp) ModNetwork.sendToPlayer(new ClashStatePacket(false, 0, 0, ""), sp);
        }
        if (result < 0) return;
        Vec3 ea = c.a.getEyePosition(), eb = c.b.getEyePosition();
        Vec3 meet = ea.lerp(eb, c.pos);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, meet.x, meet.y, meet.z, 1, 0, 0, 0, 0);
        l.playSound(null, net.minecraft.core.BlockPos.containing(meet), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4F, 0.8F);
        if (result == 2) {
            com.xenowarriors.anim.Poses.play(c.a, com.xenowarriors.anim.Poses.CLASH_LOSE, 20); com.xenowarriors.anim.Poses.play(c.b, com.xenowarriors.anim.Poses.CLASH_LOSE, 20);
            for (LivingEntity e : new LivingEntity[]{c.a, c.b}) {
                e.hurt(l.damageSources().explosion(null, null), c.dmg * 0.4F);
                Vec3 away = e.position().subtract(meet).multiply(1, 0, 1).normalize();
                e.push(away.x, 0.4, away.z); e.hurtMarked = true;
            }
            for (LivingEntity e : new LivingEntity[]{c.a, c.b}) if (e instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("The clash blew up between you!").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        LivingEntity win = result == 1 ? c.a : c.b, lose = result == 1 ? c.b : c.a;
        com.xenowarriors.anim.Poses.play(win, com.xenowarriors.anim.Poses.CLASH_WIN, 30);
        com.xenowarriors.anim.Poses.play(lose, com.xenowarriors.anim.Poses.CLASH_LOSE, 20);
        lose.hurt(win instanceof Player pw ? l.damageSources().playerAttack(pw) : l.damageSources().mobAttack(win), c.dmg * 2F);
        Vec3 away = lose.position().subtract(win.position()).multiply(1, 0, 1).normalize();
        lose.push(away.x * 1.8, 0.6, away.z * 1.8); lose.hurtMarked = true;
        if (win instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("You won the clash!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        if (lose instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("You lost the clash...").withStyle(ChatFormatting.RED), true);
    }
}

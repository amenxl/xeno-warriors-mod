package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.damage.ModDamageTypes;
import com.xenowarriors.entity.KishiDomeEntity;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillAnimPacket;
import com.xenowarriors.network.SkillCooldownPacket;
import com.xenowarriors.skill.SkillManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Team ultimate - Gravity Fox Fire: Mikiko and Rhalix in the same party, within 12 blocks. One of them sneaks and
 * presses their ultimate key; the other gets a prompt and joins (presses their ultimate key, or clicks [Join]).
 * Rhalix slams down a full-strength Kishi dome that pins the enemies, then Mikiko's fox fire storms through
 * everything inside it, ending in one big blast. Uses both ultimates' cooldowns and Karma from both.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TeamUltimate {
    public static final int KARMA = 40, PROMPT_TICKS = 160;
    private static final double RANGE = 12;
    private record Request(UUID from, UUID to, long expires) {}
    private static final Map<UUID, Request> PENDING = new HashMap<>();          // by the one being asked
    private static final List<Run> RUNS = new ArrayList<>();

    private TeamUltimate() {}

    private static ServerPlayer partner(ServerPlayer p) {
        CharacterType me = ModCapabilities.getType(p);
        CharacterType want = me == CharacterType.MIKIKO ? CharacterType.RHALIX : me == CharacterType.RHALIX ? CharacterType.MIKIKO : null;
        if (want == null) return null;
        ServerPlayer best = null;
        for (ServerPlayer m : Parties.members(p)) {
            if (m == p || m.level() != p.level() || !m.isAlive() || m.distanceTo(p) > RANGE) continue;
            if (ModCapabilities.getType(m) == want && (best == null || m.distanceTo(p) < best.distanceTo(p))) best = m;
        }
        return best;
    }

    /** Ultimate key pressed: answers a pending team-up, or (sneaking) asks the partner. False = do the normal ultimate. */
    public static boolean onUltimateKey(ServerPlayer p) {
        Request r = PENDING.get(p.getUUID());
        if (r != null && p.level().getGameTime() <= r.expires) {
            join(p);
            return true;
        }
        if (!p.isShiftKeyDown()) return false;
        ServerPlayer other = partner(p);
        if (other == null) return false;
        if (!ready(p, true) || !ready(other, false)) {
            if (!ready(other, false)) p.displayClientMessage(Component.literal(other.getName().getString() + " isn't ready for a team ultimate yet")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        PENDING.put(other.getUUID(), new Request(p.getUUID(), other.getUUID(), p.level().getGameTime() + PROMPT_TICKS));
        p.displayClientMessage(Component.literal("Asked " + other.getName().getString() + " for Gravity Fox Fire...").withStyle(ChatFormatting.GOLD), true);
        other.sendSystemMessage(Component.literal(p.getName().getString() + " wants to do Gravity Fox Fire! Press your ultimate key or ")
                .withStyle(ChatFormatting.GOLD).append(Parties.button("[Join]", "/xeno teamult join", ChatFormatting.GREEN)));
        other.level().playSound(null, other.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1F, 1.4F);
        return true;
    }

    private static boolean ready(ServerPlayer p, boolean tell) {
        long now = p.server.overworld().getGameTime();
        boolean[] ok = {true};
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.cooldownEnd[SkillManager.ULTIMATE_SLOT] > now) ok[0] = false;
        });
        if (!ok[0]) {
            if (tell) p.displayClientMessage(Component.literal("Your ultimate isn't ready").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!Karma.has(p, KARMA)) {
            if (tell) Karma.notEnough(p, KARMA);
            return false;
        }
        return true;
    }

    public static void join(ServerPlayer p) {
        Request r = PENDING.remove(p.getUUID());
        if (r == null || p.level().getGameTime() > r.expires) {
            p.displayClientMessage(Component.literal("No team ultimate to join right now").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerPlayer asker = p.server.getPlayerList().getPlayer(r.from);
        if (asker == null || !Parties.sameParty(asker, p) || asker.level() != p.level() || asker.distanceTo(p) > RANGE
                || !ready(asker, false) || !ready(p, true)) {
            p.displayClientMessage(Component.literal("Too late - you're not together for it any more").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerPlayer rhalix = ModCapabilities.getType(p) == CharacterType.RHALIX ? p : asker;
        ServerPlayer mikiko = rhalix == p ? asker : p;
        for (ServerPlayer s : new ServerPlayer[]{rhalix, mikiko}) {
            if (!s.isCreative()) Karma.add(s, -KARMA);
            var skill = SkillManager.get(ModCapabilities.getType(s), SkillManager.ULTIMATE_SLOT);
            int cd = skill == null ? 60 * 20 : skill.getCooldownTicks();
            long now = s.server.overworld().getGameTime();
            s.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.cooldownEnd[SkillManager.ULTIMATE_SLOT] = now + cd);
            ModNetwork.sendToPlayer(new SkillCooldownPacket(SkillManager.ULTIMATE_SLOT, cd, cd), s);
        }
        start(rhalix, mikiko);
    }

    // ------------------------------------------------------------------ the move

    private static final class Run {
        final ServerPlayer rhalix, mikiko;
        final Vec3 at;
        int t;
        Run(ServerPlayer r, ServerPlayer m, Vec3 at) { rhalix = r; mikiko = m; this.at = at; }
    }

    private static void start(ServerPlayer rhalix, ServerPlayer mikiko) {
        Vec3 at = target(rhalix, mikiko);
        KishiDomeEntity dome = KishiDomeEntity.create(rhalix.level(), rhalix, at);
        dome.raiseLevel();
        dome.raiseLevel();
        rhalix.level().addFreshEntity(dome);
        ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(rhalix.getId(), SkillAnimPacket.KISHI, !rhalix.onGround()), rhalix);
        rhalix.level().playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2F, 0.5F);
        rhalix.level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1F, 0.4F);
        for (ServerPlayer s : new ServerPlayer[]{rhalix, mikiko})
            s.displayClientMessage(Component.literal("GRAVITY FOX FIRE!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        RUNS.add(new Run(rhalix, mikiko, at));
    }

    /** The biggest group of enemies near them, or where Rhalix is looking. */
    private static Vec3 target(ServerPlayer rhalix, ServerPlayer mikiko) {
        List<LivingEntity> foes = enemies(rhalix, new AABB(rhalix.blockPosition()).inflate(18), rhalix, mikiko);
        if (!foes.isEmpty()) {
            LivingEntity best = foes.get(0);
            int bestN = -1;
            for (LivingEntity f : foes) {
                int n = 0;
                for (LivingEntity g : foes) if (g.distanceTo(f) < 5) n++;
                if (n > bestN) { bestN = n; best = f; }
            }
            return best.position();
        }
        Vec3 eye = rhalix.getEyePosition();
        Vec3 end = eye.add(rhalix.getViewVector(1F).scale(16));
        var hit = rhalix.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rhalix));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static List<LivingEntity> enemies(ServerPlayer src, AABB box, ServerPlayer a, ServerPlayer b) {
        return src.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != a && e != b && !(e instanceof Player)
                && Parties.petOwner(e) == null && (e instanceof Enemy || com.xenowarriors.karma.KarmaSensing.corrupted(e)
                || (e instanceof Mob m && m.getTarget() instanceof Player t && Parties.sameParty(t, a))));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long t0 = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld().getGameTime(); PENDING.values().removeIf(r -> false && t0 < 0);
        RUNS.removeIf(TeamUltimate::step);
    }

    /** One tick of the move; true when it's over. */
    private static boolean step(Run r) {
        r.t++;
        if (!r.mikiko.isAlive() || !r.rhalix.isAlive() || r.mikiko.level() != r.rhalix.level()) return true;
        ServerLevel level = r.rhalix.serverLevel();
        double radius = KishiDomeEntity.MAX_RADIUS;
        List<LivingEntity> inside = enemies(r.rhalix, new AABB(r.at, r.at).inflate(radius, KishiDomeEntity.MAX_HEIGHT, radius), r.rhalix, r.mikiko);
        // pinned: dragged into the dome and held down
        for (LivingEntity e : inside) {
            Vec3 pull = r.at.subtract(e.position()).multiply(0.06, 0, 0.06);
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 1, 0.3).add(pull.x, -0.2, pull.z));
            e.hurtMarked = true;
        }
        if (r.t == 20) {
            ModNetwork.sendToTrackingAndSelf(new SkillAnimPacket(r.mikiko.getId(), SkillAnimPacket.BLITZ, !r.mikiko.onGround()), r.mikiko);
            level.playSound(null, r.mikiko.getX(), r.mikiko.getY(), r.mikiko.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.7F);
        }
        // the fox fire storm through the dome
        if (r.t >= 22 && r.t <= 82) {
            for (int i = 0; i < 6; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = level.random.nextDouble() * radius;
                level.sendParticles(ParticleTypes.FLAME, r.at.x + Math.cos(a) * d, r.at.y + level.random.nextDouble() * 3, r.at.z + Math.sin(a) * d, 2, 0.1, 0.2, 0.1, 0.03);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, r.at.x + Math.cos(a + 1) * d, r.at.y + 0.2, r.at.z + Math.sin(a + 1) * d, 1, 0.1, 0.4, 0.1, 0.05);
            }
            if (r.t % 6 == 0) {
                level.playSound(null, r.at.x, r.at.y, r.at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.8F + level.random.nextFloat() * 0.4F);
                for (LivingEntity e : inside) {
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.source(level, ModDamageTypes.FIRE_BLITZ, r.mikiko, r.mikiko), 3F);
                    e.setSecondsOnFire(4);
                    level.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + 1, e.getZ(), 2, 0.2, 0.3, 0.2, 0);
                }
            }
        }
        // the finale: everything in the dome goes up in one blast
        if (r.t == 90) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, r.at.x, r.at.y + 1, r.at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, r.at.x, r.at.y + 1, r.at.z, 120, radius * 0.5, 1.5, radius * 0.5, 0.15);
            level.playSound(null, r.at.x, r.at.y, r.at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2F, 0.7F);
            for (LivingEntity e : inside) {
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.source(level, ModDamageTypes.FIRE_BLITZ, r.mikiko, r.mikiko), 14F);
                Vec3 out = e.position().subtract(r.at).normalize();
                e.setDeltaMovement(out.x * 0.8, 0.6, out.z * 0.8);
                e.hurtMarked = true;
            }
            return true;
        }
        return false;
    }
}

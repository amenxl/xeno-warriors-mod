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
        com.xenowarriors.network.PopupPacket.send(other, com.xenowarriors.network.PopupPacket.REQUEST, p.getUUID(), ModCapabilities.getType(p).ordinal(), -1, "Team attack!",
                p.getName().getString() + " wants Gravity Fox Fire! Ultimate key to join.", "shout");
        ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Team Ultimate",
                p.getName().getString() + " wants to do Gravity Fox Fire!", "/xeno teamult join", "", PROMPT_TICKS, 0xFFFFB040), other);
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
            com.xenowarriors.journey.Journey.onSkill(s);
            var skill = SkillManager.get(ModCapabilities.getType(s), SkillManager.ULTIMATE_SLOT);
            int cd = skill == null ? 60 * 20 : skill.getCooldownTicks();
            long now = s.server.overworld().getGameTime();
            s.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.cooldownEnd[SkillManager.ULTIMATE_SLOT] = now + cd);
            ModNetwork.sendToPlayer(new SkillCooldownPacket(SkillManager.ULTIMATE_SLOT, cd, cd), s);
        }
        start(rhalix, mikiko);
    }

    // ------------------------------------------------------------------ the move

    private static void start(ServerPlayer rhalix, ServerPlayer mikiko) {
        Vec3 at = target(rhalix, mikiko);
        // stand the dome on the ground under the target point

        {
            net.minecraft.core.BlockPos.MutableBlockPos bp = net.minecraft.core.BlockPos.containing(at).mutable();
            for (int i = 0; i < 12 && rhalix.level().getBlockState(bp.below()).getCollisionShape(rhalix.level(), bp.below()).isEmpty(); i++) bp.move(0, -1, 0);
            at = new Vec3(at.x, bp.getY(), at.z);
        }
        rhalix.level().addFreshEntity(com.xenowarriors.entity.TeamUltEntity.create(rhalix, mikiko, at));
        for (ServerPlayer s : new ServerPlayer[]{rhalix, mikiko})
            s.displayClientMessage(Component.literal("GRAVITY FOX FIRE!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
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
}

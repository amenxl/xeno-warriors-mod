package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.BotTeamUlt;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.entity.TeamUltEntity;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillCooldownPacket;
import com.xenowarriors.skill.SkillEvents;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.rhalix.JudgmentAbsorptionSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
import net.minecraftforge.common.util.FakePlayer;

import java.util.*;

/**
 * Team ultimates - Mikiko and Rhalix on the same side (party, or a player and their bot, or two bots), within 12
 * blocks. Either of them can be a player or a party bot.
 * <ul>
 *   <li><b>Gravity Fox Fire</b>: Rhalix slams down a full-strength Kishi dome that pins the enemies, then Mikiko's fox
 *       fire storms through everything inside it, ending in one big blast.</li>
 *   <li><b>Blazing Judgment</b> (once Rhalix knows Judgment Absorption, Character Level 30): Rhalix's Judgment
 *       Absorption drags every enemy into one knot, then Mikiko brings her Blazing Wind down on it.</li>
 * </ul>
 * A player sneaks and presses their ultimate key: their partner gets a request (a player answers with the ultimate key
 * or [Join]; a bot answers at once). Sneak + ultimate again while waiting switches to the other team ultimate. Bots
 * ask their player for one in a fight when both are ready (see bot/BotTeamUlt), and two bots do it together on their own.
 * Uses both ultimates' cooldowns and {@link #KARMA} Karma from both.
 */
public final class TeamUltimate {
    public static final int KARMA = 40, PROMPT_TICKS = 160;
    public static final double RANGE = 12;
    private static final String PREF = "xw_teamult_pick";

    public enum Kind {
        GRAVITY_FOX_FIRE("Gravity Fox Fire", TeamUltEntity.GRAVITY_FOX_FIRE),
        BLAZING_JUDGMENT("Blazing Judgment", TeamUltEntity.BLAZING_JUDGMENT),
        /** Mikiko + Kaeru (HekubladeBarrage). */
        HEKUBLADE_BARRAGE("Hekublade Scatter Barrage", -1);
        public final String title;
        public final int id;
        Kind(String title, int id) { this.title = title; this.id = id; }
    }

    private record Request(UUID from, UUID to, long expires, Kind kind) {}
    private static final Map<UUID, Request> PENDING = new HashMap<>();          // by the one being asked (always a player)

    private TeamUltimate() {}

    // ------------------------------------------------------------------ who's who

    /** The character of a player or a bot (NONE for anything else). */
    public static CharacterType type(LivingEntity e) {
        if (e instanceof PartyBotEntity b) return b.character();
        if (e instanceof ServerPlayer p && !(p instanceof FakePlayer)) return ModCapabilities.getType(p);
        return CharacterType.NONE;
    }

    /** Who this character can team up with: Mikiko with Rhalix or Kaeru, Rhalix and Kaeru with Mikiko. */
    private static java.util.Set<CharacterType> want(CharacterType me) {
        return me == CharacterType.MIKIKO ? java.util.EnumSet.of(CharacterType.RHALIX, CharacterType.KAERU)
                : me == CharacterType.RHALIX || me == CharacterType.KAERU ? java.util.EnumSet.of(CharacterType.MIKIKO) : java.util.EnumSet.noneOf(CharacterType.class);
    }

    /** A Mikiko and Kaeru pair (their team attack is the Hekublade Scatter Barrage). */
    private static boolean kaeruPair(LivingEntity a, LivingEntity b) {
        return type(a) == CharacterType.KAERU || type(b) == CharacterType.KAERU;
    }

    public static String name(LivingEntity e) {
        return e instanceof PartyBotEntity b ? b.character().getDisplayName().getString() : e.getName().getString();
    }

    private static int level(LivingEntity e) {
        if (e instanceof PartyBotEntity b) return b.botLevel();
        return e instanceof ServerPlayer p ? com.xenowarriors.stats.Stats.characterLevel(p) : 0;
    }

    /** The team ultimates this pair can do (Blazing Judgment once Rhalix knows Judgment Absorption). */
    public static List<Kind> kinds(LivingEntity a, LivingEntity b) {
        if (kaeruPair(a, b)) return List.of(Kind.HEKUBLADE_BARRAGE);
        LivingEntity rhalix = type(a) == CharacterType.RHALIX ? a : b;
        boolean judgment = rhalix instanceof ServerPlayer p && p.isCreative() || level(rhalix) >= JudgmentAbsorptionSkill.UNLOCK_LEVEL;
        return judgment ? List.of(Kind.GRAVITY_FOX_FIRE, Kind.BLAZING_JUDGMENT) : List.of(Kind.GRAVITY_FOX_FIRE);
    }

    private static boolean near(LivingEntity a, LivingEntity b) {
        return a != b && b.isAlive() && b.level() == a.level() && b.distanceTo(a) <= RANGE && !(b instanceof PartyBotEntity pb && pb.downed());
    }

    /** Partners for a team ultimate near {@code e} on its side (players in the party and their bots), nearest first. */
    public static List<LivingEntity> partners(LivingEntity e) {
        java.util.Set<CharacterType> w = want(type(e));
        ServerPlayer anchor = e instanceof PartyBotEntity b ? b.owner() : e instanceof ServerPlayer p && !(p instanceof FakePlayer) ? p : null;
        if (w.isEmpty() || anchor == null) return List.of();
        List<LivingEntity> out = new ArrayList<>();
        for (ServerPlayer m : Parties.members(anchor)) {
            if (w.contains(type(m)) && near(e, m) && !m.isSpectator()) out.add(m);
            for (PartyBotEntity b : PartyBots.bots(m)) if (w.contains(b.character()) && near(e, b) && !b.sparring()) out.add(b);
        }
        out.sort(Comparator.comparingDouble(o -> o.distanceToSqr(e)));
        return out;
    }

    /** In a team ultimate (or another ultimate cutscene) right now. */
    public static boolean busy(LivingEntity e) {
        return e.getPersistentData().getLong(SkillEvents.ULT_UNTIL) >= e.level().getGameTime();
    }

    /** Ready for a team ultimate: free to act, ultimate off cooldown, {@link #KARMA} Karma. */
    public static boolean ready(LivingEntity e, boolean tell) {
        if (!e.isAlive() || busy(e)) return false;
        if (e instanceof PartyBotEntity b) return BotTeamUlt.ultReady(b, KARMA);
        if (!(e instanceof ServerPlayer p) || p.isSpectator() || Duels.cantAct(p)) return false;   // (down: the asker can be knocked out while waiting, and /xeno teamult join skips the skill key's checks)
        long now = p.server.overworld().getGameTime();
        boolean[] ok = {true};
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            if (d.cooldownEnd[SkillManager.ULTIMATE_SLOT] > now) ok[0] = false;
        });
        if (!ok[0]) {
            if (tell) p.displayClientMessage(Component.literal("Your ultimate isn't ready").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!p.isCreative() && !Karma.has(p, KARMA)) {
            if (tell) Karma.notEnough(p, KARMA);
            return false;
        }
        return true;
    }

    /** An enemy a team ultimate goes for: hostile (or after their side), not anyone's ally, not a player or a bot. */
    public static boolean foe(LivingEntity r, LivingEntity m, LivingEntity e) {
        if (!e.isAlive() || e == r || e == m || e instanceof Player || e instanceof PartyBotEntity || Parties.petOwner(e) != null) return false;
        if (com.xenowarriors.combat.Allies.isAlly(r, e) || com.xenowarriors.combat.Allies.isAlly(m, e)) return false;
        return e instanceof Enemy || com.xenowarriors.karma.KarmaSensing.corrupted(e)
                || e instanceof Mob mob && mob.getTarget() != null && com.xenowarriors.combat.Allies.isAlly(r, mob.getTarget());
    }

    /** Foes within {@code radius} of {@code e} (for "is there a fight on"). */
    public static List<LivingEntity> foesNear(LivingEntity e, double radius) {
        return e.level().getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(radius), x -> foe(e, e, x));
    }

    // ------------------------------------------------------------------ asking

    /** Ultimate key pressed: answers a pending team-up, or (sneaking) asks the partner. False = do the normal ultimate. */
    public static boolean onUltimateKey(ServerPlayer p) {
        Request r = PENDING.get(p.getUUID());
        if (r != null && p.level().getGameTime() <= r.expires) {
            join(p);
            return true;
        }
        if (!p.isShiftKeyDown()) return false;
        // asked already and still waiting: switch to the other team ultimate
        for (Request mine : PENDING.values()) {
            if (!mine.from.equals(p.getUUID()) || p.level().getGameTime() > mine.expires) continue;
            ServerPlayer other = p.server.getPlayerList().getPlayer(mine.to);
            if (other == null) break;
            List<Kind> ks = kinds(p, other);
            if (ks.size() < 2) { p.displayClientMessage(Component.literal("Still waiting for " + other.getName().getString() + "...").withStyle(ChatFormatting.GRAY), true); return true; }
            Kind next = ks.get((ks.indexOf(mine.kind) + 1) % ks.size());
            remember(p, next);
            ask(p, other, next);
            return true;
        }
        List<LivingEntity> ps = partners(p);
        if (ps.isEmpty()) return false;
        LivingEntity other = ps.get(0);
        for (LivingEntity o : ps) if (ready(o, false)) { other = o; break; }   // (a ready one first)
        if (!ready(p, true)) return true;
        Kind k = preferred(p, kinds(p, other));
        if (!ready(other, false)) {
            p.displayClientMessage(Component.literal(name(other) + " isn't ready for " + k.title + " yet").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (other instanceof PartyBotEntity b) {                               // a bot teammate jumps at it
            p.sendSystemMessage(Component.literal("").append(b.label()).append(Component.literal(": Let's do it - " + k.title + "!").withStyle(ChatFormatting.GOLD)));
            startNow(p, b, k);
            return true;
        }
        if (other instanceof ServerPlayer op) ask(p, op, k);
        return true;
    }

    /** The team ultimate this player last picked, if the pair can do it (else the newest one they can). */
    private static Kind preferred(ServerPlayer p, List<Kind> ks) {
        String s = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString(PREF);
        for (Kind k : ks) if (k.name().equals(s)) return k;
        return ks.get(ks.size() - 1);
    }

    private static void remember(ServerPlayer p, Kind k) {
        var pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        pers.putString(PREF, k.name());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
    }

    /** {@code from} (a player or a bot) asks the player {@code to} for a team ultimate. */
    public static void ask(LivingEntity from, ServerPlayer to, Kind k) {
        PENDING.put(to.getUUID(), new Request(from.getUUID(), to.getUUID(), to.level().getGameTime() + PROMPT_TICKS, k));
        boolean bot = from instanceof PartyBotEntity;
        String who = name(from);
        if (from instanceof ServerPlayer fp) {
            boolean two = kinds(from, to).size() > 1;
            fp.displayClientMessage(Component.literal("Asked " + to.getName().getString() + " for " + k.title + "..." + (two ? " (sneak + ultimate again to switch)" : "")).withStyle(ChatFormatting.GOLD), true);
        }
        Component line = bot ? Component.literal("").append(((PartyBotEntity) from).label()).append(Component.literal(": Ready for " + k.title + "! Press your ultimate key or ").withStyle(ChatFormatting.GOLD))
                : Component.literal(who + " wants to do " + k.title + "! Press your ultimate key or ").withStyle(ChatFormatting.GOLD);
        to.sendSystemMessage(Component.empty().append(line).append(Parties.button("[Join]", "/xeno teamult join", ChatFormatting.GREEN)));
        to.level().playSound(null, to.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1F, 1.4F);
        com.xenowarriors.network.PopupPacket.send(to, com.xenowarriors.network.PopupPacket.REQUEST, from.getUUID(), type(from).ordinal(), -1, "Team attack!",
                bot ? who + ": Ready for " + k.title + "! Ultimate key to join." : who + " wants " + k.title + "! Ultimate key to join.", "shout");
        ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Team Ultimate",
                bot ? who + ": Ready for " + k.title + "!" : who + " wants to do " + k.title + "!", "/xeno teamult join", "", PROMPT_TICKS, 0xFFFFB040), to);
        XenoWarriors.LOGGER.info("XW team ult request: {} -> {} ({})", who, to.getGameProfile().getName(), k.title);
    }

    /** A request waiting for this player: {asker's UUID, kind}, or null. */
    public static Object[] pending(ServerPlayer p) {
        Request r = PENDING.get(p.getUUID());
        return r == null || p.level().getGameTime() > r.expires ? null : new Object[]{r.from, r.kind};
    }

    public static void join(ServerPlayer p) {
        Request r = PENDING.remove(p.getUUID());
        if (r != null) com.xenowarriors.network.RequestPopupPacket.dismiss(p, "/xeno teamult join");   // (answered with the ultimate key: the card goes)
        if (r == null || p.level().getGameTime() > r.expires) {
            p.displayClientMessage(Component.literal("No team ultimate to join right now").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        LivingEntity asker = find(p.server, r.from);
        if (asker == null || !asker.isAlive() || asker.level() != p.level() || asker.distanceTo(p) > RANGE
                || !com.xenowarriors.combat.Allies.isAlly(asker, p) || !kinds(asker, p).contains(r.kind) || !ready(asker, false) || !ready(p, true)) {
            p.displayClientMessage(Component.literal("Too late - you're not together for it any more").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (asker instanceof ServerPlayer ap && !Parties.sameParty(ap, p)) return;
        startNow(asker, p, r.kind);
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp != null) return sp;
        for (ServerLevel l : server.getAllLevels()) if (l.getEntity(id) instanceof LivingEntity e) return e;
        return null;
    }

    // ------------------------------------------------------------------ the move

    /** Both pay (Karma and their ultimate's cooldown) and it starts. The two must be a Rhalix and a Mikiko. */
    public static boolean startNow(LivingEntity a, LivingEntity b, Kind k) {
        CharacterType other = k == Kind.HEKUBLADE_BARRAGE ? CharacterType.KAERU : CharacterType.RHALIX;
        LivingEntity rhalix = type(a) == other ? a : b;                         // (Rhalix - or Kaeru for the barrage)
        LivingEntity mikiko = rhalix == a ? b : a;
        if (type(rhalix) != other || type(mikiko) != CharacterType.MIKIKO) return false;
        for (LivingEntity s : new LivingEntity[]{rhalix, mikiko}) {
            var skill = SkillManager.get(type(s), SkillManager.ULTIMATE_SLOT);
            int cd = skill == null ? 60 * 20 : skill.getCooldownTicks();
            if (s instanceof PartyBotEntity bot) { BotTeamUlt.spend(bot, KARMA, cd); continue; }
            ServerPlayer sp = (ServerPlayer) s;
            if (!sp.isCreative()) Karma.add(sp, -KARMA);
            com.xenowarriors.journey.Journey.onSkill(sp);
            long now = sp.server.overworld().getGameTime();
            sp.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.cooldownEnd[SkillManager.ULTIMATE_SLOT] = now + cd);
            ModNetwork.sendToPlayer(new SkillCooldownPacket(SkillManager.ULTIMATE_SLOT, cd, cd), sp);
        }
        start(k, rhalix, mikiko);
        return true;
    }

    private static void start(Kind k, LivingEntity rhalix, LivingEntity mikiko) {
        if (k == Kind.HEKUBLADE_BARRAGE) {                                     // Mikiko + Kaeru: the blades fly from where they stand
            HekubladeBarrage.start(mikiko, rhalix);
            for (LivingEntity s : new LivingEntity[]{rhalix, mikiko})
                if (s instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal(k.title.toUpperCase(Locale.ROOT) + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                else if (s instanceof PartyBotEntity pb && pb.owner() != null && pb.owner() != rhalix && pb.owner() != mikiko)
                    pb.owner().displayClientMessage(Component.literal(name(mikiko) + " & " + name(rhalix) + ": " + k.title + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            XenoWarriors.LOGGER.info("XW team ult start: {} by {} + {}", k.title, name(mikiko), name(rhalix));
            return;
        }
        Vec3 at = target(rhalix, mikiko);
        // stand it on the ground under the target point
        net.minecraft.core.BlockPos.MutableBlockPos bp = net.minecraft.core.BlockPos.containing(at).mutable();
        for (int i = 0; i < 12 && rhalix.level().getBlockState(bp.below()).getCollisionShape(rhalix.level(), bp.below()).isEmpty(); i++) bp.move(0, -1, 0);
        at = new Vec3(at.x, bp.getY(), at.z);
        rhalix.level().addFreshEntity(TeamUltEntity.create(k.id, rhalix, mikiko, at));
        for (LivingEntity s : new LivingEntity[]{rhalix, mikiko})
            if (s instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal(k.title.toUpperCase(Locale.ROOT) + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        for (LivingEntity s : new LivingEntity[]{rhalix, mikiko}) {
            ServerPlayer owner = s instanceof PartyBotEntity b ? b.owner() : null;
            if (owner != null && owner != rhalix && owner != mikiko) owner.displayClientMessage(Component.literal(name(rhalix) + " & " + name(mikiko) + ": " + k.title + "!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        }
        XenoWarriors.LOGGER.info("XW team ult start: {} by {} ({}) + {} ({}) at {}", k.title, name(rhalix), rhalix.getClass().getSimpleName(), name(mikiko), mikiko.getClass().getSimpleName(), at);
    }

    /** The biggest group of enemies near them, or the one a bot is fighting, or where Rhalix is looking. */
    private static Vec3 target(LivingEntity rhalix, LivingEntity mikiko) {
        List<LivingEntity> foes = rhalix.level().getEntitiesOfClass(LivingEntity.class, new AABB(rhalix.blockPosition()).inflate(18), e -> foe(rhalix, mikiko, e));
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
        for (LivingEntity s : new LivingEntity[]{rhalix, mikiko})
            if (s instanceof Mob m && m.getTarget() != null && m.getTarget().distanceTo(s) < 20) return m.getTarget().position();
        Vec3 eye = rhalix.getEyePosition();
        Vec3 end = eye.add(rhalix.getViewVector(1F).scale(16));
        var hit = rhalix.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rhalix));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** (tests) forget every waiting request. */
    public static void clearForTest() { PENDING.clear(); }
}

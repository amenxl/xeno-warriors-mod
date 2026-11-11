package com.xenowarriors.raid;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.XwConfig;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.RepRules;
import com.xenowarriors.faction.Reputation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Faction raids: when the Government, the Sky Rebellion or the Townsfolk hate you (Hostile or Hated standing), now and
 * then they come after you, wherever you are in the Overworld or Recarnet. A warning first (title, siren, a boss bar),
 * then waves - more and bigger the worse your standing and the more people you have with you:
 * <ul>
 * <li>Government: helicopters packed with soldiers fly in and drop them on you, armored trucks and cars full of soldiers
 * drive in; everyone shoots from their seats (drivers too), then they pile out and fight. A Commander leads the last wave.</li>
 * <li>Sky Rebellion: demon-vessel rebels dive out of the sky on their wings; a named member leads the last wave.</li>
 * <li>Townsfolk: an angry militia marches out from the nearest town - pitchforks, axes, torches - led by a captain.</li>
 * </ul>
 * Beat every wave and they back off (a little standing back, some loot, and a long break before they try again). Die,
 * run far enough or leave the world and they give up. Raiders never break blocks, and everything a raid brought
 * (soldiers, rebels, militia, vehicles) is cleared when it ends - or when its chunk loads after the raid is over.
 * The Sky Patrol & Skylanders never raid. Toggles: config [raids], gamerule xwFactionRaids; /xeno raid gov|sr|town|stop.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FactionRaids {
    /** Persistent-data key on every raider and raid vehicle: the id of the raid it belongs to. */
    public static final String TAG = "xw_raid";
    public static final GameRules.Key<GameRules.BooleanValue> RULE = GameRules.register("xwFactionRaids", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));

    /** How often each player's standing is rolled (ticks): every 2 in-game minutes. */
    public static final int CHECK_EVERY = 2400;
    /** Warning before the first wave, and the pause between waves (ticks). */
    public static final int WARN_TICKS = 300, BETWEEN_WAVES = 160;
    /** Who sees the bar / counts as fighting it; how far the target can get from where it started before it's called off. */
    static final double AREA = 96, FLED = 160;
    /** After a raid: that faction won't raid you again for 3 days if you beat it (1.5 if you didn't); no raid of any kind for a day. */
    public static final long COOLDOWN_WIN = 3 * 24000L, COOLDOWN_LOSS = 36000L, COOLDOWN_ANY = 24000L;
    /** A raid that drags on this long is called off (15 minutes). */
    static final int MAX_AGE = 20 * 60 * 15;
    /** Standing won back for beating a raid. */
    public static final int WIN_REP = 40;

    /** (UI test) short warning and pauses. */
    public static boolean testFast;

    static final List<Raid> RAIDS = new ArrayList<>();
    static final Map<UUID, Raid> BY_ID = new HashMap<>();

    private FactionRaids() {}

    /** Who raids. (The Sky Patrol & Skylanders don't.) */
    public enum Kind {
        GOV(Faction.GOV, "Government Raid", BossEvent.BossBarColor.BLUE, ChatFormatting.AQUA),
        SR(Faction.SR, "Sky Rebellion Raid", BossEvent.BossBarColor.RED, ChatFormatting.RED),
        TOWN(Faction.TOWN, "Townsfolk Raid", BossEvent.BossBarColor.GREEN, ChatFormatting.GREEN);
        public final Faction faction;
        public final String title;
        final BossEvent.BossBarColor color;
        final ChatFormatting style;
        Kind(Faction f, String title, BossEvent.BossBarColor color, ChatFormatting style) { this.faction = f; this.title = title; this.color = color; this.style = style; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public static Kind of(Faction f) { for (Kind k : values()) if (k.faction == f) return k; return null; }
    }

    /** One raid on one player. */
    public static final class Raid {
        public final UUID id = UUID.randomUUID();
        public final Kind kind;
        public final ServerLevel level;
        public final UUID target;
        public final BlockPos center;
        /** 0 = Hostile, 1 = Hated. */
        public final int severity;
        public final int waves;
        final ServerBossEvent bar;
        public final Set<UUID> raiders = new LinkedHashSet<>(), vehicles = new LinkedHashSet<>();
        final Set<UUID> fought = new HashSet<>();
        final Map<UUID, Integer> unloaded = new HashMap<>();
        public int wave, waveSize, age, nextWaveAt, failedSpawns;
        public String ended;
        public boolean won;

        Raid(Kind kind, ServerLevel level, ServerPlayer target, int severity) {
            this.kind = kind; this.level = level; this.severity = severity;
            this.target = target == null ? new UUID(0, 0) : target.getUUID();             // (no target: the server self check)
            this.center = target == null ? level.getSharedSpawnPos() : target.blockPosition();
            this.waves = 3 + severity;
            this.bar = new ServerBossEvent(Component.literal(kind.title).withStyle(kind.style), kind.color, BossEvent.BossBarOverlay.NOTCHED_10);
            this.nextWaveAt = testFast ? 30 : WARN_TICKS;
        }

        public ServerPlayer targetPlayer() { return level.getServer().getPlayerList().getPlayer(target); }

        /** The raiders still in the world (loaded and alive). */
        public List<LivingEntity> alive() {
            List<LivingEntity> out = new ArrayList<>();
            for (UUID u : raiders) if (level.getEntity(u) instanceof LivingEntity e && e.isAlive()) out.add(e);
            return out;
        }

        public List<com.xenowarriors.vehicle.VehicleEntity> vehicleList() {
            List<com.xenowarriors.vehicle.VehicleEntity> out = new ArrayList<>();
            for (UUID u : vehicles) if (level.getEntity(u) instanceof com.xenowarriors.vehicle.VehicleEntity v && v.isAlive()) out.add(v);
            return out;
        }

        public ServerBossEvent bar() { return bar; }

        /** How many people are with the target (other players near, and bots count half): bigger raids. */
        int extra(ServerPlayer t) {
            int players = 0, bots = 0;
            for (ServerPlayer q : level.players()) if (q != t && !q.isSpectator() && q.distanceToSqr(t) < AREA * AREA) players++;
            for (ServerPlayer q : level.players()) if (q.distanceToSqr(t) < AREA * AREA) bots += com.xenowarriors.bot.PartyBots.bots(q).size();
            return players + bots / 2;
        }
    }

    // ------------------------------------------------------------------ settings

    public static boolean enabled(MinecraftServer server, Kind k) {
        if (!XwConfig.get(XwConfig.FACTION_RAIDS)) return false;
        if (server != null && !server.getGameRules().getBoolean(RULE)) return false;
        return switch (k) {
            case GOV -> XwConfig.get(XwConfig.GOV_RAIDS);
            case SR -> XwConfig.get(XwConfig.SR_RAIDS);
            case TOWN -> XwConfig.get(XwConfig.TOWN_RAIDS);
        };
    }

    /** The chance (0..1) per check that a faction you're on this standing with raids you: 0 unless Hostile, 5% at -151 up to 18% at -1000. */
    public static double chance(int rep) {
        if (Reputation.Tier.of(rep).atLeast(Reputation.Tier.UNFRIENDLY)) return 0;
        return 0.05 + 0.13 * Math.min(1.0, (Reputation.Tier.HOSTILE.below - 1 - rep) / 850.0);
    }

    /** Where raids can happen: the Overworld and Recarnet - never a Journey, the Tower, VR, a Room or another mod's dimension. */
    public static boolean placeOk(ServerPlayer p) {
        Level l = p.level();
        if (l.dimension() != Level.OVERWORLD && !com.xenowarriors.recarnet.Recarnet.is(l)) return false;
        return !com.xenowarriors.modes.ModeRules.inMode(p) && !com.xenowarriors.journey.Journey.inLobbyOrRun(p) && !com.xenowarriors.vr.Vr.inVr(p);
    }

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        return root;
    }

    private static long cooldownLeft(ServerPlayer p, Kind k) {
        CompoundTag d = data(p);
        long now = p.level().getGameTime();
        return Math.max(Math.max(0, d.getLong("xw_raid_cd_" + k.key()) - now), Math.max(0, d.getLong("xw_raid_cd_any") - now));
    }

    /** (Tests / the stop command) forget the cooldowns. */
    public static void clearCooldowns(ServerPlayer p) {
        CompoundTag d = data(p);
        for (Kind k : Kind.values()) d.remove("xw_raid_cd_" + k.key());
        d.remove("xw_raid_cd_any");
        d.remove("xw_raid_next");
    }

    /** Could this faction raid this player right now (before the dice)? */
    public static boolean eligible(ServerPlayer p, Kind k) {
        if (!enabled(p.server, k) || p.level().getDifficulty() == Difficulty.PEACEFUL) return false;
        if (!p.isAlive() || p.isCreative() || p.isSpectator() || !placeOk(p)) return false;
        if (!Reputation.hostile(p, k.faction)) return false;
        // people never go after Mikiko (only a soul wearing them would): no Townsfolk raids on him
        if (k == Kind.TOWN && com.xenowarriors.character.ModCapabilities.getType(p) == com.xenowarriors.character.CharacterType.MIKIKO) return false;
        if (raidOn(p) != null || raidNear(p) != null) return false;
        return cooldownLeft(p, k) <= 0;
    }

    /**
     * One roll for a player (every {@link #CHECK_EVERY} ticks; tests pass a fixed roll): the worst-hated eligible faction
     * whose chance beats the roll starts a raid. Returns the raid, or null.
     */
    public static Raid check(ServerPlayer p, double roll) {
        Kind best = null;
        double bestChance = 0;
        for (Kind k : Kind.values()) {
            if (!eligible(p, k)) continue;
            double c = chance(Reputation.get(p, k.faction));
            if (c > bestChance) { bestChance = c; best = k; }
        }
        if (best == null || roll >= bestChance) return null;
        return start(p, best, false);
    }

    public static Raid raidOn(Player p) {
        for (Raid r : RAIDS) if (r.target.equals(p.getUUID()) && r.ended == null) return r;
        return null;
    }

    /** A raid going on near this player (on anyone). */
    public static Raid raidNear(Player p) {
        for (Raid r : RAIDS) {
            if (r.ended != null || r.level != p.level()) continue;
            ServerPlayer t = r.targetPlayer();
            if (t != null && t.distanceToSqr(p) < AREA * AREA) return r;
        }
        return null;
    }

    /** This player is being raided by that faction right now (their soldiers fighting back doesn't make you "wanted"). */
    public static boolean underRaid(Player p, Faction f) {
        for (Raid r : RAIDS) if (r.ended == null && r.kind.faction == f && (r.target.equals(p.getUUID()) || r.fought.contains(p.getUUID()))) return true;
        return false;
    }

    public static Raid byId(UUID id) { Raid r = BY_ID.get(id); return r == null || r.ended != null ? null : r; }

    /** The raid this entity belongs to (null if none, or that raid is over). */
    public static Raid of(Entity e) {
        CompoundTag t = e.getPersistentData();
        return t.hasUUID(TAG) ? byId(t.getUUID(TAG)) : null;
    }

    public static boolean isRaider(Entity e) { return e != null && e.getPersistentData().hasUUID(TAG); }

    /** Both from the same raid (no friendly fire between them). */
    public static boolean sameRaid(Entity a, Entity b) {
        if (a == null || b == null || !isRaider(a) || !isRaider(b)) return false;
        return a.getPersistentData().getUUID(TAG).equals(b.getPersistentData().getUUID(TAG));
    }

    public static List<Raid> active() { return Collections.unmodifiableList(RAIDS); }

    // ------------------------------------------------------------------ starting

    /** Starts a raid by that faction on this player (force: ignore standing, chance and cooldowns - the command). */
    public static Raid start(ServerPlayer p, Kind k, boolean force) {
        if (raidOn(p) != null || p.level().getDifficulty() == Difficulty.PEACEFUL || !placeOk(p)) return null;
        if (!force && !eligible(p, k)) return null;
        int rep = Reputation.get(p, k.faction);
        Raid r = new Raid(k, p.serverLevel(), p, Reputation.Tier.of(rep) == Reputation.Tier.HATED ? 1 : 0);
        RAIDS.add(r);
        BY_ID.put(r.id, r);
        long now = p.level().getGameTime();
        data(p).putLong("xw_raid_cd_any", now + COOLDOWN_ANY);
        data(p).putLong("xw_raid_cd_" + k.key(), now + COOLDOWN_LOSS);   // (raised to the win cooldown if they beat it)
        warn(r, p);
        XenoWarriors.LOGGER.info("[raids] {} on {} (standing {}, {} waves, bar {})", k.title, p.getName().getString(), rep, r.waves, r.bar.getId());
        return r;
    }

    private static final String[][] WARN = {
            {"GOVERNMENT RAID", "Helicopters and armored convoys are coming for you!", "The Government has sent soldiers after you - helicopters and armored convoys are on their way!"},
            {"SKY REBELLION RAID", "The rebels are diving out of the sky!", "The Sky Rebellion is coming for you - rebels are diving out of the sky!"},
            {"TOWNSFOLK RAID", "An angry mob is marching on you!", "The Townsfolk have had enough of you - an angry mob is marching out of town!"}};

    private static void warn(Raid r, ServerPlayer t) {
        String[] w = WARN[r.kind.ordinal()];
        for (ServerPlayer q : r.level.players()) {
            if (q.distanceToSqr(t) > AREA * AREA) continue;
            boolean target = q == t;
            r.bar.addPlayer(q);                                                 // (the raid bar shows from the first moment)
            r.fought.add(q.getUUID());
            q.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            q.connection.send(new ClientboundSetTitleTextPacket(Component.literal(w[0]).withStyle(r.kind.style, ChatFormatting.BOLD)));
            q.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(target ? w[1] : t.getName().getString() + " is being raided - help them!").withStyle(ChatFormatting.GRAY)));
            q.sendSystemMessage(Component.literal(target ? w[2] : w[2].replace("you", t.getName().getString())).withStyle(r.kind.style, ChatFormatting.BOLD));
            com.xenowarriors.network.PopupPacket.send(q, com.xenowarriors.network.PopupPacket.DANGER, q.getUUID(), com.xenowarriors.character.CharacterType.NONE.ordinal(), -1,
                    r.kind.title + "!", target ? w[2] + " Survive every wave." : w[2], "");
        }
        r.bar.setName(Component.literal(r.kind.title + " - incoming!").withStyle(r.kind.style, ChatFormatting.BOLD));
        r.bar.setProgress(0F);
        r.level.playSound(null, t.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64F, r.kind == Kind.SR ? 0.7F : 1F);
        if (r.kind == Kind.TOWN) r.level.playSound(null, t.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 4F, 1F);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                long now = p.level().getGameTime();
                CompoundTag d = data(p);
                long next = d.getLong("xw_raid_next");
                if (next == 0 || next > now + CHECK_EVERY) { d.putLong("xw_raid_next", now + CHECK_EVERY); continue; }   // (first sight of them, or the clock went back)
                if (now < next) continue;
                d.putLong("xw_raid_next", now + CHECK_EVERY);
                check(p, p.getRandom().nextDouble());
            }
        }
        for (Raid r : new ArrayList<>(RAIDS)) {
            try { tick(r); }
            catch (Throwable t) { XenoWarriors.LOGGER.error("[raids] tick failed - calling it off", t); end(r, false, "error"); }
        }
        RAIDS.removeIf(r -> r.ended != null);
    }

    private static void tick(Raid r) {
        r.age++;
        ServerPlayer t = r.targetPlayer();
        if (t == null || t.level() != r.level) { end(r, false, "left"); return; }
        if (!t.isAlive()) { end(r, false, "died"); return; }
        if (r.level.getDifficulty() == Difficulty.PEACEFUL) { end(r, false, "peaceful"); return; }
        if (!t.blockPosition().closerThan(r.center, FLED)) { end(r, false, "fled"); return; }
        if (r.age > MAX_AGE) { end(r, false, "timeout"); return; }
        // the bar: everyone near the target
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer q : r.level.players()) if (!q.isSpectator() && q.distanceToSqr(t) < AREA * AREA) near.add(q);
        for (ServerPlayer q : new ArrayList<>(r.bar.getPlayers())) if (!near.contains(q)) r.bar.removePlayer(q);
        for (ServerPlayer q : near) { r.bar.addPlayer(q); r.fought.add(q.getUUID()); }

        if (r.wave == 0) {                                                  // the warning
            int warn = testFast ? 30 : WARN_TICKS;
            r.bar.setName(Component.literal(r.kind.title + " - incoming!").withStyle(r.kind.style, ChatFormatting.BOLD));
            r.bar.setProgress(Math.min(1F, r.age / (float) warn));
            if (r.age % 20 == 0) siren(r, t, r.age / 20 % 2 == 0);
            if (r.age >= r.nextWaveAt) spawnWave(r, t);
            return;
        }
        if (r.age % 10 != 0) return;
        List<LivingEntity> alive = upkeep(r, t);
        int pending = 0;
        for (UUID u : r.raiders) if (!(r.level.getEntity(u) instanceof LivingEntity)) pending++;
        int left = alive.size() + pending;
        if (r.waveSize > 0) r.bar.setProgress(Math.max(0F, Math.min(1F, left / (float) r.waveSize)));
        r.bar.setName(Component.literal(r.kind.title + " - Wave " + r.wave + "/" + r.waves + " - " + left + " left").withStyle(r.kind.style));
        if (left > 0) return;
        if (r.nextWaveAt < 0) {
            if (r.wave >= r.waves) { end(r, true, "won"); return; }
            r.nextWaveAt = r.age + (testFast ? 30 : BETWEEN_WAVES);
            for (ServerPlayer q : near) q.displayClientMessage(Component.literal("Wave " + r.wave + " beaten - more are coming!").withStyle(ChatFormatting.GOLD), true);
            r.bar.setName(Component.literal(r.kind.title + " - Wave " + r.wave + "/" + r.waves + " beaten").withStyle(r.kind.style));
        }
        if (r.age >= r.nextWaveAt) spawnWave(r, t);
    }

    private static void siren(Raid r, ServerPlayer t, boolean low) {
        switch (r.kind) {
            case GOV -> r.level.playSound(null, t.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 2F, low ? 0.7F : 1.0F);   // a two-tone siren
            case SR -> { if (low) r.level.playSound(null, t.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 2F, 0.6F); }
            case TOWN -> { if (low) r.level.playSound(null, t.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3F, 0.9F); }
        }
    }

    /** Keeps the raiders on the job: targets, the bots helping, ones lost in unloaded chunks. Returns the ones alive. */
    private static List<LivingEntity> upkeep(Raid r, ServerPlayer t) {
        List<LivingEntity> alive = new ArrayList<>();
        for (Iterator<UUID> it = r.raiders.iterator(); it.hasNext(); ) {
            UUID u = it.next();
            Entity e = r.level.getEntity(u);
            if (e instanceof LivingEntity le && le.isAlive()) { alive.add(le); r.unloaded.remove(u); continue; }
            if (e != null) { it.remove(); continue; }                   // dead / gone
            int n = r.unloaded.merge(u, 10, Integer::sum);
            if (n > 600) { it.remove(); r.unloaded.remove(u); }         // lost in an unloaded chunk for 30 s: it's out (and is cleared when it loads)
        }
        for (Iterator<UUID> it = r.vehicles.iterator(); it.hasNext(); ) {
            Entity e = r.level.getEntity(it.next());
            if (e != null && !e.isAlive()) it.remove();
        }
        for (LivingEntity e : alive) {
            if (!(e instanceof Mob m)) continue;
            LivingEntity cur = m.getTarget();
            boolean bad = cur == null || !cur.isAlive() || (cur instanceof Player pl && (pl.isCreative() || pl.isSpectator())) || cur.distanceToSqr(m) > 64 * 64;
            if (bad) {
                LivingEntity pick = t.distanceToSqr(m) < 72 * 72 ? t : null;
                if (pick == null) for (ServerPlayer q : r.level.players()) if (r.fought.contains(q.getUUID()) && !q.isSpectator() && !q.isCreative() && q.distanceToSqr(m) < 32 * 32) { pick = q; break; }
                if (pick != null) m.setTarget(pick);
                else if (m.getVehicle() == null && m.getNavigation().isDone()) m.getNavigation().moveTo(t, 1.1);
            }
        }
        // bots fight the raiders
        if (r.age % 20 == 0 && !alive.isEmpty()) {
            for (UUID u : r.fought) {
                if (!(r.level.getPlayerByUUID(u) instanceof ServerPlayer q)) continue;
                for (var b : com.xenowarriors.bot.PartyBots.bots(q)) {
                    if (b.getTarget() != null && b.getTarget().isAlive()) continue;
                    LivingEntity best = null;
                    double bd = 28 * 28;
                    for (LivingEntity e : alive) { double d = e.distanceToSqr(b); if (d < bd) { bd = d; best = e; } }
                    if (best != null) b.journeyTarget(best, r.level.getGameTime() + 120);
                }
            }
        }
        return alive;
    }

    private static void spawnWave(Raid r, ServerPlayer t) {
        r.wave++;
        r.nextWaveAt = -1;
        int extra = r.extra(t);
        int before = r.raiders.size();
        switch (r.kind) {
            case GOV -> RaidSpawns.gov(r, t, extra);
            case SR -> RaidSpawns.sr(r, t, extra);
            case TOWN -> RaidSpawns.town(r, t, extra);
        }
        if (r.raiders.size() == before) {                                  // nowhere to come in from (all water / unloaded): try again shortly
            r.wave--;
            r.nextWaveAt = r.age + 100;
            if (++r.failedSpawns > 6) end(r, false, "nowhere");
            return;
        }
        r.waveSize = r.raiders.size() - before;
        r.bar.setProgress(1F);
        boolean last = r.wave == r.waves;
        String msg = switch (r.kind) {
            case GOV -> last ? "The Government Commander is leading the last wave in!" : "Government helicopters and convoys incoming!";
            case SR -> last ? "A Sky Rebellion lieutenant dives in with the last wave!" : "Rebels are diving out of the sky!";
            case TOWN -> last ? "The militia captain leads the last of the mob!" : "The angry mob is coming!";
        };
        for (ServerPlayer q : r.level.players()) if (q.distanceToSqr(t) < AREA * AREA)
            q.displayClientMessage(Component.literal(msg).withStyle(r.kind.style), true);
        r.level.playSound(null, t.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 48F, last ? 0.8F : 1F);
        XenoWarriors.LOGGER.info("[raids] {} wave {}/{}: {} raiders, {} vehicles", r.kind.title, r.wave, r.waves, r.waveSize, r.vehicles.size());
    }

    // ------------------------------------------------------------------ ending

    /** Ends a raid: won (every wave beaten) or not (died, fled, left, called off). Everything it brought goes. */
    public static void end(Raid r, boolean won, String why) {
        if (r.ended != null) return;
        r.ended = why;
        r.won = won;
        r.bar.removeAllPlayers();
        BY_ID.remove(r.id);
        ServerPlayer t = r.targetPlayer();
        // clear out what it brought (raiders in unloaded chunks go when their chunk loads: see onJoin)
        for (UUID u : r.vehicles) {
            Entity e = r.level.getEntity(u);
            if (e == null) continue;
            e.ejectPassengers();
            puff(r.level, e);
            e.discard();
        }
        for (UUID u : r.raiders) {
            Entity e = r.level.getEntity(u);
            if (e == null) continue;
            if (e.isAlive()) puff(r.level, e);
            e.discard();
        }
        long now = r.level.getGameTime();
        if (t != null) data(t).putLong("xw_raid_cd_" + r.kind.key(), now + (won ? COOLDOWN_WIN : COOLDOWN_LOSS));
        XenoWarriors.LOGGER.info("[raids] {} ended: {} (wave {}/{}, bar {} players left {})", r.kind.title, why, r.wave, r.waves, r.bar.getId(), r.bar.getPlayers().size());
        if (!won) {
            String line = switch (why) {
                case "died" -> "The raiders pull back - they got what they came for.";
                case "fled" -> "You got away - the raiders give up the chase.";
                case "peaceful" -> "The raid is called off.";
                default -> "The raiders pull back.";
            };
            for (ServerPlayer q : r.level.players()) if (r.fought.contains(q.getUUID()) && (q == t || t == null || q.distanceToSqr(t) < AREA * AREA))
                q.sendSystemMessage(Component.literal(r.kind.title + " over: " + line).withStyle(ChatFormatting.GRAY));
            return;
        }
        // won: they back off a little, and everyone who fought gets something
        if (t != null) {
            RepRules rule = switch (r.kind) { case GOV -> RepRules.GOV_RAID; case SR -> RepRules.SR_RAID; case TOWN -> RepRules.TOWN_RAID_HELD; };
            rule.apply(t);
            if (r.kind == Kind.GOV) com.xenowarriors.economy.Money.add(t, 150L + 50L * r.wave, true);
        }
        for (UUID u : r.fought) {
            if (!(r.level.getPlayerByUUID(u) instanceof ServerPlayer q) || (t != null && q.distanceToSqr(t) > AREA * AREA)) continue;
            q.sendSystemMessage(Component.literal("You fought off the " + r.kind.title + "! " + (q == t ? "The " + r.kind.faction.title + " back off - for now." : ""))
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            r.level.playSound(null, q.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1F, 1F);
            for (ItemStack s : loot(r)) {
                ItemEntity it = new ItemEntity(r.level, q.getX(), q.getY() + 1.2, q.getZ(), s);
                it.setPickUpDelay(10);
                r.level.addFreshEntity(it);
            }
        }
    }

    private static List<ItemStack> loot(Raid r) {
        var rnd = r.level.random;
        return switch (r.kind) {
            case GOV -> List.of(new ItemStack(Items.IRON_INGOT, 6 + rnd.nextInt(6) + r.severity * 4), new ItemStack(com.xenowarriors.item.ModItems.DATA_SHARD.get(), 2 + rnd.nextInt(3)),
                    new ItemStack(Items.EMERALD, 3 + rnd.nextInt(4)));
            case SR -> List.of(new ItemStack(Items.DIAMOND, 1 + rnd.nextInt(2) + r.severity), new ItemStack(Items.EMERALD, 4 + rnd.nextInt(5)),
                    new ItemStack(com.xenowarriors.item.ModItems.SKY_CRYSTAL.get(), 2 + rnd.nextInt(3)));
            case TOWN -> List.of(new ItemStack(Items.EMERALD, 4 + rnd.nextInt(5) + r.severity * 3), new ItemStack(Items.BREAD, 4 + rnd.nextInt(4)),
                    new ItemStack(Items.IRON_INGOT, 2 + rnd.nextInt(3)));
        };
    }

    private static void puff(ServerLevel l, Entity e) {
        l.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 8, e.getBbWidth() / 2, e.getBbHeight() / 3, e.getBbWidth() / 2, 0.02);
    }

    // ------------------------------------------------------------------ events

    /** Raiders and raid vehicles from a raid that's over (or from before a restart) never come back into the world. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide) return;
        CompoundTag t = e.getEntity().getPersistentData();
        if (!t.hasUUID(TAG)) return;
        Raid r = byId(t.getUUID(TAG));
        UUID id = e.getEntity().getUUID();
        if (r == null || (!r.raiders.contains(id) && !r.vehicles.contains(id))) e.setCanceled(true);
    }

    /** No friendly fire inside a raid; the militia's torches set you alight. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        Entity src = e.getSource().getEntity();
        if (src == null || e.getEntity().level().isClientSide) return;
        if (sameRaid(src, e.getEntity())) { e.setCanceled(true); return; }
        if (isRaider(src) && src instanceof com.xenowarriors.npc.HumanNpcEntity h && h.getOffhandItem().is(Items.TORCH) && e.getSource().getDirectEntity() == src
                && h.getRandom().nextFloat() < 0.35F) e.getEntity().setSecondsOnFire(3);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Raid r = raidOn(p);
        if (r != null) end(r, false, "died");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Raid r = raidOn(e.getEntity());
        if (r != null) end(r, false, "left");
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        for (Raid r : RAIDS) r.bar.removeAllPlayers();
        RAIDS.clear();
        BY_ID.clear();
    }

    // ------------------------------------------------------------------ /xeno raid gov|sr|town|stop|status

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        var raid = Commands.literal("raid").requires(s -> s.hasPermission(2));
        for (Kind k : Kind.values())
            raid.then(Commands.literal(k.key()).executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Raid r = start(p, k, true);
                ctx.getSource().sendSuccess(() -> Component.literal(r != null ? k.title + " started (" + r.waves + " waves)"
                        : "Can't start one here (a raid is already on, peaceful difficulty, or not in the Overworld / Recarnet)"), true);
                return r != null ? 1 : 0;
            }));
        raid.then(Commands.literal("stop").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            Raid r = raidOn(p);
            if (r == null) r = raidNear(p);
            if (r != null) end(r, false, "stopped");
            clearCooldowns(p);
            Raid fr = r;
            ctx.getSource().sendSuccess(() -> Component.literal(fr != null ? fr.kind.title + " stopped (cooldowns cleared)" : "No raid near you (cooldowns cleared)"), true);
            return fr != null ? 1 : 0;
        }));
        raid.then(Commands.literal("status").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            for (Kind k : Kind.values()) {
                int rep = Reputation.get(p, k.faction);
                long cd = cooldownLeft(p, k);
                ctx.getSource().sendSuccess(() -> Component.literal(k.title + ": standing " + rep + " (" + Reputation.Tier.of(rep).title + "), chance "
                        + Math.round(chance(rep) * 100) + "% per check" + (cd > 0 ? ", cooldown " + cd / 1200 + " min" : "") + (enabled(p.server, k) ? "" : " [off]")), false);
            }
            Raid r = raidOn(p);
            ctx.getSource().sendSuccess(() -> Component.literal(r == null ? "No raid on you." : r.kind.title + " on you: wave " + r.wave + "/" + r.waves), false);
            return 1;
        }));
        e.getDispatcher().register(Commands.literal("xeno").then(raid));
    }

    // ------------------------------------------------------------------ dedicated-server self check

    /** Spawns one Gov helicopter + convoy unit, one rebel and one militia member with no player, checks them, and clears them. */
    public static String selfTest(MinecraftServer server) {
        ServerLevel l = server.overworld();
        BlockPos at = l.getSharedSpawnPos();
        Raid r = new Raid(Kind.GOV, l, null, 0);
        RAIDS.add(r);
        BY_ID.put(r.id, r);
        try {
            Vec3 c = Vec3.atBottomCenterOf(at);
            var heli = RaidSpawns.govVehicle(r, com.xenowarriors.vehicle.VehicleKind.HELICOPTER, c.add(0, 20, 0), 0F, 8, false);
            var truck = RaidSpawns.govVehicle(r, com.xenowarriors.vehicle.VehicleKind.GOV_TRUCK, c.add(6, 0, 0), 0F, 6, false);
            var sr = RaidSpawns.rebel(r, c.add(0, 10, 4), -1);
            var town = RaidSpawns.militia(r, c.add(-4, 0, 0), true);
            String s = "heli=" + (heli == null ? "none" : heli.getPassengers().size() + " aboard") + " truck=" + (truck == null ? "none" : truck.getPassengers().size() + " aboard")
                    + " rebel=" + (sr != null) + " militia=" + (town != null) + " raiders=" + r.raiders.size();
            // the clean-up: an entity from a raid that's gone is refused
            var stray = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(l);
            boolean refused = false;
            if (stray != null) {
                stray.moveTo(c.x, c.y, c.z);
                stray.getPersistentData().putUUID(TAG, UUID.randomUUID());
                refused = !l.addFreshEntity(stray) || stray.isRemoved() || l.getEntity(stray.getUUID()) == null;
            }
            end(r, false, "selftest");
            int left = 0;
            for (UUID u : r.raiders) if (l.getEntity(u) != null && !l.getEntity(u).isRemoved()) left++;
            for (UUID u : r.vehicles) if (l.getEntity(u) != null && !l.getEntity(u).isRemoved()) left++;
            return s + " strayRefused=" + refused + " leftAfterEnd=" + left + ((heli != null && truck != null && refused && left == 0) ? " OK" : " FAIL");
        } finally {
            end(r, false, "selftest");
            RAIDS.remove(r);
        }
    }
}

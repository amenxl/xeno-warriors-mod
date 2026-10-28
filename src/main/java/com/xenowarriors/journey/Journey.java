package com.xenowarriors.journey;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterManager;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.journey.JourneyRules.*;
import com.xenowarriors.network.JourneyStatePacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.tower.Tower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Journey mode: you and one partner (or you alone) pick a party of 8 - 4 characters each (8 solo) - and walk a floating
 * path of steps: battles, scenario battles (Sky Haters want Skylanders, the facility wants humans...), healing pods and a
 * boss at the end. Before each battle you choose which of your characters to bring; when the one you're playing goes
 * down, the next one tags in. Health carries over between battles and nothing heals you in a battle: heal and revive at
 * the Heal/Enhance shop between steps with Journey Coins, earned from your battle score and challenges.
 * Difficulties: Easy, Normal, Hard, Insane, Xeno - beat one to open the next. Clearing a journey pays Money.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Journey {
    public static final int LOBBY = 0, IDLE = 1, CHOOSING = 2, BATTLE = 3;
    /** Screens to open on the client. */
    public static final int OPEN_NONE = 0, OPEN_LOBBY = 1, OPEN_TEAM = 2, OPEN_MENU = 3, OPEN_RESULTS = 4;
    private static final String KEY = "xw_journey";

    private record Back(ResourceKey<Level> dim, Vec3 pos, float yRot) {}

    static final class Member {
        final UUID id; String name;
        final List<CharacterType> picks = new ArrayList<>(), chosen = new ArrayList<>();
        boolean ready, out, fought;
        CharacterType active = CharacterType.NONE, original = CharacterType.NONE;
        int score; float hurt;
        Back back; GameType mode = GameType.SURVIVAL;
        /** The characters fighting beside you as AI teammates this battle (bot entity -> character). */
        final Map<UUID, CharacterType> bots = new LinkedHashMap<>();
        Member(ServerPlayer p) { id = p.getUUID(); name = p.getGameProfile().getName(); }
    }

    static final class Run {
        int slot, diff, phase = LOBBY, cur, jc, chSec, chHearts, waves;
        UUID host;
        final List<Member> members = new ArrayList<>();
        final Map<CharacterType, Float> hp = new EnumMap<>(CharacterType.class);
        int[] nodes = new int[0];
        boolean cleared = true, powerUp, powerNext;
        Scenario scenario = Scenario.NONE; Foe foe = Foe.MIXED; Challenge challenge = Challenge.NO_DOWNS; boolean challengeOk;
        long chooseUntil, spawnAt, battleStart, battleEnd;
        float enemyHp;
        final Set<UUID> alive = new HashSet<>(), seen = new HashSet<>();
        final List<String> log = new ArrayList<>();
        CompoundTag results = new CompoundTag();
        final ArrayDeque<BlockPos> building = new ArrayDeque<>();
        final UUID[] labels = new UUID[JourneyRules.STEPS + 1];
        boolean solo() { return members.size() == 1; }
        /** How many characters each player brings onto the field at once: 4 alone, 2 each as a duo. */
        int field() { return solo() ? 4 : 2; }
        int cap() { return solo() ? 8 : 4; }
        Member member(UUID id) { for (Member m : members) if (m.id.equals(id)) return m; return null; }
        boolean alive(CharacterType t) { return hp.getOrDefault(t, 0F) > 0F; }
        int kind() { return cur < nodes.length ? nodes[cur] : JourneyRules.START; }
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Run> BY = new HashMap<>();

    private Journey() {}

    /** In a journey (walking the path or fighting) - not just the lobby. */
    public static boolean inRun(Player p) { Run r = BY.get(p.getUUID()); return r != null && r.phase != LOBBY; }
    public static boolean inLobbyOrRun(Player p) { return BY.containsKey(p.getUUID()); }

    private static Component msg(String s, ChatFormatting c) { return Component.literal(s).withStyle(c); }

    // ------------------------------------------------------------------------------------------------ progress

    /** How many difficulties this player has cleared, in order (0-5). */
    public static int cleared(ServerPlayer p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(KEY); }

    public static void setCleared(ServerPlayer p, int n) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        root.putInt(KEY, Math.max(0, Math.min(5, n)));
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
    }

    /** The hardest difficulty this lobby can pick: one past the lowest either of you has cleared. */
    private static int maxDiff(Run r, MinecraftServer server) {
        int min = 5;
        for (Member m : r.members) { ServerPlayer p = server.getPlayerList().getPlayer(m.id); if (p != null) min = Math.min(min, cleared(p)); }
        return Math.min(4, min);
    }

    // ------------------------------------------------------------------------------------------------ the lobby

    /** From the main menu or /xeno journey: opens your lobby, your partner's, or your journey's menu. */
    public static void open(ServerPlayer p) {
        Run r = BY.get(p.getUUID());
        if (r != null) { sync(p.server, r, p.getUUID(), r.phase == LOBBY ? OPEN_LOBBY : r.phase == CHOOSING ? OPEN_TEAM : OPEN_MENU); return; }
        if (Tower.is(p.level()) || com.xenowarriors.room.RoomTech.is(p.level())) { p.displayClientMessage(msg("You can't start a journey from here", ChatFormatting.GRAY), true); return; }
        List<ServerPlayer> mates = new ArrayList<>(com.xenowarriors.party.Parties.members(p));
        mates.remove(p);
        if (mates.size() > 1) { p.sendSystemMessage(msg("Journey mode is for you and one partner - your party has " + (mates.size() + 1) + " players.", ChatFormatting.RED)); return; }
        for (ServerPlayer m : mates) {
            Run o = BY.get(m.getUUID());
            if (o != null) {
                if (o.phase == LOBBY && o.members.size() < 2) join(p, o);
                else p.displayClientMessage(msg(m.getGameProfile().getName() + " is already on a journey", ChatFormatting.GRAY), true);
                return;
            }
        }
        int slot = 0;
        while (RUNS.containsKey(slot)) slot++;
        Run n = new Run();
        n.slot = slot; n.host = p.getUUID();
        RUNS.put(slot, n);
        n.members.add(new Member(p));
        BY.put(p.getUUID(), n);
        n.diff = maxDiff(n, p.server);
        for (ServerPlayer m : mates) {
            m.sendSystemMessage(msg(p.getGameProfile().getName() + " is setting up a Journey! ", ChatFormatting.AQUA).copy()
                    .append(Component.literal("[Join]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xeno journey"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/xeno journey"))))));
            com.xenowarriors.network.PopupPacket.send(m, com.xenowarriors.network.PopupPacket.REQUEST, p.getUUID(), ModCapabilities.getType(p).ordinal(), -1,
                    "Journey", p.getGameProfile().getName() + " wants you on their Journey - Main Menu > Journey", "");
        }
        sync(p.server, n, p.getUUID(), OPEN_LOBBY);
    }

    private static void join(ServerPlayer p, Run r) {
        r.members.add(new Member(p));
        BY.put(p.getUUID(), r);
        Member host = r.members.get(0);
        while (host.picks.size() > 4) host.picks.remove(host.picks.size() - 1);
        for (Member m : r.members) m.ready = false;
        r.diff = Math.min(r.diff, maxDiff(r, p.server));
        syncAll(p.server, r, OPEN_NONE);
        sync(p.server, r, p.getUUID(), OPEN_LOBBY);
    }

    /** The client asks for something (see JourneyActionPacket). */
    public static void action(ServerPlayer p, int act, int a, int b) {
        if (act == 0) { open(p); return; }
        Run r = BY.get(p.getUUID());
        if (r == null) return;
        Member me = r.member(p.getUUID());
        if (me == null) return;
        MinecraftServer server = p.server;
        switch (act) {
            case 1 -> {                                            // pick / unpick a character
                if (r.phase != LOBBY || a < 0 || a >= CharacterType.values().length) return;
                CharacterType t = CharacterType.values()[a];
                if (t == CharacterType.NONE || !com.xenowarriors.character.Seasons.unlocked(server, t.season())) return;
                if (me.picks.remove(t)) { me.ready = false; break; }
                for (Member o : r.members) if (o != me && o.picks.contains(t)) return;
                if (me.picks.size() >= r.cap()) return;
                me.picks.add(t);
                me.ready = false;
            }
            case 2 -> { if (r.phase == LOBBY && p.getUUID().equals(r.host)) { r.diff = Math.max(0, Math.min(maxDiff(r, server), a)); for (Member m : r.members) m.ready = false; } }
            case 3 -> { if (r.phase == LOBBY) me.ready = !me.ready && me.picks.size() == r.cap(); }
            case 4 -> { if (r.phase == LOBBY && p.getUUID().equals(r.host)) start(server, r, p); }
            case 5 -> { if (r.phase == LOBBY) leave(p, "You left the Journey lobby", false); return; }
            case 6 -> {                                            // choose who comes into this battle
                if (r.phase != CHOOSING || me.ready || a < 0 || a >= CharacterType.values().length) return;
                CharacterType t = CharacterType.values()[a];
                if (!me.picks.contains(t) || !r.alive(t) || !r.scenario.rule.fits(t)) return;
                if (!me.chosen.remove(t) && me.chosen.size() < r.field()) me.chosen.add(t);
            }
            case 7 -> {
                if (r.phase != CHOOSING || me.out) return;
                if (me.ready) me.ready = false;
                else if (!me.chosen.isEmpty()) me.ready = true;
                if (allReady(r)) { startBattle(server, r); return; }
            }
            case 8 -> buy(server, r, p, a, b);
            case 9 -> { if (r.phase != LOBBY) leave(p, "You retreated from the Journey", true); return; }
            case 10 -> { if (r.phase == IDLE && !r.cleared && r.kind() != JourneyRules.HEAL) beginChoose(server, r); return; }
            default -> { return; }
        }
        syncAll(server, r, OPEN_NONE);
    }

    private static boolean allReady(Run r) { for (Member m : r.members) if (!m.ready) return false; return true; }

    private static void start(MinecraftServer server, Run r, ServerPlayer host) {
        ServerLevel tl = server.getLevel(Tower.LEVEL);
        if (tl == null) { host.sendSystemMessage(msg("The Tower dimension isn't loaded.", ChatFormatting.RED)); return; }
        for (Member m : r.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            String why = p == null ? m.name + " isn't online"
                    : m.picks.size() != r.cap() ? m.name + " needs to pick " + r.cap() + " characters"
                    : !m.ready && !m.id.equals(r.host) ? m.name + " isn't ready"
                    : p.isCreative() || p.isSpectator() ? m.name + " needs to be in Survival or Adventure mode"
                    : !com.xenowarriors.bot.PartyBots.bots(p).isEmpty() ? m.name + " has bot teammates out - dismiss them first (Journey is just the two of you)"
                    : Tower.is(p.level()) || com.xenowarriors.room.RoomTech.is(p.level()) ? m.name + " can't leave from where they are"
                    : r.members.size() == 2 && !m.id.equals(r.host) && !com.xenowarriors.party.Parties.sameParty(p.getUUID(), r.host) ? m.name + " needs to be in your party"
                    : null;
            if (why != null) { host.displayClientMessage(msg(why, ChatFormatting.RED), true); return; }
        }
        r.diff = Math.min(r.diff, maxDiff(r, server));
        // the path: 10 steps, a healing pod at 7 (and sometimes 4), a boss at the end
        var rnd = tl.getRandom();
        r.nodes = new int[JourneyRules.STEPS + 1];
        r.nodes[0] = JourneyRules.START;
        for (int i = 1; i <= JourneyRules.STEPS; i++) {
            r.nodes[i] = i == JourneyRules.STEPS ? JourneyRules.BOSS : i == 7 ? JourneyRules.HEAL : i == 4 && rnd.nextBoolean() ? JourneyRules.HEAL
                    : i == 1 ? JourneyRules.BATTLE : i == 3 || i == 6 ? JourneyRules.SCENARIO : rnd.nextInt(3) == 0 ? JourneyRules.SCENARIO : JourneyRules.BATTLE;
        }
        r.hp.clear();
        for (Member m : r.members) for (CharacterType t : m.picks) r.hp.put(t, 1F);
        r.jc = JourneyRules.START_JC;
        r.cur = 0; r.cleared = true; r.phase = IDLE;
        r.log.clear();
        r.log.add(JourneyRules.DIFF[r.diff] + " journey started (" + (r.solo() ? "solo" : "duo") + ")");
        for (Entity e : tl.getEntitiesOfClass(Entity.class, JourneyWorld.area(r.slot), e -> !(e instanceof Player))) e.discard();
        JourneyWorld.buildPath(tl, r.slot, r.nodes);
        for (int i = 1; i < r.nodes.length; i++) relabel(tl, r, i, false);
        relabel(tl, r, 0, false);
        BlockPos s = JourneyWorld.node(r.slot, 0);
        int k = 0;
        for (Member m : r.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p == null) continue;
            m.back = new Back(p.level().dimension(), p.position(), p.getYRot());
            m.original = ModCapabilities.getType(p);
            m.mode = p.gameMode.getGameModeForPlayer();
            m.ready = false;
            setBack(p, m);
            p.teleportTo(tl, s.getX() + .5 + (k++ == 0 ? -1.5 : 1.5), s.getY() + 1, s.getZ() + .5, 0F, 0F);
            lead(p, r, m);
            title(p, JourneyRules.DIFF[r.diff] + " JOURNEY", "Walk the path - step on the next platform", JourneyRules.DIFF_COLOR[r.diff]);
            p.sendSystemMessage(msg("JOURNEY - " + JourneyRules.STEPS + " steps to the boss. Nothing heals you in battle: use Main Menu > Journey (Heal/Enhance) between steps. "
                    + "Earn Journey Coins with high scores and challenges.", ChatFormatting.AQUA));
        }
        openBridge(r);
        syncAll(server, r, OPEN_NONE);
    }

    // ------------------------------------------------------------------------------------------------ leaving

    public static void leave(ServerPlayer p, String why, boolean restore) {
        Run r = BY.remove(p.getUUID());
        if (r == null) return;
        Member m = r.member(p.getUUID());
        if (m != null) clearBots(p.server, r, m);
        r.members.remove(m);
        com.xenowarriors.tower.TowerBosses.removePlayer(p);
        if (m != null && r.phase != LOBBY) {
            for (CharacterType t : m.picks) r.hp.remove(t);
            if (restore) restore(p, m);
            p.sendSystemMessage(msg(why, ChatFormatting.GRAY));
        }
        ModNetwork.sendToPlayer(new JourneyStatePacket(new CompoundTag(), OPEN_NONE), p);
        if (r.members.isEmpty()) { close(p.server, r); return; }
        if (r.host.equals(p.getUUID())) r.host = r.members.get(0).id;
        for (Member o : r.members) {
            ServerPlayer op = p.server.getPlayerList().getPlayer(o.id);
            if (op != null) op.sendSystemMessage(msg(p.getGameProfile().getName() + " left the Journey" + (r.phase != LOBBY ? " - their characters went with them" : ""), ChatFormatting.YELLOW));
        }
        if (r.phase == LOBBY) for (Member o : r.members) o.ready = false;
        if (r.phase == CHOOSING && allReady(r)) startBattle(p.server, r);
        else if (r.phase == BATTLE) checkLost(p.server, r);
        syncAll(p.server, r, OPEN_NONE);
    }

    private static void restore(ServerPlayer p, Member m) {
        restore(p, m.original, m.mode, m.back == null ? null : m.back.dim(), m.back == null ? null : m.back.pos(), m.back == null ? 0F : m.back.yRot());
    }

    /** Back to who you were (the select screen if that character's taken now, or you had none), your game mode and where you were. */
    private static void restore(ServerPlayer p, CharacterType original, GameType mode, ResourceKey<Level> dim, Vec3 pos, float yRot) {
        setBack(p, null);
        if (p.gameMode.getGameModeForPlayer() != mode) p.setGameMode(mode);
        ServerPlayer holder = com.xenowarriors.character.CharacterLock.takenBy(p, original);
        if (original == CharacterType.NONE || holder != null) {
            CharacterManager.setCharacter(p, CharacterType.NONE);
            if (holder != null) p.sendSystemMessage(msg(holder.getGameProfile().getName() + " is playing " + original.getDisplayName().getString() + " now - pick another character.", ChatFormatting.YELLOW));
            CharacterManager.openSelectScreen(p);
        } else if (ModCapabilities.getType(p) != original) CharacterManager.setCharacter(p, original);
        p.removeAllEffects();
        p.clearFire();
        p.setHealth(p.getMaxHealth());
        if (Tower.is(p.level()) && pos != null) {
            ServerLevel to = dim == null ? null : p.server.getLevel(dim);
            if (to == null) to = p.server.overworld();
            p.teleportTo(to, pos.x, pos.y, pos.z, yRot, 0);
        }
    }

    /** Where to send you back (kept on you, so a crash or restart mid-journey still gets you home). */
    private static void setBack(ServerPlayer p, Member m) {
        CompoundTag root = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (m == null) root.remove("xw_journey_back");
        else {
            CompoundTag t = new CompoundTag();
            t.putInt("char", m.original.ordinal());
            t.putInt("mode", m.mode.getId());
            if (m.back != null) {
                t.putString("dim", m.back.dim().location().toString());
                t.putDouble("x", m.back.pos().x); t.putDouble("y", m.back.pos().y); t.putDouble("z", m.back.pos().z); t.putFloat("yRot", m.back.yRot());
            }
            root.put("xw_journey_back", t);
        }
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
    }

    /** Logged in with a journey still on you (the server stopped mid-run): put you back. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || BY.containsKey(p.getUUID())) return;
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("xw_journey_back");
        if (t.isEmpty()) return;
        ResourceKey<Level> dim = t.contains("dim") ? ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(t.getString("dim"))) : null;
        Vec3 pos = t.contains("x") ? new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")) : null;
        if (!Tower.is(p.level()) && pos != null && dim != null) {          // (the Tower already sent you to spawn: go back to where you were)
            ServerLevel to = p.server.getLevel(dim);
            if (to != null) p.teleportTo(to, pos.x, pos.y, pos.z, t.getFloat("yRot"), 0);
            pos = null;
        }
        restore(p, (t.getInt("char") >= 0 && t.getInt("char") < CharacterType.values().length ? CharacterType.values()[t.getInt("char")] : CharacterType.NONE), GameType.byId(t.getInt("mode")), dim, pos, t.getFloat("yRot"));
        p.sendSystemMessage(msg("Your last Journey ended when the server stopped - you're back where you started it.", ChatFormatting.GRAY));
    }

    private static void close(MinecraftServer server, Run r) {
        RUNS.remove(r.slot);
        for (Member m : r.members) BY.remove(m.id);
        ServerLevel tl = server.getLevel(Tower.LEVEL);
        if (tl == null || r.nodes.length == 0) return;
        com.xenowarriors.tower.TowerBosses.clear(JourneyWorld.arena(r.slot));
        for (Entity e : tl.getEntitiesOfClass(Entity.class, JourneyWorld.area(r.slot), e -> !(e instanceof Player) && !(e instanceof com.xenowarriors.bot.PartyBotEntity))) e.discard();
    }

    // ------------------------------------------------------------------------------------------------ walking the path

    /** The character you walk the path as: the one you last fought with if they're up, else your first one still standing. */
    private static void lead(ServerPlayer p, Run r, Member m) {
        CharacterType t = m.active != CharacterType.NONE && r.alive(m.active) && m.picks.contains(m.active) ? m.active : CharacterType.NONE;
        if (t == CharacterType.NONE) for (CharacterType c : m.picks) if (r.alive(c)) { t = c; break; }
        if (t == CharacterType.NONE && !m.picks.isEmpty()) t = m.picks.get(0);
        if (t == CharacterType.NONE) return;
        become(p, t);
        m.active = t;
        p.setHealth(Math.max(1F, p.getMaxHealth() * r.hp.getOrDefault(t, 0F)));
    }

    private static void become(ServerPlayer p, CharacterType t) {
        if (ModCapabilities.getType(p) != t) CharacterManager.setCharacter(p, t);
    }

    private static void openBridge(Run r) {
        if (r.cur + 1 >= r.nodes.length) return;
        r.building.addAll(JourneyWorld.bridge(r.slot, r.cur));
    }

    private static void relabel(ServerLevel tl, Run r, int i, boolean done) {
        if (r.labels[i] != null) { Entity e = tl.getEntity(r.labels[i]); if (e != null) e.discard(); }
        String text = done ? "CLEARED" : JourneyRules.STEP_NAME[r.nodes[i]];
        int col = done ? 0x9A9AA8 : JourneyRules.STEP_COLOR[r.nodes[i]];
        Entity e = JourneyWorld.label(tl, JourneyWorld.node(r.slot, i), text, col, i);
        r.labels[i] = e == null ? null : e.getUUID();
    }

    private static void arrive(MinecraftServer server, ServerLevel tl, Run r, int i) {
        JourneyWorld.recolor(tl, JourneyWorld.node(r.slot, r.cur), JourneyWorld.ring(r.nodes[r.cur], 2));
        if (r.cur > 0) relabel(tl, r, r.cur, true);
        r.cur = i;
        r.cleared = false;
        BlockPos c = JourneyWorld.node(r.slot, i);
        JourneyWorld.recolor(tl, c, JourneyWorld.ring(r.nodes[i], 1));
        int k = 0;
        for (Member m : r.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p != null && !JourneyWorld.onPlatform(p, c)) p.teleportTo(tl, c.getX() + .5 + (k == 0 ? -1.5 : 1.5), c.getY() + 1, c.getZ() + .5, p.getYRot(), 0);
            k++;
        }
        tl.playSound(null, c, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 1.4F);
        if (r.nodes[i] == JourneyRules.HEAL) {
            for (CharacterType t : r.hp.keySet()) if (r.alive(t)) r.hp.put(t, Math.min(1F, r.hp.get(t) + 0.6F));
            for (Member m : r.members) { ServerPlayer p = server.getPlayerList().getPlayer(m.id); if (p != null) { lead(p, r, m); title(p, "HEALING POD", "Everyone still standing healed 60%", 0x60F060); } }
            tl.sendParticles(ParticleTypes.HEART, c.getX() + .5, c.getY() + 1.5, c.getZ() + .5, 30, 2.5, .6, 2.5, 0);
            tl.playSound(null, c, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1.2F);
            r.log.add("Step " + i + ": Healing Pod - healed 60%");
            stepDone(server, tl, r);
            return;
        }
        beginChoose(server, r);
    }

    private static void stepDone(MinecraftServer server, ServerLevel tl, Run r) {
        r.cleared = true;
        openBridge(r);
        for (Member m : r.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p == null) continue;
            p.sendSystemMessage(msg("Step " + r.cur + " done. ", ChatFormatting.GREEN).copy()
                    .append(Component.literal("[Heal/Enhance]").withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/xeno journey"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Spend Journey Coins on healing and revives")))))
                    .append(msg("  (" + r.jc + " JC)  The path to step " + (r.cur + 1) + " is open.", ChatFormatting.GRAY)));
        }
        syncAll(server, r, OPEN_NONE);
    }

    // ------------------------------------------------------------------------------------------------ choosing the team

    private static boolean canField(Run r, Member m, Rule rule) {
        for (CharacterType t : m.picks) if (r.alive(t) && rule.fits(t)) return true;
        return false;
    }

    private static void beginChoose(MinecraftServer server, Run r) {
        var rnd = server.overworld().getRandom();
        int kind = r.kind();
        if (kind == JourneyRules.SCENARIO) {
            // a scenario this party can actually play: everyone still standing must have someone who fits
            List<Scenario> ok = new ArrayList<>();
            for (Scenario s : Scenario.values()) {
                if (s == Scenario.NONE) continue;
                boolean fits = true;
                for (Member m : r.members) if (canField(r, m, Rule.ANY) && !canField(r, m, s.rule)) fits = false;
                if (fits) ok.add(s);
            }
            List<Scenario> picky = new ArrayList<>(ok);
            picky.remove(Scenario.OUTBREAK);
            r.scenario = !picky.isEmpty() && rnd.nextInt(4) != 0 ? picky.get(rnd.nextInt(picky.size())) : Scenario.OUTBREAK;
            r.foe = r.scenario.foe;
        } else {
            r.scenario = Scenario.NONE;
            r.foe = Foe.values()[rnd.nextInt(Foe.values().length)];
        }
        Challenge[] all = Challenge.values();
        r.challenge = all[rnd.nextInt(all.length)];
        int people = r.members.size();
        r.chSec = 45 + 8 * (3 + r.diff + r.cur / 3 + (people - 1) * 2) + (kind == JourneyRules.BOSS ? 60 : 0);
        r.chHearts = 10 + 5 * people + (kind == JourneyRules.BOSS ? 10 : 0);
        r.phase = CHOOSING;
        r.chooseUntil = server.overworld().getGameTime() + 20 * 60;
        boolean anyone = false;
        for (Member m : r.members) {
            m.chosen.clear();
            m.out = !canField(r, m, r.scenario.rule);
            m.ready = m.out;
            if (!m.out) anyone = true;
        }
        if (!anyone) { failed(server, r); return; }
        syncAll(server, r, OPEN_TEAM);
    }

    // ------------------------------------------------------------------------------------------------ battles

    private static void startBattle(MinecraftServer server, Run r) {
        ServerLevel tl = server.getLevel(Tower.LEVEL);
        if (tl == null) return;
        boolean boss = r.kind() == JourneyRules.BOSS;
        JourneyWorld.arena(tl, r.slot, r.foe, boss);
        BlockPos c = JourneyWorld.arena(r.slot);
        r.phase = BATTLE;
        r.powerUp = r.powerNext; r.powerNext = false;
        r.challengeOk = true;
        r.enemyHp = 0;
        r.alive.clear(); r.seen.clear();
        r.waves = boss ? 1 : r.diff >= 3 ? 2 : 1;
        long now = tl.getGameTime();
        r.spawnAt = now + 60;
        r.battleStart = now + 60;
        int k = 0;
        for (Member m : r.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p == null) continue;
            m.score = 0; m.hurt = 0;
            m.ready = false;
            m.fought = false;
            double a = k++ * Math.PI;
            if (m.out || m.chosen.isEmpty()) {
                m.out = true;
                p.setGameMode(GameType.SPECTATOR);
                p.teleportTo(tl, c.getX() + .5, c.getY() + 12, c.getZ() + .5 - 14, 0F, 30F);
                p.sendSystemMessage(msg("None of your characters can fight in this battle - you're watching this one.", ChatFormatting.GRAY));
                continue;
            }
            if (r.challenge == Challenge.SOLO && m.chosen.size() != 1) r.challengeOk = false;
            m.fought = true;
            p.teleportTo(tl, c.getX() + .5 + Math.sin(a) * 2, c.getY() + 1, c.getZ() + .5 + 10, 180F, 0F);
            m.active = m.chosen.get(0);
            become(p, m.active);
            p.removeAllEffects();
            p.clearFire();
            p.setHealth(Math.max(1F, p.getMaxHealth() * r.hp.getOrDefault(m.active, 1F)));
            com.xenowarriors.karma.Karma.add(p, 100000F);
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
            for (int i = 1; i < m.chosen.size(); i++) spawnBot(p, r, m, m.chosen.get(i));
            String what = boss ? "BOSS" : r.scenario != Scenario.NONE ? r.scenario.title.toUpperCase() : "BATTLE " + r.cur;
            title(p, what, "Challenge: " + challengeText(r) + (r.challengeOk ? "" : "  (missed)"), boss ? 0xC040FF : JourneyRules.STEP_COLOR[r.kind()]);
            if (r.powerUp) p.displayClientMessage(msg("Power Up: +30% damage this battle", ChatFormatting.GOLD), true);
        }
        tl.playSound(null, c, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2F, 1.2F);
        syncAll(server, r, OPEN_NONE);
        checkLost(server, r);
    }

    /** One of your picks fighting beside you (a party bot of that character, with its journey health). */
    private static void spawnBot(ServerPlayer p, Run r, Member m, CharacterType t) {
        var b = com.xenowarriors.bot.PartyBots.spawn(p, t, new CompoundTag());
        if (b == null) return;
        b.getPersistentData().putBoolean("xw_journey_bot", true);
        b.setHealth(Math.max(1F, b.getMaxHealth() * r.hp.getOrDefault(t, 1F)));
        b.karma = 100F;
        m.bots.put(b.getUUID(), t);
    }

    /** Your AI teammates leave the field (their health is kept; anything they picked up goes to you). */
    private static void clearBots(MinecraftServer server, Run r, Member m) {
        ServerLevel tl = server.getLevel(Tower.LEVEL);
        ServerPlayer o = server.getPlayerList().getPlayer(m.id);
        for (var e : m.bots.entrySet()) {
            Entity en = tl == null ? null : tl.getEntity(e.getKey());
            if (en instanceof com.xenowarriors.bot.PartyBotEntity b) {
                if (b.isAlive() && !b.downed() && r.alive(e.getValue())) r.hp.put(e.getValue(), Math.max(0.001F, b.getHealth() / b.getMaxHealth()));
                giveBag(b, o);
                b.discard();
            }
        }
        m.bots.clear();
    }

    private static void giveBag(com.xenowarriors.bot.PartyBotEntity b, ServerPlayer o) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            var it = b.inventory.removeItemNoUpdate(i);
            if (it.isEmpty()) continue;
            if (o == null || !o.getInventory().add(it)) b.spawnAtLocation(it);
        }
    }

    /** A character going down (yours or an AI teammate's): the score drops, and it's out until revived at the shop. */
    private static void charDown(MinecraftServer server, Run r, Member m, CharacterType down) {
        r.hp.put(down, 0F);
        m.score = Math.max(0, m.score - JourneyRules.OWN_DOWN);
        for (Member o : r.members) if (o != m) o.score = Math.max(0, o.score - JourneyRules.MATE_DOWN);
        if (r.challenge == Challenge.NO_DOWNS) fail(server, r, down.getDisplayName().getString() + " went down");
        for (Member o : r.members) {
            ServerPlayer op = server.getPlayerList().getPlayer(o.id);
            if (op != null) op.displayClientMessage(msg(down.getDisplayName().getString() + " is down!", ChatFormatting.RED), true);
        }
    }

    /** Who gets the score for a hit: a player, or the owner of the AI teammate (or its skill stand-in) that landed it. */
    private static ServerPlayer scorer(Entity src) {
        var bot = com.xenowarriors.bot.PartyBots.botOf(src);
        if (bot != null) return bot.getPersistentData().getBoolean("xw_journey_bot") ? bot.owner() : null;
        return src instanceof ServerPlayer p && !(src instanceof net.minecraftforge.common.util.FakePlayer) ? p : null;
    }

    static String challengeText(Run r) {
        return switch (r.challenge) {
            case SPEED -> String.format(r.challenge.text, r.chSec);
            case TOUGH -> String.format(r.challenge.text, r.chHearts);
            default -> r.challenge.text;
        } + " (+" + r.challenge.bonus + " JC)";
    }

    private static List<ServerPlayer> fighters(MinecraftServer server, Run r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Member m : r.members) {
            if (m.out) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p != null && Tower.is(p.level())) out.add(p);
        }
        return out;
    }

    private static void battleTick(MinecraftServer server, ServerLevel tl, Run r, long now) {
        BlockPos c = JourneyWorld.arena(r.slot);
        List<ServerPlayer> fs = fighters(server, r);
        List<LivingEntity> targets = new ArrayList<>(fs);
        for (Member mb : r.members) for (var e : new ArrayList<>(mb.bots.entrySet())) {
            Entity en = tl.getEntity(e.getKey());
            if (en instanceof com.xenowarriors.bot.PartyBotEntity b && b.isAlive() && !b.downed()) {
                r.hp.put(e.getValue(), Math.max(0.001F, b.getHealth() / b.getMaxHealth()));
                if (b.getY() < c.getY() - 10) b.teleportTo(c.getX() + .5, c.getY() + 1, c.getZ() + .5);
                targets.add(b);
                continue;
            }
            mb.bots.remove(e.getKey());                       // down (or gone): no reviving in battle
            if (en instanceof com.xenowarriors.bot.PartyBotEntity b) {
                tl.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + .5, b.getZ(), 20, .4, .4, .4, .05);
                giveBag(b, server.getPlayerList().getPlayer(mb.id));
                b.discard();
            }
            charDown(server, r, mb, e.getValue());
        }
        if (now >= r.battleStart) {
            // everything flagged in the arena counts - including helpers a boss calls in
            r.alive.clear();
            for (Mob m : tl.getEntitiesOfClass(Mob.class, new AABB(c).inflate(JourneyWorld.ARENA_R + 14, 24, JourneyWorld.ARENA_R + 14),
                    e -> e.isAlive() && (e.getPersistentData().getBoolean("xw_journey") || e.getPersistentData().getBoolean("xw_tower")))) {
                if (m.getY() < c.getY() - 12 || m.position().distanceTo(Vec3.atCenterOf(c)) > JourneyWorld.ARENA_R + 10) {
                    if (com.xenowarriors.tower.TowerBosses.isBoss(m)) m.teleportTo(c.getX() + .5, c.getY() + 1, c.getZ() + .5);
                    else { m.discard(); continue; }
                }
                r.alive.add(m.getUUID());
                if (r.seen.add(m.getUUID())) r.enemyHp += m.getMaxHealth();
                if (!targets.isEmpty() && (m.getTarget() == null || !targets.contains(m.getTarget())))
                    m.setTarget(targets.get(tl.random.nextInt(targets.size())));
            }
            if (r.alive.isEmpty()) {
                if (r.waves <= 0) { endBattle(server, tl, r, true); return; }
                if (r.spawnAt == Long.MAX_VALUE) {
                    r.spawnAt = now + 40;
                    for (ServerPlayer p : fs) p.displayClientMessage(msg("Next wave incoming!", ChatFormatting.RED), true);
                } else if (now >= r.spawnAt) {
                    r.waves--;
                    r.spawnAt = Long.MAX_VALUE;
                    List<Mob> made;
                    if (r.kind() == JourneyRules.BOSS)
                        made = com.xenowarriors.tower.TowerBosses.spawn(tl, c, JourneyRules.BOSS_FLOOR[r.diff], fs);
                    else made = JourneyWorld.spawn(tl, r.slot, r.foe, r.diff, r.cur, r.members.size(), fs);
                    if (made.isEmpty()) made = JourneyWorld.spawn(tl, r.slot, Foe.DARK, r.diff, r.cur, r.members.size(), fs);
                    for (Mob m : made) { r.alive.add(m.getUUID()); if (r.seen.add(m.getUUID())) r.enemyHp += m.getMaxHealth(); }
                }
            }
            if (r.challengeOk && r.challenge == Challenge.SPEED && now - r.battleStart > r.chSec * 20L) fail(server, r, "Out of time");
        }
        for (ServerPlayer p : fs) {
            Member m = r.member(p.getUUID());
            if (m == null) continue;
            if (p.getY() < c.getY() - 10) p.teleportTo(c.getX() + .5, c.getY() + 1, c.getZ() + .5);
            if (m.active != CharacterType.NONE && r.alive(m.active)) r.hp.put(m.active, Math.max(0.001F, p.getHealth() / p.getMaxHealth()));
        }
        if (now % 10 == 0) syncAll(server, r, OPEN_NONE);
    }

    private static void fail(MinecraftServer server, Run r, String why) {
        if (!r.challengeOk) return;
        r.challengeOk = false;
        for (ServerPlayer p : fighters(server, r)) p.displayClientMessage(msg("Challenge failed: " + why, ChatFormatting.RED), true);
    }

    private static void endBattle(MinecraftServer server, ServerLevel tl, Run r, boolean won) {
        BlockPos c = JourneyWorld.arena(r.slot);
        boolean boss = r.kind() == JourneyRules.BOSS;
        com.xenowarriors.tower.TowerBosses.clear(c);
        for (Member m : r.members) clearBots(server, r, m);
        for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(c).inflate(JourneyWorld.ARENA_R + 14, 24, JourneyWorld.ARENA_R + 14),
                e -> (e instanceof Mob && !(e instanceof com.xenowarriors.bot.PartyBotEntity)) || e instanceof net.minecraft.world.entity.projectile.Projectile)) e.discard();
        // drops and XP come with you
        BlockPos at = JourneyWorld.node(r.slot, r.cur);
        for (Entity e : tl.getEntitiesOfClass(Entity.class, new AABB(c).inflate(JourneyWorld.ARENA_R + 6, 16, JourneyWorld.ARENA_R + 6),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb))
            e.teleportTo(at.getX() + .5, at.getY() + 1, at.getZ() + .5);
        int fighting = 0;
        for (Member m : r.members) if (m.fought) fighting++;
        float share = Math.max(1F, r.enemyHp * JourneyRules.PER_DAMAGE / Math.max(1, fighting));
        CompoundTag res = new CompoundTag();
        res.putBoolean("won", won);
        res.putBoolean("boss", boss);
        res.putString("challenge", challengeText(r));
        res.putBoolean("challengeOk", won && r.challengeOk);
        ListTag ps = new ListTag();
        int gained = 0;
        StringBuilder line = new StringBuilder();
        for (Member m : r.members) {
            int rank = JourneyRules.rank(m.score / share);
            int jc = won && m.fought ? JourneyRules.RANK_JC[rank] * (boss ? 2 : 1) + (r.challengeOk ? r.challenge.bonus : 0) : 0;   // (watching earns nothing)
            gained += jc;
            CompoundTag t = new CompoundTag();
            t.putString("name", m.name); t.putInt("score", m.score); t.putInt("rank", rank); t.putInt("jc", jc);
            ps.add(t);
            if (line.length() > 0) line.append(", ");
            line.append(m.name).append(" ").append(JourneyRules.RANK[rank]);
        }
        res.put("players", ps);
        res.putInt("gained", gained);
        r.jc += gained;
        r.results = res;
        String what = boss ? "Boss" : r.scenario != Scenario.NONE ? r.scenario.title : "Battle";
        r.log.add("Step " + r.cur + ": " + what + " - " + (won ? "won (" + line + ")" + (r.challengeOk ? ", challenge done" : "") + ", +" + gained + " JC" : "lost"));
        // back up to the path
        int k = 0;
        for (Member m : r.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p == null) continue;
            if (p.isSpectator()) p.setGameMode(m.mode);
            m.out = false;
            p.removeAllEffects();
            p.clearFire();
            p.teleportTo(tl, at.getX() + .5 + (k++ == 0 ? -1.5 : 1.5), at.getY() + 1, at.getZ() + .5, 0F, 0F);
            lead(p, r, m);
            title(p, won ? "VICTORY" : "DEFEAT", won ? "+" + gained + " Journey Coins" : "Revive your party and try again", won ? 0xFFD040 : 0xFF4040);
        }
        r.phase = IDLE;
        boolean anyAlive = false;
        for (float v : r.hp.values()) if (v > 0) anyAlive = true;
        if (won && boss) { finished(server, r); return; }
        if (!anyAlive) { failed(server, r); return; }
        if (won) { JourneyWorld.recolor(tl, at, JourneyWorld.ring(r.nodes[r.cur], 1)); stepDone(server, tl, r); }
        syncAll(server, r, OPEN_RESULTS);
    }

    private static void checkLost(MinecraftServer server, Run r) {
        if (r.phase != BATTLE) return;
        for (Member m : r.members) if (!m.out) return;
        ServerLevel tl = server.getLevel(Tower.LEVEL);
        if (tl != null) endBattle(server, tl, r, false);
    }

    private static void finished(MinecraftServer server, Run r) {
        long money = JourneyRules.REWARD[r.diff];
        for (Member m : new ArrayList<>(r.members)) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p == null) continue;
            boolean first = cleared(p) <= r.diff;
            if (first) setCleared(p, r.diff + 1);
            com.xenowarriors.economy.Money.add(p, money, true);
            String next = r.diff < 4 ? (first ? JourneyRules.DIFF[r.diff + 1] + " unlocked!" : "") : "You beat Xeno difficulty!";
            p.sendSystemMessage(msg("JOURNEY CLEARED (" + JourneyRules.DIFF[r.diff] + ")! +" + com.xenowarriors.economy.Money.fmt(money) + " Money. " + next, ChatFormatting.GOLD));
            com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), CharacterType.NONE.ordinal(), -1,
                    JourneyRules.DIFF[r.diff] + " Journey cleared", "+" + com.xenowarriors.economy.Money.fmt(money) + " Money. " + next, "");
            leave(p, "Journey complete!", true);
            title(p, "JOURNEY CLEARED", JourneyRules.DIFF[r.diff] + "  -  +" + com.xenowarriors.economy.Money.fmt(money) + " Money", JourneyRules.DIFF_COLOR[r.diff]);
        }
    }

    private static void failed(MinecraftServer server, Run r) {
        r.phase = IDLE;
        for (Member m : new ArrayList<>(r.members)) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.id);
            if (p == null) continue;
            leave(p, "Your whole party is down - the journey is over (step " + r.cur + "/" + JourneyRules.STEPS + ").", true);
            title(p, "JOURNEY FAILED", "Your whole party went down", 0xFF4040);
        }
    }

    // ------------------------------------------------------------------------------------------------ the shop

    private static void buy(MinecraftServer server, Run r, ServerPlayer p, int item, int who) {
        if (r.phase != IDLE) { p.displayClientMessage(msg("The shop opens between steps, not during a battle", ChatFormatting.RED), true); return; }
        if (item < 0 || item >= JourneyRules.PRICE.length) return;
        int price = JourneyRules.PRICE[item];
        if (r.jc < price) { p.displayClientMessage(msg("Not enough Journey Coins (" + price + " JC)", ChatFormatting.RED), true); return; }
        CharacterType t = who >= 0 && who < CharacterType.values().length ? CharacterType.values()[who] : CharacterType.NONE;
        switch (item) {
            case JourneyRules.S_HEAL, JourneyRules.S_FULL -> {
                if (!r.hp.containsKey(t) || !r.alive(t) || r.hp.get(t) >= 1F) return;
                r.hp.put(t, item == JourneyRules.S_FULL ? 1F : Math.min(1F, r.hp.get(t) + 0.4F));
            }
            case JourneyRules.S_REVIVE -> {
                if (!r.hp.containsKey(t) || r.alive(t)) return;
                r.hp.put(t, 0.5F);
            }
            case JourneyRules.S_PARTY -> {
                boolean any = false;
                for (CharacterType c : r.hp.keySet()) if (r.alive(c) && r.hp.get(c) < 1F) { r.hp.put(c, Math.min(1F, r.hp.get(c) + 0.25F)); any = true; }
                if (!any) return;
            }
            case JourneyRules.S_POWER -> { if (r.powerNext) return; r.powerNext = true; }
            default -> { return; }
        }
        r.jc -= price;
        for (Member m : r.members) {
            ServerPlayer mp = server.getPlayerList().getPlayer(m.id);
            if (mp == null) continue;
            if (m.active == t || item == JourneyRules.S_PARTY || (item == JourneyRules.S_REVIVE && !r.alive(m.active))) lead(mp, r, m);
            mp.displayClientMessage(msg(p.getGameProfile().getName() + " bought " + JourneyRules.SHOP_NAME[item] + (t != CharacterType.NONE ? " for " + t.getDisplayName().getString() : "") + " (-" + price + " JC)", ChatFormatting.AQUA), true);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.4F);
    }

    // ------------------------------------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel tl = server.getLevel(Tower.LEVEL);
        if (tl == null) return;
        long now = tl.getGameTime();
        if (now % 5 == 0) com.xenowarriors.tower.TowerBosses.tick(tl);
        for (Run r : new ArrayList<>(RUNS.values())) {
            if (r.phase == LOBBY) {
                for (Member m : new ArrayList<>(r.members)) if (server.getPlayerList().getPlayer(m.id) == null) { BY.remove(m.id); r.members.remove(m); }
                if (r.members.isEmpty()) { RUNS.remove(r.slot); continue; }
                if (r.member(r.host) == null) r.host = r.members.get(0).id;
                continue;
            }
            // the bridge builds a few blocks a tick
            for (int i = 0; i < 5 && !r.building.isEmpty(); i++) {
                BlockPos b = r.building.poll();
                tl.setBlock(b, net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 2);
                if (i == 0) tl.sendParticles(ParticleTypes.END_ROD, b.getX() + .5, b.getY() + 1, b.getZ() + .5, 2, .3, .2, .3, 0);
            }
            for (Member m : new ArrayList<>(r.members)) {
                ServerPlayer p = server.getPlayerList().getPlayer(m.id);
                if (p == null) { leaveOffline(server, r, m); continue; }
                if (!Tower.is(p.level())) { leave(p, "You left the Journey", true); continue; }
                p.getFoodData().setFoodLevel(20);
            }
            if (!RUNS.containsValue(r)) continue;
            if (r.phase == BATTLE) { battleTick(server, tl, r, now); continue; }
            if (r.phase == CHOOSING) {
                if (now >= r.chooseUntil) {                // time's up: everyone still choosing brings whoever can fight
                    for (Member m : r.members) if (!m.ready) {
                        if (m.chosen.isEmpty()) for (CharacterType t : m.picks) if (r.alive(t) && r.scenario.rule.fits(t) && m.chosen.size() < r.field()) m.chosen.add(t);
                        m.ready = true;
                    }
                    startBattle(server, r);
                } else if (now % 20 == 0) syncAll(server, r, OPEN_NONE);
                continue;
            }
            // IDLE: on the path
            if (now % 5 != 0) continue;
            BlockPos here = JourneyWorld.node(r.slot, r.cur);
            for (Member m : r.members) {
                ServerPlayer p = server.getPlayerList().getPlayer(m.id);
                if (p != null && p.getY() < JourneyWorld.PATH_Y - 12) p.teleportTo(here.getX() + .5, here.getY() + 1, here.getZ() + .5);
            }
            if (r.cleared && r.building.isEmpty() && r.cur + 1 < r.nodes.length) {
                BlockPos next = JourneyWorld.node(r.slot, r.cur + 1);
                if (now % 20 == 0) {                          // a light trail to the next step, and a beam over it
                    for (int i = 0; i <= 10; i++) {
                        double t = i / 10.0;
                        tl.sendParticles(ParticleTypes.END_ROD, here.getX() + .5 + (next.getX() - here.getX()) * t, here.getY() + 1.2 + (next.getY() - here.getY()) * t,
                                here.getZ() + .5 + (next.getZ() - here.getZ()) * t, 1, 0, 0, 0, 0);
                    }
                    for (int y = 0; y < 12; y += 2) tl.sendParticles(ParticleTypes.END_ROD, next.getX() + .5, next.getY() + 1 + y, next.getZ() + .5, 1, .05, .3, .05, 0);
                }
                for (Member m : r.members) {
                    ServerPlayer p = server.getPlayerList().getPlayer(m.id);
                    if (p != null && JourneyWorld.onPlatform(p, next)) { arrive(server, tl, r, r.cur + 1); break; }
                }
            }
        }
    }

    private static void leaveOffline(MinecraftServer server, Run r, Member m) {
        clearBots(server, r, m);
        BY.remove(m.id);
        r.members.remove(m);
        for (CharacterType t : m.picks) r.hp.remove(t);
        if (r.members.isEmpty()) close(server, r);
        else { if (r.host.equals(m.id)) r.host = r.members.get(0).id; checkLost(server, r); }
    }

    // ------------------------------------------------------------------------------------------------ rules

    /** Nothing heals you during a journey: only the shop and healing pods (which set your health directly). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if ((event.getEntity() instanceof ServerPlayer p && inRun(p)) || event.getEntity().getPersistentData().getBoolean("xw_journey_bot")) event.setCanceled(true);
    }

    /** No totems either: going down means the next character tags in. */
    @SubscribeEvent
    public static void onTotem(net.minecraftforge.event.entity.living.LivingUseTotemEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inRun(p)) event.setCanceled(true);
    }

    /** No damage on the path or to whoever's watching, and no hitting your partner. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Run r = BY.get(p.getUUID());
            if (r == null || r.phase == LOBBY) return;
            Member m = r.member(p.getUUID());
            if (r.phase != BATTLE || m == null || m.out || event.getSource().getEntity() instanceof Player) event.setCanceled(true);
        }
    }

    /** Power Up: +30% damage for the battle. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        ServerPlayer p = scorer(event.getSource().getEntity());
        if (p != null && !(event.getEntity() instanceof Player) && !(event.getEntity() instanceof com.xenowarriors.bot.PartyBotEntity)) {
            Run r = BY.get(p.getUUID());
            if (r != null && r.phase == BATTLE && r.powerUp) event.setAmount(event.getAmount() * 1.3F);
        }
    }

    /** Score: up for every hit you land, down for every hit you take (never below 0). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof com.xenowarriors.bot.PartyBotEntity b && b.getPersistentData().getBoolean("xw_journey_bot")) {
            ServerPlayer o = b.owner();
            Run r = o == null ? null : BY.get(o.getUUID());
            Member m = r == null ? null : r.member(o.getUUID());
            if (m == null || r.phase != BATTLE) return;
            float amount = Math.min(event.getAmount(), b.getHealth());
            m.score = Math.max(0, m.score - Math.round(amount * JourneyRules.PER_HURT));
            m.hurt += amount;
            float total = 0;
            for (Member x : r.members) total += x.hurt;
            if (r.challenge == Challenge.TOUGH && total > r.chHearts * 2) fail(o.server, r, "took too much damage");
        } else if (target instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer)) {
            Run r = BY.get(p.getUUID());
            if (r == null || r.phase != BATTLE) return;
            Member m = r.member(p.getUUID());
            if (m == null || m.out) return;
            float amount = Math.min(event.getAmount(), p.getHealth());
            m.score = Math.max(0, m.score - Math.round(amount * JourneyRules.PER_HURT));
            float total = 0;
            m.hurt += amount;
            for (Member o : r.members) total += o.hurt;
            if (r.challenge == Challenge.TOUGH && total > r.chHearts * 2) fail(p.server, r, "took too much damage");
        } else if ((Object) scorer(event.getSource().getEntity()) instanceof ServerPlayer p) {
            Run r = BY.get(p.getUUID());
            if (r == null || r.phase != BATTLE || !(target.getPersistentData().getBoolean("xw_journey") || target.getPersistentData().getBoolean("xw_tower"))) return;
            Member m = r.member(p.getUUID());
            if (m != null) m.score += Math.round(Math.min(event.getAmount(), target.getHealth()) * JourneyRules.PER_DAMAGE);
        }
    }

    /** Called by the skill system whenever a skill (or KA / Xeno Blast) goes off. */
    public static void onSkill(ServerPlayer p) {
        Run r = BY.get(p.getUUID());
        if (r == null || r.phase != BATTLE) return;
        Member m = r.member(p.getUUID());
        if (m == null) return;
        m.score += JourneyRules.PER_SKILL;
        if (r.challenge == Challenge.NO_SKILLS) fail(p.server, r, "a skill was used");
    }

    /** Your character goes down: the next one you brought tags in. Nobody can be revived in a battle. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Run r = BY.get(p.getUUID());
            if (r == null || r.phase == LOBBY) return;
            event.setCanceled(true);
            p.clearFire();
            p.removeAllEffects();
            Member m = r.member(p.getUUID());
            if (r.phase != BATTLE || m == null || m.out) { p.setHealth(1F); return; }
            CharacterType down = m.active;
            ServerLevel tl = p.serverLevel();
            tl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + .5, p.getZ(), 20, .4, .4, .4, .05);
            charDown(p.server, r, m, down);
            // take over an AI teammate still fighting: you become them, with their health, where they stand
            com.xenowarriors.bot.PartyBotEntity take = null;
            CharacterType next = CharacterType.NONE;
            for (var e : new ArrayList<>(m.bots.entrySet())) {
                if (tl.getEntity(e.getKey()) instanceof com.xenowarriors.bot.PartyBotEntity b && b.isAlive() && !b.downed()) { take = b; next = e.getValue(); break; }
            }
            if (take != null) {
                float frac = Math.max(0.001F, take.getHealth() / take.getMaxHealth());
                r.hp.put(next, frac);
                Vec3 at = take.position();
                float yaw = take.getYRot();
                m.bots.remove(take.getUUID());
                giveBag(take, p);
                take.discard();
                m.active = next;
                become(p, next);
                p.teleportTo(tl, at.x, at.y, at.z, yaw, p.getXRot());
                p.setHealth(Math.max(1F, p.getMaxHealth() * frac));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 4, false, false));
                tl.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1F, 1.2F);
                title(p, "TAKE OVER", "You're " + next.getDisplayName().getString() + " now!", next.getColor());
            } else {
                m.out = true;
                p.setHealth(1F);
                p.setGameMode(GameType.SPECTATOR);
                title(p, "ALL DOWN", r.solo() ? "" : "Watch your partner finish it", 0xFF4040);
                checkLost(p.server, r);
            }
            syncAll(p.server, r, OPEN_NONE);
        } else if ((Object) scorer(event.getSource().getEntity()) instanceof ServerPlayer p) {
            Run r = BY.get(p.getUUID());
            if (r != null && r.phase == BATTLE && r.seen.contains(event.getEntity().getUUID())) {
                Member m = r.member(p.getUUID());
                if (m != null) m.score += JourneyRules.PER_KILL;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && BY.containsKey(p.getUUID())) leave(p, "Left the Journey", true);
    }

    // ------------------------------------------------------------------------------------------------ syncing

    private static void syncAll(MinecraftServer server, Run r, int open) {
        for (Member m : r.members) sync(server, r, m.id, open);
    }

    private static void sync(MinecraftServer server, Run r, UUID to, int open) {
        ServerPlayer p = server.getPlayerList().getPlayer(to);
        if (p == null) return;
        ModNetwork.sendToPlayer(new JourneyStatePacket(state(server, r, to), open), p);
    }

    private static CompoundTag state(MinecraftServer server, Run r, UUID viewer) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("active", true);
        t.putInt("phase", r.phase);
        t.putInt("diff", r.diff);
        t.putInt("maxDiff", maxDiff(r, server));
        t.putBoolean("host", viewer.equals(r.host));
        t.putInt("cap", r.cap());
        t.putInt("field", r.field());
        t.putInt("jc", r.jc);
        t.putInt("cur", r.cur);
        t.putBoolean("cleared", r.cleared);
        t.put("nodes", new IntArrayTag(r.nodes));
        t.putBoolean("powerNext", r.powerNext);
        t.putBoolean("powerUp", r.powerUp);
        int season = com.xenowarriors.character.Seasons.highest(server);
        t.putInt("season", season);
        ListTag ms = new ListTag();
        for (Member m : r.members) {
            CompoundTag mt = new CompoundTag();
            mt.putString("name", m.name);
            mt.putBoolean("me", m.id.equals(viewer));
            mt.putBoolean("ready", m.ready);
            mt.putBoolean("out", m.out);
            mt.putInt("active", m.active.ordinal());
            mt.putInt("score", m.score);
            mt.put("picks", new IntArrayTag(m.picks.stream().mapToInt(Enum::ordinal).toArray()));
            mt.put("chosen", new IntArrayTag(m.chosen.stream().mapToInt(Enum::ordinal).toArray()));
            ms.add(mt);
        }
        t.put("members", ms);
        CompoundTag hp = new CompoundTag();
        for (var e : r.hp.entrySet()) hp.putFloat(String.valueOf(e.getKey().ordinal()), e.getValue());
        t.put("hp", hp);
        if (r.phase == CHOOSING || r.phase == BATTLE) {
            t.putInt("kind", r.kind());
            t.putString("scenario", r.scenario.title);
            t.putString("scenarioText", r.scenario.text);
            t.putInt("rule", r.scenario.rule.ordinal());
            t.putString("ruleText", r.scenario.rule.text);
            t.putString("challenge", challengeText(r));
            t.putBoolean("challengeOk", r.challengeOk);
            long now = server.getLevel(Tower.LEVEL) == null ? 0 : server.getLevel(Tower.LEVEL).getGameTime();
            t.putInt("chooseLeft", (int) Math.max(0, (r.chooseUntil - now) / 20));
            t.putInt("time", (int) Math.max(0, (now - r.battleStart) / 20));
            t.putInt("enemies", r.alive.size());
            t.putInt("waves", r.waves);
            t.putString("foe", r.foe.name());
        }
        t.put("results", r.results.copy());
        ListTag log = new ListTag();
        for (String s : r.log) log.add(StringTag.valueOf(s));
        t.put("log", log);
        t.put("prices", new IntArrayTag(JourneyRules.PRICE));
        return t;
    }

    private static void title(ServerPlayer p, String title, String sub, int color) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 10));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(s -> s.withColor(color & 0xFFFFFF).withBold(true))));
    }

    // ------------------------------------------------------------------------------------------------ testing

    /** (UI test / op) jump an active journey to step n (the steps before count as cleared). */
    public static void skipTo(ServerPlayer p, int step) {
        Run r = BY.get(p.getUUID());
        if (r == null || r.phase != IDLE) return;
        ServerLevel tl = p.server.getLevel(Tower.LEVEL);
        if (tl == null) return;
        step = Math.max(1, Math.min(JourneyRules.STEPS, step));
        r.building.clear();
        for (int i = r.cur; i < step - 1; i++) {
            for (BlockPos b : JourneyWorld.bridge(r.slot, i)) tl.setBlock(b, net.minecraft.world.level.block.Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 2);
            JourneyWorld.recolor(tl, JourneyWorld.node(r.slot, i), JourneyWorld.ring(r.nodes[i], 2));
            if (i > 0) relabel(tl, r, i, true);
        }
        r.cur = step - 1;
        r.cleared = true;
        BlockPos c = JourneyWorld.node(r.slot, r.cur);
        JourneyWorld.recolor(tl, c, JourneyWorld.ring(r.nodes[r.cur], 1));
        for (Member m : r.members) { ServerPlayer mp = p.server.getPlayerList().getPlayer(m.id); if (mp != null) mp.teleportTo(tl, c.getX() + .5, c.getY() + 1, c.getZ() + .5, 0, 0); }
        openBridge(r);
        syncAll(p.server, r, OPEN_NONE);
    }

    /** (UI test) set a character's health in the run (0 = down) and the coins. */
    public static void testSet(ServerPlayer p, CharacterType t, float hp, int jc) {
        Run r = BY.get(p.getUUID());
        if (r == null) return;
        if (t != CharacterType.NONE && r.hp.containsKey(t)) r.hp.put(t, hp);
        if (jc >= 0) r.jc = jc;
        syncAll(p.server, r, OPEN_NONE);
    }

    /** (UI test) end the current battle now. */
    public static void testEnd(ServerPlayer p, boolean won) {
        Run r = BY.get(p.getUUID());
        ServerLevel tl = p.server.getLevel(Tower.LEVEL);
        if (r != null && tl != null && r.phase == BATTLE) endBattle(p.server, tl, r, won);
    }

    /** (UI test) the centre of step i's platform on this player's journey. */
    public static BlockPos testNode(ServerPlayer p, int i) { Run r = BY.get(p.getUUID()); return r == null ? null : JourneyWorld.node(r.slot, i); }

    /** (UI test) the arena centre of this player's journey. */
    public static BlockPos testArena(ServerPlayer p) { Run r = BY.get(p.getUUID()); return r == null ? null : JourneyWorld.arena(r.slot); }
}

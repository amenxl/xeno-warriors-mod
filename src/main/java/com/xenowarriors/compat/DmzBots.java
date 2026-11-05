package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ, bot teammates have DragonMineZ characters of their own (saved like the player's characters - see
 * {@link DmzPerCharacter}), and they grow:
 * <ul>
 *   <li>A bot without one gets a random Dragon Ball race and class (that race's default look), saved for that character.</li>
 *   <li>Its character earns TP while it's out with you - DragonMineZ's passive TP every 5 s - and TP for its kills
 *       (DragonMineZ's own formula: the victim's health x its TP ratio + its per-hit TP).</li>
 *   <li>Its kills count for your DragonMineZ missions (kill objectives), as if you'd landed the blow.</li>
 *   <li>Its menu has a Dragon Ball button: its DMZ character is swapped into yours while DragonMineZ's menu is open
 *       (points, skills, missions all go to the bot's), and yours is put back when it closes - or when you log out,
 *       get hit, die, switch characters, after 10 minutes, or after a crash.</li>
 * </ul>
 * All by reflection; nothing at all happens without DragonMineZ.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBots {
    private DmzBots() {}

    private static Boolean loaded;
    private static boolean broken;
    private static Method initialize, getCharacter, getStats, getStatus, setCreated, getResources, addTp, getTp;
    private static Method loadedRaces, raceStats, allClasses, raceChar, serverConfig, gameplay, passiveTp, tpRatio, tpPerHit, creditKill;
    private static final String[] STAT_SETTERS = {"setStrength", "setStrikePower", "setResistance", "setVitality", "setKiPower", "setEnergy"};
    private static final Set<CharacterType> GIRLS = EnumSet.of(CharacterType.MIKIKO, CharacterType.KAERU, CharacterType.KYRIE, CharacterType.LEXY,
            CharacterType.TECHNICA, CharacterType.AIONI, CharacterType.YURI);

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz bots: {}", s);
    }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (getCharacter != null) return true;
        try {
            Class<?> data = Class.forName("com.dragonminez.common.stats.StatsData");
            for (Method m : data.getMethods()) if (m.getName().equals("initializeWithRaceAndClass") && m.getParameterCount() == 19) initialize = m;
            getStats = data.getMethod("getStats");
            getStatus = data.getMethod("getStatus");
            getResources = data.getMethod("getResources");
            setCreated = getStatus.getReturnType().getMethod("setHasCreatedCharacter", boolean.class);
            addTp = getResources.getReturnType().getMethod("addTrainingPoints", float.class);
            getTp = getResources.getReturnType().getMethod("getTrainingPoints");
            Class<?> cm = Class.forName("com.dragonminez.common.config.ConfigManager");
            loadedRaces = cm.getMethod("getLoadedRaces");
            raceStats = cm.getMethod("getRaceStats", String.class);
            allClasses = raceStats.getReturnType().getMethod("getAllClasses");
            raceChar = cm.getMethod("getRaceCharacter", String.class);
            serverConfig = cm.getMethod("getServerConfig");
            gameplay = serverConfig.getReturnType().getMethod("getGameplay");
            passiveTp = gameplay.getReturnType().getMethod("getPassiveTpGain");
            tpRatio = gameplay.getReturnType().getMethod("getTpHealthRatio");
            tpPerHit = gameplay.getReturnType().getMethod("getTpPerHit");
            try { creditKill = Class.forName("com.dragonminez.server.events.QuestEvents").getMethod("creditQuestKill", ServerPlayer.class, LivingEntity.class); }
            catch (Throwable t) { XenoWarriors.LOGGER.warn("Bot kills won't count for DragonMineZ missions (its API changed?)", t); }
            getCharacter = data.getMethod("getCharacter");
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("Bots' DragonMineZ characters off (its API changed?)", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ 1: a race for every bot

    private static Object call(Object o, String m) {
        try { return o.getClass().getMethod(m).invoke(o); } catch (Throwable t) { return null; }
    }

    private static <T> T or(Object v, T def) {
        @SuppressWarnings("unchecked") T t = v == null ? def : (T) v;
        return t;
    }

    /** Has this saved DMZ character been made (a race picked)? */
    private static boolean made(CompoundTag tag) {
        return tag != null && tag.getCompound("Status").getBoolean("HasCreatedChar");
    }

    /**
     * Gives the bot a DragonMineZ character if it has none: a random loaded race and class with that race's default
     * look. Returns "race/class" when one was made, null when it already had one (or it can't be done).
     */
    public static String ensureRace(PartyBotEntity b) {
        if (!ready() || initialize == null) return null;
        ServerPlayer o = b.owner();
        if (o == null || o instanceof FakePlayer) return null;
        String key = DmzPerCharacter.botKey(b);
        if (DmzPerCharacter.isLive(o, key) || key.equals(DmzPerCharacter.viewing(o))) return null;
        CompoundTag tag = DmzPerCharacter.saved(o, key);
        if (made(tag)) return null;
        try {
            Player stand = PartyBots.standIn(b);
            if (stand == null) return null;
            Object sd = DmzPerCharacter.blank(stand);
            if (sd == null) return null;
            if (tag != null) DmzPerCharacter.loadData(sd, tag.copy());               // (keep anything it had)
            Object st = getStats.invoke(sd);
            for (String m : STAT_SETTERS) st.getClass().getMethod(m, int.class).invoke(st, 0);
            @SuppressWarnings("unchecked") List<String> races = new ArrayList<>((List<String>) loadedRaces.invoke(null));
            if (races.isEmpty()) return null;
            Random r = new Random(b.getRandom().nextLong());
            String race = races.get(r.nextInt(races.size()));
            List<String> classes = new ArrayList<>();
            Object rs = raceStats.invoke(null, race);
            if (rs != null) for (Object c : (Collection<?>) allClasses.invoke(rs)) classes.add(String.valueOf(c));
            String cls = classes.isEmpty() ? "warrior" : classes.get(r.nextInt(classes.size()));
            Object rc = raceChar.invoke(null, race);
            Object ch = getCharacter.invoke(sd);
            boolean hasGender = rc != null && Boolean.TRUE.equals(call(rc, "getHasGender"));
            String gender = hasGender && GIRLS.contains(b.character()) ? "female" : "male";
            ch.getClass().getMethod("setRace", String.class).invoke(ch, race);
            String[] bones = rc == null ? null : (String[]) call(rc, "getHeadBones");
            Object[] args = {
                    race, cls, gender,
                    or(rc == null ? null : call(rc, "getDefaultHairType"), or(call(ch, "getHairId"), 0)),
                    call(ch, "getHairBase"),
                    or(rc == null ? null : call(rc, "getDefaultBodyType"), or(call(ch, "getBodyType"), 0)),
                    or(rc == null ? null : call(rc, "getDefaultEyesType"), or(call(ch, "getEyesType"), 0)),
                    or(rc == null ? null : call(rc, "getDefaultNoseType"), or(call(ch, "getNoseType"), 0)),
                    or(rc == null ? null : call(rc, "getDefaultMouthType"), or(call(ch, "getMouthType"), 0)),
                    or(rc == null ? null : call(rc, "getDefaultTattooType"), or(call(ch, "getTattooType"), 0)),
                    or(call(ch, "getBoobScale"), 0F),
                    bones != null && bones.length > 0 && bones[0] != null ? bones[0] : or(call(ch, "getActiveHeadBone"), ""),
                    or(rc == null ? null : call(rc, "getDefaultHairColor"), or(call(ch, "getHairColor"), "")),
                    or(rc == null ? null : call(rc, "getDefaultBodyColor"), or(call(ch, "getBodyColor"), "")),
                    or(rc == null ? null : call(rc, "getDefaultBodyColor2"), or(call(ch, "getBodyColor2"), "")),
                    or(rc == null ? null : call(rc, "getDefaultBodyColor3"), or(call(ch, "getBodyColor3"), "")),
                    or(rc == null ? null : call(rc, "getDefaultEye1Color"), or(call(ch, "getEye1Color"), "")),
                    or(rc == null ? null : call(rc, "getDefaultEye2Color"), or(call(ch, "getEye2Color"), "")),
                    or(rc == null ? null : call(rc, "getDefaultAuraColor"), or(call(ch, "getAuraColor"), ""))};
            initialize.invoke(sd, args);
            setCreated.invoke(getStatus.invoke(sd), true);
            try {                                                                   // (like DragonMineZ's own creation: the first form picked)
                Class<?> th = Class.forName("com.dragonminez.common.util.TransformationsHelper");
                Class<?> sdc = sd.getClass();
                ch.getClass().getMethod("setSelectedFormGroup", String.class).invoke(ch, th.getMethod("getGroupWithFirstAvailableForm", sdc).invoke(null, sd));
                ch.getClass().getMethod("setSelectedForm", String.class).invoke(ch, th.getMethod("getFirstAvailableForm", sdc).invoke(null, sd));
                ch.getClass().getMethod("setSelectedStackFormGroup", String.class).invoke(ch, th.getMethod("getGroupWithFirstAvailableStackForm", sdc).invoke(null, sd));
                ch.getClass().getMethod("setSelectedStackForm", String.class).invoke(ch, th.getMethod("getFirstAvailableStackForm", sdc).invoke(null, sd));
            } catch (Throwable ignored) {}
            CompoundTag out = DmzPerCharacter.saveData(sd);
            if (out == null || !DmzPerCharacter.store(o, key, out)) return null;
            String picked = race + "/" + cls + "/" + gender;
            note("race picked for " + key + " (" + o.getGameProfile().getName() + "'s bot): " + picked + " level " + call(sd, "getLevel"));
            return picked;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't make a DragonMineZ character for a bot", t);
            return null;
        }
    }

    // ------------------------------------------------------------------ 2: TP for bots

    private static Number cfg(Method m) {
        try { return (Number) m.invoke(gameplay.invoke(serverConfig.invoke(null))); } catch (Throwable t) { return null; }
    }

    /** Adds TP to the bot's DMZ character (the open one if its menu is up, else the saved tag). Returns its TP after, or -1. */
    public static float giveTp(PartyBotEntity b, float tp) {
        if (!ready() || tp <= 0) return -1;
        ServerPlayer o = b.owner();
        if (o == null) return -1;
        String key = DmzPerCharacter.botKey(b);
        if (DmzPerCharacter.isLive(o, key)) return -1;
        try {
            if (key.equals(DmzPerCharacter.viewing(o))) {                         // (its menu is open: its data is in the owner's capability)
                Object res = getResources.invoke(DmzPerCharacter.live(o));
                addTp.invoke(res, tp);
                DmzPerCharacter.syncStats(o);
                return ((Number) getTp.invoke(res)).floatValue();
            }
            CompoundTag tag = DmzPerCharacter.saved(o, key);
            if (!made(tag)) return -1;
            CompoundTag res = tag.getCompound("Resources");
            float now = res.getFloat("TrainingPointsF") + tp;
            res.putFloat("TrainingPointsF", now);
            tag.put("Resources", res);
            return now;
        } catch (Throwable t) { return -1; }
    }

    /** The bot's DMZ character's TP right now (-1 = none). */
    public static float tp(PartyBotEntity b) {
        if (!ready()) return -1;
        ServerPlayer o = b.owner();
        if (o == null) return -1;
        String key = DmzPerCharacter.botKey(b);
        try {
            if (key.equals(DmzPerCharacter.viewing(o))) return ((Number) getTp.invoke(getResources.invoke(DmzPerCharacter.live(o)))).floatValue();
        } catch (Throwable t) { return -1; }
        CompoundTag tag = DmzPerCharacter.saved(o, key);
        return made(tag) ? tag.getCompound("Resources").getFloat("TrainingPointsF") : -1;
    }

    /** DragonMineZ's passive TP (every 5 s), or a modest 2 if its config turned it off. */
    public static int passive() {
        Number n = cfg(passiveTp);
        return n == null || n.intValue() <= 0 ? 2 : n.intValue();
    }

    /** TP for a kill, DragonMineZ's way: the victim's max health x its TP ratio, plus its TP per hit. */
    public static int killTp(LivingEntity dead) {
        Number ratio = cfg(tpRatio), hit = cfg(tpPerHit);
        return (int) Math.round(dead.getMaxHealth() * (ratio == null ? 0.5 : ratio.doubleValue())) + (hit == null ? 1 : hit.intValue());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || !ready()) return;
        if (p.tickCount % 20 == 3) DmzPerCharacter.checkView(p);
        if (p.tickCount % 100 != 11) return;
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.journey() || b.downed()) continue;
            ensureRace(b);
            giveTp(b, passive());
        }
    }

    // ------------------------------------------------------------------ 3: the bot's DMZ menu stays safe

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) DmzPerCharacter.endView(p, "logged out");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) DmzPerCharacter.endView(p, "changed dimension");
    }

    @SubscribeEvent
    public static void onStopping(net.minecraftforge.event.server.ServerStoppingEvent e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) DmzPerCharacter.endView(p, "server stopping");
    }

    /** No fighting with a bot's stats: being hit or attacking puts yours back first. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && DmzPerCharacter.viewing(p) != null) DmzPerCharacter.endView(p, "hit");
        if (e.getSource().getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && DmzPerCharacter.viewing(p) != null) DmzPerCharacter.endView(p, "attacked");
    }

    // ------------------------------------------------------------------ 2 + 4: a bot's kills

    /** The bot behind a kill (its own hit, its stand-in's, its projectile's), or null. */
    public static PartyBotEntity killerBot(Entity src) {
        Entity a = com.xenowarriors.combat.Allies.actor(src);
        return a instanceof PartyBotEntity b ? b : PartyBots.botOf(src);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeathFirst(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) DmzPerCharacter.endView(p, "died");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (e.isCanceled() || dead.level().isClientSide || !ready() || dead instanceof PartyBotEntity || dead instanceof Player) return;
        PartyBotEntity b = killerBot(e.getSource().getEntity());
        if (b == null) return;
        ServerPlayer o = b.owner();
        if (o == null || o instanceof FakePlayer) return;
        if (!b.journey()) {
            int tp = killTp(dead);
            float after = giveTp(b, tp);
            if (after >= 0) note("kill TP: " + DmzPerCharacter.botKey(b) + " +" + tp + " for " + dead.getType().getDescriptionId() + " -> " + after);
        }
        if (creditKill != null && DmzPerCharacter.viewing(o) == null) {
            try {
                creditKill.invoke(null, o, dead);
                note("quest kill credited to " + o.getGameProfile().getName() + " for " + net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(dead.getType())
                        + " killed by bot " + b.character().getId());
            } catch (Throwable t) { XenoWarriors.LOGGER.warn("Bot kill didn't count for DragonMineZ missions", t); creditKill = null; }
        }
    }
}

package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: you can fuse with a bot teammate (its menu's "Fuse" button) when the bot carries Potara earrings
 * in its bag (a Potara fusion) or its DragonMineZ character knows the Fusion Dance skill (a Metamoru fusion).
 * <p>
 * DragonMineZ's fusion is between two real players (the partner becomes a spectator riding the leader). A bot isn't
 * one, so this drives DragonMineZ's own fused state on you alone: its fused flags, its fusion name, its mixed look
 * (its own {@code mixAppearance}), its fusion stat bonus (its own {@code calculateAndApplyStats}, the bot's DragonMineZ
 * character as the partner - Potara x1.75-2.5, Metamoru x1.25-2.0 of the partner's stats), its Fused effect and sound,
 * lasting its configured fusion time. Your "partner" in its data is you yourself, so its own fusion tick (which ends a
 * fusion whose partner is offline, dead or far away) leaves it running, and this ends it - restoring your look and
 * stats, adding DragonMineZ's fusion cooldown, and bringing the bot back. While fused the bot is put away (its whole
 * record - things, armour, level - like a Journey's stashed bots), kept in your saved data so a crash or logout never
 * loses it. A Potara fusion that runs its full time breaks the earrings (DragonMineZ's rule); ended early (you die or
 * log out) they go back in the bot's bag. All by reflection; nothing at all without DragonMineZ.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzFusion {
    private DmzFusion() {}

    private static final String KEY = "xw_dmz_fusion";
    public static final String POTARA = "POTHALA", DANCE = "METAMORU";
    /** The bond you need with a character to fuse with them; the higher it is, the stronger the fusion. */
    public static final int NEED_BOND = 30;
    /** Bond XP a second while fused (with that character). */
    public static final float FUSED_BOND_XP = 1F;

    /** How much harder a fusion hits (and how much less it takes) from the bond it was made with: x1.0 at 30, x1.5 at 100. */
    public static float bondPower(int bond) { return 1F + Math.max(0, Math.min(100, bond) - NEED_BOND) / (float) (100 - NEED_BOND) * 0.5F; }

    /** The bond this fusion was made with (0 if not fused with a bot). */
    public static int fusedBond(Player p) { return fused(p) ? persisted(p).getCompound(KEY).getInt("bond") : 0; }

    private static Boolean loaded;
    private static boolean broken;
    private static Method getStatus, getCharacter, getStats, getBonus, getCooldowns, getSkills, totalStats, skillLevel;
    private static Method setFused, setLeader, setPartner, setType, setName, setTimer, isFused, setOrigAppearance, getOrigAppearance, setPothala;
    private static Method saveAppearance, loadAppearance, removeBonuses, addCooldown;
    private static Method buildName, mixAppearance, applyStats, clearState;
    private static Method serverConfig, gameplay, durationSec, cooldownSec;
    private static Object fusedEffect, cdEffect, fusionSound;

    /** (For the tests) what happened, newest last. */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 40) LOG.remove(0);
        LOG.add(s);
        XenoWarriors.LOGGER.info("XW dmz fusion: {}", s);
    }

    private static Method priv(Class<?> c, String name, Class<?>... args) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    static boolean ready() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        if (!loaded || broken) return false;
        if (clearState != null) return true;
        try {
            Class<?> sd = Class.forName("com.dragonminez.common.stats.StatsData");
            getStatus = sd.getMethod("getStatus");
            getCharacter = sd.getMethod("getCharacter");
            getStats = sd.getMethod("getStats");
            getBonus = sd.getMethod("getBonusStats");
            getCooldowns = sd.getMethod("getCooldowns");
            getSkills = sd.getMethod("getSkills");
            totalStats = getStats.getReturnType().getMethod("getTotalStats");
            skillLevel = getSkills.getReturnType().getMethod("getSkillLevel", String.class);
            Class<?> st = getStatus.getReturnType();
            setFused = st.getMethod("setFused", boolean.class);
            setLeader = st.getMethod("setFusionLeader", boolean.class);
            setPartner = st.getMethod("setFusionPartnerUUID", UUID.class);
            setType = st.getMethod("setFusionType", String.class);
            setName = st.getMethod("setFusionName", String.class);
            setTimer = st.getMethod("setFusionTimer", int.class);
            isFused = st.getMethod("isFused");
            setOrigAppearance = st.getMethod("setOriginalAppearance", CompoundTag.class);
            getOrigAppearance = st.getMethod("getOriginalAppearance");
            try { setPothala = st.getMethod("setPothalaColor", String.class); } catch (Throwable ignored) {}
            Class<?> ch = getCharacter.getReturnType();
            saveAppearance = ch.getMethod("saveAppearance", CompoundTag.class);
            loadAppearance = ch.getMethod("loadAppearance", CompoundTag.class);
            removeBonuses = getBonus.getReturnType().getMethod("removeAllBonuses", String.class);
            addCooldown = getCooldowns.getReturnType().getMethod("addCooldown", String.class, int.class);
            Class<?> fl = Class.forName("com.dragonminez.server.util.FusionLogic");
            buildName = priv(fl, "buildFusionName", String.class, String.class, String.class);
            mixAppearance = priv(fl, "mixAppearance", sd, sd);
            applyStats = priv(fl, "calculateAndApplyStats", sd, sd, String.class, int.class, int.class);
            Class<?> cm = Class.forName("com.dragonminez.common.config.ConfigManager");
            serverConfig = cm.getMethod("getServerConfig");
            gameplay = serverConfig.getReturnType().getMethod("getGameplay");
            durationSec = gameplay.getReturnType().getMethod("getFusionDurationSeconds");
            cooldownSec = gameplay.getReturnType().getMethod("getFusionCooldownSeconds");
            Class<?> fx = Class.forName("com.dragonminez.common.init.MainEffects");
            fusedEffect = fx.getField("FUSED").get(null);
            cdEffect = fx.getField("FUSION_CD").get(null);
            try { fusionSound = Class.forName("com.dragonminez.common.init.MainSounds").getField("FUSION").get(null); } catch (Throwable ignored) {}
            clearState = priv(fl, "clearFusionState", sd);
            return true;
        } catch (Throwable t) {
            broken = true; clearState = null;
            XenoWarriors.LOGGER.warn("Fusing with bots is off (DragonMineZ's fusion API changed?)", t);
            return false;
        }
    }

    private static MobEffect effect(Object ro) {
        try { return (MobEffect) ((RegistryObject<?>) ro).get(); } catch (Throwable t) { return null; }
    }

    private static int cfg(Method m, int def) {
        try { Object v = m.invoke(gameplay.invoke(serverConfig.invoke(null))); return v instanceof Number n && n.intValue() > 0 ? n.intValue() : def; } catch (Throwable t) { return def; }
    }

    /** DragonMineZ's fusion time, in ticks (5 minutes if its config doesn't say). */
    public static int durationTicks() { return ready() ? cfg(durationSec, 300) * 20 : 6000; }

    private static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }

    private static CompoundTag persisted(Player p) {
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, t);
        return t;
    }

    /** Fused with a bot right now (our record of it)? */
    public static boolean fused(Player p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(KEY); }

    /** Ticks left of the fusion, or -1. */
    public static long left(ServerPlayer p) {
        if (!fused(p)) return -1;
        return Math.max(0, p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY).getLong("until") - now(p));
    }

    // ---------------------------------------------------------------- can this bot fuse?

    private static int earringSlot(PartyBotEntity b) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (s.isEmpty()) continue;
            var id = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (id != null && id.getNamespace().equals("dragonminez") && id.getPath().contains("pothala")) return i;
        }
        return -1;
    }

    /** The bot's DMZ character in a throwaway StatsData (null if it has none). */
    private static Object botData(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null) return null;
        CompoundTag tag = DmzPerCharacter.saved(o, DmzPerCharacter.botKey(b));
        if (tag == null || !tag.getCompound("Status").getBoolean("HasCreatedChar")) return null;
        Player stand = PartyBots.standIn(b);
        Object d = stand == null ? null : DmzPerCharacter.blank(stand);
        if (d == null || !DmzPerCharacter.loadData(d, tag.copy())) return null;
        return d;
    }

    /** Which fusion this bot could do with you: POTARA (earrings in its bag), DANCE (its character knows the dance), or null. */
    public static String kind(PartyBotEntity b) {
        if (!ready() || b == null) return null;
        if (earringSlot(b) >= 0) return POTARA;
        Object d = botData(b);
        try { if (d != null && ((Integer) skillLevel.invoke(getSkills.invoke(d), "fusion")) > 0) return DANCE; } catch (Throwable ignored) {}
        return null;
    }

    /** Why you can't fuse with it right now, or null if you can. */
    public static String whyNot(ServerPlayer p, PartyBotEntity b) {
        if (!ready()) return "Fusion needs DragonMineZ";
        if (p instanceof FakePlayer || b == null || !b.isAlive()) return "They're not here";
        if (b.journey() || b.downed() || b.sparring()) return "Not right now";
        if (fused(p)) return "You're already fused";
        if (DmzPerCharacter.viewing(p) != null) return "Close their Dragon Ball menu first";
        if (b.distanceTo(p) > 8) return "Stand closer to them";
        Object me = DmzPerCharacter.live(p);
        if (me == null || !DmzPerCharacter.createdData(me)) return "Make your Dragon Ball character first";
        try { if ((Boolean) isFused.invoke(getStatus.invoke(me))) return "You're already fused"; } catch (Throwable ignored) {}
        MobEffect cd = effect(cdEffect);
        if (cd != null && p.hasEffect(cd)) return "You can't fuse again yet (fusion cooldown)";
        if (botData(b) == null) return "They don't have a Dragon Ball character yet";
        if (kind(b) == null) return "They need Potara earrings in their bag, or the Fusion Dance skill";
        int bond = com.xenowarriors.party.Bonds.level(p, b);
        if (bond < NEED_BOND) return "Your bond with " + b.character().getDisplayName().getString() + " is too low to fuse (" + bond + "/" + NEED_BOND + ")";
        return null;
    }

    // ---------------------------------------------------------------- fusing

    /** Fuse the owner with this bot. True when it happened. */
    public static boolean fuse(ServerPlayer p, PartyBotEntity b) {
        String why = whyNot(p, b);
        if (why != null) {
            p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GRAY), true);
            note("refused " + (b == null ? "-" : b.character().getId()) + ": " + why);
            return false;
        }
        String type = kind(b);
        Object me = DmzPerCharacter.live(p), them = botData(b);
        try {
            Object status = getStatus.invoke(me);
            int mine = (Integer) totalStats.invoke(getStats.invoke(me)), theirs = (Integer) totalStats.invoke(getStats.invoke(them));
            ItemStack earring = ItemStack.EMPTY;
            if (POTARA.equals(type)) {
                int slot = earringSlot(b);
                earring = b.inventory.removeItem(slot, 1);
                String color = String.valueOf(ForgeRegistries.ITEMS.getKey(earring.getItem())).contains("green") ? "green" : "yellow";
                if (setPothala != null) setPothala.invoke(status, color);
            }
            // DragonMineZ's applyFusion, for the leader only (the partner is the bot's character, not a player)
            CompoundTag look = new CompoundTag();
            saveAppearance.invoke(getCharacter.invoke(me), look);
            setOrigAppearance.invoke(status, look);
            setFused.invoke(status, true);
            setLeader.invoke(status, true);
            setPartner.invoke(status, p.getUUID());                                 // (itself: its fusion tick finds the partner online, alive and right here)
            setType.invoke(status, type);
            String botName = b.character().getDisplayName().getString();
            String name = (String) buildName.invoke(null, p.getGameProfile().getName(), botName, type);
            setName.invoke(status, name);
            mixAppearance.invoke(null, me, them);
            removeBonuses.invoke(getBonus.invoke(me), "FusionBonus");                // (a stale one left by a crash would stack on top)
            applyStats.invoke(null, me, them, type, mine, theirs);                   // DragonMineZ's own player + player maths, the bot as the partner
            int dur = durationTicks();
            setTimer.invoke(status, dur + 1200);                                     // (we end it; its own timer never gets there)
            MobEffect fusedFx = effect(fusedEffect);
            if (fusedFx != null) p.addEffect(new MobEffectInstance(fusedFx, dur, 0, false, false));
            // the bot is put away (its record kept in yours) for the fusion
            CompoundTag rec = new CompoundTag();
            rec.putString("type", type);
            rec.putString("char", b.character().getId());
            rec.put("bot", b.record());
            rec.putLong("until", now(p) + dur);
            rec.putLong("start", now(p));
            rec.putString("name", name);
            int bond = com.xenowarriors.party.Bonds.level(p, b);
            rec.putInt("bond", bond);
            if (!earring.isEmpty()) rec.put("earring", earring.save(new CompoundTag()));
            persisted(p).put(KEY, rec);
            var lvl = p.serverLevel();
            lvl.sendParticles(ParticleTypes.FLASH, b.getX(), b.getY() + 1, b.getZ(), 1, 0, 0, 0, 0);
            lvl.sendParticles(ParticleTypes.END_ROD, (b.getX() + p.getX()) / 2, p.getY() + 1, (b.getZ() + p.getZ()) / 2, 40, .6, .8, .6, .08);
            PartyBots.forgetFake(b.getUUID());
            b.discard();
            if (fusionSound instanceof RegistryObject<?> ro && ro.get() instanceof SoundEvent se) lvl.playSound(null, p.getX(), p.getY(), p.getZ(), se, SoundSource.PLAYERS, 1F, 1F);
            p.refreshDisplayName();
            p.refreshTabListName();
            DmzPerCharacter.syncAll(p);
            p.sendSystemMessage(Component.literal("You fused with " + botName + "! You are " + name + " ("
                    + (POTARA.equals(type) ? "Potara" : "Fusion Dance") + ", " + dur / 20 + " s; bond " + bond + ": x"
                    + String.format(Locale.ROOT, "%.2f", bondPower(bond)) + " strength).").withStyle(ChatFormatting.GOLD));
            lastCheck = checkStats(me, them, type, mine, theirs);
            note("fused " + p.getGameProfile().getName() + " + " + b.character().getId() + " = " + name + " type=" + type + " totals " + mine + "+" + theirs
                    + " for " + dur + " ticks; " + statsLine(me) + "; DMZ formula check: " + lastCheck);
            return true;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Fusing with a bot failed", t);
            note("FAILED " + t);
            return false;
        }
    }

    // ---------------------------------------------------------------- the stat check

    /** (Tests / logs) the last fusion's check against DragonMineZ's player + player formula. */
    public static String lastCheck = "";

    /**
     * DragonMineZ's own player + player fusion maths (FusionLogic.calculateAndApplyStats, 2.1.3), worked out here on
     * its own: ratio = min(totals) / max(totals); multiplier = low + ratio * (high - low) with Potara 1.75-2.5 and the
     * Fusion Dance 1.25-2.0; for every stat in the server's fusionBoosts list the leader gets the PARTNER's base stat x
     * multiplier as a "FusionBonus" (RES is split into DEF and STM). The partner here is the bot's own DragonMineZ
     * character. Returns "OK ..." when what's on the leader matches, "MISMATCH ..." otherwise.
     */
    public static String checkStats(Object leader, Object partner, String type, int mine, int theirs) {
        try {
            double ratio = Math.max(mine, theirs) <= 0 ? 0D : (double) Math.min(mine, theirs) / Math.max(mine, theirs);
            double lo = POTARA.equals(type) ? 1.75 : 1.25, hi = POTARA.equals(type) ? 2.5 : 2.0;
            double mult = lo + ratio * (hi - lo);
            String[] boosts = (String[]) gameplay.getReturnType().getMethod("getFusionBoosts").invoke(gameplay.invoke(serverConfig.invoke(null)));
            Object stats = getStats.invoke(partner), bonus = getBonus.invoke(leader);
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (String stat : boosts) {
                String getter = switch (stat) {
                    case "STR" -> "getStrength"; case "SKP" -> "getStrikePower"; case "RES" -> "getResistance";
                    case "VIT" -> "getVitality"; case "PWR" -> "getKiPower"; case "ENE" -> "getEnergy"; default -> null;
                };
                int base = getter == null ? 0 : (Integer) stats.getClass().getMethod(getter).invoke(stats);
                double want = base * mult, got = fusionBonus(bonus, "RES".equalsIgnoreCase(stat) ? "DEF" : stat);
                boolean same = Math.abs(want - got) < 0.01;
                ok &= same;
                sb.append(' ').append(stat).append('=').append(String.format(java.util.Locale.US, "%.2f/%.2f", want, got));
            }
            return (ok ? "OK" : "MISMATCH") + String.format(java.util.Locale.US, " x%.4f (totals %d,%d)", mult, mine, theirs) + sb;
        } catch (Throwable t) {
            return "check failed: " + t;
        }
    }

    /** The FusionBonus value DragonMineZ holds on this stat. */
    private static double fusionBonus(Object bonus, String stat) throws Exception {
        double sum = 0;
        Object list = bonus.getClass().getMethod("getBonuses", String.class).invoke(bonus, stat);
        if (list instanceof java.util.List<?> l)
            for (Object sb : l) {
                if ("FusionBonus".equals(sb.getClass().getField("name").get(sb))) sum += (Double) sb.getClass().getField("value").get(sb);
            }
        return sum;
    }

    /** (Logs) the player's DMZ fused state and stats. */
    public static String statsLine(Object data) {
        try {
            Object st = getStatus.invoke(data), s = getStats.invoke(data);
            return "fused=" + isFused.invoke(st) + " name=" + st.getClass().getMethod("getFusionName").invoke(st) + " type=" + st.getClass().getMethod("getFusionType").invoke(st)
                    + " STR=" + s.getClass().getMethod("getStrength").invoke(s) + " total=" + totalStats.invoke(s)
                    + " maxKi=" + data.getClass().getMethod("getMaxEnergy").invoke(data) + " kiDmg=" + data.getClass().getMethod("getKiDamage").invoke(data)
                    + " melee=" + data.getClass().getMethod("getMeleeDamage").invoke(data)
                    + " hair=" + getCharacter.invoke(data).getClass().getMethod("getHairColor").invoke(getCharacter.invoke(data));
        } catch (Throwable t) { return "? " + t; }
    }

    /**
     * DragonMineZ's side of ending it: your look and stats back, its fused flags cleared, its cooldown. Only touches a
     * fusion that is one of ours (fused with yourself as the "partner"), so it's safe to call on any DMZ data.
     */
    private static void undoDmz(ServerPlayer p, Object me) {
        if (me == null) return;
        try {
            Object status = getStatus.invoke(me);
            if (!ours(p, status)) return;
            removeBonuses.invoke(getBonus.invoke(me), "FusionBonus");
            Object look = getOrigAppearance.invoke(status);
            if (look instanceof CompoundTag t && !t.isEmpty()) loadAppearance.invoke(getCharacter.invoke(me), t);
            int cd = cfg(cooldownSec, 600) * 20;
            addCooldown.invoke(getCooldowns.invoke(me), "FusionCooldown", cd);
            clearState.invoke(null, me);
            MobEffect fx = effect(fusedEffect), cdFx = effect(cdEffect);
            if (fx != null) p.removeEffect(fx);
            if (cdFx != null && p.isAlive()) p.addEffect(new MobEffectInstance(cdFx, cd, 0, false, false, true));
            p.refreshDisplayName();
            p.refreshTabListName();
            DmzPerCharacter.syncAll(p);
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Ending a bot fusion didn't fully work", t); }
    }

    /** Is this DMZ status a bot fusion of ours (fused, with the player themself as the "partner")? */
    private static boolean ours(ServerPlayer p, Object status) {
        try {
            if (!(Boolean) isFused.invoke(status)) return false;
            return p.getUUID().equals(status.getClass().getMethod("getFusionPartnerUUID").invoke(status));
        } catch (Throwable t) { return false; }
    }

    /** The character id of the bot you're fused with right now (it's away, but still on your team), or null. */
    public static String fusedChar(Player p) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return keep.contains(KEY) ? keep.getCompound(KEY).getString("char") : null;
    }

    /** Ends the fusion: your look and stats back, DragonMineZ's cooldown, the bot back beside you. */
    public static void end(ServerPlayer p, String why, boolean ranOut) {
        CompoundTag keep = persisted(p);
        if (!keep.contains(KEY)) return;
        CompoundTag rec = keep.getCompound(KEY);
        keep.remove(KEY);
        Object me = ready() ? DmzPerCharacter.live(p) : null;
        undoDmz(p, me);
        CompoundTag bot = rec.getCompound("bot");
        ItemStack earring = rec.contains("earring") ? ItemStack.of(rec.getCompound("earring")) : ItemStack.EMPTY;
        if (com.xenowarriors.journey.Journey.inRun(p)) {                     // (no bots on a Journey: it waits at home with the others)
            PartyBots.stashRecord(p, bot);
            if (!earring.isEmpty() && !ranOut && !p.getInventory().add(earring)) p.drop(earring, false);
            p.sendSystemMessage(Component.literal("The fusion wore off (" + why + "). Your teammate waits for you at home.").withStyle(ChatFormatting.GOLD));
            note("ended (" + why + ") on a Journey: bot stashed");
            return;
        }
        PartyBotEntity b = PartyBots.spawn(p, CharacterType.byId(rec.getString("char")), bot);
        if (b != null) {
            if (!earring.isEmpty() && !ranOut) b.inventory.addItem(earring);            // (a full fusion breaks the earrings, as in DragonMineZ)
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, b.getX(), b.getY() + 1, b.getZ(), 30, .4, .8, .4, .06);
        }
        p.sendSystemMessage(Component.literal("The fusion wore off (" + why + ")." + (ranOut && !earring.isEmpty() ? " The Potara earrings shattered." : ""))
                .withStyle(ChatFormatting.GOLD));
        note("ended (" + why + ", ranOut=" + ranOut + ") bot back=" + (b != null) + (me != null ? "; " + statsLine(me) : ""));
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 10 != 0 || !fused(p)) return;
        long left = left(p);
        if (left <= 0) { end(p, "time's up", true); return; }
        if (p.tickCount % 20 == 0) {                                             // fused: your bond with them slowly grows
            String c = persisted(p).getCompound(KEY).getString("char");
            if (!c.isEmpty()) com.xenowarriors.party.Bonds.addXp(p, c, FUSED_BOND_XP);
        }
        if (com.xenowarriors.journey.Journey.inRun(p)) { end(p, "a Journey began", false); return; }   // (a Journey is you and your partner only)
        Object me = ready() ? DmzPerCharacter.live(p) : null;
        try {
            if (me == null || !(Boolean) isFused.invoke(getStatus.invoke(me))) { end(p, "the fusion was undone", false); return; }
            setTimer.invoke(getStatus.invoke(me), (int) left + 1200);
        } catch (Throwable ignored) {}
    }

    /** A fusion's bond strength: its hits land harder and it takes less, by the bond it was made with. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && p != e.getEntity() && fused(p)) e.setAmount(e.getAmount() * bondPower(fusedBond(p)));
        if (e.getEntity() instanceof ServerPlayer p && fused(p)) e.setAmount(e.getAmount() / bondPower(fusedBond(p)));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && fused(p) && !InventoryTotem.saves(p, e.getSource())) end(p, "you were knocked out", false);   // (an inventory totem saves them: not knocked out)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && fused(p)) end(p, "logged out", false);   // (before the bots are saved, so it's among them)
    }

    /**
     * DragonMineZ ends a fusion when your game mode changes by knocking out the fusion partner - and with a bot, that's
     * you. So a change of game mode ends it here first (it just wears off).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && fused(p) && e.getNewGameMode() != e.getCurrentGameMode())
            end(p, "your game mode changed", false);
    }

    /**
     * Logging in fused (the game closed without a clean logout): DragonMineZ's own login check would end the fusion by
     * knocking out the partner - you. Its fused state is undone before it looks; the bot comes back a moment later
     * (onLogin). Also clears a fused state of ours left in the DMZ data without our record.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoginFirst(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || !ready()) return;
        Object me = DmzPerCharacter.live(p);
        try { if (me == null || !ours(p, getStatus.invoke(me))) return; } catch (Throwable t) { return; }
        undoDmz(p, me);
        note("login: undid a bot fusion left in " + p.getGameProfile().getName() + "'s DragonMineZ data (record kept=" + fused(p) + ")");
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer) && fused(p))
            com.xenowarriors.skill.ServerScheduler.later(40, () -> { if (!p.hasDisconnected()) end(p, "the world was closed", false); });   // (after a crash)
    }

    @SubscribeEvent
    public static void onStopping(net.minecraftforge.event.server.ServerStoppingEvent e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) if (fused(p)) end(p, "the world was closed", false);
    }
}

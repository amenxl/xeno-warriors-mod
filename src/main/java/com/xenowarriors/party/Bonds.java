package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bond levels (0-100) between the character you're playing and each teammate character (other players' characters and
 * your bots'), kept per pair: e.g. your Mikiko's bond with Rhalix. It grows by being together on a team - a little just
 * for being near each other, much faster while fighting side by side, with every kill one of you makes, and with every
 * revive.
 * <ul>
 *   <li>Synergies get stronger with the bond: x(1 + bond/100), so double at bond 100 (see {@link Synergy}).</li>
 *   <li>Their downsides fade with it, and are gone at bond {@link #NO_DOWNSIDE} (Soul Link's shared death).</li>
 *   <li>Every 10 bond levels of your highest bond with someone in your party: +{@link #HEARTS_PER_10} max hearts
 *       (+{@link #DMZ_HEARTS_PER_10} with DragonMineZ, whose health values are far bigger), so at most +50 hearts.</li>
 *   <li>Bots get the same: a bot's bond with each teammate is its character's own (kept in its owner's save, under that
 *       character - the same record the owner gets when they play it), or that teammate's bond with it if higher; its
 *       best bond gives it the same bonus hearts, on top of its level health and DragonMineZ's health bonus.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Bonds {
    public static final int MAX = 100, NO_DOWNSIDE = 50, HEARTS_PER_10 = 5, DMZ_HEARTS_PER_10 = 300;
    public static final float NEAR_XP = 0.25F, COMBAT_XP = 1.5F, KILL_XP = 6F, REVIVE_XP = 25F;
    private static final String KEY = "xw_bond";
    private static final UUID HEALTH_ID = UUID.fromString("6f3c1f0e-8a3b-4c51-9a1e-5b7d2a9c0e31");
    /** The same bonus on a bot (its own id, so it stacks with its level health and DmzBotStats' DragonMineZ health). */
    public static final UUID BOT_HEALTH_ID = UUID.fromString("6f3c1f0e-8a3b-4c51-9a1e-5b7d2a9c0e32");
    private static Boolean dmz;

    private Bonds() {}

    public static float needed(int level) { return 25F + level * 5F; }

    // ---------------------------------------------------------------- storage

    private static CompoundTag root(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(KEY)) { pers.put(KEY, new CompoundTag()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers); }
        return pers.getCompound(KEY);
    }

    private static String me(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(PlayerCharacter::key).orElse(ModCapabilities.getType(p).getId());
    }

    /** The character id that a teammate counts as. */
    public static String charOf(Entity e) {
        if (e instanceof ServerPlayer p) return ModCapabilities.getType(p).getId();
        if (e instanceof PartyBotEntity b) return b.charKey();
        return "none";
    }

    /** Your current character's bond with this teammate character (0-100). */
    public static int level(Player p, String partnerChar) {
        if (partnerChar == null || partnerChar.equals("none")) return 0;
        return Math.max(0, Math.min(MAX, root(p).getCompound(me(p)).getCompound(partnerChar).getInt("lv")));
    }

    public static float progress(Player p, String partnerChar) {
        int lv = level(p, partnerChar);
        return lv >= MAX ? 1F : Math.min(1F, root(p).getCompound(me(p)).getCompound(partnerChar).getFloat("xp") / needed(lv));
    }

    public static int level(Player p, Entity partner) { return level(p, charOf(partner)); }

    public static void set(ServerPlayer p, String partnerChar, int level, float xp) {
        CompoundTag r = root(p);
        CompoundTag mine = r.getCompound(me(p));
        CompoundTag t = new CompoundTag();
        t.putInt("lv", Math.max(0, Math.min(MAX, level)));
        t.putFloat("xp", Math.max(0F, xp));
        mine.put(partnerChar, t);
        r.put(me(p), mine);
        applyHealth(p);
        applyTeamBots(p);
    }

    /** Bond XP with a teammate character (both ways happen separately: each player keeps their own side). */
    public static void addXp(ServerPlayer p, String partnerChar, float amount) {
        if (amount <= 0 || partnerChar == null || partnerChar.equals("none") || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        int before = level(p, partnerChar);
        int lv = grow(p, me(p), partnerChar, amount);
        if (lv > before) {
            String who = com.xenowarriors.character.CharacterType.byId(partnerChar).getDisplayName().getString();
            boolean tens = lv / 10 > before / 10;
            p.displayClientMessage(Component.literal("Bond with " + who + ": " + lv + (tens ? "  - synergy stronger, more max hearts (you and your bots)" : "")
                    + (before < NO_DOWNSIDE && lv >= NO_DOWNSIDE ? "  - synergy downsides gone!" : "")).withStyle(ChatFormatting.LIGHT_PURPLE), !tens);
            if (tens) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 1.2F);
                com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), ModCapabilities.getType(p).ordinal(), -1,
                        "Bond " + lv, "You and " + who + " fight better together", "normal");
            }
            applyHealth(p);
            if (tens) applyTeamBots(p);
        }
    }

    /** Adds bond XP to one record ({@code charKey}'s bond with {@code partnerChar}) in this player's save. The new level. */
    private static int grow(ServerPlayer p, String charKey, String partnerChar, float amount) {
        CompoundTag r = root(p);
        CompoundTag mine = r.getCompound(charKey);
        CompoundTag old = mine.getCompound(partnerChar);
        int lv = Math.max(0, Math.min(MAX, old.getInt("lv")));
        if (lv >= MAX) return lv;
        float xp = old.getFloat("xp") + amount;
        while (lv < MAX && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        if (lv >= MAX) xp = 0;
        CompoundTag t = new CompoundTag();
        t.putInt("lv", lv); t.putFloat("xp", xp);
        mine.put(partnerChar, t);
        r.put(charKey, mine);
        return lv;
    }

    // ---------------------------------------------------------------- bots: the same bonds, from their side

    /** The save key of a bot's character in its owner's save. */
    private static String botKey(PartyBotEntity b, ServerPlayer o) {
        return b.charKey();
    }

    /** The bot character's own bond with this teammate character (kept in the owner's save, under the bot's character). */
    public static int botOwn(PartyBotEntity b, String partnerChar) {
        ServerPlayer o = b.owner();
        if (o == null || partnerChar == null || partnerChar.equals("none")) return 0;
        return Math.max(0, Math.min(MAX, root(o).getCompound(botKey(b, o)).getCompound(partnerChar).getInt("lv")));
    }

    /** A bot's bond with a teammate: its own side, or the teammate's side (a player's bond with it) if that's higher. */
    public static int botLevel(PartyBotEntity b, Entity partner) {
        int lv = botOwn(b, charOf(partner));
        if (partner instanceof ServerPlayer m && !(m instanceof net.minecraftforge.common.util.FakePlayer)) lv = Math.max(lv, level(m, charOf(b)));
        return lv;
    }

    /** Everyone on a bot's team right now (its owner, the party players and all their bots), not the bot itself. */
    public static List<LivingEntity> team(PartyBotEntity b) {
        List<LivingEntity> out = new java.util.ArrayList<>();
        ServerPlayer o = b.owner();
        if (o == null) return out;
        for (ServerPlayer m : Parties.inParty(o) ? Parties.members(o) : List.of(o)) {
            out.add(m);
            for (PartyBotEntity x : PartyBots.bots(m)) if (x != b) out.add(x);
        }
        return out;
    }

    /** A bot's highest bond with anyone on its team now (this sets its bonus hearts, like {@link #best(ServerPlayer)}). */
    public static int best(PartyBotEntity b) {
        int best = 0;
        for (LivingEntity e : team(b)) best = Math.max(best, botLevel(b, e));
        return best;
    }

    /** (Tests / commands) sets the bot character's own bond with a teammate character. */
    public static void setBot(PartyBotEntity b, String partnerChar, int level, float xp) {
        ServerPlayer o = b.owner();
        if (o == null) return;
        CompoundTag r = root(o);
        CompoundTag mine = r.getCompound(botKey(b, o));
        CompoundTag t = new CompoundTag();
        t.putInt("lv", Math.max(0, Math.min(MAX, level)));
        t.putFloat("xp", Math.max(0F, xp));
        mine.put(partnerChar, t);
        r.put(botKey(b, o), mine);
        applyHealth(b);
    }

    /** Bond XP for the bot's side (quiet, but for every 10 levels, which its owner hears about). */
    public static void addXp(PartyBotEntity b, String partnerChar, float amount) {
        ServerPlayer o = b.owner();
        if (o == null || amount <= 0 || partnerChar == null || partnerChar.equals("none") || b.journey()) return;
        int before = botOwn(b, partnerChar);
        int lv = grow(o, botKey(b, o), partnerChar, amount);
        if (lv / 10 > before / 10) {
            o.displayClientMessage(Component.literal("").append(b.label()).append(Component.literal("'s bond with "
                    + com.xenowarriors.character.CharacterType.byId(partnerChar).getDisplayName().getString() + ": " + lv + "  - more max hearts")).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            applyHealth(b);
        }
    }

    // ---------------------------------------------------------------- what it gives

    /** How much a synergy's bonus is multiplied by at this bond (1x at 0, 2x at 100). */
    public static float strength(int bond) { return 1F + Math.max(0, Math.min(MAX, bond)) / (float) MAX; }

    /** How much of a synergy's downside is left at this bond (1 = all of it, 0 = gone at {@link #NO_DOWNSIDE}). */
    public static float downside(int bond) { return Math.max(0F, 1F - bond / (float) NO_DOWNSIDE); }

    public static boolean dmz() {
        if (dmz == null) dmz = net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("dragonminez");
        return dmz;
    }

    public static int heartsPer10() { return dmz() ? DMZ_HEARTS_PER_10 : HEARTS_PER_10; }

    /** Hearts from a bond level. */
    public static int hearts(int bond) { return Math.min(MAX, Math.max(0, bond)) / 10 * heartsPer10(); }

    /** Everyone on your team right now, wherever they are: party players and the bots of everyone in it. */
    public static List<LivingEntity> team(ServerPlayer p) {
        List<LivingEntity> out = new java.util.ArrayList<>();
        for (ServerPlayer m : Parties.inParty(p) ? Parties.members(p) : List.of(p)) {
            if (m != p) out.add(m);
            for (PartyBotEntity b : PartyBots.bots(m)) out.add(b);
        }
        return out;
    }

    /** Your highest bond with anyone on your team now (this sets the bonus hearts). */
    public static int best(ServerPlayer p) {
        int best = 0;
        for (LivingEntity e : team(p)) best = Math.max(best, level(p, e));
        return best;
    }


    public static void applyHealth(ServerPlayer p) {
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        int hearts = hearts(best(p));
        AttributeModifier cur = hp.getModifier(HEALTH_ID);
        if (cur != null ? Math.abs(cur.getAmount() - hearts * 2.0) < 1.0E-4 : hearts <= 0) return;   // (what's on the player itself: a stale note from another world or life never hides a wrong amount)
        hp.removeModifier(HEALTH_ID);
        if (hearts > 0) hp.addPermanentModifier(new AttributeModifier(HEALTH_ID, "Xeno Warriors bond", hearts * 2.0, AttributeModifier.Operation.ADDITION));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static final java.util.Map<UUID, Integer> BOT_APPLIED = new java.util.HashMap<>();

    /**
     * A bot's bond hearts: a modifier of its own on its max health, so it adds to its level health (PartyBotEntity
     * .applyLevel sets the base) and DragonMineZ's health bonus (DmzBotStats, another modifier) without either
     * replacing it. Applied on spawn (before a saved record's health goes back in, so a re-added or relogged bot keeps
     * its health), on every bond change that matters, and every 5 s.
     */
    public static void applyHealth(PartyBotEntity b) {
        if (b.level().isClientSide) return;
        AttributeInstance hp = b.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        int best = best(b), hearts = hearts(best);
        AttributeModifier cur = hp.getModifier(BOT_HEALTH_ID);
        if (cur != null ? Math.abs(cur.getAmount() - hearts * 2.0) < 1.0E-4 : hearts <= 0) return;
        float before = b.getMaxHealth();
        hp.removeModifier(BOT_HEALTH_ID);
        if (hearts > 0) hp.addTransientModifier(new AttributeModifier(BOT_HEALTH_ID, "Xeno Warriors bond", hearts * 2.0, AttributeModifier.Operation.ADDITION));
        b.maxHealthChanged(before);                                            // (in range; a relogged bot gets its saved health back; a fresh one starts full)
        Integer was = BOT_APPLIED.put(b.getUUID(), hearts);
        if (was == null || was != hearts)
            XenoWarriors.LOGGER.info("XW bond hearts: {} bot best bond {} -> +{} hearts, maxHp {} -> {}", b.charKey(), best, hearts, Math.round(before), Math.round(b.getMaxHealth()));
    }

    /** Every bot on this player's team (their bonds with this player's character may have changed). */
    public static void applyTeamBots(ServerPlayer p) {
        for (ServerPlayer m : Parties.inParty(p) ? Parties.members(p) : List.of(p))
            for (PartyBotEntity b : PartyBots.bots(m)) applyHealth(b);
    }

    public static void forget(UUID bot) { BOT_APPLIED.remove(bot); }

    // ---------------------------------------------------------------- what grows it

    private static boolean fighting(LivingEntity e, int now) {
        if (e instanceof PartyBotEntity b && b.getTarget() != null && b.getTarget().isAlive()) return true;
        return now - e.getLastHurtByMobTimestamp() < 100 || now - e.getLastHurtMobTimestamp() < 100;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0 || p.isSpectator()) return;
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        if (p.tickCount % 100 == 0) { applyHealth(p); for (PartyBotEntity b : PartyBots.bots(p)) applyHealth(b); }
        if (!p.isAlive() || com.xenowarriors.vr.Vr.inVr(p)) return;
        boolean fight = fighting(p, p.tickCount);
        for (LivingEntity e : Synergy.partners(p)) {
            boolean together = fight || fighting(e, e.tickCount);
            addXp(p, charOf(e), together ? COMBAT_XP : NEAR_XP);
        }
        for (PartyBotEntity b : PartyBots.bots(p)) {                            // (its bots grow their own side the same way)
            if (b.downed() || b.journey()) continue;
            boolean bf = fighting(b, b.tickCount);
            for (LivingEntity e : Synergy.partners(b)) addXp(b, charOf(e), bf || fighting(e, e.tickCount) ? COMBAT_XP : NEAR_XP);
        }
    }

    /** A kill by you or a teammate near you: bond with everyone fighting there. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || dead instanceof Player || dead instanceof PartyBotEntity) return;
        Entity killer = e.getSource().getEntity();
        if (killer instanceof net.minecraftforge.common.util.FakePlayer fp) { PartyBotEntity b = PartyBots.botOf(fp); if (b != null) killer = b; }
        if (!(killer instanceof LivingEntity k)) return;
        if (!(dead instanceof net.minecraft.world.entity.monster.Enemy) && !(dead instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null)) return;
        Set<UUID> done = new HashSet<>();
        for (ServerPlayer p : ((net.minecraft.server.level.ServerLevel) dead.level()).players()) {
            if (p.distanceToSqr(dead) > 32 * 32 || !done.add(p.getUUID())) continue;
            List<LivingEntity> ps = Synergy.partners(p);
            if (p != k && !ps.contains(k)) continue;
            for (LivingEntity o : ps) addXp(p, charOf(o), KILL_XP);
            for (PartyBotEntity b : PartyBots.bots(p)) {                         // (its bots: the same, their own side)
                if (b.downed() || b.level() != dead.level() || b.distanceToSqr(dead) > 32 * 32) continue;
                List<LivingEntity> bs = Synergy.partners(b);
                if (b != k && !bs.contains(k)) continue;
                for (LivingEntity o : bs) addXp(b, charOf(o), KILL_XP);
            }
        }
    }

    /** Someone got back up: the reviver and the revived bond a lot. */
    public static void revived(LivingEntity downed, LivingEntity helper) {
        if (downed == null || helper == null) return;
        if (downed instanceof ServerPlayer d) addXp(d, charOf(helper), REVIVE_XP);
        if (helper instanceof ServerPlayer h) addXp(h, charOf(downed), REVIVE_XP);
        if (downed instanceof PartyBotEntity b && b.owner() != null && b.owner() != helper) addXp(b.owner(), charOf(b), REVIVE_XP / 2);
        if (downed instanceof PartyBotEntity b) addXp(b, charOf(helper), REVIVE_XP);           // (bots keep their own side too)
        if (helper instanceof PartyBotEntity b) addXp(b, charOf(downed), REVIVE_XP);
    }
}

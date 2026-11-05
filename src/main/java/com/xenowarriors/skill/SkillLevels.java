package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.SkillLevelsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Ability levels: every skill (and ultimate) levels up with use, separately for each character you play.
 * <ul>
 *   <li>Level 1-10 inside a tier. Each level past 1 takes 3% off the skill's cooldown and Karma cost (-27% at level 10).</li>
 *   <li>At level 10 the skill can be upgraded to the next tier (Character Customization, Skills tab): back to level 1,
 *       but the tier hits much harder ({@link #DAMAGE_PER_TIER}x per tier) and costs more Karma ({@link #COST_PER_TIER}x)
 *       with a longer cooldown ({@link #COOLDOWN_PER_TIER}x). Levelling that tier to 10 brings cost and cooldown down again.</li>
 *   <li>10 tiers; tier 10 level 10 is the top (overall ability level 100).</li>
 * </ul>
 * Party bots use their owner's levels for that character. Stored in the player's persisted data, so it survives death.
 * The damage multiplier is applied to anything a skill hurts while it runs (combat/SkillContext carries the skill id).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkillLevels {
    public static final int MAX_LEVEL = 10, MAX_TIER = 10;
    public static final float LEVEL_REDUCTION = 0.03F, COST_PER_TIER = 1.12F, COOLDOWN_PER_TIER = 1.10F, DAMAGE_PER_TIER = 1.30F;
    /** No skill costs more than this after tier scaling (unless its base cost already does) - so a tier 10 skill stays usable. */
    public static final int COST_CAP = 90;
    private static final String KEY = "xw_skill_lv";

    private SkillLevels() {}

    /** Uses needed to go from {@code level} to the next one in a tier. */
    public static int needed(int tier, int level) { return 3 + level + tier; }

    // ---------------------------------------------------------------- the multipliers (pure; client and server)

    public static float levelFactor(int level) { return 1F - LEVEL_REDUCTION * (Math.max(1, Math.min(MAX_LEVEL, level)) - 1); }
    public static float costMult(int tier, int level) { return (float) Math.pow(COST_PER_TIER, Math.max(0, tier - 1)) * levelFactor(level); }
    public static float cooldownMult(int tier, int level) { return (float) Math.pow(COOLDOWN_PER_TIER, Math.max(0, tier - 1)) * levelFactor(level); }
    public static float damageMult(int tier) { return (float) Math.pow(DAMAGE_PER_TIER, Math.max(0, tier - 1)); }

    public static int cost(int base, int tier, int level) {
        if (base <= 0) return base;
        int c = Math.max(1, Math.round(base * costMult(tier, level)));
        return Math.min(c, Math.max(base, COST_CAP));
    }

    public static int cooldown(int base, int tier, int level) {
        if (base <= 0) return base;
        return Math.max(1, Math.round(base * cooldownMult(tier, level)));
    }

    // ---------------------------------------------------------------- storage

    private static CompoundTag root(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(KEY)) { pers.put(KEY, new CompoundTag()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers); }
        return pers.getCompound(KEY);
    }

    private static String charKey(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(PlayerCharacter::key).orElse(ModCapabilities.getType(p).getId());
    }

    private static String charKey(Player p, CharacterType t) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key(t)).orElse(t.getId());
    }

    /** {tier, level, uses} for one skill of one character (defaults to tier 1, level 1). */
    private static int[] get(Player p, String charKey, String skillId) {
        int[] v = root(p).getCompound(charKey).getIntArray(skillId);
        if (v.length != 3) return new int[]{1, 1, 0};
        return new int[]{Math.max(1, Math.min(MAX_TIER, v[0])), Math.max(1, Math.min(MAX_LEVEL, v[1])), Math.max(0, v[2])};
    }

    private static void put(Player p, String charKey, String skillId, int[] v) {
        CompoundTag r = root(p);
        CompoundTag c = r.getCompound(charKey);
        c.putIntArray(skillId, v.clone());
        r.put(charKey, c);
    }

    /** A character is gone for good (a deleted You): its ability levels go too. */
    public static void forget(Player p, String charKey) { root(p).remove(charKey); }

    /** (tests) sets a skill's tier and level for the character this player is playing. */
    public static void setForTest(Player p, String skillId, int tier, int level) { put(p, charKey(p), skillId, new int[]{tier, level}); }

    // ---------------------------------------------------------------- accessors (for other systems)

    /** Tier 1-10 of a skill for the character this player is playing. */
    public static int tier(Player p, String skillId) { return get(p, charKey(p), skillId)[0]; }
    /** Level 1-10 inside its tier. */
    public static int level(Player p, String skillId) { return get(p, charKey(p), skillId)[1]; }
    /** Overall ability level, 1-100: (tier - 1) * 10 + level. */
    public static int abilityLevel(ServerPlayer p, String skillId) { int[] v = get(p, charKey(p), skillId); return (v[0] - 1) * MAX_LEVEL + v[1]; }
    /** The same for one of their characters (for a bot of that character). */
    public static int abilityLevel(ServerPlayer owner, CharacterType t, String skillId) { int[] v = get(owner, charKey(owner, t), skillId); return (v[0] - 1) * MAX_LEVEL + v[1]; }
    public static int tier(ServerPlayer owner, CharacterType t, String skillId) { return get(owner, charKey(owner, t), skillId)[0]; }
    public static int level(ServerPlayer owner, CharacterType t, String skillId) { return get(owner, charKey(owner, t), skillId)[1]; }

    /** {tier, level} that apply to this caster: a player's own, or a bot's owner's for the bot's character. */
    public static int[] of(Entity caster, String skillId) {
        if (caster == null || skillId == null) return new int[]{1, 1};
        if (caster instanceof net.minecraftforge.common.util.FakePlayer fp) {
            PartyBotEntity b = com.xenowarriors.bot.PartyBots.botOf(fp);
            return b == null ? new int[]{1, 1} : of(b, skillId);
        }
        if (caster instanceof PartyBotEntity b) {
            ServerPlayer o = b.owner();
            if (o == null) return new int[]{1, 1};
            int[] v = get(o, charKey(o, b.character()), skillId);
            return new int[]{v[0], v[1]};
        }
        if (caster instanceof ServerPlayer p) { int[] v = get(p, charKey(p), skillId); return new int[]{v[0], v[1]}; }
        return new int[]{1, 1};
    }

    /** What this skill costs this player right now (from its base cost). */
    public static int costFor(Entity caster, String skillId, int base) { int[] v = of(caster, skillId); return cost(base, v[0], v[1]); }
    public static int cooldownFor(Entity caster, String skillId, int base) { int[] v = of(caster, skillId); return cooldown(base, v[0], v[1]); }

    // ---------------------------------------------------------------- levelling

    /** A player used a skill (it went off): one use toward its next level. */
    public static void used(ServerPlayer p, Skill skill) {
        if (p == null || skill == null || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        String ck = charKey(p), id = skill.getId();
        int[] v = get(p, ck, id);
        if (v[1] >= MAX_LEVEL) return;                                   // (waiting for the tier upgrade, or maxed)
        v[2]++;
        if (v[2] >= needed(v[0], v[1])) {
            v[2] = 0;
            v[1]++;
            String name = skill.getName().getString();
            if (v[1] >= MAX_LEVEL && v[0] < MAX_TIER) {
                p.sendSystemMessage(Component.literal(name + " reached level 10! Upgrade it to Tier " + (v[0] + 1) + " in Character Customization (Skills tab).").withStyle(ChatFormatting.GOLD));
                com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), ModCapabilities.getType(p).ordinal(), -1,
                        "Tier upgrade ready", name + " can go to Tier " + (v[0] + 1), "normal");
            } else {
                p.displayClientMessage(Component.literal(name + " - Tier " + v[0] + " Lv " + v[1] + "  (-" + Math.round((1F - levelFactor(v[1])) * 100) + "% cost & cooldown)").withStyle(ChatFormatting.AQUA), true);
            }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.3F);
        }
        put(p, ck, id, v);
        sync(p);
    }

    /** Upgrade a level-10 skill to its next tier (from the Character Customization skill card). */
    public static boolean upgrade(ServerPlayer p, String skillId) {
        if (!known(p, skillId)) return false;
        String ck = charKey(p);
        int[] v = get(p, ck, skillId);
        if (v[1] < MAX_LEVEL || v[0] >= MAX_TIER) {
            p.displayClientMessage(Component.literal(v[0] >= MAX_TIER ? "Already at the top tier." : "Reach level 10 first (" + v[1] + "/10).").withStyle(ChatFormatting.RED), true);
            sync(p);
            return false;
        }
        v[0]++; v[1] = 1; v[2] = 0;
        put(p, ck, skillId, v);
        p.sendSystemMessage(Component.literal("Upgraded to Tier " + v[0] + ": x" + String.format("%.2f", damageMult(v[0])) + " damage, x"
                + String.format("%.2f", costMult(v[0], 1)) + " Karma, x" + String.format("%.2f", cooldownMult(v[0], 1)) + " cooldown.").withStyle(ChatFormatting.GOLD));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.8F);
        sync(p);
        return true;
    }

    /** The skill is one of this character's (pool skills or ultimates) - a client can't upgrade random ids. */
    private static boolean known(ServerPlayer p, String id) {
        for (SkillPool.Entry e : SkillPool.of(p)) if (e.skill().getId().equals(id)) return true;
        for (SkillPool.Entry e : SkillPool.ultimates(p)) if (e.skill().getId().equals(id)) return true;
        return false;
    }

    /** (tests / commands) set a skill's tier and level directly. */
    public static void set(ServerPlayer p, String skillId, int tier, int level, int uses) {
        put(p, charKey(p), skillId, new int[]{Math.max(1, Math.min(MAX_TIER, tier)), Math.max(1, Math.min(MAX_LEVEL, level)), Math.max(0, uses)});
        sync(p);
    }

    /** Everything stored for the current character, to the client. */
    public static void sync(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer || p.connection == null) return;
        CompoundTag c = root(p).getCompound(charKey(p));
        List<SkillLevelsPacket.Entry> l = new ArrayList<>();
        for (String id : c.getAllKeys()) {
            int[] v = get(p, charKey(p), id);
            l.add(new SkillLevelsPacket.Entry(id, v[0], v[1], v[2]));
        }
        ModNetwork.sendToPlayer(new SkillLevelsPacket("", l), p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }

    /** Skill damage: the tier's multiplier on whatever the running skill hurts. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!com.xenowarriors.combat.SkillContext.active()) return;
        String id = com.xenowarriors.combat.SkillContext.skill();
        if (id == null) return;
        Entity caster = com.xenowarriors.combat.SkillContext.caster();
        if (caster == event.getEntity()) return;
        int[] v = of(caster, id);
        if (v[0] <= 1) return;
        event.setAmount(event.getAmount() * damageMult(v[0]));
    }
}

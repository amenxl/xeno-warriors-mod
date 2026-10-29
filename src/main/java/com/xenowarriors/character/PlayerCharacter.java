package com.xenowarriors.character;

import com.xenowarriors.karma.Karma;
import net.minecraft.nbt.CompoundTag;

/** Per-player data: which character they picked, their Karma, and skill cooldowns. */
public class PlayerCharacter {
    private CharacterType type = CharacterType.NONE;
    /** Game time when each skill slot's cooldown ends (not saved; resets on relog). */
    public final long[] cooldownEnd = new long[6];
    /** Karma - the energy every skill runs on. Starts full. */
    private float karma = Karma.MAX;
    /** Level-up stats (see stats/Stats): level and progress toward the next level for each. */
    public final int[] statLevel = new int[3];
    public final float[] statXp = new float[3];
    /** Mikiko's foxes: one compound per fox {uuid, name, level, xp, variant, gen, dead}. */
    public net.minecraft.nbt.ListTag pets = new net.minecraft.nbt.ListTag();
    /** Rhalix's winged companions: {uuid, name, type, gen, dead, data}. */
    public net.minecraft.nbt.ListTag wingPets = new net.minecraft.nbt.ListTag();
    /** Rhalix: are his wings out (true) or put away (false)? */
    public boolean wingsOut = true;
    /** Mikiko's outfit: 0 Recarnet 1 (normal), 1 Origins 1, 2 Origins 2. */
    public int outfit;
    /** How torn their clothes are from explosions (0-3); mends when healed up and resting. */
    public int torn;
    /** The fox riding on his back (saved entity data), or empty. */
    public CompoundTag backFox = new CompoundTag();
    /** Total ticks Mikiko has spent flying (his flying gets better with practice). */
    public int flightTicks;
    /** How many times they've used Karma Sensing (range and duration grow with practice). */
    public int senseUses;
    /** Everything Mikiko has sealed inside the Hekublade: {id, type, name, data, time}. */
    public net.minecraft.nbt.ListTag sealed = new net.minecraft.nbt.ListTag();
    /** Where to send him back to when he leaves the Hekublade realm {dim, x, y, z, yaw}. */
    public CompoundTag bladeReturn = new CompoundTag();
    public int nextSealId;
    /** If this player is sealed inside a Mikiko's Hekublade: whose, and where to put them back. */
    public java.util.UUID prisonerOf;
    /** Mining skill: level (0-50) and progress, from breaking blocks. */
    public int miningLevel;
    /** The Hekublade's own level (1-50) and progress; higher = longer stays inside it. */
    public int bladeLevel = 1;
    public float bladeXp;
    /** Karma Altar upgrades on the Hekublade: Sky Edge and Dark Edge, 0-3 each. */
    public int bladeSky, bladeDark;
    /** Ticks of time left inside the blade (refills while outside), and when he may go back in after being pushed out. */
    public int realmTicks = -1;
    public long realmCooldownUntil;
    public float miningXp;
    /** Crafting and smelting skills (0-50 each). */
    public int craftLevel, smeltLevel;
    public float craftXp, smeltXp;
    public CompoundTag prisonReturn = new CompoundTag();
    /** Equipped skills per character id: 4 indexes into SkillPool (the ultimate is fixed). */
    public CompoundTag loadouts = new CompoundTag();
    /** Each character's own stats (levels + XP), so switching characters keeps every character's level. */
    public CompoundTag charStats = new CompoundTag();

    /** The player's ten You characters (see you/YouSlot) and which one is in use. */
    public final com.xenowarriors.you.YouSlot[] youSlots = new com.xenowarriors.you.YouSlot[com.xenowarriors.you.YouSlot.SLOTS];
    public int youSlot;
    { for (int i = 0; i < youSlots.length; i++) youSlots[i] = new com.xenowarriors.you.YouSlot(); }

    /** The You character in use (an empty one when they're playing a show character). */
    public com.xenowarriors.you.YouSlot you() { return youSlots[Math.max(0, Math.min(youSlots.length - 1, youSlot))]; }

    /** The save key for a character's own stats, loadout and standing: the show character's id, or "you&lt;slot&gt;". */
    public static String key(CharacterType t, int youSlot) { return t == CharacterType.YOU ? "you" + youSlot : t.getId(); }
    public String key(CharacterType t) { return key(t, youSlot); }
    public String key() { return key(type, youSlot); }

    /** Saves the current stats under this character, then loads that character's (fresh = level 1) . */
    public void switchStats(CharacterType from, CharacterType to) { switchStats(key(from), key(to)); }

    /** The same, by save key (You slots have one key each). */
    public void switchStats(String from, String to) {
        if (!from.equals(CharacterType.NONE.getId())) {
            CompoundTag t = new CompoundTag();
            t.putIntArray("lv", statLevel.clone());
            float[] xp = statXp; t.putFloat("x0", xp[0]); t.putFloat("x1", xp[1]); t.putFloat("x2", xp[2]);
            charStats.put(from, t);
        }
        if (to.equals(CharacterType.NONE.getId()) || to.equals(from)) return;
        CompoundTag t = charStats.getCompound(to);
        int[] lv = t.getIntArray("lv");
        for (int i = 0; i < 3; i++) { statLevel[i] = i < lv.length ? lv[i] : 0; statXp[i] = t.getFloat("x" + i); }
    }
    /** A KA given for testing (/xeno ka self); their real KAs aren't revealed yet. */
    public String ka = "";

    public int[] loadout(CharacterType t) {
        int[] l = loadouts.getIntArray(key(t));
        return l.length == 4 ? l : com.xenowarriors.skill.SkillPool.defaultLoadout();
    }
    public void setLoadout(CharacterType t, int[] l) { loadouts.putIntArray(key(t), l.clone()); }
    /** Last Karma value sent to the client (-1 = never sent). Not saved. */
    public float lastSyncedKarma = -1F;

    public CharacterType getType() { return type; }
    public void setType(CharacterType type) { this.type = type; }

    /** Current Karma cap (Fox King mode raises it by half). Not saved. */
    public float karmaMax = Karma.MAX;

    public float getKarma() { return karma; }
    public void setKarma(float value) { this.karma = Math.max(0F, Math.min(karmaMax, value)); }

    /** Keeps the character after death; Karma comes back full on respawn. */
    public void copyFrom(PlayerCharacter other) {
        this.type = other.type;
        System.arraycopy(other.statLevel, 0, statLevel, 0, 3);
        System.arraycopy(other.statXp, 0, statXp, 0, 3);
        this.flightTicks = other.flightTicks;
        this.senseUses = other.senseUses;
        this.sealed = other.sealed.copy();
        this.bladeReturn = other.bladeReturn.copy();
        this.nextSealId = other.nextSealId;
        this.prisonerOf = other.prisonerOf;
        this.miningLevel = other.miningLevel;
        this.bladeLevel = other.bladeLevel;
        this.bladeXp = other.bladeXp;
        this.bladeSky = other.bladeSky;
        this.bladeDark = other.bladeDark;
        this.realmTicks = other.realmTicks;
        this.realmCooldownUntil = other.realmCooldownUntil;
        this.miningXp = other.miningXp;
        this.craftLevel = other.craftLevel;
        this.craftXp = other.craftXp;
        this.smeltLevel = other.smeltLevel;
        this.smeltXp = other.smeltXp;
        this.prisonReturn = other.prisonReturn.copy();
        this.loadouts = other.loadouts.copy();
        this.charStats = other.charStats.copy();
        this.ka = other.ka;
        this.pets = other.pets.copy();
        this.wingPets = other.wingPets.copy();
        this.backFox = other.backFox.copy();
        this.wingsOut = other.wingsOut;
        this.outfit = other.outfit;
        this.youSlot = other.youSlot;
        for (int i = 0; i < youSlots.length; i++) youSlots[i] = other.youSlots[i].copy();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("character", type.getId());
        tag.putFloat("karma", karma);
        tag.putIntArray("stat_levels", statLevel.clone());
        tag.putInt("flight_ticks", flightTicks);
        tag.putInt("sense_uses", senseUses);
        tag.put("sealed", sealed.copy());
        tag.put("blade_return", bladeReturn.copy());
        tag.putInt("next_seal_id", nextSealId);
        if (prisonerOf != null) tag.putUUID("prisoner_of", prisonerOf);
        tag.put("prison_return", prisonReturn.copy());
        tag.put("loadouts", loadouts.copy());
        tag.put("char_stats", charStats.copy());
        tag.putString("ka", ka);
        tag.putInt("mining_level", miningLevel);
        tag.putInt("blade_level", bladeLevel);
        tag.putFloat("blade_xp", bladeXp);
        tag.putInt("blade_sky", bladeSky);
        tag.putInt("blade_dark", bladeDark);
        tag.putInt("realm_ticks", realmTicks);
        tag.putLong("realm_cooldown", realmCooldownUntil);
        tag.putFloat("mining_xp", miningXp);
        tag.putInt("craft_level", craftLevel);
        tag.putFloat("craft_xp", craftXp);
        tag.putInt("smelt_level", smeltLevel);
        tag.putFloat("smelt_xp", smeltXp);
        tag.put("pets", pets.copy());
        tag.put("wing_pets", wingPets.copy());
        tag.put("back_fox", backFox.copy());
        tag.putBoolean("wings_in", !wingsOut);
        tag.putInt("outfit", outfit);
        tag.putInt("torn", torn);
        tag.putInt("you_slot", youSlot);
        net.minecraft.nbt.ListTag ys = new net.minecraft.nbt.ListTag();
        for (com.xenowarriors.you.YouSlot s : youSlots) ys.add(s.save());
        tag.put("you_slots", ys);
        for (int i = 0; i < 3; i++) tag.putFloat("stat_xp_" + i, statXp[i]);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.type = CharacterType.byId(tag.getString("character"));
        this.flightTicks = tag.getInt("flight_ticks");
        this.senseUses = tag.getInt("sense_uses");
        this.sealed = tag.getList("sealed", net.minecraft.nbt.Tag.TAG_COMPOUND);
        this.bladeReturn = tag.getCompound("blade_return");
        this.nextSealId = tag.getInt("next_seal_id");
        this.prisonerOf = tag.hasUUID("prisoner_of") ? tag.getUUID("prisoner_of") : null;
        this.prisonReturn = tag.getCompound("prison_return");
        this.loadouts = tag.getCompound("loadouts");
        this.charStats = tag.getCompound("char_stats");
        this.ka = tag.getString("ka");
        this.miningLevel = tag.getInt("mining_level");
        this.bladeLevel = Math.max(1, tag.getInt("blade_level"));
        this.bladeXp = tag.getFloat("blade_xp");
        this.bladeSky = tag.getInt("blade_sky");
        this.bladeDark = tag.getInt("blade_dark");
        this.realmTicks = tag.contains("realm_ticks") ? tag.getInt("realm_ticks") : -1;
        this.realmCooldownUntil = tag.getLong("realm_cooldown");
        this.miningXp = tag.getFloat("mining_xp");
        this.craftLevel = tag.getInt("craft_level");
        this.craftXp = tag.getFloat("craft_xp");
        this.smeltLevel = tag.getInt("smelt_level");
        this.smeltXp = tag.getFloat("smelt_xp");
        this.pets = tag.getList("pets", net.minecraft.nbt.Tag.TAG_COMPOUND);
        this.wingPets = tag.getList("wing_pets", net.minecraft.nbt.Tag.TAG_COMPOUND);
        this.backFox = tag.getCompound("back_fox");
        this.wingsOut = !tag.getBoolean("wings_in");
        this.outfit = tag.getInt("outfit");
        this.torn = tag.getInt("torn");
        this.youSlot = Math.max(0, Math.min(youSlots.length - 1, tag.getInt("you_slot")));
        net.minecraft.nbt.ListTag ys = tag.getList("you_slots", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < youSlots.length; i++) youSlots[i] = i < ys.size() ? com.xenowarriors.you.YouSlot.load(ys.getCompound(i)) : new com.xenowarriors.you.YouSlot();
        int[] lv = tag.getIntArray("stat_levels");
        for (int i = 0; i < 3; i++) {
            statLevel[i] = i < lv.length ? lv[i] : 0;
            statXp[i] = tag.getFloat("stat_xp_" + i);
        }
        this.karma = tag.contains("karma") ? Math.max(0F, Math.min(Karma.MAX * 3F, tag.getFloat("karma"))) : Karma.MAX;   // (caps above 100 exist: the tick re-clamps to the real one)
    }
}

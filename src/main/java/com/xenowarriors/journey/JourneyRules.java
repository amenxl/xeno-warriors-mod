package com.xenowarriors.journey;

import com.xenowarriors.character.CharacterType;

/** Journey mode's fixed tables: difficulties, step kinds, scenarios, challenges, the shop. */
public final class JourneyRules {
    private JourneyRules() {}

    public static final String[] DIFF = {"EASY", "NORMAL", "HARD", "INSANE", "XENO"};
    public static final int[] DIFF_COLOR = {0x5FD05F, 0x50A0FF, 0xFFB040, 0xFF4040, 0xC040FF};
    static final float[] HP_MULT = {0.7F, 1F, 1.5F, 2.2F, 3.2F}, DMG_MULT = {0.6F, 1F, 1.3F, 1.7F, 2.2F};
    /** Money for clearing a journey, per player. */
    public static final long[] REWARD = {600, 1200, 2500, 4500, 8000};
    /** Which Tower boss "floor" each difficulty's final boss is made as (sets the boss and how tough it is). */
    static final int[] BOSS_FLOOR = {10, 20, 30, 50, 80};

    // step kinds
    public static final int START = 0, BATTLE = 1, SCENARIO = 2, HEAL = 3, BOSS = 4, EVENT = 5, ITEM = 6, DBZ = 7;
    public static final String[] STEP_NAME = {"START", "BATTLE", "SCENARIO", "HEALING POD", "BOSS", "EVENT", "ITEM", "DBZ"};
    public static final int[] STEP_COLOR = {0xFFFFFF, 0xFF7040, 0x40D0FF, 0x60F060, 0xC040FF, 0xFFD040, 0xF070E0, 0xFF9A20};
    /** Whether a step is a fight. */
    public static boolean fight(int kind) { return kind == BATTLE || kind == SCENARIO || kind == BOSS || kind == DBZ; }

    // stages: each difficulty has 3, longer each time; clear Stage 3 to open the next difficulty
    public static final int STAGES = 3;
    public static final int[] STAGE_ROWS = {10, 14, 18};
    public static final float[] STAGE_PAY = {1F, 1.5F, 2F};

    /** Event panel boosts: for the rest of the journey, for the whole party; they stack. */
    public enum Boost {
        POWER("Power", "+10% damage", 5, 0xFF6040), GUARD("Guard", "-10% damage taken", 5, 0x60A0FF), SWIFT("Swift", "+10% speed", 4, 0x60F0C0),
        VITALITY("Vitality", "+10% max health", 5, 0x60F060), FORTUNE("Fortune", "+20% Journey Coins", 5, 0xFFD040);
        public final String title, text; public final int max, color;
        Boost(String title, String text, int max, int color) { this.title = title; this.text = text; this.max = max; this.color = color; }
    }

    // shop
    public static final int S_HEAL = 0, S_FULL = 1, S_REVIVE = 2, S_PARTY = 3, S_POWER = 4;
    public static final int[] PRICE = {6, 12, 20, 18, 10};
    public static final String[] SHOP_NAME = {"Heal +40%", "Full Heal", "Revive (50%)", "Party Heal +25%", "Power Up"};
    public static final int START_JC = 15;

    public enum Rule {
        ANY("Anyone"), SKYLANDER("Skylanders only"), HUMAN("Humans only (no Skylanders)"), SKY_PATROL("Sky Patrol only"), REBELLION("Sky Rebellion only");
        public final String text;
        Rule(String t) { text = t; }
        public boolean fits(CharacterType t) {
            return switch (this) {
                case ANY -> true;
                case SKYLANDER -> t.skylander();
                case HUMAN -> !t.skylander();
                case SKY_PATROL -> t.skyPatrol();
                case REBELLION -> t == CharacterType.LOGAN || t == CharacterType.KILL || t == CharacterType.KEITA;
            };
        }
    }

    /** Who you fight (and how the arena looks). */
    public enum Foe { MIXED, GOV, SR, SKY_HATERS, DARK, DBZ }

    public enum Scenario {
        NONE("", "", Rule.ANY, Foe.MIXED),
        SKY_HATERS("Sky Hater Raid", "Sky Haters are raiding a Skylander island! Only Skylanders can defend their home.", Rule.SKYLANDER, Foe.SKY_HATERS),
        UNDERCOVER("Undercover Raid", "The Government scans for Skylanders at the gate. Only humans can get inside.", Rule.HUMAN, Foe.GOV),
        PATROL("Sky Patrol Call", "The Sky Rebellion is causing trouble in the capital - a job for the Sky Patrol.", Rule.SKY_PATROL, Foe.SR),
        HIDEOUT("Hideout Defense", "Government agents found the Rebellion's hideout. Only its members know the way in.", Rule.REBELLION, Foe.GOV),
        OUTBREAK("Corruption Outbreak", "Dark Possessed are spreading through the town. Anyone can answer the call.", Rule.ANY, Foe.DARK);
        public final String title, text; public final Rule rule; public final Foe foe;
        Scenario(String title, String text, Rule rule, Foe foe) { this.title = title; this.text = text; this.rule = rule; this.foe = foe; }
    }

    public enum Challenge {
        SOLO("Only use one party member", 15),
        NO_SKILLS("No skills", 12),
        NO_DOWNS("Nobody goes down", 8),
        SPEED("Win within %d seconds", 10),
        TOUGH("Take under %d hearts of damage", 10);
        public final String text; public final int bonus;
        Challenge(String t, int b) { text = t; bonus = b; }
    }

    public static final String[] RANK = {"D", "C", "B", "A", "S"};
    static final int[] RANK_JC = {2, 5, 9, 14, 20};
    public static final int[] RANK_COLOR = {0x8A8A8A, 0x60C060, 0x50A0FF, 0xFFB040, 0xFF50E0};

    /** Score / what an even share of the enemies' health is worth. */
    static int rank(float ratio) {
        return ratio >= 1.3F ? 4 : ratio >= 1.0F ? 3 : ratio >= 0.7F ? 2 : ratio >= 0.4F ? 1 : 0;
    }

    // score
    static final int PER_DAMAGE = 10, PER_SKILL = 60, PER_KILL = 50, PER_HURT = 20, OWN_DOWN = 800, MATE_DOWN = 400;
}

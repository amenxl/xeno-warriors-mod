package com.xenowarriors.skill;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.UltShowPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

import java.util.Map;

/**
 * Every ultimate but Rhalix's and Mikiko's (theirs have their own long cutscenes) gets a show: a short cutscene for the
 * one using it (UltShowClient: close on them as the power gathers, a sweeping shot as it's let go, a wide one of the
 * hit) and big effects everyone near sees - built from AAA Particles' / EpicFight Nightfall's effects and Lodestone's
 * when they're installed, with our own particles under them either way. Each ultimate has a style that picks the shots
 * and effects: a burst, slashes, a beam, a rain from the sky, a summoning, or powering up.
 */
public final class UltShow {
    private UltShow() {}

    public static final int BURST = 0, SLASH = 1, BEAM = 2, RAIN = 3, SUMMON = 4, POWER = 5;
    /** How long the show runs (ticks): the cutscene, and the caster held still (and shielded) for it. */
    public static final int LENGTH = 50;

    private static final Map<String, Integer> STYLES = Map.ofEntries(
            Map.entry("FullOutputBeamSkill", BEAM), Map.entry("HekubeatsFinaleSkill", BURST), Map.entry("RoyalGravity", BURST),
            Map.entry("MaynayaCrash", BURST), Map.entry("MoonlitGale", SLASH), Map.entry("PsychoticTimeBomb", BURST),
            Map.entry("TechBarrage", RAIN), Map.entry("BrotherBlast", BEAM), Map.entry("OpenSkyGround", SUMMON),
            Map.entry("PastMages", SUMMON), Map.entry("ThunderJudgement", RAIN), Map.entry("HellclawRend", SLASH),
            Map.entry("MassCut", SLASH), Map.entry("WeaponOfWill", POWER), Map.entry("PortalAmbush", SUMMON),
            Map.entry("DarkKarmaOverload", BURST), Map.entry("KeepersJudgement", SLASH), Map.entry("KitsuneTier3", POWER),
            Map.entry("RebellionsEnd", SLASH), Map.entry("AvaUnleashed", POWER), Map.entry("SistersVengeance", BURST));

    /** The style of an ultimate: its own, or guessed from its name. */
    public static int style(Skill s) {
        Integer v = STYLES.get(s.getClass().getSimpleName());
        if (v != null) return v;
        String id = s.getId();
        if (id.matches(".*(beam|blast|cannon|laser|ray).*")) return BEAM;
        if (id.matches(".*(rain|meteor|storm|thunder|barrage|volley).*")) return RAIN;
        if (id.matches(".*(slash|blade|cut|sword|rend|claw|slice|gale|judg).*")) return SLASH;
        if (id.matches(".*(summon|call|army|portal|mage|clone).*")) return SUMMON;
        if (id.matches(".*(form|mode|unleash|overload|surge|awaken|weapon|power).*")) return POWER;
        return BURST;
    }

    /** Rhalix's and Mikiko's ultimates have their own cutscenes (and Ultima's Xeno Blast its own). */
    public static boolean own(CharacterType t, Skill s) {
        return t == CharacterType.RHALIX || t == CharacterType.MIKIKO || s instanceof com.xenowarriors.skill.rhalix.GravityBarrageSkill
                || s.getClass().getSimpleName().equals("BlazingWindSkill") || s.getClass().getSimpleName().equals("HekubladeRainSkill")
                || s.getId().equals("xeno_blast");
    }

    /** A player used their ultimate: the cutscene for them, the effects for everyone near. */
    public static void play(ServerPlayer p, Skill s, CharacterType t) {
        if (own(t, s)) return;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LENGTH, 2, false, false));   // (held still for the show: not a sitting duck)
        var you = t == CharacterType.YOU ? com.xenowarriors.you.You.slot(p) : null;    // (your own character: the colour you picked)
        int col = (you != null ? you.color() : t.getColor()) & 0xFFFFFF;
        ModNetwork.sendToTrackingAndSelf(new UltShowPacket(p.getId(), style(s), col, s.getName().getString(), p.getYRot(), true), p);
    }

    /** A bot used its ultimate: the effects only (nobody's camera moves). */
    public static void bot(Entity b, Skill s, CharacterType t) {
        if (own(t, s)) return;
        ModNetwork.sendToTrackingEntityAndSelf(new UltShowPacket(b.getId(), style(s), t.getColor() & 0xFFFFFF, s.getName().getString(), b.getYRot(), false), b);
    }
}

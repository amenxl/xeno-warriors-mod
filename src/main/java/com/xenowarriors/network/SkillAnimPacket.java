package com.xenowarriors.network;

import com.xenowarriors.client.ClientSkillState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> clients: a player just used a skill, so play its body animation. */
public record SkillAnimPacket(int entityId, int anim, boolean airborne) {
    public static final int KISHI = 0;
    public static final int STOMP = 1;
    public static final int COMPRESS = 2;
    public static final int BANG = 3;
    public static final int BARRAGE = 4;
    public static final int SCATTER = 5;
    public static final int KITSUNE = 6;
    public static final int NETSU = 7;
    public static final int BLITZ = 8;
    public static final int BLAZING = 9;
    public static final int TEAM_RHALIX = 10, TEAM_MIKIKO = 11;
    public static final int DANCE_KICK = 12, NETSU_BURST = 13, MANIFEST = 14, EMPATHY = 15, KA_RELEASE = 16;
    /** General moves for the newer characters' skills (see SkillAnimations / GENERIC). */
    public static final int CAST = 20, SLASH = 21, DASH = 22, SHOOT = 23, RAISE = 24, GROUND = 25, TYPE = 26, SWING = 27, GUARD = 28, PUNCH = 29;
    /** Kaeru fights with her legs: a front kick, and a spinning kick for her finale. */
    public static final int KICK = 30, SPIN_KICK = 31;

    /** The general move each of the newer skills plays (skills with their own animation aren't listed). */
    public static final java.util.Map<String, Integer> GENERIC = new java.util.HashMap<>();
    static {
        String[][] m = {
            {"CAST", "gravity_fetch", "no_milk", "wind_of_sorrow", "clear_space", "ticking_time_bomb", "iron_grip", "bar_thief", "fire_spell", "thunder_flash",
                     "lights_out", "scent_track", "attack_prediction", "thunder_spell", "judgment_absorption",
                     "will_break", "will_break_click", "kyle_phone", "interrogation", "c2_serum", "fake_cry", "walkie_talkie", "shadow_voice", "shadow_voice_click",
                     "demon_grant", "dark_bind", "soul_snatch", "demon_glare"},
            {"SLASH", "rapier_thrust", "dagger_flurry", "rapid_vampire_slice", "impale", "hellclaw_rend",
                      "demon_claw", "twin_blade", "kitsune_attack", "kitsune_throw", "fox_palm", "kitsune_tier3", "fathers_blade", "fathers_blade_click", "assassins_takedown", "keepers_judgement"},
            {"DASH", "wing_rush", "knight_charge", "devil_dash", "lazy_slash",
                     "shadow_slash", "shadow_slash_click", "portal_step", "red_slash", "red_slash_click", "keepers_step", "sword_draw", "fox_step", "shadow_step", "portal_ambush"},
            {"SHOOT", "explosive_rounds", "gravity_rounds", "tech_barrage", "karma_blast", "brother_blast", "energy_blast", "full_output_beam",
                      "snap_shot", "knife_throw", "dark_karma_burst", "dark_karma_burst_click", "begone", "begone_click", "wing_gust"},
            {"RAISE", "royal_gravity", "lunar_mind", "moonlit_gale", "psychotic_time_bomb", "karma_surge", "persephone", "rescue_platform", "war_cry", "good_karma",
                      "freestyle_bars", "tenjo_medical", "open_sky_ground", "past_mages", "thunder_judgement", "devil_unleashed", "fox_heal",
                      "output_surge", "living_weapon",
                      "override", "modification_surge", "weapon_of_will", "vanish", "dark_karma_overload", "taunt", "ava_unleashed", "sisters_vengeance",
                      "rebellions_end", "boss_orders", "demon_form", "heavenly_demon", "cage_fighter", "protect_ava", "devil_self_split"},
            {"GROUND", "table_breaker", "gravity_boots", "body_slam", "ice_spell", "devil_flame", "kneel", "ground_pound"},
            {"TYPE", "camera_hack", "firewall", "elevator_hack", "virus_upload", "visor_scan"},
            {"SWING", "batter_up", "batter_shoot", "broom_smack", "mass_cut"},
            {"GUARD", "bullet_stop", "boxing_stance", "wind_wall", "knockout_mode", "cloak_device", "mana_barrier", "royal_guard", "play_weak", "hekubarrier", "portal_escape", "tough_body"},
            {"PUNCH", "freak_punch", "maynaya_crash", "haymaker"},
            {"KICK", "sound_burst"}, {"SPIN_KICK", "hekubeats_finale"},
            {"DANCE_KICK", "disco_kick"}};
        for (String[] row : m) {
            int a = switch (row[0]) { case "CAST" -> CAST; case "SLASH" -> SLASH; case "DASH" -> DASH; case "SHOOT" -> SHOOT; case "RAISE" -> RAISE; case "GROUND" -> GROUND;
                case "TYPE" -> TYPE; case "SWING" -> SWING; case "GUARD" -> GUARD; case "PUNCH" -> PUNCH; case "KICK" -> KICK; case "SPIN_KICK" -> SPIN_KICK; default -> DANCE_KICK; };
            for (int i = 1; i < row.length; i++) GENERIC.put(row[i], a);
        }
    }

    public static void encode(SkillAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.anim);
        buf.writeBoolean(msg.airborne);
    }

    public static SkillAnimPacket decode(FriendlyByteBuf buf) {
        return new SkillAnimPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SkillAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientSkillState.startAnim(msg.entityId, msg.anim, msg.airborne));
    }
}

package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mastery for the transformations besides Fox King (which has its own): Rhalix's Demon's Remnants, Kill's (and the
 * Demon race's) Devil Unleashed, and Lexy's Persephone. Like Fox King's, 0-100 per form, grown while in it - a little
 * every second, more for every hit landed and every kill - and it makes the form better:
 * <ul>
 *   <li>lasts longer: up to three times as long at 100;</li>
 *   <li>cheaper: Karma cost down a third from 25, down two thirds from 75;</li>
 *   <li>back sooner: recovery down a quarter from 50, by half from 75;</li>
 *   <li>mastered (100): the form hits harder.</li>
 * </ul>
 * Kept per character, in the player's save.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FormMastery {
    private FormMastery() {}

    public static final String DEMON = "demons_remnants", DEVIL = "devil_unleashed", PERSEPHONE = "persephone";
    public static final float MAX = 100F, PER_SECOND = 0.05F, PER_HIT = 0.1F, PER_KILL = 0.5F;
    private static final String KEY = "xw_form_mastery";

    private static String charKey(Player p) { return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key(d.getType())).orElse(ModCapabilities.getType(p).getId()); }

    private static CompoundTag root(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(KEY)) { pers.put(KEY, new CompoundTag()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers); }
        return pers.getCompound(KEY);
    }

    public static float get(Player p, String form) { return root(p).getCompound(charKey(p)).getFloat(form); }

    public static void set(Player p, String form, float m) {
        CompoundTag r = root(p), c = r.getCompound(charKey(p));
        c.putFloat(form, Math.max(0F, Math.min(MAX, m)));
        r.put(charKey(p), c);
    }

    public static String name(String form) { return switch (form) { case DEMON -> "Demon's Remnants"; case DEVIL -> "Devil Unleashed"; default -> "Persephone"; }; }

    /** Adds mastery, with a line at every 10 and at 25 / 50 / 75 / 100. */
    public static void add(ServerPlayer p, String form, float amount) {
        if (p instanceof FakePlayer) return;
        float before = get(p, form);
        if (before >= MAX) return;
        float after = Math.min(MAX, before + amount);
        set(p, form, after);
        int b = (int) before, a = (int) after;
        if (a / 10 > b / 10) {
            String perk = a >= 100 ? " - MASTERED: it hits harder" : a / 25 > b / 25 ? (a >= 75 ? " - cheaper and back much sooner" : a >= 50 ? " - back sooner" : " - cheaper") : "";
            p.displayClientMessage(Component.literal(name(form) + " mastery " + a + "/100 (lasts x" + String.format(java.util.Locale.ROOT, "%.1f", durationMult(a)) + ")" + perk).withStyle(ChatFormatting.GOLD), false);
        }
    }

    public static float durationMult(float m) { return 1F + 2F * Math.max(0F, Math.min(MAX, m)) / MAX; }
    public static float costMult(float m) { return m >= 75 ? 1F / 3F : m >= 25 ? 2F / 3F : 1F; }
    public static float cooldownMult(float m) { return m >= 75 ? 0.5F : m >= 50 ? 0.75F : 1F; }
    public static boolean mastered(float m) { return m >= MAX; }

    /** The form this player is in right now (one of ours with mastery), or null. */
    public static String active(ServerPlayer p) {
        if (com.xenowarriors.rhalix.DemonsRemnants.active(p)) return DEMON;
        if (com.xenowarriors.skill.ffr.SrSkills.devilActive(p)) return DEVIL;
        if (com.xenowarriors.skill.sp.SpState.persephone(p)) return PERSEPHONE;
        return null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        String f = active(p);
        if (f != null) add(p, f, PER_SECOND);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        String f = active(p);
        if (f != null) add(p, f, PER_HIT);
        if (f == DEVIL || f == PERSEPHONE) {                                     // mastered: it hits harder
            if (mastered(get(p, f))) e.setAmount(e.getAmount() * 1.25F);
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        String f = active(p);
        if (f != null) add(p, f, PER_KILL);
    }
}

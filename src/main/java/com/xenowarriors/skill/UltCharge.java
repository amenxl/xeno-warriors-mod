package com.xenowarriors.skill;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.party.TeamUltimate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Charging an ultimate: the ultimate key doesn't fire it at once - you charge it for {@link #MIN} ticks first, and
 * holding the key keeps charging (up to {@link #MAX}) for up to +50% power. While you charge, a teammate who can do a
 * team ultimate with you (Mikiko + Rhalix) joins in by pressing their own ultimate key - and a bot teammate joins once
 * you've held it past the base charge. Letting go (after the base charge) fires your ultimate on its own.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UltCharge {
    private UltCharge() {}

    /** Base charge (ticks) and the most it can be held; bot teammates join after this much charging. */
    public static final int MIN = 30, MAX = 100, BOT_JOIN = 40;
    /** The ultimate key slot number the client sends when the key is let go. */
    public static final int RELEASE = -10;
    private static final float BONUS = 0.5F;
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(1F, 0.75F, 0.2F), 1.2F);

    private static final class Charge { long start; boolean held = true; String skill; }
    private static final Map<UUID, Charge> CHARGING = new HashMap<>();
    private record Power(String skill, float mult, long until) {}
    private static final Map<UUID, Power> POWER = new HashMap<>();
    /** Set while the charged ultimate goes off (SkillManager lets it through). */
    static boolean firing;
    /** (For the tests) the last fired: {ticks charged, power}. */
    public static float[] last = {0F, 0F};

    public static boolean charging(LivingEntity e) { return CHARGING.containsKey(e.getUUID()); }

    /** How far along a player's charge is: {ticks, power}, or null. */
    public static float[] state(ServerPlayer p) {
        Charge c = CHARGING.get(p.getUUID());
        if (c == null) return null;
        long t = p.serverLevel().getGameTime() - c.start;
        return new float[]{t, power(t)};
    }

    private static float power(long t) { return 1F + BONUS * Math.max(0F, Math.min(1F, (t - MIN) / (float) (MAX - MIN))); }

    /**
     * The ultimate key (not sneaking): starts charging if the ultimate could go off now. True = handled (charging, or
     * already charging); false = let SkillManager say why it can't.
     */
    public static boolean begin(ServerPlayer p) {
        if (firing) return false;
        if (CHARGING.containsKey(p.getUUID())) return true;
        Skill skill = SkillManager.get(p, SkillManager.ULTIMATE_SLOT);
        if (skill == null) return false;
        long now = p.server.overworld().getGameTime();
        boolean[] ok = {true};
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { if (d.cooldownEnd[SkillManager.ULTIMATE_SLOT] > now) ok[0] = false; });
        if (!ok[0]) return false;
        int cost = SkillLevels.costFor(p, skill.getId(), skill.getKarmaCost(p));
        if (!p.isCreative() && !Karma.has(p, cost)) return false;
        Charge c = new Charge();
        c.start = p.serverLevel().getGameTime();
        c.skill = skill.getId();
        CHARGING.put(p.getUUID(), c);
        p.displayClientMessage(Component.literal("Charging " + skill.getName().getString() + "... hold to power it up").withStyle(ChatFormatting.GOLD), true);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.7F);
        // teammates who could join: told to press their ultimate key
        for (LivingEntity o : TeamUltimate.partners(p))
            if (o instanceof ServerPlayer op) {
                op.displayClientMessage(Component.literal(p.getName().getString() + " is charging an ultimate - press your ultimate key to team up!").withStyle(ChatFormatting.GOLD), true);
                op.serverLevel().playSound(null, op.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1F, 1.4F);
            }
        return true;
    }

    /** The ultimate key pressed (from the client): join a charging teammate, or start charging. True = handled. */
    public static boolean keyPressed(ServerPlayer p) {
        if (com.xenowarriors.party.Duels.cantAct(p) || !p.isAlive() || p.isSpectator()) return false;
        if (tryJoin(p)) return true;
        return begin(p);
    }

    /** The ultimate key let go. */
    public static void release(ServerPlayer p) {
        Charge c = CHARGING.get(p.getUUID());
        if (c != null) c.held = false;
    }

    /**
     * A teammate pressed their ultimate key: if someone they can team up with is charging nearby, the team ultimate
     * starts (the charge is used up). True if it did.
     */
    public static boolean tryJoin(LivingEntity joiner) {
        for (LivingEntity o : TeamUltimate.partners(joiner)) {
            if (!(o instanceof ServerPlayer charger) || !CHARGING.containsKey(charger.getUUID())) continue;
            List<TeamUltimate.Kind> ks = TeamUltimate.kinds(charger, joiner);
            if (ks.isEmpty() || !TeamUltimate.ready(joiner, joiner instanceof ServerPlayer) || !TeamUltimate.ready(charger, false)) continue;
            CHARGING.remove(charger.getUUID());
            return TeamUltimate.startNow(charger, joiner, ks.get(ks.size() - 1));
        }
        return false;
    }

    private static void fire(ServerPlayer p, Charge c, long t) {
        CHARGING.remove(p.getUUID());
        float mult = power(t);
        last = new float[]{t, mult};
        POWER.put(p.getUUID(), new Power(c.skill, mult, p.serverLevel().getGameTime() + 20 * 20));
        firing = true;
        try { SkillManager.useSkill(p, SkillManager.ULTIMATE_SLOT); } finally { firing = false; }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHARGING.isEmpty()) return;
        var server = event.getServer();
        for (var e : new java.util.ArrayList<>(CHARGING.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Charge c = e.getValue();
            if (p == null || !p.isAlive() || p.isSpectator() || com.xenowarriors.party.Duels.cantAct(p)) { CHARGING.remove(e.getKey()); continue; }
            long t = p.serverLevel().getGameTime() - c.start;
            // a bot teammate who can team up joins once you've held it a moment
            if (c.held && t >= BOT_JOIN) {
                for (LivingEntity o : TeamUltimate.partners(p))
                    if (o instanceof PartyBotEntity b && !TeamUltimate.kinds(p, b).isEmpty() && TeamUltimate.ready(b, false) && TeamUltimate.ready(p, false)) {
                        CHARGING.remove(e.getKey());
                        p.sendSystemMessage(Component.literal("").append(b.label()).append(Component.literal(": I'm with you!").withStyle(ChatFormatting.GOLD)));
                        List<TeamUltimate.Kind> ks = TeamUltimate.kinds(p, b);
                        TeamUltimate.startNow(p, b, ks.get(ks.size() - 1));
                        break;
                    }
                if (!CHARGING.containsKey(e.getKey())) continue;
            }
            if (t % 3 == 0) {                                                   // gathering power: a glow that grows
                int n = 2 + (int) (6 * (power(t) - 1F) / BONUS);
                p.serverLevel().sendParticles(GLOW, p.getX(), p.getY() + 1, p.getZ(), n, 0.5, 0.8, 0.5, 0.02);
            }
            if (t % 10 == 0 && t >= MIN && c.held)
                p.displayClientMessage(Component.literal("Ultimate power " + Math.round(power(t) * 100) + "% - let go to unleash").withStyle(ChatFormatting.GOLD), true);
            if ((!c.held && t >= MIN) || t >= MAX) fire(p, c, t);
        }
    }

    /** A charged ultimate's damage: up to +50% (for 20 seconds after it went off, from that skill only). */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (POWER.isEmpty() || !com.xenowarriors.combat.SkillContext.active()) return;
        var caster = com.xenowarriors.combat.SkillContext.caster();
        Power pw = caster == null ? null : POWER.get(caster.getUUID());
        if (pw == null) return;
        if (caster.level().getGameTime() > pw.until) { POWER.remove(caster.getUUID()); return; }
        if (!pw.skill.equals(com.xenowarriors.combat.SkillContext.skill())) return;
        event.setAmount(event.getAmount() * pw.mult);
    }
}

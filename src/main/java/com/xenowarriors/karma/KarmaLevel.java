package com.xenowarriors.karma;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Karma Level: how strong someone is, as one number (the show's "power level"). The Government's Karma Scanner,
 * Yuri and Ultima can measure anyone's.
 * Built from health and attack, and for players their max Karma, Character Level and (with DragonMineZ) their power.
 */
public final class KarmaLevel {
    private KarmaLevel() {}

    public static long of(LivingEntity e) {
        double hp = Math.max(1, e.getMaxHealth());
        var atkAttr = e.getAttribute(Attributes.ATTACK_DAMAGE);
        double atk = atkAttr == null ? 1 : Math.max(0, atkAttr.getValue());
        double v = 40 * hp * (1 + atk / 4);
        if (e instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer)) {
            float kmax = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.karmaMax).orElse(Karma.MAX);
            int lvl = com.xenowarriors.stats.Stats.characterLevel(p);
            v *= (1 + kmax / 100.0) * (1 + lvl / 20.0);
            if (ModCapabilities.getType(p) == CharacterType.NONE) v *= 0.5;
            v *= Math.max(com.xenowarriors.compat.DmzScaling.multiplier(p, true), com.xenowarriors.compat.DmzScaling.multiplier(p, false));
        } else if (e instanceof com.xenowarriors.bot.PartyBotEntity b) {
            v *= 2 * (1 + b.botLevel() / 20.0);
        } else {
            v *= 1 + com.xenowarriors.scaling.EnemyScaling.level(e) / 25.0;
        }
        return Math.max(1, Math.round(v));
    }

    public static String fmt(long v) { return String.format(java.util.Locale.ROOT, "%,d", v); }
}

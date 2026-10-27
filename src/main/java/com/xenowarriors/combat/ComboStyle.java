package com.xenowarriors.combat;

import com.xenowarriors.character.CharacterType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;

/**
 * Universal combo combat (everyone - players and party bots - plays by the same rules): what you hold decides your
 * combo. Each hit has a wind-up (the strike lands about a third of the way in), and the next hit can only start from
 * the hit's cancel point (~78%) - clicking early just queues it, so spam doesn't speed anything up. The last hit is a
 * finisher that knocks them away; sneak + click is a slow heavy hit that launches (and breaks shields).
 *
 * <p>Damage is balanced against vanilla: a whole string, chained perfectly, deals about 10% more per second than
 * fully-charged vanilla hits with the same weapon (worked out from its attack speed, so modded weapons balance too);
 * the hits are weighted so the finisher hits hardest.
 */
public enum ComboStyle {
    /** Bare hands (or holding something that isn't a weapon): a quick 5-hit string. */
    FIST(new int[]{6, 6, 7, 7, 11}, new float[]{1F, 1F, 1F, 1.15F, 1.7F}, 13, 0F, 1.0F, false),
    /** Mikiko's own fist combo (her moves, her damage table): 2 punches, 3 Bajiquan strikes, Tie Shan Kao. */
    MIKIKO(new int[]{7, 7, 9, 9, 10, 14}, new float[]{1F, 1F, 1F, 1F, 1F, 1F}, 14, 0F, 0.8F, true),
    /** Daggers / fast blades (attack speed 2+): a fast 5-hit flurry. */
    QUICK_BLADE(new int[]{7, 7, 8, 8, 12}, new float[]{1F, 1F, 1F, 1.1F, 1.6F}, 14, 0.2F, 1.1F, false),
    /** Swords: 4 flowing slashes. */
    SWORD(new int[]{9, 9, 10, 14}, new float[]{1F, 1F, 1.1F, 1.6F}, 16, 0.3F, 1.3F, false),
    /** Axes, hammers and anything slow: 3 big, heavy swings. */
    HEAVY(new int[]{13, 14, 19}, new float[]{1F, 1.1F, 1.8F}, 22, 0.3F, 1.8F, false),
    /** Spears and tridents: 4 long-reaching thrusts and sweeps. */
    SPEAR(new int[]{10, 10, 11, 14}, new float[]{1F, 1F, 1.05F, 1.5F}, 17, 1.2F, 1.4F, false),
    /** Pickaxes, shovels, hoes and other tools: a clumsy 3-hit string. */
    TOOL(new int[]{10, 10, 13}, new float[]{1F, 1F, 1.5F}, 16, 0F, 1.1F, false);

    public final int[] durations;
    public final float[] weights;
    public final int heavyDuration;
    public final float reachBonus;
    /** Knockback strength of the finisher (vanilla's is 0.4-0.5). */
    public final float finisherKnock;
    /** Deals its own damage (Mikiko's table), so no combo multiplier on the light hits. */
    public final boolean ownDamage;

    /** Hits land this far into each attack, and the next one can start from the cancel point. */
    public static final float STRIKE = 0.36F, CANCEL = 0.78F;
    /** A new click within this many ticks after a hit ends carries the string on. */
    public static final int CHAIN = 9;

    ComboStyle(int[] durations, float[] weights, int heavyDuration, float reachBonus, float finisherKnock, boolean ownDamage) {
        this.durations = durations;
        this.weights = weights;
        this.heavyDuration = heavyDuration;
        this.reachBonus = reachBonus;
        this.finisherKnock = finisherKnock;
        this.ownDamage = ownDamage;
    }

    public int length() { return durations.length; }

    public int duration(int hit, boolean heavy) {
        return heavy ? heavyDuration : durations[Math.floorMod(hit, durations.length)];
    }

    public static int windup(int duration) { return Math.max(2, Math.round(duration * STRIKE)); }

    public static int cancel(int duration) { return Math.max(3, Math.round(duration * CANCEL)); }

    /**
     * Damage multiplier for a hit (on top of a fully-charged vanilla hit).
     * @param attackSpeed attacks per second at full charge (the attack speed attribute; 1 for bots)
     */
    public float multiplier(int hit, boolean heavy, double attackSpeed) {
        float speed = (float) Math.max(0.25, Math.min(6.0, attackSpeed));
        if (heavy) return clamp(1.15F * speed * heavyDuration / 20F);
        float t = 0F, w = 0F;
        for (int i = 0; i < durations.length; i++) { t += cancel(durations[i]); w += weights[i]; }
        float k = 1.1F * speed * t / (20F * w);
        return clamp(weights[Math.floorMod(hit, weights.length)] * k);
    }

    private static float clamp(float m) { return Math.max(0.3F, Math.min(3.0F, m)); }

    /** Attack speed (attacks per second): the attribute for players; for mobs without it, 4 + the item's modifier. */
    public static double attackSpeed(LivingEntity user, ItemStack held) {
        var inst = user.getAttribute(Attributes.ATTACK_SPEED);
        if (inst != null) return inst.getValue();
        double v = 4.0;
        for (var m : held.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED))
            if (m.getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION) v += m.getAmount();
        return v;
    }

    /** The combo for what this person holds, or null if it isn't a melee attack (bows, crossbows...). */
    public static ComboStyle of(LivingEntity user, ItemStack held, CharacterType character) {
        Item it = held.getItem();
        if (it instanceof ProjectileWeaponItem) return null;
        if (it instanceof TridentItem) return SPEAR;
        String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it));
        if (id.contains("spear") || id.contains("lance") || id.contains("halberd") || id.contains("glaive") || id.contains("pike")) return SPEAR;
        double speed = attackSpeed(user, held);
        if (it instanceof SwordItem) return speed >= 2.0 ? QUICK_BLADE : SWORD;
        if (it instanceof AxeItem || (it instanceof TieredItem && speed <= 0.95)) return HEAVY;
        if (it instanceof TieredItem) return TOOL;
        if (id.contains("hammer") || id.contains("mace")) return HEAVY;
        if (id.contains("dagger") || id.contains("knife")) return QUICK_BLADE;
        boolean weapon = held.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
        if (weapon) return speed <= 0.95 ? HEAVY : speed >= 2.0 ? QUICK_BLADE : SWORD;
        // bare hands or a non-weapon: fists (Mikiko has his own fist combo)
        return character == CharacterType.MIKIKO && held.isEmpty() ? MIKIKO : FIST;
    }
}

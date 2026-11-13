package com.xenowarriors.apl;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ability Points (APL): one for every Character Level a character has gained, each character its own. Spent in
 * Character Customization > Abilities on abilities with ranks (each rank costs more); a reset gives them all back for
 * Money. Your bots of a character use that character's ranks where they can (Revive Range, Quick Reviver, Revive
 * Bubble, Steady Feet). The ones you'd want to switch off are on the Passives tab and the toggle wheel.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Apl {
    private Apl() {}

    public enum Ability {
        REVIVE_RANGE("revive_range", "Revive Range", new int[]{3, 6, 9, 12, 15}, null,
                "You revive teammates from further away", new String[]{"2.7 blocks", "3.1 blocks", "3.6 blocks", "4.0 blocks", "4.5 blocks"}),
        QUICK_REVIVER("quick_reviver", "Quick Reviver", new int[]{5, 10, 15}, null,
                "You revive teammates faster", new String[]{"33% faster", "50% faster", "twice as fast"}),
        REVIVE_BUBBLE("revive_bubble", "Revive Bubble", new int[]{5, 10, 15}, "revive_bubble",
                "When someone revives you, a bubble shields them from hits", new String[]{"3 seconds", "5 seconds", "8 seconds"}),
        SECOND_WIND("second_wind", "Second Wind", new int[]{15, 25, 40}, "second_wind",
                "Downed, you get back up by yourself after 5 seconds", new String[]{"25% health, every 10 minutes", "40% health, every 7 minutes", "60% health, every 5 minutes"}),
        STEADY_FEET("steady_feet", "Steady Feet", new int[]{5, 10, 15}, "steady_feet",
                "Less knockback from hits", new String[]{"20% less", "40% less", "60% less"}),
        AUTO_EAT("auto_eat", "Auto Eat", new int[]{10}, "auto_eat",
                "Eats the best food you carry when you get hungry", new String[]{"on"}),
        AUTO_POTION("auto_potion", "Auto Potion", new int[]{15, 10}, "auto_potion",
                "Drinks a healing potion you carry when your health is low", new String[]{"below 35% health", "below the health you pick"});

        public final String id, title, toggle, what;
        public final int[] costs;
        public final String[] ranks;

        Ability(String id, String title, int[] costs, String toggle, String what, String[] ranks) {
            this.id = id; this.title = title; this.costs = costs; this.toggle = toggle; this.what = what; this.ranks = ranks;
        }

        public int max() { return costs.length; }
        /** APL spent to reach this rank. */
        public int spentTo(int rank) { int s = 0; for (int i = 0; i < rank && i < costs.length; i++) s += costs[i]; return s; }
        public static Ability byId(String id) { for (Ability a : values()) if (a.id.equals(id)) return a; return null; }
    }

    private static final String ROOT = "xw_apl", POTION_AT = "potion_at";
    /** The health % Auto Potion rank 2 can be set to. */
    public static final int[] POTION_CHOICES = {25, 35, 50, 65};
    /** A reset: this much Money, plus this much per APL given back. */
    public static final long RESET_BASE = 500, RESET_PER = 50;

    // ------------------------------------------------------------------ storage (per character, in the player's save)

    private static String key(Player p) {
        return p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.key())
                .orElse(com.xenowarriors.character.ModCapabilities.getType(p).getId());
    }

    private static CompoundTag data(Player owner, String charKey, boolean create) {
        CompoundTag pers = owner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = pers.getCompound(ROOT);
        CompoundTag me = root.getCompound(charKey);
        if (create) { root.put(charKey, me); pers.put(ROOT, root); owner.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers); }
        return me;
    }

    /** A rank of the character this player is playing. */
    public static int rank(Player p, Ability a) {
        if (p == null || p instanceof net.minecraftforge.common.util.FakePlayer) return 0;
        return Math.min(a.max(), data(p, key(p), false).getInt(a.id));
    }

    /** A rank of one of the owner's characters (its bot). */
    public static int rankFor(Player owner, String charKey, Ability a) {
        if (owner == null || charKey == null) return 0;
        return Math.min(a.max(), data(owner, charKey, false).getInt(a.id));
    }

    /** Whoever this is (a player, or a bot of one of their characters): its rank. */
    public static int rankOf(LivingEntity e, Ability a) {
        if (e instanceof PartyBotEntity b) return rankFor(b.owner(), b.charKey(), a);
        if (e instanceof Player p) return rank(p, a);
        return 0;
    }

    /** Switched on (the Passives tab / toggle wheel), for those that can be switched. */
    public static boolean on(Player p, Ability a) {
        if (a.toggle == null) return true;
        var e = com.xenowarriors.toggle.Toggles.Entry.byId(a.toggle);
        return e == null || com.xenowarriors.toggle.Toggles.enabled(p, e);
    }

    /** Ranked and switched on. */
    public static int live(Player p, Ability a) { int r = rank(p, a); return r > 0 && on(p, a) ? r : 0; }

    public static int earned(ServerPlayer p) { return Math.max(0, com.xenowarriors.stats.Stats.characterLevel(p) - 1); }

    public static int spent(Player p) {
        CompoundTag d = data(p, key(p), false);
        int s = 0;
        for (Ability a : Ability.values()) s += a.spentTo(Math.min(a.max(), d.getInt(a.id)));
        return s;
    }

    public static int available(ServerPlayer p) { return Math.max(0, earned(p) - spent(p)); }

    public static long resetCost(Player p) { int s = spent(p); return s == 0 ? 0 : RESET_BASE + RESET_PER * s; }

    public static int potionAt(Player p) {
        int v = data(p, key(p), false).getInt(POTION_AT);
        return v <= 0 ? 35 : v;
    }

    // ------------------------------------------------------------------ actions (the Abilities tab)

    /** Buys the next rank. Returns a message (null = done). */
    public static String buy(ServerPlayer p, Ability a) {
        int r = rank(p, a);
        if (r >= a.max()) return a.title + " is at its top rank.";
        int cost = a.costs[r];
        if (available(p) < cost && !p.isCreative()) return "Not enough APL: rank " + (r + 1) + " costs " + cost + " (you have " + available(p) + "). APL comes from leveling up.";
        data(p, key(p), true).putInt(a.id, r + 1);
        bought++;
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        p.displayClientMessage(Component.literal(a.title + " - rank " + (r + 1) + ": " + a.ranks[r]).withStyle(ChatFormatting.AQUA), true);
        if (a.toggle != null) com.xenowarriors.toggle.Toggles.send(p);
        return null;
    }

    /** Gives all of this character's APL back, for Money. */
    public static String reset(ServerPlayer p) {
        int s = spent(p);
        if (s == 0) return "Nothing to reset.";
        long cost = resetCost(p);
        if (!p.isCreative() && !com.xenowarriors.economy.Money.take(p, cost)) return "A reset costs " + com.xenowarriors.economy.Money.fmt(cost) + " Money.";
        CompoundTag d = data(p, key(p), true);
        for (Ability a : Ability.values()) d.remove(a.id);
        p.displayClientMessage(Component.literal("Abilities reset: " + s + " APL back" + (p.isCreative() ? "" : " (-" + com.xenowarriors.economy.Money.fmt(cost) + " Money)")).withStyle(ChatFormatting.GOLD), true);
        com.xenowarriors.toggle.Toggles.send(p);
        return null;
    }

    public static void cyclePotion(ServerPlayer p) {
        int cur = potionAt(p), next = POTION_CHOICES[0];
        for (int i = 0; i < POTION_CHOICES.length; i++) if (POTION_CHOICES[i] == cur) next = POTION_CHOICES[(i + 1) % POTION_CHOICES.length];
        data(p, key(p), true).putInt(POTION_AT, next);
    }

    public static void sync(ServerPlayer p) {
        int[] ranks = new int[Ability.values().length];
        for (Ability a : Ability.values()) ranks[a.ordinal()] = rank(p, a);
        ModNetwork.sendToPlayer(new com.xenowarriors.network.AplPacket(com.xenowarriors.network.AplPacket.STATE, -1, earned(p), spent(p), resetCost(p), potionAt(p), ranks), p);
    }

    // ------------------------------------------------------------------ the abilities

    /** Revive Range: how close a reviver must be. */
    public static double reviveReach(LivingEntity helper) {
        int r = rankOf(helper, Ability.REVIVE_RANGE);
        return com.xenowarriors.party.ReviveCircle.RADIUS + (r <= 0 ? 0 : new double[]{0.5, 0.9, 1.4, 1.8, 2.3}[r - 1]);
    }

    /** The furthest any reviver can reach (for the search box). */
    public static final double MAX_REACH = 4.5;

    /** Quick Reviver: revive progress this tick (1 normally). */
    public static int reviveGain(LivingEntity helper, long tick) {
        int r = rankOf(helper, Ability.QUICK_REVIVER);
        if (r <= 0) return 1;
        return switch (r) { case 1 -> tick % 3 == 0 ? 2 : 1; case 2 -> tick % 2 == 0 ? 2 : 1; default -> 2; };
    }

    private static final String BUBBLE = "xw_revive_bubble";

    /** Revive Bubble (the downed one's): the one reviving them is shielded for a while. Called once a revive starts. */
    public static void reviveStarted(LivingEntity downed, LivingEntity helper) {
        int r = downed instanceof Player p ? live(p, Ability.REVIVE_BUBBLE) : rankOf(downed, Ability.REVIVE_BUBBLE);
        if (r <= 0 || helper == null) return;
        int secs = new int[]{3, 5, 8}[r - 1];
        helper.getPersistentData().putLong(BUBBLE, helper.level().getGameTime() + secs * 20L);
        bubbles++;
        helper.level().playSound(null, helper.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
    }

    public static boolean bubbled(LivingEntity e) { return e.getPersistentData().getLong(BUBBLE) > e.level().getGameTime(); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !bubbled(v) || e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        e.setCanceled(true);
        blocked++;
        if (v.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.END_ROD, v.getX(), v.getY() + 1, v.getZ(), 6, 0.5, 0.6, 0.5, 0.02);
    }

    /** Steady Feet: less knockback. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKnockback(LivingKnockBackEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        int r = v instanceof Player p ? live(p, Ability.STEADY_FEET) : rankOf(v, Ability.STEADY_FEET);
        if (r <= 0) return;
        e.setStrength(e.getStrength() * (1F - 0.2F * r));
        steadied++;
    }

    private static final String WIND = "xw_second_wind_at";

    /** Second Wind: can this downed player get up by themselves now? */
    public static boolean secondWindReady(ServerPlayer p) {
        int r = live(p, Ability.SECOND_WIND);
        if (r <= 0) return false;
        long cd = new long[]{10, 7, 5}[r - 1] * 60 * 20;
        long last = p.getPersistentData().getLong(WIND);
        return last == 0 || p.level().getGameTime() - last >= cd || p.level().getGameTime() < last;
    }

    /** Uses it: the health to get up with (and the cooldown starts). */
    public static float useSecondWind(ServerPlayer p) {
        int r = Math.max(1, rank(p, Ability.SECOND_WIND));
        p.getPersistentData().putLong(WIND, p.level().getGameTime());
        winds++;
        return p.getMaxHealth() * new float[]{0.25F, 0.4F, 0.6F}[r - 1];
    }

    /** Auto Eat and Auto Potion, once a second. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 7 || !p.isAlive() || p.isSpectator()
                || com.xenowarriors.party.Duels.isKnockedOut(p)) return;
        if (live(p, Ability.AUTO_POTION) > 0) {
            int at = rank(p, Ability.AUTO_POTION) >= 2 ? potionAt(p) : 35;
            if (p.getHealth() < p.getMaxHealth() * at / 100F && p.level().getGameTime() >= p.getPersistentData().getLong("xw_auto_potion_cd")) drinkPotion(p);
        }
        if (live(p, Ability.AUTO_EAT) > 0 && p.getFoodData().getFoodLevel() <= 14 && !p.isUsingItem()) eatBest(p);
    }

    private static boolean healing(ItemStack s) {
        if (!s.is(Items.POTION)) return false;
        for (MobEffectInstance m : PotionUtils.getMobEffects(s))
            if (m.getEffect() == MobEffects.HEAL || m.getEffect() == MobEffects.REGENERATION) return true;
        return false;
    }

    private static void drinkPotion(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!healing(s)) continue;
            for (MobEffectInstance m : PotionUtils.getMobEffects(s)) {
                if (m.getEffect().isInstantenous()) m.getEffect().applyInstantenousEffect(p, p, p, m.getAmplifier(), 1.0);
                else p.addEffect(new MobEffectInstance(m));
            }
            s.shrink(1);
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (!inv.add(bottle)) p.drop(bottle, false);
            p.getPersistentData().putLong("xw_auto_potion_cd", p.level().getGameTime() + 200);
            p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 1F);
            p.displayClientMessage(Component.literal("Auto Potion: drank a healing potion").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            potions++;
            return;
        }
    }

    private static boolean badFood(ItemStack s) {
        return s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH) || s.is(Items.CHICKEN)
                || s.is(Items.SUSPICIOUS_STEW) || s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.CHORUS_FRUIT);
    }

    private static void eatBest(ServerPlayer p) {
        var inv = p.getInventory();
        int best = -1, bestFood = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            var food = s.getItem().getFoodProperties(s, p);
            if (food == null || badFood(s) || food.getNutrition() <= bestFood) continue;
            if (p.getFoodData().getFoodLevel() + food.getNutrition() > 20 + 2 && food.getNutrition() > 4) continue;   // (don't waste a big meal on a small hunger)
            best = i; bestFood = food.getNutrition();
        }
        if (best < 0) return;
        ItemStack s = inv.getItem(best);
        ItemStack one = s.split(1);
        ItemStack left = one.finishUsingItem(p.level(), p);       // (eats it: food, effects, a bowl back for stews)
        if (!left.isEmpty() && left != one && !inv.add(left)) p.drop(left, false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.7F, 1F);
        eaten++;
    }

    /** (Tests) */
    public static int bought, bubbles, blocked, steadied, winds, potions, eaten;
}

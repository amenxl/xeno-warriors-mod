package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Bigger stomachs. Mikiko holds 10 hunger bars' worth (200 points, shown as the 10 icons changing color every full
 * bar); Rhalix 3 (60 points) - slow to drain on the ground, faster while flying. Ultima doesn't eat at all (no hunger).
 * The real food bar only ever shows the top 20 points (kept at 19 until the stomach is completely full so he can
 * still eat), so health regen, sprinting and starving work like normal once it's low.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BigHunger {
    private static final String TOTAL = "xw_food_total", LAST = "xw_food_last", FRAC = "xw_food_frac";

    private BigHunger() {}

    /** Max food points for this character (20 = vanilla). */
    public static int max(Player p) {
        return switch (ModCapabilities.getType(p)) {
            case MIKIKO -> 200;
            case RHALIX -> 60;
            default -> 20;
        } + com.xenowarriors.stats.Appetite.extraFood(p);          // (+20 per extra layer from the Appetite level)
    }

    /** Extra food (e.g. the Appetite bonus): into the big stomach if there is one, else onto the bar. */
    public static void addFood(ServerPlayer p, int points, float saturation) {
        var food = p.getFoodData();
        if (points > 0) {
            int max = max(p);
            if (max > 20) {
                var d = p.getPersistentData();
                int total = d.contains(TOTAL) ? d.getInt(TOTAL) : food.getFoodLevel();
                d.putInt(TOTAL, Math.min(max, total + points));
            } else food.setFoodLevel(Math.min(20, food.getFoodLevel() + points));
        }
        if (saturation > 0) food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + saturation));
    }

    public static boolean noHunger(Player p) { return ModCapabilities.getType(p) == CharacterType.ULTIMA || com.xenowarriors.you.YouRaces.noHunger(p); }

    public static int total(Player p) {
        return p.getPersistentData().contains(TOTAL) ? p.getPersistentData().getInt(TOTAL) : p.getFoodData().getFoodLevel();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        var food = p.getFoodData();
        if (noHunger(p)) {                                              // Ultima runs on Data, not food
            if (food.getFoodLevel() != 20) food.setFoodLevel(20);
            food.setSaturation(Math.min(food.getSaturationLevel(), 2F));
            return;
        }
        int max = max(p);
        var d = p.getPersistentData();
        if (max <= 20) {                                                 // normal stomach: forget any stored extra
            if (d.contains(TOTAL)) { d.remove(TOTAL); d.remove(LAST); d.remove(FRAC); sync(p, food.getFoodLevel(), 20); }
            return;
        }
        int total = d.contains(TOTAL) ? d.getInt(TOTAL) : food.getFoodLevel();
        int last = d.contains(LAST) ? d.getInt(LAST) : food.getFoodLevel();
        int delta = food.getFoodLevel() - last;
        if (delta < 0 && ModCapabilities.getType(p) == CharacterType.RHALIX) {   // Rhalix: slow on the ground, faster in the air
            float mult = p.getAbilities().flying ? 1.6F : 0.5F;
            float f = d.getFloat(FRAC) + -delta * mult;
            int whole = (int) f;
            d.putFloat(FRAC, f - whole);
            delta = -whole;
        }
        total = Math.max(0, Math.min(max, total + delta));
        if (ModCapabilities.getType(p) == CharacterType.RHALIX && p.getAbilities().flying && p.tickCount % 200 == 0) total = Math.max(0, total - 1);
        int shown = total >= max ? 20 : Math.min(19, total);
        if (food.getFoodLevel() != shown) food.setFoodLevel(shown);
        boolean changed = !d.contains(TOTAL) || d.getInt(TOTAL) != total;
        d.putInt(TOTAL, total);
        d.putInt(LAST, shown);
        if (changed || p.tickCount % 100 == 0) sync(p, total, max);
    }

    /**
     * Health regen from a full stomach: as soon as the base hunger bar (the first 20 points) is full you heal like
     * vanilla does on a full bar, and every extra full bar on top makes it faster (1 bar: half a heart every 2 seconds,
     * 2 bars: every second, 4 bars: twice a second...). Each heal costs food like vanilla's.
     */
    @SubscribeEvent
    public static void onRegen(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.isSpectator() || !p.isAlive()) return;
        if (noHunger(p) || !p.isHurt() || !p.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION)) return;
        int bars = fullBars(p);
        if (bars <= 0) return;
        int every = regenInterval(bars);
        if (p.tickCount % every != 0) return;
        p.heal(1F);
        p.getFoodData().addExhaustion(4F);
    }

    /** How many whole hunger bars (20 points each) are full right now. */
    public static int fullBars(Player p) {
        int pts = max(p) > 20 ? total(p) : p.getFoodData().getFoodLevel();
        return pts / 20;
    }

    /** Ticks between heals for this many full bars. */
    public static int regenInterval(int bars) { return Math.max(8, 40 / Math.max(1, bars)); }

    /** Food eaten while the visible bar can't hold it goes into the big stomach. */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || max(p) <= 20) return;
        FoodProperties fp = event.getItem().getFoodProperties(p);
        if (fp == null) return;
        var d = p.getPersistentData();
        int last = d.contains(LAST) ? d.getInt(LAST) : p.getFoodData().getFoodLevel();
        int gainedOnBar = p.getFoodData().getFoodLevel() - last;
        int total = d.contains(TOTAL) ? d.getInt(TOTAL) + fp.getNutrition() : p.getFoodData().getFoodLevel();   // (no stomach yet: the bar already holds this meal - don't count it twice)
        d.putInt(TOTAL, Math.min(max(p), total));
        d.putInt(LAST, p.getFoodData().getFoodLevel());                // the bar change is already counted
    }

    private static void sync(ServerPlayer p, int total, int max) {
        ModNetwork.sendToPlayer(new com.xenowarriors.network.BigHungerPacket(total, max), p);
    }
}

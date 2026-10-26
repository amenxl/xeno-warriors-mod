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
        };
    }

    public static boolean noHunger(Player p) { return ModCapabilities.getType(p) == CharacterType.ULTIMA; }

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

    /** Food eaten while the visible bar can't hold it goes into the big stomach. */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || max(p) <= 20) return;
        FoodProperties fp = event.getItem().getFoodProperties(p);
        if (fp == null) return;
        var d = p.getPersistentData();
        int last = d.contains(LAST) ? d.getInt(LAST) : p.getFoodData().getFoodLevel();
        int gainedOnBar = p.getFoodData().getFoodLevel() - last;
        int total = (d.contains(TOTAL) ? d.getInt(TOTAL) : last) + fp.getNutrition() - gainedOnBar + gainedOnBar;
        d.putInt(TOTAL, Math.min(max(p), total));
        d.putInt(LAST, p.getFoodData().getFoodLevel());                // the bar change is already counted
    }

    private static void sync(ServerPlayer p, int total, int max) {
        ModNetwork.sendToPlayer(new com.xenowarriors.network.BigHungerPacket(total, max), p);
    }
}

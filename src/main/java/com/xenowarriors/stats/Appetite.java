package com.xenowarriors.stats;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.BigHunger;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Appetite (the hunger level): levels up from the food you eat (XP = the food points it gives), for each character.
 *  - every level makes food give {@link #PER_LEVEL} more (hunger points and saturation): +100% at level 100;
 *  - every 10 levels adds a whole extra layer of hunger bars (20 more food points), up to 10 extra layers.
 * The extra layers go into the same big stomach Mikiko and Rhalix use (character/BigHunger): the real food bar only
 * shows the top 20 points, so sprinting, regeneration and starving keep working like vanilla.
 * Separate from Speed/Attack/Defense (it doesn't change the Character Level).
 * <p>Bots have it too, per character in their owner's save (the same record as when the owner plays that character):
 * the food a bot eats levels it, and its level makes food heal it more by the same +1% a level. (Bots have no hunger
 * bar, so the extra layers only matter once you play that character.)
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Appetite {
    public static final int MAX_LEVEL = 100, LEVELS_PER_LAYER = 10;
    public static final float PER_LEVEL = 0.01F;
    private static final String KEY = "xw_appetite";

    private Appetite() {}

    /** Food points needed to go from this level to the next. */
    public static float needed(int level) { return 20F + level * 4F; }

    private static CompoundTag root(Player p) {
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!pers.contains(KEY)) { pers.put(KEY, new CompoundTag()); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers); }
        return pers.getCompound(KEY);
    }

    private static String charKey(Player p) {
        return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(PlayerCharacter::key).orElse(ModCapabilities.getType(p).getId());
    }

    public static int level(Player p) { return Math.max(0, Math.min(MAX_LEVEL, root(p).getCompound(charKey(p)).getInt("lv"))); }

    public static float progress(Player p) {
        int lv = level(p);
        return lv >= MAX_LEVEL ? 1F : Math.min(1F, root(p).getCompound(charKey(p)).getFloat("xp") / needed(lv));
    }

    /** Raw XP toward the next level (Stat Boosts keep it when they add levels). */
    public static float xp(Player p) { return root(p).getCompound(charKey(p)).getFloat("xp"); }

    /** Extra layers of hunger bars (one per 10 levels). */
    public static int layers(int level) { return Math.min(MAX_LEVEL, level) / LEVELS_PER_LAYER; }
    public static int layers(Player p) { return layers(level(p)); }

    /** Extra food points on top of this character's stomach. */
    public static int extraFood(Player p) { return p.level().isClientSide ? 0 : layers(p) * 20; }

    /** How much more food gives at a level (0.25 = +25%). */
    public static float bonus(int level) { return Math.min(MAX_LEVEL, level) * PER_LEVEL; }

    // ---------------------------------------------------------------- bots

    private static String botKey(com.xenowarriors.bot.PartyBotEntity b, ServerPlayer o) {
        return o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.key(b.character())).orElse(b.character().getId());
    }

    /** A bot's Appetite level (its character's, in its owner's save; 0 without its owner online). */
    public static int level(com.xenowarriors.bot.PartyBotEntity b) {
        ServerPlayer o = b.owner();
        return o == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, root(o).getCompound(botKey(b, o)).getInt("lv")));
    }

    public static float bonus(com.xenowarriors.bot.PartyBotEntity b) { return bonus(level(b)); }

    /** Food a bot ate: XP toward its character's Appetite (its owner hears when it goes up). */
    public static void addXp(com.xenowarriors.bot.PartyBotEntity b, float amount) {
        ServerPlayer o = b.owner();
        if (o == null || amount <= 0 || b.journey()) return;
        String k = botKey(b, o);
        CompoundTag r = root(o);
        CompoundTag c = r.getCompound(k);
        int lv = Math.max(0, Math.min(MAX_LEVEL, c.getInt("lv")));
        if (lv >= MAX_LEVEL) return;
        int before = lv;
        float xp = c.getFloat("xp") + amount;
        while (lv < MAX_LEVEL && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        if (lv >= MAX_LEVEL) xp = 0;
        c.putInt("lv", lv); c.putFloat("xp", xp);
        r.put(k, c);
        if (lv > before) com.xenowarriors.statboost.StatBoosts.botLeveledUp(b, o, com.xenowarriors.statboost.BoostStat.APPETITE);
        if (lv > before) o.displayClientMessage(Component.literal("").append(b.label()).append(Component.literal(": Appetite level " + lv + " (food +" + Math.round(bonus(lv) * 100) + "%)")).withStyle(ChatFormatting.GOLD), true);
    }

    public static void set(ServerPlayer p, int level, float xp) {
        CompoundTag r = root(p);
        CompoundTag c = r.getCompound(charKey(p));
        c.putInt("lv", Math.max(0, Math.min(MAX_LEVEL, level)));
        c.putFloat("xp", Math.max(0F, xp));
        r.put(charKey(p), c);
    }

    public static void addXp(ServerPlayer p, float amount) {
        if (amount <= 0 || p.isCreative() || p.isSpectator()) return;
        int lv = level(p);
        if (lv >= MAX_LEVEL) return;
        float xp = root(p).getCompound(charKey(p)).getFloat("xp") + amount;
        int before = lv;
        while (lv < MAX_LEVEL && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        if (lv >= MAX_LEVEL) xp = 0;
        set(p, lv, xp);
        if (lv > before) {
            boolean layer = layers(lv) > layers(before);
            p.displayClientMessage(Component.literal("Appetite level " + lv + "! Food gives +" + Math.round(bonus(lv) * 100) + "%"
                    + (layer ? " - and you can hold an extra layer of hunger bars (" + (layers(lv) + 1) + " in all)!" : "")).withStyle(ChatFormatting.GOLD), !layer);
            if (layer) com.xenowarriors.network.PopupPacket.send(p, com.xenowarriors.network.PopupPacket.GOOD, p.getUUID(), ModCapabilities.getType(p).ordinal(), -1,
                    "Bigger appetite!", "+1 layer of hunger bars", "normal");
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8F, layer ? 0.7F : 1.2F);
            com.xenowarriors.statboost.StatBoosts.leveledUp(p, com.xenowarriors.statboost.BoostStat.APPETITE);
        }
    }

    /**
     * Eating: XP from the food, and the bonus points on top (after BigHunger has put the food itself in the stomach,
     * so the bonus lands in the right place).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || BigHunger.noHunger(p)) return;
        FoodProperties fp = event.getItem().getFoodProperties(p);
        if (fp == null || fp.getNutrition() <= 0) return;
        float b = bonus(level(p));
        if (b > 0) {
            int extra = Math.round(fp.getNutrition() * b);
            float sat = fp.getNutrition() * fp.getSaturationModifier() * 2F * b;
            BigHunger.addFood(p, extra, sat);
        }
        addXp(p, fp.getNutrition());
    }
}

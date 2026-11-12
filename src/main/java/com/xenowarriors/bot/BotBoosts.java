package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.statboost.BoostStat;
import com.xenowarriors.statboost.BoostTier;
import com.xenowarriors.statboost.StatBoostItem;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * A bot teammate given a Stat Boost (Attack, Speed, Defense, Building, Appetite, Mining, Crafting, Smelting) uses it on
 * itself: its character's levels in your save go up (Mining, Crafting and Smelting are shared by all your characters,
 * so those are yours too). It uses one boost a second from its bag or hand. Hekublade, Wires and Guns boosts aren't
 * its to use (they're the player's) - it keeps those for you.
 */
public final class BotBoosts {
    private BotBoosts() {}

    /** (For the tests) boosts bots have used. */
    public static int used;

    public static boolean usable(BoostStat s) {
        return switch (s) {
            case HEKUBLADE, WIRES, GUNS -> false;
            default -> true;
        };
    }

    /** Once a second from the bot's tick: use one boost it carries (if it can still gain from it). */
    public static void tick(PartyBotEntity b) {
        if (b.level().isClientSide || b.journey() || b.downed()) return;
        ServerPlayer o = b.owner();
        if (o == null) return;
        ItemStack hand = b.getMainHandItem();
        if (hand.getItem() instanceof StatBoostItem it && usable(it.stat) && use(b, o, it)) { hand.shrink(1); return; }
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (s.getItem() instanceof StatBoostItem it && usable(it.stat) && use(b, o, it)) {
                s.shrink(1);
                b.inventory.setChanged();
                return;
            }
        }
        if (b.tickCount % 100 < 20) passOn(b, o);                                 // (every 5 s: boosts it can't use any more go to someone who can)
    }

    /** (Tests) boosts handed on: to bots, to you. */
    public static int toBots, toPlayer;

    /** Maxed out in this stat (a bot: its character's level; shared ones are yours). */
    public static boolean maxed(PartyBotEntity b, ServerPlayer o, BoostStat stat) {
        return switch (stat) {
            case SPEED, ATTACK, DEFENSE -> o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> {
                if (d.getType() == b.character()) return stat.level(o) >= stat.max();
                int[] lv = d.charStats.getCompound(b.character().getId()).getIntArray("lv");
                return lv.length == 3 && lv[stat.ordinal()] >= Stat.MAX_LEVEL;
            }).orElse(true);
            case BUILDING -> com.xenowarriors.stats.Building.level(b) >= stat.max();
            case APPETITE -> com.xenowarriors.stats.Appetite.level(b) >= stat.max();
            case MINING, CRAFTING, SMELTING -> stat.level(o) >= stat.max();
            default -> true;
        };
    }

    /**
     * A boost it's maxed out on: it hands it to a teammate bot nearby that can still use it (that first), or else to
     * you - but never to you if you're maxed out on it too (then it keeps it).
     */
    static void passOn(PartyBotEntity b, ServerPlayer o) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!(s.getItem() instanceof StatBoostItem it) || !usable(it.stat) || !maxed(b, o, it.stat)) continue;
            PartyBotEntity to = null;
            for (PartyBotEntity x : PartyBots.bots(o))
                if (x != b && !x.downed() && !x.journey() && x.level() == b.level() && x.distanceToSqr(b) < 24 * 24 && !maxed(x, o, it.stat) && x.inventory.canAddItem(s)
                        && (to == null || x.distanceToSqr(b) < to.distanceToSqr(b))) to = x;
            if (to != null) {
                ItemStack give = s.split(s.getCount());
                to.inventory.addItem(give);
                b.inventory.setChanged();
                toBots++;
                o.displayClientMessage(Component.empty().append(b.label()).append(" has maxed " + it.stat.label + " - gives its " + it.stat.label + " Boost" + (give.getCount() > 1 ? "s" : "") + " to ").append(to.label()).withStyle(it.tier.color), true);
                b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return;
            }
            if (it.stat.usable(o) && it.stat.level(o) < it.stat.max() && o.level() == b.level() && o.distanceToSqr(b) < 24 * 24) {   // (you, only if you can still use it)
                ItemStack give = s.split(s.getCount());
                if (!o.getInventory().add(give)) o.drop(give, false);
                b.inventory.setChanged();
                toPlayer++;
                o.displayClientMessage(Component.empty().append(b.label()).append(" has maxed " + it.stat.label + " - gives you its " + it.stat.label + " Boost").withStyle(it.tier.color), true);
                b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return;
            }
        }
    }

    private static boolean use(PartyBotEntity b, ServerPlayer o, StatBoostItem it) {
        int gained = add(b, o, it.stat, it.tier.levels);
        if (gained <= 0) return false;
        used++;
        int before = b.botLevel();
        b.applyLevel(PartyBots.levelFor(o, b.character()));
        o.displayClientMessage(Component.literal("").append(b.label()).append(Component.literal(" used a " + it.stat.label + " Boost: +" + gained + " "
                + it.stat.label + (b.botLevel() > before ? "  (level " + b.botLevel() + ")" : ""))).withStyle(it.tier.color), true);
        if (b.level() instanceof ServerLevel sl) {
            sl.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, it.tier == BoostTier.HUNDRED ? 0.7F : 1.2F);
            sl.sendParticles(it.tier == BoostTier.HUNDRED ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER,
                    b.getX(), b.getY() + 1, b.getZ(), 10 + it.tier.ordinal() * 6, 0.4, 0.6, 0.4, 0.1);
        }
        return true;
    }

    /** Adds the levels to the bot's character (its stats in the owner's save); returns how many were added. */
    static int add(PartyBotEntity b, ServerPlayer o, BoostStat stat, int n) {
        CharacterType t = b.character();
        return switch (stat) {
            case SPEED, ATTACK, DEFENSE -> o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> {
                if (d.getType() == t) return 0;                                    // (you're playing it yourself)
                CompoundTag c = d.charStats.getCompound(t.getId());
                int[] lv = c.getIntArray("lv"); if (lv.length != 3) lv = new int[3];
                int i = stat.ordinal();
                int after = (int) Math.min((long) Stat.MAX_LEVEL, (long) lv[i] + n);
                int g = after - lv[i];
                if (g <= 0) return 0;
                lv[i] = after;
                c.putIntArray("lv", lv);
                if (after >= Stat.MAX_LEVEL) c.putFloat("x" + i, 0F);
                d.charStats.put(t.getId(), c);
                return g;
            }).orElse(0);
            case BUILDING -> com.xenowarriors.stats.Building.addLevels(b, n);
            case APPETITE -> com.xenowarriors.stats.Appetite.addLevels(b, n);
            case MINING, CRAFTING, SMELTING -> stat.add(o, n);                      // (shared by all your characters)
            default -> 0;
        };
    }
}

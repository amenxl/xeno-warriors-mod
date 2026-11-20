package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fire cooks the raw food you carry - Mikiko, and anyone with the element of fire (a fire character of your own, anyone
 * given fire): every few seconds one raw item in the inventory turns into its cooked version (what a furnace would make).
 * And a teammate with fire cooks yours too: someone in your party (a player or one of your bots) within 16 blocks who
 * has the element of fire, once your bond with them is {@value #COOK_BOND} or more.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoCook {
    public static final int INTERVAL = 60;   // one item every 3 seconds
    /** The bond you need with a fire teammate before they cook for you. */
    public static final int COOK_BOND = 50;
    /** (Tests) items cooked for someone by a teammate. */
    public static int forOthers;

    private AutoCook() {}

    /** Merge partial stacks of this food together so it doesn't spread over many slots. */
    private static void stackUp(Inventory inv, ItemStack like) {
        for (int a = 0; a < inv.items.size(); a++) {
            ItemStack into = inv.items.get(a);
            if (into.isEmpty() || !ItemStack.isSameItemSameTags(into, like) || into.getCount() >= into.getMaxStackSize()) continue;
            for (int b = a + 1; b < inv.items.size() && into.getCount() < into.getMaxStackSize(); b++) {
                ItemStack from = inv.items.get(b);
                if (from.isEmpty() || !ItemStack.isSameItemSameTags(from, into)) continue;
                int move = Math.min(from.getCount(), into.getMaxStackSize() - into.getCount());
                into.grow(move);
                from.shrink(move);
                if (from.isEmpty()) inv.items.set(b, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % INTERVAL != 0) return;
        if (p.isSpectator()) return;
        net.minecraft.world.entity.LivingEntity cook = com.xenowarriors.character.FireTorch.fire(p) ? p : cookFor(p);   // (Mikiko, anyone with fire - or a fire teammate you're close with)
        if (cook == null) return;
        cookOne(p, cook);
    }

    /** A teammate with fire near you, close enough (bond) to cook for you: a party member, or one of your bots. */
    public static net.minecraft.world.entity.LivingEntity cookFor(ServerPlayer p) {
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(p)) {
            if (m == p || m.level() != p.level() || m.distanceToSqr(p) > 16 * 16 || m.isSpectator() || !com.xenowarriors.character.FireTorch.fire(m)) continue;
            String c = com.xenowarriors.party.Bonds.charOf(m);
            if (c != null && com.xenowarriors.party.Bonds.level(p, c) >= COOK_BOND) return m;
        }
        for (var b : com.xenowarriors.bot.PartyBots.bots(p)) {
            if (b.level() != p.level() || b.downed() || b.distanceToSqr(p) > 16 * 16 || !com.xenowarriors.bot.BotSupport.fire(b)) continue;
            if (com.xenowarriors.party.Bonds.level(p, b.charKey()) >= COOK_BOND) return b;
        }
        return null;
    }

    /** One raw food item of this player's cooked (by them, or by the teammate {@code cook}). */
    public static boolean cookOne(ServerPlayer p, net.minecraft.world.entity.LivingEntity cook) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack raw = inv.items.get(i);
            if (raw.isEmpty()) continue;
            var recipe = p.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(raw), p.level());
            if (recipe.isEmpty()) continue;
            ItemStack cooked = recipe.get().getResultItem(p.level().registryAccess()).copy();
            if (cooked.isEmpty() || !cooked.isEdible()) continue;   // only food (not ores, sand...)
            // cooked food joins any stack of the same food he already has (or takes the raw item's slot)
            raw.shrink(1);
            if (raw.isEmpty()) inv.items.set(i, ItemStack.EMPTY);
            ItemStack kind = cooked.copy();             // add() empties the stack it's given
            if (!inv.add(cooked)) p.drop(cooked, false);
            stackUp(inv, kind);
            inv.setChanged();
            p.serverLevel().sendParticles(ParticleTypes.SMALL_FLAME, cook.getX(), cook.getY() + 1.0, cook.getZ(), 6, 0.25, 0.3, 0.25, 0.01);
            p.serverLevel().sendParticles(ParticleTypes.SMOKE, cook.getX(), cook.getY() + 1.2, cook.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            p.level().playSound(null, cook.getX(), cook.getY(), cook.getZ(), SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.PLAYERS, 0.5F, 1.2F);
            if (cook != p) {
                forOthers++;
                if (cook instanceof net.minecraft.world.entity.player.Player cp) cp.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true); else cook.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                net.minecraft.network.chat.Component who = cook instanceof com.xenowarriors.bot.PartyBotEntity b ? b.label() : cook.getName();
                p.displayClientMessage(net.minecraft.network.chat.Component.empty().append(who).append(" cooked your ").append(kind.getHoverName()).withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
            return true;   // one item per interval
        }
        return false;
    }
}

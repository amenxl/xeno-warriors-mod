package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Souls-like Universe mod's Healingstone (heals someone on your side): right-click one of your bots with it to heal
 * them; a bot carrying some uses one on you when you're hurt; Auto Potion uses them too. Each heals
 * {@value #SHARE} of max health. Senzu Beans (DragonMineZ) count for Auto Potion as well (see Apl).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealingStone {
    private HealingStone() {}

    public static final float SHARE = 0.05F;
    private static final ResourceLocation ID = new ResourceLocation("slu", "healingstone");
    /** (Tests) heals given with one. */
    public static int used;

    public static boolean is(ItemStack s) {
        if (s.isEmpty()) return false;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return ID.equals(id);
    }

    /** Heals them with one (the stack goes down by one). */
    public static void heal(LivingEntity who, ItemStack stack, LivingEntity by) {
        who.heal(who.getMaxHealth() * SHARE);
        stack.shrink(1);
        if (who.level() instanceof ServerLevel l) {
            l.sendParticles(ParticleTypes.HEART, who.getX(), who.getY() + who.getBbHeight() + 0.2, who.getZ(), 4, 0.3, 0.2, 0.3, 0);
            l.sendParticles(ParticleTypes.HAPPY_VILLAGER, who.getX(), who.getY() + 1, who.getZ(), 10, 0.4, 0.5, 0.4, 0);
            l.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 1.4F);
        }
        if (by != null) by.swing(InteractionHand.MAIN_HAND, true);
        used++;
    }

    /** You, holding one, right-click your bot (or a party member's): it's healed. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof PartyBotEntity b) || !is(e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!com.xenowarriors.bot.PartyBots.friendly(b, p)) return;
        if (b.getHealth() >= b.getMaxHealth()) { p.displayClientMessage(net.minecraft.network.chat.Component.literal(b.getName().getString() + " is already at full health"), true); return; }
        heal(b, e.getItemStack(), p);
    }

    /** A bot with Healingstones in its bag: you below 40% health and close by - it uses one on you (every 5 s at most). */
    public static void botTick(PartyBotEntity b) {
        if (b.tickCount % 20 != 13 || b.downed()) return;
        ServerPlayer o = b.owner();
        if (o == null || !o.isAlive() || o.level() != b.level() || o.distanceToSqr(b) > 6 * 6 || o.getHealth() >= o.getMaxHealth() * 0.4F) return;
        long now = b.level().getGameTime();
        if (now < b.getPersistentData().getLong("xw_stone_cd")) return;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!is(s)) continue;
            com.xenowarriors.bot.BotMoves.face(b, (float) Math.toDegrees(Math.atan2(-(o.getX() - b.getX()), o.getZ() - b.getZ())), 40F);
            heal(o, s, b);
            b.inventory.setChanged();
            b.getPersistentData().putLong("xw_stone_cd", now + 100);
            o.displayClientMessage(net.minecraft.network.chat.Component.literal(b.getName().getString() + " used a Healingstone on you"), true);
            return;
        }
    }

    /** Auto Potion: one from your inventory. True if it used one. */
    public static boolean autoUse(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (is(s)) { heal(p, s, p); return true; }
        }
        return false;
    }

    /** Auto Potion: a Senzu Bean (DragonMineZ: the plain one or the red Health one), eaten as it would be - if it isn't cooling down. */
    public static boolean senzu(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            var id = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (id == null || !id.getNamespace().equals("dragonminez") || !(id.getPath().equals("senzu_bean") || id.getPath().equals("senzu_bean_red"))) continue;
            if (p.getCooldowns().isOnCooldown(s.getItem())) return false;
            ItemStack left = s.getItem().finishUsingItem(s, p.level(), p);
            if (left != s) inv.setItem(i, left);
            return true;
        }
        return false;
    }
}

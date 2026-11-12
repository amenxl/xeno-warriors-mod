package com.xenowarriors.kaioken;

import com.xenowarriors.ModEffects;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Crimson Heart Seed (a super rare boss drop) and what it does.
 * <ul>
 *   <li>Eaten: for good - DragonMineZ's Kaioken never costs that player health again (kept through death and
 *       switching characters; the Crimson Control effect icon shows it). A bot that eats one: its character, for good. (DragonMineZ takes the health straight off each second; any health lost in Kaioken that no hit
 *       explains is put back.)</li>
 *   <li>Bosses drop it: {@link #BOSS_CHANCE} per boss killed by a player or a bot.</li>
 *   <li>Bots: a bot with one in its bag eats it just before it goes Kaioken (if it isn't already under the effect),
 *       and a bot with more than one hands a spare to a teammate bot nearby that has none.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KaiokenGuard {
    private KaiokenGuard() {}

    public static final int DURATION = 20 * 60 * 20;
    public static final float BOSS_CHANCE = 0.015F;
    /** (Tests) health put back, seeds dropped, eaten by bots, handed over by bots. */
    public static float restored;
    public static int dropped, botAte, botGave;

    private static final String FOREVER = "xw_crimson_heart", BOTS = "xw_crimson_bots";

    /** Eaten: Kaioken never costs this one health again (a player; a bot: its character, in its owner's save). */
    public static void give(LivingEntity e) {
        if (e instanceof Player p) {
            var pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            pers.putBoolean(FOREVER, true);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        } else if (e instanceof PartyBotEntity b && b.owner() != null) {
            var pers = b.owner().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            var bots = pers.getCompound(BOTS);
            bots.putBoolean(b.charKey(), true);
            pers.put(BOTS, bots);
            b.owner().getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        }
        icon(e);
    }

    /** The effect icon (it never runs out). */
    private static void icon(LivingEntity e) {
        if (!e.hasEffect(ModEffects.CRIMSON_CONTROL.get())) e.addEffect(new MobEffectInstance(ModEffects.CRIMSON_CONTROL.get(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
    }

    public static boolean guarded(LivingEntity e) {
        if (e instanceof Player p) return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(FOREVER) || e.hasEffect(ModEffects.CRIMSON_CONTROL.get());
        if (e instanceof PartyBotEntity b && b.owner() != null && b.owner().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(BOTS).getBoolean(b.charKey())) return true;
        return e.hasEffect(ModEffects.CRIMSON_CONTROL.get());
    }

    /** (Tests) forgets it. */
    public static void clearForTest(Player p) {
        var pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        pers.remove(FOREVER); pers.remove(BOTS);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        p.removeEffect(ModEffects.CRIMSON_CONTROL.get());
    }

    // ------------------------------------------------------------------ players: no Kaioken health cost

    /** player -> {health at the start of the tick, damage taken from real hits this tick} */
    private static final Map<UUID, float[]> HP = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        float[] h = HP.get(p.getUUID());
        if (h != null) h[1] += e.getAmount();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (p.tickCount % 100 == 41 && p.isAlive() && guarded(p)) {           // (the icon back after a death / a milk bucket; and on its bots)
            icon(p);
            for (PartyBotEntity b : PartyBots.bots(p)) if (guarded(b)) icon(b);
        }
        if (!guarded(p) || com.xenowarriors.compat.DmzBotPower.playerStack(p) == null) { HP.remove(p.getUUID()); return; }
        HP.put(p.getUUID(), new float[]{p.getHealth(), 0});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTickEnd(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        float[] h = HP.get(p.getUUID());
        if (h == null || !p.isAlive()) return;
        float lost = h[0] - p.getHealth() - h[1];                        // (what no hit explains: Kaioken's cost)
        if (lost > 0.001F) { p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + lost)); restored += lost; }
    }

    // ------------------------------------------------------------------ boss drops

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled() || victim instanceof Player || victim instanceof PartyBotEntity) return;
        if (!com.xenowarriors.karma.KarmaSensing.boss(victim) && !victim.getPersistentData().getBoolean("xw_boss") && !(victim instanceof com.xenowarriors.gov.UltimaPrototypeEntity)) return;
        Entity src = event.getSource().getEntity();
        if (src instanceof FakePlayer fp) { PartyBotEntity b = PartyBots.botOf(fp); if (b != null) src = b; }
        ServerPlayer owner = src instanceof ServerPlayer p && !(p instanceof FakePlayer) ? p : src instanceof PartyBotEntity b ? b.owner() : null;
        if (owner == null || victim.getRandom().nextFloat() >= BOSS_CHANCE * (forced ? 1000 : 1)) return;
        ItemEntity it = new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), new ItemStack(ModItems.CRIMSON_HEART_SEED.get()));
        it.setDefaultPickUpDelay();
        event.getDrops().add(it);
        dropped++;
        owner.sendSystemMessage(Component.literal(victim.getName().getString() + " dropped a Crimson Heart Seed!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    /** (Tests) every boss drops one. */
    public static boolean forced;

    // ------------------------------------------------------------------ bots

    private static int seedSlot(PartyBotEntity b) {
        var inv = b.inventory;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.CRIMSON_HEART_SEED.get())) return i;
        return -1;
    }

    private static int seeds(PartyBotEntity b) {
        int n = 0;
        var inv = b.inventory;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ModItems.CRIMSON_HEART_SEED.get())) n += inv.getItem(i).getCount();
        return n;
    }

    /** About to go Kaioken: eats a seed from its bag if it has one and isn't under the effect. True if it's guarded now. */
    public static boolean botBeforeKaioken(PartyBotEntity b) {
        if (guarded(b)) return true;
        int slot = seedSlot(b);
        if (slot < 0) return false;
        b.inventory.getItem(slot).shrink(1);
        give(b);
        botAte++;
        if (b.level() instanceof ServerLevel l) l.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.15F, 0.2F), 1.2F), b.getX(), b.getY() + 1.2, b.getZ(), 20, 0.3, 0.3, 0.3, 0);
        ServerPlayer o = b.owner();
        if (o != null) o.displayClientMessage(Component.empty().append(b.label()).append(" eats a Crimson Heart Seed - Kaioken won't hurt it now").withStyle(ChatFormatting.RED), true);
        return true;
    }

    /** A bot with a spare seed hands one to a teammate bot nearby that has none. */
    @SubscribeEvent
    public static void onOwnerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 100 != 23) return;
        share(p);
    }

    /** Bots with a spare seed hand one each to a teammate bot nearby that has none. */
    public static void share(ServerPlayer p) {
        var bots = PartyBots.bots(p);
        if (bots.size() < 2) return;
        for (PartyBotEntity giver : bots) {
            if (seeds(giver) < 2 || giver.getTarget() != null || giver.downed()) continue;
            for (PartyBotEntity taker : bots) {
                if (taker == giver || seeds(taker) > 0 || taker.distanceToSqr(giver) > 8 * 8 || !taker.inventory.canAddItem(new ItemStack(ModItems.CRIMSON_HEART_SEED.get()))) continue;
                int slot = seedSlot(giver);
                giver.inventory.getItem(slot).shrink(1);
                taker.inventory.addItem(new ItemStack(ModItems.CRIMSON_HEART_SEED.get()));
                giver.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                giver.getLookControl().setLookAt(taker);
                botGave++;
                p.displayClientMessage(Component.empty().append(giver.label()).append(" gives ").append(taker.label()).append(" a Crimson Heart Seed").withStyle(ChatFormatting.RED), true);
                break;
            }
        }
    }
}

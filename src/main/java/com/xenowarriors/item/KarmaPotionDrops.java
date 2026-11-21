package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enemies (anything hostile) killed by a player - or a player's bot - sometimes drop a Karma Potion (4%) or a Karma
 * Regeneration Potion (2.5%); each level of Looting adds a bit. Not in VR (nothing drops there) and not from story
 * training targets.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KarmaPotionDrops {
    private KarmaPotionDrops() {}

    public static final float KARMA = 0.04F, REGEN = 0.025F, PER_LOOTING = 0.01F;

    /** (Tests) every enemy drops both. */
    public static boolean forced;
    /** (Tests) potions dropped. */
    public static int dropped;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled() || !(victim instanceof Enemy) || victim instanceof Player || victim instanceof PartyBotEntity) return;
        if ("Target".equals(victim.getCustomName() == null ? null : victim.getCustomName().getString())) return;   // (story training dummies)
        Entity src = event.getSource().getEntity();
        if (src instanceof FakePlayer fp) { PartyBotEntity b = PartyBots.botOf(fp); if (b != null) src = b; }
        boolean byPlayer = src instanceof ServerPlayer p && !(p instanceof FakePlayer) || src instanceof PartyBotEntity b && b.ownerId() != null;
        if (!byPlayer) return;
        float bonus = PER_LOOTING * event.getLootingLevel();
        var r = victim.getRandom();
        if (forced || r.nextFloat() < KARMA + bonus) add(event, victim, new ItemStack(ModItems.KARMA_POTION.get()));
        if (forced || r.nextFloat() < REGEN + bonus) add(event, victim, new ItemStack(ModItems.KARMA_REGEN_POTION.get()));
    }

    private static void add(LivingDropsEvent event, LivingEntity v, ItemStack s) {
        ItemEntity it = new ItemEntity(v.level(), v.getX(), v.getY() + 0.5, v.getZ(), s);
        it.setDefaultPickUpDelay();
        event.getDrops().add(it);
        dropped++;
    }
}

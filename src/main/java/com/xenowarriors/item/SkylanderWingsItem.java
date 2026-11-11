package com.xenowarriors.item;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Skylander Wings: a Skylander's wings, dropped when one is killed (one in four; guardians half the time). A trophy
 * for everyone - but Mikiko eats them, and each pair gives him +10 levels in every stat (Speed, Attack, Defense,
 * Mining, Crafting and Smelting).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SkylanderWingsItem extends Item {
    public static final int LEVELS = 10;
    public static final float DROP = 0.25F, GUARDIAN_DROP = 0.5F;

    public SkylanderWingsItem(Properties props) {
        super(props.food(new FoodProperties.Builder().nutrition(6).saturationMod(0.8F).alwaysEat().build()));
    }

    private static boolean mikiko(Player p) {
        CharacterType t = p.level().isClientSide ? com.xenowarriors.you.You.clientType.apply(p) : ModCapabilities.getType(p);
        return t == CharacterType.MIKIKO;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (!mikiko(p)) {
            if (!level.isClientSide) p.displayClientMessage(Component.literal("Only Mikiko would eat these...").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(p.getItemInHand(hand));
        }
        return super.use(level, p, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        ItemStack r = super.finishUsingItem(stack, level, e);
        if (e instanceof ServerPlayer p && ModCapabilities.getType(p) == CharacterType.MIKIKO) grant(p);
        return r;
    }

    /** +10 levels to every stat and skill (capped at the max). */
    public static void grant(ServerPlayer p) {
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            for (int i = 0; i < d.statLevel.length; i++) d.statLevel[i] = Math.min(Stat.MAX_LEVEL, d.statLevel[i] + LEVELS);
            int m0 = d.miningLevel;
            d.miningLevel = Math.min(Stat.MAX_LEVEL, d.miningLevel + LEVELS);
            com.xenowarriors.stats.HandMining.leveled(p, m0, d.miningLevel);
            d.craftLevel = Math.min(Stat.MAX_LEVEL, d.craftLevel + LEVELS);
            d.smeltLevel = Math.min(Stat.MAX_LEVEL, d.smeltLevel + LEVELS);
        });
        com.xenowarriors.stats.Stats.reapply(p);
        com.xenowarriors.stats.Stats.syncAll(p);
        com.xenowarriors.stats.Mining.sync(p);
        p.displayClientMessage(Component.literal("+" + LEVELS + " to every stat!").withStyle(ChatFormatting.GOLD), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.2F);
        if (p.level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        XenoWarriors.LOGGER.info("XW-WINGS {} ate Skylander wings: stats now {}", p.getName().getString(),
                java.util.Arrays.toString(p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.statLevel.clone()).orElse(new int[0])));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Taken from a fallen Skylander").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Mikiko: eat for +" + LEVELS + " to every stat").withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof com.xenowarriors.npc.SkylanderEntity s) || s.level().isClientSide || s.wingless()) return;
        if (event.getSource().getEntity() == null) return;                      // killed by someone, not a fall or the void
        if (s.getRandom().nextFloat() >= (s.guardian() ? GUARDIAN_DROP : DROP)) return;
        event.getDrops().add(new ItemEntity(s.level(), s.getX(), s.getY() + 0.5, s.getZ(), new ItemStack(ModItems.SKYLANDER_WINGS.get())));
    }
}

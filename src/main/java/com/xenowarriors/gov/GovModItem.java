package com.xenowarriors.gov;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * A Government human-mod injector (the same mods Gov guards have): right-click to inject. Lasts 10 minutes.
 * Speed Mod, Jump Boosters, Scanner Eye (enemies near you glow through walls), Armor Plating.
 */
@Mod.EventBusSubscriber(modid = com.xenowarriors.XenoWarriors.MOD_ID)
public class GovModItem extends Item {
    public static final int SPEED = 0, JUMP = 1, SCANNER = 2, PLATING = 3;
    private static final int TICKS = 20 * 60 * 10;
    private static final String SCAN = "xw_scanner_until";
    private final int kind;

    public GovModItem(int kind, Properties p) { super(p); this.kind = kind; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide) {
            switch (kind) {
                case SPEED -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, TICKS, 1));
                case JUMP -> { p.addEffect(new MobEffectInstance(MobEffects.JUMP, TICKS, 2)); p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 8, 0)); p.getPersistentData().putLong("xw_jumpmod_until", level.getGameTime() + TICKS); }
                case SCANNER -> { p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, TICKS, 0)); p.getPersistentData().putLong(SCAN, level.getGameTime() + TICKS); }
                default -> { p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TICKS, 0)); p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, TICKS, 2)); }
            }
            level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.8F);
            p.displayClientMessage(Component.literal(getDescription().getString() + " installed - 10 minutes").withStyle(ChatFormatting.AQUA), true);
            if (!p.isCreative()) st.shrink(1);
            p.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack st, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(switch (kind) {
            case SPEED -> "Human mod: Speed II";
            case JUMP -> "Human mod: huge jumps, no fall damage";
            case SCANNER -> "Human mod: enemies near you glow through walls, night vision";
            default -> "Human mod: Resistance and extra hearts";
        }).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("10 minutes - right-click to inject").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Scanner Eye: every 2 seconds, mark enemies within 24 blocks so they glow through walls. Jump Boosters: no fall damage. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        if (p.getPersistentData().getLong(SCAN) <= p.level().getGameTime()) return;
        ServerLevel l = p.serverLevel();
        for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                t -> t != p && (t instanceof net.minecraft.world.entity.monster.Enemy || t instanceof GovAgentEntity)))
            t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
    }

    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent e) {
        if (e.getEntity() instanceof Player p && p.getPersistentData().getLong("xw_jumpmod_until") > p.level().getGameTime()) e.setDamageMultiplier(0F);
    }
}

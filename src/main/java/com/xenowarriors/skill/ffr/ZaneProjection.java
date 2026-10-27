package com.xenowarriors.skill.ffr;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zane's KA - Projection: "the real you isn't here". For 8 seconds attacks pass straight through him.
 * The cost: a projection hits softer (his own hits do half), and holding it drains him - he comes out slowed and hungry.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZaneProjection {
    private ZaneProjection() {}
    private static final int COST = 40, TICKS = 160;
    private static final Map<UUID, Long> ACTIVE = new HashMap<>();

    public static boolean active(Entity e) { Long t = ACTIVE.get(e.getUUID()); return t != null && t > e.level().getGameTime(); }

    public static boolean use(ServerPlayer p) {
        if (active(p)) return false;
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.trySpend(p, COST)) return false;
        ACTIVE.put(p.getUUID(), p.level().getGameTime() + TICKS);
        Component line = Component.literal(p.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("KA - Projection! The real me isn't here.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        for (ServerPlayer o : p.serverLevel().players()) if (o.distanceToSqr(p) < 40 * 40) o.displayClientMessage(line, true);
        p.serverLevel().sendParticles(ZaneSkills.MANA, p.getX(), p.getY() + 1, p.getZ(), 50, 0.4, 0.9, 0.4, 0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1F, 1.2F);
        com.xenowarriors.skill.ServerScheduler.later(TICKS, () -> {
            if (!p.isAlive()) return;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
            p.causeFoodExhaustion(8F);
            p.serverLevel().sendParticles(ZaneSkills.MANA, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.9, 0.4, 0);
        });
        return true;
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && active(p) && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            if (p.tickCount % 4 == 0) p.serverLevel().sendParticles(ZaneSkills.MANA, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.6, 0.3, 0);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer p && active(p)) e.setAmount(e.getAmount() * 0.5F);
    }
}

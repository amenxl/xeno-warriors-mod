package com.xenowarriors.recarnet;

import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.npc.SkylanderEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Royal Guard of Section 1: a Skylander knight in silver and gold who keeps the checkpoint at the Royal Capital's
 * landing platform (see {@link RoyalGate}). Talk to one to get checked in. Attacks the Sky Rebellion on sight.
 */
public class RoyalGuardEntity extends SkylanderEntity {
    public RoyalGuardEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createGuardAttributes() {
        return SkylanderEntity.createAttributes().add(Attributes.MAX_HEALTH, 50).add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    public String look() { return "sky_guard"; }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                e -> e instanceof Player p && !p.isCreative() && !p.isSpectator() && RoyalGate.skyRebellion(ModCapabilities.getType(p))));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, com.xenowarriors.sr.SrMemberEntity.class, true));
    }

    /** Posted at a spot (they stay near it and walk back after a fight). */
    public void post(BlockPos at) {
        makeGuardian(at);
        setCustomName(Component.literal("Royal Guard").withStyle(ChatFormatting.GOLD));
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(50);
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(7);
        setHealth(getMaxHealth());
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (p instanceof ServerPlayer sp) {
            getLookControl().setLookAt(p);
            RoyalGate.talk(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}

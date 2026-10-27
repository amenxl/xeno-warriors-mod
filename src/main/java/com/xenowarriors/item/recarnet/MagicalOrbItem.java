package com.xenowarriors.item.recarnet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Zane's Magical Orb: stores mana and the spells of past mages. Sneak + right-click picks Fire, Ice or Thunder;
 * right-click casts it (mana = Karma, a short cooldown).
 */
public class MagicalOrbItem extends Item {
    private static final String[] SPELLS = {"Fire", "Ice", "Thunder"};
    private static final ChatFormatting[] COLORS = {ChatFormatting.RED, ChatFormatting.AQUA, ChatFormatting.YELLOW};
    private static final float COST = 15F;

    public MagicalOrbItem(Properties p) { super(p); }

    private static int spell(ItemStack s) { return Math.floorMod(s.getOrCreateTag().getInt("Spell"), 3); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        ServerPlayer sp = (ServerPlayer) p;
        if (p.isShiftKeyDown()) {
            int s = (spell(st) + 1) % 3;
            st.getOrCreateTag().putInt("Spell", s);
            p.displayClientMessage(Component.literal("Orb: " + SPELLS[s]).withStyle(COLORS[s]), true);
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F + s * .2F);
            return InteractionResultHolder.success(st);
        }
        if (!com.xenowarriors.karma.Karma.trySpend(sp, COST)) { com.xenowarriors.karma.Karma.notEnough(sp, COST); return InteractionResultHolder.fail(st); }
        ServerLevel l = (ServerLevel) level;
        Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
        switch (spell(st)) {
            case 0 -> {
                for (int i = -1; i <= 1; i++) {
                    Vec3 d = look.yRot(i * 0.12F);
                    SmallFireball fb = new SmallFireball(l, p, d.x, d.y, d.z);
                    fb.setPos(eye.x + d.x, eye.y - 0.1, eye.z + d.z);
                    l.addFreshEntity(fb);
                }
                l.playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1F, 1.1F);
            }
            case 1 -> {
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6), e -> e != p && e.isAlive()
                        && !(e instanceof Player o && com.xenowarriors.party.Parties.sameParty(sp, o)))) {
                    e.hurt(l.damageSources().indirectMagic(p, p), 4F);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                    e.setTicksFrozen(Math.max(e.getTicksFrozen(), 160));
                }
                l.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 80, 3, 1, 3, 0.05);
                l.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, 1.6F);
            }
            default -> {
                HitResult hit = l.clip(new ClipContext(eye, eye.add(look.scale(28)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(l);
                if (bolt != null) { bolt.moveTo(hit.getLocation()); bolt.setCause(sp); l.addFreshEntity(bolt); }
            }
        }
        p.getCooldowns().addCooldown(this, 50);
        return InteractionResultHolder.success(st);
    }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Spell: " + SPELLS[spell(s)]).withStyle(COLORS[spell(s)]));
        tip.add(Component.literal("Right-click: cast (15 Karma). Sneak + right-click: next spell").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("\"Stores mana and the spells of past mages.\"").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}

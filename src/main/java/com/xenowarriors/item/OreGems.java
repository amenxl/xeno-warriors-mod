package com.xenowarriors.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;

/** What the newer ores' drops do. */
public final class OreGems {
    private OreGems() {}

    private static abstract class Usable extends Item {
        private final String tip; private final ChatFormatting col; private final int cooldown;
        Usable(Properties p, String tip, ChatFormatting col, int cooldown) { super(p); this.tip = tip; this.col = col; this.cooldown = cooldown; }
        abstract boolean act(ServerPlayer p, ItemStack st);
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
            ItemStack st = p.getItemInHand(hand);
            if (l.isClientSide) return InteractionResultHolder.success(st);
            if (!act((ServerPlayer) p, st)) return InteractionResultHolder.fail(st);
            if (!p.isCreative()) st.shrink(1);
            if (cooldown > 0) p.getCooldowns().addCooldown(this, cooldown);
            return InteractionResultHolder.consume(st);
        }
        @Override public void appendHoverText(ItemStack s, Level l, List<Component> tips, TooltipFlag f) { tips.add(Component.literal(tip).withStyle(col)); }
    }

    /** Moonstone (high in the mountains): night eyes and moon-light falling; Rhalix draws Karma from it. */
    public static class Moonstone extends Usable {
        public Moonstone(Properties p) { super(p, "Use: night vision and slow falling (Rhalix: +30 Karma)", ChatFormatting.AQUA, 40); }
        @Override boolean act(ServerPlayer p, ItemStack st) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0));
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
            if (com.xenowarriors.character.ModCapabilities.getType(p) == com.xenowarriors.character.CharacterType.RHALIX) com.xenowarriors.karma.Karma.add(p, 30F);
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 0.7F);
            return true;
        }
    }

    /** Karma Crystal (deep down): crushed, it gives you back 40 Karma. */
    public static class KarmaCrystal extends Usable {
        public KarmaCrystal(Properties p) { super(p, "Use: +40 Karma", ChatFormatting.GOLD, 60); }
        @Override boolean act(ServerPlayer p, ItemStack st) {
            com.xenowarriors.karma.Karma.add(p, 40F);
            p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(1F, 0.9F, 0.5F), 1F), p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.7, 0.4, 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1F, 1.3F);
            return true;
        }
    }

    /** Old World Fragment: a piece of the old world - read what's carved on it. */
    public static class OldWorldFragment extends Item {
        private static final String[] LORE = {
                "\"...the Fox King's crown answers only to fox blood...\"",
                "\"In the old world, a boy named Mikiko sat behind a rich kid called Gale.\"",
                "\"The Old Fox King has not forgotten the fake king.\"",
                "\"Len Kitsune gave her blood to save a child. She was never the same.\"",
                "\"Before the Keepers left, the palace was full.\"",
                "\"A devil named Duo walked here once. The souls still whisper his name.\"",
                "\"The demon sealed in the boy wants to come home. The other half does not.\"",
                "\"Without the moon-winged one, the black hole will swallow everything.\""};
        public OldWorldFragment(Properties p) { super(p); }
        @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
            ItemStack st = p.getItemInHand(hand);
            if (!l.isClientSide) {
                p.displayClientMessage(Component.literal(LORE[p.getRandom().nextInt(LORE.length)]).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                l.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1F, 0.7F);
                p.getCooldowns().addCooldown(this, 40);
            }
            return InteractionResultHolder.success(st);
        }
        @Override public void appendHoverText(ItemStack s, Level l, List<Component> tips, TooltipFlag f) {
            tips.add(Component.literal("Use: read what's carved on it").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Aurora Gem (snowy lands): like Yuri's tech, it maps the ores around you for a moment. */
    public static class AuroraGem extends Usable {
        public AuroraGem(Properties p) { super(p, "Use: shows the ores within 16 blocks for 15 seconds", ChatFormatting.GREEN, 100); }
        @Override boolean act(ServerPlayer p, ItemStack st) {
            ServerLevel l = p.serverLevel();
            List<BlockPos> ores = new java.util.ArrayList<>();
            BlockPos c = p.blockPosition();
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-16, -16, -16), c.offset(16, 16, 16)))
                if (l.getBlockState(q).is(net.minecraftforge.common.Tags.Blocks.ORES)) ores.add(q.immutable());
            p.displayClientMessage(Component.literal("Aurora scan: " + ores.size() + " ores nearby").withStyle(ChatFormatting.GREEN), true);
            for (int i = 0; i < 15; i++) com.xenowarriors.skill.ServerScheduler.later(i * 20, () -> {
                for (BlockPos q : ores) if (q.distSqr(p.blockPosition()) < 40 * 40)
                    l.sendParticles(p, new DustParticleOptions(new Vector3f(0.4F, 1F, 0.75F), 1.2F), true, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 2, 0.25, 0.25, 0.25, 0);
            });
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
            return true;
        }
    }

    /** Demonite Sword: hits very hard and makes them wither - but it feeds on you too. */
    public static class DemoniteSword extends SwordItem {
        public DemoniteSword(Properties p) { super(Tiers.NETHERITE, 6, -2.6F, p); }
        @Override public boolean hurtEnemy(ItemStack st, LivingEntity target, LivingEntity attacker) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0));
            if (attacker instanceof Player p && !p.isCreative()) p.hurt(p.damageSources().magic(), 1F);   // the curse
            return super.hurtEnemy(st, target, attacker);
        }
        @Override public void appendHoverText(ItemStack s, Level l, List<Component> tips, TooltipFlag f) {
            tips.add(Component.literal("Withers what it cuts").withStyle(ChatFormatting.DARK_RED));
            tips.add(Component.literal("Cursed: every hit costs you half a heart").withStyle(ChatFormatting.RED));
        }
    }
}

package com.xenowarriors.ultima;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;

/** Data Shard (from Data Ore): Ultima absorbs it for Data and a little healing. Also a crafting part for Gov tech. */
public class DataShardItem extends Item {
    public static final float DATA = 25F, HEAL = 3F;

    public DataShardItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (ModCapabilities.getType(p) != CharacterType.ULTIMA) return InteractionResultHolder.pass(st);
        if (!level.isClientSide && p instanceof ServerPlayer sp) {
            Karma.add(sp, DATA);
            sp.heal(HEAL);
            if (!sp.isCreative()) st.shrink(1);
            sp.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(.2F, 1F, .5F), 1.2F), sp.getX(), sp.getY() + 1.2, sp.getZ(), 18, .4, .6, .4, 0);
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.8F);
            sp.displayClientMessage(Component.literal("+" + (int) DATA + " Data").withStyle(ChatFormatting.GREEN), true);
            sp.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Ultima can absorb it (+Data, heals a bit)").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Used to build Government tech").withStyle(ChatFormatting.DARK_GRAY));
    }
}

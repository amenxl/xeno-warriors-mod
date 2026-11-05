package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.ka.Element;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fire in their hands: anyone with the element of fire (a fire You character, anyone given fire, and Mikiko always)
 * right-clicks a stick and lights it - one stick becomes a torch.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FireTorch {
    private FireTorch() {}

    /** Has the element of fire (Mikiko counts, whatever else). */
    public static boolean fire(Player p) {
        CharacterType t = p.level().isClientSide ? com.xenowarriors.you.You.clientType.apply(p) : ModCapabilities.getType(p);
        if (t == CharacterType.MIKIKO) return true;
        if (t == CharacterType.NONE) return false;
        var slot = t == CharacterType.YOU ? com.xenowarriors.you.You.slot(p) : null;
        if (slot != null && slot.element == Element.FIRE) return true;
        return com.xenowarriors.ka.Elements.of(p) == Element.FIRE;
    }

    /** Lights the stick in this hand; true if it did. */
    public static boolean light(Player p, InteractionHand hand) {
        ItemStack held = p.getItemInHand(hand);
        if (!held.is(Items.STICK) || !fire(p)) return false;
        if (p.level().isClientSide) return true;
        held.shrink(1);
        ItemStack torch = new ItemStack(Items.TORCH);
        if (held.isEmpty()) p.setItemInHand(hand, torch);
        else if (!p.getInventory().add(torch)) p.drop(torch, false);
        p.swing(hand, true);
        if (p.level() instanceof ServerLevel sl) {
            var look = p.getLookAngle();
            double x = p.getX() + look.x * 0.5, y = p.getEyeY() - 0.35, z = p.getZ() + look.z * 0.5;
            sl.sendParticles(ParticleTypes.FLAME, x, y, z, 8, 0.12, 0.12, 0.12, 0.02);
            sl.sendParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 3, 0.08, 0.08, 0.08, 0.01);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
        return true;
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        if (light(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }

    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()) return;                 // (sneaking: place / use the block as normal)
        if (light(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        }
    }
}

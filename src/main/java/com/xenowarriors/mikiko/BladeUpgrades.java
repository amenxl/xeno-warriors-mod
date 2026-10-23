package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.character.PlayerCharacter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Karma Altar upgrades on the Hekublade. Two paths, 3 levels each, and they stack:
 * <ul>
 *   <li><b>Sky Edge</b> (Sky Crystals): more damage, a lot more against anything in the air, and hits lift the target.</li>
 *   <li><b>Dark Edge</b> (Corrupted Shards): more damage and life drain; at level 3 hits wither.</li>
 * </ul>
 * The levels live on Mikiko (so a freshly summoned blade has them) and are copied onto the blade item, which is
 * what the renderer reads to make it glow sky blue / purple.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeUpgrades {
    public static final int MAX = 3;
    public static final String SKY = "XwSky", DARK = "XwDark";
    public static final int[] MATERIALS = {4, 8, 16};
    public static final int[] KARMA = {20, 40, 60};

    private BladeUpgrades() {}

    public static int sky(ItemStack s) { return s.hasTag() ? s.getTag().getInt(SKY) : 0; }
    public static int dark(ItemStack s) { return s.hasTag() ? s.getTag().getInt(DARK) : 0; }

    /** Stamps Mikiko's upgrade levels on a blade stack. */
    public static ItemStack apply(ServerPlayer p, ItemStack stack) {
        PlayerCharacter d = p.getCapability(ModCapabilities.PLAYER_CHARACTER).resolve().orElse(null);
        if (d == null) return stack;
        return stamp(stack, d.bladeSky, d.bladeDark);
    }

    public static ItemStack stamp(ItemStack stack, int sky, int dark) {
        CompoundTag tag = stack.getOrCreateTag();
        if (sky > 0) tag.putInt(SKY, sky); else tag.remove(SKY);
        if (dark > 0) tag.putInt(DARK, dark); else tag.remove(DARK);
        if (tag.isEmpty()) stack.setTag(null);
        return stack;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p) return;
        ItemStack blade = p.getMainHandItem();
        if (!Hekublade.isBlade(blade)) return;
        int sky = sky(blade), dark = dark(blade);
        if (sky == 0 && dark == 0) return;
        LivingEntity t = event.getEntity();
        ServerLevel level = p.serverLevel();
        float amount = event.getAmount();
        double y = t.getY() + t.getBbHeight() * 0.6;
        if (sky > 0) {
            amount += 0.75F * sky;
            if (!t.onGround() && !t.isInWater()) amount *= 1F + 0.15F * sky;      // made for fighting in the air
            t.setDeltaMovement(t.getDeltaMovement().add(0, 0.08 * sky, 0));
            t.hurtMarked = true;
            level.sendParticles(ParticleTypes.END_ROD, t.getX(), y, t.getZ(), 3 + sky * 2, 0.25, 0.3, 0.25, 0.06);
        }
        if (dark > 0) {
            amount += 0.75F * dark;
            p.heal(amount * 0.06F * dark);
            if (dark >= 3) t.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), p);
            level.sendParticles(ParticleTypes.SOUL, t.getX(), y, t.getZ(), 2 + dark, 0.25, 0.3, 0.25, 0.03);
            level.sendParticles(ParticleTypes.WITCH, t.getX(), y, t.getZ(), 3 + dark * 2, 0.25, 0.3, 0.25, 0.05);
        }
        event.setAmount(amount);
    }

    /** A faint trail of the edge's color off the blade while he holds it. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 8 != 0) return;
        ItemStack blade = p.getMainHandItem();
        if (!Hekublade.isBlade(blade)) return;
        int sky = sky(blade), dark = dark(blade);
        if (sky == 0 && dark == 0) return;
        float yaw = p.yBodyRot * ((float) Math.PI / 180F);
        double hx = p.getX() - Math.cos(yaw) * 0.45, hz = p.getZ() - Math.sin(yaw) * 0.45, hy = p.getY() + 0.9;
        if (sky > 0) p.serverLevel().sendParticles(ParticleTypes.END_ROD, hx, hy, hz, 1, 0.1, 0.4, 0.1, 0.005);
        if (dark > 0) p.serverLevel().sendParticles(ParticleTypes.WITCH, hx, hy, hz, 1, 0.1, 0.4, 0.1, 0.01);
    }
}

package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.sky.CorruptedArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Corrupted Armor, client side: the boots' extra air jump (a double jump - or a triple jump for Mikiko, who already
 * has one), and the full set's dark aura (with DragonMineZ: a purple aura crackling with lightning in a fight).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CorruptedArmorClient {
    private static boolean lastJump, used;
    private static int cooldownUntil;
    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.55F, 0.15F, 0.85F), 1.2F);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.12F, 0.06F, 0.16F), 1.4F);

    private CorruptedArmorClient() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        LocalPlayer me = mc.player;
        if (me != null) jump(mc, me);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!CorruptedArmorItem.fullSet(p)) continue;
            aura(mc, p);
        }
    }

    private static void jump(Minecraft mc, LocalPlayer p) {
        boolean down = mc.options.keyJump.isDown();
        boolean pressed = down && !lastJump;
        lastJump = down;
        if (p.onGround() || p.isInWater() || p.onClimbable()) { used = false; return; }
        if (!pressed || used || p.getAbilities().flying || p.isFallFlying() || p.isPassenger() || mc.screen != null) return;
        if (!(p.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof CorruptedArmorItem) || p.tickCount < cooldownUntil) return;
        // Mikiko already double jumps (when he can't fly yet): this is his third
        if (ClientCharacterData.getLocal() == CharacterType.MIKIKO && !p.getAbilities().mayfly
                && !com.xenowarriors.client.mikiko.MikikoAnimations.doubleJumpedBefore(p, 2)) return;
        used = true;
        cooldownUntil = p.tickCount + com.xenowarriors.sky.CorruptedArmorEffects.JUMP_COOLDOWN;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, 0.62, v.z);
        p.fallDistance = 0;
        com.xenowarriors.network.ModNetwork.sendToServer(new com.xenowarriors.network.DarkJumpPacket());
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            mc.level.addParticle(DARK, p.getX() + Math.cos(a) * 0.6, p.getY(), p.getZ() + Math.sin(a) * 0.6, Math.cos(a) * 0.15, -0.05, Math.sin(a) * 0.15);
        }
    }

    /** The full set: dark smoke curling off them; with DragonMineZ in a fight, a purple aura with lightning. */
    private static void aura(Minecraft mc, AbstractClientPlayer p) {
        var r = p.getRandom();
        if (r.nextInt(3) == 0)
            mc.level.addParticle(DARK, p.getX() + (r.nextDouble() - 0.5) * 0.9, p.getY() + r.nextDouble() * 1.8, p.getZ() + (r.nextDouble() - 0.5) * 0.9, 0, 0.03, 0);
        boolean dmz = net.minecraftforge.fml.ModList.get().isLoaded("dragonminez");
        boolean fighting = p.hurtTime > 0 || p.swinging || Surroundings.of(p) == Surroundings.ENEMIES;
        if (!dmz || !fighting) return;
        for (int i = 0; i < 3; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 0.55 + r.nextDouble() * 0.35;
            mc.level.addParticle(PURPLE, p.getX() + Math.cos(a) * rad, p.getY() + r.nextDouble() * 0.6, p.getZ() + Math.sin(a) * rad, 0, 0.18, 0);
        }
        if (r.nextInt(4) == 0)
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (r.nextDouble() - 0.5), p.getY() + r.nextDouble() * 2, p.getZ() + (r.nextDouble() - 0.5),
                    (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3, (r.nextDouble() - 0.5) * 0.3);
    }
}

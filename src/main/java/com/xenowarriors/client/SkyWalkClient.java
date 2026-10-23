package com.xenowarriors.client;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.sky.SkyArmorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sky Boots: hold sneak and walk off an edge - you stay at the height of the last block you stepped off, walking on
 * the sky for up to 200 blocks (it resets when you touch the ground again).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkyWalkClient {
    public static final double MAX_BLOCKS = 200;
    private static boolean active;
    private static double groundY, walked;
    private static int lastGround = -100;

    private SkyWalkClient() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.isPaused()) return;
        boolean boots = p.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof SkyArmorItem;
        if (p.onGround() && !active) {
            groundY = p.getY();
            lastGround = p.tickCount;
            walked = 0;
        }
        if (!boots || !p.isShiftKeyDown() || p.getAbilities().flying || p.isInWater() || p.isPassenger() || p.isFallFlying()
                || p.input.jumping) {
            if (active) end(p, false);
            return;
        }
        if (!active) {
            // just stepped off a ledge while sneaking
            if (!p.onGround() && p.tickCount - lastGround <= 3 && p.getY() > groundY - 0.7) {
                active = true;
                walked = 0;
            } else return;
        }
        // standing on solid ground again (stepped back onto a block at this height): that's normal walking
        if (p.level().getBlockState(p.blockPosition().below()).isSolid() && p.getY() - Math.floor(p.getY()) < 0.01 && p.onGround()
                && !p.level().noCollision(p, p.getBoundingBox().move(0, -0.05, 0))) {
            active = false;
            groundY = p.getY();
            walked = 0;
            return;
        }
        walked += Math.sqrt(Math.pow(p.getX() - p.xo, 2) + Math.pow(p.getZ() - p.zo, 2));
        if (walked >= MAX_BLOCKS) {
            end(p, true);
            return;
        }
        Vec3 v = p.getDeltaMovement();
        p.setPos(p.getX(), groundY, p.getZ());
        p.setDeltaMovement(v.x, 0, v.z);
        p.setOnGround(true);
        p.fallDistance = 0;
        if (p.tickCount % 3 == 0) p.level().addParticle(ParticleTypes.CLOUD, p.getX(), groundY, p.getZ(), 0, -0.02, 0);
        if (p.tickCount % 10 == 0)
            p.displayClientMessage(Component.literal("Sky walk: " + (int) (MAX_BLOCKS - walked) + " blocks left").withStyle(ChatFormatting.AQUA), true);
    }

    private static void end(LocalPlayer p, boolean faded) {
        active = false;
        if (faded) p.displayClientMessage(Component.literal("The sky won't hold you any further!").withStyle(ChatFormatting.GRAY), true);
    }
}

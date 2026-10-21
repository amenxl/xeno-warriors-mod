package com.xenowarriors.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.client.fox.FoxClientFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * DragonMineZ compatibility: when Mikiko turns Oozaru he becomes a giant fox - a huge Ember-coated fox (Golden Oozaru:
 * a golden one) that walks, runs and looks around with him - instead of a great ape.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GiantFox {
    private static final int EMBER = 1, GOLDEN = 5;
    private static final Map<Integer, Fox> FOXES = new HashMap<>();

    private GiantFox() {}

    private static Fox fox(AbstractClientPlayer p) {
        return FOXES.computeIfAbsent(p.getId(), id -> new Fox(EntityType.FOX, p.level()));
    }

    /** Keep each giant fox in step with its Mikiko. */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) {
            if (mc.level == null) FOXES.clear();
            return;
        }
        FOXES.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player pl) || !DmzForms.giantFox(pl));
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!DmzForms.giantFox(p)) continue;
            Fox f = fox(p);
            f.xo = p.xo; f.yo = p.yo; f.zo = p.zo;
            f.setPos(p.getX(), p.getY(), p.getZ());
            f.yRotO = p.yRotO; f.setYRot(p.getYRot());
            f.xRotO = p.xRotO; f.setXRot(p.getXRot());
            f.yBodyRotO = p.yBodyRotO; f.yBodyRot = p.yBodyRot;
            f.yHeadRotO = p.yHeadRotO; f.yHeadRot = p.yHeadRot;
            f.setOnGround(p.onGround());
            f.setSprinting(p.isSprinting());
            f.walkAnimation.update(Math.min(1F, p.walkAnimation.speed() * 1.2F), 1F);
            f.tickCount = p.tickCount;
            f.hurtTime = p.hurtTime;
            f.hurtDuration = p.hurtDuration;
            f.deathTime = p.deathTime;
            FoxClientFx.forceSkin(f.getId(), DmzForms.goldenFox(p) ? GOLDEN : EMBER);
            // a golden fox glows
            if (DmzForms.goldenFox(p) && p.tickCount % 2 == 0) {
                var r = p.getRandom();
                mc.level.addParticle(new DustParticleOptions(new Vector3f(1F, 0.85F, 0.3F), 2F), p.getX() + (r.nextDouble() - 0.5) * 4,
                        p.getY() + r.nextDouble() * 4, p.getZ() + (r.nextDouble() - 0.5) * 4, 0, 0.05, 0);
            }
        }
    }

    /** Draw the giant fox in place of Mikiko (sized to his Oozaru body). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRender(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!(p instanceof AbstractClientPlayer cp) || !DmzForms.giantFox(p)) return;
        event.setCanceled(true);
        Fox f = fox(cp);
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(f);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float scale = Math.max(4F, p.getBbHeight() / 0.7F * 0.75F);
        pose.scale(scale, scale, scale);
        float yaw = net.minecraft.util.Mth.rotLerp(event.getPartialTick(), p.yRotO, p.getYRot());
        renderer.render(f, yaw, event.getPartialTick(), pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    /** No human hands in first person while you're a giant fox. */
    @SubscribeEvent
    public static void onHands(RenderHandEvent event) {
        Player p = Minecraft.getInstance().player;
        if (p != null && DmzForms.giantFox(p)) event.setCanceled(true);
    }
}

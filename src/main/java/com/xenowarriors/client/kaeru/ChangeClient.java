package com.xenowarriors.client.kaeru;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.kaeru.KaeruChange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Draws Kaeru's Change: the silhouette phase (via the skin/detail hooks), then her as the creature, person or block. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChangeClient {
    public static final ResourceLocation SIL_SKIN = XenoWarriors.id("textures/entity/character/kaeru_silhouette.png");
    public static final ResourceLocation SIL_FACE = XenoWarriors.id("textures/entity/character/kaeru_silhouette_face.png");

    private record Form(int mode, String id) {}
    private static final Map<Integer, Form> FORMS = new HashMap<>();
    private static final Map<Integer, Entity> DISGUISE = new HashMap<>();

    private ChangeClient() {}

    public static void set(int entity, int mode, String form) {
        DISGUISE.remove(entity);
        if (mode == KaeruChange.NONE) FORMS.remove(entity); else FORMS.put(entity, new Form(mode, form));
    }

    public static boolean silhouette(int entity) { Form f = FORMS.get(entity); return f != null && f.mode == KaeruChange.SILHOUETTE; }

    private static Entity disguise(AbstractClientPlayer p, Form f) {
        Entity d = DISGUISE.get(p.getId());
        if (d != null) return d;
        ClientLevel level = (ClientLevel) p.level();
        if (f.id.startsWith("player:")) {
            Entity src = level.getEntity(Integer.parseInt(f.id.substring(7)));
            if (src instanceof Player sp) d = new RemotePlayer(level, sp.getGameProfile());
        } else {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(f.id));
            d = type.create(level);
        }
        if (d != null) { d.setId(-1000 - p.getId()); DISGUISE.put(p.getId(), d); }
        return d;
    }

    /** Keeps the stand-in walking and looking the way she does. */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) { if (mc.level == null) { FORMS.clear(); DISGUISE.clear(); } return; }
        for (var e : FORMS.entrySet()) {
            if (!(mc.level.getEntity(e.getKey()) instanceof AbstractClientPlayer p)) continue;
            Form f = e.getValue();
            if (f.mode == KaeruChange.SILHOUETTE) {
                if (mc.level.random.nextInt(4) == 0)
                    mc.level.addParticle(ParticleTypes.END_ROD, p.getRandomX(0.6), p.getRandomY(), p.getRandomZ(0.6), 0, 0.04, 0);
                continue;
            }
            if (f.mode != KaeruChange.CREATURE) continue;
            Entity d = disguise(p, f);
            if (d == null) continue;
            d.xo = d.getX(); d.yo = d.getY(); d.zo = d.getZ();
            d.setPos(p.getX(), p.getY(), p.getZ());
            d.xOld = p.xOld; d.yOld = p.yOld; d.zOld = p.zOld;
            d.yRotO = d.getYRot(); d.xRotO = d.getXRot();
            d.setYRot(p.getYRot()); d.setXRot(p.getXRot());
            d.setOnGround(p.onGround());
            d.tickCount++;
            if (d instanceof LivingEntity l) {
                l.yBodyRotO = l.yBodyRot; l.yHeadRotO = l.yHeadRot;
                l.yBodyRot = p.yBodyRot; l.yHeadRot = p.yHeadRot;
                float moved = (float) Mth.length(p.getX() - p.xo, 0, p.getZ() - p.zo);
                l.walkAnimation.update(Math.min(moved * 4F, 1F), 0.4F);
                l.swinging = p.swinging; l.attackAnim = p.attackAnim; l.oAttackAnim = p.oAttackAnim;
                l.setShiftKeyDown(p.isShiftKeyDown());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRender(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p)) return;
        Form f = FORMS.get(p.getId());
        if (f == null || f.mode == KaeruChange.SILHOUETTE) return;
        event.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        if (f.mode == KaeruChange.BLOCK) {
            BlockState bs = BuiltInRegistries.BLOCK.get(new ResourceLocation(f.id)).defaultBlockState();
            pose.pushPose();
            pose.translate(-0.5, 0, -0.5);
            mc.getBlockRenderer().renderSingleBlock(bs, pose, event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return;
        }
        Entity d = disguise(p, f);
        if (d == null) return;
        float yaw = Mth.lerp(event.getPartialTick(), d.yRotO, d.getYRot());
        mc.getEntityRenderDispatcher().render(d, 0, 0, 0, yaw, event.getPartialTick(), pose, event.getMultiBufferSource(), event.getPackedLight());
    }

    /** No name tag over a disguise. */
    @SubscribeEvent
    public static void onName(RenderNameTagEvent event) {
        Form f = FORMS.get(event.getEntity().getId());
        if (f != null && f.mode != KaeruChange.SILHOUETTE) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }
}

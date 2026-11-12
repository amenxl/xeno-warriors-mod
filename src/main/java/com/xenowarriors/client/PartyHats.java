package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/** A bot party on (BotPartyTime): party hats on everyone there, and confetti falling around the middle. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PartyHats {
    private PartyHats() {}

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(XenoWarriors.id("party_hat"), "main");
    private static final ResourceLocation TEX = XenoWarriors.id("textures/entity/party_hat.png");
    private static final Set<Integer> HATS = new HashSet<>();
    private static long until;
    private static BlockPos center = BlockPos.ZERO;
    /** Hat colours (picked by entity id). */
    private static final int[] COLORS = {0xFF4FA0, 0x40C0FF, 0xFFE040, 0x7CFF6A, 0xC070FF, 0xFF8A3A};
    private static final int[] CONFETTI = {0xFF4FA0, 0x40C0FF, 0xFFE040, 0x7CFF6A, 0xC070FF, 0xFFFFFF, 0xFF8A3A};

    public static void set(int[] ids, int ticks, BlockPos c) {
        HATS.clear();
        for (int id : ids) HATS.add(id);
        Minecraft mc = Minecraft.getInstance();
        until = ticks <= 0 || mc.level == null ? 0 : mc.level.getGameTime() + ticks + 40;
        center = c;
    }

    public static boolean wearing(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() < until && HATS.contains(e.getId());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused() || mc.level.getGameTime() >= until) return;
        var r = mc.level.random;
        for (int i = 0; i < 4; i++) {                                         // confetti drifting down around the party
            int c = CONFETTI[r.nextInt(CONFETTI.length)];
            var dust = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 0.9F);
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 6;
            mc.level.addParticle(dust, center.getX() + 0.5 + Math.cos(a) * d, center.getY() + 3.5 + r.nextDouble() * 2, center.getZ() + 0.5 + Math.sin(a) * d, 0, -0.05, 0);
        }
    }

    /** The hat on a humanoid's head. */
    public static final class Layer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        private ModelPart hat;
        public Layer(RenderLayerParent<T, M> p) { super(p); }
        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, T e, float limb, float amount, float pt, float age, float yaw, float pitch) {
            if (!wearing(e) || e.isInvisible()) return;
            if (hat == null) hat = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
            int c = COLORS[Math.floorMod(e.getId(), COLORS.length)];
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            hat.render(pose, buf.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, 1F);
            pose.popPose();
        }
    }

    /** A cone: stacked, narrowing boxes with a pom-pom, tilted a little. */
    static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition cone = root.addOrReplaceChild("cone", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3F, -2F, -3F, 6, 2, 6)
                        .texOffs(0, 8).addBox(-2.25F, -4F, -2.25F, 4.5F, 2, 4.5F)
                        .texOffs(0, 16).addBox(-1.5F, -6F, -1.5F, 3, 2, 3)
                        .texOffs(16, 16).addBox(-0.75F, -8F, -0.75F, 1.5F, 2, 1.5F)
                        .texOffs(24, 0).addBox(-1F, -9.8F, -1F, 2, 2, 2),
                PartPose.offsetAndRotation(1.2F, -8F, -0.5F, -0.1F, 0F, 0.18F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class HatReg {
        @SubscribeEvent
        public static void hatLayers(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(LAYER, PartyHats::create); }
    }
}

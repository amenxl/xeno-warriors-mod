package com.xenowarriors.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.entity.HekubladeShardEntity;
import com.xenowarriors.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A floating Hekublade copy, pointed at where Mikiko is looking (or where it's flying), with a pale glow trail. */
public class HekubladeShardRenderer extends EntityRenderer<HekubladeShardEntity> {
    private final ItemStack blade = new ItemStack(ModItems.HEKUBLADE.get());

    public HekubladeShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(HekubladeShardEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(HekubladeShardEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity owner = e.ownerEntity();
        Vec3 dir;
        if (!e.fired() && owner != null) {
            Vec3 at = e.hoverPos(owner, partialTick);
            Vec3 aim = owner instanceof LivingEntity l
                    ? l.getEyePosition(partialTick).add(l.getViewVector(partialTick).scale(24)) : at.add(0, 0, 1);
            // render at the smooth hover spot (the entity's own position only updates per tick)
            Vec3 ent = new Vec3(Mth.lerp(partialTick, e.xOld, e.getX()), Mth.lerp(partialTick, e.yOld, e.getY()), Mth.lerp(partialTick, e.zOld, e.getZ()));
            Vec3 off = at.subtract(ent);
            pose.translate(off.x, off.y, off.z);
            dir = aim.subtract(at);
        } else {
            dir = e.getDeltaMovement();
        }
        if (dir.lengthSqr() < 1.0E-6) dir = new Vec3(0, 0, 1);
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        float grow = Mth.clamp((e.tickCount + partialTick) / 5F, 0F, 1F);

        pose.pushPose();
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d));
        pose.mulPose(Axis.ZP.rotationDegrees(45));           // the model's blade runs diagonally; line it up with +Y
        pose.scale(1.5F * grow, 1.5F * grow, 1.5F * grow);
        Minecraft.getInstance().getItemRenderer().renderStatic(blade, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, e.level(), e.getId());
        pose.popPose();

        // pale glow streak behind it
        float len = e.fired() ? 2.2F : 0.6F;
        FxDraw.beam(pose, FxDraw.glow(buffers), -d.x() * len, -d.y() * len, -d.z() * len, e.fired() ? 0.14F : 0.06F,
                1F, 0.92F, 0.85F, e.fired() ? 0.55F : 0.25F);
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HekubladeShardEntity e) {
        return FxDraw.WHITE;
    }
}

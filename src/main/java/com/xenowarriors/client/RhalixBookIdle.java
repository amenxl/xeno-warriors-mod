package com.xenowarriors.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Rhalix's Origins outfits have a reading emote, played when he stands about bare-handed:
 * <ul>
 * <li>Origins 1: pushes his glasses up his nose, summons a book in a puff of violet, opens it and reads;</li>
 * <li>Origins 2: summons the book onto his palm, spins it round on his hand, catches it, opens it and reads.</li>
 * </ul>
 * He keeps reading (turning a page now and then) until he moves; then the book is gone.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixBookIdle {
    private static final int AFTER = 120;                      // ticks standing still first
    private static final ResourceLocation BOOK_TEX = new ResourceLocation("textures/entity/enchanting_table_book.png");
    private static final Map<Integer, int[]> STILL = new HashMap<>();   // id -> {ticks still}
    private static BookModel book;

    private RhalixBookIdle() {}

    public static boolean hasBook(Player p) {
        int o = ClientCharacterData.rhalixOutfit(p);
        return o == ClientCharacterData.RHALIX_ORIGINS_1 || o == ClientCharacterData.RHALIX_ORIGINS_2;
    }

    private static boolean calm(AbstractClientPlayer p) {
        return Math.abs(p.getX() - p.xo) + Math.abs(p.getZ() - p.zo) < 0.02 && !p.swinging && p.onGround() && !Busy.busy(p)
                && !p.isCrouching() && !p.isPassenger() && !p.isUsingItem() && !FlightTracker.isFlying(p) && !p.isSleeping()
                && p.getMainHandItem().isEmpty() && !RhalixWingsClient.busy(p) && p.hurtTime == 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) return;
        STILL.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (!hasBook(p)) { STILL.remove(p.getId()); continue; }
            int[] s = STILL.computeIfAbsent(p.getId(), k -> new int[1]);
            s[0] = calm(p) ? s[0] + 1 : 0;
            float t = s[0] - AFTER;
            int appear = ClientCharacterData.rhalixOutfit(p) == ClientCharacterData.RHALIX_ORIGINS_1 ? 34 : 4;
            if (t >= appear && t < appear + 5) puff(mc, p);
        }
    }

    private static void puff(Minecraft mc, AbstractClientPlayer p) {
        var r = p.getRandom();
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = p.getX() - Mth.sin(yaw) * 0.55, fz = p.getZ() + Mth.cos(yaw) * 0.55;
        for (int i = 0; i < 3; i++) {
            mc.level.addParticle(new DustParticleOptions(new Vector3f(0.65F, 0.35F, 1F), 0.7F),
                    fx + (r.nextDouble() - 0.5) * 0.4, p.getY() + 1.0 + r.nextDouble() * 0.4, fz + (r.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
        }
    }

    /** Ticks into the emote (partial-tick smooth), or -1. */
    public static float time(Player p, float partial) {
        int[] s = STILL.get(p.getId());
        if (s == null || s[0] < AFTER) return -1F;
        return s[0] - AFTER + partial;
    }

    public static boolean active(Player p) {
        return time(p, 0F) >= 0F;
    }

    private static float sm(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3 - 2 * x);
    }

    private static void set(net.minecraft.client.model.geom.ModelPart part, float x, float y, float z, float w) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    /** How the book is held right now: {stage (0 none, 1 on his palm, 2 in both hands), scale 0..1, spin radians, openness}. */
    private static float[] bookState(Player p, float t) {
        boolean o1 = ClientCharacterData.rhalixOutfit(p) == ClientCharacterData.RHALIX_ORIGINS_1;
        if (o1) {
            if (t < 34) return new float[]{0, 0, 0, 0};
            if (t < 50) return new float[]{1, sm((t - 34) / 6F), 0, 0};
            return new float[]{2, 1, 0, sm((t - 50) / 10F)};
        }
        if (t < 4) return new float[]{0, 0, 0, 0};
        if (t < 52) {
            float spin = t < 12 ? 0F : (float) (Math.PI * 4 * sm((t - 12) / 36F));   // two full turns on his palm
            return new float[]{1, sm((t - 4) / 6F), spin, 0};
        }
        return new float[]{2, 1, 0, sm((t - 52) / 10F)};
    }

    public static void apply(HumanoidModel<?> m, Player p, float age) {
        if (!hasBook(p)) return;
        float t = time(p, Minecraft.getInstance().getFrameTime());
        if (t < 0F) return;
        boolean o1 = ClientCharacterData.rhalixOutfit(p) == ClientCharacterData.RHALIX_ORIGINS_1;
        float in = sm(t / 6F);
        float read = sm((t - (o1 ? 46 : 50)) / 8F);              // into the reading pose
        if (o1) {
            // push the glasses up with his right hand: hand to his face, a little nudge, back down
            float g = sm(t / 7F) * (1F - sm((t - 24F) / 7F));
            float nudge = t > 9 && t < 20 ? Mth.sin((t - 9) / 11F * (float) Math.PI) * 0.12F : 0F;
            set(m.rightArm, -1.75F - nudge, -0.62F, 0.15F, g);
            if (g > 0.01F) LimbBend.extraElbows(p, 1.75F * g, 0F);
            m.head.xRot = Mth.lerp(g, m.head.xRot, 0.05F);
            // left hand out, palm up: the book appears on it
            float palm = sm((t - 28F) / 6F) * (1F - read);
            set(m.leftArm, -1.05F, 0.25F, -0.05F, palm);
        } else {
            // right hand out, palm up, the book spinning on it
            float palm = in * (1F - read);
            set(m.rightArm, -1.25F, -0.15F, 0.05F, palm);
            if (palm > 0.01F) LimbBend.extraElbows(p, 0.35F * palm, 0F);
            m.head.xRot = Mth.lerp(palm, m.head.xRot, 0.35F);    // watching it spin
        }
        if (read > 0F) {
            // both hands holding it open in front of him, head bent over it, a page turned now and then
            float turn = (t % 90F) > 80F ? Mth.sin(((t % 90F) - 80F) / 10F * (float) Math.PI) * 0.25F : 0F;
            set(m.rightArm, -0.95F - turn, -0.38F, 0.05F, read);
            set(m.leftArm, -0.95F, 0.38F, -0.05F, read);
            LimbBend.extraElbows(p, 0.5F * read, 0.5F * read);
            m.head.xRot = Mth.lerp(read, m.head.xRot, 0.42F + Mth.sin(age * 0.05F) * 0.03F);
            m.head.yRot = Mth.lerp(read, m.head.yRot, 0F);
        }
        m.hat.copyFrom(m.head);
    }

    /** Draws the book (model space, after the player model). */
    public static void render(Player p, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
        if (!hasBook(p)) return;
        float t = time(p, partial);
        if (t < 0F) return;
        float[] st = bookState(p, t);
        if (st[0] == 0F || st[1] <= 0.01F) return;
        if (book == null) book = new BookModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BOOK));
        boolean o1 = ClientCharacterData.rhalixOutfit(p) == ClientCharacterData.RHALIX_ORIGINS_1;
        pose.pushPose();
        if (st[0] == 1F) {
            // standing on his palm (Origins 1: left hand; Origins 2: right hand), spinning round
            pose.translate((o1 ? 4.6F : -4.6F) / 16F, -0.6F / 16F, -9.6F / 16F);
            pose.mulPose(Axis.YP.rotation(st[2]));
        } else {
            pose.translate(0F, 4.4F / 16F, -10.2F / 16F);        // between his hands, held up in front of him
            pose.mulPose(Axis.XP.rotationDegrees(-30F));          // tilted up toward his face
        }
        pose.mulPose(Axis.ZP.rotationDegrees(180F));              // (model space is upside down)
        pose.mulPose(Axis.YP.rotationDegrees(-90F));              // pages open toward him
        float s = (st[0] == 1F ? 0.55F : 0.78F) * st[1];      // (bigger once it's open in both hands)
        pose.scale(s, s, s);
        float flip = (t % 90F) > 80F ? ((t % 90F) - 80F) / 10F : 0F;
        book.setupAnim(t, flip, 0F, st[3]);
        book.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutout(BOOK_TEX)), light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pose.popPose();
    }
}

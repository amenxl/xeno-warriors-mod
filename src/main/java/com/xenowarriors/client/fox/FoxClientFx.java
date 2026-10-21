package com.xenowarriors.client.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.fox.FoxSkins;
import com.xenowarriors.network.FoxFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How pet foxes look and move on the client: their skin, a wagging tail, happy head tilts, and the
 * one-off moves (a spin when they level up, a hop when they see you again, dashing off when they run away).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FoxClientFx {
    private static final Map<Integer, Integer> SKINS = new HashMap<>();
    private static final Map<Integer, Integer> PREVIEW = new HashMap<>();
    private static final Map<Integer, long[]> FX = new HashMap<>();     // id -> {kind, start game time}
    private static final int[] FX_LENGTH = {30, 24, 20};
    private static Field[] partFields;

    private FoxClientFx() {}

    public static void setSkin(int id, int skin) {
        if (skin < 0) SKINS.remove(id);     // released back to the wild
        else SKINS.put(id, skin);
    }

    public static boolean isPet(Fox fox) {
        return SKINS.containsKey(fox.getId());
    }

    public static void preview(int id, int skin) {
        if (skin < 0) PREVIEW.remove(id);
        else PREVIEW.put(id, skin);
    }

    public static void play(int id, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FX.put(id, new long[]{kind, mc.level.getGameTime()});
        Entity e = mc.level.getEntity(id);
        if (e != null && kind == FoxFxPacket.RUN_AWAY) {
            for (int i = 0; i < 12; i++) {
                mc.level.addParticle(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.3, e.getZ(),
                        (mc.level.random.nextDouble() - 0.5) * 0.15, 0.05, (mc.level.random.nextDouble() - 0.5) * 0.15);
            }
        }
    }

    public static boolean runningAway(int id) {
        long[] f = FX.get(id);
        return f != null && f[0] == FoxFxPacket.RUN_AWAY;
    }

    /** The texture for a pet fox wearing a skin (null = vanilla red/snow). */
    /** Show this (client-only) fox in a given skin. */
    public static void forceSkin(int id, int skin) {
        PREVIEW.put(id, skin);
    }

    public static ResourceLocation texture(Fox fox) {
        Integer skin = PREVIEW.getOrDefault(fox.getId(), SKINS.get(fox.getId()));
        if (skin == null || skin <= 0) return null;
        return XenoWarriors.id("textures/entity/fox/" + FoxSkins.get(skin).id() + (fox.isSleeping() ? "_sleep" : "") + ".png");
    }

    // ------------------------------------------------------------ model parts

    /** head, body, right hind, left hind, right front, left front, tail (null if the model is unexpected). */
    public static ModelPart[] parts(FoxModel<?> model) {
        try {
            if (partFields == null) {
                List<Field> found = new ArrayList<>();
                for (Field f : FoxModel.class.getDeclaredFields()) {
                    if (f.getType() == ModelPart.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        found.add(f);
                    }
                }
                partFields = found.toArray(new Field[0]);
            }
            if (partFields.length != 7) return null;
            ModelPart[] out = new ModelPart[7];
            for (int i = 0; i < 7; i++) out[i] = (ModelPart) partFields[i].get(model);
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            partFields = new Field[0];
            return null;
        }
    }

    // ------------------------------------------------------------ one-off moves

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            SKINS.clear();
            FX.clear();
            PREVIEW.clear();
            return;
        }
        if (level.getGameTime() % 100 == 0) SKINS.keySet().removeIf(id -> level.getEntity(id) == null);
        FX.entrySet().removeIf(en -> level.getGameTime() - en.getValue()[1] > FX_LENGTH[(int) en.getValue()[0]] + 10);
        for (var en : FX.entrySet()) {
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof Fox fox)) continue;
            int kind = (int) en.getValue()[0];
            long t = level.getGameTime() - en.getValue()[1];
            if (kind == FoxFxPacket.RUN_AWAY) {
                fox.deathTime = 0;       // no falling over: it dashes off instead
                if (t % 2 == 0) level.addParticle(ParticleTypes.POOF, fox.getX(), fox.getY() + 0.2, fox.getZ(), 0, 0.02, 0);
            } else if (kind == FoxFxPacket.LEVEL_UP && t < 30 && t % 3 == 0) {
                double a = t * 0.6;
                level.addParticle(ParticleTypes.END_ROD, fox.getX() + Math.cos(a) * 0.6, fox.getY() + 0.3 + t * 0.03,
                        fox.getZ() + Math.sin(a) * 0.6, 0, 0.02, 0);
            }
        }
    }

    private static final Map<Integer, Boolean> PUSHED = new HashMap<>();

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Fox fox)) return;
        long[] f = FX.get(fox.getId());
        Minecraft mc = Minecraft.getInstance();
        if (f == null || mc.level == null) return;
        int kind = (int) f[0];
        float t = mc.level.getGameTime() - f[1] + event.getPartialTick();
        float len = FX_LENGTH[kind];
        if (t > len) {
            if (kind == FoxFxPacket.RUN_AWAY) event.setCanceled(true);   // gone
            return;
        }
        float p = t / len;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.put(fox.getId(), true);
        switch (kind) {
            case FoxFxPacket.LEVEL_UP -> {
                // jumps up and spins all the way around
                pose.translate(0, Mth.sin(p * Mth.PI) * 0.7F, 0);
                pose.mulPose(Axis.YP.rotationDegrees(p * p * (3 - 2 * p) * 360F));
            }
            case FoxFxPacket.GREET -> {
                // two happy hops, wiggling
                pose.translate(0, Math.abs(Mth.sin(p * Mth.PI * 2F)) * 0.35F, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(p * Mth.PI * 6F) * 10F * (1 - p)));
            }
            case FoxFxPacket.RUN_AWAY -> {
                // bolts off the way it's facing, shrinking into the distance
                float yaw = fox.getYRot() * Mth.DEG_TO_RAD;
                float d = p * p * 5F;
                pose.translate(-Mth.sin(yaw) * d, Math.abs(Mth.sin(p * Mth.PI * 4F)) * 0.15F, Mth.cos(yaw) * d);
                float s = 1F - p * 0.6F;
                pose.scale(s, s, s);
            }
            default -> { }
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId()) != null) event.getPoseStack().popPose();
    }
}

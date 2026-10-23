package com.xenowarriors.client.party;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.PartySyncPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client side of parties and battles: party members, the right-click menu, and knocked-out players. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PartyClient {
    private static List<PartySyncPacket.Member> members = List.of();
    private static final Map<UUID, Long> KNOCKED = new HashMap<>();     // player -> game time they get up
    private static CameraType cameraBefore;

    private PartyClient() {}

    public static void setMembers(List<PartySyncPacket.Member> list) {
        members = list;
    }

    public static List<PartySyncPacket.Member> members() {
        return members;
    }

    public static boolean inMyParty(UUID id) {
        for (var m : members) if (m.id().equals(id)) return true;
        return false;
    }

    public static boolean amLeader() {
        Minecraft mc = Minecraft.getInstance();
        for (var m : members) if (mc.player != null && m.id().equals(mc.player.getUUID())) return m.leader();
        return false;
    }

    public static void setKnockedOut(UUID player, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks <= 0 || mc.level == null) KNOCKED.remove(player);
        else KNOCKED.put(player, mc.level.getGameTime() + ticks);
    }

    public static boolean knockedOut(Player p) {
        return KNOCKED.containsKey(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        members = List.of();
        KNOCKED.clear();
    }

    /** Right-click another player (empty hand): the party / battle menu. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide || !(event.getTarget() instanceof Player target) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getItemStack().isEmpty() || event.getEntity().isShiftKeyDown()) return;
        Minecraft.getInstance().setScreen(new PlayerActionScreen(target.getUUID(), target.getName().getString()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        KNOCKED.values().removeIf(t -> now > t + 40);
        // dazed: little stars circling the head of anyone knocked out
        for (Player p : mc.level.players()) {
            if (!knockedOut(p) || p.tickCount % 3 != 0) continue;
            Vec3 head = headPos(p);
            double a = p.tickCount * 0.35;
            for (int i = 0; i < 3; i++) {
                double b = a + i * Math.PI * 2 / 3;
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, head.x + Math.cos(b) * 0.35, head.y + 0.35, head.z + Math.sin(b) * 0.35, 0, 0, 0);
            }
        }
        // third person while you're knocked out, so you can see yourself lying there
        boolean ko = mc.player != null && knockedOut(mc.player);
        if (ko && cameraBefore == null) {
            cameraBefore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!ko && cameraBefore != null) {
            mc.options.setCameraType(cameraBefore);
            cameraBefore = null;
        }
    }

    /** Knocked out or downed: no walking, jumping or crouching. */
    @SubscribeEvent
    public static void onMoveInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (!knockedOut(event.getEntity())) return;
        var in = event.getInput();
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** ...and no attacking, using items or picking blocks. */
    @SubscribeEvent
    public static void onInteractKey(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && knockedOut(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Lying on his back: the head ends up behind where he stood. */
    private static Vec3 headPos(Player p) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        return p.position().add(fwd.scale(-1.5)).add(0, 0.2, 0);
    }

    private static final Set<Integer> LAID = new HashSet<>();

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!knockedOut(p)) return;
        PoseStack pose = event.getPoseStack();
        com.xenowarriors.client.PoseGuard.push(pose);
        LAID.add(p.getId());
        float yaw = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        // fall back onto his back: rotate about his right-hand axis
        pose.translate(0, 0.15, 0);
        pose.mulPose(Axis.of(new org.joml.Vector3f(-Mth.cos(yaw), 0, -Mth.sin(yaw))).rotationDegrees(90F));
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (LAID.remove(event.getEntity().getId())) com.xenowarriors.client.PoseGuard.pop(event.getPoseStack());
    }

    /** Limp, arms flung out. */
    public static void applyPose(HumanoidModel<?> m, Player p, float age) {
        if (age == 0F || !knockedOut(p)) return;
        m.rightArm.xRot = 0.1F;
        m.rightArm.yRot = 0F;
        m.rightArm.zRot = 1.25F;
        m.leftArm.xRot = 0.1F;
        m.leftArm.yRot = 0F;
        m.leftArm.zRot = -1.1F;
        m.rightLeg.xRot = 0F;
        m.rightLeg.zRot = 0.18F;
        m.leftLeg.xRot = 0.05F;
        m.leftLeg.zRot = -0.12F;
        m.head.xRot = 0F;
        m.head.yRot = 0.5F + Mth.sin(age * 0.08F) * 0.15F;      // head lolling
        m.head.zRot = 0F;
        m.body.xRot = 0F;
    }
}

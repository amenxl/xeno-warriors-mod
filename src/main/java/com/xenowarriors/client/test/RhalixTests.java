package com.xenowarriors.client.test;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * UI tests for Rhalix's Season 1 ("Recarnet 1") model (focuses starting with "rhalix", run from UiTest): the same set
 * of shots before and after a model change - standing from every side, close-ups of the head, waist and boots, wings
 * put away, mid-stride (the walk pose is held still, so both runs show the same stride), an attack swing, a skill
 * pose, flying with the wings out, and in iron armor. Each shot is filmed from an invisible armor stand placed around
 * him, so the angles match between runs. Results go to the log as "XW-TEST rhalix ...".
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RhalixTests {
    /** Adds one step to the UI test: a screenshot name (or null), what to do, and how many ticks to wait after. */
    public interface Steps { void add(String shot, Runnable action, int delay); }

    private RhalixTests() {}

    public static void log(String s) { XenoWarriors.LOGGER.info("XW-TEST rhalix " + s); }

    private static ServerPlayer sp() {
        Minecraft mc = Minecraft.getInstance();
        var s = mc.getSingleplayerServer();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) {
        var s = Minecraft.getInstance().getSingleplayerServer();
        if (s != null) s.execute(() -> { ServerPlayer p = sp(); if (p != null) try { f.accept(p); } catch (Throwable t) { XenoWarriors.LOGGER.error("XW-TEST rhalix server step failed", t); } });
    }

    // ------------------------------------------------------------------ a held walk pose

    /** While set: {walk position, speed} forced on the local player every tick (a frozen stride). */
    private static float[] stride;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stride == null) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        var w = p.walkAnimation;
        float s = stride[1];
        // three updates land exactly on the wanted position with the speed steady (speedOld = speed)
        w.update(stride[0] - w.position() - 2F * s, 1F);
        w.update(s, 1F);
        w.update(s, 1F);
    }

    private static void face(Minecraft mc) {
        var p = mc.player;
        p.setYRot(0F); p.yRotO = 0F; p.setXRot(0F); p.xRotO = 0F;
        p.yBodyRot = 0F; p.yBodyRotO = 0F; p.yHeadRot = 0F; p.yHeadRotO = 0F;
        server(sp -> { sp.setYRot(0F); sp.setXRot(0F); sp.setYHeadRot(0F); sp.yBodyRot = 0F; });
    }

    /** An invisible armor stand to film from: offset from the player, yaw, pitch (he faces +z). */
    private static void camera(Minecraft mc, Steps l, double dx, double dy, double dz, float yaw, float pitch) {
        int[] id = {-1};
        l.add(null, () -> server(p -> {
            p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, p.getBoundingBox().inflate(40), a -> a.getTags().contains("xw_rh_cam")).forEach(a -> a.discard());
            var a = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(p.serverLevel());
            a.moveTo(p.getX() + dx, p.getY() + dy, p.getZ() + dz, yaw, pitch); a.setNoGravity(true); a.setInvisible(true); a.setYHeadRot(yaw);
            a.addTag("xw_rh_cam");
            p.serverLevel().addFreshEntity(a); id[0] = a.getId();
        }), 12);
        l.add(null, () -> {
            if (mc.level.getEntity(id[0]) instanceof net.minecraft.world.entity.decoration.ArmorStand a) {
                a.setYRot(yaw); a.yRotO = yaw; a.setXRot(pitch); a.xRotO = pitch; a.setYHeadRot(yaw); a.yHeadRotO = yaw; mc.setCameraEntity(a);
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true;
            face(mc);
        }, 6);
    }

    private static final double FULL = 2.7, CLOSE = 1.15;
    private static final double D = 0.7071;

    /** Full-body shots around him: front, three-quarter, side, back, back three-quarter; y = camera height offset. */
    private static void around(Minecraft mc, Steps l, String name, double y) {
        camera(mc, l, 0, y - 0.75, FULL, 180F, 0F);
        l.add("rhalix_" + name + "_front", () -> {}, 3);
        camera(mc, l, FULL * D, y - 0.75, FULL * D, 135F, 0F);
        l.add("rhalix_" + name + "_q", () -> {}, 3);
        camera(mc, l, FULL, y - 0.75, 0, 90F, 0F);
        l.add("rhalix_" + name + "_side", () -> {}, 3);
        camera(mc, l, 0, y - 0.75, -FULL, 0F, 0F);
        l.add("rhalix_" + name + "_back", () -> {}, 3);
        camera(mc, l, -FULL * D, y - 0.75, -FULL * D, -45F, 0F);
        l.add("rhalix_" + name + "_qback", () -> {}, 3);
    }

    private static void armor(ServerPlayer p, boolean on) {
        p.setItemSlot(EquipmentSlot.HEAD, on ? new ItemStack(Items.IRON_HELMET) : ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.CHEST, on ? new ItemStack(Items.IRON_CHESTPLATE) : ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.LEGS, on ? new ItemStack(Items.IRON_LEGGINGS) : ItemStack.EMPTY);
        p.setItemSlot(EquipmentSlot.FEET, on ? new ItemStack(Items.IRON_BOOTS) : ItemStack.EMPTY);
    }

    public static void run(Minecraft mc, String focus, Steps l) {
        double[] home = new double[3];
        l.add(null, () -> { mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE); mc.getToasts().clear(); mc.setScreen(null); }, 5);
        l.add(null, () -> server(p -> {
            com.xenowarriors.character.CharacterManager.setCharacter(p, CharacterType.RHALIX);
        }), 40);
        l.add(null, () -> server(p -> {
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.outfit = 0);      // Season 1 ("Recarnet 1")
            com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
            p.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            armor(p, false);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            home[0] = p.getX(); home[1] = p.getY(); home[2] = p.getZ();
            log("character " + com.xenowarriors.character.CharacterManager.class.getSimpleName() + " set: rhalix outfit 0, wings out "
                    + com.xenowarriors.character.RhalixWings.out(p));
        }), 30);
        l.add(null, () -> {
            var m = com.xenowarriors.client.render.CharacterDetailLayer.detailModel(mc.player);
            log("detail model " + com.xenowarriors.client.render.CharacterDetailLayer.detailModelPath(mc.player) + " loaded " + (m != null)
                    + (m == null ? "" : String.format(" body parts %d, coat flaps %s, hair strands hidden by hoods %d, mouth stick pieces %d",
                    m.body.getAllParts().count(), m.coatFrontR != null && m.coatFrontL != null && m.coatSideR != null && m.coatSideL != null && m.coatBack != null,
                    m.hoodPokers.size(), m.mouthStick.size())));
        }, 5);

        // 1. standing, wings out (his default look)
        around(mc, l, "stand", 0);
        // 2. close-ups: head and collar, waist (belt, coat flaps, pockets), boots
        camera(mc, l, 0, -0.1, CLOSE, 180F, 0F);
        l.add("rhalix_head_front", () -> {}, 3);
        camera(mc, l, CLOSE * D, -0.1, CLOSE * D, 135F, 0F);
        l.add("rhalix_head_q", () -> {}, 3);
        camera(mc, l, CLOSE, -0.1, 0, 90F, 0F);
        l.add("rhalix_head_side", () -> {}, 3);
        camera(mc, l, 0, -0.1, -CLOSE, 0F, 0F);
        l.add("rhalix_head_back", () -> {}, 3);
        camera(mc, l, 0, -0.95, CLOSE, 180F, 0F);
        l.add("rhalix_waist_front", () -> {}, 3);
        camera(mc, l, CLOSE * D, -0.95, CLOSE * D, 135F, 0F);
        l.add("rhalix_waist_q", () -> {}, 3);
        camera(mc, l, -CLOSE, -0.95, 0, -90F, 0F);
        l.add("rhalix_waist_side", () -> {}, 3);
        camera(mc, l, 0, -0.95, -CLOSE, 0F, 0F);
        l.add("rhalix_waist_back", () -> {}, 3);
        camera(mc, l, 0, -1.4, CLOSE, 180F, 12F);
        l.add("rhalix_boots_front", () -> {}, 3);
        camera(mc, l, 0, -1.4, -CLOSE, 0F, 12F);
        l.add("rhalix_boots_back", () -> {}, 3);

        // 3. wings put away: the coat's back
        l.add(null, () -> server(p -> { com.xenowarriors.character.RhalixWings.toggle(p); log("wings toggled -> out " + com.xenowarriors.character.RhalixWings.out(p)); }), 60);
        camera(mc, l, 0, -0.75, -FULL, 0F, 0F);
        l.add("rhalix_nowings_back", () -> {}, 3);
        camera(mc, l, -FULL * D, -0.75, -FULL * D, -45F, 0F);
        l.add("rhalix_nowings_qback", () -> {}, 3);
        camera(mc, l, 0, -0.95, -CLOSE, 0F, 0F);
        l.add("rhalix_nowings_waist_back", () -> {}, 3);

        // 4. mid-stride (held), both legs forward in turn
        float s = 0.62F;
        float[] phases = {0F, (float) (Math.PI / 0.6662F)};
        String[] legs = {"leftfwd", "rightfwd"};
        for (int k = 0; k < 2; k++) {
            float ph = phases[k];
            String n = legs[k];
            l.add(null, () -> { stride = new float[]{ph, s}; }, 10);
            l.add(null, () -> log(String.format("walk %s: legs right %.2f left %.2f (limb swing %.2f, amount %.2f)", n,
                    net.minecraft.util.Mth.cos(ph * 0.6662F) * 1.4F * s, net.minecraft.util.Mth.cos(ph * 0.6662F + (float) Math.PI) * 1.4F * s, ph, s)), 1);
            camera(mc, l, 0, -0.75, FULL, 180F, 0F);
            l.add("rhalix_walk_" + n + "_front", () -> {}, 3);
            camera(mc, l, FULL * D, -0.75, FULL * D, 135F, 0F);
            l.add("rhalix_walk_" + n + "_q", () -> {}, 3);
            camera(mc, l, -FULL, -0.75, 0, -90F, 0F);
            l.add("rhalix_walk_" + n + "_side", () -> {}, 3);
            camera(mc, l, -FULL * D, -0.75, -FULL * D, -45F, 0F);
            l.add("rhalix_walk_" + n + "_qback", () -> {}, 3);
            camera(mc, l, CLOSE * D, -0.95, CLOSE * D, 135F, 0F);
            l.add("rhalix_walk_" + n + "_waist", () -> {}, 3);
        }
        l.add(null, () -> stride = null, 20);

        // wings back out for the rest
        l.add(null, () -> server(p -> { com.xenowarriors.character.RhalixWings.toggle(p); log("wings toggled -> out " + com.xenowarriors.character.RhalixWings.out(p)); }), 60);

        // 5. an attack swing (three frames), from the front three-quarter
        camera(mc, l, FULL * D, -0.75, FULL * D, 135F, 0F);
        l.add(null, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND), 2);
        l.add("rhalix_attack_1", () -> {}, 2);
        l.add("rhalix_attack_2", () -> {}, 2);
        l.add("rhalix_attack_3", () -> {}, 10);

        // 6. a skill pose (his first skill: arms up, wings spread)
        l.add(null, () -> server(p -> {
            p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.setKarma(d.karmaMax); java.util.Arrays.fill(d.cooldownEnd, 0); });
            var pool = com.xenowarriors.skill.SkillPool.of(CharacterType.RHALIX);
            if (pool.isEmpty()) { log("no skills"); return; }
            var sk = pool.get(0).skill();
            p.setYRot(0); p.setXRot(0);
            try {
                boolean used = sk.use(p);
                Integer gen = com.xenowarriors.network.SkillAnimPacket.GENERIC.get(sk.getId());
                if (gen != null) com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.SkillAnimPacket(p.getId(), gen, false), p);
                log("skill " + sk.getId() + " used " + used);
            } catch (Exception ex) { log("skill " + sk.getId() + " failed " + ex); }
        }), 8);
        l.add("rhalix_skill_1", () -> {}, 6);
        camera(mc, l, 0, -0.75, FULL, 180F, 0F);
        l.add("rhalix_skill_2", () -> {}, 6);
        camera(mc, l, -FULL * D, -0.75, -FULL * D, -45F, 0F);
        l.add("rhalix_skill_3", () -> {}, 60);

        // 7. flying: wings beating (a few frames from the back and side)
        l.add(null, () -> server(p -> { p.teleportTo(home[0], home[1], home[2]); p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); }), 20);
        l.add(null, () -> { var p = mc.player; p.getAbilities().flying = true; p.onUpdateAbilities();
            server(sp -> { sp.getAbilities().flying = true; sp.onUpdateAbilities(); sp.teleportTo(home[0], home[1] + 1.5, home[2]); }); }, 30);
        camera(mc, l, 0, 0.75, -FULL - 0.6, 0F, 0F);
        l.add("rhalix_fly_back_1", () -> {}, 4);
        l.add("rhalix_fly_back_2", () -> {}, 4);
        camera(mc, l, -FULL * D - 0.4, 0.75, -FULL * D - 0.4, -45F, 0F);
        l.add("rhalix_fly_qback", () -> {}, 4);
        camera(mc, l, FULL + 0.6, 0.75, 0, 90F, 0F);
        l.add("rhalix_fly_side", () -> {}, 4);
        l.add(null, () -> { var p = mc.player; p.getAbilities().flying = false; p.onUpdateAbilities();
            server(sp -> { sp.getAbilities().flying = false; sp.onUpdateAbilities(); sp.teleportTo(home[0], home[1], home[2]); }); }, 40);

        // 8. iron armor over the coat
        l.add(null, () -> server(p -> armor(p, true)), 20);
        camera(mc, l, 0, -0.75, FULL, 180F, 0F);
        l.add("rhalix_armor_front", () -> {}, 3);
        camera(mc, l, FULL * D, -0.75, FULL * D, 135F, 0F);
        l.add("rhalix_armor_q", () -> {}, 3);
        camera(mc, l, 0, -0.75, -FULL, 0F, 0F);
        l.add("rhalix_armor_back", () -> {}, 3);
        l.add(null, () -> server(p -> armor(p, false)), 5);

        l.add(null, () -> { stride = null; mc.setCameraEntity(mc.player); mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); log("done"); }, 5);
    }
}

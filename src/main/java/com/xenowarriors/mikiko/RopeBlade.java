package com.xenowarriors.mikiko;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.entity.ThrownHekubladeEntity;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.RopeFxPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Season 3 Hekublade's rope (its pommel ring): Mikiko hooks a finger through it and
 * <ul>
 * <li>throws the blade like a boomerang (use / right click) - it homes on his lock-on target however far away, cuts
 *     round them and slowly comes back; meanwhile he fights with fists and kicks and can't call it back until it's
 *     home (or 15 seconds have passed);</li>
 * <li>lashes it out on the rope and reels it in as the finisher of his blade combos;</li>
 * <li>running with enemies about, spins it on the rope in front of him like a saw - whoever he runs into gets cut
 *     (damage follows his attack power).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RopeBlade {
    public static final int FX_THROW = 0, FX_LASH = 1;
    private static final double LASH_RANGE = 9;
    private static final Map<UUID, ThrownHekubladeEntity> OUT = new HashMap<>();
    private static final Map<UUID, Map<Integer, Integer>> SPIN_HIT = new HashMap<>();
    private static final Map<UUID, int[]> COMBO = new HashMap<>();
    private static final Map<UUID, Integer> LAST_LASH = new HashMap<>();

    private RopeBlade() {}

    /** Mikiko in a Season 3 outfit. */
    public static boolean seasonThree(ServerPlayer p) {
        if (ModCapabilities.getType(p) != CharacterType.MIKIKO) return false;
        int o = p.getCapability(com.xenowarriors.character.ModCapabilities.PLAYER_CHARACTER).map(d -> d.outfit).orElse(0);
        return o == 3 || o == 4;
    }

    public static boolean eligible(ServerPlayer p) {
        return seasonThree(p) && Hekublade.holding(p);
    }

    public static boolean isOut(ServerPlayer p) {
        ThrownHekubladeEntity e = OUT.get(p.getUUID());
        return e != null && !e.isRemoved();
    }

    private static float attack(ServerPlayer p, float f) {
        return (float) Math.max(2.0, p.getAttributeValue(Attributes.ATTACK_DAMAGE) * f);
    }

    // ------------------------------------------------------------------ boomerang throw

    public static void throwBlade(ServerPlayer p, int targetId) {
        if (!eligible(p) || isOut(p)) return;
        LivingEntity target = p.level().getEntity(targetId) instanceof LivingEntity l && l.isAlive() && l != p ? l : null;
        Hekublade.removeAll(p.getInventory());
        ThrownHekubladeEntity e = ThrownHekubladeEntity.create(p, target);
        p.level().addFreshEntity(e);
        OUT.put(p.getUUID(), e);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1F, 0.8F);
        ModNetwork.sendToTrackingAndSelf(new RopeFxPacket(p.getId(), FX_THROW), p);
    }

    /** It made it back: into his hand (unless he's already got another one). */
    public static void returned(ServerPlayer p, ThrownHekubladeEntity e) {
        if (OUT.get(p.getUUID()) == e) OUT.remove(p.getUUID());
        if (!p.getInventory().contains(new net.minecraft.world.item.ItemStack(com.xenowarriors.item.ModItems.HEKUBLADE.get()))) Hekublade.toggle(p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    /** The summon key while it's out: no calling it back early - only after 15 seconds does that bring it home. */
    public static boolean blockSummon(ServerPlayer p) {
        ThrownHekubladeEntity e = OUT.get(p.getUUID());
        if (e == null || e.isRemoved()) { OUT.remove(p.getUUID()); return false; }
        if (e.tickCount < ThrownHekubladeEntity.MAX_LIFE) {
            p.displayClientMessage(Component.literal("The Hekublade is still on its way back...").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return true;
        }
        OUT.remove(p.getUUID());
        e.discard();
        return false;                                  // (the summon goes ahead and brings it straight back)
    }

    // ------------------------------------------------------------------ rope lash (combo finisher)

    public static void lash(ServerPlayer p) {
        if (!eligible(p)) return;
        Integer last = LAST_LASH.get(p.getUUID());
        if (last != null && p.tickCount - last < 10) return;
        LAST_LASH.put(p.getUUID(), p.tickCount);
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1F), end = eye.add(look.scale(LASH_RANGE));
        float dmg = attack(p, 0.8F);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0))) {
            if (e == p || !e.isAlive()) continue;
            if (e.getBoundingBox().inflate(0.6).clip(eye, end).isEmpty()) continue;
            e.hurt(p.damageSources().playerAttack(p), dmg);
            Vec3 pull = p.position().subtract(e.position()).normalize().scale(0.35);       // yanked a little as it reels in
            e.push(pull.x, 0.15, pull.z);
            e.hurtMarked = true;
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1F, 0.8F);
        ModNetwork.sendToTrackingAndSelf(new RopeFxPacket(p.getId(), FX_LASH), p);
    }

    /** Without Epic Fight: every fourth full-strength blade hit in a row is the lash. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (ModList.get().isLoaded("epicfight") || !(event.getSource().getEntity() instanceof ServerPlayer p) || !eligible(p)) return;
        if (event.getSource().getDirectEntity() != p || p.getAttackStrengthScale(0.5F) < 0.85F) return;
        int[] c = COMBO.computeIfAbsent(p.getUUID(), k -> new int[2]);
        if (p.tickCount - c[1] > 40) c[0] = 0;
        c[1] = p.tickCount;
        if (++c[0] >= 4) { c[0] = 0; lash(p); }
    }

    // ------------------------------------------------------------------ running saw spin

    /** Running with the blade and enemies about: he spins it in front of him on the rope. */
    public static boolean sawSpin(ServerPlayer p) {
        if (!eligible(p) || !p.isSprinting() || p.isPassenger()) return false;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10))) {
            if (m.isAlive() && (m instanceof Enemy || m.getTarget() == p)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        ThrownHekubladeEntity out = OUT.get(p.getUUID());
        if (out != null && out.isRemoved()) {                // vanished somehow (unloaded...): back to him
            OUT.remove(p.getUUID());
            returned(p, out);
        }
        if (p.tickCount % 2 != 0 || !sawSpin(p)) return;
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 c = p.position().add(0, 1.0, 0).add(look.scale(1.3));
        Map<Integer, Integer> cd = SPIN_HIT.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        float dmg = attack(p, 0.6F);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(1.3, 1.0, 1.3))) {
            if (e == p || !e.isAlive()) continue;
            Integer last = cd.get(e.getId());
            if (last != null && p.tickCount - last < 10) continue;
            cd.put(e.getId(), p.tickCount);
            e.hurt(p.damageSources().playerAttack(p), dmg);
            e.push(look.x * 0.5, 0.2, look.z * 0.5);
            e.hurtMarked = true;
            p.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        if (cd.size() > 64) cd.clear();
    }
}

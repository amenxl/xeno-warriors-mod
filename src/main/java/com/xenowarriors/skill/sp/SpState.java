package com.xenowarriors.skill.sp;

import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.*;

/**
 * Timed effects shared by the Sky Patrol kits: Kyrie's bullet-stopping gravity field and boxing stance, Max's wind wall,
 * Sam's time bombs and knockout mode, Lexy's Persephone takeover, and the wounds from Max's Vampire Cut.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpState {
    /** player -> game time it ends */
    static final Map<UUID, Long> GRAVITY_FIELD = new HashMap<>(), WIND_WALL = new HashMap<>(), BOXING = new HashMap<>(),
            KNOCKOUT = new HashMap<>(), PERSEPHONE = new HashMap<>();
    /** victim entity id -> bomb */
    static final Map<Integer, Bomb> BOMBS = new HashMap<>();
    /** victim entity id -> vampire wound */
    static final Map<Integer, Wound> WOUNDS = new HashMap<>();
    private static final Set<Integer> FROZEN = new HashSet<>();

    record Bomb(UUID owner, long at, float damage, ServerLevel level) {}
    record Wound(UUID owner, long until, ServerLevel level) {}

    private SpState() {}

    static long now(Entity e) { return e.level().getGameTime(); }
    static boolean on(Map<UUID, Long> m, Entity e) { Long t = m.get(e.getUUID()); return t != null && t > now(e); }

    public static boolean boxing(Entity e) { return on(BOXING, e); }
    public static boolean knockout(Entity e) { return on(KNOCKOUT, e); }
    public static boolean persephone(Entity e) { return on(PERSEPHONE, e); }
    public static boolean hasBomb(Entity e) { return BOMBS.containsKey(e.getId()); }

    public static void plantBomb(ServerPlayer owner, LivingEntity t, int ticks, float dmg) {
        BOMBS.put(t.getId(), new Bomb(owner.getUUID(), now(owner) + ticks, dmg, owner.serverLevel()));
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
    }

    /** Sets a planted bomb off right away (damage x mult). */
    public static boolean detonate(LivingEntity t, float mult) {
        Bomb b = BOMBS.remove(t.getId());
        if (b == null) return false;
        boom(b, t, mult);
        return true;
    }

    private static void boom(Bomb b, LivingEntity t, float mult) {
        ServerLevel l = b.level();
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(b.owner());
        if (!t.isAlive()) return;
        l.explode(owner, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 1.2F, Level.ExplosionInteraction.NONE);
        t.hurt(owner != null ? l.damageSources().playerAttack(owner) : l.damageSources().explosion(null, null), b.damage() * mult);
        t.removeEffect(MobEffects.GLOWING);
    }

    public static void wound(ServerPlayer max, LivingEntity t, int ticks) {
        WOUNDS.put(t.getId(), new Wound(max.getUUID(), now(max) + ticks, max.serverLevel()));
    }

    public static boolean wounded(LivingEntity t) { return WOUNDS.containsKey(t.getId()); }
    public static void endWounds(ServerPlayer max) { WOUNDS.values().removeIf(w -> w.owner().equals(max.getUUID())); }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // bombs ticking down
        for (Iterator<Map.Entry<Integer, Bomb>> it = BOMBS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next(); Bomb b = en.getValue();
            Entity e = b.level().getEntity(en.getKey());
            if (!(e instanceof LivingEntity t) || !t.isAlive()) { it.remove(); continue; }
            long left = b.at() - b.level().getGameTime();
            if (left % 20 == 0 && left > 0) {
                b.level().playSound(null, t.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1F, left <= 60 ? 1.8F : 1.2F);
                b.level().sendParticles(new DustParticleOptions(new Vector3f(1F, 0.25F, 0.2F), 1.2F), t.getX(), t.getY() + t.getBbHeight() + 0.3, t.getZ(), 4, 0.1, 0.1, 0.1, 0);
            }
            if (left <= 0) { it.remove(); boom(b, t, 1F); }
        }
        // Vampire Cut wounds: no healing, and Max drinks their health
        if (server.getTickCount() % 10 == 0) {
            for (Iterator<Map.Entry<Integer, Wound>> it = WOUNDS.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next(); Wound w = en.getValue();
                Entity e = w.level().getEntity(en.getKey());
                ServerPlayer max = server.getPlayerList().getPlayer(w.owner());
                if (!(e instanceof LivingEntity t) || !t.isAlive() || max == null || w.level().getGameTime() > w.until()) { it.remove(); continue; }
                t.invulnerableTime = 0;
                if (t.hurt(w.level().damageSources().playerAttack(max), 1.5F)) max.heal(1.0F);
                w.level().sendParticles(new DustParticleOptions(new Vector3f(0.7F, 0.02F, 0.05F), 1.0F), t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 5, 0.2, 0.3, 0.2, 0);
            }
        }
        // fields that catch projectiles
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean grav = on(GRAVITY_FIELD, p), wind = on(WIND_WALL, p);
            if (!grav && !wind) continue;
            ServerLevel l = p.serverLevel();
            for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(grav ? 5 : 3.5))) {
                if (pr.getOwner() == p) continue;
                if (grav) {                                              // Kyrie: stopped dead in the air
                    pr.setDeltaMovement(Vec3.ZERO); pr.setNoGravity(true); pr.hurtMarked = true; FROZEN.add(pr.getId());
                    if (l.getGameTime() % 4 == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, pr.getX(), pr.getY(), pr.getZ(), 2, 0.05, 0.05, 0.05, 0);
                } else if (pr.getDeltaMovement().lengthSqr() > 0.01) {    // Max: blown straight back
                    pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.2)); pr.setOwner(p); pr.hurtMarked = true;
                    l.sendParticles(ParticleTypes.CLOUD, pr.getX(), pr.getY(), pr.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
                }
            }
            if (wind && l.getGameTime() % 4 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = (l.getGameTime() * 0.4 + i * Math.PI / 3);
                    l.sendParticles(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * 2.2, p.getY() + 0.3 + i * 0.3, p.getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
                }
            }
            if (grav && l.getGameTime() % 5 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    l.sendParticles(new DustParticleOptions(new Vector3f(0.5F, 0.3F, 0.85F), 1F), p.getX() + Math.cos(a) * 5, p.getY() + 0.1, p.getZ() + Math.sin(a) * 5, 1, 0, 0, 0, 0);
                }
            }
        }
        if (server.getTickCount() % 20 == 0 && !FROZEN.isEmpty()) {         // field gone: what it held drops
            for (ServerLevel l : server.getAllLevels()) {
                for (Iterator<Integer> it = FROZEN.iterator(); it.hasNext(); ) {
                    Entity e = l.getEntity(it.next());
                    if (e == null) continue;
                    boolean held = false;
                    for (ServerPlayer p : l.players()) if (on(GRAVITY_FIELD, p) && p.distanceToSqr(e) < 36) held = true;
                    if (!held) { e.setNoGravity(false); it.remove(); }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onHurt(LivingHurtEvent event) {
        Entity src = event.getSource().getEntity();
        if (src instanceof ServerPlayer p) {
            if (boxing(p) && event.getSource().getDirectEntity() == p) event.setAmount(event.getAmount() * 1.6F);   // Kyrie's boxing
            if (persephone(p)) event.setAmount(event.getAmount() * 1.8F);                                         // Persephone's strength
            // Sam's knockout mode: a blow that would finish them knocks them out instead
            if (knockout(p) && event.getAmount() >= event.getEntity().getHealth() && !(event.getEntity() instanceof ServerPlayer)) {
                LivingEntity t = event.getEntity();
                event.setAmount(Math.max(0, t.getHealth() - 1F));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 10));
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 4));
                t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
                if (t instanceof net.minecraft.world.entity.Mob m) { m.setTarget(null); m.getNavigation().stop(); }
                p.serverLevel().sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() + 0.2, t.getZ(), 12, 0.3, 0.1, 0.3, 0.1);
                p.displayClientMessage(Component.literal("Knocked out!").withStyle(ChatFormatting.YELLOW), true);
            }
        }
        if (event.getEntity() instanceof ServerPlayer v && persephone(v)) event.setAmount(event.getAmount() * 0.7F);
    }

    /** Vampire Cut wounds won't heal. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (WOUNDS.containsKey(event.getEntity().getId())) event.setCanceled(true);
    }
}

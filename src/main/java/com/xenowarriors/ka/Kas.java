package com.xenowarriors.ka;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.KaEffectPacket;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.npc.HumanNpcEntity;
import com.xenowarriors.npc.SkylanderEntity;
import com.xenowarriors.skill.Rooting;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.SkillTargeting;
import com.xenowarriors.skill.rhalix.KaReleaseSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.UUID;

/** Who has a KA, using them (NPCs and players), what they do, and what they cost. */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Kas {
    private static final String KA = "xw_ka", ROLLED = "xw_ka_rolled", CD = "xw_ka_cd", CAST = "xw_ka_cast", CAST_TARGET = "xw_ka_cast_target";
    private static final String STRAIN = "xw_ka_strain", BURN = "xw_ka_burn", EFFECT = "xw_kae_", SRC = "_src", DELAYED = "xw_ka_delayed_hit";
    private static final UUID REACH_ID = UUID.fromString("2a6c4d61-9b0e-4f0c-8a5f-1c2d3e4f5a61");
    public static final int BURNOUT = 6;
    public static final String PAID = "xw_ka_paid", PAID_UNTIL = "xw_ka_paid_until";

    /** The Karma the last KA used on this entity cost its user (while it's still fresh), or -1. */
    public static int lastPaid(LivingEntity e) {
        return e.getPersistentData().getLong(PAID_UNTIL) >= e.level().getGameTime() ? e.getPersistentData().getInt(PAID) : -1;
    }

    private Kas() {}

    // ---------------------------------------------------------------- who has what

    public static Ka of(LivingEntity e) {
        if (e instanceof ServerPlayer p) return p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> Ka.byId(d.ka)).orElse(null);
        return Ka.byId(e.getPersistentData().getString(KA));
    }

    public static void give(LivingEntity e, Ka ka) {
        if (e instanceof ServerPlayer p) {          // players only for testing (/xeno ka self): their real KAs aren't revealed yet
            p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> d.ka = ka == null ? "" : ka.id());
            com.xenowarriors.skill.SkillManager.syncLoadout(p);
            return;
        }
        e.getPersistentData().putString(KA, ka == null ? "" : ka.id());
        e.getPersistentData().putBoolean(ROLLED, true);
    }

    private static void roll(Mob m) {
        CompoundTag t = m.getPersistentData();
        t.putBoolean(ROLLED, true);
        var r = m.getRandom();
        Ka pick = null;
        if (m instanceof HumanNpcEntity h) {
            if (h.carriesSoul()) return;
            boolean captain = h.hasCustomName() && h.getCustomName().getString().contains("Captain");
            if (captain && r.nextInt(2) == 0) pick = Ka.RARE[r.nextInt(Ka.RARE.length)];
            else if (h.raider() && r.nextInt(16) == 0) pick = Ka.RARE[r.nextInt(Ka.RARE.length)];
            else if (r.nextInt(h.raider() ? 10 : 25) == 0) pick = Ka.SIMPLE[r.nextInt(Ka.SIMPLE.length)];
        } else if (m instanceof SkylanderEntity s) {
            if (s.guardian() && r.nextInt(5) < 2) pick = Ka.RARE[r.nextInt(Ka.RARE.length)];
            else if (r.nextInt(s.guardian() ? 7 : 25) == 0) pick = Ka.SIMPLE[r.nextInt(Ka.SIMPLE.length)];
        }
        if (pick != null) t.putString(KA, pick.id());
    }

    // ---------------------------------------------------------------- strain: the price grows with use

    public static float strain(LivingEntity e) { return e.getPersistentData().getFloat(STRAIN); }

    public static boolean burntOut(LivingEntity e) { return e.getPersistentData().getLong(BURN) > e.level().getGameTime(); }

    private static float addStrain(LivingEntity e) {
        float s = strain(e) + 1F;
        e.getPersistentData().putFloat(STRAIN, s);
        if (s >= BURNOUT) {
            e.getPersistentData().putLong(BURN, e.level().getGameTime() + 5 * 60 * 20);
            for (ServerPlayer o : ((ServerLevel) e.level()).players()) if (o.distanceToSqr(e) < 32 * 32)
                o.displayClientMessage(Component.literal(e.getName().getString() + "'s KA burnt out!").withStyle(ChatFormatting.DARK_RED), true);
            ((ServerLevel) e.level()).sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1.2, e.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
        }
        return s;
    }

    // ---------------------------------------------------------------- using one

    public static final int PLAYER_COOLDOWN = 45 * 20;

    /** A player with a test KA pressing the super ultimate key. True if it went off. */
    public static boolean usePlayer(ServerPlayer p) {
        Ka ka = of(p);
        if (ka == null) {
            p.displayClientMessage(Component.literal(ModCapabilities.getType(p).getDisplayName().getString() + "'s KA hasn't been revealed yet...").withStyle(ChatFormatting.DARK_PURPLE), true);
            return false;
        }
        if (burntOut(p)) { p.displayClientMessage(Component.literal("Your KA is burnt out.").withStyle(ChatFormatting.DARK_RED), true); return false; }
        LivingEntity target = SkillTargeting.aimed(p, 14, 22);
        if (target == null && needsTarget(ka)) { p.displayClientMessage(Component.literal("KA " + ka.title() + ": no one to use it on.").withStyle(ChatFormatting.GRAY), true); return false; }
        int cost = EnemyKarma.kaCost(p, ka);
        if (!p.isCreative() && !com.xenowarriors.karma.Karma.has(p, cost)) { com.xenowarriors.karma.Karma.notEnough(p, cost); return false; }
        if (!p.isCreative()) com.xenowarriors.karma.Karma.add(p, -cost);
        if (target != null) { target.getPersistentData().putInt(PAID, cost); target.getPersistentData().putLong(PAID_UNTIL, now(p) + 200); }
        announce(p, ka);
        apply(p, target, ka);
        return true;
    }

    private static boolean needsTarget(Ka ka) { return ka != Ka.REACH && ka != Ka.MIRROR; }

    private static void announce(LivingEntity user, Ka ka) {
        Component line = Component.literal(user.getName().getString() + ": ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("KA - " + ka.title() + "!").withStyle(ka.chat(), ChatFormatting.BOLD));
        for (ServerPlayer o : ((ServerLevel) user.level()).players()) if (o.distanceToSqr(user) < 40 * 40) o.displayClientMessage(line, true);
        user.level().playSound(null, user.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1F, ka.rare ? 0.7F : 1.1F);
        ring((ServerLevel) user.level(), user, ka, 2.2, 32);
    }

    private static void ring(ServerLevel l, Entity e, Ka ka, double r, int n) {
        Vector3f c = new Vector3f((ka.color >> 16 & 255) / 255F, (ka.color >> 8 & 255) / 255F, (ka.color & 255) / 255F);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            l.sendParticles(new DustParticleOptions(c, 1.1F), e.getX() + Math.cos(a) * r, e.getY() + 0.1, e.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static long now(Entity e) { return e.level().getGameTime(); }

    private static void mark(LivingEntity target, Ka ka, int ticks, Entity src) {
        target.getPersistentData().putLong(EFFECT + ka.id(), now(target) + ticks);
        if (src != null) target.getPersistentData().putInt(EFFECT + ka.id() + SRC, src.getId());
        if (target instanceof ServerPlayer p && (ka == Ka.GRIP || ka == Ka.FLIP || ka == Ka.SIGHT))
            ModNetwork.sendToPlayer(new KaEffectPacket(ka.ordinal(), ticks, src == null ? -1 : src.getId()), p);
    }

    public static boolean has(LivingEntity e, Ka ka) { return e.getPersistentData().getLong(EFFECT + ka.id()) > now(e); }

    private static Entity src(LivingEntity e, Ka ka) { return e.level().getEntity(e.getPersistentData().getInt(EFFECT + ka.id() + SRC)); }

    /** Fires a KA from {@code user} at {@code target} (may be null for self KAs), then charges the user its price. */
    public static void apply(LivingEntity user, LivingEntity target, Ka ka) {
        ServerLevel level = (ServerLevel) user.level();
        boolean resisted = target != null && KaReleaseSkill.free(target);
        if (resisted) {
            if (target instanceof ServerPlayer tp) tp.displayClientMessage(Component.literal("KA " + ka.title() + " couldn't take hold - KA Release!").withStyle(ChatFormatting.DARK_PURPLE), true);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
        } else if (target != null) {
            ring(level, target, ka, 1.2, 20);
            if (target instanceof ServerPlayer tp && user != target)
                tp.sendSystemMessage(Component.literal(user.getName().getString() + " used KA " + ka.title() + " on you - it manipulates " + ka.manipulates + ".").withStyle(ka.chat()));
        }
        int d = ka.rare ? 80 : 100;
        if (!resisted) switch (ka) {
            case WEIGHT -> { mark(target, ka, d, user); target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, 3)); }
            case GRIP -> mark(target, ka, 120, user);
            case STRIDE -> {
                Vec3 to = target.position().subtract(user.position());
                if (to.length() > 2.5) {
                    Vec3 near = target.position().subtract(to.normalize().scale(1.3));
                    user.teleportTo(near.x, target.getY(), near.z);
                }
                Vec3 pull = user.position().subtract(target.position()).normalize().scale(0.9);
                target.push(pull.x, 0.2, pull.z);
                target.hurtMarked = true;
                target.hurt(user.damageSources().mobAttack(user), 5F);
                level.sendParticles(ParticleTypes.PORTAL, user.getX(), user.getY() + 1, user.getZ(), 30, 0.3, 0.6, 0.3, 0.5);
            }
            case HOLD -> {
                for (var hand : net.minecraft.world.InteractionHand.values()) {
                    ItemStack held = target.getItemInHand(hand);
                    if (held.isEmpty()) continue;
                    ItemEntity it = new ItemEntity(level, target.getX(), target.getEyeY() - 0.3, target.getZ(), held.copy());
                    it.setPickUpDelay(50);
                    it.setDeltaMovement(target.getLookAngle().scale(0.25).add(0, 0.2, 0));
                    level.addFreshEntity(it);
                    target.setItemInHand(hand, ItemStack.EMPTY);
                }
                level.playSound(null, target.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 0.8F, 1.4F);
            }
            case PACE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, 2));
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, d, 2));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 0));
            }
            case SIGHT -> { mark(target, ka, 80, user); if (target instanceof Mob m) m.setTarget(user); }
            case DELAY -> mark(target, ka, 120, user);
            case TETHER -> mark(target, ka, 160, user);
            case FLIP -> mark(target, ka, 80, user);
            case MEMORY -> {
                if (target instanceof Player pl) {
                    var inv = pl.getInventory();
                    java.util.List<ItemStack> bar = new java.util.ArrayList<>();
                    for (int i = 0; i < 9; i++) bar.add(inv.getItem(i));
                    java.util.Collections.shuffle(bar);
                    for (int i = 0; i < 9; i++) inv.setItem(i, bar.get(i));
                } else if (target instanceof Mob m) { m.setTarget(null); m.setLastHurtByMob(null); }
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            }
            case SWAP -> {
                Vec3 a = user.position(), b = target.position();
                user.teleportTo(b.x, b.y, b.z);
                target.teleportTo(a.x, a.y, a.z);
                level.playSound(null, user.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1F, 1.2F);
            }
            case BIND -> { mark(target, ka, 80, user); mark(user, Ka.BIND, 80, null); }
            default -> {}
        }
        // self KAs
        if (ka == Ka.REACH) {
            mark(user, ka, 140, null);
            if (user instanceof ServerPlayer p) setReach(p, 3.0);
        }
        if (ka == Ka.MIRROR) mark(user, ka, 100, null);
        price(user, ka, addStrain(user));
    }

    /** What it costs the user: always tied to what the KA manipulates, worse with strain. */
    private static void price(LivingEntity u, Ka ka, float strain) {
        float s = 1F + strain * 0.5F;
        int d = (int) (60 * s);
        switch (ka) {
            case WEIGHT -> u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, strain >= 3 ? 2 : 1));
            case GRIP -> { u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d / 2, 0)); mark(u, Ka.GRIP, d, null); }
            case STRIDE -> { u.addEffect(new MobEffectInstance(MobEffects.CONFUSION, d, 0)); u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, 0)); }
            case HOLD -> {
                u.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 0));
                u.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, d, 1));
                if (strain >= 4 && !u.getMainHandItem().isEmpty()) {          // their own grip gives out
                    u.spawnAtLocation(u.getMainHandItem().copy());
                    u.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }
            }
            case REACH -> ServerScheduler.later(140, () -> {
                if (u instanceof ServerPlayer p) { setReach(p, -1.5); ServerScheduler.later(d, () -> setReach(p, 0)); }
                else u.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, d, 0));
            });
            case PACE -> { u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d, 1)); u.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, d, 1)); }
            case SIGHT -> u.addEffect(new MobEffectInstance(strain >= 4 ? MobEffects.DARKNESS : MobEffects.BLINDNESS, d / 2, 0));
            case DELAY -> u.getPersistentData().putLong("xw_ka_delay_self", now(u) + d);
            case MIRROR, TETHER -> {}                      // handled where the damage happens
            case FLIP -> { if (u instanceof ServerPlayer) mark(u, Ka.FLIP, d / 2, null); else u.addEffect(new MobEffectInstance(MobEffects.CONFUSION, d, 0)); }
            case MEMORY -> {
                if (u instanceof Mob m) ServerScheduler.later(20, () -> m.setTarget(null));
                if (u instanceof Player pl && strain >= 2) {
                    var inv = pl.getInventory();
                    int a = pl.getRandom().nextInt(9), b = pl.getRandom().nextInt(9);
                    ItemStack t = inv.getItem(a); inv.setItem(a, inv.getItem(b)); inv.setItem(b, t);
                    pl.displayClientMessage(Component.literal("You forgot where you put things...").withStyle(ChatFormatting.GOLD), true);
                }
            }
            case SWAP -> {
                int slips = Math.max(1, (int) strain);
                for (int i = 0; i < slips; i++) ServerScheduler.later(20 + i * 30, () -> {
                    if (!u.isAlive()) return;
                    Vec3 j = u.position().add(u.getRandom().nextGaussian() * 2, 0, u.getRandom().nextGaussian() * 2);
                    if (u.level().noCollision(u, u.getBoundingBox().move(j.subtract(u.position())))) u.teleportTo(j.x, j.y, j.z);
                });
            }
            case BIND -> {}                                // they stand still while holding it (marked above)
        }
    }

    private static void setReach(ServerPlayer p, double amount) {
        var attr = p.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (attr == null) return;
        attr.removeModifier(REACH_ID);
        if (amount != 0) attr.addTransientModifier(new AttributeModifier(REACH_ID, "KA Reach", amount, AttributeModifier.Operation.ADDITION));
        if (amount > 0) ServerScheduler.later(140, () -> { var a = p.getAttribute(ForgeMod.ENTITY_REACH.get()); if (a != null && a.getModifier(REACH_ID) != null && a.getModifier(REACH_ID).getAmount() > 0) a.removeModifier(REACH_ID); });
    }

    /** KA Release: wipes every KA effect on them. */
    public static void release(ServerPlayer p) {
        CompoundTag t = p.getPersistentData();
        for (String k : new java.util.ArrayList<>(t.getAllKeys())) if (k.startsWith(EFFECT)) t.remove(k);
        t.remove(PAID_UNTIL);
        ModNetwork.sendToPlayer(new KaEffectPacket(-1, 0, -1), p);
        setReach(p, 0);
    }

    // ---------------------------------------------------------------- every tick

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        long now = now(e);
        CompoundTag t = e.getPersistentData();
        // ongoing effects
        if (has(e, Ka.WEIGHT)) {
            if (e instanceof Player pl && pl.getAbilities().flying && !pl.isCreative()) { pl.getAbilities().flying = false; pl.onUpdateAbilities(); }
            if (e instanceof Player pl && pl.isFallFlying()) pl.stopFallFlying();
            if (!e.onGround()) { e.setDeltaMovement(e.getDeltaMovement().add(0, -0.12, 0)); e.hurtMarked = true; }
        }
        if (has(e, Ka.BIND)) {
            Entity src = src(e, Ka.BIND);
            Rooting.hold(e, e.position());
        }
        if (has(e, Ka.GRIP) && !(e instanceof Player)) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 1.25, v.y, v.z * 1.25);
        }
        if (has(e, Ka.FLIP) && e instanceof Mob m && e.tickCount % 10 == 0) {
            m.getNavigation().stop();
            e.setDeltaMovement(e.getDeltaMovement().multiply(-1, 1, -1));
        }
        if (has(e, Ka.REACH) && e instanceof Mob m && m.getTarget() != null && e.tickCount % 18 == 0 && m.distanceTo(m.getTarget()) < 6.5 && m.hasLineOfSight(m.getTarget())) {
            m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            m.doHurtTarget(m.getTarget());
            ((ServerLevel) e.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, m.getTarget().getX(), m.getTarget().getY() + 1, m.getTarget().getZ(), 1, 0, 0, 0, 0);
        }
        // strain fades: one point a minute
        if (e.tickCount % 20 == 0 && EnemyKarma.usesKarma(e)) EnemyKarma.regen(e);
        if (e.tickCount % 20 == 0 && t.getFloat(STRAIN) > 0) t.putFloat(STRAIN, Math.max(0, t.getFloat(STRAIN) - 1F / 60F));
        // a delayed batch of hits lands
        if (t.contains("xw_ka_delay_store") && t.getLong("xw_ka_delay_self") <= now) {
            float dmg = t.getFloat("xw_ka_delay_store");
            t.remove("xw_ka_delay_store");
            t.putBoolean(DELAYED, true);
            e.hurt(e.damageSources().magic(), dmg);
            t.remove(DELAYED);
        }
        if (!(e instanceof Mob m)) return;
        // NPCs: roll once, then use it on whoever they're fighting
        if (!t.getBoolean(ROLLED) && e.tickCount > 40 && (e instanceof HumanNpcEntity || e instanceof SkylanderEntity)) roll(m);
        Ka ka = Ka.byId(t.getString(KA));
        if (ka == null) return;
        if (e.tickCount % 30 == 0) ring((ServerLevel) e.level(), e, ka, 0.7, 8);                // the tell: a little colored ring
        long cast = t.getLong(CAST);
        if (cast > 0 && now >= cast) {
            t.remove(CAST);
            Entity tg = e.level().getEntity(t.getInt(CAST_TARGET));
            int cost = EnemyKarma.kaCost(e, ka);
            if (tg instanceof LivingEntity lt && lt.isAlive() && lt.distanceTo(e) < 16 && e.isAlive() && EnemyKarma.has(e, cost)) {
                EnemyKarma.spend(e, cost);
                lt.getPersistentData().putInt(PAID, cost);                   // KA Release costs what this took them
                lt.getPersistentData().putLong(PAID_UNTIL, now + 200);
                apply(e, lt, ka);
            }
            return;
        }
        LivingEntity target = m.getTarget();
        if (cast == 0 && target != null && target.isAlive() && now >= t.getLong(CD) && !burntOut(e) && e.distanceTo(target) < 12 && m.hasLineOfSight(target)
                && EnemyKarma.has(e, EnemyKarma.kaCost(e, ka))
                && !(target instanceof Player pl && (pl.isCreative() || pl.isSpectator()))) {
            t.putLong(CAST, now + 15);                                     // a short wind-up you can see coming
            t.putInt(CAST_TARGET, target.getId());
            t.putLong(CD, now + (ka.rare ? 500 : 600) + m.getRandom().nextInt(300));
            announce(e, ka);
        }
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (has(e, Ka.WEIGHT)) { e.setDeltaMovement(e.getDeltaMovement().multiply(1, 0.2, 1)); }
    }

    // ---------------------------------------------------------------- damage hooks: Delay, Mirror, Tether, Bind

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        Entity direct = event.getSource().getDirectEntity(), attacker = event.getSource().getEntity();
        // Mirror: it bounces back (and a bit of it reaches the mirror's owner too)
        if (has(victim, Ka.MIRROR) && attacker instanceof LivingEntity la && la != victim) {
            event.setCanceled(true);
            la.hurt(victim.damageSources().indirectMagic(victim, victim), event.getAmount());
            if (direct instanceof Projectile pr) pr.discard();
            float back = event.getAmount() * 0.15F * (1F + strain(victim) * 0.5F);
            victim.getPersistentData().putBoolean(DELAYED, true);
            victim.hurt(victim.damageSources().magic(), back);
            victim.getPersistentData().remove(DELAYED);
            ((ServerLevel) victim.level()).sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + 1, victim.getZ(), 10, 0.4, 0.6, 0.4, 0.05);
            return;
        }
        // Delay: hits from someone under Delay land 2 seconds later
        if (attacker instanceof LivingEntity la && has(la, Ka.DELAY) && !victim.getPersistentData().getBoolean(DELAYED)) {
            event.setCanceled(true);
            float amount = event.getAmount();
            var src = event.getSource();
            ServerScheduler.later(40, () -> {
                if (!victim.isAlive()) return;
                victim.getPersistentData().putBoolean(DELAYED, true);
                victim.invulnerableTime = 0;
                victim.hurt(src, amount);
                victim.getPersistentData().remove(DELAYED);
            });
            if (la instanceof ServerPlayer p) p.displayClientMessage(Component.literal("...your hit hasn't landed yet").withStyle(ChatFormatting.BLUE), true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim.getPersistentData().getBoolean(DELAYED)) return;
        CompoundTag t = victim.getPersistentData();
        // Delay's price: hits on the user are stored and land all together afterwards
        if (t.getLong("xw_ka_delay_self") > now(victim)) {
            t.putFloat("xw_ka_delay_store", t.getFloat("xw_ka_delay_store") + event.getAmount());
            event.setCanceled(true);
            return;
        }
        // Tether: whatever the tether's owner takes, the tethered take too - and it pulls back on the owner a little
        for (LivingEntity other : victim.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(24),
                o -> o != victim && has(o, Ka.TETHER) && o.getPersistentData().getInt(EFFECT + "tether" + SRC) == victim.getId())) {
            other.getPersistentData().putBoolean(DELAYED, true);
            other.hurt(victim.damageSources().magic(), event.getAmount() * 0.6F);
            other.getPersistentData().remove(DELAYED);
        }
        if (has(victim, Ka.TETHER) && src(victim, Ka.TETHER) instanceof LivingEntity owner && owner.isAlive()) {
            owner.getPersistentData().putBoolean(DELAYED, true);
            owner.hurt(owner.damageSources().magic(), event.getAmount() * 0.2F * (1F + strain(owner) * 0.5F));
            owner.getPersistentData().remove(DELAYED);
        }
        // Bind breaks when the one holding it gets hit
        for (LivingEntity held : victim.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(24),
                o -> has(o, Ka.BIND) && o.getPersistentData().getInt(EFFECT + "bind" + SRC) == victim.getId())) {
            held.getPersistentData().remove(EFFECT + "bind");
            held.getPersistentData().remove(Rooting.ROOTED_UNTIL);
        }
        if (has(victim, Ka.BIND) && !victim.getPersistentData().contains(EFFECT + "bind" + SRC)) victim.getPersistentData().remove(EFFECT + "bind");
    }
}

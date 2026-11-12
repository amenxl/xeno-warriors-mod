package com.xenowarriors.kaiplanet;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Brik, King Kai's planet's builder (an Otherworld resident - halo and all). He lives near King Kai's house and puts
 * back whatever gets broken (KaiPlanet): flies over to it - straight through the planet, being a spirit - and sets the
 * blocks back one after another (faster the more there is to do). Can't be hurt or pushed around. If he ever ends up
 * off the planet he flies back; too far away, or stuck somewhere he can't build from, and he teleports back.
 */
public class KaiBuilderEntity extends PathfinderMob {
    /** Further than this from the planet's middle: too far, he teleports home. */
    public static final double LEASH = 118;
    private static final int STUCK_TICKS = 10 * 20, REACH = 6;
    private int job = -1, nextPick, nextPlace;
    private long lastProgress;
    private boolean jumpedForJob, flyingHome;
    private final Int2LongOpenHashMap skip = new Int2LongOpenHashMap();

    public KaiBuilderEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setCustomName(Component.literal("Brik the Builder"));
        setPersistenceRequired();
        setDropChance(EquipmentSlot.MAINHAND, 0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override protected void registerGoals() {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean canBeLeashed(Player p) { return false; }
    @Override public boolean canChangeDimensions() { return false; }
    @Override public void knockback(double s, double x, double z) {}
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }

    /** Only /kill and the void get him (and he's back in a few seconds anyway). */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);
        return false;
    }

    @Override public boolean isInvulnerableTo(DamageSource src) { return !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY); }

    /** Working (flying to a spot / building). */
    public boolean working() { return job >= 0; }

    @Override
    public void tick() {
        noPhysics = (working() || flyingHome) && !level().isClientSide;
        super.tick();
        if (level().isClientSide) {
            if (working() || !onGround()) if (tickCount % 4 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.1, getZ(), 0, -0.02, 0);
            return;
        }
        ServerLevel l = (ServerLevel) level();
        if (!KaiPlanet.otherworld(l)) {                                          // somehow taken off to another world: gone (back home in a moment)
            l.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 20, 0.4, 0.8, 0.4, 0.05);
            discard();
            return;
        }
        if (tickCount % 20 == 0 && !KaiPlanet.theOne(l, this)) { discard(); return; }
        if (isPassenger()) stopRiding();
        if (KaiPlanet.plan() == null) return;
        Vec3 c = new Vec3(KaiBlueprint.CX, KaiBlueprint.CY, KaiBlueprint.CZ);
        if (position().distanceTo(c) > LEASH) { home(l, "too far from the planet"); return; }
        long now = l.getGameTime();
        if (job >= 0 && !KaiPlanet.pending.get(job)) job = -1;
        if (job < 0 && --nextPick <= 0) {
            nextPick = 10;
            job = pickJob(l, now);
            if (job >= 0) { lastProgress = now; jumpedForJob = false; }
        }
        if (job < 0) { idle(l); return; }
        flyingHome = false;
        BlockPos spot = KaiPlanet.plan().pos(job);
        Vec3 sc = Vec3.atCenterOf(spot);
        Vec3 out = sc.subtract(c);
        out = out.lengthSqr() < 1 ? new Vec3(0, 1, 0) : out.normalize();
        Vec3 hover = sc.add(out.scale(2.6)).add(0, -0.9, 0);
        fly(hover, 0.75);
        getLookControl().setLookAt(sc.x, sc.y, sc.z, 30F, 30F);
        holding(KaiPlanet.plan().state(job).getBlock().asItem().getDefaultInstance());
        if (getEyePosition().distanceTo(sc) <= REACH && --nextPlace <= 0) {
            int pend = KaiPlanet.pendingCount();
            int perTick = pend > 1500 ? 8 : pend > 300 ? 3 : 1;
            nextPlace = pend > 64 ? 1 : 3;
            int done = 0;
            for (int k = 0; k < perTick && job >= 0; k++) {
                if (KaiPlanet.place(l, job, this)) { done++; lastProgress = now; }
                else skip.put(job, now + 60 * 20);                                // (someone in the way: come back to it)
                job = nearbyJob(l, now);
            }
            if (done > 0) swing(InteractionHand.MAIN_HAND);
        }
        if (job >= 0 && now - lastProgress > STUCK_TICKS) {                      // can't build from here: teleport over (once per spot, then skip it)
            if (!jumpedForJob) {
                jumpedForJob = true; lastProgress = now;
                BlockPos s = KaiPlanet.plan().pos(job);
                Vec3 to = Vec3.atCenterOf(s).add(out.scale(2.6)).add(0, -0.9, 0);
                blink(l, to);
                KaiPlanet.teleports++;
            } else { skip.put(job, now + 60 * 20); job = -1; }
        }
    }

    private int pickJob(ServerLevel l, long now) {
        return KaiPlanet.pick(l, getX(), getY(), getZ(), 4000, i -> skip.get(i) > now);
    }

    /** The next spot within reach (keeps him working one area), else the nearest anywhere. */
    private int nearbyJob(ServerLevel l, long now) {
        return KaiPlanet.pick(l, getX(), getEyeY(), getZ(), 800, i -> skip.get(i) > now);
    }

    private void idle(ServerLevel l) {
        holding(ItemStack.EMPTY);
        BlockPos home = KaiPlanet.home(l);
        if (home == null) { flyingHome = false; setDeltaMovement(getDeltaMovement().scale(0.6)); return; }
        Vec3 h = Vec3.atBottomCenterOf(home);
        double d = position().distanceTo(h);
        flyingHome = d > 5;
        if (flyingHome) fly(h.add(0, 1.2, 0), 0.6);                              // back over to the house
        else {
            setDeltaMovement(getDeltaMovement().multiply(0.6, 1, 0.6).add(0, onGround() ? 0 : -0.04, 0));   // (lands)
            Player p = l.getNearestPlayer(this, 8);
            if (p != null) getLookControl().setLookAt(p, 20F, 20F);
        }
    }

    /** Flies at it (a spirit: through blocks when working). */
    private void fly(Vec3 to, double speed) {
        Vec3 d = to.subtract(position());
        double len = d.length();
        Vec3 v = len < 0.05 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.3) / len);
        setDeltaMovement(getDeltaMovement().scale(0.3).add(v.scale(0.7)));
        if (v.horizontalDistanceSqr() > 0.0004) { setYRot((float) (Math.atan2(-v.x, v.z) * 180 / Math.PI)); yBodyRot = getYRot(); }
    }

    private void holding(ItemStack s) {
        if (!ItemStack.isSameItem(getItemBySlot(EquipmentSlot.MAINHAND), s)) setItemSlot(EquipmentSlot.MAINHAND, s.copy());
    }

    /** Home, in a flash (off the planet, or lost). */
    void home(ServerLevel l, String why) {
        BlockPos home = KaiPlanet.home(l);
        Vec3 to = home != null ? Vec3.atBottomCenterOf(home).add(0, 0.2, 0) : new Vec3(KaiBlueprint.KING_KAI.getX() + 6.5, KaiBlueprint.KING_KAI.getY() + 2, KaiBlueprint.KING_KAI.getZ() + 6.5);
        job = -1;
        blink(l, to);
        KaiPlanet.teleports++;
        com.xenowarriors.XenoWarriors.LOGGER.debug("Brik teleported home: {}", why);
    }

    private void blink(ServerLevel l, Vec3 to) {
        l.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 16, 0.3, 0.7, 0.3, 0.04);
        l.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5F, 1.4F);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        l.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 1, to.z, 16, 0.3, 0.7, 0.3, 0.04);
    }

    private static final String[] LINES = {
            "Somebody blew a hole in the planet again? Don't worry - I'll have it fixed in no time!",
            "King Kai likes his planet in one piece. So do I!",
            "Training's fine, just try not to knock the house over.",
            "Every block back where it belongs. That's the job!",
            "The gravity up here keeps my hammer arm strong."};

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide) {
            int pend = KaiPlanet.pendingCount();
            String line = pend > 0 ? "Hold on, I've got " + pend + " block" + (pend == 1 ? "" : "s") + " to put back!" : LINES[random.nextInt(LINES.length)];
            p.sendSystemMessage(Component.literal("Brik: ").withStyle(ChatFormatting.GOLD).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
            getLookControl().setLookAt(p);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** (Tests) sends him off somewhere. */
    public void blinkForTest(Vec3 to) { teleportTo(to.x, to.y, to.z); job = -1; }
}

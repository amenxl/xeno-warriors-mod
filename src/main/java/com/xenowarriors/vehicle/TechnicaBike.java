package com.xenowarriors.vehicle;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Technica's own pink motorcycle. She gets its key the first time she's played; using the key (anywhere) summons the
 * bike next to her - calling it back if it's parked somewhere else. Its guns fire while she drives (attack: the Twin
 * Blasters; use: the Rocket Pod once it's on), and she adds attachments by using them on it:
 * <ul>
 *   <li>Twin Blasters (built in): rapid pink bolts where she looks.</li>
 *   <li>Rocket Pod: a heavy rocket that bursts (no block damage).</li>
 *   <li>Hover Thrusters: it flies like the Mk II vehicles.</li>
 *   <li>Armor Plating: twice the health, takes less damage.</li>
 *   <li>Nitro Booster: a third faster.</li>
 * </ul>
 * The attachments are kept with Technica (her saved data), so a summoned or re-parked bike always has them. A
 * Technica bot summons it too, when your vehicle has no room for her, and rides along (BotRides).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class TechnicaBike {
    private TechnicaBike() {}

    public static final int GUNS = 0, ROCKETS = 1, THRUSTERS = 2, PLATING = 3, NITRO = 4;
    /** The model's bone for each attachment. */
    public static final String[] BONES = {"guns", "rockets", "thrusters", "plating", "nitro"};
    public static final String[] NAMES = {"Twin Blasters", "Rocket Pod", "Hover Thrusters", "Armor Plating", "Nitro Booster"};
    private static final String ATTACH = "xw_technica_bike_attach", KEY_GIVEN = "xw_technica_key_given";
    /** Bikes out in the world, by owner. */
    private static final Map<UUID, UUID> OUT = new HashMap<>();
    private static final Map<UUID, Long> GUN_AT = new HashMap<>(), ROCKET_AT = new HashMap<>();
    public static final int GUN_CD = 5, ROCKET_CD = 50;
    /** (Tests) shots fired. */
    public static int shots, rockets;

    public static final RegistryObject<Item>[] ATTACHMENTS = registerAttachments();

    @SuppressWarnings("unchecked")
    private static RegistryObject<Item>[] registerAttachments() {
        RegistryObject<Item>[] r = new RegistryObject[BONES.length];
        for (int i = 1; i < BONES.length; i++) {
            final int bit = i;
            r[i] = com.xenowarriors.item.ModItems.ITEMS.register("bike_" + BONES[i], () -> new AttachmentItem(bit));
        }
        return r;
    }

    public static void init() {}

    /** An attachment for Technica's motorcycle: use it on her bike to put it on. */
    public static class AttachmentItem extends Item {
        public final int bit;
        public AttachmentItem(int bit) { super(new Item.Properties().stacksTo(1)); this.bit = bit; }
        @Override public void appendHoverText(ItemStack s, net.minecraft.world.level.Level l, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
            tip.add(Component.literal("An attachment for Technica's motorcycle - use it on her bike (as Technica) to put it on.").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal(switch (bit) {
                case ROCKETS -> "Use (right-click) while driving: fire a rocket";
                case THRUSTERS -> "The bike flies: Jump to lift off, Sprint key to come down";
                case PLATING -> "Twice the health, and it takes less damage";
                default -> "A third faster";
            }).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    // ---------------------------------------------------------------- whose bike, what's on it

    static boolean technica(Entity e) {
        if (e instanceof PartyBotEntity b) return b.character() == CharacterType.TECHNICA;
        return e instanceof Player p && ModCapabilities.getType(p) == CharacterType.TECHNICA;
    }

    public static int attachments(Player owner) {
        var t = owner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return t.contains(ATTACH) ? t.getInt(ATTACH) : 1 << GUNS;
    }

    private static void setAttachments(Player owner, int a) {
        var t = owner.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        t.putInt(ATTACH, a);
        owner.getPersistentData().put(Player.PERSISTED_NBT_TAG, t);
    }

    /** Using an attachment on her bike: on it goes. True if the item was an attachment (handled). */
    static boolean install(VehicleEntity v, Player p, ItemStack held) {
        if (!(held.getItem() instanceof AttachmentItem a)) return false;
        if (!technica(p)) { p.displayClientMessage(Component.literal("Only Technica knows how to fit that.").withStyle(ChatFormatting.GRAY), true); return true; }
        if (v.owner() != null && !v.owner().equals(p.getUUID())) { p.displayClientMessage(Component.literal("That's not your bike.").withStyle(ChatFormatting.RED), true); return true; }
        if (v.has(a.bit)) { p.displayClientMessage(Component.literal("It already has the " + NAMES[a.bit] + ".").withStyle(ChatFormatting.GRAY), true); return true; }
        int now = v.attachments() | (1 << a.bit);
        v.setAttachments(now);
        setAttachments(p, now);
        if (!p.isCreative()) held.shrink(1);
        v.level().playSound(null, v.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, 1.6F);
        ((ServerLevel) v.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, v.getX(), v.getY() + 0.8, v.getZ(), 20, 0.5, 0.4, 0.5, 0.1);
        p.displayClientMessage(Component.literal("Fitted the " + NAMES[a.bit] + " to your motorcycle!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    // ---------------------------------------------------------------- summoning

    /** Her bike in the world (loaded), or null. */
    public static VehicleEntity out(ServerPlayer owner) {
        UUID id = OUT.get(owner.getUUID());
        if (id != null) for (ServerLevel l : owner.server.getAllLevels()) if (l.getEntity(id) instanceof VehicleEntity v && v.isAlive()) return v;
        OUT.remove(owner.getUUID());
        for (ServerLevel l : owner.server.getAllLevels())                          // (after a restart: find it by its owner)
            for (VehicleEntity v : l.getEntities(Vehicles.TYPES.get(VehicleKind.TECHNICA_BIKE).get(), x -> x.isAlive() && owner.getUUID().equals(x.owner()))) {
                OUT.put(owner.getUUID(), v.getUUID());
                return v;
            }
        return null;
    }

    /** Summon (or call back) the owner's Technica bike beside `near`. */
    public static VehicleEntity summon(ServerPlayer owner, Entity near) {
        VehicleEntity old = out(owner);
        if (old != null) {
            if (old.level() == near.level() && old.distanceToSqr(near) < 6 * 6) return old;
            if (!old.getPassengers().isEmpty()) {                                    // (someone's riding it: it stays with them)
                owner.displayClientMessage(Component.literal("Your motorcycle is in use.").withStyle(ChatFormatting.GRAY), true);
                return null;
            }
            old.discard();
        }
        ServerLevel l = (ServerLevel) near.level();
        VehicleEntity v = Vehicles.TYPES.get(VehicleKind.TECHNICA_BIKE).get().create(l);
        if (v == null) return null;
        float yaw = near.getYRot();
        Vec3 at = spot(l, v, near);
        if (at == null) { owner.displayClientMessage(Component.literal("No room to bring the motorcycle here.").withStyle(ChatFormatting.GRAY), true); return null; }
        v.moveTo(at.x, at.y, at.z, yaw, 0);
        v.yBodyRot = yaw; v.yHeadRot = yaw;
        v.setOwner(owner.getUUID());
        v.setAttachments(attachments(owner));
        l.addFreshEntity(v);
        OUT.put(owner.getUUID(), v.getUUID());
        l.sendParticles(new DustParticleOptions(new Vector3f(1F, 0.35F, 0.7F), 1.6F), at.x, at.y + 0.7, at.z, 30, 0.6, 0.5, 0.6, 0.02);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
        return v;
    }

    private static Vec3 spot(ServerLevel l, VehicleEntity v, Entity near) {
        Vec3 side = Vec3.directionFromRotation(0, near.getYRot() + 90F);
        for (Vec3 c : new Vec3[]{near.position().add(side.scale(1.8)), near.position().add(side.scale(-1.8)), near.position().add(near.getLookAngle().multiply(1, 0, 1).normalize().scale(2.2)), near.position()}) {
            v.moveTo(c.x, c.y, c.z);
            if (l.noCollision(v)) return c;
            v.moveTo(c.x, c.y + 1, c.z);
            if (l.noCollision(v)) return c.add(0, 1, 0);
        }
        return null;
    }

    /** The key (the Technica bike's item): summon it, as Technica. */
    static boolean useKey(ServerPlayer p) {
        if (!technica(p)) { p.displayClientMessage(Component.literal("Only Technica can call this motorcycle.").withStyle(ChatFormatting.GRAY), true); return false; }
        if (com.xenowarriors.modes.ModeRules.arena(p.level()) && !p.isCreative()) { p.displayClientMessage(Component.literal("Vehicles can't be brought here.").withStyle(ChatFormatting.RED), true); return false; }
        VehicleEntity v = summon(p, p);
        if (v != null) p.displayClientMessage(Component.literal("Your motorcycle is here!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return v != null;
    }

    /** Technica gets her motorcycle key the first time she's played. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 100 != 37 || p instanceof net.minecraftforge.common.util.FakePlayer) return;
        if (!technica(p)) return;
        var t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (t.getBoolean(KEY_GIVEN)) return;
        t.putBoolean(KEY_GIVEN, true);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, t);
        ItemStack key = VehicleItem.make(VehicleKind.TECHNICA_BIKE, 0);
        if (!p.addItem(key)) p.drop(key, false);
        p.sendSystemMessage(Component.literal("You got your motorcycle key! Use it anywhere to call your pink motorcycle. It has guns (attack while driving), and you can fit attachments to it.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---------------------------------------------------------------- guns

    /** The driver fires: mode 0 the Twin Blasters, 1 a rocket. Players (from their packet) and bots. */
    public static void fire(LivingEntity driver, int mode, LivingEntity aimAt) {
        if (!(driver.getVehicle() instanceof VehicleEntity v) || !v.kind.technica() || v.getFirstPassenger() != driver) return;
        ServerLevel l = (ServerLevel) driver.level();
        long now = l.getGameTime();
        if (mode == 1) {
            if (!v.has(ROCKETS)) { if (driver instanceof ServerPlayer p) p.displayClientMessage(Component.literal("No Rocket Pod fitted yet.").withStyle(ChatFormatting.GRAY), true); return; }
            if (now - ROCKET_AT.getOrDefault(v.getUUID(), -999L) < ROCKET_CD) return;
            ROCKET_AT.put(v.getUUID(), now);
        } else {
            if (!v.has(GUNS)) return;
            if (now - GUN_AT.getOrDefault(v.getUUID(), -999L) < GUN_CD) return;
            GUN_AT.put(v.getUUID(), now);
        }
        Vec3 dir = aimAt != null ? aimAt.getBoundingBox().getCenter().subtract(driver.getEyePosition()).normalize() : driver.getLookAngle();
        float yaw = v.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-net.minecraft.util.Mth.cos(yaw), 0, -net.minecraft.util.Mth.sin(yaw)), fwd = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
        float lvl = driver instanceof ServerPlayer p ? com.xenowarriors.stats.Stats.characterLevel(p) : driver instanceof PartyBotEntity b ? b.botLevel() : 1;
        if (mode == 1) {
            Vec3 from = v.position().add(0, 1.6, 0).add(fwd.scale(-0.2));
            Vec3 end = ray(l, driver, v, from, dir, 48);
            l.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1F, 0.7F);
            trail(l, from, end, ParticleTypes.FLAME, 1.2);
            l.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 2, 0.4, 0.4, 0.4, 0);
            l.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1F, 1.2F);
            float dmg = 12F + lvl * 0.15F;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(3.0), e -> e.isAlive() && friendlyFire(driver, v, e) == false)) {
                e.invulnerableTime = 0;
                e.hurt(l.damageSources().explosion(driver, driver), dmg);
            }
            rockets++;
            return;
        }
        float dmg = 4F + lvl * 0.06F;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 from = v.position().add(0, 0.95, 0).add(right.scale(side * 0.55)).add(fwd.scale(1.0));
            Vec3 end = ray(l, driver, v, from, dir, 40);
            trail(l, from, end, new DustParticleOptions(new Vector3f(1F, 0.35F, 0.75F), 0.9F), 0.7);
            LivingEntity hit = hitAlong(l, driver, v, from, end);
            if (hit != null) { hit.invulnerableTime = 0; hit.hurt(l.damageSources().mobAttack(driver), dmg); }
        }
        l.playSound(null, v.getX(), v.getY() + 1, v.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.35F, 1.9F);
        shots++;
    }

    private static boolean friendlyFire(LivingEntity driver, VehicleEntity v, LivingEntity e) {
        if (e == driver || e == v || VehicleShots.together(driver, e)) return true;
        Player owner = driver instanceof Player p ? p : driver instanceof PartyBotEntity b ? b.owner() : null;
        if (owner == null) return false;
        if (e == owner || e instanceof Player q && com.xenowarriors.party.Parties.sameParty(owner, q)) return true;
        return e instanceof PartyBotEntity b && b.owner() != null && (b.owner() == owner || com.xenowarriors.party.Parties.sameParty(owner, b.owner()));
    }

    private static Vec3 ray(ServerLevel l, Entity driver, VehicleEntity v, Vec3 from, Vec3 dir, double reach) {
        Vec3 far = from.add(dir.scale(reach));
        BlockHitResult b = l.clip(new net.minecraft.world.level.ClipContext(from, far, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, driver));
        Vec3 end = b.getType() == HitResult.Type.MISS ? far : b.getLocation();
        LivingEntity hit = hitAlong(l, (LivingEntity) driver, v, from, end);
        return hit == null ? end : hit.getBoundingBox().getCenter();
    }

    private static LivingEntity hitAlong(ServerLevel l, LivingEntity driver, VehicleEntity v, Vec3 from, Vec3 end) {
        LivingEntity best = null; double bd = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.0), e -> e.isAlive() && !friendlyFire(driver, v, e))) {
            var c = e.getBoundingBox().inflate(0.3).clip(from, end);
            if (c.isPresent() && c.get().distanceToSqr(from) < bd) { bd = c.get().distanceToSqr(from); best = e; }
        }
        return best;
    }

    private static void trail(ServerLevel l, Vec3 a, Vec3 b, net.minecraft.core.particles.ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(60, d.length() / step);
        for (int i = 0; i <= n; i++) { Vec3 q = a.add(d.scale(i / (double) Math.max(1, n))); l.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
    }

    /** (Tests) */
    /**
     * A Technica bot fits an attachment from its bag to its owner's motorcycle (her bike: the one her player calls too).
     * True if it went on (false: it's already there).
     */
    public static boolean botInstall(Player owner, int bit) {
        int a = attachments(owner);
        if ((a & (1 << bit)) != 0) return false;
        a |= 1 << bit;
        setAttachments(owner, a);
        if (owner instanceof ServerPlayer sp) { VehicleEntity v = out(sp); if (v != null) v.setAttachments(a); }
        return true;
    }

    public static void setAttachmentsForTest(Player owner, int a) { setAttachments(owner, a); }
    public static void forgetForTest(UUID owner) { OUT.remove(owner); }
}

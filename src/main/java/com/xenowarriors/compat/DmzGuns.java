package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.item.DmzGunItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * With DragonMineZ: our Blaster guns, in the style of its Blaster Cannon - they fire DragonMineZ's own ki blasts
 * ({@code KiBlastEntity.setupKiBlast / setupKiLargeBlast}, the same call the Blaster Cannon makes: flat damage, a short
 * charge in front of the shooter, then it flies where they look). Mobs holding one (armed Skylanders, DragonMineZ's
 * soldiers who spawn with one) shoot it at what they're fighting and sometimes drop it.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzGuns {
    private DmzGuns() {}

    /** The guns. Blaster Cannon for reference: 10 damage, speed 1, size 1, 3 s between shots, 200 uses. */
    public enum Kind {
        RIFLE("Blaster Rifle", "Hold right-click: rapid fire", 600, 4, true, 1, 0, 4F, 1.7F, 0.45F, 1, 0x63E6FF, 0x0B5C96, false),
        BURST("Burst Cannon", "Three quick blasts", 250, 36, false, 3, 4, 8F, 1.3F, 0.8F, 2, 0x9CFF5D, 0x2E7A0A, false),
        MORTAR("Blaster Mortar", "Charges one huge blast", 120, 100, false, 1, 0, 28F, 0.75F, 2.6F, 18, 0xFFB43C, 0x9A3A00, true);

        public final String title, blurb;
        public final int durability, cooldown, shots, gap, cast, color, border;
        public final boolean held, large;
        public final float damage, speed, size;

        Kind(String title, String blurb, int durability, int cooldown, boolean held, int shots, int gap, float damage, float speed, float size, int cast, int color, int border, boolean large) {
            this.title = title; this.blurb = blurb; this.durability = durability; this.cooldown = cooldown; this.held = held; this.shots = shots; this.gap = gap;
            this.damage = damage; this.speed = speed; this.size = size; this.cast = cast; this.color = color; this.border = border; this.large = large;
        }

        public String damageLine() { return (shots > 1 ? shots + " x " : "") + Math.round(damage) + " ki damage" + (held ? " every " + cooldown + " ticks" : ", " + cooldown / 20F + " s reload"); }
    }

    private static Boolean loaded;
    private static boolean broken;
    private static Constructor<?> blastNew;
    private static Method setup, setupLarge;

    /** (For the tests) shots fired: "kind shooter damage". */
    public static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    private static void note(String s) {
        if (LOG.size() > 60) LOG.remove(0);
        LOG.add(s);
    }

    public static boolean on() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("dragonminez");
        return loaded;
    }

    private static boolean ready() {
        if (!on() || broken) return false;
        if (setup != null) return true;
        try {
            Class<?> kb = Class.forName("com.dragonminez.common.init.entities.ki.KiBlastEntity");
            blastNew = kb.getConstructor(Level.class, LivingEntity.class);
            setup = kb.getMethod("setupKiBlast", LivingEntity.class, float.class, float.class, int.class, int.class, int.class, float.class, int.class);
            setupLarge = kb.getMethod("setupKiLargeBlast", LivingEntity.class, float.class, float.class, int.class, int.class, int.class, float.class, int.class);
            return true;
        } catch (Throwable t) {
            broken = true; setup = null;
            XenoWarriors.LOGGER.warn("Blaster guns can't fire (DragonMineZ's ki blast API changed?)", t);
            return false;
        }
    }

    /** One shot (or burst) from this shooter, where it's looking (or at target, for mobs). */
    public static void fire(LivingEntity shooter, Kind kind, LivingEntity target) {
        if (!(shooter.level() instanceof ServerLevel level) || !ready()) return;
        for (int i = 0; i < kind.shots; i++) {
            Runnable one = () -> {
                if (!shooter.isAlive() || shooter.isRemoved()) return;
                if (target != null) { if (!target.isAlive()) return; aim(shooter, target); }
                try {
                    Object blast = blastNew.newInstance(level, shooter);
                    (kind.large ? setupLarge : setup).invoke(blast, shooter, kind.damage, kind.speed, kind.color, kind.border, 0xFFFFFF, kind.size, kind.cast);   // (it adds itself to the world)
                    level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), kind.large ? SoundEvents.FIRECHARGE_USE : SoundEvents.BLAZE_SHOOT,
                            SoundSource.PLAYERS, kind.large ? 1F : 0.5F, kind == Kind.RIFLE ? 1.6F : 1.1F);
                    note(kind.name() + " " + net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(shooter.getType()) + " dmg=" + kind.damage + " size=" + kind.size
                            + " -> " + blast.getClass().getSimpleName() + " in world=" + (((net.minecraft.world.entity.Entity) blast).isAddedToWorld()));
                } catch (Throwable t) {
                    if (!broken) XenoWarriors.LOGGER.warn("A blaster gun shot failed", t);
                    broken = true;
                }
            };
            if (i == 0) one.run();
            else com.xenowarriors.skill.ServerScheduler.later(i * kind.gap, one);
        }
    }

    /** Faces the target (body, head, pitch): the blast flies where its shooter looks. */
    public static void aim(LivingEntity s, LivingEntity target) {
        Vec3 d = target.getBoundingBox().getCenter().subtract(s.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90F), pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        s.setYRot(yaw); s.yRotO = yaw; s.setYHeadRot(yaw); s.yHeadRotO = yaw; s.yBodyRot = yaw; s.yBodyRotO = yaw;
        s.setXRot(pitch); s.xRotO = pitch;
    }

    public static Kind kindOf(ItemStack s) { return s.getItem() instanceof DmzGunItem g ? g.kind() : null; }

    /** Our gun items (empty without DragonMineZ). */
    public static List<Item> items() {
        List<Item> out = new ArrayList<>();
        for (var ro : new net.minecraftforge.registries.RegistryObject<?>[]{com.xenowarriors.item.ModItems.BLASTER_RIFLE, com.xenowarriors.item.ModItems.BURST_CANNON, com.xenowarriors.item.ModItems.BLASTER_MORTAR})
            if (ro != null && ro.isPresent()) out.add((Item) ro.get());
        return out;
    }

    public static ItemStack random(net.minecraft.util.RandomSource r) {
        List<Item> all = items();
        return all.isEmpty() ? ItemStack.EMPTY : new ItemStack(all.get(r.nextInt(all.size())));
    }

    /** A mob holding one: shoots it at its target from range, now and then. */
    public static final class ShootGoal extends Goal {
        private final Mob mob;
        private int wait = 20;

        public ShootGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.LOOK)); }

        private Kind kind() { return kindOf(mob.getMainHandItem()); }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            if (kind() == null || t == null || !t.isAlive()) return false;
            double d = mob.distanceTo(t);
            return d > 3 && d < 28 && mob.hasLineOfSight(t);
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 60F, 60F);
            if (--wait > 0) return;
            Kind k = kind();
            if (k == null) return;
            if (k.held) {                                                      // (a rifle: short volleys)
                for (int i = 0; i < 4; i++) com.xenowarriors.skill.ServerScheduler.later(i * k.cooldown, () -> { if (mob.isAlive() && t.isAlive() && kind() == Kind.RIFLE) fire(mob, Kind.RIFLE, t); });
                wait = 50 + mob.getRandom().nextInt(30);
            } else {
                fire(mob, k, t);
                wait = k.cooldown + 20 + mob.getRandom().nextInt(30);
            }
        }
    }

    private static final Set<String> SOLDIERS = Set.of("saga_friezasoldier01", "saga_friezasoldier02", "saga_friezasoldier03", "saga_morosoldier", "red_ribbon_soldier", "bandit");

    /** Mobs with one of our guns shoot it (and may drop it); DragonMineZ's soldiers sometimes turn up carrying one. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !on() || !(e.getEntity() instanceof PathfinderMob mob)) return;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id != null && id.getNamespace().equals("dragonminez") && SOLDIERS.contains(id.getPath()) && !mob.getPersistentData().getBoolean("xw_gun_rolled")) {
            mob.getPersistentData().putBoolean("xw_gun_rolled", true);
            if (mob.getMainHandItem().isEmpty() && mob.getRandom().nextFloat() < 0.15F) {
                ItemStack gun = random(mob.getRandom());
                if (!gun.isEmpty()) { mob.setItemSlot(EquipmentSlot.MAINHAND, gun); mob.setDropChance(EquipmentSlot.MAINHAND, 0.25F); }
            }
        }
        if (kindOf(mob.getMainHandItem()) != null) {
            mob.goalSelector.addGoal(2, new ShootGoal(mob));
            if (mob.getPersistentData().getBoolean("xw_gun_drop_set")) return;
            mob.getPersistentData().putBoolean("xw_gun_drop_set", true);
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.2F);                       // (now and then, when a player kills it)
        }
    }
}

package com.xenowarriors.npc;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.npc.rebuild.RebuildData;
import com.xenowarriors.npc.rebuild.VillageRebuild;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Every village has two founders - a young man and a young woman - who keep it going: when the village is down to few
 * people (or none), they bring a new townsperson into it every couple of minutes until it's lived in again. They're
 * very strong (lots of health, heal quickly, hit hard, fight off what threatens the village), stay close to it, can't
 * be taken by souls, and if one of them dies they're back 5 minutes later.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class VillageFounders {
    public static final String TAG = "xw_founder";
    public static final int RESPAWN = 5 * 60 * 20, NEW_PERSON_EVERY = 2 * 60 * 20, WANT = 6, MISSING_AFTER = 60 * 20;
    public static final double HEALTH = 300, DAMAGE = 14, ARMOR = 10, REGEN = 2F;   // (regen: health a second)
    private static final UUID HP = UUID.fromString("5b1f7d4e-3c2a-4e8b-9a61-0c7d2e4f8a10"), DMG = UUID.fromString("5b1f7d4e-3c2a-4e8b-9a61-0c7d2e4f8a11"),
            ARM = UUID.fromString("5b1f7d4e-3c2a-4e8b-9a61-0c7d2e4f8a12");
    private static final String[] BOYS = {"Ren", "Kai", "Jun", "Takeo", "Hiro", "Sora"}, GIRLS = {"Aiko", "Mei", "Yuna", "Hana", "Rin", "Saki"};
    private static final String[] ROLES = {"boy", "girl"};

    /** (Tests) founders spawned, respawned after dying, and new people they brought. */
    public static int spawned, respawned, newPeople;

    private VillageFounders() {}

    public static boolean is(Entity e) { return e.getTags().contains(TAG); }

    /** The village a founder belongs to (its anchor), or null. */
    static BlockPos home(Entity e) { return e.getPersistentData().contains("xw_founder_home") ? BlockPos.of(e.getPersistentData().getLong("xw_founder_home")) : null; }

    // ------------------------------------------------------------------ saved: per village, each founder's id, when they died, when last seen

    public static final class Data extends SavedData {
        static final String NAME = "xenowarriors_village_founders";
        final CompoundTag villages;
        Data(CompoundTag t) { villages = t; }
        static Data load(CompoundTag t) { return new Data(t.getCompound("v")); }
        @Override public CompoundTag save(CompoundTag t) { t.put("v", villages); return t; }
        CompoundTag of(BlockPos anchor) {
            String k = Long.toString(anchor.asLong());
            if (!villages.contains(k)) { villages.put(k, new CompoundTag()); setDirty(); }
            return villages.getCompound(k);
        }
    }

    static Data data(ServerLevel l) { return l.getDataStorage().computeIfAbsent(Data::load, () -> new Data(new CompoundTag()), Data.NAME); }

    // ------------------------------------------------------------------ keeping them

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l) || l.getGameTime() % 100 != 37) return;
        RebuildData rd = VillageRebuild.data(l);
        if (rd == null || rd.villages().isEmpty()) return;
        if (com.xenowarriors.tower.Tower.is(l) || com.xenowarriors.vr.Vr.is(l)) return;
        Data d = data(l);
        long now = l.getGameTime();
        for (RebuildData.Village v : rd.villages()) {
            BlockPos a = v.anchor;
            if (!l.isPositionEntityTicking(a) || l.getNearestPlayer(a.getX(), a.getY(), a.getZ(), 96, false) == null) continue;
            CompoundTag t = d.of(a);
            AABB area = new AABB(v.box.minX(), v.box.minY(), v.box.minZ(), v.box.maxX() + 1, v.box.maxY() + 1, v.box.maxZ() + 1).inflate(32);
            List<HumanNpcEntity> founders = l.getEntitiesOfClass(HumanNpcEntity.class, area, h -> is(h) && a.equals(home(h)) && h.isAlive());
            for (String role : ROLES) {
                HumanNpcEntity f = null;
                for (HumanNpcEntity h : founders) if (role.equals(h.getPersistentData().getString("xw_founder_role"))) f = h;
                if (f != null) { t.putLong(role + "_seen", now); continue; }
                long died = t.getLong(role + "_died");
                boolean dead = died > 0;
                if (dead && now - died < RESPAWN) continue;                                     // (back 5 minutes after dying)
                if (!dead && t.contains(role + "_seen") && now - t.getLong(role + "_seen") < MISSING_AFTER) continue;   // (just out of sight a moment)
                if (spawn(l, v, role, t) != null) {
                    if (dead) { respawned++; t.remove(role + "_died"); } else spawned++;
                    t.putLong(role + "_seen", now);
                    d.setDirty();
                }
            }
            // the village down to few people: the founders bring a new one
            if (founders.size() == 2 && now - t.getLong("born") >= NEW_PERSON_EVERY) {
                int people = l.getEntitiesOfClass(HumanNpcEntity.class, area, h -> !is(h) && h.isAlive() && !h.possessed() && !h.raider()).size()
                        + l.getEntitiesOfClass(Villager.class, area, Villager::isAlive).size();
                if (people < WANT) { newPerson(l, v, founders); t.putLong("born", now); d.setDirty(); }
            }
        }
    }

    private static BlockPos spot(ServerLevel l, BlockPos a, int tries) {
        var r = l.getRandom();
        for (int i = 0; i < tries; i++) {
            int x = a.getX() + r.nextInt(17) - 8, z = a.getZ() + r.nextInt(17) - 8;
            BlockPos p = new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(p.getY() - a.getY()) < 12 && l.getBlockState(p.below()).isSolid() && l.noCollision(new AABB(p).inflate(0.3, 0, 0.3).expandTowards(0, 1, 0))) return p;
        }
        return null;
    }

    static HumanNpcEntity spawn(ServerLevel l, RebuildData.Village v, String role, CompoundTag t) {
        BlockPos at = spot(l, v.anchor, 16);
        if (at == null) return null;
        HumanNpcEntity h = ModNpcs.HUMAN.get().create(l);
        if (h == null) return null;
        h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, l.getRandom().nextFloat() * 360F, 0F);
        h.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        boolean boy = role.equals("boy");
        h.setFounder(boy ? "spiky" : "girl");
        String name = t.getString(role + "_name");
        if (name.isEmpty()) { name = (boy ? BOYS : GIRLS)[l.getRandom().nextInt(6)]; t.putString(role + "_name", name); }
        h.setCustomName(Component.literal(name + ", Village Founder").withStyle(ChatFormatting.GOLD));
        h.setCustomNameVisible(false);
        h.addTag(TAG);
        h.getPersistentData().putString("xw_founder_role", role);
        h.getPersistentData().putLong("xw_founder_home", v.anchor.asLong());
        h.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(boy ? Items.DIAMOND_SWORD : Items.DIAMOND_AXE));
        h.setDropChance(EquipmentSlot.MAINHAND, 0F);
        strong(h);
        h.setHealth(h.getMaxHealth());
        h.setPersistenceRequired();
        h.restrictTo(v.anchor, 40);
        l.addFreshEntity(h);
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, h.getX(), h.getY() + 1, h.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
        return h;
    }

    private static void mod(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, double v) {
        var in = e.getAttribute(a);
        if (in == null || in.getModifier(id) != null) return;
        in.addPermanentModifier(new AttributeModifier(id, "Village founder", v - in.getBaseValue(), AttributeModifier.Operation.ADDITION));
    }

    static void strong(HumanNpcEntity h) {
        mod(h, Attributes.MAX_HEALTH, HP, HEALTH);
        mod(h, Attributes.ATTACK_DAMAGE, DMG, DAMAGE);
        mod(h, Attributes.ARMOR, ARM, ARMOR);
    }

    /** A new townsperson joins, next to the founders (hearts between them). */
    private static void newPerson(ServerLevel l, RebuildData.Village v, List<HumanNpcEntity> founders) {
        HumanNpcEntity f = founders.get(0);
        BlockPos at = spot(l, f.blockPosition(), 12);
        if (at == null) at = spot(l, v.anchor, 12);
        if (at == null) return;
        HumanNpcEntity h = ModNpcs.HUMAN.get().create(l);
        if (h == null) return;
        h.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, l.getRandom().nextFloat() * 360F, 0F);
        h.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.BREEDING, null, null);
        h.setPersistenceRequired();
        l.addFreshEntity(h);
        for (HumanNpcEntity x : founders) l.sendParticles(ParticleTypes.HEART, x.getX(), x.getY() + 2, x.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, h.getX(), h.getY() + 1, h.getZ(), 15, 0.4, 0.6, 0.4, 0.05);
        newPeople++;
    }

    /** Healing quickly, and staying near their village. */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof HumanNpcEntity h) || h.level().isClientSide || !is(h)) return;
        if (h.tickCount % 20 == 0 && h.isAlive() && h.getHealth() < h.getMaxHealth()) h.heal((float) REGEN);
        if (h.tickCount % 200 == 0) {
            strong(h);                                                                       // (kept after a reload)
            BlockPos home = home(h);
            if (home != null) { h.restrictTo(home, 40); if (h.distanceToSqr(home.getX(), home.getY(), home.getZ()) > 64 * 64 && h.getTarget() == null) h.getNavigation().moveTo(home.getX(), home.getY(), home.getZ(), 1.0); }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof HumanNpcEntity h) || !(h.level() instanceof ServerLevel l) || !is(h) || e.isCanceled()) return;
        BlockPos home = home(h);
        if (home == null) return;
        Data d = data(l);
        CompoundTag t = d.of(home);
        t.putLong(h.getPersistentData().getString("xw_founder_role") + "_died", l.getGameTime());
        d.setDirty();
    }

    /** (Tests) the founders of the village nearest this spot (anchor within 256), and its anchor. */
    public static BlockPos villageNear(ServerLevel l, BlockPos p) {
        RebuildData rd = VillageRebuild.data(l);
        if (rd == null) return null;
        BlockPos best = null; double bd = 256 * 256;
        for (RebuildData.Village v : rd.villages()) { double dd = v.anchor.distSqr(p); if (dd < bd) { bd = dd; best = v.anchor; } }
        return best;
    }

    public static List<HumanNpcEntity> foundersOf(ServerLevel l, BlockPos anchor) {
        return l.getEntitiesOfClass(HumanNpcEntity.class, new AABB(anchor).inflate(128), h -> is(h) && anchor.equals(home(h)) && h.isAlive());
    }
}

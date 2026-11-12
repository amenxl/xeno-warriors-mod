package com.xenowarriors.foes;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.entity.ModEntities;
import com.xenowarriors.ka.Element;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Season 3's enemies and where they turn up (at night, out in the world - never in the modes' arenas):
 * Spell Casters hunt anyone at Character Level 25+ (two at once from 60); Vessels (Cynic Dancers) prowl Recarnet (away
 * from Aurora City and the Royal Capital), and the Sky Rebellion's camps day and night, rarely the Overworld; the Ancient
 * Swordstress (a C-soul) comes out at night for anyone at Level 35+.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Foes {
    private Foes() {}

    public static final float CRYSTAL_CHANCE = 0.35F, ESSENCE_CHANCE = 0.3F;

    public static final RegistryObject<EntityType<ElementalistEntity>> ELEMENTALIST = ModEntities.ENTITY_TYPES.register("elementalist",
            () -> EntityType.Builder.<ElementalistEntity>of(ElementalistEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).clientTrackingRange(10).build("elementalist"));
    public static final RegistryObject<EntityType<VesselEntity>> VESSEL = ModEntities.ENTITY_TYPES.register("vessel",
            () -> EntityType.Builder.<VesselEntity>of(VesselEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).clientTrackingRange(10).build("vessel"));

    public static final RegistryObject<EntityType<SwordstressEntity>> SWORDSTRESS = ModEntities.ENTITY_TYPES.register("ancient_swordstress",
            () -> EntityType.Builder.<SwordstressEntity>of(SwordstressEntity::new, MobCategory.MONSTER).sized(0.7F, 1.9F).clientTrackingRange(10).build("ancient_swordstress"));

    public static void init() {}

    @Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attrs {
        @SubscribeEvent
        public static void attributes(EntityAttributeCreationEvent event) {
            event.put(ELEMENTALIST.get(), ElementalistEntity.createAttributes().build());
            event.put(VESSEL.get(), VesselEntity.createAttributes().build());
            event.put(SWORDSTRESS.get(), SwordstressEntity.createAttributes().build());
        }
    }

    /** Spell Casters (Elementalists), Vessels and the Ancient Swordstress are Season 3 enemies (locked until /xeno season unlock). */
    public static final int SEASON = 3;

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 400 != 137) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel l) || p.isSpectator() || !com.xenowarriors.modes.ModeRules.canGrief(l)) continue;
            boolean recarnet = com.xenowarriors.recarnet.Recarnet.is(l), over = l.dimension() == Level.OVERWORLD;
            if (!recarnet && !over || !com.xenowarriors.character.Seasons.unlocked(p.server, SEASON)) continue;
            int lvl = com.xenowarriors.stats.Stats.characterLevel(p);
            BlockPos at = p.blockPosition();
            boolean town = recarnet && (com.xenowarriors.recarnet.Recarnet.inAurora(l, at) || com.xenowarriors.recarnet.RoyalCapitalFeature.inCapital(l, at));
            // Elementalists
            if (l.isNight() && lvl >= 25 && !town && l.getEntitiesOfClass(ElementalistEntity.class, p.getBoundingBox().inflate(64)).isEmpty() && l.random.nextFloat() < 0.12F) {
                int n = lvl >= 60 ? 2 : 1;
                for (int i = 0; i < n; i++) spawnElementalist(l, at);
            }
            // Vessels
            boolean srNear = !l.getEntitiesOfClass(com.xenowarriors.sr.SrMemberEntity.class, p.getBoundingBox().inflate(96)).isEmpty();
            float vc = srNear ? 0.35F : recarnet && l.isNight() && !town ? 0.1F : over && l.isNight() ? 0.03F : 0F;
            if (lvl >= 15 && vc > 0 && l.getEntitiesOfClass(VesselEntity.class, p.getBoundingBox().inflate(64)).size() < 2 && l.random.nextFloat() < vc) spawnVessel(l, at);
            // the Ancient Swordstress: a strong C-soul, out at night for the strong
            if (l.isNight() && lvl >= 35 && !town && l.getEntitiesOfClass(SwordstressEntity.class, p.getBoundingBox().inflate(96)).isEmpty() && l.random.nextFloat() < (recarnet ? 0.06F : 0.03F)) {
                BlockPos s = spot(l, at);
                SwordstressEntity sw = s == null ? null : SWORDSTRESS.get().create(l);
                if (sw != null) place(l, sw, s);
            }
        }
    }

    private static BlockPos spot(ServerLevel l, BlockPos near) {
        for (int tries = 0; tries < 8; tries++) {
            double a = l.random.nextDouble() * Math.PI * 2, d = 18 + l.random.nextInt(14);
            BlockPos at = near.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
            if (!l.isLoaded(at)) continue;
            BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            if (com.xenowarriors.recarnet.Recarnet.is(l) && top.getY() >= com.xenowarriors.recarnet.RoyalCapitalFeature.FLOOR_Y) continue;
            if (!l.getBlockState(top.below()).isSolidRender(l, top.below()) || !l.isEmptyBlock(top) || !l.isEmptyBlock(top.above())) continue;
            return top;
        }
        return null;
    }

    private static <T extends Mob> T place(ServerLevel l, T m, BlockPos at) {
        m.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, l.random.nextFloat() * 360F, 0);
        m.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        l.addFreshEntity(m);
        return m;
    }

    public static ElementalistEntity spawnElementalist(ServerLevel l, BlockPos near) {
        BlockPos at = spot(l, near);
        ElementalistEntity e = at == null ? null : ELEMENTALIST.get().create(l);
        if (e == null) return null;
        Element[] pool = Element.HUMAN;
        Element a = pool[l.random.nextInt(pool.length)], b;
        do b = pool[l.random.nextInt(pool.length)]; while (b == a);
        e.setup(a, b, l.random.nextInt(ElementalistEntity.LOOKS.length));
        return place(l, e, at);
    }

    public static VesselEntity spawnVessel(ServerLevel l, BlockPos near) {
        BlockPos at = spot(l, near);
        VesselEntity v = at == null ? null : VESSEL.get().create(l);
        if (v == null) return null;
        v.setLook(l.random.nextInt(2));
        return place(l, v, at);
    }

    /** (Tests) right beside this spot (no search). */
    public static <T extends Mob> T placeForTest(ServerLevel l, T m, BlockPos at) { return place(l, m, at); }
}

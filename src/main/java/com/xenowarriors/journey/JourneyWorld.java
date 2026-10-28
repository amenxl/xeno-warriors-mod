package com.xenowarriors.journey;

import com.xenowarriors.collection.Catalog;
import com.xenowarriors.tower.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Journey mode's places, in the Tower dimension (far from the Tower floors): a floating path of diamond platforms
 * (one per step, with a hovering label) joined by glass bridges that build as you clear each step, and an arena
 * below it for the battles, re-skinned for whoever you're fighting.
 */
public final class JourneyWorld {
    private JourneyWorld() {}

    public static final int SPACING = 512, Z = 30000, PATH_Y = 130, ARENA_R = 16, GAP = 16;
    private static final UUID HP = UUID.fromString("7c1d4e22-8b4e-4d66-ab62-1e2f3d4c5b01"), DMG = UUID.fromString("7c1d4e22-8b4e-4d66-ab62-1e2f3d4c5b02");

    public static BlockPos arena(int slot) { return new BlockPos(slot * SPACING + SPACING / 2, Tower.Y, Z); }
    public static BlockPos start(int slot) { return new BlockPos(slot * SPACING + SPACING / 2, PATH_Y, Z + 70); }

    public static AABB area(int slot) {
        BlockPos a = arena(slot), s = start(slot);
        return new AABB(a.getX() - 40, a.getY() - 20, a.getZ() - 40, a.getX() + 40, s.getY() + 40, s.getZ() + GAP * 20 + 10);
    }

    // ------------------------------------------------------------------------------------------------ the path

    /** Clears what an earlier run left and lays out every platform (no bridges yet). */
    public static void buildPath(ServerLevel l, int slot, JourneyMap map) {
        BlockPos s = start(slot);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = s.getX() - 22; x <= s.getX() + 22; x++)
            for (int z = s.getZ() - 8; z <= s.getZ() + GAP * 19 + 6; z++)
                for (int y = s.getY() - 2; y <= s.getY() + 24; y++)
                    if (!l.getBlockState(m.set(x, y, z)).isAir()) l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
        for (JourneyMap.Node n : map.nodes) platform(l, map.pos(slot, n), ring(n.kind, n.state == JourneyMap.HERE ? 2 : 0));
    }

    /** Ring colours: state 0 = ahead (by step kind), 1 = where you are, 2 = done. */
    static BlockState ring(int kind, int state) {
        if (state == 3) return Blocks.BLACK_STAINED_GLASS.defaultBlockState();
        if (state == 1) return Blocks.RED_STAINED_GLASS.defaultBlockState();
        if (state == 2) return Blocks.GRAY_STAINED_GLASS.defaultBlockState();
        return switch (kind) {
            case JourneyRules.BATTLE -> Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            case JourneyRules.SCENARIO -> Blocks.CYAN_STAINED_GLASS.defaultBlockState();
            case JourneyRules.HEAL -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case JourneyRules.BOSS -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case JourneyRules.EVENT -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
            case JourneyRules.ITEM -> Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
            case JourneyRules.DBZ -> Blocks.BROWN_STAINED_GLASS.defaultBlockState();
            default -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        };
    }

    static void platform(ServerLevel l, BlockPos c, BlockState ring) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            int d = Math.abs(dx) + Math.abs(dz);
            if (d > 4) continue;
            BlockState b = d == 4 ? ring : d == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : d == 2 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            l.setBlock(m.set(c.getX() + dx, c.getY(), c.getZ() + dz), b, 2);
        }
        l.setBlock(m.set(c.getX(), c.getY() + 4, c.getZ()), Blocks.LIGHT.defaultBlockState(), 2);
    }

    static void recolor(ServerLevel l, BlockPos c, BlockState ring) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
            if (Math.abs(dx) + Math.abs(dz) == 4) l.setBlock(m.set(c.getX() + dx, c.getY(), c.getZ() + dz), ring, 2);
    }

    /** The bridge from one step to the next, in the order it's laid (start to end), 3 wide; the second half one block up. */
    static List<BlockPos> bridge(BlockPos a, BlockPos b) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        int n = (int) Math.ceil(Math.sqrt(a.distSqr(b)) * 2);
        for (int k = 0; k <= n; k++) {
            double t = k / (double) n;
            int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t), z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
            int y = t < 0.5 ? a.getY() : b.getY();
            for (int w = -1; w <= 1; w++) {
                BlockPos p = new BlockPos(x + w, y, z);
                if (Math.abs(p.getX() - a.getX()) + Math.abs(p.getZ() - a.getZ()) <= 4 && p.getY() == a.getY()) continue;
                if (Math.abs(p.getX() - b.getX()) + Math.abs(p.getZ() - b.getZ()) <= 4 && p.getY() == b.getY()) continue;
                if (seen.add(p)) out.add(p);
            }
        }
        return out;
    }

    static boolean onPlatform(net.minecraft.world.entity.Entity e, BlockPos c) {
        return Math.abs(e.getX() - (c.getX() + .5)) + Math.abs(e.getZ() - (c.getZ() + .5)) <= 4.6 && e.getY() > c.getY() - 0.5 && e.getY() < c.getY() + 4;
    }

    /** A hovering label over a step. */
    static Entity label(ServerLevel l, BlockPos c, String text, int color, int step) {
        String json = "[{\"text\":\"" + String.format("%02d", step) + "\\n\",\"color\":\"#C8D8E8\"},{\"text\":\"" + text + "\",\"color\":\"#" + String.format("%06X", color & 0xFFFFFF) + "\",\"bold\":true}]";
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:text_display");
        t.putString("text", json);
        t.putString("billboard", "center");
        t.putInt("background", 0x40000000);
        t.putBoolean("shadow", true);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(0, 0, 0));
        tr.put("scale", floats(1.8F, 1.8F, 1.8F));
        t.put("transformation", tr);
        Entity e = EntityType.loadEntityRecursive(t, l, en -> { en.moveTo(c.getX() + .5, c.getY() + 3.2, c.getZ() + .5, 0, 0); return en; });
        if (e == null) return null;
        e.addTag("xw_journey");
        l.addFreshEntity(e);
        return e;
    }

    private static ListTag floats(float... v) { ListTag t = new ListTag(); for (float f : v) t.add(FloatTag.valueOf(f)); return t; }

    // ------------------------------------------------------------------------------------------------ the arena


    /** The arena's walls (once per slot) and a floor in the look of who you're fighting. */
    static void arena(ServerLevel l, int slot, JourneyRules.Foe foe, boolean boss) {
        BlockPos c = arena(slot);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int Y = c.getY();
        if (!l.getBlockState(m.set(c.getX() + ARENA_R + 1, Y + 20, c.getZ())).is(Blocks.BARRIER)) {
            for (int dx = -ARENA_R - 2; dx <= ARENA_R + 2; dx++) for (int dz = -ARENA_R - 2; dz <= ARENA_R + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > ARENA_R + 1.5) continue;
                l.setBlock(m.set(c.getX() + dx, Y - 1, c.getZ() + dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
                if (d > ARENA_R + 0.5) for (int y = 1; y <= 30; y++) l.setBlock(m.set(c.getX() + dx, Y + y, c.getZ() + dz), y <= 4 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.BARRIER.defaultBlockState(), 2);
            }
        }
        Block[] look = boss ? new Block[]{Blocks.DEEPSLATE_TILES, Blocks.CRYING_OBSIDIAN, Blocks.RED_STAINED_GLASS}
                : switch (foe) {
            case GOV -> new Block[]{Blocks.SMOOTH_QUARTZ, Blocks.SEA_LANTERN, Blocks.CYAN_STAINED_GLASS};
            case SR -> new Block[]{Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.YELLOW_STAINED_GLASS};
            case SKY_HATERS -> new Block[]{Blocks.GRASS_BLOCK, Blocks.WHITE_WOOL, Blocks.WHITE_STAINED_GLASS};
            case DBZ -> new Block[]{Blocks.SMOOTH_SANDSTONE, Blocks.ORANGE_TERRACOTTA, Blocks.ORANGE_STAINED_GLASS};
            case DARK -> new Block[]{Blocks.BLACKSTONE, Blocks.CRYING_OBSIDIAN, Blocks.PURPLE_STAINED_GLASS};
            default -> new Block[]{Blocks.SMOOTH_STONE, Blocks.SEA_LANTERN, Blocks.LIGHT_BLUE_STAINED_GLASS};
        };
        for (int dx = -ARENA_R - 2; dx <= ARENA_R + 2; dx++) for (int dz = -ARENA_R - 2; dz <= ARENA_R + 2; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > ARENA_R + 1.5) continue;
            int x = c.getX() + dx, z = c.getZ() + dz;
            if (d <= ARENA_R + 0.5) {
                Block top = d > ARENA_R - 0.5 ? Blocks.POLISHED_BLACKSTONE_BRICKS : (Math.abs(dx) + Math.abs(dz)) % 6 == 0 && d > 3 ? look[1] : look[0];
                l.setBlock(m.set(x, Y, z), top.defaultBlockState(), 2);
                for (int y = 1; y <= 6; y++) if (!l.getBlockState(m.set(x, Y + y, z)).isAir() && d < ARENA_R) l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            } else for (int y = 1; y <= 4; y++) l.setBlock(m.set(x, Y + y, z), look[2].defaultBlockState(), 2);
        }
    }

    // ------------------------------------------------------------------------------------------------ enemies

    /** Spawns one wave: returns what was spawned. */
    static List<Mob> spawn(ServerLevel l, int slot, JourneyRules.Foe foe, int diff, int step, int players, List<ServerPlayer> targets, float scale) {
        BlockPos c = arena(slot);
        var rnd = l.getRandom();
        int count = Math.min(14, 3 + diff + step / 3 + (players - 1) * 2);
        List<Mob> out = new ArrayList<>();
        List<String> pool = null;
        List<String> dbz = List.of();
        if (foe == JourneyRules.Foe.DBZ) {
            // a named Dragon Ball character (two on Insane and Xeno) leading their henchmen, by difficulty
            dbz = new ArrayList<>();
            List<String> named = real(DBZ_NAMED[diff]), minions = real(DBZ_MINIONS[diff]);
            for (int i = 0; i < (diff >= 3 ? 2 : 1) && !named.isEmpty(); i++) dbz.add(named.get(rnd.nextInt(named.size())));
            for (int i = 0; i < Math.max(1, count / 2) && !minions.isEmpty(); i++) dbz.add(minions.get(rnd.nextInt(minions.size())));
            if (dbz.isEmpty()) foe = JourneyRules.Foe.MIXED;
            else count = dbz.size();
        }
        for (int i = 0; i < count; i++) {
            JourneyRules.Foe f = foe == JourneyRules.Foe.MIXED && rnd.nextInt(3) == 0 ? JourneyRules.Foe.values()[1 + rnd.nextInt(4)] : foe;
            Mob mob = null;
            double a = rnd.nextDouble() * Math.PI * 2, d = 8 + rnd.nextDouble() * (ARENA_R - 10);
            BlockPos at = c.offset((int) (Math.cos(a) * d), 1, (int) (Math.sin(a) * d));
            switch (f) {
                case GOV -> { var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(l); if (g != null) { g.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0); g.setup(true, 1, c); } mob = g; }
                case SR -> { var s = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(l); if (s != null) { s.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0); s.setup(rnd.nextInt(2), c); } mob = s; }
                case SKY_HATERS -> {
                    var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(l);
                    if (h != null) {
                        h.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0);
                        try { h.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
                        h.makeRaider(i == 0 && diff >= 2);
                    }
                    mob = h;
                }
                case DARK -> mob = create(l, i % 3 == 0 ? "xenowarriors:dark_possessed" : "xenowarriors:corrupted_soul", at);
                case DBZ -> {
                    mob = create(l, dbz.get(i), at);
                    if (mob != null && i < (diff >= 3 ? 2 : 1)) mob.getPersistentData().putBoolean("xw_journey_named", true);
                }
                default -> {
                    if (pool == null) {
                        pool = new ArrayList<>();
                        boolean s3 = com.xenowarriors.character.Seasons.unlocked(l.getServer(), 3);
                        for (String id : Catalog.all(l)) if (Catalog.inTower(id) && (s3 || !id.equals("xenowarriors:order_assassin"))) pool.add(id);
                    }
                    if (!pool.isEmpty()) mob = create(l, pool.get(rnd.nextInt(pool.size())), at);
                }
            }
            if (mob == null) continue;
            prepare(l, mob, diff, step, scale);
            if (!targets.isEmpty()) mob.setTarget(targets.get(rnd.nextInt(targets.size())));
            l.addFreshEntity(mob);
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, .4, .6, .4, .3);
            out.add(mob);
        }
        return out;
    }

    private static Mob create(ServerLevel l, String id, BlockPos at) {
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(id));
        if (t == null) return null;
        Entity e;
        try { e = t.create(l); } catch (Throwable x) { return null; }
        if (!(e instanceof Mob m)) { if (e != null) e.discard(); return null; }
        m.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, l.getRandom().nextFloat() * 360F, 0);
        try { m.finalizeSpawn(l, l.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
        return m;
    }

    // DBZ panels (DragonMineZ's saga characters), by difficulty: who leads, and their henchmen
    private static final String[][] DBZ_NAMED = {
            {"saga_raditz", "saga_nappa", "saga_vegeta", "saga_piccolo", "saga_yamcha", "saga_tien_early", "saga_krillin", "saga_chaoz"},
            {"saga_dodoria", "saga_zarbon", "saga_zarbont1", "saga_guldo", "saga_burter", "saga_jeice", "saga_recoome", "saga_ginyu", "saga_cui", "saga_frieza_first", "saga_vegeta_namek", "saga_a19", "saga_drgero"},
            {"saga_frieza_second", "saga_frieza_third", "saga_frieza_base", "saga_a16", "saga_a17", "saga_a18", "saga_cell_imperfect", "saga_cell_semiperfect", "saga_cooler", "saga_turles", "saga_slug", "saga_garlick_jr_transformed", "saga_mecha_frieza"},
            {"saga_frieza_fp", "saga_cell_perfect", "saga_cell_superperfect", "saga_bojack", "saga_zangya", "saga_broly_base", "saga_broly_ssj", "saga_dabura", "saga_buufat", "saga_metal_cooler", "saga_janemba_fat", "saga_cooler_5ta", "saga_vegeta_majin"},
            {"saga_kidbuu", "saga_superbuu", "saga_superbuu_gotenks", "saga_superbuu_gohan", "saga_evilbuu", "saga_broly_lssj", "saga_super_janemba", "saga_vegetto_ssj", "saga_gotenks_ssj3", "saga_goku_end_ssj3", "saga_super_a13", "saga_bojack_fp"}};
    private static final String[][] DBZ_MINIONS = {
            {"saga_saibaman1", "saga_saibaman2", "saga_saibaman3", "red_ribbon_soldier", "bandit"},
            {"saga_friezasoldier01", "saga_friezasoldier02", "saga_friezasoldier03", "saga_saibaman4", "saga_saibaman5"},
            {"saga_friezasoldier01", "saga_friezasoldier03", "saga_cell_jr", "saga_slug_soldier", "saga_dore", "saga_neiz", "saga_salza"},
            {"saga_cell_jr", "saga_morosoldier", "saga_puipui", "saga_spopovitch", "saga_yakon", "saga_bido", "saga_bujin"},
            {"saga_cell_jr", "saga_morosoldier", "saga_bujin", "saga_bido", "saga_gokua", "saga_paragus", "saga_metal_cooler_core"}};

    private static List<String> real(String[] ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) if (ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation("dragonminez", id))) out.add("dragonminez:" + id);
        return out;
    }

    /** Whether DBZ panels can happen (DragonMineZ is installed with its saga characters). */
    static boolean dbzReady() { return !real(DBZ_NAMED[0]).isEmpty(); }

    /** The final boss eased (Easy) or toughened (Hard and up) by the journey's difficulty. */
    static void bossDifficulty(Mob m, int diff) {
        var mh = m.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(mh.getBaseValue() * JourneyRules.HP_MULT[diff]);
        var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (md != null) md.setBaseValue(md.getBaseValue() * JourneyRules.DMG_MULT[diff]);
        m.setHealth(m.getMaxHealth());
    }

    /** With DragonMineZ: enemies keep up with your DragonMineZ power (bosses too). */
    static void scale(Mob m, float scale) {
        m.getPersistentData().putFloat("xw_journey_dmz", scale);
        if (scale <= 1F) return;
        var mh = m.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null) mh.addPermanentModifier(new AttributeModifier(DMZ, "Journey DragonMineZ", scale - 1F, AttributeModifier.Operation.MULTIPLY_TOTAL));
        var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (md != null) md.addPermanentModifier(new AttributeModifier(DMZ_DMG, "Journey DragonMineZ", Math.sqrt(scale) - 1F, AttributeModifier.Operation.MULTIPLY_TOTAL));   // (health keeps up fully, damage more gently)
        m.setHealth(m.getMaxHealth());
        m.getPersistentData().putFloat("xw_journey_dmz", scale);
    }

    private static final UUID DMZ = UUID.fromString("7c1d4e22-8b4e-4d66-ab62-1e2f3d4c5b03"), DMZ_DMG = UUID.fromString("7c1d4e22-8b4e-4d66-ab62-1e2f3d4c5b04");

    private static void prepare(ServerLevel l, Mob m, int diff, int step, float scale) {
        scale(m, scale);
        m.setPersistenceRequired();
        float hp = JourneyRules.HP_MULT[diff] * (1F + 0.05F * step) - 1F, dmg = JourneyRules.DMG_MULT[diff] * (1F + 0.03F * step) - 1F;
        var mh = m.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null && hp != 0) mh.addPermanentModifier(new AttributeModifier(HP, "Journey", hp, AttributeModifier.Operation.MULTIPLY_TOTAL));
        var md = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (md != null && dmg != 0) md.addPermanentModifier(new AttributeModifier(DMG, "Journey", dmg, AttributeModifier.Operation.MULTIPLY_TOTAL));
        m.removeEffect(MobEffects.INVISIBILITY);                    // (phasing Gov agents: you need to see who you're fighting)
        if (diff >= 3) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, -1, 0, false, false));
        m.setHealth(m.getMaxHealth());
        m.getPersistentData().putBoolean("xw_journey", true);
    }
}

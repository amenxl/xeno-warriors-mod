package com.xenowarriors.npc;

import com.xenowarriors.item.ModItems;
import com.xenowarriors.sky.ModBlocks;
import com.xenowarriors.sky.ModTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Island Skylanders tending their island like townsfolk tend theirs: they dig Sky Ore out of their island (filling the
 * hole back with stone, so the island never gets eaten away), plant trees and flowers, forge Sky Crystal into a sword
 * and armor, and spend crystals on one build of their own - a crystal shrine, a lookout tower, a flower garden, or a
 * little quartz home if the island has no house yet. Only on their own island, in daylight, with mobGriefing on.
 */
public class SkylanderWorkGoal extends Goal {
    private enum Job { NONE, MINE, PLANT, BUILD }
    private static final String[] PROJECTS = {"shrine", "lookout", "garden", "home"};
    private final SkylanderEntity s;
    private Job job = Job.NONE;
    private BlockPos target;
    private int work, cooldown, buildStep;

    public SkylanderWorkGoal(SkylanderEntity s) {
        this.s = s;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean allowed() {
        return s.islandHome() != null && !s.guardian() && !s.flying() && !s.wingless() && s.getTarget() == null
                && s.level().isDay() && s.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0 || !allowed()) return false;
        cooldown = 120 + s.getRandom().nextInt(240);
        forge();
        if (s.project() < 0 && crystals() >= 6) {
            BlockPos spot = findSpot();
            if (spot != null) { s.setProject(pickProject(), spot); buildStep = 0; }
        }
        if (s.project() >= 0 && s.projectAt() != null && crystals() >= 1) { job = Job.BUILD; target = s.projectAt(); return true; }
        if (s.getRandom().nextInt(4) == 0) { target = findPlantSpot(); job = target != null ? Job.PLANT : Job.NONE; if (target != null) return true; }
        target = findOre();
        job = target != null ? Job.MINE : Job.NONE;
        return target != null;
    }

    @Override public boolean canContinueToUse() { return job != Job.NONE && target != null && allowed(); }

    @Override
    public void start() {
        work = 0;
        s.setWorking(true);
        s.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
    }

    @Override
    public void stop() {
        job = Job.NONE;
        target = null;
        s.setWorking(false);
        s.getNavigation().stop();
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) s.level();
        s.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double reach = job == Job.BUILD ? 6 : job == Job.MINE ? 4.5 : 2.5;
        if (s.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) > reach * reach) {
            if (s.getNavigation().isDone()) s.getNavigation().moveTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 0.8);
            if (++work > 500) stop();
            return;
        }
        s.getNavigation().stop();
        switch (job) {
            case MINE -> mine(level);
            case PLANT -> plant(level);
            case BUILD -> build(level);
            default -> stop();
        }
    }

    // ---------------------------------------------------------------- gathering

    private void mine(ServerLevel level) {
        BlockState st = level.getBlockState(target);
        if (!st.is(ModBlocks.SKY_ORE.get())) { stop(); return; }
        if (work++ % 6 == 0) {
            s.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            level.sendParticles(ParticleTypes.END_ROD, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 0.7F, 1.2F);
        }
        if (work >= 60) {
            level.setBlock(target, Blocks.COBBLESTONE.defaultBlockState(), 3);     // filled back in: the island stays whole
            level.playSound(null, target, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 0.8F, 1F);
            s.pack().addItem(new ItemStack(ModItems.SKY_CRYSTAL.get(), 1 + s.getRandom().nextInt(2)));
            stop();
        }
    }

    private void plant(ServerLevel level) {
        if (!level.getBlockState(target).isAir() || !level.getBlockState(target.below()).is(Blocks.GRASS_BLOCK)) { stop(); return; }
        int r = s.getRandom().nextInt(5);
        BlockState put = r == 0 ? Blocks.OAK_SAPLING.defaultBlockState() : r == 1 ? Blocks.BIRCH_SAPLING.defaultBlockState()
                : r == 2 ? Blocks.CORNFLOWER.defaultBlockState() : r == 3 ? Blocks.ALLIUM.defaultBlockState() : Blocks.AZURE_BLUET.defaultBlockState();
        level.setBlock(target, put, 3);
        s.swing(InteractionHand.MAIN_HAND);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
        level.playSound(null, target, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 0.8F, 1F);
        stop();
    }

    /** Sky Ore within a few blocks under the island's grass, inside its radius. */
    private BlockPos findOre() {
        BlockPos home = s.islandHome();
        int r = s.islandRadius();
        for (int i = 0; i < 60; i++) {
            BlockPos p = home.offset(s.getRandom().nextInt(r * 2 + 1) - r, -1 - s.getRandom().nextInt(7), s.getRandom().nextInt(r * 2 + 1) - r);
            if (s.level().getBlockState(p).is(ModBlocks.SKY_ORE.get())) return p;
        }
        return null;
    }

    private BlockPos findPlantSpot() {
        BlockPos home = s.islandHome();
        int r = Math.max(3, s.islandRadius() - 2);
        int around = 0;
        for (int i = 0; i < 30; i++) {
            BlockPos p = topOf(home.offset(s.getRandom().nextInt(r * 2 + 1) - r, 0, s.getRandom().nextInt(r * 2 + 1) - r));
            if (p == null || !s.level().getBlockState(p).isAir() || !s.level().getBlockState(p.below()).is(Blocks.GRASS_BLOCK)) continue;
            // don't turn the whole island into a thicket
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 3, 2)))
                if (s.level().getBlockState(q).is(net.minecraft.tags.BlockTags.SAPLINGS) || s.level().getBlockState(q).is(net.minecraft.tags.BlockTags.LOGS)) around++;
            if (around == 0) return p;
            around = 0;
        }
        return null;
    }

    private BlockPos topOf(BlockPos col) {
        for (int y = 8; y >= -8; y--) {
            BlockPos p = col.offset(0, y, 0);
            if (s.level().getBlockState(p.below()).is(Blocks.GRASS_BLOCK) && (s.level().getBlockState(p).isAir() || s.level().getBlockState(p).canBeReplaced())) return p;
        }
        return null;
    }

    // ---------------------------------------------------------------- forging

    private SimpleContainer pack() { return s.pack(); }

    private int crystals() {
        int n = 0;
        for (int k = 0; k < pack().getContainerSize(); k++) if (pack().getItem(k).is(ModItems.SKY_CRYSTAL.get())) n += pack().getItem(k).getCount();
        return n;
    }

    private boolean spend(int n) {
        if (crystals() < n) return false;
        for (int k = 0; k < pack().getContainerSize() && n > 0; k++) {
            ItemStack st = pack().getItem(k);
            if (!st.is(ModItems.SKY_CRYSTAL.get())) continue;
            int t = Math.min(n, st.getCount());
            st.shrink(t);
            n -= t;
        }
        return true;
    }

    /** Crystal into gear: a sword first, then boots and a helmet (their wings leave no room for a chestplate). */
    private void forge() {
        if (s.getMainHandItem().isEmpty() && spend(2)) equip(EquipmentSlot.MAINHAND, ModTools.SKY_SWORD.get());
        else if (s.getItemBySlot(EquipmentSlot.FEET).isEmpty() && crystals() >= 10 && spend(4)) equip(EquipmentSlot.FEET, ModItems.SKY_BOOTS.get());
        else if (s.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && crystals() >= 11 && spend(5)) equip(EquipmentSlot.HEAD, ModItems.SKY_HELMET.get());
    }

    private void equip(EquipmentSlot slot, Item item) {
        s.setItemSlot(slot, new ItemStack(item));
        s.setDropChance(slot, 0.1F);
        s.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1F, 1.3F);
        if (s.level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.END_ROD, s.getX(), s.getY() + 1, s.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
    }

    // ---------------------------------------------------------------- their build

    private int pickProject() {
        if (!s.hasHouse() && s.getRandom().nextInt(2) == 0) return 3;
        return s.getRandom().nextInt(3);
    }

    /** A flat grassy patch on the island with room above (5x5 for a home, 3x3 for the rest). */
    private BlockPos findSpot() {
        BlockPos home = s.islandHome();
        int r = Math.max(2, s.islandRadius() - 4);
        for (int i = 0; i < 25; i++) {
            BlockPos p = topOf(home.offset(s.getRandom().nextInt(r * 2 + 1) - r, 0, s.getRandom().nextInt(r * 2 + 1) - r));
            if (p != null && clear(p, 2)) return p;
        }
        return null;
    }

    private boolean clear(BlockPos c, int half) {
        for (int dx = -half; dx <= half; dx++) for (int dz = -half; dz <= half; dz++) {
            BlockState g = s.level().getBlockState(c.offset(dx, -1, dz));
            if (!(g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.COARSE_DIRT) || g.is(Blocks.DIRT))) return false;
            for (int y = 0; y <= 5; y++) {
                BlockState b = s.level().getBlockState(c.offset(dx, y, dz));
                if (!b.isAir() && !b.canBeReplaced()) return false;
            }
        }
        return true;
    }

    /** Magic builds: each crystal shapes about six blocks. */
    private void build(ServerLevel level) {
        if (work++ % 7 != 0) return;
        List<Object[]> plan = plan(s.project(), target);
        while (buildStep < plan.size()) {
            Object[] step = plan.get(buildStep++);
            BlockPos p = (BlockPos) step[0];
            BlockState b = (BlockState) step[1];
            BlockState cur = level.getBlockState(p);
            if (cur.equals(b) || (!cur.isAir() && !cur.canBeReplaced() && !b.isAir())) continue;
            if (!b.isAir() && s.bump() % 6 == 0 && !spend(1)) { buildStep--; stop(); return; }     // out of crystal: back to digging
            level.setBlock(p, b, 3);
            s.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.02);
            level.playSound(null, p, b.getSoundType().getPlaceSound(), SoundSource.NEUTRAL, 0.7F, 1.1F);
            return;
        }
        if (s.project() == 3) s.setHasHouse(true);
        s.finishProject();
        level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1F, 1F);
        stop();
    }

    public static List<Object[]> plan(int project, BlockPos c) {
        List<Object[]> out = new ArrayList<>();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState(), quartz = Blocks.SMOOTH_QUARTZ.defaultBlockState(),
                calcite = Blocks.CALCITE.defaultBlockState(), glass = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(),
                slab = Blocks.QUARTZ_SLAB.defaultBlockState(), air = Blocks.AIR.defaultBlockState(),
                ore = ModBlocks.SKY_ORE.get().defaultBlockState(), lantern = Blocks.SOUL_LANTERN.defaultBlockState();
        switch (project) {
            case 0 -> {     // crystal shrine: quartz ring, four short pillars, Sky Ore on a plinth with a lantern
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(new Object[]{c.offset(dx, -1, dz), quartz});
                for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                    out.add(new Object[]{c.offset(o[0], 0, o[1]), pillar});
                    out.add(new Object[]{c.offset(o[0], 1, o[1]), Blocks.QUARTZ_STAIRS.defaultBlockState()});
                }
                out.add(new Object[]{c, calcite});
                out.add(new Object[]{c.above(), ore});
                out.add(new Object[]{c.above(2), lantern});
            }
            case 1 -> {     // lookout: a five-high quartz tower with a slab crown and a lantern on top
                for (int y = 0; y < 5; y++) out.add(new Object[]{c.above(y), pillar});
                for (Direction d : Direction.Plane.HORIZONTAL) out.add(new Object[]{c.above(5).relative(d), slab});
                out.add(new Object[]{c.above(5), quartz});
                out.add(new Object[]{c.above(6), Blocks.LANTERN.defaultBlockState()});
                for (Direction d : Direction.Plane.HORIZONTAL) out.add(new Object[]{c.relative(d), Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(
                        net.minecraft.world.level.block.StairBlock.FACING, d.getOpposite())});
            }
            case 2 -> {     // garden: a calcite-edged flower bed round a flowering azalea
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (edge) out.add(new Object[]{c.offset(dx, 0, dz), Blocks.CALCITE.defaultBlockState()});
                }
                BlockState[] flowers = {Blocks.CORNFLOWER.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState()};
                int k = 0;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) out.add(new Object[]{c.offset(dx, 0, dz), flowers[k++ % 4]});
                out.add(new Object[]{c, Blocks.FLOWERING_AZALEA.defaultBlockState()});
            }
            default -> {    // a small quartz home: calcite walls, quartz corners, sky-glass roof, a lantern inside
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.add(new Object[]{c.offset(dx, -1, dz), quartz});
                for (int y = 0; y <= 2; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2, door = dz == 2 && dx == 0 && y < 2,
                            window = y == 1 && !corner && (dx == 0 || dz == 0);
                    out.add(new Object[]{c.offset(dx, y, dz), door ? air : corner ? pillar : window ? glass : calcite});
                }
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    out.add(new Object[]{c.offset(dx, 3, dz), Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? glass : quartz});
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(new Object[]{c.offset(dx, 4, dz), slab});
                out.add(new Object[]{c.offset(0, 2, 0), lantern.setValue(LanternBlock.HANGING, true)});
                out.add(new Object[]{c.offset(-1, 0, -1), Blocks.CRAFTING_TABLE.defaultBlockState()});
            }
        }
        return out;
    }
}

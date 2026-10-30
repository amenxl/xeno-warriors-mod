package com.xenowarriors.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Townsfolk living their lives like players do: in the daytime they chop trees, dig out stone and ore, work what they
 * gather into planks, tools, weapons and iron armor (and wear them), and once they have enough material they build
 * themselves a small hut on open ground. Only natural trees (logs with leaves), raw stone and ores are taken, huts go
 * only on clear natural ground, and nothing happens with mobGriefing off - so players' builds are left alone.
 * Once they have a home they pull up grass for seeds and lay out a fenced wheat farm around a water hole, then tend it
 * (harvest ripe wheat, replant, bake bread and eat it when hurt), and add a well and a lamp post by their hut.
 */
public class HumanWorkGoal extends Goal {
    private enum Job { NONE, CHOP, MINE, BUILD, GRASS, TEND }
    private static final String FARM = "xw_farm", EXTRAS = "xw_extras";
    private java.util.List<Object[]> plan;
    private java.util.function.Consumer<BlockPos> onBuilt;
    private final HumanNpcEntity h;
    private Job job = Job.NONE;
    private BlockPos target;
    private int work, cooldown, buildStep;

    public HumanWorkGoal(HumanNpcEntity h) {
        this.h = h;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean allowed() {
        return !h.carriesSoul() && !h.raider() && h.getTarget() == null && h.reaction() == 0
                && h.level().isDay() && h.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0 || !allowed()) return false;
        cooldown = 100 + h.getRandom().nextInt(200);
        craft();
        eat();
        if (h.home() == null && count(Items.OAK_PLANKS) + count(Items.COBBLESTONE) >= 40) {
            BlockPos spot = findHutSpot();
            if (spot != null) { startBuild(spot, hutPlan(spot), c -> h.setHome(c)); return true; }
        }
        if (h.home() != null && farm() != null && h.getRandom().nextInt(3) > 0) {           // tend the farm
            BlockPos crop = findCropWork();
            if (crop != null) { job = Job.TEND; target = crop; return true; }
        }
        if (h.home() != null && farm() == null) {
            if (count(Items.WHEAT_SEEDS) < 6) {                                               // seeds first
                target = findGrass();
                if (target != null) { job = Job.GRASS; return true; }
            } else if (count(Items.OAK_PLANKS) >= 24) {
                BlockPos spot = findSpotNear(h.home(), 3);
                if (spot != null) { startBuild(spot, farmPlan(spot), c -> h.getPersistentData().putLong(FARM, c.asLong())); return true; }
            }
        }
        int extras = h.getPersistentData().getInt(EXTRAS);
        if (h.home() != null && farm() != null && extras < 2 && count(Items.COBBLESTONE) >= 12 && count(Items.OAK_PLANKS) >= 6) {
            BlockPos spot = findSpotNear(h.home(), 1);
            if (spot != null) { startBuild(spot, extras == 0 ? wellPlan(spot) : lampPlan(spot), c -> h.getPersistentData().putInt(EXTRAS, extras + 1)); return true; }
        }
        boolean wantStone = count(Items.COBBLESTONE) < 30 || needIron();
        target = h.getRandom().nextBoolean() || !wantStone ? findLog() : null;
        job = target != null ? Job.CHOP : Job.NONE;
        if (target == null && wantStone) { target = findStone(); job = target != null ? Job.MINE : Job.NONE; }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return job != Job.NONE && target != null && allowed();
    }

    @Override
    public void start() {
        work = 0;
        h.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
    }

    @Override
    public void stop() {
        job = Job.NONE;
        target = null;
        h.getNavigation().stop();
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) h.level();
        double d = h.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        h.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (job == Job.BUILD) { build(level); return; }
        if (job == Job.TEND) { tend(level, d); return; }
        if (d > 3.2 * 3.2) {
            if (h.getNavigation().isDone()) h.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
            if (++work > 400) stop();
            return;
        }
        h.getNavigation().stop();
        BlockState s = level.getBlockState(target);
        if ((job == Job.CHOP && !s.is(BlockTags.LOGS)) || (job == Job.MINE && !minable(s)) || (job == Job.GRASS && !grassy(s))) { stop(); return; }
        if (job == Job.GRASS) {                       // pull up grass: sometimes a seed
            com.xenowarriors.anim.Poses.play(h, com.xenowarriors.anim.Poses.PLACE, 8);
            if (com.xenowarriors.modes.ModeRules.canGrief(level)) level.destroyBlock(target, false, h);
            if (h.getRandom().nextFloat() < 0.6F) pack().addItem(new ItemStack(Items.WHEAT_SEEDS, 1 + h.getRandom().nextInt(2)));
            stop();
            return;
        }
        if (work++ % 10 == 0) {
            com.xenowarriors.anim.Poses.play(h, job == Job.CHOP ? com.xenowarriors.anim.Poses.CHOP : com.xenowarriors.anim.Poses.MINE, 10);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, target, s.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.6F, 0.9F);
        }
        if (work >= (job == Job.CHOP ? 50 : 70)) {
            Item got = s.is(BlockTags.LOGS) ? Items.OAK_LOG : s.is(Blocks.IRON_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE) ? Items.RAW_IRON
                    : s.is(Blocks.COAL_ORE) ? Items.COAL : Items.COBBLESTONE;
            if (com.xenowarriors.modes.ModeRules.canGrief(level)) level.destroyBlock(target, false, h);
            pack().addItem(new ItemStack(got, got == Items.COBBLESTONE ? 1 : 1));
            // keep at it on the same tree
            if (job == Job.CHOP && level.getBlockState(target.above()).is(BlockTags.LOGS)) { target = target.above(); work = 0; return; }
            stop();
        }
    }

    // ---------------------------------------------------------------- what they take

    private SimpleContainer pack() { return h.pack(); }

    private int count(Item i) {
        int n = 0;
        for (int k = 0; k < pack().getContainerSize(); k++) if (pack().getItem(k).is(i)) n += pack().getItem(k).getCount();
        return n;
    }

    private void take(Item i, int n) {
        for (int k = 0; k < pack().getContainerSize() && n > 0; k++) {
            ItemStack s = pack().getItem(k);
            if (!s.is(i)) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t);
            n -= t;
        }
    }

    private boolean needIron() {
        return h.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || h.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
    }

    private static boolean minable(BlockState s) {
        return s.is(Blocks.STONE) || s.is(Blocks.IRON_ORE) || s.is(Blocks.COAL_ORE) || s.is(Blocks.DEEPSLATE_IRON_ORE);
    }

    /** A log that's part of a natural tree (leaves next to it). */
    private BlockPos findLog() {
        BlockPos base = h.blockPosition();
        for (int i = 0; i < 40; i++) {
            BlockPos p = base.offset(h.getRandom().nextInt(25) - 12, h.getRandom().nextInt(7) - 2, h.getRandom().nextInt(25) - 12);
            if (!h.level().getBlockState(p).is(BlockTags.LOGS)) continue;
            for (Direction dir : Direction.values())
                if (h.level().getBlockState(p.relative(dir)).is(BlockTags.LEAVES) || h.level().getBlockState(p.above(2)).is(BlockTags.LEAVES)) {
                    BlockPos low = p;
                    while (h.level().getBlockState(low.below()).is(BlockTags.LOGS)) low = low.below();
                    return low;
                }
        }
        return null;
    }

    /** Raw stone or ore showing at the surface (open to the air). */
    private BlockPos findStone() {
        BlockPos base = h.blockPosition();
        for (int i = 0; i < 50; i++) {
            BlockPos p = base.offset(h.getRandom().nextInt(21) - 10, h.getRandom().nextInt(7) - 4, h.getRandom().nextInt(21) - 10);
            if (minable(h.level().getBlockState(p)) && h.level().getBlockState(p.above()).isAir()) return p;
        }
        return null;
    }

    /** Turns what they've gathered into things they use: planks, then a weapon and tools, then iron armor. */
    private void craft() {
        int logs = count(Items.OAK_LOG);
        if (logs > 0) { take(Items.OAK_LOG, logs); pack().addItem(new ItemStack(Items.OAK_PLANKS, logs * 4)); }
        int raw = count(Items.RAW_IRON), coal = count(Items.COAL);
        if (raw > 0 && coal > 0) { int n = Math.min(raw, coal * 8); take(Items.RAW_IRON, n); take(Items.COAL, (n + 7) / 8); pack().addItem(new ItemStack(Items.IRON_INGOT, n)); }
        ItemStack hand = h.getMainHandItem();
        if (hand.isEmpty() || hand.is(Items.WOODEN_SWORD)) {
            if (count(Items.COBBLESTONE) >= 2 && count(Items.OAK_PLANKS) >= 1) { take(Items.COBBLESTONE, 2); take(Items.OAK_PLANKS, 1); equip(EquipmentSlot.MAINHAND, Items.STONE_SWORD); }
            else if (hand.isEmpty() && count(Items.OAK_PLANKS) >= 3) { take(Items.OAK_PLANKS, 3); equip(EquipmentSlot.MAINHAND, Items.WOODEN_SWORD); }
        }
        int iron = count(Items.IRON_INGOT);
        if (h.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && iron >= 5) { take(Items.IRON_INGOT, 5); equip(EquipmentSlot.HEAD, Items.IRON_HELMET); iron -= 5; }
        if (h.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && iron >= 8) { take(Items.IRON_INGOT, 8); equip(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE); }
    }

    /** Bread from wheat; eaten when they're hurt. */
    private void eat() {
        int wheat = count(Items.WHEAT);
        if (wheat >= 3) { take(Items.WHEAT, wheat / 3 * 3); pack().addItem(new ItemStack(Items.BREAD, wheat / 3)); }
        if (h.getHealth() < h.getMaxHealth() - 4 && count(Items.BREAD) > 0) {
            take(Items.BREAD, 1);
            h.heal(5F);
            h.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.8F, 1F);
        }
    }

    // ---------------------------------------------------------------- the farm

    private BlockPos farm() {
        return h.getPersistentData().contains(FARM) ? BlockPos.of(h.getPersistentData().getLong(FARM)) : null;
    }

    private static boolean grassy(BlockState s) { return s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN); }

    private BlockPos findGrass() {
        BlockPos base = h.blockPosition();
        for (int i = 0; i < 50; i++) {
            BlockPos p = base.offset(h.getRandom().nextInt(25) - 12, h.getRandom().nextInt(5) - 2, h.getRandom().nextInt(25) - 12);
            if (grassy(h.level().getBlockState(p))) return p;
        }
        return null;
    }

    /** A ripe crop to harvest, or empty farmland to sow, in their farm. */
    private BlockPos findCropWork() {
        BlockPos f = farm();
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos p = f.offset(dx, 0, dz);
            BlockState s = h.level().getBlockState(p);
            if (s.getBlock() instanceof net.minecraft.world.level.block.CropBlock c && c.isMaxAge(s)) out.add(p);
            else if (s.isAir() && h.level().getBlockState(p.below()).is(Blocks.FARMLAND) && count(Items.WHEAT_SEEDS) > 0) out.add(p);
        }
        return out.isEmpty() ? null : out.get(h.getRandom().nextInt(out.size()));
    }

    private void tend(ServerLevel level, double d) {
        if (d > 2.6 * 2.6) {
            if (h.getNavigation().isDone()) h.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.7);
            if (++work > 300) stop();
            return;
        }
        BlockState s = level.getBlockState(target);
        com.xenowarriors.anim.Poses.play(h, com.xenowarriors.anim.Poses.PLACE, 8);
        if (s.getBlock() instanceof net.minecraft.world.level.block.CropBlock c && c.isMaxAge(s)) {
            if (com.xenowarriors.modes.ModeRules.canGrief(level)) level.destroyBlock(target, false, h);
            pack().addItem(new ItemStack(Items.WHEAT, 1));
            pack().addItem(new ItemStack(Items.WHEAT_SEEDS, 1 + h.getRandom().nextInt(2)));
        }
        if (level.getBlockState(target).isAir() && level.getBlockState(target.below()).is(Blocks.FARMLAND) && count(Items.WHEAT_SEEDS) > 0) {
            take(Items.WHEAT_SEEDS, 1);
            level.setBlock(target, Blocks.WHEAT.defaultBlockState(), 3);
            level.playSound(null, target, net.minecraft.sounds.SoundEvents.CROP_PLANTED, SoundSource.NEUTRAL, 0.8F, 1F);
        }
        stop();
    }

    /** Clear natural ground near their home, `r` blocks out from the centre (r=3 gives a 7x7 patch). */
    private BlockPos findSpotNear(BlockPos home, int r) {
        for (int i = 0; i < 30; i++) {
            double a = h.getRandom().nextDouble() * Math.PI * 2, dist = 6 + r + h.getRandom().nextInt(6);
            BlockPos c = h.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    home.offset((int) (Math.cos(a) * dist), 0, (int) (Math.sin(a) * dist)));
            if (clearN(c, r)) return c;
        }
        return null;
    }

    private boolean clearN(BlockPos c, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            BlockState gs = h.level().getBlockState(c.offset(dx, -1, dz));
            if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT) || gs.is(Blocks.COARSE_DIRT) || gs.is(Blocks.PODZOL))) return false;
            for (int y = 0; y <= 3; y++) {
                BlockState s = h.level().getBlockState(c.offset(dx, y, dz));
                if (!s.isAir() && !s.canBeReplaced()) return false;
            }
        }
        return true;
    }

    private void equip(EquipmentSlot slot, Item item) {
        h.setItemSlot(slot, new ItemStack(item));
        h.setDropChance(slot, 0.15F);
        h.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 0.7F, 1F);
    }

    // ---------------------------------------------------------------- their hut

    /** Open, flat, natural ground (grass/dirt/sand) with nothing standing on it - 7x7 with room above. */
    private BlockPos findHutSpot() {
        BlockPos base = h.blockPosition();
        for (int i = 0; i < 20; i++) {
            BlockPos c = h.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    base.offset(h.getRandom().nextInt(33) - 16, 0, h.getRandom().nextInt(33) - 16));
            if (clear(c)) return c;
        }
        return null;
    }

    private boolean clear(BlockPos c) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos g = c.offset(dx, -1, dz);
            BlockState gs = h.level().getBlockState(g);
            if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT) || gs.is(Blocks.SAND) || gs.is(Blocks.COARSE_DIRT) || gs.is(Blocks.PODZOL))) return false;
            for (int y = 0; y <= 4; y++) {
                BlockState s = h.level().getBlockState(c.offset(dx, y, dz));
                if (!s.isAir() && !s.canBeReplaced()) return false;
            }
        }
        return true;
    }

    /** One block at a time: a cobblestone floor and walls, a doorway, windows, a plank roof, a torch inside. */
    private void build(ServerLevel level) {
        if (h.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > 6 * 6) {
            if (h.getNavigation().isDone()) h.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8);
            if (++work > 600) stop();
            return;
        }
        if (work++ % 8 != 0) return;
        if (plan == null) { stop(); return; }
        while (buildStep < plan.size()) {
            Object[] step = plan.get(buildStep++);
            BlockPos p = (BlockPos) step[0];
            BlockState s = (BlockState) step[1];
            BlockState cur = level.getBlockState(p);
            // (natural ground may be dug into: the farmland, water holes and floors go one block down)
            boolean ground = cur.is(Blocks.GRASS_BLOCK) || cur.is(Blocks.DIRT) || cur.is(Blocks.COARSE_DIRT) || cur.is(Blocks.PODZOL);
            if (cur.equals(s) || (!cur.isAir() && !cur.canBeReplaced() && !s.isAir() && !ground)) continue;
            Item cost = s.is(Blocks.COBBLESTONE) || s.is(Blocks.COBBLESTONE_WALL) || s.is(Blocks.COBBLESTONE_SLAB) ? Items.COBBLESTONE
                    : s.is(Blocks.OAK_PLANKS) || s.is(Blocks.OAK_SLAB) || s.is(Blocks.OAK_FENCE) || s.is(Blocks.OAK_FENCE_GATE) ? Items.OAK_PLANKS
                    : s.is(Blocks.WHEAT) ? Items.WHEAT_SEEDS : null;
            if (cost == Items.WHEAT_SEEDS && count(cost) < 1) continue;                 // sow what they can
            if (cost != null && count(cost) < 1) {
                if (count(Items.OAK_PLANKS) > 0 && cost == Items.COBBLESTONE) { s = Blocks.OAK_PLANKS.defaultBlockState(); cost = Items.OAK_PLANKS; }
                else { stop(); return; }                     // out of material: gather more and come back
            }
            if (cost != null) take(cost, 1);
            level.setBlock(p, s, 3);
            com.xenowarriors.anim.Poses.play(h, com.xenowarriors.anim.Poses.PLACE, 8);
            level.playSound(null, p, s.getSoundType().getPlaceSound(), SoundSource.NEUTRAL, 0.7F, 1F);
            return;
        }
        if (onBuilt != null) onBuilt.accept(target);
        com.xenowarriors.anim.Poses.play(h, com.xenowarriors.anim.Poses.WIPE, 40);           // done - phew
        stop();
    }

    private void startBuild(BlockPos spot, java.util.List<Object[]> plan, java.util.function.Consumer<BlockPos> done) {
        job = Job.BUILD; target = spot; buildStep = 0; this.plan = plan; onBuilt = done;
    }

    /** A 5x5 wheat field around a water hole, fenced, with a gate. */
    static java.util.List<Object[]> farmPlan(BlockPos c) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            out.add(new Object[]{c.offset(dx, -1, dz), dx == 0 && dz == 0 ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState()});
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) != 3 && Math.abs(dz) != 3) continue;
            out.add(new Object[]{c.offset(dx, 0, dz), dx == 0 && dz == 3 ? Blocks.OAK_FENCE_GATE.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState()});
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if (dx != 0 || dz != 0) out.add(new Object[]{c.offset(dx, 0, dz), Blocks.WHEAT.defaultBlockState()});
        out.add(new Object[]{c.offset(-3, 1, -3), Blocks.TORCH.defaultBlockState()});
        out.add(new Object[]{c.offset(3, 1, 3), Blocks.TORCH.defaultBlockState()});
        return out;
    }

    /** A little stone well with a roof. */
    static java.util.List<Object[]> wellPlan(BlockPos c) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        out.add(new Object[]{c.offset(0, -2, 0), Blocks.COBBLESTONE.defaultBlockState()});
        out.add(new Object[]{c.offset(0, -1, 0), Blocks.WATER.defaultBlockState()});
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) {
            out.add(new Object[]{c.offset(dx, -1, dz), Blocks.COBBLESTONE.defaultBlockState()});
            out.add(new Object[]{c.offset(dx, 0, dz), Blocks.COBBLESTONE_WALL.defaultBlockState()});
        }
        for (int[] q : new int[][]{{-1, -1}, {1, 1}}) { out.add(new Object[]{c.offset(q[0], 1, q[1]), Blocks.OAK_FENCE.defaultBlockState()}); out.add(new Object[]{c.offset(q[0], 2, q[1]), Blocks.OAK_FENCE.defaultBlockState()}); }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(new Object[]{c.offset(dx, 3, dz), Blocks.OAK_SLAB.defaultBlockState()});
        return out;
    }

    /** A lamp post: a cobble base, a fence pole and a lantern. */
    static java.util.List<Object[]> lampPlan(BlockPos c) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        out.add(new Object[]{c, Blocks.COBBLESTONE_WALL.defaultBlockState()});
        for (int y = 1; y <= 3; y++) out.add(new Object[]{c.offset(0, y, 0), Blocks.OAK_FENCE.defaultBlockState()});
        out.add(new Object[]{c.offset(0, 4, 0), Blocks.LANTERN.defaultBlockState()});
        return out;
    }

    /** (UI test) put one of the plans down at once. */
    public static void testPlace(ServerLevel l, BlockPos c, String which) {
        java.util.List<Object[]> p = switch (which) { case "hut" -> hutPlan(c); case "farm" -> farmPlan(c); case "well" -> wellPlan(c); default -> lampPlan(c); };
        for (Object[] st : p) l.setBlock((BlockPos) st[0], (BlockState) st[1], 3);
        if (which.equals("farm")) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            if ((dx != 0 || dz != 0) && (dx + dz) % 2 == 0) l.setBlock(c.offset(dx, 0, dz), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
    }

    static java.util.List<Object[]> hutPlan(BlockPos c) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        BlockState cob = Blocks.COBBLESTONE.defaultBlockState(), pl = Blocks.OAK_PLANKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) out.add(new Object[]{c.offset(dx, -1, dz), cob});
        for (int y = 0; y <= 2; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            if (!edge) continue;
            boolean door = dz == -2 && dx == 0 && y < 2, window = y == 1 && (dx == 0 || dz == 0) && !door;
            out.add(new Object[]{c.offset(dx, y, dz), door ? air : window ? Blocks.GLASS_PANE.defaultBlockState() : (Math.abs(dx) == 2 && Math.abs(dz) == 2 ? pl : cob)});
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) out.add(new Object[]{c.offset(dx, 3, dz), Blocks.OAK_SLAB.defaultBlockState()});
        out.add(new Object[]{c.offset(1, 0, 1), Blocks.CRAFTING_TABLE.defaultBlockState()});
        out.add(new Object[]{c.offset(-1, 2, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.NORTH)});
        return out;
    }
}

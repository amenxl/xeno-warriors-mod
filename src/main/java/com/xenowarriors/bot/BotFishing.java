package com.xenowarriors.bot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Quiet times by the water: now and then, when you're near water and nothing's going on, a bot goes fishing - it makes
 * itself a fishing rod first if it has none (sticks and string in its bag), walks to the water's edge, casts, and reels
 * in a few catches (the game's own fishing loot) into its bag. Anything happening - a fight, you walking off - and it
 * packs up.
 */
public class BotFishing extends Goal {
    /** How often (ticks) it thinks about fishing, and the chance then. */
    public static final int LOOK_EVERY = 20 * 45;
    public static final float CHANCE = 0.35F;
    public static final double WATER_NEAR_OWNER = 12, OWNER_RANGE = 16;
    /** (tests) */
    public static int rodsMade, catches, trips;
    public static boolean forceForTest;

    private final PartyBotEntity b;
    private int nextLook, t, biteAt, caught, want;
    private BlockPos water, stand;
    private ItemStack before = ItemStack.EMPTY;

    public BotFishing(PartyBotEntity b) {
        this.b = b;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        nextLook = 200 + b.getRandom().nextInt(LOOK_EVERY);
    }

    private ServerPlayer owner() { return b.owner(); }

    private boolean quiet() {
        ServerPlayer o = owner();
        return o != null && o.level() == b.level() && o.distanceTo(b) < OWNER_RANGE * 1.5 && BotErrands.danger(b) == null && !b.isPassenger()
                && b.mode() != PartyBotEntity.MINING && !b.journey() && !com.xenowarriors.party.Duels.isDowned(o) && o.getLastHurtByMob() == null;
    }

    private static boolean rod(ItemStack s) { return s.is(Items.FISHING_ROD); }

    private int rodSlot() {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (rod(b.inventory.getItem(i))) return i;
        return -1;
    }

    /** Water near you that it can stand next to: the water block and the dry spot beside it. */
    private boolean findWater() {
        ServerPlayer o = owner();
        BlockPos c = o.blockPosition();
        ServerLevel l = (ServerLevel) b.level();
        double best = Double.MAX_VALUE;
        water = null; stand = null;
        int r = (int) WATER_NEAR_OWNER;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -3; dy <= 2; dy++) {
            BlockPos w = c.offset(dx, dy, dz);
            if (!l.getFluidState(w).is(FluidTags.WATER) || !l.getBlockState(w.above()).isAir()) continue;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos s = w.relative(d).above();
                if (!l.getBlockState(s).isAir() || !l.getBlockState(s.above()).isAir() || !l.getBlockState(s.below()).isFaceSturdy(l, s.below(), net.minecraft.core.Direction.UP)) continue;
                double dist = s.distSqr(b.blockPosition()) + s.distSqr(c) * 0.5;
                if (dist < best) { best = dist; water = w; stand = s; }
            }
        }
        return water != null;
    }

    @Override public boolean canUse() {
        if (b.tickCount < nextLook && !forceForTest) return false;
        nextLook = b.tickCount + LOOK_EVERY;
        if (!quiet() || (!forceForTest && b.getRandom().nextFloat() >= CHANCE)) return false;
        if (!findWater()) return false;
        if (rodSlot() < 0 && !rod(b.getMainHandItem())) {                    // no rod: make one (3 sticks and 2 string)
            ItemStack made = BotCrafting.craftRecipe(b, new ResourceLocation("minecraft", "fishing_rod"));
            if (made == null || made.isEmpty()) return false;
            rodsMade++;
            ServerPlayer o = owner();
            if (o != null) o.displayClientMessage(Component.empty().append(b.label()).append(" made a fishing rod.").withStyle(ChatFormatting.AQUA), false);
        }
        forceForTest = false;
        return true;
    }

    @Override public boolean canContinueToUse() { return water != null && quiet() && caught < want && t < 20 * 90; }

    @Override public void start() {
        t = 0; caught = 0; want = 1 + b.getRandom().nextInt(3); biteAt = -1;
        trips++;
        before = b.getMainHandItem().copy();
        int s = rodSlot();
        if (!rod(b.getMainHandItem()) && s >= 0) {                               // the rod in hand (what it held goes back in the bag)
            ItemStack r = b.inventory.getItem(s);
            b.inventory.setItem(s, b.getMainHandItem().copy());
            b.setItemSlot(EquipmentSlot.MAINHAND, r);
        }
        PartyBots.testLog(b, "goes fishing at " + water.toShortString());
    }

    @Override public void stop() {
        if (rod(b.getMainHandItem()) && !rod(before)) {                         // the rod back in the bag, its own thing back in hand
            ItemStack r = b.getMainHandItem().copy();
            int s = -1;
            for (int i = 0; i < b.inventory.getContainerSize(); i++) if (ItemStack.isSameItemSameTags(b.inventory.getItem(i), before) && !before.isEmpty()) { s = i; break; }
            if (s >= 0) { b.setItemSlot(EquipmentSlot.MAINHAND, b.inventory.getItem(s)); b.inventory.setItem(s, r); }
            else { b.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); ItemStack left = b.inventory.addItem(r); if (!left.isEmpty()) b.spawnAtLocation(left); }
        }
        water = null; stand = null;
        b.getNavigation().stop();
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        t++;
        ServerLevel l = (ServerLevel) b.level();
        Vec3 bob = Vec3.atCenterOf(water).add(0, 0.45, 0);
        b.getLookControl().setLookAt(bob.x, bob.y, bob.z);
        if (b.distanceToSqr(Vec3.atBottomCenterOf(stand)) > 1.2 * 1.2) {          // to the water's edge
            if (b.getNavigation().isDone() || t % 20 == 0) b.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0);
            if (t > 20 * 20) water = null;                                     // (can't get there)
            return;
        }
        b.getNavigation().stop();
        if (biteAt < 0) {                                                      // cast
            b.swing(InteractionHand.MAIN_HAND);
            l.playSound(null, b.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (b.getRandom().nextFloat() * 0.4F + 0.8F));
            l.sendParticles(ParticleTypes.SPLASH, bob.x, bob.y, bob.z, 6, 0.1, 0, 0.1, 0);
            biteAt = t + 100 + b.getRandom().nextInt(300);
            return;
        }
        if (t % 10 == 0) l.sendParticles(ParticleTypes.BUBBLE, bob.x, bob.y - 0.2, bob.z, 1, 0.05, 0, 0.05, 0);   // the bobber
        if (t >= biteAt - 20 && t < biteAt && t % 2 == 0) l.sendParticles(ParticleTypes.FISHING, bob.x + (b.getRandom().nextDouble() - 0.5), bob.y, bob.z + (b.getRandom().nextDouble() - 0.5), 1, 0, 0, 0, 0);
        if (t < biteAt) return;
        // a bite: reel it in
        b.swing(InteractionHand.MAIN_HAND);
        l.playSound(null, bob.x, bob.y, bob.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.25F, 1F);
        l.sendParticles(ParticleTypes.SPLASH, bob.x, bob.y, bob.z, 12, 0.2, 0, 0.2, 0);
        ItemStack r = b.getMainHandItem();
        LootParams p = new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN, bob).withParameter(LootContextParams.TOOL, r)
                .withParameter(LootContextParams.THIS_ENTITY, PartyBots.standIn(b)).withLuck(0).create(LootContextParamSets.FISHING);
        for (ItemStack got : l.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING).getRandomItems(p)) {
            ItemStack left = b.inventory.addItem(got);
            if (!left.isEmpty()) b.spawnAtLocation(left);
            ServerPlayer o = owner();
            if (o != null && caught == 0) o.displayClientMessage(Component.empty().append(b.label()).append(" caught ").append(got.getHoverName()).append("!").withStyle(ChatFormatting.AQUA), true);
        }
        l.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(l, b.getX(), b.getY() + 0.5, b.getZ(), 1 + b.getRandom().nextInt(6)));
        if (r.isDamageableItem()) r.hurtAndBreak(1, b, x -> {});
        catches++; caught++;
        biteAt = -1;
    }
}

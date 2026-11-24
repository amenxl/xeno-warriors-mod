package com.xenowarriors.bot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Bots and boats. Your boat's full: a bot gets into another boat nearby that has room (its own team's, or an empty
 * one) and paddles after you. And once your Crafting is level {@value #LEVEL} (bots share your Crafting), a bot that
 * has to cross water makes a boat (5 planks of a wood - logs are cut into planks first) or takes one from its bag,
 * puts it in the water and paddles across - instead of laying a block path (BotWaterBridge) - then gets out at the
 * far side and puts the boat back in its bag.
 */
public final class BotBoats {
    private BotBoats() {}

    public static final int LEVEL = 20;
    /** On a boat: the bot that steers it; the bot it belongs to (it packs it back up). */
    static final String DRIVER = "xw_bot_boat_driver", OWN = "xw_bot_boat_own";
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};
    /** (Tests) boats made, put in the water, boarded (someone else's), packed back up; ticks paddled. */
    public static int crafted, launched, boarded, packed, paddled;
    /** (Tests) why it last got out. */
    public static String why = "";

    static int seats(Boat b) { return b instanceof ChestBoat ? 1 : 2; }

    /** Its owner's Crafting is high enough for boats. */
    public static boolean unlocked(PartyBotEntity b) {
        ServerPlayer o = b.owner();
        return o != null && com.xenowarriors.stats.Crafting.craftLevel(o) >= LEVEL;
    }

    private static PartyBotEntity driverOf(Boat bt) {
        if (!bt.getPersistentData().hasUUID(DRIVER) || !(bt.getFirstPassenger() instanceof PartyBotEntity b)) return null;
        return b.getUUID().equals(bt.getPersistentData().getUUID(DRIVER)) ? b : null;
    }

    /** Paddling a boat it steers itself (not one you're in). */
    public static boolean ownBoat(PartyBotEntity b) { return b.getVehicle() instanceof Boat bt && driverOf(bt) == b; }

    // ------------------------------------------------------------------ getting a boat

    /** Your boat has no room for it: another boat with room nearby, or (unlocked) one of its own. True if it's seen to. */
    static boolean overflow(PartyBotEntity b, ServerPlayer o) {
        if (!(b.level() instanceof ServerLevel l) || b.isPassenger()) return false;
        Entity ov = o.getVehicle();
        for (Boat bt : l.getEntitiesOfClass(Boat.class, b.getBoundingBox().inflate(16), x -> x.isAlive() && x != ov && x.getPassengers().size() < seats(x)
                && x.getPassengers().stream().allMatch(e -> e instanceof PartyBotEntity pb && o.getUUID().equals(pb.ownerId())))) {
            if (b.distanceToSqr(bt) > 3 * 3) { b.getNavigation().moveTo(bt, 1.3); return true; }
            if (b.startRiding(bt, false)) {
                if (bt.getFirstPassenger() == b) bt.getPersistentData().putUUID(DRIVER, b.getUUID());
                boarded++;
                return true;
            }
        }
        return unlocked(b) && launch(b, l);
    }

    /** Crossing water on its own (BotWaterBridge): with boats unlocked, a boat instead of a block path. True if it's in one now. */
    static boolean cross(PartyBotEntity b, ServerLevel l) {
        return unlocked(b) && !b.isPassenger() && launch(b, l);
    }

    /** A boat from its bag (or made now) into the water by it, and in it goes. */
    private static boolean launch(PartyBotEntity b, ServerLevel l) {
        ServerPlayer o = b.owner();
        if (o != null && o.level() == l && o.getVehicle() == null && !o.isInWater() && o.onGround() && b.distanceToSqr(o) < 8 * 8) return false;   // (you're right here on land: no boat - it'd only get straight back out)
        BlockPos at = water(l, b);
        if (at == null) return false;
        int slot = boatSlot(b);
        if (slot < 0) {
            if (!make(b)) return false;
            slot = boatSlot(b);
            if (slot < 0) return false;
        }
        ItemStack it = b.inventory.getItem(slot);
        String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it.getItem()).getPath();
        Boat bt = new Boat(l, at.getX() + 0.5, at.getY() + 1.0 - 0.1, at.getZ() + 0.5);
        bt.setVariant(Boat.Type.byName(path.replace("_chest_boat", "").replace("_boat", "").replace("_raft", "")));
        bt.setYRot(b.getYRot());
        if (!l.noCollision(bt.getBoundingBox().deflate(0.05))) return false;   // (blocks and other boats only - not the bot standing there)
        l.addFreshEntity(bt);
        it.shrink(1);
        b.inventory.setChanged();
        bt.getPersistentData().putUUID(DRIVER, b.getUUID());
        bt.getPersistentData().putUUID(OWN, b.getUUID());
        if (!b.startRiding(bt, true)) { take(b, bt); return false; }
        launched++;
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        l.playSound(null, at, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.2F);
        return true;
    }

    private static int boatSlot(PartyBotEntity b) {
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (s.getItem() instanceof BoatItem && !net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath().contains("chest")) return i;
        }
        return -1;
    }

    /** Makes a boat from its bag: 5 planks of one wood (cutting logs into planks first if it must). */
    static boolean make(PartyBotEntity b) {
        for (String w : WOODS) {
            ResourceLocation boat = new ResourceLocation("minecraft", w + "_boat");
            ItemStack made = BotCrafting.craftRecipe(b, boat);
            if (made == null) {                                                  // planks from its logs, then try again
                ResourceLocation planks = new ResourceLocation("minecraft", w + "_planks");
                for (int k = 0; k < 2 && made == null; k++) {
                    if (BotCrafting.craftRecipe(b, planks) == null) break;
                    made = BotCrafting.craftRecipe(b, boat);
                }
            }
            if (made != null && !made.isEmpty()) {
                crafted++;
                ServerPlayer o = b.owner();
                if (o != null) o.displayClientMessage(Component.empty().append(b.label()).append(" made a boat.").withStyle(ChatFormatting.AQUA), true);
                return true;
            }
        }
        return false;
    }

    /** Water to put a boat in, next to it (a water surface with air over it), or null. */
    private static BlockPos water(ServerLevel l, PartyBotEntity b) {
        BlockPos c = b.blockPosition();
        BlockPos best = null; double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 1, 2))) {
            if (!l.getFluidState(p).is(Fluids.WATER) || !l.getBlockState(p.above()).isAir()) continue;
            double d = p.distToCenterSqr(b.position());
            if (d < bd) { bd = d; best = p.immutable(); }
        }
        return best;
    }

    // ------------------------------------------------------------------ paddling

    /** Every tick, from the bot's own (server): paddling its boat after you, and out at the far side. */
    static void tick(PartyBotEntity b) {
        if (!(b.getVehicle() instanceof Boat bt) || driverOf(bt) != b || !(b.level() instanceof ServerLevel l)) return;
        ServerPlayer o = b.owner();
        if (o == null || o.level() != l || b.downed()) { why = "no owner here / down"; leave(b, bt); return; }
        Entity ov = o.getVehicle();
        if (ov == bt) return;                                                    // (you got in: you paddle)
        Entity to = ov != null ? ov : o;
        Vec3 d = new Vec3(to.getX() - bt.getX(), 0, to.getZ() - bt.getZ());
        double dist = d.length();
        boolean ashore = ov == null && !o.isInWater() && o.onGround();
        if (ashore && (dist < 7 || bt.horizontalCollision && dist < 20 || !bt.isInWater() && bt.onGround())) {
            why = "ashore: dist " + Math.round(dist) + " bumped " + bt.horizontalCollision + " inWater " + bt.isInWater() + " onGround " + bt.onGround(); leave(b, bt); return; }
        if (dist > 96) { why = "far behind " + Math.round(dist); leave(b, bt); return; }                                  // (left far behind: its catching-up does the rest)
        float want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float off = Mth.wrapDegrees(want - bt.getYRot());
        float yaw = bt.getYRot() + Mth.clamp(off, -6F, 6F);
        bt.setYRot(yaw); bt.yRotO = yaw;
        b.setYRot(yaw); b.yBodyRot = yaw; b.yHeadRot = yaw;
        boolean go = Math.abs(off) < 60 && dist > (ov != null ? 6 : 3);
        if (go) {
            float r = yaw * Mth.DEG_TO_RAD;
            bt.setDeltaMovement(bt.getDeltaMovement().add(-Mth.sin(r) * 0.045, 0, Mth.cos(r) * 0.045));
            paddled++;
        }
        bt.setPaddleState(go || Math.abs(off) > 6, go || Math.abs(off) > 6);
    }

    /** Out of the boat; its own boat goes back in its bag. */
    static void leave(PartyBotEntity b, Boat bt) {
        b.stopRiding();
        if (bt.getPassengers().isEmpty()) bt.getPersistentData().remove(DRIVER);
        if (bt.getPersistentData().hasUUID(OWN) && bt.getPersistentData().getUUID(OWN).equals(b.getUUID()) && bt.getPassengers().isEmpty()) take(b, bt);
    }

    private static void take(PartyBotEntity b, Boat bt) {
        ItemStack it = new ItemStack(bt.getDropItem());
        bt.discard();
        ItemStack left = b.inventory.addItem(it);
        if (!left.isEmpty()) b.spawnAtLocation(left);
        packed++;
    }

    /** It's going away (dismissed, unloaded): its own boat goes with it. */
    static void forget(PartyBotEntity b) {
        if (b.getVehicle() instanceof Boat bt && driverOf(bt) == b) { why = "going away"; leave(b, bt); }
    }
}

package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rhalix can fly in survival (double-tap jump, like creative) on his wings - but only up to
 * MAX_HEIGHT blocks above whatever is under him. His own client stops him rising past it; the
 * server stops the flight if someone gets far beyond it anyway.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RhalixFlight {
    /** Starting flight height (blocks above the ground); each level he gains (any stat) lets him go one block higher. */
    public static final double BASE_HEIGHT = 60, PER_LEVEL = 3, MAX_LIMIT = 200;
    /** The limit as last told by the server (client side). */
    public static double clientMax = BASE_HEIGHT;
    /** Flying speed: normal at first, building up the longer he keeps flying forward (about 10 seconds to top speed). */
    private static final float BASE_SPEED = 0.06F, TOP_SPEED = 0.2F;
    private static final int SPEED_UP_TICKS = 200;
    private static final java.util.Map<java.util.UUID, double[]> CRUISE = new java.util.HashMap<>();   // {x, z, ticks}
    private static final String GRANTED = "xenowarriors_granted_flight";

    private RhalixFlight() {}

    /** Three hunger bars (6 points) or less: too weak to keep his wings going. */
    public static final int HUNGRY_FOOD = 6;

    public static boolean canFly(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        return winged(p) && !p.isCreative() && !p.isSpectator() && RhalixWings.out(p)
                && (t != CharacterType.RHALIX || !starving(p)) && !com.xenowarriors.you.YouRaces.isSeed(p);
    }

    /** Has wings: a winged show character, or a You character of a winged race (not a demon in human form). */
    public static boolean winged(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        return t == CharacterType.YOU ? com.xenowarriors.you.You.wings(p) : t.skylander();
    }

    /** Puts them on the ground (wings gone): flight taken back right away. */
    public static void land(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return;
        var a = p.getAbilities();
        if (a.flying || a.mayfly) { a.mayfly = false; a.flying = false; p.onUpdateAbilities(); }
    }

    public static boolean starving(Player p) {
        return p.getFoodData().getFoodLevel() <= HUNGRY_FOOD && !p.isCreative() && !p.isSpectator();
    }

    private static final java.util.Map<java.util.UUID, int[]> HUNGER = new java.util.HashMap<>();   // {hungry 0/1, next growl tick}

    /** Starving: he drops out of the sky, his stomach growls (and keeps growling now and then until he eats). */
    private static void hunger(ServerPlayer p) {
        boolean rhalix = ModCapabilities.getType(p) == CharacterType.RHALIX;
        boolean hungry = rhalix && starving(p);
        int[] h = HUNGER.computeIfAbsent(p.getUUID(), k -> new int[]{0, 0});
        boolean was = h[0] == 1;
        if (hungry != was) {
            h[0] = hungry ? 1 : 0;
            boolean growl = false;
            if (hungry) {
                var ab = p.getAbilities();
                if (ab.flying) {
                    ab.flying = false;
                    p.onUpdateAbilities();
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.xenowarriors.rhalix.too_hungry_fall"), true);
                } else {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.xenowarriors.rhalix.too_hungry"), true);
                }
                growl = true;
                h[1] = p.tickCount + 300 + p.getRandom().nextInt(400);
            }
            if (growl) playGrowl(p);
            com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.RhalixHungerPacket(p.getId(), hungry, growl), p);
        } else if (hungry && p.tickCount >= h[1]) {
            h[1] = p.tickCount + 300 + p.getRandom().nextInt(400);
            playGrowl(p);
            com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.RhalixHungerPacket(p.getId(), true, true), p);
        } else if (hungry && p.tickCount % 100 == 0) {
            // keep players who just came near up to date
            com.xenowarriors.network.ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.RhalixHungerPacket(p.getId(), true, false), p);
        }
    }

    private static void playGrowl(ServerPlayer p) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.xenowarriors.ModSounds.STOMACH_GROWL.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.9F + p.getRandom().nextFloat() * 0.2F);
    }

    /** How high he can fly: 60 blocks to start, +3 for every level he's gained in Speed, Attack and Defense. */
    public static double maxHeight(Player p) {
        if (p.level().isClientSide) return clientMax;
        int levels = p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.statLevel[0] + d.statLevel[1] + d.statLevel[2]).orElse(0);
        return Math.min(MAX_LIMIT, BASE_HEIGHT + levels * PER_LEVEL);
    }

    /** Blocks between the player's feet and the ground/water below (limit + 16 if there's nothing in range). */
    public static double heightAboveGround(Player p) {
        double range = maxHeight(p) + 16;
        Vec3 from = p.position();
        Vec3 to = from.subtract(0, range, 0);
        var hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.MISS ? range : from.y - hit.getLocation().y;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        hunger(p);
        var abilities = p.getAbilities();
        boolean granted = p.getPersistentData().getBoolean(GRANTED);
        if (canFly(p)) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                p.getPersistentData().putBoolean(GRANTED, true);
                p.onUpdateAbilities();
            }
            // way past the limit (lag or a modified client): drop out of flight
            if (p.tickCount % 40 == 0) {
                com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.FlightHeightPacket((float) maxHeight(p)), p);
            }
            // gaining speed while flying forward
            double[] c = CRUISE.computeIfAbsent(p.getUUID(), k -> new double[]{p.getX(), p.getZ(), 0});
            double moved = Math.hypot(p.getX() - c[0], p.getZ() - c[1]);
            c[0] = p.getX();
            c[1] = p.getZ();
            c[2] = abilities.flying && moved > 0.9 ? Math.min(SPEED_UP_TICKS, c[2] + 5) : Math.max(0, c[2] - 15);
            float speed = (BASE_SPEED + (TOP_SPEED - BASE_SPEED) * (float) (c[2] / SPEED_UP_TICKS))
                    * com.xenowarriors.stats.FlightBoost.factor(p);
            if (Math.abs(abilities.getFlyingSpeed() - speed) > 0.002F) {
                abilities.setFlyingSpeed(speed);
                p.onUpdateAbilities();
            }
            // (past the limit he just can't climb any higher - see the client's limitRhalixFlight; he's never dropped)
        } else if (granted && !p.isCreative() && !p.isSpectator() && !com.xenowarriors.mikiko.WindFlight.canFly(p)) {
            // switched away from Rhalix: take the flight back
            abilities.mayfly = false;
            abilities.flying = false;
            abilities.setFlyingSpeed(BASE_SPEED);
            CRUISE.remove(p.getUUID());
            p.getPersistentData().remove(GRANTED);
            p.onUpdateAbilities();
        } else if (granted) {
            p.getPersistentData().remove(GRANTED);
        }
    }
}

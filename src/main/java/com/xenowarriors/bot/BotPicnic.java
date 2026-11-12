package com.xenowarriors.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Picnics. Now and then, when nobody's fighting, one of your bots asks for a picnic (a request card - turned down by
 * itself if anything attacks). Say yes and it sets out a picnic table next to you: a table with benches either side
 * and a picnic basket (a barrel) full of food - hamburgers, bread, apples, pie, cookies. For 3 minutes:
 * <ul>
 *   <li>food eaten at the picnic heals: Regeneration on top of what it fills;</li>
 *   <li>the bots sit at the table, eat and get their health back;</li>
 *   <li>at the end everyone who was there gets closer (bond with you, and with each other).</li>
 * </ul>
 * The table is packed away afterwards (whatever's left in the basket comes back to you). A fight starting ends it early,
 * without the bond. Where blocks can't be placed (arenas, VR...) the food is handed out instead.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotPicnic {
    private BotPicnic() {}

    public static final int LENGTH = 20 * 60 * 3, GAP = 20 * 60 * 35, ASK_TICKS = 20 * 20;
    public static final float BOND_END = 25F;
    private static final String LAST = "xw_picnic_at";

    private static final class Picnic {
        long end; BlockPos center, basket; final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
        final List<BlockPos> seats = new ArrayList<>(); final Set<UUID> guests = new HashSet<>();
    }
    private static final Map<UUID, Picnic> PICNICS = new HashMap<>();
    /** (Tests) picnics asked for / held / finished in full. */
    public static int asked, held, finished;

    public static boolean active(ServerPlayer o) { return PICNICS.containsKey(o.getUUID()); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer) return;
        Picnic pc = PICNICS.get(o.getUUID());
        if (pc != null) { picnicTick(o, pc); return; }
        if (o.tickCount % 200 != 71) return;
        long now = o.level().getGameTime(), last = o.getPersistentData().getLong(LAST);
        if (last > 0 && now >= last && now - last < GAP) return;
        if (o.getRandom().nextInt(14) != 0 || !BotSpar.quiet(o) || o.isSpectator() || o.getVehicle() != null || !o.onGround()) return;
        if (BotPartyTime.active(o) || BotRequests.waiting(o)) return;
        List<PartyBotEntity> bots = new ArrayList<>();
        for (PartyBotEntity b : PartyBots.bots(o)) if (!b.downed() && !b.journey() && !b.sparring() && b.getTarget() == null && b.distanceToSqr(o) < 16 * 16) bots.add(b);
        if (bots.isEmpty()) return;
        ask(bots.get(o.getRandom().nextInt(bots.size())), o);
        o.getPersistentData().putLong(LAST, now);
    }

    public static void ask(PartyBotEntity b, ServerPlayer o) {
        asked++;
        BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(b.character().getDisplayName().getString() + " wants a picnic!",
                "A table, a basket of food and a break - picnic food heals, and everyone gets closer.", "/xenopicnic yes " + b.getId(), "/xenopicnic no " + b.getId(), ASK_TICKS, 0xFF7CD860));
        o.displayClientMessage(Component.empty().append(b.label()).append(": \"How about a picnic? I'm starving!\"").withStyle(ChatFormatting.GREEN), false);
    }

    /** The food in the basket. */
    private static List<ItemStack> food() {
        return List.of(new ItemStack(com.xenowarriors.item.ModItems.HAMBURGER.get(), 4), new ItemStack(Items.BREAD, 6), new ItemStack(Items.APPLE, 6),
                new ItemStack(Items.PUMPKIN_PIE, 3), new ItemStack(Items.COOKIE, 8), new ItemStack(Items.COOKED_CHICKEN, 4), new ItemStack(Items.SWEET_BERRIES, 8));
    }

    /** Sets the picnic out by its player. */
    public static boolean start(ServerPlayer o) {
        if (PICNICS.containsKey(o.getUUID()) || BotPartyTime.active(o)) return false;
        Picnic pc = new Picnic();
        pc.end = o.level().getGameTime() + LENGTH;
        ServerLevel l = o.serverLevel();
        BlockPos spot = com.xenowarriors.modes.ModeRules.canGrief(l) ? findSpot(l, o) : null;
        if (spot != null) {
            pc.center = spot;
            Direction f = Direction.EAST;                                       // the table runs along z: benches west and east of it
            for (int dz = 0; dz <= 1; dz++) {
                BlockPos t = spot.offset(0, 0, dz);
                place(l, pc, t, Blocks.OAK_FENCE.defaultBlockState());
                place(l, pc, t.above(), (dz == 0 ? Blocks.RED_CARPET : Blocks.WHITE_CARPET).defaultBlockState());   // (a checked tablecloth)
                place(l, pc, t.west(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
                place(l, pc, t.east(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
                pc.seats.add(t.west()); pc.seats.add(t.east());
            }
            BlockPos basket = spot.offset(0, 0, -1);
            place(l, pc, basket, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            if (l.getBlockEntity(basket) instanceof BarrelBlockEntity be) {
                int i = 0;
                for (ItemStack s : food()) be.setItem(i++, s);
                pc.basket = basket;
            }
        } else {                                                                 // nowhere to put a table (or no building here): the food is passed round
            pc.center = o.blockPosition();
            for (ItemStack s : food()) if (!o.getInventory().add(s.copy())) o.drop(s.copy(), false);
        }
        PICNICS.put(o.getUUID(), pc);
        held++;
        guests(o, pc);
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 40 * 40)
            p.displayClientMessage(Component.literal(pc.basket != null ? "Picnic time! Help yourself from the basket." : "Picnic time! The food's in your bag.")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        l.playSound(null, pc.center, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.6F);
        return true;
    }

    /** A free 3x4 patch of flat ground a couple of blocks from them (null when there's none). */
    private static BlockPos findSpot(ServerLevel l, ServerPlayer o) {
        BlockPos base = o.blockPosition();
        for (int r = 2; r <= 5; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos c = base.offset(dx, dy, dz);
                if (fits(l, c, o)) return c;
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel l, BlockPos c, ServerPlayer o) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos p = c.offset(x, 0, z);
            if (!l.getBlockState(p).isAir() || !l.getBlockState(p.above()).isAir()) return false;
            if (!l.getBlockState(p.below()).isFaceSturdy(l, p.below(), Direction.UP)) return false;
            if (o.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) return false;
        }
        BlockPos p = c.offset(0, 0, 2);                                         // (the second row of the table)
        for (int x = -1; x <= 1; x++) {
            BlockPos q = p.offset(x, 0, -1);
            if (!l.getBlockState(q).isAir() || !l.getBlockState(q.below()).isFaceSturdy(l, q.below(), Direction.UP)) return false;
        }
        return true;
    }

    private static void place(ServerLevel l, Picnic pc, BlockPos p, BlockState s) {
        pc.placed.put(p.immutable(), s);
        l.setBlockAndUpdate(p, s);
    }

    /** Everyone at the picnic: its player, party members close by and its player's bots close by. */
    private static List<LivingEntity> guests(ServerPlayer o, Picnic pc) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 c = Vec3.atCenterOf(pc.center);
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(o)) if (m.level() == o.level() && m.position().distanceTo(c) < 24) out.add(m);
        for (PartyBotEntity b : PartyBots.bots(o)) if (b.level() == o.level() && !b.downed() && b.position().distanceTo(c) < 24) out.add(b);
        for (LivingEntity g : out) pc.guests.add(g.getUUID());
        return out;
    }

    private static void picnicTick(ServerPlayer o, Picnic pc) {
        ServerLevel l = o.serverLevel();
        long now = l.getGameTime();
        boolean fight = !BotSpar.quiet(o);
        if (now >= pc.end || fight || !o.isAlive() || o.level() != l || o.position().distanceTo(Vec3.atCenterOf(pc.center)) > 48) { end(o, pc, !fight && now >= pc.end); return; }
        if (o.tickCount % 10 != 0) return;
        List<LivingEntity> gs = guests(o, pc);
        int seat = 0;
        for (LivingEntity g : gs) if (g instanceof PartyBotEntity b) {
            BlockPos s = pc.seats.isEmpty() ? null : pc.seats.get(seat++ % pc.seats.size());
            Vec3 at = s == null ? Vec3.atBottomCenterOf(pc.center) : Vec3.atBottomCenterOf(s).add(0, 0.5, 0);
            if (b.position().distanceTo(at) > 1.2) { if (b.getNavigation().isDone() || o.tickCount % 40 == 0) b.getNavigation().moveTo(at.x, at.y, at.z, 1.0); }
            else {
                b.getNavigation().stop();
                b.getLookControl().setLookAt(pc.center.getX() + 0.5, pc.center.getY() + 1.1, pc.center.getZ() + 0.5);
                if (o.tickCount % 60 == b.getId() % 6 * 10) {                     // a bite: munch, heal a little
                    b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    b.heal(2F);
                    l.playSound(null, b.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.5F, 1F + l.random.nextFloat() * 0.3F);
                }
            }
        }
    }

    /** Food eaten at a picnic heals: Regeneration on top of what it fills. */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getItem().getFoodProperties(p) == null) return;
        for (Map.Entry<UUID, Picnic> en : PICNICS.entrySet())
            if (en.getValue().guests.contains(p.getUUID()) || en.getKey().equals(p.getUUID()))
                if (p.position().distanceTo(Vec3.atCenterOf(en.getValue().center)) < 16) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 1, false, true));
                    return;
                }
    }

    private static void end(ServerPlayer o, Picnic pc, boolean full) {
        PICNICS.remove(o.getUUID());
        ServerLevel l = o.serverLevel();
        if (pc.basket != null && l.getBlockEntity(pc.basket) instanceof BarrelBlockEntity be) {   // (what's left in the basket comes back to you)
            for (int i = 0; i < be.getContainerSize(); i++) {
                ItemStack s = be.removeItemNoUpdate(i);
                if (!s.isEmpty() && !o.getInventory().add(s)) o.drop(s, false);
            }
        }
        List<BlockPos> order = new ArrayList<>(pc.placed.keySet());
        Collections.reverse(order);                                             // (the tablecloth before the table under it)
        for (BlockPos p : order) if (l.getBlockState(p).is(pc.placed.get(p).getBlock())) l.removeBlock(p, false);
        finished += full ? 1 : 0;
        if (full) {
            List<LivingEntity> gs = guests(o, pc);
            List<PartyBotEntity> bots = new ArrayList<>();
            for (LivingEntity g : gs) if (g instanceof PartyBotEntity b) bots.add(b);
            for (PartyBotEntity b : bots) {
                com.xenowarriors.party.Bonds.addXp(o, b.character().getId(), BOND_END);
                for (PartyBotEntity b2 : bots) if (b2 != b) com.xenowarriors.party.Bonds.addXp(b, b2.character().getId(), BOND_END);
            }
            for (LivingEntity g : gs) if (g instanceof ServerPlayer m && m != o) {
                com.xenowarriors.party.Bonds.addXp(m, com.xenowarriors.party.Bonds.charOf(o), BOND_END);
                com.xenowarriors.party.Bonds.addXp(o, com.xenowarriors.party.Bonds.charOf(m), BOND_END);
            }
            o.displayClientMessage(Component.literal("That was a lovely picnic - everyone there feels closer.").withStyle(ChatFormatting.GREEN), false);
        } else o.displayClientMessage(Component.literal("The picnic's packed up early.").withStyle(ChatFormatting.GRAY), false);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenopicnic")
                .then(Commands.literal("yes").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), true))))
                .then(Commands.literal("no").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), false)))));
    }

    private static int answer(ServerPlayer p, int id, boolean yes) {
        com.xenowarriors.network.RequestPopupPacket.dismiss(p, "/xenopicnic yes " + id);
        if (!(p.level().getEntity(id) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId())) return 0;
        if (!yes) { p.displayClientMessage(Component.empty().append(b.label()).append(": \"Oh well. Another time!\"").withStyle(ChatFormatting.GRAY), true); return 1; }
        if (!BotSpar.quiet(p)) { p.displayClientMessage(Component.literal("Not now - there's trouble about.").withStyle(ChatFormatting.RED), true); return 0; }
        return start(p) ? 1 : 0;
    }

    /** (Tests) ends a picnic now (as if its time were up). */
    public static void endForTest(ServerPlayer o) { Picnic pc = PICNICS.get(o.getUUID()); if (pc != null) end(o, pc, true); }
    public static BlockPos basketForTest(ServerPlayer o) { Picnic pc = PICNICS.get(o.getUUID()); return pc == null ? null : pc.basket; }
    public static int blocksForTest(ServerPlayer o) { Picnic pc = PICNICS.get(o.getUUID()); return pc == null ? -1 : pc.placed.size(); }

    /** Logging out mid-picnic: packed away (no bond). */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer o && PICNICS.containsKey(o.getUUID())) end(o, PICNICS.get(o.getUUID()), false);
    }
}

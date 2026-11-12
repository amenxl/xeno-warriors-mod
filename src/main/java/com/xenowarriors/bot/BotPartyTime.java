package com.xenowarriors.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.PartyHatsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Party time! Now and then (rarely - at most every 40 minutes), when nobody's fighting, one of your bots asks to throw
 * a party (a request card). Say yes and for 3 minutes:
 * <ul>
 *   <li>everyone there - you, party members nearby and your bots - wears a party hat;</li>
 *   <li>confetti rains down and fireworks go up now and then;</li>
 *   <li>a cake is set out (taken away again at the end if nobody finished it), everyone gets a snack and Regeneration;</li>
 *   <li>a little party tune plays and the bots dance about - hops, spins and arm-waves;</li>
 *   <li>bonds grow: you with each bot and the bots with each other, a bit every 20 seconds and a good chunk at the end.</li>
 * </ul>
 * A fight starting ends it early (no end bonus then).
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotPartyTime {
    private BotPartyTime() {}

    public static final int LENGTH = 20 * 60 * 3, GAP = 20 * 60 * 40, ASK_TICKS = 20 * 20;
    public static final float BOND_TICK = 5F, BOND_END = 30F;
    private static final String LAST = "xw_party_time_at";
    /** pentatonic steps for the tune (a random, cheerful little melody - nothing in particular) */
    private static final float[] NOTES = {0.5F, 0.561F, 0.63F, 0.749F, 0.841F, 1.0F, 1.122F, 1.26F, 1.498F, 1.682F, 2.0F};

    private static final class Party { long end; BlockPos center, cake; int note; final Set<UUID> guests = new HashSet<>(); }
    private static final Map<UUID, Party> PARTIES = new HashMap<>();
    /** (Tests) parties asked for / thrown / finished; fireworks launched. */
    public static int asked, thrown, finished, fireworks;

    public static boolean active(ServerPlayer o) { return PARTIES.containsKey(o.getUUID()); }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer) return;
        Party pt = PARTIES.get(o.getUUID());
        if (pt != null) { partyTick(o, pt); return; }
        if (o.tickCount % 200 != 157) return;
        long now = o.level().getGameTime(), last = o.getPersistentData().getLong(LAST);
        if (last > 0 && now >= last && now - last < GAP) return;
        if (o.getRandom().nextInt(12) != 0 || !BotSpar.quiet(o) || o.isSpectator() || o.getVehicle() != null) return;
        List<PartyBotEntity> bots = new ArrayList<>();
        for (PartyBotEntity b : PartyBots.bots(o)) if (!b.downed() && !b.journey() && b.getTarget() == null && b.distanceToSqr(o) < 16 * 16) bots.add(b);
        if (bots.isEmpty()) return;
        ask(bots.get(o.getRandom().nextInt(bots.size())), o);
        o.getPersistentData().putLong(LAST, now);
    }

    public static void ask(PartyBotEntity b, ServerPlayer o) {
        asked++;
        BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(b.character().getDisplayName().getString() + " wants to throw a party!",
                "Party hats, cake, confetti and fireworks - and everyone gets closer.", "/xenoparty yes " + b.getId(), "/xenoparty no " + b.getId(), ASK_TICKS, 0xFFFF6FD8));
        o.displayClientMessage(Component.empty().append(b.label()).append(": \"Hey - how about a party?\"").withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** Starts the party around its player. */
    public static boolean start(ServerPlayer o) {
        if (PARTIES.containsKey(o.getUUID())) return false;
        Party pt = new Party();
        pt.end = o.level().getGameTime() + LENGTH;
        pt.center = o.blockPosition();
        ServerLevel l = o.serverLevel();
        if (com.xenowarriors.modes.ModeRules.canGrief(l)) {                   // the cake
            for (int dx = -2; dx <= 2 && pt.cake == null; dx++) for (int dz = -2; dz <= 2 && pt.cake == null; dz++) {
                BlockPos p = pt.center.offset(dx, 0, dz);
                if ((dx != 0 || dz != 0) && l.getBlockState(p).isAir() && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP)) {
                    l.setBlockAndUpdate(p, Blocks.CAKE.defaultBlockState());
                    pt.cake = p;
                }
            }
        }
        PARTIES.put(o.getUUID(), pt);
        thrown++;
        for (LivingEntity g : guests(o, pt)) {
            g.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60, 0, false, true));
            if (g instanceof ServerPlayer gp) gp.getInventory().add(new ItemStack(Items.COOKIE, 4));
        }
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 40 * 40)
            p.displayClientMessage(Component.literal("Party time! Hats on, everyone!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
        l.playSound(null, pt.center, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1.2F);
        firework(l, pt, 3);
        return true;
    }

    /** Everyone at the party: its player, party members close by and its player's bots close by. */
    private static List<LivingEntity> guests(ServerPlayer o, Party pt) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 c = Vec3.atCenterOf(pt.center);
        for (ServerPlayer m : com.xenowarriors.party.Parties.members(o)) if (m.level() == o.level() && m.position().distanceTo(c) < 24) out.add(m);
        for (PartyBotEntity b : PartyBots.bots(o)) if (b.level() == o.level() && !b.downed() && b.position().distanceTo(c) < 24) out.add(b);
        for (LivingEntity g : out) pt.guests.add(g.getUUID());
        return out;
    }

    private static void partyTick(ServerPlayer o, Party pt) {
        ServerLevel l = o.serverLevel();
        long now = l.getGameTime();
        boolean fight = !BotSpar.quiet(o);
        if (now >= pt.end || fight || !o.isAlive() || o.position().distanceTo(Vec3.atCenterOf(pt.center)) > 48) { end(o, pt, !fight && now >= pt.end); return; }
        List<LivingEntity> gs = guests(o, pt);
        if (o.tickCount % 20 == 0) {                                          // hats on (and the confetti) for everyone near
            int[] ids = gs.stream().mapToInt(LivingEntity::getId).toArray();
            int left = (int) (pt.end - now);
            for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 64 * 64) ModNetwork.sendToPlayer(new PartyHatsPacket(ids, left, pt.center), p);
        }
        if (o.tickCount % 8 == 0) {                                           // the tune: a random cheerful melody
            float pitch = NOTES[Math.floorMod(pt.note + l.random.nextInt(5) - 2, NOTES.length)];
            pt.note = Math.floorMod(pt.note + l.random.nextInt(5) - 2, NOTES.length);
            l.playSound(null, pt.center, o.tickCount % 32 == 0 ? SoundEvents.NOTE_BLOCK_BASS.value() : (o.tickCount % 16 == 0 ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BELL.value()),
                    SoundSource.RECORDS, 0.5F, pitch);
        }
        if (l.random.nextInt(170) == 0) firework(l, pt, 1 + l.random.nextInt(2));
        for (LivingEntity g : gs) if (g instanceof PartyBotEntity b && o.tickCount % 10 == b.getId() % 10) dance(b, pt);
        if (o.tickCount % 400 == 0) bondTick(o, gs, BOND_TICK);
    }

    /** The bots dance about near the middle: hops, spins and arm-waves. */
    private static void dance(PartyBotEntity b, Party pt) {
        var r = b.getRandom();
        Vec3 c = Vec3.atBottomCenterOf(pt.center);
        if (b.position().distanceTo(c) > 5 && b.getNavigation().isDone()) {
            double a = r.nextDouble() * Math.PI * 2;
            b.getNavigation().moveTo(c.x + Math.cos(a) * 2.5, c.y, c.z + Math.sin(a) * 2.5, 1.0);
            return;
        }
        switch (r.nextInt(4)) {
            case 0 -> { if (b.onGround()) b.getJumpControl().jump(); }
            case 1 -> { b.setYRot(b.getYRot() + 90F); b.yBodyRot = b.getYRot(); b.setYHeadRot(b.getYRot()); }
            case 2 -> b.swing(r.nextBoolean() ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
            default -> b.getLookControl().setLookAt(c.x, c.y + 1.6, c.z);
        }
    }

    private static void firework(ServerLevel l, Party pt, int n) {
        var r = l.random;
        for (int i = 0; i < n; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            CompoundTag fw = new CompoundTag(), ex = new CompoundTag();
            ex.putByte("Type", (byte) r.nextInt(5));
            int[] pal = {0xFF4FA0, 0x40C0FF, 0xFFE040, 0x7CFF6A, 0xC070FF, 0xFFFFFF};
            ex.putIntArray("Colors", new int[]{pal[r.nextInt(pal.length)], pal[r.nextInt(pal.length)]});
            ex.putBoolean("Twinkle", r.nextBoolean());
            ListTag exs = new ListTag(); exs.add(ex);
            fw.put("Explosions", exs);
            fw.putByte("Flight", (byte) 2);
            rocket.getOrCreateTag().put("Fireworks", fw);
            double a = r.nextDouble() * Math.PI * 2, d = 5 + r.nextDouble() * 3;
            Vec3 at = Vec3.atBottomCenterOf(pt.center).add(Math.cos(a) * d, 0.5, Math.sin(a) * d);
            l.addFreshEntity(new FireworkRocketEntity(l, at.x, at.y, at.z, rocket));   // (it goes off high up - nobody gets hurt)
            fireworks++;
        }
    }

    private static void bondTick(ServerPlayer o, List<LivingEntity> gs, float xp) {
        List<PartyBotEntity> bots = new ArrayList<>();
        for (LivingEntity g : gs) if (g instanceof PartyBotEntity b) bots.add(b);
        for (PartyBotEntity b : bots) {
            com.xenowarriors.party.Bonds.addXp(o, b.character().getId(), xp);
            for (PartyBotEntity b2 : bots) if (b2 != b) com.xenowarriors.party.Bonds.addXp(b, b2.character().getId(), xp);
        }
        for (LivingEntity g : gs) if (g instanceof ServerPlayer m && m != o) {     // (party members: with the host's character and each other)
            com.xenowarriors.party.Bonds.addXp(m, com.xenowarriors.party.Bonds.charOf(o), xp);
            com.xenowarriors.party.Bonds.addXp(o, com.xenowarriors.party.Bonds.charOf(m), xp);
        }
    }

    private static void end(ServerPlayer o, Party pt, boolean full) {
        PARTIES.remove(o.getUUID());
        ServerLevel l = o.serverLevel();
        if (pt.cake != null && l.getBlockState(pt.cake).is(Blocks.CAKE)) l.removeBlock(pt.cake, false);
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(o) < 64 * 64) ModNetwork.sendToPlayer(new PartyHatsPacket(new int[0], 0, pt.center), p);
        finished++;
        if (full) {
            List<LivingEntity> gs = guests(o, pt);
            bondTick(o, gs, BOND_END);
            firework(l, pt, 4);
            o.displayClientMessage(Component.literal("What a party! Everyone there feels a lot closer.").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else o.displayClientMessage(Component.literal("The party's broken up.").withStyle(ChatFormatting.GRAY), false);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenoparty")
                .then(Commands.literal("yes").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), true))))
                .then(Commands.literal("no").then(Commands.argument("bot", IntegerArgumentType.integer()).executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "bot"), false)))));
    }

    private static int answer(ServerPlayer p, int id, boolean yes) {
        com.xenowarriors.network.RequestPopupPacket.dismiss(p, "/xenoparty yes " + id);
        if (!(p.level().getEntity(id) instanceof PartyBotEntity b) || !p.getUUID().equals(b.ownerId())) return 0;
        if (!yes) { p.displayClientMessage(Component.empty().append(b.label()).append(": \"Aw. Next time!\"").withStyle(ChatFormatting.GRAY), true); return 1; }
        if (!BotSpar.quiet(p)) { p.displayClientMessage(Component.literal("Not now - there's trouble about.").withStyle(ChatFormatting.RED), true); return 0; }
        return start(p) ? 1 : 0;
    }

    /** (Tests) ends a party now (as if its time were up). */
    public static void endForTest(ServerPlayer o) { Party pt = PARTIES.get(o.getUUID()); if (pt != null) end(o, pt, true); }
}

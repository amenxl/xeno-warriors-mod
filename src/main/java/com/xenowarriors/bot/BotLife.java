package com.xenowarriors.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.party.Bonds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * A bot teammate's life around you:
 * <ul>
 *   <li>Night camp: you go to bed - they gather round and keep watch (a campfire by the bed where you may build,
 *       put out again when you get up). Anything hostile coming close, they go for it; it gets near you and they wake
 *       you up.</li>
 *   <li>Feeding you: hungry (5 drumsticks or fewer) and nothing about - one with food in its bag comes over and feeds
 *       you (Yuri: a hamburger if she has one).</li>
 *   <li>Gifts: with a bond of {@value #GIFT_BOND}+, now and then one gives you something - a valuable from its bag,
 *       or something of its own.</li>
 *   <li>Asking for help: two teammates who don't get on yet (a bond under {@value #HELP_BOND}) - one asks you to help
 *       them hang out (a request card); say yes and they spend a little while together and get closer. And one losing a
 *       fight far from you calls for help.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotLife {
    private BotLife() {}

    public static final int GIFT_BOND = 70, HELP_BOND = 30, HUNGRY = 10, HANG_TICKS = 20 * 30;
    public static final float HANG_XP = 60F;
    private static final String FED = "xw_life_fed", GIFT = "xw_life_gift", ASKED = "xw_life_help", CALLED = "xw_life_called";
    /** (Tests) */
    public static int camps, campfires, wakes, feeds, gifts, helpAsks, hangouts, helpCalls;

    private static final class Camp { BlockPos fire; boolean woke; }
    private static final Map<UUID, Camp> CAMPS = new HashMap<>();
    private record Hang(UUID a, UUID b, long until) {}
    private static final Map<UUID, Hang> HANGS = new HashMap<>();

    /** A night camp or a hang-out going on. */
    public static boolean busy(ServerPlayer o) { return CAMPS.containsKey(o.getUUID()) || HANGS.containsKey(o.getUUID()); }

    private static long now(ServerPlayer o) { return o.level().getGameTime(); }

    private static boolean calm(PartyBotEntity b, ServerPlayer o) {
        return !b.downed() && !b.journey() && !b.sparring() && !b.isPassenger() && b.getTarget() == null && b.level() == o.level() && b.errand == null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer o) || o instanceof FakePlayer) return;
        try {
            if (o.isSleeping() || CAMPS.containsKey(o.getUUID())) camp(o);
            if (o.tickCount % 10 == 4) { Hang h = HANGS.get(o.getUUID()); if (h != null) hang(o, h); }
            if (o.tickCount % 40 == 21) feed(o);
            if (o.tickCount % 20 == 9) callForHelp(o);
            if (o.tickCount % 1200 == 611) gift(o);
            if (o.tickCount % 2400 == 1213) askHelp(o);
        } catch (Throwable t) { XenoWarriors.LOGGER.warn("Bot life", t); }
    }

    // ---------------------------------------------------------------- night camp

    private static void camp(ServerPlayer o) {
        Camp c = CAMPS.get(o.getUUID());
        if (!o.isSleeping()) {                                                    // up again: the fire goes out
            if (c != null) {
                if (c.fire != null && o.level().getBlockState(c.fire).is(Blocks.CAMPFIRE)) o.level().setBlock(c.fire, Blocks.AIR.defaultBlockState(), 3);
                CAMPS.remove(o.getUUID());
                for (PartyBotEntity b : PartyBots.bots(o)) if (b.distanceToSqr(o) < 16 * 16 && !c.woke && b.getRandom().nextInt(3) == 0) {
                    say(o, b, morning(b.character())); break;
                }
            }
            return;
        }
        List<PartyBotEntity> bots = PartyBots.bots(o);
        if (bots.isEmpty()) return;
        if (c == null) {
            c = new Camp();
            CAMPS.put(o.getUUID(), c);
            camps++;
            c.fire = campfire(o);
            if (c.fire != null) campfires++;
            PartyBots.testLog(bots.get(0), "night camp" + (c.fire != null ? " with a campfire" : ""));
        }
        if (o.tickCount % 10 != 2) return;
        BlockPos bed = o.getSleepingPos().orElse(o.blockPosition());
        Vec3 centre = Vec3.atBottomCenterOf(bed);
        List<Mob> foes = o.level().getEntitiesOfClass(Mob.class, o.getBoundingBox().inflate(16, 6, 16),
                m -> m.isAlive() && m instanceof Enemy && !(m instanceof PartyBotEntity) && !PartyBotEntity.untouchable(m));
        for (int i = 0; i < bots.size(); i++) {
            PartyBotEntity b = bots.get(i);
            if (b.downed() || b.journey() || b.level() != o.level()) continue;
            if (b.errand != null) { b.errand = null; b.getNavigation().stop(); }   // (bedtime: errands can wait)
            if (b.getTarget() == null && !foes.isEmpty()) {                       // keeping watch: anything coming, they go for it
                Mob near = foes.stream().filter(m -> !PartyBots.friendly(b, m)).min(Comparator.comparingDouble(m -> m.distanceToSqr(b))).orElse(null);
                if (near != null) b.setTarget(near);
            }
            if (b.getTarget() != null) continue;
            double a = Math.PI * 2 * i / bots.size();                              // a ring round the bed, looking out
            Vec3 spot = centre.add(Math.cos(a) * 3.2, 0, Math.sin(a) * 3.2);
            if (b.position().distanceToSqr(spot) > 1.5 * 1.5) b.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            else { b.getNavigation().stop(); b.getLookControl().setLookAt(spot.x + Math.cos(a) * 6, b.getEyeY(), spot.z + Math.sin(a) * 6); }
        }
        boolean danger = foes.stream().anyMatch(m -> m.distanceToSqr(o) < 8 * 8);
        for (PartyBotEntity b : bots) danger |= b.hurtTime > 0 && b.distanceToSqr(o) < 16 * 16;
        if (danger && !c.woke) {                                                  // trouble close by: they wake you
            c.woke = true;
            wakes++;
            PartyBotEntity w = bots.stream().filter(b -> !b.downed()).min(Comparator.comparingDouble(b -> b.distanceToSqr(o))).orElse(null);
            o.stopSleepInBed(true, true);
            if (w != null) say(o, w, "Wake up! We've got trouble!");
            o.level().playSound(null, o.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1F, 1.2F);
        }
    }

    /** A lit campfire on open ground a few blocks from the bed - only where you may build (null: no room, or not here). */
    private static BlockPos campfire(ServerPlayer o) {
        if (!(o.level() instanceof ServerLevel l) || com.xenowarriors.modes.ModeRules.arena(l) || o.isSpectator()) return null;
        BlockPos bed = o.getSleepingPos().orElse(o.blockPosition());
        if (!l.canSeeSky(bed.above())) return null;                               // (indoors: no fire in the house)
        for (Direction d : Direction.Plane.HORIZONTAL) for (int r = 3; r <= 4; r++) {
            BlockPos p = bed.relative(d, r);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos q = p.above(dy);
                if (!l.getBlockState(q).isAir() || !l.getBlockState(q.above()).isAir() || !l.getBlockState(q.below()).isFaceSturdy(l, q.below(), Direction.UP)) continue;
                if (!l.mayInteract(o, q) || !o.mayBuild()) return null;
                if (!l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(q)).isEmpty()) continue;
                l.setBlock(q, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false), 3);
                return q;
            }
        }
        return null;
    }

    private static String morning(CharacterType t) {
        return switch (t) {
            case YURI -> "Morning! Breakfast burgers?";
            case MAX -> "Quiet night. The moon kept watch with us.";
            case ULTIMA -> "Night watch complete. No threats remaining.";
            case GARRY, STEVIE -> "Rise and shine! What a night!";
            default -> "Morning! All clear out here.";
        };
    }

    /** (Server stopping, logout) a fire left burning goes out. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Camp c = CAMPS.remove(e.getEntity().getUUID());
        if (c != null && c.fire != null && e.getEntity().level().getBlockState(c.fire).is(Blocks.CAMPFIRE)) e.getEntity().level().setBlock(c.fire, Blocks.AIR.defaultBlockState(), 3);
        HANGS.remove(e.getEntity().getUUID());
    }

    // ---------------------------------------------------------------- feeding you

    private static boolean badFood(ItemStack s) {
        return s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO) || s.is(Items.PUFFERFISH) || s.is(Items.CHICKEN)
                || s.is(Items.SUSPICIOUS_STEW) || s.is(Items.CHORUS_FRUIT);
    }

    /** The food it would give you (Yuri: a hamburger first), as a bag slot; -1 if it has none to spare. */
    private static int foodSlot(PartyBotEntity b) {
        int best = -1, bestN = 0, total = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!s.isEdible() || badFood(s)) continue;
            total += s.getCount();
            if (b.character() == CharacterType.YURI && s.is(com.xenowarriors.item.ModItems.HAMBURGER.get())) return i;
            var f = s.getFoodProperties(b);
            int n = f == null ? 0 : f.getNutrition();
            if (n > bestN) { bestN = n; best = i; }
        }
        boolean spare = total >= 2 || b.getHealth() >= b.getMaxHealth() * 0.9F;   // (never its last bite when it needs it)
        return spare ? best : -1;
    }

    private static void feed(ServerPlayer o) {
        if (o.isCreative() || o.isSpectator() || o.isSleeping() || o.getFoodData().getFoodLevel() > HUNGRY || !BotSpar.quiet(o)) return;
        if (now(o) < o.getPersistentData().getLong(FED)) return;
        PartyBotEntity giver = null; int slot = -1;
        for (PartyBotEntity b : PartyBots.bots(o)) {
            if (!calm(b, o) || b.distanceToSqr(o) > 20 * 20) continue;
            int s = foodSlot(b);
            if (s >= 0 && (giver == null || b.distanceToSqr(o) < giver.distanceToSqr(o))) { giver = b; slot = s; }
        }
        if (giver == null) return;
        if (giver.distanceToSqr(o) > 2.6 * 2.6) { giver.getNavigation().moveTo(o, 1.2); return; }   // (comes over first)
        giver.getNavigation().stop();
        giver.getLookControl().setLookAt(o);
        ItemStack bite = giver.inventory.removeItem(slot, 1);
        if (bite.isEmpty()) return;
        String name = bite.getHoverName().getString();
        o.getPersistentData().putLong(FED, now(o) + 20 * 90);
        giver.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        o.eat(o.level(), bite);                                                    // (fed: as if you'd eaten it)
        feeds++;
        Bonds.addXp(o, giver.charKey(), 2F);
        say(o, giver, (giver.character() == CharacterType.YURI && name.toLowerCase(Locale.ROOT).contains("burger") ? "Here - a burger fixes everything!" : "You look starving - eat this!") + " (" + name + ")");
    }

    // ---------------------------------------------------------------- gifts

    private static final Item[] VALUABLES = {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.AMETHYST_SHARD, Items.LAPIS_LAZULI, Items.QUARTZ, Items.ENDER_PEARL, Items.NAME_TAG};

    private static ItemStack keepsake(CharacterType t) {
        return switch (t) {
            case MIKIKO -> new ItemStack(Items.SWEET_BERRIES, 8);
            case RHALIX -> new ItemStack(Items.FEATHER, 4);
            case YURI -> new ItemStack(com.xenowarriors.item.ModItems.HAMBURGER.get(), 2);
            case KYRIE -> new ItemStack(Items.AMETHYST_SHARD, 3);
            case MAX -> new ItemStack(Items.GLOW_BERRIES, 6);
            case SAM -> new ItemStack(Items.COOKIE, 8);
            case TECHNICA -> new ItemStack(Items.REDSTONE, 12);
            case KAERU -> new ItemStack(Items.RABBIT_FOOT, 1);
            case ULTIMA -> new ItemStack(Items.IRON_INGOT, 4);
            case ZANE -> new ItemStack(Items.EXPERIENCE_BOTTLE, 3);
            case GARRY, STEVIE -> new ItemStack(Items.NOTE_BLOCK, 2);
            default -> new ItemStack(Items.POPPY, 3);
        };
    }

    /** (Tests) a gift now, no dice roll. */
    public static void giftForTest(ServerPlayer o) { forceGift = true; try { gift(o); } finally { forceGift = false; } }
    private static boolean forceGift;

    private static void gift(ServerPlayer o) {
        if (o.isSpectator() || o.isSleeping() || !BotSpar.quiet(o)) return;
        long now = now(o);
        for (PartyBotEntity b : PartyBots.bots(o)) {
            if (!calm(b, o) || b.distanceToSqr(o) > 12 * 12 || Bonds.level(o, b.charKey()) < GIFT_BOND) continue;
            if (!forceGift && (now < b.getPersistentData().getLong(GIFT) || b.getRandom().nextInt(12) != 0)) continue;
            b.getPersistentData().putLong(GIFT, now + 20 * 60 * 30);               // (half an hour at least till the next)
            ItemStack g = ItemStack.EMPTY;
            for (int i = 0; i < b.inventory.getContainerSize() && g.isEmpty(); i++)
                for (Item v : VALUABLES) if (b.inventory.getItem(i).is(v)) { g = b.inventory.removeItem(i, 1); break; }
            String line = g.isEmpty() ? "I got you something!" : "Found this - I want you to have it.";
            if (g.isEmpty()) g = keepsake(b.character());
            String what = g.getCount() + " " + g.getHoverName().getString();
            if (!o.getInventory().add(g)) o.drop(g, false);
            b.getLookControl().setLookAt(o);
            b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            ((ServerLevel) b.level()).sendParticles(ParticleTypes.HEART, b.getX(), b.getEyeY() + 0.4, b.getZ(), 3, 0.3, 0.2, 0.3, 0);
            gifts++;
            say(o, b, line + " (" + what + ")");
            return;                                                               // (one at a time)
        }
    }

    // ---------------------------------------------------------------- asking for help

    /** Two teammates who don't get on yet: one asks you to help them hang out. */
    private static void askHelp(ServerPlayer o) {
        if (now(o) < o.getPersistentData().getLong(ASKED) || HANGS.containsKey(o.getUUID()) || !BotSpar.quiet(o) || BotRequests.waiting(o) || o.isSleeping()) return;
        List<PartyBotEntity> bots = new ArrayList<>();
        for (PartyBotEntity b : PartyBots.bots(o)) if (calm(b, o) && b.distanceToSqr(o) < 24 * 24) bots.add(b);
        Collections.shuffle(bots, new Random(o.getRandom().nextLong()));
        for (PartyBotEntity a : bots) for (PartyBotEntity c : bots) {
            if (a == c || Bonds.botOwn(a, c.charKey()) >= HELP_BOND) continue;
            if (o.getRandom().nextInt(3) != 0) { o.getPersistentData().putLong(ASKED, now(o) + 20 * 60 * 10); return; }
            ask(o, a, c);
            return;
        }
    }

    public static void ask(ServerPlayer o, PartyBotEntity a, PartyBotEntity c) {
        o.getPersistentData().putLong(ASKED, now(o) + 20 * 60 * 30);
        helpAsks++;
        String an = a.character().getDisplayName().getString(), cn = c.character().getDisplayName().getString();
        BotRequests.send(o, new com.xenowarriors.network.RequestPopupPacket(an + " asks for your help",
                an + " and " + cn + " don't really get on yet. Help them hang out for a bit? (their bond goes up)",
                "/xenohelp yes " + a.getId() + " " + c.getId(), "/xenohelp no " + a.getId() + " " + c.getId(), 20 * 20, 0xFFE070C0));
        say(o, a, "Hey... could you help me get along with " + cn + "?");
    }

    private static int answer(ServerPlayer o, int ia, int ic, boolean yes) {
        com.xenowarriors.network.RequestPopupPacket.dismiss(o, "/xenohelp yes " + ia + " " + ic);
        if (!(o.level().getEntity(ia) instanceof PartyBotEntity a) || !(o.level().getEntity(ic) instanceof PartyBotEntity c)
                || !o.getUUID().equals(a.ownerId()) || !o.getUUID().equals(c.ownerId())) return 0;
        if (!yes) { say(o, a, "Okay... maybe another time."); return 1; }
        if (!BotSpar.quiet(o)) { o.displayClientMessage(Component.literal("Not now - there's trouble about.").withStyle(ChatFormatting.RED), true); return 0; }
        HANGS.put(o.getUUID(), new Hang(a.getUUID(), c.getUUID(), now(o) + HANG_TICKS));
        hangouts++;
        say(o, a, "Thanks! Come on, " + c.character().getDisplayName().getString() + " - let's talk a bit.");
        return 1;
    }

    private static PartyBotEntity find(ServerPlayer o, UUID u) {
        for (PartyBotEntity b : PartyBots.bots(o)) if (b.getUUID().equals(u)) return b;
        return null;
    }

    private static void hang(ServerPlayer o, Hang h) {
        PartyBotEntity a = find(o, h.a()), c = find(o, h.b());
        if (a == null || c == null || a.downed() || c.downed() || a.getTarget() != null || c.getTarget() != null) {   // (a fight broke it up)
            HANGS.remove(o.getUUID());
            return;
        }
        Vec3 mid = a.position().add(c.position()).scale(0.5);
        for (PartyBotEntity x : new PartyBotEntity[]{a, c}) {
            PartyBotEntity other = x == a ? c : a;
            if (x.distanceToSqr(other) > 2.5 * 2.5) x.getNavigation().moveTo(mid.x, mid.y, mid.z, 1.0);
            else { x.getNavigation().stop(); x.getLookControl().setLookAt(other); }
        }
        if (o.tickCount % 40 < 10 && a.distanceToSqr(c) < 3 * 3)
            ((ServerLevel) a.level()).sendParticles(ParticleTypes.NOTE, mid.x, a.getEyeY() + 0.5, mid.z, 2, 0.4, 0.1, 0.4, 0.5);
        if (now(o) < h.until()) return;
        HANGS.remove(o.getUUID());
        Bonds.addXp(a, c.charKey(), HANG_XP);
        Bonds.addXp(c, a.charKey(), HANG_XP);
        ((ServerLevel) a.level()).sendParticles(ParticleTypes.HEART, mid.x, a.getEyeY() + 0.5, mid.z, 6, 0.5, 0.2, 0.5, 0);
        o.displayClientMessage(Component.empty().append(a.label()).append(" and ").append(c.label()).append(" get on a little better now (bond +)").withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** (Tests) ends a hang-out now. */
    public static void endHangForTest(ServerPlayer o) { Hang h = HANGS.get(o.getUUID()); if (h != null) { HANGS.put(o.getUUID(), new Hang(h.a(), h.b(), 0)); hang(o, HANGS.get(o.getUUID())); } }

    /** Losing a fight far from you: it calls for help (where it is). */
    private static void callForHelp(ServerPlayer o) {
        for (PartyBotEntity b : PartyBots.bots(o)) {
            LivingEntity t = b.getTarget();
            if (t == null || b.downed() || b.sparring() || b.journey() || b.level() != o.level() || b.getHealth() > b.getMaxHealth() * 0.35F) continue;
            double d = Math.sqrt(b.distanceToSqr(o));
            if (d < 16 || now(o) < b.getPersistentData().getLong(CALLED)) continue;
            b.getPersistentData().putLong(CALLED, now(o) + 20 * 60);
            helpCalls++;
            say(o, b, "Help! I'm in trouble over here! (" + (int) d + " blocks " + dir(o, b) + ", fighting " + t.getName().getString() + ")");
            b.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));   // (you can see where)
        }
    }

    private static String dir(ServerPlayer o, PartyBotEntity b) {
        double dx = b.getX() - o.getX(), dz = b.getZ() - o.getZ();
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        String[] n = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return n[(int) Math.floorMod(Math.round(a / 45), 8)];
    }

    private static void say(ServerPlayer o, PartyBotEntity b, String s) {
        o.displayClientMessage(Component.empty().append(b.label()).append(": " + s).withStyle(ChatFormatting.AQUA), false);
        PartyBots.testLog(b, "life: " + s);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xenohelp")
                .then(Commands.literal("yes").then(Commands.argument("a", IntegerArgumentType.integer()).then(Commands.argument("b", IntegerArgumentType.integer())
                        .executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "a"), IntegerArgumentType.getInteger(c, "b"), true)))))
                .then(Commands.literal("no").then(Commands.argument("a", IntegerArgumentType.integer()).then(Commands.argument("b", IntegerArgumentType.integer())
                        .executes(c -> answer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "a"), IntegerArgumentType.getInteger(c, "b"), false))))));
    }
}

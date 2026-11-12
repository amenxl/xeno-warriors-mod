package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.Race;
import com.xenowarriors.economy.Money;
import com.xenowarriors.faction.Faction;
import com.xenowarriors.faction.Reputation;
import com.xenowarriors.gov.GovAgentEntity;
import com.xenowarriors.gov.GovEntities;
import com.xenowarriors.gov.GovFacilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.skill.ServerScheduler;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.toggle.Toggles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Government's paths for You characters (C2's modification program - Kyle's department): a Human can become a
 * Modified Human, an Artificial Demon or an Artificial Skylander. One path per character, for good. Taken at a Gov
 * Supply Terminal (its C2 Procedures page): a level, standing with the Government and Money - and for the demon program,
 * the Demon Program Card that recruiter "3602" hands out in Aurora City. The procedure itself: lie still while C2's
 * technicians work (the screen goes dark), wake up changed.
 * <ul>
 *   <li>Modified Human (Season 2): the Government mods for good - Speed Mod at Lv 20, Jump Boosters 30, Scanner Eye 40,
 *       Armor Plating 50, each switched on / off on the wheel - and Overclock.</li>
 *   <li>Artificial Demon (Season 2): the demon kit (wings, human form, Devil Dash, Devil Flame, Devil Unleashed), immune to
 *       the Aurora virus and poison; the townsfolk don't trust demons (Townsfolk -100).</li>
 *   <li>Artificial Skylander: tech wings like Technica's, flight, Wing Rush and a Lunar or Solar form.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouPaths {
    private YouPaths() {}

    public record Path(int id, String title, String desc, int level, Reputation.Tier need, long price, boolean card, int season) {}

    public static final List<Path> PATHS = List.of(
            new Path(YouSlot.PATH_MODIFIED, "Modified Human", "The Government mods, for good: Speed Mod (Lv 20), Jump Boosters (30), Scanner Eye (40), Armor Plating (50) - switched on / off on the wheel. New skill: Overclock.",
                    20, Reputation.Tier.FRIENDLY, 15000, false, 2),
            new Path(YouSlot.PATH_DEMON, "Artificial Demon", "The demon program: demon wings and flight, a human form, Devil Dash, Devil Flame and Devil Unleashed. Immune to the Aurora virus and poison. Townsfolk won't trust you (-100).",
                    30, Reputation.Tier.FRIENDLY, 25000, true, 2),
            new Path(YouSlot.PATH_SKYLANDER, "Artificial Skylander", "Made a Skylander, like Technica: tech wings and flight, Wing Rush, and a Lunar or Solar form.",
                    40, Reputation.Tier.HONORED, 60000, false, 1));

    public static Path path(int id) { for (Path p : PATHS) if (p.id == id) return p; return null; }

    /** A Modified Human's mods: the level each unlocks at (0: not a mod). */
    public static int modLevel(Toggles.Entry e) {
        return switch (e) { case MOD_SPEED -> 20; case MOD_JUMP -> 30; case MOD_SCANNER -> 40; case MOD_PLATING -> 50; default -> 0; };
    }

    private static boolean hasCard(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) if (s.is(com.xenowarriors.gov.GovItems.DEMON_CARD.get())) return true;
        return p.getOffhandItem().is(com.xenowarriors.gov.GovItems.DEMON_CARD.get());
    }

    /** Why this player can't take this path right now (null: they can). */
    public static String why(ServerPlayer p, Path path) {
        YouSlot s = You.slot(p);
        if (s == null) return "Only for your own characters";
        if (s.race != Race.HUMAN) return "Only Humans can be modified";
        if (s.path != YouSlot.PATH_NONE) return s.path == path.id ? "You took this path" : "You already took a path";
        if (!com.xenowarriors.character.Seasons.unlocked(p.server, path.season)) return "Season " + path.season + " content - locked";
        boolean c = p.isCreative();
        if (!c && com.xenowarriors.stats.Stats.characterLevel(p) < path.level) return "Needs Character Level " + path.level;
        if (!c && !Reputation.tier(p, Faction.GOV).atLeast(path.need)) return "Needs " + path.need.title + " standing with the Government";
        if (path.card && !c && !hasCard(p)) return "Needs a Demon Program Card (recruiter 3602, Aurora City)";
        if (!c && Money.get(p) < price(p, path)) return "Needs " + Money.fmt(price(p, path)) + " Money";
        return null;
    }

    /** With your Government standing's discount. */
    public static long price(ServerPlayer p, Path path) { return path.price * (100 - com.xenowarriors.gov.GovShop.discount(p)) / 100; }

    // ------------------------------------------------------------------ the terminal's C2 page

    public static void open(ServerPlayer p) {
        List<com.xenowarriors.network.C2Packet.Row> rows = new ArrayList<>();
        for (Path path : PATHS) {
            String why = why(p, path);
            rows.add(new com.xenowarriors.network.C2Packet.Row(path.id, path.title, path.desc, path.level, path.need.title, price(p, path), path.card, why == null ? "" : why));
        }
        YouSlot s = You.slot(p);
        String who = s == null ? "Play one of your own characters to see your options." : s.path != YouSlot.PATH_NONE ? s.name + " is a " + s.shown().title + "." : s.race != Race.HUMAN ? s.name + " is a " + s.shown().title + " - C2 only works on Humans." : s.name + " - Human. Eligible for one procedure.";
        ModNetwork.sendToPlayer(new com.xenowarriors.network.C2Packet(rows, Money.get(p), who, -1, 0), p);
    }

    /** The player signed the forms (the confirm screen): the procedure. form: a Skylander's Lunar (1) or Solar (2). */
    public static void request(ServerPlayer p, int id, int form) {
        Path path = path(id);
        if (path == null || !com.xenowarriors.gov.GovShop.atTerminalForC2(p)) return;
        String why = why(p, path);
        if (why != null) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true); open(p); return; }
        YouSlot s = You.slot(p);
        if (!p.isCreative()) {
            if (!Money.take(p, price(p, path))) return;
            if (path.card) for (ItemStack it : p.getInventory().items) if (it.is(com.xenowarriors.gov.GovItems.DEMON_CARD.get())) { it.shrink(1); break; }
        }
        p.closeContainer();
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.C2Packet(List.of(), 0, "", -2, 0), p);   // (closes the screen)
        procedure(p, s, path, form == YouSlot.FORM_SOLAR ? YouSlot.FORM_SOLAR : YouSlot.FORM_LUNAR);
    }

    /** (Tests and the procedure) the change itself, after the lights go out. */
    static void procedure(ServerPlayer p, YouSlot s, Path path, int form) {
        ServerLevel l = p.serverLevel();
        int len = 100;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, len + 20, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, len, 6, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, len, 0, false, false));
        String[] lines = {"C2 technician: \"Lie still. This won't hurt... much.\"", "C2 technician: \"Beginning the " + path.title + " procedure.\"", "...", "C2 technician: \"Procedure complete. Welcome to the program.\""};
        for (int i = 0; i < lines.length; i++) {
            int k = i;
            ServerScheduler.later(i * 30, () -> {
                if (p.hasDisconnected()) return;
                p.displayClientMessage(Component.literal(lines[k]).withStyle(k == 2 ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA), false);
                l.playSound(null, p.blockPosition(), k == 3 ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.4F);
            });
        }
        ServerScheduler.later(len, () -> { if (!p.hasDisconnected() && You.slot(p) == s) finish(p, s, path, form); });
    }

    private static void finish(ServerPlayer p, YouSlot s, Path path, int form) {
        s.path = path.id;
        if (path.id == YouSlot.PATH_SKYLANDER) s.form = form;
        s.humanForm = false;
        Reputation.add(p, Faction.GOV, 60);                                      // (one of theirs now)
        if (path.id == YouSlot.PATH_DEMON) Reputation.add(p, Faction.TOWN, -100); // ("demons are soulless monsters")
        changed(p);
        ServerLevel l = p.serverLevel();
        var particle = path.id == YouSlot.PATH_DEMON ? ParticleTypes.SOUL_FIRE_FLAME : path.id == YouSlot.PATH_SKYLANDER ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK;
        l.sendParticles(particle, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1.0, 0.6, 0.06);
        p.sendSystemMessage(Component.literal(s.name + " is now a" + (path.title.startsWith("A") ? "n " : " ") + path.title + ".").withStyle(st -> st.withColor(s.shown().color).withBold(true)));
        p.sendSystemMessage(Component.literal(switch (path.id) {
            case YouSlot.PATH_MODIFIED -> "Your mods are on the toggle wheel (hold the transform key). New skill: Overclock.";
            case YouSlot.PATH_DEMON -> "Demon wings: fly like any demon. The transform key swaps human / demon form; sneak + the key: Devil Unleashed. New skills: Devil Dash, Devil Flame.";
            default -> "Tech wings: you can fly (H puts them away). New skills: Wing Rush and your " + (form == YouSlot.FORM_SOLAR ? "Solar" : "Lunar") + " form.";
        }).withStyle(ChatFormatting.GRAY));
        procedures++;
    }

    /** After a change: stats, skills, the wheel and everyone's view of them. */
    static void changed(ServerPlayer p) {
        YouRaces.apply(p);
        com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
        com.xenowarriors.skill.SkillManager.syncLoadout(p);
        Toggles.send(p);
    }

    /** (Tests) procedures done. */
    public static int procedures;

    /** (Tests) does the procedure right away (no checks, no wait). */
    public static void finishForTest(ServerPlayer p, int id, int form) { YouSlot s = You.slot(p); if (s != null) finish(p, s, path(id), form); }

    // ------------------------------------------------------------------ a Modified Human's mods, for good

    private static final UUID PLATING_ID = UUID.fromString("3c9a1f6e-2b77-4e0a-9f31-c2d1a0b7e5a1");

    public static boolean modOn(ServerPlayer p, Toggles.Entry e) {
        YouSlot s = You.slot(p);
        return s != null && s.path == YouSlot.PATH_MODIFIED && Toggles.enabled(p, e) && Toggles.unavailable(p, e) == null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 20 != 3) return;
        var armor = p.getAttribute(Attributes.ARMOR);
        boolean plating = modOn(p, Toggles.Entry.MOD_PLATING);
        if (armor != null) {
            boolean has = armor.getModifier(PLATING_ID) != null;
            if (plating && !has) armor.addTransientModifier(new AttributeModifier(PLATING_ID, "Armor Plating", 6, AttributeModifier.Operation.ADDITION));
            else if (!plating && has) armor.removeModifier(PLATING_ID);
        }
        YouSlot s = You.slot(p);
        if (s == null || s.path != YouSlot.PATH_MODIFIED) return;
        long now = p.level().getGameTime();
        if (modOn(p, Toggles.Entry.MOD_SPEED)) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 1, true, false, true));
        if (modOn(p, Toggles.Entry.MOD_JUMP)) { p.addEffect(new MobEffectInstance(MobEffects.JUMP, 50, 2, true, false, true)); p.getPersistentData().putLong("xw_jumpmod_until", now + 50); }
        if (modOn(p, Toggles.Entry.MOD_SCANNER)) {
            if (!p.hasEffect(MobEffects.NIGHT_VISION) || p.getEffect(MobEffects.NIGHT_VISION).getDuration() < 260) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
            p.getPersistentData().putLong("xw_scanner_until", now + 50);
        }
        if (plating) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 0, true, false, true));
    }

    /** Overclock (Modified Human): push your mods past the limit - fast and strong for 8 seconds, at the cost of some health. */
    public static final class Overclock implements Skill {
        @Override public String getId() { return "you_overclock"; }
        @Override public int getCooldownTicks() { return 40 * 20; }
        @Override public int getKarmaCost() { return 30; }
        @Override public boolean use(ServerPlayer p) {
            float cost = p.getMaxHealth() * 0.15F;
            p.setHealth(Math.max(1F, p.getHealth() - cost));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 2));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 1));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 160, 1));
            p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 2F);
            p.displayClientMessage(Component.literal("Overclock - output past the limit!").withStyle(ChatFormatting.AQUA), true);
            return true;
        }
    }

    // ------------------------------------------------------------------ an Artificial Demon: immune to sickness

    /** Immune to the Aurora virus and poison: made demons (and Devils). */
    public static boolean sickImmune(Player p) {
        YouSlot s = You.slot(p);
        return s != null && (s.path == YouSlot.PATH_DEMON || s.devil);
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable e) {
        if (e.getEntity() instanceof Player p && !p.level().isClientSide && e.getEffectInstance().getEffect() == MobEffects.POISON && sickImmune(p)) e.setResult(Event.Result.DENY);
    }

    // ------------------------------------------------------------------ recruiter "3602" (Aurora City)

    public static final String RECRUITER = "xw_recruiter";

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || ev.getServer().getTickCount() % 400 != 211) return;
        for (ServerPlayer p : ev.getServer().getPlayerList().getPlayers()) {
            if (!(p.level() instanceof ServerLevel l) || !com.xenowarriors.recarnet.Recarnet.inAurora(l, p.blockPosition()) || p.isSpectator()) continue;
            var near = l.getEntitiesOfClass(GovAgentEntity.class, p.getBoundingBox().inflate(96), a -> a.getTags().contains(RECRUITER));
            for (int i = 1; i < near.size(); i++) near.get(i).discard();          // (just the one about)
            if (!near.isEmpty() || l.random.nextFloat() > 0.35F) continue;
            spawnRecruiter(l, p.blockPosition());
        }
    }

    /** A recruiter on the street somewhere near (null: no spot). */
    public static GovAgentEntity spawnRecruiter(ServerLevel l, BlockPos near) {
        for (int tries = 0; tries < 8; tries++) {
            double a = l.random.nextDouble() * Math.PI * 2, d = 12 + l.random.nextInt(20);
            BlockPos at = near.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
            if (!l.isLoaded(at)) continue;
            BlockPos top = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
            if (top.getY() >= com.xenowarriors.recarnet.RoyalCapitalFeature.FLOOR_Y || !l.getBlockState(top.below()).isSolidRender(l, top.below()) || !l.isEmptyBlock(top) || !l.isEmptyBlock(top.above())) continue;
            GovAgentEntity r = GovEntities.GOV_AGENT.get().create(l);
            if (r == null) return null;
            r.moveTo(top.getX() + .5, top.getY(), top.getZ() + .5, l.random.nextFloat() * 360F, 0);
            r.setup(false, 1, top);
            r.addTag(RECRUITER);
            r.setCustomName(Component.literal("Recruiter 3602").withStyle(ChatFormatting.AQUA));
            r.setCustomNameVisible(true);
            l.addFreshEntity(r);
            return r;
        }
        return null;
    }

    private static final String[] PITCH = {
            "Recruiter 3602: \"The Government's demon program - five years of body training, and you'll never be sick a day again.\"",
            "Recruiter 3602: \"Demons are immune to all sickness. Think about it.\"",
            "Recruiter 3602: \"Power, protection, a purpose. Sign up at any Supply Terminal.\""};

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND || !e.getTarget().getTags().contains(RECRUITER)) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (GovFacilities.wanted(p) || Reputation.hostile(p, Faction.GOV)) { p.sendSystemMessage(Component.literal("Recruiter 3602: \"I don't talk to criminals.\"").withStyle(ChatFormatting.GRAY)); return; }
        YouSlot s = You.slot(p);
        if (s != null && s.race == Race.HUMAN && s.path == YouSlot.PATH_NONE && !hasCard(p)) {
            ItemStack card = new ItemStack(com.xenowarriors.gov.GovItems.DEMON_CARD.get());
            if (!p.addItem(card)) p.drop(card, false);
            p.sendSystemMessage(Component.literal("Recruiter 3602: \"You look like you could use some power. Take this - bring it to a Supply Terminal (C2 Procedures).\"").withStyle(ChatFormatting.AQUA));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1F, 1F);
            cardsGiven++;
            return;
        }
        p.sendSystemMessage(Component.literal(PITCH[p.getRandom().nextInt(PITCH.length)]).withStyle(ChatFormatting.AQUA));
    }

    /** (Tests) Demon Program Cards handed out. */
    public static int cardsGiven;
}

package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.network.KnockoutPacket;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * PvP battles: right-click a player and request a battle; once they accept, a 3-2-1 countdown and then fight.
 * Nobody dies: dropping to 0 health knocks you out - you lie there dazed for 5 seconds (can't move, can't be
 * hit) and then get back up with half your health. The battle ends when someone is knocked out, walks off
 * (more than 48 blocks apart), logs out, or after 5 minutes.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Duels {
    private static final int REQUEST_TICKS = 30 * 20, COUNTDOWN = 60, MAX_TICKS = 5 * 60 * 20, KO_TICKS = 100;
    private static final double MAX_DISTANCE = 48;

    private static final class Duel {
        final UUID a, b;
        int ticks;

        Duel(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        UUID other(UUID id) {
            return id.equals(a) ? b : a;
        }

        boolean fighting() {
            return ticks >= COUNTDOWN;
        }
    }

    private record Request(UUID from, long expires) {}

    private static final Map<UUID, Duel> ACTIVE = new HashMap<>();
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final Map<UUID, Integer> KNOCKED = new HashMap<>();

    // ---- downed (in a party, beaten by something other than a battle): 30 s for a party member to revive you
    /** Downed lasts until someone revives you (this just keeps the effects/client state on); alone = whole party down. */
    private static final int DOWNED_TICKS = 1_000_000, ALONE_TICKS = 10 * 20, REVIVE_TICKS = ReviveCircle.TICKS;
    private static final Map<UUID, net.minecraft.world.phys.Vec3> DOWNED_AT = new HashMap<>();
    private static final Map<UUID, Integer> DOWNED = new HashMap<>();
    private static final Map<UUID, Integer> REVIVING = new HashMap<>();    // downed player -> revive progress

    private Duels() {}

    private static MutableComponent msg(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    public static boolean dueling(Player a, Player b) {
        Duel d = ACTIVE.get(a.getUUID());
        return d != null && d.other(a.getUUID()).equals(b.getUUID()) && d.fighting();
    }

    public static boolean inDuel(Player p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    /** Knocked out in a battle, or downed waiting for a revive. */
    public static boolean isKnockedOut(Player p) {
        return KNOCKED.containsKey(p.getUUID()) || DOWNED.containsKey(p.getUUID());
    }

    /** A party, or bot teammates of their own: someone who could revive them. */
    public static boolean hasTeam(ServerPlayer p) {
        return Parties.inParty(p) || !com.xenowarriors.bot.PartyBots.bots(p).isEmpty();
    }

    public static boolean isDowned(Player p) {
        return DOWNED.containsKey(p.getUUID());
    }

    private static void disable(ServerPlayer p, int ticks) {
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks + 5, 10, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks + 5, 250, false, false));     // can't jump
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks + 5, 10, false, false));
        p.clearFire();
        if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
    }

    private static void enable(ServerPlayer p) {
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.JUMP);
        p.removeEffect(MobEffects.WEAKNESS);
    }

    /** Down instead of dead: 30 seconds for a party member to come and revive them. */
    private static void down(ServerPlayer p) {
        p.setHealth(1F);
        DOWNED.put(p.getUUID(), ALONE_TICKS);
        DOWNED_AT.put(p.getUUID(), p.position());
        REVIVING.remove(p.getUUID());
        disable(p, DOWNED_TICKS);
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 14, 0.4, 0.3, 0.4, 0.03);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1F, 0.6F);
        ModNetwork.sendToTrackingAndSelf(new KnockoutPacket(p.getUUID(), DOWNED_TICKS), p);
        title(p, "DOWNED", ChatFormatting.RED, "Wait for a party member to revive you");
        p.sendSystemMessage(msg("You're down until a teammate stands in your circle for 2 seconds. Type /xeno giveup to respawn instead."));
        if (p.getServer() != null) {
            for (ServerPlayer other : p.getServer().getPlayerList().getPlayers()) {
                if (other != p && Parties.sameParty(p, other)) {
                    other.sendSystemMessage(msg(p.getName().getString() + " is down! Stand in their circle for 2 seconds to revive them."));
                }
            }
        }
    }

    /** Kaeru's Fox Heal: revives a downed teammate straight away. */
    public static void foxRevive(ServerPlayer p, ServerPlayer by) { if (DOWNED.containsKey(p.getUUID())) revive(p, by); }

    private static void revive(ServerPlayer p, LivingEntity by) {
        DOWNED.remove(p.getUUID());
        DOWNED_AT.remove(p.getUUID());
        REVIVING.remove(p.getUUID());
        enable(p);
        p.setHealth(Math.max(6F, p.getMaxHealth() * 0.4F));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2, false, true));
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        ModNetwork.sendToTrackingAndSelf(new KnockoutPacket(p.getUUID(), 0), p);
        title(p, "REVIVED", ChatFormatting.GREEN, "by " + ReviveCircle.name(by));
        if (by instanceof ServerPlayer bp) bp.displayClientMessage(msg("You revived " + p.getName().getString() + "!"), true);
    }

    /** Ran out of time (or gave up): they die for real. */
    public static void bleedOut(ServerPlayer p) {
        if (!DOWNED.containsKey(p.getUUID())) return;
        DOWNED.remove(p.getUUID());
        DOWNED_AT.remove(p.getUUID());
        REVIVING.remove(p.getUUID());
        enable(p);
        ModNetwork.sendToTrackingAndSelf(new KnockoutPacket(p.getUUID(), 0), p);
        p.kill();
    }

    private static void title(ServerPlayer p, String title, ChatFormatting color, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 8));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub == null ? "" : sub)));
    }

    // ---------------------------------------------------------------- requests

    public static void request(ServerPlayer from, ServerPlayer to) {
        if (from == to) return;
        if (inDuel(from) || inDuel(to)) {
            from.sendSystemMessage(msg((inDuel(from) ? "You're" : to.getName().getString() + " is") + " already in a battle."));
            return;
        }
        if (from.isCreative() || to.isCreative()) {
            from.sendSystemMessage(msg("Battles are for survival mode."));
            return;
        }
        REQUESTS.put(to.getUUID(), new Request(from.getUUID(), from.level().getGameTime() + REQUEST_TICKS));
        from.sendSystemMessage(msg("You challenged " + to.getName().getString() + " to a PvP battle!"));
        to.sendSystemMessage(msg(from.getName().getString() + " challenges you to a PvP battle! ")
                .append(Parties.button("[Accept]", "/xeno duel accept", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(Parties.button("[Decline]", "/xeno duel decline", ChatFormatting.RED)));
        to.level().playSound(null, to.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
        ModNetwork.sendToPlayer(new com.xenowarriors.network.RequestPopupPacket("Battle Challenge",
                from.getName().getString() + " challenges you to a PvP battle!", "/xeno duel accept", "/xeno duel decline", REQUEST_TICKS, 0xFFFF6A5A), to);
    }

    public static void accept(ServerPlayer p) {
        Request req = REQUESTS.remove(p.getUUID());
        MinecraftServer server = p.getServer();
        ServerPlayer from = req == null || server == null ? null : server.getPlayerList().getPlayer(req.from);
        if (req == null || from == null || p.level().getGameTime() > req.expires) {
            p.sendSystemMessage(msg("You don't have a battle request (or it ran out)."));
            return;
        }
        if (inDuel(from) || inDuel(p) || from.level() != p.level() || from.distanceTo(p) > MAX_DISTANCE) {
            p.sendSystemMessage(msg("That battle can't start right now."));
            return;
        }
        Duel d = new Duel(from.getUUID(), p.getUUID());
        ACTIVE.put(d.a, d);
        ACTIVE.put(d.b, d);
        for (ServerPlayer s : new ServerPlayer[]{from, p}) {
            s.sendSystemMessage(msg("Battle: " + from.getName().getString() + " vs " + p.getName().getString() + "!"));
        }
    }

    public static void decline(ServerPlayer p) {
        Request req = REQUESTS.remove(p.getUUID());
        if (req == null) return;
        p.sendSystemMessage(msg("Battle declined."));
        ServerPlayer from = p.getServer() == null ? null : p.getServer().getPlayerList().getPlayer(req.from);
        if (from != null) from.sendSystemMessage(msg(p.getName().getString() + " declined your battle."));
    }

    public static void forfeit(ServerPlayer p) {
        Duel d = ACTIVE.get(p.getUUID());
        if (d == null) return;
        end(p.getServer(), d, d.other(p.getUUID()), p.getName().getString() + " gave up.");
    }

    private static void end(MinecraftServer server, Duel d, UUID winner, String why) {
        ACTIVE.remove(d.a);
        ACTIVE.remove(d.b);
        if (server == null) return;
        for (UUID id : new UUID[]{d.a, d.b}) {
            ServerPlayer s = server.getPlayerList().getPlayer(id);
            if (s == null) continue;
            if (winner == null) {
                title(s, "DRAW", ChatFormatting.YELLOW, why);
            } else if (winner.equals(id)) {
                title(s, "VICTORY!", ChatFormatting.GOLD, why);
                s.level().playSound(null, s.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1F);
            } else {
                title(s, "DEFEAT", ChatFormatting.RED, why);
            }
        }
    }

    // ---------------------------------------------------------------- knock-outs instead of dying

    private static void knockOut(ServerPlayer p) {
        p.setHealth(1F);
        KNOCKED.put(p.getUUID(), KO_TICKS);
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KO_TICKS + 5, 10, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, KO_TICKS + 5, 250, false, false));     // can't jump
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, KO_TICKS + 5, 10, false, false));
        p.clearFire();
        if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 14, 0.4, 0.3, 0.4, 0.03);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1F, 0.6F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.6F);
        ModNetwork.sendToTrackingAndSelf(new KnockoutPacket(p.getUUID(), KO_TICKS), p);
        Duel d = ACTIVE.get(p.getUUID());
        if (d != null) {
            ServerPlayer winner = p.getServer() == null ? null : p.getServer().getPlayerList().getPlayer(d.other(p.getUUID()));
            end(p.getServer(), d, d.other(p.getUUID()), p.getName().getString() + " was knocked out!");
            if (winner != null) title(p, "KNOCKED OUT", ChatFormatting.RED, winner.getName().getString() + " wins the battle");
            if (winner != null && Parties.sameParty(winner, p)) {          // sparring a party member: big XP (more vs. someone stronger)
                com.xenowarriors.bot.PartyBots.sparReward(winner, com.xenowarriors.stats.Stats.characterLevel(p), true);
                com.xenowarriors.bot.PartyBots.sparReward(p, com.xenowarriors.stats.Stats.characterLevel(winner), false);
            }
        }
    }

    private static void getUp(ServerPlayer p) {
        KNOCKED.remove(p.getUUID());
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.JUMP);
        p.removeEffect(MobEffects.WEAKNESS);
        p.setHealth(Math.max(6F, p.getMaxHealth() * 0.5F));
        p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.5, 0.4, 0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1F, 1F);
        ModNetwork.sendToTrackingAndSelf(new KnockoutPacket(p.getUUID(), 0), p);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player att && isKnockedOut(att)) {
            event.setCanceled(true);           // someone who's down can't fight
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (isKnockedOut(p)) {
            event.setCanceled(true);           // no hitting someone who's down
            return;
        }
        // during the countdown the two can't hurt each other yet
        Duel d = ACTIVE.get(p.getUUID());
        if (d != null && !d.fighting() && event.getSource().getEntity() instanceof Player att && d.other(p.getUUID()).equals(att.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static boolean unstoppable(net.minecraft.world.damagesource.DamageSource s) {
        return s.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) || s.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD);
    }

    private static boolean hasTotem(Player p) {
        return p.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p) || event.getAmount() < p.getHealth()) return;
        if (com.xenowarriors.journey.Journey.inRun(p) || com.xenowarriors.tower.Tower.is(p.level()) || com.xenowarriors.vr.Vr.inVr(p)) return;   // (the modes handle their own deaths)
        if (event.getSource().getEntity() instanceof ServerPlayer k && com.xenowarriors.character.ModCapabilities.getType(k) == com.xenowarriors.character.CharacterType.MIKIKO && k != p) return;   // (a Mikiko's kill: the blade seals them)
        Duel d = ACTIVE.get(p.getUUID());
        if (d != null && d.fighting()) {
            event.setCanceled(true);
            knockOut(p);
            return;
        }
        // in a party (or with bot teammates): go down instead of dying, so someone can revive you
        if (hasTeam(p) && !isKnockedOut(p) && !unstoppable(event.getSource()) && !hasTotem(p) && !p.isCreative()) {
            event.setCanceled(true);
            down(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (com.xenowarriors.journey.Journey.inRun(p) || com.xenowarriors.tower.Tower.is(p.level()) || com.xenowarriors.vr.Vr.inVr(p)) return;   // (the modes handle their own knock-outs)
        if (ACTIVE.get(p.getUUID()) != null && ACTIVE.get(p.getUUID()).fighting()) {
            event.setCanceled(true);
            knockOut(p);
        } else if (hasTeam(p) && !isKnockedOut(p) && !unstoppable(event.getSource()) && !hasTotem(p)) {
            event.setCanceled(true);
            down(p);
        }
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        REQUESTS.values().removeIf(r -> now > r.expires);
        // knock-outs wearing off
        Iterator<Map.Entry<UUID, Integer>> it = KNOCKED.entrySet().iterator();
        java.util.List<ServerPlayer> rising = new java.util.ArrayList<>();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            int left = e.getValue() - 1;
            e.setValue(left);
            if (left % 10 == 0) {
                p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.4, p.getZ(), 4, 0.4, 0.1, 0.4, 0.02);
            }
            if (left <= 0) rising.add(p);
        }
        for (ServerPlayer p : rising) getUp(p);
        tickDowned(server);
        // battles: countdown, distance, time limit
        java.util.Set<Duel> seen = new java.util.HashSet<>();
        for (Duel d : new java.util.ArrayList<>(ACTIVE.values())) {
            if (!seen.add(d)) continue;
            ServerPlayer a = server.getPlayerList().getPlayer(d.a), b = server.getPlayerList().getPlayer(d.b);
            if (a == null || b == null) {
                end(server, d, a == null ? d.b : d.a, "The other player left.");
                continue;
            }
            if (d.ticks <= COUNTDOWN && (COUNTDOWN - d.ticks) % 20 == 0) {
                int n = (COUNTDOWN - d.ticks) / 20;
                for (ServerPlayer s : new ServerPlayer[]{a, b}) {
                    if (n > 0) {
                        title(s, String.valueOf(n), ChatFormatting.YELLOW, "Get ready...");
                        s.level().playSound(null, s.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 1F, 1F);
                    } else {
                        title(s, "FIGHT!", ChatFormatting.RED, s == a ? "vs " + b.getName().getString() : "vs " + a.getName().getString());
                        s.level().playSound(null, s.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1F, 1.2F);
                    }
                }
            }
            d.ticks++;
            if (a.level() != b.level() || a.distanceTo(b) > MAX_DISTANCE) {
                end(server, d, null, "You moved too far apart.");
            } else if (d.ticks > MAX_TICKS + COUNTDOWN) {
                end(server, d, null, "Time's up!");
            }
        }
    }

    /** Downed players' timers, party members reviving them, and mobs leaving them alone. */
    private static void tickDowned(MinecraftServer server) {
        java.util.List<ServerPlayer> dying = new java.util.ArrayList<>();
        java.util.List<LivingEntity[]> revived = new java.util.ArrayList<>();
        for (var e : DOWNED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            // can't move: held where they fell (falling straight down is fine)
            net.minecraft.world.phys.Vec3 at = DOWNED_AT.get(p.getUUID());
            if (at != null && p.position().subtract(at).horizontalDistanceSqr() > 0.6 * 0.6) {
                p.connection.teleport(at.x, Math.min(p.getY(), at.y + 0.5), at.z, p.getYRot(), p.getXRot());
            } else if (at != null && p.getY() < at.y) {
                DOWNED_AT.put(p.getUUID(), new net.minecraft.world.phys.Vec3(at.x, p.getY(), at.z));
            }
            // nobody left who could come for them (the whole party's down or offline): they don't get back up
            boolean someone = false;
            for (ServerPlayer other : server.getPlayerList().getPlayers())
                if (other != p && other.isAlive() && !isKnockedOut(other) && Parties.sameParty(p, other)) { someone = true; break; }
            if (!someone) for (ServerPlayer m : Parties.members(p)) for (var b : com.xenowarriors.bot.PartyBots.bots(m)) if (!b.downed() && !b.journey()) { someone = true; break; }
            int left = someone ? ALONE_TICKS : e.getValue() - 1;
            e.setValue(left);
            if (left <= 0) {
                dying.add(p);
                continue;
            }
            // a teammate (player or bot) standing in their circle fills the revive bar
            LivingEntity helper = ReviveCircle.helperFor(p);
            int progress = helper == null ? 0 : REVIVING.getOrDefault(p.getUUID(), 0) + 1;
            REVIVING.put(p.getUUID(), progress);
            if (p.tickCount % 3 == 0) ReviveCircle.draw(p.serverLevel(), p.position(), progress);
            if (helper != null && progress > 0) {
                ReviveCircle.show(helper, p, progress);
                if (progress >= REVIVE_TICKS) {
                    revived.add(new LivingEntity[]{p, helper});
                    continue;
                }
            }
            if (left % 10 == 0) {
                p.displayClientMessage(Component.literal(someone ? "DOWNED - waiting for a party member to revive you (/xeno giveup to respawn)"
                        : "DOWNED - your whole party is down! " + (left / 20) + "s...").withStyle(ChatFormatting.RED), true);
                // mobs lose interest in someone who's down
                for (net.minecraft.world.entity.Mob mob : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                        p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) {
                    mob.setTarget(null);
                }
            }
        }
        for (LivingEntity[] r : revived) revive((ServerPlayer) r[0], r[1]);
        for (ServerPlayer p : dying) {
            p.sendSystemMessage(msg("Nobody was left to revive you..."));
            bleedOut(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (DOWNED.containsKey(id) && event.getEntity() instanceof ServerPlayer sp) bleedOut(sp);
        KNOCKED.remove(id);
        REQUESTS.remove(id);
        Duel d = ACTIVE.get(id);
        if (d != null) end(event.getEntity().getServer(), d, d.other(id), event.getEntity().getName().getString() + " left.");
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer && isKnockedOut(target)) {
            int left = KNOCKED.containsKey(target.getUUID()) ? KNOCKED.get(target.getUUID()) : DOWNED_TICKS;
            ModNetwork.sendToPlayer(new KnockoutPacket(target.getUUID(), left), viewer);
        }
    }

    // ---------------------------------------------------------------- downed: no attacking, using or breaking

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (isKnockedOut(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (isKnockedOut(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (isKnockedOut(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseEntity(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (isKnockedOut(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHitBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (isKnockedOut(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (isKnockedOut(event.getPlayer())) event.setCanceled(true);
    }

    /** /xeno giveup: stop waiting and respawn. */
    public static boolean giveUp(ServerPlayer p) {
        if (!isDowned(p)) return false;
        p.sendSystemMessage(msg("You gave up."));
        bleedOut(p);
        return true;
    }
}

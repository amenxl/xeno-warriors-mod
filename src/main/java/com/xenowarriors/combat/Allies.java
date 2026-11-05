package com.xenowarriors.combat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.party.Duels;
import com.xenowarriors.party.Parties;
import com.xenowarriors.soul.SoulEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Who is on whose side, in one place. Every skill, ultimate, KA, Xeno Blast and area effect asks
 * {@link #isAlly} before it hurts, pulls, pushes, freezes or debuffs anything.
 *
 * A "side" is a player: the player themself, their party, everyone's pets (foxes, wing pets, tamed animals,
 * Ultima's robots), their party bots, their Soul Splitter souls, and anything those threw (projectiles, skill
 * entities). Duels and bot sparring still let the two fighters hit each other.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Allies {
    private Allies() {}

    /** The thing really behind {@code e}: a projectile's or skill entity's owner, a bot's stand-in's bot. */
    public static Entity actor(Entity e) {
        for (int i = 0; i < 4 && e != null; i++) {
            PartyBotEntity bot = e instanceof Player ? PartyBots.botOf(e) : null;
            if (bot != null) return bot;
            Entity up = null;
            if (e instanceof Projectile pr) up = pr.getOwner();
            else if (e instanceof TraceableEntity t && !(e instanceof LivingEntity)) up = t.getOwner();
            if (up == null || up == e) return e;
            e = up;
        }
        return e;
    }

    /** The player whose side this entity is on (null = nobody's: a wild mob, a monster). */
    public static UUID side(Entity e) {
        e = actor(e);
        if (e == null) return null;
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof PartyBotEntity b) return b.ownerId();
        if (e instanceof SoulEntity s) return s.ownerId();
        return Parties.petOwner(e);
    }

    /** Same side: the same player, or players in the same party. */
    public static boolean sameSide(UUID a, UUID b) {
        UUID ra = com.xenowarriors.soul.SoulSkills.ownerOfStandIn(a), rb = com.xenowarriors.soul.SoulSkills.ownerOfStandIn(b);   // (a soul's skill stand-in: its owner's side)
        if (ra == null) ra = PartyBots.ownerOfStandIn(a);               // (a party bot's stand-in: its owner's side)
        if (rb == null) rb = PartyBots.ownerOfStandIn(b);
        if (ra != null) a = ra;
        if (rb != null) b = rb;
        return a != null && b != null && (a.equals(b) || Parties.sameParty(a, b));
    }

    /**
     * Whether {@code target} is on {@code caster}'s side and so must be left alone by their attacks (it's true for the
     * caster themself too). Opponents in a duel, and a bot sparring with its owner, are not allies.
     */
    public static boolean isAlly(Entity caster, Entity target) {
        if (caster == null || target == null) return false;
        Entity a = actor(caster), b = actor(target);
        if (a == b) return true;
        UUID sa = side(a), sb = side(b);
        if (!sameSide(sa, sb)) return false;
        if (opponents(a, b)) return false;
        return true;
    }

    /** {@link #isAlly} for a skill entity that only remembers its caster's UUID. */
    public static boolean isAlly(net.minecraft.world.level.Level level, UUID casterId, Entity target) {
        if (casterId == null || target == null) return false;
        if (casterId.equals(target.getUUID())) return true;
        Entity caster = level.getPlayerByUUID(casterId);
        if (caster == null && level instanceof ServerLevel s) caster = s.getEntity(casterId);
        if (caster != null) return isAlly(caster, target);
        return sameSide(casterId, side(target));
    }

    /** Two on the same side who are fighting each other on purpose right now (a duel, or sparring). */
    public static boolean opponents(Entity a, Entity b) {
        a = actor(a); b = actor(b);
        if (a instanceof Player pa && b instanceof Player pb) return Duels.dueling(pa, pb);
        if (a instanceof PartyBotEntity bot && b instanceof Player p) return bot.sparring() && p.getUUID().equals(bot.ownerId());
        if (b instanceof PartyBotEntity bot && a instanceof Player p) return bot.sparring() && p.getUUID().equals(bot.ownerId());
        // two players' things in a duel (their souls, pets): fair game too
        UUID sa = side(a), sb = side(b);
        if (sa != null && sb != null && !sa.equals(sb) && a.level() instanceof ServerLevel l
                && l.getPlayerByUUID(sa) != null && l.getPlayerByUUID(sb) != null) return Duels.dueling(l.getPlayerByUUID(sa), l.getPlayerByUUID(sb));
        return false;
    }

    /** Whether this hit came from one of the mod's skills (a skill damage type, a skill entity, or a skill's own task). */
    public static boolean skillHit(net.minecraft.world.damagesource.DamageSource src) {
        if (SkillContext.active()) return true;
        var key = src.typeHolder().unwrapKey().orElse(null);
        if (key != null && key.location().getNamespace().equals(XenoWarriors.MOD_ID)) return true;
        Entity d = src.getDirectEntity();
        if (d == null || d == src.getEntity() || d instanceof LivingEntity) return false;
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(d.getType());
        return id != null && id.getNamespace().equals(XenoWarriors.MOD_ID);
    }

    /** A skill's explosion (or one set off inside a skill) pushes and hurts only the other side. */
    @SubscribeEvent
    public static void onDetonate(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        Entity src = event.getExplosion().getIndirectSourceEntity();
        if (src == null) src = event.getExplosion().getExploder();
        if (src == null) return;
        Entity ex = event.getExplosion().getExploder();
        boolean skill = SkillContext.active() || (ex != null && !(ex instanceof LivingEntity)
                && XenoWarriors.MOD_ID.equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(ex.getType()).getNamespace()));
        if (!skill) return;
        Entity caster = src;
        event.getAffectedEntities().removeIf(e -> isAlly(caster, e));
    }

    /**
     * The safety net: nothing a side does hurts its own - its skills and their entities, its pets, bots and souls.
     * (Plain blows between players of a party are already stopped by Parties; this covers everything else.)
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        Entity src = event.getSource().getEntity();
        if (src == null || event.getEntity().level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        if (actor(src) == victim) {
            // your own skill doesn't hurt you (a fall, a vanilla explosion you walked into, a thorns bounce still do)
            if (skillHit(event.getSource()) && event.getSource().getDirectEntity() != victim) event.setCanceled(true);
            return;
        }
        if (isAlly(src, victim)) event.setCanceled(true);
    }
}

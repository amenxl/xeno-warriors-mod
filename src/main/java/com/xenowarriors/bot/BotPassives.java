package com.xenowarriors.bot;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.skill.Skill;
import com.xenowarriors.skill.SkillPool;
import com.xenowarriors.toggle.Toggles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Bots get their character's toggles and passives, set the way you set them for that character (Character
 * Customization > Passives, or the toggle wheel while you played them):
 * <ul>
 *   <li>Mikiko - True Empathy (when on): reads the next hit coming at him - dodged and hit back. Overused (three times
 *       inside 90 s) it strains him and switches itself off for a couple of minutes. Fox King: only when it's enabled.</li>
 *   <li>Ultima - Attack Prediction (when on): the same, reading two hits (Data from its own bar). Living Weapon: in a
 *       real fight, now and then - its hits sweep everyone round the one it hits.</li>
 *   <li>Rhalix - Demon's Remnants: only when it's enabled.</li>
 *   <li>Max - Lunar Mind: at night, stronger, faster and healing.</li>
 *   <li>Technica - Gravity Boots (no fall damage, {@value #BOOTS_S} s to recharge), Visor Scan on each new enemy (it
 *       glows; you hear its health and armor), Rescue Platform when two or more teammates are down near her.</li>
 *   <li>Kaeru - Scent Tracking: picks up a new enemy's scent (it glows a while, through walls).</li>
 * </ul>
 * And their right+left click skill (the one you picked for that character): after a landed hit now and then, or in a
 * fight from a distance - it drops a block, with a cooldown a fifth shorter, like yours.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BotPassives {
    private BotPassives() {}

    public static final int BOOTS_S = 20, VISOR_S = 8, RESCUE_S = 180, EMPATHY_S = 25, PREDICT_S = 20, WEAPON_S = 25, SCENT_S = 30, STRAIN_OFF_S = 120;

    private static final Map<UUID, Map<String, Long>> NEXT = new HashMap<>();
    /** bot -> {read until, reads left} */
    private static final Map<UUID, long[]> READ = new HashMap<>();
    private static final Map<UUID, Long> WEAPON = new HashMap<>();
    private static final Map<UUID, Integer> LAST_TARGET = new HashMap<>();
    private static final Map<UUID, long[]> STRAIN = new HashMap<>();
    /** (Tests) how often each happened. */
    public static int reads, dodges, lunar, boots, visors, rescues, scents, rlUses, weapons, strains;

    private static long now(Entity e) { return e.level().getGameTime(); }
    private static boolean ready(PartyBotEntity b, String k) { return now(b) >= NEXT.getOrDefault(b.getUUID(), Map.of()).getOrDefault(k, 0L); }
    private static void wait(PartyBotEntity b, String k, int ticks) { NEXT.computeIfAbsent(b.getUUID(), x -> new HashMap<>()).put(k, now(b) + ticks); }

    /** (Tests) clears a bot's cooldowns and reads. */
    public static void resetForTest(PartyBotEntity b) { forget(b.getUUID()); }

    /** It's gone (dismissed, sent home): its state goes too. */
    public static void forget(UUID u) { NEXT.remove(u); READ.remove(u); WEAPON.remove(u); LAST_TARGET.remove(u); STRAIN.remove(u); }

    /** A skill of its character it may use (its level, the season), by id; null if not. */
    static Skill skill(PartyBotEntity b, String id) {
        List<SkillPool.Entry> pool = SkillPool.of(b.character());
        for (int i = 0; i < pool.size(); i++) if (pool.get(i).skill().getId().equals(id)) return PartyBots.botMay(b, i) ? pool.get(i).skill() : null;
        return null;
    }

    /** Its owner has this switched on for its character (and it's a toggle of that character). */
    public static boolean on(PartyBotEntity b, Toggles.Entry e) {
        return Toggles.of(b.character()).contains(e) && Toggles.enabledFor(b.owner(), b.character(), e);
    }

    /** A passive / transformation it may use now (on, and - for one with a skill - unlocked for it). */
    private static boolean usable(PartyBotEntity b, Toggles.Entry e) {
        if (!on(b, e)) return false;
        if (b.journey() && com.xenowarriors.journey.Journey.noSkills(b.owner())) return false;
        return skill(b, e.id) != null;
    }

    /** Toggle skills (passives, the transformations) are its passives here, not slot skills; a buff only if it's enabled. */
    static boolean blockedInSlot(PartyBotEntity b, Skill s) {
        Toggles.Entry e = Toggles.Entry.byId(s.getId());
        if (e == null || !Toggles.of(b.character()).contains(e)) return false;
        return e.kind != Toggles.Kind.BUFF || !Toggles.enabledFor(b.owner(), b.character(), e);
    }

    // ------------------------------------------------------------------ the tick

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer o) || o instanceof FakePlayer) return;
        for (PartyBotEntity b : PartyBots.bots(o)) {
            if (!b.isAlive() || b.downed() || b.level() != o.level()) continue;
            try {
                if (b.character() == CharacterType.TECHNICA) gravityBoots(b);
                if ((o.tickCount + b.getId()) % 10 != 0) continue;
                switch (b.character()) {
                    case MIKIKO -> read(b, o, Toggles.Entry.TRUE_EMPATHY, 6, 30, EMPATHY_S, 60, 1);
                    case ULTIMA -> { read(b, o, Toggles.Entry.ATTACK_PREDICTION, 8, 25, PREDICT_S, 160, 2); livingWeapon(b, o); }
                    case MAX -> lunarMind(b);
                    case TECHNICA -> { visor(b, o); rescue(b, o); }
                    case KAERU -> scent(b, o);
                    default -> {}
                }
            } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot's passives", t); }
        }
    }

    // ------------------------------------------------------------------ True Empathy / Attack Prediction: reading the next hits

    private static void read(PartyBotEntity b, ServerPlayer o, Toggles.Entry e, int range, int baseCost, int gapS, int readTicks, int hits) {
        if (!usable(b, e) || !ready(b, "read") || b.sparring()) return;
        long[] r = READ.get(b.getUUID());
        if (r != null && r[0] > now(b) && r[1] > 0) return;                            // (still reading)
        boolean threat = !b.level().getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(range), m -> m.isAlive() && m.getTarget() == b).isEmpty();
        if (!threat) return;
        Skill s = skill(b, e.id);
        float cost = com.xenowarriors.skill.SkillLevels.costFor(b, s.getId(), baseCost);
        if (b.karma < cost) return;
        b.karma -= cost;
        com.xenowarriors.skill.SkillLevels.used(b, s);
        wait(b, "read", gapS * 20);
        READ.put(b.getUUID(), new long[]{now(b) + readTicks, hits});
        reads++;
        ServerLevel l = (ServerLevel) b.level();
        l.sendParticles(ParticleTypes.ENCHANT, b.getX(), b.getEyeY(), b.getZ(), 30, 0.4, 0.4, 0.4, 0.6);
        l.playSound(null, b.blockPosition(), e == Toggles.Entry.TRUE_EMPATHY ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 1F, e == Toggles.Entry.TRUE_EMPATHY ? 1.3F : 2F);
        com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.MIRROR, 8);
        long t = now(b);                                                               // the strain: three inside 90 s is too many
        long[] uses = Arrays.stream(STRAIN.getOrDefault(b.getUUID(), new long[0])).filter(x -> t - x < 90 * 20).toArray();
        long[] next = Arrays.copyOf(uses, uses.length + 1); next[uses.length] = t;
        STRAIN.put(b.getUUID(), next);
        if (next.length >= 3) {
            STRAIN.remove(b.getUUID());
            strains++;
            b.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
            b.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
            wait(b, "read", STRAIN_OFF_S * 20);                                         // (switched itself off for a while; your setting stays)
            o.displayClientMessage(Component.empty().append(b.label()).append(": " + e.title + " overused - it switched itself off for a while").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    /** A hit coming at a bot that's reading: it steps aside and hits back. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent ev) {
        if (!(ev.getEntity() instanceof PartyBotEntity b) || b.level().isClientSide || b.downed()) return;
        long[] r = READ.get(b.getUUID());
        if (r == null || r[1] <= 0 || r[0] < now(b)) return;
        Entity by = ev.getSource().getEntity();
        if (!(by instanceof LivingEntity at) || at == b || PartyBots.friendly(b, at)) return;
        r[1]--;
        dodges++;
        ev.setCanceled(true);
        Vec3 away = b.position().subtract(at.position()).multiply(1, 0, 1);
        Vec3 side = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(-away.z, 0, away.x).normalize();
        if (b.getRandom().nextBoolean()) side = side.scale(-1);
        b.setDeltaMovement(side.scale(0.55).add(0, 0.15, 0));
        b.hurtMarked = true;
        ((ServerLevel) b.level()).sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 0.2, b.getZ(), 6, 0.2, 0.05, 0.2, 0.02);
        if (at.distanceToSqr(b) < 4.5 * 4.5) com.xenowarriors.skill.ServerScheduler.later(4, () -> { if (b.isAlive() && at.isAlive() && !b.downed()) { b.getLookControl().setLookAt(at); b.swing(net.minecraft.world.InteractionHand.MAIN_HAND); b.doHurtTarget(at); } });
    }

    // ------------------------------------------------------------------ Ultima: Living Weapon

    private static void livingWeapon(PartyBotEntity b, ServerPlayer o) {
        LivingEntity t = b.getTarget();
        if (t == null || !t.isAlive() || b.sparring() || weaponOn(b) || !ready(b, "weapon") || !usable(b, Toggles.Entry.LIVING_WEAPON)) return;
        if (!PartyBots.strong(b, t)) return;
        float chance = b.getHealth() < b.getMaxHealth() * 0.5F ? 0.3F : 0.12F;
        if (b.getRandom().nextFloat() >= chance) return;
        Skill s = skill(b, Toggles.Entry.LIVING_WEAPON.id);
        float cost = com.xenowarriors.skill.SkillLevels.costFor(b, s.getId(), s.getKarmaCost());
        if (b.karma < cost) return;
        b.karma -= cost;
        com.xenowarriors.skill.SkillLevels.used(b, s);
        float m = com.xenowarriors.skill.FormMastery.get(o, com.xenowarriors.skill.FormMastery.LIVING);
        int len = Math.round(200 * com.xenowarriors.skill.FormMastery.durationMult(m));
        WEAPON.put(b.getUUID(), now(b) + len);
        wait(b, "weapon", Math.round(WEAPON_S * 20 * com.xenowarriors.skill.FormMastery.cooldownMult(m)) + len);
        b.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, len, 0, false, false, true));
        ServerLevel l = (ServerLevel) b.level();
        l.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.12F, 0.88F, 0.48F), 1.2F), b.getX(), b.getY() + 1, b.getZ(), 40, 0.5, 0.8, 0.5, 0);
        l.playSound(null, b.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.NEUTRAL, 1F, 1.3F);
        com.xenowarriors.anim.Poses.play(b, com.xenowarriors.anim.Poses.KA_BREAK, 14);
        o.displayClientMessage(Component.empty().append(b.label()).append(": Living Weapon!").withStyle(ChatFormatting.GREEN), true);
        weapons++;
    }

    public static boolean weaponOn(PartyBotEntity b) { return WEAPON.getOrDefault(b.getUUID(), 0L) > now(b); }

    // ------------------------------------------------------------------ Max: Lunar Mind

    private static void lunarMind(PartyBotEntity b) {
        if ((b.tickCount / 10) % 4 != 0 || !usable(b, Toggles.Entry.LUNAR_MIND)) return;
        long t = b.level().getDayTime() % 24000;
        if (t <= 13000 || t >= 23000 || !b.level().dimensionType().hasSkyLight()) return;
        boolean was = b.hasEffect(MobEffects.DAMAGE_BOOST);
        b.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, true, false, true));
        b.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false, true));
        if (b.tickCount % 120 < 40) b.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false, true));
        if (!was) { ((ServerLevel) b.level()).sendParticles(ParticleTypes.END_ROD, b.getX(), b.getY() + 1, b.getZ(), 20, 0.4, 0.8, 0.4, 0.02); lunar++; }
    }

    // ------------------------------------------------------------------ Technica

    private static void gravityBoots(PartyBotEntity b) {
        if (b.onGround() || b.fallDistance < 3.6F || b.getDeltaMovement().y > -0.3 || b.isInWater() || b.isPassenger() || b.isNoGravity()) return;
        if (!ready(b, "boots") || !usable(b, Toggles.Entry.GRAVITY_BOOTS)) return;
        double fall = -b.getDeltaMovement().y;
        BlockPos below = b.blockPosition();
        int clear = 0;
        while (clear < 6 && b.level().getBlockState(below.below(clear + 1)).getCollisionShape(b.level(), below.below(clear + 1)).isEmpty()) clear++;
        if (clear > Math.max(2, (int) Math.ceil(fall * 2.5))) return;
        b.fallDistance = 0F;
        b.getPersistentData().putLong(com.xenowarriors.toggle.Passives.BOOTS_SAFE, b.level().getGameTime() + 40);   // (no fall damage at all for 2 s - other mods track falls their own way)
        b.setDeltaMovement(b.getDeltaMovement().multiply(1, 0.15, 1));
        b.hurtMarked = true;
        b.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
        ((ServerLevel) b.level()).sendParticles(com.xenowarriors.skill.sp.TechSkills.gravParticle(), b.getX(), b.getY() + 0.1, b.getZ(), 30, 0.4, 0.05, 0.4, 0);
        b.level().playSound(null, b.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.8F, 1.8F);
        wait(b, "boots", BOOTS_S * 20);
        boots++;
    }

    private static void visor(PartyBotEntity b, ServerPlayer o) {
        LivingEntity t = b.getTarget();
        Integer last = LAST_TARGET.get(b.getUUID());
        LAST_TARGET.put(b.getUUID(), t == null ? -1 : t.getId());
        if (t == null || !t.isAlive() || b.sparring() || (last != null && last == t.getId()) || !ready(b, "visor") || !usable(b, Toggles.Entry.VISOR_SCAN)) return;
        wait(b, "visor", VISOR_S * 20);
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
        ((ServerLevel) b.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, b.getX(), b.getEyeY(), b.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
        if (o.distanceToSqr(b) < 40 * 40)
            o.displayClientMessage(Component.empty().append(b.label()).append("'s visor: " + t.getName().getString() + " - " + (int) t.getHealth() + "/" + (int) t.getMaxHealth() + " HP, armor " + t.getArmorValue()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        visors++;
    }

    private static void rescue(PartyBotEntity b, ServerPlayer o) {
        if (!ready(b, "rescue") || !usable(b, Toggles.Entry.RESCUE_PLATFORM)) return;
        List<LivingEntity> down = new ArrayList<>();
        for (LivingEntity m : com.xenowarriors.party.Bonds.team(b)) {
            if (m.level() != b.level() || m.distanceToSqr(b) > 48 * 48) continue;
            if (m instanceof PartyBotEntity x ? x.downed() : m instanceof ServerPlayer sp && com.xenowarriors.party.Duels.isDowned(sp)) down.add(m);
        }
        if (down.size() < 2) return;
        Skill s = skill(b, Toggles.Entry.RESCUE_PLATFORM.id);
        wait(b, "rescue", RESCUE_S * 20);
        PartyBots.castAt(b, s, down.get(0));
        for (LivingEntity d : down) {
            d.teleportTo(b.getX() + b.getRandom().nextGaussian(), b.getY(), b.getZ() + b.getRandom().nextGaussian());
            if (d instanceof PartyBotEntity x) x.revive();
            else if (d instanceof ServerPlayer m) com.xenowarriors.party.Duels.foxRevive(m, PartyBots.fake(b));
        }
        o.displayClientMessage(Component.empty().append(b.label()).append(": Rescue Platform! " + down.size() + " teammates picked up").withStyle(ChatFormatting.GOLD), false);
        rescues++;
    }

    // ------------------------------------------------------------------ Kaeru

    private static void scent(PartyBotEntity b, ServerPlayer o) {
        LivingEntity t = b.getTarget();
        Integer last = LAST_TARGET.get(b.getUUID());
        LAST_TARGET.put(b.getUUID(), t == null ? -1 : t.getId());
        if (t == null || !t.isAlive() || b.sparring() || (last != null && last == t.getId()) || !ready(b, "scent") || !usable(b, Toggles.Entry.SCENT_TRACK)) return;
        Skill s = skill(b, Toggles.Entry.SCENT_TRACK.id);
        float cost = com.xenowarriors.skill.SkillLevels.costFor(b, s.getId(), s.getKarmaCost());
        if (b.karma < cost) return;
        b.karma -= cost;
        com.xenowarriors.skill.SkillLevels.used(b, s);
        wait(b, "scent", SCENT_S * 20);
        t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        b.level().playSound(null, b.blockPosition(), SoundEvents.FOX_SNIFF, SoundSource.NEUTRAL, 1F, 1.2F);
        if (o.distanceToSqr(b) < 40 * 40) o.displayClientMessage(Component.empty().append(b.label()).append(" caught the scent of " + t.getName().getString()).withStyle(ChatFormatting.GOLD), true);
        scents++;
    }

    // ------------------------------------------------------------------ the right+left click skill

    /** (Tests) no dice roll for the right+left click move. */
    public static boolean forceRl;

    /** (Tests) its right+left click move at this target now. */
    public static boolean rlForTest(PartyBotEntity b, LivingEntity t) { forceRl = true; try { return tryRl(b, t, false); } finally { forceRl = false; } }

    /** Its right+left click skill at this target, if it's ready (true if it went off). */
    static boolean tryRl(PartyBotEntity b, LivingEntity target, boolean afterHit) {
        if (target == null || !target.isAlive() || b.downed()) return false;
        if (com.xenowarriors.item.Guns.isGun(b.getMainHandItem()) || com.xenowarriors.item.Guns.isGun(b.getOffhandItem())) return false;   // (a gun in hand: it shoots instead)
        if (b.journey() && com.xenowarriors.journey.Journey.noSkills(b.owner())) return false;
        if (!com.xenowarriors.character.Seasons.unlocked(b.getServer(), b.character().season())) return false;
        String id = Toggles.rlSkillFor(b.owner(), b.character());
        if (id.isEmpty() || !ready(b, "rl")) return false;
        if (!forceRl && b.getRandom().nextFloat() >= (afterHit ? 0.3F : 0.25F)) return false;
        Skill s = skill(b, id);
        if (s == null) return false;
        float cost = com.xenowarriors.skill.SkillLevels.costFor(b, id, s.getKarmaCost());
        if (b.karma < cost) return false;
        if (b.isUsingItem()) b.stopUsingItem();                                         // (a block dropped for it, like yours)
        if (!PartyBots.castAt(b, s, target)) { wait(b, "rl", 60); return false; }
        b.karma -= cost;
        wait(b, "rl", Math.max(30, Math.round(com.xenowarriors.skill.SkillLevels.cooldownFor(b, id, s.getCooldownTicks()) * Toggles.RL_COOLDOWN)));
        rlUses++;
        PartyBots.testLog(b, "right+left click skill: " + id);
        return true;
    }

    /** A landed hit: its right+left click move sometimes follows; Living Weapon sweeps the ones around. */
    static void afterHit(PartyBotEntity b, Entity hit) {
        if (!(hit instanceof LivingEntity t)) return;
        if (weaponOn(b)) {
            float dmg = (float) b.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5F;
            for (LivingEntity e : b.level().getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(2.5), e -> e != t && e != b && e.isAlive()
                    && (e instanceof net.minecraft.world.entity.monster.Enemy || e instanceof Mob m && m.getTarget() == b) && !(e instanceof PartyBotEntity)))
                e.hurt(b.damageSources().mobAttack(b), dmg);
            ((ServerLevel) b.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
        }
        if (b.getTarget() == t && (b.sparring() || PartyBots.strong(b, t))) tryRl(b, t, true);
    }
}

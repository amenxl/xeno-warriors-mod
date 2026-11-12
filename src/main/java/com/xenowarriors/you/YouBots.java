package com.xenowarriors.you;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.Race;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Your own characters as bot teammates: their race's ways, as a player of them has them. Their race's health (see
 * PartyBotEntity.applyLevel) and damage (a Demon's and a Devil's, an Angel's, a Seedling's on Namek), a Seedling's
 * power under Namek's three suns, and - since a bot is that character, played by the AI - what it earns goes into its
 * save: a Demon's kills feed its Devil's Hunger (its Lunar/Solar mastery grows as it uses its forms: YouAngel.formUsed
 * through its stand-in). The one-time steps (the Devil's duel, the Angel's trial, a Government path) are yours to do,
 * playing it yourself.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouBots {
    private YouBots() {}

    /** Extra health from its race (as YouRaces.apply gives a player of it in survival). */
    public static double raceHealth(PartyBotEntity b) {
        YouSlot s = b.you();
        if (s == null) return 0;
        return switch (s.shown()) {
            case ANGEL -> 20;
            case DEVIL -> 16;
            case DEMON, ARTIFICIAL_DEMON -> 6;
            case SEEDLING, YOU -> 4;
            default -> 0;
        };
    }

    public static boolean onNamek(Entity e) { return e.level().dimension().location().toString().equals("dragonminez:namek"); }

    /** Damage it deals, by race (a bot never hides a demon in human form). */
    public static float damageMult(PartyBotEntity b) {
        YouSlot s = b.you();
        if (s == null) return 1F;
        if (s.demon()) return s.devil ? 1.3F : 1.15F;
        if (s.race == Race.SEEDLING && onNamek(b)) return YouRaces.NAMEK_DAMAGE;
        if (s.angel()) return 1.5F;
        return 1F;
    }

    private static boolean namekPower(PartyBotEntity b) { YouSlot s = b.you(); return s != null && s.race == Race.SEEDLING && onNamek(b); }

    /** Every second: what its race does by itself. */
    public static void tick(PartyBotEntity b) {
        if (!(b.level() instanceof ServerLevel l) || b.downed()) return;
        YouKa.botTick(b);                                                        // (its KA: rolled at Level 30, used in a fight)
        if (namekPower(b)) {                                                     // Namek's three suns
            b.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false, true));
            b.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, true, false, true));
            b.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
            l.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX(), b.getY() + 1, b.getZ(), 3, 0.5, 0.8, 0.5, 0);
        }
        int lv = b.botLevel();
        double hp = 20 + 2 * com.xenowarriors.stats.Stats.bonusHearts(lv) + raceHealth(b);
        var a = b.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (a != null && Math.abs(a.getBaseValue() - hp) > 0.01) b.applyLevel(lv);   // (its race changed - a Devil now, say: its health follows)
    }

    /** Its hits (and hits on a Seedling of yours on Namek). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        Entity src = e.getSource().getEntity();
        if (src instanceof PartyBotEntity b && b.ownCharacter()) { float m = damageMult(b); if (m != 1F) e.setAmount(e.getAmount() * m); }
        if (e.getEntity() instanceof PartyBotEntity v && v.ownCharacter() && namekPower(v)) e.setAmount(e.getAmount() * YouRaces.NAMEK_GUARD);
    }

    /** A Demon of yours feeds its Devil's Hunger with its kills, as you would playing it. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        Entity src = e.getSource().getEntity();
        if (src instanceof Projectile pr) src = pr.getOwner();
        if (!(src instanceof PartyBotEntity b) || !b.ownCharacter() || e.getEntity() instanceof Player) return;
        LivingEntity dead = e.getEntity();
        if (!(dead instanceof Enemy) && !(dead instanceof com.xenowarriors.gov.GovAgentEntity) && !com.xenowarriors.karma.KarmaSensing.boss(dead)) return;
        if (dead.getTags().contains("xw_journey") || dead.getPersistentData().getBoolean("xw_tower") || dead.getTags().contains(YouDevil.SHADOW_TAG)) return;
        YouSlot s = b.you();
        ServerPlayer o = b.owner();
        if (s == null || o == null || !s.demon() || s.devil || b.botLevel() < YouDevil.LEVEL || s.hunger >= YouDevil.HUNGER_MAX) return;
        s.hunger = Math.min(YouDevil.HUNGER_MAX, s.hunger + (com.xenowarriors.karma.KarmaSensing.boss(dead) ? 5 : 1));
        if (s.hunger >= YouDevil.HUNGER_MAX)
            o.sendSystemMessage(Component.literal(s.name + "'s Devil's Hunger is full. Play as " + s.name + " at night and the shadow will come.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        else if (s.hunger % 10 == 0) o.displayClientMessage(Component.literal(s.name + ": Devil's Hunger " + s.hunger + " / " + YouDevil.HUNGER_MAX).withStyle(ChatFormatting.DARK_RED), true);
        fed++;
    }

    /** (Tests) kills that fed a bot's hunger. */
    public static int fed;
}

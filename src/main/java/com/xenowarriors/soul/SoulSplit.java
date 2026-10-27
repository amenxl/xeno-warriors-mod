package com.xenowarriors.soul;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.karma.Karma;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Soul Splitter (season 2): splitting your soul off into fighters that stay by your side.
 *  Mikiko - lasts the longest; 1 soul, 2 at Soul Splitter level 15, 3 at level 40.
 *  Rhalix - 1 soul; at level 10 it throws his gravity slams, at level 25 his wire lashes too.
 *  Kaeru  - out of the Hekublade: 1 soul like Rhalix. (In the blade she casts HERSELF out instead - see KaeruSoul.)
 * While split, your health, damage and Karma (and Ki with DragonMineZ) are divided by the number of souls (at least 2),
 * and given back when the souls come home. Wander more than 48 blocks from a soul and it fades and finds its way back.
 * The Soul Splitter stat levels up by keeping souls out and by what they defeat.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulSplit {
    public static final double LEASH = 48;
    private static final UUID HEALTH_MOD = UUID.fromString("5b0a1c9e-7d43-4c1e-9b0e-3f5f6c2a9d11");
    private static final Map<UUID, Split> ACTIVE = new HashMap<>();

    static final class Split {
        final List<SoulEntity> souls = new ArrayList<>();
        int divisor; float karmaStash, kiStash; long end;
    }

    private SoulSplit() {}

    // ------------------------------------------------------------------ the Soul Splitter stat
    private static CompoundTag tag(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int level(Player p) { return tag(p).getInt("xw_soul_lvl"); }
    public static void setLevel(Player p, int level) { tag(p).putInt("xw_soul_lvl", level); tag(p).putFloat("xw_soul_xp", 0); }
    public static float needed(int level) { return 30F + level * 12F; }

    public static void addXp(ServerPlayer p, float xp) {
        CompoundTag t = tag(p);
        int lvl = t.getInt("xw_soul_lvl");
        float have = t.getFloat("xw_soul_xp") + xp;
        boolean up = false;
        while (lvl < Stat.MAX_LEVEL && have >= needed(lvl)) { have -= needed(lvl); lvl++; up = true; }
        t.putInt("xw_soul_lvl", lvl); t.putFloat("xw_soul_xp", have);
        if (up) {
            p.displayClientMessage(Component.literal("Soul Splitter is now level " + lvl).withStyle(ChatFormatting.AQUA), false);
            CharacterType c = ModCapabilities.getType(p);
            if (c == CharacterType.MIKIKO && (lvl == 15 || lvl == 40))
                p.displayClientMessage(Component.literal("Mikiko can split off " + souls(c, lvl) + " souls now!").withStyle(ChatFormatting.GOLD), false);
            if (c == CharacterType.RHALIX && lvl == RhalixSoulArts.GRAVITY_LEVEL)
                p.displayClientMessage(Component.literal("Rhalix's soul can use his gravity now!").withStyle(ChatFormatting.GOLD), false);
            if (c == CharacterType.RHALIX && lvl == RhalixSoulArts.WIRE_LEVEL)
                p.displayClientMessage(Component.literal("Rhalix's soul can use his wires now!").withStyle(ChatFormatting.GOLD), false);
        }
    }

    public static int souls(CharacterType c, int level) {
        if (c != CharacterType.MIKIKO) return 1;
        return 1 + (level >= 15 ? 1 : 0) + (level >= 40 ? 1 : 0);
    }

    /** Seconds the souls stay out. Mikiko holds it the longest. */
    public static int seconds(CharacterType c, int level) {
        float e = Stat.effective(level);
        return switch (c) {
            case MIKIKO -> 90 + (int) Math.min(90, e * 1.5F);
            case RHALIX -> 45 + (int) Math.min(45, e * 0.75F);
            default -> 40 + (int) Math.min(40, e * 0.75F);
        };
    }

    public static boolean active(Player p) { return ACTIVE.containsKey(p.getUUID()); }
    public static int divisor(Player p) { Split s = ACTIVE.get(p.getUUID()); return s == null ? 1 : s.divisor; }

    // ------------------------------------------------------------------ splitting
    public static boolean start(ServerPlayer p) {
        if (active(p)) { end(p, null); return false; }
        CharacterType c = ModCapabilities.getType(p);
        int lvl = level(p), n = souls(c, lvl), d = Math.max(2, n);
        ServerLevel l = p.serverLevel();
        Split s = new Split();
        s.divisor = d;
        s.end = l.getGameTime() + seconds(c, lvl) * 20L;
        // health
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        float fullHp = (float) hp.getValue();
        hp.removeModifier(HEALTH_MOD);
        hp.addTransientModifier(new AttributeModifier(HEALTH_MOD, "Soul Splitter", 1.0 / d - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        // karma and ki
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> {
            s.karmaStash = data.getKarma() * (1F - 1F / d);
            data.setKarma(data.getKarma() - s.karmaStash);
            Karma.sync(p, data);
        });
        float ki = com.xenowarriors.compat.DmzPetKi.ki(p)[0];
        if (ki > 0 && com.xenowarriors.compat.DmzPetKi.setKi(p, ki / d)) s.kiStash = ki - ki / d;
        // the souls
        float dmg = (float) Math.max(5.0, p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.0) / d;
        for (int i = 0; i < n; i++) {
            SoulEntity e = SoulEntities.SOUL.get().create(l);
            if (e == null) continue;
            double a = Math.toRadians(p.getYRot() + 90 + (i - (n - 1) / 2.0) * 60);
            e.moveTo(p.getX() + Math.cos(a) * 1.6, p.getY(), p.getZ() + Math.sin(a) * 1.6, p.getYRot(), 0);
            e.setCharacter(c); e.setOwner(p.getUUID()); e.soulLevel = lvl;
            e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(6.0, fullHp / d));
            e.setHealth(e.getMaxHealth());
            e.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(dmg);
            e.setCustomName(Component.literal(p.getName().getString() + "'s soul").withStyle(ChatFormatting.AQUA));
            l.addFreshEntity(e);
            s.souls.add(e);
            l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 20, 0.3, 0.6, 0.3, 0.04);
        }
        ACTIVE.put(p.getUUID(), s);
        l.playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5F, 0.8F);
        l.playSound(null, p.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1F, 0.6F);
        com.xenowarriors.XwFx.send(l, com.xenowarriors.network.FxPacket.SKILL, p.getX(), p.getY(), p.getZ(), 0x9FE8FF, 1.2F);
        p.displayClientMessage(Component.literal("Soul Splitter Lv " + lvl + ": " + n + (n == 1 ? " soul" : " souls") + " out - your strength is split " + d + " ways")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** Brings every soul home and gives back what was divided. {@code why} is shown if not null. */
    public static void end(ServerPlayer p, String why) {
        Split s = ACTIVE.remove(p.getUUID());
        if (s == null) return;
        for (SoulEntity e : s.souls) {
            if (!e.isRemoved()) {
                if (e.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 16, 0.3, 0.6, 0.3, 0.03);
                e.discard();
            }
        }
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(HEALTH_MOD);
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(data -> { data.setKarma(data.getKarma() + s.karmaStash); Karma.sync(p, data); });
        if (s.kiStash > 0) {
            float[] ki = com.xenowarriors.compat.DmzPetKi.ki(p);
            com.xenowarriors.compat.DmzPetKi.setKi(p, Math.min(ki[1], ki[0] + s.kiStash));
        }
        if (p.isAlive()) p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1F, 1.4F);
        if (why != null) p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.AQUA), true);
    }

    /** A soul faded (owner too far, or left) or was beaten. When the last one is gone, the split ends. */
    static void soulGone(SoulEntity e, boolean faded) {
        UUID owner = e.ownerId();
        if (!e.isRemoved()) {
            if (e.level() instanceof ServerLevel l) l.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 16, 0.3, 0.6, 0.3, 0.03);
            if (faded) e.discard();
        }
        Split s = owner == null ? null : ACTIVE.get(owner);
        if (s == null) { if (!e.isRemoved() && faded) e.discard(); return; }
        s.souls.remove(e);
        ServerPlayer p = e.level().getServer() == null ? null : e.level().getServer().getPlayerList().getPlayer(owner);
        if (s.souls.isEmpty()) {
            if (p != null) end(p, faded ? "You went too far - your soul found its way back to you" : "Your soul was beaten and came back to you");
            else ACTIVE.remove(owner);
        }
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            Split s = ACTIVE.get(id);
            if (p == null) { for (SoulEntity e : s.souls) e.discard(); ACTIVE.remove(id); continue; }
            s.souls.removeIf(Entity::isRemoved);
            addXp(p, s.souls.size());
            if (s.souls.isEmpty()) end(p, null);
            else if (p.serverLevel().getGameTime() >= s.end) end(p, "Your soul came back to you");
        }
    }

    /** Your own blows are divided while split. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p && active(p) && !(event.getEntity() instanceof SoulEntity))
            event.setAmount(event.getAmount() / divisor(p));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) end(p, null);
        if (event.getSource().getEntity() instanceof SoulEntity s && s.owner() != null) addXp(s.owner(), 5F);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) end(p, null);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        AttributeInstance hp = event.getEntity().getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(HEALTH_MOD);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) end(p, "Your soul couldn't follow you there and came back");
    }
}

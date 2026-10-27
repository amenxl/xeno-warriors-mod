package com.xenowarriors.kaeru;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.mikiko.Hekublade;
import com.xenowarriors.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Kaeru and the Hekublade.
 *  OUT   - her own body, full strength. G summons the blade (only if Mikiko isn't holding it - one holder at a time).
 *  BLADE - sneak + G near a Mikiko: she goes back into his Hekublade and watches through it.
 *          G steps back out. Any skill key casts her soul out into the world.
 *  SOUL  - her soul in the outside world: half as strong (health and damage), and it can't go further from Mikiko than
 *          500 blocks + her Soul Splitter level. If the soul is beaten it goes back into the blade, and she has to wait
 *          60 seconds before casting it out again. Sneak + G returns to the blade any time.
 * BLADE and SOUL need a Mikiko player online; if he leaves she's simply back in her own body.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KaeruSoul {
    public static final int OUT = 0, BLADE = 1, SOUL = 2;
    public static final int BASE_RANGE = 500, SOUL_COOLDOWN = 60 * 20;
    private static final UUID HALF_HP = UUID.fromString("c3d7a3e2-1b9f-4f37-a6e3-8e0c4b7f2a55");

    private KaeruSoul() {}

    private static CompoundTag tag(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int state(Player p) { return ModCapabilities.getType(p) == CharacterType.KAERU ? tag(p).getInt("xw_kaeru_state") : OUT; }
    public static boolean inBlade(Player p) { return state(p) == BLADE; }
    private static void setState(ServerPlayer p, int s) { tag(p).putInt("xw_kaeru_state", s); }

    /** Her skill damage multiplier (the half-strength soul is handled by the damage event, so this is always 1). */
    public static float strength(ServerPlayer p) { return 1F; }

    /** How far her soul may roam from Mikiko. */
    public static int range(ServerPlayer p) { return BASE_RANGE + (int) (Stat.effective(com.xenowarriors.soul.SoulSplit.level(p)) * 10F); }

    private static ServerPlayer host(ServerPlayer p) {
        CompoundTag t = tag(p);
        if (!t.hasUUID("xw_kaeru_host")) return null;
        ServerPlayer h = p.server.getPlayerList().getPlayer(t.getUUID("xw_kaeru_host"));
        return h != null && h.isAlive() && ModCapabilities.getType(h) == CharacterType.MIKIKO ? h : null;
    }

    private static ServerPlayer nearestMikiko(ServerPlayer p, double r) {
        ServerPlayer best = null; double bd = r * r;
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || o.level() != p.level() || ModCapabilities.getType(o) != CharacterType.MIKIKO || !o.isAlive()) continue;
            double d = o.distanceToSqr(p);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    // ------------------------------------------------------------------ one blade, one holder
    private static boolean holds(ServerPlayer p) {
        return Hekublade.removeAllCheck(p.getInventory()) || Hekublade.isBlade(p.containerMenu.getCarried());
    }

    /** Someone else (Mikiko for Kaeru, Kaeru for Mikiko) who is holding the Hekublade right now, or null. */
    public static ServerPlayer otherHolder(ServerPlayer p) {
        CharacterType me = ModCapabilities.getType(p);
        CharacterType other = me == CharacterType.KAERU ? CharacterType.MIKIKO : me == CharacterType.MIKIKO ? CharacterType.KAERU : null;
        if (other == null) return null;
        boolean partied = com.xenowarriors.party.Parties.inParty(p);
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || ModCapabilities.getType(o) != other) continue;
            if (partied && !com.xenowarriors.party.Parties.sameParty(o, p)) continue;
            if (holds(o)) return o;
        }
        return null;
    }

    /** For Kaeru's blade skills: false (with a message) if Mikiko has the blade or she's not in her body. */
    public static boolean bladeFreeFor(ServerPlayer p) {
        ServerPlayer o = otherHolder(p);
        if (o == null) return true;
        p.displayClientMessage(Component.literal(o.getName().getString() + " is holding the Hekublade").withStyle(ChatFormatting.RED), true);
        return false;
    }

    // ------------------------------------------------------------------ the G key
    public static void bladeKey(ServerPlayer p) {
        int s = state(p);
        if (s == BLADE) { stepOut(p, OUT); return; }
        ServerPlayer h = s == SOUL ? host(p) : nearestMikiko(p, 16);
        if (h == null) {
            p.displayClientMessage(Component.literal(s == SOUL ? "Mikiko isn't here..." : "Mikiko needs to be close (16 blocks) to return to the Hekublade").withStyle(ChatFormatting.GRAY), true);
            if (s == SOUL) stepOut(p, OUT);
            return;
        }
        enterBlade(p, h);
    }

    private static void enterBlade(ServerPlayer p, ServerPlayer h) {
        Hekublade.removeAll(p.getInventory());
        com.xenowarriors.soul.SoulSplit.end(p, null);
        halfStrength(p, false);
        CompoundTag t = tag(p);
        if (state(p) == OUT) t.putInt("xw_kaeru_gm", p.gameMode.getGameModeForPlayer().getId());
        t.putUUID("xw_kaeru_host", h.getUUID());
        setState(p, BLADE);
        burst(p);
        p.setGameMode(GameType.SPECTATOR);
        p.teleportTo(h.serverLevel(), h.getX(), h.getY(), h.getZ(), h.getYRot(), h.getXRot());
        p.setCamera(h);
        h.displayClientMessage(Component.literal("Kaeru returned to the Hekublade").withStyle(ChatFormatting.GOLD), true);
        p.displayClientMessage(Component.literal("In the Hekublade - G to step out, any skill key to cast your soul out").withStyle(ChatFormatting.GOLD), true);
    }

    /** Leaves the blade: as herself (OUT) or as her soul (SOUL). */
    private static void stepOut(ServerPlayer p, int to) {
        ServerPlayer h = host(p);
        int was = state(p);
        setState(p, to);
        if (was == BLADE) {
            p.setCamera(p);
            p.setGameMode(GameType.byId(tag(p).getInt("xw_kaeru_gm")));
            if (h != null) p.teleportTo(h.serverLevel(), h.getX() + h.getLookAngle().x, h.getY(), h.getZ() + h.getLookAngle().z, h.getYRot(), 0);
        }
        halfStrength(p, to == SOUL);
        burst(p);
    }

    /** Any skill key while in the blade. */
    public static void castOut(ServerPlayer p) {
        if (host(p) == null) { stepOut(p, OUT); return; }
        long left = tag(p).getLong("xw_kaeru_cd") - p.serverLevel().getGameTime();
        if (left > 0) { p.displayClientMessage(Component.literal("Your soul needs " + (left + 19) / 20 + "s to recover").withStyle(ChatFormatting.GRAY), true); return; }
        stepOut(p, SOUL);
        p.displayClientMessage(Component.literal("Soul cast out - half strength, " + range(p) + " blocks from Mikiko at most").withStyle(ChatFormatting.AQUA), true);
    }

    private static void halfStrength(ServerPlayer p, boolean on) {
        AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        hp.removeModifier(HALF_HP);
        if (on) hp.addTransientModifier(new AttributeModifier(HALF_HP, "Kaeru's soul", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
        p.setHealth(Math.min(p.getHealth(), p.getMaxHealth()));
        if (!on && p.getHealth() < 1) p.setHealth(1);
    }

    private static void burst(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 24, 0.3, 0.6, 0.3, 0.05);
        l.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.6, 0.3, 0.03);
        l.playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 1.2F);
        l.playSound(null, p.blockPosition(), com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(), SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    // ------------------------------------------------------------------ events
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        int s = state(p);
        if (s == OUT) return;
        ServerPlayer h = host(p);
        if (h == null) {                                                   // Mikiko left: back in her own body
            stepOut(p, OUT);
            p.displayClientMessage(Component.literal("Mikiko is gone - you're back in your own body").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (s == BLADE) {
            if (p.level() != h.level() || p.distanceToSqr(h) > 1) p.teleportTo(h.serverLevel(), h.getX(), h.getY(), h.getZ(), h.getYRot(), h.getXRot());
            if (p.tickCount % 20 == 0 && p.getCamera() != h) p.setCamera(h);
            return;
        }
        // SOUL: the leash
        if (p.level() != h.level()) { enterBlade(p, h); return; }
        if (p.tickCount % 10 != 0) return;
        double max = range(p), d = Math.sqrt(p.distanceToSqr(h));
        if (d > max) {
            Vec3 back = h.position().add(p.position().subtract(h.position()).normalize().scale(max * 0.95));
            p.teleportTo(back.x, Math.max(back.y, h.getY()), back.z);
            p.displayClientMessage(Component.literal("Your soul can't go any further from Mikiko").withStyle(ChatFormatting.RED), true);
        } else if (d > max * 0.9 && p.tickCount % 40 == 0) {
            p.displayClientMessage(Component.literal("Getting far from Mikiko (" + (int) d + "/" + (int) max + ")").withStyle(ChatFormatting.YELLOW), true);
        }
        if (p.tickCount % 20 == 0) com.xenowarriors.soul.SoulSplit.addXp(p, 0.5F);
    }

    /** The soul hits half as hard; a beaten soul goes back into the blade instead of dying. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer a && state(a) == SOUL) event.setAmount(event.getAmount() * 0.5F);
        if (event.getEntity() instanceof ServerPlayer p && state(p) == SOUL && event.getAmount() >= p.getHealth()) {
            ServerPlayer h = host(p);
            if (h == null) return;
            event.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            enterBlade(p, h);
            tag(p).putLong("xw_kaeru_cd", p.serverLevel().getGameTime() + SOUL_COOLDOWN);
            p.displayClientMessage(Component.literal("Your soul was beaten and went back to the Hekublade (60s to recover)").withStyle(ChatFormatting.RED), false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && state(p) != OUT) stepOut(p, OUT);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && state(p) != OUT) stepOut(p, OUT);
    }
}

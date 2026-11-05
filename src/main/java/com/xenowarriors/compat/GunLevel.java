package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gun level: every character has one, per character, raised by hitting things with TACZ guns (XP = damage dealt, a
 * bonus per kill), with the guns Mikiko and Rhalix summon from their weapons (no TACZ needed) and - when TACZ isn't
 * installed - with bows and crossbows (half XP). Each level adds a little gun damage (+0.2%, up to +150%), and at
 * level {@link #INFINITE} the magazine never runs dry. Technica is the Sky Patrol's gunsmith: her gun level starts at
 * {@link #TECHNICA_START} and her guns hit {@link #TECHNICA_BONUS} harder on top.
 * <p>Bots the same: a bot's gun level is its character's (kept in its owner's save - the one the owner gets playing
 * it), its TACZ shots (or bow shots without TACZ) get the same damage bonus and train it the same way. (Bots already
 * reload for free, so the unlimited-ammo perk changes nothing for them.)
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GunLevel {
    public static final int MAX = 30000, INFINITE = 500, TECHNICA_START = 100;
    public static final float PER_LEVEL = 0.002F, MAX_BONUS = 1.5F, TECHNICA_BONUS = 0.25F, KILL_XP = 5F;
    private static final String LV = "xw_gun_lv_", XP = "xw_gun_xp_", MAG = "xw_mag_max";

    private GunLevel() {}

    public static boolean on() { return TaczGuns.available(); }

    private static CompoundTag keep(Player p) {
        var root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static String key(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        String k = t.getId();
        if (t == CharacterType.YOU) k += p.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> String.valueOf(d.youSlot)).orElse("0");
        return k;
    }

    public static int start(CharacterType t) { return t == CharacterType.TECHNICA ? TECHNICA_START : 1; }

    public static int level(Player p) {
        CompoundTag k = keep(p);
        String lk = LV + key(p);
        return k.contains(lk) ? k.getInt(lk) : start(ModCapabilities.getType(p));
    }

    public static void setLevel(ServerPlayer p, int level) {
        keep(p).putInt(LV + key(p), Math.max(1, Math.min(MAX, level)));
        keep(p).putFloat(XP + key(p), 0F);
        sync(p);
    }

    public static float xp(Player p) { return keep(p).getFloat(XP + key(p)); }

    /** XP from one level to the next. */
    public static float needed(int level) { return 10F + level * 0.5F; }

    /** Damage multiplier for this player's gun hits. */
    public static float multiplier(Player p) {
        float m = 1F + Math.min(MAX_BONUS, level(p) * PER_LEVEL);
        if (ModCapabilities.getType(p) == CharacterType.TECHNICA) m += TECHNICA_BONUS;
        return m;
    }

    // ---------------------------------------------------------------- bots

    /** A bot's gun level (its character's, in its owner's save). */
    public static int level(com.xenowarriors.bot.PartyBotEntity b) {
        ServerPlayer o = b.owner();
        if (o == null) return start(b.character());
        String lk = LV + b.character().getId();
        return keep(o).contains(lk) ? keep(o).getInt(lk) : start(b.character());
    }

    public static float multiplier(com.xenowarriors.bot.PartyBotEntity b) {
        float m = 1F + Math.min(MAX_BONUS, level(b) * PER_LEVEL);
        if (b.character() == CharacterType.TECHNICA) m += TECHNICA_BONUS;
        return m;
    }

    public static void addXp(com.xenowarriors.bot.PartyBotEntity b, float amount) {
        ServerPlayer o = b.owner();
        if (o == null || amount <= 0 || b.journey()) return;
        int lv = level(b);
        if (lv >= MAX) return;
        String k = b.character().getId();
        float xp = keep(o).getFloat(XP + k) + amount;
        int before = lv;
        while (lv < MAX && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        keep(o).putInt(LV + k, lv);
        keep(o).putFloat(XP + k, xp);
        if (lv != before) o.displayClientMessage(Component.literal("").append(b.label()).append(Component.literal(": gun level " + lv)).withStyle(ChatFormatting.GOLD), true);
    }

    /** (Tests) a bot's shot: what it does after its gun level, and the XP it earns. */
    public static float botShot(com.xenowarriors.bot.PartyBotEntity b, net.minecraft.world.entity.LivingEntity target, float amount) {
        float a = amount * multiplier(b);
        addXp(b, Math.min(a, target.getHealth() + 1F));
        return a;
    }

    private static com.xenowarriors.bot.PartyBotEntity botShooter(net.minecraft.world.damagesource.DamageSource src) {
        if (!(src.getEntity() instanceof com.xenowarriors.bot.PartyBotEntity b)) return null;
        if (on()) return TaczGuns.isBullet(src.getDirectEntity()) ? b : null;
        return src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow ? b : null;
    }

    public static void addXp(ServerPlayer p, float amount) {
        if (amount <= 0 || p.isCreative() || p.isSpectator() || ModCapabilities.getType(p) == CharacterType.NONE) return;
        int lv = level(p);
        if (lv >= MAX) return;
        float xp = xp(p) + amount;
        int before = lv;
        while (lv < MAX && xp >= needed(lv)) { xp -= needed(lv); lv++; }
        keep(p).putInt(LV + key(p), lv);
        keep(p).putFloat(XP + key(p), xp);
        if (lv != before) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
            p.displayClientMessage(Component.literal("Gun level " + lv + (lv >= INFINITE && before < INFINITE ? " - unlimited ammo!" : ""))
                    .withStyle(ChatFormatting.GOLD), true);
            sync(p);
        }
    }

    public static void sync(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        int lv = level(p);
        com.xenowarriors.network.ModNetwork.sendToPlayer(new com.xenowarriors.network.GunLevelPacket(lv, Math.min(1F, xp(p) / needed(lv))), p);
    }

    private static ServerPlayer shooter(net.minecraft.world.damagesource.DamageSource src) {
        return TaczGuns.isBullet(src.getDirectEntity()) && src.getEntity() instanceof ServerPlayer p ? p : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        com.xenowarriors.bot.PartyBotEntity bot = botShooter(event.getSource());
        if (bot != null && bot != event.getEntity()) {                            // a bot's shot: its own gun level (bows: XP only, half, as for players)
            if (on()) event.setAmount(botShot(bot, event.getEntity(), event.getAmount()));
            else addXp(bot, Math.min(event.getAmount(), event.getEntity().getHealth() + 1F) * 0.5F);
            return;
        }
        if (!on()) {
            // without TACZ: bows and crossbows train it instead (half XP), so the summoned Hekublade / wire guns
            // (gun level 50) can still be reached - those guns add their own XP (FormGunItem)
            if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow
                    && event.getSource().getEntity() instanceof ServerPlayer a && a != event.getEntity())
                addXp(a, Math.min(event.getAmount(), event.getEntity().getHealth() + 1F) * 0.5F);
            return;
        }
        ServerPlayer p = shooter(event.getSource());
        if (p == null || p == event.getEntity()) return;
        float m = multiplier(p);
        event.setAmount(event.getAmount() * m);
        addXp(p, Math.min(event.getAmount(), event.getEntity().getHealth() + 1F));
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!on()) return;
        com.xenowarriors.bot.PartyBotEntity bot = botShooter(event.getSource());
        if (bot != null) addXp(bot, KILL_XP);
        ServerPlayer p = shooter(event.getSource());
        if (p != null) addXp(p, KILL_XP);
    }

    /** Unlimited ammo: the magazine is kept as full as it's ever been seen. */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 2 != 0) return;
        if (p.tickCount % 100 == 0) sync(p);                       // (keeps the shown level right after a character switch)
        if (!on()) return;
        ItemStack g = p.getMainHandItem();
        if (!TaczGuns.isGun(g)) return;
        int ammo = TaczGuns.ammo(g);
        if (ammo < 0) return;
        CompoundTag tag = g.getOrCreateTag();
        int max = tag.getInt(MAG);
        if (ammo > max) { tag.putInt(MAG, ammo); return; }
        if (ammo < max && level(p) >= INFINITE) TaczGuns.setAmmo(g, max);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    public static void onCharacterChanged(ServerPlayer p) { sync(p); }
}

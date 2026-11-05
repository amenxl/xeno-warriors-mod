package com.xenowarriors.forms;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.network.ModNetwork;
import com.xenowarriors.network.WeaponFormsPacket;
import com.xenowarriors.network.WireFxPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Weapon forms: Mikiko turns his Hekublade into guns, tools and the Heku-umbrella; Rhalix (Season 2) makes a whip,
 * a sword, guns and tools out of his wires. Tap the summon key: summon / put away the weapon. Hold it: the wheel of
 * forms opens while power gathers; release on a form to summon it - fully charged (2 seconds) it's the powered-up
 * summon (+damage for 30 seconds), like the plain hold-summon always was.
 * <p>
 * Forms only exist while summoned (can't be dropped, stored or kept by anyone else) and summoning one sends the
 * others away. Guns have infinite ammo but wear out; when one breaks there's a wait before the next.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WeaponForms {
    public static final int CHARGE = 40, GUN_BREAK_COOLDOWN = 30 * 20, MAX_HOLD = 30 * 20;
    public static final String BROKEN = "xw_gun_broken_until";
    /** Test counters / switches. */
    public static int SHOTS, HITS;
    public static boolean testWear;
    private static final Map<UUID, Integer> RHALIX_CHANNEL = new HashMap<>();
    private static final Map<UUID, String> SENT = new HashMap<>();

    private WeaponForms() {}

    // ------------------------------------------------------------------ who and what

    /** HEKU for Mikiko, WIRE for Rhalix once Season 2 is unlocked, otherwise null. */
    public static Form.Style style(Player p) {
        CharacterType t = ModCapabilities.getType(p);
        if (t == CharacterType.MIKIKO) return Form.Style.HEKU;
        if (t == CharacterType.RHALIX && p instanceof ServerPlayer sp && com.xenowarriors.character.Seasons.unlocked(sp.server, 2)) return Form.Style.WIRE;
        return null;
    }

    public static int weaponLevel(ServerPlayer p) {
        Form.Style s = style(p);
        if (s == Form.Style.HEKU) return com.xenowarriors.mikiko.BladeLevel.level(p);
        if (s == Form.Style.WIRE) return com.xenowarriors.rhalix.WireLevel.level(p);
        return 0;
    }

    public static boolean unlocked(ServerPlayer p, Form f) {
        Form.Style s = style(p);
        return s != null && f.has(s) && f.meets(weaponLevel(p), com.xenowarriors.stats.Mining.level(p), com.xenowarriors.compat.GunLevel.level(p));
    }

    public static int mask(ServerPlayer p) {
        int m = 0;
        for (Form f : Form.values()) if (unlocked(p, f)) m |= 1 << f.ordinal();
        return m;
    }

    public static long gunCooldownLeft(ServerPlayer p) {
        return Math.max(0, p.getPersistentData().getLong(BROKEN) - p.serverLevel().getGameTime());
    }

    // ------------------------------------------------------------------ summoning

    /** Summons a form (sending away whatever form is out). Returns false (with a message) if it can't. */
    public static boolean summon(ServerPlayer p, Form f, boolean powered) {
        Form.Style s = style(p);
        if (s == null || f == null || !f.has(s) || p.isSpectator() || com.xenowarriors.party.Duels.cantAct(p)) return false;
        if (!unlocked(p, f)) {
            p.displayClientMessage(Component.literal(f.label + " is locked: needs " + f.requirement(s)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (f.gun() && gunCooldownLeft(p) > 0) {
            p.displayClientMessage(Component.literal("Your last gun broke - a new one in " + (gunCooldownLeft(p) + 19) / 20 + "s").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (s == Form.Style.HEKU && com.xenowarriors.mikiko.RopeBlade.blockSummon(p)) return false;
        removeForms(p.getInventory());
        if (f == Form.BLADE) {
            if (!p.getInventory().contains(new ItemStack(com.xenowarriors.item.ModItems.HEKUBLADE.get()))) com.xenowarriors.mikiko.Hekublade.toggle(p);
        } else {
            if (s == Form.Style.HEKU) com.xenowarriors.mikiko.Hekublade.removeAll(p.getInventory());
            ItemStack stack = FormItems.stack(s, f);
            if (stack.isEmpty()) return false;
            stack.getOrCreateTag().putInt(FormTools.LV, weaponLevel(p));
            if (f.gun()) {
                int d = FormGunItem.durabilityFor(com.xenowarriors.compat.GunLevel.level(p));
                stack.getTag().putInt(FormGunItem.DUR, Math.max(1, d - wornShots(p, f)));
                stack.getTag().putInt(FormGunItem.DUR_MAX, d);
            }
            if (!com.xenowarriors.mikiko.Hekublade.place(p, stack)) return false;
            summonFx(p, s);
        }
        if (powered) powerUp(p, s);
        sync(p, true);
        return true;
    }

    private static void summonFx(ServerPlayer p, Form.Style s) {
        ServerLevel level = p.serverLevel();
        if (s == Form.Style.HEKU) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), com.xenowarriors.ModSounds.HEKUBLADE_SUMMON.get(), SoundSource.PLAYERS, 0.9F, 1.15F);
            ModNetwork.sendToTrackingAndSelf(new com.xenowarriors.network.HekubladeFxPacket(p.getId()), p);
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1F, 0.7F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 1F, 0.6F);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.2F, 0.9F), 0.9F), p.getX(), p.getY() + 1.1, p.getZ(), 24, 0.35, 0.4, 0.35, 0);
            ModNetwork.sendToTrackingAndSelf(new WireFxPacket(p.getId(), WireFxPacket.SUMMON, -1, 0), p);
        }
    }

    /** The powered-up summon: +damage (Strength II) for 30 seconds. */
    public static void powerUp(ServerPlayer p, Form.Style s) {
        if (s == Form.Style.HEKU) { com.xenowarriors.mikiko.BladeChannel.powerUp(p); return; }
        ServerLevel level = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, com.xenowarriors.mikiko.BladeChannel.BOOST, 1, false, true, true));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.4, p.getZ(), 40, 0.5, 0.6, 0.5, 0.08);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.6F, 0.15F, 1F), 1.6F), p.getX(), p.getY() + 1.2, p.getZ(), 30, 0.6, 0.8, 0.6, 0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 1.3F);
        p.displayClientMessage(Component.literal("Wire power: +damage for 30s").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Takes every summoned form out of an inventory (not the Hekublade itself). */
    public static int removeForms(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (FormItems.isForm(c.getItem(i))) {
            if (c instanceof Inventory inv && inv.player instanceof ServerPlayer sp) rememberWear(sp, c.getItem(i));
            c.setItem(i, ItemStack.EMPTY);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ gun wear survives putting the gun away

    /** Per gun form: {"u": shots used, "t": game time put away}. */
    public static final String WEAR = "xw_gun_wear";

    /**
     * A worn gun put away (or swapped for another form) comes back just as worn - otherwise put away + summon again
     * was a free repair and the broken-gun wait never came. Rested as long as that wait, it's fresh again.
     */
    static void rememberWear(ServerPlayer p, ItemStack st) {
        if (!(st.getItem() instanceof FormGunItem g) || !st.hasTag() || !st.getTag().contains(FormGunItem.DUR)) return;
        int used = FormGunItem.durMax(st) - FormGunItem.dur(st);
        net.minecraft.nbt.CompoundTag all = p.getPersistentData().getCompound(WEAR);
        if (used <= 0 || FormGunItem.dur(st) <= 0) all.remove(g.form().name());          // (fresh, or broken: the wait is its own price)
        else {
            net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
            e.putInt("u", used);
            e.putLong("t", p.serverLevel().getGameTime());
            all.put(g.form().name(), e);
        }
        p.getPersistentData().put(WEAR, all);
    }

    /** Shots already used on this gun form (0 once it has rested {@link #GUN_BREAK_COOLDOWN}). */
    public static int wornShots(ServerPlayer p, Form f) {
        net.minecraft.nbt.CompoundTag e = p.getPersistentData().getCompound(WEAR).getCompound(f.name());
        if (e.isEmpty()) return 0;
        long since = p.serverLevel().getGameTime() - e.getLong("t");
        return since < 0 || since >= GUN_BREAK_COOLDOWN ? 0 : e.getInt("u");
    }

    public static boolean anyOut(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (FormItems.isForm(p.getInventory().getItem(i))) return true;
        return false;
    }

    /** Rhalix's tap of the summon key: wires out / everything away. (Mikiko's goes through Hekublade.toggle.) */
    public static void tap(ServerPlayer p) {
        Form.Style s = style(p);
        if (s == null) return;
        if (s == Form.Style.HEKU) { com.xenowarriors.mikiko.Hekublade.toggle(p); return; }
        if (removeForms(p.getInventory()) > 0) { awayFx(p, s); sync(p, true); return; }
        summon(p, Form.WIRES, false);
    }

    public static void awayFx(ServerPlayer p, Form.Style s) {
        ServerLevel level = p.serverLevel();
        if (s == Form.Style.WIRE) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.7F, 1.2F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.1, p.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
        } else {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
            level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1.1, p.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** A gun ran out of durability: it's gone, and no new gun for a while. */
    public static void gunBroke(ServerPlayer p, ItemStack stack) {
        removeForms(p.getInventory());
        p.getPersistentData().putLong(BROKEN, p.serverLevel().getGameTime() + GUN_BREAK_COOLDOWN);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1F, 0.8F);
        p.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        p.displayClientMessage(Component.literal("Your gun broke! A new one in " + GUN_BREAK_COOLDOWN / 20 + "s").withStyle(ChatFormatting.RED), true);
        sync(p, true);
    }

    /** Tells the player about forms a weapon level-up just opened. */
    public static void announceUnlocks(ServerPlayer p, int before, int after) {
        Form.Style s = style(p);
        if (s == null) return;
        int mining = com.xenowarriors.stats.Mining.level(p), gun = com.xenowarriors.compat.GunLevel.level(p);
        for (Form f : Form.wheel(s))
            if (!f.meets(before, mining, gun) && f.meets(after, mining, gun))
                p.sendSystemMessage(Component.literal("New form: " + f.label + " (hold the summon key to pick it)").withStyle(ChatFormatting.GOLD));
        sync(p, true);
    }

    // ------------------------------------------------------------------ the hold wheel

    /** The wheel opened: power starts gathering (Mikiko raises the Hekublade as before). */
    public static void open(ServerPlayer p) {
        Form.Style s = style(p);
        if (s == null || com.xenowarriors.party.Duels.cantAct(p)) return;
        if (s == Form.Style.HEKU) com.xenowarriors.mikiko.BladeChannel.openWheel(p);
        else {
            RHALIX_CHANNEL.put(p.getUUID(), p.tickCount);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 1.2F);
        }
    }

    public static boolean charged(ServerPlayer p) {
        Form.Style s = style(p);
        if (s == Form.Style.HEKU) return com.xenowarriors.mikiko.BladeChannel.charged(p);
        Integer t = RHALIX_CHANNEL.get(p.getUUID());
        return t != null && p.tickCount - t >= CHARGE;
    }

    /** Released on a form: summon it (powered if the charge was full). */
    public static void pick(ServerPlayer p, Form f) {
        boolean full = charged(p);
        close(p);
        summon(p, f, full);
    }

    public static void close(ServerPlayer p) {
        RHALIX_CHANNEL.remove(p.getUUID());
        com.xenowarriors.mikiko.BladeChannel.closeWheel(p);
    }

    // ------------------------------------------------------------------ upkeep

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        FormGunItem.tickAim(p);
        Integer ch = RHALIX_CHANNEL.get(p.getUUID());
        if (ch != null) {
            int t = p.tickCount - ch;
            if (!p.isAlive() || t > MAX_HOLD) RHALIX_CHANNEL.remove(p.getUUID());
            else if (t % 3 == 0) {
                double a = t * 0.5, h = Math.min(1.0, t / (double) CHARGE) * 2.0;
                p.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.6F, 0.2F, 1F), 1.0F),
                        p.getX() + Math.cos(a) * 0.8, p.getY() + 0.2 + h, p.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
                if (t == CHARGE || t == CHARGE + 1 || t == CHARGE + 2) {
                    p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.5, p.getZ(), 20, 0.4, 0.5, 0.4, 0.05);
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 0.7F);
                }
            }
        }
        if (p.tickCount % 10 != 0) return;
        Form.Style s = style(p);
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof FormItems.Summoned f && f.style() != s) inv.setItem(i, ItemStack.EMPTY);   // not theirs (or Season 2 locked again)
        }
        if (p.containerMenu.getCarried().getItem() instanceof FormItems.Summoned f && f.style() != s) p.containerMenu.setCarried(ItemStack.EMPTY);
        sync(p, p.tickCount % 200 == 0);
    }

    /** Sends the wheel state when it changed (or when forced). */
    public static void sync(ServerPlayer p, boolean force) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        Form.Style s = style(p);
        int lv = weaponLevel(p);
        float prog = s == Form.Style.HEKU ? com.xenowarriors.mikiko.BladeLevel.progress(p) : s == Form.Style.WIRE ? com.xenowarriors.rhalix.WireLevel.progress(p) : 0F;
        long cd = gunCooldownLeft(p);
        WeaponFormsPacket pk = new WeaponFormsPacket(s == null ? -1 : s.ordinal(), mask(p), lv, prog, (int) cd);
        String key = pk.style() + ":" + pk.mask() + ":" + lv + ":" + Math.round(prog * 50) + ":" + (cd > 0 ? (cd / 20) : 0);
        if (!force && key.equals(SENT.get(p.getUUID()))) return;
        SENT.put(p.getUUID(), key);
        ModNetwork.sendToPlayer(pk, p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        RHALIX_CHANNEL.remove(event.getEntity().getUUID());
        FormGunItem.forget(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ can't be dropped, stored or hung up

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (FormItems.isForm(event.getEntity().getItem()) && event.getPlayer() instanceof ServerPlayer p) {
            event.setCanceled(true);
            Form.Style s = ((FormItems.Summoned) event.getEntity().getItem().getItem()).style();
            awayFx(p, s);
            sync(p, true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(item -> FormItems.isForm(item.getItem()));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && FormItems.isForm(item.getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOpenTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.containerMenu == p.inventoryMenu) return;
        for (Slot slot : p.containerMenu.slots)
            if (!(slot.container instanceof Inventory) && FormItems.isForm(slot.getItem())) slot.set(ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        for (Slot slot : event.getContainer().slots)
            if (!(slot.container instanceof Inventory) && FormItems.isForm(slot.getItem())) slot.set(ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onHang(PlayerInteractEvent.EntityInteractSpecific event) {
        if ((event.getTarget() instanceof net.minecraft.world.entity.decoration.ArmorStand || event.getTarget() instanceof net.minecraft.world.entity.decoration.ItemFrame)
                && FormItems.isForm(event.getItemStack())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFrame(PlayerInteractEvent.EntityInteract event) {
        if ((event.getTarget() instanceof net.minecraft.world.entity.decoration.ArmorStand || event.getTarget() instanceof net.minecraft.world.entity.decoration.ItemFrame)
                && FormItems.isForm(event.getItemStack())) event.setCanceled(true);
    }
}

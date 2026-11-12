package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * With DragonMineZ: a bot with DragonMineZ stat capsules in its bag uses them on its own DragonMineZ character, one
 * every few seconds - each gives its stat points exactly as it would for a player (DragonMineZ's own capsule code, its
 * caps and its "maxed" rule). A capsule for a stat it's maxed on stays in its bag. Never on the character you're
 * playing, and not while its Dragon Ball menu is open (then they're yours to use). All by reflection.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzBotCapsules {
    private DmzBotCapsules() {}

    private static Class<?> capsuleClass;
    private static Method apply, cfg;
    private static boolean broken;
    /** (Tests) capsules bots used. */
    public static int used;

    private static boolean ready() {
        if (broken) return false;
        if (apply != null) return true;
        if (!DmzBotQuests.ready()) return false;
        try {
            capsuleClass = Class.forName("com.dragonminez.common.init.item.consumables.CapsuleItem");
            for (Method m : capsuleClass.getDeclaredMethods()) if (m.getName().equals("applyCapsuleStats")) { m.setAccessible(true); apply = m; }
            if (apply == null) { broken = true; return false; }
            return true;
        } catch (Throwable t) {
            broken = true;
            XenoWarriors.LOGGER.warn("DragonMineZ capsules for bots aren't available", t);
            return false;
        }
    }

    public static boolean capsule(ItemStack s) { return ready() && capsuleClass.isInstance(s.getItem()); }

    /** The stats a capsule gives (DragonMineZ's config: its stat names, split by its separator). */
    private static String[] stats(Object item) throws Exception {
        Object conf = Class.forName("com.dragonminez.common.config.ConfigManager").getMethod("getServerConfig").invoke(null);
        Object caps = DmzBotQuests.m(DmzBotQuests.m(conf, "getGameplay"), "getCapsules");
        java.lang.reflect.Field tf = capsuleClass.getDeclaredField("type");
        tf.setAccessible(true);
        Object values = DmzBotQuests.m(caps, "getCapsuleValues", tf.get(item));
        String sep = (String) DmzBotQuests.m(caps, "getStatSeparator");
        return ((String) DmzBotQuests.m(values, "getStats")).split(java.util.regex.Pattern.quote(sep));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p instanceof FakePlayer || p.tickCount % 60 != 29 || !ready()) return;
        for (PartyBotEntity b : PartyBots.bots(p)) {
            if (b.journey() || b.downed()) continue;
            try { useOne(b); } catch (Throwable t) { XenoWarriors.LOGGER.warn("A bot couldn't use a DragonMineZ capsule", t); }
        }
    }

    /** Uses one capsule from its bag (if any helps). True if it did. */
    public static boolean useOne(PartyBotEntity b) throws Exception {
        if (!ready()) return false;
        int slot = -1;
        for (int i = 0; i < b.inventory.getContainerSize() && slot < 0; i++) if (capsuleClass.isInstance(b.inventory.getItem(i).getItem())) slot = i;
        if (slot < 0) return false;
        DmzBotQuests.Data d = DmzBotQuests.open(b, false);
        if (d == null || d.live) return false;                                   // (no character yet, or its menu is open: yours to use)
        ItemStack stack = b.inventory.getItem(slot);
        for (int i = slot; i < b.inventory.getContainerSize(); i++) {
            ItemStack s = b.inventory.getItem(i);
            if (!capsuleClass.isInstance(s.getItem())) continue;
            boolean gave = false;
            StringBuilder what = new StringBuilder();
            for (String stat : stats(s.getItem())) {
                ItemStack one = s.copyWithCount(1);
                Object msg = apply.invoke(s.getItem(), one, d.sd, stat.trim());
                if (one.isEmpty()) { gave = true; if (msg instanceof Component c) what.append(what.length() > 0 ? ", " : "").append(c.getString()); }
            }
            if (!gave) continue;                                                  // (maxed in that: it keeps it)
            s.shrink(1);
            b.inventory.setChanged();
            d.touched.add("Stats"); d.touched.add("Resources");
            d.commit();
            used++;
            float before = b.getMaxHealth();
            DmzBotStats.apply(b);
            float gained = b.getMaxHealth() - before;
            if (gained > 0 && b.isAlive()) b.heal(gained);
            if (b.level() instanceof ServerLevel l) {
                l.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, b.getX(), b.getY() + 1, b.getZ(), 12, 0.4, 0.6, 0.4, 0.1);
                l.playSound(null, b.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.4F);
            }
            ServerPlayer o = b.owner();
            if (o != null) o.displayClientMessage(Component.empty().append(b.label()).append(" used a capsule: " + what).withStyle(ChatFormatting.GREEN), true);
            PartyBots.testLog(b, "capsule: " + what);
            return true;
        }
        return false;
    }
}

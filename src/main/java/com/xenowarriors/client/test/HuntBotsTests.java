package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.party.Bonds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.*;

/**
 * Bug-hunt checks for bots and parties (focus "huntbots", "dmzhuntbots" with DragonMineZ). Each case logs
 * "XW-TEST huntbots ... -> PASS/FAIL":
 * <ol>
 *   <li>relog: a bot whose bond hearts come from another bot keeps its health (it spawns before that bot is back);</li>
 *   <li>a downed bot whose owner goes to another dimension isn't brought back up for free;</li>
 *   <li>a bot waiting outside (the waiting list) still counts towards the party limit when adding bots;</li>
 *   <li>per-bot static maps are emptied when bots go (revive progress, team-ultimate asks).</li>
 * </ol>
 */
public final class HuntBotsTests {
    private HuntBotsTests() {}

    public static boolean handles(String focus) { return focus != null && focus.replaceFirst("^dmz", "").startsWith("huntbots"); }

    private static void log(String s) { UiTest.log("huntbots " + s); }

    private static String f(float v) { return String.format(Locale.ROOT, "%.1f", v); }

    private static PartyBotEntity bot(ServerPlayer p, CharacterType t) {
        for (PartyBotEntity b : PartyBots.bots(p)) if (b.character() == t) return b;
        return null;
    }

    private static void clear(ServerPlayer p) {
        for (PartyBotEntity b : new ArrayList<>(PartyBots.bots(p))) { PartyBots.checkIn(b, p); PartyBots.forgetFake(b.getUUID()); b.discard(); }
        for (CharacterType t : CharacterType.PLAYABLE) p.getPersistentData().remove("xw_bot_rejoin_" + t.getId());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> c, String name) {
        try { var f = c.getDeclaredField(name); f.setAccessible(true); return (T) f.get(null); } catch (Throwable t) { log("reflect " + name + " failed: " + t); return null; }
    }

    private static int mapSize(Class<?> c, String name, UUID key) {
        Map<UUID, ?> m = field(c, name);
        return m == null ? -1 : m.containsKey(key) ? 1 : 0;
    }

    static void fill(ServerLevel l, int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.state.BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) l.setBlock(new BlockPos(x, y, z), s, 3);
    }

    private static final float[] HP = new float[4];
    private static final Object[] HOME = new Object[1];
    private static final UUID[] GONE = new UUID[1];

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO);
            UiTest.server(p -> { p.setGameMode(GameType.SURVIVAL); p.setHealth(p.getMaxHealth()); HOME[0] = p.position(); }); }, 40));

        // ---- 1. relog: bond hearts that come from another bot
        l.add(new Step(null, () -> UiTest.server(p -> {
            clear(p);
            Bonds.set(p, "rhalix", 0, 0); Bonds.set(p, "kaeru", 0, 0);
            PartyBots.spawn(p, CharacterType.RHALIX, null);
            PartyBots.spawn(p, CharacterType.KAERU, null);
        }), 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity r = bot(p, CharacterType.RHALIX), k = bot(p, CharacterType.KAERU);
            if (r == null || k == null) { log("relog FAIL no bots"); return; }
            Bonds.setBot(k, "rhalix", 0, 0);
            Bonds.setBot(r, "mikiko", 0, 0);
            Bonds.setBot(r, "kaeru", 100, 0);                                   // Rhalix's hearts come from Kaeru (another bot), not the owner
            r.setHealth(r.getMaxHealth() - 2F);
            HP[0] = r.getHealth(); HP[1] = r.getMaxHealth();
            log("relog before: rhalix hp=" + f(HP[0]) + "/" + f(HP[1]) + " best bond=" + Bonds.best(r) + " (with kaeru) level hp=" + f((float) r.getAttribute(Attributes.MAX_HEALTH).getBaseValue()));
            PartyBots.onLogout(new PlayerEvent.PlayerLoggedOutEvent(p));
            log("relog out: bots now=" + PartyBots.bots(p).size());
            PartyBots.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
            PartyBotEntity n = bot(p, CharacterType.RHALIX);
            log("relog back (same tick): rhalix hp=" + (n == null ? "-" : f(n.getHealth()) + "/" + f(n.getMaxHealth())));
        }), 40));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity n = bot(p, CharacterType.RHALIX);
            if (n == null) { log("relog FAIL no rhalix after relog"); return; }
            // (its saved health back - not cut to its level health, not refilled; with DragonMineZ its max can grow a little meanwhile - a DMZ character made for it - and it regenerates a bit)
            boolean ok = n.getMaxHealth() >= HP[1] - 0.01F && n.getHealth() >= HP[0] - 0.5F && n.getHealth() <= HP[0] + Math.max(0.5F, n.getMaxHealth() * 0.01F);
            log("relog after 2 s: rhalix hp=" + f(n.getHealth()) + "/" + f(n.getMaxHealth()) + " (was " + f(HP[0]) + "/" + f(HP[1]) + ") -> " + (ok ? "PASS" : "FAIL"));
        }), 10));

        // ---- 2. a downed bot and a trip to the Nether
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            if (k == null) { log("downdim FAIL no kaeru"); return; }
            k.inventory.addItem(new ItemStack(Items.EMERALD, 7));
            GONE[0] = k.getUUID();
            k.die(k.damageSources().generic());
            log("downdim: kaeru downed=" + k.downed() + " hp=" + f(k.getHealth()) + " revive progress tracked=" + mapSize(com.xenowarriors.party.ReviveCircle.class, "BOT_PROGRESS", k.getUUID()));
            ServerLevel n = p.server.getLevel(Level.NETHER);
            if (n == null) { log("downdim: no nether"); return; }
            fill(n, -3, 100, -3, 3, 104, 3, Blocks.AIR.defaultBlockState());
            fill(n, -3, 99, -3, 3, 99, 3, Blocks.OBSIDIAN.defaultBlockState());
            p.teleportTo(n, 0.5, 100, 0.5, 0, 10);
        }), 60));
        l.add(new Step(null, () -> UiTest.server(p -> {
            PartyBotEntity k = bot(p, CharacterType.KAERU);
            long rejoin = p.getPersistentData().getLong("xw_bot_rejoin_kaeru") - p.level().getGameTime();
            int saved = 0;
            CompoundTag inv = com.xenowarriors.character.CharacterInventories.saved(p, "kaeru");
            var list = inv.getList("inv", 10);
            for (int i = 0; i < list.size(); i++) { ItemStack s = ItemStack.of(list.getCompound(i)); if (s.is(Items.EMERALD)) saved += s.getCount(); }
            boolean freeRevive = k != null && !k.downed();
            log("downdim in nether: kaeru bot=" + (k != null) + (k == null ? "" : " downed=" + k.downed() + " hp=" + f(k.getHealth()) + " in " + k.level().dimension().location().getPath())
                    + " recovering=" + (rejoin > 0 ? rejoin / 20 + "s" : "no") + " emeralds home=" + saved + " -> " + (freeRevive ? "FAIL (back up for free)" : "PASS"));
            log("maps: revive progress for the gone kaeru=" + mapSize(com.xenowarriors.party.ReviveCircle.class, "BOT_PROGRESS", GONE[0])
                    + " team-ult asks=" + mapSize(com.xenowarriors.bot.BotTeamUlt.class, "NEXT_ASK", GONE[0]) + " -> " + (mapSize(com.xenowarriors.party.ReviveCircle.class, "BOT_PROGRESS", GONE[0]) == 0 ? "PASS" : "FAIL"));
            var h = (net.minecraft.world.phys.Vec3) HOME[0];
            p.teleportTo(p.server.overworld(), h.x, h.y, h.z, 0, 10);
        }), 80));

        // ---- 3. the waiting list counts towards the party limit
        l.add(new Step(null, () -> UiTest.server(p -> {
            clear(p);
            PartyBots.spawn(p, CharacterType.RHALIX, null);
            PartyBots.spawn(p, CharacterType.KAERU, null);
            List<Map.Entry<UUID, CompoundTag>> left = field(PartyBots.class, "LEFT_BEHIND");
            CompoundTag rec = new CompoundTag();
            rec.putString("char", "ultima"); rec.put("inv", new net.minecraft.nbt.ListTag()); rec.put("armour", new net.minecraft.nbt.ListTag()); rec.putFloat("hp", 20F);
            if (left != null) left.add(Map.entry(p.getUUID(), rec));
            PartyBots.add(p, new String[]{"kyrie"});
            int live = PartyBots.bots(p).size(), waiting = PartyBots.waitingFor(p.getUUID());
            log("limit: live bots=" + live + " waiting=" + waiting + " total with you=" + (1 + live + waiting) + " (max 4) -> " + (1 + live + waiting <= 4 ? "PASS" : "FAIL"));
            if (left != null) left.removeIf(e -> e.getKey().equals(p.getUUID()));
        }), 20));
        l.add(new Step(null, () -> UiTest.server(p -> {
            clear(p);
            log("DONE");
        }), 20));
    }
}

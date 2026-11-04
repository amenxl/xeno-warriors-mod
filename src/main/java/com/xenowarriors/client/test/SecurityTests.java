package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * UI tests for the persistence / exploit fixes (focus "bugs2..."):
 * bot bags that went home while the owner was away survive a save + restart and arrive once; a vehicle wrecked by two
 * hits in one tick drops one item; VR refuses a spectator; a skill slotted in creative above your level can't be used
 * in survival. Results are logged as "XW-TEST bugs2 ...".
 */
public final class SecurityTests {
    private SecurityTests() {}

    private static void log(String s) { UiTest.log("bugs2 " + s); }

    public static void build(Minecraft mc, List<Step> l, String focus) {
        l.add(new Step(null, () -> { UiTest.noToasts(mc); mc.setScreen(null); UiTest.character(CharacterType.MIKIKO); }, 40));

        // 1. bot limbo: a world save, a restart, a login (twice)
        l.add(new Step(null, () -> UiTest.server(p -> {
            try { log("limbo world file: " + com.xenowarriors.bot.BotLimbo.selfTest(p.server)); } catch (Exception e) { log("limbo world file: FAIL " + e); }
            log("limbo real owner: " + com.xenowarriors.bot.BotLimbo.testRealOwner(p, CharacterType.LEXY.getId()));
        }), 10));

        // 2. a vehicle wrecked by two big hits in the same tick drops one vehicle item, not two
        l.add(new Step(null, () -> UiTest.server(p -> {
            var kind = com.xenowarriors.vehicle.VehicleKind.CAR;
            var v = com.xenowarriors.vehicle.Vehicles.TYPES.get(kind).get().create(p.serverLevel());
            v.moveTo(p.getX() + 6, p.getY(), p.getZ(), 0, 0);
            v.setOwner(p.getUUID());
            p.serverLevel().addFreshEntity(v);
            boolean a = v.hurt(p.damageSources().generic(), 100000F), b = v.hurt(p.damageSources().generic(), 100000F);
            var item = com.xenowarriors.vehicle.VehicleItems.ITEMS_BY_KIND.get(kind).get();
            int n = 0;
            for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(v.blockPosition()).inflate(4))) if (e.getItem().is(item)) { n += e.getItem().getCount(); e.discard(); }
            log("vehicle double wreck: hits=" + a + "/" + b + " vehicle items dropped=" + n + (n == 1 ? " OK" : " FAIL"));
        }), 10));

        // 3. VR: a spectator's request is refused (sent as the real packet); a normal one goes in, then leaves
        l.add(new Step(null, () -> UiTest.server(p -> p.setGameMode(GameType.SPECTATOR)), 10));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.VrActionPacket(0, 0)), 20));
        l.add(new Step(null, () -> UiTest.server(p -> { log("vr as spectator: inVr=" + com.xenowarriors.vr.Vr.is(p.level()) + (com.xenowarriors.vr.Vr.is(p.level()) ? " FAIL" : " OK")); p.setGameMode(GameType.SURVIVAL); }), 10));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.VrActionPacket(0, 0)), 40));
        l.add(new Step("bugs2_vr_in", () -> UiTest.server(p -> log("vr in survival: inVr=" + com.xenowarriors.vr.Vr.is(p.level()) + (com.xenowarriors.vr.Vr.is(p.level()) ? " OK" : " FAIL"))), 5));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.VrActionPacket(2, 0)), 40));
        l.add(new Step(null, () -> UiTest.server(p -> log("vr left: inVr=" + com.xenowarriors.vr.Vr.is(p.level()))), 5));

        // 4. a skill above your level, slotted in creative, can't be used once you're back in survival
        int[] idx = {-1};
        l.add(new Step(null, () -> UiTest.server(p -> {
            com.xenowarriors.character.Seasons.set(p.server, 3);
            var pool = com.xenowarriors.skill.SkillPool.of(p);
            int lv = com.xenowarriors.stats.Stats.characterLevel(p);
            for (int i = 0; i < pool.size(); i++) if (pool.get(i).unlockLevel() > lv) { idx[0] = i; break; }
            p.setGameMode(GameType.CREATIVE);
            log("skill lock: level " + lv + ", picking pool #" + idx[0] + (idx[0] >= 0 ? " (" + pool.get(idx[0]).skill().getName().getString() + ", unlocks at " + pool.get(idx[0]).unlockLevel() + ")" : ""));
        }), 10));
        l.add(new Step(null, () -> { if (idx[0] >= 0) ModNetwork.sendToServer(new com.xenowarriors.network.SetSkillSlotPacket(0, idx[0])); }, 10));
        l.add(new Step(null, () -> UiTest.server(p -> p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> { d.cooldownEnd[0] = 0; com.xenowarriors.karma.Karma.add(p, 100000F); })), 5));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.UseSkillPacket(0)), 30));
        l.add(new Step(null, () -> UiTest.server(p -> p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            long now = p.server.overworld().getGameTime();
            log("skill lock creative (control): used=" + (d.cooldownEnd[0] > now) + " slot0=" + d.loadout(CharacterType.MIKIKO)[0]);
            d.cooldownEnd[0] = 0;
            p.setGameMode(GameType.SURVIVAL);
            com.xenowarriors.karma.Karma.add(p, 100000F);
        })), 60));
        l.add(new Step(null, () -> ModNetwork.sendToServer(new com.xenowarriors.network.UseSkillPacket(0)), 30));
        l.add(new Step("bugs2_skill_locked", () -> UiTest.server(p -> p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            long now = p.server.overworld().getGameTime();
            boolean used = d.cooldownEnd[0] > now;
            log("skill lock survival: used=" + used + (used ? " FAIL" : " OK"));
        })), 5));
        l.add(new Step(null, () -> log("done"), 5));
    }
}

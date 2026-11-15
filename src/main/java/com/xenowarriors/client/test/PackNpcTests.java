package com.xenowarriors.client.test;

import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * (Pack test, focus "packnpcs") The townsfolk, the possessed, the Sky Rebellion and Government soldiers on Epic Fight's
 * body: each fights a zombie - it lands its hits, its swings are Epic Fight's moves, and it's drawn by Epic Fight.
 */
final class PackNpcTests {
    private PackNpcTests() {}

    private record Case(String name, java.util.function.Function<ServerPlayer, Mob> make, ItemStack held) {}

    private static int npc = -1, dummy = -1;
    private static float hp0;
    private static final Set<String> ANIMS = new LinkedHashSet<>();

    private static String anim(LivingEntity e) {
        try {
            var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(e,
                    yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch.class);
            if (patch == null) return "no patch";
            var pl = patch.getAnimator().getPlayerFor(null);
            var a = pl == null ? null : pl.getRealAnimation();
            return a == null ? "none" : String.valueOf(a.get()).replace("animmodels/animations/", "");
        } catch (Throwable t) { return "error " + t; }
    }

    static void build(Minecraft mc, List<Step> l) {
        Case[] cases = {
                new Case("townsfolk_militia", p -> { var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(p.serverLevel()); h.makeMilitia(false); return h; }, new ItemStack(Items.IRON_SWORD)),
                new Case("townsfolk_fists", p -> { var h = com.xenowarriors.npc.ModNpcs.HUMAN.get().create(p.serverLevel()); h.makeMilitia(false); return h; }, ItemStack.EMPTY),
                new Case("possessed", p -> com.xenowarriors.npc.ModNpcs.DARK_POSSESSED.get().create(p.serverLevel()), new ItemStack(Items.IRON_AXE)),
                new Case("sky_rebel", p -> { var s = com.xenowarriors.sr.SrEntities.SR_MEMBER.get().create(p.serverLevel()); s.setup(1, p.blockPosition()); return s; }, new ItemStack(Items.IRON_SWORD)),
                new Case("gov_melee", p -> { var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel()); g.setup(false, 1, p.blockPosition()); return g; }, new ItemStack(Items.IRON_SWORD)),
                new Case("gov_gun", p -> { var g = com.xenowarriors.gov.GovEntities.GOV_AGENT.get().create(p.serverLevel()); g.setup(false, 1, p.blockPosition()); return g; }, null)};
        for (Case c : cases) one(mc, l, c);
        l.add(new Step(null, () -> { mc.options.hideGui = false; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 3));
    }

    private static void one(Minecraft mc, List<Step> l, Case c) {
        l.add(new Step(null, () -> PackTests.server(p -> {
            PackTests.arena(p); PackTests.clear(p);
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var lvl = p.serverLevel();
            var z = EntityType.ZOMBIE.create(lvl);
            z.moveTo(p.getX() + 1, p.getY(), p.getZ() + 6, 180, 0);
            z.addTag("xw_pack"); z.setPersistenceRequired(); z.setNoAi(true);
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300); z.setHealth(300);
            lvl.addFreshEntity(z);
            Mob m = c.make().apply(p);
            if (m == null) { PackTests.check("npcs " + c.name() + " spawned", false, "null"); return; }
            m.moveTo(p.getX() - 1, p.getY(), p.getZ() + 6, 0, 0);
            m.addTag("xw_pack"); m.setPersistenceRequired();
            if (c.held() != null) m.setItemInHand(InteractionHand.MAIN_HAND, c.held().copy());
            lvl.addFreshEntity(m);
            m.setTarget(z);
            npc = m.getId(); dummy = z.getId(); hp0 = z.getHealth(); ANIMS.clear();
            p.setYRot(0); p.setYHeadRot(0); p.setXRot(15);
        }), 10));
        l.add(new Step(null, () -> { mc.options.hideGui = true; mc.options.setCameraType(CameraType.FIRST_PERSON); }, 2));
        for (int t = 0; t < 30; t++) {
            int tt = t;
            l.add(new Step(tt == 12 || tt == 20 ? "pack_npcs_" + c.name() + "_" + tt : null, () -> PackTests.server(p -> {
                var m = p.serverLevel().getEntity(npc);
                var z = p.serverLevel().getEntity(dummy);
                if (m instanceof Mob mob && z instanceof LivingEntity lz) { if (mob.getTarget() != lz) mob.setTarget(lz); ANIMS.add(anim(mob)); }
            }), 5));
        }
        l.add(new Step(null, () -> PackTests.server(p -> {
            var m = p.serverLevel().getEntity(npc);
            var z = p.serverLevel().getEntity(dummy);
            float now = z instanceof LivingEntity lz ? lz.getHealth() : 0;
            boolean attacks = ANIMS.stream().anyMatch(s -> s.contains("combat/") || s.contains("auto") || s.contains("attack"));
            String held = m instanceof LivingEntity lm ? String.valueOf(lm.getMainHandItem()) : "-";
            PackTests.check("npcs " + c.name() + ": fights on Epic Fight's body (hits land, Epic Fight's moves)", m != null && now < hp0 && attacks,
                    "holding " + held + ", zombie hp " + hp0 + " -> " + now + ", animations " + ANIMS);
            if (m != null) m.discard();
            if (z != null) z.discard();
        }), 3));
    }
}

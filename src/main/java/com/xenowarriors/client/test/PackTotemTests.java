package com.xenowarriors.client.test;

import com.xenowarriors.bot.PartyBots;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.KnockdownClient;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import com.xenowarriors.compat.InventoryTotem;
import com.xenowarriors.party.Duels;
import com.xenowarriors.skill.SkillEvents;
import com.xenowarriors.skill.SkillManager;
import com.xenowarriors.skill.XenoBlast;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Pack test part 3 (run from {@link PackTests}): Inventory Totem (a Totem of Undying anywhere in the inventory saves you)
 * against our downed / revive system and the Xeno Blast (last resort). The expected result is what a totem does without
 * Xeno Warriors: it's used up (exactly one), you live on at low health with Regeneration, you're NOT downed, you can act.
 * With a totem in hand, vanilla uses that one and the inventory ones stay. Someone already down isn't hit (the totem stays).
 * Without the mod installed, the old behaviour is expected instead (downed, totem kept).
 * Logs "XW-TEST pack totem ..." through PackTests.check.
 */
final class PackTotemTests {
    private PackTotemTests() {}

    private static final String TAG = "xw_pack";
    private static int before;
    private static float hpBefore;

    private static boolean modOn() { return ModList.get().isLoaded("inventorytotem"); }

    private static int totems(ServerPlayer p) { return p.getInventory().countItem(Items.TOTEM_OF_UNDYING); }

    /** Survival, no effects (the arena's Resistance V would stop every hit), full health, empty hands, a bot teammate, no totems. */
    private static void reset(ServerPlayer p) {
        if (Duels.isDowned(p)) Duels.foxRevive(p, p);
        com.xenowarriors.bot.ReviveAftermath.testClear(p);          // (no back-up invincibility from the last case)
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        p.clearFire();
        p.setHealth(p.getMaxHealth());
        p.invulnerableTime = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) inv.setItem(i, ItemStack.EMPTY);
        inv.selected = 0;
        inv.setItem(0, ItemStack.EMPTY);
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        p.getPersistentData().remove(SkillEvents.ULT_UNTIL);
        if (PartyBots.bots(p).isEmpty()) {
            var b = PartyBots.spawn(p, CharacterType.MIKIKO, null);
            if (b != null) { b.moveTo(PackTests.HOME[0] + 6, PackTests.HOME[1], PackTests.HOME[2] - 6, 0, 0); b.getNavigation().stop(); }
        }
    }

    private static void lethal(ServerPlayer p, DamageSource src) {
        before = totems(p);
        hpBefore = p.getHealth();
        p.invulnerableTime = 0;
        p.hurt(src, 1000F);
    }

    private static String state(ServerPlayer p) {
        return "alive=" + p.isAlive() + " hp=" + PackTests.f(p.getHealth()) + " downed=" + Duels.isDowned(p) + " cantAct=" + Duels.cantAct(p)
                + " totems " + before + "->" + totems(p) + " regen=" + p.hasEffect(MobEffects.REGENERATION) + " mainhand=" + p.getMainHandItem().getItem()
                + " offhand=" + p.getOffhandItem().getItem() + " team=" + Duels.hasTeam(p) + " inventorytotem=" + modOn();
    }

    /** Saved by the inventory totem: one used, alive, not downed, free to act. (Without the mod: downed, totem kept.) */
    private static void expectSaved(ServerPlayer p, String what, int used) {
        boolean ok = modOn()
                ? p.isAlive() && p.getHealth() > 0 && !Duels.isDowned(p) && !Duels.cantAct(p) && totems(p) == before - used && p.hasEffect(MobEffects.REGENERATION)
                : p.isAlive() && Duels.isDowned(p) && totems(p) == before;
        PackTests.check("totem " + what, ok, state(p));
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> { UiTest.character(CharacterType.MIKIKO); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = false; }, 25));
        l.add(new Step(null, () -> PackTests.server(p -> { PackTests.clear(p); PackTests.arena(p); reset(p); PackTests.log("totem setup: " + state(p)); }), 20));

        // 1. one totem deep in the inventory (not the hotbar), lethal hit while on a team (would go down)
        l.add(new Step(null, () -> PackTests.server(p -> {
            reset(p);
            p.getInventory().setItem(20, new ItemStack(Items.TOTEM_OF_UNDYING));
            lethal(p, p.damageSources().generic());
        }), 3));
        l.add(new Step("pack_totem_saved", () -> PackTests.server(p -> expectSaved(p, "inventory slot, on a team", 1)), 2));
        l.add(new Step(null, () -> PackTests.client("totem client", () -> PackTests.check("totem client state", mc.player != null && mc.player.isAlive()
                && !(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) && (!modOn() || KnockdownClient.down(mc.player, 1F) == 0F),
                "alive=" + (mc.player != null && mc.player.isAlive()) + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName())
                        + " knockdown=" + (mc.player == null ? "?" : PackTests.f(KnockdownClient.down(mc.player, 1F))))), 20));

        // 2. a stack of two: exactly one used (no dupe, no double use by our handler and Inventory Totem's)
        l.add(new Step(null, () -> PackTests.server(p -> {
            reset(p);
            p.getInventory().setItem(30, new ItemStack(Items.TOTEM_OF_UNDYING, 2));
            lethal(p, p.damageSources().generic());
        }), 3));
        l.add(new Step(null, () -> PackTests.server(p -> expectSaved(p, "stack of two", 1)), 5));

        // 3. one in the offhand and one in the inventory: vanilla uses the offhand one, the inventory one stays
        l.add(new Step(null, () -> PackTests.server(p -> {
            reset(p);
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            p.getInventory().setItem(15, new ItemStack(Items.TOTEM_OF_UNDYING));
            lethal(p, p.damageSources().generic());
        }), 3));
        l.add(new Step(null, () -> PackTests.server(p -> PackTests.check("totem offhand first", p.isAlive() && !Duels.isDowned(p) && totems(p) == 1
                && p.getInventory().getItem(15).is(Items.TOTEM_OF_UNDYING) && p.getOffhandItem().isEmpty(), state(p))), 5));

        // 4. already downed (no totem then), a totem picked up, hit again: still downed, totem untouched; revived: up, totem kept
        l.add(new Step(null, () -> PackTests.server(p -> { reset(p); lethal(p, p.damageSources().generic()); PackTests.log("totem downed first: " + state(p)); }), 5));
        l.add(new Step(null, () -> PackTests.server(p -> {
            p.getInventory().setItem(22, new ItemStack(Items.TOTEM_OF_UNDYING));
            lethal(p, p.damageSources().generic());
        }), 3));
        l.add(new Step("pack_totem_downed", () -> PackTests.server(p -> PackTests.check("totem while downed", Duels.isDowned(p) && p.isAlive() && totems(p) == 1, state(p))), 3));
        l.add(new Step(null, () -> PackTests.server(p -> { Duels.foxRevive(p, p); PackTests.check("totem revived", !Duels.isDowned(p) && !Duels.cantAct(p) && p.isAlive() && totems(p) == 1, state(p)); }), 5));

        // 5. Xeno Blast (last resort) cast, an enemy lands a lethal hit mid-cutscene: the cutscene ends, the totem saves you
        l.add(new Step(null, () -> PackTests.server(p -> {
            reset(p);
            p.getPersistentData().remove("xw_karma_lock_until");
            SkillManager.resetCooldowns(p);
            com.xenowarriors.karma.Karma.add(p, 200);
            p.getInventory().setItem(25, new ItemStack(Items.TOTEM_OF_UNDYING));
            SkillManager.useSkill(p, SkillManager.SUPER_SLOT);
            PackTests.log("totem xeno blast cast: casting=" + XenoBlast.casting(p));
        }), 40));
        l.add(new Step(null, () -> PackTests.server(p -> {
            Mob z = EntityType.ZOMBIE.create(p.serverLevel());
            if (z == null) return;
            z.moveTo(PackTests.HOME[0] - 12, PackTests.HOME[1], PackTests.HOME[2] - 8, 0, 0);
            z.setNoAi(true); z.setPersistenceRequired(); z.addTag(TAG);
            p.serverLevel().addFreshEntity(z);
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); z.setHealth(200);
            boolean wasCasting = XenoBlast.casting(p);
            lethal(p, p.damageSources().mobAttack(z));
            PackTests.log("totem xeno blast hit: was casting=" + wasCasting + " now casting=" + XenoBlast.casting(p) + " " + state(p));
        }), 3));
        l.add(new Step("pack_totem_xeno", () -> PackTests.server(p -> {
            expectSaved(p, "during xeno blast", 1);
            PackTests.check("totem xeno blast not stuck", !XenoBlast.casting(p) && !com.xenowarriors.combat.Frozen.is(p)
                    && p.getPersistentData().getLong(SkillEvents.ULT_UNTIL) < p.level().getGameTime(),
                    "casting=" + XenoBlast.casting(p) + " frozen=" + com.xenowarriors.combat.Frozen.is(p) + " ultUntil>now=" + (p.getPersistentData().getLong(SkillEvents.ULT_UNTIL) >= p.level().getGameTime()));
        }), 200));
        l.add(new Step(null, () -> PackTests.server(p -> PackTests.check("totem after the blast", p.isAlive() && !Duels.cantAct(p) && !XenoBlast.casting(p) && !com.xenowarriors.combat.Frozen.is(p), state(p))), 3));
        l.add(new Step(null, () -> { mc.options.setCameraType(CameraType.FIRST_PERSON); PackTests.server(p -> { reset(p); PackTests.clear(p); PackTests.arena(p); }); }, 10));
    }
}

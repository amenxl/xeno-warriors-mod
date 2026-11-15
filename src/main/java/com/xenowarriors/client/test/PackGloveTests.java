package com.xenowarriors.client.test;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.UiTest;
import com.xenowarriors.client.UiTest.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** (Pack test, focus "packglove") Mikiko's bare-hand punches as fast as Epic Fight's glove (the user's video: bare hands slower). */
final class PackGloveTests {
    private PackGloveTests() {}

    static final float[] SPEED = new float[2];

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static float speed(net.minecraft.server.level.ServerPlayer p) {
        var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(p);
        if (patch == null) return -1;
        List c = patch.getHoldingItemCapability(InteractionHand.MAIN_HAND).getAutoAttackMotion(patch);
        if (c == null || c.isEmpty()) return -2;
        Object a = ((yesman.epicfight.api.asset.AssetAccessor) c.get(0)).get();
        if (!(a instanceof yesman.epicfight.api.animation.types.AttackAnimation attack)) return -3;
        return attack.getPlaySpeed(patch, attack);
    }

    static void build(Minecraft mc, List<Step> l) {
        l.add(new Step(null, () -> UiTest.character(CharacterType.MIKIKO), 40));
        String[] what = {"bare hands", "glove"};
        for (int i = 0; i < 2; i++) {
            int ii = i;
            l.add(new Step(null, () -> PackTests.server(p -> {
                PackTests.arena(p); PackTests.clear(p);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.getInventory().selected = 0;
                var glove = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("epicfight", "glove"));
                p.getInventory().setItem(0, ii == 0 || glove == null ? ItemStack.EMPTY : new ItemStack(glove));
            }), 20));
            l.add(new Step(null, () -> PackTests.server(p -> {
                SPEED[ii] = speed(p);
                PackTests.log("glove: " + what[ii] + " held " + p.getMainHandItem() + " attack speed " + p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)
                        + " play speed " + SPEED[ii]);
            }), 5));
        }
        l.add(new Step(null, () -> PackTests.check("glove: Mikiko's bare-hand punches play as fast as the glove's", SPEED[0] > 0 && SPEED[0] >= SPEED[1] - 0.01F,
                "bare " + SPEED[0] + " glove " + SPEED[1]), 2));
    }
}

package com.xenowarriors.compat;

import com.xenowarriors.XenoWarriors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * With Simply Swords installed: now and then a townsperson who fights, or a corrupted soul, carries one of its weapons
 * (iron mostly, sometimes gold or diamond) - and has a fair chance to drop it when beaten. With Epic Fight (Simply
 * Swords Epicfied) they fight with that weapon's moves.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SimplySwordsGear {
    private SimplySwordsGear() {}

    public static final float HUMAN_CHANCE = 0.07F, SOUL_CHANCE = 0.05F, DROP_CHANCE = 0.35F;
    private static final String[] KINDS = {"longsword", "katana", "cutlass", "rapier", "claymore", "spear", "glaive", "sai", "twinblade", "greataxe", "scythe", "warglaive"};
    private static final String TAG = "xw_simplyswords";
    /** (Tests) weapons handed out; a forced roll (null = random). */
    public static int armed;
    public static Boolean testRoll;

    private static Boolean loaded;
    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded("simplyswords");
        return loaded;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !loaded() || !(event.getEntity() instanceof Mob m)) return;
        float chance;
        if (m instanceof com.xenowarriors.npc.HumanNpcEntity h) chance = h.fighter() && !h.possessed() ? HUMAN_CHANCE : 0F;
        else if (m instanceof com.xenowarriors.corrupted.CorruptedSoulEntity) chance = SOUL_CHANCE;
        else return;
        if (m.getPersistentData().getBoolean(TAG)) return;
        m.getPersistentData().putBoolean(TAG, true);
        boolean roll = testRoll != null ? testRoll : m.getRandom().nextFloat() < chance;
        if (!roll) return;
        ItemStack w = pick(m.getRandom());
        if (w.isEmpty()) return;
        m.setItemSlot(EquipmentSlot.MAINHAND, w);
        m.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);
        armed++;
    }

    /** A random Simply Swords weapon: mostly iron, sometimes gold, now and then diamond. */
    public static ItemStack pick(net.minecraft.util.RandomSource r) {
        float t = r.nextFloat();
        String tier = t < 0.62F ? "iron" : t < 0.86F ? "gold" : "diamond";
        for (int tries = 0; tries < 6; tries++) {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("simplyswords", tier + "_" + KINDS[r.nextInt(KINDS.length)]));
            if (item != null && item != net.minecraft.world.item.Items.AIR) return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }
}

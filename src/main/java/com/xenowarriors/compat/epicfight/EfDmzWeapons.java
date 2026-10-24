package com.xenowarriors.compat.epicfight;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets;

import java.util.List;
import java.util.function.Function;

/**
 * DragonMineZ's weapons in Epic Fight, wielded differently by Mikiko and Rhalix:
 * <ul>
 *   <li>Power Pole - Mikiko whirls it in wide sweeping arcs (tachi moves); Rhalix drives it like a spear, heavy thrusts.</li>
 *   <li>Z Sword - Mikiko handles it like a longsword; Rhalix swings it like the slab it is (greatsword).</li>
 *   <li>Brave / Dimensional Sword - Mikiko's quick one-handed cuts (sword); Rhalix's two-handed longsword.</li>
 *   <li>Yajirobe's Katana - Mikiko's quick-draw iaido (uchigatana); Rhalix's tachi.</li>
 * </ul>
 * Anyone else gets Mikiko's (the first) style. Item files: data/dragonminez/capabilities/weapons/*.json.
 */
public final class EfDmzWeapons {
    private EfDmzWeapons() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EfDmzWeapons::onPresets);
    }

    private static void onPresets(WeaponCapabilityPresetRegistryEvent event) {
        var types = event.getTypeEntry();
        types.put(XenoWarriors.id("dmz_pole"), both(WeaponCapabilityPresets.TACHI, CapabilityItem.Styles.TWO_HAND,
                WeaponCapabilityPresets.SPEAR, CapabilityItem.Styles.TWO_HAND));
        types.put(XenoWarriors.id("dmz_heavy_sword"), both(WeaponCapabilityPresets.LONGSWORD, CapabilityItem.Styles.TWO_HAND,
                WeaponCapabilityPresets.GREATSWORD, CapabilityItem.Styles.TWO_HAND));
        types.put(XenoWarriors.id("dmz_sword"), both(WeaponCapabilityPresets.SWORD, CapabilityItem.Styles.ONE_HAND,
                WeaponCapabilityPresets.LONGSWORD, CapabilityItem.Styles.TWO_HAND));
        types.put(XenoWarriors.id("dmz_katana"), both(WeaponCapabilityPresets.UCHIGATANA, CapabilityItem.Styles.TWO_HAND,
                WeaponCapabilityPresets.TACHI, CapabilityItem.Styles.TWO_HAND));
    }

    /** A weapon with Mikiko's moveset (and everyone else's) from one preset and Rhalix's from another. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Function<Item, CapabilityItem.Builder> both(Function<Item, CapabilityItem.Builder> mikiko, Style mStyle,
                                                               Function<Item, CapabilityItem.Builder> rhalix, Style rStyle) {
        return item -> {
            WeaponCapability.Builder b = (WeaponCapability.Builder) mikiko.apply(item);
            WeaponCapability.Builder r = (WeaponCapability.Builder) rhalix.apply(item);
            List combos = r.getComboAnimations().get(rStyle);
            Style rhalixKey = CapabilityItem.Styles.OCHS;         // (a style slot of its own for his moves)
            if (combos != null && !combos.isEmpty()) b.newStyleCombo(rhalixKey, (yesman.epicfight.api.animation.AnimationManager.AnimationAccessor[]) combos.toArray(new yesman.epicfight.api.animation.AnimationManager.AnimationAccessor[0]));
            boolean hasRhalix = combos != null && !combos.isEmpty();
            b.styleProvider(patch -> hasRhalix && isRhalix(patch) ? rhalixKey : mStyle);
            return b;
        };
    }

    private static boolean isRhalix(LivingEntityPatch<?> patch) {
        if (!(patch.getOriginal() instanceof Player p)) return false;
        if (p.level().isClientSide) return net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> p instanceof net.minecraft.client.player.AbstractClientPlayer cp
                        && com.xenowarriors.client.ClientCharacterData.get(cp) == CharacterType.RHALIX) == Boolean.TRUE;
        return ModCapabilities.getType(p) == CharacterType.RHALIX;
    }
}

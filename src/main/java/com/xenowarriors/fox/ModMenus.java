package com.xenowarriors.fox;

import com.xenowarriors.XenoWarriors;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, XenoWarriors.MOD_ID);

    public static final RegistryObject<MenuType<FoxMenu>> FOX = MENUS.register("fox", () -> IForgeMenuType.create(FoxMenu::fromNetwork));

    private ModMenus() {}
}

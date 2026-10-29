package com.xenowarriors.character;

import com.xenowarriors.XenoWarriors;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModCapabilities {
    public static final Capability<PlayerCharacter> PLAYER_CHARACTER = CapabilityManager.get(new CapabilityToken<>() {});

    private ModCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerCharacter.class);
    }

    public static CharacterType getType(Player player) {
        return player.getCapability(PLAYER_CHARACTER).map(PlayerCharacter::getType).orElse(CharacterType.NONE);
    }

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            // (no invalidate listener: an invalidated LazyOptional can never come back, and then the data couldn't
            //  be copied over to the respawned player - the warrior, stats, foxes etc. would all be lost on death)
            event.addCapability(XenoWarriors.id("character"), new Provider());
        }
    }

    /** Keep the chosen character (and stats, pets, ...) after death and when returning from the End. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        original.getCapability(PLAYER_CHARACTER).ifPresent(old ->
                event.getEntity().getCapability(PLAYER_CHARACTER).ifPresent(fresh -> {
                    fresh.copyFrom(old);
                    fresh.karmaMax = old.karmaMax;
                    if (!event.isWasDeath()) fresh.setKarma(old.getKarma());   // through the End portal: keep Karma too
                }));
        original.invalidateCaps();
        if (event.isWasDeath() && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {   // timed forms end with the old body (their buffs did)
            com.xenowarriors.mikiko.FoxKing.stop(sp, false);
            com.xenowarriors.mikiko.Netsu.stop(sp);
        }
    }

    private static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final PlayerCharacter data = new PlayerCharacter();
        private final LazyOptional<PlayerCharacter> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PLAYER_CHARACTER.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() { return data.save(); }

        @Override
        public void deserializeNBT(CompoundTag tag) { data.load(tag); }

        void invalidate() { optional.invalidate(); }
    }
}

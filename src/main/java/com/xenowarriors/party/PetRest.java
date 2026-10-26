package com.xenowarriors.party;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A party can only have 4 pets out. Anyone can still join a party - pets over the limit just stay behind (they sit
 * where they are and don't follow or answer whistles) until there's room again. The party leader's pets come first,
 * then each member's in the order they joined.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetRest {
    public static final String KEY = "xw_pet_rest";

    private PetRest() {}

    /** Staying behind (the party's pet limit)? */
    public static boolean resting(Entity e) { return e != null && e.getPersistentData().getBoolean(KEY); }

    private static List<Entity> pets(ServerPlayer p) {
        List<Entity> out = new ArrayList<>();
        p.getCapability(ModCapabilities.PLAYER_CHARACTER).ifPresent(d -> {
            for (ListTag list : new ListTag[]{d.pets, d.wingPets})
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag rec = list.getCompound(i);
                    Entity e = rec.hasUUID("uuid") ? p.serverLevel().getEntity(rec.getUUID("uuid")) : null;
                    if (e != null && e.isAlive()) out.add(e);
                }
        });
        return out;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 40 != 0) return;
        Set<ServerPlayer> done = new HashSet<>();
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (done.contains(p)) continue;
            List<ServerPlayer> party = Parties.members(p);
            done.addAll(party);
            int out = 0;
            for (ServerPlayer m : party) for (Entity pet : pets(m)) {
                boolean rest = party.size() > 1 && out >= Parties.MAX_PETS;
                if (!rest) out++;
                set(pet, rest, m);
            }
        }
    }

    private static void set(Entity pet, boolean rest, ServerPlayer owner) {
        if (rest == resting(pet)) return;
        pet.getPersistentData().putBoolean(KEY, rest);
        if (pet instanceof Fox f) f.setSitting(rest);
        if (pet instanceof net.minecraft.world.entity.Mob m && rest) m.getNavigation().stop();
        owner.displayClientMessage(Component.literal(pet.getDisplayName().getString() + (rest
                ? " stays behind - your party already has " + Parties.MAX_PETS + " pets out"
                : " can come along again!")).withStyle(rest ? ChatFormatting.GRAY : ChatFormatting.GREEN), false);
    }
}

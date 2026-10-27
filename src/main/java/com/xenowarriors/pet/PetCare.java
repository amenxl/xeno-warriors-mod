package com.xenowarriors.pet;

import com.xenowarriors.XenoWarriors;
import com.xenowarriors.fox.FoxLevels;
import com.xenowarriors.fox.FoxPets;
import com.xenowarriors.fox.FoxSkins;
import com.xenowarriors.network.PetListPacket;
import com.xenowarriors.wing.WingPets;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Looking after pets (Mikiko's foxes and Rhalix's winged companions):
 * <ul>
 *   <li>A pet that runs away from a fight needs {@link #COOLDOWN} to get over it before the whistle can call it back.</li>
 *   <li>Enemies mostly leave pets alone: when one picks a pet as its target it usually goes for the owner instead
 *       (or picks something else), so pets don't get focused down.</li>
 *   <li>The Pets menu's list and actions (rename, skin, bag, whistle).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID)
public final class PetCare {
    /** How long a pet stays away after running from a fight: 5 minutes. */
    public static final int COOLDOWN = 20 * 60 * 5;
    /** How often an enemy that picks a pet as its target changes its mind (70%). */
    private static final float SPARE = 0.7F;
    private static final String ROLL_UNTIL = "xw_pet_roll_until", ROLL_OK = "xw_pet_roll_ok";

    private PetCare() {}

    private static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }

    /** Starts the cool-down on a pet that just ran away. */
    public static void ranAway(ServerPlayer p, CompoundTag rec) {
        rec.putLong("awayUntil", now(p) + COOLDOWN);
    }

    /** Ticks until a pet that ran away can come back (0 = ready, or it never left). */
    public static int left(ServerPlayer p, CompoundTag rec) {
        if (!rec.getBoolean("dead")) return 0;
        return (int) Math.max(0, rec.getLong("awayUntil") - now(p));
    }

    /** True (and tells the player) when this pet is still recovering. */
    public static boolean recovering(ServerPlayer p, CompoundTag rec) {
        int l = left(p, rec);
        if (l <= 0) return false;
        p.sendSystemMessage(Component.literal(rec.getString("name") + " is still shaken from running away - ready in " + fmt(l) + ".")
                .withStyle(ChatFormatting.GRAY));
        return true;
    }

    public static String fmt(int ticks) {
        int s = (ticks + 19) / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    // ------------------------------------------------------------------ enemies leave pets alone (mostly)

    private static Player ownerOfPet(LivingEntity e) {
        if (e instanceof Fox f && FoxPets.ownerOf(f) != null) return FoxPets.owner(f);
        if (WingPets.winged(e) && WingPets.ownerOf(e) != null) return WingPets.owner(e);
        return null;
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target == null || target.level().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        Player owner = ownerOfPet(target);
        if (owner == null || ownerOfPet(mob) != null) return;              // not a pet, or a pet vs pet
        // one decision per enemy for a few seconds, so it doesn't re-roll every time it looks around
        long t = mob.level().getGameTime();
        var data = mob.getPersistentData();
        boolean spare;
        if (data.getLong(ROLL_UNTIL) > t) spare = data.getBoolean(ROLL_OK);
        else {
            spare = mob.getRandom().nextFloat() < SPARE;
            data.putLong(ROLL_UNTIL, t + 100);
            data.putBoolean(ROLL_OK, spare);
        }
        if (!spare) return;
        boolean ownerOk = owner.isAlive() && !owner.isCreative() && !owner.isSpectator() && owner.distanceTo(mob) < 24;
        event.setNewTarget(ownerOk ? owner : null);
    }

    // ------------------------------------------------------------------ the Pets menu

    public static PetListPacket list(ServerPlayer p) {
        List<PetListPacket.Pet> out = new ArrayList<>();
        ListTag foxes = FoxPets.records(p);
        for (int i = 0; i < foxes.size(); i++) {
            CompoundTag rec = foxes.getCompound(i);
            Fox fox = FoxPets.loaded(p, rec);
            int level = Math.max(1, rec.getInt("level"));
            List<String> skins = new ArrayList<>();
            List<Integer> unlock = new ArrayList<>();
            for (FoxSkins.Skin s : FoxSkins.ALL) { skins.add(s.name()); unlock.add(s.level()); }
            out.add(new PetListPacket.Pet(0, i, rec.getString("name"), "Fox", level, rec.getFloat("xp"), level >= FoxLevels.MAX ? 0 : FoxLevels.xpFor(level),
                    status(p, rec, fox != null), left(p, rec), fox == null ? 0 : fox.getHealth(), fox == null ? (float) FoxLevels.health(level) : fox.getMaxHealth(),
                    fox == null ? -1 : fox.getId(), rec.getInt("skin"), skins, unlock, rec.getInt("kills")));
        }
        ListTag wings = WingPets.records(p);
        for (int i = 0; i < wings.size(); i++) {
            CompoundTag rec = wings.getCompound(i);
            Mob mob = WingPets.loaded(p, rec);
            List<Integer> unlock = new ArrayList<>();
            for (int k = 0; k < WingPets.SKINS.length; k++) unlock.add(0);
            out.add(new PetListPacket.Pet(1, i, rec.getString("name"), WingPets.kindName(rec), 0, 0, 0, status(p, rec, mob != null), left(p, rec),
                    mob == null ? 0 : mob.getHealth(), mob == null ? 0 : mob.getMaxHealth(), mob == null ? -1 : mob.getId(),
                    rec.getInt("skin"), List.of(WingPets.SKINS), unlock, 0));
        }
        return new PetListPacket(out);
    }

    /** 0 with you, 1 on your back, 2 ran away (recovering), 3 ran away (ready to whistle), 4 somewhere else. */
    private static int status(ServerPlayer p, CompoundTag rec, boolean loaded) {
        if (rec.getBoolean("dead")) return left(p, rec) > 0 ? 2 : 3;
        if (rec.getBoolean("back")) return 1;
        return loaded ? 0 : 4;
    }

    public static final int RENAME = 0, SKIN = 1, BAG = 2, WHISTLE = 3;

    public static void action(ServerPlayer p, int kind, int index, int action, int arg, String text) {
        if (action == WHISTLE) {
            FoxPets.whistle(p);
            if (com.xenowarriors.character.ModCapabilities.getType(p) != com.xenowarriors.character.CharacterType.RHALIX && !WingPets.records(p).isEmpty()) WingPets.whistle(p);
        } else {
            ListTag list = kind == 0 ? FoxPets.records(p) : WingPets.records(p);
            if (index < 0 || index >= list.size()) return;
            CompoundTag rec = list.getCompound(index);
            if (!rec.hasUUID("uuid")) return;
            var id = rec.getUUID("uuid");
            if (kind == 0) {
                if (action == RENAME) FoxPets.rename(p, id, text);
                else if (action == SKIN) {
                    if (FoxPets.loaded(p, rec) != null) FoxPets.setSkin(p, id, arg);
                    else if (FoxSkins.unlocked(arg, rec.getInt("level"))) rec.putInt("skin", arg);
                } else if (action == BAG) FoxPets.openBag(p, id);
            } else {
                if (action == RENAME) WingPets.rename(p, id, text);
                else if (action == SKIN) {
                    if (WingPets.loaded(p, rec) != null) WingPets.action(p, id, com.xenowarriors.network.WingPetActionPacket.SKIN, arg);
                    else rec.putInt("skin", Math.floorMod(arg, WingPets.SKINS.length));
                } else if (action == BAG) WingPets.action(p, id, com.xenowarriors.network.WingPetActionPacket.BAG, 0);
            }
        }
        if (action != BAG) com.xenowarriors.network.ModNetwork.sendToPlayer(list(p), p);
    }
}

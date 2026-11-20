package com.xenowarriors.npc;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.xenowarriors.XenoWarriors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Some townsfolk have a pet: a wolf or a cat with a name, at their heels wherever they go. Right-click its owner and
 * they'll tell you about it - and for some Money ({@value #WOLF_PRICE} for a wolf, {@value #CAT_PRICE} for a cat)
 * they'll let you have it: it's tamed to you on the spot. A pet whose owner is gone (moved on, despawned) goes too.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanPets {
    private HumanPets() {}

    public static final float CHANCE = 0.18F;
    public static final int WOLF_PRICE = 400, CAT_PRICE = 300;
    static final String WANTS = "xw_wants_pet", PET = "xw_pet", OWNER = "xw_pet_owner";
    private static final String[] NAMES = {"Biscuit", "Shadow", "Mochi", "Rex", "Luna", "Pepper", "Bandit", "Ziggy", "Kiwi", "Bear", "Nala", "Pumpkin", "Ash", "Sushi"};
    /** (Tests) pets given out and sold. */
    public static int given, sold;

    /** At spawn: this one may get a pet (made on its first tick, once it's in the world). */
    static void roll(HumanNpcEntity h, net.minecraft.util.RandomSource r) {
        if (r.nextFloat() < CHANCE) h.getPersistentData().putBoolean(WANTS, true);
    }

    /** (Tests) gives this person a pet now. */
    public static Entity give(HumanNpcEntity h, boolean wolf) {
        if (!(h.level() instanceof ServerLevel s)) return null;
        TamableAnimal pet = wolf ? EntityType.WOLF.create(s) : EntityType.CAT.create(s);
        if (pet == null) return null;
        pet.moveTo(h.getX() + 0.8, h.getY(), h.getZ() + 0.8, h.getYRot(), 0F);
        try { pet.finalizeSpawn(s, s.getCurrentDifficultyAt(h.blockPosition()), MobSpawnType.EVENT, null, null); } catch (Throwable ignored) {}
        pet.setAge(0);
        pet.setCustomName(Component.literal(NAMES[s.random.nextInt(NAMES.length)]));
        pet.setPersistenceRequired();
        pet.getPersistentData().putUUID(OWNER, h.getUUID());
        s.addFreshEntity(pet);
        h.getPersistentData().putUUID(PET, pet.getUUID());
        h.getPersistentData().remove(WANTS);
        given++;
        return pet;
    }

    static TamableAnimal pet(HumanNpcEntity h) {
        if (!h.getPersistentData().hasUUID(PET) || !(h.level() instanceof ServerLevel s)) return null;
        Entity e = s.getEntity(h.getPersistentData().getUUID(PET));
        return e instanceof TamableAnimal t && t.isAlive() && !t.isTame() ? t : null;
    }

    /** Every second from the person's tick: the pet made, and kept at their heels. */
    static void tick(HumanNpcEntity h) {
        if (h.tickCount % 20 != 5 || !(h.level() instanceof ServerLevel)) return;
        if (h.getPersistentData().getBoolean(WANTS) && !h.raider() && !h.skyHater() && !h.carriesSoul() && !h.defender()) { give(h, h.getRandom().nextBoolean()); return; }
        TamableAnimal p = pet(h);
        if (p == null) return;
        double d = p.distanceToSqr(h);
        if (d > 24 * 24) p.teleportTo(h.getX() + 0.8, h.getY(), h.getZ() + 0.8);
        else if (d > 5 * 5) p.getNavigation().moveTo(h, 1.15);
    }

    /** Right-click on the owner: they tell you about it, with a [Buy] button. True if they had one to talk about. */
    static boolean talk(HumanNpcEntity h, ServerPlayer pl) {
        TamableAnimal p = pet(h);
        if (p == null || p.distanceToSqr(h) > 16 * 16) return false;
        boolean wolf = p instanceof Wolf;
        int price = wolf ? WOLF_PRICE : CAT_PRICE;
        String name = p.getCustomName() == null ? (wolf ? "my wolf" : "my cat") : p.getCustomName().getString();
        String cmd = "/xenopet buy " + h.getId();
        pl.sendSystemMessage(Component.literal("\"That's " + name + ", my " + (wolf ? "wolf" : "cat") + ". ...You like " + (wolf ? "him" : "her")
                + "? " + price + " Money and " + name + " can go with you.\" ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[Buy " + name + " - " + price + " Money]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("You have " + com.xenowarriors.economy.Money.fmt(com.xenowarriors.economy.Money.get(pl)) + " Money"))))));
        p.getNavigation().moveTo(h, 1.0);
        return true;
    }

    /** Buying it: the Money, then it's yours. */
    public static boolean buy(ServerPlayer pl, HumanNpcEntity h) {
        TamableAnimal p = pet(h);
        if (p == null) { pl.displayClientMessage(Component.literal("They don't have a pet to give.").withStyle(ChatFormatting.RED), true); return false; }
        if (pl.distanceToSqr(h) > 10 * 10) { pl.displayClientMessage(Component.literal("Go closer to them first.").withStyle(ChatFormatting.RED), true); return false; }
        int price = p instanceof Wolf ? WOLF_PRICE : CAT_PRICE;
        if (!com.xenowarriors.economy.Money.take(pl, price)) {
            pl.sendSystemMessage(Component.literal("Not enough Money: " + price + " needed, you have " + com.xenowarriors.economy.Money.fmt(com.xenowarriors.economy.Money.get(pl)) + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        p.tame(pl);
        p.setOrderedToSit(false);
        p.getNavigation().stop();
        p.getPersistentData().remove(OWNER);
        h.getPersistentData().remove(PET);
        if (p instanceof Cat c) c.setOrderedToSit(false);
        ServerLevel s = (ServerLevel) h.level();
        s.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 0.8, p.getZ(), 7, 0.4, 0.3, 0.4, 0.02);
        s.playSound(null, p.blockPosition(), p instanceof Wolf ? SoundEvents.WOLF_AMBIENT : SoundEvents.CAT_PURREOW, SoundSource.NEUTRAL, 1F, 1.1F);
        String name = p.getCustomName() == null ? "them" : p.getCustomName().getString();
        pl.sendSystemMessage(Component.literal("\"Take good care of " + name + ", okay?\"").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("  " + name + " is yours now.").withStyle(ChatFormatting.GREEN)));
        sold++;
        return true;
    }

    /** A pet whose person is gone goes too (checked every few seconds). */
    @SubscribeEvent
    public static void onPetTick(LivingEvent.LivingTickEvent e) {
        Entity en = e.getEntity();
        if (en.tickCount % 100 != 13 || !(en instanceof TamableAnimal t) || !(en.level() instanceof ServerLevel s)) return;
        if (!t.getPersistentData().hasUUID(OWNER) || t.isTame()) return;
        UUID o = t.getPersistentData().getUUID(OWNER);
        if (!(s.getEntity(o) instanceof HumanNpcEntity h) || !h.isAlive() || h.distanceToSqr(t) > 64 * 64) t.discard();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("xenopet").then(Commands.literal("buy").then(Commands.argument("who", IntegerArgumentType.integer())
                .executes(c -> {
                    ServerPlayer pl = c.getSource().getPlayerOrException();
                    Entity h = pl.serverLevel().getEntity(IntegerArgumentType.getInteger(c, "who"));
                    return h instanceof HumanNpcEntity hn && buy(pl, hn) ? 1 : 0;
                }))));
    }
}

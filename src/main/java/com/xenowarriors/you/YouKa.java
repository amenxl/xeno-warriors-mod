package com.xenowarriors.you;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.xenowarriors.XenoWarriors;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.ka.Ka;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Your own characters' KAs. In the show only some people can use one - so each character you make gets one roll, at
 * Character Level {@link #LEVEL}: a 1 in 3 chance to awaken a KA, picked at random (the simpler ones more often than
 * the rare ones). It goes in the super ultimate slot, with its growing price, like any KA (see ka.Kas). A bot of one of
 * your own rolls too, and uses its KA in a fight. With DragonMineZ, the dragon grants a wish for a KA: a new one (or a
 * first one) for the character you're playing.
 */
@Mod.EventBusSubscriber(modid = XenoWarriors.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouKa {
    private YouKa() {}

    public static final int LEVEL = 30;
    /** 1 in this many awaken a KA. */
    public static final int CHANCE = 3;
    /** Of those that do, this share get a rare one. */
    public static final float RARE_SHARE = 0.3F;
    public static final String WISH_NAME = "Awaken a new KA";

    /** (Tests) a fixed roll: true = awaken, false = not (null = random). */
    public static Boolean testAwaken;
    /** (Tests) rolls made, KAs awakened, wishes granted. */
    public static int rolls, awakened, wishes;

    static Ka pick(RandomSource r, Ka not) {
        Ka k;
        do {
            Ka[] pool = r.nextFloat() < RARE_SHARE ? Ka.RARE : Ka.SIMPLE;
            k = pool[r.nextInt(pool.length)];
        } while (k == not);
        return k;
    }

    /** Its one roll (once it's Level 30). Returns the KA it awakened, or null. */
    public static Ka roll(YouSlot s, RandomSource r) {
        s.kaRolled = true;
        rolls++;
        boolean yes = testAwaken != null ? testAwaken : r.nextInt(CHANCE) == 0;
        if (!yes) return null;
        Ka k = pick(r, null);
        s.ka = k.id();
        awakened++;
        return k;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 40 != 21) return;
        YouSlot s = You.slot(p);
        if (s == null || s.kaRolled || com.xenowarriors.stats.Stats.characterLevel(p) < LEVEL) return;
        Ka k = roll(s, p.getRandom());
        tell(p, s, k);
        com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
        com.xenowarriors.skill.SkillManager.syncLoadout(p);
    }

    private static void tell(ServerPlayer p, YouSlot s, Ka k) {
        if (k == null) {
            p.sendSystemMessage(Component.literal(s.name + " reached Level " + LEVEL + "... but no KA awakens. Only some people ever have one.").withStyle(ChatFormatting.GRAY));
            return;
        }
        p.sendSystemMessage(Component.literal(s.name + "'s KA has awakened: " + k.title() + "!").withStyle(k.chat(), ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("It manipulates " + k.manipulates + " - and its price: " + k.cost + ", worse the more it's used. It's in your super ultimate slot.").withStyle(ChatFormatting.GRAY));
        com.xenowarriors.network.PopupPacket.toParty(p, com.xenowarriors.network.PopupPacket.REQUEST, "KA awakened!", s.name + " awakened the KA " + k.title() + "!", "rage");
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1F, 0.7F);
    }

    /** A bot of one of your own: its roll at Level 30 too, and its KA on it (it uses it like any KA user - ka.Kas). */
    public static void botTick(PartyBotEntity b) {
        YouSlot s = b.you();
        ServerPlayer o = b.owner();
        if (s == null || o == null) return;
        if (!s.kaRolled && b.botLevel() >= LEVEL) {
            Ka k = roll(s, b.getRandom());
            if (k != null) o.sendSystemMessage(Component.literal(s.name + " (bot) awakened a KA: " + k.title() + "!").withStyle(k.chat(), ChatFormatting.BOLD));
            else o.displayClientMessage(Component.literal(s.name + " (bot) reached Level " + LEVEL + " - no KA awakened.").withStyle(ChatFormatting.GRAY), true);
        }
        String have = b.getPersistentData().getString("xw_ka");
        if (!have.equals(s.ka)) com.xenowarriors.ka.Kas.give(b, Ka.byId(s.ka));
    }

    // ------------------------------------------------------------------ the wish

    /** The dragon's wish: a new KA (or a first one) for the character you're playing. */
    public static int wish(ServerPlayer p) {
        YouSlot s = You.slot(p);
        if (s == null) {
            p.sendSystemMessage(Component.literal("The dragon's eyes glow... but only a character you made yourself can be given a KA. (Switch to one and wish again.)").withStyle(ChatFormatting.GOLD));
            return 0;
        }
        Ka old = Ka.byId(s.ka);
        Ka k = pick(p.getRandom(), old);
        s.ka = k.id();
        s.kaRolled = true;
        wishes++;
        p.getPersistentData().remove("xw_ka_strain");
        p.sendSystemMessage(Component.literal("Your wish is granted! " + s.name + (old == null ? " has awakened a KA: " : "'s KA is now ") + k.title() + "!").withStyle(k.chat(), ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal("It manipulates " + k.manipulates + " - its price: " + k.cost + ".").withStyle(ChatFormatting.GRAY));
        com.xenowarriors.character.CharacterManager.syncToTrackingAndSelf(p);
        com.xenowarriors.skill.SkillManager.syncLoadout(p);
        return 1;
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("xenokawish").requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(c, "player"));
                    return p == null ? 0 : wish(p);
                })));
    }

    /** Adds the KA wish to every dragon's wish list (DragonMineZ keeps them per dragon; added again after a reload). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSync(net.minecraftforge.event.OnDatapackSyncEvent e) { addWish(); }

    @SubscribeEvent
    public static void onStarted(net.minecraftforge.event.server.ServerStartedEvent e) { addWish(); }

    @SuppressWarnings("unchecked")
    public static boolean addWish() {
        if (!ModList.get().isLoaded("dragonminez")) return false;
        try {
            Class<?> reg = Class.forName("com.dragonminez.common.wish.DragonWishRegistry");
            Class<?> wishC = Class.forName("com.dragonminez.common.wish.Wish");
            Class<?> cmdC = Class.forName("com.dragonminez.common.wish.wishes.CommandWish");
            Method get = reg.getMethod("getServerWishes"), set = reg.getMethod("setServerWishes", Map.class), name = wishC.getMethod("getName");
            Map<String, List<Object>> cur = (Map<String, List<Object>>) get.invoke(null);
            if (cur == null || cur.isEmpty()) return false;
            Map<String, List<Object>> out = new LinkedHashMap<>();
            boolean changed = false;
            for (var en : cur.entrySet()) {
                List<Object> l = new ArrayList<>(en.getValue() == null ? List.of() : en.getValue());
                boolean has = false;
                for (Object w : l) if (WISH_NAME.equals(name.invoke(w))) has = true;
                if (!has) {
                    l.add(cmdC.getConstructor(String.class, String.class, String[].class).newInstance(WISH_NAME,
                            "Xeno Warriors: a new KA (or a first one) for the character you made yourself that you're playing.", new String[]{"xenokawish %player%"}));
                    changed = true;
                }
                out.put(en.getKey(), l);
            }
            if (changed) set.invoke(null, out);
            return true;
        } catch (Throwable t) {
            XenoWarriors.LOGGER.warn("Couldn't add the KA wish to DragonMineZ's dragons", t);
            return false;
        }
    }
}

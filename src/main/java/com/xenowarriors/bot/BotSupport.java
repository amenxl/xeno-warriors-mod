package com.xenowarriors.bot;

import com.xenowarriors.character.CharacterType;
import com.xenowarriors.character.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bot teammates looking after the party: lighting up dark places (caves) with torches from their bag, and Kaeru's
 * Fox Heal on hurt (or downed) party members near her.
 */
public final class BotSupport {
    private BotSupport() {}

    /** Darker than this (block or sky light) and it lights the place up. */
    public static final int DARK = 7;
    private static final Map<UUID, Long> NEXT_TORCH = new HashMap<>(), NEXT_HEAL = new HashMap<>();
    /** (tests) torches placed, Fox Heals cast. */
    public static int torches, heals;

    /** Once a second, from the bot's own tick. */
    static void tick(PartyBotEntity b) {
        if (b.downed() || !(b.level() instanceof ServerLevel l)) return;
        long now = l.getGameTime();
        torch(b, l, now);
        if (b.character() == CharacterType.KAERU) foxHeal(b, l, now);
    }

    static void forget(UUID bot) { NEXT_TORCH.remove(bot); NEXT_HEAL.remove(bot); }

    // ---------------------------------------------------------------- torches

    /** A mode arena or a made place (Tower, Journey, VR, rooms, the Hekublade): never lit up by bots. */
    static boolean arena(Level l) {
        var d = l.dimension();
        return com.xenowarriors.tower.Tower.is(l) || d == com.xenowarriors.vr.Vr.LEVEL || d == com.xenowarriors.room.RoomTech.LEVEL
                || d == com.xenowarriors.mikiko.HekubladeRealm.KEY;
    }

    /** How light it is here: the brighter of block light and sky light (night outside still counts as open sky). */
    public static int light(Level l, BlockPos p) {
        return Math.max(l.getBrightness(LightLayer.BLOCK, p), l.getBrightness(LightLayer.SKY, p));
    }

    private static void torch(PartyBotEntity b, ServerLevel l, long now) {
        if (b.isPassenger() || b.journey() || b.sparring() || b.isInWater() || arena(l)) return;
        if (now < NEXT_TORCH.getOrDefault(b.getUUID(), 0L)) return;
        BlockPos feet = b.blockPosition();
        if (light(l, feet) >= DARK) return;
        int slot = torchSlot(b);
        if (slot == -2) {                                                        // none left: it makes some (coal and sticks, or fire in its hands)
            NEXT_TORCH.put(b.getUUID(), now + 60);
            if (!makeTorches(b, l)) return;
            slot = torchSlot(b);
            if (slot == -2) return;
        }
        BlockPos at = place(l, feet);
        if (at == null) return;
        com.xenowarriors.stats.Building.botPlaced(b, at, l.getBlockState(at));     // (its character's Building level)
        ItemStack s = slot == -1 ? b.getOffhandItem() : b.inventory.getItem(slot);
        s.shrink(1);
        if (slot >= 0 && s.isEmpty()) b.inventory.setItem(slot, ItemStack.EMPTY);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        l.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
        NEXT_TORCH.put(b.getUUID(), now + 30);
        torches++;
        PartyBots.testLog(b, "placed a torch at " + at.toShortString() + " (light was " + light(l, feet) + ", " + (s.getCount()) + " left in that stack)");
    }

    /** (tests) torches made: crafted, lit with fire. */
    public static int crafted, lit;

    /** Has the element of fire (Mikiko, or one of your own characters with fire as an element). */
    public static boolean fire(PartyBotEntity b) {
        if (b.character() == CharacterType.MIKIKO) return true;
        var y = b.you();
        return y != null && (y.element == com.xenowarriors.ka.Element.FIRE || y.element2 == com.xenowarriors.ka.Element.FIRE);
    }

    private static int count(PartyBotEntity b, net.minecraft.world.item.Item it) {
        int n = 0;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (b.inventory.getItem(i).is(it)) n += b.inventory.getItem(i).getCount();
        return n;
    }

    /**
     * Out of torches in the dark: it makes some from its bag. With fire in its hands it lights sticks (one stick, one
     * torch - up to 8); otherwise it crafts them from coal or charcoal and sticks. Sticks come from its planks (and
     * planks from its logs) if it has none. True if it made any.
     */
    static boolean makeTorches(PartyBotEntity b, ServerLevel l) {
        net.minecraft.resources.ResourceLocation stick = new net.minecraft.resources.ResourceLocation("minecraft", "stick");
        if (count(b, Items.STICK) == 0 && BotCrafting.craftRecipe(b, stick) == null) {
            for (String w : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"})
                if (BotCrafting.craftRecipe(b, new net.minecraft.resources.ResourceLocation("minecraft", w + "_planks")) != null) break;
            BotCrafting.craftRecipe(b, stick);
        }
        ServerPlayer o = b.owner();
        if (fire(b)) {
            int n = Math.min(8, count(b, Items.STICK));
            if (n <= 0) return false;
            int left = n;
            for (int i = 0; i < b.inventory.getContainerSize() && left > 0; i++) {
                ItemStack st = b.inventory.getItem(i);
                if (!st.is(Items.STICK)) continue;
                int t = Math.min(left, st.getCount()); st.shrink(t); left -= t;
            }
            ItemStack torches = new ItemStack(Items.TORCH, n);
            ItemStack rest = b.inventory.addItem(torches);
            if (!rest.isEmpty()) b.spawnAtLocation(rest);
            b.inventory.setChanged();
            l.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, b.getX(), b.getY() + 1.2, b.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
            l.playSound(null, b.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
            lit += n;
            if (o != null) o.displayClientMessage(net.minecraft.network.chat.Component.empty().append(b.label()).append(" lit " + n + " torches with fire.").withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return true;
        }
        net.minecraft.resources.ResourceLocation torch = new net.minecraft.resources.ResourceLocation("minecraft", "torch");
        int made = 0;
        for (int k = 0; k < 2; k++) {
            ItemStack m = BotCrafting.craftRecipe(b, torch);
            if (m == null) { if (k == 0 && BotCrafting.craftRecipe(b, stick) != null && (m = BotCrafting.craftRecipe(b, torch)) != null) made += m.getCount(); break; }
            made += m.getCount();
        }
        if (made == 0) return false;
        crafted += made;
        if (o != null) o.displayClientMessage(net.minecraft.network.chat.Component.empty().append(b.label()).append(" made " + made + " torches.").withStyle(net.minecraft.ChatFormatting.GRAY), true);
        return true;
    }

    /** Where its torches are: -1 the off hand, a bag slot, or -2 for none. */
    private static int torchSlot(PartyBotEntity b) {
        if (isTorch(b.getOffhandItem())) return -1;
        for (int i = 0; i < b.inventory.getContainerSize(); i++) if (isTorch(b.inventory.getItem(i))) return i;
        return -2;
    }

    private static boolean isTorch(ItemStack s) { return s.is(Items.TORCH) || s.is(Items.SOUL_TORCH); }

    /** Puts a torch up near its feet: on a wall at head height, else on the floor there or beside it. Where it went, or null. */
    private static BlockPos place(ServerLevel l, BlockPos feet) {
        BlockPos head = feet.above();
        if (l.getBlockState(head).isAir()) for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState wall = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, d.getOpposite());
            if (wall.canSurvive(l, head)) { l.setBlock(head, wall, 3); return head; }
        }
        BlockState floor = Blocks.TORCH.defaultBlockState();
        if (l.getBlockState(feet).isAir() && floor.canSurvive(l, feet)) { l.setBlock(feet, floor, 3); return feet; }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos q = feet.relative(d);
            if (l.getBlockState(q).isAir() && floor.canSurvive(l, q)) { l.setBlock(q, floor, 3); return q; }
        }
        return null;
    }

    // ---------------------------------------------------------------- Kaeru's Fox Heal

    /** Hurt below this share of their health (or downed): she heals them. */
    static final float HURT = 0.5F;

    private static void foxHeal(PartyBotEntity b, ServerLevel l, long now) {
        if (now < NEXT_HEAL.getOrDefault(b.getUUID(), 0L)) return;
        ServerPlayer o = b.owner();
        if (b.journey() && com.xenowarriors.journey.Journey.noSkills(o)) return;
        if (!com.xenowarriors.character.Seasons.unlocked(l.getServer(), b.character().season())) return;   // (Season 2 locked again)
        LivingEntity who = needsHeal(b, l);
        if (who == null) return;
        com.xenowarriors.skill.Skill skill = new com.xenowarriors.skill.kaeru.FoxHealSkill();
        float cost = com.xenowarriors.skill.SkillLevels.costFor(b, skill.getId(), skill.getKarmaCost());
        if (b.karma < cost) return;
        // the same skill in its loadout shares the cooldown
        int[] loadout = o == null ? com.xenowarriors.skill.SkillPool.defaultLoadout()
                : o.getCapability(ModCapabilities.PLAYER_CHARACTER).map(d -> d.loadoutWithUlt(b.character())).orElse(com.xenowarriors.skill.SkillPool.defaultLoadout());
        for (int i = 0; i < b.skillReady.length; i++) {
            var s = com.xenowarriors.skill.SkillPool.inSlot(b.character(), loadout, i);
            if (s != null && s.getId().equals(skill.getId()) && now < b.skillReady[i]) return;
        }
        float before = who.getHealth();
        boolean downed = PartyBots.isDown(who);
        if (!PartyBots.castAt(b, skill, who)) { NEXT_HEAL.put(b.getUUID(), now + 60); return; }
        b.karma -= cost;
        long ready = now + Math.max(40, com.xenowarriors.skill.SkillLevels.cooldownFor(b, skill.getId(), skill.getCooldownTicks()));
        NEXT_HEAL.put(b.getUUID(), ready);
        for (int i = 0; i < b.skillReady.length; i++) {
            var s = com.xenowarriors.skill.SkillPool.inSlot(b.character(), loadout, i);
            if (s != null && s.getId().equals(skill.getId())) b.skillReady[i] = ready;
        }
        heals++;
        PartyBots.testLog(b, "Fox Heal on " + who.getName().getString() + (downed ? " (downed)" : "") + String.format(java.util.Locale.ROOT, ": %.1f -> %.1f hp, karma left %.0f", before, who.getHealth(), b.karma));
    }

    /** The party member near her (8 blocks) who most needs it: anyone downed, else the most hurt one under half health. */
    private static LivingEntity needsHeal(PartyBotEntity b, ServerLevel l) {
        LivingEntity best = null;
        float worst = HURT;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, b.getBoundingBox().inflate(8))) {
            if (e == b || !e.isAlive() || !(e instanceof Player || e instanceof PartyBotEntity)) continue;
            if (e instanceof Player p && (p.isSpectator() || p instanceof net.minecraftforge.common.util.FakePlayer)) continue;
            if (!com.xenowarriors.combat.Allies.isAlly(b, e) || com.xenowarriors.combat.Allies.opponents(b, e)) continue;
            if (e instanceof Player p && com.xenowarriors.party.Duels.isDowned(p)) return e;
            if (e instanceof PartyBotEntity m && m.downed()) { if (best == null || !PartyBots.isDown(best)) best = e; worst = -1F; continue; }   // (a downed player still comes first)
            float f = e.getHealth() / e.getMaxHealth();
            if (f < worst) { worst = f; best = e; }
        }
        return best;
    }
}

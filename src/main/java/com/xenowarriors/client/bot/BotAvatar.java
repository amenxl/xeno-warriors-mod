package com.xenowarriors.client.bot;

import com.mojang.authlib.GameProfile;
import com.xenowarriors.bot.PartyBotEntity;
import com.xenowarriors.character.CharacterType;
import com.xenowarriors.client.ClientCharacterData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The player body a bot teammate is drawn with: a client-only stand-in player that copies the bot every tick
 * (position, turning, walk cycle, swings, hurt flashes, what it holds and wears). Drawn by the normal player
 * renderer, so a bot gets everything a player of that character gets: their idles (Garry's beatbox, Keita's doze,
 * Mikiko's moods...), skill poses, faces, Rhalix's wings, outfits' details, armour.
 *
 * It shares the bot's entity id (so per-entity animation state - skill poses, combo swings, poses - lines up with
 * what the server sends for the bot), and the UUID of the bot's stand-in skill player on the server (so state the
 * server keys by that player, like Kishi, lines up too). It is never added to the world.
 */
public class BotAvatar extends RemotePlayer {
    PartyBotEntity bot;
    private CharacterType shownAs = CharacterType.NONE;
    private UUID ownerShown;
    private boolean knocked;

    BotAvatar(ClientLevel level, PartyBotEntity bot) {
        super(level, new GameProfile(uuidFor(bot), "xwbot"));
        this.bot = bot;
        entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);        // hat, jacket, sleeves, pants: all on, like players
    }

    /** The same UUID as the bot's stand-in player on the server (see PartyBots.fake), or the bot's own for a display bot. */
    static UUID uuidFor(PartyBotEntity b) {
        return b.ownerId() == null ? b.getUUID() : UUID.nameUUIDFromBytes(("xwbot" + b.ownerId() + b.character().getId()).getBytes());
    }

    /** Copy the bot's state (each client tick, and again right before it's drawn). */
    void sync(PartyBotEntity b) {
        bot = b;
        if (getId() != b.getId()) setId(b.getId());
        if (shownAs != b.character() || !java.util.Objects.equals(ownerShown, b.ownerId())) {
            ClientCharacterData.set(getUUID(), CharacterType.NONE);
            ClientCharacterData.setOutfit(getUUID(), 0);
            setUUID(uuidFor(b));
            shownAs = b.character(); ownerShown = b.ownerId();
        }
        ClientCharacterData.set(getUUID(), shownAs);
        ClientCharacterData.setOutfit(getUUID(), b.outfit());                  // (the outfit you had on them)
        setPos(b.getX(), b.getY(), b.getZ());
        xo = b.xo; yo = b.yo; zo = b.zo;
        xOld = b.xOld; yOld = b.yOld; zOld = b.zOld;
        setYRot(b.getYRot()); yRotO = b.yRotO;
        setXRot(b.getXRot()); xRotO = b.xRotO;
        yBodyRot = b.yBodyRot; yBodyRotO = b.yBodyRotO;
        yHeadRot = b.yHeadRot; yHeadRotO = b.yHeadRotO;
        tickCount = b.tickCount;
        var from = (com.xenowarriors.mixin.WalkAnimationAccessor) b.walkAnimation;
        var to = (com.xenowarriors.mixin.WalkAnimationAccessor) walkAnimation;
        walkAnimation.setSpeed(b.walkAnimation.speed());
        to.xenowarriors$setSpeedOld(from.xenowarriors$speedOld());
        to.xenowarriors$setPosition(from.xenowarriors$position());
        swinging = b.swinging; swingTime = b.swingTime; swingingArm = b.swingingArm;
        attackAnim = b.attackAnim; oAttackAnim = b.oAttackAnim;
        hurtTime = b.hurtTime; hurtDuration = b.hurtDuration; deathTime = b.deathTime;
        // (a mob standing still gets no movement updates, so its onGround can be stale here: check the floor under it)
        boolean ground = b.onGround() || !b.isNoGravity() && Math.abs(b.getY() - b.yo) < 0.01
                && !b.level().noCollision(b, b.getBoundingBox().move(0, -0.08, 0).deflate(0.05, 0, 0.05));
        boolean cloud = NimbusUnder.riding(b);                                  // (on its Flying Nimbus: standing on the cloud, not flying)
        if (cloud) ground = true;
        setOnGround(ground);
        fallDistance = b.fallDistance;
        wasTouchingWater = b.isInWater();
        setDeltaMovement(b.getDeltaMovement());
        setPose(b.getPose());
        setShiftKeyDown(b.isShiftKeyDown());
        setNoGravity(b.isNoGravity());
        var maxHp = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp != null && maxHp.getBaseValue() != b.getMaxHealth()) maxHp.setBaseValue(b.getMaxHealth());
        double dx = b.getX() - b.xo, dz = b.getZ() - b.zo;
        if (cloud) { walkAnimation.setSpeed(0F); to.xenowarriors$setSpeedOld(0F); }
        boolean running = !cloud && (b.isSprinting() || (ground || b.isNoGravity()) && dx * dx + dz * dz > 0.2 * 0.2);   // (bots keep up at a run)
        if (isSprinting() != running) setSprinting(running);
        getAbilities().flying = b.isNoGravity() && !ground && !b.isPassenger() && !cloud;
        // downed: lying on its back, dazed, like a knocked-out player
        boolean down = b.downed();
        if (down) com.xenowarriors.client.party.PartyClient.setKnockedOut(getUUID(), 40);
        else if (knocked) com.xenowarriors.client.party.PartyClient.setKnockedOut(getUUID(), 0);
        knocked = down;
    }

    /** Gone: forget what it was showing. */
    void forget() {
        ClientCharacterData.set(getUUID(), CharacterType.NONE);
        ClientCharacterData.setOutfit(getUUID(), 0);
        if (knocked) com.xenowarriors.client.party.PartyClient.setKnockedOut(getUUID(), 0);
    }

    public PartyBotEntity bot() { return bot; }

    // what it holds and wears, its health and riding: the bot's own
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return bot == null ? ItemStack.EMPTY : bot.getItemBySlot(slot); }
    @Override public float getHealth() { return bot == null ? 20F : bot.getHealth(); }
    @Override public boolean isInvisible() { return bot != null && bot.isInvisible(); }
    @Override public boolean isPassenger() { return bot != null && bot.isPassenger(); }
    @Override public Entity getVehicle() { return bot == null ? null : bot.getVehicle(); }
    @Override public boolean isSpectator() { return false; }
    @Override public boolean isCreative() { return false; }
    @Override public boolean isCurrentlyGlowing() { return bot != null && bot.isCurrentlyGlowing(); }
    @Override public boolean shouldShowName() { return false; }
}

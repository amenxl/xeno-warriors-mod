package com.xenowarriors.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a joining player's mods send the host a packet bigger than vanilla's 32 KB player-to-host limit (up to 1 MB, the
 * same as host-to-player) - some mod in the user's pack sends one a few seconds after joining, which got their friend
 * kicked with "Payload may not be larger than 32767 bytes". Each oversized one is logged with the mod channel that sent it.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class BigModPacketMixin {
    @Shadow @Final private ResourceLocation identifier;
    @Shadow @Final private FriendlyByteBuf data;

    private static final int XW_LIMIT = 1048576;
    private static int xw$logged;

    @ModifyConstant(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", constant = @Constant(intValue = 32767), require = 0)
    private int xenowarriors$biggerLimit(int vanilla) { return XW_LIMIT; }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"), require = 0)
    private void xenowarriors$noteBig(FriendlyByteBuf buf, CallbackInfo ci) {
        int n = data == null ? 0 : data.readableBytes();
        if (n > 32767 && xw$logged++ < 50)
            com.xenowarriors.XenoWarriors.LOGGER.info("XW big mod packet from a player: {} ({} bytes) - let through (vanilla stops at 32767)", identifier, n);
    }
}

package com.xenowarriors.network;

import com.xenowarriors.you.YouSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: your ten You characters (with each one's Character Level), and which is in use; opens the slots screen. */
public record YouSlotsPacket(YouSlot[] slots, int[] levels, int current, boolean open, String[] looks) {
    public static void encode(YouSlotsPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.slots.length);
        for (int i = 0; i < m.slots.length; i++) { m.slots[i].write(buf); buf.writeVarInt(m.levels[i]); }
        buf.writeVarInt(m.current); buf.writeBoolean(m.open);
        buf.writeVarInt(m.looks.length); for (String l : m.looks) buf.writeUtf(l, 64);
    }
    public static YouSlotsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt(); YouSlot[] s = new YouSlot[n]; int[] lv = new int[n];
        for (int i = 0; i < n; i++) { s[i] = YouSlot.read(buf); lv[i] = buf.readVarInt(); }
        int cur = buf.readVarInt(); boolean open = buf.readBoolean();
        String[] looks = new String[Math.min(512, buf.readVarInt())]; for (int i = 0; i < looks.length; i++) looks[i] = buf.readUtf(64);
        return new YouSlotsPacket(s, lv, cur, open, looks);
    }
    public static void handle(YouSlotsPacket m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.xenowarriors.client.you.YouClient.slots(m.slots, m.levels, m.current, m.open, m.looks));
        ctx.get().setPacketHandled(true);
    }
}

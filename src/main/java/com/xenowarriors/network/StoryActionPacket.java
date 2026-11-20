package com.xenowarriors.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: a Story menu button or the end of a dialogue. */
public record StoryActionPacket(int action, String arg, int choice) {
    public static final int OPEN = 0, PICK = 1, BEGIN = 2, QUIT = 3, HUB = 4, DONE = 5, START = 6;

    public static void encode(StoryActionPacket m, FriendlyByteBuf b) { b.writeVarInt(m.action); b.writeUtf(m.arg, 64); b.writeVarInt(m.choice); }
    public static StoryActionPacket decode(FriendlyByteBuf b) { return new StoryActionPacket(b.readVarInt(), b.readUtf(64), b.readVarInt()); }

    public static void handle(StoryActionPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        {
            if (p == null) { ctx.get().setPacketHandled(true); return; }
            switch (m.action) {
                case OPEN -> com.xenowarriors.story.StoryRun.openMenu(p);
                case PICK -> com.xenowarriors.story.Story.pick(p, m.arg);
                case BEGIN -> com.xenowarriors.story.StoryRun.begin(p);
                case QUIT -> com.xenowarriors.story.StoryRun.quit(p, "You left the story mission");
                case HUB -> com.xenowarriors.story.StoryRun.visitHub(p, m.arg);
                case DONE -> com.xenowarriors.story.StoryRun.dialogueDone(p, m.arg, m.choice);
                case START -> { if (!com.xenowarriors.story.Story.active(p)) com.xenowarriors.story.Story.start(p); }
                default -> {}
            }
            com.xenowarriors.story.Story.sync(p);
        }
        ctx.get().setPacketHandled(true);
    }
}

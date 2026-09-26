package com.lotusblight.map;

import com.lotusblight.entity.HonchoMeetingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client-to-server: the story inside the meeting cutscene reached Honcho's plea, so he can go down on
 * his knees for it (see HonchoMeetingManager#onStoryQuestion). No payload - the manager only acts on
 * it while this player's meeting is actually in its story stage.
 */
public class HonchoStoryQuestionPacket {
    public HonchoStoryQuestionPacket() {}

    public static void encode(HonchoStoryQuestionPacket packet, FriendlyByteBuf buf) {}

    public static HonchoStoryQuestionPacket decode(FriendlyByteBuf buf) {
        return new HonchoStoryQuestionPacket();
    }

    public static void handle(HonchoStoryQuestionPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) HonchoMeetingManager.onStoryQuestion(player);
        });
        ctx.setPacketHandled(true);
    }
}

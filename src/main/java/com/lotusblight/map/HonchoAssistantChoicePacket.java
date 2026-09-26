package com.lotusblight.map;

import com.lotusblight.advancement.HonchoBestBossTrigger;
import com.lotusblight.advancement.HonchoCruelTrigger;
import com.lotusblight.data.LotusPlayerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client-to-server: the player answered Honcho's "let me be your assistant" question (see HonchoAssistantOverlay). */
public class HonchoAssistantChoicePacket {
    private final boolean accepted;

    public HonchoAssistantChoicePacket(boolean accepted) {
        this.accepted = accepted;
    }

    public static void encode(HonchoAssistantChoicePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.accepted);
    }

    public static HonchoAssistantChoicePacket decode(FriendlyByteBuf buf) {
        return new HonchoAssistantChoicePacket(buf.readBoolean());
    }

    public static void handle(HonchoAssistantChoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            // Only an actually asked, still unanswered question counts - otherwise any client could
            // hand itself both endings' advancements whenever it liked.
            if (player == null || !LotusPlayerState.isHonchoAssistantPending(player)) return;
            LotusPlayerState.setHonchoAssistantPending(player, false);
            com.lotusblight.entity.HonchoMeetingManager.onAssistantAnswered(player);
            if (packet.accepted) {
                HonchoBestBossTrigger.INSTANCE.trigger(player);
                com.lotusblight.entity.HonchoSpeech.say(player, "— Спасибо... Я буду стараться.");
            } else {
                HonchoCruelTrigger.INSTANCE.trigger(player);
                com.lotusblight.entity.HonchoSpeech.say(player, "— ...Понимаю. Прости, что спросил.");
            }
        });
        ctx.setPacketHandled(true);
    }
}

package com.lotusblight.map;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: tells the receiving player's client to play the "Нудная лекция" cutscene (see
 * WorldLotusLectureOverlay). Empty payload — the scene's lines are static (see
 * LotusDialogueLibrary#worldLotusLectureLines) and already present in both jars, and the client
 * substitutes its own player's name locally, so there's nothing worth sending over the wire.
 */
public class ShowWorldLotusLecturePacket {
    public static void encode(ShowWorldLotusLecturePacket packet, net.minecraft.network.FriendlyByteBuf buf) {
    }

    public static ShowWorldLotusLecturePacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        return new ShowWorldLotusLecturePacket();
    }

    public static void handle(ShowWorldLotusLecturePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.lotusblight.client.WorldLotusLectureOverlay.show()));
        ctx.setPacketHandled(true);
    }
}

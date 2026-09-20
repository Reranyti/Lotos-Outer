package com.lotusblight.map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server-to-client packet carrying every Mossy Gland's position (see MossyGlandSavedData) - not
 * gated by discovery/hidden state like outbreak markers, since there are only ever
 * MossyGlandWorldgen.NATURAL_GLAND_COUNT of them and finding them at all was the actual reported
 * problem (bug #21 - "не можем найти мшистый биом... его нет ни в структурах ни в биоме").
 */
public class GlandSyncPacket {
    private final List<BlockPos> positions;

    public GlandSyncPacket(List<BlockPos> positions) {
        this.positions = positions;
    }

    public static void encode(GlandSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.positions.size());
        for (BlockPos pos : packet.positions) {
            buf.writeBlockPos(pos);
        }
    }

    public static GlandSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buf.readBlockPos());
        }
        return new GlandSyncPacket(positions);
    }

    public static void handle(GlandSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientGlandCache.update(packet.positions));
        ctx.setPacketHandled(true);
    }
}

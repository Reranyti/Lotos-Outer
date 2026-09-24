package com.lotusblight.map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server-to-client: every currently-visible meteorite impact site position (see MeteoriteSpreadSavedData/MapSyncManager) - waypoints only, no phase/progress like outbreak MapMarker has. */
public class MeteoriteMarkerSyncPacket {
    private final List<BlockPos> positions;

    public MeteoriteMarkerSyncPacket(List<BlockPos> positions) {
        this.positions = positions;
    }

    public static void encode(MeteoriteMarkerSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.positions.size());
        for (BlockPos pos : packet.positions) {
            buf.writeBlockPos(pos);
        }
    }

    public static MeteoriteMarkerSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buf.readBlockPos());
        }
        return new MeteoriteMarkerSyncPacket(positions);
    }

    public static void handle(MeteoriteMarkerSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMapCache.updateMeteoriteMarkers(packet.positions)));
        ctx.setPacketHandled(true);
    }
}

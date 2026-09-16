package com.lotusblight.map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server-to-client packet carrying the sending player's currently-visible
 * outbreak markers (see {@link MapSyncManager}). The client never scans the
 * world for this data — it just stores whatever arrives in {@link ClientMapCache}
 * and the HUD/atlas render from that cache.
 */
public class MapSyncPacket {
    private final List<MapMarker> markers;

    public MapSyncPacket(List<MapMarker> markers) {
        this.markers = markers;
    }

    public static void encode(MapSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.markers.size());
        for (MapMarker marker : packet.markers) {
            buf.writeUUID(marker.id());
            buf.writeBlockPos(marker.pos());
            buf.writeVarInt(marker.phase());
            buf.writeFloat(marker.progress());
            buf.writeVarInt(marker.infectedBlockCount());
            buf.writeBoolean(marker.heartAnchor());
            buf.writeBoolean(marker.hidden());
        }
    }

    public static MapSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MapMarker> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            BlockPos pos = buf.readBlockPos();
            int phase = buf.readVarInt();
            float progress = buf.readFloat();
            int infectedBlockCount = buf.readVarInt();
            boolean heartAnchor = buf.readBoolean();
            boolean hidden = buf.readBoolean();
            markers.add(new MapMarker(id, pos, phase, progress, infectedBlockCount, heartAnchor, hidden));
        }
        return new MapSyncPacket(markers);
    }

    public static void handle(MapSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientMapCache.update(packet.markers));
        ctx.setPacketHandled(true);
    }
}

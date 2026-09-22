package com.lotusblight.map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-to-client: drives the "Побег от лотоса" countdown HUD + music (see LotusChaseOverlay).
 * Three states, not just start/stop - success and being caught need different music behavior:
 * STARTED begins both the clock and lotus_chase_theme from the top; SURVIVED hides the clock but
 * lets the track keep playing to its natural end (1:50-2:14 is the escape's own outro); CAUGHT
 * hides the clock AND cuts the music immediately, for the kinetic-death beat.
 */
public class ChaseStatePacket {
    public enum State { STARTED, SURVIVED, CAUGHT }

    private final State state;
    private final int durationTicks;

    public ChaseStatePacket(State state, int durationTicks) {
        this.state = state;
        this.durationTicks = durationTicks;
    }

    public static void encode(ChaseStatePacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.state);
        buf.writeVarInt(packet.durationTicks);
    }

    public static ChaseStatePacket decode(FriendlyByteBuf buf) {
        return new ChaseStatePacket(buf.readEnum(State.class), buf.readVarInt());
    }

    public static void handle(ChaseStatePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            switch (packet.state) {
                case STARTED -> com.lotusblight.client.LotusChaseOverlay.start(packet.durationTicks);
                case SURVIVED -> com.lotusblight.client.LotusChaseOverlay.stopClockOnly();
                case CAUGHT -> com.lotusblight.client.LotusChaseOverlay.stopAll();
            }
        }));
        ctx.setPacketHandled(true);
    }
}

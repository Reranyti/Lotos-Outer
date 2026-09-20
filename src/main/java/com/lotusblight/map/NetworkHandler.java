package com.lotusblight.map;

import com.lotusblight.LotusBlight;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Owns the mod's single {@link SimpleChannel}, used to push the player's
 * currently-visible outbreak markers from server to client (see
 * {@link MapSyncPacket}) so client code never has to scan blocks to know
 * where outbreaks are.
 *
 * {@link #register()} must be called once during mod construction — it is
 * NOT wired up automatically (channel registration isn't an event and can't
 * be done via {@code @Mod.EventBusSubscriber}). Add this line to
 * {@code LotusBlight}'s constructor:
 *
 * <pre>    com.lotusblight.map.NetworkHandler.register();</pre>
 */
public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LotusBlight.MODID, "map_sync"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean registered = false;

    private NetworkHandler() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.registerMessage(id++, MapSyncPacket.class, MapSyncPacket::encode, MapSyncPacket::decode, MapSyncPacket::handle);
        CHANNEL.registerMessage(id++, DialogueChoicePacket.class, DialogueChoicePacket::encode, DialogueChoicePacket::decode, DialogueChoicePacket::handle);
        CHANNEL.registerMessage(id++, PlayerStateSyncPacket.class, PlayerStateSyncPacket::encode, PlayerStateSyncPacket::decode, PlayerStateSyncPacket::handle);
        CHANNEL.registerMessage(id++, ShowInnerVoicePacket.class, ShowInnerVoicePacket::encode, ShowInnerVoicePacket::decode, ShowInnerVoicePacket::handle);
        CHANNEL.registerMessage(id++, DialogueAnswerPacket.class, DialogueAnswerPacket::encode, DialogueAnswerPacket::decode, DialogueAnswerPacket::handle);
        CHANNEL.registerMessage(id++, DialogueOpenedPacket.class, DialogueOpenedPacket::encode, DialogueOpenedPacket::decode, DialogueOpenedPacket::handle);
        CHANNEL.registerMessage(id++, GlandSyncPacket.class, GlandSyncPacket::encode, GlandSyncPacket::decode, GlandSyncPacket::handle);
    }
}

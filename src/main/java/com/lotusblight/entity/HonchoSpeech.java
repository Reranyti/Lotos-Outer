package com.lotusblight.entity;

import com.lotusblight.map.HonchoLinePacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Everything Honcho says to a player goes through here, so the client can show it as his line (see HonchoLinePacket). */
public final class HonchoSpeech {
    private HonchoSpeech() {}

    public static void say(ServerPlayer player, String line) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HonchoLinePacket(line));
    }
}

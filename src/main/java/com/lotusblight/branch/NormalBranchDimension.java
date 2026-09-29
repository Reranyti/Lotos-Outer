package com.lotusblight.branch;

import com.lotusblight.LotusBlight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The empty place the Нормальная_ветка leads to: a void dimension (data/lotusblight/dimension/hollow)
 * with no ground and no light. This holds its key and the move that drops a player into it.
 */
public final class NormalBranchDimension {
    public static final ResourceKey<Level> HOLLOW =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(LotusBlight.MODID, "hollow"));

    private NormalBranchDimension() {}

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(HOLLOW);
    }

    /** Drops the player into the empty place at its origin. Returns false if the dimension is missing. */
    public static boolean send(ServerPlayer player, Vec3 at) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel hollow = level(server);
        if (hollow == null) return false;
        player.teleportTo(hollow, at.x, at.y, at.z, player.getYRot(), player.getXRot());
        return true;
    }
}

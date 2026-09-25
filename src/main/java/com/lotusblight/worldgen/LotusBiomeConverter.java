package com.lotusblight.worldgen;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * "МНЕ НУЖНА МЕХАНИКА ЧТО НОРМАЛЬНО РАБОТАЕТ" - a mature outbreak (phase 4, the "mini-biome")
 * doesn't just look like lotus_marsh (see LotusTerritory's client-side render hook) or get a
 * growth-rate bonus for being near it (InfectionSpreadEngine#isInLotusTerritory) - this actually
 * rewrites the chunk's real biome storage to lotus_marsh, using WorldEdit (a hard dependency, see
 * mods.toml) since neither vanilla nor Forge expose a clean API for changing an already-generated
 * chunk's biome. Fires once per outbreak, at the exact phase 3->4 transition (see
 * InfectionSpreadEngine#tickOutbreak).
 */
public final class LotusBiomeConverter {
    /** Matches InfectionSpreadEngine/MeteoriteSpreadEngine's own "territory" radius convention. */
    private static final int CONVERSION_RADIUS = 32;

    private LotusBiomeConverter() {
    }

    public static void convertToLotusMarsh(ServerLevel level, BlockPos center) {
        BiomeType lotusMarsh = BiomeTypes.get("lotusblight:lotus_marsh");
        if (lotusMarsh == null) return; // Registry not ready yet (shouldn't happen this late, but never crash the spread tick over it).

        var weWorld = ForgeAdapter.adapt(level);
        // setBiome takes a single column (BlockVector2, whole height) at a time - no bulk-region
        // overload - so this walks every column in the square instead of one Region call. One-time
        // cost per outbreak reaching phase 4, not a per-tick operation.
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            for (int x = center.getX() - CONVERSION_RADIUS; x <= center.getX() + CONVERSION_RADIUS; x++) {
                for (int z = center.getZ() - CONVERSION_RADIUS; z <= center.getZ() + CONVERSION_RADIUS; z++) {
                    editSession.setBiome(BlockVector2.at(x, z), lotusMarsh);
                }
            }
        }

        // "переслать чанк заново" - a biome rewrite through WorldEdit's own chunk storage doesn't
        // automatically refresh fog/grass color for players who already have the chunk loaded;
        // resending the raw chunk packet forces the client to pick it up immediately instead of
        // only on next relog/re-enter of the area.
        int minChunkX = (center.getX() - CONVERSION_RADIUS) >> 4;
        int maxChunkX = (center.getX() + CONVERSION_RADIUS) >> 4;
        int minChunkZ = (center.getZ() - CONVERSION_RADIUS) >> 4;
        int maxChunkZ = (center.getZ() + CONVERSION_RADIUS) >> 4;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) continue;
            if (player.blockPosition().distSqr(center) > (double) (CONVERSION_RADIUS + 64) * (CONVERSION_RADIUS + 64)) continue;
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    var chunk = level.getChunk(cx, cz);
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                            chunk, level.getLightEngine(), null, null));
                }
            }
        }
    }
}

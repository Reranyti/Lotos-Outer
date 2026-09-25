package com.lotusblight.worldgen;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.math.BlockVector3;
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
        // The 2D setBiome(BlockVector2) overload is a deprecated leftover from pre-1.18 column biomes -
        // it only writes the single 4x4x4 cell at Y=0, so the surface never changed. Biomes are stored
        // per 4x4x4 cell now, so one write per cell covers the whole volume. Columns in unloaded
        // chunks are skipped - WorldEdit would load (or generate) them synchronously otherwise.
        int minX = (center.getX() - CONVERSION_RADIUS) & ~3;
        int minZ = (center.getZ() - CONVERSION_RADIUS) & ~3;
        int maxX = center.getX() + CONVERSION_RADIUS;
        int maxZ = center.getZ() + CONVERSION_RADIUS;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
            for (int x = minX; x <= maxX; x += 4) {
                for (int z = minZ; z <= maxZ; z += 4) {
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    for (int y = minY; y < maxY; y += 4) {
                        editSession.setBiome(BlockVector3.at(x, y, z), lotusMarsh);
                    }
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
                    if (!level.hasChunk(cx, cz)) continue;
                    var chunk = level.getChunk(cx, cz);
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                            chunk, level.getLightEngine(), null, null));
                }
            }
        }
    }
}

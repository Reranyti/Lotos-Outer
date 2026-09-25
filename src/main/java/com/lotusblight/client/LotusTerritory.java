package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * "СООТВЕТСТВИЕ БИОМОВ ЧТО ЯВЛЯЮТСЯ ОДНИМ И ТЕМ ЖЕ" - a mature (phase 4+, heart-anchored) outbreak
 * is conceptually a patch of the real lotus_marsh biome, not just "some other biome with recolored
 * blocks". Actually changing a generated chunk's biome id at runtime needs Mixins (deliberately
 * avoided elsewhere in this project, e.g. MeteoriteSpreadEngine#isBlessingTerritory), so this is
 * the same "world property" pattern instead: InfectedGroundColor asks this whether a position
 * should render using lotus_marsh's own real palette regardless of the block's actual ambient
 * biome. Reuses ClientMapCache's already-visibility-gated marker list (hidden/undiscovered
 * outbreaks correctly stay excluded, exactly like the map itself) rather than a separate sync, so
 * no new information is leaked about outbreaks the player hasn't found yet.
 */
public final class LotusTerritory {
    public static final ResourceLocation LOTUS_MARSH = new ResourceLocation(LotusBlight.MODID, "lotus_marsh");

    private static final int MINI_BIOME_PHASE = 4;
    private static final double TERRITORY_RADIUS = 96.0;
    private static final double TERRITORY_RADIUS_SQ = TERRITORY_RADIUS * TERRITORY_RADIUS;

    private LotusTerritory() {
    }

    public static boolean isLotusTerritory(BlockPos pos) {
        for (MapMarker marker : ClientMapCache.markers()) {
            if (marker.phase() < MINI_BIOME_PHASE || !marker.heartAnchor()) continue;
            if (marker.pos().distSqr(pos) <= TERRITORY_RADIUS_SQ) return true;
        }
        return false;
    }
}

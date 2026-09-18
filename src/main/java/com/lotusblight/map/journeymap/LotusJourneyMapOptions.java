package com.lotusblight.map.journeymap;

import journeymap.api.v2.common.option.BooleanOption;
import journeymap.api.v2.common.option.OptionCategory;

/**
 * Dedicated JourneyMap options category for the mod, registered from
 * {@code RegistryEvent.OptionsRegistryEvent} (see LotusJourneyMapPlugin#initialize).
 * Before this, the mod's waypoints/overlays were unconditional - no way to turn
 * either off from JourneyMap's own options screen the way every other
 * JourneyMap-integrated mod exposes its own category.
 */
public final class LotusJourneyMapOptions {

    private static final OptionCategory CATEGORY = new OptionCategory("lotusblight", "Lotus Blight", "Отображение заражения лотоса на карте");

    public final BooleanOption showOutbreakMarkers;
    public final BooleanOption showInfectionArea;

    public LotusJourneyMapOptions() {
        showOutbreakMarkers = new BooleanOption(CATEGORY, "showOutbreakMarkers", "Метки очагов лотоса", true);
        showInfectionArea = new BooleanOption(CATEGORY, "showInfectionArea", "Зона заражения (полигон)", true);
    }
}

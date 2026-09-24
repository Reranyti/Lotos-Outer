package com.lotusblight.data;

/**
 * "репутация со всеми - с лотосом, с учёными, с звёздным светом и тех кто он встретит позже" -
 * the sides the player's standing is tracked against, shown in the diary's reputation tab once
 * they've lived through StarFall (see LotusWikiScreen). New factions (e.g. a future "Путеводный
 * свет"/"Ройн") get added here later - the storage in LotusPlayerState is already generic per
 * Faction, so a new entry needs no format change.
 */
public enum Faction {
    LOTUS("Лотос"),
    SCIENTISTS("Учёные"),
    STAR_LIGHT("Звёздный Свет");

    private final String displayName;

    Faction(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

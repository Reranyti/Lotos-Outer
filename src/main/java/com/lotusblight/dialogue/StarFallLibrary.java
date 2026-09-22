package com.lotusblight.dialogue;

import java.util.List;

/**
 * "StarFall" — Star Light's one-time appearance, two variations per branch (war: aggressive,
 * helpless; alliance: friendly, lore-heavy). Lines are the project owner's own writing - this file
 * only holds the data shape. "лоровые моменты что надо запомнить будут фиксировать камеру сами" -
 * mark a line `important = true` to camera-lock the player while it's shown (see StarFallOverlay).
 */
public final class StarFallLibrary {
    private StarFallLibrary() {}

    public record StarLine(String text, boolean important) {}

    // TODO(lore): replace with the real script once written.
    public static List<StarLine> warLines() {
        return List.of(
                new StarLine("...", true)
        );
    }

    // TODO(lore): replace with the real script once written.
    public static List<StarLine> allianceLines() {
        return List.of(
                new StarLine("...", true)
        );
    }
}

package com.lotusblight.overlay;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The circles of an osu! beatmap (.osu). Only what the rhythm game needs: each circle's playfield
 * position and time, plus the approach and hit timings worked out from the map's AR and OD. Positions
 * are in osu's 512x384 playfield; the game scales them to the screen.
 */
final class OsuMap {
    /** One tap: playfield x/y and the millisecond it must be hit. */
    record Circle(double x, double y, double timeMs, boolean newCombo) {}

    final List<Circle> circles = new ArrayList<>();
    double approachMs = 1065;    // how long before its time a circle appears
    double hitWindowMs = 150;    // how far off a tap may be and still count

    static OsuMap load(String resource) {
        OsuMap map = new OsuMap();
        try (InputStream in = OsuMap.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing " + resource);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            String section = "";
            double ar = 5, od = 5;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[")) { section = line; continue; }
                if (section.equals("[Difficulty]")) {
                    if (line.startsWith("ApproachRate:")) ar = parse(line);
                    else if (line.startsWith("OverallDifficulty:")) od = parse(line);
                } else if (section.equals("[HitObjects]")) {
                    String[] p = line.split(",");
                    if (p.length < 4) continue;
                    int type = Integer.parseInt(p[3]);
                    // Skip spinners (bit 3); take circles and slider heads.
                    if ((type & 8) != 0) continue;
                    map.circles.add(new Circle(Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]), (type & 4) != 0));
                }
            }
            map.approachMs = ar >= 5 ? 1200 - 750 * (ar - 5) / 5 : 1200 + 600 * (5 - ar) / 5;
            map.hitWindowMs = Math.max(120, 200 - 10 * od);
        } catch (Exception e) {
            throw new RuntimeException("bad map " + resource, e);
        }
        return map;
    }

    private static double parse(String line) {
        return Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
    }
}

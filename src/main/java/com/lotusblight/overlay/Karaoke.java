package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The chorus karaoke laid over the finale animation (about 2:17-2:45): a top line and a bottom line of
 * the sung lyrics, each shown from its cue until the next one, timed to the track. The cues live in
 * karaoke.txt ("seconds<TAB>top<TAB>bottom") so they're easy to nudge by ear.
 */
final class Karaoke {
    private record Line(double t, String top, String bottom) {}

    private final List<Line> lines = load();

    /** Draws the lyric line active at track time t (seconds), or nothing outside the chorus. */
    void render(Graphics2D g, int w, int h, double t) {
        Line cur = null;
        double next = Double.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).t() <= t) {
                cur = lines.get(i);
                next = i + 1 < lines.size() ? lines.get(i + 1).t() : cur.t() + 4;
            }
        }
        if (cur == null || t > next + 0.3) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double age = t - cur.t();
        float alpha = (float) Math.min(1, age / 0.15);          // quick fade-in on the cue
        if (!cur.top().isEmpty()) line(g, w, cur.top(), h * 0.24, h * 0.075, alpha);
        if (!cur.bottom().isEmpty()) line(g, w, cur.bottom(), h * 0.82, h * 0.06, alpha);
    }

    private static void line(Graphics2D g, int w, String s, double y, double size, float alpha) {
        Font f = g.getFont().deriveFont(Font.BOLD, (float) size);
        g.setFont(f);
        float sz = (float) size;
        while (g.getFontMetrics().stringWidth(s) > w * 0.92 && sz > 10) {
            sz -= 2;
            g.setFont(f.deriveFont(sz));
        }
        int sw = g.getFontMetrics().stringWidth(s);
        int x = (w - sw) / 2;
        // A dark outline so it reads over the bright animation.
        g.setColor(new Color(0, 0, 0, (int) (alpha * 200)));
        for (int dx = -2; dx <= 2; dx += 2)
            for (int dy = -2; dy <= 2; dy += 2) g.drawString(s, x + dx, (int) y + dy);
        g.setColor(new Color(1f, 1f, 1f, alpha));
        g.drawString(s, x, (int) y);
    }

    private static List<Line> load() {
        List<Line> list = new ArrayList<>();
        try (InputStream in = Karaoke.class.getResourceAsStream("/assets/lotusblight/overlay/karaoke.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split("\\t", -1);
                    double t = Double.parseDouble(p[0].trim());
                    list.add(new Line(t, p.length > 1 ? p[1].trim() : "", p.length > 2 ? p[2].trim() : ""));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}

package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The osu!-style rhythm game that plays over the finale scene: circles rise out of the playfield with a
 * shrinking approach ring, and the player left-clicks each one as its ring meets the circle. A hit hurts
 * the Glitcher; a miss costs the player HP. Emptying the player's HP is a loss; reaching the end of the
 * chart is a win. It only reads the clock it is given, so the caller drives it from the music.
 */
final class RhythmGame {
    private static final double PLAYFIELD_W = 512, PLAYFIELD_H = 384;
    private static final double CS = 7.6;               // circle size from the map

    private final OsuMap map;
    private final int maxHp;
    private double hp;
    private int combo;
    private int hits;
    private int nextMiss;                                // first circle not yet hit or judged missed
    private final boolean[] hit;
    private double lastFlash;                            // time of the last hit, for a small flash

    RhythmGame(OsuMap map, int maxHp) {
        this.map = map;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.hit = new boolean[map.circles.size()];
    }

    boolean alive() { return hp > 0; }
    boolean finished(double timeMs) { return nextMiss >= map.circles.size() && timeMs > lastTime() + 500; }
    double hpFraction() { return Math.max(0, hp) / maxHp; }
    int combo() { return combo; }

    private double lastTime() {
        return map.circles.isEmpty() ? 0 : map.circles.get(map.circles.size() - 1).timeMs();
    }

    /** Judges circles whose window has passed as misses. Call each frame before drawing. */
    void update(double timeMs) {
        while (nextMiss < map.circles.size()) {
            OsuMap.Circle c = map.circles.get(nextMiss);
            if (timeMs <= c.timeMs() + map.hitWindowMs) break;
            if (!hit[nextMiss]) { hp -= 1; combo = 0; }
            nextMiss++;
        }
    }

    /** A left click at screen (mx,my). Returns true if it landed on a circle in its hit window. */
    boolean click(double mx, double my, double timeMs, int w, int h) {
        double[] geo = geometry(w, h);
        double radius = geo[2];
        for (int i = nextMiss; i < map.circles.size(); i++) {
            OsuMap.Circle c = map.circles.get(i);
            if (c.timeMs() - map.approachMs > timeMs) break;    // not visible yet
            if (hit[i]) continue;
            if (Math.abs(timeMs - c.timeMs()) > map.hitWindowMs) continue;
            double cx = geo[0] + c.x() / PLAYFIELD_W * geo[3];
            double cy = geo[1] + c.y() / PLAYFIELD_H * geo[4];
            if (Math.hypot(mx - cx, my - cy) <= radius * 1.9) {   // generous catch radius
                hit[i] = true;
                hits++;
                combo++;
                lastFlash = timeMs;
                return true;
            }
        }
        return false;
    }

    void render(Graphics2D g, int w, int h, double timeMs) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double[] geo = geometry(w, h);
        double ox = geo[0], oy = geo[1], radius = geo[2], fw = geo[3], fh = geo[4];

        // Draw upcoming circles, farthest-in-time first so nearer ones sit on top.
        for (int i = map.circles.size() - 1; i >= nextMiss; i--) {
            OsuMap.Circle c = map.circles.get(i);
            double dt = c.timeMs() - timeMs;
            if (dt > map.approachMs || dt < -map.hitWindowMs) continue;
            if (hit[i]) continue;
            double cx = ox + c.x() / PLAYFIELD_W * fw;
            double cy = oy + c.y() / PLAYFIELD_H * fh;
            double appear = Math.min(1, Math.max(0, 1 - dt / map.approachMs));   // 0..1 as it comes in

            // The circle body.
            g.setColor(new Color(0x2A, 0x0B, 0x4A, (int) (appear * 220)));
            g.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
            g.setStroke(new BasicStroke((float) (radius * 0.18)));
            g.setColor(new Color(0xB0, 0x60, 0xFF, (int) (appear * 255)));
            g.drawOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));

            // The approach ring shrinking onto it.
            if (dt > 0) {
                double ar = radius * (1 + 2.6 * dt / map.approachMs);
                g.setStroke(new BasicStroke((float) (radius * 0.12)));
                g.setColor(new Color(0xE0, 0xC0, 0xFF, (int) (appear * 200)));
                g.drawOval((int) (cx - ar), (int) (cy - ar), (int) (ar * 2), (int) (ar * 2));
            }
        }

        drawHud(g, w, h, timeMs);
    }

    private void drawHud(Graphics2D g, int w, int h, double timeMs) {
        // Player HP bar along the top.
        int barW = (int) (w * 0.6), barX = (w - barW) / 2, barY = (int) (h * 0.03), barH = (int) (h * 0.02);
        g.setColor(new Color(40, 20, 60));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(hpFraction() > 0.3 ? new Color(0xB060FF) : new Color(0xFF4060));
        g.fillRect(barX, barY, (int) (barW * hpFraction()), barH);
        g.setColor(new Color(0xE0C0FF));
        g.setStroke(new BasicStroke(2));
        g.drawRect(barX, barY, barW, barH);

        if (combo > 1) {
            g.setColor(new Color(0xE0, 0xC0, 0xFF, 220));
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.05)));
            String s = combo + "x";
            g.drawString(s, (int) (w * 0.03), (int) (h * 0.95));
        }
    }

    /** {offsetX, offsetY, circleRadius, fieldW, fieldH} for the centred playfield. */
    private double[] geometry(int w, int h) {
        double fh = h * 0.82;
        double fw = fh * PLAYFIELD_W / PLAYFIELD_H;
        double ox = (w - fw) / 2, oy = (h - fh) / 2;
        double radiusOsu = 54.4 - 4.48 * CS;            // osu circle radius in playfield px
        double radius = radiusOsu / PLAYFIELD_H * fh * 1.7;   // bigger than osu, easier to see and hit
        return new double[]{ox, oy, radius, fw, fh};
    }
}

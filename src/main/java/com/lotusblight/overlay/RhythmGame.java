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
    private final int bossMaxHp;
    private double bossHp;
    private int combo;
    private int maxCombo;
    private int hits;
    private int misses;
    private int muted;                                   // circles switched off for another mechanic
    private int nextMiss;                                // first circle not yet hit or judged missed
    private final boolean[] hit;
    private double lastFlash;                            // time of the last hit, for a small flash

    RhythmGame(OsuMap map, int maxHp) {
        this.map = map;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.bossMaxHp = Math.max(1, map.circles.size());
        this.bossHp = bossMaxHp;
        this.hit = new boolean[map.circles.size()];
    }

    /** Starting mid-song: drop every note before timeMs without counting it as a miss. */
    void skipTo(double timeMs) {
        while (nextMiss < map.circles.size() && map.circles.get(nextMiss).timeMs() < timeMs - map.hitWindowMs) {
            hit[nextMiss] = true;
            bossHp -= 1;
            nextMiss++;
        }
    }

    /** Circles inside [from, to) are switched off: another mechanic owns that stretch of the song. */
    void mute(double fromMs, double toMs) {
        for (int i = 0; i < map.circles.size(); i++) {
            double t = map.circles.get(i).timeMs();
            if (t >= fromMs && t < toMs && !hit[i]) { hit[i] = true; muted++; }
        }
    }

    /** One contact beat landed: the Glitcher's bar drops by that beat's share of the circles that were switched off. */
    void contactHit(double share) { bossHp -= muted * share; }

    private double lastHitAt = -1e9, lastMissAt = -1e9;
    private boolean hudFull;

    double lastHitAt() { return lastHitAt; }

    double lastMissAt() { return lastMissAt; }

    /** The health bars stay unfolded and named, as after the second song's eye scene. */
    void fullHud() { hudFull = true; }

    /** Damage that isn't a missed circle (the eye that wants the space bar). */
    void hurt(double amount) { hp -= amount; }

    boolean alive() { return hp > 0; }
    boolean finished(double timeMs) { return nextMiss >= map.circles.size() && timeMs > lastTime() + 500; }
    double hpFraction() { return Math.max(0, hp) / maxHp; }
    double bossFraction() { return Math.max(0, bossHp) / bossMaxHp; }
    int combo() { return combo; }
    int hits() { return hits; }
    int misses() { return misses; }
    int total() { return map.circles.size() - muted; }
    int maxCombo() { return maxCombo; }

    /** A rank for an accuracy 0..1, osu-style. */
    static String grade(double accuracy) {
        if (accuracy >= 0.97) return "S";
        if (accuracy >= 0.90) return "A";
        if (accuracy >= 0.80) return "B";
        if (accuracy >= 0.70) return "C";
        return "D";
    }

    private double lastTime() {
        return map.circles.isEmpty() ? 0 : map.circles.get(map.circles.size() - 1).timeMs();
    }

    /** Judges circles whose window has passed as misses. Call each frame before drawing. */
    void update(double timeMs) {
        while (nextMiss < map.circles.size()) {
            OsuMap.Circle c = map.circles.get(nextMiss);
            if (timeMs <= c.timeMs() + map.hitWindowMs) break;
            if (!hit[nextMiss]) { hp -= 1; misses++; combo = 0; lastMissAt = timeMs; }
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
                bossHp -= 1;
                combo++;
                maxCombo = Math.max(maxCombo, combo);
                lastFlash = timeMs;
                lastHitAt = timeMs;
                return true;
            }
        }
        return false;
    }

    void render(Graphics2D g, int w, int h, double timeMs) {
        render(g, w, h, timeMs, true);
    }

    /** hud false draws just the circles, no HP bars or combo (used where the bars are hidden). */
    void render(Graphics2D g, int w, int h, double timeMs, boolean hud) {
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

        if (hud) drawHud(g, w, h, timeMs);
    }

    /** Just the bars and the combo (when the circles are drawn somewhere smaller). */
    void renderHud(Graphics2D g, int w, int h, double timeMs) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawHud(g, w, h, timeMs);
    }

    private void drawHud(Graphics2D g, int w, int h, double timeMs) {
        // From 1:19 of the second song the bars unfold to both sides and get their names.
        double u = hudFull ? 1 : ContactBreak.unfold(timeMs);
        double names = hudFull ? 1 : ContactBreak.labelAlpha(timeMs);
        // The Glitcher (boss) HP along the very top, wide and red-purple.
        int bw = (int) (w * (0.7 + 0.24 * u)), bx = (w - bw) / 2, by = (int) (h * 0.018), bh = (int) (h * (0.022 + 0.006 * u));
        g.setColor(new Color(30, 12, 40));
        g.fillRect(bx, by, bw, bh);
        g.setColor(new Color(0x8A2BE2));
        g.fillRect(bx, by, (int) (bw * bossFraction()), bh);
        g.setColor(new Color(0xE0C0FF));
        g.setStroke(new BasicStroke(2));
        g.drawRect(bx, by, bw, bh);

        // Player HP just under it, narrower.
        int barW = (int) (w * (0.5 + 0.34 * u)), barX = (w - barW) / 2, barY = by + bh + (int) (h * 0.012), barH = (int) (h * (0.016 + 0.006 * u));
        g.setColor(new Color(40, 20, 60));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(hpFraction() > 0.3 ? new Color(0x60D0FF) : new Color(0xFF4060));
        g.fillRect(barX, barY, (int) (barW * hpFraction()), barH);
        g.setColor(new Color(0xC0E0FF));
        g.drawRect(barX, barY, barW, barH);

        if (names > 0) {
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.017)));
            label(g, "Ошибки", bx + 8, by + bh - (int) (bh * 0.22), names);
            label(g, "Ваше хп", barX + 8, barY + barH - (int) (barH * 0.18), names);
        }

        if (combo > 1) {
            g.setColor(new Color(0xE0, 0xC0, 0xFF, 220));
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.05)));
            String s = combo + "x";
            g.drawString(s, (int) (w * 0.03), (int) (h * 0.95));
        }
    }

    private static void label(Graphics2D g, String text, int x, int baseline, double alpha) {
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        g.setColor(new Color(0, 0, 0, a * 3 / 4));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(new Color(255, 255, 255, a));
        g.drawString(text, x, baseline);
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

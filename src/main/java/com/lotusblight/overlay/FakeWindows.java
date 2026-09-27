package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The Glitcher's phase-two attack: fake Windows error dialogs that spam the screen in a spiral, one on
 * each vocal syllable of the track, piling up to block the circles. They are drawn by us inside the
 * overlay - not real OS windows - and never touch a single file; the "Delete your hard drive" text is
 * pure theatre, like the old error-spam memes.
 */
final class FakeWindows {
    private static final String[] TITLES = {"Microsoft Windows", "Warning", "System Error", "无信号"};
    private static final String[] BODIES = {
            "Windows was not installed correctly. Please reinstall Windows.",
            "Windows has encountered 1 541 373 errors and counting.",
            "Proceeding with the operation 'Delete' will erase the contents of your hard drive.",
            "Critical process 有线 has stopped responding.",
            "无信号  —  NO SIGNAL",
            "A fatal exception 0E has occurred at 0028:C0011E36.",
    };
    private static final String[][] BUTTONS = {{"OK"}, {"Proceed", "Delete"}, {"Retry", "Cancel"}};

    // When no sung phrase is active, windows fall back to the chant.
    private static final String[] CHANTS = {"有线", "无信号"};

    /** A sung phrase and when it starts, in seconds. */
    private record Phrase(double t, String text) {}
    private final List<Phrase> phrases = loadPhrases();

    /** The phrase being sung at time t (seconds), or null before the first one. */
    private String phraseAt(double t) {
        String cur = null;
        for (Phrase p : phrases) {
            if (p.t() <= t && t - p.t() < 1.4) cur = p.text();   // shown briefly after it's sung
        }
        return cur;
    }

    /** One dialog: where it sits and which fake text it shows. */
    private static final class Win {
        int x, y, w, h, title, body, btn;
        boolean chant;
        String chantText;              // the sung phrase (or a fallback chant syllable)
        double born;
    }

    private final double[] times;      // syllable times in ms
    private int spawned;
    private final List<Win> wins = new ArrayList<>();
    private final java.util.Random rnd = new java.util.Random(1);
    private double lastSpawnMs = -1;
    private double fade = 1;            // the whole pile fades out in the gaps between phrases
    /** Next step along the staircase - it only moves on when a window takes a new spot. */
    private int path;
    /** Spots of windows the player closed; new windows fill these first, in the order they were freed. */
    private final java.util.ArrayDeque<int[]> freed = new java.util.ArrayDeque<>();

    FakeWindows() {
        this.times = loadTimes();
    }

    /** Spawns any dialog whose syllable has arrived and draws the pile. timeMs is the track time. */
    void render(Graphics2D g, int w, int h, double timeMs) {
        while (spawned < times.length && times[spawned] * 1000 <= timeMs) {
            // A closed window's spot is taken again before the staircase goes on, so closing windows
            // keeps the wall where it was instead of letting it spread over more of the screen.
            int[] spot = freed.poll();
            Win win = makeWindow(spot == null ? path++ : path, w, h, times[spawned]);
            if (spot != null) {
                win.x = spot[0];
                win.y = spot[1];
            }
            wins.add(win);
            lastSpawnMs = timeMs;
            fade = 1;
            spawned++;
        }
        // Keep the pile bounded so it stays a wall, not a memory leak.
        while (wins.size() > 160) wins.remove(0);
        // In a gap between phrases (or after the last one), the whole wall fades away and clears.
        if (lastSpawnMs >= 0 && timeMs - lastSpawnMs > 1200) {
            fade -= 0.04;
            if (fade <= 0) { wins.clear(); freed.clear(); fade = 0; return; }
        }
        if (wins.isEmpty()) return;

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (fade < 1) b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) fade));
        for (Win win : wins) draw(b, win);
        b.dispose();
    }

    /** A left click at (mx,my): closes the topmost window there and returns true if one was closed. */
    boolean close(double mx, double my) {
        for (int i = wins.size() - 1; i >= 0; i--) {
            Win win = wins.get(i);
            if (mx >= win.x && mx <= win.x + win.w && my >= win.y && my <= win.y + win.h) {
                wins.remove(i);
                freed.add(new int[]{win.x, win.y});
                return true;
            }
        }
        return false;
    }

    void reset() { wins.clear(); freed.clear(); spawned = 0; path = 0; fade = 1; lastSpawnMs = -1; }

    /** Lay the dialogs along a slowly turning path so the pile winds into a spiral, meme-style. */
    private Win makeWindow(int i, int screenW, int screenH, double tSec) {
        Win win = new Win();
        win.w = (int) (screenW * 0.22);
        win.h = (int) (win.w * 0.42);
        // A diagonal staircase that bounces off the screen edges, like the classic error-spam cascade:
        // each window steps a little right-and-down from the last, folding back at the borders.
        double stepX = screenW * 0.022, stepY = screenH * 0.03;
        double spanX = screenW - win.w, spanY = screenH - win.h;
        win.x = (int) bounce(i * stepX, spanX);
        win.y = (int) bounce(i * stepY, spanY);
        win.title = i % TITLES.length;
        win.body = rnd.nextInt(BODIES.length);
        win.btn = rnd.nextInt(BUTTONS.length);
        // A window either chants the phrase being sung right now, or shows an error. When a phrase is
        // sung, windows echo it; otherwise they fall back to the 有线/无信号 syllables.
        // Most windows carry the text sung right now (the phrase, or the 有线/无信号 syllables); the rest
        // are error dialogs for variety.
        String phrase = phraseAt(tSec);
        if (phrase != null) {
            win.chant = true;
            win.chantText = phrase;
        } else if (rnd.nextInt(10) < 7) {
            win.chant = true;
            win.chantText = CHANTS[rnd.nextInt(CHANTS.length)];
        }
        return win;
    }

    private void draw(Graphics2D g, Win win) {
        if (win.chant) {
            // A window that shows the sung phrase, big and centred.
            int barH = drawFrame(g, win.x, win.y, win.w, win.h, TITLES[win.title]);
            g.setColor(Color.BLACK);
            String s = win.chantText;
            float size = win.h * 0.34f;
            g.setFont(g.getFont().deriveFont(Font.BOLD, size));
            while (g.getFontMetrics().stringWidth(s) > win.w * 0.9 && size > 8) {
                size -= 2;
                g.setFont(g.getFont().deriveFont(Font.BOLD, size));
            }
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, win.x + (win.w - sw) / 2, win.y + barH + (int) ((win.h - barH) * 0.62));
            return;
        }
        drawDialog(g, win.x, win.y, win.w, win.h, TITLES[win.title], BODIES[win.body], BUTTONS[win.btn]);
    }

    /** Where a drawn dialog's close box and buttons are, for click testing. */
    record Hits(Rectangle close, Rectangle[] buttons) {}

    /**
     * One error dialog in the old Windows look: title bar, red error icon, the body (a '\n' starts a new
     * line, long lines wrap at spaces) and the buttons along the bottom right. Font sizes follow h.
     */
    static Hits drawDialog(Graphics2D g, int x, int y, int w, int h, String title, String body, String[] btns) {
        int barH = drawFrame(g, x, y, w, h, title);
        // Red error icon.
        int ix = x + w / 14, iy = y + barH + h / 8, is = (int) (h * 0.22);
        g.setColor(new Color(0xD0021B));
        g.fillOval(ix, iy, is, is);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, is * 0.9f));
        g.drawString("x", ix + is / 4, iy + (int) (is * 0.78));
        // Body text, wrapped short.
        g.setColor(Color.BLACK);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, h * 0.11f));
        int cy = y + barH + h / 6, lineH = (int) (h * 0.16);
        for (String paragraph : body.split("\n")) {
            cy = wrap(g, paragraph, ix + is + 6, cy, (int) (w * 0.72), lineH);
        }
        // Buttons.
        Rectangle[] rects = new Rectangle[btns.length];
        // Sized off the width, but never wider than a wide dialog's height allows (the cascade never hits this).
        int bw = Math.min(w / 4, (int) (h * 0.75)), bh = (int) (h * 0.16), gap = Math.min(w / 30, h / 10);
        int bx = x + w - (bw + gap) * btns.length;
        int byy = y + h - bh - gap;
        for (int i = 0; i < btns.length; i++) {
            String s = btns[i];
            g.setColor(new Color(0xE0E0E0));
            g.fillRect(bx, byy, bw, bh);
            g.setColor(new Color(0x707070));
            g.drawRect(bx, byy, bw, bh);
            g.setColor(Color.BLACK);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, bh * 0.55f));
            g.drawString(s, bx + bw / 5, byy + (int) (bh * 0.68));
            rects[i] = new Rectangle(bx, byy, bw, bh);
            bx += bw + gap;
        }
        int b = (int) (barH * 0.6);
        return new Hits(new Rectangle(x + w - b - 4, y + (barH - b) / 2, b, b), rects);
    }

    /** The shared window chrome: title bar, close box, grey body and outline. Returns the title bar height. */
    static int drawFrame(Graphics2D g, int x, int y, int w, int h, String title) {
        // Title bar - the old blue gradient.
        g.setPaint(new GradientPaint(x, y, new Color(0x2A5BD7), x, y + h * 0.22f, new Color(0x4E8BF5)));
        int barH = (int) (h * 0.22);
        g.fillRect(x, y, w, barH);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, h * 0.13f));
        g.drawString(title, x + w / 40, y + (int) (barH * 0.7));
        // Close/min/max buttons.
        int b = (int) (barH * 0.6), by = y + (barH - b) / 2;
        g.setColor(new Color(0xC0392B));
        g.fillRect(x + w - b - 4, by, b, b);
        g.setColor(Color.WHITE);
        g.drawString("x", x + w - b - 4 + b / 4, by + (int) (b * 0.8));

        // Body.
        g.setColor(new Color(0xF0F0F0));
        g.fillRect(x, y + barH, w, h - barH);
        g.setColor(new Color(0x808080));
        g.setStroke(new BasicStroke(1));
        g.drawRect(x, y, w, h);
        return barH;
    }

    /** Folds a growing value back and forth within [0, span] - a triangle wave, for the bounce. */
    private static double bounce(double v, double span) {
        if (span <= 0) return 0;
        double m = v % (2 * span);
        if (m < 0) m += 2 * span;
        return m <= span ? m : 2 * span - m;
    }

    /** Draws text wrapped at spaces within maxW; returns the baseline for the next line after it. */
    private static int wrap(Graphics2D g, String text, int x, int y, int maxW, int lineH) {
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(test) > maxW && line.length() > 0) {
                g.drawString(line.toString(), x, cy);
                cy += lineH;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, cy);
            cy += lineH;
        }
        return cy;
    }

    private static double[] loadTimes() {
        List<Double> t = new ArrayList<>();
        try (InputStream in = FakeWindows.class.getResourceAsStream("/assets/lotusblight/overlay/syllables.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) t.add(Double.parseDouble(line));
                }
            }
        } catch (Exception ignored) {
        }
        double[] a = new double[t.size()];
        for (int i = 0; i < a.length; i++) a[i] = t.get(i);
        return a;
    }

    /** The sung phrases with their start times, from phrases.txt ("seconds<TAB>text" per line). */
    private static List<Phrase> loadPhrases() {
        List<Phrase> list = new ArrayList<>();
        try (InputStream in = FakeWindows.class.getResourceAsStream("/assets/lotusblight/overlay/phrases.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split("\\t", 2);
                    if (p.length == 2) list.add(new Phrase(Double.parseDouble(p[0].trim()), p[1].trim()));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}

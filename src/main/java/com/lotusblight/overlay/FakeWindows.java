package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
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

    FakeWindows() {
        this.times = loadTimes();
    }

    /** Spawns any dialog whose syllable has arrived and draws the pile. timeMs is the track time. */
    void render(Graphics2D g, int w, int h, double timeMs) {
        while (spawned < times.length && times[spawned] * 1000 <= timeMs) {
            wins.add(makeWindow(spawned, w, h, times[spawned]));
            lastSpawnMs = timeMs;
            fade = 1;
            spawned++;
        }
        // Keep the pile bounded so it stays a wall, not a memory leak.
        while (wins.size() > 160) wins.remove(0);
        // In a gap between phrases (or after the last one), the whole wall fades away and clears.
        if (lastSpawnMs >= 0 && timeMs - lastSpawnMs > 1200) {
            fade -= 0.04;
            if (fade <= 0) { wins.clear(); fade = 0; return; }
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
                return true;
            }
        }
        return false;
    }

    void reset() { wins.clear(); spawned = 0; fade = 1; lastSpawnMs = -1; }

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
        // Title bar - the old blue gradient.
        g.setPaint(new GradientPaint(win.x, win.y, new Color(0x2A5BD7), win.x, win.y + win.h * 0.22f, new Color(0x4E8BF5)));
        int barH = (int) (win.h * 0.22);
        g.fillRect(win.x, win.y, win.w, barH);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, win.h * 0.13f));
        g.drawString(TITLES[win.title], win.x + win.w / 40, win.y + (int) (barH * 0.7));
        // Close/min/max buttons.
        int b = (int) (barH * 0.6), by = win.y + (barH - b) / 2;
        g.setColor(new Color(0xC0392B));
        g.fillRect(win.x + win.w - b - 4, by, b, b);
        g.setColor(Color.WHITE);
        g.drawString("x", win.x + win.w - b - 4 + b / 4, by + (int) (b * 0.8));

        // Body.
        g.setColor(new Color(0xF0F0F0));
        g.fillRect(win.x, win.y + barH, win.w, win.h - barH);
        g.setColor(new Color(0x808080));
        g.setStroke(new BasicStroke(1));
        g.drawRect(win.x, win.y, win.w, win.h);
        if (win.chant) {
            // A window that shows the sung phrase, big and centred.
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
        // Red error icon.
        int ix = win.x + win.w / 14, iy = win.y + barH + win.h / 8, is = (int) (win.h * 0.22);
        g.setColor(new Color(0xD0021B));
        g.fillOval(ix, iy, is, is);
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(Font.BOLD, is * 0.9f));
        g.drawString("x", ix + is / 4, iy + (int) (is * 0.78));
        // Body text, wrapped short.
        g.setColor(Color.BLACK);
        g.setFont(g.getFont().deriveFont(Font.PLAIN, win.h * 0.11f));
        wrap(g, BODIES[win.body], ix + is + 6, win.y + barH + win.h / 6, (int) (win.w * 0.72), (int) (win.h * 0.16));
        // Buttons.
        String[] btns = BUTTONS[win.btn];
        int bw = win.w / 4, bh = (int) (win.h * 0.16), gap = win.w / 30;
        int bx = win.x + win.w - (bw + gap) * btns.length;
        int byy = win.y + win.h - bh - gap;
        for (String s : btns) {
            g.setColor(new Color(0xE0E0E0));
            g.fillRect(bx, byy, bw, bh);
            g.setColor(new Color(0x707070));
            g.drawRect(bx, byy, bw, bh);
            g.setColor(Color.BLACK);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, bh * 0.55f));
            g.drawString(s, bx + bw / 5, byy + (int) (bh * 0.68));
            bx += bw + gap;
        }
    }

    /** Folds a growing value back and forth within [0, span] - a triangle wave, for the bounce. */
    private static double bounce(double v, double span) {
        if (span <= 0) return 0;
        double m = v % (2 * span);
        if (m < 0) m += 2 * span;
        return m <= span ? m : 2 * span - m;
    }

    private static void wrap(Graphics2D g, String text, int x, int y, int maxW, int lineH) {
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
        if (line.length() > 0) g.drawString(line.toString(), x, cy);
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

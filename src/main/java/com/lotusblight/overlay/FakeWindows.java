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

    /** One dialog: where it sits and which fake text it shows. */
    private static final class Win {
        int x, y, w, h, title, body, btn;
        double born;
    }

    private final double[] times;      // syllable times in ms
    private int spawned;
    private final List<Win> wins = new ArrayList<>();
    private final java.util.Random rnd = new java.util.Random(1);

    FakeWindows() {
        this.times = loadTimes();
    }

    /** Spawns any dialog whose syllable has arrived and draws the pile. timeMs is the track time. */
    void render(Graphics2D g, int w, int h, double timeMs) {
        while (spawned < times.length && times[spawned] * 1000 <= timeMs) {
            wins.add(makeWindow(spawned, w, h));
            spawned++;
        }
        // Keep the pile bounded so it stays a wall, not a memory leak.
        while (wins.size() > 120) wins.remove(0);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Win win : wins) draw(g, win);
    }

    void reset() { wins.clear(); spawned = 0; }

    /** Lay the dialogs along a slowly turning path so the pile winds into a spiral, meme-style. */
    private Win makeWindow(int i, int screenW, int screenH) {
        Win win = new Win();
        win.w = (int) (screenW * 0.22);
        win.h = (int) (win.w * 0.42);
        double cx = screenW * 0.5, cy = screenH * 0.5;
        double ang = i * 0.5;                       // winds around
        double rad = (i % 40) * (screenW * 0.006);  // spirals out, then restarts the arm
        win.x = (int) (cx + Math.cos(ang) * rad - win.w / 2.0);
        win.y = (int) (cy + Math.sin(ang) * rad * 0.6 - win.h / 2.0);
        win.title = i % TITLES.length;
        win.body = rnd.nextInt(BODIES.length);
        win.btn = rnd.nextInt(BUTTONS.length);
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
}

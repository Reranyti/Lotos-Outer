package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The closing cutscene, shown after the results of the third song. This is only its base: the stage, the two
 * figures, the fade in and out, the captions and the way out. What is said, and what happens between the
 * lines, is written into {@link #LINES} and {@link #BEATS} by whoever writes the story - nothing here decides it.
 *
 * <p>It runs on the wall clock, like the cutscene between the songs, and the scene is a pure function of
 * that clock, so any moment of it can be previewed with {@code --finale SECONDS}.
 */
final class Finale3 {
    /** {from ms, to ms, Japanese, English, speaker (0 nobody, 1 Honcho, 2 the Glitcher)} - the captions. Empty until written. */
    private static final Object[][] LINES = {
    };

    /**
     * Moments of the scene besides the words, {from ms, to ms, kind}. Nothing is wired to them yet; a kind is
     * a hook for the picture code below (see {@link #drawBeat}).
     */
    private static final Object[][] BEATS = {
    };

    private static final double FADE_IN_MS = 1_800, FADE_OUT_MS = 2_400, LEAD_OUT_MS = 2_500;
    /** How long the scene lasts when no lines are written yet. */
    private static final double EMPTY_LENGTH_MS = 8_000;

    private final BufferedImage desktop;
    private final int[] glitcherSkin, honchoSkin;
    private final SkinModel model = new SkinModel(), honchoModel = SkinModel.honcho();
    private final SoftRenderer[] figures = new SoftRenderer[2];
    private final SoftRenderer.Pose pose = new SoftRenderer.Pose();
    private final double unit;
    private final long startNano = System.nanoTime();
    private double seekMs;

    Finale3(int[] glitcherSkin, int[] honchoSkin, BufferedImage desktop, int screenH) {
        this.glitcherSkin = glitcherSkin;
        this.honchoSkin = honchoSkin;
        this.desktop = desktop;
        this.unit = screenH * 0.36 / 32.0 * 0.6;
        for (int i = 0; i < 2; i++) figures[i] = new SoftRenderer((int) (30 * unit), (int) (36 * unit));
    }

    /** Starts the scene at a given second (for previewing). */
    void seek(double seconds) {
        seekMs = seconds * 1000 - (System.nanoTime() - startNano) / 1e6;
    }

    private double now() {
        return (System.nanoTime() - startNano) / 1e6 + seekMs;
    }

    /** How long the whole scene lasts, by its last written line (or a short default while there are none). */
    private static double length() {
        double end = 0;
        for (Object[] l : LINES) end = Math.max(end, (Double) l[1]);
        for (Object[] b : BEATS) end = Math.max(end, (Double) b[1]);
        return end > 0 ? end + LEAD_OUT_MS : EMPTY_LENGTH_MS;
    }

    /** True once the last of it has faded to black. */
    boolean done() {
        return now() >= length() + FADE_OUT_MS;
    }

    void render(Graphics2D g, int w, int h) {
        double c = now();
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // The stage: the real desktop, dimmed, with a soft light on the floor where they stand.
        b.setColor(new Color(4, 3, 10));
        b.fillRect(0, 0, w, h);
        if (desktop != null) {
            b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            b.drawImage(desktop, 0, 0, w, h, null);
            b.setComposite(AlphaComposite.SrcOver);
        }
        b.setPaint(new GradientPaint(0, 0, new Color(4, 3, 12, 150), 0, h, new Color(10, 6, 24, 200)));
        b.fillRect(0, 0, w, h);
        b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h * 0.78), (float) (w * 0.42), new float[]{0f, 1f},
                new Color[]{new Color(150, 120, 220, 70), new Color(150, 120, 220, 0)}));
        b.fillRect(0, 0, w, h);

        // The two of them, side by side, at rest.
        drawFigure(b, w, h, c, 0, w * 0.40, h * 0.86);
        drawFigure(b, w, h, c, 1, w * 0.60, h * 0.86);

        for (Object[] beat : BEATS) {
            if (c >= (Double) beat[0] && c < (Double) beat[1]) drawBeat(b, w, h, c, (String) beat[2], (c - (Double) beat[0]) / ((Double) beat[1] - (Double) beat[0]));
        }
        for (Object[] line : LINES) {
            if (c >= (Double) line[0] && c < (Double) line[1]) caption(b, w, h, c, line);
        }

        // In from black, out to black.
        double in = Math.min(1, c / FADE_IN_MS);
        double out = Math.max(0, Math.min(1, (c - length()) / FADE_OUT_MS));
        double dark = Math.max(1 - in, out);
        if (dark > 0) {
            b.setColor(new Color(0, 0, 0, (int) (255 * dark)));
            b.fillRect(0, 0, w, h);
        }
        b.dispose();
    }

    private void drawFigure(Graphics2D g, int w, int h, double c, int who, double x, double feetY) {
        double sway = Math.sin(c / 1000.0 * 1.1 + who * 1.7);
        pose.reset();
        pose.yaw = who == 0 ? 0.55 : -0.55;                              // turned a little towards each other
        pose.partPitch[SkinModel.Part.RIGHT_ARM.ordinal()] = 0.04 * sway;
        pose.partPitch[SkinModel.Part.LEFT_ARM.ordinal()] = -0.04 * sway;
        SoftRenderer r = figures[who];
        r.clear();
        r.draw(who == 0 ? honchoModel : model, who == 0 ? honchoSkin : glitcherSkin, pose, unit, r.width / 2.0, r.height - 2 * unit);
        Graphics2D d = (Graphics2D) g.create();
        double sc = 1 / 0.6;
        d.translate(x, feetY);
        d.scale(sc, sc);
        d.drawImage(r.image, -r.width / 2, -r.height + (int) (2 * unit), null);
        d.dispose();
    }

    /** Where the picture code for a {@code BEATS} entry goes; {@code k} runs 0..1 over the beat. Nothing is drawn yet. */
    private void drawBeat(Graphics2D g, int w, int h, double c, String kind, double k) {
    }

    /** A caption at the bottom: the English line under the Japanese one, and a mark for who speaks. */
    private void caption(Graphics2D g, int w, int h, double c, Object[] line) {
        double from = (Double) line[0], to = (Double) line[1];
        double a = Math.min(1, Math.min((c - from) / 250.0, (to - c) / 300.0));
        if (a <= 0) return;
        String jp = (String) line[2], en = (String) line[3];
        int speaker = line.length > 4 ? (Integer) line[4] : 0;
        Color tint = speaker == 1 ? new Color(255, 236, 190) : speaker == 2 ? new Color(230, 170, 255) : new Color(240, 240, 250);
        Graphics2D d = (Graphics2D) g.create();
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
        int size = (int) (h * 0.034);
        d.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        FontMetrics fm = d.getFontMetrics();
        int jw = fm.stringWidth(jp);
        d.setFont(new Font(Font.DIALOG, Font.PLAIN, (int) (size * 0.8)));
        FontMetrics fe = d.getFontMetrics();
        int ew = fe.stringWidth(en);
        int boxW = Math.max(jw, ew) + (int) (h * 0.08), boxH = (int) (h * 0.11);
        int bx = (w - boxW) / 2, by = (int) (h * 0.86);
        d.setColor(new Color(0, 0, 0, 150));
        d.fillRoundRect(bx, by, boxW, boxH, 18, 18);
        d.setFont(new Font(Font.DIALOG, Font.BOLD, size));
        d.setColor(tint);
        d.drawString(jp, (w - jw) / 2, by + (int) (boxH * 0.44));
        d.setFont(new Font(Font.DIALOG, Font.PLAIN, (int) (size * 0.8)));
        d.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 210));
        d.drawString(en, (w - ew) / 2, by + (int) (boxH * 0.85));
        d.dispose();
    }
}

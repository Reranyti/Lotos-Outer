package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * The Architect "death screen": the Glitcher seen from behind, a glowing symbol floating above him and
 * a line of text, the whole thing lit in one Architect's colour. Four of them - blue, gold, red,
 * purple - each with its own symbol and line. Purely a picture drawn for a moment in time.
 */
final class ArchitectScene {
    /** One Architect: colour, floating symbol and the line under it. */
    enum Architect {
        BLUE(0x3E7BFF, 0xBBD4FF, "Сик поймал тебя..."),
        GOLD(0xFFC24A, 0xFFE6A8, "Dead again."),
        RED(0xFF3030, 0xFF9A9A, "It was very foolish to stay in plain sight..."),
        PURPLE(0xB25CFF, 0xE0BBFF, "You died to my creation...");

        final int glow;
        final int text;
        final String line;

        Architect(int glow, int text, String line) {
            this.glow = glow;
            this.text = text;
            this.line = line;
        }
    }

    private final BufferedImage back;    // the Glitcher from behind, dark
    private final int figW;
    private final int figH;
    private final double originX;
    private final double originY;
    private final double scale;

    ArchitectScene(int[] skin, int height) {
        this.scale = height * 0.44 / 32.0;
        double rs = scale / 2;
        SkinModel model = new SkinModel();
        SoftRenderer r = new SoftRenderer((int) (26 * rs), (int) (42 * rs));
        r.clear();
        r.draw(model, skin, backPose(), rs, r.width / 2.0, r.height - 2 * rs);
        this.figW = r.width * 2;
        this.figH = r.height * 2;
        this.originX = r.width;
        this.originY = (r.height - 2 * rs) * 2;
        this.back = new BufferedImage(figW, figH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = back.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(r.image, 0, 0, figW, figH, null);
        g.dispose();
        darken(back);
    }

    /** Draws Architect a at time seconds. reveal 0..1 fades the whole screen in. */
    void render(Graphics2D g, int w, int h, Architect a, double time, double reveal) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color glow = new Color(a.glow);
        double symX = w * 0.5;
        double symY = h * 0.20;
        double breathe = 1 + 0.05 * Math.sin(time * 2.2);

        // Near-black background, lit from the symbol with the Architect's colour.
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        float rad = (float) (h * 0.9);
        g.setPaint(new RadialGradientPaint((float) symX, (float) symY, rad, new float[]{0f, 1f},
                new Color[]{new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 90),
                        new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);

        drawSpecks(g, w, h, glow, time);

        // A rim of the colour behind the figure, then the dark figure from behind.
        double fx = w * 0.5;
        double fy = h * 0.99;
        float grad = (float) (figH * 0.7 * breathe);
        g.setPaint(new RadialGradientPaint((float) fx, (float) (fy - figH * 0.4), grad, new float[]{0f, 1f},
                new Color[]{new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 130), new Color(0, 0, 0, 0)}));
        g.fillOval((int) (fx - grad), (int) (fy - figH * 0.4 - grad), (int) (grad * 2), (int) (grad * 2));
        g.drawImage(back, (int) (fx - originX), (int) (fy - originY), null);

        drawSymbol(g, a, symX, symY, h * 0.075 * breathe, glow);

        // Fade the whole thing in.
        if (reveal < 1) {
            g.setColor(new Color(0, 0, 0, (int) ((1 - reveal) * 255)));
            g.fillRect(0, 0, w, h);
        }
    }

    private void drawSymbol(Graphics2D g, Architect a, double x, double y, double s, Color glow) {
        Graphics2D b = (Graphics2D) g.create();
        b.setPaint(new RadialGradientPaint((float) x, (float) y, (float) (s * 3), new float[]{0f, 1f},
                new Color[]{new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 200), new Color(0, 0, 0, 0)}));
        b.fillOval((int) (x - s * 3), (int) (y - s * 3), (int) (s * 6), (int) (s * 6));
        b.setColor(brighten(glow));
        b.setStroke(new BasicStroke((float) (s * 0.28), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (a) {
            case BLUE -> {                           // crescent moon
                java.awt.geom.Area moon = new java.awt.geom.Area(new Ellipse2D.Double(x - s, y - s, s * 2, s * 2));
                moon.subtract(new java.awt.geom.Area(new Ellipse2D.Double(x - s * 0.4, y - s * 1.1, s * 2, s * 2.2)));
                b.fill(moon);
            }
            case GOLD -> {                           // four-pointed star
                Path2D star = new Path2D.Double();
                double outer = s * 1.5, inner = s * 0.42;
                for (int i = 0; i < 8; i++) {
                    double an = -Math.PI / 2 + i * Math.PI / 4;
                    double rr = (i % 2 == 0) ? outer : inner;
                    double px = x + Math.cos(an) * rr, py = y + Math.sin(an) * rr;
                    if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
                }
                star.closePath();
                b.fill(star);
            }
            case RED -> {                            // diamond with an inner spiral
                Path2D d = new Path2D.Double();
                d.moveTo(x, y - s); d.lineTo(x + s, y); d.lineTo(x, y + s); d.lineTo(x - s, y); d.closePath();
                b.draw(d);
                Path2D sp = new Path2D.Double();
                for (double t = 0; t < Math.PI * 3; t += 0.2) {
                    double r = s * 0.5 * t / (Math.PI * 3);
                    double px = x + Math.cos(t) * r, py = y + Math.sin(t) * r;
                    if (t == 0) sp.moveTo(px, py); else sp.lineTo(px, py);
                }
                b.draw(sp);
            }
            default -> {                             // upward triangle / arrow
                Path2D t = new Path2D.Double();
                t.moveTo(x, y - s); t.lineTo(x + s * 0.9, y + s * 0.7); t.lineTo(x - s * 0.9, y + s * 0.7); t.closePath();
                b.draw(t);
            }
        }
        b.dispose();
    }

    private void drawSpecks(Graphics2D g, int w, int h, Color glow, double time) {
        java.util.Random rnd = new java.util.Random(1234);
        g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 150));
        for (int i = 0; i < 40; i++) {
            double bx = rnd.nextDouble() * w;
            double drift = (time * (10 + rnd.nextInt(20))) % (h * 0.5);
            double by = (rnd.nextDouble() * h * 0.6 + h * 0.15) - drift;
            if (by < 0) by += h * 0.6;
            int sz = 2 + rnd.nextInt(3);
            g.fillOval((int) bx, (int) by, sz, sz);
        }
    }

    /** Facing away from the camera, standing, head bowed a little. */
    private static SoftRenderer.Pose backPose() {
        SoftRenderer.Pose p = new SoftRenderer.Pose();
        p.yaw = Math.PI;
        p.partPitch[SkinModel.Part.HEAD.ordinal()] = 0.15;
        p.partRoll[SkinModel.Part.RIGHT_ARM.ordinal()] = -0.08;
        p.partRoll[SkinModel.Part.LEFT_ARM.ordinal()] = 0.08;
        return p;
    }

    private static void darken(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int c = img.getRGB(x, y);
                if ((c >>> 24) == 0) continue;
                int r = (int) (((c >> 16) & 0xFF) * 0.18);
                int gg = (int) (((c >> 8) & 0xFF) * 0.18);
                int bb = (int) ((c & 0xFF) * 0.20);
                img.setRGB(x, y, (c & 0xFF000000) | r << 16 | gg << 8 | bb);
            }
        }
    }

    private static Color brighten(Color c) {
        return new Color(Math.min(255, c.getRed() + 80), Math.min(255, c.getGreen() + 80), Math.min(255, c.getBlue() + 80));
    }
}

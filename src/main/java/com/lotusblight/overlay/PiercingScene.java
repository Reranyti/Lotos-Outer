package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The piercing beat (~2:23): the Glitcher stands facing us; his three reflections - blue, gold, red -
 * rise behind him and their light lances through him, the screen floods red and blood bursts out toward
 * them. Only three: the fourth "architect" is the Glitcher himself. Drawn for a moment in time.
 */
final class PiercingScene {
    private static final int[] COLORS = {0x3E7BFF, 0xFFC24A, 0xFF3030};   // blue, gold, red reflections
    private static final int BLOOD = 60;

    private final BufferedImage front;   // the Glitcher facing us, dark
    private final int figW;
    private final int figH;
    private final double originX;
    private final double originY;
    private final double[] bx = new double[BLOOD];
    private final double[] by = new double[BLOOD];
    private final double[] bvx = new double[BLOOD];
    private final double[] bvy = new double[BLOOD];

    PiercingScene(int[] skin, int height) {
        double scale = height * 0.46 / 32.0;
        double rs = scale / 2;
        SkinModel model = new SkinModel();
        SoftRenderer r = new SoftRenderer((int) (26 * rs), (int) (42 * rs));
        r.clear();
        SoftRenderer.Pose p = new SoftRenderer.Pose();
        p.partPitch[SkinModel.Part.HEAD.ordinal()] = -0.15;      // head tipped back
        p.partRoll[SkinModel.Part.RIGHT_ARM.ordinal()] = -0.25;
        p.partRoll[SkinModel.Part.LEFT_ARM.ordinal()] = 0.25;
        r.draw(model, skin, p, rs, r.width / 2.0, r.height - 2 * rs);
        this.figW = r.width * 2;
        this.figH = r.height * 2;
        this.originX = r.width;
        this.originY = (r.height - 2 * rs) * 2;
        this.front = new BufferedImage(figW, figH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = front.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(r.image, 0, 0, figW, figH, null);
        g.dispose();
        Random rnd = new Random(7);
        for (int i = 0; i < BLOOD; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double sp = 0.4 + rnd.nextDouble();
            bvx[i] = Math.cos(a) * sp;
            bvy[i] = Math.sin(a) * sp - 0.3;
        }
    }

    /**
     * time seconds from the start of the beat. The reflections rise and strike around STRIKE, blood and
     * the red flood follow.
     */
    void render(Graphics2D g, int w, int h, double time) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double strike = 0.9;
        double fx = w * 0.5, fy = h * 0.98;
        double figTopY = fy - figH * 0.55;

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);

        // The screen floods red after the strike.
        double redOverlay = Math.max(0, Math.min(0.8, (time - strike) * 0.9));

        // Three reflections rise behind the figure and fan out.
        double rise = Math.min(1, time / strike);
        double[] angles = {-0.7, 0, 0.7};
        for (int i = 0; i < 3; i++) {
            Color c = new Color(COLORS[i]);
            double ang = angles[i];
            double dist = w * 0.26 * rise;
            double sx = fx + Math.sin(ang) * dist;
            double sy = figTopY - h * 0.16 * rise - Math.cos(ang) * dist * 0.2;
            // The reflection's glow.
            float rad = (float) (h * 0.22);
            g.setPaint(new RadialGradientPaint((float) sx, (float) sy, rad, new float[]{0f, 1f},
                    new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 150), new Color(0, 0, 0, 0)}));
            g.fillOval((int) (sx - rad), (int) (sy - rad), (int) (rad * 2), (int) (rad * 2));
            // The lance of light through the figure once it strikes.
            if (time > strike) {
                float a = (float) Math.max(0, 1 - (time - strike) * 1.2);
                g.setStroke(new BasicStroke((float) (h * 0.012)));
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (a * 220)));
                g.drawLine((int) sx, (int) sy, (int) fx, (int) (figTopY + figH * 0.2));
            }
        }

        // The dark figure over the reflections.
        g.drawImage(front, (int) (fx - originX), (int) (fy - originY), null);

        // Blood bursts out at the strike, toward the reflections.
        if (time > strike) {
            double t = time - strike;
            g.setColor(new Color(0xC0, 0x10, 0x10));
            for (int i = 0; i < BLOOD; i++) {
                double px = fx + bvx[i] * t * w * 0.20;
                double py = figTopY + figH * 0.2 + bvy[i] * t * w * 0.20 + 0.5 * 900 * t * t;
                int sz = (int) Math.max(1, 6 - t * 6);
                g.fillOval((int) px, (int) py, sz, sz);
            }
        }

        if (redOverlay > 0) {
            g.setColor(new Color(0.6f, 0f, 0f, (float) redOverlay));
            g.fillRect(0, 0, w, h);
        }
    }
}

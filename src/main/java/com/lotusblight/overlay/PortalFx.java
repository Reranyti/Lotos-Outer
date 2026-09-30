package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.Random;

/** The portal: a tall ring of turning arcs round a dark hollow, with sparks winding into it. */
final class PortalFx {
    private PortalFx() {}

    /**
     * @param a  how open it is, 0..1 (0 draws nothing)
     * @param rx half the width at full size, ry half the height at full size
     * @param t  seconds, for the turning
     */
    static void draw(Graphics2D g, double px, double py, double rx, double ry, double a, double t, int h) {
        if (a <= 0.01) return;
        rx *= a;
        ry *= a;
        Graphics2D d = (Graphics2D) g.create();
        int gr = (int) (ry * 2.2);
        d.setPaint(new RadialGradientPaint((float) px, (float) py, Math.max(2, gr), new float[]{0f, 1f},
                new Color[]{new Color(150, 110, 255, (int) (110 * a)), new Color(150, 110, 255, 0)}));
        d.fill(new Ellipse2D.Double(px - gr, py - gr, gr * 2, gr * 2));
        d.setColor(new Color(6, 2, 18, (int) (245 * a)));
        d.fill(new Ellipse2D.Double(px - rx, py - ry, rx * 2, ry * 2));
        for (int i = 0; i < 12; i++) {
            double f = 0.35 + 0.65 * (i / 11.0);
            double start = (t * (i % 2 == 0 ? 70 : -95) + i * 41) % 360;
            d.setStroke(new BasicStroke((float) (h * 0.004 * (1 + (i % 3)))));
            d.setColor(i % 2 == 0 ? new Color(170, 120, 255, (int) (220 * a)) : new Color(110, 220, 255, (int) (200 * a)));
            d.draw(new Arc2D.Double(px - rx * f, py - ry * f, rx * 2 * f, ry * 2 * f, start, 70 + 10 * (i % 4), Arc2D.OPEN));
        }
        Random sr = new Random(5);
        for (int i = 0; i < 26; i++) {
            double ang = sr.nextDouble() * Math.PI * 2 + t * (0.8 + sr.nextDouble());
            double f = 1.0 + 0.5 * ((sr.nextDouble() + t * 0.3) % 1.0);
            double sz = h * 0.005 * (1 + sr.nextDouble());
            d.setColor(new Color(220, 200, 255, (int) (200 * a * Math.max(0, 1.5 - f))));
            d.fill(new Ellipse2D.Double(px + Math.cos(ang) * rx * f - sz, py + Math.sin(ang) * ry * f - sz, sz * 2, sz * 2));
        }
        d.dispose();
    }
}

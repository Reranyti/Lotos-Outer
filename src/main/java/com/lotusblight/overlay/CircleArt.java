package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.lotusblight.overlay.PlayfieldFx.Style;

/**
 * The faces a circle can wear: the plain purple one, a soap bubble, an error box whose OK has to be clicked, an eye,
 * a piece of the broken desktop. Each has its approach mark (what shrinks onto it), and what becomes of it when it is
 * hit or missed. All of it is drawn round the point that has to be clicked.
 */
final class CircleArt {
    private CircleArt() {}

    private static BufferedImage bubble, eye;
    private static List<BufferedImage> shards;
    private static double shardR;
    private static BufferedImage shardSource;

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // ------------------------------------------------------------ drawing a live circle

    /**
     * @param appear 0..1 as it comes in; {@code dt} ms to go until it is to be hit (negative once late)
     */
    static void draw(Graphics2D g, Style st, double cx, double cy, double r, double appear, double dt, double approachMs,
                     int idx, double tMs, double glitch, BufferedImage desktop) {
        Random jr = new Random((long) (tMs / 70) * 31 + idx);
        if (glitch > 0.05 && jr.nextDouble() < glitch * 0.35) {          // a torn circle jumps aside for a moment
            cx += (jr.nextDouble() - 0.5) * r * 0.5 * glitch;
            cy += (jr.nextDouble() - 0.5) * r * 0.25 * glitch;
        }
        double alpha = appear * (glitch > 0.3 && jr.nextDouble() < 0.06 * glitch ? 0.35 : 1);
        switch (st) {
            case BUBBLE -> drawBubble(g, cx, cy, r, alpha, dt, approachMs, idx, tMs);
            case ERROR -> drawError(g, cx, cy, r, alpha, dt, approachMs, idx, tMs, false, 0);
            case SHARD -> drawShard(g, cx, cy, r, alpha, dt, approachMs, idx, tMs, desktop);
            case EYE -> drawEye(g, cx, cy, r, alpha, dt, approachMs, idx, tMs, 0, false);
            default -> drawPlain(g, cx, cy, r, alpha, dt, approachMs);
        }
    }

    /** Safe against a bad alpha. */
    private static int a255(double a) {
        return (int) Math.max(0, Math.min(255, 255 * a));
    }

    private static void drawPlain(Graphics2D g, double cx, double cy, double r, double appear, double dt, double approachMs) {
        g.setColor(new Color(0x2A, 0x0B, 0x4A, a255(appear * 220 / 255.0)));
        g.fillOval((int) (cx - r), (int) (cy - r), (int) (r * 2), (int) (r * 2));
        g.setStroke(new BasicStroke((float) (r * 0.18)));
        g.setColor(new Color(0xB0, 0x60, 0xFF, a255(appear)));
        g.drawOval((int) (cx - r), (int) (cy - r), (int) (r * 2), (int) (r * 2));
        if (dt > 0) {
            double ar = r * (1 + 2.6 * dt / approachMs);
            g.setStroke(new BasicStroke((float) (r * 0.12)));
            g.setColor(new Color(0xE0, 0xC0, 0xFF, a255(appear * 200 / 255.0)));
            g.drawOval((int) (cx - ar), (int) (cy - ar), (int) (ar * 2), (int) (ar * 2));
        }
    }

    // ------------------------------------------------------------ bubbles

    private static BufferedImage bubbleSprite() {
        if (bubble != null) return bubble;
        int s = 256;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // The body: clear in the middle, milky towards the edge.
        g.setPaint(new RadialGradientPaint(128f, 128f, 124f, new float[]{0f, 0.7f, 0.9f, 1f},
                new Color[]{new Color(210, 235, 255, 30), new Color(180, 220, 255, 60), new Color(200, 230, 255, 140), new Color(235, 245, 255, 190)}));
        g.fill(new Ellipse2D.Double(4, 4, 248, 248));
        // A dark thin outline so it reads on a pale wallpaper.
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(20, 70, 150, 140));
        g.draw(new Ellipse2D.Double(4, 4, 248, 248));
        // The oily colours on the skin of it.
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 120, 210, 150));
        g.draw(new Arc2D.Double(14, 14, 228, 228, 200, 110, Arc2D.OPEN));
        g.setColor(new Color(120, 255, 235, 150));
        g.draw(new Arc2D.Double(14, 14, 228, 228, 20, 100, Arc2D.OPEN));
        g.setColor(new Color(255, 240, 130, 130));
        g.draw(new Arc2D.Double(20, 20, 216, 216, 320, 50, Arc2D.OPEN));
        // The window of the sky in it, and a spark.
        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 235));
        g.draw(new Arc2D.Double(38, 38, 180, 180, 118, 52, Arc2D.OPEN));
        g.setColor(new Color(255, 255, 255, 240));
        g.fill(new Ellipse2D.Double(66, 62, 22, 14));
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(new Ellipse2D.Double(160, 176, 34, 16));
        g.dispose();
        return bubble = img;
    }

    private static void drawBubble(Graphics2D g, double cx, double cy, double r, double alpha, double dt, double approachMs, int idx, double t) {
        double wob = 1 + 0.035 * Math.sin(t * 0.006 + idx * 1.7);
        double wob2 = 1 + 0.035 * Math.cos(t * 0.0053 + idx);
        double rx = r * 1.05 * wob, ry = r * 1.05 * wob2;
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        d.drawImage(bubbleSprite(), (int) (cx - rx), (int) (cy - ry), (int) (rx * 2), (int) (ry * 2), null);
        if (dt > 0) {
            double ar = r * (1 + 2.6 * dt / approachMs);
            d.setStroke(new BasicStroke((float) (r * 0.06)));
            d.setColor(new Color(255, 255, 255, a255(alpha * 0.85)));
            d.draw(new Ellipse2D.Double(cx - ar, cy - ar, ar * 2, ar * 2));
            d.setStroke(new BasicStroke((float) (r * 0.022)));
            d.setColor(new Color(90, 190, 255, a255(alpha * 0.8)));
            d.draw(new Ellipse2D.Double(cx - ar - r * 0.05, cy - ar - r * 0.05, ar * 2 + r * 0.1, ar * 2 + r * 0.1));
        }
        d.dispose();
    }

    // ------------------------------------------------------------ error boxes

    /**
     * A Windows-style error box with its OK button at (cx, cy) - that is where the click has to land. The ring that
     * shrinks is a frame round the button. {@code pressed} is how far the button is pushed in after a hit.
     */
    private static void drawError(Graphics2D g, double cx, double cy, double r, double alpha, double dt, double approachMs,
                                  int idx, double t, boolean missed, double age) {
        double bw = r * 1.7, bh = r * 0.64;
        double W = r * 5.0, H = r * 3.3;
        double x0 = cx - W / 2, bottom = cy + bh / 2 + r * 0.5, y0 = bottom - H;
        double scale = 0.88 + 0.12 * smooth(alpha * 1.4);
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        d.translate(cx, cy);
        d.scale(scale, scale);
        d.translate(-cx, -cy);
        double a = Math.max(0, Math.min(1, alpha));
        if (missed) {
            double shake = Math.sin(age * 0.06) * r * 0.08 * (1 - age / 420.0);
            d.translate(shake, 0);
            a *= 1 - smooth(age / 420.0);
        }
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
        // shadow, body, border
        d.setColor(new Color(0, 0, 0, 90));
        d.fill(new Rectangle2DD(x0 + r * 0.14, y0 + r * 0.16, W, H));
        d.setColor(new Color(236, 236, 242));
        d.fill(new Rectangle2DD(x0, y0, W, H));
        d.setColor(new Color(24, 26, 60));
        d.setStroke(new BasicStroke((float) Math.max(2, r * 0.04)));
        d.draw(new Rectangle2DD(x0, y0, W, H));
        // title bar
        double tb = r * 0.56;
        Color c1 = missed ? new Color(150, 20, 40) : new Color(18, 54, 150), c2 = missed ? new Color(220, 60, 70) : new Color(70, 130, 230);
        d.setPaint(new GradientPaint((float) x0, (float) y0, c1, (float) (x0 + W), (float) y0, c2));
        d.fill(new Rectangle2DD(x0 + 1, y0 + 1, W - 2, tb));
        d.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (r * 0.34)));
        d.setColor(Color.WHITE);
        d.drawString("Ошибка", (float) (x0 + r * 0.22), (float) (y0 + tb * 0.72));
        double xs = tb * 0.72;
        d.setColor(new Color(214, 50, 50));
        d.fill(new Rectangle2DD(x0 + W - xs - r * 0.12, y0 + (tb - xs) / 2, xs, xs));
        d.setColor(Color.WHITE);
        d.setStroke(new BasicStroke((float) (r * 0.05)));
        double bx = x0 + W - xs - r * 0.12 + xs * 0.25, by = y0 + (tb - xs) / 2 + xs * 0.25;
        d.draw(new Line2D.Double(bx, by, bx + xs * 0.5, by + xs * 0.5));
        d.draw(new Line2D.Double(bx + xs * 0.5, by, bx, by + xs * 0.5));
        // the icon and the words
        double ix = x0 + r * 0.95, iy = y0 + tb + r * 1.0;
        d.setColor(new Color(210, 30, 40));
        d.fill(new Ellipse2D.Double(ix - r * 0.46, iy - r * 0.46, r * 0.92, r * 0.92));
        d.setColor(Color.WHITE);
        d.setStroke(new BasicStroke((float) (r * 0.11), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        d.draw(new Line2D.Double(ix - r * 0.2, iy - r * 0.2, ix + r * 0.2, iy + r * 0.2));
        d.draw(new Line2D.Double(ix + r * 0.2, iy - r * 0.2, ix - r * 0.2, iy + r * 0.2));
        d.setFont(new Font(Font.DIALOG, Font.PLAIN, (int) (r * 0.36)));
        d.setColor(new Color(20, 20, 30));
        d.drawString("Изображение", (float) (x0 + r * 1.75), (float) (iy - r * 0.05));
        d.drawString("не найдено", (float) (x0 + r * 1.75), (float) (iy + r * 0.36));
        // the OK button, pushed in after a hit
        double press = (!missed && age > 0) ? (age < 120 ? 1 : 0) : 0;
        double bxl = cx - bw / 2, byt = cy - bh / 2 + press * r * 0.03;
        d.setColor(new Color(214, 214, 222));
        d.fill(new Rectangle2DD(bxl, byt, bw, bh));
        d.setStroke(new BasicStroke((float) Math.max(2, r * 0.045)));
        d.setColor(press > 0 ? new Color(90, 90, 100) : Color.WHITE);
        d.draw(new Line2D.Double(bxl, byt, bxl + bw, byt));
        d.draw(new Line2D.Double(bxl, byt, bxl, byt + bh));
        d.setColor(press > 0 ? Color.WHITE : new Color(90, 90, 100));
        d.draw(new Line2D.Double(bxl + bw, byt, bxl + bw, byt + bh));
        d.draw(new Line2D.Double(bxl, byt + bh, bxl + bw, byt + bh));
        d.setColor(new Color(20, 20, 40));
        d.setStroke(new BasicStroke(1.4f));
        d.draw(new Rectangle2DD(bxl - 2, byt - 2, bw + 4, bh + 4));
        d.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (r * 0.4)));
        FontMetrics fm = d.getFontMetrics();
        d.drawString("OK", (float) (cx - fm.stringWidth("OK") / 2.0), (float) (byt + bh / 2 + fm.getAscent() * 0.36));
        d.dispose();
        // the frame that shrinks onto the button
        if (dt > 0 && !missed) {
            double k = 1 + 2.6 * dt / approachMs;
            Graphics2D f = (Graphics2D) g.create();
            f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            f.setStroke(new BasicStroke((float) (r * 0.1)));
            f.setColor(new Color(255, 230, 120, a255(alpha * 0.9)));
            double fw = bw * 1.2 * k, fh = bh * 1.5 * k;
            f.draw(new RoundRectangle2D.Double(cx - fw / 2, cy - fh / 2, fw, fh, r * 0.25, r * 0.25));
            f.dispose();
        }
    }

    /** A rectangle taking doubles (java.awt.geom's is Float or Double by name; this keeps the drawing code short). */
    private static final class Rectangle2DD extends java.awt.geom.Rectangle2D.Double {
        Rectangle2DD(double x, double y, double w, double h) {
            super(x, y, w, h);
        }
    }

    // ------------------------------------------------------------ eyes

    private static BufferedImage eyeSprite() {
        if (eye == null) eye = Hazards.makeEyeSprite();
        return eye;
    }

    /** Where the eyes look: the middle of the playfield (set by the game each frame). */
    static double focusX, focusY;

    /**
     * The eye of the first song's field of eyes, as it is there: the doubled outline in pink, olive and white round a
     * dark almond, a white pupil with its olive dot turned towards the middle, the lid coming down now and then.
     * {@code closing} 0..1 is the lid coming down after a hit.
     */
    private static void drawEye(Graphics2D g, double cx, double cy, double r, double alpha, double dt, double approachMs,
                                int idx, double t, double closing, boolean missed) {
        BufferedImage sprite = eyeSprite();
        double sc = r * 2.9 / sprite.getWidth();
        double bl = ((t / 1000.0) * 0.27 + idx * 0.13) % 1.0;
        double lid = bl < 0.05 ? Math.abs(bl / 0.05 - 0.5) * 2 : 1;      // a slow blink, each eye on its own time
        double open = (0.15 + 0.85 * lid) * (1 - closing * 0.92) * smooth(alpha * 1.5 + 0.02);
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double a = Math.max(0, Math.min(1, alpha));
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0.0, Math.min(1.0, a))));
        d.translate(cx, cy);
        d.scale(sc, sc * Math.max(0.08, open));
        d.drawImage(sprite, -sprite.getWidth() / 2, -sprite.getHeight() / 2, null);
        if (lid > 0.5 && closing < 0.6) {
            double dx = focusX - cx, dy = focusY - cy, len = Math.max(1, Math.hypot(dx, dy));
            double px = dx / len * 7, py = dy / len * 4;
            d.setColor(new Color(255, 255, 255, 235));
            d.fillOval((int) (px - 11), (int) (py - 11), 22, 22);
            d.setColor(new Color(0x9A, 0x90, 0x30, 200));
            d.fillOval((int) (px - 13), (int) (py - 5), 10, 10);
        }
        if (missed) {
            d.setColor(new Color(255, 60, 90, 70));
            d.fillOval(-90, -45, 180, 90);
        }
        d.dispose();
        if (dt > 0 && !missed) {
            double ar = r * (1 + 2.6 * dt / approachMs);
            Graphics2D f = (Graphics2D) g.create();
            f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            f.setStroke(new BasicStroke((float) (r * 0.1)));
            f.setColor(new Color(255, 255, 255, a255(alpha * 0.8)));
            f.draw(new Ellipse2D.Double(cx - ar, cy - ar, ar * 2, ar * 2));
            f.dispose();
        }
    }

    // ------------------------------------------------------------ pieces of the desktop

    private static List<BufferedImage> shardSprites(BufferedImage desktop, double r) {
        if (shards != null && shardSource == desktop && Math.abs(shardR - r) < 1) return shards;
        shardSource = desktop;
        shardR = r;
        shards = new ArrayList<>();
        Random rnd = new Random(2024);
        int size = (int) Math.ceil(r * 3.4);
        for (int k = 0; k < 14; k++) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = 4 + rnd.nextInt(3);
            double[] ang = new double[n];
            for (int i = 0; i < n; i++) ang[i] = (i + rnd.nextDouble() * 0.7) * Math.PI * 2 / n;
            Path2D.Double poly = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                double rad = r * (0.95 + 0.45 * rnd.nextDouble());
                double x = size / 2.0 + Math.cos(ang[i]) * rad, y = size / 2.0 + Math.sin(ang[i]) * rad * (0.75 + 0.3 * rnd.nextDouble());
                if (i == 0) poly.moveTo(x, y);
                else poly.lineTo(x, y);
            }
            poly.closePath();
            Graphics2D c = (Graphics2D) g.create();
            c.setClip(poly);
            if (desktop != null) {
                int sx = (int) (rnd.nextDouble() * Math.max(1, desktop.getWidth() - size)), sy = (int) (rnd.nextDouble() * Math.max(1, desktop.getHeight() - size));
                c.drawImage(desktop, 0, 0, size, size, sx, sy, sx + size, sy + size, null);
            } else {
                c.setPaint(new GradientPaint(0, 0, new Color(60, 120, 210), size, size, new Color(150, 200, 250)));
                c.fillRect(0, 0, size, size);
            }
            c.dispose();
            g.setStroke(new BasicStroke((float) Math.max(2, r * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(20, 30, 60, 160));
            g.draw(poly);
            g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.035)));
            g.setColor(new Color(255, 255, 255, 235));
            g.draw(poly);
            g.dispose();
            shards.add(img);
        }
        return shards;
    }

    private static void drawShard(Graphics2D g, double cx, double cy, double r, double alpha, double dt, double approachMs,
                                  int idx, double t, BufferedImage desktop) {
        BufferedImage sp = shardSprites(desktop, r).get(idx % 14);
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double a = Math.max(0, Math.min(1, alpha));
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
        d.translate(cx, cy);
        d.rotate(Math.sin(t * 0.0016 + idx) * 0.12);                      // it turns a little as it hangs
        d.drawImage(sp, -sp.getWidth() / 2, -sp.getHeight() / 2, null);
        d.dispose();
        if (dt > 0) {
            // A ring of cracks drawing in: a jagged circle.
            double ar = r * (1 + 2.6 * dt / approachMs);
            Random jr = new Random(idx * 7L + 3);
            Path2D.Double ring = new Path2D.Double();
            int n = 26;
            for (int i = 0; i <= n; i++) {
                double ang = i * Math.PI * 2 / n;
                double k = 1 + (jr.nextDouble() - 0.5) * 0.14;
                double x = cx + Math.cos(ang) * ar * k, y = cy + Math.sin(ang) * ar * k;
                if (i == 0) ring.moveTo(x, y);
                else ring.lineTo(x, y);
            }
            Graphics2D f = (Graphics2D) g.create();
            f.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            f.setStroke(new BasicStroke((float) (r * 0.13), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            f.setColor(new Color(120, 200, 255, a255(alpha * 0.25)));
            f.draw(ring);
            f.setStroke(new BasicStroke((float) (r * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            f.setColor(new Color(255, 255, 255, a255(alpha * 0.9)));
            f.draw(ring);
            f.dispose();
        }
    }

    // ------------------------------------------------------------ what becomes of it

    /**
     * The end of a circle: {@code age} ms since it was hit (or, if {@code missed}, since its time ran out).
     * Draws nothing once it is over (about 450 ms).
     */
    static void after(Graphics2D g, Style st, double cx, double cy, double r, double age, boolean missed, int idx, double tMs, BufferedImage desktop) {
        if (age < 0 || age > 450) return;
        double u = age / 450.0;
        Graphics2D d = (Graphics2D) g.create();
        d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch (st) {
            case BUBBLE -> {
                if (missed) {                                              // it floats off and fades
                    d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.7 * (1 - u))));
                    d.drawImage(bubbleSprite(), (int) (cx - r), (int) (cy - r - u * r * 2.5), (int) (r * 2), (int) (r * 2), null);
                } else {                                                   // it pops: a ring of spray
                    double rr = r * (1 + 0.9 * u);
                    d.setStroke(new BasicStroke((float) (r * 0.1 * (1 - u) + 1)));
                    d.setColor(new Color(255, 255, 255, a255(1 - u)));
                    d.draw(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
                    Random sr = new Random(idx * 13L);
                    for (int i = 0; i < 10; i++) {
                        double ang = sr.nextDouble() * Math.PI * 2, sp = r * (0.7 + 1.3 * sr.nextDouble());
                        double x = cx + Math.cos(ang) * (r + sp * u), y = cy + Math.sin(ang) * (r + sp * u) + r * 0.6 * u * u;
                        double sz = r * 0.13 * (1 - u) + 1;
                        d.setColor(new Color(200, 235, 255, a255(0.9 * (1 - u))));
                        d.fill(new Ellipse2D.Double(x - sz, y - sz, sz * 2, sz * 2));
                    }
                }
            }
            case ERROR -> {
                if (missed) drawError(d, cx, cy, r, 1, 0, 1, idx, tMs, true, age);
                else {
                    double fade = 1 - smooth(u * 1.5);
                    d.translate(cx, cy);
                    d.scale(1 - 0.08 * u, 1 - 0.08 * u);
                    d.translate(-cx, -cy);
                    drawError(d, cx, cy, r, fade, 0, 1, idx, tMs, false, age);
                }
            }
            case EYE -> {
                double closing = missed ? smooth(u) * 0.7 : smooth(u * 2.5);
                drawEye(d, cx, cy, r, 1 - smooth((u - 0.4) / 0.6), 0, 1, idx, tMs, closing, missed);
                if (!missed && u < 0.25) {
                    d.setColor(new Color(255, 255, 255, a255(0.6 * (1 - u / 0.25))));
                    d.fill(new Ellipse2D.Double(cx - r * 1.3, cy - r * 1.3, r * 2.6, r * 2.6));
                }
            }
            case SHARD -> {
                BufferedImage sp = shardSprites(desktop, r).get(idx % 14);
                if (missed) {                                              // it drops away, turning
                    d.translate(cx, cy + r * 6 * u * u);
                    d.rotate(u * 2.2 * ((idx % 2 == 0) ? 1 : -1));
                    d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (1 - u)));
                    d.drawImage(sp, -sp.getWidth() / 2, -sp.getHeight() / 2, null);
                } else {                                                   // it bursts into splinters
                    Random sr = new Random(idx * 29L);
                    for (int i = 0; i < 9; i++) {
                        double ang = sr.nextDouble() * Math.PI * 2, sp2 = r * (1 + 2.2 * sr.nextDouble());
                        double x = cx + Math.cos(ang) * sp2 * u, y = cy + Math.sin(ang) * sp2 * u + r * 1.4 * u * u;
                        double sz = r * (0.42 - 0.3 * u) * (0.5 + sr.nextDouble());
                        AffineTransform keep = d.getTransform();
                        d.translate(x, y);
                        d.rotate(sr.nextDouble() * 6 + u * 5 * (sr.nextBoolean() ? 1 : -1));
                        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, 1 - u * u)));
                        Path2D.Double tri = new Path2D.Double();
                        tri.moveTo(-sz, sz * 0.6);
                        tri.lineTo(sz, sz * 0.4);
                        tri.lineTo(0, -sz);
                        tri.closePath();
                        java.awt.Shape oc = d.getClip();
                        d.clip(tri);
                        d.drawImage(sp, (int) -sz * 2, (int) -sz * 2, (int) (sz * 4), (int) (sz * 4), null);
                        d.setClip(oc);
                        d.setColor(new Color(255, 255, 255, 220));
                        d.setStroke(new BasicStroke(1.4f));
                        d.draw(tri);
                        d.setTransform(keep);
                    }
                    if (u < 0.3) {
                        d.setComposite(AlphaComposite.SrcOver);
                        d.setColor(new Color(255, 255, 255, a255(0.7 * (1 - u / 0.3))));
                        d.fill(new Ellipse2D.Double(cx - r * 1.1, cy - r * 1.1, r * 2.2, r * 2.2));
                    }
                }
            }
            default -> {
                double rr = r * (1 + (missed ? 0.0 : 0.6) * u);
                d.setStroke(new BasicStroke((float) (r * 0.14 * (1 - u) + 1)));
                d.setColor(missed ? new Color(255, 80, 100, a255(0.7 * (1 - u))) : new Color(0xE0, 0xC0, 0xFF, a255(0.9 * (1 - u))));
                d.draw(new Ellipse2D.Double(cx - rr, cy - rr, rr * 2, rr * 2));
            }
        }
        d.dispose();
    }
}

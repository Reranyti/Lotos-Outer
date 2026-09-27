package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Plays the finale animation as a frame sequence: ffmpeg turns the .mp4 into numbered JPEGs once, and
 * this draws the frame for the current time each paint (reading it from disk only when the index moves
 * on). The mod reads the same frames from the jar instead of a folder.
 */
final class VideoScene {
    private final File dir;
    private final double fps;
    private final int count;
    private int shownIndex = -1;
    private BufferedImage shown;

    VideoScene(File dir, double fps) {
        this.dir = dir;
        this.fps = fps;
        int n = 0;
        while (new File(dir, String.format("f_%04d.jpg", n + 1)).exists()) n++;
        this.count = n;
    }

    double lengthSeconds() { return count / fps; }
    boolean done(double timeSec) { return timeSec >= lengthSeconds(); }

    void render(Graphics2D g, int w, int h, double timeSec) {
        int idx = Math.min(count, (int) Math.floor(timeSec * fps) + 1);
        if (idx != shownIndex && idx >= 1) {
            File f = new File(dir, String.format("f_%04d.jpg", idx));
            if (f.exists()) {
                try { shown = ImageIO.read(f); shownIndex = idx; } catch (Exception ignored) {}
            }
        }
        if (shown != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double s = Math.max((double) w / shown.getWidth(), (double) h / shown.getHeight());
            int dw = (int) (shown.getWidth() * s), dh = (int) (shown.getHeight() * s);
            g.drawImage(shown, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        }
    }
}

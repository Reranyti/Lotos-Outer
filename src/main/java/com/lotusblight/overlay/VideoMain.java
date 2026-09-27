package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Plays the finale animation as a frame sequence with its audio - the way the overlay can show a video
 * without a media library: ffmpeg turns the .mp4 into numbered JPEGs plus a WAV once, and this reads the
 * frame for the current audio position each paint. Full-screen, opaque (it covers the desktop), Esc to
 * quit. A test harness for now; the mod will read the frames from the jar the same way.
 *
 * Usage: java com.lotusblight.overlay.VideoMain --frames DIR --audio anim.wav [--fps 15]
 */
public final class VideoMain {
    private static final int FPS = 60;

    private VideoMain() {}

    public static void main(String[] args) throws Exception {
        String dir = null, audio = null;
        double fps = 15;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--frames")) dir = args[i + 1];
            if (args[i].equals("--audio")) audio = args[i + 1];
            if (args[i].equals("--fps")) fps = Double.parseDouble(args[i + 1]);
        }
        if (GraphicsEnvironment.isHeadless() || dir == null) { System.err.println("need --frames"); System.exit(2); }
        final File frames = new File(dir);
        final double frameRate = fps;

        Clip clip = null;
        final long startNano = System.nanoTime();
        if (audio != null) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(audio))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                clip.start();
            } catch (Exception e) { System.err.println("no audio: " + e); }
        }
        final Clip music = clip;

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        JFrame frame = new JFrame("Lotus Blight");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(Color.BLACK);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0); }
        });

        JComponent canvas = new JComponent() {
            private int shownIndex = -1;
            private BufferedImage shown;

            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                int w = getWidth(), h = getHeight();
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, w, h);
                double t = music != null ? music.getMicrosecondPosition() / 1_000_000.0
                        : (System.nanoTime() - startNano) / 1e9;
                int idx = (int) Math.floor(t * frameRate) + 1;
                if (idx != shownIndex) {
                    File f = new File(frames, String.format("f_%04d.jpg", idx));
                    if (f.exists()) {
                        try { shown = ImageIO.read(f); shownIndex = idx; } catch (Exception ignored) {}
                    }
                }
                if (shown != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    // Fill the screen, keeping the 16:9 aspect.
                    double s = Math.max((double) w / shown.getWidth(), (double) h / shown.getHeight());
                    int dw = (int) (shown.getWidth() * s), dh = (int) (shown.getHeight() * s);
                    g.drawImage(shown, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                }
            }
        };
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.requestFocus();
        new Timer(1000 / FPS, e -> frame.repaint()).start();
    }
}

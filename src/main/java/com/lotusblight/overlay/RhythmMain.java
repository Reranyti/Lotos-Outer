package com.lotusblight.overlay;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Runs a song's rhythm game: circles driven by the music clock, left-click to hit, Esc to quit. By
 * default it plays over the real desktop as a see-through overlay (the desktop stays fully visible, other
 * windows are minimized while it runs); pass --window for a plain black test window instead.
 *
 * Usage: java com.lotusblight.overlay.RhythmMain [--song 1|2] [--audio f.wav] [--at s] [--window]
 */
public final class RhythmMain {
    private static final int FPS = 60;
    // Alpha 1 of 255: invisible, but it makes Windows send the clicks to us, not through to the desktop.
    private static final Color CATCH_INPUT = new Color(0, 0, 0, 1);

    private RhythmMain() {}

    public static void main(String[] args) throws Exception {
        String audio = null;
        double startAt = 0;
        int song = 1;
        boolean desktop = true;
        for (int i = 0; i < args.length; i++) {
            if (i < args.length - 1 && args[i].equals("--audio")) audio = args[i + 1];
            if (i < args.length - 1 && args[i].equals("--at")) startAt = Double.parseDouble(args[i + 1]);
            if (i < args.length - 1 && args[i].equals("--song")) song = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--window")) desktop = false;
        }
        if (GraphicsEnvironment.isHeadless()) { System.err.println("No screen."); System.exit(2); }

        OsuMap map = OsuMap.load(song == 2 ? "/assets/lotusblight/overlay/map_2.osu"
                : "/assets/lotusblight/overlay/map_1.osu");
        RhythmGame game = new RhythmGame(map, song == 2 ? 180 : 48);
        final boolean overDesktop = desktop;
        int[] skin = loadSkin();
        GraphicsConfiguration gc0 = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Hazards hazards = new Hazards(skin, gc0.getBounds().height);

        Clip clip = null;
        final long startNano = System.nanoTime();
        final double base = startAt;
        if (audio != null) {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(audio))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                clip.setMicrosecondPosition((long) (startAt * 1_000_000));
                clip.start();
            } catch (Exception e) { System.err.println("no audio: " + e); clip = null; }
        }
        final Clip music = clip;
        if (startAt > 0) game.skipTo(startAt * 1000);

        // On the desktop overlay, clear other windows out of the way and bring them back on exit.
        if (overDesktop && isWindows()) {
            shell("MinimizeAll()");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shell("UndoMinimizeALL()")));
        }

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        JFrame frame = new JFrame("Lotus Blight");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(overDesktop ? new Color(0, 0, 0, 0) : Color.BLACK);
        frame.setAlwaysOnTop(overDesktop);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0); }
        });

        final boolean[] over = {false};
        JComponent canvas = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                int w = getWidth(), h = getHeight();
                double t = music != null ? music.getMicrosecondPosition() / 1000.0
                        : base * 1000 + (System.nanoTime() - startNano) / 1_000_000.0;
                if (overDesktop) {
                    // Barely-there fill so the desktop shows through but clicks still land on us.
                    g.setComposite(java.awt.AlphaComposite.Src);
                    g.setColor(CATCH_INPUT);
                    g.fillRect(0, 0, w, h);
                    g.setComposite(java.awt.AlphaComposite.SrcOver);
                } else {
                    g.setColor(new Color(0x120820));
                    g.fillRect(0, 0, w, h);
                }

                game.update(t);
                game.render(g, w, h, t);
                hazards.render(g, w, h, t);

                if (!game.alive() || game.finished(t)) {
                    over[0] = true;
                    boolean won = game.alive();
                    g.setColor(new Color(0, 0, 0, 205));
                    g.fillRect(0, 0, w, h);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    double acc = game.total() == 0 ? 0 : game.hits() / (double) game.total();
                    if (won) {
                        String grade = RhythmGame.grade(acc);
                        overLine(g, w, "РАНГ  " + grade, h * 0.22, h * 0.13);
                        overLine(g, w, String.format("Точность  %.1f%%", acc * 100), h * 0.44, h * 0.05);
                        overLine(g, w, "Попаданий  " + game.hits() + " / " + game.total(), h * 0.53, h * 0.05);
                        overLine(g, w, "Промахов  " + game.misses(), h * 0.61, h * 0.05);
                        overLine(g, w, "Макс. комбо  " + game.maxCombo(), h * 0.69, h * 0.05);
                    } else {
                        g.setColor(new Color(0xFF4060));
                        g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.12)));
                        String s = "ПОРАЖЕНИЕ";
                        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, (int) (h * 0.5));
                    }
                    overLine(g, w, "Esc — выход", h * 0.9, h * 0.035);
                }
            }
        };
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (over[0] || e.getButton() != MouseEvent.BUTTON1) return;
                double t = music != null ? music.getMicrosecondPosition() / 1000.0
                        : base * 1000 + (System.nanoTime() - startNano) / 1_000_000.0;
                game.click(e.getX(), e.getY(), t, canvas.getWidth(), canvas.getHeight());
            }
        });
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.requestFocus();
        new Timer(1000 / FPS, e -> frame.repaint()).start();
    }

    private static void overLine(Graphics2D g, int w, String s, double y, double size) {
        g.setColor(new Color(0xE0C0FF));
        g.setFont(g.getFont().deriveFont(Font.BOLD, (float) size));
        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, (int) y);
    }

    private static int[] loadSkin() throws Exception {
        try (java.io.InputStream in = RhythmMain.class.getResourceAsStream(
                "/assets/lotusblight/textures/overlay/glitcher.png")) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            return img.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Calls one method of Explorer's Shell.Application through PowerShell (minimize / restore windows). */
    private static void shell(String call) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "(New-Object -ComObject Shell.Application)." + call)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }
}

package com.lotusblight.overlay;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.InputStream;
import java.awt.image.BufferedImage;

/**
 * The whole boss encounter, end to end: song 1 over the desktop, then the finale animation, then song 2
 * over it, then a results screen ranking how well both songs went. The Glitcher is the boss - hits drain
 * his HP; misses drain the player's. Losing a song ends the run (the real build makes that a crash; here
 * it just shows the defeat screen). A test harness driving the pieces together.
 */
public final class GameMain {
    private static final int FPS = 60;
    private static final Color CATCH_INPUT = new Color(0, 0, 0, 1);

    private enum Phase { SONG1, SONG2, RESULTS, DEFEAT }

    /** Icons taken off the desktop before the fight (by the exit scene), kept covered while it runs. */
    private static volatile DesktopSnapshot hiddenIcons;

    /** The pictures of the desktop's icons (from the screen shot taken before the fight), or none. */
    static java.util.List<java.awt.image.BufferedImage> desktopIcons() {
        DesktopSnapshot d = hiddenIcons;
        java.util.List<java.awt.image.BufferedImage> list = new java.util.ArrayList<>();
        if (d != null) for (DesktopSnapshot.Icon icon : d.icons) list.add(icon.image);
        return list;
    }

    static java.util.List<java.awt.Rectangle> desktopIconSpots() {
        DesktopSnapshot d = hiddenIcons;
        java.util.List<java.awt.Rectangle> list = new java.util.ArrayList<>();
        if (d != null) for (DesktopSnapshot.Icon icon : d.icons) list.add(icon.bounds);
        return list;
    }

    /** He picked this one up: the overlay paints bare wallpaper over it (nothing on the real desktop moves). */
    static void takeDesktopIcon(int index) {
        DesktopSnapshot d = hiddenIcons;
        if (d != null && index >= 0 && index < d.icons.size()) d.icons.get(index).taken = true;
    }

    static void keepIconsHidden(DesktopSnapshot desktop) {
        hiddenIcons = desktop;
    }
    // The finale animation plays as the song-2 backdrop from 2:15 (the video is that 2:15-3:04 window).
    private static final double ANIM_START_MS = 135_000;

    public static void main(String[] args) throws Exception {
        String song1Wav = null, song2Wav = null, framesDir = null, animWav = null, video = null, videoResource = null;
        double animFps = 30, startSong2 = -1;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--song1")) song1Wav = args[i + 1];
            if (args[i].equals("--song2")) song2Wav = args[i + 1];
            if (args[i].equals("--frames")) framesDir = args[i + 1];
            if (args[i].equals("--video")) video = args[i + 1];
            if (args[i].equals("--video-resource")) videoResource = args[i + 1];
            if (args[i].equals("--animAudio")) animWav = args[i + 1];
            if (args[i].equals("--animFps")) animFps = Double.parseDouble(args[i + 1]);
            if (args[i].equals("--start2")) startSong2 = Double.parseDouble(args[i + 1]);
        }
        if (GraphicsEnvironment.isHeadless()) { System.err.println("No screen."); System.exit(2); }
        // Pictures are read straight from memory - no temporary cache files on disk.
        javax.imageio.ImageIO.setUseCache(false);

        int[] skin = loadSkin();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        OsuMap map1 = OsuMap.load("/assets/lotusblight/overlay/map_1.osu");
        OsuMap map2 = OsuMap.load("/assets/lotusblight/overlay/map_2.osu");
        RhythmGame g1 = new RhythmGame(map1, 48);
        RhythmGame g2 = new RhythmGame(map2, 220);
        Hazards hazards = new Hazards(skin, screen.height);
        // The animation: straight from the video (a file, or inside our own jar), else from a folder of frames.
        FinaleVideo anim = video != null || videoResource != null ? openVideo(video, videoResource)
                : framesDir != null ? new VideoScene(new File(framesDir), animFps) : null;
        FakeWindows fakeWindows = new FakeWindows();
        Karaoke karaoke = new Karaoke();

        Engine engine = new Engine(g1, g2, anim, fakeWindows, karaoke, song1Wav, song2Wav, animWav);
        if (startSong2 >= 0) engine.jumpToSong2(startSong2);

        JFrame frame = new JFrame("Lotus Blight");
        frame.setUndecorated(true);
        frame.setBounds(screen);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0); }
        });
        JComponent canvas = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                g.setComposite(AlphaComposite.Src);
                g.setColor(CATCH_INPUT);            // catch clicks; the desktop shows through
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setComposite(AlphaComposite.SrcOver);
                if (hiddenIcons != null) {
                    // Bare wallpaper where icons were knocked off before the fight.
                    for (DesktopSnapshot.Icon icon : hiddenIcons.icons) {
                        if (icon.taken) g.drawImage(icon.cover, icon.bounds.x, icon.bounds.y, null);
                    }
                }
                engine.render(g, getWidth(), getHeight(), hazards);
            }
        };
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) engine.click(e.getX(), e.getY(), canvas.getWidth(), canvas.getHeight());
            }
        });
        frame.setContentPane(canvas);
        frame.setVisible(true);
        frame.requestFocus();
        new Timer(1000 / FPS, e -> frame.repaint()).start();
    }

    /** Drives the phases and owns the music clock for each one. */
    private static final class Engine {
        private final RhythmGame g1, g2;
        private final FinaleVideo anim;
        private final FakeWindows fakeWindows;
        private final Karaoke karaoke;
        private final String song1Wav, song2Wav, animWav;
        private Phase phase = Phase.SONG1;
        private Clip clip;
        private long phaseStartNano = System.nanoTime();

        Engine(RhythmGame g1, RhythmGame g2, FinaleVideo anim, FakeWindows fw, Karaoke k, String s1, String s2, String aw) {
            this.g1 = g1; this.g2 = g2; this.anim = anim; this.fakeWindows = fw; this.karaoke = k;
            this.song1Wav = s1; this.song2Wav = s2; this.animWav = aw;
            play(song1Wav);
        }

        /** Jump straight into song 2 at a given track time (for previewing phase two). */
        void jumpToSong2(double seconds) {
            phase = Phase.SONG2;
            play(song2Wav);
            if (clip != null) clip.setMicrosecondPosition((long) (seconds * 1_000_000));
            g2.skipTo(seconds * 1000);
        }

        private void play(String wav) {
            stop();
            // Without a track the clock runs on its own, so each song still starts from zero.
            phaseStartNano = System.nanoTime();
            if (wav == null) return;
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new File(wav))) {
                clip = AudioSystem.getClip();
                clip.open(in);
                clip.start();
            } catch (Exception e) { clip = null; }
            phaseStartNano = System.nanoTime();
        }

        private void stop() { if (clip != null) { clip.stop(); clip.close(); clip = null; } }

        /**
         * The track has played to its end. A song counts as over then even if its last circle sits too
         * close to the end for the usual half-second after it - the clock follows the track, and stops
         * with it.
         */
        private boolean trackOver() {
            return clip != null && clip.getFramePosition() >= clip.getFrameLength();
        }

        /** Seconds the results or the defeat screen stays up before the fight closes by itself. */
        private static final double END_SCREEN_SECONDS = 10;
        private long endedAt;

        private void end(Phase last) {
            phase = last;
            stop();
            endedAt = System.nanoTime();
        }

        private void closeWhenShown() {
            if ((System.nanoTime() - endedAt) / 1e9 >= END_SCREEN_SECONDS) System.exit(0);
        }

        private double clockMs() {
            return clip != null ? clip.getMicrosecondPosition() / 1000.0
                    : (System.nanoTime() - phaseStartNano) / 1e6;
        }

        void click(int x, int y, int w, int h) {
            double t = clockMs();
            if (phase == Phase.SONG1) g1.click(x, y, t, w, h);
            else if (phase == Phase.SONG2) {
                fakeWindows.close(x, y);        // a click also clears a fake window it lands on
                g2.click(x, y, t, w, h);
            }
        }

        void render(Graphics2D g, int w, int h, Hazards hazards) {
            double t = clockMs();
            switch (phase) {
                case SONG1 -> {
                    g1.update(t);
                    g1.render(g, w, h, t);
                    hazards.render(g, w, h, t);
                    if (!g1.alive()) end(Phase.DEFEAT);
                    else if (g1.finished(t) || trackOver()) { phase = Phase.SONG2; play(song2Wav); }
                }
                case SONG2 -> {
                    g2.update(t);
                    // From 2:15 the finale animation is the backdrop: it goes down first, the chorus
                    // lyrics over it, and the circles with the HP bars on top of everything, so the fight
                    // carries on over the video. Before that the circles play over the desktop, with the
                    hazards.renderBack(g, w, h, t);
                    // Glitcher's fake error windows piling up on top of them.
                    if (anim != null && t >= ANIM_START_MS && anim.ready()) {
                        anim.render(g, w, h, (t - ANIM_START_MS) / 1000.0);
                        karaoke.render(g, w, h, t / 1000.0);
                        g2.render(g, w, h, t);
                    } else {
                        g2.render(g, w, h, t);
                        fakeWindows.render(g, w, h, t);
                    }
                    if (!g2.alive()) end(Phase.DEFEAT);
                    else if (g2.finished(t) || trackOver()) end(Phase.RESULTS);
                }
                case RESULTS -> { results(g, w, h); closeWhenShown(); }
                case DEFEAT -> { defeat(g, w, h); closeWhenShown(); }
            }
        }

        private void results(Graphics2D g, int w, int h) {
            g.setColor(new Color(0, 0, 0, 210));
            g.fillRect(0, 0, w, h);
            int hits = g1.hits() + g2.hits();
            int total = g1.total() + g2.total();
            double acc = total == 0 ? 0 : hits / (double) total;
            String grade = RhythmGame.grade(acc);
            int combo = Math.max(g1.maxCombo(), g2.maxCombo());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            center(g, "РАНГ  " + grade, h * 0.20, h * 0.14, gradeColor(grade), w);
            center(g, String.format("Точность  %.1f%%", acc * 100), h * 0.42, h * 0.05, new Color(0xE0C0FF), w);
            center(g, "Попаданий  " + hits + " / " + total, h * 0.52, h * 0.05, new Color(0xE0C0FF), w);
            center(g, "Макс. комбо  " + combo, h * 0.60, h * 0.05, new Color(0xE0C0FF), w);
            center(g, "Esc — выход", h * 0.85, h * 0.035, new Color(0x9070B0), w);
        }

        private void defeat(Graphics2D g, int w, int h) {
            g.setColor(new Color(0, 0, 0, 220));
            g.fillRect(0, 0, w, h);
            center(g, "ПОРАЖЕНИЕ", h * 0.5, h * 0.12, new Color(0xFF4060), w);
            center(g, "Esc — выход", h * 0.7, h * 0.035, new Color(0x9070B0), w);
        }

        private static Color gradeColor(String grade) {
            return switch (grade) {
                case "S" -> new Color(0xFFD24A);
                case "A" -> new Color(0x60FF80);
                case "B" -> new Color(0x60C0FF);
                case "C" -> new Color(0xC080FF);
                default -> new Color(0xFF6060);
            };
        }

        private static void center(Graphics2D g, String s, double y, double size, Color c, int w) {
            g.setColor(c);
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) size));
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, (w - sw) / 2, (int) y);
        }
    }

    /**
     * The video player, or none if the video can't be opened (or the decoder isn't there). The video is
     * read whole into memory - from a file, or from a resource in our own jar, so nothing is copied out.
     */
    private static FinaleVideo openVideo(String file, String resource) {
        try {
            byte[] data;
            if (file != null) {
                data = java.nio.file.Files.readAllBytes(new File(file).toPath());
            } else {
                try (InputStream in = GameMain.class.getResourceAsStream(resource)) {
                    if (in == null) return null;
                    data = in.readAllBytes();
                }
            }
            return new VideoStream(data);
        } catch (Exception | LinkageError e) {
            System.err.println("Finale video: " + e);
            return null;
        }
    }

    private static int[] loadSkin() throws Exception {
        try (InputStream in = GameMain.class.getResourceAsStream("/assets/lotusblight/textures/overlay/glitcher.png")) {
            BufferedImage img = javax.imageio.ImageIO.read(in);
            return img.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }
}

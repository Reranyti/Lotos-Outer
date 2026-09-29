package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entry point of the desktop overlay. It runs as its own small Java process, started from the mod's
 * own jar with the game's Java, so it only needs the JDK - no Minecraft, no Forge. It draws over the
 * desktop and never changes anything on it; the one thing it does to other programs is minimize all
 * windows on start (like Win+D) and bring them back when it closes.
 *
 * Usage: java -cp lotusblight.jar com.lotusblight.overlay.OverlayMain [--seconds N] [--no-icons] [--no-minimize]
 */
public final class OverlayMain {
    private static final String SKIN = "/assets/lotusblight/textures/overlay/glitcher.png";

    private OverlayMain() {}

    public static void main(String[] args) throws Exception {
        List<String> flags = Arrays.asList(args);
        int seconds = 0;
        int at = flags.indexOf("--seconds");
        if (at >= 0 && at + 1 < flags.size()) seconds = Integer.parseInt(flags.get(at + 1));
        boolean icons = !flags.contains("--no-icons");
        boolean minimize = !flags.contains("--no-minimize") && isWindows();

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No screen to draw on.");
            System.exit(2);
        }
        int[] skin = loadSkin();

        if (minimize) {
            shell("MinimizeAll()");
            // Whatever way the process ends, the windows come back.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shell("UndoMinimizeALL()")));
            // Give the minimize animation time to finish before the desktop is looked at.
            Thread.sleep(900);
        }
        int limit = seconds;
        SwingUtilities.invokeLater(() -> new OverlayWindow(skin, limit, icons, () -> System.exit(0)).show());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Calls one method of Explorer's Shell.Application object through PowerShell and waits for it. */
    private static void shell(String call) {
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "(New-Object -ComObject Shell.Application)." + call)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Not being able to minimize only means the windows stay where they are.
        }
    }

    private static int[] loadSkin() throws IOException {
        try (InputStream in = OverlayMain.class.getResourceAsStream(SKIN)) {
            if (in == null) throw new IOException("missing " + SKIN);
            BufferedImage image = ImageIO.read(in);
            if (image.getWidth() != 64 || image.getHeight() != 64) {
                throw new IOException(SKIN + " must be a 64x64 skin");
            }
            return image.getRGB(0, 0, 64, 64, null, 0, 64);
        }
    }
}

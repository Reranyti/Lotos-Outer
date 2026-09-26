package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Entry point of the desktop overlay. It runs as its own small Java process, started from the mod's
 * own jar with the game's Java, so it only needs the JDK - no Minecraft, no Forge. It draws over the
 * desktop and never touches anything on it.
 *
 * Usage: java -cp lotusblight.jar com.lotusblight.overlay.OverlayMain [--seconds N]
 */
public final class OverlayMain {
    private static final String SKIN = "/assets/lotusblight/textures/overlay/glitcher.png";

    private OverlayMain() {}

    public static void main(String[] args) throws Exception {
        int seconds = 0;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--seconds")) seconds = Integer.parseInt(args[i + 1]);
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No screen to draw on.");
            System.exit(2);
        }
        int[] skin = loadSkin();
        int limit = seconds;
        SwingUtilities.invokeLater(() -> new OverlayWindow(skin, limit, () -> System.exit(0)).show());
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

package com.lotusblight.overlay;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the desktop icons so the overlay can pretend to pick them up. Nothing on the real desktop is
 * moved: a picture of the screen is taken once, kept only in memory, and compared with the wallpaper
 * Windows is showing - wherever they differ on the icon grid, there's an icon. The wallpaper also gives
 * the clean background to paint over the spot an icon was "taken" from. When anything doesn't add up
 * (a window covers the desktop, the wallpaper can't be read, not Windows) the list simply stays empty.
 */
final class DesktopSnapshot {
    /** One icon: where it sits, its picture cut out of the screen, and the wallpaper behind it. */
    static final class Icon {
        final Rectangle bounds;
        final BufferedImage image;
        final BufferedImage cover;
        boolean taken;

        Icon(Rectangle bounds, BufferedImage image, BufferedImage cover) {
            this.bounds = bounds;
            this.image = image;
            this.cover = cover;
        }
    }

    // Per-channel difference at which a pixel counts as "not wallpaper" - above JPEG and scaling noise.
    private static final int DIFF = 48;
    // Share of the screen outside the icons that may disagree with the wallpaper before we give up.
    private static final double MAX_STRAY = 0.12;

    final List<Icon> icons = new ArrayList<>();

    static DesktopSnapshot capture(Rectangle screen, int floorY) {
        DesktopSnapshot snapshot = new DesktopSnapshot();
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return snapshot;
            BufferedImage shot = new Robot().createScreenCapture(screen);
            BufferedImage wallpaper = loadWallpaper(shot);
            if (wallpaper == null) return snapshot;
            snapshot.find(shot, wallpaper, Math.min(shot.getWidth(), wallpaper.getWidth()), Math.min(floorY, shot.getHeight()));
        } catch (Exception | LinkageError e) {
            snapshot.icons.clear();
        }
        return snapshot;
    }

    /** Icons from an already taken picture of the screen - also how the detection is checked offline. */
    static DesktopSnapshot fromImages(BufferedImage shot, int floorY) throws Exception {
        DesktopSnapshot snapshot = new DesktopSnapshot();
        BufferedImage wallpaper = loadWallpaper(shot);
        if (wallpaper != null) snapshot.find(shot, wallpaper, shot.getWidth(), Math.min(floorY, shot.getHeight()));
        return snapshot;
    }

    private void find(BufferedImage shot, BufferedImage wallpaper, int w, int h) {
        boolean[] diff = new boolean[w * h];
        int diffCount = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (differs(shot.getRGB(x, y), wallpaper.getRGB(x, y))) {
                    diff[y * w + x] = true;
                    diffCount++;
                }
            }
        }
        if (diffCount == 0) return;

        int stepX = period(projection(diff, w, h, true), 56, 160, 76);
        int stepY = period(projection(diff, w, h, false), 56, 180, 84);
        int offX = phase(projection(diff, w, h, true), stepX);
        int offY = phase(projection(diff, w, h, false), stepY);

        int covered = 0;
        for (int cy = offY - stepY; cy < h; cy += stepY) {
            for (int cx = offX - stepX; cx < w; cx += stepX) {
                Rectangle cell = new Rectangle(cx, cy, stepX, stepY).intersection(new Rectangle(0, 0, w, h));
                if (cell.isEmpty()) continue;
                int count = 0;
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
                for (int y = cell.y; y < cell.y + cell.height; y++) {
                    for (int x = cell.x; x < cell.x + cell.width; x++) {
                        if (!diff[y * w + x]) continue;
                        count++;
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
                // An icon is a decent blob that leaves the cell's edges clear; a window fills them.
                if (count < 200 || maxY - minY < 20 || !edgesMostlyClear(diff, w, cell)) continue;
                Rectangle box = new Rectangle(minX - 2, minY - 2, maxX - minX + 5, maxY - minY + 5).intersection(cell);
                // Desktop icons carry a white caption; a patch of stray noise doesn't.
                if (!hasCaption(shot, diff, w, box)) continue;
                icons.add(cut(shot, wallpaper, diff, w, box));
                covered += count;
            }
        }
        // Too much of the screen disagrees with the wallpaper - something is in the way. Don't guess.
        if (diffCount - covered > (long) w * h * MAX_STRAY) icons.clear();
    }

    private static Icon cut(BufferedImage shot, BufferedImage wallpaper, boolean[] diff, int w, Rectangle box) {
        BufferedImage image = new BufferedImage(box.width, box.height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage cover = new BufferedImage(box.width, box.height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < box.height; y++) {
            for (int x = 0; x < box.width; x++) {
                int sx = box.x + x, sy = box.y + y;
                cover.setRGB(x, y, wallpaper.getRGB(sx, sy));
                // Keep the icon's pixels plus a one-pixel rim so soft edges and text shadows survive.
                if (near(diff, w, sx, sy)) image.setRGB(x, y, 0xFF000000 | shot.getRGB(sx, sy));
            }
        }
        return new Icon(box, image, cover);
    }

    private static boolean hasCaption(BufferedImage shot, boolean[] diff, int w, Rectangle box) {
        int white = 0;
        for (int y = box.y; y < box.y + box.height; y++) {
            for (int x = box.x; x < box.x + box.width; x++) {
                if (!diff[y * w + x]) continue;
                int c = shot.getRGB(x, y);
                if (((c >> 16) & 0xFF) > 190 && ((c >> 8) & 0xFF) > 190 && (c & 0xFF) > 190) white++;
            }
        }
        return white >= 15;
    }

    private static boolean near(boolean[] diff, int w, int x, int y) {
        int h = diff.length / w;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && diff[ny * w + nx]) return true;
            }
        }
        return false;
    }

    private static boolean edgesMostlyClear(boolean[] diff, int w, Rectangle cell) {
        int ring = 0, hits = 0;
        for (int x = cell.x; x < cell.x + cell.width; x++) {
            for (int y : new int[]{cell.y, cell.y + cell.height - 1}) {
                ring++;
                if (diff[y * w + x]) hits++;
            }
        }
        for (int y = cell.y; y < cell.y + cell.height; y++) {
            for (int x : new int[]{cell.x, cell.x + cell.width - 1}) {
                ring++;
                if (diff[y * w + x]) hits++;
            }
        }
        return hits <= ring * 0.25;
    }

    private static boolean differs(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) > DIFF
                || Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) > DIFF
                || Math.abs((a & 0xFF) - (b & 0xFF)) > DIFF;
    }

    private static double[] projection(boolean[] diff, int w, int h, boolean ontoX) {
        double[] p = new double[ontoX ? w : h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (diff[y * w + x]) p[ontoX ? x : y]++;
            }
        }
        return p;
    }

    /**
     * The icon grid's spacing along one axis: the lag at which the diff profile best matches itself.
     * With a single row or column there's nothing to match, so Windows' usual spacing is used.
     */
    private static int period(double[] p, int min, int max, int fallback) {
        double mean = 0;
        for (double v : p) mean += v;
        mean /= p.length;
        double[] c = new double[p.length];
        for (int i = 0; i < p.length; i++) c[i] = p[i] - mean;
        double zero = 0;
        for (double v : c) zero += v * v;
        if (zero == 0) return fallback;

        int lo = min, hi = Math.min(max, p.length / 2);
        double[] score = new double[hi + 2];
        double top = 0;
        for (int lag = lo; lag <= hi; lag++) {
            double s = 0;
            for (int i = 0; i + lag < c.length; i++) s += c[i] * c[i + lag];
            score[lag] = s / zero;
            top = Math.max(top, score[lag]);
        }
        if (top < 0.2) return fallback;
        // The shortest lag that peaks close to the best one - longer peaks are just its multiples.
        for (int lag = lo + 1; lag < hi; lag++) {
            if (score[lag] >= top * 0.7 && score[lag] >= score[lag - 1] && score[lag] >= score[lag + 1]) return lag;
        }
        return fallback;
    }

    /** Where the grid lines fall: the offset whose lines cross the fewest icon pixels. */
    private static int phase(double[] p, int step) {
        int best = 0;
        double bestSum = Double.MAX_VALUE;
        for (int off = 0; off < step; off++) {
            double sum = 0;
            for (int i = off; i < p.length; i += step) sum += p[i];
            if (sum < bestSum) {
                bestSum = sum;
                best = off;
            }
        }
        return best;
    }

    /**
     * The wallpaper as Windows draws it under this picture of the screen, or null when it can't be
     * reproduced.
     */
    private static BufferedImage loadWallpaper(BufferedImage shot) throws Exception {
        int w = shot.getWidth(), h = shot.getHeight();
        String appData = System.getenv("APPDATA");
        if (appData == null) return null;
        File file = new File(appData, "Microsoft/Windows/Themes/TranscodedWallpaper");
        String style = registry("HKCU\\Control Panel\\Desktop", "WallpaperStyle");
        String tile = registry("HKCU\\Control Panel\\Desktop", "TileWallpaper");
        Color background = parseColor(registry("HKCU\\Control Panel\\Colors", "Background"));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, w, h);
        BufferedImage img = file.isFile() ? ImageIO.read(file) : null;
        if (img != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int iw = img.getWidth(), ih = img.getHeight();
            switch (style == null ? "10" : style) {
                case "2" -> g.drawImage(img, 0, 0, w, h, null);
                case "6" -> {
                    double s = Math.min((double) w / iw, (double) h / ih);
                    int dw = (int) Math.round(iw * s), dh = (int) Math.round(ih * s);
                    g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                }
                case "0" -> {
                    if ("1".equals(tile)) {
                        for (int y = 0; y < h; y += ih) for (int x = 0; x < w; x += iw) g.drawImage(img, x, y, null);
                    } else {
                        g.drawImage(img, (w - iw) / 2, (h - ih) / 2, null);
                    }
                }
                default -> {
                    // 10 (fill), 22 (span) and anything newer: cover the screen and crop the rest. Windows
                    // doesn't always crop exactly in the middle, so the crop is lined up with the screen.
                    double s = Math.max((double) w / iw, (double) h / ih);
                    int dw = (int) Math.round(iw * s), dh = (int) Math.round(ih * s);
                    BufferedImage scaled = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
                    Graphics2D sg = scaled.createGraphics();
                    sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    sg.drawImage(img, 0, 0, dw, dh, null);
                    sg.dispose();
                    int[] off = bestCrop(scaled, shot);
                    g.drawImage(scaled, -off[0], -off[1], null);
                }
            }
        }
        g.dispose();
        return out;
    }

    /**
     * The crop of a wallpaper larger than the screen that matches the screen best, compared on a
     * sparse grid of points so it stays quick. Icons and windows only add the same noise to every
     * candidate, so they don't move the answer.
     */
    private static int[] bestCrop(BufferedImage scaled, BufferedImage shot) {
        int slackX = scaled.getWidth() - shot.getWidth(), slackY = scaled.getHeight() - shot.getHeight();
        int[] best = {slackX / 2, slackY / 2};
        long bestScore = Long.MAX_VALUE;
        for (int oy = 0; oy <= slackY; oy += Math.max(1, slackY / 60)) {
            for (int ox = 0; ox <= slackX; ox += Math.max(1, slackX / 60)) {
                long score = 0;
                for (int y = 4; y < shot.getHeight(); y += 12) {
                    for (int x = 4; x < shot.getWidth(); x += 12) {
                        int a = shot.getRGB(x, y), b = scaled.getRGB(x + ox, y + oy);
                        score += Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                                + Math.abs((a & 0xFF) - (b & 0xFF));
                    }
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{ox, oy};
                }
            }
        }
        return best;
    }

    private static Color parseColor(String rgb) {
        try {
            String[] parts = rgb.trim().split("\\s+");
            return new Color(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    /** One value from the registry through reg.exe - the JDK has no registry API of its own. */
    private static String registry(String key, String value) {
        try {
            Process p = new ProcessBuilder("reg", "query", key, "/v", value).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String t = line.trim();
                    if (t.startsWith(value + " ")) {
                        String[] parts = t.split("\\s{2,}|\\t+", 3);
                        return parts.length == 3 ? parts[2].trim() : null;
                    }
                }
            } finally {
                p.waitFor();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}

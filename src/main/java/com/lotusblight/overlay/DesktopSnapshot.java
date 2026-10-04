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
 * Windows is showing - wherever they differ there is an icon. The wallpaper also gives
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
    /** What the search found or why it gave up - printed by --debug. */
    String report = "not looked";
    /** The picture of the whole screen as it was before the fight - the desktop that later comes down. */
    BufferedImage shot;

    static DesktopSnapshot capture(Rectangle screen, int floorY) {
        DesktopSnapshot snapshot = new DesktopSnapshot();
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
                snapshot.report = "not Windows";
                return snapshot;
            }
            BufferedImage shot = new Robot().createScreenCapture(screen);
            snapshot.shot = shot;
            BufferedImage wallpaper = loadWallpaper(shot);
            if (wallpaper == null) {
                snapshot.report = "wallpaper not readable";
                return snapshot;
            }
            snapshot.find(shot, wallpaper, Math.min(shot.getWidth(), wallpaper.getWidth()), Math.min(floorY, shot.getHeight()));
        } catch (Exception | LinkageError e) {
            snapshot.icons.clear();
            snapshot.report = "failed: " + e;
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
        if (diffCount == 0) {
            report = "screen matches the wallpaper exactly - no icons";
            return;
        }

        // Each icon is one blob: the picture with its caption right under it. Smearing the mask a few
        // pixels up and down joins the two, and a couple of pixels sideways joins the caption's
        // letters and words, while the columns stay apart - Windows keeps captions narrower than
        // their cell.
        int[] label = new int[w * h];
        boolean[] joined = smear(smear(diff, w, h, 2, true), w, h, 7, false);
        int covered = 0;
        int next = 0;
        int[] queue = new int[w * h];
        for (int start = 0; start < w * h; start++) {
            if (!joined[start] || label[start] != 0) continue;
            next++;
            int head = 0, tail = 0;
            queue[tail++] = start;
            label[start] = next;
            int count = 0;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
            while (head < tail) {
                int i = queue[head++];
                int x = i % w, y = i / w;
                if (diff[i]) {
                    count++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
                if (x > 0) tail = visit(joined, label, queue, tail, i - 1, next);
                if (x < w - 1) tail = visit(joined, label, queue, tail, i + 1, next);
                if (y > 0) tail = visit(joined, label, queue, tail, i - w, next);
                if (y < h - 1) tail = visit(joined, label, queue, tail, i + w, next);
            }
            if (count == 0) continue;
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            // Icon-sized, not a sliver of noise and not a whole window.
            if (count < 200 || bh < 20 || bw > 160 || bh > 200) continue;
            Rectangle box = new Rectangle(minX - 2, minY - 2, bw + 4, bh + 4).intersection(new Rectangle(0, 0, w, h));
            // Desktop icons carry a white caption; a patch of stray noise doesn't.
            if (!hasCaption(shot, diff, w, box)) continue;
            icons.add(cut(shot, wallpaper, diff, w, box));
            covered += count;
        }
        // Too much of the screen disagrees with the wallpaper - something is in the way. Don't guess.
        double stray = (diffCount - covered) / (double) ((long) w * h);
        report = String.format("%d icons; %.1f%% of the screen off-wallpaper outside them", icons.size(), stray * 100);
        if (stray > MAX_STRAY) {
            icons.clear();
            report += " - too much, gave up";
        }
    }

    private static int visit(boolean[] joined, int[] label, int[] queue, int tail, int i, int id) {
        if (joined[i] && label[i] == 0) {
            label[i] = id;
            queue[tail++] = i;
        }
        return tail;
    }

    /** Marks every pixel within reach of a marked one, along rows or along columns. */
    private static boolean[] smear(boolean[] mask, int w, int h, int reach, boolean alongRows) {
        boolean[] out = new boolean[mask.length];
        int lines = alongRows ? h : w, len = alongRows ? w : h;
        for (int line = 0; line < lines; line++) {
            int last = -reach - 1;
            for (int k = 0; k < len; k++) {
                int i = alongRows ? line * w + k : k * w + line;
                if (mask[i]) last = k;
                if (k - last <= reach) out[i] = true;
            }
            last = len + reach + 1;
            for (int k = len - 1; k >= 0; k--) {
                int i = alongRows ? line * w + k : k * w + line;
                if (mask[i]) last = k;
                if (last - k <= reach) out[i] = true;
            }
        }
        return out;
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

    private static boolean differs(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) > DIFF
                || Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) > DIFF
                || Math.abs((a & 0xFF) - (b & 0xFF)) > DIFF;
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
            Process p = new ProcessBuilder(WinTools.reg(), "query", key, "/v", value).redirectErrorStream(true).start();
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

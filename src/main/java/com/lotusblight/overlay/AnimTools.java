package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tools for working on {@link AnimClip}s without the game: a contact sheet of frames (front and side, with the
 * skeleton, the IK targets and the paths of the hands, feet and head drawn over), a written report that tracks
 * the joints and checks the motion (floor contact and foot sliding, reach, elbow and knee directions, loop
 * closure, sudden jumps), and JSON export.
 *
 * <pre>
 *   java -cp ... com.lotusblight.overlay.AnimTools list
 *   java -cp ... com.lotusblight.overlay.AnimTools sheet walk [--frames 12] [--rig honcho] [--out sheet.png] [--trails] [--view both|front|side]
 *   java -cp ... com.lotusblight.overlay.AnimTools report walk
 *   java -cp ... com.lotusblight.overlay.AnimTools export walk walk.json
 * </pre>
 * A clip is a name from {@link AnimLibrary} or the path of a JSON file.
 */
final class AnimTools {
    private AnimTools() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("commands: list | sheet <clip> | report <clip> | export <clip> <file>");
            return;
        }
        System.setProperty("java.awt.headless", "true");
        switch (args[0]) {
            case "list" -> {
                for (AnimClip c : AnimLibrary.all().values()) {
                    System.out.printf(Locale.ROOT, "%-8s %.2fs %s, %d tracks%n", c.name, c.length, c.loop ? "loop" : "once", c.tracks.size());
                }
            }
            case "sheet" -> sheet(load(args[1]), args);
            case "report" -> System.out.println(report(load(args[1]), rigOf(args)));
            case "export" -> {
                Files.writeString(new File(args[2]).toPath(), load(args[1]).toJson(), StandardCharsets.UTF_8);
                System.out.println("written " + args[2]);
            }
            default -> System.out.println("unknown command " + args[0]);
        }
    }

    static AnimClip load(String nameOrFile) throws Exception {
        AnimClip c = AnimLibrary.all().get(nameOrFile);
        if (c != null) return c;
        return AnimClip.fromJson(Files.readString(new File(nameOrFile).toPath(), StandardCharsets.UTF_8));
    }

    private static String opt(String[] args, String name, String def) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(name)) return args[i + 1];
        return def;
    }

    private static boolean flag(String[] args, String name) {
        for (String a : args) if (a.equals(name)) return true;
        return false;
    }

    private static Rig15 rigOf(String[] args) {
        return "honcho".equals(opt(args, "--rig", "steve")) ? Rig15.honcho() : Rig15.standard();
    }

    static int[] loadSkin(String resource) {
        try (InputStream in = AnimTools.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            return javax.imageio.ImageIO.read(in).getRGB(0, 0, 64, 64, null, 0, 64);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------ tracking

    /** The points that are followed through a clip. */
    private static final String[] TRACKED = {"root", "head", "r_hand", "l_hand", "r_foot", "l_foot", "r_elbow", "l_elbow", "r_knee", "l_knee"};

    private static double[] point(Rig15 rig, Rig15.Skel sk, String name) {
        return switch (name) {
            case "root" -> sk.pivotPos(Rig15.Joint.LOWER_TORSO);
            case "head" -> sk.headTop();
            case "r_hand" -> sk.endPoint(Rig15.Limb.R_ARM);
            case "l_hand" -> sk.endPoint(Rig15.Limb.L_ARM);
            case "r_foot" -> sk.pivotPos(Rig15.Joint.R_FOOT);
            case "l_foot" -> sk.pivotPos(Rig15.Joint.L_FOOT);
            case "r_elbow" -> sk.pivotPos(Rig15.Joint.R_LOWER_ARM);
            case "l_elbow" -> sk.pivotPos(Rig15.Joint.L_LOWER_ARM);
            case "r_knee" -> sk.pivotPos(Rig15.Joint.R_LOWER_LEG);
            default -> sk.pivotPos(Rig15.Joint.L_LOWER_LEG);
        };
    }

    /** Signed bend of an elbow or knee about its hinge: negative folds an arm forward, positive folds a leg back (degrees). */
    static double flexion(Rig15.Skel sk, Rig15.Limb l) {
        double[] s = sk.pivotPos(l.upper), e = sk.pivotPos(l.lower);
        double[] w = sk.pivotPos(l.end);
        double[] u = Quat.unit(Quat.sub(e, s)), lo = Quat.unit(Quat.sub(w, e));
        double[] hinge = sk.worldRot[l.upper.ordinal()].rotate(new double[]{1, 0, 0});
        return Math.toDegrees(Math.atan2(Quat.dot(Quat.cross(u, lo), hinge), Quat.dot(u, lo)));
    }

    static String report(AnimClip clip, Rig15 rig) {
        StringBuilder out = new StringBuilder();
        double fps = 60, dt = 1 / fps;
        int n = (int) Math.round(clip.length * fps);
        double[][][] pts = new double[n + 1][TRACKED.length][];
        double[][] flex = new double[n + 1][4];
        double[][] stretch = new double[n + 1][4];
        for (int f = 0; f <= n; f++) {
            Pose15 pose = clip.sample(f * dt);
            Rig15.Skel sk = rig.solve(pose);
            for (int i = 0; i < TRACKED.length; i++) pts[f][i] = point(rig, sk, TRACKED[i]);
            for (Rig15.Limb l : Rig15.Limb.values()) {
                flex[f][l.ordinal()] = flexion(sk, l);
                stretch[f][l.ordinal()] = sk.solved[l.ordinal()] ? sk.stretch[l.ordinal()] : 0;
            }
        }
        out.append(String.format(Locale.ROOT, "%s: %.2f s, %s, %d samples at %.0f fps%n", clip.name, clip.length, clip.loop ? "loop" : "once", n + 1, fps));
        out.append(String.format(Locale.ROOT, "%-9s %8s %8s %8s %8s%n", "point", "speed", "peak", "accel", "range y"));
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < TRACKED.length; i++) {
            double peak = 0, sum = 0, accel = 0, minY = 1e9, maxY = -1e9;
            double[] prevV = null;
            for (int f = 1; f <= n; f++) {
                double[] v = Quat.scale(Quat.sub(pts[f][i], pts[f - 1][i]), fps);
                double sp = Quat.length(v);
                sum += sp;
                peak = Math.max(peak, sp);
                if (prevV != null) accel = Math.max(accel, Quat.length(Quat.sub(v, prevV)) * fps);
                prevV = v;
                minY = Math.min(minY, pts[f][i][1]);
                maxY = Math.max(maxY, pts[f][i][1]);
            }
            out.append(String.format(Locale.ROOT, "%-9s %8.1f %8.1f %8.0f %4.1f..%.1f%n", TRACKED[i], sum / Math.max(1, n), peak, accel, minY, maxY));
        }
        // Floor: how low the soles go and how far a planted foot slides.
        for (Rig15.Limb l : new Rig15.Limb[]{Rig15.Limb.R_LEG, Rig15.Limb.L_LEG}) {
            int idx = l == Rig15.Limb.R_LEG ? 4 : 5;
            double minSole = 1e9, maxSlide = 0, run = 0;
            boolean inContact = false;
            double[] last = null;
            for (int f = 0; f <= n; f++) {
                Rig15.Skel sk = rig.solve(clip.sample(f * dt));
                double[] sole = sk.endPoint(l);
                minSole = Math.min(minSole, sole[1]);
                boolean contact = sole[1] < 0.35;
                if (contact && inContact && last != null) run += Math.hypot(sole[0] - last[0], sole[2] - last[2]);
                else if (!contact) run = 0;
                if (contact) maxSlide = Math.max(maxSlide, run);
                inContact = contact;
                last = sole;
            }
            out.append(String.format(Locale.ROOT, "%s sole: lowest %.2f, travels %.2f px while planted (a treadmill walk does this on purpose)%n", l.key(), minSole, maxSlide));
            if (minSole < -0.15) warnings.add(l.key() + " goes through the floor (" + String.format(Locale.ROOT, "%.2f", minSole) + ")");
        }
        // Reach and hinge direction.
        for (Rig15.Limb l : Rig15.Limb.values()) {
            double maxStretch = 0, minFlex = 1e9, maxFlex = -1e9;
            double tOver = -1;
            for (int f = 0; f <= n; f++) {
                maxStretch = Math.max(maxStretch, stretch[f][l.ordinal()]);
                if (stretch[f][l.ordinal()] > 1.0 && tOver < 0) tOver = f * dt;
                minFlex = Math.min(minFlex, flex[f][l.ordinal()]);
                maxFlex = Math.max(maxFlex, flex[f][l.ordinal()]);
            }
            out.append(String.format(Locale.ROOT, "%-6s flex %.0f..%.0f deg, reach up to %.0f%% of full stretch%n", l.key(), minFlex, maxFlex, maxStretch * 100));
            if (maxStretch > 1.0) warnings.add(l.key() + " target out of reach from " + String.format(Locale.ROOT, "%.2f", tOver) + " s (" + String.format(Locale.ROOT, "%.0f", maxStretch * 100) + "%)");
            if (l.arm && maxFlex > 2.5) warnings.add(l.key() + " elbow bends backwards (" + String.format(Locale.ROOT, "%.0f", maxFlex) + " deg)");
            if (!l.arm && minFlex < -2.5) warnings.add(l.key() + " knee bends backwards (" + String.format(Locale.ROOT, "%.0f", minFlex) + " deg)");
        }
        // A loop has to meet itself.
        if (clip.loop) {
            Rig15.Skel a = rig.solve(clip.sample(0)), b = rig.solve(clip.sample(clip.length - 1e-6));
            double worst = 0;
            for (Rig15.Joint j : Rig15.Joint.values()) worst = Math.max(worst, Quat.length(Quat.sub(a.pivotPos(j), b.pivotPos(j))));
            out.append(String.format(Locale.ROOT, "loop closure: worst joint differs by %.2f px between the ends%n", worst));
            if (worst > 0.5) warnings.add("the loop does not close (" + String.format(Locale.ROOT, "%.2f", worst) + " px)");
        }
        // Sudden jumps: a frame-to-frame change in velocity far above its surroundings.
        for (int i = 0; i < TRACKED.length; i++) {
            double[] acc = new double[n + 1];
            for (int f = 2; f <= n; f++) {
                double[] v1 = Quat.sub(pts[f][i], pts[f - 1][i]), v0 = Quat.sub(pts[f - 1][i], pts[f - 2][i]);
                acc[f] = Quat.length(Quat.sub(v1, v0)) * fps * fps;
            }
            for (int f = 4; f <= n - 3; f++) {
                double around = 0;
                for (int k = -3; k <= 3; k++) if (k != 0) around += acc[f + k] / 6;
                if (acc[f] > 3000 && acc[f] > around * 6) {
                    warnings.add(String.format(Locale.ROOT, "%s jumps at %.2f s (accel %.0f px/s2)", TRACKED[i], f * dt, acc[f]));
                    break;
                }
            }
        }
        out.append(warnings.isEmpty() ? "no problems found\n" : "problems:\n");
        for (String w : warnings) out.append("  - ").append(w).append('\n');
        return out.toString();
    }

    // ------------------------------------------------------------ contact sheet

    static void sheet(AnimClip clip, String[] args) throws Exception {
        Rig15 rig = rigOf(args);
        int frames = Integer.parseInt(opt(args, "--frames", "12"));
        double scale = Double.parseDouble(opt(args, "--scale", "5"));
        String view = opt(args, "--view", "both");
        boolean trails = flag(args, "--trails");
        String skinRes = opt(args, "--skin", rig.honcho ? "/assets/lotusblight/textures/entity/honcho.png" : "/assets/lotusblight/textures/overlay/glitcher.png");
        int[] skin = skinRes.equals("none") ? null : loadSkin(skinRes);
        if (skin == null) {                                   // a plain checker, so the shapes read without a skin
            skin = new int[64 * 64];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    boolean dark = ((x / 2) + (y / 2)) % 2 == 0;
                    skin[y * 64 + x] = dark ? 0xFF9CA8C8 : 0xFFB8C2DC;
                }
            }
        }
        boolean both = view.equals("both");
        int views = both ? 2 : 1;
        int cw = (int) (40 * scale), ch = (int) (44 * scale);
        int cols = Math.min(frames, Math.max(2, (int) Math.ceil(Math.sqrt(frames * 1.3))));
        int rows = (int) Math.ceil(frames / (double) cols);
        int cellW = cw * views;
        BufferedImage img = new BufferedImage(cols * cellW, rows * ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 22, 34));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Paths over the whole clip, for the trails.
        double[][][] trail = null;
        String[] trailNames = {"r_hand", "l_hand", "r_foot", "l_foot", "head"};
        if (trails) {
            int steps = 90;
            trail = new double[trailNames.length][steps + 1][];
            for (int s = 0; s <= steps; s++) {
                Rig15.Skel sk = rig.solve(clip.sample(clip.length * s / steps - (clip.loop ? 0 : 0)));
                for (int i = 0; i < trailNames.length; i++) trail[i][s] = point(rig, sk, trailNames[i]);
            }
        }
        SoftRenderer r = new SoftRenderer(cw, ch);
        for (int f = 0; f < frames; f++) {
            double t = clip.loop ? clip.length * f / frames : clip.length * f / Math.max(1, frames - 1);
            Pose15 pose = clip.sample(t);
            Rig15.Skel sk = rig.solve(pose);
            int cx = (f % cols) * cellW, cy = (f / cols) * ch;
            for (int v = 0; v < views; v++) {
                double yaw = (both && v == 1) || view.equals("side") ? Math.PI / 2 : 0;
                double ox = cw / 2.0, oy = ch - 4 * scale;
                r.clear();
                r.drawRig(rig, sk, skin, yaw, 0, scale, ox, oy);
                Graphics2D cg = (Graphics2D) g.create(cx + v * cw, cy, cw, ch);
                cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                cg.setColor(new Color(60, 56, 84));
                cg.drawLine(0, (int) oy, cw, (int) oy);                          // the floor
                cg.setColor(new Color(255, 255, 255, 20));
                cg.drawRect(0, 0, cw - 1, ch - 1);
                if (trails) drawTrails(cg, trail, yaw, scale, ox, oy);
                cg.drawImage(r.image, 0, 0, null);
                drawSkeleton(cg, rig, sk, pose, yaw, scale, ox, oy);
                cg.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                cg.setColor(new Color(220, 220, 240));
                if (v == 0) cg.drawString(String.format(Locale.ROOT, "%.2fs", t), 4, 13);
                cg.dispose();
            }
        }
        g.dispose();
        File out = new File(opt(args, "--out", "anim_" + clip.name + ".png"));
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("written " + out.getAbsolutePath() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }

    private static void drawTrails(Graphics2D g, double[][][] trail, double yaw, double scale, double ox, double oy) {
        Color[] colors = {new Color(255, 120, 120, 150), new Color(255, 190, 120, 150), new Color(120, 255, 150, 150), new Color(120, 220, 255, 150), new Color(230, 140, 255, 150)};
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < trail.length; i++) {
            g.setColor(colors[i]);
            double[] prev = null;
            for (double[] p : trail[i]) {
                double[] s = SoftRenderer.project(p, yaw, 0, scale, ox, oy);
                if (prev != null) g.drawLine((int) prev[0], (int) prev[1], (int) s[0], (int) s[1]);
                prev = s;
            }
        }
    }

    private static void drawSkeleton(Graphics2D g, Rig15 rig, Rig15.Skel sk, Pose15 pose, double yaw, double scale, double ox, double oy) {
        g.setStroke(new BasicStroke(1.4f));
        for (Rig15.Joint j : Rig15.Joint.values()) {
            Rig15.Joint p = Rig15.parentOf(j);
            if (p == null) continue;
            double[] a = SoftRenderer.project(sk.pivotPos(p), yaw, 0, scale, ox, oy), b = SoftRenderer.project(sk.pivotPos(j), yaw, 0, scale, ox, oy);
            g.setColor(new Color(255, 255, 255, 140));
            g.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
        }
        for (Rig15.Joint j : Rig15.Joint.values()) {
            double[] b = SoftRenderer.project(sk.pivotPos(j), yaw, 0, scale, ox, oy);
            g.setColor(new Color(255, 230, 90));
            g.fillOval((int) b[0] - 2, (int) b[1] - 2, 5, 5);
        }
        for (Rig15.Limb l : Rig15.Limb.values()) {
            Pose15.Ik k = pose.ik[l.ordinal()];
            if (k.w <= 0.01) continue;
            double[] t = SoftRenderer.project(k.pos, yaw, 0, scale, ox, oy);
            g.setColor(l.arm ? new Color(255, 80, 80) : new Color(90, 255, 120));
            g.drawLine((int) t[0] - 4, (int) t[1], (int) t[0] + 4, (int) t[1]);
            g.drawLine((int) t[0], (int) t[1] - 4, (int) t[0], (int) t[1] + 4);
        }
        if (pose.lookW > 0.01) {
            double[] t = SoftRenderer.project(pose.lookPos, yaw, 0, scale, ox, oy);
            g.setColor(new Color(230, 140, 255));
            g.drawOval((int) t[0] - 3, (int) t[1] - 3, 6, 6);
        }
    }
}

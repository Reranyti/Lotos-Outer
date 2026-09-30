package com.lotusblight.overlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A keyframed animation for the {@link Rig15}. Every channel is a track of keys; between two keys the value
 * follows the easing of the first one. Channels:
 * <pre>
 *   root.pos              hips offset x, y, z
 *   root.rot              hips turn, Euler degrees (x pitch, y turn, z roll)
 *   &lt;joint&gt;.rot           a joint's local turn, Euler degrees, e.g. head.rot, r_lower_arm.rot
 *   ik.&lt;limb&gt;.pos        where the limb's end is (r_arm, l_arm: fist tip; r_leg, l_leg: ankle), stage space
 *   ik.&lt;limb&gt;.w          how much the solve counts (0..1)
 *   ik.&lt;limb&gt;.pole       the way the elbow / knee points
 *   ik.&lt;limb&gt;.roll       a foot's sole pitch, degrees (toes down positive)
 *   ik.&lt;limb&gt;.yaw        a foot's own turn, degrees
 *   look.pos, look.w      a point for the head to look at, and how much
 * </pre>
 * Rotation tracks are interpolated as quaternions, so a joint never gimbal-flips between its keys.
 */
final class AnimClip {
    enum Ease {
        LINEAR, STEP, SMOOTH,
        IN_QUAD, OUT_QUAD, IN_OUT_QUAD,
        IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
        IN_BACK, OUT_BACK, OUT_ELASTIC, OUT_BOUNCE,
        /** A Catmull-Rom spline through the neighbouring keys (the easing is then the spline's own). */
        SPLINE,
        /** The cubic Bezier given in the key's {@code bez}: (x1, y1, x2, y2) as in CSS. */
        BEZIER;

        static Ease byName(String s) {
            for (Ease e : values()) if (e.name().equalsIgnoreCase(s)) return e;
            return LINEAR;
        }
    }

    record Key(double t, double[] v, Ease ease, double[] bez) {}

    record Event(double t, String name) {}

    static final class Track {
        final int dim;
        final List<Key> keys = new ArrayList<>();

        Track(int dim) {
            this.dim = dim;
        }
    }

    final String name;
    final double length;
    final boolean loop;
    final Map<String, Track> tracks = new LinkedHashMap<>();
    final List<Event> events = new ArrayList<>();

    AnimClip(String name, double length, boolean loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    // ------------------------------------------------------------ building

    /** Adds a key to a channel ({@code v}: one number per component). */
    AnimClip key(String channel, double t, Ease ease, double... v) {
        return key(channel, t, ease, null, v);
    }

    AnimClip key(String channel, double t, Ease ease, double[] bez, double... v) {
        Track tr = tracks.computeIfAbsent(channel, c -> new Track(v.length));
        if (tr.dim != v.length) throw new IllegalArgumentException(channel + " takes " + tr.dim + " numbers");
        tr.keys.add(new Key(t, v.clone(), ease, bez));
        tr.keys.sort((a, b) -> Double.compare(a.t(), b.t()));
        return this;
    }

    AnimClip event(double t, String name) {
        events.add(new Event(t, name));
        return this;
    }

    static int dimOf(String channel) {
        if (channel.endsWith(".w") || channel.endsWith(".roll") || channel.endsWith(".yaw")) return 1;
        return 3;
    }

    // ------------------------------------------------------------ sampling

    /** The pose at time {@code t} (seconds). Channels without keys stay at their rest value. */
    Pose15 sample(double t) {
        Pose15 p = new Pose15();
        sample(t, p);
        return p;
    }

    void sample(double t, Pose15 p) {
        p.reset();
        double tt = loop && length > 0 ? ((t % length) + length) % length : Math.max(0, Math.min(length, t));
        for (Map.Entry<String, Track> e : tracks.entrySet()) {
            String ch = e.getKey();
            Track tr = e.getValue();
            if (tr.keys.isEmpty()) continue;
            boolean rotation = ch.endsWith(".rot");
            if (rotation) {
                Quat q = sampleRot(tr, tt);
                if (ch.equals("root.rot")) p.rootRot = q;
                else {
                    Rig15.Joint j = Rig15.Joint.byKey(ch.substring(0, ch.length() - 4));
                    if (j != null && j != Rig15.Joint.LOWER_TORSO) p.rot[j.ordinal()] = q;
                    else if (j == Rig15.Joint.LOWER_TORSO) p.rootRot = q;
                }
                continue;
            }
            double[] v = sampleVec(tr, tt);
            if (ch.equals("root.pos")) System.arraycopy(v, 0, p.rootPos, 0, 3);
            else if (ch.equals("look.pos")) System.arraycopy(v, 0, p.lookPos, 0, 3);
            else if (ch.equals("look.w")) p.lookW = v[0];
            else if (ch.startsWith("ik.")) {
                String[] parts = ch.split("\\.");
                Rig15.Limb limb = parts.length == 3 ? Rig15.Limb.byKey(parts[1]) : null;
                if (limb == null) continue;
                Pose15.Ik k = p.ik[limb.ordinal()];
                switch (parts[2]) {
                    case "pos" -> System.arraycopy(v, 0, k.pos, 0, 3);
                    case "pole" -> System.arraycopy(v, 0, k.pole, 0, 3);
                    case "w" -> k.w = v[0];
                    case "roll" -> k.roll = v[0];
                    case "yaw" -> k.yaw = v[0];
                    default -> { }
                }
            }
        }
    }

    /** Which two keys {@code t} lies between and how far along (0..1, before easing). */
    private int segment(Track tr, double t) {
        List<Key> ks = tr.keys;
        if (t <= ks.get(0).t()) return -1;
        for (int i = 0; i < ks.size() - 1; i++) {
            if (t < ks.get(i + 1).t()) return i;
        }
        return ks.size() - 1;
    }

    private double[] sampleVec(Track tr, double t) {
        List<Key> ks = tr.keys;
        int i = segment(tr, t);
        if (i < 0) return ks.get(0).v().clone();
        if (i >= ks.size() - 1) return ks.get(ks.size() - 1).v().clone();
        Key a = ks.get(i), b = ks.get(i + 1);
        double u = (t - a.t()) / Math.max(1e-9, b.t() - a.t());
        double[] out = new double[tr.dim];
        if (a.ease() == Ease.SPLINE) {
            double[] p0 = neighbour(tr, i - 1, true), p3 = neighbour(tr, i + 2, false);
            for (int c = 0; c < out.length; c++) out[c] = catmull(p0[c], a.v()[c], b.v()[c], p3[c], u);
            return out;
        }
        double s = ease(a.ease(), a.bez(), u);
        for (int c = 0; c < out.length; c++) out[c] = a.v()[c] + (b.v()[c] - a.v()[c]) * s;
        return out;
    }

    /** The key value beyond the ends: wrapped round for a loop (one period away), held otherwise. */
    private double[] neighbour(Track tr, int i, boolean before) {
        List<Key> ks = tr.keys;
        if (i >= 0 && i < ks.size()) return ks.get(i).v();
        if (loop && ks.size() > 2) {
            int n = ks.size() - 1;                         // the last key repeats the first
            return ks.get(((i % n) + n) % n).v();
        }
        return before ? ks.get(0).v() : ks.get(ks.size() - 1).v();
    }

    private Quat sampleRot(Track tr, double t) {
        List<Key> ks = tr.keys;
        int i = segment(tr, t);
        if (i < 0) return eulerOf(ks.get(0));
        if (i >= ks.size() - 1) return eulerOf(ks.get(ks.size() - 1));
        Key a = ks.get(i), b = ks.get(i + 1);
        double u = (t - a.t()) / Math.max(1e-9, b.t() - a.t());
        if (a.ease() == Ease.SPLINE) {
            // Through the neighbours, component-wise on the quaternions with matching signs, then normalised.
            Quat q0 = eulerOf(neighbourKey(tr, i - 1, true)), q1 = eulerOf(a), q2 = eulerOf(b), q3 = eulerOf(neighbourKey(tr, i + 2, false));
            if (q1.dot(q0) < 0) q0 = new Quat(-q0.w, -q0.x, -q0.y, -q0.z);
            if (q1.dot(q2) < 0) q2 = new Quat(-q2.w, -q2.x, -q2.y, -q2.z);
            if (q2.dot(q3) < 0) q3 = new Quat(-q3.w, -q3.x, -q3.y, -q3.z);
            return new Quat(catmull(q0.w, q1.w, q2.w, q3.w, u), catmull(q0.x, q1.x, q2.x, q3.x, u),
                    catmull(q0.y, q1.y, q2.y, q3.y, u), catmull(q0.z, q1.z, q2.z, q3.z, u)).normalized();
        }
        return Quat.slerp(eulerOf(a), eulerOf(b), ease(a.ease(), a.bez(), u));
    }

    private Key neighbourKey(Track tr, int i, boolean before) {
        List<Key> ks = tr.keys;
        if (i >= 0 && i < ks.size()) return ks.get(i);
        if (loop && ks.size() > 2) {
            int n = ks.size() - 1;
            return ks.get(((i % n) + n) % n);
        }
        return before ? ks.get(0) : ks.get(ks.size() - 1);
    }

    private static Quat eulerOf(Key k) {
        return Quat.euler(k.v()[0], k.v()[1], k.v()[2]);
    }

    private static double catmull(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    // ------------------------------------------------------------ easing

    static double ease(Ease e, double[] bez, double u) {
        u = Math.max(0, Math.min(1, u));
        switch (e) {
            case STEP: return u < 1 ? 0 : 1;
            case SMOOTH: return u * u * (3 - 2 * u);
            case IN_QUAD: return u * u;
            case OUT_QUAD: return 1 - (1 - u) * (1 - u);
            case IN_OUT_QUAD: return u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
            case IN_CUBIC: return u * u * u;
            case OUT_CUBIC: return 1 - Math.pow(1 - u, 3);
            case IN_OUT_CUBIC: return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
            case IN_BACK: {
                double c1 = 1.70158, c3 = c1 + 1;
                return c3 * u * u * u - c1 * u * u;
            }
            case OUT_BACK: {
                double c1 = 1.70158, c3 = c1 + 1;
                return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
            }
            case OUT_ELASTIC: {
                if (u == 0 || u == 1) return u;
                return Math.pow(2, -10 * u) * Math.sin((u * 10 - 0.75) * (2 * Math.PI) / 3) + 1;
            }
            case OUT_BOUNCE: {
                double n1 = 7.5625, d1 = 2.75;
                if (u < 1 / d1) return n1 * u * u;
                if (u < 2 / d1) { u -= 1.5 / d1; return n1 * u * u + 0.75; }
                if (u < 2.5 / d1) { u -= 2.25 / d1; return n1 * u * u + 0.9375; }
                u -= 2.625 / d1;
                return n1 * u * u + 0.984375;
            }
            case BEZIER: return bez == null || bez.length < 4 ? u : bezier(bez[0], bez[1], bez[2], bez[3], u);
            case SPLINE:
            case LINEAR:
            default: return u;
        }
    }

    /** y at x = {@code u} on the cubic Bezier from (0,0) to (1,1) with handles (x1,y1), (x2,y2). */
    private static double bezier(double x1, double y1, double x2, double y2, double x) {
        double lo = 0, hi = 1, t = x;
        for (int i = 0; i < 28; i++) {
            double cx = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t;
            if (cx < x) lo = t; else hi = t;
            t = (lo + hi) / 2;
        }
        return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t;
    }

    // ------------------------------------------------------------ JSON

    String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"name\": ").append(quote(name)).append(",\n  \"length\": ").append(num(length))
                .append(",\n  \"loop\": ").append(loop).append(",\n  \"tracks\": {\n");
        int n = 0;
        for (Map.Entry<String, Track> e : tracks.entrySet()) {
            if (n++ > 0) sb.append(",\n");
            sb.append("    ").append(quote(e.getKey())).append(": [");
            int m = 0;
            for (Key k : e.getValue().keys) {
                if (m++ > 0) sb.append(", ");
                sb.append("{\"t\": ").append(num(k.t())).append(", \"v\": [");
                for (int i = 0; i < k.v().length; i++) sb.append(i > 0 ? ", " : "").append(num(k.v()[i]));
                sb.append("], \"e\": ").append(quote(k.ease().name().toLowerCase(Locale.ROOT)));
                if (k.bez() != null) {
                    sb.append(", \"bez\": [");
                    for (int i = 0; i < k.bez().length; i++) sb.append(i > 0 ? ", " : "").append(num(k.bez()[i]));
                    sb.append("]");
                }
                sb.append("}");
            }
            sb.append("]");
        }
        sb.append("\n  },\n  \"events\": [");
        for (int i = 0; i < events.size(); i++) {
            sb.append(i > 0 ? ", " : "").append("{\"t\": ").append(num(events.get(i).t())).append(", \"name\": ").append(quote(events.get(i).name())).append("}");
        }
        sb.append("]\n}\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static AnimClip fromJson(String json) {
        Object root = new JsonReader(json).value();
        Map<String, Object> o = (Map<String, Object>) root;
        AnimClip c = new AnimClip((String) o.getOrDefault("name", "clip"), ((Number) o.getOrDefault("length", 1.0)).doubleValue(),
                Boolean.TRUE.equals(o.get("loop")));
        Map<String, Object> tr = (Map<String, Object>) o.getOrDefault("tracks", Map.of());
        for (Map.Entry<String, Object> e : tr.entrySet()) {
            for (Object ko : (List<Object>) e.getValue()) {
                Map<String, Object> k = (Map<String, Object>) ko;
                List<Object> vs = (List<Object>) k.get("v");
                double[] v = new double[vs.size()];
                for (int i = 0; i < v.length; i++) v[i] = ((Number) vs.get(i)).doubleValue();
                double[] bez = null;
                if (k.get("bez") != null) {
                    List<Object> bs = (List<Object>) k.get("bez");
                    bez = new double[bs.size()];
                    for (int i = 0; i < bez.length; i++) bez[i] = ((Number) bs.get(i)).doubleValue();
                }
                c.key(e.getKey(), ((Number) k.get("t")).doubleValue(), Ease.byName((String) k.getOrDefault("e", "linear")), bez, v);
            }
        }
        for (Object eo : (List<Object>) o.getOrDefault("events", List.of())) {
            Map<String, Object> ev = (Map<String, Object>) eo;
            c.event(((Number) ev.get("t")).doubleValue(), (String) ev.get("name"));
        }
        return c;
    }

    private static String num(double d) {
        return String.format(Locale.ROOT, "%.5f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A small recursive-descent JSON reader: objects, arrays, strings, numbers, booleans, null. */
    private static final class JsonReader {
        private final String s;
        private int i;

        JsonReader(String s) {
            this.s = s;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    i++;                                   // the colon
                    m.put(k, value());
                    ws();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++;
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(st, i));
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') {
                    char n = s.charAt(i++);
                    sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
                } else sb.append(c);
            }
            i++;
            return sb.toString();
        }
    }
}

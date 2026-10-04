package com.lotusblight.overlay;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.function.Supplier;

/**
 * What the events of a song do to its playfield: where the circles and the health bars are (turned with the world,
 * shrunk with the field, held in the hands of the thing that holds the screen), what the circles are made of at
 * each moment (bubbles while the desktop is Aero, error boxes that want their OK clicked, eyes, pieces of the
 * broken desktop), and how much they tear. The game draws and takes clicks through it, so the picture and the
 * playing always agree.
 */
abstract class PlayfieldFx {
    enum Style { NORMAL, BUBBLE, ERROR, SHARD, EYE }

    /** Screen from playfield. */
    AffineTransform transform(double ms, int w, int h) {
        return new AffineTransform();
    }

    /** Everything outside this (screen space) is hidden; null = no limit. */
    Shape clip(double ms, int w, int h) {
        return null;
    }

    /** What the circle numbered {@code idx}, to be hit at {@code hitMs}, is made of. */
    Style style(double hitMs, int idx) {
        return Style.NORMAL;
    }

    /** 0..1: how badly the circles and bars tear and stutter. */
    double glitch(double ms) {
        return 0;
    }

    /** The desktop, for the pieces (null if there is none). */
    BufferedImage desktop() {
        return null;
    }

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // ------------------------------------------------------------ the first song

    /** Bubbles while the desktop is Aero, eyes while the eye field is open, the playfield drawing away with the field. */
    static PlayfieldFx song1() {
        return new PlayfieldFx() {
            @Override
            AffineTransform transform(double ms, int w, int h) {
                AffineTransform at = new AffineTransform();
                double z = Hazards.recedeAt(ms);
                double s = 1 - 0.42 * z;
                double shake = ms >= GlitcherActor.STOMP_MS && ms < GlitcherActor.STOMP_MS + 650 ? (1 - (ms - GlitcherActor.STOMP_MS) / 650.0) * h * 0.014 : 0;
                Random r = new Random((long) (ms / 30));
                at.translate(w / 2.0 + (r.nextDouble() - 0.5) * shake * 2, h / 2.0 + h * 0.1 * z + (r.nextDouble() - 0.5) * shake * 2);
                at.scale(s, s);
                at.translate(-w / 2.0, -h / 2.0);
                return at;
            }

            @Override
            Style style(double hitMs, int idx) {
                if (Hazards.fieldAt(hitMs) > 0.6) return Style.EYE;
                if (Hazards.aeroAt(hitMs) > 0.6) return Style.BUBBLE;
                return Style.NORMAL;
            }

            @Override
            double glitch(double ms) {
                // While the wallpaper is torn away for the eyes, and at the stomp.
                double stomp = ms >= GlitcherActor.STOMP_MS - 300 && ms < GlitcherActor.BLACKOUT_MS + 900 ? 0.8 : 0;
                return stomp;
            }
        };
    }

    /** The first song on the Chromo difficulty: all of the above, and the screen turns (0:45) and swings both ways (1:43). */
    static PlayfieldFx song1Chromo() {
        PlayfieldFx base = song1();
        return new PlayfieldFx() {
            @Override
            AffineTransform transform(double ms, int w, int h) {
                AffineTransform at = new AffineTransform();
                at.translate(w / 2.0, h / 2.0);
                at.rotate(ChromoSong1.spin(ms));
                at.translate(-w / 2.0, -h / 2.0);
                at.concatenate(base.transform(ms, w, h));
                return at;
            }

            @Override
            Style style(double hitMs, int idx) {
                return base.style(hitMs, idx);
            }

            @Override
            double glitch(double ms) {
                return base.glitch(ms);
            }
        };
    }

    // ------------------------------------------------------------ the second song

    /** Error boxes among the circles while the Glitcher throws his windows; the playfield tears as the eye comes. */
    static PlayfieldFx song2() {
        return new PlayfieldFx() {
            @Override
            Style style(double hitMs, int idx) {
                if (hitMs >= 8_000 && hitMs < 77_000 && idx % 4 == 2) return Style.ERROR;
                return Style.NORMAL;
            }

            @Override
            double glitch(double ms) {
                return ContactBreak.glitchAmount(ms);
            }

            @Override
            AffineTransform transform(double ms, int w, int h) {
                double g = ContactBreak.glitchAmount(ms);
                AffineTransform at = new AffineTransform();
                if (g > 0.05) {
                    Random r = new Random((long) (ms / 55));
                    if (r.nextDouble() < g * 0.4) at.translate((r.nextDouble() - 0.5) * w * 0.02 * g, (r.nextDouble() - 0.5) * h * 0.012 * g);
                }
                return at;
            }
        };
    }

    // ------------------------------------------------------------ the third song

    /**
     * Pieces of the broken desktop for circles, the whole playfield turning with the world and shut in the hands
     * of the thing that holds the screen, eyes in the tunnel, shaking with Honcho's fear, torn by the Glitcher at the end.
     */
    static PlayfieldFx song3(Supplier<BufferedImage> desktop) {
        return new PlayfieldFx() {
            @Override
            AffineTransform transform(double ms, int w, int h) {
                double s = ms / 1000.0;
                AffineTransform at = new AffineTransform();
                double[] rect = Song3Show.screenRect(ms, w, h);
                if (rect != null) {
                    at.translate(rect[0], rect[1]);
                    at.scale(rect[2] / w, rect[3] / h);
                    return at;
                }
                at.translate(w / 2.0, h / 2.0);
                double a = Song3Show.spin(s);
                if (a != 0) {
                    at.rotate(a);
                }
                if (s >= Song3Show.TUNNEL_FROM && s < Song3Show.TUNNEL_TO) {          // Honcho's fear shakes it, and his heart
                    double u = s - Song3Show.TUNNEL_FROM;
                    double fear = smooth(u / 9.0);
                    Random r = new Random((long) (s * 24));
                    double heart = 1 + 0.018 * Math.pow(Math.max(0, Math.sin(s * 5.2)), 6) * (1 + 2 * fear);
                    at.translate((r.nextDouble() - 0.5) * h * 0.018 * fear, (r.nextDouble() - 0.5) * h * 0.018 * fear);
                    at.scale(heart, heart);
                }
                at.translate(-w / 2.0, -h / 2.0);
                return at;
            }

            @Override
            Shape clip(double ms, int w, int h) {
                double[] rect = Song3Show.screenRect(ms, w, h);
                return rect == null ? null : new Rectangle2D.Double(rect[0], rect[1], rect[2], rect[3]);
            }

            @Override
            Style style(double hitMs, int idx) {
                double s = hitMs / 1000.0;
                if (s >= Song3Show.TUNNEL_FROM && s < Song3Show.TUNNEL_TO) return Style.EYE;
                return Style.SHARD;
            }

            @Override
            double glitch(double ms) {
                return smooth((ms / 1000.0 - Song3Show.GLITCH_FROM) / 25.0) * 0.9;
            }

            @Override
            BufferedImage desktop() {
                return desktop.get();
            }
        };
    }
}

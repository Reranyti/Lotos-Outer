package com.lotusblight.overlay;

import java.util.Random;

/**
 * Glitch effects applied straight to an ARGB pixel buffer: torn horizontal slices, a red/blue channel
 * split, dead blocks and dropped rows. Everything scales with an intensity from 0 (clean) to 1.
 */
final class GlitchFx {
    private static final int[] BLOCK_COLORS = {0xFF000000, 0xFF7300FF, 0xFF8624FF, 0xFF160046};

    private final Random random = new Random();
    private int[] scratch = new int[0];

    void apply(int[] px, int w, int h, double intensity) {
        if (intensity <= 0.01) return;
        if (scratch.length < px.length) scratch = new int[px.length];

        tearSlices(px, w, h, intensity);
        if (random.nextDouble() < intensity) channelSplit(px, w, h, 1 + (int) (intensity * w * 0.02));
        if (random.nextDouble() < intensity * 0.8) deadBlocks(px, w, h, intensity);
        if (random.nextDouble() < intensity * 0.5) dropRows(px, w, h, intensity);
    }

    /** Shifts a few horizontal bands sideways, like a torn video frame. */
    private void tearSlices(int[] px, int w, int h, double intensity) {
        int slices = (int) (intensity * 8) + (random.nextDouble() < intensity ? 1 : 0);
        for (int s = 0; s < slices; s++) {
            int bandH = 2 + random.nextInt(Math.max(1, (int) (h * 0.08)));
            int y0 = random.nextInt(Math.max(1, h - bandH));
            int shift = (int) ((random.nextDouble() * 2 - 1) * w * 0.15 * intensity);
            if (shift == 0) continue;
            for (int y = y0; y < y0 + bandH; y++) {
                int row = y * w;
                System.arraycopy(px, row, scratch, 0, w);
                for (int x = 0; x < w; x++) {
                    int from = x - shift;
                    px[row + x] = from >= 0 && from < w ? scratch[from] : 0;
                }
            }
        }
    }

    /** Pulls the red channel one way and the blue the other, leaving coloured fringes on the edges. */
    private void channelSplit(int[] px, int w, int h, int offset) {
        System.arraycopy(px, 0, scratch, 0, w * h);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int here = scratch[row + x];
                int left = x + offset < w ? scratch[row + x + offset] : 0;
                int right = x - offset >= 0 ? scratch[row + x - offset] : 0;
                int a = Math.max(here >>> 24, Math.max(left >>> 24, right >>> 24));
                if (a == 0) {
                    px[row + x] = 0;
                    continue;
                }
                int r = (left >> 16) & 0xFF;
                int g = (here >> 8) & 0xFF;
                int b = right & 0xFF;
                px[row + x] = a << 24 | r << 16 | g << 8 | b;
            }
        }
    }

    /** Stamps square blocks of the skin's own colours over the opaque pixels. */
    private void deadBlocks(int[] px, int w, int h, double intensity) {
        int blocks = 1 + (int) (intensity * 10);
        for (int i = 0; i < blocks; i++) {
            int size = 4 + random.nextInt(Math.max(1, (int) (w * 0.08)));
            int bx = random.nextInt(Math.max(1, w - size));
            int by = random.nextInt(Math.max(1, h - size));
            int color = BLOCK_COLORS[random.nextInt(BLOCK_COLORS.length)];
            for (int y = by; y < by + size; y++) {
                for (int x = bx; x < bx + size; x++) {
                    int idx = y * w + x;
                    if ((px[idx] >>> 24) != 0) px[idx] = color;
                }
            }
        }
    }

    /** Blanks out thin rows, as if the signal dropped for a moment. */
    private void dropRows(int[] px, int w, int h, double intensity) {
        int rows = 1 + (int) (intensity * 6);
        for (int i = 0; i < rows; i++) {
            int y0 = random.nextInt(h);
            int rh = 1 + random.nextInt(3);
            for (int y = y0; y < Math.min(h, y0 + rh); y++) {
                java.util.Arrays.fill(px, y * w, y * w + w, 0);
            }
        }
    }
}

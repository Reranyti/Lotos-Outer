package com.lotusblight.spread;

/**
 * Tuning table for the four infection phases (Цветение / Захват реки /
 * Захват ближников / Лотосовый мини-биом, see PHASE_DESIGN.md).
 *
 * Unlike the old implementation (pure wall-clock age lookup), phase here is
 * driven primarily by how much the outbreak has actually infected — an
 * outbreak that a player keeps cutting back stays in an early phase, one
 * left alone visibly grows. Radius and attempts-per-tick scale with phase so
 * later phases feel like a real escalating mini-biome instead of a constant
 * trickle.
 */
public final class InfectionPhases {

    private InfectionPhases() {
    }

    /**
     * Infected-block-count thresholds to advance out of phase 1/2/3. Index 0 unused.
     * Phase 4 (which grows the lotus heart) raised from 220 - with the boosted attempts below,
     * 220 let a whole field of independently-seeded marsh-pond outbreaks (see
     * GuaranteedSpawnManager) all reach phase 4 within minutes of each other, scattering a heart
     * across nearly every pond in a lotus_marsh biome at once instead of it staying a rare,
     * earned mini-biome milestone. First pass overshot to 500 and made a single deliberately
     * nurtured outbreak feel like it barely progressed at all - split the difference at 350.
     */
    private static final int[] PHASE_UP_THRESHOLD = {0, 0, 24, 90, 350};

    /** Radius (blocks) a spread attempt from this phase may reach from its source block. */
    private static final int[] SPREAD_RADIUS = {0, 3, 4, 5, 5};

    /**
     * Spread attempts performed per active outbreak per engine pass (every SPREAD_INTERVAL_TICKS,
     * 10s by default - not literally per tick despite the name). First boosted from {1,2,3,4} to
     * {2,4,6,7} to fix phase 1/2 reading as "почти пусты"; feedback after that pass was still
     * "прогресс ощущается нищенски" - the early phases should feel like a smaller version of the
     * mini-biome's density, not a thin preview of it. Raised again, keeping the same escalating
     * shape phase 4 already had.
     */
    private static final int[] ATTEMPTS_PER_TICK = {0, 4, 6, 8, 9};

    /** Chance (0..1) that a phase-4 attempt near land also tries to grow a vine barrier. */
    private static final double VINE_BARRIER_CHANCE = 0.015;

    /** Lowest infected-block-count that counts as this phase — used by admin commands that force a phase directly. */
    public static int minBlockCountForPhase(int phase) {
        return PHASE_UP_THRESHOLD[Math.max(1, Math.min(4, phase))];
    }

    public static int phaseForBlockCount(int infectedBlockCount) {
        int phase = 1;
        for (int p = 2; p <= 4; p++) {
            if (infectedBlockCount >= PHASE_UP_THRESHOLD[p]) {
                phase = p;
            }
        }
        return phase;
    }

    public static float progressWithinPhase(int phase, int infectedBlockCount) {
        int lower = PHASE_UP_THRESHOLD[phase];
        int upper = phase < 4 ? PHASE_UP_THRESHOLD[phase + 1] : Math.max(lower + 1, lower + 120);
        return net.minecraft.util.Mth.clamp((infectedBlockCount - lower) / (float) (upper - lower), 0f, 1f);
    }

    public static int spreadRadius(int phase) {
        return SPREAD_RADIUS[Math.max(1, Math.min(4, phase))];
    }

    public static int attemptsPerTick(int phase) {
        return ATTEMPTS_PER_TICK[Math.max(1, Math.min(4, phase))];
    }

    public static boolean canGrowVineBarrier(int phase) {
        return phase >= 4;
    }

    public static double vineBarrierChance() {
        return VINE_BARRIER_CHANCE;
    }

    public static String phaseName(int phase) {
        return switch (phase) {
            case 1 -> "Цветение";
            case 2 -> "Захват реки";
            case 3 -> "Захват ближников";
            default -> "Лотосовый мини-биом";
        };
    }
}

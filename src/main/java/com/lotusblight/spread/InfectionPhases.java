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
    /**
     * Phase 5 ("Под контролем Мирового Лотоса") added on top of the original 4 - per the plan,
     * getting there from phase 4 should take 3-5x longer than any prior phase transition, since
     * it's meant to be a rare, momentous breakout rather than a routine milestone. Phase 3->4 was
     * a 260-block gap; 1400 puts the 4->5 gap at ~1050, roughly 4x that.
     */
    private static final int[] PHASE_UP_THRESHOLD = {0, 0, 24, 90, 350, 1400};

    /**
     * Radius (blocks) a spread attempt from this phase may reach from its source block. Phase 4
     * used to be 5 - barely different from phase 3's own 5, so "reaching the mini-biome" changed
     * almost nothing about how far the infection could actually reach per attempt. A 100%-phase-4
     * outbreak still read as "a drop in the ocean" against the real TerraBlender-generated biome
     * (which places its trees across the whole chunk at once, not through bounded per-tick
     * attempts) - this alone was never going to close that gap, but it needed to stop being an
     * afterthought next to phase 3.
     */
    /**
     * Was 500 - a genuine bug, not just an aggressive number. randomNeighbour() samples UNIFORMLY
     * across the full radius, so a 500-block radius meant almost every attempt landed in an
     * unloaded chunk and silently failed (hasChunkAt check) - phase 5 looked like it did
     * literally nothing ("даже на 5 стадии изменений нет"). "No longer locally contained" instead
     * comes from removing the frontier size cap at phase 5 (see FRONTIER_CAP's use in
     * InfectionSpreadEngine#pushFrontier) so growth keeps accumulating outward indefinitely
     * instead of being trimmed back to a small recent window - not from one huge single-attempt
     * radius that mostly wastes its own attempts.
     */
    private static final int[] SPREAD_RADIUS = {0, 3, 5, 7, 12, 16};

    /**
     * Spread attempts performed per active outbreak per engine pass (every SPREAD_INTERVAL_TICKS,
     * 10s by default - not literally per tick despite the name). Raised twice already for "прогресс
     * ощущается нищенски"; still not enough - even standing right next to a maxed-out outbreak
     * "ничего не даёт", no felt danger, no felt growth. Phase 4 in particular needs to be a real
     * step up from phase 3, not a rounding error on the same curve.
     */
    private static final int[] ATTEMPTS_PER_TICK = {0, 4, 7, 11, 22, 35};

    /** Chance (0..1) that a phase-4 attempt near land also tries to grow a vine barrier. */
    private static final double VINE_BARRIER_CHANCE = 0.015;

    public static final int MAX_PHASE = 5;

    /** Lowest infected-block-count that counts as this phase — used by admin commands that force a phase directly. */
    public static int minBlockCountForPhase(int phase) {
        return PHASE_UP_THRESHOLD[Math.max(1, Math.min(MAX_PHASE, phase))];
    }

    public static int phaseForBlockCount(int infectedBlockCount) {
        int phase = 1;
        for (int p = 2; p <= MAX_PHASE; p++) {
            if (infectedBlockCount >= PHASE_UP_THRESHOLD[p]) {
                phase = p;
            }
        }
        return phase;
    }

    public static float progressWithinPhase(int phase, int infectedBlockCount) {
        int lower = PHASE_UP_THRESHOLD[phase];
        int upper = phase < MAX_PHASE ? PHASE_UP_THRESHOLD[phase + 1] : Math.max(lower + 1, lower + 120);
        return net.minecraft.util.Mth.clamp((infectedBlockCount - lower) / (float) (upper - lower), 0f, 1f);
    }

    public static int spreadRadius(int phase) {
        return SPREAD_RADIUS[Math.max(1, Math.min(MAX_PHASE, phase))];
    }

    public static int attemptsPerTick(int phase) {
        return ATTEMPTS_PER_TICK[Math.max(1, Math.min(MAX_PHASE, phase))];
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
            case 4 -> "Лотосовый мини-биом";
            default -> "Под контролем Мирового Лотоса";
        };
    }
}

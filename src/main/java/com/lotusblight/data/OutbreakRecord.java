package com.lotusblight.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Immutable snapshot of a single infection outbreak (anchor). Replaces the
 * non-persistent {@code static Map<GlobalPos,Integer>} that used to live in
 * LotusEvents — every field here survives a server restart because it is
 * only ever read from / written to {@link OutbreakSavedData}.
 */
public record OutbreakRecord(
        UUID id,
        BlockPos pos,
        int phase,
        float progress,
        boolean hidden,
        long createdGameTime,
        int infectedBlockCount,
        int maxPhaseCap,
        int peakPhase,
        long suppressedUntil,
        boolean biomeConverted
) {
    public static final int MIN_PHASE = 1;
    /** Kept in sync with InfectionPhases.MAX_PHASE by hand - was hardcoded 4, which silently
     * clamped phase 5 back down to 4 on every save (withPhase is called on every spread tick). */
    public static final int MAX_PHASE = com.lotusblight.spread.InfectionPhases.MAX_PHASE;

    /**
     * RootGrowthEngine used to enforce a child outbreak's "stay small" phase cap through its own
     * in-memory-only Map<UUID, Integer> (childPhaseCaps), re-clamping it every 40-tick sweep. None
     * of that survives a server restart - the map starts empty again, so any already-registered
     * child outbreak silently lost its cap and could grow to full size like a normal one. The cap
     * is now part of the persisted record itself, so it survives restarts the same way every other
     * field here does.
     */
    public OutbreakRecord withPhase(int newPhase) {
        return new OutbreakRecord(id, pos, Math.max(MIN_PHASE, Math.min(maxPhaseCap, newPhase)), progress, hidden, createdGameTime, infectedBlockCount, maxPhaseCap, peakPhase, suppressedUntil, biomeConverted);
    }

    public OutbreakRecord withProgress(float newProgress) {
        return new OutbreakRecord(id, pos, phase, Math.max(0f, Math.min(1f, newProgress)), hidden, createdGameTime, infectedBlockCount, maxPhaseCap, peakPhase, suppressedUntil, biomeConverted);
    }

    public OutbreakRecord withHidden(boolean newHidden) {
        return new OutbreakRecord(id, pos, phase, progress, newHidden, createdGameTime, infectedBlockCount, maxPhaseCap, peakPhase, suppressedUntil, biomeConverted);
    }

    public OutbreakRecord withInfectedBlockCount(int newCount) {
        return new OutbreakRecord(id, pos, phase, progress, hidden, createdGameTime, Math.max(0, newCount), maxPhaseCap, peakPhase, suppressedUntil, biomeConverted);
    }

    public OutbreakRecord withMaxPhaseCap(int newCap) {
        int clampedCap = Math.max(MIN_PHASE, Math.min(MAX_PHASE, newCap));
        return new OutbreakRecord(id, pos, Math.min(phase, clampedCap), progress, hidden, createdGameTime, infectedBlockCount, clampedCap, peakPhase, suppressedUntil, biomeConverted);
    }

    /**
     * Highest phase this outbreak has ever reached. The live phase follows infectedBlockCount and can
     * drop (cleansing, mining, the open-ocean cap); the one-time phase-up payoffs - announcement,
     * the phase 4 tree burst and the WorldEdit biome rewrite - key off this instead, so dipping
     * under a threshold and climbing back doesn't replay them.
     */
    public OutbreakRecord withPeakPhase(int newPeak) {
        return new OutbreakRecord(id, pos, phase, progress, hidden, createdGameTime, infectedBlockCount, maxPhaseCap, Math.max(peakPhase, newPeak), suppressedUntil, biomeConverted);
    }

    /**
     * "остаются но замедляют развитие" - cleansing powder leaves lotus shoots standing, but dusting
     * one slows its outbreak down until this game time (see InfectionSpreadEngine#tickOutbreak).
     */
    public OutbreakRecord withSuppressedUntil(long gameTime) {
        return new OutbreakRecord(id, pos, phase, progress, hidden, createdGameTime, infectedBlockCount, maxPhaseCap, peakPhase, gameTime, biomeConverted);
    }

    /** The phase 4 lotus_marsh biome rewrite (LotusBiomeConverter) has been done for this outbreak. */
    public OutbreakRecord withBiomeConverted(boolean converted) {
        return new OutbreakRecord(id, pos, phase, progress, hidden, createdGameTime, infectedBlockCount, maxPhaseCap, peakPhase, suppressedUntil, converted);
    }

    public boolean isSuppressed(long gameTime) {
        return gameTime < suppressedUntil;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putUUID("Id", id);
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putInt("Phase", phase);
        tag.putFloat("Progress", progress);
        tag.putBoolean("Hidden", hidden);
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putInt("InfectedBlockCount", infectedBlockCount);
        tag.putInt("MaxPhaseCap", maxPhaseCap);
        tag.putInt("PeakPhase", peakPhase);
        tag.putLong("SuppressedUntil", suppressedUntil);
        tag.putBoolean("BiomeConverted", biomeConverted);
        return tag;
    }

    public static OutbreakRecord load(CompoundTag tag) {
        return new OutbreakRecord(
                tag.getUUID("Id"),
                new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                tag.getInt("Phase"),
                tag.getFloat("Progress"),
                tag.getBoolean("Hidden"),
                tag.getLong("CreatedGameTime"),
                tag.getInt("InfectedBlockCount"),
                // Older saves predate this field entirely - default to uncapped rather than 0
                // (which getInt returns for a missing key, and would wrongly cap every existing
                // outbreak down to nothing the moment its save data is next loaded).
                tag.contains("MaxPhaseCap") ? tag.getInt("MaxPhaseCap") : MAX_PHASE,
                // Older saves have no peak yet - the current phase is the best known lower bound.
                tag.contains("PeakPhase") ? tag.getInt("PeakPhase") : tag.getInt("Phase"),
                tag.getLong("SuppressedUntil"),
                // Older saves: outbreaks that reached phase 4 before 1.1042 only had their Y=0 biome
                // cells rewritten, so their surface never changed - left false, they get redone once.
                tag.getBoolean("BiomeConverted")
        );
    }

    public static OutbreakRecord newAnchor(BlockPos pos, long gameTime, boolean hidden) {
        return new OutbreakRecord(UUID.randomUUID(), pos, MIN_PHASE, 0f, hidden, gameTime, 0, MAX_PHASE, MIN_PHASE, 0L, false);
    }
}

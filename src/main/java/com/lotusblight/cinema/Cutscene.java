package com.lotusblight.cinema;

/**
 * One animated scene the engine can play: a function from a moment of the clip to a picture. The same scene can be drawn two ways, by the
 * processor ({@link #renderCpu}, a plain array of pixels) or by the graphics card ({@link #collectGpu} gathers what is to be drawn without
 * touching the card, so a thread of its own can do it while the game draws the last frame; {@link GpuScene#draw} then draws it).
 * A new scene only has to build its 3D picture once and answer both.
 */
public interface Cutscene {
    /** The size of the processor's picture, in pixels (it is also the size the 2D layer's coordinates are given in). */
    int width();

    int height();

    /** How long the clip lasts, in seconds. */
    double length();

    /** The picture {@code clock} seconds into the clip, ARGB, {@link #width()} by {@link #height()}. The array is reused from frame to frame. */
    int[] renderCpu(double clock);

    /** The same moment, collected for the graphics card at an output of {@code outW} by {@code outH}. */
    GpuScene.Frame collectGpu(double clock, int outW, int outH);
}

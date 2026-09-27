package com.lotusblight.overlay;

import java.awt.Graphics2D;

/** The finale animation as the fight draws it: the picture for a moment in time. */
interface FinaleVideo {
    void render(Graphics2D g, int w, int h, double timeSec);

    /** Whether there is anything to show at all - if not, the fight carries on without. */
    boolean ready();
}

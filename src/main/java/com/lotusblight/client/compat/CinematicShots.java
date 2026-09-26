package com.lotusblight.client.compat;

import net.cinematic.CinematicClientController;
import net.cinematic.CinematicNetwork;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The only place that touches the optional Cinematic mod (modId "cinematic") - callers check
 * {@code ModList.get().isLoaded("cinematic")} first, so none of its classes load without it.
 *
 * Cinematic runs one letterboxed camera move per session and then fades out and back to the
 * player, blocking every screen while it's active - so it's used for shots between our own windows,
 * never around them.
 */
public final class CinematicShots {
    private static final float REVEAL_SECONDS = 3.2f;
    /** How far in front of Honcho the camera stops, off to one side of the player's line to him. */
    private static final double REVEAL_STANDOFF = 2.4;
    private static final double REVEAL_SIDE_DEGREES = 35.0;

    private CinematicShots() {}

    /**
     * The trip-meeting reveal: the camera leaves the player lying on the ground, rises in an arc to
     * the side and settles in front of Honcho, looking at him, before fading back to the player.
     */
    public static void playHonchoReveal(LocalPlayer player, Entity honcho) {
        Vec3 eye = player.getEyePosition();
        Vec3 honchoEye = honcho.getEyePosition();
        Vec3 flat = new Vec3(eye.x - honchoEye.x, 0, eye.z - honchoEye.z);
        Vec3 toPlayer = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-toPlayer.z, 0, toPlayer.x);

        Vec3 rise = eye.add(0, 1.6, 0).add(side.scale(1.0));
        Vec3 settle = honchoEye.add(toPlayer.yRot((float) Math.toRadians(REVEAL_SIDE_DEGREES)).scale(REVEAL_STANDOFF)).add(0, -0.2, 0);
        Vec3[] path = {eye, rise, settle};

        double[] xs = new double[path.length];
        double[] ys = new double[path.length];
        double[] zs = new double[path.length];
        for (int i = 0; i < path.length; i++) {
            xs[i] = path[i].x;
            ys[i] = path[i].y;
            zs[i] = path[i].z;
        }
        CinematicClientController.handleGoToPacket(new CinematicNetwork.GoToCinematicPacket(honcho.getId(), REVEAL_SECONDS, xs, ys, zs));
    }

    /** True from the bars coming in until the view is handed back to the player. */
    public static boolean isRunning() {
        return CinematicClientController.isActive();
    }
}

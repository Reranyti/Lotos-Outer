package com.lotusblight.branch;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.NormalBranchScenePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the entry scene once the death-run completes: the sky reddens and the player is pulled to the
 * nearest water, held under as if drowning, lifted back out while the red deepens, and then the screen
 * floods bordeaux and the world "comes apart" - all on screen only, nothing is really broken - before
 * the move to the empty place. The stages and timings live here; the client just paints what it's told.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID)
public final class NormalBranchScene {
    // Stage lengths in ticks (20/s).
    private static final int RED = 70;        // ~3.5s pulled to water, reddening
    private static final int DROWN = 60;      // ~3s held under
    private static final int LIFT = 50;       // ~2.5s lifted out
    private static final int COLLAPSE = 70;   // ~3.5s bordeaux flood + shake
    private static final int TOTAL = RED + DROWN + LIFT + COLLAPSE;

    private static final Map<UUID, Run> runs = new HashMap<>();

    private NormalBranchScene() {}

    private static final class Run {
        int tick;
        Vec3 water;      // where the player is drawn to drown
        double liftFrom; // y the lift starts from
        ServerPlayer owner; // the body the scene runs on (a respawn gives a new one)
    }

    /** Starts the scene for a player who has just finished the run. */
    static void start(ServerPlayer player) {
        Run run = new Run();
        run.water = nearestWater(player);
        run.owner = player;
        runs.put(player.getUUID(), run);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TOTAL + 40, 250, false, false));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NormalBranchScenePacket(1, 0f));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || runs.isEmpty()) return;
        runs.entrySet().removeIf(e -> {
            ServerPlayer player = playerFor(event, e.getKey());
            if (player == null) return true;
            return tickRun(player, e.getValue());
        });
    }

    /** Advances one player's scene a tick. Returns true when it's finished (and the run is dropped). */
    private static boolean tickRun(ServerPlayer player, Run run) {
        // The scene is started by the final death itself: it waits for the respawn, then plays from the start on the new body.
        if (!player.isAlive()) return false;
        if (run.owner != player) {
            run.owner = player;
            run.tick = 0;
            run.water = nearestWater(player);
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TOTAL + 40, 250, false, false));
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NormalBranchScenePacket(1, 0f));
        }
        run.tick++;
        // The player can't act during any of this.
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 250, false, false));

        int t = run.tick;
        if (t <= RED) {
            // Draw the player toward the water and redden the sky.
            if (run.water != null) {
                Vec3 to = run.water.subtract(player.position()).scale(0.15);
                player.teleportTo(player.getX() + to.x, player.getY() + to.y * 0.4, player.getZ() + to.z);
            }
            send(player, NormalBranchSceneStage.RED, t / (float) RED * 0.5f);
        } else if (t <= RED + DROWN) {
            // Held under, air draining, red deepening.
            player.setAirSupply(Math.max(-20, player.getAirSupply() - 4));
            player.setDeltaMovement(0, -0.02, 0);
            float p = (t - RED) / (float) DROWN;
            send(player, NormalBranchSceneStage.DROWN, 0.5f + p * 0.3f);
            if (t == RED + DROWN) run.liftFrom = player.getY();
        } else if (t <= RED + DROWN + LIFT) {
            // Lifted up out of the water, hanging.
            float p = (t - RED - DROWN) / (float) LIFT;
            player.teleportTo(player.getX(), run.liftFrom + p * 4.0, player.getZ());
            player.setAirSupply(player.getMaxAirSupply());
            send(player, NormalBranchSceneStage.LIFT, 0.8f);
        } else {
            // The world comes apart on screen; bordeaux floods in.
            float p = (t - RED - DROWN - LIFT) / (float) COLLAPSE;
            send(player, NormalBranchSceneStage.COLLAPSE, 0.8f + p * 0.2f);
        }

        if (t >= TOTAL) {
            send(player, NormalBranchSceneStage.OFF, 0f);
            NormalBranchEntry.finish(player);
            return true;
        }
        return false;
    }

    private static void send(ServerPlayer player, int stage, float intensity) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new NormalBranchScenePacket(stage, intensity));
    }

    /** The nearest water surface within a modest range, or null if there is none close by. */
    private static Vec3 nearestWater(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int r = 24;
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -8; dy <= 8; dy++) {
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(p)) continue;
                    FluidState fluid = level.getFluidState(p);
                    if (fluid.is(net.minecraft.world.level.material.Fluids.WATER)) {
                        double d = p.distSqr(origin);
                        if (d < bestSq) { bestSq = d; best = p.immutable(); }
                    }
                }
            }
        }
        return best == null ? null : new Vec3(best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5);
    }

    private static ServerPlayer playerFor(TickEvent.ServerTickEvent event, UUID id) {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() == null ? null
                : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(id);
    }

    /** Stage constants mirrored to the client overlay. */
    private static final class NormalBranchSceneStage {
        static final int OFF = 0, RED = 1, DROWN = 2, LIFT = 3, COLLAPSE = 4;
    }
}

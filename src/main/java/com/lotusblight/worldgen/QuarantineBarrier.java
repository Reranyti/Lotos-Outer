package com.lotusblight.worldgen;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "при касании откидывает тебя как попрыгунчика... ты технически не можешь умереть от кинетической
 * энергии и вообще от него" - the actual gameplay enforcement of the quarantine wall. Deliberately
 * NOT vanilla's own border damage-per-second (see WorldBorderSetup zeroing that out) - touching the
 * edge instead launches the player back inward with a strong bounce, and a short window afterward
 * blanket-cancels fall/kinetic damage so the bounce (or the landing after it) can never kill them.
 * Purely mechanical - QuarantineBarrierRenderer (client side) is the visual, unrelated to this.
 */
public final class QuarantineBarrier {
    private static final double BOUNCE_THRESHOLD = 2.0;
    private static final double BOUNCE_STRENGTH = 1.8;
    private static final double BOUNCE_UPWARD = 0.4;
    private static final int IMMUNITY_TICKS = 100;

    private final Map<UUID, Long> immuneUntilTick = new HashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        WorldBorder border = overworld.getWorldBorder();
        long gameTick = overworld.getGameTime();

        for (ServerPlayer player : overworld.players()) {
            double x = player.getX();
            double z = player.getZ();
            double distToMinX = x - border.getMinX();
            double distToMaxX = border.getMaxX() - x;
            double distToMinZ = z - border.getMinZ();
            double distToMaxZ = border.getMaxZ() - z;
            double nearest = Math.min(Math.min(distToMinX, distToMaxX), Math.min(distToMinZ, distToMaxZ));
            if (nearest > BOUNCE_THRESHOLD) continue;

            double pushX = 0;
            double pushZ = 0;
            if (distToMinX <= BOUNCE_THRESHOLD) pushX += 1;
            if (distToMaxX <= BOUNCE_THRESHOLD) pushX -= 1;
            if (distToMinZ <= BOUNCE_THRESHOLD) pushZ += 1;
            if (distToMaxZ <= BOUNCE_THRESHOLD) pushZ -= 1;
            double len = Math.sqrt(pushX * pushX + pushZ * pushZ);
            if (len == 0) continue;
            pushX = pushX / len * BOUNCE_STRENGTH;
            pushZ = pushZ / len * BOUNCE_STRENGTH;

            player.setDeltaMovement(pushX, BOUNCE_UPWARD, pushZ);
            player.hurtMarked = true;
            player.fallDistance = 0f;
            immuneUntilTick.put(player.getUUID(), gameTick + IMMUNITY_TICKS);
        }

        if (server.getTickCount() % 200 == 0) {
            immuneUntilTick.entrySet().removeIf(e -> e.getValue() < gameTick - IMMUNITY_TICKS);
        }
    }

    /** Cancels fall/kinetic damage for a player still inside their post-bounce immunity window - the bounce itself, or the landing after it, must never be lethal. */
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Long until = immuneUntilTick.get(player.getUUID());
        if (until == null) return;
        if (player.level().getGameTime() > until) return;
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }
}

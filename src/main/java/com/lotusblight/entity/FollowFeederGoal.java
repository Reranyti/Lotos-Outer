package com.lotusblight.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * "зависимость от игрока в прямом смысле" - Honcho walks toward whoever last fed him a vial once
 * he's too far away, same shape as a tamed animal following its owner, but keyed off feederUuid
 * (set in HonchoEntity#mobInteract) instead of a real TamableAnimal owner relationship, since Zombie
 * has no such thing built in.
 */
final class FollowFeederGoal extends Goal {
    private final HonchoEntity honcho;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;
    private ServerPlayer feeder;

    FollowFeederGoal(HonchoEntity honcho, double speedModifier, float stopDistance, float startDistance) {
        this.honcho = honcho;
        this.speedModifier = speedModifier;
        this.stopDistance = stopDistance;
        this.startDistance = startDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        var uuid = honcho.getFeederUuid();
        if (uuid == null || !(honcho.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        var player = level.getServer().getPlayerList().getPlayer(uuid);
        if (player == null || player.level() != honcho.level() || !player.isAlive()) return false;
        if (honcho.distanceTo(player) < startDistance) return false;
        feeder = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return feeder != null && feeder.isAlive() && feeder.level() == honcho.level() && honcho.distanceTo(feeder) > stopDistance;
    }

    @Override
    public void stop() {
        feeder = null;
        honcho.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (feeder == null) return;
        honcho.getLookControl().setLookAt(feeder, 30.0f, 30.0f);
        honcho.getNavigation().moveTo(feeder, speedModifier);
    }
}

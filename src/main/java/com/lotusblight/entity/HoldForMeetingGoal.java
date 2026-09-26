package com.lotusblight.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Keeps Honcho in place and facing the player while the meeting scene plays (see HonchoMeetingManager). */
final class HoldForMeetingGoal extends Goal {
    private final HonchoEntity honcho;

    HoldForMeetingGoal(HonchoEntity honcho) {
        this.honcho = honcho;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return honcho.getMeetingPlayerId() != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        honcho.getNavigation().stop();
        if (!(honcho.level() instanceof ServerLevel level)) return;
        var player = level.getServer().getPlayerList().getPlayer(honcho.getMeetingPlayerId());
        if (player != null) {
            honcho.getLookControl().setLookAt(player, 30.0f, 30.0f);
        }
    }
}

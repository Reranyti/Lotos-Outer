package com.lotusblight.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Creak (the creature of the stairwell): a tall, lanky stone-and-rebar humanoid. For now only its model and its first reflex
 * exist - when someone looks at it, it covers its face with both hands (see {@link #isCovering()}); its stalking, its
 * attack and the rest of its behaviour are decided separately. The look of it (model, pose, walk) is drawn by
 * {@code CreakRenderer} from a mesh made by tools/creak.
 */
public class CreakEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> COVERING = SynchedEntityData.defineId(CreakEntity.class, EntityDataSerializers.BOOLEAN);
    private static final double SEEN_RANGE = 40.0;
    private static final double SEEN_COS = 0.93;

    /** How far the hands have come up to the face, 0..1, eased on the client (and kept here so the renderer can read last tick's too). */
    public float cover, coverO;

    public CreakEntity(EntityType<? extends CreakEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(COVERING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    public boolean isCovering() {
        return this.entityData.get(COVERING);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        coverO = cover;
        cover = Mth.approach(cover, isCovering() ? 1.0f : 0.0f, 0.12f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 4 == 0) {
            boolean seen = false;
            for (Player player : level().players()) {
                if (player.isSpectator() || player.distanceToSqr(this) > SEEN_RANGE * SEEN_RANGE) continue;
                if (lookedAtBy(player)) {
                    seen = true;
                    break;
                }
            }
            if (seen != isCovering()) this.entityData.set(COVERING, seen);
        }
    }

    /** Whether the player's gaze is on this creature: the view line passes close to its head and nothing is in the way. */
    private boolean lookedAtBy(Player player) {
        Vec3 view = player.getViewVector(1.0f).normalize();
        Vec3 toHead = this.getEyePosition().subtract(player.getEyePosition());
        double dist = toHead.length();
        if (dist < 0.5) return true;
        return view.dot(toHead.scale(1.0 / dist)) > SEEN_COS && player.hasLineOfSight(this);
    }
}

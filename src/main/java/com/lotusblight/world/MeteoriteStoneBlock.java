package com.lotusblight.world;

import com.lotusblight.data.LotusPlayerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "метеоритный камень убивает при прикосновении любом на войне, на нейтрале не даёт добыть" - left
 * behind at the exact center of a StarFall meteor impact, and spread further by
 * MeteoriteSpreadEngine. "На войне" there meant war with Star Light, i.e. the ALLIANCE branch (same
 * inversion as StarFall's scripts and the black hearts): it kills an ALLIANCE player on contact,
 * can't be mined at all on UNDECIDED, and is an ordinary minable block for the war-branch
 * (RESISTANCE) player, who's on Star Light's side. It used to kill RESISTANCE instead.
 */
public class MeteoriteStoneBlock extends Block {
    private static final float LETHAL_DAMAGE = 1000.0f;
    /** The impact drops this block right under the player it hit - a moment to step off before it kills. */
    public static final int IMPACT_GRACE_TICKS = 100;
    private static final java.util.Map<java.util.UUID, Long> GRACE_UNTIL = new java.util.HashMap<>();

    public MeteoriteStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * entityInside only fires for blocks whose cell overlaps the entity's box - for a full cube that
     * never happens, so the contact kill never went off. Standing on it and starting to hit it are
     * the two real ways to touch a solid block.
     */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof Player player) {
            killIfAlliance(level, player);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        killIfAlliance(level, player);
        super.attack(state, level, pos, player);
    }

    /** Called by the StarFall impact that placed the stone under this player. */
    public static void grantImpactGrace(ServerLevel level, Player player) {
        GRACE_UNTIL.put(player.getUUID(), level.getGameTime() + IMPACT_GRACE_TICKS);
    }

    private static void killIfAlliance(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (LotusPlayerState.getDialogueBranch(player) != LotusPlayerState.BRANCH_ALLIANCE) return;
        Long graceUntil = GRACE_UNTIL.get(player.getUUID());
        if (graceUntil != null) {
            if (serverLevel.getGameTime() < graceUntil) return;
            GRACE_UNTIL.remove(player.getUUID());
        }
        player.hurt(serverLevel.damageSources().magic(), LETHAL_DAMAGE);
    }

    /**
     * UNDECIDED (neutral) can't harvest it at all - matches getDestroyProgress(0) precedent elsewhere in this mod for "looks solid, isn't actually minable".
     * Mining progress is also computed on the client, where the player's persistent data is empty -
     * the synced ClientPlayerStateCache has to be read there, or everyone reads as UNDECIDED.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        int branch = player.level().isClientSide()
                ? com.lotusblight.map.ClientPlayerStateCache.dialogueBranch()
                : LotusPlayerState.getDialogueBranch(player);
        if (branch == LotusPlayerState.BRANCH_UNDECIDED) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }
}

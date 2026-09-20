package com.lotusblight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The indestructible anchor of one lotus outbreak — a single block position
 * (unlike the earlier version, which stacked 4 separate real blocks
 * vertically into a "pillar"; the anchor's OutbreakRecord/BlockPos never
 * changes, only the visual got simpler). Its GeckoLib crown model draws a
 * wide, flat "giant lily pad" that visually spans well beyond this one
 * block's footprint, so it reads as a real lily pad on the water surface
 * instead of a stem rising out of it.
 */
public final class LotusMainBlock extends Block implements EntityBlock {
    // Deliberately oversized past the normal 0-16 block bounds — same trick the old crown
    // collision box already used (-4..20) for the petals; here it's stretched much further so
    // the big pad reads as ~4 blocks wide. Kept low/thin so it stays walkable like a real pad.
    // The pad geometry is a square plus its 45-degree-rotated twin (see infected_lotus_crown.geo.json)
    // - the rotated diamond's corners reach further out than a plain square (±28.28 from center
    // vs ±24 for the old full-square model this shape used to match), so the old -16..32 outline
    // cut the star's diagonal tips off, making the selection/interaction hitbox look offset from
    // the visible model.
    // Height was 3 (matching only the flat pad cubes) - the raised center bud/petals in the geo
    // model actually reach up to y=10, well above that. Entities and vegetation could pass straight
    // through the visibly-solid bud, and things floating/standing on it (e.g. underwater kelp,
    // mobs) looked like they were clipping into or hovering off of the model - "не совпадение
    // структур плюс слиплость". Raised to fully contain the bud instead of just the pad's rim.
    private static final VoxelShape PAD_SHAPE = Block.box(-20, 0, -20, 36, 10, 36);

    public LotusMainBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        // The main anchor cannot be mined normally. Cleansing systems must remove it explicitly.
        if (!level.isClientSide()) return;
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public float getDestroyProgress(BlockState state, net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos) {
        return 0.0f;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PAD_SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // No baked blockstate model — LotusCrownBlockRenderer (GeckoLib) draws it instead.
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LotusCrownBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, blockEntity) -> {
            if (blockEntity instanceof LotusCrownBlockEntity crown && lvl instanceof ServerLevel serverLevel) {
                crown.serverTick(serverLevel);
            }
        };
    }
}

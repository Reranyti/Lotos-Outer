package com.lotusblight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The indestructible four-block-tall anchor of one lotus outbreak. Only the crown part (PART=3) carries a BlockEntity, for the GeckoLib bloom animation. */
public final class LotusMainBlock extends Block implements EntityBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    private static final VoxelShape STEM = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape CROWN = Block.box(-4, 3, -4, 20, 16, 20);

    public LotusMainBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (!level.getBlockState(pos).canBeReplaced(context)) return null;
        for (int i = 1; i < 4; i++) {
            if (!level.getBlockState(pos.above(i)).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(PART, 0);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || state.getValue(PART) != 0 || oldState.is(this)) return;
        for (int i = 1; i < 4; i++) {
            BlockPos above = pos.above(i);
            if (level.getBlockState(above).isAir()) {
                level.setBlock(above, defaultBlockState().setValue(PART, i), 3);
            }
        }
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
        return state.getValue(PART) == 3 ? CROWN : STEM;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // The crown's static blockstate model points at "builtin/entity" (no baked geometry) —
        // LotusCrownBlockRenderer draws it instead. Stem parts (0-2) keep their normal model.
        return state.getValue(PART) == 3 ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 3 ? new LotusCrownBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(PART) != 3) {
            return null;
        }
        return (lvl, pos, st, blockEntity) -> {
            if (blockEntity instanceof LotusCrownBlockEntity crown && lvl instanceof ServerLevel serverLevel) {
                crown.serverTick(serverLevel);
            }
        };
    }
}

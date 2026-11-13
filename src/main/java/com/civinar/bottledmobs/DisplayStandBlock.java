package com.civinar.bottledmobs;

import com.civinar.bottledmobs.advancement.BottledMobsAchievements;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A pedestal or wall bracket that grows a label for the jar directly above it.
 */
public class DisplayStandBlock extends BaseEntityBlock {

    public static final MapCodec<DisplayStandBlock> CODEC = simpleCodec(DisplayStandBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WALL = BooleanProperty.create("wall");

    private static final VoxelShape FLOOR_SHAPE =
            Shapes.box(2.0 / 16.0, 0.0, 2.0 / 16.0, 14.0 / 16.0, 1.0, 14.0 / 16.0);
    private static final VoxelShape WALL_SHAPE = Shapes.or(
            Shapes.box(2.0 / 16.0, 12.0 / 16.0, 2.0 / 16.0, 14.0 / 16.0, 1.0, 1.0),
            Shapes.box(3.0 / 16.0, 4.0 / 16.0, 13.0 / 16.0, 13.0 / 16.0, 12.0 / 16.0, 1.0));

    public DisplayStandBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WALL, false));
    }

    @Override
    protected MapCodec<DisplayStandBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WALL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) {
            return null;
        }
        boolean wall = face.getAxis().isHorizontal();
        Direction facing = wall ? face : context.getHorizontalDirection().getOpposite();
        BlockState state = this.defaultBlockState().setValue(WALL, wall).setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(WALL)) {
            Direction facing = state.getValue(FACING);
            BlockPos support = pos.relative(facing.getOpposite());
            return level.getBlockState(support).isFaceSturdy(level, support, facing);
        }
        BlockPos support = pos.below();
        return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(WALL) ? WALL_SHAPE : FLOOR_SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayStandBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (!level.isClientSide && placer instanceof ServerPlayer serverPlayer) {
            BlockPos above = pos.above();
            if (level.getBlockEntity(above) instanceof BottledMobBlockEntity jar && !jar.getMob().isEmpty()) {
                BottledMobsAchievements.onDisplay(serverPlayer, jar);
            }
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}

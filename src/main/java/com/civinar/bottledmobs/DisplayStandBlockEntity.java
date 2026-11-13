package com.civinar.bottledmobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker block entity. The renderer reads the jar block above it every frame,
 * so the label stays correct without storing duplicate display data.
 */
public class DisplayStandBlockEntity extends BlockEntity {

    public DisplayStandBlockEntity(BlockPos pos, BlockState state) {
        super(BottledMobs.DISPLAY_STAND_BE.get(), pos, state);
    }
}

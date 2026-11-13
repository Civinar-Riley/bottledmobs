package com.civinar.bottledmobs;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class JarShapes {

    public static final VoxelShape JAR = Shapes.box(
            2.0 / 16.0, 0.0, 2.0 / 16.0,
            14.0 / 16.0, 15.0 / 16.0, 14.0 / 16.0
    );

    private JarShapes() {
    }
}

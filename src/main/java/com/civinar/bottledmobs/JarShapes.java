package com.civinar.bottledmobs;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class JarShapes {

    public static final VoxelShape JAR = Shapes.box(
            1.0 / 16.0, 0.0, 1.0 / 16.0,
            15.0 / 16.0, 1.0, 15.0 / 16.0
    );

    private JarShapes() {
    }
}

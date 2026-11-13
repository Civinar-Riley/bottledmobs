package com.civinar.bottledmobs;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GameplayConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue MIN_SCALE;
    public static final ModConfigSpec.DoubleValue MAX_SCALE;
    public static final ModConfigSpec.DoubleValue SCALE_STEP;
    public static final ModConfigSpec.DoubleValue MIN_HEIGHT;
    public static final ModConfigSpec.DoubleValue MAX_HEIGHT;
    public static final ModConfigSpec.DoubleValue HEIGHT_STEP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("display");
        MIN_SCALE = builder
                .comment("Smallest scale a displayed mob can be set to.")
                .defineInRange("minScale", 0.1D, 0.01D, 16.0D);
        MAX_SCALE = builder
                .comment("Largest scale a displayed mob can be set to.")
                .defineInRange("maxScale", 3.0D, 0.01D, 16.0D);
        SCALE_STEP = builder
                .comment("Scale change per hoe or axe use.")
                .defineInRange("scaleStep", 0.1D, 0.001D, 1.0D);
        MIN_HEIGHT = builder
                .comment("Lowest vertical offset, in blocks.")
                .defineInRange("minHeight", 0.0D, 0.0D, 4.0D);
        MAX_HEIGHT = builder
                .comment("Highest vertical offset, in blocks.")
                .defineInRange("maxHeight", 1.0D, 0.0D, 4.0D);
        HEIGHT_STEP = builder
                .comment("Vertical offset change per pickaxe or shovel use, in blocks.")
                .defineInRange("heightStep", 1.0D / 16.0D, 0.001D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    public static float minScale() {
        return (float) Math.min(MIN_SCALE.get(), MAX_SCALE.get());
    }

    public static float maxScale() {
        return (float) Math.max(MIN_SCALE.get(), MAX_SCALE.get());
    }

    public static float scaleStep() {
        return SCALE_STEP.get().floatValue();
    }

    public static float minHeight() {
        return (float) Math.min(MIN_HEIGHT.get(), MAX_HEIGHT.get());
    }

    public static float maxHeight() {
        return (float) Math.max(MIN_HEIGHT.get(), MAX_HEIGHT.get());
    }

    public static float heightStep() {
        return HEIGHT_STEP.get().floatValue();
    }

    private GameplayConfig() {
    }
}

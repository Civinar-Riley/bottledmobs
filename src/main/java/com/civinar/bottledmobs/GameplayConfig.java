package com.civinar.bottledmobs;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class GameplayConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue DEFAULT_SCALE;
    public static final ModConfigSpec.DoubleValue MIN_SCALE;
    public static final ModConfigSpec.DoubleValue MAX_SCALE;
    public static final ModConfigSpec.DoubleValue SCALE_STEP;
    public static final ModConfigSpec.DoubleValue MIN_HEIGHT;
    public static final ModConfigSpec.DoubleValue MAX_HEIGHT;
    public static final ModConfigSpec.DoubleValue HEIGHT_STEP;
    public static final ModConfigSpec.BooleanValue REDSTONE_RELEASE;
    public static final ModConfigSpec.BooleanValue CAPTURE_PASSENGER_STACKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("display");
        DEFAULT_SCALE = builder
                .translation("bottledmobs.configuration.display.defaultScale")
                .comment("Initial scale of a mob when it is bottled, before any adjustment.")
                .defineInRange("defaultScale", 0.3D, 0.01D, 16.0D);
        MIN_SCALE = builder
                .translation("bottledmobs.configuration.display.minScale")
                .comment("Smallest scale a displayed mob can be set to.")
                .defineInRange("minScale", 0.1D, 0.01D, 16.0D);
        MAX_SCALE = builder
                .translation("bottledmobs.configuration.display.maxScale")
                .comment("Largest scale a displayed mob can be set to.")
                .defineInRange("maxScale", 3.0D, 0.01D, 16.0D);
        SCALE_STEP = builder
                .translation("bottledmobs.configuration.display.scaleStep")
                .comment("Scale change per hoe or axe use.")
                .defineInRange("scaleStep", 0.1D, 0.001D, 1.0D);
        MIN_HEIGHT = builder
                .translation("bottledmobs.configuration.display.minHeight")
                .comment("Lowest vertical offset, in blocks.")
                .defineInRange("minHeight", 0.0D, 0.0D, 4.0D);
        MAX_HEIGHT = builder
                .translation("bottledmobs.configuration.display.maxHeight")
                .comment("Highest vertical offset, in blocks.")
                .defineInRange("maxHeight", 1.0D, 0.0D, 4.0D);
        HEIGHT_STEP = builder
                .translation("bottledmobs.configuration.display.heightStep")
                .comment("Vertical offset change per pickaxe or shovel use, in blocks.")
                .defineInRange("heightStep", 1.0D / 16.0D, 0.001D, 1.0D);
        builder.pop();

        builder.push("interaction");
        REDSTONE_RELEASE = builder
                .translation("bottledmobs.configuration.interaction.redstoneRelease")
                .comment("Release the captured mob when a redstone signal is applied.")
                .define("redstoneRelease", false);
        CAPTURE_PASSENGER_STACKS = builder
                .translation("bottledmobs.configuration.interaction.capturePassengerStacks")
                .comment("Capture a mob together with the creatures riding it.")
                .define("capturePassengerStacks", true);
        builder.pop();
        SPEC = builder.build();
    }

    /** Initial display scale for a freshly bottled mob, clamped to the min/max bounds. */
    public static float defaultScale() {
        float value = DEFAULT_SCALE.get().floatValue();
        return Math.min(Math.max(value, minScale()), maxScale());
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

    public static boolean redstoneRelease() {
        return REDSTONE_RELEASE.get();
    }

    public static boolean capturePassengerStacks() {
        return CAPTURE_PASSENGER_STACKS.get();
    }

    private GameplayConfig() {
    }
}

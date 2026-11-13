package com.civinar.bottledmobs;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ANIMATE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ANIMATE = builder
                .comment("Spin captured mobs and tick their models while displayed.")
                .define("animateCapturedMobs", true);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}

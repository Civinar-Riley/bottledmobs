package com.civinar.bottledmobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Display settings for a jar. An empty shell means the default jar appearance;
 * minecraft:air means no shell at all.
 */
public record JarSettings(Optional<ResourceLocation> shell, float scale, float height, Direction facing) {

    public static final Codec<JarSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("shell").forGetter(JarSettings::shell),
            Codec.FLOAT.fieldOf("scale").forGetter(JarSettings::scale),
            Codec.FLOAT.fieldOf("height").forGetter(JarSettings::height),
            Codec.INT.optionalFieldOf("facing", Direction.NORTH.get3DDataValue()).forGetter(JarSettings::facingValue)
    ).apply(instance, (shell, scale, height, facing) ->
            new JarSettings(shell, scale, height, Direction.from3DDataValue(facing))));

    public static final StreamCodec<ByteBuf, JarSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), JarSettings::shell,
            ByteBufCodecs.FLOAT, JarSettings::scale,
            ByteBufCodecs.FLOAT, JarSettings::height,
            ByteBufCodecs.VAR_INT, JarSettings::facingValue,
            (shell, scale, height, facing) ->
                    new JarSettings(shell, scale, height, Direction.from3DDataValue(facing))
    );

    public JarSettings {
        shell = shell == null ? Optional.empty() : shell;
        scale = Float.isFinite(scale) ? scale : 0.35F;
        height = Float.isFinite(height) ? height : 0.0F;
        facing = facing == null || !facing.getAxis().isHorizontal() ? Direction.NORTH : facing;
    }

    public int facingValue() {
        return this.facing.get3DDataValue();
    }
}

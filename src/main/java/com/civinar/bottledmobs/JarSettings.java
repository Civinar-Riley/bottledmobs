package com.civinar.bottledmobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Display settings copied between jars with a book, or attached to a written book.
 * An empty shell means "use the default jar appearance"; minecraft:air means "no shell".
 */
public record JarSettings(Optional<ResourceLocation> shell, float scale, float height) {

    public static final Codec<JarSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("shell").forGetter(JarSettings::shell),
            Codec.FLOAT.fieldOf("scale").forGetter(JarSettings::scale),
            Codec.FLOAT.fieldOf("height").forGetter(JarSettings::height)
    ).apply(instance, JarSettings::new));

    public static final StreamCodec<ByteBuf, JarSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), JarSettings::shell,
            ByteBufCodecs.FLOAT, JarSettings::scale,
            ByteBufCodecs.FLOAT, JarSettings::height,
            JarSettings::new
    );

    public JarSettings {
        shell = shell == null ? Optional.empty() : shell;
        scale = Float.isFinite(scale) ? scale : 0.35F;
        height = Float.isFinite(height) ? height : 0.0F;
    }
}

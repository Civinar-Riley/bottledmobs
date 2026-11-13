package com.civinar.bottledmobs;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sends only the display settings after placement. The captured mob itself is
 * sent once with the block entity's initial data, so resizing a jar never
 * resends the full entity NBT.
 */
public record JarSettingsPayload(BlockPos pos, JarSettings settings) implements CustomPacketPayload {

    public static final Type<JarSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BottledMobs.MOD_ID, "jar_settings"));

    public static final StreamCodec<ByteBuf, JarSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, JarSettingsPayload::pos,
            JarSettings.STREAM_CODEC, JarSettingsPayload::settings,
            JarSettingsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

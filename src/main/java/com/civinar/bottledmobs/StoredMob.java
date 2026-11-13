package com.civinar.bottledmobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * The captured mob carried by a bottle item.
 */
public record StoredMob(CompoundTag entityData, String entityId, String customName, String fallbackName) {

    public static final StoredMob EMPTY = new StoredMob(new CompoundTag(), "", "", "");

    public static final Codec<StoredMob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CompoundTag.CODEC.fieldOf("entity").forGetter(StoredMob::entityData),
            Codec.STRING.optionalFieldOf("entity_id", "").forGetter(StoredMob::entityId),
            Codec.STRING.optionalFieldOf("custom_name", "").forGetter(StoredMob::customName),
            Codec.STRING.optionalFieldOf("fallback_name", "").forGetter(StoredMob::fallbackName)
    ).apply(instance, StoredMob::new));

    public static final StreamCodec<ByteBuf, StoredMob> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, StoredMob::entityData,
            ByteBufCodecs.STRING_UTF8, StoredMob::entityId,
            ByteBufCodecs.STRING_UTF8, StoredMob::customName,
            ByteBufCodecs.STRING_UTF8, StoredMob::fallbackName,
            StoredMob::new
    );

    public StoredMob {
        entityData = entityData == null ? new CompoundTag() : entityData.copy();
        entityId = entityId == null ? "" : entityId;
        customName = customName == null ? "" : customName;
        fallbackName = fallbackName == null ? "" : fallbackName;
    }

    @Override
    public CompoundTag entityData() {
        return this.entityData.copy();
    }

    public boolean isEmpty() {
        return this.entityData.isEmpty();
    }

    /**
     * Resolves the name against the client's current language instead of freezing
     * the name that was visible when the mob was captured.
     */
    public Component displayName() {
        if (!this.customName.isEmpty()) {
            return Component.literal(this.customName);
        }
        Optional<EntityType<?>> type = EntityType.by(this.entityData);
        if (type.isPresent()) {
            return Component.translatable(type.get().getDescriptionId());
        }
        if (!this.fallbackName.isEmpty()) {
            return Component.literal(this.fallbackName);
        }
        return Component.translatable("tooltip.bottledmobs.unknown");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("entity", this.entityData.copy());
        tag.putString("entity_id", this.entityId);
        tag.putString("custom_name", this.customName);
        tag.putString("fallback_name", this.fallbackName);
        return tag;
    }

    public static StoredMob load(CompoundTag tag) {
        if (tag == null || !tag.contains("entity")) {
            return EMPTY;
        }
        return new StoredMob(
                tag.getCompound("entity"),
                tag.getString("entity_id"),
                tag.getString("custom_name"),
                tag.getString("fallback_name")
        );
    }
}

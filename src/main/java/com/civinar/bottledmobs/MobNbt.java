package com.civinar.bottledmobs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

public final class MobNbt {

    private MobNbt() {
    }

    @Nullable
    public static Entity create(CompoundTag tag, Level level) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        Optional<EntityType<?>> type = EntityType.by(tag);
        if (type.isEmpty()) {
            return null;
        }
        Entity entity = type.get().create(level);
        if (entity == null) {
            return null;
        }
        try {
            entity.load(tag);
        } catch (Exception exception) {
            BottledMobs.LOGGER.warn("Could not restore a captured mob from its stored data", exception);
            return null;
        }
        return entity;
    }

    public static CompoundTag save(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.save(tag);
        return tag;
    }
}

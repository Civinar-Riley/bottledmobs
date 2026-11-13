package com.civinar.bottledmobs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public final class MobNbt {

    private MobNbt() {
    }

    @Nullable
    public static Entity create(CompoundTag tag, Level level) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        if (EntityType.by(tag).isEmpty()) {
            return null;
        }
        try {
            // loadEntityRecursive restores the root together with every passenger,
            // wiring each rider onto its vehicle so the group displays and releases intact.
            return EntityType.loadEntityRecursive(tag, level, entity -> entity);
        } catch (Exception exception) {
            BottledMobs.LOGGER.warn("Could not restore a captured mob from its stored data", exception);
            return null;
        }
    }

    /**
     * Restores only the root creature, ignoring any stored passengers. Used for
     * read-only inspection (comparator signal) so mounting side effects do not fire.
     */
    @Nullable
    public static Entity createSingle(CompoundTag tag, Level level) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        try {
            return EntityType.create(tag, level).orElse(null);
        } catch (Exception exception) {
            BottledMobs.LOGGER.warn("Could not restore a captured mob from its stored data", exception);
            return null;
        }
    }

    public static CompoundTag save(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.save(tag);
        return tag;
    }
}

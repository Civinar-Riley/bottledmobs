package com.civinar.bottledmobs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public final class MobNbt {

    private MobNbt() {
    }

    /**
     * Spawn positions tried by {@link #findFreeSpot}, relative to the preferred spot:
     * the spot itself, the two blocks above it, then the eight surrounding columns
     * at both heights.
     */
    private static final List<Vec3> SPAWN_OFFSETS = List.of(
            Vec3.ZERO,
            new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 2.0D, 0.0D),
            new Vec3(1.0D, 0.0D, 0.0D), new Vec3(-1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 0.0D, -1.0D),
            new Vec3(1.0D, 0.0D, 1.0D), new Vec3(1.0D, 0.0D, -1.0D),
            new Vec3(-1.0D, 0.0D, 1.0D), new Vec3(-1.0D, 0.0D, -1.0D),
            new Vec3(1.0D, 1.0D, 0.0D), new Vec3(-1.0D, 1.0D, 0.0D),
            new Vec3(0.0D, 1.0D, 1.0D), new Vec3(0.0D, 1.0D, -1.0D),
            new Vec3(1.0D, 1.0D, 1.0D), new Vec3(1.0D, 1.0D, -1.0D),
            new Vec3(-1.0D, 1.0D, 1.0D), new Vec3(-1.0D, 1.0D, -1.0D));

    /**
     * Moves {@code entity} to the first position around {@code preferred} whose
     * bounding box does not collide with blocks, so a mob is never released inside
     * a wall. When every candidate is blocked the entity is left wherever the last
     * attempt put it and the caller should keep the mob bottled.
     *
     * @return true when a free position was found and the entity moved to it
     */
    public static boolean findFreeSpot(ServerLevel level, Entity entity, Vec3 preferred, float yaw) {
        for (Vec3 offset : SPAWN_OFFSETS) {
            entity.absMoveTo(preferred.x + offset.x, preferred.y + offset.y,
                    preferred.z + offset.z, yaw, 0.0F);
            if (level.noCollision(entity, entity.getBoundingBox())) {
                return true;
            }
        }
        return false;
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

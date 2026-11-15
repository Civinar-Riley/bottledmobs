package com.civinar.bottledmobs;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
     * Moves the whole group to the first loaded, unobstructed position around
     * {@code preferred}. If every candidate is blocked, keep the mob bottled.
     */
    public static boolean findFreeSpot(ServerLevel level, Entity entity, Vec3 preferred, float yaw) {
        List<Entity> members = entity.getSelfAndPassengers().toList();
        for (Vec3 offset : SPAWN_OFFSETS) {
            entity.absMoveTo(preferred.x + offset.x, preferred.y + offset.y,
                    preferred.z + offset.z, yaw, 0.0F);
            positionPassengers(entity);
            if (members.stream().allMatch(member -> hasSpace(level, member))) {
                return true;
            }
        }
        return false;
    }

    private static void positionPassengers(Entity entity) {
        entity.setOldPosAndRot();
        for (Entity passenger : entity.getPassengers()) {
            entity.positionRider(passenger);
            positionPassengers(passenger);
        }
    }

    private static boolean hasSpace(ServerLevel level, Entity entity) {
        AABB box = entity.getBoundingBox();
        return box.minY >= level.getMinBuildHeight() && box.maxY <= level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(box)
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && level.noCollision(entity, box);
    }

    /**
     * Adds every member or removes the members already added. The caller must
     * only clear its stored data after this succeeds; join events can reject riders.
     */
    public static boolean addToLevel(ServerLevel level, Entity entity) {
        List<Entity> members = entity.getSelfAndPassengers().toList();
        List<Entity> added = new ArrayList<>();
        boolean success = false;
        try {
            for (Entity member : members) {
                if (!level.addFreshEntity(member)) {
                    return false;
                }
                added.add(member);
            }
            success = true;
            return true;
        } finally {
            if (!success) {
                for (int index = added.size() - 1; index >= 0; index--) {
                    added.get(index).discard();
                }
            }
        }
    }

    @Nullable
    public static Entity create(CompoundTag tag, Level level) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        try {
            Entity entity = EntityType.loadEntityRecursive(tag, level, member -> member);
            // Vanilla skips missing types or rejected mounts. Never release only
            // part of a stored group and then erase the passengers' data.
            if (entity != null && entity.getSelfAndPassengers().count()
                    == MobInfo.selfAndPassengers(tag).size()) {
                return entity;
            }
            BottledMobs.LOGGER.warn("Could not restore every member of a captured group");
        } catch (Exception exception) {
            BottledMobs.LOGGER.warn("Could not restore a captured mob from its stored data", exception);
        }
        return null;
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

    @Nullable
    public static CompoundTag save(Entity entity) {
        try {
            CompoundTag tag = new CompoundTag();
            if (entity.save(tag) && MobInfo.selfAndPassengers(tag).size()
                    == entity.getSelfAndPassengers().count()) {
                return tag;
            }
            BottledMobs.LOGGER.warn("Could not save every member of the captured group {}", entity.getType());
        } catch (Exception exception) {
            BottledMobs.LOGGER.warn("Could not save a captured mob", exception);
        }
        return null;
    }
}

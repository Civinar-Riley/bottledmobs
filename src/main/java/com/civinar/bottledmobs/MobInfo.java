package com.civinar.bottledmobs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Small shared helpers for describing the creature stored in a jar.
 */
public final class MobInfo {

    private MobInfo() {
    }

    public static Component speciesName(StoredMob mob) {
        return speciesName(mob.dataView()).orElseGet(() -> mob.fallbackName().isEmpty()
                ? Component.translatable("tooltip.bottledmobs.unknown")
                : Component.literal(mob.fallbackName()));
    }

    public static Optional<Component> speciesName(CompoundTag data) {
        return EntityType.by(data).map(type -> Component.translatable(type.getDescriptionId()));
    }

    public static Optional<Component> variantName(StoredMob mob) {
        return VariantRules.resolve(mob.dataView());
    }

    public static boolean isBaby(StoredMob mob) {
        return isBaby(mob.dataView());
    }

    public static boolean isBaby(CompoundTag data) {
        if (data.contains("IsBaby")) {
            return data.getBoolean("IsBaby");
        }
        return data.contains("Age") && data.getInt("Age") < 0;
    }

    public static boolean isAquatic(StoredMob mob) {
        return EntityType.by(mob.dataView())
                .map(type -> type.is(BottledMobs.AQUATIC_DISPLAY))
                .orElse(false);
    }

    /**
     * Registered bounding box width of the stored creature, used to size the jar
     * renderer's culling box without instantiating the entity.
     */
    public static float displayWidth(StoredMob mob) {
        return EntityType.by(mob.dataView()).map(EntityType::getWidth).orElse(1.0F);
    }

    /**
     * Registered bounding box height of the stored creature.
     */
    public static float displayHeight(StoredMob mob) {
        return EntityType.by(mob.dataView()).map(EntityType::getHeight).orElse(2.0F);
    }

    /**
     * Root first, then passengers depth-first. Group capture and group display
     * rules use this without needing a live entity.
     */
    public static List<CompoundTag> selfAndPassengers(CompoundTag data) {
        List<CompoundTag> result = new ArrayList<>();
        collect(data, result);
        return result;
    }

    /**
     * Number of creatures stored in the jar, counting the root and every rider.
     */
    public static int groupSize(StoredMob mob) {
        return selfAndPassengers(mob.dataView()).size();
    }

    /**
     * Whether the stored group contains more than the root creature.
     */
    public static boolean isGroup(StoredMob mob) {
        return groupSize(mob) > 1;
    }

    /**
     * Whether any member of the stored group carries the given entity type tag.
     * Used for the server-side {@code uncapturable} gate.
     */
    public static boolean hasTag(StoredMob mob, TagKey<EntityType<?>> tag) {
        for (CompoundTag part : selfAndPassengers(mob.dataView())) {
            Optional<EntityType<?>> type = EntityType.by(part);
            if (type.isPresent() && type.get().is(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any creature in a live group (root and riders) carries the given
     * entity type tag. Used by the capture gate before the group is serialised.
     */
    public static boolean hasTag(Entity root, TagKey<EntityType<?>> tag) {
        for (Entity part : root.getSelfAndPassengers().toList()) {
            if (part.getType().is(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Display name for a stored group. The root keeps its own name (custom name or
     * species), then each rider is appended after a separator. Chicken jockeys and
     * spider jockeys use a dedicated name instead of the generic composition.
     */
    public static Component groupName(StoredMob mob) {
        Optional<Component> special = jockeyName(mob);
        if (special.isPresent()) {
            return special.get();
        }
        List<CompoundTag> parts = selfAndPassengers(mob.dataView());
        MutableComponent name = rootName(mob, parts.get(0));
        for (int index = 1; index < parts.size(); index++) {
            name = name.append(Component.translatable("tooltip.bottledmobs.group_separator"))
                    .append(speciesName(parts.get(index))
                            .orElseGet(() -> Component.translatable("tooltip.bottledmobs.unknown")));
        }
        return name;
    }

    private static MutableComponent rootName(StoredMob mob, CompoundTag root) {
        if (!mob.customName().isEmpty()) {
            return Component.literal(mob.customName());
        }
        return speciesName(root).map(Component::copy).orElseGet(() -> {
            String fallback = mob.fallbackName();
            return fallback.isEmpty()
                    ? Component.translatable("tooltip.bottledmobs.unknown")
                    : Component.literal(fallback);
        });
    }

    private static Optional<Component> jockeyName(StoredMob mob) {
        List<CompoundTag> parts = selfAndPassengers(mob.dataView());
        if (parts.size() != 2) {
            return Optional.empty();
        }
        Optional<EntityType<?>> root = EntityType.by(parts.get(0));
        Optional<EntityType<?>> rider = EntityType.by(parts.get(1));
        if (root.isEmpty() || rider.isEmpty()) {
            return Optional.empty();
        }
        EntityType<?> rootType = root.get();
        EntityType<?> riderType = rider.get();
        if (rootType == EntityType.CHICKEN
                && (riderType == EntityType.ZOMBIE
                        || riderType == EntityType.HUSK
                        || riderType == EntityType.DROWNED)) {
            return Optional.of(Component.translatable("tooltip.bottledmobs.chicken_jockey"));
        }
        if (rootType == EntityType.SPIDER
                && (riderType == EntityType.SKELETON
                        || riderType == EntityType.STRAY)) {
            return Optional.of(Component.translatable("tooltip.bottledmobs.spider_jockey"));
        }
        return Optional.empty();
    }

    private static void collect(CompoundTag data, List<CompoundTag> out) {
        out.add(data);
        ListTag passengers = data.getList("Passengers", Tag.TAG_COMPOUND);
        for (int index = 0; index < passengers.size(); index++) {
            collect(passengers.getCompound(index), out);
        }
    }
}

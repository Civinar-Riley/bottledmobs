package com.civinar.bottledmobs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
        return speciesName(mob.entityData()).orElseGet(() -> mob.fallbackName().isEmpty()
                ? Component.translatable("tooltip.bottledmobs.unknown")
                : Component.literal(mob.fallbackName()));
    }

    public static Optional<Component> speciesName(CompoundTag data) {
        return EntityType.by(data).map(type -> Component.translatable(type.getDescriptionId()));
    }

    public static Optional<Component> variantName(StoredMob mob) {
        return VariantRules.resolve(mob.entityData());
    }

    public static boolean isBaby(StoredMob mob) {
        return isBaby(mob.entityData());
    }

    public static boolean isBaby(CompoundTag data) {
        if (data.contains("IsBaby")) {
            return data.getBoolean("IsBaby");
        }
        return data.contains("Age") && data.getInt("Age") < 0;
    }

    public static boolean isAquatic(StoredMob mob) {
        return EntityType.by(mob.entityData())
                .map(type -> type.is(BottledMobs.AQUATIC_DISPLAY))
                .orElse(false);
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

    private static void collect(CompoundTag data, List<CompoundTag> out) {
        out.add(data);
        ListTag passengers = data.getList("Passengers", Tag.TAG_COMPOUND);
        for (int index = 0; index < passengers.size(); index++) {
            collect(passengers.getCompound(index), out);
        }
    }
}

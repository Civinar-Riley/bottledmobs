package com.civinar.bottledmobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data-driven block light for displayed mobs. Values are read from
 * {@code data/<namespace>/bottledmobs/glow/*.json} on both sides.
 */
public final class GlowRules {

    private static volatile Map<ResourceLocation, Integer> rules = Map.of();

    private GlowRules() {
    }

    /**
     * Reads the built-in glow map from the mod jar. The client's resource
     * manager only exposes assets, so this cannot use a data reload listener.
     */
    public static void loadBuiltin() {
        try (InputStream stream = GlowRules.class.getResourceAsStream(
                "/data/bottledmobs/bottledmobs/glow/glow.json")) {
            if (stream == null) {
                BottledMobs.LOGGER.warn("Missing built-in glow rules");
                return;
            }
            JsonElement root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                BottledMobs.LOGGER.warn("Invalid built-in glow rules");
                return;
            }
            Map<ResourceLocation, Integer> loaded = new HashMap<>();
            parseObject(root.getAsJsonObject(), loaded);
            rules = Map.copyOf(loaded);
            BottledMobs.LOGGER.info("Loaded {} glow rules", rules.size());
        } catch (IOException | RuntimeException exception) {
            BottledMobs.LOGGER.error("Could not load built-in glow rules", exception);
        }
    }

    public static void apply(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, Integer> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            JsonElement root = entry.getValue();
            if (root == null || !root.isJsonObject()) {
                BottledMobs.LOGGER.warn("Glow rule file {} must contain a JSON object", entry.getKey());
                continue;
            }
            parseObject(root.getAsJsonObject(), loaded);
        }
        rules = Map.copyOf(loaded);
        BottledMobs.LOGGER.info("Loaded {} glow rules", rules.size());
    }

    private static void parseObject(JsonObject object, Map<ResourceLocation, Integer> loaded) {
        for (Map.Entry<String, JsonElement> rule : object.entrySet()) {
            ResourceLocation entity = ResourceLocation.tryParse(rule.getKey());
            JsonElement value = rule.getValue();
            if (entity == null || value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                BottledMobs.LOGGER.warn("Skipping invalid glow rule {}", rule.getKey());
                continue;
            }
            loaded.put(entity, Mth.clamp(value.getAsInt(), 0, 15));
        }
    }

    public static int lightFor(CompoundTag data) {
        int light = 0;
        for (CompoundTag part : MobInfo.selfAndPassengers(data)) {
            Optional<EntityType<?>> type = EntityType.by(part);
            if (type.isPresent()) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type.get());
                light = Math.max(light, rules.getOrDefault(id, 0));
            }
        }
        return Mth.clamp(light, 0, 15);
    }
}

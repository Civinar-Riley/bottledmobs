package com.civinar.bottledmobs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data-driven display variants. Each rule matches an NBT path/value on a
 * captured entity and supplies a translation key for the label.
 */
public final class VariantRules {

    private static volatile Map<ResourceLocation, List<Rule>> rules = Map.of();

    private VariantRules() {
    }

    /**
     * Loads the rules shipped in the mod jar. The client resource manager only
     * exposes assets, so the built-in data rules are read from the classpath
     * instead of a resource-pack reload listener.
     */
    public static void loadBuiltin() {
        String directory = "/data/bottledmobs/bottledmobs/variants/";
        try (InputStream indexStream = VariantRules.class.getResourceAsStream(directory + "index.json")) {
            if (indexStream == null) {
                BottledMobs.LOGGER.warn("Missing built-in variant rule index");
                return;
            }
            JsonElement index = JsonParser.parseReader(
                    new InputStreamReader(indexStream, StandardCharsets.UTF_8));
            JsonElement filesElement = index.isJsonObject() ? index.getAsJsonObject().get("files") : null;
            if (filesElement == null || !filesElement.isJsonArray()) {
                BottledMobs.LOGGER.warn("Invalid built-in variant rule index");
                return;
            }
            Map<ResourceLocation, JsonElement> files = new HashMap<>();
            for (JsonElement element : filesElement.getAsJsonArray()) {
                String file = element.getAsString();
                try (InputStream stream = VariantRules.class.getResourceAsStream(directory + file)) {
                    if (stream == null) {
                        BottledMobs.LOGGER.warn("Missing built-in variant rule file {}", file);
                        continue;
                    }
                    files.put(ResourceLocation.fromNamespaceAndPath(BottledMobs.MOD_ID, file),
                            JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                }
            }
            apply(files);
        } catch (IOException | RuntimeException exception) {
            BottledMobs.LOGGER.error("Could not load built-in variant rules", exception);
        }
    }

    public static void apply(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, List<Rule>> loaded = new HashMap<>();
        int count = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            JsonElement root = entry.getValue();
            if (root == null || !root.isJsonArray()) {
                BottledMobs.LOGGER.warn("Variant rule file {} must contain a JSON array", entry.getKey());
                continue;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                try {
                    Rule rule = parse(element);
                    loaded.computeIfAbsent(rule.entity(), key -> new ArrayList<>()).add(rule);
                    count++;
                } catch (RuntimeException exception) {
                    BottledMobs.LOGGER.warn("Skipping invalid variant rule in {}", entry.getKey(), exception);
                }
            }
        }
        Map<ResourceLocation, List<Rule>> frozen = new HashMap<>();
        loaded.forEach((entity, list) -> frozen.put(entity, List.copyOf(list)));
        rules = Map.copyOf(frozen);
        BottledMobs.LOGGER.info("Loaded {} variant rules", count);
    }

    public static Optional<Component> resolve(CompoundTag data) {
        Optional<EntityType<?>> type = EntityType.by(data);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        List<Rule> list = rules.get(BuiltInRegistries.ENTITY_TYPE.getKey(type.get()));
        if (list == null) {
            return Optional.empty();
        }
        for (Rule rule : list) {
            Tag tag = getPath(data, rule.path());
            if (tag != null && rule.matches(tag)) {
                return Optional.of(Component.translatable(rule.translation()));
            }
        }
        return Optional.empty();
    }

    private static Rule parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Rule must be a JSON object");
        }
        JsonObject object = element.getAsJsonObject();
        ResourceLocation entity = ResourceLocation.tryParse(requiredString(object, "entity"));
        if (entity == null) {
            throw new IllegalArgumentException("Invalid entity id");
        }
        String path = requiredString(object, "path");
        String translation = requiredString(object, "name");
        JsonElement value = object.get("value");
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing primitive value");
        }
        return new Rule(entity, path, value.getAsJsonPrimitive(), translation);
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing string field " + key);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank field " + key);
        }
        return value;
    }

    private static Tag getPath(CompoundTag data, String path) {
        Tag current = data;
        for (String part : path.split("\\.")) {
            if (!(current instanceof CompoundTag compound)) {
                return null;
            }
            current = compound.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private record Rule(ResourceLocation entity, String path, JsonPrimitive value, String translation) {

        boolean matches(Tag tag) {
            if (this.value.isString()) {
                return tag instanceof StringTag string
                        && sameId(this.value.getAsString(), string.getAsString());
            }
            if (this.value.isNumber()) {
                return tag instanceof NumericTag number
                        && number.getAsDouble() == this.value.getAsDouble();
            }
            if (this.value.isBoolean()) {
                return tag instanceof NumericTag number
                        && (number.getAsInt() != 0) == this.value.getAsBoolean();
            }
            return false;
        }
    }

    private static boolean sameId(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        return stripNamespace(expected).equals(stripNamespace(actual));
    }

    private static String stripNamespace(String value) {
        int separator = value.indexOf(':');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }
}

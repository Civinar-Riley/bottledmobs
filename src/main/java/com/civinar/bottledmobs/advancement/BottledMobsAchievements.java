package com.civinar.bottledmobs.advancement;

import com.civinar.bottledmobs.BottledMobs;
import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.GlowRules;
import com.civinar.bottledmobs.MobInfo;
import com.civinar.bottledmobs.StoredMob;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side achievement plumbing. The trigger is registered once; each milestone
 * is a string action that the relevant gameplay code fires. The per-player set of
 * distinct captured species lives in a copy-on-death attachment so it survives
 * respawns.
 */
public final class BottledMobsAchievements {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BottledMobs.MOD_ID);

    private static final Codec<Set<ResourceLocation>> SPECIES_CODEC =
            ResourceLocation.CODEC.listOf().xmap(LinkedHashSet::new, List::copyOf);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Set<ResourceLocation>>> SPECIES =
            ATTACHMENTS.register("captured_species", () ->
                    AttachmentType.<Set<ResourceLocation>>builder(() -> new LinkedHashSet<>())
                            .serialize(SPECIES_CODEC)
                            .copyOnDeath()
                            .build());

    private static final int TEN_SPECIES = 10;
    private static final float MINIATURE_SCALE = 0.15F;
    private static final float GIANT_SCALE = 2.5F;

    private BottledMobsAchievements() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    /**
     * Called when a creature (optionally with its riders) is bottled. Records the
     * species and fires the capture / group / ten-species milestones.
     */
    public static void onCapture(ServerPlayer player, EntityType<?> type, boolean group) {
        Set<ResourceLocation> species = player.getData(SPECIES.get());
        species.add(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        fire(player, "capture");
        if (group) {
            fire(player, "group_capture");
        }
        if (species.size() >= TEN_SPECIES) {
            fire(player, "ten_species");
        }
    }

    /**
     * Called after any display-affecting interaction with a placed jar (scale,
     * figure mode, re-insertion, placement). Fires the matching milestones for the
     * jar's current state.
     */
    public static void onDisplay(ServerPlayer player, BottledMobBlockEntity jar) {
        StoredMob mob = jar.getMob();
        if (mob.isEmpty()) {
            return;
        }
        float scale = jar.getScale();
        if (scale <= MINIATURE_SCALE) {
            fire(player, "miniature");
        }
        if (scale >= GIANT_SCALE) {
            fire(player, "giant");
        }
        if (jar.isFigureMode()) {
            fire(player, "figure");
        }
        if (MobInfo.isAquatic(mob)) {
            fire(player, "aquarium");
        }
        if (GlowRules.lightFor(mob.entityData()) > 0) {
            fire(player, "night_light");
        }
        if (jar.isOnDisplayStand()) {
            fire(player, "curator");
        }
    }

    private static void fire(ServerPlayer player, String action) {
        BottledMobsTrigger.INSTANCE.trigger(player, action);
    }
}

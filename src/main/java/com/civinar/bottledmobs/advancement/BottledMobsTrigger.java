package com.civinar.bottledmobs.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.CriterionValidator;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * A single generic criterion trigger shared by every achievement in the mod. Each
 * achievement carries an {@code action} string that names the milestone it listens
 * for, so one trigger covers all nine without a codec per achievement.
 */
public class BottledMobsTrigger extends SimpleCriterionTrigger<BottledMobsTrigger.TriggerInstance> {

    public static final BottledMobsTrigger INSTANCE = new BottledMobsTrigger();

    private BottledMobsTrigger() {
    }

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String action) {
        this.trigger(player, instance -> instance.matches(action));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, String action)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.fieldOf("action").forGetter(TriggerInstance::action)
        ).apply(instance, TriggerInstance::new));

        public boolean matches(String action) {
            return this.action.equals(action);
        }

        @Override
        public void validate(CriterionValidator validator) {
        }
    }
}

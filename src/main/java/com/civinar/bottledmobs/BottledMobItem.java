package com.civinar.bottledmobs;

import com.civinar.bottledmobs.advancement.BottledMobsAchievements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

public class BottledMobItem extends BlockItem {

    public BottledMobItem(Block block, Item.Properties properties) {
        super(block, properties
                .stacksTo(1)
                .component(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        StoredMob mob = stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
        Level level = context.getLevel();

        if (!mob.isEmpty() && !player.isShiftKeyDown()) {
            if (level instanceof ServerLevel serverLevel) {
                Entity entity = MobNbt.create(mob.entityData(), serverLevel);
                if (entity == null) {
                    player.displayClientMessage(
                            Component.translatable("message.bottledmobs.release_failed"), true);
                } else {
                    Vec3 spot = context.getClickLocation();
                    if (!MobNbt.findFreeSpot(serverLevel, entity, spot, player.getYRot())) {
                        player.displayClientMessage(
                                Component.translatable("message.bottledmobs.no_space"), true);
                    } else if (!MobNbt.addToLevel(serverLevel, entity)) {
                        player.displayClientMessage(
                                Component.translatable("message.bottledmobs.release_failed"), true);
                    } else {
                        stack.set(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
                        serverLevel.playSound(null, spot.x, spot.y, spot.z,
                                SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
                        serverLevel.sendParticles(ParticleTypes.POOF,
                                spot.x, spot.y + 0.5, spot.z,
                                8, 0.25, 0.5, 0.25, 0.01);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (target instanceof Player) {
            return InteractionResult.PASS;
        }
        if (capture(player, target, hand)) {
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (entity instanceof Player) {
            return false;
        }
        return capture(player, entity, InteractionHand.MAIN_HAND);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if ((event.getTarget() instanceof LivingEntity || event.getTarget() instanceof PartEntity<?>)
                && event.getItemStack().getItem() instanceof BottledMobItem item
                && item.capture(event.getEntity(), event.getTarget(), event.getHand())) {
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if ((event.getTarget() instanceof LivingEntity || event.getTarget() instanceof PartEntity<?>)
                && event.getItemStack().getItem() instanceof BottledMobItem item
                && item.capture(event.getEntity(), event.getTarget(), event.getHand())) {
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    /**
     * @return true when the interaction was handled by this item and should not
     * continue to vanilla behaviour.
     */
    boolean capture(Player player, Entity target, InteractionHand hand) {
        while (target instanceof PartEntity<?> part) {
            target = part.getParent();
        }
        if (target instanceof Player || player.isSpectator()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(this)) {
            return false;
        }
        StoredMob current = stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
        if (!current.isEmpty()) {
            return false;
        }

        // The server owns the capture decision; the client only consumes the interaction.
        if (target.level().isClientSide) {
            return true;
        }

        Level level = target.level();
        if (target.getVehicle() != null) {
            player.displayClientMessage(
                    Component.translatable("message.bottledmobs.cannot_capture_riding"), true);
            return true;
        }

        // A player can only end up as a passenger through /ride, but capturing the
        // stack would discard them together with the root, so refuse it outright.
        for (Entity member : target.getSelfAndPassengers().toList()) {
            if (member instanceof Player) {
                player.displayClientMessage(
                        Component.translatable("message.bottledmobs.cannot_capture_riding"), true);
                return true;
            }
        }

        boolean group = !target.getPassengers().isEmpty();
        if (group && !GameplayConfig.capturePassengerStacks()) {
            player.displayClientMessage(
                    Component.translatable("message.bottledmobs.cannot_capture_riding"), true);
            return true;
        }

        if (MobInfo.hasTag(target, BottledMobs.UNCAPTURABLE)) {
            player.displayClientMessage(
                    Component.translatable("message.bottledmobs.uncapturable"), true);
            return true;
        }

        CompoundTag data = MobNbt.save(target);
        if (data == null) {
            player.displayClientMessage(
                    Component.translatable("message.bottledmobs.capture_failed"), true);
            return true;
        }
        EntityType<?> capturedType = target.getType();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(capturedType).toString();
        String customName = target.hasCustomName() && target.getCustomName() != null
                ? target.getCustomName().getString()
                : "";
        String fallbackName = target.getDisplayName().getString();

        stack.set(BottledMobs.STORED_MOB.get(),
                new StoredMob(data, entityId, customName, fallbackName));
        stack.remove(BottledMobs.JAR_SETTINGS.get());
        level.playSound(null, target.blockPosition(),
                SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    8, 0.25, 0.5, 0.25, 0.01);
        }
        // Remove the riders first, then the root, so nothing is left behind or duplicated.
        List<Entity> members = new ArrayList<>(target.getSelfAndPassengers().toList());
        for (int index = members.size() - 1; index >= 0; index--) {
            members.get(index).discard();
        }

        if (player instanceof ServerPlayer serverPlayer) {
            BottledMobsAchievements.onCapture(serverPlayer, capturedType, group);
        }
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY).isEmpty();
    }

    @Override
    public Component getName(ItemStack stack) {
        MutableComponent name = Component.translatable(this.getDescriptionId(stack));
        StoredMob mob = stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
        if (mob.isEmpty()) {
            return name;
        }
        name.append(" ").append(mob.displayName());
        if (mob.customName().isEmpty()) {
            MobInfo.variantName(mob).ifPresent(variant ->
                    name.append(Component.translatable("tooltip.bottledmobs.variant_format", variant)));
        }
        return name;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        StoredMob mob = stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
        if (mob.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.bottledmobs.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.bottledmobs.contains", mob.displayName())
                    .withStyle(ChatFormatting.AQUA));
            if (MobInfo.isGroup(mob)) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.group", MobInfo.groupSize(mob))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!mob.customName().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.species", MobInfo.speciesName(mob))
                        .withStyle(ChatFormatting.GRAY));
            }
            MobInfo.variantName(mob).ifPresent(variant ->
                    tooltip.add(Component.translatable("tooltip.bottledmobs.variant", variant)
                            .withStyle(ChatFormatting.GRAY)));
            if (MobInfo.isBaby(mob)) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.baby")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (MobInfo.isAquatic(mob)) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.aquatic")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (GlowRules.lightFor(mob.dataView()) > 0) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.glowing")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        if (Screen.hasShiftDown()) {
            for (int line = 1; line <= 8; line++) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.usage." + line)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.bottledmobs.usage_hint").withStyle(ChatFormatting.GOLD));
        }
    }
}

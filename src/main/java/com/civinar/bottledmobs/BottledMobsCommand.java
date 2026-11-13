package com.civinar.bottledmobs;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * {@code /bottledmobs give <targets> <entity> [name]} — hands out a pre-filled jar.
 * Restricted to permission level 2 and deliberately bypasses the uncapturable tag
 * so admins can still prepare any exhibit.
 */
public final class BottledMobsCommand {

    private static final SimpleCommandExceptionType ERROR_NOT_LIVING = new SimpleCommandExceptionType(
            Component.translatable("command.bottledmobs.give.not_living"));

    private BottledMobsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();
        dispatcher.register(Commands.literal("bottledmobs")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("entity",
                                                ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                        .executes(context -> give(context, null))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(context -> give(context,
                                                        StringArgumentType.getString(context, "name"))))))));
    }

    private static int give(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        Holder.Reference<EntityType<?>> holder =
                ResourceArgument.getResource(context, "entity", Registries.ENTITY_TYPE);
        EntityType<?> type = holder.value();
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity)) {
            throw ERROR_NOT_LIVING.create();
        }

        String customName = name == null || name.isBlank() ? "" : name;
        if (!customName.isEmpty()) {
            entity.setCustomName(Component.literal(customName));
        }
        CompoundTag data = MobNbt.save(entity);
        StoredMob stored = new StoredMob(data,
                BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(),
                customName,
                entity.getDisplayName().getString());

        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : players) {
            ItemStack stack = new ItemStack(BottledMobs.BOTTLED_MOB_ITEM.get());
            stack.set(BottledMobs.STORED_MOB.get(), stored);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        int count = players.size();
        Component filled = stored.displayName();
        source.sendSuccess(() -> Component.translatable("command.bottledmobs.give.success",
                filled, count), true);
        return count;
    }
}

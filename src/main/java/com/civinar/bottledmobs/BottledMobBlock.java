package com.civinar.bottledmobs;

import com.civinar.bottledmobs.advancement.BottledMobsAchievements;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

public class BottledMobBlock extends BaseEntityBlock {

    public static final MapCodec<BottledMobBlock> CODEC = simpleCodec(BottledMobBlock::new);
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);

    public BottledMobBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIGHT, 0));
    }

    @Override
    protected MapCodec<BottledMobBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIGHT);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar
                ? jar.getComparatorSignal()
                : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BottledMobBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BottledMobBlockEntity jar) {
                jar.serverTick();
            }
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return JarShapes.JAR;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar) {
            jar.acceptItem(stack, placer == null ? Direction.NORTH : placer.getDirection());
            if (!level.isClientSide) {
                JarSettings settings = stack.get(BottledMobs.JAR_SETTINGS.get());
                if (settings != null) {
                    jar.applySettings(settings);
                } else {
                    jar.applyDefaultScale();
                }
                if (placer instanceof ServerPlayer serverPlayer) {
                    BottledMobsAchievements.onDisplay(serverPlayer, jar);
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // The creative break path never runs getDrops, so the drop has to be spawned
        // here or the stored mob would be deleted along with the jar.
        if (!level.isClientSide && player.isCreative()
                && level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar) {
            ItemStack drop = new ItemStack(BottledMobs.BOTTLED_MOB_ITEM.get());
            jar.saveToItem(drop, level.registryAccess());
            Block.popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        LootParams lootParams = params.withParameter(LootContextParams.BLOCK_STATE, state)
                .create(LootContextParamSets.BLOCK);
        BlockEntity blockEntity = lootParams.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ItemStack drop = new ItemStack(BottledMobs.BOTTLED_MOB_ITEM.get());
        if (blockEntity instanceof BottledMobBlockEntity jar) {
            jar.saveToItem(drop, lootParams.getLevel().registryAccess());
        }
        return List.of(drop);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        ItemStack stack = new ItemStack(BottledMobs.BOTTLED_MOB_ITEM.get());
        if (level instanceof Level actualLevel
                && level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar) {
            jar.saveToItem(stack, actualLevel.registryAccess());
        }
        return stack;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // The server owns the jar data; the client only consumes the interaction.
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                jar.resetDisplay(player.getDirection());
                playSound(level, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.POOF,
                            pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                            6, 0.25, 0.25, 0.25, 0.01);
                }
            } else {
                jar.rotate();
                playSound(level, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F);
            }
            fireDisplayAchievement(player, jar);
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.is(Items.NAME_TAG)) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName == null || customName.getString().isBlank()) {
                player.displayClientMessage(
                        Component.translatable("message.bottledmobs.name_tag_unnamed"), true);
            } else if (jar.getMob().isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("message.bottledmobs.name_tag_empty"), true);
            } else {
                jar.renameMob(customName.getString());
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                playSound(level, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.is(BottledMobs.BOTTLED_MOB_ITEM.get())) {
            StoredMob stored = stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
            if (jar.getMob().isEmpty() && !stored.isEmpty()) {
                jar.insertMob(stored);
                stack.set(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
                stack.remove(BottledMobs.JAR_SETTINGS.get());
                playSound(level, pos, SoundEvents.BOTTLE_FILL, 1.0F);
                fireDisplayAchievement(player, jar);
            } else {
                jar.resetShell();
                playSound(level, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        Block shell = Block.byItem(stack.getItem());
        if (!shell.defaultBlockState().isAir()) {
            jar.setShell(shell);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            playSound(level, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (itemPath.endsWith("_sword")) {
            jar.setShell(Blocks.AIR);
            playSound(level, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F);
        } else if (itemPath.endsWith("_hoe")) {
            jar.scaleUp();
            playSound(level, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.1F);
        } else if (itemPath.endsWith("_axe")) {
            jar.scaleDown();
            playSound(level, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, 0.9F);
        } else if (itemPath.endsWith("_pickaxe")) {
            jar.raise();
            playSound(level, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.1F);
        } else if (itemPath.endsWith("_shovel")) {
            jar.lower();
            playSound(level, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, 0.9F);
        } else if (stack.is(Items.STICK)) {
            boolean figureMode = jar.toggleFigureMode();
            playSound(level, pos, figureMode
                    ? SoundEvents.ITEM_FRAME_REMOVE_ITEM
                    : SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F);
        } else if (stack.is(Items.WRITABLE_BOOK)) {
            copySettings(pos, player, hand, jar);
            playSound(level, pos, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        } else if (stack.is(Items.WRITTEN_BOOK)) {
            pasteSettings(stack, jar);
            playSound(level, pos, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        }

        fireDisplayAchievement(player, jar);
        return ItemInteractionResult.SUCCESS;
    }

    private static void fireDisplayAchievement(Player player, BottledMobBlockEntity jar) {
        if (player instanceof ServerPlayer serverPlayer) {
            BottledMobsAchievements.onDisplay(serverPlayer, jar);
        }
    }

    private static void playSound(Level level, BlockPos pos, SoundEvent sound, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.6F, pitch);
    }

    private static void copySettings(BlockPos pos, Player player, InteractionHand hand,
                                     BottledMobBlockEntity jar) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Bottled Mob " + pos.toShortString()),
                player.getName().getString(),
                0,
                List.of(),
                true
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        book.set(BottledMobs.JAR_SETTINGS.get(), jar.copySettings());
        player.setItemInHand(hand, book);
    }

    private static void pasteSettings(ItemStack stack, BottledMobBlockEntity jar) {
        JarSettings settings = stack.get(BottledMobs.JAR_SETTINGS.get());
        if (settings != null) {
            jar.applySettings(settings);
        }
    }
}

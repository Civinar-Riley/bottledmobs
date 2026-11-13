package com.civinar.bottledmobs;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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

    public BottledMobBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<BottledMobBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BottledMobBlockEntity(pos, state);
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
            JarSettings settings = stack.get(BottledMobs.JAR_SETTINGS.get());
            if (!level.isClientSide && settings != null) {
                jar.applySettings(settings);
            }
        }
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
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.is(BottledMobs.BOTTLED_MOB_ITEM.get())) {
            jar.resetShell();
            playSound(level, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }

        Block shell = Block.byItem(stack.getItem());
        if (!shell.defaultBlockState().isAir()) {
            jar.setShell(shell);
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
            jar.setFigureMode();
            playSound(level, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F);
        } else if (stack.is(Items.WRITABLE_BOOK)) {
            copySettings(pos, player, hand, jar);
            playSound(level, pos, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        } else if (stack.is(Items.WRITTEN_BOOK)) {
            pasteSettings(stack, jar);
            playSound(level, pos, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        }

        return ItemInteractionResult.SUCCESS;
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

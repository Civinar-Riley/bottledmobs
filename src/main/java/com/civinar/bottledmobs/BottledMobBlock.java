package com.civinar.bottledmobs;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar) {
            jar.acceptItem(stack, placer == null ? Direction.NORTH : placer.getDirection());
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()
                && level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar) {
            ItemStack drop = new ItemStack(BottledMobs.BOTTLED_MOB_ITEM.get());
            jar.saveToItem(drop, level.registryAccess());
            Block.popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof BottledMobBlockEntity jar)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(BottledMobs.BOTTLED_MOB_ITEM.get())) {
            jar.resetShell();
            return ItemInteractionResult.SUCCESS;
        }

        Block shell = Block.byItem(stack.getItem());
        if (!shell.defaultBlockState().isAir()) {
            jar.setShell(shell);
            return ItemInteractionResult.SUCCESS;
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (itemPath.endsWith("_sword")) {
            jar.setShell(Blocks.AIR);
        } else if (itemPath.endsWith("_hoe")) {
            jar.scaleUp();
        } else if (itemPath.endsWith("_axe")) {
            jar.scaleDown();
        } else if (itemPath.endsWith("_pickaxe")) {
            jar.raise();
        } else if (itemPath.endsWith("_shovel")) {
            jar.lower();
        } else if (stack.is(Items.STICK)) {
            jar.setFigureMode();
        } else if (stack.is(Items.WRITABLE_BOOK)) {
            copySettings(level, pos, player, hand, jar);
        } else if (stack.is(Items.WRITTEN_BOOK)) {
            pasteSettings(level, stack, jar);
        }

        return ItemInteractionResult.SUCCESS;
    }

    private static void copySettings(Level level, BlockPos pos, Player player, InteractionHand hand,
                                     BottledMobBlockEntity jar) {
        if (level.isClientSide) {
            return;
        }
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

    private static void pasteSettings(Level level, ItemStack stack, BottledMobBlockEntity jar) {
        if (level.isClientSide) {
            return;
        }
        JarSettings settings = stack.get(BottledMobs.JAR_SETTINGS.get());
        if (settings != null) {
            jar.applySettings(settings);
        }
    }
}

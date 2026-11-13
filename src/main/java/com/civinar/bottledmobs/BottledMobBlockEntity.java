package com.civinar.bottledmobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;

public class BottledMobBlockEntity extends BlockEntity {

    public static final float DEFAULT_SCALE = 0.35F;
    public static final float MIN_SCALE = 0.05F;
    public static final float MAX_SCALE = 2.0F;
    public static final float SCALE_STEP = 0.1F;
    public static final float HEIGHT_STEP = 1.0F / 16.0F;
    public static final float MAX_HEIGHT = 1.0F;

    private StoredMob mob = StoredMob.EMPTY;
    private Direction facing = Direction.NORTH;
    private Block shell;
    private float scale = DEFAULT_SCALE;
    private float height;
    private int revision;

    public BottledMobBlockEntity(BlockPos pos, BlockState state) {
        super(BottledMobs.BOTTLED_MOB_BE.get(), pos, state);
    }

    public void acceptItem(ItemStack stack, @Nullable Direction facing) {
        this.mob = stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
        this.facing = facing == null ? Direction.NORTH : facing;
        this.markUpdated();
    }

    public StoredMob getMob() {
        return this.mob;
    }

    public Direction getFacing() {
        return this.facing;
    }

    public float getScale() {
        return this.scale;
    }

    public float getHeight() {
        return this.height;
    }

    public int getRevision() {
        return this.revision;
    }

    public BlockState getShellState() {
        if (this.shell == null) {
            return BottledMobs.EMPTY_JAR.get().defaultBlockState();
        }
        return this.shell.defaultBlockState();
    }

    public void setShell(@Nullable Block block) {
        this.shell = block;
        this.markUpdated();
    }

    public void resetShell() {
        this.setShell(null);
    }

    public void scaleUp() {
        this.scale = Math.min(this.scale + SCALE_STEP, MAX_SCALE);
        this.markUpdated();
    }

    public void scaleDown() {
        this.scale = Math.max(this.scale - SCALE_STEP, MIN_SCALE);
        this.markUpdated();
    }

    public void raise() {
        this.height = Math.min(this.height + HEIGHT_STEP, MAX_HEIGHT);
        this.markUpdated();
    }

    public void lower() {
        this.height = Math.max(this.height - HEIGHT_STEP, 0.0F);
        this.markUpdated();
    }

    public void setFigureMode() {
        this.shell = Blocks.AIR;
        this.scale = 1.0F;
        this.height = 0.0F;
        this.markUpdated();
    }

    public JarSettings copySettings() {
        ResourceLocation shellId = this.shell == null ? null : BuiltInRegistries.BLOCK.getKey(this.shell);
        return new JarSettings(Optional.ofNullable(shellId), this.scale, this.height);
    }

    public void applySettings(JarSettings settings) {
        this.scale = Mth.clamp(settings.scale(), MIN_SCALE, MAX_SCALE);
        this.height = Mth.clamp(settings.height(), 0.0F, MAX_HEIGHT);
        this.shell = shellFromId(settings.shell());
        this.markUpdated();
    }

    @Nullable
    private static Block shellFromId(Optional<ResourceLocation> id) {
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation location = id.get();
        if (location.equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR))) {
            return Blocks.AIR;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        return block == Blocks.AIR ? null : block;
    }

    @Nullable
    private static Block shellFromString(String id) {
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        if (location.equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR))) {
            return Blocks.AIR;
        }
        Block block = BuiltInRegistries.BLOCK.get(location);
        return block == Blocks.AIR ? null : block;
    }

    private void markUpdated() {
        this.revision++;
        this.setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("mob", this.mob.save());
        tag.putString("facing", this.facing.getName());
        tag.putString("shell", this.shell == null ? "" : BuiltInRegistries.BLOCK.getKey(this.shell).toString());
        tag.putFloat("scale", this.scale);
        tag.putFloat("height", this.height);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mob = StoredMob.load(tag.getCompound("mob"));
        Direction loadedFacing = Direction.byName(tag.getString("facing"));
        this.facing = loadedFacing == null ? Direction.NORTH : loadedFacing;
        this.shell = shellFromString(tag.getString("shell"));
        this.scale = tag.contains("scale")
                ? Mth.clamp(tag.getFloat("scale"), MIN_SCALE, MAX_SCALE)
                : DEFAULT_SCALE;
        this.height = Mth.clamp(tag.getFloat("height"), 0.0F, MAX_HEIGHT);
        this.revision++;
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        if (!this.mob.isEmpty()) {
            stack.set(BottledMobs.STORED_MOB.get(), this.mob);
        }
    }
}

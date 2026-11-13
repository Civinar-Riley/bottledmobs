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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Optional;

public class BottledMobBlockEntity extends BlockEntity {

    public static final float DEFAULT_SCALE = 0.5F;
    public static final float SAFE_MIN_SCALE = 0.01F;
    public static final float SAFE_MAX_SCALE = 16.0F;
    public static final float SAFE_MAX_HEIGHT = 4.0F;

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
        this.revision++;
        this.setChanged();
        this.syncFull();
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
        this.scale = Mth.clamp(this.scale + GameplayConfig.scaleStep(),
                GameplayConfig.minScale(), GameplayConfig.maxScale());
        this.markUpdated();
    }

    public void scaleDown() {
        this.scale = Mth.clamp(this.scale - GameplayConfig.scaleStep(),
                GameplayConfig.minScale(), GameplayConfig.maxScale());
        this.markUpdated();
    }

    public void raise() {
        this.height = Mth.clamp(this.height + GameplayConfig.heightStep(),
                GameplayConfig.minHeight(), GameplayConfig.maxHeight());
        this.markUpdated();
    }

    public void lower() {
        this.height = Mth.clamp(this.height - GameplayConfig.heightStep(),
                GameplayConfig.minHeight(), GameplayConfig.maxHeight());
        this.markUpdated();
    }

    public void setFigureMode() {
        this.shell = Blocks.AIR;
        this.scale = Mth.clamp(1.0F, GameplayConfig.minScale(), GameplayConfig.maxScale());
        this.height = 0.0F;
        this.markUpdated();
    }

    public void rotate() {
        Direction base = this.facing.getAxis().isHorizontal() ? this.facing : Direction.NORTH;
        this.facing = base.getClockWise(Direction.Axis.Y);
        this.markUpdated();
    }

    public void resetDisplay(@Nullable Direction facing) {
        this.shell = null;
        this.scale = DEFAULT_SCALE;
        this.height = 0.0F;
        this.facing = facing == null || !facing.getAxis().isHorizontal() ? Direction.NORTH : facing;
        this.markUpdated();
    }

    public JarSettings copySettings() {
        ResourceLocation shellId = this.shell == null ? null : BuiltInRegistries.BLOCK.getKey(this.shell);
        return new JarSettings(Optional.ofNullable(shellId), this.scale, this.height, this.facing);
    }

    public void applySettings(JarSettings settings) {
        this.scale = clamp(settings.scale(),
                GameplayConfig.minScale(), GameplayConfig.maxScale(), DEFAULT_SCALE);
        this.height = clamp(settings.height(),
                GameplayConfig.minHeight(), GameplayConfig.maxHeight(), 0.0F);
        this.shell = shellFromId(settings.shell());
        this.facing = settings.facing();
        this.markUpdated();
    }

    /**
     * Applies values that have already been clamped by the server. No config is
     * read here: a remote client may not have the server's config file.
     */
    public void applySyncedSettings(JarSettings settings) {
        this.scale = clamp(settings.scale(), SAFE_MIN_SCALE, SAFE_MAX_SCALE, DEFAULT_SCALE);
        this.height = clamp(settings.height(), 0.0F, SAFE_MAX_HEIGHT, 0.0F);
        this.shell = shellFromId(settings.shell());
        this.facing = settings.facing();
        this.revision++;
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Mth.clamp(value, min, max);
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
        if (this.level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(this.worldPosition),
                    new JarSettingsPayload(this.worldPosition, this.copySettings()));
        }
    }

    private void syncFull() {
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
                ? clamp(tag.getFloat("scale"), SAFE_MIN_SCALE, SAFE_MAX_SCALE, DEFAULT_SCALE)
                : DEFAULT_SCALE;
        this.height = tag.contains("height")
                ? clamp(tag.getFloat("height"), 0.0F, SAFE_MAX_HEIGHT, 0.0F)
                : 0.0F;
        this.revision++;
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        if (!this.mob.isEmpty()) {
            stack.set(BottledMobs.STORED_MOB.get(), this.mob);
        }
        stack.set(BottledMobs.JAR_SETTINGS.get(), this.copySettings());
    }
}

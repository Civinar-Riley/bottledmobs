package com.civinar.bottledmobs.client;

import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.ClientConfig;
import com.civinar.bottledmobs.MobNbt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

public class BottledMobBlockRenderer implements BlockEntityRenderer<BottledMobBlockEntity> {

    private static final float SPIN_SPEED = 1.5F;
    private static final Map<Direction, Quaternionf> FACING_ROTATIONS = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Direction.values()) {
            Quaternionf rotation = new Quaternionf(direction.getRotation());
            rotation.mul(new Quaternionf().fromAxisAngleDeg(1.0F, 0.0F, 0.0F, -90.0F));
            rotation.mul(new Quaternionf().fromAxisAngleDeg(0.0F, 1.0F, 0.0F, 180.0F));
            FACING_ROTATIONS.put(direction, rotation);
        }
    }

    private final EntityRenderDispatcher entityRenderer;
    private final BlockRenderDispatcher blockRenderer;
    private final Map<BottledMobBlockEntity, CachedEntity> cache = new WeakHashMap<>();

    public BottledMobBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher();
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(BottledMobBlockEntity jar, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        Entity entity = this.displayEntity(jar);
        if (entity != null) {
            this.animate(entity, jar, partialTick);

            pose.pushPose();
            pose.translate(0.5, jar.getHeight(), 0.5);
            float scale = jar.getScale();
            pose.scale(scale, scale, scale);
            pose.mulPose(FACING_ROTATIONS.get(jar.getFacing()));
            this.entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0F, partialTick, pose, buffer, light);
            pose.popPose();
        }

        BlockState shell = jar.getShellState();
        if (!shell.isAir()) {
            pose.pushPose();
            this.blockRenderer.renderSingleBlock(shell, pose, buffer, light, overlay, ModelData.EMPTY, null);
            pose.popPose();
        }
    }

    @Nullable
    private Entity displayEntity(BottledMobBlockEntity jar) {
        if (jar.getMob().isEmpty()) {
            return null;
        }
        CachedEntity cached = this.cache.get(jar);
        if (cached != null && cached.revision() == jar.getRevision()) {
            return cached.entity();
        }

        Level level = jar.getLevel();
        if (level == null) {
            return null;
        }
        Entity entity = MobNbt.create(jar.getMob().entityData(), level);
        if (entity == null) {
            return null;
        }
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = 0.0F;
            living.yBodyRot = 0.0F;
            living.yHeadRotO = 0.0F;
            living.yBodyRotO = 0.0F;
        }
        this.cache.put(jar, new CachedEntity(jar.getRevision(), entity));
        return entity;
    }

    private void animate(Entity entity, BottledMobBlockEntity jar, float partialTick) {
        Level level = jar.getLevel();
        if (level == null || !ClientConfig.SPEC.isLoaded() || !ClientConfig.ANIMATE.get()) {
            return;
        }

        entity.tickCount++;
        float yaw = (level.getGameTime() % 360000L + partialTick) * SPIN_SPEED;
        entity.yRotO = entity.getYRot();
        entity.setYRot(yaw);
        if (entity instanceof LivingEntity living) {
            living.yHeadRotO = living.yHeadRot;
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRot = yaw;
            living.yBodyRot = yaw;
        }
    }

    private record CachedEntity(int revision, Entity entity) {
    }
}

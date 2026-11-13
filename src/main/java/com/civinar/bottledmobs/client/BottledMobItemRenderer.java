package com.civinar.bottledmobs.client;

import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.BottledMobs;
import com.civinar.bottledmobs.MobInfo;
import com.civinar.bottledmobs.MobNbt;
import com.civinar.bottledmobs.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;

public class BottledMobItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final int CACHE_LIMIT = 64;
    private static final Quaternionf ITEM_ROTATION = createItemRotation();

    private final EntityRenderDispatcher entityRenderer;
    private final BlockRenderDispatcher blockRenderer;
    private final Map<StoredMob, Entity> cache = new IdentityHashMap<>();

    public BottledMobItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher();
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
    }

    private static Quaternionf createItemRotation() {
        Quaternionf rotation = new Quaternionf(Direction.EAST.getRotation());
        rotation.mul(new Quaternionf().fromAxisAngleDeg(1.0F, 0.0F, 0.0F, -90.0F));
        rotation.mul(new Quaternionf().fromAxisAngleDeg(0.0F, 1.0F, 0.0F, 180.0F));
        return rotation;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        StoredMob mob = stack.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
        Entity entity = this.displayEntity(mob);

        if (entity != null) {
            pose.pushPose();
            pose.translate(0.5, 0.0, 0.5);
            pose.scale(BottledMobBlockEntity.DEFAULT_SCALE, BottledMobBlockEntity.DEFAULT_SCALE,
                    BottledMobBlockEntity.DEFAULT_SCALE);
            pose.mulPose(ITEM_ROTATION);
            this.entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0F, 0.0F, pose, buffer, light);
            pose.popPose();
        }

        if (MobInfo.isAquatic(mob)) {
            JarWaterRenderer.render(pose, buffer, light);
        }

        this.blockRenderer.renderSingleBlock(
                BottledMobs.EMPTY_JAR.get().defaultBlockState(),
                pose, buffer, light, overlay, ModelData.EMPTY, null);
    }

    @Nullable
    private Entity displayEntity(StoredMob mob) {
        if (mob.isEmpty()) {
            return null;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        if (this.cache.size() >= CACHE_LIMIT) {
            this.cache.clear();
        }
        Entity cached = this.cache.get(mob);
        if (cached != null) {
            return cached;
        }

        Entity entity = MobNbt.create(mob.entityData(), level);
        if (entity == null) {
            return null;
        }
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = 0.0F;
            living.yBodyRot = 0.0F;
            living.yHeadRotO = 0.0F;
            living.yBodyRotO = 0.0F;
        }
        this.cache.put(mob, entity);
        return entity;
    }
}

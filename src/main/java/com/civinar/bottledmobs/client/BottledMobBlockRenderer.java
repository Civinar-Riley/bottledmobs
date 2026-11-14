package com.civinar.bottledmobs.client;

import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.ClientConfig;
import com.civinar.bottledmobs.DisplayStandBlock;
import com.civinar.bottledmobs.MobInfo;
import com.civinar.bottledmobs.MobNbt;
import com.civinar.bottledmobs.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

public class BottledMobBlockRenderer implements BlockEntityRenderer<BottledMobBlockEntity> {

    private static final float SPIN_SPEED = 1.5F;
    /** Upper bound for the culling box so a huge scaled mob cannot disable culling. */
    private static final double MAX_EXTENT = 64.0D;
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
    private final Map<BottledMobBlockEntity, DisplayData> cache = new WeakHashMap<>();

    public BottledMobBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher();
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public AABB getRenderBoundingBox(BottledMobBlockEntity jar) {
        BlockPos pos = jar.getBlockPos();
        StoredMob mob = jar.getMob();
        float width = MobInfo.displayWidth(mob);
        DisplayData cached = this.cache.get(jar);
        // Until the jar has rendered once, allow for a rider stack on top.
        float height = cached != null && cached.revision() == jar.getRevision()
                ? cached.groupHeight()
                : Math.max(MobInfo.displayHeight(mob), 2.0F);
        double scale = jar.getScale();
        // Sized from the creature's registered bounding box instead of a fixed
        // multiple of the scale, which used to overshoot by dozens of blocks.
        double half = Math.min(MAX_EXTENT, Math.max(1.2D, 0.6D + Math.max(width, height) * scale * 0.6D));
        double top = Math.min(MAX_EXTENT, 1.5D + height * scale + jar.getHeight());
        return new AABB(pos.getX() + 0.5D - half, pos.getY() - 0.25D, pos.getZ() + 0.5D - half,
                pos.getX() + 0.5D + half, pos.getY() + top, pos.getZ() + 0.5D + half);
    }

    @Override
    public void render(BottledMobBlockEntity jar, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        DisplayData data = this.displayData(jar);
        boolean animated = animationEnabled();
        Entity entity = data.entity();
        if (entity != null) {
            if (animated) {
                this.animate(entity, jar, partialTick);
            }

            pose.pushPose();
            pose.translate(0.5, jar.getHeight(), 0.5);
            float scale = jar.getScale();
            pose.scale(scale, scale, scale);
            pose.mulPose(FACING_ROTATIONS.get(jar.getFacing()));
            // A frozen display has to render one steady frame. Passing the live
            // partial tick makes every model that animates with age in ticks snap
            // back once per game tick, which reads as trembling.
            JarEntityRenderer.render(entity, this.entityRenderer, pose, buffer, light,
                    animated ? partialTick : 0.0F);
            pose.popPose();
        }

        if (data.water()) {
            JarWaterRenderer.render(pose, buffer, light);
            this.spawnBubbles(jar);
        }

        BlockState shell = jar.getShellState();
        if (!shell.isAir()) {
            pose.pushPose();
            this.blockRenderer.renderSingleBlock(shell, pose, buffer, light, overlay, ModelData.EMPTY, null);
            pose.popPose();
        }

        if (!data.label().isEmpty() && !this.hasDisplayStand(jar)) {
            JarLabelRenderer.render(data.label(), pose, buffer, light);
        }
    }

    private static boolean animationEnabled() {
        return ClientConfig.SPEC.isLoaded() && ClientConfig.ANIMATE.get();
    }

    private boolean hasDisplayStand(BottledMobBlockEntity jar) {
        Level level = jar.getLevel();
        return level != null
                && level.getBlockState(jar.getBlockPos().below()).getBlock() instanceof DisplayStandBlock;
    }

    private void spawnBubbles(BottledMobBlockEntity jar) {
        Level level = jar.getLevel();
        if (level == null || !level.isClientSide || level.random.nextFloat() > 0.02F) {
            return;
        }
        double x = jar.getBlockPos().getX() + 0.35 + level.random.nextDouble() * 0.3;
        double y = jar.getBlockPos().getY() + 0.25 + level.random.nextDouble() * 0.35;
        double z = jar.getBlockPos().getZ() + 0.35 + level.random.nextDouble() * 0.3;
        level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.01, 0.0);
    }

    /**
     * Everything derived from the stored mob, rebuilt only when the block entity
     * changes. Keeps the per-frame path free of NBT copies, registry lookups and
     * entity creation.
     */
    private DisplayData displayData(BottledMobBlockEntity jar) {
        DisplayData cached = this.cache.get(jar);
        if (cached != null && cached.revision() == jar.getRevision()) {
            return cached;
        }

        StoredMob mob = jar.getMob();
        Level level = jar.getLevel();
        Entity entity = null;
        boolean aquatic = false;
        float groupHeight = MobInfo.displayHeight(mob);
        if (!mob.isEmpty() && level != null) {
            aquatic = MobInfo.isAquatic(mob);
            entity = MobNbt.create(mob.entityData(), level);
            if (entity != null) {
                JarEntityRenderer.prepare(entity, aquatic);
                groupHeight = groupHeight(entity);
            }
        }

        DisplayData data = new DisplayData(jar.getRevision(), entity,
                aquatic && jar.hasDefaultShell(), mob.customName(), groupHeight);
        this.cache.put(jar, data);
        return data;
    }

    /** Sum of the bounding box heights of the root and every rider stacked on it. */
    private static float groupHeight(Entity entity) {
        float height = entity.getBbHeight();
        for (Entity passenger : entity.getPassengers()) {
            height += groupHeight(passenger);
        }
        return height;
    }

    private void animate(Entity entity, BottledMobBlockEntity jar, float partialTick) {
        Level level = jar.getLevel();
        if (level == null) {
            return;
        }

        // The 2^23 wrap keeps tickCount within float precision for partial ticks;
        // it lands after ~4.8 days of server uptime and only resets idle-pose phase.
        JarEntityRenderer.setAge(entity, (int) (level.getGameTime() % (1L << 23)));
        float yaw = (level.getGameTime() % 360000L + partialTick) * SPIN_SPEED;
        JarEntityRenderer.applyYaw(entity, yaw);
    }

    private record DisplayData(int revision, @Nullable Entity entity, boolean water, String label,
                               float groupHeight) {
    }
}

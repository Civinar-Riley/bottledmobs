package com.civinar.bottledmobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders a stored creature together with every rider it was captured with.
 *
 * <p>Vanilla renders the entities in a world separately, so a captured group has
 * to be drawn by hand. The root is drawn at the given origin and each passenger is
 * offset using the vehicle's own {@code positionRider} attachment maths, which
 * keeps chicken jockeys and spider jockeys sitting where vanilla puts them.
 */
public final class JarEntityRenderer {

    private JarEntityRenderer() {
    }

    public static void render(Entity root, EntityRenderDispatcher dispatcher, PoseStack pose,
                              MultiBufferSource buffer, int light, float partialTick) {
        dispatcher.render(root, 0.0, 0.0, 0.0, 0.0F, partialTick, pose, buffer, light);
        for (Entity passenger : root.getPassengers()) {
            root.positionRider(passenger);
            double dx = passenger.getX() - root.getX();
            double dy = passenger.getY() - root.getY();
            double dz = passenger.getZ() - root.getZ();
            pose.pushPose();
            pose.translate(dx, dy, dz);
            render(passenger, dispatcher, pose, buffer, light, partialTick);
            pose.popPose();
        }
    }

    /**
     * Clears the vanilla name tag and zeroes the rotations of the whole group so
     * the jar's own label is the only name shown. Aquatic groups are marked as
     * being in water so vanilla fish renderers keep them upright instead of
     * tipping them onto their side.
     */
    public static void prepare(Entity entity, boolean inWater) {
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);
        resetRotations(entity);
        if (inWater) {
            markInWater(entity);
        }
        for (Entity passenger : entity.getPassengers()) {
            prepare(passenger, inWater);
        }
    }

    private static void resetRotations(Entity entity) {
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        // The interpolation sources must match the current values, otherwise a
        // frozen display would slide between them once per game tick.
        entity.yRotO = 0.0F;
        entity.xRotO = 0.0F;
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = 0.0F;
            living.yBodyRot = 0.0F;
            living.yHeadRotO = 0.0F;
            living.yBodyRotO = 0.0F;
        }
    }

    private static void markInWater(Entity entity) {
        entity.wasTouchingWater = true;
    }

    public static void tick(Entity entity) {
        entity.tickCount++;
        for (Entity passenger : entity.getPassengers()) {
            tick(passenger);
        }
    }

    public static void applyYaw(Entity entity, float yaw) {
        entity.yRotO = entity.getYRot();
        entity.setYRot(yaw);
        if (entity instanceof LivingEntity living) {
            living.yHeadRotO = living.yHeadRot;
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRot = yaw;
            living.yBodyRot = yaw;
        }
        for (Entity passenger : entity.getPassengers()) {
            applyYaw(passenger, yaw);
        }
    }
}

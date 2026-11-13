package com.civinar.bottledmobs.client;

import com.civinar.bottledmobs.BottledMobs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws a small translucent water volume inside the default jar model.
 */
public final class JarWaterRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BottledMobs.MOD_ID, "textures/block/jar_water.png");

    private static final float X0 = 3.2F / 16.0F;
    private static final float X1 = 12.8F / 16.0F;
    private static final float Y0 = 0.8F / 16.0F;
    private static final float Y1 = 10.5F / 16.0F;
    private static final float Z0 = 3.2F / 16.0F;
    private static final float Z1 = 12.8F / 16.0F;

    private JarWaterRenderer() {
    }

    public static void render(PoseStack pose, MultiBufferSource buffer, int light) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f matrix = pose.last().pose();
        float red = 0.86F;
        float green = 0.94F;
        float blue = 1.0F;
        float alpha = 0.55F;

        // Top surface.
        quad(consumer, matrix, pose, X0, Y1, Z0, X0, Y1, Z1, X1, Y1, Z1, X1, Y1, Z0,
                0.0F, 1.0F, 0.0F, red, green, blue, alpha, light);
        // North and south.
        quad(consumer, matrix, pose, X0, Y0, Z0, X1, Y0, Z0, X1, Y1, Z0, X0, Y1, Z0,
                0.0F, 0.0F, -1.0F, red, green, blue, alpha, light);
        quad(consumer, matrix, pose, X1, Y0, Z1, X0, Y0, Z1, X0, Y1, Z1, X1, Y1, Z1,
                0.0F, 0.0F, 1.0F, red, green, blue, alpha, light);
        // West and east.
        quad(consumer, matrix, pose, X0, Y0, Z1, X0, Y0, Z0, X0, Y1, Z0, X0, Y1, Z1,
                -1.0F, 0.0F, 0.0F, red, green, blue, alpha, light);
        quad(consumer, matrix, pose, X1, Y0, Z0, X1, Y0, Z1, X1, Y1, Z1, X1, Y1, Z0,
                1.0F, 0.0F, 0.0F, red, green, blue, alpha, light);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, PoseStack pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz,
                             float red, float green, float blue, float alpha, int light) {
        vertex(consumer, matrix, pose, x0, y0, z0, 0.0F, 1.0F, nx, ny, nz, red, green, blue, alpha, light);
        vertex(consumer, matrix, pose, x1, y1, z1, 1.0F, 1.0F, nx, ny, nz, red, green, blue, alpha, light);
        vertex(consumer, matrix, pose, x2, y2, z2, 1.0F, 0.0F, nx, ny, nz, red, green, blue, alpha, light);
        vertex(consumer, matrix, pose, x3, y3, z3, 0.0F, 0.0F, nx, ny, nz, red, green, blue, alpha, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack pose,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz,
                               float red, float green, float blue, float alpha, int light) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose.last(), nx, ny, nz);
    }
}

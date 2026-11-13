package com.civinar.bottledmobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

/**
 * A small billboard label above jars that carry a name tag.
 */
public final class JarLabelRenderer {

    private static final int MAX_WIDTH = 80;
    private static final float SCALE = 0.025F;

    private JarLabelRenderer() {
    }

    public static void render(String name, PoseStack pose, MultiBufferSource buffer, int light) {
        if (name == null || name.isBlank()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String text = font.plainSubstrByWidth(name, MAX_WIDTH);
        int width = font.width(text);
        Camera camera = minecraft.gameRenderer.getMainCamera();

        pose.pushPose();
        pose.translate(0.5, 1.18, 0.5);
        pose.mulPose(camera.rotation());
        pose.scale(-SCALE, -SCALE, SCALE);
        font.drawInBatch(Component.literal(text), -width / 2.0F, 0.0F, 0xFFFFFFFF, true,
                pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0x40000000, light);
        pose.popPose();
    }
}

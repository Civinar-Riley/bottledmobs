package com.civinar.bottledmobs.client;

import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.DisplayStandBlock;
import com.civinar.bottledmobs.DisplayStandBlockEntity;
import com.civinar.bottledmobs.MobInfo;
import com.civinar.bottledmobs.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class DisplayStandRenderer implements BlockEntityRenderer<DisplayStandBlockEntity> {

    public DisplayStandRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DisplayStandBlockEntity stand, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        Level level = stand.getLevel();
        if (level == null) {
            return;
        }
        BlockPos above = stand.getBlockPos().above();
        if (!(level.getBlockEntity(above) instanceof BottledMobBlockEntity jar)) {
            return;
        }
        StoredMob mob = jar.getMob();
        if (mob.isEmpty()) {
            return;
        }

        BlockState state = stand.getBlockState();
        Direction facing = state.getValue(DisplayStandBlock.FACING);
        String title = mob.customName().isEmpty()
                ? MobInfo.speciesName(mob).getString()
                : mob.customName();
        List<String> details = new ArrayList<>();
        if (!mob.customName().isEmpty()) {
            details.add(MobInfo.speciesName(mob).getString());
        }
        MobInfo.variantName(mob).ifPresent(variant -> details.add(variant.getString()));
        if (MobInfo.isBaby(mob)) {
            details.add(Component.translatable("tooltip.bottledmobs.baby").getString());
        }

        double x = 0.5 + facing.getStepX() * 0.39;
        double z = 0.5 + facing.getStepZ() * 0.39;
        double y = state.getValue(DisplayStandBlock.WALL) ? 0.82 : 0.55;
        JarLabelRenderer.render(title, x, y, z, 0.018F, 72, pose, buffer, light);
        String subtitle = String.join(" · ", details);
        if (!subtitle.isBlank()) {
            JarLabelRenderer.render(subtitle, x, y - 0.035, z, 0.014F, 84, pose, buffer, light);
        }
    }
}

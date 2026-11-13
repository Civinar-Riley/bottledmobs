package com.civinar.bottledmobs.client;

import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.DisplayStandBlock;
import com.civinar.bottledmobs.DisplayStandBlockEntity;
import com.civinar.bottledmobs.MobInfo;
import com.civinar.bottledmobs.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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
import java.util.Map;
import java.util.WeakHashMap;

public class DisplayStandRenderer implements BlockEntityRenderer<DisplayStandBlockEntity> {

    private final Map<DisplayStandBlockEntity, Label> labels = new WeakHashMap<>();

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
        if (jar.getMob().isEmpty()) {
            return;
        }

        BlockState state = stand.getBlockState();
        Direction facing = state.getValue(DisplayStandBlock.FACING);
        Label label = this.label(stand, jar);

        double x = 0.5 + facing.getStepX() * 0.39;
        double z = 0.5 + facing.getStepZ() * 0.39;
        double y = state.getValue(DisplayStandBlock.WALL) ? 0.82 : 0.55;
        JarLabelRenderer.render(label.title(), x, y, z, 0.018F, 72, pose, buffer, light);
        if (!label.subtitle().isBlank()) {
            JarLabelRenderer.render(label.subtitle(), x, y - 0.035, z, 0.014F, 84, pose, buffer, light);
        }
    }

    /**
     * Building the label used to run per frame: three NBT copies, a variant rule
     * scan and all the component translation. It only changes when the jar or the
     * language changes.
     */
    private Label label(DisplayStandBlockEntity stand, BottledMobBlockEntity jar) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        Label cached = this.labels.get(stand);
        if (cached != null && cached.jar() == jar && cached.revision() == jar.getRevision()
                && cached.language().equals(language)) {
            return cached;
        }

        StoredMob mob = jar.getMob();
        String title = mob.displayName().getString();
        List<String> details = new ArrayList<>();
        if (!mob.customName().isEmpty()) {
            details.add(MobInfo.speciesName(mob).getString());
        }
        MobInfo.variantName(mob).ifPresent(variant -> details.add(variant.getString()));
        if (MobInfo.isBaby(mob)) {
            details.add(Component.translatable("tooltip.bottledmobs.baby").getString());
        }
        if (MobInfo.isGroup(mob)) {
            details.add(Component.translatable("tooltip.bottledmobs.group", MobInfo.groupSize(mob)).getString());
        }

        Label label = new Label(jar, jar.getRevision(), language, title, String.join(" · ", details));
        this.labels.put(stand, label);
        return label;
    }

    private record Label(BottledMobBlockEntity jar, int revision, String language,
                         String title, String subtitle) {
    }
}

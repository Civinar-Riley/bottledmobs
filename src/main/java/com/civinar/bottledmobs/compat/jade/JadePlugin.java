package com.civinar.bottledmobs.compat.jade;

import com.civinar.bottledmobs.BottledMobBlock;
import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.BottledMobs;
import com.civinar.bottledmobs.GlowRules;
import com.civinar.bottledmobs.MobInfo;
import com.civinar.bottledmobs.StoredMob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * Optional Jade integration. Jade discovers the class through {@link WailaPlugin},
 * so it is never loaded when Jade is absent and the mod still starts normally.
 * The jar is a {@code compileOnly} dependency and is not bundled.
 */
@WailaPlugin(BottledMobs.MOD_ID)
public final class JadePlugin implements IWailaPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(BottledMobs.MOD_ID, "jar");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(JarComponentProvider.INSTANCE, BottledMobBlock.class);
    }

    public static final class JarComponentProvider implements IBlockComponentProvider {

        static final JarComponentProvider INSTANCE = new JarComponentProvider();

        private JarComponentProvider() {
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof BottledMobBlockEntity jar)) {
                return;
            }
            StoredMob mob = jar.getMob();
            if (mob.isEmpty()) {
                return;
            }

            tooltip.add(Component.translatable("tooltip.bottledmobs.contains", mob.displayName())
                    .withStyle(ChatFormatting.AQUA));
            if (MobInfo.isGroup(mob)) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.group", MobInfo.groupSize(mob))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!mob.customName().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.species", MobInfo.speciesName(mob))
                        .withStyle(ChatFormatting.GRAY));
            }
            MobInfo.variantName(mob).ifPresent(variant ->
                    tooltip.add(Component.translatable("tooltip.bottledmobs.variant", variant)
                            .withStyle(ChatFormatting.GRAY)));
            if (MobInfo.isBaby(mob)) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.baby").withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable("tooltip.bottledmobs.scale",
                    String.format(Locale.ROOT, "%.2f", jar.getScale())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.bottledmobs.height",
                    String.format(Locale.ROOT, "%.2f", jar.getHeight())).withStyle(ChatFormatting.GRAY));
            if (jar.isFigureMode()) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.figure_mode")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (MobInfo.isAquatic(mob)) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.aquatic").withStyle(ChatFormatting.GRAY));
            }
            if (GlowRules.lightFor(mob.dataView()) > 0) {
                tooltip.add(Component.translatable("tooltip.bottledmobs.glowing").withStyle(ChatFormatting.GRAY));
            }
        }
    }
}

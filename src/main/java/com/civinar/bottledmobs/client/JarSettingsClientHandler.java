package com.civinar.bottledmobs.client;

import com.civinar.bottledmobs.BottledMobBlockEntity;
import com.civinar.bottledmobs.JarSettingsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class JarSettingsClientHandler {

    private JarSettingsClientHandler() {
    }

    public static void apply(JarSettingsPayload payload) {
        Level level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(payload.pos()) instanceof BottledMobBlockEntity jar) {
            jar.applySyncedSettings(payload.settings());
        }
    }
}

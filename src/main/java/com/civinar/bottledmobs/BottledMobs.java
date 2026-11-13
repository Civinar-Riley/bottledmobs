package com.civinar.bottledmobs;

import com.civinar.bottledmobs.client.ClientSetup;
import com.civinar.bottledmobs.client.JarSettingsClientHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Supplier;

@Mod(BottledMobs.MOD_ID)
public final class BottledMobs {

    public static final String MOD_ID = "bottledmobs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public static final DeferredBlock<Block> BOTTLED_MOB = BLOCKS.register("bottled_mob",
            () -> new BottledMobBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(1.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredBlock<Block> EMPTY_JAR = BLOCKS.register("empty_jar",
            () -> new EmptyJarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(1.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> BOTTLED_MOB_ITEM = ITEMS.register("bottled_mob",
            () -> new BottledMobItem(BOTTLED_MOB.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> EMPTY_JAR_ITEM =
            ITEMS.registerSimpleBlockItem("empty_jar", EMPTY_JAR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BottledMobBlockEntity>> BOTTLED_MOB_BE =
            BLOCK_ENTITIES.register("bottled_mob",
                    () -> BlockEntityType.Builder.of(BottledMobBlockEntity::new, BOTTLED_MOB.get()).build(null));

    public static final Supplier<DataComponentType<StoredMob>> STORED_MOB =
            DATA_COMPONENTS.registerComponentType("stored_mob", builder -> builder
                    .persistent(StoredMob.CODEC)
                    .networkSynchronized(StoredMob.STREAM_CODEC));

    public static final Supplier<DataComponentType<JarSettings>> JAR_SETTINGS =
            DATA_COMPONENTS.registerComponentType("jar_settings", builder -> builder
                    .persistent(JarSettings.CODEC)
                    .networkSynchronized(JarSettings.STREAM_CODEC));

    public BottledMobs(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, GameplayConfig.SPEC);

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);

        modBus.addListener(this::addCreativeTabItems);
        modBus.addListener(this::registerPayloads);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(JarSettingsPayload.TYPE, JarSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> JarSettingsClientHandler.apply(payload)));
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(EMPTY_JAR_ITEM.get());
            event.accept(BOTTLED_MOB_ITEM.get());
        }
    }
}

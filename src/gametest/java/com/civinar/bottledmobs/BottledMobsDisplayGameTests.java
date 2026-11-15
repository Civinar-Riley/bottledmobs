package com.civinar.bottledmobs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(BottledMobs.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BottledMobsDisplayGameTests {
    @GameTest(template = "empty", batch = "bottledmobs.config")
    public static void existingJarsClampWhenServerLimitsTighten(GameTestHelper helper) {
        double minScale = GameplayConfig.MIN_SCALE.get();
        double maxScale = GameplayConfig.MAX_SCALE.get();
        double minHeight = GameplayConfig.MIN_HEIGHT.get();
        double maxHeight = GameplayConfig.MAX_HEIGHT.get();
        BlockPos highPos = new BlockPos(5, 1, 5);
        BlockPos lowPos = new BlockPos(7, 1, 5);
        try {
            // This batch changes global config only within this synchronous call.
            GameplayConfig.MIN_SCALE.set(0.1D);
            GameplayConfig.MAX_SCALE.set(3.0D);
            GameplayConfig.MIN_HEIGHT.set(0.0D);
            GameplayConfig.MAX_HEIGHT.set(2.0D);
            helper.setBlock(highPos, BottledMobs.BOTTLED_MOB.get());
            helper.setBlock(lowPos, BottledMobs.BOTTLED_MOB.get());
            BottledMobBlockEntity high = helper.getBlockEntity(highPos);
            BottledMobBlockEntity low = helper.getBlockEntity(lowPos);
            high.applySettings(new JarSettings(Optional.empty(), 3.0F, 2.0F, Direction.EAST));
            low.applySettings(new JarSettings(Optional.empty(), 0.1F, 0.0F, Direction.WEST));
            CompoundTag savedHigh = high.getUpdateTag(helper.getLevel().registryAccess());

            GameplayConfig.MIN_SCALE.set(0.5D);
            GameplayConfig.MAX_SCALE.set(1.0D);
            GameplayConfig.MIN_HEIGHT.set(0.25D);
            GameplayConfig.MAX_HEIGHT.set(0.5D);
            // Exercise both an already loaded jar and an old jar loaded after tightening.
            high.loadAdditional(savedHigh, helper.getLevel().registryAccess());
            high.serverTick();
            low.serverTick();
            settings(helper, high, 1.0F, 0.5F);
            settings(helper, low, 0.5F, 0.25F);
            helper.assertTrue(high.getFacing() == Direction.EAST && low.getFacing() == Direction.WEST,
                    "Clamping changed the jars' facing");
            int highRevision = high.getRevision();
            int lowRevision = low.getRevision();
            high.serverTick();
            low.serverTick();
            helper.assertTrue(high.getRevision() == highRevision && low.getRevision() == lowRevision,
                    "Unchanged display limits trigger a settings update on every tick");
            CompoundTag saved = high.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(saved.getFloat("scale") == 1.0F && saved.getFloat("height") == 0.5F,
                    "The corrected display bounds were not persisted");

            low.resetDisplay(Direction.NORTH);
            helper.assertTrue(low.getHeight() == 0.25F, "Reset ignored a positive minimum height");
            high.toggleFigureMode();
            helper.assertTrue(high.getHeight() == 0.25F, "Figure mode ignored a positive minimum height");
        } finally {
            GameplayConfig.MIN_SCALE.set(minScale);
            GameplayConfig.MAX_SCALE.set(maxScale);
            GameplayConfig.MIN_HEIGHT.set(minHeight);
            GameplayConfig.MAX_HEIGHT.set(maxHeight);
            helper.setBlock(highPos, Blocks.AIR);
            helper.setBlock(lowPos, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "bottledmobs.config.redstone")
    public static void canceledRedstoneReleaseKeepsStoredMobAndRollsBackRoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean previousRedstoneRelease = GameplayConfig.REDSTONE_RELEASE.get();
        BlockPos jarPos = new BlockPos(10, 2, 10);
        BlockPos powerPos = jarPos.east();
        Entity root = EntityType.PIG.create(level);
        Entity passenger = EntityType.CHICKEN.create(level);
        helper.assertTrue(root != null && passenger != null, "Could not create the redstone release fixture");
        UUID rootUuid = root.getUUID();
        UUID passengerUuid = passenger.getUUID();
        Entity[] addedRoot = {null};
        Consumer<EntityJoinLevelEvent> cancelPassenger = event -> {
            if (event.getLevel() == level && event.getEntity().getUUID().equals(passengerUuid)) {
                addedRoot[0] = level.getEntity(rootUuid);
                event.setCanceled(true);
            }
        };
        try {
            GameplayConfig.REDSTONE_RELEASE.set(true);
            helper.setBlock(powerPos, Blocks.AIR);
            helper.setBlock(jarPos, BottledMobs.BOTTLED_MOB.get());
            Vec3 pos = helper.absoluteVec(new Vec3(10.5D, 2.0D, 10.5D));
            root.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            passenger.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            helper.assertTrue(passenger.startRiding(root, true), "Could not mount the redstone passenger");
            StoredMob stored = new StoredMob(saved(helper, root), "minecraft:pig", "", "Pig");
            BottledMobBlockEntity jar = helper.getBlockEntity(jarPos);
            jar.insertMob(stored);
            helper.assertTrue(!level.hasNeighborSignal(helper.absolutePos(jarPos)),
                    "The redstone fixture must start unpowered");
            jar.serverTick();

            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, cancelPassenger);
            helper.setBlock(powerPos, Blocks.REDSTONE_BLOCK);
            jar.serverTick();

            helper.assertTrue(addedRoot[0] != null,
                    "The redstone passenger was not canceled after its root joined the level");
            helper.assertTrue(jar.getMob().equals(stored),
                    "Canceled redstone release changed or cleared the jar contents");
            helper.assertTrue(addedRoot[0].isRemoved() && level.getEntity(rootUuid) == null,
                    "Canceled redstone release did not roll back the already-added root");
            helper.assertTrue(level.getEntity(passengerUuid) == null,
                    "Canceled redstone release left its passenger in the level");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancelPassenger);
            GameplayConfig.REDSTONE_RELEASE.set(previousRedstoneRelease);
            helper.setBlock(powerPos, Blocks.AIR);
            helper.setBlock(jarPos, Blocks.AIR);
            Entity joinedPassenger = level.getEntity(passengerUuid);
            if (joinedPassenger != null) {
                joinedPassenger.discard();
            }
            Entity joinedRoot = level.getEntity(rootUuid);
            if (joinedRoot != null) {
                joinedRoot.discard();
            }
            passenger.discard();
            root.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "bottledmobs.display")
    public static void markedBlackHorseUsesColorBits(GameTestHelper helper) {
        Horse horse = EntityType.HORSE.create(helper.getLevel());
        helper.assertTrue(horse != null, "Could not create the horse fixture");
        try {
            CompoundTag data = saved(helper, horse);
            data.putInt("Variant", 260);
            horse.load(data);
            CompoundTag actual = saved(helper, horse);
            helper.assertTrue(actual.getInt("Variant") == 260, "The fixture lost its horse markings");
            helper.assertTrue(VariantRules.resolve(actual)
                            .map(name -> name.equals(Component.translatable("variant.bottledmobs.horse.black")))
                            .orElse(false),
                    "Horse Variant 260 did not resolve to black with its marking bits masked out");
            helper.assertTrue(actual.getInt("Variant") == 260, "Variant lookup mutated the saved horse data");
        } finally {
            horse.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "bottledmobs.display")
    public static void recessiveBrownPandaUsesBothGenes(GameTestHelper helper) {
        Panda panda = EntityType.PANDA.create(helper.getLevel());
        helper.assertTrue(panda != null, "Could not create the panda fixture");
        try {
            panda.setMainGene(Panda.Gene.BROWN);
            panda.setHiddenGene(Panda.Gene.NORMAL);
            CompoundTag normal = saved(helper, panda);
            helper.assertTrue(normal.getString("MainGene").equals("brown")
                            && normal.getString("HiddenGene").equals("normal"),
                    "The fixture does not contain the intended panda genes");
            helper.assertTrue(VariantRules.resolve(normal)
                            .map(name -> name.equals(Component.translatable("variant.bottledmobs.panda.normal")))
                            .orElse(false),
                    "A brown/normal panda was labeled brown instead of its normal phenotype");
            panda.setHiddenGene(Panda.Gene.BROWN);
            helper.assertTrue(VariantRules.resolve(saved(helper, panda))
                            .map(name -> name.equals(Component.translatable("variant.bottledmobs.panda.brown")))
                            .orElse(false),
                    "A brown/brown panda was not labeled brown");
        } finally {
            panda.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "bottledmobs.display")
    public static void customJockeyNamesSurviveGroupDisplay(GameTestHelper helper) {
        Chicken chicken = EntityType.CHICKEN.create(helper.getLevel());
        Zombie rider = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(chicken != null && rider != null, "Could not create the jockey fixture");
        try {
            rider.setBaby(true);
            helper.assertTrue(rider.startRiding(chicken, true), "Could not mount the jockey fixture");
            CompoundTag unnamed = saved(helper, chicken);
            StoredMob ordinary = new StoredMob(unnamed, "minecraft:chicken", "", "Chicken");
            helper.assertTrue(ordinary.displayName().equals(Component.translatable("tooltip.bottledmobs.chicken_jockey")),
                    "An unnamed chicken jockey lost its dedicated name");
            chicken.setCustomName(Component.literal("Captain Cluck"));
            StoredMob named = new StoredMob(saved(helper, chicken), "minecraft:chicken", "Captain Cluck", "Chicken");
            helper.assertTrue(named.displayName().getString().contains("Captain Cluck"),
                    "The dedicated jockey label replaced the root's custom name");
            helper.assertTrue(!named.displayName().equals(Component.translatable("tooltip.bottledmobs.chicken_jockey")),
                    "A named jockey still uses the unnamed-group label");
        } finally {
            rider.discard();
            chicken.discard();
        }
        helper.succeed();
    }

    private static CompoundTag saved(GameTestHelper helper, Entity entity) {
        CompoundTag data = MobNbt.save(entity);
        helper.assertTrue(data != null, "Could not save the display fixture");
        return data;
    }

    private static void settings(GameTestHelper helper, BottledMobBlockEntity jar, float scale, float height) {
        helper.assertTrue(jar.getScale() == scale && jar.getHeight() == height,
                "Old jar was not clamped to the current server display limits");
    }
}

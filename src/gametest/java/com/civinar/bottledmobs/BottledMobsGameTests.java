package com.civinar.bottledmobs;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(BottledMobs.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BottledMobsGameTests {
    private static final String BATCH = "bottledmobs.entities";
    private static final Vec3 RELEASE_SPOT = new Vec3(10.5D, 1.0D, 10.5D);

    @GameTest(template = "empty", batch = BATCH)
    public static void duplicatePassengerUuidKeepsBottleAndExistingEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity root = nestedGroup(helper, RELEASE_SPOT);
        StoredMob stored = store(helper, root);
        UUID rootId = root.getUUID();
        UUID middleId = root.getFirstPassenger().getUUID();
        UUID passengerId = root.getFirstPassenger().getFirstPassenger().getUUID();
        Chicken existing = create(helper, EntityType.CHICKEN, new Vec3(2.5D, 1.0D, 2.5D));
        existing.setUUID(passengerId);
        Player player = mockPlayer(helper);
        ItemStack bottle = bottle(player, stored);
        try {
            helper.assertTrue(level.addFreshEntity(existing), "Could not add the UUID-conflict fixture");
            release(helper, player, RELEASE_SPOT);

            assertStored(helper, bottle, stored);
            helper.assertTrue(level.getEntity(rootId) == null, "Failed release left its root in the level");
            helper.assertTrue(level.getEntity(middleId) == null, "Failed release left an earlier passenger in the level");
            helper.assertTrue(level.getEntity(passengerId) == existing && !existing.isRemoved(),
                    "Rollback removed or replaced the pre-existing entity with the duplicate UUID");
        } finally {
            discardStored(level, stored);
            discardGroup(root);
            existing.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void canceledPassengerRollsBackAndBottleCanRetry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity original = nestedGroup(helper, RELEASE_SPOT);
        StoredMob stored = store(helper, original);
        UUID rootId = original.getUUID();
        UUID middleId = original.getFirstPassenger().getUUID();
        UUID lastId = original.getFirstPassenger().getFirstPassenger().getUUID();
        Player player = mockPlayer(helper);
        ItemStack bottle = bottle(player, stored);
        List<Entity> attempted = new ArrayList<>();
        boolean[] canceledAfterEarlierMembersJoined = {false};
        Consumer<EntityJoinLevelEvent> cancelPassenger = event -> {
            if (event.getLevel() != level) {
                return;
            }
            UUID id = event.getEntity().getUUID();
            if (id.equals(rootId) || id.equals(middleId) || id.equals(lastId)) {
                attempted.add(event.getEntity());
            }
            if (id.equals(lastId)) {
                canceledAfterEarlierMembersJoined[0] = level.getEntity(rootId) != null
                        && level.getEntity(middleId) != null;
                event.setCanceled(true);
            }
        };
        try {
            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, cancelPassenger);
            try {
                release(helper, player, RELEASE_SPOT);
                helper.assertTrue(canceledAfterEarlierMembersJoined[0],
                        "The canceled passenger must be attempted after its ancestors have joined");
                helper.assertTrue(attempted.size() == 3, "Release did not attempt the entire three-member group");
                assertStored(helper, bottle, stored);
                assertAbsent(helper, stored);
                helper.assertTrue(attempted.get(0).isRemoved() && attempted.get(1).isRemoved(),
                        "Rollback did not discard the already-added root and passenger");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancelPassenger);
            }

            release(helper, player, RELEASE_SPOT);
            helper.assertTrue(content(bottle).isEmpty(), "A successful retry did not empty the bottle");
            assertReleasedGroup(helper, stored);
        } finally {
            discardStored(level, stored);
            attempted.forEach(Entity::discard);
            discardGroup(original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void remoteNestedPassengersReleaseAtTheirMounts(GameTestHelper helper) {
        Entity original = nestedGroup(helper, new Vec3(100_000.5D, 80.0D, -100_000.5D));
        StoredMob stored = store(helper, original);
        Player player = mockPlayer(helper);
        ItemStack bottle = bottle(player, stored);
        try {
            release(helper, player, RELEASE_SPOT);
            helper.assertTrue(content(bottle).isEmpty(), "Open-space release did not empty the bottle");
            assertReleasedGroup(helper, stored);
            Vec3 destination = helper.absoluteVec(RELEASE_SPOT);
            for (CompoundTag part : MobInfo.selfAndPassengers(stored.entityData())) {
                Entity entity = helper.getLevel().getEntity(part.getUUID("UUID"));
                helper.assertTrue(entity.position().distanceToSqr(destination) < 16.0D,
                        "Released group member retained its remote saved coordinates");
                helper.assertTrue(helper.getLevel().noCollision(entity, entity.getBoundingBox()),
                        "Released member collides at its mounted position");
                if (entity.getVehicle() != null) {
                    Vec3 expected = entity.getVehicle().getPassengerRidingPosition(entity)
                            .subtract(entity.getVehicleAttachmentPoint(entity.getVehicle()));
                    helper.assertTrue(entity.position().distanceToSqr(expected) < 1.0E-8D,
                            "Nested passenger was not positioned using its immediate vehicle");
                }
            }
        } finally {
            discardStored(helper.getLevel(), stored);
            discardGroup(original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void lowCeilingRejectsWholeGroupAndKeepsBottle(GameTestHelper helper) {
        // Template y=0 is helper y=1: helper coordinates start at the structure block.
        Vec3 releaseSpot = new Vec3(10.5D, 2.0D, 10.5D);
        Chicken root = create(helper, EntityType.CHICKEN, releaseSpot);
        Skeleton rider = create(helper, EntityType.SKELETON, releaseSpot);
        helper.assertTrue(rider.startRiding(root, true), "Could not mount the tall passenger");
        StoredMob stored = store(helper, root);
        Player player = mockPlayer(helper);
        ItemStack bottle = bottle(player, stored);
        try {
            // The root fits below the roof at the preferred height. Every offset is
            // covered, including the two higher candidates, but the rider cannot fit.
            roof(helper, true);
            helper.assertTrue(helper.getLevel().noCollision(root, root.getBoundingBox()),
                    "The low-ceiling fixture must leave room for the root on its own; pos=" + root.position()
                            + "; box=" + root.getBoundingBox() + "; collidingBlocks="
                            + BlockPos.betweenClosedStream(
                                    BlockPos.containing(root.getBoundingBox().minX, root.getBoundingBox().minY,
                                            root.getBoundingBox().minZ),
                                    BlockPos.containing(root.getBoundingBox().maxX, root.getBoundingBox().maxY,
                                            root.getBoundingBox().maxZ))
                                    .filter(pos -> root.isColliding(pos, helper.getLevel().getBlockState(pos)))
                                    .map(pos -> pos.toShortString() + "=" + helper.getLevel().getBlockState(pos))
                                    .toList());
            release(helper, player, releaseSpot);
            assertStored(helper, bottle, stored);
            assertAbsent(helper, stored);

            roof(helper, false);
            release(helper, player, releaseSpot);
            helper.assertTrue(content(bottle).isEmpty(), "Removing the roof did not allow the same bottle to release");
            assertReleasedGroup(helper, stored);
        } finally {
            roof(helper, false);
            discardStored(helper.getLevel(), stored);
            discardGroup(root);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void unsavableDescendantRejectsCaptureWithoutDiscardingGroup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig root = create(helper, EntityType.PIG, RELEASE_SPOT);
        Pig middle = create(helper, EntityType.PIG, RELEASE_SPOT);
        LightningBolt last = create(helper, EntityType.LIGHTNING_BOLT, RELEASE_SPOT);
        last.setVisualOnly(true);
        helper.assertTrue(middle.startRiding(root, true) && last.startRiding(middle, true),
                "Could not mount the unsavable descendant");
        Player player = mockPlayer(helper);
        ItemStack bottle = bottle(player, StoredMob.EMPTY);
        try {
            for (Entity member : List.of(root, middle, last)) {
                helper.assertTrue(level.addFreshEntity(member), "Could not add the capture fixture");
            }
            helper.assertTrue(MobNbt.save(root) == null,
                    "Saving silently omitted a descendant whose vanilla entity type cannot be serialized");
            item().capture(player, root, InteractionHand.MAIN_HAND);
            helper.assertTrue(content(bottle).isEmpty(), "Incomplete group data was stored in the bottle");
            for (Entity member : List.of(root, middle, last)) {
                helper.assertTrue(!member.isRemoved() && level.getEntity(member.getUUID()) == member,
                        "A rejected capture discarded a group member");
            }
            helper.assertTrue(middle.getVehicle() == root && last.getVehicle() == middle,
                    "A rejected capture changed the passenger hierarchy");
        } finally {
            last.discard();
            middle.discard();
            root.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void unknownNestedPassengerRejectsRestore(GameTestHelper helper) {
        Entity root = nestedGroup(helper, RELEASE_SPOT);
        CompoundTag data = store(helper, root).entityData();
        data.getList("Passengers", Tag.TAG_COMPOUND).getCompound(0)
                .getList("Passengers", Tag.TAG_COMPOUND).getCompound(0)
                .putString("id", "bottledmobs:missing_test_entity");
        Entity restored = null;
        try {
            restored = MobNbt.create(data, helper.getLevel());
            helper.assertTrue(restored == null, "Restore accepted a group after silently dropping an unknown descendant");
        } finally {
            if (restored != null) {
                discardGroup(restored);
            }
            discardGroup(root);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void dragonPartCaptureStoresAndRemovesParentDragon(GameTestHelper helper) {
        EnderDragon dragon = create(helper, EntityType.ENDER_DRAGON, RELEASE_SPOT);
        Player player = mockPlayer(helper);
        ItemStack bottle = bottle(player, StoredMob.EMPTY);
        try {
            helper.assertTrue(helper.getLevel().addFreshEntity(dragon), "Could not add the dragon fixture");
            helper.assertTrue(item().capture(player, dragon.getSubEntities()[0], InteractionHand.MAIN_HAND),
                    "Clicking a dragon part was not handled");
            StoredMob stored = content(bottle);
            helper.assertTrue(!stored.isEmpty(), "Dragon-part capture produced an empty bottle");
            helper.assertTrue(stored.entityId().equals("minecraft:ender_dragon"), "Captured a part instead of its parent type");
            helper.assertTrue(stored.entityData().getUUID("UUID").equals(dragon.getUUID()),
                    "Dragon-part capture stored a different entity");
            helper.assertTrue(dragon.isRemoved() && helper.getLevel().getEntity(dragon.getUUID()) == null,
                    "Captured parent dragon remains in the level");
            for (Entity part : dragon.getSubEntities()) {
                helper.assertTrue(helper.getLevel().getEntityOrPart(part.getId()) == null,
                        "A captured dragon left a registered multipart hitbox");
            }
            Entity restored = MobNbt.create(stored.entityData(), helper.getLevel());
            try {
                helper.assertTrue(restored instanceof EnderDragon, "Captured parent data cannot restore a dragon");
            } finally {
                if (restored != null) {
                    discardGroup(restored);
                }
            }
        } finally {
            dragon.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void villagerInteractionCapturesBeforeVanillaConsumesClick(GameTestHelper helper) {
        Villager villager = create(helper, EntityType.VILLAGER, RELEASE_SPOT);
        villager.setOffers(new MerchantOffers());
        Player player = mockPlayer(helper);
        ItemStack bottle = bottle(player, StoredMob.EMPTY);
        try {
            helper.assertTrue(helper.getLevel().addFreshEntity(villager), "Could not add the villager fixture");
            InteractionResult result = player.interactOn(villager, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "Villager capture did not consume the interaction");
            assertCaptured(helper, bottle, villager);
            helper.assertTrue(villager.getTradingPlayer() == null, "Capture also opened the vanilla trading interaction");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void ownedWolfInteractionCapturesWithoutTogglingSitting(GameTestHelper helper) {
        FakePlayer owner = owner(helper);
        Wolf wolf = ownedWolf(helper, owner);
        ItemStack bottle = bottle(owner, StoredMob.EMPTY);
        try {
            helper.assertTrue(wolf.isOwnedBy(owner), "The wolf fixture must resolve its owner from the server level");
            InteractionResult result = owner.interactOn(wolf, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(), "Owned-wolf capture did not consume the interaction");
            assertCaptured(helper, bottle, wolf);
            CompoundTag data = content(bottle).entityData();
            helper.assertTrue(data.getUUID("Owner").equals(owner.getUUID()), "Capture lost the wolf's owner");
            helper.assertTrue(!data.getBoolean("Sitting") && !wolf.isOrderedToSit(),
                    "Vanilla sit interaction ran before the wolf was captured");
        } finally {
            wolf.discard();
            helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void specificOffhandInteractionCapturesOwnedWolf(GameTestHelper helper) {
        FakePlayer owner = owner(helper);
        Wolf wolf = ownedWolf(helper, owner);
        ItemStack bottle = new ItemStack(BottledMobs.BOTTLED_MOB_ITEM.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        owner.setItemInHand(InteractionHand.OFF_HAND, bottle);
        try {
            InteractionResult result = CommonHooks.onInteractEntityAt(owner, wolf,
                    new Vec3(0.0D, 0.5D, 0.0D), InteractionHand.OFF_HAND);
            helper.assertTrue(result != null && result.consumesAction(),
                    "The specific interaction hook did not handle the offhand bottle");
            assertCaptured(helper, bottle, wolf);
            helper.assertTrue(owner.getMainHandItem().is(Items.STICK), "Offhand capture changed the main-hand item");
            helper.assertTrue(!content(bottle).entityData().getBoolean("Sitting"),
                    "Specific interaction changed the wolf's sitting state");
        } finally {
            wolf.discard();
            helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void commandBottlesHaveIndependentUuidsAndBothRelease(GameTestHelper helper)
            throws CommandSyntaxException {
        StoredMob first = BottledMobsCommand.createMob(EntityType.PIG, helper.getLevel(), "First recipient");
        StoredMob second = BottledMobsCommand.createMob(EntityType.PIG, helper.getLevel(), "Second recipient");
        UUID firstId = first.entityData().getUUID("UUID");
        UUID secondId = second.entityData().getUUID("UUID");
        Player player = mockPlayer(helper);
        try {
            helper.assertTrue(!firstId.equals(secondId), "Command reused the same entity UUID for two recipients");
            ItemStack firstBottle = bottle(player, first);
            release(helper, player, new Vec3(6.5D, 1.0D, 6.5D));
            ItemStack secondBottle = bottle(player, second);
            release(helper, player, new Vec3(13.5D, 1.0D, 13.5D));
            helper.assertTrue(content(firstBottle).isEmpty() && content(secondBottle).isEmpty(),
                    "Both command-created bottles must release successfully");
            Entity firstPig = helper.getLevel().getEntity(firstId);
            Entity secondPig = helper.getLevel().getEntity(secondId);
            helper.assertTrue(firstPig instanceof Pig && secondPig instanceof Pig && firstPig != secondPig,
                    "Command-created mobs did not coexist in the level");
            helper.assertTrue(firstPig.getName().getString().equals("First recipient")
                            && secondPig.getName().getString().equals("Second recipient"),
                    "Command-created bottles lost their individual custom names");
        } finally {
            discardStored(helper.getLevel(), first);
            discardStored(helper.getLevel(), second);
        }
        helper.succeed();
    }

    private static BottledMobItem item() {
        return (BottledMobItem) BottledMobs.BOTTLED_MOB_ITEM.get();
    }

    private static Player mockPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 1.0D, 2.5D)));
        return player;
    }

    private static FakePlayer owner(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bottle-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(2.5D, 1.0D, 2.5D)));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static Wolf ownedWolf(GameTestHelper helper, Player owner) {
        Wolf wolf = create(helper, EntityType.WOLF, RELEASE_SPOT);
        wolf.setTame(true, true);
        wolf.setOwnerUUID(owner.getUUID());
        wolf.setOrderedToSit(false);
        helper.assertTrue(helper.getLevel().addFreshEntity(wolf), "Could not add the owned-wolf fixture");
        return wolf;
    }

    private static ItemStack bottle(Player player, StoredMob stored) {
        ItemStack bottle = new ItemStack(BottledMobs.BOTTLED_MOB_ITEM.get());
        bottle.set(BottledMobs.STORED_MOB.get(), stored);
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        return bottle;
    }

    private static StoredMob content(ItemStack bottle) {
        return bottle.getOrDefault(BottledMobs.STORED_MOB.get(), StoredMob.EMPTY);
    }

    private static void release(GameTestHelper helper, Player player, Vec3 relativeSpot) {
        Vec3 spot = helper.absoluteVec(relativeSpot);
        BlockPos floor = BlockPos.containing(spot).below();
        InteractionResult result = item().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(spot, Direction.UP, floor, false)));
        helper.assertTrue(result.consumesAction(), "Bottle use did not handle the release interaction");
    }

    private static <T extends Entity> T create(GameTestHelper helper, EntityType<T> type, Vec3 relativePos) {
        T entity = type.create(helper.getLevel());
        helper.assertTrue(entity != null, "Could not create " + EntityType.getKey(type));
        Vec3 pos = helper.absoluteVec(relativePos);
        entity.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        entity.setNoGravity(true);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        return entity;
    }

    private static Entity nestedGroup(GameTestHelper helper, Vec3 relativePos) {
        Pig root = create(helper, EntityType.PIG, relativePos);
        Pig middle = create(helper, EntityType.PIG, relativePos);
        Chicken last = create(helper, EntityType.CHICKEN, relativePos);
        helper.assertTrue(middle.startRiding(root, true) && last.startRiding(middle, true),
                "Could not create a nested three-member passenger group");
        return root;
    }

    private static StoredMob store(GameTestHelper helper, Entity entity) {
        CompoundTag data = MobNbt.save(entity);
        helper.assertTrue(data != null, "Could not save the valid entity fixture");
        String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
        return new StoredMob(data, EntityType.getKey(entity.getType()).toString(), name,
                entity.getDisplayName().getString());
    }

    private static void assertStored(GameTestHelper helper, ItemStack bottle, StoredMob expected) {
        helper.assertTrue(bottle.getCount() == 1, "Failed release consumed the bottle item");
        helper.assertTrue(content(bottle).equals(expected), "Failed release changed or cleared the stored mob data");
    }

    private static void assertAbsent(GameTestHelper helper, StoredMob stored) {
        for (CompoundTag part : MobInfo.selfAndPassengers(stored.entityData())) {
            helper.assertTrue(helper.getLevel().getEntity(part.getUUID("UUID")) == null,
                    "Failed release left a member in the level: " + part.getString("id"));
        }
    }

    private static void assertReleasedGroup(GameTestHelper helper, StoredMob stored) {
        Entity root = helper.getLevel().getEntity(stored.entityData().getUUID("UUID"));
        helper.assertTrue(root != null, "Released root is missing from the level");
        List<Entity> members = root.getSelfAndPassengers().toList();
        List<CompoundTag> saved = MobInfo.selfAndPassengers(stored.entityData());
        helper.assertTrue(members.size() == saved.size(), "Release changed the passenger hierarchy size");
        for (int index = 0; index < saved.size(); index++) {
            UUID id = saved.get(index).getUUID("UUID");
            helper.assertTrue(members.get(index).getUUID().equals(id)
                            && helper.getLevel().getEntity(id) == members.get(index),
                    "A released member is missing, reordered, or not registered in the server level");
        }
    }

    private static void assertCaptured(GameTestHelper helper, ItemStack bottle, Entity target) {
        StoredMob stored = content(bottle);
        helper.assertTrue(!stored.isEmpty(), "Real player interaction did not fill the bottle");
        helper.assertTrue(stored.entityData().getUUID("UUID").equals(target.getUUID()),
                "Real player interaction captured the wrong entity");
        helper.assertTrue(target.isRemoved() && helper.getLevel().getEntity(target.getUUID()) == null,
                "Captured entity remains in the level");
    }

    private static void roof(GameTestHelper helper, boolean present) {
        for (int x = 8; x <= 12; x++) {
            for (int y = 3; y <= 6; y++) {
                for (int z = 8; z <= 12; z++) {
                    helper.setBlock(x, y, z, present ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    private static void discardStored(ServerLevel level, StoredMob stored) {
        List<CompoundTag> parts = MobInfo.selfAndPassengers(stored.entityData());
        for (int index = parts.size() - 1; index >= 0; index--) {
            Entity entity = level.getEntity(parts.get(index).getUUID("UUID"));
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static void discardGroup(Entity root) {
        List<Entity> members = root.getSelfAndPassengers().toList();
        for (int index = members.size() - 1; index >= 0; index--) {
            members.get(index).discard();
        }
    }
}

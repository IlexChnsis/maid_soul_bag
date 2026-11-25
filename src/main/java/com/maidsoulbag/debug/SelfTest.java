package com.maidsoulbag.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.logic.BagActions;
import com.maidsoulbag.logic.BagUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Head-less self test, used to validate the storage round trip on a dedicated server.
 * Run with: /maidbag selftest
 */
public final class SelfTest {
    private SelfTest() {
    }

    public static int run(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = server.overworld();
        UUID testId = UUID.nameUUIDFromBytes("maid_soul_bag_selftest".getBytes(StandardCharsets.UTF_8));
        UUID otherId = UUID.nameUUIDFromBytes("maid_soul_bag_other".getBytes(StandardCharsets.UTF_8));
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(testId, "MaidBagSelfTest"));
        player.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
        // Remove leftovers of a previous (possibly failed) run so the stage numbers stay deterministic.
        discardTestMaids(server, testId, otherId);

        ItemStack bag = new ItemStack(MaidSoulBag.SOUL_BAG.get());
        String slots = top.theillusivec4.curios.api.CuriosApi.getItemStackSlots(bag, level).keySet().toString();
        log(source, "stage=0 bagItemSlots=" + slots + " bagIsSoulBag=" + bag.is(MaidSoulBag.SOUL_BAG.get()));

        // 1) recall own maid
        EntityMaid ownMaid = spawnMaid(level, player, player.getX() + 2.5D, player.getZ() + 0.5D);
        BagActions.recallFrom(player, bag);
        int stored = BagUtil.storedCount(bag);
        boolean ownGone = !ownMaid.isAlive() || ownMaid.isRemoved();
        log(source, "stage=1 recall stored=" + stored + " ownMaidGone=" + ownGone);

        // 2) release: soul spell must come back as an empty soul spell
        BagActions.releaseFrom(player, bag);
        int storedAfterRelease = BagUtil.storedCount(bag);
        boolean emptySlabPresent = BagUtil.readContents(bag).stream()
                .anyMatch(stack -> stack.is(InitItems.SMART_SLAB_EMPTY.get()));
        List<EntityMaid> near = level.getEntitiesOfClass(EntityMaid.class,
                new AABB(player.blockPosition()).inflate(16.0D),
                entity -> testId.equals(entity.getOwnerUUID()));
        boolean sameUuid = near.stream().anyMatch(entity -> entity.getUUID().equals(ownMaid.getUUID()));
        log(source, "stage=2 release stored=" + storedAfterRelease + " emptySlabKept=" + emptySlabPresent
                + " maidsNearby=" + near.size() + " sameUuid=" + sameUuid);

        // 3) recall again: must recycle the empty soul spell instead of using a fresh slot
        BagActions.recallFrom(player, bag);
        List<ItemStack> afterRecycle = BagUtil.readContents(bag);
        long emptySlabs = afterRecycle.stream().filter(s -> s.is(InitItems.SMART_SLAB_EMPTY.get())).count();
        log(source, "stage=3 recallAgain stored=" + BagUtil.storedCount(bag) + " ownMaidGone="
                + (!ownMaid.isAlive() || ownMaid.isRemoved()) + " emptySlabsLeft=" + emptySlabs
                + " (expect 0 = empty soul spell was reused)");

        // 4) a maid owned by someone else must not be recalled
        FakePlayer other = FakePlayerFactory.get(level, new GameProfile(otherId, "MaidBagOther"));
        other.setPos(player.getX() + 6.0D, player.getY(), player.getZ() + 6.0D);
        EntityMaid otherMaid = spawnMaid(level, other, other.getX() + 1.5D, other.getZ() + 0.5D);
        BagActions.recallFrom(player, bag);
        boolean otherStillThere = otherMaid.isAlive() && !otherMaid.isRemoved();
        log(source, "stage=4 foreignRecall stored=" + BagUtil.storedCount(bag) + " otherMaidStillThere=" + otherStillThere);

        // 5) a foreign charm must not be released; filler soul spells must be ignored
        ItemStack foreignCharm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(foreignCharm, otherMaid);
        List<ItemStack> contents = BagUtil.readContents(bag);
        int slot = BagUtil.firstCharmSlot(contents);
        contents.set(slot, foreignCharm);
        contents.set(Math.min(slot + 1, BagUtil.CAPACITY - 1), InitItems.SMART_SLAB_INIT.get().getDefaultInstance());
        BagUtil.writeContents(bag, contents);
        int before = BagUtil.storedCount(bag);
        BagActions.releaseFrom(player, bag);
        int after = BagUtil.storedCount(bag);
        log(source, "stage=5 foreignRelease before=" + before + " after=" + after + " (own released, foreign kept)");

        // 6) broom hook: two own charms with different favorability, the lower one must be released
        EntityMaid lowFav = spawnMaid(level, player, player.getX() - 3.5D, player.getZ() - 0.5D);
        lowFav.setFavorability(10);
        EntityMaid highFav = spawnMaid(level, player, player.getX() - 5.5D, player.getZ() - 0.5D);
        highFav.setFavorability(50);
        ItemStack lowCharm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(lowCharm, lowFav);
        ItemStack highCharm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(highCharm, highFav);
        lowFav.discard();
        highFav.discard();

        ItemStack broomBag = new ItemStack(MaidSoulBag.SOUL_BAG.get());
        List<ItemStack> broomContents = BagUtil.readContents(broomBag);
        broomContents.set(0, lowCharm);
        broomContents.set(1, highCharm);
        BagUtil.writeContents(broomBag, broomContents);
        UUID released = BagActions.releaseLowestFavorabilityFrom(player, broomBag,
                player.getX() + 4.5D, player.getY(), player.getZ(), 0.0F);
        boolean releasedLow = released != null && released.equals(BagUtil.maidIdOf(lowCharm));
        log(source, "stage=6 broomRelease releasedLowFavorability=" + releasedLow
                + " stored=" + BagUtil.storedCount(broomBag) + " (expect 1)");

        // 7) dismount hook: recall that maid back into the bag
        BagActions.recallMaidByIdTo(player, broomBag, released);
        boolean broomMaidGone = released != null && BagActions.findMaid(player, released) == null;
        log(source, "stage=7 broomRecall stored=" + BagUtil.storedCount(broomBag)
                + " maidGone=" + broomMaidGone + " (expect 2/true)");

        // 8) broom item goes to the first free inventory slot
        BagActions.giveBroomItem(player);
        boolean hasBroom = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(InitItems.BROOM.get())) {
                hasBroom = true;
                break;
            }
        }
        log(source, "stage=8 broomItem hasBroomInInventory=" + hasBroom);

        // 9) a maid riding the same vehicle as the player must not be recalled
        com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom broom =
                com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom.TYPE.create(level);
        broom.moveTo(player.getX() - 8.5D, player.getY(), player.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(broom);
        EntityMaid ridingMaid = spawnMaid(level, player, player.getX() - 8.5D, player.getZ() + 1.5D);
        boolean maidOnBroom = ridingMaid.startRiding(broom);
        boolean playerOnBroom = player.startRiding(broom, true);
        ItemStack rideBag = new ItemStack(MaidSoulBag.SOUL_BAG.get());
        BagActions.recallFrom(player, rideBag);
        boolean ridingMaidStillThere = ridingMaid.isAlive() && !ridingMaid.isRemoved();
        log(source, "stage=9 sameVehicleSkip maidOnBroom=" + maidOnBroom + " playerOnBroom=" + playerOnBroom
                + " ridingMaidKept=" + ridingMaidStillThere
                + (playerOnBroom ? " (expect true)" : " (fake player cannot mount; needs in-game check)"));

        // Fresh fixtures for the wheel stages: two stored charms with custom names, one live maid,
        // and one maid riding a broom (player not mounted, she must still be refused).
        player.stopRiding();
        broom.discard();
        ridingMaid.discard();

        EntityMaid storedLow = spawnMaid(level, player, player.getX() + 3.0D, player.getZ());
        storedLow.setFavorability(7);
        storedLow.setCustomName(Component.literal("轮盘甲"));
        EntityMaid storedHigh = spawnMaid(level, player, player.getX() + 4.0D, player.getZ());
        storedHigh.setFavorability(55);
        storedHigh.setCustomName(Component.literal("轮盘乙"));
        ItemStack wheelLowCharm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(wheelLowCharm, storedLow);
        ItemStack wheelHighCharm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(wheelHighCharm, storedHigh);
        storedLow.discard();
        storedHigh.discard();

        EntityMaid wheelLiveMaid = spawnMaid(level, player, player.getX() + 2.0D, player.getZ() + 2.0D);
        wheelLiveMaid.setCustomName(Component.literal("轮盘丙"));

        com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom wheelBroom =
                com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom.TYPE.create(level);
        wheelBroom.moveTo(player.getX() - 10.5D, player.getY(), player.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(wheelBroom);
        EntityMaid wheelRidingMaid = spawnMaid(level, player, player.getX() - 10.5D, player.getZ() + 1.5D);
        boolean wheelMaidMounted = wheelRidingMaid.startRiding(wheelBroom);

        ItemStack wheelBag = new ItemStack(MaidSoulBag.SOUL_BAG.get());
        List<ItemStack> wheelContents = BagUtil.readContents(wheelBag);
        wheelContents.set(0, wheelLowCharm);
        wheelContents.set(1, wheelHighCharm);
        BagUtil.writeContents(wheelBag, wheelContents);

        // 10) wheel row list: 2 stored + 1 released + 1 riding
        BagActions.resetCooldown(testId);
        List<com.maidsoulbag.network.WheelEntryData> state =
                com.maidsoulbag.logic.WheelService.buildState(player, wheelBag);
        long wheelStored = state.stream().filter(com.maidsoulbag.network.WheelEntryData::stored).count();
        long wheelReleasedRows = state.stream()
                .filter(e -> e.status() == com.maidsoulbag.network.WheelEntryData.STATUS_RELEASED).count();
        long wheelRidingRows = state.stream()
                .filter(e -> e.status() == com.maidsoulbag.network.WheelEntryData.STATUS_RIDING).count();
        boolean nameParsed = state.stream()
                .anyMatch(e -> e.stored() && e.nameJson().contains("轮盘甲"));
        log(source, "stage=10 wheelState rows=" + state.size() + " stored=" + wheelStored
                + " released=" + wheelReleasedRows + " riding=" + wheelRidingRows
                + " nameParsed=" + nameParsed + " maidMounted=" + wheelMaidMounted
                + " (expect 4/2/1/1/true/true)");

        // 11) wheel release by slot: the stored charm must spawn its maid at the owner position
        BagActions.resetCooldown(testId);
        boolean wheelReleased = com.maidsoulbag.logic.WheelService.releaseFromSlot(player, wheelBag, 0);
        EntityMaid wheelReleasedMaid = BagActions.findMaid(player, BagUtil.maidIdOf(wheelLowCharm));
        log(source, "stage=11 wheelRelease ok=" + wheelReleased + " maidBack=" + (wheelReleasedMaid != null)
                + " storedLeft=" + BagUtil.storedCount(wheelBag) + " (expect true/true/1)");

        // 12) wheel recall by entity id: she goes back into the recycled empty soul spell of the same bag
        BagActions.resetCooldown(testId);
        boolean wheelRecalled = wheelReleasedMaid != null
                && com.maidsoulbag.logic.WheelService.recallEntity(player, wheelBag, wheelReleasedMaid.getId());
        boolean wheelMaidGone = BagActions.findMaid(player, BagUtil.maidIdOf(wheelLowCharm)) == null;
        log(source, "stage=12 wheelRecall ok=" + wheelRecalled + " maidGone=" + wheelMaidGone
                + " stored=" + BagUtil.storedCount(wheelBag) + " (expect true/true/2)");

        // 13) a riding maid must be refused by the wheel and stay in the world
        BagActions.resetCooldown(testId);
        boolean ridingRefused = !com.maidsoulbag.logic.WheelService.recallEntity(
                player, wheelBag, wheelRidingMaid.getId());
        boolean ridingStillThere = wheelRidingMaid.isAlive() && !wheelRidingMaid.isRemoved();
        log(source, "stage=13 wheelRidingRefused refused=" + ridingRefused + " maidKept=" + ridingStillThere
                + " (expect true/true)");

        // 14) rename a live maid: set, then clear
        BagActions.resetCooldown(testId);
        boolean renamed = com.maidsoulbag.logic.WheelService.renameMaid(player, wheelLiveMaid.getId(), " 轮盘改名 ");
        boolean nameOk = wheelLiveMaid.getCustomName() != null
                && "轮盘改名".equals(wheelLiveMaid.getCustomName().getString());
        boolean cleared = com.maidsoulbag.logic.WheelService.renameMaid(player, wheelLiveMaid.getId(), "");
        boolean clearOk = wheelLiveMaid.getCustomName() == null;
        log(source, "stage=14 wheelRename renamed=" + renamed + " nameOk=" + nameOk
                + " cleared=" + cleared + " clearOk=" + clearOk + " (expect all true)");

        // 15) brooms may be stored, but the maid lists / actions must ignore them
        EntityMaid broomFilterMaid = spawnMaid(level, player, player.getX() + 2.5D, player.getZ() - 2.0D);
        ItemStack filterCharm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(filterCharm, broomFilterMaid);
        broomFilterMaid.discard();
        ItemStack broomStoreBag = new ItemStack(MaidSoulBag.SOUL_BAG.get());
        List<ItemStack> broomStoreContents = BagUtil.readContents(broomStoreBag);
        broomStoreContents.set(0, new ItemStack(InitItems.BROOM.get()));
        broomStoreContents.set(1, new ItemStack(InitItems.BROOM.get()));
        broomStoreContents.set(2, filterCharm);
        BagUtil.writeContents(broomStoreBag, broomStoreContents);
        List<com.maidsoulbag.network.WheelEntryData> broomState =
                com.maidsoulbag.logic.WheelService.buildState(player, broomStoreBag);
        long broomMaidRows = broomState.stream()
                .filter(com.maidsoulbag.network.WheelEntryData::stored).count();
        log(source, "stage=15 broomFilter wheelRows=" + broomState.size()
                + " maidRows=" + broomMaidRows + " (expect 3/1: 1 stored maid + 2 live maids, 2 brooms filtered)");

        // 16) placing a broom from the bag consumes exactly one broom; blocked targets place nothing
        net.minecraft.core.BlockPos broomPos = player.blockPosition().offset(3, 0, 0);
        net.minecraft.core.BlockPos blockedPos = player.blockPosition().offset(2, 0, 0);
        EntityMaid blocker = spawnMaid(level, player, blockedPos.getX() + 0.5D, blockedPos.getZ() + 0.5D);
        boolean broomPlaced = com.maidsoulbag.logic.BroomPlacer.placeAt(player, broomStoreBag, broomPos);
        boolean broomSpawned = !level.getEntitiesOfClass(
                com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom.class,
                new AABB(broomPos).inflate(2.0D),
                placedBroom -> testId.equals(placedBroom.getOwnerUUID())).isEmpty();
        int broomsAfterPlace = broomCount(broomStoreBag);
        boolean blockedRefused = !com.maidsoulbag.logic.BroomPlacer.placeAt(
                player, broomStoreBag, blockedPos);
        int broomsAfterBlocked = broomCount(broomStoreBag);
        log(source, "stage=16 broomPlace placed=" + broomPlaced + " spawned=" + broomSpawned
                + " broomsLeft=" + broomsAfterPlace + " blockedRefused=" + blockedRefused
                + " broomsAfterBlocked=" + broomsAfterBlocked + " (expect true/true/1/true/1)");
        blocker.discard();
        level.getEntitiesOfClass(com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom.class,
                new AABB(player.blockPosition()).inflate(8.0D),
                leftover -> testId.equals(leftover.getOwnerUUID()))
                .forEach(net.minecraft.world.entity.Entity::discard);

        // 17) broom pickup: the bag gets it first; a full bag refuses and falls back to the inventory
        ItemStack returnBag = new ItemStack(MaidSoulBag.SOUL_BAG.get());
        boolean broomToBag = BagActions.storeBroomInBag(player, returnBag);
        int broomInBag = broomCount(returnBag);
        List<ItemStack> returnContents = BagUtil.readContents(returnBag);
        for (int i = 0; i < BagUtil.CAPACITY; i++) {
            if (returnContents.get(i).isEmpty()) {
                returnContents.set(i, InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance());
            }
        }
        BagUtil.writeContents(returnBag, returnContents);
        boolean fullRefused = !BagActions.storeBroomInBag(player, returnBag);
        log(source, "stage=17 broomReturn toBag=" + broomToBag + " broomInBag=" + broomInBag
                + " fullRefused=" + fullRefused + " (expect true/1/true)");

        wheelLiveMaid.discard();
        wheelRidingMaid.stopRiding();
        wheelBroom.discard();
        wheelRidingMaid.discard();
        otherMaid.discard();
        return 1;
    }

    private static int broomCount(ItemStack bag) {
        int count = 0;
        for (ItemStack stack : BagUtil.readContents(bag)) {
            if (BagUtil.isBroom(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void discardTestMaids(MinecraftServer server, UUID... owners) {
        List<UUID> ids = List.of(owners);
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid
                        && maid.getOwnerUUID() != null
                        && ids.contains(maid.getOwnerUUID())) {
                    maid.stopRiding();
                    maid.discard();
                }
            }
        }
    }

    private static EntityMaid spawnMaid(ServerLevel level, FakePlayer owner, double x, double z) {
        EntityMaid maid = InitEntities.MAID.get().create(level);
        maid.moveTo(x, owner.getY(), z, 0.0F, 0.0F);
        maid.tame(owner);
        level.addFreshEntity(maid);
        return maid;
    }

    private static void log(CommandSourceStack source, String message) {
        MaidSoulBag.LOGGER.info("[MaidSoulBag][selftest] {}", message);
        source.sendSuccess(() -> Component.literal("[selftest] " + message), false);
    }
}

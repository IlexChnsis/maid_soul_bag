package com.maidsoulbag.logic;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import com.maidsoulbag.MaidSoulBag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side release / recall implementation.
 */
public final class BagActions {
    private static final int COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

    private BagActions() {
    }

    public static void open(ServerPlayer player) {
        if (player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu chest
                && chest.getContainer() instanceof com.maidsoulbag.storage.BagContainer) {
            return;
        }
        ItemStack bag = BagUtil.findBag(player).orElse(null);
        if (bag == null) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.not_equipped"));
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> com.maidsoulbag.storage.BagContainer.createMenu(id, inventory),
                Component.translatable("container.maid_soul_bag")));
    }

    public static void release(ServerPlayer player) {
        if (isOnCooldown(player)) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.cooldown"));
            return;
        }
        ItemStack bag = BagUtil.findBag(player).orElse(null);
        if (bag == null) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.not_equipped"));
            return;
        }
        releaseFrom(player, bag);
    }

    /** Core release logic, also used by the head-less self test with an explicit bag stack. */
    public static void releaseFrom(ServerPlayer player, ItemStack bag) {
        closeBagMenu(player);
        List<ItemStack> contents = BagUtil.readContents(bag);
        int released = 0;
        int skippedOwner = 0;
        int failed = 0;

        for (int i = 0; i < BagUtil.CAPACITY; i++) {
            ItemStack charm = contents.get(i);
            if (!BagUtil.isSoulCharm(charm)) {
                // Empty slots and filler soul spells are kept untouched.
                continue;
            }
            UUID owner = BagUtil.ownerOf(charm);
            if (owner == null || !owner.equals(player.getUUID())) {
                skippedOwner++;
                continue;
            }
            if (spawnMaid(player, charm)) {
                // Same semantics as TLM: a used soul spell item becomes an empty soul spell.
                contents.set(i, InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance());
                released++;
            } else {
                failed++;
            }
        }

        if (released > 0) {
            BagUtil.writeContents(bag, contents);
            BagUtil.saveBag(player, bag);
        }

        if (released == 0) {
            if (skippedOwner > 0) {
                player.sendSystemMessage(Component.translatable("message.maid_soul_bag.only_others"));
            } else if (failed > 0) {
                player.sendSystemMessage(Component.translatable("message.maid_soul_bag.failed_place", failed));
            } else {
                player.sendSystemMessage(Component.translatable("message.maid_soul_bag.empty"));
            }
            return;
        }
        player.sendSystemMessage(Component.translatable("message.maid_soul_bag.released", released));
        if (skippedOwner > 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.skipped_owner", skippedOwner));
        }
        if (failed > 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.failed_place", failed));
        }
    }

    public static void recall(ServerPlayer player) {
        if (isOnCooldown(player)) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.cooldown"));
            return;
        }
        ItemStack bag = BagUtil.findBag(player).orElse(null);
        if (bag == null) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.not_equipped"));
            return;
        }
        recallFrom(player, bag);
    }

    /** Core recall logic, also used by the head-less self test with an explicit bag stack. */
    public static void recallFrom(ServerPlayer player, ItemStack bag) {
        closeBagMenu(player);
        List<ItemStack> contents = BagUtil.readContents(bag);
        if (BagUtil.firstCharmSlot(contents) < 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.full", BagUtil.CAPACITY));
            return;
        }

        int recalled = 0;
        int skippedRiding = 0;
        int skippedSpecial = 0;
        int skippedBlocked = 0;
        int noRoom = 0;

        // Do NOT query with a huge AABB: Sable (Aeronautics) aborts entity lookups wider than 100k blocks
        // and returns an empty list. Iterate the loaded entities of every level instead.
        List<EntityMaid> candidates = new java.util.ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid
                        && maid.isAlive() && !maid.isRemoved()
                        && player.getUUID().equals(maid.getOwnerUUID())) {
                    candidates.add(maid);
                }
            }
        }

        for (EntityMaid maid : candidates) {
            int slot = BagUtil.firstCharmSlot(contents);
            if (slot < 0) {
                noRoom++;
                continue;
            }
            // A maid riding the same vehicle as the player (e.g. the broom) must be left alone.
            if (player.getVehicle() != null && maid.getVehicle() == player.getVehicle()) {
                skippedRiding++;
                continue;
            }
            if (maid.isInvulnerable()) {
                skippedSpecial++;
                continue;
            }
            ItemStack charm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
            ItemSmartSlab.storeMaidData(charm, maid);
            if (!charm.has(InitDataComponent.MAID_INFO.get())) {
                skippedSpecial++;
                continue;
            }
            maid.discard();
            if (!maid.isRemoved()) {
                // Another mod blocked the removal: do not duplicate the maid.
                MaidSoulBag.LOGGER.warn("[MaidSoulBag] recall blocked by another mod for maid {}", maid.getUUID());
                skippedBlocked++;
                continue;
            }
            contents.set(slot, charm);
            recalled++;
        }

        if (recalled > 0) {
            BagUtil.writeContents(bag, contents);
            BagUtil.saveBag(player, bag);
        }

        if (recalled == 0 && skippedRiding == 0 && skippedSpecial == 0
                && skippedBlocked == 0 && noRoom == 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.recall_none"));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.maid_soul_bag.recalled", recalled));
        if (skippedRiding > 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.recall_riding", skippedRiding));
        }
        if (skippedSpecial > 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.recall_special", skippedSpecial));
        }
        if (skippedBlocked > 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.recall_blocked", skippedBlocked));
        }
        if (noRoom > 0) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.recall_full", noRoom));
        }
    }

    private static boolean spawnMaid(ServerPlayer player, ItemStack charm) {
        return spawnMaidAt(player, charm,
                player.getX(), player.getY(), player.getZ(), player.getYRot());
    }

    /** Spawn a stored maid at an arbitrary position (release key, broom hook). */
    public static boolean spawnMaidAt(ServerPlayer player, ItemStack charm, double x, double y, double z, float yaw) {
        ServerLevel level = player.serverLevel();
        CompoundTag tag = BagUtil.maidTag(charm);
        if (tag == null) {
            MaidSoulBag.LOGGER.warn("[MaidSoulBag] release failed: soul spell has no maid data");
            return false;
        }
        EntityMaid maid = InitEntities.MAID.get().create(level);
        if (maid == null) {
            MaidSoulBag.LOGGER.warn("[MaidSoulBag] release failed: could not create maid entity");
            return false;
        }
        NeoForge.EVENT_BUS.post(new MaidAndItemTransformEvent.ToMaid(maid, charm, tag));
        maid.load(tag);

        UUID uuid = maid.getUUID();
        for (ServerLevel other : player.server.getAllLevels()) {
            if (other.getEntity(uuid) != null) {
                MaidSoulBag.LOGGER.warn("[MaidSoulBag] release failed: maid {} already exists in {}",
                        uuid, other.dimension().location());
                return false;
            }
        }

        maid.moveTo(x, y, z, yaw, 0.0F);
        if (!level.addFreshEntity(maid)) {
            MaidSoulBag.LOGGER.warn("[MaidSoulBag] release failed: addFreshEntity returned false for {}", uuid);
            return false;
        }
        level.sendParticles(ParticleTypes.POOF, maid.getX(), maid.getY() + 0.6D, maid.getZ(),
                12, 0.3D, 0.4D, 0.3D, 0.02D);
        level.playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 1.0F,
                0.9F + level.random.nextFloat() * 0.1F);
        return true;
    }

    /**
     * Release the stored maid with the lowest favorability (random on ties) at the given position.
     * Returns the released maid UUID, or null when nothing was released.
     */
    public static UUID releaseLowestFavorability(ServerPlayer player, double x, double y, double z, float yaw) {
        ItemStack bag = BagUtil.findBag(player).orElse(null);
        if (bag == null) {
            return null;
        }
        return releaseLowestFavorabilityFrom(player, bag, x, y, z, yaw);
    }

    /** Same as {@link #releaseLowestFavorability} but with an explicit bag stack (self test). */
    public static UUID releaseLowestFavorabilityFrom(ServerPlayer player, ItemStack bag,
                                                     double x, double y, double z, float yaw) {
        List<ItemStack> contents = BagUtil.readContents(bag);
        int slot = BagUtil.lowestFavorabilitySlot(contents, player.getUUID());
        if (slot < 0) {
            return null;
        }
        ItemStack charm = contents.get(slot);
        UUID maidId = BagUtil.maidIdOf(charm);
        if (!spawnMaidAt(player, charm, x, y, z, yaw)) {
            return null;
        }
        contents.set(slot, InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance());
        BagUtil.writeContents(bag, contents);
        BagUtil.saveBag(player, bag);
        player.sendSystemMessage(Component.translatable("message.maid_soul_bag.broom_released",
                BagUtil.favorabilityOf(charm)));
        return maidId;
    }

    /** Recall one maid by UUID into the bag; the charm is dropped if the bag is missing/full. */
    public static boolean recallMaidById(ServerPlayer player, UUID maidId) {
        return recallMaidByIdTo(player, BagUtil.findBag(player).orElse(null), maidId);
    }

    /** Same as {@link #recallMaidById} but with an explicit bag stack (self test). */
    public static boolean recallMaidByIdTo(ServerPlayer player, ItemStack bag, UUID maidId) {
        EntityMaid maid = findMaid(player, maidId);
        if (maid == null || !maid.isAlive() || maid.isRemoved()) {
            return false;
        }
        ItemStack charm = InitItems.SMART_SLAB_HAS_MAID.get().getDefaultInstance();
        ItemSmartSlab.storeMaidData(charm, maid);
        maid.discard();
        if (!maid.isRemoved()) {
            // Another mod blocked the removal: do not duplicate the maid.
            MaidSoulBag.LOGGER.warn("[MaidSoulBag] recall blocked by another mod for maid {}", maid.getUUID());
            return false;
        }

        if (bag == null) {
            player.drop(charm, false);
            return true;
        }
        List<ItemStack> contents = BagUtil.readContents(bag);
        int slot = BagUtil.firstCharmSlot(contents);
        if (slot < 0) {
            player.drop(charm, false);
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.broom_recall_full"));
            return true;
        }
        contents.set(slot, charm);
        BagUtil.writeContents(bag, contents);
        BagUtil.saveBag(player, bag);
        return true;
    }

    /**
     * Give the broom back after dismounting: the soul bag gets it first (when worn and not full),
     * otherwise it goes to the first empty inventory slot (or is dropped when the inventory is full).
     */
    public static void giveBroomItem(ServerPlayer player) {
        ItemStack bag = BagUtil.findBag(player).orElse(null);
        if (bag != null) {
            if (storeBroomInBag(player, bag)) {
                return;
            }
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.broom_bag_full"));
        }
        ItemStack broom = new ItemStack(InitItems.BROOM.get());
        if (!player.getInventory().add(broom)) {
            player.drop(broom, false);
        }
    }

    /** Store one broom into the first empty bag slot. Returns false when the bag has no free slot. */
    public static boolean storeBroomInBag(ServerPlayer player, ItemStack bag) {
        // Close the bag GUI first: its stale container snapshot would otherwise overwrite the broom.
        closeBagMenu(player);
        List<ItemStack> contents = BagUtil.readContents(bag);
        int slot = BagUtil.firstEmptySlot(contents);
        if (slot < 0) {
            return false;
        }
        contents.set(slot, new ItemStack(InitItems.BROOM.get()));
        BagUtil.writeContents(bag, contents);
        BagUtil.saveBag(player, bag);
        return true;
    }

    /** Locate a live maid entity by UUID in any loaded level. */
    public static EntityMaid findMaid(ServerPlayer player, UUID maidId) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid && maid.getUUID().equals(maidId)) {
                    return maid;
                }
            }
        }
        return null;
    }

    /** Close an open bag GUI so its stale container snapshot cannot overwrite the bag item. */
    private static void closeBagMenu(ServerPlayer player) {
        if (player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu chest
                && chest.getContainer() instanceof com.maidsoulbag.storage.BagContainer) {
            player.closeContainer();
        }
    }

    /** Test hook / admin reset. */
    public static void resetCooldown(UUID playerId) {
        LAST_ACTION.remove(playerId);
    }

    /**
     * Wheel actions share the same 1 second cooldown as the K/L keys.
     *
     * @return true when the action may run (and the cooldown is now armed)
     */
    public static boolean consumeCooldown(ServerPlayer player) {
        return !isOnCooldown(player);
    }

    private static boolean isOnCooldown(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_ACTION.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return true;
        }
        LAST_ACTION.put(player.getUUID(), now);
        return false;
    }
}

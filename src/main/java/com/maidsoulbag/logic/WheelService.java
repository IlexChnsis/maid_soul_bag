package com.maidsoulbag.logic;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.maidsoulbag.network.WheelEntryData;
import com.maidsoulbag.network.WheelStatePayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Server side implementation of the maid wheel: building the row list, releasing a single stored maid,
 * recalling a single live maid, and renaming a live maid.
 */
public final class WheelService {
    private static final double RENAME_RANGE_SQR = 64.0D;

    private WheelService() {
    }

    // ------------------------------------------------------------------
    // Row list
    // ------------------------------------------------------------------

    public static List<WheelEntryData> buildState(ServerPlayer player) {
        return buildState(player, BagUtil.findBag(player).orElse(null));
    }

    public static List<WheelEntryData> buildState(ServerPlayer player, ItemStack bag) {
        List<WheelEntryData> out = new ArrayList<>();

        // 1) Stored maids, in bag slot order.
        if (bag != null) {
            List<ItemStack> contents = BagUtil.readContents(bag);
            for (int slot = 0; slot < BagUtil.CAPACITY; slot++) {
                ItemStack charm = contents.get(slot);
                if (!BagUtil.isSoulCharm(charm)) {
                    continue;
                }
                UUID owner = BagUtil.ownerOf(charm);
                if (owner == null || !owner.equals(player.getUUID())) {
                    continue;
                }
                CompoundTag tag = BagUtil.maidTag(charm);
                if (tag == null) {
                    continue;
                }
                out.add(new WheelEntryData(true, slot, -1,
                        BagUtil.nameJsonOf(tag), BagUtil.modelIdOf(tag),
                        BagUtil.favorabilityOf(charm), BagUtil.healthOf(tag), BagUtil.maxHealthOf(tag),
                        WheelEntryData.STATUS_STORED, ""));
            }
        }

        // 2) Live maids of every loaded dimension, same dimension / nearest first.
        List<EntityMaid> live = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid
                        && maid.isAlive() && !maid.isRemoved()
                        && player.getUUID().equals(maid.getOwnerUUID())) {
                    live.add(maid);
                }
            }
        }
        live.sort(Comparator
                .comparing((EntityMaid maid) -> !maid.level().dimension().equals(player.level().dimension()))
                .thenComparingDouble(maid -> maid.distanceToSqr(player)));

        for (EntityMaid maid : live) {
            int status = maid.getVehicle() != null
                    ? WheelEntryData.STATUS_RIDING
                    : WheelEntryData.STATUS_RELEASED;
            out.add(new WheelEntryData(false, -1, maid.getId(),
                    nameJsonOf(maid), maid.getModelId(),
                    maid.getFavorability(), maid.getHealth(), maid.getMaxHealth(),
                    status, maid.level().dimension().location().toString()));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Release the maid stored in the given bag slot at the owner's position. */
    public static boolean releaseFromSlot(ServerPlayer player, int slot) {
        return releaseFromSlot(player, BagUtil.findBag(player).orElse(null), slot);
    }

    /** Same as {@link #releaseFromSlot(ServerPlayer, int)} with an explicit bag stack (self test). */
    public static boolean releaseFromSlot(ServerPlayer player, ItemStack bag, int slot) {
        if (bag == null) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.not_equipped"));
            return false;
        }
        if (!BagActions.consumeCooldown(player)) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.cooldown"));
            return false;
        }
        List<ItemStack> contents = BagUtil.readContents(bag);
        if (slot < 0 || slot >= BagUtil.CAPACITY) {
            return false;
        }
        ItemStack charm = contents.get(slot);
        if (!BagUtil.isSoulCharm(charm)) {
            return false;
        }
        UUID owner = BagUtil.ownerOf(charm);
        if (owner == null || !owner.equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.only_others"));
            return false;
        }
        Component name = displayName(charm, player.registryAccess());
        if (!BagActions.spawnMaidAt(player, charm, player.getX(), player.getY(), player.getZ(), player.getYRot())) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.wheel_failed"));
            return false;
        }
        contents.set(slot, InitItems.SMART_SLAB_EMPTY.get().getDefaultInstance());
        BagUtil.writeContents(bag, contents);
        BagUtil.saveBag(player, bag);
        player.sendSystemMessage(Component.translatable("message.maid_soul_bag.wheel_released", name));
        return true;
    }

    /** Recall a single live maid by entity id. Refuses while she is riding a vehicle. */
    public static boolean recallEntity(ServerPlayer player, int entityId) {
        return recallEntity(player, BagUtil.findBag(player).orElse(null), entityId);
    }

    /** Same as {@link #recallEntity(ServerPlayer, int)} with an explicit bag stack (self test). */
    public static boolean recallEntity(ServerPlayer player, ItemStack bag, int entityId) {
        if (!BagActions.consumeCooldown(player)) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.cooldown"));
            return false;
        }
        EntityMaid maid = findMaid(player, entityId);
        if (maid == null || !maid.isAlive() || maid.isRemoved()) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.wheel_failed"));
            return false;
        }
        if (!player.getUUID().equals(maid.getOwnerUUID())) {
            return false;
        }
        Component name = displayName(maid);
        if (maid.getVehicle() != null) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.wheel_riding", name));
            return false;
        }
        if (bag == null) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.not_equipped"));
            return false;
        }
        if (!BagActions.recallMaidByIdTo(player, bag, maid.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.wheel_failed"));
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.maid_soul_bag.wheel_recalled", name));
        return true;
    }

    /** Rename a live maid. Empty name clears the custom name. */
    public static boolean renameMaid(ServerPlayer player, int entityId, String rawName) {
        EntityMaid maid = findMaid(player, entityId);
        if (maid == null || !maid.isAlive() || maid.isRemoved()) {
            return false;
        }
        if (!player.getUUID().equals(maid.getOwnerUUID())) {
            return false;
        }
        if (player.distanceToSqr(maid) > RENAME_RANGE_SQR) {
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.rename_too_far"));
            return false;
        }
        String name = rawName == null ? "" : rawName.strip();
        if (name.length() > 32) {
            name = name.substring(0, 32);
        }
        if (name.isEmpty()) {
            maid.setCustomName(null);
            maid.setCustomNameVisible(false);
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.renamed_cleared"));
        } else {
            maid.setCustomName(Component.literal(name));
            maid.setCustomNameVisible(true);
            player.sendSystemMessage(Component.translatable("message.maid_soul_bag.renamed", name));
        }
        maid.setPersistenceRequired();
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Find a maid by entity id in any loaded level. */
    public static EntityMaid findMaid(ServerPlayer player, int entityId) {
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof EntityMaid maid) {
                return maid;
            }
        }
        return null;
    }

    private static String nameJsonOf(EntityMaid maid) {
        Component customName = maid.getCustomName();
        if (customName == null || customName.getString().isEmpty()) {
            return "";
        }
        return Component.Serializer.toJson(customName, maid.registryAccess());
    }

    private static Component displayName(ItemStack charm, RegistryAccess registries) {
        CompoundTag tag = BagUtil.maidTag(charm);
        String json = tag == null ? "" : BagUtil.nameJsonOf(tag);
        if (!json.isEmpty()) {
            try {
                Component parsed = Component.Serializer.fromJson(json, registries);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // fall through to the generic name
            }
        }
        return Component.translatable("gui.maid_soul_bag.unnamed");
    }

    private static Component displayName(EntityMaid maid) {
        Component customName = maid.getCustomName();
        if (customName != null && !customName.getString().isEmpty()) {
            return customName;
        }
        return Component.translatable("gui.maid_soul_bag.unnamed");
    }

    /** Reason code for an empty wheel: no bag vs no maids. */
    public static int emptyReason(ServerPlayer player) {
        return BagUtil.findBag(player).isEmpty() ? WheelStatePayload.REASON_NO_BAG : WheelStatePayload.REASON_OK;
    }
}

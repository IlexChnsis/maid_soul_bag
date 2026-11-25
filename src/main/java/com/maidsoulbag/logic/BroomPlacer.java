package com.maidsoulbag.logic;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.maidsoulbag.MaidSoulBag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Places a broom stored in the soul bag on the block the player is looking at (within vanilla block
 * reach). The normal broom automation then releases the lowest favorability maid and mounts the owner.
 * <p>
 * Nothing is consumed when there is no valid placement spot, so the key silently does nothing.
 */
public final class BroomPlacer {
    private BroomPlacer() {
    }

    /** Try to place a broom from the worn bag at the block the player is looking at. */
    public static boolean placeFromBag(ServerPlayer player) {
        ItemStack bag = BagUtil.findBag(player).orElse(null);
        if (bag == null || player.isSpectator()) {
            return false;
        }
        List<ItemStack> contents = BagUtil.readContents(bag);
        int slot = BagUtil.firstBroomSlot(contents);
        if (slot < 0) {
            return false;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        return place(player, bag, contents, slot, blockHit);
    }

    /** Self test helper: place using an explicit block position (as if the player was looking at it). */
    public static boolean placeAt(ServerPlayer player, ItemStack bag, BlockPos pos) {
        if (bag == null || player.isSpectator()) {
            return false;
        }
        List<ItemStack> contents = BagUtil.readContents(bag);
        int slot = BagUtil.firstBroomSlot(contents);
        if (slot < 0) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return place(player, bag, contents, slot, hit);
    }

    private static boolean place(ServerPlayer player, ItemStack bag, List<ItemStack> contents,
                                 int slot, BlockHitResult blockHit) {
        // Same restriction as the TLM broom item: never place against the bottom of a block.
        if (blockHit.getDirection() == Direction.DOWN) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        ItemStack broomStack = contents.get(slot);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, broomStack, blockHit);
        BlockPos pos = context.getClickedPos();
        if (!level.isInWorldBounds(pos) || !level.mayInteract(player, pos)) {
            return false;
        }
        EntityDimensions dimensions = EntityBroom.TYPE.getDimensions();
        AABB box = dimensions.makeBoundingBox(Vec3.atBottomCenterOf(pos));
        if (!level.noCollision(box)) {
            return false;
        }
        if (!level.getEntities((net.minecraft.world.entity.Entity) null, box).isEmpty()) {
            return false;
        }

        EntityBroom broom = EntityBroom.TYPE.create(level);
        if (broom == null) {
            return false;
        }
        broom.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        broom.setOwnerUUID(player.getUUID());
        if (!level.addFreshEntity(broom)) {
            MaidSoulBag.LOGGER.warn("[MaidSoulBag] could not place broom at {}", pos);
            return false;
        }

        // Consume exactly one broom (several brooms may be stored).
        broomStack.shrink(1);
        contents.set(slot, broomStack.isEmpty() ? ItemStack.EMPTY : broomStack);
        BagUtil.writeContents(bag, contents);
        BagUtil.saveBag(player, bag);

        level.playSound(null, broom.getX(), broom.getY(), broom.getZ(),
                SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
        return true;
    }
}

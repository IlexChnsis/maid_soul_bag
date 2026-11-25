package com.maidsoulbag.item;

import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.logic.BagUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SoulBagItem extends Item {
    public SoulBagItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrDefault(MaidSoulBag.COUNT.get(), 0) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int count = stack.getOrDefault(MaidSoulBag.COUNT.get(), 0);
        tooltip.add(Component.translatable("tooltip.maid_soul_bag.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.maid_soul_bag.count", count, BagUtil.CAPACITY)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** A dropped bag must not fall into the void (same trick as the soul spell item). */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.isCurrentlyGlowing()) {
            entity.setGlowingTag(true);
        }
        if (!entity.isInvulnerable()) {
            entity.setInvulnerable(true);
        }
        Vec3 position = entity.position();
        int minY = entity.level().getMinBuildHeight();
        if (position.y < minY) {
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPos(position.x, minY, position.z);
        }
        return super.onEntityItemUpdate(stack, entity);
    }
}

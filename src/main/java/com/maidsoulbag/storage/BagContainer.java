package com.maidsoulbag.storage;

import com.maidsoulbag.logic.BagUtil;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * A 27 slot container that reads/writes the soul bag item's data component.
 * The GUI itself is the vanilla three-row chest menu (no custom screen code).
 */
public class BagContainer extends SimpleContainer {
    private final Player player;
    private boolean ready;

    public BagContainer(Player player) {
        super(BagUtil.CAPACITY);
        this.player = player;
        if (player instanceof ServerPlayer serverPlayer) {
            BagUtil.findBag(serverPlayer).ifPresent(bag -> {
                List<ItemStack> contents = BagUtil.readContents(bag);
                for (int i = 0; i < BagUtil.CAPACITY; i++) {
                    super.setItem(i, contents.get(i));
                }
            });
        }
        this.ready = true;
    }

    public static ChestMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, new BagContainer(inventory.player));
    }

    @Override
    public void setChanged() {
        if (!this.ready || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BagUtil.findBag(serverPlayer).ifPresent(bag -> {
            BagUtil.writeContents(bag, snapshot());
            BagUtil.saveBag(serverPlayer, bag);
        });
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return BagUtil.isSoulSlab(stack) || BagUtil.isBroom(stack);
    }

    private List<ItemStack> snapshot() {
        List<ItemStack> list = new ArrayList<>(BagUtil.CAPACITY);
        for (int i = 0; i < BagUtil.CAPACITY; i++) {
            list.add(getItem(i));
        }
        return list;
    }
}

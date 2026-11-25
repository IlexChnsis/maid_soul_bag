package com.maidsoulbag.logic;

import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.maidsoulbag.MaidSoulBag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Low level helpers around the soul bag item stack and soul spells.
 */
public final class BagUtil {
    public static final int CAPACITY = 27;

    private BagUtil() {
    }

    public static Optional<ItemStack> findBag(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(MaidSoulBag.SOUL_BAG.get()))
                .map(SlotResult::stack)
                .filter(stack -> !stack.isEmpty());
    }

    public static List<ItemStack> readContents(ItemStack bag) {
        List<ItemStack> raw = bag.get(MaidSoulBag.CONTENTS.get());
        List<ItemStack> out = new ArrayList<>(CAPACITY);
        for (int i = 0; i < CAPACITY; i++) {
            out.add(raw != null && i < raw.size() ? raw.get(i) : ItemStack.EMPTY);
        }
        return out;
    }

    public static void writeContents(ItemStack bag, List<ItemStack> contents) {
        List<ItemStack> list = new ArrayList<>(CAPACITY);
        int count = 0;
        for (int i = 0; i < CAPACITY; i++) {
            ItemStack stack = i < contents.size() ? contents.get(i) : ItemStack.EMPTY;
            list.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            if (isSoulCharm(stack)) {
                count++;
            }
        }
        bag.set(MaidSoulBag.CONTENTS.get(), List.copyOf(list));
        bag.set(MaidSoulBag.COUNT.get(), count);
    }

    public static int storedCount(ItemStack bag) {
        return bag.getOrDefault(MaidSoulBag.COUNT.get(), 0);
    }

    public static int firstEmptySlot(List<ItemStack> contents) {
        for (int i = 0; i < CAPACITY; i++) {
            if (contents.size() <= i || contents.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** A "soul spell with a maid" = smart_slab_has_maid + maid_info data component. */
    public static boolean isSoulCharm(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(InitItems.SMART_SLAB_HAS_MAID.get())
                && stack.has(InitDataComponent.MAID_INFO.get());
    }

    /** Any of the three soul spell items (init / empty / has maid) may be stored as filler. */
    public static boolean isSoulSlab(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(InitItems.SMART_SLAB_EMPTY.get())
                || stack.is(InitItems.SMART_SLAB_INIT.get())
                || stack.is(InitItems.SMART_SLAB_HAS_MAID.get()));
    }

    /** A TLM broom may also be stored in the bag (several are allowed, one is used per placement). */
    public static boolean isBroom(ItemStack stack) {
        return !stack.isEmpty() && stack.is(InitItems.BROOM.get());
    }

    /** Index of the first broom stack, or -1. */
    public static int firstBroomSlot(List<ItemStack> contents) {
        for (int i = 0; i < CAPACITY; i++) {
            ItemStack stack = i < contents.size() ? contents.get(i) : ItemStack.EMPTY;
            if (isBroom(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Slot for a new charm: recycle an existing empty soul spell first, then a truly empty slot.
     * This keeps the bag compact and means releasing then recalling never needs extra space.
     */
    public static int firstCharmSlot(List<ItemStack> contents) {
        int emptySlot = -1;
        for (int i = 0; i < CAPACITY; i++) {
            ItemStack stack = i < contents.size() ? contents.get(i) : ItemStack.EMPTY;
            if (stack.is(InitItems.SMART_SLAB_EMPTY.get())) {
                return i;
            }
            if (emptySlot < 0 && stack.isEmpty()) {
                emptySlot = i;
            }
        }
        return emptySlot;
    }

    public static CompoundTag maidTag(ItemStack charm) {
        CustomData data = charm.get(InitDataComponent.MAID_INFO.get());
        return data == null ? null : data.copyTag();
    }

    public static UUID ownerOf(ItemStack charm) {
        CompoundTag tag = maidTag(charm);
        if (tag == null || !tag.hasUUID("Owner")) {
            return null;
        }
        return tag.getUUID("Owner");
    }

    public static UUID maidIdOf(ItemStack charm) {
        CompoundTag tag = maidTag(charm);
        if (tag == null || !tag.hasUUID("UUID")) {
            return null;
        }
        return tag.getUUID("UUID");
    }

    /** TLM stores favorability in the "MaidFavorability" NBT key. */
    public static int favorabilityOf(ItemStack charm) {
        CompoundTag tag = maidTag(charm);
        return tag == null ? 0 : tag.getInt("MaidFavorability");
    }

    /** Custom name of a stored maid, as a JSON component string ("" when the maid is unnamed). */
    public static String nameJsonOf(CompoundTag tag) {
        return tag == null ? "" : tag.getString("CustomName");
    }

    /** TLM model id of a stored maid, used for the fallback model name. */
    public static String modelIdOf(CompoundTag tag) {
        return tag == null ? "" : tag.getString("model_id");
    }

    /** Current health stored in the maid NBT. */
    public static float healthOf(CompoundTag tag) {
        return tag == null ? 0.0F : tag.getFloat("Health");
    }

    /**
     * Max health of a stored maid, parsed from the vanilla "attributes" list. Falls back to 20
     * (the vanilla player/maid base) when the attribute is missing or malformed.
     */
    public static float maxHealthOf(CompoundTag tag) {
        if (tag == null) {
            return 20.0F;
        }
        net.minecraft.nbt.ListTag attributes = tag.getList("attributes", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < attributes.size(); i++) {
            CompoundTag attribute = attributes.getCompound(i);
            if ("minecraft:generic.max_health".equals(attribute.getString("id"))) {
                double base = attribute.getDouble("base");
                return base <= 0.0D ? 20.0F : (float) base;
            }
        }
        return 20.0F;
    }

    /**
     * Index of the own charm with the lowest favorability; ties are broken randomly.
     * Returns -1 when the bag has no charm of this owner.
     */
    public static int lowestFavorabilitySlot(List<ItemStack> contents, UUID owner) {
        int best = -1;
        int bestFavorability = Integer.MAX_VALUE;
        int ties = 0;
        for (int i = 0; i < CAPACITY; i++) {
            ItemStack stack = i < contents.size() ? contents.get(i) : ItemStack.EMPTY;
            if (!isSoulCharm(stack)) {
                continue;
            }
            UUID charmOwner = ownerOf(stack);
            if (charmOwner == null || !charmOwner.equals(owner)) {
                continue;
            }
            int favorability = favorabilityOf(stack);
            if (favorability < bestFavorability) {
                bestFavorability = favorability;
                best = i;
                ties = 1;
            } else if (favorability == bestFavorability) {
                ties++;
                if (java.util.concurrent.ThreadLocalRandom.current().nextInt(ties) == 0) {
                    best = i;
                }
            }
        }
        return best;
    }

    /**
     * Curios may hand out a stack view instead of the live stack; write it back explicitly so
     * component changes survive and get synced to the client.
     */
    public static void saveBag(Player player, ItemStack bag) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.findFirstCurio(MaidSoulBag.SOUL_BAG.get()).ifPresent(result ->
                        handler.setEquippedCurio(
                                result.slotContext().identifier(),
                                result.slotContext().index(),
                                bag)));
    }
}

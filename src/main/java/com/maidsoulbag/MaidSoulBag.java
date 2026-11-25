package com.maidsoulbag;

import com.maidsoulbag.item.SoulBagItem;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.List;

@Mod(MaidSoulBag.MODID)
public final class MaidSoulBag {
    public static final String MODID = "maid_soul_bag";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** The accessory itself. */
    public static final DeferredItem<SoulBagItem> SOUL_BAG = ITEMS.registerItem(
            "soul_bag", SoulBagItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());

    /** 27 soul charm stacks, persistent only (never networked: maid NBT can be heavy). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> CONTENTS =
            DATA_COMPONENTS.registerComponentType("contents",
                    builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

    /** Cached number of stored charms, networked so the tooltip works client side. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> COUNT =
            DATA_COMPONENTS.registerComponentType("count",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Vanilla three-row chest menu, reused for the bag GUI. */
    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> BAG_MENU = MENUS.register(
            "soul_bag", () -> new MenuType<>(
                    (id, inventory) -> ChestMenu.threeRows(
                            id, inventory, new com.maidsoulbag.storage.BagContainer(inventory.player)),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.maid_soul_bag"))
                    .icon(() -> SOUL_BAG.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(SOUL_BAG.get()))
                    .build());

    public MaidSoulBag(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        MENUS.register(modEventBus);
        TABS.register(modEventBus);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.maidsoulbag.config.ClientConfig.SPEC);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.maidsoulbag.client.ClientIntegration.registerConfigScreen(modContainer);
        }
        LOGGER.info("[MaidSoulBag] enabled | TLM={} Curios={} TLM-Spell={}",
                ModList.get().isLoaded("touhou_little_maid"),
                ModList.get().isLoaded("curios"),
                ModList.get().isLoaded("touhou_little_maid_spell"));
    }
}

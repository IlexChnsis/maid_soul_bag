package com.maidsoulbag.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.TouhouImageButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.MaidMainContainer;
import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds a pencil button to the maid GUI (next to "sound / YSM skin / change skin"). Clicking it opens
 * {@link MaidRenameScreen}; the rename itself is free (no name tag is consumed).
 * <p>
 * Uses TLM's own {@code MaidContainerGuiEvent} extension points, so no mixin is required.
 */
@EventBusSubscriber(modid = MaidSoulBag.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class MaidGuiHook {
    private static final String WIDGET_KEY = "maid_soul_bag.rename";
    private static final ResourceLocation RENAME_ICON =
            ResourceLocation.fromNamespaceAndPath(MaidSoulBag.MODID, "textures/gui/rename_button.png");

    private static AbstractWidget renameButton;
    private static EntityMaid renameMaid;
    private static net.minecraft.client.gui.screens.Screen renameParent;

    private MaidGuiHook() {
    }

    @SubscribeEvent
    public static void onMaidGuiInit(MaidContainerGuiEvent.Init event) {
        if (!(event.getGui().getMenu() instanceof MaidMainContainer)) {
            return;
        }
        EntityMaid maid = event.getGui().getMaid();
        if (maid == null) {
            return;
        }
        net.minecraft.client.gui.screens.Screen parent = event.getGui();
        TouhouImageButton button = new TouhouImageButton(
                event.getLeftPos() + ClientConfig.NAME_BUTTON_X.get(),
                event.getTopPos() + ClientConfig.NAME_BUTTON_Y.get(),
                9, 9,
                0, 0, 9,
                RENAME_ICON,
                9, 18,
                pressed -> Minecraft.getInstance().setScreen(new MaidRenameScreen(maid, parent)));
        renameButton = button;
        renameMaid = maid;
        renameParent = parent;
        event.addButton(WIDGET_KEY, button);
    }

    @SubscribeEvent
    public static void onMaidGuiTooltip(MaidContainerGuiEvent.Tooltip event) {
        if (renameButton == null || renameMaid == null || renameParent != event.getGui()) {
            return;
        }
        if (renameButton.isMouseOver(event.getMouseX(), event.getMouseY())) {
            event.getGraphics().renderTooltip(Minecraft.getInstance().font,
                    Component.translatable("gui.maid_soul_bag.rename_button"),
                    event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (renameParent != null && event.getScreen() == renameParent) {
            renameButton = null;
            renameMaid = null;
            renameParent = null;
        }
    }
}

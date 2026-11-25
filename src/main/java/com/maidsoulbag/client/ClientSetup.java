package com.maidsoulbag.client;

import com.maidsoulbag.MaidSoulBag;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client registration: reuse the vanilla chest screen, register the key bindings.
 */
@EventBusSubscriber(modid = MaidSoulBag.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MaidSoulBag.BAG_MENU.get(), ContainerScreen::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ClientKeys.OPEN);
        event.register(ClientKeys.RELEASE);
        event.register(ClientKeys.RECALL);
        event.register(ClientKeys.PLACE_BROOM);
        event.register(ClientKeys.RADIAL);
    }
}

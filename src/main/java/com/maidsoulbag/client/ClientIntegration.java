package com.maidsoulbag.client;

import com.maidsoulbag.MaidSoulBag;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Adds the built-in NeoForge config screen so the chord modifiers can be edited in game
 * (Mods -> Maid Soul Bag -> Config). Only referenced on the physical client.
 */
public final class ClientIntegration {
    private ClientIntegration() {
    }

    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new ConfigurationScreen(modContainer, parent));
        MaidSoulBag.LOGGER.debug("[MaidSoulBag] config screen registered");
    }
}

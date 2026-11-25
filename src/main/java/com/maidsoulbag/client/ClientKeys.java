package com.maidsoulbag.client;

import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.config.ClientConfig;
import com.maidsoulbag.network.BagActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings.
 * <p>
 * Every action is registered as a vanilla {@link KeyMapping}, so it shows up in
 * Options -> Controls -> "Maid Soul Bag" and can be rebound (or unbound) there.
 * The optional modifier chord (Shift / Ctrl / Alt + key) is configured through the mod config screen.
 */
@EventBusSubscriber(modid = MaidSoulBag.MODID, value = Dist.CLIENT)
public final class ClientKeys {
    private static final String CATEGORY = "key.categories.maid_soul_bag";

    public static final KeyMapping OPEN = new KeyMapping(
            "key.maid_soul_bag.open", InputConstants.KEY_B, CATEGORY);

    public static final KeyMapping RELEASE = new KeyMapping(
            "key.maid_soul_bag.release", InputConstants.KEY_K, CATEGORY);

    public static final KeyMapping RECALL = new KeyMapping(
            "key.maid_soul_bag.recall", InputConstants.KEY_L, CATEGORY);

    /** Places a broom taken from the bag on the block the player is looking at. */
    public static final KeyMapping PLACE_BROOM = new KeyMapping(
            "key.maid_soul_bag.place_broom", InputConstants.KEY_G, CATEGORY);

    /** Hold this key to open the maid wheel (release it to act on the selected maid). */
    public static final KeyMapping RADIAL = new KeyMapping(
            "key.maid_soul_bag.radial", InputConstants.KEY_R, CATEGORY);

    private static boolean radialHeld;

    private ClientKeys() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (OPEN.consumeClick() && modifierHeld(ClientConfig.OPEN_MODIFIER.get())) {
            PacketDistributor.sendToServer(new BagActionPayload(BagActionPayload.Action.OPEN));
        }
        if (RELEASE.consumeClick() && modifierHeld(ClientConfig.RELEASE_MODIFIER.get())) {
            PacketDistributor.sendToServer(new BagActionPayload(BagActionPayload.Action.RELEASE));
        }
        if (RECALL.consumeClick() && modifierHeld(ClientConfig.RECALL_MODIFIER.get())) {
            PacketDistributor.sendToServer(new BagActionPayload(BagActionPayload.Action.RECALL));
        }
        if (PLACE_BROOM.consumeClick() && modifierHeld(ClientConfig.PLACE_BROOM_MODIFIER.get())) {
            PacketDistributor.sendToServer(new BagActionPayload(BagActionPayload.Action.PLACE_BROOM));
        }
        tickRadial();
    }

    /** Press and hold {@link #RADIAL} to open the wheel; the wheel itself handles the release. */
    private static void tickRadial() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean down = radialDown() && modifierHeld(ClientConfig.RADIAL_MODIFIER.get());
        if (!down) {
            radialHeld = false;
        } else if (!radialHeld && minecraft.screen == null && minecraft.player != null) {
            radialHeld = true;
            PacketDistributor.sendToServer(com.maidsoulbag.network.WheelRequestPayload.INSTANCE);
            com.maidsoulbag.client.wheel.WheelScreen.open();
        }
    }

    private static boolean radialDown() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        if (window == 0L) {
            return false;
        }
        try {
            InputConstants.Key key = InputConstants.getKey(RADIAL.saveString());
            return key.getValue() > 0 && InputConstants.isKeyDown(window, key.getValue());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean modifierHeld(ClientConfig.Modifier modifier) {
        if (modifier == ClientConfig.Modifier.NONE) {
            return true;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window == 0L) {
            return false;
        }
        return switch (modifier) {
            case SHIFT -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
            case CONTROL -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            case ALT -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
            case NONE -> true;
        };
    }
}

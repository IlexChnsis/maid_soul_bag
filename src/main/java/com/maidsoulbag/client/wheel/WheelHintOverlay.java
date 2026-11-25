package com.maidsoulbag.client.wheel;

import com.maidsoulbag.MaidSoulBag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Bottom-left hint used by the wheel (e.g. "the maid is riding, cannot recall").
 */
@EventBusSubscriber(modid = MaidSoulBag.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class WheelHintOverlay {
    private static final int DURATION = 60;
    private static final int FADE = 15;

    private static Component message;
    private static int ticksLeft;

    private WheelHintOverlay() {
    }

    public static void show(Component text) {
        message = text;
        ticksLeft = DURATION;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (message == null || ticksLeft <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        float fade = Math.min(1.0F, ticksLeft / (float) FADE);
        int alpha = (int) (255 * fade);
        int color = (alpha << 24) | 0xE8908C;
        int x = 6;
        int y = minecraft.getWindow().getGuiScaledHeight() - 24;
        graphics.drawString(minecraft.font, message, x, y, color, true);
    }
}

package com.maidsoulbag.client.wheel;

import com.maidsoulbag.client.ClientKeys;
import com.maidsoulbag.config.ClientConfig;
import com.maidsoulbag.network.WheelActionPayload;
import com.maidsoulbag.network.WheelEntryData;
import com.maidsoulbag.network.WheelStatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The maid wheel: three horizontal rows (the middle one is the current selection), scroll with the mouse
 * wheel, release the key to act.
 * <p>
 * Stored maid: released at the owner's position. Live maid: recalled into the bag. Riding maid: only a hint.
 */
public class WheelScreen extends Screen {
    private static final int BAR_GAP = 56;
    private static final int CENTER_WIDTH = 240;
    private static final int CENTER_HEIGHT = 40;
    private static final int SIDE_WIDTH = 200;
    private static final int SIDE_HEIGHT = 28;
    private static final int MAX_VISIBLE_DISTANCE = 2; // snap values beyond this to their target

    private final List<WheelRow> rows = new ArrayList<>();
    private float[] slots = new float[0];
    private int selected;
    private boolean loading = true;
    private boolean done;
    private final int keyCode;
    private long lastFrameNanos = System.nanoTime();
    private float openAnimation;

    public WheelScreen() {
        super(Component.translatable("gui.maid_soul_bag.wheel_title"));
        this.keyCode = resolveKeyCode();
    }

    // ------------------------------------------------------------------
    // Opening / receiving data
    // ------------------------------------------------------------------

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new WheelScreen());
        playSound(1.7F, 0.30F);
    }

    public static void receive(WheelStatePayload payload) {
        if (Minecraft.getInstance().screen instanceof WheelScreen wheel) {
            wheel.setData(payload);
        }
    }

    private void setData(WheelStatePayload payload) {
        loading = false;
        rows.clear();
        for (WheelEntryData entry : payload.entries()) {
            rows.add(WheelRow.of(entry));
        }
        if (rows.isEmpty()) {
            WheelHintOverlay.show(payload.reason() == WheelStatePayload.REASON_NO_BAG
                    ? Component.translatable("message.maid_soul_bag.not_equipped")
                    : Component.translatable("message.maid_soul_bag.wheel_empty"));
            done = true;
            onClose();
            return;
        }
        selected = Mth.clamp(WheelClientState.lastSelected, 0, rows.size() - 1);
        slots = new float[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            slots[i] = i - selected;
        }
        openAnimation = 0.0F;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x40000000);

        long now = System.nanoTime();
        float delta = Math.min(0.1F, (now - lastFrameNanos) / 1_000_000_000.0F);
        lastFrameNanos = now;

        if (loading) {
            graphics.drawCenteredString(font, Component.translatable("gui.maid_soul_bag.wheel_loading"),
                    width / 2, height / 2 - 4, 0xFFF2EAD3);
            return;
        }

        float smoothing = 1.0F - (float) Math.exp(-delta * 16.0F);
        for (int i = 0; i < rows.size(); i++) {
            float target = i - selected;
            if (Math.abs(slots[i] - target) > MAX_VISIBLE_DISTANCE) {
                slots[i] = target;
            } else {
                slots[i] = Mth.lerp(smoothing, slots[i], target);
            }
        }
        openAnimation = Math.min(1.0F, openAnimation + delta * 8.0F);

        // Side rows first so the selected row is drawn on top.
        for (int i = 0; i < rows.size(); i++) {
            if (Math.abs(slots[i]) >= 0.5F && Math.abs(slots[i]) < 1.55F) {
                drawBar(graphics, rows.get(i), slots[i]);
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            if (Math.abs(slots[i]) < 0.5F) {
                drawBar(graphics, rows.get(i), slots[i]);
            }
        }
    }

    private void drawBar(GuiGraphics graphics, WheelRow row, float slot) {
        float distance = Math.abs(slot);
        boolean center = distance < 0.5F;
        float scale = (1.0F - 0.30F * distance) * (0.92F + 0.08F * openAnimation);
        float alpha = Mth.clamp(1.0F - 0.55F * distance, 0.15F, 1.0F);

        int barWidth = center ? CENTER_WIDTH : SIDE_WIDTH;
        int barHeight = center ? CENTER_HEIGHT : SIDE_HEIGHT;
        float centerX = width / 2.0F;
        float centerY = height / 2.0F + slot * BAR_GAP;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(scale, scale, 1.0F);

        int halfWidth = barWidth / 2;
        int halfHeight = barHeight / 2;
        int background = center ? 0x991E1B2A : 0x771E1B2A;
        int border = center ? 0xFFE8C36A : 0x66E8C36A;

        graphics.fill(-halfWidth, -halfHeight, halfWidth, halfHeight, withAlpha(background, alpha));
        graphics.fill(-halfWidth, -halfHeight, halfWidth, -halfHeight + 1, withAlpha(border, alpha));
        graphics.fill(-halfWidth, halfHeight - 1, halfWidth, halfHeight, withAlpha(border, alpha));
        graphics.fill(-halfWidth, -halfHeight, -halfWidth + 1, halfHeight, withAlpha(border, alpha));
        graphics.fill(halfWidth - 1, -halfHeight, halfWidth, halfHeight, withAlpha(border, alpha));
        if (center) {
            graphics.fill(-halfWidth, -halfHeight, -halfWidth + 2, halfHeight, withAlpha(0xFFE8C36A, alpha));
        }

        Font barFont = this.font;
        int baseline = -3;
        int nameX = -halfWidth + 8;
        int nameWidth = (int) (barWidth * 0.32F);
        String name = barFont.plainSubstrByWidth(row.name.getString(), nameWidth);
        graphics.drawString(barFont, name, nameX, baseline, withAlpha(0xFFF2EAD3, alpha), false);

        int favorX = -halfWidth + (int) (barWidth * 0.37F);
        graphics.drawString(barFont, row.favorText, favorX, baseline, withAlpha(0xFFE8C36A, alpha), false);

        int healthX = -halfWidth + (int) (barWidth * 0.53F);
        graphics.drawString(barFont, row.healthText, healthX, baseline, withAlpha(0xFFD8CBB0, alpha), false);

        int rightLimit = halfWidth - 8;
        int available = rightLimit - (healthX + barFont.width(row.healthText) + 6);
        Component status = row.statusText;
        int statusWidth = barFont.width(status);
        if (statusWidth > available && available > 6) {
            String cut = barFont.plainSubstrByWidth(status.getString(),
                    Math.max(2, available - barFont.width("…")));
            status = Component.literal(cut + "…");
            statusWidth = barFont.width(status);
        }
        graphics.drawString(barFont, status, rightLimit - statusWidth, baseline,
                withAlpha(row.statusColor(), alpha), false);

        pose.popPose();
    }

    private static int withAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int resultAlpha = Mth.clamp((int) (baseAlpha * alpha), 0, 255);
        return (resultAlpha << 24) | (color & 0xFFFFFF);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (loading || done || rows.isEmpty() || scrollY == 0.0D) {
            return true;
        }
        selected = Math.floorMod(selected + (scrollY > 0.0D ? -1 : 1), rows.size());
        playSound(1.9F, 0.16F);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            done = true;
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.keyCode >= 0 && keyCode == this.keyCode) {
            confirm();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (done || keyCode < 0) {
            return;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (window != 0L && !InputConstants.isKeyDown(window, keyCode)) {
            confirm();
        }
    }

    private void confirm() {
        if (done) {
            return;
        }
        done = true;
        if (loading || rows.isEmpty()) {
            onClose();
            return;
        }
        WheelRow row = rows.get(selected);
        WheelClientState.lastSelected = selected;
        if (row.status == WheelEntryData.STATUS_RIDING) {
            WheelHintOverlay.show(Component.translatable("message.maid_soul_bag.wheel_riding", row.shortName()));
            playSound(0.9F, 0.35F);
            onClose();
            return;
        }
        PacketDistributor.sendToServer(new WheelActionPayload(row.stored, row.bagSlot, row.entityId));
        playSound(1.35F, 0.40F);
        onClose();
    }

    @Override
    public void onClose() {
        done = true;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void playSound(float pitch, float volume) {
        if (!ClientConfig.WHEEL_SOUND.get()) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
    }

    private static int resolveKeyCode() {
        try {
            InputConstants.Key key = InputConstants.getKey(ClientKeys.RADIAL.saveString());
            return key.getValue();
        } catch (Exception ignored) {
            return -1;
        }
    }
}

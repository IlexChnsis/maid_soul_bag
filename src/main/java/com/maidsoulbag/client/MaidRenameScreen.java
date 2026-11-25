package com.maidsoulbag.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidsoulbag.MaidSoulBag;
import com.maidsoulbag.network.RenameMaidPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Small popup used by the pencil button in the maid GUI. Renaming is free (no name tag is consumed);
 * closing the popup returns to the maid GUI instead of the world.
 */
public class MaidRenameScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 92;
    private static final int MAX_LENGTH = 32;

    private final EntityMaid maid;
    private final Screen parent;
    private final String original;

    private EditBox nameBox;

    public MaidRenameScreen(EntityMaid maid, Screen parent) {
        super(Component.translatable("gui.maid_soul_bag.rename_title"));
        this.maid = maid;
        this.parent = parent;
        Component customName = maid.getCustomName();
        this.original = customName == null ? "" : customName.getString();
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        nameBox = new EditBox(font, left + 20, top + 26, PANEL_WIDTH - 40, 18,
                Component.translatable("gui.maid_soul_bag.rename_title"));
        nameBox.setMaxLength(MAX_LENGTH);
        nameBox.setValue(original);
        nameBox.setBordered(true);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> confirm())
                .bounds(left + 26, top + 60, 70, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + 104, top + 60, 70, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        graphics.fill(left, top, right, bottom, 0xE61E1B2A);
        graphics.fill(left, top, right, top + 1, 0xFFE8C36A);
        graphics.fill(left, bottom - 1, right, bottom, 0xFFE8C36A);
        graphics.fill(left, top, left + 1, bottom, 0xFFE8C36A);
        graphics.fill(right - 1, top, right, bottom, 0xFFE8C36A);
        graphics.drawCenteredString(font, title, width / 2, top + 9, 0xFFF2EAD3);
        graphics.drawString(font, Component.translatable("gui.maid_soul_bag.rename_hint"),
                left + 20, top + 47, 0xFFA99F8C, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (nameBox != null && nameBox.getValue().isBlank()) {
            graphics.drawString(font, Component.translatable("gui.maid_soul_bag.unnamed"),
                    nameBox.getX() + 4, nameBox.getY() + 5, 0xFF8C8577, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        String name = nameBox == null ? original : nameBox.getValue().strip();
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH);
        }
        PacketDistributor.sendToServer(new RenameMaidPayload(maid.getId(), name));
        onClose();
    }

    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft.getInstance().setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

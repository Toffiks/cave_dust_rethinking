package net.lizistired.cavedust;

import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class CaveDustReleaseButton extends Button {
    private final Runnable action;
    private boolean mouseArmed;

    CaveDustReleaseButton(int x, int y, int width, int height, Component message, Runnable action) {
        super(x, y, width, height, message, button -> { }, DEFAULT_NARRATION);
        this.action = action;
    }

    @Override
    public void onPress() {
        if (Minecraft.getInstance().getLastInputType() == InputType.MOUSE) {
            mouseArmed = true;
        } else {
            action.run();
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (mouseArmed) {
            mouseArmed = false;
            action.run();
        }
    }
}

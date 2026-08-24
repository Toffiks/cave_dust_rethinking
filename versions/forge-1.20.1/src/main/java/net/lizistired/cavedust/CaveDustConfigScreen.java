package net.lizistired.cavedust;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CaveDustConfigScreen extends Screen {
    private final Screen parent;
    private final CaveDustConfig config;

    CaveDustConfigScreen(Screen parent) {
        super(Component.translatable("menu.cavedust.title"));
        this.parent = parent;
        this.config = CaveDustClient.config();
    }

    @Override
    protected void init() {
        config.reload();
        int left = (this.width - 200) / 2;
        int row = (this.height - (20 + 2 * 24)) / 2;

        Button enabled = Button.builder(enabledText(), button -> {
                    config.toggleEnabled();
                    button.setMessage(enabledText());
                    button.setTooltip(enabledTooltip());
                })
                .bounds(left, row, 200, 20)
                .tooltip(enabledTooltip())
                .build();
        this.addRenderableWidget(enabled);

        row += 24;
        this.addRenderableWidget(new CaveDustReleaseButton(left, row, 200, 20,
                Component.translatable("menu.cavedust.title.advanced"),
                () -> this.minecraft.setScreen(new CaveDustAdvancedConfigScreen(this))));

        row += 24;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left, row, 200, 20)
                .build());
    }

    private Component enabledText() {
        return Component.translatable("menu.cavedust.global." + config.enabled());
    }

    private Tooltip enabledTooltip() {
        return Tooltip.create(Component.translatable("menu.cavedust.global.tooltip." + config.enabled()));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
    }
}
